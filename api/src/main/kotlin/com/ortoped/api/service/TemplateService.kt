package com.ortoped.api.service

import com.ortoped.api.model.*
import com.ortoped.api.plugins.BadRequestException
import com.ortoped.api.plugins.NotFoundException
import com.ortoped.api.repository.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Service for managing and applying curation templates
 */
class TemplateService(
    private val templateRepository: CurationTemplateRepository,
    private val curationRepository: CurationRepository,
    private val curationSessionRepository: CurationSessionRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ========================================================================
    // Template CRUD
    // ========================================================================

    /**
     * List all available templates
     */
    fun listTemplates(
        activeOnly: Boolean = true,
        userId: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): CurationTemplatesResponse {
        val offset = ((page - 1) * pageSize).toLong()

        val templates = if (userId != null) {
            templateRepository.findAvailableForUser(userId, activeOnly, pageSize, offset)
        } else {
            templateRepository.findAll(activeOnly, globalOnly = false, limit = pageSize, offset = offset)
        }

        return CurationTemplatesResponse(
            templates = templates.map { it.toResponse() },
            total = templates.size
        )
    }

    /**
     * Get a template by ID
     */
    fun getTemplate(templateId: String): CurationTemplateResponse {
        val uuid = UUID.fromString(templateId)
        val template = templateRepository.findById(uuid)
            ?: throw NotFoundException("Template not found: $templateId")
        return template.toResponse()
    }

    /**
     * Create a new template
     */
    fun createTemplate(request: CreateTemplateRequest, userId: String?): CurationTemplateResponse {
        // Validate conditions and actions
        validateConditions(request.conditions)
        validateActions(request.actions)

        val template = templateRepository.create(
            name = request.name,
            description = request.description,
            conditions = json.encodeToString(request.conditions),
            actions = json.encodeToString(request.actions),
            createdBy = userId,
            isGlobal = request.isGlobal
        )

        logger.info { "Created template ${template.id}: ${template.name}" }
        return template.toResponse()
    }

    /**
     * Update an existing template
     */
    fun updateTemplate(
        templateId: String,
        request: CreateTemplateRequest,
        userId: String?
    ): CurationTemplateResponse {
        val uuid = UUID.fromString(templateId)
        val existing = templateRepository.findById(uuid)
            ?: throw NotFoundException("Template not found: $templateId")

        // Check ownership for non-global templates
        if (!existing.isGlobal && existing.createdBy != userId) {
            throw BadRequestException("Cannot modify template owned by another user")
        }

        validateConditions(request.conditions)
        validateActions(request.actions)

        templateRepository.update(
            id = uuid,
            name = request.name,
            description = request.description,
            conditions = json.encodeToString(request.conditions),
            actions = json.encodeToString(request.actions),
            isGlobal = request.isGlobal
        )

        logger.info { "Updated template $templateId" }
        return templateRepository.findById(uuid)?.toResponse()
            ?: throw NotFoundException("Template not found after update")
    }

    /**
     * Delete a template
     */
    fun deleteTemplate(templateId: String, userId: String?) {
        val uuid = UUID.fromString(templateId)
        val existing = templateRepository.findById(uuid)
            ?: throw NotFoundException("Template not found: $templateId")

        // Check ownership for non-global templates
        if (!existing.isGlobal && existing.createdBy != userId) {
            throw BadRequestException("Cannot delete template owned by another user")
        }

        templateRepository.delete(uuid)
        logger.info { "Deleted template $templateId" }
    }

    // ========================================================================
    // Template Application
    // ========================================================================

    /**
     * Apply a template to a curation session
     */
    fun applyTemplate(
        scanId: String,
        request: ApplyTemplateRequest,
        userId: String?
    ): ApplyTemplateResponse {
        val scanUuid = UUID.fromString(scanId)
        val templateUuid = UUID.fromString(request.templateId)

        // Get session
        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        if (session.status == CurationSessionStatus.APPROVED.value) {
            throw BadRequestException("Cannot apply template to approved session")
        }

        // Get template
        val template = templateRepository.findById(templateUuid)
            ?: throw NotFoundException("Template not found: ${request.templateId}")

        // Parse conditions and actions
        val conditions = try {
            json.decodeFromString<List<TemplateCondition>>(template.conditions)
        } catch (e: Exception) {
            throw BadRequestException("Invalid template conditions format")
        }

        val actions = try {
            json.decodeFromString<List<TemplateAction>>(template.actions)
        } catch (e: Exception) {
            throw BadRequestException("Invalid template actions format")
        }

        // Get curation items to evaluate
        val statusFilter = request.filterStatus?.map { CurationStatus.fromString(it) }
        val items = curationRepository.findBySessionId(
            sessionId = session.id,
            status = null, // Get all, filter below
            limit = 10000, // Get all items
            offset = 0
        ).filter { item ->
            // Filter by status if specified
            if (statusFilter != null) {
                CurationStatus.fromString(item.status) in statusFilter
            } else {
                true
            }
        }

        // Match items against conditions
        val matchedItems = items.filter { item -> matchesConditions(item, conditions) }

        val affected = mutableListOf<TemplateAffectedItem>()

        if (!request.dryRun) {
            // Apply actions to matched items
            matchedItems.forEach { item ->
                val result = applyActions(item, actions, userId)
                if (result != null) {
                    affected.add(result)
                }
            }

            // Update session statistics
            curationSessionRepository.recalculateStatistics(session.id, curationRepository)

            // Increment template usage count
            templateRepository.incrementUsageCount(templateUuid)

            logger.info { "Applied template ${template.name} to ${affected.size} items in scan $scanId" }
        } else {
            // Dry run - just report what would be affected
            matchedItems.forEach { item ->
                affected.add(TemplateAffectedItem(
                    dependencyId = item.dependencyId,
                    dependencyName = item.dependencyName,
                    action = actions.firstOrNull()?.type ?: "UNKNOWN",
                    resultingStatus = predictStatus(actions),
                    resultingLicense = predictLicense(item, actions)
                ))
            }
        }

        return ApplyTemplateResponse(
            templateId = request.templateId,
            templateName = template.name,
            matchedCount = matchedItems.size,
            appliedCount = affected.size,
            dryRun = request.dryRun,
            affected = affected
        )
    }

    /**
     * Preview which items would match a set of conditions
     */
    fun previewConditions(
        scanId: String,
        conditions: List<TemplateCondition>
    ): List<CurationItemResponse> {
        val scanUuid = UUID.fromString(scanId)

        val session = curationSessionRepository.findByScanId(scanUuid)
            ?: throw NotFoundException("No curation session found for scan: $scanId")

        validateConditions(conditions)

        val items = curationRepository.findBySessionId(
            sessionId = session.id,
            limit = 10000,
            offset = 0
        )

        return items.filter { matchesConditions(it, conditions) }
            .map { it.toItemResponse() }
    }

    // ========================================================================
    // Condition Matching
    // ========================================================================

    private fun matchesConditions(item: CurationEntity, conditions: List<TemplateCondition>): Boolean {
        // All conditions must match (AND logic)
        return conditions.all { condition -> matchesCondition(item, condition) }
    }

    private fun matchesCondition(item: CurationEntity, condition: TemplateCondition): Boolean {
        return when (condition.type.uppercase()) {
            "AI_CONFIDENCE_EQUALS" -> {
                item.aiConfidence?.uppercase() == condition.value.uppercase()
            }
            "AI_CONFIDENCE_ABOVE" -> {
                val confidenceOrder = listOf("LOW", "MEDIUM", "HIGH")
                val itemLevel = confidenceOrder.indexOf(item.aiConfidence?.uppercase() ?: "")
                val targetLevel = confidenceOrder.indexOf(condition.value.uppercase())
                itemLevel > targetLevel
            }
            "AI_CONFIDENCE_BELOW" -> {
                val confidenceOrder = listOf("LOW", "MEDIUM", "HIGH")
                val itemLevel = confidenceOrder.indexOf(item.aiConfidence?.uppercase() ?: "")
                val targetLevel = confidenceOrder.indexOf(condition.value.uppercase())
                itemLevel >= 0 && itemLevel < targetLevel
            }
            "LICENSE_EQUALS" -> {
                item.aiSuggestedLicense?.equals(condition.value, ignoreCase = true) == true ||
                item.originalLicense?.equals(condition.value, ignoreCase = true) == true
            }
            "LICENSE_IN" -> {
                val licenses = parseJsonArray(condition.value)
                licenses.any { license ->
                    item.aiSuggestedLicense?.equals(license, ignoreCase = true) == true ||
                    item.originalLicense?.equals(license, ignoreCase = true) == true
                }
            }
            "LICENSE_CONTAINS" -> {
                item.aiSuggestedLicense?.contains(condition.value, ignoreCase = true) == true ||
                item.originalLicense?.contains(condition.value, ignoreCase = true) == true
            }
            "ORIGINAL_LICENSE_IN" -> {
                val licenses = parseJsonArray(condition.value)
                val original = item.originalLicense
                if (original == null || original.isBlank()) {
                    // Check if null/empty is in the list
                    licenses.any { it.isBlank() || it == "null" || it == "NOASSERTION" }
                } else {
                    licenses.any { it.equals(original, ignoreCase = true) }
                }
            }
            "DEPENDENCY_NAME_MATCHES" -> {
                Regex(condition.value, RegexOption.IGNORE_CASE).matches(item.dependencyName)
            }
            "DEPENDENCY_NAME_CONTAINS" -> {
                item.dependencyName.contains(condition.value, ignoreCase = true)
            }
            "SCOPE_EQUALS" -> {
                item.dependencyScope?.equals(condition.value, ignoreCase = true) == true
            }
            "HAS_AI_SUGGESTION" -> {
                val expected = condition.value.toBooleanStrictOrNull() ?: true
                (item.aiSuggestedLicense != null) == expected
            }
            "NO_AI_SUGGESTION" -> {
                item.aiSuggestedLicense == null
            }
            "STATUS_EQUALS" -> {
                item.status.equals(condition.value, ignoreCase = true)
            }
            "PRIORITY_EQUALS" -> {
                item.priorityLevel?.equals(condition.value, ignoreCase = true) == true
            }
            "PRIORITY_IN" -> {
                val priorities = parseJsonArray(condition.value)
                priorities.any { it.equals(item.priorityLevel, ignoreCase = true) }
            }
            else -> {
                logger.warn { "Unknown condition type: ${condition.type}" }
                false
            }
        }
    }

    // ========================================================================
    // Action Application
    // ========================================================================

    private fun applyActions(
        item: CurationEntity,
        actions: List<TemplateAction>,
        userId: String?
    ): TemplateAffectedItem? {
        var status: CurationStatus? = null
        var license: String? = null
        var comment: String? = null
        var priorityLevel: PriorityLevel? = null

        actions.forEach { action ->
            when (action.type.uppercase()) {
                "ACCEPT_AI" -> {
                    if (item.aiSuggestedLicense != null) {
                        status = CurationStatus.ACCEPTED
                        license = item.aiSuggestedLicense
                    }
                }
                "REJECT" -> {
                    status = CurationStatus.REJECTED
                    license = action.value ?: item.originalLicense
                }
                "MODIFY_LICENSE" -> {
                    status = CurationStatus.MODIFIED
                    license = action.value
                }
                "SET_PRIORITY" -> {
                    priorityLevel = PriorityLevel.fromString(action.value ?: "MEDIUM")
                }
                "ADD_COMMENT" -> {
                    comment = action.value
                }
            }
        }

        // Apply status change if any
        if (status != null) {
            curationRepository.updateDecision(
                id = item.id,
                status = status!!,
                curatedLicense = license,
                curatorComment = comment ?: "Applied by template",
                curatorId = userId ?: "system"
            )
        }

        // Apply priority change if any
        if (priorityLevel != null) {
            curationRepository.updatePriority(
                id = item.id,
                priorityLevel = priorityLevel!!,
                priorityScore = getPriorityScore(priorityLevel!!),
                priorityFactors = json.encodeToString(listOf(
                    mapOf(
                        "name" to "template_override",
                        "weight" to 1.0,
                        "description" to "Priority set by template"
                    )
                ))
            )
        }

        return if (status != null || priorityLevel != null) {
            TemplateAffectedItem(
                dependencyId = item.dependencyId,
                dependencyName = item.dependencyName,
                action = actions.map { it.type }.joinToString(", "),
                resultingStatus = status?.value,
                resultingLicense = license
            )
        } else null
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun validateConditions(conditions: List<TemplateCondition>) {
        val validTypes = setOf(
            "AI_CONFIDENCE_EQUALS", "AI_CONFIDENCE_ABOVE", "AI_CONFIDENCE_BELOW",
            "LICENSE_EQUALS", "LICENSE_IN", "LICENSE_CONTAINS", "ORIGINAL_LICENSE_IN",
            "DEPENDENCY_NAME_MATCHES", "DEPENDENCY_NAME_CONTAINS", "SCOPE_EQUALS",
            "HAS_AI_SUGGESTION", "NO_AI_SUGGESTION", "STATUS_EQUALS",
            "PRIORITY_EQUALS", "PRIORITY_IN"
        )

        conditions.forEach { condition ->
            if (condition.type.uppercase() !in validTypes) {
                throw BadRequestException("Invalid condition type: ${condition.type}")
            }
        }
    }

    private fun validateActions(actions: List<TemplateAction>) {
        val validTypes = setOf(
            "ACCEPT_AI", "REJECT", "MODIFY_LICENSE", "SET_PRIORITY", "ADD_COMMENT"
        )

        actions.forEach { action ->
            if (action.type.uppercase() !in validTypes) {
                throw BadRequestException("Invalid action type: ${action.type}")
            }

            // Validate required values
            when (action.type.uppercase()) {
                "MODIFY_LICENSE" -> {
                    if (action.value.isNullOrBlank()) {
                        throw BadRequestException("MODIFY_LICENSE action requires a license value")
                    }
                }
                "SET_PRIORITY" -> {
                    val validPriorities = setOf("CRITICAL", "HIGH", "MEDIUM", "LOW")
                    if (action.value?.uppercase() !in validPriorities) {
                        throw BadRequestException("SET_PRIORITY requires valid priority: $validPriorities")
                    }
                }
            }
        }
    }

    private fun parseJsonArray(value: String): List<String> {
        return try {
            json.decodeFromString<List<String>>(value)
        } catch (e: Exception) {
            // Try comma-separated
            value.split(",").map { it.trim() }
        }
    }

    private fun predictStatus(actions: List<TemplateAction>): String? {
        actions.forEach { action ->
            when (action.type.uppercase()) {
                "ACCEPT_AI" -> return "ACCEPTED"
                "REJECT" -> return "REJECTED"
                "MODIFY_LICENSE" -> return "MODIFIED"
            }
        }
        return null
    }

    private fun predictLicense(item: CurationEntity, actions: List<TemplateAction>): String? {
        actions.forEach { action ->
            when (action.type.uppercase()) {
                "ACCEPT_AI" -> return item.aiSuggestedLicense
                "REJECT" -> return action.value ?: item.originalLicense
                "MODIFY_LICENSE" -> return action.value
            }
        }
        return null
    }

    private fun getPriorityScore(level: PriorityLevel): Double = when (level) {
        PriorityLevel.CRITICAL -> 0.95
        PriorityLevel.HIGH -> 0.75
        PriorityLevel.MEDIUM -> 0.50
        PriorityLevel.LOW -> 0.25
    }

    private fun CurationTemplateEntity.toResponse(): CurationTemplateResponse {
        val conditions = try {
            json.decodeFromString<List<TemplateCondition>>(this.conditions)
        } catch (e: Exception) {
            emptyList()
        }

        val actions = try {
            json.decodeFromString<List<TemplateAction>>(this.actions)
        } catch (e: Exception) {
            emptyList()
        }

        return CurationTemplateResponse(
            id = id.toString(),
            name = name,
            description = description,
            conditions = conditions,
            actions = actions,
            createdBy = createdBy,
            isGlobal = isGlobal,
            isActive = isActive,
            usageCount = usageCount,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

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

        val priorityFactorsList = priorityFactors?.let {
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
            spdxLicense = null
        )
    }
}
