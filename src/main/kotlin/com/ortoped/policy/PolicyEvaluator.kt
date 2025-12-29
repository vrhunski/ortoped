package com.ortoped.policy

import com.ortoped.model.Dependency
import com.ortoped.model.ScanResult
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Evaluates scan results against policy rules
 */
class PolicyEvaluator(
    private val config: PolicyConfig
) {
    private val classifier = LicenseClassifier(config)

    /**
     * Evaluate a scan result against the policy
     */
    fun evaluate(scanResult: ScanResult): PolicyReport {
        logger.info { "Evaluating policy '${config.name}' against ${scanResult.dependencies.size} dependencies" }

        val violations = mutableListOf<PolicyViolation>()
        val exempted = mutableListOf<ExemptedDependency>()
        val categoryDistribution = mutableMapOf<String, Int>()

        var evaluated = 0

        for (dependency in scanResult.dependencies) {
            // Check exemptions
            val exemption = findExemption(dependency)
            if (exemption != null) {
                exempted.add(ExemptedDependency(
                    dependencyId = dependency.id,
                    dependencyName = dependency.name,
                    exemptionReason = exemption.reason,
                    approvedBy = exemption.approvedBy
                ))
                continue
            }

            evaluated++

            // Determine effective license (considering AI suggestions)
            val effectiveLicense = getEffectiveLicense(dependency)
            val category = classifier.classify(effectiveLicense)

            // Track distribution
            categoryDistribution[category] = (categoryDistribution[category] ?: 0) + 1

            // Evaluate each enabled rule
            for (rule in config.rules.filter { it.enabled }) {
                val violation = evaluateRule(rule, dependency, effectiveLicense, category)
                if (violation != null) {
                    violations.add(violation)
                }
            }
        }

        val summary = PolicySummary(
            totalDependencies = scanResult.dependencies.size,
            evaluatedDependencies = evaluated,
            exemptedDependencies = exempted.size,
            totalViolations = violations.size,
            errorCount = violations.count { it.severity == Severity.ERROR },
            warningCount = violations.count { it.severity == Severity.WARNING },
            infoCount = violations.count { it.severity == Severity.INFO },
            licenseDistributionByCategory = categoryDistribution
        )

        val passed = determinePassed(summary)

        return PolicyReport(
            projectName = scanResult.projectName,
            projectVersion = scanResult.projectVersion,
            policyName = config.name,
            policyVersion = config.version,
            evaluationDate = Instant.now().toString(),
            summary = summary,
            violations = violations,
            exemptedDependencies = exempted,
            passed = passed,
            aiEnhanced = scanResult.aiEnhanced
        )
    }

    /**
     * Get the effective license for a dependency
     * Priority: concluded > AI high confidence > declared > NOASSERTION
     */
    private fun getEffectiveLicense(dependency: Dependency): String {
        // Priority: concluded license
        if (dependency.concludedLicense != null) {
            return dependency.concludedLicense
        }

        // AI suggestion with high confidence
        if (config.settings.aiSuggestions.acceptHighConfidence) {
            dependency.aiSuggestion?.let { ai ->
                if (ai.confidence == "HIGH" && ai.spdxId != null) {
                    return ai.spdxId
                }
            }
        }

        // Declared license
        return dependency.declaredLicenses.firstOrNull() ?: "NOASSERTION"
    }

    /**
     * Find exemption for a dependency
     */
    private fun findExemption(dependency: Dependency): Exemption? {
        return config.settings.exemptions.find { exemption ->
            matchesPattern(dependency.id, exemption.dependency)
        }
    }

    /**
     * Check if dependency ID matches a pattern (with glob support)
     */
    private fun matchesPattern(dependencyId: String, pattern: String): Boolean {
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .toRegex()
        return regex.matches(dependencyId)
    }

    /**
     * Evaluate a single rule against a dependency
     */
    private fun evaluateRule(
        rule: PolicyRule,
        dependency: Dependency,
        license: String,
        category: String
    ): PolicyViolation? {
        // Check scope filter
        if (rule.scopes.isNotEmpty() && dependency.scope !in rule.scopes) {
            return null
        }

        val matches = when {
            // Category-based matching
            rule.category != null -> category == rule.category

            // Denylist matching
            rule.denylist != null -> license.uppercase() in rule.denylist.map { it.uppercase() }

            // Allowlist matching (violation if NOT in list)
            rule.allowlist != null -> license.uppercase() !in rule.allowlist.map { it.uppercase() }

            else -> false
        }

        if (!matches || rule.action == RuleAction.ALLOW) {
            return null
        }

        val message = formatMessage(rule.message ?: "Policy violation", dependency, license)

        return PolicyViolation(
            ruleId = rule.id,
            ruleName = rule.name,
            severity = rule.severity,
            dependencyId = dependency.id,
            dependencyName = dependency.name,
            dependencyVersion = dependency.version,
            license = license,
            licenseCategory = category,
            scope = dependency.scope,
            message = message
        )
    }

    /**
     * Format message template with placeholders
     */
    private fun formatMessage(template: String, dependency: Dependency, license: String): String {
        return template
            .replace("{{license}}", license)
            .replace("{{dependency}}", "${dependency.name}:${dependency.version}")
            .replace("{{dependencyId}}", dependency.id)
    }

    /**
     * Determine if policy evaluation passed based on settings
     */
    private fun determinePassed(summary: PolicySummary): Boolean {
        if (config.settings.failOn.errors && summary.errorCount > 0) {
            return false
        }
        if (config.settings.failOn.warnings && summary.warningCount > 0) {
            return false
        }
        return true
    }
}
