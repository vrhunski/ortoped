package com.ortoped.core.model

import kotlinx.serialization.Serializable

// ============================================================================
// CORE CURATION MODELS
// Used across API and Core modules for curation workflow
// ============================================================================

/**
 * Curation status enum
 */
enum class CurationStatus {
    PENDING,    // Awaiting review
    ACCEPTED,   // AI suggestion confirmed
    REJECTED,   // AI suggestion wrong, using original/manual
    MODIFIED    // Curator chose different license
}

/**
 * Curation session status enum
 */
enum class CurationSessionStatus {
    IN_PROGRESS,
    COMPLETED,   // All items reviewed
    APPROVED     // Final approval given
}

/**
 * Priority level for smart filtering
 */
enum class PriorityLevel {
    CRITICAL,    // High-risk licenses, conflicts
    HIGH,        // Medium confidence AI suggestions
    MEDIUM,      // Standard review items
    LOW          // High-confidence, low-risk items
}

/**
 * Curation decision for a single dependency
 */
@Serializable
data class CurationDecision(
    val id: String,
    val scanId: String,
    val dependencyId: String,
    val dependencyName: String,
    val dependencyVersion: String,

    // Original state
    val originalLicense: String?,
    val aiSuggestion: LicenseSuggestion?,

    // Decision
    val status: String,
    val curatedLicense: String?,
    val comment: String?,
    val curatorId: String?,
    val curatedAt: String?
)

/**
 * Curation session tracking
 */
@Serializable
data class CurationSession(
    val id: String,
    val scanId: String,
    val status: String,
    val statistics: CurationStatistics,
    val approval: CurationApproval?,
    val createdAt: String,
    val updatedAt: String
)

/**
 * Statistics for a curation session
 */
@Serializable
data class CurationStatistics(
    val total: Int,
    val pending: Int,
    val accepted: Int,
    val rejected: Int,
    val modified: Int
) {
    val completionPercentage: Double
        get() = if (total > 0) ((total - pending).toDouble() / total * 100) else 0.0

    val isComplete: Boolean
        get() = pending == 0
}

/**
 * Curation approval info
 */
@Serializable
data class CurationApproval(
    val approvedBy: String,
    val approvedAt: String,
    val comment: String?
)

// ============================================================================
// PRIORITY MODELS
// ============================================================================

/**
 * Priority information for a curation item
 */
@Serializable
data class CurationPriority(
    val dependencyId: String,
    val priority: String,
    val score: Double, // 0.0 - 1.0
    val factors: List<PriorityFactor>
)

/**
 * Factor contributing to priority score
 */
@Serializable
data class PriorityFactor(
    val name: String,
    val weight: Double,
    val description: String
)

// ============================================================================
// SPDX MODELS
// ============================================================================

/**
 * SPDX license information
 */
@Serializable
data class SpdxLicense(
    val licenseId: String,
    val name: String,
    val text: String? = null,
    val isOsiApproved: Boolean = false,
    val isFsfLibre: Boolean = false,
    val isDeprecated: Boolean = false,
    val seeAlso: List<String> = emptyList()
)

/**
 * Result of SPDX license validation
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
 * License compatibility check result
 */
@Serializable
data class LicenseCompatibilityResult(
    val license1: String,
    val license2: String,
    val compatible: Boolean,
    val reason: String?,
    val notes: List<String> = emptyList()
)

// ============================================================================
// TEMPLATE MODELS
// ============================================================================

/**
 * Curation template definition
 */
@Serializable
data class CurationTemplate(
    val id: String,
    val name: String,
    val description: String?,
    val conditions: List<TemplateCondition>,
    val actions: List<TemplateAction>,
    val createdBy: String?,
    val isGlobal: Boolean,
    val usageCount: Int
)

/**
 * Template condition type
 */
enum class TemplateConditionType {
    AI_CONFIDENCE_EQUALS,
    AI_CONFIDENCE_ABOVE,
    AI_CONFIDENCE_BELOW,
    LICENSE_EQUALS,
    LICENSE_IN,
    LICENSE_CONTAINS,
    ORIGINAL_LICENSE_IN,
    DEPENDENCY_NAME_MATCHES,
    DEPENDENCY_NAME_CONTAINS,
    SCOPE_EQUALS,
    HAS_AI_SUGGESTION,
    NO_AI_SUGGESTION
}

/**
 * Template condition
 */
@Serializable
data class TemplateCondition(
    val type: String,
    val value: String // Single value or JSON array depending on type
)

/**
 * Template action type
 */
enum class TemplateActionType {
    ACCEPT_AI,
    REJECT,
    MODIFY_LICENSE,
    SET_PRIORITY,
    ADD_COMMENT
}

/**
 * Template action
 */
@Serializable
data class TemplateAction(
    val type: String,
    val value: String? = null
)

/**
 * Result of applying a template
 */
@Serializable
data class TemplateApplicationResult(
    val templateId: String,
    val templateName: String,
    val matchedCount: Int,
    val appliedCount: Int,
    val affectedDependencies: List<String>
)

// ============================================================================
// INCREMENTAL CURATION MODELS
// ============================================================================

/**
 * Change type for incremental curation
 */
enum class DependencyChangeType {
    ADDED,
    UPDATED,
    REMOVED
}

/**
 * Dependency change between scans
 */
@Serializable
data class DependencyChange(
    val dependencyId: String,
    val dependencyName: String,
    val changeType: String,
    val previousVersion: String?,
    val currentVersion: String?,
    val previousLicense: String?,
    val currentLicense: String?,
    val previousCuration: CurationDecision?
)

/**
 * Summary of changes between scans
 */
@Serializable
data class IncrementalChanges(
    val previousScanId: String?,
    val added: List<DependencyChange>,
    val updated: List<DependencyChange>,
    val removed: List<DependencyChange>,
    val unchangedCount: Int
) {
    val totalChanges: Int
        get() = added.size + updated.size + removed.size

    val hasChanges: Boolean
        get() = totalChanges > 0
}
