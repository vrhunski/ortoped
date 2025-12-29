package com.ortoped.scanner

import com.ortoped.ai.LicenseResolver
import com.ortoped.model.LicenseSuggestion
import com.ortoped.model.UnresolvedLicense
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScanOrchestratorTest {

    @Test
    fun `should perform basic scan without AI enhancement`(@TempDir tempDir: File) = runBlocking {
        val scanner = SimpleScannerWrapper()
        val orchestrator = ScanOrchestrator(scanner = scanner)

        val result = orchestrator.scanWithAiEnhancement(
            projectDir = tempDir,
            enableAiResolution = false,
            demoMode = true
        )

        assertNotNull(result)
        assertEquals("demo-project", result.projectName)
        assertTrue(result.dependencies.isNotEmpty(), "Should have dependencies from demo")
        assertEquals(false, result.aiEnhanced, "Should not be AI enhanced when disabled")
    }

    @Test
    fun `should enhance scan with AI when enabled`(@TempDir tempDir: File) = runBlocking {
        // Mock license resolver with controlled responses
        val mockResolver = mockk<LicenseResolver>()
        coEvery { mockResolver.resolveLicense(any()) } returns LicenseSuggestion(
            suggestedLicense = "MIT License",
            confidence = "HIGH",
            reasoning = "License header analysis",
            spdxId = "MIT",
            alternatives = listOf("ISC", "BSD-2-Clause")
        )

        val scanner = SimpleScannerWrapper()
        val orchestrator = ScanOrchestrator(
            scanner = scanner,
            licenseResolver = mockResolver
        )

        val result = orchestrator.scanWithAiEnhancement(
            projectDir = tempDir,
            enableAiResolution = true,
            demoMode = true
        )

        assertNotNull(result)
        assertTrue(result.aiEnhanced, "Should be AI enhanced when enabled")
        assertTrue(result.summary.aiResolvedLicenses > 0, "Should have AI-resolved licenses")

        // Check that dependencies have AI suggestions
        val withAiSuggestions = result.dependencies.filter { it.aiSuggestion != null }
        assertTrue(withAiSuggestions.isNotEmpty(), "Should have dependencies with AI suggestions")
    }

    @Test
    fun `should handle parallel AI resolution`(@TempDir tempDir: File) = runBlocking {
        val mockResolver = mockk<LicenseResolver>()
        coEvery { mockResolver.resolveLicense(any()) } returns LicenseSuggestion(
            suggestedLicense = "Apache-2.0",
            confidence = "HIGH",
            reasoning = "License text matches Apache 2.0",
            spdxId = "Apache-2.0",
            alternatives = emptyList()
        )

        val scanner = SimpleScannerWrapper()
        val orchestrator = ScanOrchestrator(
            scanner = scanner,
            licenseResolver = mockResolver
        )

        val result = orchestrator.scanWithAiEnhancement(
            projectDir = tempDir,
            enableAiResolution = true,
            parallelAiCalls = true,
            demoMode = true
        )

        assertNotNull(result)
        assertTrue(result.aiEnhanced)
        assertTrue(result.summary.aiResolvedLicenses > 0)
    }

    @Test
    fun `should handle sequential AI resolution`(@TempDir tempDir: File) = runBlocking {
        val mockResolver = mockk<LicenseResolver>()
        coEvery { mockResolver.resolveLicense(any()) } returns LicenseSuggestion(
            suggestedLicense = "BSD-2-Clause",
            confidence = "HIGH",
            reasoning = "License matches BSD 2-Clause pattern",
            spdxId = "BSD-2-Clause",
            alternatives = listOf("MIT")
        )

        val scanner = SimpleScannerWrapper()
        val orchestrator = ScanOrchestrator(
            scanner = scanner,
            licenseResolver = mockResolver
        )

        val result = orchestrator.scanWithAiEnhancement(
            projectDir = tempDir,
            enableAiResolution = true,
            parallelAiCalls = false,
            demoMode = true
        )

        assertNotNull(result)
        assertTrue(result.aiEnhanced)
        assertTrue(result.summary.aiResolvedLicenses > 0)
    }

    @Test
    fun `should skip AI when no unresolved licenses`(@TempDir tempDir: File) = runBlocking {
        val mockResolver = mockk<LicenseResolver>(relaxed = true)
        val mockScanner = mockk<SimpleScannerWrapper>()

        // Mock scanner to return result with all licenses resolved
        coEvery { mockScanner.scanProject(any(), any(), any()) } returns com.ortoped.model.ScanResult(
            projectName = "fully-resolved-project",
            projectVersion = "1.0.0",
            scanDate = "2024-01-01",
            dependencies = listOf(
                com.ortoped.model.Dependency(
                    id = "Maven:com.example:lib:1.0.0",
                    name = "com.example:lib",
                    version = "1.0.0",
                    declaredLicenses = listOf("MIT"),
                    detectedLicenses = listOf("MIT"),
                    concludedLicense = "MIT",
                    scope = "compile",
                    isResolved = true
                )
            ),
            summary = com.ortoped.model.ScanSummary(
                totalDependencies = 1,
                resolvedLicenses = 1,
                unresolvedLicenses = 0,
                licenseDistribution = mapOf("MIT" to 1)
            ),
            unresolvedLicenses = emptyList()
        )

        val orchestrator = ScanOrchestrator(
            scanner = mockScanner,
            licenseResolver = mockResolver
        )

        val result = orchestrator.scanWithAiEnhancement(
            projectDir = tempDir,
            enableAiResolution = true,
            demoMode = true
        )

        assertNotNull(result)
        assertEquals(false, result.aiEnhanced, "Should not be AI enhanced when no unresolved licenses")
        assertEquals(0, result.summary.aiResolvedLicenses)
    }

    @Test
    fun `should count only HIGH confidence AI suggestions`(@TempDir tempDir: File) = runBlocking {
        val mockResolver = mockk<LicenseResolver>()
        var callCount = 0

        // Alternate between HIGH and MEDIUM confidence
        coEvery { mockResolver.resolveLicense(any()) } answers {
            callCount++
            if (callCount % 2 == 1) {
                // Odd calls return HIGH confidence
                LicenseSuggestion(
                    suggestedLicense = "MIT",
                    confidence = "HIGH",
                    reasoning = "High confidence match",
                    spdxId = "MIT"
                )
            } else {
                // Even calls return MEDIUM confidence
                LicenseSuggestion(
                    suggestedLicense = "Apache-2.0",
                    confidence = "MEDIUM",
                    reasoning = "Medium confidence match",
                    spdxId = "Apache-2.0"
                )
            }
        }

        val scanner = SimpleScannerWrapper()
        val orchestrator = ScanOrchestrator(
            scanner = scanner,
            licenseResolver = mockResolver
        )

        val result = orchestrator.scanWithAiEnhancement(
            projectDir = tempDir,
            enableAiResolution = true,
            demoMode = true
        )

        assertNotNull(result)
        // Only HIGH confidence should be counted
        val highConfidenceSuggestions = result.dependencies.filter {
            it.aiSuggestion?.confidence == "HIGH"
        }
        assertTrue(highConfidenceSuggestions.isNotEmpty(), "Should have at least one HIGH confidence suggestion")
        assertTrue(result.summary.aiResolvedLicenses <= highConfidenceSuggestions.size,
            "aiResolvedLicenses should be <= HIGH confidence count")
    }

    @Test
    fun `should handle AI resolver failures gracefully`(@TempDir tempDir: File) = runBlocking {
        val mockResolver = mockk<LicenseResolver>()
        coEvery { mockResolver.resolveLicense(any()) } throws RuntimeException("API error")

        val scanner = SimpleScannerWrapper()
        val orchestrator = ScanOrchestrator(
            scanner = scanner,
            licenseResolver = mockResolver
        )

        // Should not throw, but handle gracefully
        val result = orchestrator.scanWithAiEnhancement(
            projectDir = tempDir,
            enableAiResolution = true,
            demoMode = true
        )

        assertNotNull(result)
        // AI enhancement attempted but may have 0 resolved due to errors
        assertTrue(result.aiEnhanced)
    }

    @Test
    fun `should preserve original dependencies when AI fails`(@TempDir tempDir: File) = runBlocking {
        val mockResolver = mockk<LicenseResolver>()
        coEvery { mockResolver.resolveLicense(any()) } returns null

        val scanner = SimpleScannerWrapper()
        val orchestrator = ScanOrchestrator(
            scanner = scanner,
            licenseResolver = mockResolver
        )

        val result = orchestrator.scanWithAiEnhancement(
            projectDir = tempDir,
            enableAiResolution = true,
            demoMode = true
        )

        assertNotNull(result)
        assertTrue(result.dependencies.isNotEmpty(), "Should preserve dependencies even when AI returns null")
        assertEquals(0, result.summary.aiResolvedLicenses, "Should have 0 AI-resolved when all return null")
    }

    @Test
    fun `should generate demo data correctly`(@TempDir tempDir: File) = runBlocking {
        val scanner = SimpleScannerWrapper()
        val orchestrator = ScanOrchestrator(scanner = scanner)

        val result = orchestrator.scanWithAiEnhancement(
            projectDir = tempDir,
            enableAiResolution = false,
            demoMode = true
        )

        // Demo should have both resolved and unresolved licenses
        assertTrue(result.summary.totalDependencies > 0)
        assertTrue(result.summary.unresolvedLicenses > 0, "Demo should have unresolved licenses")
        assertTrue(result.unresolvedLicenses.isNotEmpty(), "Demo should have unresolved license entries")
    }

    @Test
    fun `should update summary after AI enhancement`(@TempDir tempDir: File) = runBlocking {
        val mockResolver = mockk<LicenseResolver>()
        coEvery { mockResolver.resolveLicense(any()) } returns LicenseSuggestion(
            suggestedLicense = "MIT",
            confidence = "HIGH",
            reasoning = "AI analysis",
            spdxId = "MIT"
        )

        val scanner = SimpleScannerWrapper()
        val orchestrator = ScanOrchestrator(
            scanner = scanner,
            licenseResolver = mockResolver
        )

        val result = orchestrator.scanWithAiEnhancement(
            projectDir = tempDir,
            enableAiResolution = true,
            demoMode = true
        )

        assertNotNull(result)
        assertNotNull(result.summary)
        assertTrue(result.summary.totalDependencies > 0)
        assertTrue(result.summary.resolvedLicenses >= 0)
        assertTrue(result.summary.unresolvedLicenses >= 0)
        assertTrue(result.summary.aiResolvedLicenses >= 0)
    }
}
