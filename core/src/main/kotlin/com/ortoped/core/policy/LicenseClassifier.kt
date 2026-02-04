package com.ortoped.core.policy

/**
 * Classifies SPDX license identifiers into categories.
 * Supports SPDX license expressions with OR and AND operators.
 */
class LicenseClassifier(private val config: PolicyConfig) {

    private val licenseToCategory: Map<String, String> by lazy {
        buildLicenseMap()
    }

    // Category risk levels (higher = more restrictive)
    private val categoryRiskLevels = mapOf(
        "permissive" to 1,
        "weak-copyleft" to 2,
        "copyleft" to 3,
        "strong-copyleft" to 4,
        "network-copyleft" to 5,
        "proprietary" to 5,
        "unknown" to 6  // Unknown is highest risk
    )

    /**
     * Build reverse lookup map from license ID to category name
     */
    private fun buildLicenseMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        config.categories.forEach { (categoryName, definition) ->
            definition.licenses.forEach { license ->
                map[license.uppercase()] = categoryName
            }
        }
        return map
    }

    /**
     * Classify a license into its category.
     * Handles license expressions (OR/AND operators).
     * @param license SPDX license ID or expression, or null
     * @return category name (defaults to "unknown" if not found)
     */
    fun classify(license: String?): String {
        if (license == null || license.uppercase() == "NOASSERTION" || license.uppercase() == "UNKNOWN" || license.isBlank()) {
            return "unknown"
        }

        // Try direct lookup first
        val directCategory = licenseToCategory[license.uppercase()]
        if (directCategory != null) {
            return directCategory
        }

        // Handle license expressions
        return classifyExpression(license)
    }

    /**
     * Classify a license expression (containing OR/AND operators)
     */
    private fun classifyExpression(expression: String): String {
        val normalized = expression.uppercase()

        // Check for OR expression
        if (normalized.contains(" OR ")) {
            return classifyOrExpression(expression)
        }

        // Check for AND expression
        if (normalized.contains(" AND ")) {
            return classifyAndExpression(expression)
        }

        // Check for parentheses-based expressions
        if (normalized.contains("(") && normalized.contains(")")) {
            // Try to parse the inner license
            val inner = normalized.replace("(", "").replace(")", "").trim()
            return classify(inner)
        }

        // Not found in any category
        return "unknown"
    }

    /**
     * Classify OR expression - returns the LEAST restrictive category
     * (since user can choose the most permissive option)
     * But if categorization is ambiguous, mark as requiring review
     */
    private fun classifyOrExpression(expression: String): String {
        val licenses = expression.split(Regex("\\s+OR\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim().replace("(", "").replace(")", "") }

        val categories = licenses.map { classifySingleLicense(it) }

        // If any license is unknown, the whole expression needs review
        if (categories.any { it == "unknown" }) {
            return "unknown"
        }

        // For OR expressions, we consider the category of all options
        // If all are permissive, it's permissive
        // If there's a mix, it depends on the user's choice
        val uniqueCategories = categories.toSet()

        return when {
            uniqueCategories.size == 1 -> uniqueCategories.first()
            uniqueCategories.all { it == "permissive" } -> "permissive"
            // If there's a mix including copyleft, mark for review
            uniqueCategories.any { it.contains("copyleft") } -> "dual-license"
            else -> categories.minByOrNull { categoryRiskLevels[it] ?: 99 } ?: "unknown"
        }
    }

    /**
     * Classify AND expression - returns the MOST restrictive category
     * (since user must comply with all)
     */
    private fun classifyAndExpression(expression: String): String {
        val licenses = expression.split(Regex("\\s+AND\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim().replace("(", "").replace(")", "") }

        val categories = licenses.map { classifySingleLicense(it) }

        // If any license is unknown, the whole expression is problematic
        if (categories.any { it == "unknown" }) {
            return "unknown"
        }

        // For AND expressions, return the most restrictive category
        return categories.maxByOrNull { categoryRiskLevels[it] ?: 0 } ?: "unknown"
    }

    /**
     * Classify a single license (no expressions)
     */
    private fun classifySingleLicense(license: String): String {
        val normalized = license.uppercase().trim()

        if (normalized == "NOASSERTION" || normalized == "UNKNOWN" || normalized.isBlank()) {
            return "unknown"
        }

        // Try direct lookup
        licenseToCategory[normalized]?.let { return it }

        // Try without version suffix (e.g., "MIT" from "MIT-1.0")
        val withoutVersion = normalized.replace(Regex("-[0-9.]+(-only|-or-later)?$"), "")
        licenseToCategory[withoutVersion]?.let { return it }

        // Try base license for variations (e.g., "GPL-2.0-only" -> "GPL-2.0" -> "GPL")
        val baseVariations = listOf(
            normalized.replace("-only", "").replace("-or-later", ""),
            normalized.substringBefore("-"),
            normalized.substringBefore("+")
        )
        for (variation in baseVariations) {
            licenseToCategory[variation]?.let { return it }
        }

        return "unknown"
    }

    /**
     * Get all licenses in a specific category
     */
    fun getLicensesInCategory(categoryName: String): List<String> {
        return config.categories[categoryName]?.licenses ?: emptyList()
    }

    /**
     * Get all defined categories
     */
    fun getCategories(): Set<String> {
        return config.categories.keys
    }

    /**
     * Check if a license expression needs manual review
     * Returns a reason if review is needed, null otherwise
     */
    fun requiresReview(license: String?): String? {
        if (license == null) return "No license specified"

        val normalized = license.uppercase()

        if (normalized == "NOASSERTION" || normalized == "UNKNOWN") {
            return "License is unknown or not asserted"
        }

        if (normalized.contains(" OR ")) {
            val licenses = normalized.split(Regex("\\s+OR\\s+"))
            val categories = licenses.map { classifySingleLicense(it.trim()) }.toSet()

            // OR with copyleft and non-copyleft needs explicit choice
            if (categories.any { it.contains("copyleft") } && categories.any { !it.contains("copyleft") }) {
                return "OR expression contains both copyleft and non-copyleft options - explicit choice required"
            }

            // OR with any unknown
            if (categories.contains("unknown")) {
                return "OR expression contains unknown license(s)"
            }
        }

        if (normalized.contains(" AND ")) {
            val licenses = normalized.split(Regex("\\s+AND\\s+"))
            val categories = licenses.map { classifySingleLicense(it.trim()) }.toSet()

            // AND with copyleft
            if (categories.any { it.contains("copyleft") }) {
                return "AND expression includes copyleft license - must comply with all obligations"
            }

            // AND with any unknown
            if (categories.contains("unknown")) {
                return "AND expression contains unknown license(s)"
            }
        }

        if (classify(license) == "unknown") {
            return "License not recognized in policy categories"
        }

        return null
    }
}
