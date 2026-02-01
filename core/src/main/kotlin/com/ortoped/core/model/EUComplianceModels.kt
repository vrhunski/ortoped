package com.ortoped.core.model

import kotlinx.serialization.Serializable

// ============================================================================
// EU COMPLIANCE MODELS
// Models for EU/German regulatory compliance in license curation
// ============================================================================

/**
 * Distribution scope determines which obligations apply
 * Different scopes trigger different compliance requirements
 */
enum class DistributionScope(val displayName: String, val description: String) {
    INTERNAL("Internal Use", "Used only within the organization, not distributed"),
    BINARY("Binary Distribution", "Distributed as compiled binaries to customers"),
    SOURCE("Source Distribution", "Distributed with source code"),
    SAAS("SaaS/Cloud", "Provided as a cloud service (AGPL implications)"),
    EMBEDDED("Embedded/Device", "Embedded in hardware or devices")
}

/**
 * Type of justification for a curation decision
 */
enum class JustificationType(val displayName: String, val description: String) {
    AI_ACCEPTED("AI Accepted", "AI suggestion accepted with high confidence"),
    MANUAL_OVERRIDE("Manual Override", "Curator overrode AI suggestion based on expertise"),
    EVIDENCE_BASED("Evidence Based", "Decision based on documentary evidence"),
    POLICY_EXEMPTION("Policy Exemption", "Exempted from policy rule with documented reason"),
    LEGAL_OPINION("Legal Opinion", "Based on legal counsel opinion")
}

/**
 * Type of evidence supporting a curation decision
 */
enum class EvidenceType(val displayName: String, val description: String) {
    LICENSE_FILE("License File", "LICENSE or COPYING file in repository"),
    REPO_INSPECTION("Repository Inspection", "Manual inspection of source repository"),
    VENDOR_CONFIRMATION("Vendor Confirmation", "Written confirmation from vendor/maintainer"),
    LEGAL_OPINION("Legal Opinion", "Written opinion from legal counsel"),
    PRIOR_AUDIT("Prior Audit", "Reference to previous compliance audit"),
    PACKAGE_METADATA("Package Metadata", "Information from package registry metadata")
}

/**
 * Approval decision for two-role workflow
 */
enum class ApprovalDecision(val displayName: String) {
    APPROVED("Approved"),
    REJECTED("Rejected"),
    RETURNED("Returned for Revision")
}

/**
 * Actor role for audit logging
 */
enum class ActorRole(val displayName: String) {
    CURATOR("Curator"),
    APPROVER("Approver"),
    SYSTEM("System")
}

/**
 * License category for structured justification
 */
enum class LicenseCategory(val displayName: String, val requiresJustification: Boolean) {
    PERMISSIVE("Permissive", false),
    WEAK_COPYLEFT("Weak Copyleft", true),
    STRONG_COPYLEFT("Strong Copyleft", true),
    NETWORK_COPYLEFT("Network Copyleft (AGPL)", true),
    PROPRIETARY("Proprietary", true),
    PUBLIC_DOMAIN("Public Domain", false),
    UNKNOWN("Unknown", true)
}

/**
 * Audit log action types
 */
enum class AuditAction(val displayName: String) {
    CREATE("Create"),
    DECIDE("Make Decision"),
    JUSTIFY("Add Justification"),
    SUBMIT("Submit for Approval"),
    APPROVE("Approve"),
    REJECT("Reject"),
    RETURN("Return for Revision"),
    MODIFY("Modify"),
    RESOLVE_OR("Resolve OR License")
}

/**
 * Audit log entity types
 */
enum class AuditEntityType {
    CURATION,
    SESSION,
    APPROVAL,
    JUSTIFICATION,
    OR_LICENSE
}

// ============================================================================
// DATA CLASSES
// ============================================================================

/**
 * Structured justification for a curation decision
 * Mandatory for non-permissive licenses per EU compliance
 */
@Serializable
data class CurationJustification(
    val id: String? = null,
    val curationId: String,

    // License identification
    val spdxId: String,
    val licenseCategory: String, // LicenseCategory value
    val concludedLicense: String,

    // Structured justification
    val justificationType: String, // JustificationType value
    val justificationText: String,

    // Policy context
    val policyRuleId: String? = null,
    val policyRuleName: String? = null,

    // Evidence
    val evidenceType: String? = null, // EvidenceType value
    val evidenceReference: String? = null,
    val evidenceCollectedAt: String? = null,

    // Distribution scope
    val distributionScope: String = "BINARY",

    // Curator info
    val curatorId: String,
    val curatorName: String? = null,
    val curatorEmail: String? = null,
    val curatedAt: String? = null,

    // Integrity
    val justificationHash: String? = null
)

/**
 * Curation approval record for two-role workflow
 */
@Serializable
data class CurationApprovalRecord(
    val id: String? = null,
    val sessionId: String,

    // Submitter (curator)
    val submitterId: String,
    val submitterName: String? = null,
    val submittedAt: String,

    // Approver (must be different from submitter)
    val approverId: String? = null,
    val approverName: String? = null,
    val approverRole: String? = null,

    // Decision
    val decision: String? = null, // ApprovalDecision value
    val decisionComment: String? = null,
    val decidedAt: String? = null,

    // If returned
    val returnReason: String? = null,
    val revisionItems: List<String>? = null
)

/**
 * Audit log entry for compliance tracking
 */
@Serializable
data class AuditLogEntry(
    val id: String? = null,
    val entityType: String, // AuditEntityType value
    val entityId: String,
    val action: String, // AuditAction value
    val actorId: String,
    val actorRole: String, // ActorRole value
    val previousState: String? = null, // JSON
    val newState: String, // JSON
    val changeSummary: String,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val createdAt: String? = null
)

/**
 * OR license resolution record
 */
@Serializable
data class OrLicenseResolution(
    val id: String? = null,
    val curationId: String,
    val originalExpression: String,
    val licenseOptions: List<String>,
    val chosenLicense: String? = null,
    val choiceReason: String? = null,
    val resolvedBy: String? = null,
    val resolvedAt: String? = null,
    val isResolved: Boolean = false
)

// ============================================================================
// RESPONSE/REQUEST MODELS
// ============================================================================

/**
 * Justification submission request
 */
@Serializable
data class JustificationSubmission(
    val spdxId: String,
    val licenseCategory: String,
    val concludedLicense: String,
    val justificationType: String,
    val justificationText: String,
    val policyRuleId: String? = null,
    val policyRuleName: String? = null,
    val evidenceType: String? = null,
    val evidenceReference: String? = null,
    val distributionScope: String = "BINARY"
)

/**
 * Approval submission request
 */
@Serializable
data class ApprovalSubmission(
    val decision: String, // ApprovalDecision value
    val comment: String? = null,
    val returnReason: String? = null,
    val revisionItems: List<String>? = null
)

/**
 * OR license resolution request
 */
@Serializable
data class OrLicenseResolutionRequest(
    val chosenLicense: String,
    val choiceReason: String
)

/**
 * Summary of session readiness for approval
 */
@Serializable
data class ApprovalReadiness(
    val isReady: Boolean,
    val totalItems: Int,
    val pendingItems: Int,
    val unresolvedOrLicenses: Int,
    val pendingJustifications: Int,
    val blockers: List<ApprovalBlocker>
)

/**
 * Reason why approval is blocked
 */
@Serializable
data class ApprovalBlocker(
    val type: String, // 'PENDING_ITEMS', 'UNRESOLVED_OR', 'MISSING_JUSTIFICATION'
    val count: Int,
    val message: String,
    val affectedItems: List<String>? = null
)

/**
 * OR license with options for resolution UI
 */
@Serializable
data class OrLicenseItem(
    val curationId: String,
    val dependencyId: String,
    val dependencyName: String,
    val originalExpression: String,
    val options: List<OrLicenseOption>,
    val isResolved: Boolean,
    val resolution: OrLicenseResolution? = null
)

/**
 * Single OR license option with analysis
 */
@Serializable
data class OrLicenseOption(
    val license: String,
    val category: String,
    val isOsiApproved: Boolean,
    val isFsfLibre: Boolean,
    val policyCompliant: Boolean,
    val recommendation: String? = null
)

/**
 * Explanation data for curation UI
 */
@Serializable
data class CurationExplanations(
    val dependencyId: String,
    val whyNotExplanations: List<ViolationExplanationSummary>,
    val triggeredObligations: List<ObligationSummary>,
    val compatibilityIssues: List<CompatibilityIssueSummary>,
    val resolutions: List<ResolutionOptionSummary>
)

/**
 * Simplified violation explanation for UI
 */
@Serializable
data class ViolationExplanationSummary(
    val policyRuleId: String,
    val policyRuleName: String,
    val severity: String,
    val explanation: String,
    val whatItMeans: String,
    val howToFix: String
)

/**
 * Obligation summary for UI
 */
@Serializable
data class ObligationSummary(
    val name: String,
    val description: String,
    val trigger: String,
    val effortLevel: String, // 'LOW', 'MEDIUM', 'HIGH'
    val appliesToScope: List<String> // Which distribution scopes
)

/**
 * Compatibility issue summary for UI
 */
@Serializable
data class CompatibilityIssueSummary(
    val otherDependencyId: String,
    val otherDependencyName: String,
    val otherLicense: String,
    val currentLicense: String,
    val compatibilityLevel: String, // 'FULL', 'CONDITIONAL', 'INCOMPATIBLE', 'UNKNOWN'
    val reason: String,
    val recommendation: String? = null
)

/**
 * Resolution option summary for UI
 */
@Serializable
data class ResolutionOptionSummary(
    val type: String, // 'REPLACE_DEPENDENCY', 'ADD_EXCEPTION', 'CHANGE_DISTRIBUTION'
    val description: String,
    val effort: String,
    val steps: List<String>
)

/**
 * Enhanced curation item with EU compliance fields
 */
@Serializable
data class EnhancedCurationItem(
    // Base curation fields
    val id: String,
    val sessionId: String,
    val scanId: String,
    val dependencyId: String,
    val dependencyName: String,
    val dependencyVersion: String,
    val dependencyScope: String?,

    // License info
    val originalLicense: String?,
    val declaredLicenses: List<String>,
    val detectedLicenses: List<String>,

    // AI suggestion
    val aiSuggestedLicense: String?,
    val aiConfidence: String?,
    val aiReasoning: String?,
    val aiAlternatives: List<String>?,

    // Current decision
    val status: String,
    val curatedLicense: String?,
    val curatorComment: String?,
    val curatorId: String?,
    val curatedAt: String?,

    // Priority
    val priorityLevel: String?,
    val priorityScore: Double?,
    val priorityFactors: List<PriorityFactor>?,

    // EU Compliance fields
    val requiresJustification: Boolean,
    val justificationComplete: Boolean,
    val justification: CurationJustification?,
    val blockingPolicyRule: String?,
    val isOrLicense: Boolean,
    val orLicenseOptions: List<String>?,
    val orLicenseChoice: String?,
    val distributionScope: String,

    // Inline explanations (for UI)
    val explanations: CurationExplanations? = null
)

/**
 * Pending approval view for approvers dashboard
 */
@Serializable
data class PendingApprovalView(
    val approvalId: String,
    val sessionId: String,
    val scanId: String,
    val projectId: String,
    val projectName: String,
    val submitterId: String,
    val submitterName: String?,
    val submittedAt: String,
    val totalItems: Int,
    val pendingItems: Int,
    val acceptedItems: Int,
    val rejectedItems: Int,
    val modifiedItems: Int,
    val unresolvedOrLicenses: Int,
    val pendingJustifications: Int
)

/**
 * Audit log query result
 */
@Serializable
data class AuditLogResult(
    val entries: List<AuditLogEntry>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)
