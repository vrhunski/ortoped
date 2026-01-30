package com.ortoped.core.policy.explanation

import kotlinx.serialization.Serializable

/**
 * Type of explanation provided for a policy violation
 */
@Serializable
enum class ExplanationType(val displayName: String, val icon: String) {
    WHY_PROHIBITED("Why This Is Prohibited", "ban"),
    COPYLEFT_RISK("Copyleft Risk", "shield-exclamation"),
    COMPATIBILITY_ISSUE("Compatibility Issue", "link-slash"),
    OBLIGATION_CONCERN("License Obligations", "file-contract"),
    RISK_LEVEL("Risk Assessment", "chart-line"),
    PROPAGATION_RISK("Copyleft Propagation", "sitemap"),
    USE_CASE_MISMATCH("Use Case Mismatch", "crosshairs")
}

/**
 * Structured explanation for why a policy violation occurred
 */
@Serializable
data class ViolationExplanation(
    val type: ExplanationType,
    val title: String,
    val summary: String,
    val details: List<String>,
    val context: ExplanationContext? = null
)

/**
 * Additional context from the knowledge graph
 */
@Serializable
data class ExplanationContext(
    val licenseCategory: String? = null,
    val copyleftStrength: String? = null,
    val propagationLevel: Int? = null,
    val riskLevel: Int? = null,
    val relatedLicenses: List<String> = emptyList(),
    val triggeredObligations: List<String> = emptyList(),
    val affectedUseCases: List<String> = emptyList()
)

/**
 * Effort level for implementing a resolution
 */
@Serializable
enum class EffortLevel(val displayName: String, val hours: String) {
    TRIVIAL("Trivial", "< 1 hour"),
    LOW("Low", "1-4 hours"),
    MEDIUM("Medium", "1-2 days"),
    HIGH("High", "3-5 days"),
    SIGNIFICANT("Significant", "1+ weeks")
}

/**
 * Type of resolution action
 */
@Serializable
enum class ResolutionType(val displayName: String, val icon: String) {
    REPLACE_DEPENDENCY("Replace Dependency", "swap"),
    ISOLATE_SERVICE("Isolate as Service", "cubes"),
    ACCEPT_OBLIGATIONS("Accept Obligations", "check-circle"),
    REQUEST_EXCEPTION("Request Exception", "clipboard-check"),
    REMOVE_DEPENDENCY("Remove Dependency", "trash"),
    CONTACT_AUTHOR("Contact Author", "envelope"),
    USE_ALTERNATIVE_VERSION("Use Different Version", "code-branch"),
    CHANGE_SCOPE("Change Dependency Scope", "layer-group")
}

/**
 * A suggested resolution option for a policy violation
 */
@Serializable
data class ResolutionOption(
    val type: ResolutionType,
    val title: String,
    val description: String,
    val effort: EffortLevel,
    val tradeoffs: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val alternatives: List<AlternativeDependency> = emptyList(),
    val recommended: Boolean = false
)

/**
 * An alternative dependency that could replace the violating one
 */
@Serializable
data class AlternativeDependency(
    val name: String,
    val version: String? = null,
    val license: String,
    val reason: String,
    val popularity: PopularityLevel? = null
)

/**
 * Popularity level of a dependency
 */
@Serializable
enum class PopularityLevel {
    VERY_HIGH,    // > 10M weekly downloads
    HIGH,         // 1M - 10M
    MEDIUM,       // 100K - 1M
    LOW,          // 10K - 100K
    VERY_LOW      // < 10K
}

/**
 * Enhanced policy violation with full explanations and resolutions
 */
@Serializable
data class EnhancedViolation(
    // Original violation data
    val ruleId: String,
    val ruleName: String,
    val severity: String,
    val dependencyId: String,
    val dependencyName: String,
    val dependencyVersion: String,
    val license: String,
    val licenseCategory: String?,
    val scope: String,
    val message: String,

    // Enhanced explanation data
    val explanations: List<ViolationExplanation> = emptyList(),
    val resolutions: List<ResolutionOption> = emptyList(),
    val similarPastDecisions: List<PastDecision> = emptyList()
)

/**
 * Reference to a past curation decision for similar situation
 */
@Serializable
data class PastDecision(
    val projectName: String,
    val resolution: ResolutionType,
    val outcome: String,
    val dateApplied: String
)

/**
 * Summary statistics for explanations
 */
@Serializable
data class ExplanationSummary(
    val totalViolations: Int,
    val violationsWithExplanations: Int,
    val mostCommonTypes: Map<ExplanationType, Int>,
    val recommendedResolutions: List<ResolutionOption>
)
