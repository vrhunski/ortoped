package com.ortoped.core.graph.model

import kotlinx.serialization.Serializable

/**
 * Base interface for all graph edges
 */
interface GraphEdge {
    val id: String
    val edgeType: EdgeType
    val sourceId: String
    val targetId: String
}

/**
 * Types of edges in the license knowledge graph
 */
@Serializable
enum class EdgeType(val displayName: String) {
    // License-to-License relationships
    COMPATIBLE_WITH("Compatible With"),         // Can be combined in same project
    INCOMPATIBLE_WITH("Incompatible With"),     // Cannot be combined
    UPGRADABLE_TO("Upgradable To"),             // Can upgrade to newer version
    DERIVED_FROM("Derived From"),               // Based on another license

    // License-to-Obligation relationships
    REQUIRES("Requires"),                       // License requires this obligation

    // License-to-Right relationships
    GRANTS("Grants"),                           // License grants this right

    // License-to-Condition relationships
    HAS_CONDITION("Has Condition"),             // License has this condition

    // License-to-Limitation relationships
    HAS_LIMITATION("Has Limitation"),           // License has this limitation

    // Use-case relationships
    TRIGGERS("Triggers"),                       // Use case triggers obligation
    EXEMPT_FROM("Exempt From"),                 // Use case exempt from obligation

    // Compatibility modifiers
    COMPATIBLE_WHEN("Compatible When"),         // Conditional compatibility
    COMPATIBLE_FOR("Compatible For");           // Compatible for specific use cases

    companion object {
        fun fromString(value: String): EdgeType {
            return when (value.lowercase().replace("-", "_").replace(" ", "_")) {
                "compatible_with", "compatible" -> COMPATIBLE_WITH
                "incompatible_with", "incompatible" -> INCOMPATIBLE_WITH
                "upgradable_to", "upgradable" -> UPGRADABLE_TO
                "derived_from", "derived" -> DERIVED_FROM
                "requires" -> REQUIRES
                "grants" -> GRANTS
                "has_condition" -> HAS_CONDITION
                "has_limitation" -> HAS_LIMITATION
                "triggers" -> TRIGGERS
                "exempt_from", "exempt" -> EXEMPT_FROM
                "compatible_when" -> COMPATIBLE_WHEN
                "compatible_for" -> COMPATIBLE_FOR
                else -> COMPATIBLE_WITH
            }
        }
    }
}

// =============================================================================
// Compatibility Edge
// =============================================================================

/**
 * Compatibility edge between two licenses
 */
@Serializable
data class CompatibilityEdge(
    override val id: String,
    override val sourceId: String,              // License A (SPDX ID uppercase)
    override val targetId: String,              // License B (SPDX ID uppercase)
    val compatibility: CompatibilityLevel,
    val direction: CompatibilityDirection,
    val conditions: List<String> = emptyList(), // Conditions for compatibility
    val notes: List<String> = emptyList(),      // Explanatory notes
    val sources: List<String> = emptyList(),    // Legal sources/references
    val confidence: Double = 1.0                // Confidence level (0.0-1.0)
) : GraphEdge {
    override val edgeType = EdgeType.COMPATIBLE_WITH
}

/**
 * Level of compatibility between two licenses
 */
@Serializable
enum class CompatibilityLevel(val displayName: String, val isCompatible: Boolean) {
    FULL("Full", true),                         // Fully compatible, no restrictions
    CONDITIONAL("Conditional", true),           // Compatible with specific conditions
    ONE_WAY("One-Way", true),                   // A can incorporate B, but not reverse
    INCOMPATIBLE("Incompatible", false),        // Cannot be combined
    UNKNOWN("Unknown", true);                   // Requires legal review (assume compatible)

    companion object {
        fun fromString(value: String): CompatibilityLevel {
            return when (value.lowercase().replace("-", "_").replace(" ", "_")) {
                "full", "fully_compatible" -> FULL
                "conditional" -> CONDITIONAL
                "one_way", "oneway" -> ONE_WAY
                "incompatible" -> INCOMPATIBLE
                "unknown" -> UNKNOWN
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Direction of compatibility
 */
@Serializable
enum class CompatibilityDirection(val displayName: String) {
    BIDIRECTIONAL("Bidirectional"),             // Both ways compatible
    FORWARD("Forward"),                         // Source can incorporate Target
    REVERSE("Reverse");                         // Target can incorporate Source

    companion object {
        fun fromString(value: String): CompatibilityDirection {
            return when (value.lowercase().replace("-", "_").replace(" ", "_")) {
                "bidirectional", "both" -> BIDIRECTIONAL
                "forward" -> FORWARD
                "reverse" -> REVERSE
                else -> BIDIRECTIONAL
            }
        }
    }
}

// =============================================================================
// Obligation Edge
// =============================================================================

/**
 * Obligation edge from License to Obligation
 */
@Serializable
data class ObligationEdge(
    override val id: String,
    override val sourceId: String,              // License ID
    override val targetId: String,              // Obligation ID
    val trigger: TriggerCondition,              // When obligation applies
    val scope: ObligationScope,                 // What it applies to
    val notes: List<String> = emptyList()       // Additional notes
) : GraphEdge {
    override val edgeType = EdgeType.REQUIRES
}

/**
 * Scope of an obligation - what does it apply to
 */
@Serializable
enum class ObligationScope(val displayName: String, val restrictiveness: Int) {
    MODIFIED_FILES("Modified Files", 1),        // Only modified files (MPL style)
    COMPONENT("Component", 2),                  // The specific component only
    DERIVATIVE_WORK("Derivative Work", 3),      // Entire derivative work (GPL style)
    DISTRIBUTED_WORK("Distributed Work", 4);    // Anything distributed

    companion object {
        fun fromString(value: String): ObligationScope {
            return when (value.lowercase().replace("-", "_").replace(" ", "_")) {
                "modified_files", "file", "files" -> MODIFIED_FILES
                "component" -> COMPONENT
                "derivative_work", "derivative" -> DERIVATIVE_WORK
                "distributed_work", "distributed" -> DISTRIBUTED_WORK
                else -> COMPONENT
            }
        }

        /**
         * Get the most restrictive scope from a list
         */
        fun mostRestrictive(scopes: List<ObligationScope>): ObligationScope {
            return scopes.maxByOrNull { it.restrictiveness } ?: COMPONENT
        }
    }
}

// =============================================================================
// Right Edge
// =============================================================================

/**
 * Right edge from License to Right
 */
@Serializable
data class RightEdge(
    override val id: String,
    override val sourceId: String,              // License ID
    override val targetId: String,              // Right ID
    val scope: RightScope = RightScope.UNLIMITED,
    val conditions: List<String> = emptyList()  // Conditions for this right
) : GraphEdge {
    override val edgeType = EdgeType.GRANTS
}

// =============================================================================
// Condition Edge
// =============================================================================

/**
 * Condition edge from License to Condition
 */
@Serializable
data class ConditionEdge(
    override val id: String,
    override val sourceId: String,              // License ID
    override val targetId: String,              // Condition ID
    val notes: List<String> = emptyList()
) : GraphEdge {
    override val edgeType = EdgeType.HAS_CONDITION
}

// =============================================================================
// Limitation Edge
// =============================================================================

/**
 * Limitation edge from License to Limitation
 */
@Serializable
data class LimitationEdge(
    override val id: String,
    override val sourceId: String,              // License ID
    override val targetId: String,              // Limitation ID
    val notes: List<String> = emptyList()
) : GraphEdge {
    override val edgeType = EdgeType.HAS_LIMITATION
}

// =============================================================================
// Use Case Trigger Edge
// =============================================================================

/**
 * Edge indicating a use case triggers an obligation
 */
@Serializable
data class UseCaseTriggerEdge(
    override val id: String,
    override val sourceId: String,              // Use Case ID
    override val targetId: String,              // Obligation ID
    val licenseId: String,                      // Which license this applies to
    val notes: List<String> = emptyList()
) : GraphEdge {
    override val edgeType = EdgeType.TRIGGERS
}

/**
 * Edge indicating a use case is exempt from an obligation
 */
@Serializable
data class UseCaseExemptionEdge(
    override val id: String,
    override val sourceId: String,              // Use Case ID
    override val targetId: String,              // Obligation ID
    val licenseId: String,                      // Which license this applies to
    val reason: String,                         // Why exempt
    val notes: List<String> = emptyList()
) : GraphEdge {
    override val edgeType = EdgeType.EXEMPT_FROM
}

// =============================================================================
// Helper Functions
// =============================================================================

/**
 * Create a compatibility edge ID from two license IDs
 */
fun createCompatibilityEdgeId(license1: String, license2: String): String {
    val sorted = listOf(license1.uppercase(), license2.uppercase()).sorted()
    return "${sorted[0]}-${sorted[1]}"
}

/**
 * Create an obligation edge ID
 */
fun createObligationEdgeId(licenseId: String, obligationId: String): String {
    return "${licenseId.uppercase()}-$obligationId"
}

/**
 * Create a right edge ID
 */
fun createRightEdgeId(licenseId: String, rightId: String): String {
    return "${licenseId.uppercase()}-$rightId"
}
