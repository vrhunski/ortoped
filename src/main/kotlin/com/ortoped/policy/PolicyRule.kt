package com.ortoped.policy

import kotlinx.serialization.Serializable

/**
 * A single policy rule definition
 */
@Serializable
data class PolicyRule(
    val id: String,
    val name: String,
    val description: String? = null,
    val severity: Severity,
    val enabled: Boolean = true,

    // Targeting: can specify category, allowlist, or denylist
    val category: String? = null,
    val allowlist: List<String>? = null,
    val denylist: List<String>? = null,

    // Scope filtering (empty = all scopes)
    val scopes: List<String> = emptyList(),

    // Action to take
    val action: RuleAction,

    // Message template with {{license}} and {{dependency}} placeholders
    val message: String? = null
)

/**
 * Rule severity levels
 */
@Serializable
enum class Severity {
    ERROR, WARNING, INFO;

    companion object {
        fun fromString(value: String): Severity {
            return when (value.lowercase()) {
                "error" -> ERROR
                "warning" -> WARNING
                "info" -> INFO
                else -> WARNING
            }
        }
    }
}

/**
 * Actions to take when a rule matches
 */
@Serializable
enum class RuleAction {
    ALLOW, DENY, REVIEW;

    companion object {
        fun fromString(value: String): RuleAction {
            return when (value.lowercase()) {
                "allow" -> ALLOW
                "deny" -> DENY
                "review" -> REVIEW
                else -> REVIEW
            }
        }
    }
}
