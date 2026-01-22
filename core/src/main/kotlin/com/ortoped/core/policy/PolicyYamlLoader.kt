package com.ortoped.core.policy

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Loads and validates policy YAML files
 */
class PolicyYamlLoader {

    private val yaml = Yaml.default

    /**
     * Load policy from a YAML file
     */
    fun load(policyFile: File): PolicyConfig {
        logger.info { "Loading policy from: ${policyFile.absolutePath}" }

        if (!policyFile.exists()) {
            throw PolicyLoadException("Policy file not found: ${policyFile.absolutePath}")
        }

        return try {
            val content = policyFile.readText()
            yaml.decodeFromString(PolicyConfig.serializer(), content)
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse policy file" }
            throw PolicyLoadException("Invalid policy file: ${e.message}", e)
        }
    }

    /**
     * Load policy from file or use default if file is null/doesn't exist
     */
    fun loadOrDefault(policyFile: File?): PolicyConfig {
        return if (policyFile != null && policyFile.exists()) {
            load(policyFile)
        } else {
            logger.info { "Using default policy configuration" }
            createDefaultPolicy()
        }
    }

    /**
     * Create an embedded default policy
     */
    private fun createDefaultPolicy(): PolicyConfig {
        return PolicyConfig(
            version = "1.0",
            name = "Default OrtoPed Policy",
            description = "Default policy that flags unknown licenses",
            categories = defaultCategories(),
            rules = defaultRules(),
            settings = PolicySettings()
        )
    }

    private fun defaultCategories(): Map<String, LicenseCategoryDefinition> = mapOf(
        "permissive" to LicenseCategoryDefinition(
            description = "Permissive open source licenses",
            licenses = listOf(
                "MIT", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause",
                "ISC", "Unlicense", "0BSD", "CC0-1.0"
            )
        ),
        "copyleft" to LicenseCategoryDefinition(
            description = "Strong copyleft licenses",
            licenses = listOf(
                "GPL-2.0-only", "GPL-2.0-or-later",
                "GPL-3.0-only", "GPL-3.0-or-later",
                "AGPL-3.0-only", "AGPL-3.0-or-later"
            )
        ),
        "copyleft-limited" to LicenseCategoryDefinition(
            description = "Weak copyleft licenses with limited scope",
            licenses = listOf(
                "LGPL-2.0-only", "LGPL-2.1-only", "LGPL-3.0-only",
                "MPL-2.0", "EPL-1.0", "EPL-2.0"
            )
        ),
        "unknown" to LicenseCategoryDefinition(
            description = "Unknown or unresolved licenses",
            licenses = listOf("NOASSERTION", "Unknown")
        )
    )

    private fun defaultRules(): List<PolicyRule> = listOf(
        PolicyRule(
            id = "no-unknown",
            name = "No Unknown Licenses",
            description = "All dependencies must have identified licenses",
            severity = Severity.ERROR,
            category = "unknown",
            action = RuleAction.DENY,
            message = "Dependency {{dependency}} has unresolved license - manual review required"
        )
    )
}

/**
 * Exception thrown when policy file cannot be loaded or parsed
 */
class PolicyLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
