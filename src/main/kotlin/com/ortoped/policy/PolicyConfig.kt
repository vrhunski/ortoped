package com.ortoped.policy

import kotlinx.serialization.Serializable

/**
 * Root configuration loaded from YAML policy file
 */
@Serializable
data class PolicyConfig(
    val version: String,
    val name: String,
    val description: String? = null,
    val categories: Map<String, LicenseCategoryDefinition>,
    val rules: List<PolicyRule>,
    val settings: PolicySettings = PolicySettings()
)

/**
 * Definition of a license category
 */
@Serializable
data class LicenseCategoryDefinition(
    val description: String? = null,
    val licenses: List<String>
)

/**
 * Global policy settings
 */
@Serializable
data class PolicySettings(
    val aiSuggestions: AiSuggestionSettings = AiSuggestionSettings(),
    val failOn: FailOnSettings = FailOnSettings(),
    val exemptions: List<Exemption> = emptyList()
)

/**
 * AI suggestion handling settings
 */
@Serializable
data class AiSuggestionSettings(
    val acceptHighConfidence: Boolean = true,
    val treatMediumAsWarning: Boolean = true,
    val rejectLowConfidence: Boolean = true
)

/**
 * Failure condition settings
 */
@Serializable
data class FailOnSettings(
    val errors: Boolean = true,
    val warnings: Boolean = false
)

/**
 * Policy exemption for specific dependencies
 */
@Serializable
data class Exemption(
    val dependency: String,  // Supports glob patterns like "Maven:com.internal:*"
    val reason: String,
    val approvedBy: String? = null,
    val approvedDate: String? = null
)
