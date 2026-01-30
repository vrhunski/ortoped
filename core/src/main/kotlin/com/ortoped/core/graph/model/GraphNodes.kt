package com.ortoped.core.graph.model

import kotlinx.serialization.Serializable

/**
 * Base interface for all graph nodes
 */
interface GraphNode {
    val id: String
    val nodeType: NodeType
}

/**
 * Types of nodes in the license knowledge graph
 */
enum class NodeType {
    LICENSE,
    OBLIGATION,
    RIGHT,
    CONDITION,
    LIMITATION,
    USE_CASE,
    REGULATION
}

// =============================================================================
// License Node
// =============================================================================

/**
 * A software license node in the knowledge graph
 */
@Serializable
data class LicenseNode(
    override val id: String,                    // Uppercase SPDX ID: "GPL-3.0-ONLY"
    val spdxId: String,                         // Canonical SPDX identifier: "GPL-3.0-only"
    val name: String,                           // Human-readable: "GNU General Public License v3.0 only"
    val category: LicenseCategory,              // PERMISSIVE, COPYLEFT, etc.
    val copyleftStrength: CopyleftStrength,     // NONE, WEAK, STRONG, NETWORK
    val isOsiApproved: Boolean = false,
    val isFsfFree: Boolean = false,
    val isDeprecated: Boolean = false,
    val version: String? = null,                // "3.0"
    val family: String? = null,                 // "GPL" family
    val textHash: String? = null,               // Hash of license text for comparison
    val seeAlso: List<String> = emptyList()     // Reference URLs
) : GraphNode {
    override val nodeType = NodeType.LICENSE
}

/**
 * License categories for classification
 */
@Serializable
enum class LicenseCategory(val displayName: String, val riskLevel: Int) {
    PUBLIC_DOMAIN("Public Domain", 0),          // CC0, Unlicense, WTFPL
    PERMISSIVE("Permissive", 1),                // MIT, BSD, Apache
    WEAK_COPYLEFT("Weak Copyleft", 2),          // LGPL, MPL, EPL
    STRONG_COPYLEFT("Strong Copyleft", 3),      // GPL
    NETWORK_COPYLEFT("Network Copyleft", 4),    // AGPL
    PROPRIETARY("Proprietary", 5),              // Commercial licenses
    SOURCE_AVAILABLE("Source Available", 5),    // BSL, Commons Clause
    UNKNOWN("Unknown", 6);

    companion object {
        fun fromString(value: String): LicenseCategory {
            return when (value.lowercase().replace("-", "_").replace(" ", "_")) {
                "public_domain" -> PUBLIC_DOMAIN
                "permissive" -> PERMISSIVE
                "weak_copyleft", "copyleft_limited" -> WEAK_COPYLEFT
                "strong_copyleft", "copyleft" -> STRONG_COPYLEFT
                "network_copyleft" -> NETWORK_COPYLEFT
                "proprietary", "commercial" -> PROPRIETARY
                "source_available" -> SOURCE_AVAILABLE
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Copyleft strength levels - determines how copyleft propagates
 */
@Serializable
enum class CopyleftStrength(val displayName: String, val propagationLevel: Int) {
    NONE("None", 0),                    // Permissive licenses - no copyleft
    FILE("File-level", 1),              // MPL - copyleft applies to modified files only
    LIBRARY("Library-level", 2),        // LGPL - copyleft if statically linked
    STRONG("Strong", 3),                // GPL - derivative works must be same license
    NETWORK("Network", 4);              // AGPL - network distribution triggers copyleft

    companion object {
        fun fromString(value: String): CopyleftStrength {
            return when (value.lowercase().replace("-", "_").replace(" ", "_")) {
                "none" -> NONE
                "file", "file_level" -> FILE
                "library", "library_level", "weak" -> LIBRARY
                "strong" -> STRONG
                "network" -> NETWORK
                else -> NONE
            }
        }
    }
}

// =============================================================================
// Obligation Node
// =============================================================================

/**
 * An obligation imposed by a license
 */
@Serializable
data class ObligationNode(
    override val id: String,                    // "attribution", "source-disclosure"
    val name: String,                           // "Attribution"
    val description: String,                    // What must be done
    val triggerCondition: TriggerCondition,     // When this applies
    val effort: EffortLevel,                    // How hard to comply
    val examples: List<String> = emptyList(),   // Example compliance actions
    val legalText: String? = null               // Optional legal language
) : GraphNode {
    override val nodeType = NodeType.OBLIGATION
}

/**
 * When an obligation is triggered
 */
@Serializable
enum class TriggerCondition(val displayName: String) {
    ALWAYS("Always"),                           // Always applies (e.g., patent grant)
    ON_DISTRIBUTION("On Distribution"),         // When distributing binaries
    ON_MODIFICATION("On Modification"),         // When modifying source code
    ON_DERIVATIVE("On Derivative Work"),        // When creating derivative work
    ON_NETWORK_USE("On Network Use"),           // When providing as network service (AGPL)
    ON_STATIC_LINKING("On Static Linking"),     // When statically linking
    ON_DYNAMIC_LINKING("On Dynamic Linking"),   // When dynamically linking
    ON_PATENT_CLAIM("On Patent Claim"),         // When making patent claims
    CONDITIONAL("Conditional");                 // Complex conditions (see properties)

    companion object {
        fun fromString(value: String): TriggerCondition {
            return when (value.lowercase().replace("-", "_").replace(" ", "_")) {
                "always" -> ALWAYS
                "on_distribution", "distribution" -> ON_DISTRIBUTION
                "on_modification", "modification" -> ON_MODIFICATION
                "on_derivative", "derivative" -> ON_DERIVATIVE
                "on_network_use", "network_use", "network" -> ON_NETWORK_USE
                "on_static_linking", "static_linking" -> ON_STATIC_LINKING
                "on_dynamic_linking", "dynamic_linking" -> ON_DYNAMIC_LINKING
                "on_patent_claim", "patent_claim" -> ON_PATENT_CLAIM
                else -> CONDITIONAL
            }
        }
    }
}

/**
 * Effort level required to comply with an obligation
 */
@Serializable
enum class EffortLevel(val displayName: String, val level: Int) {
    TRIVIAL("Trivial", 0),              // Include license file
    LOW("Low", 1),                      // Add copyright notice
    MEDIUM("Medium", 2),                // Document modifications
    HIGH("High", 3),                    // Provide source code
    VERY_HIGH("Very High", 4);          // Full source disclosure + build instructions

    companion object {
        fun fromString(value: String): EffortLevel {
            return when (value.lowercase().replace("-", "_").replace(" ", "_")) {
                "trivial" -> TRIVIAL
                "low" -> LOW
                "medium", "moderate" -> MEDIUM
                "high" -> HIGH
                "very_high", "veryhigh" -> VERY_HIGH
                else -> MEDIUM
            }
        }
    }
}

// =============================================================================
// Right Node
// =============================================================================

/**
 * A right granted by a license
 */
@Serializable
data class RightNode(
    override val id: String,                    // "commercial-use", "modify"
    val name: String,                           // "Commercial Use"
    val description: String,                    // What is permitted
    val scope: RightScope = RightScope.UNLIMITED
) : GraphNode {
    override val nodeType = NodeType.RIGHT
}

/**
 * Scope of a granted right
 */
@Serializable
enum class RightScope(val displayName: String) {
    UNLIMITED("Unlimited"),             // No restrictions
    LIMITED("Limited"),                 // Some conditions apply
    RESTRICTED("Restricted"),           // Significant limitations
    CONDITIONAL("Conditional");         // Complex conditions

    companion object {
        fun fromString(value: String): RightScope {
            return when (value.lowercase()) {
                "unlimited" -> UNLIMITED
                "limited" -> LIMITED
                "restricted" -> RESTRICTED
                "conditional" -> CONDITIONAL
                else -> LIMITED
            }
        }
    }
}

// =============================================================================
// Condition Node
// =============================================================================

/**
 * A condition that must be met to exercise rights
 */
@Serializable
data class ConditionNode(
    override val id: String,                    // "same-license", "include-copyright"
    val name: String,                           // "Same License"
    val description: String,                    // What must be satisfied
    val conditionType: ConditionType
) : GraphNode {
    override val nodeType = NodeType.CONDITION
}

/**
 * Types of license conditions
 */
@Serializable
enum class ConditionType(val displayName: String) {
    COPYLEFT("Copyleft"),                       // Must use same license
    NOTICE("Notice"),                           // Must include notice
    SOURCE_DISCLOSURE("Source Disclosure"),     // Must provide source
    STATE_CHANGES("State Changes"),             // Must document changes
    PATENT_GRANT("Patent Grant"),               // Grant patent rights
    NETWORK_COPYLEFT("Network Copyleft"),       // Network triggers disclosure
    OTHER("Other");

    companion object {
        fun fromString(value: String): ConditionType {
            return when (value.lowercase().replace("-", "_").replace(" ", "_")) {
                "copyleft" -> COPYLEFT
                "notice" -> NOTICE
                "source_disclosure" -> SOURCE_DISCLOSURE
                "state_changes" -> STATE_CHANGES
                "patent_grant" -> PATENT_GRANT
                "network_copyleft" -> NETWORK_COPYLEFT
                else -> OTHER
            }
        }
    }
}

// =============================================================================
// Limitation Node
// =============================================================================

/**
 * A limitation imposed by a license
 */
@Serializable
data class LimitationNode(
    override val id: String,                    // "no-warranty", "no-liability"
    val name: String,                           // "No Warranty"
    val description: String,                    // What is limited/excluded
    val limitationType: LimitationType
) : GraphNode {
    override val nodeType = NodeType.LIMITATION
}

/**
 * Types of license limitations
 */
@Serializable
enum class LimitationType(val displayName: String) {
    WARRANTY("Warranty Disclaimer"),
    LIABILITY("Liability Limitation"),
    TRADEMARK("Trademark Restriction"),
    PATENT("Patent Limitation"),
    OTHER("Other");

    companion object {
        fun fromString(value: String): LimitationType {
            return when (value.lowercase().replace("-", "_").replace(" ", "_")) {
                "warranty" -> WARRANTY
                "liability" -> LIABILITY
                "trademark" -> TRADEMARK
                "patent" -> PATENT
                else -> OTHER
            }
        }
    }
}

// =============================================================================
// Use Case Node
// =============================================================================

/**
 * A use case context (how software is being used)
 */
@Serializable
data class UseCaseNode(
    override val id: String,                    // "saas", "embedded", "internal"
    val name: String,                           // "SaaS Application"
    val description: String,
    val distributionType: DistributionType,
    val linkingType: LinkingType? = null
) : GraphNode {
    override val nodeType = NodeType.USE_CASE
}

/**
 * How software is distributed
 */
@Serializable
enum class DistributionType(val displayName: String) {
    NONE("No Distribution"),            // Internal use only
    BINARY("Binary Distribution"),      // Distribute compiled binaries
    SOURCE("Source Distribution"),      // Distribute source code
    NETWORK("Network Service"),         // SaaS/network service (no binary distribution)
    EMBEDDED("Embedded");               // Embedded in hardware

    companion object {
        fun fromString(value: String): DistributionType {
            return when (value.lowercase().replace("-", "_").replace(" ", "_")) {
                "none", "internal" -> NONE
                "binary" -> BINARY
                "source" -> SOURCE
                "network", "saas" -> NETWORK
                "embedded" -> EMBEDDED
                else -> BINARY
            }
        }
    }
}

/**
 * How dependencies are linked
 */
@Serializable
enum class LinkingType(val displayName: String) {
    STATIC("Static Linking"),           // Compiled into binary
    DYNAMIC("Dynamic Linking"),         // Dynamically linked at runtime
    PROCESS_BOUNDARY("Process Boundary"), // Separate process communication
    NETWORK_BOUNDARY("Network Boundary"); // Network API communication

    companion object {
        fun fromString(value: String): LinkingType {
            return when (value.lowercase().replace("-", "_").replace(" ", "_")) {
                "static" -> STATIC
                "dynamic" -> DYNAMIC
                "process_boundary", "process" -> PROCESS_BOUNDARY
                "network_boundary", "network", "api" -> NETWORK_BOUNDARY
                else -> DYNAMIC
            }
        }
    }
}

// =============================================================================
// Standard Obligations IDs (for consistency)
// =============================================================================

/**
 * Standard obligation identifiers used throughout the system
 */
object StandardObligations {
    const val ATTRIBUTION = "attribution"
    const val SOURCE_DISCLOSURE = "source-disclosure"
    const val STATE_CHANGES = "state-changes"
    const val SAME_LICENSE = "same-license"
    const val NETWORK_DISCLOSURE = "network-disclosure"
    const val PATENT_GRANT = "patent-grant"
    const val NOTICE_FILE = "notice-file"
    const val INCLUDE_LICENSE = "include-license"
    const val INCLUDE_COPYRIGHT = "include-copyright"
    const val DISCLOSE_SOURCE = "disclose-source"
}

/**
 * Standard right identifiers used throughout the system
 */
object StandardRights {
    const val COMMERCIAL_USE = "commercial-use"
    const val MODIFY = "modify"
    const val DISTRIBUTE = "distribute"
    const val PRIVATE_USE = "private-use"
    const val PATENT_USE = "patent-use"
    const val SUBLICENSE = "sublicense"
}

/**
 * License family identifiers
 */
object LicenseFamilies {
    const val MIT = "MIT"
    const val BSD = "BSD"
    const val APACHE = "Apache"
    const val GPL = "GPL"
    const val LGPL = "LGPL"
    const val AGPL = "AGPL"
    const val MPL = "MPL"
    const val EPL = "EPL"
    const val CC = "CC"
    const val CDDL = "CDDL"
}
