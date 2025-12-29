package com.ortoped.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScanResultTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun `should serialize and deserialize ScanResult`() {
        val scanResult = createTestScanResult()

        val jsonString = json.encodeToString(scanResult)
        val deserialized = json.decodeFromString<ScanResult>(jsonString)

        assertEquals(scanResult.projectName, deserialized.projectName)
        assertEquals(scanResult.projectVersion, deserialized.projectVersion)
        assertEquals(scanResult.dependencies.size, deserialized.dependencies.size)
        assertEquals(scanResult.summary.totalDependencies, deserialized.summary.totalDependencies)
    }

    @Test
    fun `should serialize all dependency fields`() {
        val dependency = Dependency(
            id = "Maven:com.example:library:1.0.0",
            name = "com.example:library",
            version = "1.0.0",
            declaredLicenses = listOf("MIT"),
            detectedLicenses = listOf("MIT"),
            concludedLicense = "MIT",
            scope = "compile",
            isResolved = true,
            aiSuggestion = null
        )

        val jsonString = json.encodeToString(dependency)

        assertTrue(jsonString.contains("\"id\""))
        assertTrue(jsonString.contains("\"name\""))
        assertTrue(jsonString.contains("\"version\""))
        assertTrue(jsonString.contains("\"declaredLicenses\""))
        assertTrue(jsonString.contains("\"concludedLicense\""))
    }

    @Test
    fun `should handle AI suggestion in dependency`() {
        val suggestion = LicenseSuggestion(
            suggestedLicense = "Apache-2.0",
            confidence = "HIGH",
            reasoning = "License text matches Apache 2.0 template",
            spdxId = "Apache-2.0",
            alternatives = listOf("MIT", "BSD-2-Clause")
        )

        val dependency = Dependency(
            id = "Maven:com.example:library:2.0.0",
            name = "com.example:library",
            version = "2.0.0",
            declaredLicenses = emptyList(),
            detectedLicenses = emptyList(),
            concludedLicense = null,
            scope = "compile",
            isResolved = false,
            aiSuggestion = suggestion
        )

        val jsonString = json.encodeToString(dependency)
        val deserialized = json.decodeFromString<Dependency>(jsonString)

        assertNotNull(deserialized.aiSuggestion)
        assertEquals("Apache-2.0", deserialized.aiSuggestion?.suggestedLicense)
        assertEquals("HIGH", deserialized.aiSuggestion?.confidence)
        assertEquals(2, deserialized.aiSuggestion?.alternatives?.size)
    }

    @Test
    fun `should serialize unresolved licenses`() {
        val unresolved = UnresolvedLicense(
            dependencyId = "Maven:com.example:unknown:1.0.0",
            dependencyName = "com.example:unknown",
            licenseText = "Custom license text",
            licenseUrl = "https://example.com/license",
            reason = "License file not recognized",
            licenseFilePath = "/path/to/LICENSE",
            detectedByScanner = true
        )

        val jsonString = json.encodeToString(unresolved)
        val deserialized = json.decodeFromString<UnresolvedLicense>(jsonString)

        assertEquals(unresolved.dependencyId, deserialized.dependencyId)
        assertEquals(unresolved.licenseText, deserialized.licenseText)
        assertEquals(unresolved.licenseFilePath, deserialized.licenseFilePath)
        assertTrue(deserialized.detectedByScanner)
    }

    @Test
    fun `should serialize scan summary with all fields`() {
        val summary = ScanSummary(
            totalDependencies = 100,
            resolvedLicenses = 80,
            unresolvedLicenses = 20,
            aiResolvedLicenses = 15,
            licenseDistribution = mapOf(
                "MIT" to 40,
                "Apache-2.0" to 30,
                "GPL-3.0-only" to 10
            ),
            scannerResolvedLicenses = 5
        )

        val jsonString = json.encodeToString(summary)
        val deserialized = json.decodeFromString<ScanSummary>(jsonString)

        assertEquals(100, deserialized.totalDependencies)
        assertEquals(80, deserialized.resolvedLicenses)
        assertEquals(15, deserialized.aiResolvedLicenses)
        assertEquals(5, deserialized.scannerResolvedLicenses)
        assertEquals(3, deserialized.licenseDistribution.size)
    }

    @Test
    fun `should handle default values for optional fields`() {
        val scanResult = ScanResult(
            projectName = "test-project",
            projectVersion = "1.0.0",
            scanDate = "2024-01-01",
            dependencies = emptyList(),
            summary = ScanSummary(
                totalDependencies = 0,
                resolvedLicenses = 0,
                unresolvedLicenses = 0,
                licenseDistribution = emptyMap()
            ),
            unresolvedLicenses = emptyList()
        )

        val jsonString = json.encodeToString(scanResult)
        val deserialized = json.decodeFromString<ScanResult>(jsonString)

        assertEquals(false, deserialized.aiEnhanced)
        assertEquals(false, deserialized.sourceCodeScanned)
        assertEquals(null, deserialized.scannerType)
        assertEquals(0, deserialized.packagesScanned)
    }

    @Test
    fun `should serialize complete scan result with all features`() {
        val scanResult = createTestScanResult()

        val jsonString = json.encodeToString(scanResult)

        assertTrue(jsonString.contains("\"projectName\""))
        assertTrue(jsonString.contains("\"dependencies\""))
        assertTrue(jsonString.contains("\"summary\""))
        assertTrue(jsonString.contains("\"unresolvedLicenses\""))
        assertTrue(jsonString.contains("\"aiEnhanced\""))
        assertTrue(jsonString.contains("\"sourceCodeScanned\""))
    }

    @Test
    fun `should handle nullable license fields`() {
        val dependency = Dependency(
            id = "Maven:com.example:library:1.0.0",
            name = "com.example:library",
            version = "1.0.0",
            declaredLicenses = emptyList(),
            detectedLicenses = emptyList(),
            concludedLicense = null,
            scope = "compile",
            isResolved = false,
            aiSuggestion = null
        )

        val jsonString = json.encodeToString(dependency)
        val deserialized = json.decodeFromString<Dependency>(jsonString)

        assertEquals(null, deserialized.concludedLicense)
        assertEquals(null, deserialized.aiSuggestion)
    }

    private fun createTestScanResult(): ScanResult {
        return ScanResult(
            projectName = "test-project",
            projectVersion = "1.0.0",
            scanDate = "2024-01-01T12:00:00Z",
            dependencies = listOf(
                Dependency(
                    id = "Maven:com.example:lib-mit:1.0.0",
                    name = "com.example:lib-mit",
                    version = "1.0.0",
                    declaredLicenses = listOf("MIT"),
                    detectedLicenses = listOf("MIT"),
                    concludedLicense = "MIT",
                    scope = "compile",
                    isResolved = true
                ),
                Dependency(
                    id = "Maven:com.example:lib-unknown:2.0.0",
                    name = "com.example:lib-unknown",
                    version = "2.0.0",
                    declaredLicenses = emptyList(),
                    detectedLicenses = emptyList(),
                    concludedLicense = null,
                    scope = "compile",
                    isResolved = false,
                    aiSuggestion = LicenseSuggestion(
                        suggestedLicense = "Apache-2.0",
                        confidence = "HIGH",
                        reasoning = "License header matches Apache 2.0",
                        spdxId = "Apache-2.0",
                        alternatives = listOf("MIT")
                    )
                )
            ),
            summary = ScanSummary(
                totalDependencies = 2,
                resolvedLicenses = 1,
                unresolvedLicenses = 1,
                aiResolvedLicenses = 1,
                licenseDistribution = mapOf("MIT" to 1, "Apache-2.0" to 1),
                scannerResolvedLicenses = 0
            ),
            unresolvedLicenses = listOf(
                UnresolvedLicense(
                    dependencyId = "Maven:com.example:lib-unknown:2.0.0",
                    dependencyName = "com.example:lib-unknown",
                    reason = "No license information found in metadata"
                )
            ),
            aiEnhanced = true,
            sourceCodeScanned = true,
            scannerType = "ScanCode",
            packagesScanned = 2
        )
    }
}
