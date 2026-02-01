package com.ortoped.core.graph.model

import kotlinx.serialization.Serializable

// =============================================================================
// Compatibility Results
// =============================================================================

/**
 * Result of checking compatibility between two licenses
 */
@Serializable
data class CompatibilityResult(
    val license1: String,
    val license2: String,
    val compatible: Boolean,
    val level: CompatibilityLevel,
    val reason: String,
    val conditions: List<String> = emptyList(),
    val path: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val dominantLicense: String? = null,
    val inferredRule: String? = null,
    val requiresReview: Boolean = false
)

/**
 * Path of compatibility between licenses
 */
@Serializable
data class CompatibilityPath(
    val sourceLicense: String,
    val targetLicense: String,
    val licenses: List<String>,
    val steps: List<CompatibilityStep>,
    val overallCompatibility: CompatibilityLevel,
    val allConditions: List<String>
)

/**
 * Single step in a compatibility path
 */
@Serializable
data class CompatibilityStep(
    val from: String,
    val to: String,
    val compatibility: CompatibilityLevel,
    val conditions: List<String> = emptyList()
)

// =============================================================================
// Obligation Results
// =============================================================================

/**
 * Aggregated obligations from multiple licenses
 */
@Serializable
data class AggregatedObligations(
    val obligations: List<AggregatedObligation>,
    val totalLicenses: Int,
    val highestEffort: EffortLevel?,
    val uniqueObligationCount: Int
)

/**
 * Single aggregated obligation with all its sources
 */
@Serializable
data class AggregatedObligation(
    val obligationId: String,
    val obligationName: String,
    val description: String,
    val effort: EffortLevel,
    val sources: List<ObligationSource>,
    val mostRestrictiveScope: ObligationScope,
    val examples: List<String> = emptyList()
)

/**
 * Source of an obligation (which license triggered it)
 */
@Serializable
data class ObligationSource(
    val licenseId: String,
    val licenseName: String? = null,
    val trigger: TriggerCondition,
    val scope: ObligationScope
)

/**
 * Obligation with distribution scope context (EU compliance)
 */
@Serializable
data class ObligationWithDistribution(
    val obligation: ObligationNode,
    val scope: ObligationScope,
    val trigger: TriggerCondition,
    val distributionScope: String,  // INTERNAL, BINARY, SOURCE, SAAS, EMBEDDED
    val adjustedEffort: EffortLevel,
    val isApplicable: Boolean,
    val applicabilityReason: String
)

// =============================================================================
// Dependency Tree Analysis
// =============================================================================

/**
 * Input: A dependency with its license
 */
@Serializable
data class DependencyLicense(
    val dependencyId: String,
    val dependencyName: String,
    val dependencyVersion: String,
    val license: String,                        // SPDX ID
    val isTransitive: Boolean = false,
    val scope: String = "compile",              // compile, runtime, test, etc.
    val path: List<String> = emptyList()        // Dependency path (for transitive)
)

/**
 * Complete analysis of a dependency tree
 */
@Serializable
data class DependencyTreeAnalysis(
    val totalDependencies: Int,
    val uniqueLicenses: List<String>,
    val licenseDistribution: Map<String, Int>,
    val categoryDistribution: Map<String, Int>,
    val conflicts: List<LicenseConflict>,
    val dominantLicense: DominantLicenseInfo?,
    val aggregatedObligations: AggregatedObligations,
    val complianceStatus: ComplianceStatus,
    val recommendations: List<ComplianceRecommendation> = emptyList(),
    val riskScore: Double = 0.0                 // 0.0 (low risk) to 1.0 (high risk)
)

/**
 * Information about the dominant (most restrictive) license
 */
@Serializable
data class DominantLicenseInfo(
    val licenseId: String,
    val licenseName: String,
    val category: LicenseCategory,
    val copyleftStrength: CopyleftStrength,
    val reason: String,
    val dependencyCount: Int
)

/**
 * A conflict between two licenses in the dependency tree
 */
@Serializable
data class LicenseConflict(
    val id: String,
    val dependency1: DependencyLicense,
    val dependency2: DependencyLicense,
    val reason: String,
    val severity: ConflictSeverity,
    val suggestions: List<String> = emptyList(),
    val legalReferences: List<String> = emptyList()
)

/**
 * Severity of a license conflict
 */
@Serializable
enum class ConflictSeverity(val displayName: String, val level: Int) {
    INFO("Info", 0),                            // Informational, not blocking
    WARNING("Warning", 1),                      // Should be reviewed
    BLOCKING("Blocking", 2);                    // Must be resolved

    companion object {
        fun fromString(value: String): ConflictSeverity {
            return when (value.lowercase()) {
                "info" -> INFO
                "warning", "warn" -> WARNING
                "blocking", "error" -> BLOCKING
                else -> WARNING
            }
        }
    }
}

/**
 * Overall compliance status of a project
 */
@Serializable
enum class ComplianceStatus(val displayName: String, val isCompliant: Boolean) {
    COMPLIANT("Compliant", true),               // No issues
    WARNINGS("Has Warnings", true),             // Has warnings but can proceed
    BLOCKED("Blocked", false),                  // Has blocking conflicts
    REQUIRES_REVIEW("Requires Review", true);   // Needs legal review

    companion object {
        fun fromString(value: String): ComplianceStatus {
            return when (value.lowercase().replace("-", "_").replace(" ", "_")) {
                "compliant" -> COMPLIANT
                "warnings", "has_warnings" -> WARNINGS
                "blocked" -> BLOCKED
                "requires_review", "review" -> REQUIRES_REVIEW
                else -> REQUIRES_REVIEW
            }
        }
    }
}

/**
 * A recommendation for achieving compliance
 */
@Serializable
data class ComplianceRecommendation(
    val type: RecommendationType,
    val priority: RecommendationPriority,
    val title: String,
    val description: String,
    val actions: List<String> = emptyList(),
    val affectedDependencies: List<String> = emptyList(),
    val estimatedEffort: EffortLevel? = null
)

/**
 * Type of compliance recommendation
 */
@Serializable
enum class RecommendationType(val displayName: String) {
    RESOLVE_CONFLICT("Resolve Conflict"),
    FULFILL_OBLIGATION("Fulfill Obligation"),
    REPLACE_DEPENDENCY("Replace Dependency"),
    ADD_ATTRIBUTION("Add Attribution"),
    PROVIDE_SOURCE("Provide Source"),
    LEGAL_REVIEW("Legal Review"),
    OTHER("Other");

    companion object {
        fun fromString(value: String): RecommendationType {
            return when (value.lowercase().replace("-", "_").replace(" ", "_")) {
                "resolve_conflict" -> RESOLVE_CONFLICT
                "fulfill_obligation" -> FULFILL_OBLIGATION
                "replace_dependency" -> REPLACE_DEPENDENCY
                "add_attribution" -> ADD_ATTRIBUTION
                "provide_source" -> PROVIDE_SOURCE
                "legal_review" -> LEGAL_REVIEW
                else -> OTHER
            }
        }
    }
}

/**
 * Priority of a recommendation
 */
@Serializable
enum class RecommendationPriority(val displayName: String, val level: Int) {
    LOW("Low", 0),
    MEDIUM("Medium", 1),
    HIGH("High", 2),
    CRITICAL("Critical", 3);

    companion object {
        fun fromString(value: String): RecommendationPriority {
            return when (value.lowercase()) {
                "low" -> LOW
                "medium" -> MEDIUM
                "high" -> HIGH
                "critical" -> CRITICAL
                else -> MEDIUM
            }
        }
    }
}

// =============================================================================
// Graph Statistics
// =============================================================================

/**
 * Statistics about the knowledge graph
 */
@Serializable
data class GraphStatistics(
    val totalLicenses: Int,
    val totalObligations: Int,
    val totalRights: Int,
    val totalConditions: Int,
    val totalLimitations: Int,
    val totalUseCases: Int,
    val totalEdges: Int,
    val totalCompatibilityEdges: Int,
    val totalObligationEdges: Int,
    val licensesByCategory: Map<String, Int>,
    val licenseFamilies: List<String>,
    val lastUpdated: String? = null
)

// =============================================================================
// License Details (enriched from graph)
// =============================================================================

/**
 * Complete license details from the knowledge graph
 */
@Serializable
data class LicenseDetails(
    val license: LicenseNode,
    val obligations: List<ObligationWithScope>,
    val rights: List<RightNode>,
    val conditions: List<ConditionNode>,
    val limitations: List<LimitationNode>,
    val compatibleWith: List<CompatibleLicenseSummary>,
    val incompatibleWith: List<IncompatibleLicenseSummary>
)

/**
 * Obligation with its scope for a specific license
 */
@Serializable
data class ObligationWithScope(
    val obligation: ObligationNode,
    val scope: ObligationScope,
    val trigger: TriggerCondition
)

/**
 * Summary of a compatible license
 */
@Serializable
data class CompatibleLicenseSummary(
    val licenseId: String,
    val licenseName: String,
    val compatibility: CompatibilityLevel,
    val direction: CompatibilityDirection,
    val conditions: List<String> = emptyList()
)

/**
 * Summary of an incompatible license
 */
@Serializable
data class IncompatibleLicenseSummary(
    val licenseId: String,
    val licenseName: String,
    val reason: String,
    val suggestions: List<String> = emptyList()
)

// =============================================================================
// Query Results
// =============================================================================

/**
 * Result of a license search query
 */
@Serializable
data class LicenseSearchResult(
    val licenses: List<LicenseNode>,
    val totalCount: Int,
    val query: String
)

/**
 * Result of an obligation search query
 */
@Serializable
data class ObligationSearchResult(
    val obligations: List<ObligationNode>,
    val totalCount: Int,
    val query: String
)
