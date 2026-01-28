package com.ortoped.api.service

import com.ortoped.api.model.*
import com.ortoped.api.plugins.BadRequestException
import com.ortoped.api.plugins.InternalException
import com.ortoped.api.plugins.NotFoundException
import com.ortoped.api.repository.*
import com.ortoped.core.model.Dependency
import com.ortoped.core.model.ScanResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Service for managing curation workflow
 */
class CurationService(
    private val curationRepository: CurationRepository,
    private val curationSessionRepository: CurationSessionRepository,
    private val curatedScanRepository: CuratedScanRepository,
    private val scanRepository: ScanRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ========================================================================
    // Session Management
    // ========================================================================

    /**
     * Start a new curation session for a scan
     */
    fun startCurationSession(
        scanId: String,
        request: StartCurationRequest,
        curatorId: String? = null
    ): CurationSessionResponse {
        val scanUuid = UUID.fromString(scanId)

        // Check if session already exists
        val existingSession = curationSessionRepository.findByScanId(scanUuid)
        if (existingSession != null) {
            logger.info { "Curation session already exists for scan $scanId" }
            return existingSession.toResponse()
        }

        // Get scan result
        val scanEntity = scanRepository.findById(scanUuid)
            ?: throw NotFoundException("Scan not found: $scanId")

        if (scanEntity.status != "complete") {
            throw BadRequestException("Cannot start curation for scan with status: ${scanEntity.status}")
        }

        val scanResult = scanEntity.result?.let {
            try {
                json.decodeFromString<ScanResult>(it)
            } catch (e: Exception) {
                logger.error(e) { "Failed to parse scan result for $scanId" }
                throw BadRequestException("Invalid scan result format")
            }
        } ?: throw BadRequestException("Scan has no result")

        // Filter dependencies based on request
        val dependenciesToCurate = if (request.includeResolved) {
            scanResult.dependencies
        } else {
            scanResult.dependencies.filter { !it.isResolved || it.aiSuggestion != null }
        }

        // Create session
        val session = curationSessionRepository.create(
            scanId = scanUuid,
            totalItems = dependenciesToCurate.size
        )

        logger.info { "Created curation session ${session.id} with ${dependenciesToCurate.size} items" }

        // Create curation items for each dependency
        var autoAcceptedCount = 0
        dependenciesToCurate.forEach { dep ->
            val priority = calculatePriority(dep)

            val curation = curationRepository.create(
                sessionId = session.id,
                scanId = scanUuid,
                dependencyId = dep.id,
                dependencyName = dep.name,
                dependencyVersion = dep.version,
                dependencyScope = dep.scope,
                originalLicense = dep.concludedLicense,
                declaredLicenses = json.encodeToString(dep.declaredLicenses),
                detectedLicenses = json.encodeToString(dep.detectedLicenses),
                aiSuggestedLicense = dep.aiSuggestion?.suggestedLicense,
                aiConfidence = dep.aiSuggestion?.confidence,
                aiReasoning = dep.aiSuggestion?.reasoning,
                aiAlternatives = dep.aiSuggestion?.alternatives?.let { json.encodeToString(it) },
                priorityLevel = priority.level,
                priorityScore = priority.score,
                priorityFactors = json.encodeToString(priority.factors)
            )

            // Auto-accept high confidence if requested
            val aiSuggestion = dep.aiSuggestion
            if (request.autoAcceptHighConfidence &&
                aiSuggestion?.confidence == "HIGH" &&
                priority.level == PriorityLevel.LOW) {
                curationRepository.updateDecision(
                    id = curation.id,
                    status = CurationStatus.ACCEPTED,
                    curatedLicense = aiSuggestion.suggestedLicense,
                    curatorComment = "Auto-accepted: High confidence AI suggestion",
                    curatorId = "system"
                )
                autoAcceptedCount++
            }
        }

        // Update statistics if auto-accepted any
        if (autoAcceptedCount > 0) {
            curationSessionRepository.recalculateStatistics(session.id, curationRepository)
        }

        logger.info { "Created ${dependenciesToCurate.size} curation items, auto-accepted $autoAcceptedCount" }

        // Return fresh session data
        return curationSessionRepository.findById(session.id)?.toResponse()
            ?: throw InternalException("Failed to retrieve created session")
    }

    /**
     * Get curation session for a scan
     */
    fun getSession(scanId: String): CurationSessionResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")
        return session.toResponse()
    }

    /**
     * Approve a curation session (final sign-off)
     */
    fun approveSession(
        scanId: String,
        request: ApprovalRequest,
        approvedBy: String
    ): CurationSessionResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        // Check all items are curated
        val stats = curationRepository.getStatisticsBySessionId(session.id)
        if (stats.pending > 0) {
            throw BadRequestException("Cannot approve: ${stats.pending} items still pending review")
        }

        // Update session status
        curationSessionRepository.approve(session.id, approvedBy, request.comment)

        // Create curated scan record for incremental curation
        val scanEntity = scanRepository.findById(scanUuid)
        val projectId = scanEntity?.projectId

        // Find previous curated scan for this project
        val previousCuratedScan = projectId?.let {
            curatedScanRepository.findLatestForProject(it)
        }

        // Calculate dependency hash for future diff
        val curations = curationRepository.findBySessionId(session.id)
        val dependencyHash = calculateDependencyHash(curations)

        curatedScanRepository.create(
            scanId = scanUuid,
            previousScanId = previousCuratedScan?.scanId,
            sessionId = session.id,
            curatorId = approvedBy,
            dependencyHash = dependencyHash,
            dependencyCount = curations.size
        )

        logger.info { "Approved curation session for scan $scanId by $approvedBy" }

        return curationSessionRepository.findById(session.id)?.toResponse()
            ?: throw InternalException("Failed to retrieve session")
    }

    // ========================================================================
    // Curation Items
    // ========================================================================

    /**
     * Get curation items for a session with filtering
     */
    fun getCurationItems(
        scanId: String,
        status: String? = null,
        priority: String? = null,
        hasAiSuggestion: Boolean? = null,
        page: Int = 1,
        pageSize: Int = 20,
        sortBy: String = "priority",
        sortOrder: String = "desc"
    ): CurationItemsResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        val statusEnum = status?.let { CurationStatus.fromString(it) }
        val priorityEnum = priority?.let { PriorityLevel.fromString(it) }

        val offset = ((page - 1) * pageSize).toLong()
        val items = curationRepository.findBySessionId(
            sessionId = session.id,
            status = statusEnum,
            priority = priorityEnum,
            hasAiSuggestion = hasAiSuggestion,
            limit = pageSize,
            offset = offset,
            sortBy = sortBy,
            sortOrder = sortOrder
        )

        val total = curationRepository.countBySessionId(
            sessionId = session.id,
            status = statusEnum,
            priority = priorityEnum,
            hasAiSuggestion = hasAiSuggestion
        )

        val stats = curationRepository.getStatisticsBySessionId(session.id)

        return CurationItemsResponse(
            items = items.map { it.toItemResponse() },
            total = total.toInt(),
            page = page,
            pageSize = pageSize,
            statistics = CurationStatistics(
                total = stats.total,
                pending = stats.pending,
                accepted = stats.accepted,
                rejected = stats.rejected,
                modified = stats.modified
            )
        )
    }

    /**
     * Get a single curation item
     */
    fun getCurationItem(scanId: String, dependencyId: String): CurationItemResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        val curation = curationRepository.findByDependencyId(session.id, dependencyId)
            ?: throw NotFoundException("Curation item not found: $dependencyId")

        return curation.toItemResponse()
    }

    /**
     * Submit a curation decision
     */
    fun submitDecision(
        scanId: String,
        dependencyId: String,
        request: CurationDecisionRequest,
        curatorId: String? = null
    ): CurationItemResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        if (session.status == CurationSessionStatus.APPROVED.value) {
            throw BadRequestException("Cannot modify approved curation session")
        }

        val curation = curationRepository.findByDependencyId(session.id, dependencyId)
            ?: throw NotFoundException("Curation item not found: $dependencyId")

        // Determine status and license based on action
        val (status, license) = when (request.action.uppercase()) {
            "ACCEPT" -> {
                val aiLicense = curation.aiSuggestedLicense
                    ?: throw BadRequestException("Cannot accept: No AI suggestion available")
                CurationStatus.ACCEPTED to aiLicense
            }
            "REJECT" -> {
                val manualLicense = request.license
                    ?: throw BadRequestException("License required for REJECT action")
                CurationStatus.REJECTED to manualLicense
            }
            "MODIFY" -> {
                val modifiedLicense = request.license
                    ?: throw BadRequestException("License required for MODIFY action")
                CurationStatus.MODIFIED to modifiedLicense
            }
            else -> throw BadRequestException("Invalid action: ${request.action}")
        }

        // Update the curation
        curationRepository.updateDecision(
            id = curation.id,
            status = status,
            curatedLicense = license,
            curatorComment = request.comment,
            curatorId = curatorId
        )

        // Update session statistics
        curationSessionRepository.recalculateStatistics(session.id, curationRepository)

        // Check if all items are now curated
        val stats = curationRepository.getStatisticsBySessionId(session.id)
        if (stats.pending == 0) {
            curationSessionRepository.updateStatus(session.id, CurationSessionStatus.COMPLETED)
        }

        logger.info { "Curation decision for $dependencyId: $status -> $license" }

        return curationRepository.findByDependencyId(session.id, dependencyId)?.toItemResponse()
            ?: throw InternalException("Failed to retrieve updated curation")
    }

    /**
     * Bulk curation decisions
     */
    fun bulkCurate(
        scanId: String,
        request: BulkCurationRequest,
        curatorId: String? = null
    ): BulkCurationResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        if (session.status == CurationSessionStatus.APPROVED.value) {
            throw BadRequestException("Cannot modify approved curation session")
        }

        var succeeded = 0
        var failed = 0
        val errors = mutableListOf<BulkCurationError>()

        request.decisions.forEach { item ->
            try {
                submitDecision(scanId, item.dependencyId, CurationDecisionRequest(
                    action = item.action,
                    license = item.license,
                    comment = item.comment
                ), curatorId)
                succeeded++
            } catch (e: Exception) {
                failed++
                errors.add(BulkCurationError(item.dependencyId, e.message ?: "Unknown error"))
            }
        }

        logger.info { "Bulk curation: $succeeded succeeded, $failed failed" }

        return BulkCurationResponse(
            processed = request.decisions.size,
            succeeded = succeeded,
            failed = failed,
            errors = errors
        )
    }

    // ========================================================================
    // Incremental Curation
    // ========================================================================

    /**
     * Get incremental changes between current scan and previous curated scan
     */
    fun getIncrementalChanges(scanId: String): IncrementalCurationResponse {
        val scanUuid = UUID.fromString(scanId)

        val scanEntity = scanRepository.findById(scanUuid)
            ?: throw NotFoundException("Scan not found: $scanId")

        val projectId = scanEntity.projectId
            ?: return IncrementalCurationResponse(
                previousScanId = null,
                changes = DependencyChanges(emptyList(), emptyList(), emptyList()),
                unchangedCount = 0,
                carryOverAvailable = false
            )

        // Find previous curated scan
        val previousCuratedScan = curatedScanRepository.findLatestForProject(projectId)
            ?: return IncrementalCurationResponse(
                previousScanId = null,
                changes = DependencyChanges(emptyList(), emptyList(), emptyList()),
                unchangedCount = 0,
                carryOverAvailable = false
            )

        // Get current and previous scan results
        val currentResult = scanEntity.result?.let { json.decodeFromString<ScanResult>(it) }
            ?: throw BadRequestException("Current scan has no result")

        val previousScanEntity = scanRepository.findById(previousCuratedScan.scanId)
        val previousResult = previousScanEntity?.result?.let { json.decodeFromString<ScanResult>(it) }

        if (previousResult == null) {
            return IncrementalCurationResponse(
                previousScanId = previousCuratedScan.scanId.toString(),
                changes = DependencyChanges(
                    added = currentResult.dependencies.map { it.toChange("ADDED") },
                    updated = emptyList(),
                    removed = emptyList()
                ),
                unchangedCount = 0,
                carryOverAvailable = false
            )
        }

        // Get previous curations for context
        val previousCurations = previousCuratedScan.sessionId?.let {
            curationRepository.findBySessionId(it)
        } ?: emptyList()
        val previousCurationMap = previousCurations.associateBy { it.dependencyId }

        // Calculate diff
        val currentDeps = currentResult.dependencies.associateBy { it.id }
        val previousDeps = previousResult.dependencies.associateBy { it.id }

        val added = mutableListOf<DependencyChange>()
        val updated = mutableListOf<DependencyChange>()
        val removed = mutableListOf<DependencyChange>()
        var unchangedCount = 0

        // Find added and updated
        currentDeps.forEach { (id, current) ->
            val previous = previousDeps[id]
            val previousCuration = previousCurationMap[id]

            when {
                previous == null -> {
                    added.add(current.toChange("ADDED"))
                }
                hasChanged(current, previous) -> {
                    updated.add(DependencyChange(
                        dependencyId = id,
                        dependencyName = current.name,
                        changeType = "UPDATED",
                        previousVersion = previous.version,
                        currentVersion = current.version,
                        previousLicense = previous.concludedLicense,
                        currentLicense = current.concludedLicense,
                        previousCuration = previousCuration?.toCurationSummary()
                    ))
                }
                else -> unchangedCount++
            }
        }

        // Find removed
        previousDeps.forEach { (id, previous) ->
            if (!currentDeps.containsKey(id)) {
                val previousCuration = previousCurationMap[id]
                removed.add(DependencyChange(
                    dependencyId = id,
                    dependencyName = previous.name,
                    changeType = "REMOVED",
                    previousVersion = previous.version,
                    currentVersion = null,
                    previousLicense = previous.concludedLicense,
                    currentLicense = null,
                    previousCuration = previousCuration?.toCurationSummary()
                ))
            }
        }

        return IncrementalCurationResponse(
            previousScanId = previousCuratedScan.scanId.toString(),
            changes = DependencyChanges(added, updated, removed),
            unchangedCount = unchangedCount,
            carryOverAvailable = unchangedCount > 0 && previousCurations.isNotEmpty()
        )
    }

    /**
     * Apply previous curation decisions to unchanged dependencies
     */
    fun applyPreviousCurations(
        scanId: String,
        request: ApplyPreviousCurationsRequest,
        curatorId: String? = null
    ): BulkCurationResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        val incremental = getIncrementalChanges(scanId)
        if (!incremental.carryOverAvailable) {
            throw BadRequestException("No previous curations available to apply")
        }

        val previousScanId = UUID.fromString(incremental.previousScanId!!)
        val previousCuratedScan = curatedScanRepository.findByScanId(previousScanId)
        val previousCurations = previousCuratedScan?.sessionId?.let {
            curationRepository.findBySessionId(it)
        } ?: emptyList()

        // Get current scan dependencies
        val scanEntity = scanRepository.findById(scanUuid)
        val currentResult = scanEntity?.result?.let { json.decodeFromString<ScanResult>(it) }
        val currentDepIds = currentResult?.dependencies?.map { it.id }?.toSet() ?: emptySet()

        // Filter to unchanged dependencies (not in added/updated)
        val changedIds = (incremental.changes.added + incremental.changes.updated)
            .map { it.dependencyId }.toSet()

        val unchangedPreviousCurations = previousCurations.filter { curation ->
            curation.dependencyId in currentDepIds &&
            curation.dependencyId !in changedIds &&
            curation.status != CurationStatus.PENDING.value
        }

        // Filter by specific IDs if provided
        val curationsToApply = if (request.dependencyIds != null) {
            unchangedPreviousCurations.filter { it.dependencyId in request.dependencyIds }
        } else {
            unchangedPreviousCurations
        }

        var succeeded = 0
        var failed = 0
        val errors = mutableListOf<BulkCurationError>()

        curationsToApply.forEach { previousCuration ->
            try {
                val currentCuration = curationRepository.findByDependencyId(
                    session.id, previousCuration.dependencyId
                )

                if (currentCuration != null && currentCuration.status == CurationStatus.PENDING.value) {
                    curationRepository.updateDecision(
                        id = currentCuration.id,
                        status = CurationStatus.fromString(previousCuration.status),
                        curatedLicense = previousCuration.curatedLicense,
                        curatorComment = "Carried over from previous curation: ${previousCuration.curatorComment ?: ""}".trim(),
                        curatorId = curatorId ?: "system"
                    )
                    succeeded++
                }
            } catch (e: Exception) {
                failed++
                errors.add(BulkCurationError(previousCuration.dependencyId, e.message ?: "Unknown error"))
            }
        }

        // Update session statistics
        curationSessionRepository.recalculateStatistics(session.id, curationRepository)

        logger.info { "Applied previous curations: $succeeded succeeded, $failed failed" }

        return BulkCurationResponse(
            processed = curationsToApply.size,
            succeeded = succeeded,
            failed = failed,
            errors = errors
        )
    }

    // ========================================================================
    // Private Helpers
    // ========================================================================

    private fun calculatePriority(dep: Dependency): PriorityResult {
        val factors = mutableListOf<PriorityFactorData>()
        var score = 0.5 // Base score

        // Factor 1: AI confidence
        when (dep.aiSuggestion?.confidence) {
            "LOW" -> {
                score += 0.3
                factors.add(PriorityFactorData("ai_confidence", 0.3, "Low AI confidence requires review"))
            }
            "MEDIUM" -> {
                score += 0.15
                factors.add(PriorityFactorData("ai_confidence", 0.15, "Medium AI confidence"))
            }
            "HIGH" -> {
                score -= 0.2
                factors.add(PriorityFactorData("ai_confidence", -0.2, "High AI confidence"))
            }
            null -> {
                score += 0.2
                factors.add(PriorityFactorData("no_ai_suggestion", 0.2, "No AI suggestion available"))
            }
        }

        // Factor 2: License category (copyleft needs more attention)
        val suggestedLicense = dep.aiSuggestion?.suggestedLicense ?: dep.concludedLicense
        if (suggestedLicense != null) {
            val isCopyleft = listOf("GPL", "AGPL", "LGPL", "MPL", "EPL", "CDDL")
                .any { suggestedLicense.contains(it, ignoreCase = true) }
            if (isCopyleft) {
                score += 0.2
                factors.add(PriorityFactorData("copyleft_license", 0.2, "Copyleft license requires attention"))
            }
        }

        // Factor 3: Unresolved original license
        if (!dep.isResolved && dep.aiSuggestion == null) {
            score += 0.25
            factors.add(PriorityFactorData("unresolved", 0.25, "License could not be resolved"))
        }

        // Factor 4: Runtime scope (more critical than dev)
        if (dep.scope.lowercase() in listOf("runtime", "compile", "implementation")) {
            score += 0.1
            factors.add(PriorityFactorData("runtime_scope", 0.1, "Runtime dependency"))
        }

        // Normalize score to 0-1
        score = score.coerceIn(0.0, 1.0)

        // Determine level
        val level = when {
            score >= 0.75 -> PriorityLevel.CRITICAL
            score >= 0.5 -> PriorityLevel.HIGH
            score >= 0.25 -> PriorityLevel.MEDIUM
            else -> PriorityLevel.LOW
        }

        return PriorityResult(level, score, factors)
    }

    private fun calculateDependencyHash(curations: List<CurationEntity>): String {
        val sortedIds = curations.map { "${it.dependencyId}:${it.dependencyVersion}" }.sorted()
        val content = sortedIds.joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(content.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun hasChanged(current: Dependency, previous: Dependency): Boolean {
        return current.version != previous.version ||
               current.concludedLicense != previous.concludedLicense ||
               current.declaredLicenses != previous.declaredLicenses
    }

    private fun Dependency.toChange(changeType: String) = DependencyChange(
        dependencyId = id,
        dependencyName = name,
        changeType = changeType,
        previousVersion = null,
        currentVersion = version,
        previousLicense = null,
        currentLicense = concludedLicense,
        previousCuration = null
    )

    private fun CurationEntity.toCurationSummary() = CurationDecisionSummary(
        status = status,
        originalLicense = originalLicense,
        curatedLicense = curatedLicense,
        comment = curatorComment,
        curatedBy = curatorId,
        curatedAt = curatedAt
    )

    private fun CurationSessionEntity.toResponse() = CurationSessionResponse(
        id = id.toString(),
        scanId = scanId.toString(),
        status = status,
        statistics = CurationStatistics(
            total = totalItems,
            pending = pendingItems,
            accepted = acceptedItems,
            rejected = rejectedItems,
            modified = modifiedItems
        ),
        approval = if (approvedBy != null && approvedAt != null) {
            CurationApproval(approvedBy, approvedAt, approvalComment)
        } else null,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun CurationEntity.toItemResponse(): CurationItemResponse {
        val declared = declaredLicenses?.let {
            try { json.decodeFromString<List<String>>(it) } catch (e: Exception) { emptyList() }
        } ?: emptyList()

        val detected = detectedLicenses?.let {
            try { json.decodeFromString<List<String>>(it) } catch (e: Exception) { emptyList() }
        } ?: emptyList()

        val alternatives = aiAlternatives?.let {
            try { json.decodeFromString<List<String>>(it) } catch (e: Exception) { emptyList() }
        } ?: emptyList()

        val priorityFactors = priorityFactors?.let {
            try { json.decodeFromString<List<PriorityFactor>>(it) } catch (e: Exception) { emptyList() }
        } ?: emptyList()

        return CurationItemResponse(
            id = id.toString(),
            dependencyId = dependencyId,
            dependencyName = dependencyName,
            dependencyVersion = dependencyVersion,
            scope = dependencyScope,
            declaredLicenses = declared,
            detectedLicenses = detected,
            originalConcludedLicense = originalLicense,
            aiSuggestion = if (aiSuggestedLicense != null) {
                AiSuggestionDetail(
                    suggestedLicense = aiSuggestedLicense,
                    confidence = aiConfidence ?: "UNKNOWN",
                    reasoning = aiReasoning ?: "",
                    spdxId = aiSuggestedLicense, // Usually the same
                    alternatives = alternatives
                )
            } else null,
            status = status,
            curatedLicense = curatedLicense,
            curatorComment = curatorComment,
            curatorId = curatorId,
            curatedAt = curatedAt,
            priority = if (priorityLevel != null && priorityScore != null) {
                PriorityInfo(priorityLevel, priorityScore, priorityFactors)
            } else null,
            spdxValidated = spdxValidated,
            spdxLicense = null // TODO: Add SPDX data if validated
        )
    }
}

// Helper data classes
private data class PriorityResult(
    val level: PriorityLevel,
    val score: Double,
    val factors: List<PriorityFactorData>
)

@kotlinx.serialization.Serializable
private data class PriorityFactorData(
    val name: String,
    val weight: Double,
    val description: String
)
