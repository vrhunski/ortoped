package com.ortoped.sbom

import com.ortoped.model.LicenseSuggestion

/**
 * Helper functions for mapping OrtoPed data to SBOM formats
 */
object SbomMapper {
    /**
     * Convert OrtoPed dependency ID to Package URL (purl)
     * Input format: "Type:namespace:name:version" or "Type:name:version"
     * Output format: "pkg:type/namespace/name@version"
     *
     * Examples:
     * - "NPM::lodash:4.17.21" -> "pkg:npm/lodash@4.17.21"
     * - "Maven:com.google.guava:guava:31.0-jre" -> "pkg:maven/com.google.guava/guava@31.0-jre"
     */
    fun toPurl(dependencyId: String): String {
        val parts = dependencyId.split(":")
        return when {
            parts.size >= 4 -> {
                val type = parts[0].lowercase()
                val namespace = parts[1]
                val name = parts[2]
                val version = parts.drop(3).joinToString(":")
                if (namespace.isEmpty()) {
                    "pkg:$type/$name@$version"
                } else {
                    "pkg:$type/$namespace/$name@$version"
                }
            }
            parts.size == 3 -> {
                val type = parts[0].lowercase()
                val name = parts[1]
                val version = parts[2]
                "pkg:$type/$name@$version"
            }
            else -> dependencyId
        }
    }

    /**
     * Sanitize ID for SPDX (only alphanumeric, dash, dot allowed)
     * Input: "NPM::lodash:4.17.21"
     * Output: "SPDXRef-npm-lodash-4.17.21"
     */
    fun toSpdxId(dependencyId: String): String {
        return "SPDXRef-" + dependencyId
            .replace(":", "-")
            .replace("/", "-")
            .replace("@", "-")
            .replace(Regex("[^A-Za-z0-9.-]"), "-")
    }

    /**
     * Convert license to SPDX expression
     * Priority: concludedLicense > AI spdxId > AI suggestedLicense > NOASSERTION
     */
    fun toSpdxExpression(license: String?, aiSuggestion: LicenseSuggestion?): String {
        return license
            ?: aiSuggestion?.spdxId
            ?: aiSuggestion?.suggestedLicense
            ?: "NOASSERTION"
    }

    /**
     * Map OrtoPed scope to CycloneDX scope
     */
    fun toCycloneDxScope(scope: String): String {
        return when (scope.lowercase()) {
            "compile", "dependencies", "required", "main" -> "required"
            "test", "dev", "devdependencies", "development" -> "optional"
            "runtime" -> "required"
            "provided" -> "optional"
            else -> "required"
        }
    }
}
