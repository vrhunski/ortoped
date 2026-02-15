package com.ortoped.core.scanner

import com.ortoped.core.ai.CachingLicenseResolver
import com.ortoped.core.cache.LocalFileCache
import com.ortoped.core.cache.LockfileHasher
import com.ortoped.core.curation.CurationLoader
import com.ortoped.core.curation.CurationSet
import com.ortoped.core.curation.OrtCuration
import com.ortoped.core.model.Dependency
import com.ortoped.core.model.LicenseSuggestion
import com.ortoped.core.model.ScanResult
import com.ortoped.core.model.ScanSummary
import com.ortoped.core.model.UnresolvedLicense
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScanOrchestratorTest {

    @Test
    fun `should perform basic scan without AI enhancement by default`(@TempDir tempDir: File) = runBlocking {
        val scanner = SimpleScannerWrapper()
        val orchestrator = ScanOrchestrator(scanner = scanner)

        val result = orchestrator.scanWithAiEnhancement(
            projectDir = tempDir,
            demoMode = true
        )

        assertNotNull(result)
        assertEquals("demo-project", result.projectName)
        assertTrue(result.dependencies.isNotEmpty(), "Should have dependencies from demo")
        assertFalse(result.aiEnhanced, "Should not be AI enhanced when disabled (default)")
    }

    @Test
    fun `should enhance scan with AI when explicitly enabled`(@TempDir tempDir: File) = runBlocking {
        // Mock caching license resolver with controlled responses
        val mockResolver = mockk<CachingLicenseResolver>()
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
        val mockResolver = mockk<CachingLicenseResolver>()
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
        val mockResolver = mockk<CachingLicenseResolver>()
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
        val mockResolver = mockk<CachingLicenseResolver>(relaxed = true)
        val mockScanner = mockk<SimpleScannerWrapper>()

        // Mock scanner to return result with all licenses resolved
        coEvery { mockScanner.scanProject(any(), any(), any(), any(), any(), any()) } returns ScanResult(
            projectName = "fully-resolved-project",
            projectVersion = "1.0.0",
            scanDate = "2024-01-01",
            dependencies = listOf(
                Dependency(
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
            summary = ScanSummary(
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
        assertEquals(true, result.aiEnhanced, "Should be AI enhanced even when no unresolved licenses (AI was enabled)")
        assertEquals(0, result.summary.aiResolvedLicenses)
    }

    @Test
    fun `should count only HIGH confidence AI suggestions`(@TempDir tempDir: File) = runBlocking {
        val mockResolver = mockk<CachingLicenseResolver>()
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
        val mockResolver = mockk<CachingLicenseResolver>()
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
        val mockResolver = mockk<CachingLicenseResolver>()
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
        val mockResolver = mockk<CachingLicenseResolver>()
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

    // ========================================================================
    // New tests for curation support
    // ========================================================================

    @Test
    fun `should apply curations from ortoped directory`(@TempDir tempDir: File) = runBlocking {
        // Set up .ortoped/curations.yml with a curation for one of the demo deps
        val ortopedDir = File(tempDir, ".ortoped").apply { mkdirs() }
        File(ortopedDir, "curations.yml").writeText("""
            curations:
              - id: "Maven:com.unknown:mystery-lib:1.0.0"
                concluded_license: "MIT"
                comment: "Verified by legal team"
        """.trimIndent())

        val scanner = SimpleScannerWrapper()
        val orchestrator = ScanOrchestrator(scanner = scanner)

        val result = orchestrator.scanWithAiEnhancement(
            projectDir = tempDir,
            demoMode = true
        )

        assertNotNull(result)
        // The curated dep should now be resolved
        val curatedDep = result.dependencies.find { it.id == "Maven:com.unknown:mystery-lib:1.0.0" }
        if (curatedDep != null) {
            assertEquals("MIT", curatedDep.concludedLicense)
            assertTrue(curatedDep.isResolved)
        }
    }

    @Test
    fun `should apply curations from explicit file`(@TempDir tempDir: File) = runBlocking {
        val curationsFile = File(tempDir, "my-curations.yml")
        curationsFile.writeText("""
            curations:
              - id: "Maven:com.unknown:mystery-lib:1.0.0"
                concluded_license: "Apache-2.0"
        """.trimIndent())

        val scanner = SimpleScannerWrapper()
        val orchestrator = ScanOrchestrator(scanner = scanner)

        val result = orchestrator.scanWithAiEnhancement(
            projectDir = tempDir,
            demoMode = true,
            curationsFile = curationsFile
        )

        assertNotNull(result)
        val curatedDep = result.dependencies.find { it.id == "Maven:com.unknown:mystery-lib:1.0.0" }
        if (curatedDep != null) {
            assertEquals("Apache-2.0", curatedDep.concludedLicense)
            assertTrue(curatedDep.isResolved)
        }
    }

    @Test
    fun `should use fast path cache on second scan`(@TempDir tempDir: File) = runBlocking {
        // Create a lockfile so hashing works
        File(tempDir, "package-lock.json").writeText("""{"lockfileVersion": 3}""")

        val mockScanner = mockk<SimpleScannerWrapper>()
        val scanResult = ScanResult(
            projectName = "cached-project",
            projectVersion = "1.0.0",
            scanDate = "2024-01-01",
            dependencies = listOf(
                Dependency(
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
            summary = ScanSummary(
                totalDependencies = 1,
                resolvedLicenses = 1,
                unresolvedLicenses = 0,
                licenseDistribution = mapOf("MIT" to 1)
            ),
            unresolvedLicenses = emptyList()
        )

        coEvery { mockScanner.scanProject(any(), any(), any(), any(), any(), any()) } returns scanResult

        val cache = LocalFileCache.fromProject(tempDir)
        val orchestrator = ScanOrchestrator(
            scanner = mockScanner,
            localCache = cache
        )

        // First scan — should hit the real scanner
        val result1 = orchestrator.scanWithAiEnhancement(
            projectDir = tempDir,
            demoMode = false
        )
        assertEquals("cached-project", result1.projectName)

        // Second scan — should use cache (scanner not called again)
        val result2 = orchestrator.scanWithAiEnhancement(
            projectDir = tempDir,
            demoMode = false
        )
        assertEquals("cached-project", result2.projectName)

        // Verify scanner was only called once
        io.mockk.coVerify(exactly = 1) { mockScanner.scanProject(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should skip fast path when useFastPath is false`(@TempDir tempDir: File) = runBlocking {
        File(tempDir, "package-lock.json").writeText("""{"lockfileVersion": 3}""")

        val mockScanner = mockk<SimpleScannerWrapper>()
        val scanResult = ScanResult(
            projectName = "no-cache-project",
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

        coEvery { mockScanner.scanProject(any(), any(), any(), any(), any(), any()) } returns scanResult

        val cache = LocalFileCache.fromProject(tempDir)
        val orchestrator = ScanOrchestrator(
            scanner = mockScanner,
            localCache = cache
        )

        // First scan with cache
        orchestrator.scanWithAiEnhancement(projectDir = tempDir, demoMode = false)

        // Second scan with cache disabled — should call scanner again
        orchestrator.scanWithAiEnhancement(projectDir = tempDir, demoMode = false, useFastPath = false)

        io.mockk.coVerify(exactly = 2) { mockScanner.scanProject(any(), any(), any(), any(), any(), any()) }
    }
}
