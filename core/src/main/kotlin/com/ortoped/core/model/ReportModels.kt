package com.ortoped.core.model

import kotlinx.serialization.Serializable

// ============================================================================
// COMPREHENSIVE REPORT MODELS
// Full audit trail from Scan → Policy → Curation → Final Report
// ============================================================================

/**
 * Complete audit report capturing the entire workflow
 */
@Serializable
data class ComprehensiveReport(
    // Metadata
    val id: String,
    val generatedAt: String,
    val reportVersion: String = "1.0",

    // Project info
    val project: ProjectInfo,

    // Scan phase
    val scan: ScanPhaseReport,

    // Policy evaluation phase
    val policyEvaluation: PolicyPhaseReport? = null,

    // Curation phase
    val curation: CurationPhaseReport? = null,

    // Final results
    val finalResults: FinalResultsReport,

    // Audit trail
    val auditTrail: List<AuditEvent> = emptyList()
)

/**
 * Project information
 */
@Serializable
data class ProjectInfo(
    val id: String?,
    val name: String,
    val repositoryUrl: String? = null,
    val branch: String? = null,
    val scanDate: String
)

// ============================================================================
// SCAN PHASE
// ============================================================================

/**
 * Scan phase report section
 */
@Serializable
data class ScanPhaseReport(
    val scanId: String,
    val status: String,
    val duration: Long? = null, // milliseconds

    // Configuration used
    val configuration: ScanConfiguration,

    // Results summary
    val summary: ScanPhaseSummary,

    // All dependencies (optional - can be large)
    val dependencies: List<DependencyReport>? = null
)

/**
 * Scan configuration
 */
@Serializable
data class ScanConfiguration(
    val enableAi: Boolean,
    val enableSourceScan: Boolean = false,
    val parallelAiCalls: Boolean = false
)

/**
 * Scan phase summary
 */
@Serializable
data class ScanPhaseSummary(
    val totalDependencies: Int,
    val resolvedLicenses: Int,
    val unresolvedLicenses: Int,
    val aiResolvedLicenses: Int,
    val scannerResolvedLicenses: Int = 0
)

/**
 * Individual dependency report
 */
@Serializable
data class DependencyReport(
    val id: String,
    val name: String,
    val version: String,
    val scope: String,

    // License resolution journey
    val licenseResolution: LicenseResolutionJourney
)

/**
 * Complete license resolution journey for a dependency
 */
@Serializable
data class LicenseResolutionJourney(
    // Initial state
    val declaredLicenses: List<String>,
    val detectedLicenses: List<String>,

    // AI enhancement
    val aiSuggestion: AiSuggestionSummary? = null,

    // Source code scan
    val sourceCodeLicenses: List<String>? = null,

    // Curation
    val curationDecision: CurationDecisionSummary? = null,

    // Final
    val finalLicense: String?,
    val resolutionMethod: String // DECLARED, DETECTED, AI, SOURCE_CODE, CURATED, MANUAL, UNRESOLVED
)

/**
 * AI suggestion summary for reports
 */
@Serializable
data class AiSuggestionSummary(
    val suggestedLicense: String,
    val confidence: String,
    val reasoning: String? = null
)

/**
 * Curation decision summary for reports
 */
@Serializable
data class CurationDecisionSummary(
    val action: String, // ACCEPTED, REJECTED, MODIFIED
    val finalLicense: String?,
    val comment: String? = null,
    val curatedBy: String? = null,
    val curatedAt: String? = null
)

// ============================================================================
// POLICY PHASE
// ============================================================================

/**
 * Policy evaluation phase report section
 */
@Serializable
data class PolicyPhaseReport(
    val policyId: String,
    val policyName: String,
    val policyVersion: String? = null,
    val evaluatedAt: String,

    // Results
    val passed: Boolean,
    val summary: PolicyPhaseSummary,
    val violations: List<PolicyViolationReport> = emptyList(),
    val exemptions: List<ExemptionReport> = emptyList()
)

/**
 * Policy phase summary
 */
@Serializable
data class PolicyPhaseSummary(
    val totalDependencies: Int,
    val evaluatedDependencies: Int,
    val exemptedDependencies: Int,
    val totalViolations: Int,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val licenseDistributionByCategory: Map<String, Int> = emptyMap()
)

/**
 * Policy violation report
 */
@Serializable
data class PolicyViolationReport(
    val ruleId: String,
    val ruleName: String,
    val severity: String,
    val dependencyName: String,
    val dependencyVersion: String,
    val license: String?,
    val message: String,
    val aiSuggestedFix: String? = null,
    val resolved: Boolean = false,
    val resolutionMethod: String? = null // CURATED, EXEMPTED, etc.
)

/**
 * Exemption report
 */
@Serializable
data class ExemptionReport(
    val dependencyName: String,
    val reason: String,
    val exemptedBy: String? = null,
    val exemptedAt: String? = null
)

// ============================================================================
// CURATION PHASE
// ============================================================================

/**
 * Curation phase report section
 */
@Serializable
data class CurationPhaseReport(
    val sessionId: String,
    val status: String,
    val startedAt: String,
    val completedAt: String? = null,

    // Statistics
    val statistics: CurationPhaseSummary,

    // All decisions (optional - can be large)
    val decisions: List<CurationDecisionReport>? = null,

    // Approval
    val approval: CurationApprovalReport? = null
)

/**
 * Curation phase summary
 */
@Serializable
data class CurationPhaseSummary(
    val total: Int,
    val pending: Int,
    val accepted: Int,
    val rejected: Int,
    val modified: Int,
    val completionPercentage: Double
)

/**
 * Individual curation decision for report
 */
@Serializable
data class CurationDecisionReport(
    val dependencyName: String,
    val dependencyVersion: String,
    val originalLicense: String?,
    val aiSuggestedLicense: String?,
    val aiConfidence: String?,
    val action: String,
    val finalLicense: String?,
    val comment: String?,
    val curatedBy: String?,
    val curatedAt: String?
)

/**
 * Curation approval report
 */
@Serializable
data class CurationApprovalReport(
    val approvedBy: String,
    val approvedAt: String,
    val comment: String? = null
)

// ============================================================================
// FINAL RESULTS
// ============================================================================

/**
 * Final results summary
 */
@Serializable
data class FinalResultsReport(
    // License distribution
    val licenseDistribution: Map<String, Int>,
    val categoryDistribution: Map<String, Int>,

    // Compliance
    val complianceStatus: String, // COMPLIANT, NON_COMPLIANT, REQUIRES_REVIEW
    val unresolvedIssues: Int,

    // Statistics by resolution method
    val totalDependencies: Int,
    val resolvedByDeclared: Int,
    val resolvedByDetected: Int,
    val resolvedByAi: Int,
    val resolvedBySourceScan: Int,
    val resolvedByCuration: Int,
    val unresolved: Int
)

// ============================================================================
// AUDIT TRAIL
// ============================================================================

/**
 * Audit trail event
 */
@Serializable
data class AuditEvent(
    val timestamp: String,
    val phase: String, // SCAN, POLICY, CURATION, REPORT
    val action: String,
    val actor: String?, // User or "system"
    val details: String
)

// ============================================================================
// REPORT GENERATION OPTIONS
// ============================================================================

/**
 * Options for report generation
 */
@Serializable
data class ReportOptions(
    val format: String = "json", // json, html
    val includePolicy: Boolean = true,
    val includeCuration: Boolean = true,
    val includeAuditTrail: Boolean = true,
    val includeDependencyDetails: Boolean = false, // Can make report very large
    val includeAiReasoning: Boolean = false
)

/**
 * Report metadata for storage
 */
@Serializable
data class ReportMetadata(
    val id: String,
    val scanId: String,
    val format: String,
    val generatedAt: String,
    val generatedBy: String?,
    val fileSize: Long? = null,
    val options: ReportOptions
)
