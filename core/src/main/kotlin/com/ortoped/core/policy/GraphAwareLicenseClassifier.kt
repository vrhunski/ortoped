package com.ortoped.core.policy

import com.ortoped.core.graph.LicenseKnowledgeGraph
import com.ortoped.core.graph.model.LicenseCategory
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * License classifier that delegates to the Knowledge Graph for category lookups,
 * falling back to YAML-based PolicyConfig classification for licenses not in the graph.
 *
 * This bridges the graph's richer semantic data (copyleft strength, family, OSI approval)
 * into the policy evaluation flow while preserving full backward compatibility with
 * existing policy.yml files.
 */
class GraphAwareLicenseClassifier(
    private val config: PolicyConfig,
    private val graph: LicenseKnowledgeGraph
) {
    private val fallbackClassifier = LicenseClassifier(config)

    // Map graph LicenseCategory to policy category strings used in PolicyConfig
    private val graphCategoryToPolicyCategory = mapOf(
        LicenseCategory.PUBLIC_DOMAIN to "permissive",
        LicenseCategory.PERMISSIVE to "permissive",
        LicenseCategory.WEAK_COPYLEFT to "weak-copyleft",
        LicenseCategory.STRONG_COPYLEFT to "copyleft",
        LicenseCategory.NETWORK_COPYLEFT to "network-copyleft",
        LicenseCategory.PROPRIETARY to "proprietary",
        LicenseCategory.SOURCE_AVAILABLE to "proprietary",
        LicenseCategory.UNKNOWN to "unknown"
    )

    /**
     * Classify a license using the Knowledge Graph first, falling back to YAML config.
     */
    fun classify(license: String?): String {
        if (license == null || license.isBlank() ||
            license.uppercase() == "NOASSERTION" || license.uppercase() == "UNKNOWN") {
            return "unknown"
        }

        // Handle expressions — delegate to fallback which has OR/AND parsing
        if (license.contains(" OR ") || license.contains(" AND ")) {
            return classifyExpression(license)
        }

        // Try graph lookup first
        val graphCategory = classifyFromGraph(license)
        if (graphCategory != null) {
            logger.debug { "Graph classified '$license' as '$graphCategory'" }
            return graphCategory
        }

        // Fall back to YAML-based classification
        val fallbackCategory = fallbackClassifier.classify(license)
        logger.debug { "Fallback classified '$license' as '$fallbackCategory'" }
        return fallbackCategory
    }

    /**
     * Look up a license in the Knowledge Graph and map to a policy category string.
     */
    private fun classifyFromGraph(license: String): String? {
        val node = graph.getLicense(license.uppercase()) ?: return null

        // Check if the policy config defines a custom category for this license.
        // If it does, policy config takes precedence (user override).
        val policyOverride = findInPolicyConfig(license)
        if (policyOverride != null) {
            return policyOverride
        }

        return graphCategoryToPolicyCategory[node.category]
    }

    /**
     * Check if the license is explicitly listed in any policy config category.
     */
    private fun findInPolicyConfig(license: String): String? {
        val upper = license.uppercase()
        for ((categoryName, definition) in config.categories) {
            if (definition.licenses.any { it.uppercase() == upper }) {
                return categoryName
            }
        }
        return null
    }

    /**
     * Classify OR/AND expressions using graph-enhanced single-license classification.
     */
    private fun classifyExpression(expression: String): String {
        val normalized = expression.uppercase()

        if (normalized.contains(" OR ")) {
            return classifyOrExpression(expression)
        }
        if (normalized.contains(" AND ")) {
            return classifyAndExpression(expression)
        }

        return classify(expression.replace("(", "").replace(")", "").trim())
    }

    private fun classifyOrExpression(expression: String): String {
        val licenses = expression.split(Regex("\\s+OR\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim().replace("(", "").replace(")", "") }

        val categories = licenses.map { classifySingle(it) }

        if (categories.any { it == "unknown" }) return "unknown"

        val uniqueCategories = categories.toSet()
        return when {
            uniqueCategories.size == 1 -> uniqueCategories.first()
            uniqueCategories.all { it == "permissive" } -> "permissive"
            uniqueCategories.any { it.contains("copyleft") } -> "dual-license"
            else -> categories.minByOrNull { categoryRiskLevel(it) } ?: "unknown"
        }
    }

    private fun classifyAndExpression(expression: String): String {
        val licenses = expression.split(Regex("\\s+AND\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim().replace("(", "").replace(")", "") }

        val categories = licenses.map { classifySingle(it) }

        if (categories.any { it == "unknown" }) return "unknown"

        return categories.maxByOrNull { categoryRiskLevel(it) } ?: "unknown"
    }

    private fun classifySingle(license: String): String {
        val trimmed = license.trim()
        if (trimmed.isBlank() || trimmed.uppercase() == "NOASSERTION" || trimmed.uppercase() == "UNKNOWN") {
            return "unknown"
        }

        // Try graph first
        val graphResult = classifyFromGraph(trimmed)
        if (graphResult != null) return graphResult

        // Fallback
        return fallbackClassifier.classify(trimmed)
    }

    private fun categoryRiskLevel(category: String): Int = when (category) {
        "permissive" -> 1
        "weak-copyleft" -> 2
        "copyleft" -> 3
        "strong-copyleft" -> 4
        "network-copyleft" -> 5
        "proprietary" -> 5
        "unknown" -> 6
        else -> 6
    }

    /**
     * Check if a license needs manual review (delegates to fallback for expression parsing).
     */
    fun requiresReview(license: String?): String? = fallbackClassifier.requiresReview(license)

    /**
     * Get all licenses in a specific category (from policy config).
     */
    fun getLicensesInCategory(categoryName: String): List<String> =
        fallbackClassifier.getLicensesInCategory(categoryName)

    /**
     * Get all defined categories.
     */
    fun getCategories(): Set<String> = fallbackClassifier.getCategories()
}
