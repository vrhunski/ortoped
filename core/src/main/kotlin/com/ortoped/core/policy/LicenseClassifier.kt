package com.ortoped.core.policy

/**
 * Classifies SPDX license identifiers into categories
 */
class LicenseClassifier(private val config: PolicyConfig) {

    private val licenseToCategory: Map<String, String> by lazy {
        buildLicenseMap()
    }

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
     * Classify a license into its category
     * @param license SPDX license ID or null
     * @return category name (defaults to "unknown" if not found)
     */
    fun classify(license: String?): String {
        if (license == null || license == "NOASSERTION" || license == "Unknown") {
            return "unknown"
        }
        return licenseToCategory[license.uppercase()] ?: "unknown"
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
}
