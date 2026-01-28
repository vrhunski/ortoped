package com.ortoped.api.model

import kotlinx.serialization.Serializable

// ============================================================================
// CURATION SESSION MODELS
// ============================================================================

/**
 * Request to start a curation session
 */
@Serializable
data class StartCurationRequest(
    val includeResolved: Boolean = false,
    val autoAcceptHighConfidence: Boolean = false,
    val applyTemplates: List<String> = emptyList() // Template IDs to auto-apply
)

/**
 * Response for curation session
 */
@Serializable
data class CurationSessionResponse(
    val id: String,
    val scanId: String,
    val status: String,
    val statistics: CurationStatistics,
    val approval: CurationApproval? = null,
    val createdAt: String,
    val updatedAt: String
)

/**
 * Curation statistics
 */
@Serializable
data class CurationStatistics(
    val total: Int,
    val pending: Int,
    val accepted: Int,
    val rejected: Int,
    val modified: Int,
    val completionPercentage: Double = if (total > 0) ((total - pending).toDouble() / total * 100) else 0.0
)

/**
 * Curation approval info
 */
@Serializable
data class CurationApproval(
    val approvedBy: String,
    val approvedAt: String,
    val comment: String? = null
)

/**
 * Request for final approval
 */
@Serializable
data class ApprovalRequest(
    val comment: String? = null
)

// ============================================================================
// CURATION ITEM MODELS
// ============================================================================

/**
 * Paginated list of curation items
 */
@Serializable
data class CurationItemsResponse(
    val items: List<CurationItemResponse>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val statistics: CurationStatistics
)

/**
 * Single curation item response
 */
@Serializable
data class CurationItemResponse(
    val id: String,
    val dependencyId: String,
    val dependencyName: String,
    val dependencyVersion: String,
    val scope: String?,

    // License info
    val declaredLicenses: List<String>,
    val detectedLicenses: List<String>,
    val originalConcludedLicense: String?,

    // AI suggestion
    val aiSuggestion: AiSuggestionDetail? = null,

    // Curation status
    val status: String,
    val curatedLicense: String? = null,
    val curatorComment: String? = null,
    val curatorId: String? = null,
    val curatedAt: String? = null,

    // Priority (Phase 9)
    val priority: PriorityInfo? = null,

    // SPDX validation (Phase 9)
    val spdxValidated: Boolean = false,
    val spdxLicense: SpdxLicenseInfo? = null
)

/**
 * Detailed AI suggestion info
 */
@Serializable
data class AiSuggestionDetail(
    val suggestedLicense: String,
    val confidence: String,
    val reasoning: String,
    val spdxId: String? = null,
    val alternatives: List<String> = emptyList()
)

/**
 * Priority information
 */
@Serializable
data class PriorityInfo(
    val level: String,
    val score: Double,
    val factors: List<PriorityFactor> = emptyList()
)

/**
 * Priority factor
 */
@Serializable
data class PriorityFactor(
    val name: String,
    val weight: Double,
    val description: String
)

// ============================================================================
// CURATION DECISION MODELS
// ============================================================================

/**
 * Request to submit a curation decision
 */
@Serializable
data class CurationDecisionRequest(
    val action: String, // ACCEPT, REJECT, MODIFY
    val license: String? = null, // Required for REJECT/MODIFY
    val comment: String? = null
)

/**
 * Bulk curation request
 */
@Serializable
data class BulkCurationRequest(
    val decisions: List<BulkCurationItem>
)

/**
 * Single item in bulk curation
 */
@Serializable
data class BulkCurationItem(
    val dependencyId: String,
    val action: String,
    val license: String? = null,
    val comment: String? = null
)

/**
 * Bulk curation response
 */
@Serializable
data class BulkCurationResponse(
    val processed: Int,
    val succeeded: Int,
    val failed: Int,
    val errors: List<BulkCurationError> = emptyList()
)

/**
 * Error in bulk curation
 */
@Serializable
data class BulkCurationError(
    val dependencyId: String,
    val error: String
)

// ============================================================================
// INCREMENTAL CURATION MODELS (Phase 9)
// ============================================================================

/**
 * Response for incremental curation
 */
@Serializable
data class IncrementalCurationResponse(
    val previousScanId: String?,
    val changes: DependencyChanges,
    val unchangedCount: Int,
    val carryOverAvailable: Boolean
)

/**
 * Dependency changes between scans
 */
@Serializable
data class DependencyChanges(
    val added: List<DependencyChange>,
    val updated: List<DependencyChange>,
    val removed: List<DependencyChange>
)

/**
 * Single dependency change
 */
@Serializable
data class DependencyChange(
    val dependencyId: String,
    val dependencyName: String,
    val changeType: String, // ADDED, UPDATED, REMOVED
    val previousVersion: String? = null,
    val currentVersion: String? = null,
    val previousLicense: String? = null,
    val currentLicense: String? = null,
    val previousCuration: CurationDecisionSummary? = null
)

/**
 * Summary of a curation decision (for reports)
 */
@Serializable
data class CurationDecisionSummary(
    val status: String,
    val originalLicense: String?,
    val curatedLicense: String?,
    val comment: String?,
    val curatedBy: String?,
    val curatedAt: String?
)

/**
 * Request to apply previous curation decisions
 */
@Serializable
data class ApplyPreviousCurationsRequest(
    val applyToUnchanged: Boolean = true,
    val dependencyIds: List<String>? = null // Specific deps, or all if null
)

// ============================================================================
// SPDX LICENSE MODELS (Phase 9)
// ============================================================================

/**
 * SPDX license information
 */
@Serializable
data class SpdxLicenseInfo(
    val licenseId: String,
    val name: String,
    val isOsiApproved: Boolean = false,
    val isFsfLibre: Boolean = false,
    val isDeprecated: Boolean = false,
    val seeAlso: List<String> = emptyList()
)

/**
 * SPDX validation result
 */
@Serializable
data class SpdxValidationResult(
    val isValid: Boolean,
    val licenseId: String?,
    val normalizedId: String?,
    val suggestions: List<String> = emptyList(),
    val message: String? = null
)

/**
 * SPDX license search response
 */
@Serializable
data class SpdxLicenseSearchResponse(
    val licenses: List<SpdxLicenseInfo>,
    val total: Int
)

// ============================================================================
// CURATION TEMPLATE MODELS (Phase 9)
// ============================================================================

/**
 * Curation template response
 */
@Serializable
data class CurationTemplateResponse(
    val id: String,
    val name: String,
    val description: String?,
    val conditions: List<TemplateCondition>,
    val actions: List<TemplateAction>,
    val createdBy: String?,
    val isGlobal: Boolean,
    val isActive: Boolean,
    val usageCount: Int,
    val createdAt: String,
    val updatedAt: String
)

/**
 * List of templates response
 */
@Serializable
data class CurationTemplatesResponse(
    val templates: List<CurationTemplateResponse>,
    val total: Int
)

/**
 * Request to create/update a template
 */
@Serializable
data class CreateTemplateRequest(
    val name: String,
    val description: String? = null,
    val conditions: List<TemplateCondition>,
    val actions: List<TemplateAction>,
    val isGlobal: Boolean = false
)

/**
 * Template condition
 */
@Serializable
data class TemplateCondition(
    val type: String, // AI_CONFIDENCE_EQUALS, LICENSE_IN, DEPENDENCY_NAME_MATCHES, SCOPE_EQUALS, etc.
    val value: String // Can be single value or JSON array depending on type
)

/**
 * Template action
 */
@Serializable
data class TemplateAction(
    val type: String, // ACCEPT_AI, REJECT, MODIFY_LICENSE, SET_PRIORITY, ADD_COMMENT
    val value: String? = null
)

/**
 * Request to apply a template
 */
@Serializable
data class ApplyTemplateRequest(
    val templateId: String,
    val dryRun: Boolean = false,
    val filterStatus: List<String>? = null // Only apply to items with these statuses
)

/**
 * Response for template application
 */
@Serializable
data class ApplyTemplateResponse(
    val templateId: String,
    val templateName: String,
    val matchedCount: Int,
    val appliedCount: Int,
    val dryRun: Boolean,
    val affected: List<TemplateAffectedItem> = emptyList()
)

/**
 * Item affected by template
 */
@Serializable
data class TemplateAffectedItem(
    val dependencyId: String,
    val dependencyName: String,
    val action: String,
    val resultingStatus: String?,
    val resultingLicense: String?
)

// ============================================================================
// CURATION FILTER MODELS
// ============================================================================

/**
 * Filter options for curation items
 */
@Serializable
data class CurationFilterOptions(
    val status: List<String>? = null,
    val confidence: List<String>? = null,
    val priority: List<String>? = null,
    val hasAiSuggestion: Boolean? = null,
    val spdxValidated: Boolean? = null,
    val licenseCategory: String? = null,
    val searchQuery: String? = null,
    val sortBy: String = "priority", // priority, name, status, confidence
    val sortOrder: String = "desc" // asc, desc
)
