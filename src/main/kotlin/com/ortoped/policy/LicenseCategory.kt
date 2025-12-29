package com.ortoped.policy

/**
 * Standard license categories for policy evaluation
 */
enum class LicenseCategory(val displayName: String, val riskLevel: Int) {
    PERMISSIVE("Permissive", 1),
    COPYLEFT_LIMITED("Weak Copyleft", 2),
    COPYLEFT("Strong Copyleft", 3),
    COMMERCIAL("Commercial", 4),
    PUBLIC_DOMAIN("Public Domain", 0),
    UNKNOWN("Unknown", 5);

    companion object {
        fun fromString(value: String): LicenseCategory? {
            return when (value.lowercase().replace("-", "_").replace(" ", "_")) {
                "permissive" -> PERMISSIVE
                "copyleft_limited", "weak_copyleft" -> COPYLEFT_LIMITED
                "copyleft", "strong_copyleft" -> COPYLEFT
                "commercial", "proprietary" -> COMMERCIAL
                "public_domain" -> PUBLIC_DOMAIN
                "unknown" -> UNKNOWN
                else -> null
            }
        }
    }
}
