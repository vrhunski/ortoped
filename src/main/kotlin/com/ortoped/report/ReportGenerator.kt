package com.ortoped.report

import com.ortoped.model.ScanResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val logger = KotlinLogging.logger {}

class ReportGenerator {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun generateJsonReport(scanResult: ScanResult, outputFile: File) {
        logger.info { "Generating JSON report to: ${outputFile.absolutePath}" }

        val jsonString = json.encodeToString(scanResult)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(jsonString)

        logger.info { "Report generated successfully" }
        printSummary(scanResult)
    }

    fun generateConsoleReport(scanResult: ScanResult) {
        println("\n" + "=".repeat(80))
        println("ORT SCAN REPORT")
        println("=".repeat(80))
        println()
        println("Project: ${scanResult.projectName} (${scanResult.projectVersion})")
        println("Scan Date: ${scanResult.scanDate}")
        println()

        printSummary(scanResult)

        if (scanResult.unresolvedLicenses.isNotEmpty()) {
            println("\nUnresolved Licenses:")
            println("-".repeat(80))
            scanResult.unresolvedLicenses.forEach { unresolved ->
                println("  • ${unresolved.dependencyName} (${unresolved.dependencyId})")
                println("    Reason: ${unresolved.reason}")

                // Show AI suggestion if available
                val dependency = scanResult.dependencies.find { it.id == unresolved.dependencyId }
                dependency?.aiSuggestion?.let { suggestion ->
                    println("    AI Suggestion: ${suggestion.suggestedLicense} (${suggestion.confidence})")
                    println("    Reasoning: ${suggestion.reasoning}")
                    if (suggestion.alternatives.isNotEmpty()) {
                        println("    Alternatives: ${suggestion.alternatives.joinToString(", ")}")
                    }
                }
                println()
            }
        }

        if (scanResult.aiEnhanced) {
            println("\nAI Enhancement Statistics:")
            println("-".repeat(80))
            println("  AI-resolved licenses: ${scanResult.summary.aiResolvedLicenses}")
            println("  Success rate: ${calculateSuccessRate(scanResult)}%")
        }

        println("\nTop Licenses:")
        println("-".repeat(80))
        scanResult.summary.licenseDistribution
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .forEach { (license, count) ->
                println("  ${license.padEnd(30)} : $count")
            }

        println("\n" + "=".repeat(80))
    }

    private fun printSummary(scanResult: ScanResult) {
        println("Summary:")
        println("-".repeat(80))
        println("  Total Dependencies      : ${scanResult.summary.totalDependencies}")
        println("  Resolved Licenses       : ${scanResult.summary.resolvedLicenses}")
        println("  Unresolved Licenses     : ${scanResult.summary.unresolvedLicenses}")
        if (scanResult.aiEnhanced) {
            println("  AI-Resolved Licenses    : ${scanResult.summary.aiResolvedLicenses}")
        }
        println("  Unique Licenses         : ${scanResult.summary.licenseDistribution.size}")
    }

    private fun calculateSuccessRate(scanResult: ScanResult): Int {
        val totalUnresolved = scanResult.unresolvedLicenses.size
        if (totalUnresolved == 0) return 100

        val aiResolved = scanResult.summary.aiResolvedLicenses
        return ((aiResolved.toDouble() / totalUnresolved) * 100).toInt()
    }
}