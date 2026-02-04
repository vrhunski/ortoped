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

/**
 * Request to add a dependency to curation manually
 * Used when a policy violation occurs for a dependency that isn't in the curation queue
 */
@Serializable
data class AddToCurationRequest(
    val dependencyId: String,
    val reason: String? = null // Optional reason for adding to curation (e.g., "Policy violation: unknown license")
)

/**
 * Response after adding a dependency to curation
 */
@Serializable
data class AddToCurationResponse(
    val success: Boolean,
    val item: CurationItemResponse? = null,
    val message: String
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

// ============================================================================
// EU COMPLIANCE MODELS (Phase 10)
// ============================================================================

/**
 * Enhanced curation item response with EU compliance fields
 */
@Serializable
data class EnhancedCurationItemResponse(
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

    // Priority
    val priority: PriorityInfo? = null,

    // SPDX validation
    val spdxValidated: Boolean = false,
    val spdxLicense: SpdxLicenseInfo? = null,

    // EU Compliance fields
    val requiresJustification: Boolean = true,
    val justificationComplete: Boolean = false,
    val justification: JustificationResponse? = null,
    val blockingPolicyRule: PolicyRuleReference? = null,
    val isOrLicense: Boolean = false,
    val orLicenseOptions: List<String>? = null,
    val orLicenseChoice: String? = null,
    val distributionScope: String = "BINARY",

    // Inline explanations for UI
    val explanations: CurationExplanationsResponse? = null
)

/**
 * Structured justification response
 */
@Serializable
data class JustificationResponse(
    val id: String,
    val spdxId: String,
    val licenseCategory: String,
    val concludedLicense: String,
    val justificationType: String,
    val justificationText: String,
    val policyRuleId: String? = null,
    val policyRuleName: String? = null,
    val evidenceType: String? = null,
    val evidenceReference: String? = null,
    val distributionScope: String,
    val curatorId: String,
    val curatorName: String? = null,
    val curatedAt: String,
    val justificationHash: String? = null
)

/**
 * Reference to a blocking policy rule
 */
@Serializable
data class PolicyRuleReference(
    val ruleId: String,
    val ruleName: String,
    val severity: String
)

/**
 * Curation explanations for UI ("Why Not?" + obligations + compatibility)
 */
@Serializable
data class CurationExplanationsResponse(
    val dependencyId: String,
    val whyNotExplanations: List<WhyNotExplanation> = emptyList(),
    val triggeredObligations: List<ObligationInfo> = emptyList(),
    val compatibilityIssues: List<CompatibilityIssue> = emptyList(),
    val resolutions: List<ResolutionSuggestion> = emptyList()
)

/**
 * "Why Not?" explanation for a policy violation
 */
@Serializable
data class WhyNotExplanation(
    val type: String, // LICENSE_EXPRESSION, LICENSE_CATEGORY, COPYLEFT_RISK, UNRECOGNIZED_LICENSE, UNKNOWN_LICENSE
    val title: String,
    val summary: String,
    val details: List<String> = emptyList(),
    val riskLevel: Int = 0 // 0-6, higher = more risk
)

/**
 * License obligation information
 */
@Serializable
data class ObligationInfo(
    val name: String,
    val description: String,
    val scope: String, // e.g., "Distribution", "Modification", "All Uses"
    val effort: String, // LOW, MEDIUM, HIGH
    val isRequired: Boolean = true
)

/**
 * Compatibility issue with another dependency
 */
@Serializable
data class CompatibilityIssue(
    val otherDependencyId: String,
    val otherDependencyName: String,
    val otherLicense: String,
    val currentLicense: String,
    val compatibilityLevel: String, // FULL, CONDITIONAL, INCOMPATIBLE, UNKNOWN
    val reason: String,
    val recommendation: String? = null
)

/**
 * Resolution suggestion for fixing an issue
 */
@Serializable
data class ResolutionSuggestion(
    val type: String, // REPLACE_DEPENDENCY, ADD_EXCEPTION, DOCUMENT_CHOICE, ISOLATE_SERVICE, ACCEPT_OBLIGATIONS, REQUEST_EXCEPTION, INVESTIGATE
    val title: String,
    val description: String,
    val effort: String, // LOW, MEDIUM, HIGH
    val steps: List<String> = emptyList()
)

/**
 * Request to submit structured justification
 */
@Serializable
data class JustificationRequest(
    val spdxId: String,
    val licenseCategory: String,
    val concludedLicense: String,
    val justificationType: String, // AI_ACCEPTED, MANUAL_OVERRIDE, EVIDENCE_BASED, POLICY_EXEMPTION
    val justificationText: String,
    val policyRuleId: String? = null,
    val policyRuleName: String? = null,
    val evidenceType: String? = null, // LICENSE_FILE, REPO_INSPECTION, VENDOR_CONFIRMATION, LEGAL_OPINION
    val evidenceReference: String? = null,
    val distributionScope: String = "BINARY"
)

/**
 * Request to submit curation with justification (combined)
 */
@Serializable
data class CurationWithJustificationRequest(
    val action: String, // ACCEPT, REJECT, MODIFY
    val license: String? = null, // Required for REJECT/MODIFY
    val comment: String? = null,
    val justification: JustificationRequest? = null // Required for non-permissive
)

/**
 * Request to submit session for approval (two-role workflow)
 */
@Serializable
data class SubmitForApprovalRequest(
    val comment: String? = null
)

/**
 * Request for approver to decide on session
 */
@Serializable
data class ApprovalDecisionRequest(
    val decision: String, // APPROVED, REJECTED, RETURNED
    val comment: String? = null,
    val returnReason: String? = null,
    val revisionItems: List<String>? = null // Item IDs needing revision
)

/**
 * Response for session approval status
 */
@Serializable
data class ApprovalStatusResponse(
    val sessionId: String,
    val isSubmittedForApproval: Boolean,
    val submittedBy: String? = null,
    val submittedAt: String? = null,
    val approval: ApprovalRecordResponse? = null,
    val readiness: ApprovalReadinessResponse? = null
)

/**
 * Approval record response
 */
@Serializable
data class ApprovalRecordResponse(
    val id: String,
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
    val revisionItems: List<String>?
)

/**
 * Approval readiness check response
 */
@Serializable
data class ApprovalReadinessResponse(
    val isReady: Boolean,
    val totalItems: Int,
    val pendingItems: Int,
    val unresolvedOrLicenses: Int,
    val pendingJustifications: Int,
    val blockers: List<ApprovalBlockerInfo> = emptyList()
)

/**
 * Information about what blocks approval
 */
@Serializable
data class ApprovalBlockerInfo(
    val type: String, // PENDING_ITEMS, UNRESOLVED_OR, MISSING_JUSTIFICATION
    val count: Int,
    val message: String,
    val affectedItems: List<String> = emptyList()
)

// ============================================================================
// OR LICENSE MODELS
// ============================================================================

/**
 * List of unresolved OR licenses
 */
@Serializable
data class OrLicensesResponse(
    val orLicenses: List<OrLicenseItemResponse>,
    val total: Int,
    val resolved: Int,
    val unresolved: Int
)

/**
 * Single OR license item
 */
@Serializable
data class OrLicenseItemResponse(
    val curationId: String,
    val dependencyId: String,
    val dependencyName: String,
    val dependencyVersion: String,
    val originalExpression: String,
    val options: List<OrLicenseOptionResponse>,
    val isResolved: Boolean,
    val chosenLicense: String? = null,
    val choiceReason: String? = null,
    val resolvedBy: String? = null,
    val resolvedAt: String? = null
)

/**
 * Single OR license option with analysis
 */
@Serializable
data class OrLicenseOptionResponse(
    val license: String,
    val category: String, // PERMISSIVE, WEAK_COPYLEFT, etc.
    val isOsiApproved: Boolean,
    val isFsfLibre: Boolean,
    val policyCompliant: Boolean,
    val recommendation: String? = null // "Recommended", "Not Recommended", null
)

/**
 * Request to resolve an OR license
 */
@Serializable
data class ResolveOrLicenseRequest(
    val chosenLicense: String,
    val choiceReason: String
)

// ============================================================================
// AUDIT LOG MODELS
// ============================================================================

/**
 * Audit log entry response
 */
@Serializable
data class AuditLogEntryResponse(
    val id: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val actorId: String,
    val actorRole: String,
    val changeSummary: String,
    val previousState: String? = null, // JSON string
    val newState: String, // JSON string
    val createdAt: String
)

/**
 * Audit log list response
 */
@Serializable
data class AuditLogListResponse(
    val entries: List<AuditLogEntryResponse>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)

/**
 * Audit log filter options
 */
@Serializable
data class AuditLogFilterOptions(
    val entityType: String? = null,
    val entityId: String? = null,
    val actorId: String? = null,
    val action: String? = null,
    val startDate: String? = null,
    val endDate: String? = null
)

// ============================================================================
// ENHANCED SESSION RESPONSE
// ============================================================================

/**
 * Enhanced curation session response with EU compliance fields
 */
@Serializable
data class EnhancedCurationSessionResponse(
    val id: String,
    val scanId: String,
    val status: String,
    val statistics: CurationStatistics,
    val approval: CurationApproval? = null,

    // EU Compliance fields
    val submittedForApproval: Boolean = false,
    val submittedAt: String? = null,
    val submittedBy: String? = null,
    val approvalRecord: ApprovalRecordResponse? = null,
    val readiness: ApprovalReadinessResponse? = null,

    // OR license summary
    val orLicenseSummary: OrLicenseSummary? = null,

    val createdAt: String,
    val updatedAt: String
)

/**
 * OR license summary for session view
 */
@Serializable
data class OrLicenseSummary(
    val total: Int,
    val resolved: Int,
    val unresolved: Int
)

// ============================================================================
// EXPORT MODELS
// ============================================================================

/**
 * Export format options
 */
@Serializable
data class ExportRequest(
    val format: String = "curations-yaml", // curations-yaml, notice, both
    val includeApprovalInfo: Boolean = true,
    val includeJustifications: Boolean = true
)

/**
 * Export response
 */
@Serializable
data class ExportResponse(
    val content: String,
    val format: String,
    val filename: String,
    val generatedAt: String
)

/**
 * Curations YAML export response (ORT compatible)
 */
@Serializable
data class CurationsYamlResponse(
    val yaml: String,
    val filename: String = "curations.yml"
)

/**
 * NOTICE file export response
 */
@Serializable
data class NoticeFileResponse(
    val content: String,
    val filename: String = "NOTICE"
)
