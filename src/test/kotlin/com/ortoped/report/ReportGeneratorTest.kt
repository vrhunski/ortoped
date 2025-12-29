package com.ortoped.report

import com.ortoped.model.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportGeneratorTest {

    private val generator = ReportGenerator()
    private val json = Json { ignoreUnknownKeys = true }

    private fun createTestScanResult(
        totalDeps: Int = 10,
        resolvedDeps: Int = 7,
        aiResolvedDeps: Int = 2,
        unresolvedCount: Int = 3,
        aiEnhanced: Boolean = true,
        sourceCodeScanned: Boolean = false
    ): ScanResult {
        val dependencies = mutableListOf<Dependency>()

        // Add resolved dependencies
        repeat(resolvedDeps) { i ->
            dependencies.add(
                Dependency(
                    id = "Maven:com.example:lib-$i:1.0.0",
                    name = "com.example:lib-$i",
                    version = "1.0.0",
                    declaredLicenses = listOf("MIT"),
                    detectedLicenses = listOf("MIT"),
                    concludedLicense = "MIT",
                    scope = "compile",
                    isResolved = true
                )
            )
        }

        // Add unresolved dependencies with AI suggestions
        val unresolvedLicenses = mutableListOf<UnresolvedLicense>()
        repeat(unresolvedCount) { i ->
            val depId = "Maven:com.example:unresolved-$i:1.0.0"
            dependencies.add(
                Dependency(
                    id = depId,
                    name = "com.example:unresolved-$i",
                    version = "1.0.0",
                    declaredLicenses = emptyList(),
                    detectedLicenses = emptyList(),
                    concludedLicense = null,
                    scope = "compile",
                    isResolved = false,
                    aiSuggestion = if (i < aiResolvedDeps) {
                        LicenseSuggestion(
                            suggestedLicense = "Apache-2.0",
                            confidence = "HIGH",
                            reasoning = "License header analysis",
                            spdxId = "Apache-2.0",
                            alternatives = listOf("MIT")
                        )
                    } else null
                )
            )
            unresolvedLicenses.add(
                UnresolvedLicense(
                    dependencyId = depId,
                    dependencyName = "com.example:unresolved-$i",
                    reason = "No license found in metadata"
                )
            )
        }

        return ScanResult(
            projectName = "test-project",
            projectVersion = "1.0.0",
            scanDate = "2024-01-01T12:00:00Z",
            dependencies = dependencies,
            summary = ScanSummary(
                totalDependencies = totalDeps,
                resolvedLicenses = resolvedDeps,
                unresolvedLicenses = unresolvedCount,
                aiResolvedLicenses = aiResolvedDeps,
                licenseDistribution = mapOf(
                    "MIT" to resolvedDeps,
                    "Apache-2.0" to aiResolvedDeps
                ),
                scannerResolvedLicenses = if (sourceCodeScanned) 5 else 0
            ),
            unresolvedLicenses = unresolvedLicenses,
            aiEnhanced = aiEnhanced,
            sourceCodeScanned = sourceCodeScanned,
            scannerType = if (sourceCodeScanned) "ScanCode" else null,
            packagesScanned = if (sourceCodeScanned) totalDeps else 0
        )
    }

    @Test
    fun `should generate valid JSON report file`(@TempDir tempDir: File) {
        val scanResult = createTestScanResult()
        val outputFile = File(tempDir, "report.json")

        generator.generateJsonReport(scanResult, outputFile)

        assertTrue(outputFile.exists(), "Report file should be created")
        assertTrue(outputFile.length() > 0, "Report file should not be empty")

        // Verify JSON structure
        val content = outputFile.readText()
        val parsed = json.parseToJsonElement(content)
        assertTrue(content.contains("\"projectName\""))
        assertTrue(content.contains("\"dependencies\""))
        assertTrue(content.contains("\"summary\""))
    }

    @Test
    fun `should create parent directories if needed`(@TempDir tempDir: File) {
        val scanResult = createTestScanResult()
        val outputFile = File(tempDir, "nested/dir/report.json")

        generator.generateJsonReport(scanResult, outputFile)

        assertTrue(outputFile.exists(), "Report file should be created")
        assertTrue(outputFile.parentFile.exists(), "Parent directories should be created")
    }

    @Test
    fun `should generate console report with all sections`() {
        val scanResult = createTestScanResult(
            totalDeps = 10,
            resolvedDeps = 7,
            aiResolvedDeps = 2,
            unresolvedCount = 3,
            aiEnhanced = true
        )

        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))

        generator.generateConsoleReport(scanResult)

        val output = outputStream.toString()

        // Check main sections
        assertTrue(output.contains("ORT SCAN REPORT"))
        assertTrue(output.contains("Project: test-project (1.0.0)"))
        assertTrue(output.contains("Summary:"))
        assertTrue(output.contains("Total Dependencies"))
        assertTrue(output.contains("Resolved Licenses"))
        assertTrue(output.contains("Unresolved Licenses"))

        // Check AI section
        assertTrue(output.contains("AI Enhancement Statistics:"))
        assertTrue(output.contains("AI-resolved licenses: 2"))

        // Check license distribution
        assertTrue(output.contains("Top Licenses:"))
    }

    @Test
    fun `should show scanner statistics when source code scanned`() {
        val scanResult = createTestScanResult(sourceCodeScanned = true)

        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))

        generator.generateConsoleReport(scanResult)

        val output = outputStream.toString()

        assertTrue(output.contains("Scanner Statistics:"))
        assertTrue(output.contains("Packages scanned:"))
        assertTrue(output.contains("Scanner type: ScanCode"))
    }

    @Test
    fun `should show unresolved licenses with AI suggestions`() {
        val scanResult = createTestScanResult(unresolvedCount = 2, aiResolvedDeps = 1)

        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))

        generator.generateConsoleReport(scanResult)

        val output = outputStream.toString()

        assertTrue(output.contains("Unresolved Licenses:"))
        assertTrue(output.contains("AI Suggestion:"))
        assertTrue(output.contains("Apache-2.0"))
        assertTrue(output.contains("HIGH"))
        assertTrue(output.contains("Reasoning:"))
    }

    @Test
    fun `should calculate AI success rate correctly`() {
        // Test with 3 unresolved, 2 AI-resolved = 66% success rate
        val scanResult = createTestScanResult(
            unresolvedCount = 3,
            aiResolvedDeps = 2
        )

        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))

        generator.generateConsoleReport(scanResult)

        val output = outputStream.toString()

        assertTrue(output.contains("Success rate: 66%"))
    }

    @Test
    fun `should handle zero unresolved licenses`() {
        val scanResult = createTestScanResult(
            totalDeps = 10,
            resolvedDeps = 10,
            unresolvedCount = 0,
            aiResolvedDeps = 0,
            aiEnhanced = false
        )

        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))

        generator.generateConsoleReport(scanResult)

        val output = outputStream.toString()

        assertTrue(output.contains("Total Dependencies      : 10"))
        assertTrue(output.contains("Resolved Licenses       : 10"))
        assertTrue(output.contains("Unresolved Licenses     : 0"))
        assertFalse(output.contains("AI Enhancement Statistics:"))
    }

    @Test
    fun `should show top 10 licenses sorted by count`() {
        val scanResult = ScanResult(
            projectName = "test-project",
            projectVersion = "1.0.0",
            scanDate = "2024-01-01",
            dependencies = emptyList(),
            summary = ScanSummary(
                totalDependencies = 100,
                resolvedLicenses = 100,
                unresolvedLicenses = 0,
                licenseDistribution = mapOf(
                    "MIT" to 40,
                    "Apache-2.0" to 30,
                    "GPL-3.0-only" to 10,
                    "BSD-2-Clause" to 8,
                    "ISC" to 5,
                    "LGPL-2.1-only" to 3,
                    "MPL-2.0" to 2,
                    "AGPL-3.0-only" to 1,
                    "EPL-1.0" to 1
                )
            ),
            unresolvedLicenses = emptyList()
        )

        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))

        generator.generateConsoleReport(scanResult)

        val output = outputStream.toString()

        assertTrue(output.contains("Top Licenses:"))
        assertTrue(output.contains("MIT"))
        assertTrue(output.contains("Apache-2.0"))
    }

    private fun assertFalse(contains: Boolean) {
        assertEquals(false, contains)
    }
}
