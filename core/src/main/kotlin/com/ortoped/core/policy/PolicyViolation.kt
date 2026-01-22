package com.ortoped.core.policy

import kotlinx.serialization.Serializable

/**
 * Represents a policy violation found during evaluation
 */
@Serializable
data class PolicyViolation(
    val ruleId: String,
    val ruleName: String,
    val severity: Severity,
    val dependencyId: String,
    val dependencyName: String,
    val dependencyVersion: String,
    val license: String,
    val licenseCategory: String?,
    val scope: String,
    val message: String,
    val aiSuggestion: AiFix? = null
)

/**
 * AI-suggested fix for a policy violation
 */
@Serializable
data class AiFix(
    val suggestion: String,
    val alternativeDependencies: List<String> = emptyList(),
    val reasoning: String,
    val confidence: String  // HIGH, MEDIUM, LOW
)
