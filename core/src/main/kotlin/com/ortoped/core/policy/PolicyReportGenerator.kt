package com.ortoped.core.policy

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Generates policy evaluation reports
 */
class PolicyReportGenerator {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Generate JSON report to file
     */
    fun generateJsonReport(report: PolicyReport, outputFile: File) {
        logger.info { "Generating JSON policy report to: ${outputFile.absolutePath}" }
        val jsonString = json.encodeToString(report)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(jsonString)
    }

    /**
     * Generate console report
     */
    fun generateConsoleReport(report: PolicyReport) {
        println()
        println("=".repeat(80))
        println("POLICY EVALUATION REPORT")
        println("=".repeat(80))
        println()
        println("Project: ${report.projectName} (${report.projectVersion})")
        println("Policy: ${report.policyName} v${report.policyVersion}")
        println("Evaluation Date: ${report.evaluationDate}")
        println()

        // Summary
        println("Summary:")
        println("-".repeat(80))
        println("  Total Dependencies:    ${report.summary.totalDependencies}")
        println("  Evaluated:             ${report.summary.evaluatedDependencies}")
        println("  Exempted:              ${report.summary.exemptedDependencies}")
        println("  Total Violations:      ${report.summary.totalViolations}")
        println("    - Errors:            ${report.summary.errorCount}")
        println("    - Warnings:          ${report.summary.warningCount}")
        println("    - Info:              ${report.summary.infoCount}")
        println()

        // License distribution
        println("License Distribution by Category:")
        println("-".repeat(80))
        report.summary.licenseDistributionByCategory.entries
            .sortedByDescending { it.value }
            .forEach { (category, count) ->
                println("  ${category.padEnd(25)}: $count")
            }
        println()

        // Violations
        if (report.violations.isNotEmpty()) {
            println("Violations:")
            println("-".repeat(80))
            report.violations.groupBy { it.severity }.forEach { (severity, violations) ->
                println()
                println("[$severity] (${violations.size} issues)")
                violations.forEach { v ->
                    println("  - ${v.dependencyName}:${v.dependencyVersion}")
                    println("    License: ${v.license} (${v.licenseCategory})")
                    println("    Rule: ${v.ruleName}")
                    println("    ${v.message}")
                    v.aiSuggestion?.let { ai ->
                        println("    AI Suggestion: ${ai.suggestion}")
                        if (ai.alternativeDependencies.isNotEmpty()) {
                            println("    Alternatives: ${ai.alternativeDependencies.joinToString(", ")}")
                        }
                    }
                }
            }
            println()
        }

        // Exemptions
        if (report.exemptedDependencies.isNotEmpty()) {
            println("Exempted Dependencies:")
            println("-".repeat(80))
            report.exemptedDependencies.forEach { e ->
                println("  - ${e.dependencyName}: ${e.exemptionReason}")
                if (e.approvedBy != null) {
                    println("    Approved by: ${e.approvedBy}")
                }
            }
            println()
        }

        // Final verdict
        println("=".repeat(80))
        if (report.passed) {
            println("RESULT: PASSED ✓")
        } else {
            println("RESULT: FAILED ✗")
        }
        println("=".repeat(80))
    }
}
