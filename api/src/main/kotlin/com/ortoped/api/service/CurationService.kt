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

    // ========================================================================
    // EU Compliance Methods (Phase 10)
    // ========================================================================

    /**
     * Submit a curation decision with structured justification (EU compliance)
     */
    fun submitDecisionWithJustification(
        scanId: String,
        dependencyId: String,
        request: CurationWithJustificationRequest,
        curatorId: String,
        curatorName: String? = null,
        curatorEmail: String? = null
    ): EnhancedCurationItemResponse {
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

        // Check if justification is required
        val requiresJustification = checkJustificationRequired(license, curation)
        if (requiresJustification && request.justification == null) {
            throw BadRequestException("Justification required for non-permissive license: $license")
        }

        // Log previous state for audit
        val previousState = json.encodeToString(mapOf(
            "status" to curation.status,
            "curatedLicense" to curation.curatedLicense,
            "curatorId" to curation.curatorId
        ))

        // Update the curation with EU compliance fields
        curationRepository.updateDecisionWithCompliance(
            id = curation.id,
            status = status,
            curatedLicense = license,
            curatorComment = request.comment,
            curatorId = curatorId,
            requiresJustification = requiresJustification,
            justificationComplete = request.justification != null,
            distributionScope = request.justification?.distributionScope ?: "BINARY"
        )

        // Save justification if provided
        request.justification?.let { justification ->
            saveJustification(
                curationId = curation.id,
                justification = justification,
                curatorId = curatorId,
                curatorName = curatorName,
                curatorEmail = curatorEmail
            )
        }

        // Log to audit
        logAuditEvent(
            entityType = "CURATION",
            entityId = curation.id,
            action = "DECIDE",
            actorId = curatorId,
            actorRole = "CURATOR",
            previousState = previousState,
            newState = json.encodeToString(mapOf(
                "status" to status.value,
                "curatedLicense" to license,
                "curatorId" to curatorId,
                "hasJustification" to (request.justification != null)
            )),
            changeSummary = "Decision: $status with license $license"
        )

        // Update session statistics
        curationSessionRepository.recalculateStatistics(session.id, curationRepository)

        // Check if all items are now curated
        val stats = curationRepository.getStatisticsBySessionId(session.id)
        if (stats.pending == 0) {
            curationSessionRepository.updateStatus(session.id, CurationSessionStatus.COMPLETED)
        }

        logger.info { "EU Curation decision for $dependencyId: $status -> $license (justification: ${request.justification != null})" }

        return getEnhancedCurationItem(scanId, dependencyId)
    }

    /**
     * Submit structured justification for an existing curation
     */
    fun submitJustification(
        scanId: String,
        dependencyId: String,
        request: JustificationRequest,
        curatorId: String,
        curatorName: String? = null,
        curatorEmail: String? = null
    ): JustificationResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        val curation = curationRepository.findByDependencyId(session.id, dependencyId)
            ?: throw NotFoundException("Curation item not found: $dependencyId")

        if (curation.status == CurationStatus.PENDING.value) {
            throw BadRequestException("Cannot justify pending curation - submit decision first")
        }

        val justification = saveJustification(
            curationId = curation.id,
            justification = request,
            curatorId = curatorId,
            curatorName = curatorName,
            curatorEmail = curatorEmail
        )

        // Update curation to mark justification complete
        curationRepository.updateJustificationStatus(curation.id, true)

        logger.info { "Justification submitted for $dependencyId by $curatorId" }

        return justification
    }

    /**
     * Validate session readiness for approval
     */
    fun validateForApproval(scanId: String): ApprovalReadinessResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        val stats = curationRepository.getStatisticsBySessionId(session.id)
        val blockers = mutableListOf<ApprovalBlockerInfo>()

        // Check pending items
        if (stats.pending > 0) {
            blockers.add(ApprovalBlockerInfo(
                type = "PENDING_ITEMS",
                count = stats.pending,
                message = "${stats.pending} items still pending review"
            ))
        }

        // Check unresolved OR licenses
        val unresolvedOrLicenses = curationRepository.countUnresolvedOrLicenses(session.id)
        if (unresolvedOrLicenses > 0) {
            blockers.add(ApprovalBlockerInfo(
                type = "UNRESOLVED_OR",
                count = unresolvedOrLicenses,
                message = "$unresolvedOrLicenses OR licenses require explicit resolution"
            ))
        }

        // Check missing justifications
        val pendingJustifications = curationRepository.countPendingJustifications(session.id)
        if (pendingJustifications > 0) {
            blockers.add(ApprovalBlockerInfo(
                type = "MISSING_JUSTIFICATION",
                count = pendingJustifications,
                message = "$pendingJustifications items require structured justification"
            ))
        }

        return ApprovalReadinessResponse(
            isReady = blockers.isEmpty(),
            totalItems = stats.total,
            pendingItems = stats.pending,
            unresolvedOrLicenses = unresolvedOrLicenses,
            pendingJustifications = pendingJustifications,
            blockers = blockers
        )
    }

    /**
     * Submit session for approval (curator action)
     */
    fun submitForApproval(
        scanId: String,
        request: SubmitForApprovalRequest,
        submitterId: String,
        submitterName: String? = null
    ): ApprovalStatusResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        // Validate readiness
        val readiness = validateForApproval(scanId)
        if (!readiness.isReady) {
            throw BadRequestException("Session not ready for approval: ${readiness.blockers.map { it.message }.joinToString(", ")}")
        }

        // Create approval record
        curationRepository.createApprovalRecord(
            sessionId = session.id,
            submitterId = submitterId,
            submitterName = submitterName
        )

        // Update session
        curationSessionRepository.markSubmittedForApproval(session.id, submitterId)

        // Log to audit
        logAuditEvent(
            entityType = "SESSION",
            entityId = session.id,
            action = "SUBMIT",
            actorId = submitterId,
            actorRole = "CURATOR",
            previousState = null,
            newState = json.encodeToString(mapOf(
                "submittedForApproval" to true,
                "submitterId" to submitterId
            )),
            changeSummary = "Session submitted for approval by $submitterId"
        )

        logger.info { "Session $scanId submitted for approval by $submitterId" }

        return getApprovalStatus(scanId)
    }

    /**
     * Approve or reject session (approver action - must be different from curator)
     */
    fun decideApproval(
        scanId: String,
        request: ApprovalDecisionRequest,
        approverId: String,
        approverName: String? = null,
        approverRole: String? = null
    ): ApprovalStatusResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        if (!session.submittedForApproval) {
            throw BadRequestException("Session has not been submitted for approval")
        }

        // Get approval record to check submitter
        val approvalRecord = curationRepository.getApprovalRecord(session.id)
            ?: throw BadRequestException("No approval record found")

        // EU REQUIREMENT: Approver must be different from curator (four-eyes principle)
        if (approvalRecord.submitterId == approverId) {
            throw BadRequestException("EU Compliance Violation: Approver must be different from submitter (four-eyes principle)")
        }

        // Update approval record
        curationRepository.updateApprovalDecision(
            sessionId = session.id,
            approverId = approverId,
            approverName = approverName,
            approverRole = approverRole,
            decision = request.decision,
            comment = request.comment,
            returnReason = request.returnReason,
            revisionItems = request.revisionItems?.let { json.encodeToString(it) }
        )

        // Update session status based on decision
        when (request.decision.uppercase()) {
            "APPROVED" -> {
                curationSessionRepository.approve(session.id, approverId, request.comment)
                // Mark scan as curation complete
                scanRepository.markCurationComplete(scanUuid)
            }
            "REJECTED", "RETURNED" -> {
                curationSessionRepository.resetSubmission(session.id)
            }
        }

        // Log to audit
        logAuditEvent(
            entityType = "APPROVAL",
            entityId = session.id,
            action = request.decision.uppercase(),
            actorId = approverId,
            actorRole = "APPROVER",
            previousState = null,
            newState = json.encodeToString(mapOf(
                "decision" to request.decision,
                "approverId" to approverId,
                "comment" to request.comment
            )),
            changeSummary = "Approval decision: ${request.decision} by $approverId"
        )

        logger.info { "Session $scanId ${request.decision} by $approverId" }

        return getApprovalStatus(scanId)
    }

    /**
     * Get approval status for a session
     */
    fun getApprovalStatus(scanId: String): ApprovalStatusResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        val approvalRecord = curationRepository.getApprovalRecord(session.id)
        val readiness = validateForApproval(scanId)

        return ApprovalStatusResponse(
            sessionId = session.id.toString(),
            isSubmittedForApproval = session.submittedForApproval,
            submittedBy = session.submittedBy,
            submittedAt = session.submittedAt,
            approval = approvalRecord?.toResponse(),
            readiness = readiness
        )
    }

    // ========================================================================
    // OR License Methods
    // ========================================================================

    /**
     * Detect and flag OR licenses in a session
     */
    fun detectOrLicenses(scanId: String) {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        val items = curationRepository.findBySessionId(session.id)
        items.forEach { item ->
            val license = item.aiSuggestedLicense ?: item.originalLicense ?: return@forEach
            if (license.contains(" OR ", ignoreCase = true)) {
                val options = parseOrLicenseOptions(license)
                curationRepository.flagAsOrLicense(item.id, options)
                curationRepository.createOrLicenseResolution(item.id, license, options)
            }
        }
    }

    /**
     * Get unresolved OR licenses
     */
    fun getOrLicenses(scanId: String): OrLicensesResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        val orLicenses = curationRepository.findOrLicenses(session.id)
        val resolved = orLicenses.count { it.orLicenseChoice != null }

        return OrLicensesResponse(
            orLicenses = orLicenses.map { it.toOrLicenseItemResponse() },
            total = orLicenses.size,
            resolved = resolved,
            unresolved = orLicenses.size - resolved
        )
    }

    /**
     * Resolve an OR license with explicit choice
     */
    fun resolveOrLicense(
        scanId: String,
        dependencyId: String,
        request: ResolveOrLicenseRequest,
        curatorId: String
    ): OrLicenseItemResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        val curation = curationRepository.findByDependencyId(session.id, dependencyId)
            ?: throw NotFoundException("Curation item not found: $dependencyId")

        if (!curation.isOrLicense) {
            throw BadRequestException("This is not an OR license")
        }

        // Validate chosen license is one of the options
        val options = curation.orLicenseOptions?.let {
            try { json.decodeFromString<List<String>>(it) } catch (e: Exception) { emptyList() }
        } ?: emptyList()

        if (request.chosenLicense !in options) {
            throw BadRequestException("Chosen license '${request.chosenLicense}' is not one of the available options: $options")
        }

        // Update curation with chosen license
        curationRepository.resolveOrLicense(curation.id, request.chosenLicense, curatorId)

        // Update OR license resolution record
        curationRepository.updateOrLicenseResolution(
            curationId = curation.id,
            chosenLicense = request.chosenLicense,
            choiceReason = request.choiceReason,
            resolvedBy = curatorId
        )

        // Log to audit
        logAuditEvent(
            entityType = "OR_LICENSE",
            entityId = curation.id,
            action = "RESOLVE_OR",
            actorId = curatorId,
            actorRole = "CURATOR",
            previousState = null,
            newState = json.encodeToString(mapOf(
                "chosenLicense" to request.chosenLicense,
                "reason" to request.choiceReason
            )),
            changeSummary = "OR license resolved: chose ${request.chosenLicense}"
        )

        logger.info { "OR license resolved for $dependencyId: ${request.chosenLicense}" }

        return curationRepository.findByDependencyId(session.id, dependencyId)?.toOrLicenseItemResponse()
            ?: throw InternalException("Failed to retrieve updated curation")
    }

    // ========================================================================
    // Enhanced Item Retrieval
    // ========================================================================

    /**
     * Get enhanced curation item with EU compliance fields
     */
    fun getEnhancedCurationItem(scanId: String, dependencyId: String): EnhancedCurationItemResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        val curation = curationRepository.findByDependencyId(session.id, dependencyId)
            ?: throw NotFoundException("Curation item not found: $dependencyId")

        val justification = curationRepository.getJustification(curation.id)

        return curation.toEnhancedItemResponse(justification)
    }

    /**
     * Get explanations for a curation item ("Why Not?" + obligations + compatibility)
     */
    fun getExplanationsForCuration(scanId: String, dependencyId: String): CurationExplanationsResponse {
        // This integrates with ExplanationGenerator from core module
        // For now, return placeholder - will be filled in when integrating with knowledge graph
        return CurationExplanationsResponse(
            dependencyId = dependencyId,
            whyNotExplanations = emptyList(),
            triggeredObligations = emptyList(),
            compatibilityIssues = emptyList(),
            resolutions = emptyList()
        )
    }

    // ========================================================================
    // Audit Log Methods
    // ========================================================================

    /**
     * Get audit logs for an entity
     */
    fun getAuditLogs(
        entityType: String? = null,
        entityId: String? = null,
        actorId: String? = null,
        action: String? = null,
        page: Int = 1,
        pageSize: Int = 50
    ): AuditLogListResponse {
        val offset = ((page - 1) * pageSize).toLong()
        val entityUuid = entityId?.let { UUID.fromString(it) }

        val entries = curationRepository.getAuditLogs(
            entityType = entityType,
            entityId = entityUuid,
            actorId = actorId,
            action = action,
            limit = pageSize,
            offset = offset
        )

        val total = curationRepository.countAuditLogs(entityType, entityUuid, actorId, action)

        return AuditLogListResponse(
            entries = entries.map { it.toAuditLogResponse() },
            total = total.toInt(),
            page = page,
            pageSize = pageSize
        )
    }

    // ========================================================================
    // Export Methods
    // ========================================================================

    /**
     * Export curations as ORT-compatible YAML
     */
    fun exportCurationsYaml(scanId: String): CurationsYamlResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        val curations = curationRepository.findBySessionId(session.id)
            .filter { it.status != CurationStatus.PENDING.value }

        val yamlBuilder = StringBuilder()
        yamlBuilder.appendLine("# OrtoPed Curations Export")
        yamlBuilder.appendLine("# Generated: ${java.time.Instant.now()}")
        yamlBuilder.appendLine("# Scan ID: $scanId")
        yamlBuilder.appendLine("# Session ID: ${session.id}")
        yamlBuilder.appendLine("")
        yamlBuilder.appendLine("curations:")

        curations.forEach { curation ->
            val justification = curationRepository.getJustification(curation.id)
            yamlBuilder.appendLine("  - id: \"${curation.dependencyName}:${curation.dependencyVersion}\"")
            yamlBuilder.appendLine("    curations:")
            yamlBuilder.appendLine("      concluded_license:")
            yamlBuilder.appendLine("        license: \"${curation.curatedLicense}\"")
            yamlBuilder.appendLine("        comment: |")
            yamlBuilder.appendLine("          ${curation.curatorComment ?: "No comment"}")
            yamlBuilder.appendLine("          Curator: ${curation.curatorId}")
            yamlBuilder.appendLine("          Date: ${curation.curatedAt}")
            if (justification != null) {
                yamlBuilder.appendLine("          Justification Type: ${justification.justificationType}")
                yamlBuilder.appendLine("          Distribution Scope: ${justification.distributionScope}")
            }
        }

        return CurationsYamlResponse(
            yaml = yamlBuilder.toString(),
            filename = "curations-$scanId.yml"
        )
    }

    /**
     * Export NOTICE file
     */
    fun exportNoticeFile(scanId: String): NoticeFileResponse {
        val scanUuid = UUID.fromString(scanId)
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        val curations = curationRepository.findBySessionId(session.id)
            .filter { it.status != CurationStatus.PENDING.value && it.curatedLicense != null }

        val noticeBuilder = StringBuilder()
        noticeBuilder.appendLine("THIRD-PARTY SOFTWARE NOTICES AND INFORMATION")
        noticeBuilder.appendLine("============================================")
        noticeBuilder.appendLine("")
        noticeBuilder.appendLine("This software includes the following third-party components:")
        noticeBuilder.appendLine("")

        // Group by license
        val byLicense = curations.groupBy { it.curatedLicense ?: "Unknown" }
        byLicense.entries.sortedBy { it.key }.forEach { (license, deps) ->
            noticeBuilder.appendLine("$license")
            noticeBuilder.appendLine("-".repeat(license.length))
            deps.forEach { dep ->
                noticeBuilder.appendLine("  - ${dep.dependencyName} (${dep.dependencyVersion})")
            }
            noticeBuilder.appendLine("")
        }

        noticeBuilder.appendLine("============================================")
        noticeBuilder.appendLine("Generated by OrtoPed")
        noticeBuilder.appendLine("Date: ${java.time.Instant.now()}")

        return NoticeFileResponse(
            content = noticeBuilder.toString(),
            filename = "NOTICE-$scanId.txt"
        )
    }

    // ========================================================================
    // Private Helper Methods
    // ========================================================================

    private fun checkJustificationRequired(license: String, curation: CurationEntity): Boolean {
        val nonPermissivePrefixes = listOf(
            "GPL", "AGPL", "LGPL", "MPL", "EPL", "CDDL",
            "CC-BY-SA", "CC-BY-NC", "Proprietary", "Commercial"
        )
        return nonPermissivePrefixes.any { license.contains(it, ignoreCase = true) } ||
               license.contains("Unknown", ignoreCase = true) ||
               license.contains("NOASSERTION", ignoreCase = true)
    }

    private fun saveJustification(
        curationId: UUID,
        justification: JustificationRequest,
        curatorId: String,
        curatorName: String?,
        curatorEmail: String?
    ): JustificationResponse {
        return curationRepository.saveJustification(
            curationId = curationId,
            spdxId = justification.spdxId,
            licenseCategory = justification.licenseCategory,
            concludedLicense = justification.concludedLicense,
            justificationType = justification.justificationType,
            justificationText = justification.justificationText,
            policyRuleId = justification.policyRuleId,
            policyRuleName = justification.policyRuleName,
            evidenceType = justification.evidenceType,
            evidenceReference = justification.evidenceReference,
            distributionScope = justification.distributionScope,
            curatorId = curatorId,
            curatorName = curatorName,
            curatorEmail = curatorEmail
        )
    }

    private fun logAuditEvent(
        entityType: String,
        entityId: UUID,
        action: String,
        actorId: String,
        actorRole: String,
        previousState: String?,
        newState: String,
        changeSummary: String
    ) {
        curationRepository.logAuditEvent(
            entityType = entityType,
            entityId = entityId,
            action = action,
            actorId = actorId,
            actorRole = actorRole,
            previousState = previousState,
            newState = newState,
            changeSummary = changeSummary
        )
    }

    private fun parseOrLicenseOptions(expression: String): List<String> {
        return expression.split(Regex("\\s+OR\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun CurationEntity.toOrLicenseItemResponse(): OrLicenseItemResponse {
        val options = orLicenseOptions?.let {
            try { json.decodeFromString<List<String>>(it) } catch (e: Exception) { emptyList() }
        } ?: emptyList()

        return OrLicenseItemResponse(
            curationId = id.toString(),
            dependencyId = dependencyId,
            dependencyName = dependencyName,
            dependencyVersion = dependencyVersion,
            originalExpression = aiSuggestedLicense ?: originalLicense ?: "",
            options = options.map { license ->
                OrLicenseOptionResponse(
                    license = license,
                    category = determineLicenseCategory(license),
                    isOsiApproved = isOsiApproved(license),
                    isFsfLibre = isFsfLibre(license),
                    policyCompliant = isPolicyCompliant(license),
                    recommendation = getRecommendation(license)
                )
            },
            isResolved = orLicenseChoice != null,
            chosenLicense = orLicenseChoice,
            choiceReason = curatorComment,
            resolvedBy = curatorId,
            resolvedAt = curatedAt
        )
    }

    private fun CurationEntity.toEnhancedItemResponse(justification: JustificationResponse?): EnhancedCurationItemResponse {
        val declared = declaredLicenses?.let {
            try { json.decodeFromString<List<String>>(it) } catch (e: Exception) { emptyList() }
        } ?: emptyList()

        val detected = detectedLicenses?.let {
            try { json.decodeFromString<List<String>>(it) } catch (e: Exception) { emptyList() }
        } ?: emptyList()

        val alternatives = aiAlternatives?.let {
            try { json.decodeFromString<List<String>>(it) } catch (e: Exception) { emptyList() }
        } ?: emptyList()

        val priorityFactorsList = priorityFactors?.let {
            try { json.decodeFromString<List<PriorityFactor>>(it) } catch (e: Exception) { emptyList() }
        } ?: emptyList()

        val orOptions = orLicenseOptions?.let {
            try { json.decodeFromString<List<String>>(it) } catch (e: Exception) { null }
        }

        return EnhancedCurationItemResponse(
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
                    spdxId = aiSuggestedLicense,
                    alternatives = alternatives
                )
            } else null,
            status = status,
            curatedLicense = curatedLicense,
            curatorComment = curatorComment,
            curatorId = curatorId,
            curatedAt = curatedAt,
            priority = if (priorityLevel != null && priorityScore != null) {
                PriorityInfo(priorityLevel, priorityScore, priorityFactorsList)
            } else null,
            spdxValidated = spdxValidated,
            spdxLicense = null,
            requiresJustification = requiresJustification,
            justificationComplete = justificationComplete,
            justification = justification,
            blockingPolicyRule = blockingPolicyRule?.let {
                PolicyRuleReference(it, it, "ERROR")
            },
            isOrLicense = isOrLicense,
            orLicenseOptions = orOptions,
            orLicenseChoice = orLicenseChoice,
            distributionScope = distributionScope ?: "BINARY"
        )
    }

    private fun ApprovalRecordEntity.toResponse() = ApprovalRecordResponse(
        id = id.toString(),
        submitterId = submitterId,
        submitterName = submitterName,
        submittedAt = submittedAt,
        approverId = approverId,
        approverName = approverName,
        approverRole = approverRole,
        decision = decision,
        decisionComment = decisionComment,
        decidedAt = decidedAt,
        returnReason = returnReason,
        revisionItems = revisionItems?.let {
            try { json.decodeFromString<List<String>>(it) } catch (e: Exception) { null }
        }
    )

    private fun AuditLogEntity.toAuditLogResponse() = AuditLogEntryResponse(
        id = id.toString(),
        entityType = entityType,
        entityId = entityId.toString(),
        action = action,
        actorId = actorId,
        actorRole = actorRole,
        changeSummary = changeSummary,
        previousState = previousState,
        newState = newState,
        createdAt = createdAt
    )

    // License category helpers
    private fun determineLicenseCategory(license: String): String {
        return when {
            listOf("MIT", "BSD", "Apache", "ISC", "Unlicense", "WTFPL").any { license.contains(it, ignoreCase = true) } -> "PERMISSIVE"
            listOf("LGPL", "MPL", "EPL", "CDDL").any { license.contains(it, ignoreCase = true) } -> "WEAK_COPYLEFT"
            license.contains("AGPL", ignoreCase = true) -> "NETWORK_COPYLEFT"
            license.contains("GPL", ignoreCase = true) -> "STRONG_COPYLEFT"
            listOf("CC0", "Public Domain").any { license.contains(it, ignoreCase = true) } -> "PUBLIC_DOMAIN"
            listOf("Proprietary", "Commercial").any { license.contains(it, ignoreCase = true) } -> "PROPRIETARY"
            else -> "UNKNOWN"
        }
    }

    private fun isOsiApproved(license: String): Boolean {
        val osiApproved = listOf(
            "MIT", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause", "ISC",
            "GPL-2.0", "GPL-3.0", "LGPL-2.1", "LGPL-3.0", "AGPL-3.0",
            "MPL-2.0", "EPL-1.0", "EPL-2.0"
        )
        return osiApproved.any { license.contains(it, ignoreCase = true) }
    }

    private fun isFsfLibre(license: String): Boolean {
        val fsfLibre = listOf(
            "MIT", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause",
            "GPL-2.0", "GPL-3.0", "LGPL-2.1", "LGPL-3.0", "AGPL-3.0"
        )
        return fsfLibre.any { license.contains(it, ignoreCase = true) }
    }

    private fun isPolicyCompliant(license: String): Boolean {
        val category = determineLicenseCategory(license)
        return category in listOf("PERMISSIVE", "PUBLIC_DOMAIN", "WEAK_COPYLEFT")
    }

    private fun getRecommendation(license: String): String? {
        val category = determineLicenseCategory(license)
        return when (category) {
            "PERMISSIVE", "PUBLIC_DOMAIN" -> "Recommended"
            "WEAK_COPYLEFT" -> null // Neutral
            else -> "Not Recommended"
        }
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

// Entity helper classes for EU compliance
data class ApprovalRecordEntity(
    val id: UUID,
    val sessionId: UUID,
    val submitterId: String,
    val submitterName: String?,
    val submittedAt: String,
    val approverId: String?,
    val approverName: String?,
    val approverRole: String?,
    val decision: String?,
    val decisionComment: String?,
    val decidedAt: String?,
    val returnReason: String?,
    val revisionItems: String?
)

data class AuditLogEntity(
    val id: UUID,
    val entityType: String,
    val entityId: UUID,
    val action: String,
    val actorId: String,
    val actorRole: String,
    val previousState: String?,
    val newState: String,
    val changeSummary: String,
    val createdAt: String
)
