package com.ortoped.core.scanner

import com.ortoped.core.model.Dependency
import com.ortoped.core.model.ScanResult
import com.ortoped.core.model.ScanSummary
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CachingAnalyzerWrapperTest {

    private lateinit var mockCache: ScanResultCache
    private lateinit var mockDelegate: SimpleScannerWrapper
    private lateinit var cachingWrapper: CachingAnalyzerWrapper

    @BeforeEach
    fun setup() {
        mockCache = mockk(relaxed = true)
        mockDelegate = mockk()
        cachingWrapper = CachingAnalyzerWrapper(
            cache = mockCache,
            delegate = mockDelegate
        )
    }

    @Test
    fun `should return cached result on cache hit`(@TempDir tempDir: File) = runBlocking {
        val cachedResult = createSampleScanResult("cached-project")
        val compressed = cachingWrapper.compressAndSerialize(cachedResult)

        coEvery {
            mockCache.getCachedScanResult(
                projectUrl = "https://github.com/example/project",
                revision = "abc123def456",
                configHash = any()
            )
        } returns compressed

        val config = ScanConfiguration(
            useCache = true,
            forceRescan = false
        )

        val result = cachingWrapper.scanProject(
            projectDir = tempDir,
            projectUrl = "https://github.com/example/project",
            revision = "abc123def456",
            config = config
        )

        assertNotNull(result)
        assertEquals("cached-project", result.projectName)

        // Verify delegate was NOT called
        coVerify(exactly = 0) { mockDelegate.scanProject(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should call delegate and cache result on cache miss`(@TempDir tempDir: File) = runBlocking {
        val scanResult = createSampleScanResult("fresh-project")

        coEvery { mockCache.getCachedScanResult(any(), any(), any()) } returns null
        coEvery {
            mockDelegate.scanProject(any(), any(), any(), any(), any(), any())
        } returns scanResult
        coEvery { mockCache.cacheScanResult(any(), any(), any(), any(), any(), any(), any(), any()) } returns UUID.randomUUID()

        val config = ScanConfiguration(
            useCache = true,
            forceRescan = false
        )

        val result = cachingWrapper.scanProject(
            projectDir = tempDir,
            projectUrl = "https://github.com/example/fresh",
            revision = "def456abc789",
            config = config
        )

        assertNotNull(result)
        assertEquals("fresh-project", result.projectName)

        // Verify delegate was called
        coVerify(exactly = 1) { mockDelegate.scanProject(any(), any(), any(), any(), any(), any()) }

        // Verify result was cached
        coVerify(exactly = 1) {
            mockCache.cacheScanResult(
                projectUrl = "https://github.com/example/fresh",
                revision = "def456abc789",
                configHash = any(),
                ortVersion = any(),
                ortResult = any(),
                packageCount = scanResult.dependencies.size,
                issueCount = any(),
                ttlHours = any()
            )
        }
    }

    @Test
    fun `should not use cache when projectUrl is null`(@TempDir tempDir: File) = runBlocking {
        val scanResult = createSampleScanResult("no-url-project")

        coEvery {
            mockDelegate.scanProject(any(), any(), any(), any(), any(), any())
        } returns scanResult

        val config = ScanConfiguration(useCache = true)

        val result = cachingWrapper.scanProject(
            projectDir = tempDir,
            projectUrl = null,
            revision = "abc123",
            config = config
        )

        assertNotNull(result)

        // Verify cache was NOT accessed
        coVerify(exactly = 0) { mockCache.getCachedScanResult(any(), any(), any()) }
        coVerify(exactly = 0) { mockCache.cacheScanResult(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should not use cache when revision is null`(@TempDir tempDir: File) = runBlocking {
        val scanResult = createSampleScanResult("no-rev-project")

        coEvery {
            mockDelegate.scanProject(any(), any(), any(), any(), any(), any())
        } returns scanResult

        val config = ScanConfiguration(useCache = true)

        val result = cachingWrapper.scanProject(
            projectDir = tempDir,
            projectUrl = "https://github.com/example/project",
            revision = null,
            config = config
        )

        assertNotNull(result)

        // Cache should not be accessed when revision is null (and no git repo detected)
        coVerify(exactly = 0) { mockCache.getCachedScanResult(any(), any(), any()) }
    }

    @Test
    fun `should skip cache when forceRescan is true`(@TempDir tempDir: File) = runBlocking {
        val scanResult = createSampleScanResult("forced-project")

        coEvery {
            mockDelegate.scanProject(any(), any(), any(), any(), any(), any())
        } returns scanResult
        coEvery { mockCache.cacheScanResult(any(), any(), any(), any(), any(), any(), any(), any()) } returns UUID.randomUUID()

        val config = ScanConfiguration(
            useCache = true,
            forceRescan = true
        )

        val result = cachingWrapper.scanProject(
            projectDir = tempDir,
            projectUrl = "https://github.com/example/force",
            revision = "abc123",
            config = config
        )

        assertNotNull(result)

        // Cache read should be skipped
        coVerify(exactly = 0) { mockCache.getCachedScanResult(any(), any(), any()) }

        // But result should still be cached
        coVerify(exactly = 1) { mockCache.cacheScanResult(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should skip cache when useCache is false`(@TempDir tempDir: File) = runBlocking {
        val scanResult = createSampleScanResult("no-cache-project")

        coEvery {
            mockDelegate.scanProject(any(), any(), any(), any(), any(), any())
        } returns scanResult

        val config = ScanConfiguration(useCache = false)

        val result = cachingWrapper.scanProject(
            projectDir = tempDir,
            projectUrl = "https://github.com/example/nocache",
            revision = "abc123",
            config = config
        )

        assertNotNull(result)

        // Cache should not be accessed
        coVerify(exactly = 0) { mockCache.getCachedScanResult(any(), any(), any()) }
        coVerify(exactly = 0) { mockCache.cacheScanResult(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should generate consistent config hash for same configuration`() {
        val config1 = ScanConfiguration(
            allowDynamicVersions = true,
            skipExcluded = true,
            disabledPackageManagers = listOf("NPM", "Cargo"),
            enableSourceScan = false,
            enableAi = true,
            enableSpdx = false
        )

        val config2 = ScanConfiguration(
            allowDynamicVersions = true,
            skipExcluded = true,
            disabledPackageManagers = listOf("Cargo", "NPM"), // Different order
            enableSourceScan = false,
            enableAi = true,
            enableSpdx = false
        )

        val hash1 = cachingWrapper.generateConfigHash(config1)
        val hash2 = cachingWrapper.generateConfigHash(config2)

        // Should be same hash (package managers are sorted)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `should generate different config hash for different configurations`() {
        val config1 = ScanConfiguration(
            allowDynamicVersions = true,
            skipExcluded = true,
            disabledPackageManagers = emptyList(),
            enableSourceScan = false
        )

        val config2 = ScanConfiguration(
            allowDynamicVersions = false, // Different
            skipExcluded = true,
            disabledPackageManagers = emptyList(),
            enableSourceScan = false
        )

        val hash1 = cachingWrapper.generateConfigHash(config1)
        val hash2 = cachingWrapper.generateConfigHash(config2)

        assertTrue(hash1 != hash2)
    }

    @Test
    fun `should compress and decompress scan result correctly`() {
        val original = createSampleScanResult("compression-test")

        val compressed = cachingWrapper.compressAndSerialize(original)
        val decompressed = cachingWrapper.decompressAndDeserialize(compressed)

        assertEquals(original.projectName, decompressed.projectName)
        assertEquals(original.dependencies.size, decompressed.dependencies.size)
        assertEquals(original.summary.totalDependencies, decompressed.summary.totalDependencies)
    }

    @Test
    fun `should handle cache read exception gracefully`(@TempDir tempDir: File) = runBlocking {
        val scanResult = createSampleScanResult("error-recovery")

        coEvery { mockCache.getCachedScanResult(any(), any(), any()) } throws RuntimeException("Cache error")
        coEvery {
            mockDelegate.scanProject(any(), any(), any(), any(), any(), any())
        } returns scanResult
        coEvery { mockCache.cacheScanResult(any(), any(), any(), any(), any(), any(), any(), any()) } returns UUID.randomUUID()

        val config = ScanConfiguration(useCache = true)

        val result = cachingWrapper.scanProject(
            projectDir = tempDir,
            projectUrl = "https://github.com/example/error",
            revision = "xyz789abc123",
            config = config
        )

        // Should recover and use delegate
        assertNotNull(result)
        assertEquals("error-recovery", result.projectName)
    }

    @Test
    fun `should handle cache write exception gracefully`(@TempDir tempDir: File) = runBlocking {
        val scanResult = createSampleScanResult("write-error")

        coEvery { mockCache.getCachedScanResult(any(), any(), any()) } returns null
        coEvery {
            mockDelegate.scanProject(any(), any(), any(), any(), any(), any())
        } returns scanResult
        coEvery {
            mockCache.cacheScanResult(any(), any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("Write error")

        val config = ScanConfiguration(useCache = true)

        val result = cachingWrapper.scanProject(
            projectDir = tempDir,
            projectUrl = "https://github.com/example/writeerror",
            revision = "err123abc456",
            config = config
        )

        // Should return result despite cache write failure
        assertNotNull(result)
        assertEquals("write-error", result.projectName)
    }

    @Test
    fun `should work without cache (null cache)`(@TempDir tempDir: File) = runBlocking {
        val wrapperWithoutCache = CachingAnalyzerWrapper(
            cache = null,
            delegate = mockDelegate
        )

        val scanResult = createSampleScanResult("no-cache")

        coEvery {
            mockDelegate.scanProject(any(), any(), any(), any(), any(), any())
        } returns scanResult

        val config = ScanConfiguration(useCache = true) // Even though useCache=true, no cache is set

        val result = wrapperWithoutCache.scanProject(
            projectDir = tempDir,
            projectUrl = "https://github.com/example/nocache",
            revision = "abc123def456",
            config = config
        )

        assertNotNull(result)
        assertEquals("no-cache", result.projectName)

        // Delegate should be called
        coVerify(exactly = 1) { mockDelegate.scanProject(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `scanProjectDirect should bypass cache`(@TempDir tempDir: File) = runBlocking {
        val scanResult = createSampleScanResult("direct-scan")

        coEvery {
            mockDelegate.scanProject(any(), any(), any(), any(), any(), any())
        } returns scanResult

        val result = cachingWrapper.scanProjectDirect(
            projectDir = tempDir,
            demoMode = false,
            enableSourceScan = false,
            disabledPackageManagers = emptyList(),
            allowDynamicVersions = true,
            skipExcluded = true
        )

        assertNotNull(result)
        assertEquals("direct-scan", result.projectName)

        // Cache should not be accessed
        coVerify(exactly = 0) { mockCache.getCachedScanResult(any(), any(), any()) }
        coVerify(exactly = 0) { mockCache.cacheScanResult(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should get ORT version`() {
        val version = cachingWrapper.getOrtVersion()

        assertNotNull(version)
        assertTrue(version.isNotEmpty())
    }

    @Test
    fun `compression should reduce data size`() {
        val largeResult = ScanResult(
            projectName = "large-project",
            projectVersion = "1.0.0",
            scanDate = "2024-01-01",
            dependencies = (1..100).map { i ->
                Dependency(
                    id = "Maven:org.example:dep$i:$i.0.0",
                    name = "org.example:dep$i",
                    version = "$i.0.0",
                    declaredLicenses = listOf("MIT", "Apache-2.0"),
                    detectedLicenses = listOf("MIT"),
                    concludedLicense = "MIT",
                    scope = "compile",
                    isResolved = true
                )
            },
            summary = ScanSummary(
                totalDependencies = 100,
                resolvedLicenses = 100,
                unresolvedLicenses = 0,
                licenseDistribution = mapOf("MIT" to 100)
            ),
            unresolvedLicenses = emptyList()
        )

        val compressed = cachingWrapper.compressAndSerialize(largeResult)

        // Compressed should be smaller than original JSON
        val originalJson = kotlinx.serialization.json.Json.encodeToString(
            ScanResult.serializer(),
            largeResult
        )

        assertTrue(compressed.size < originalJson.length)
    }

    @Test
    fun `should respect TTL from configuration`(@TempDir tempDir: File) = runBlocking {
        val scanResult = createSampleScanResult("ttl-test")

        coEvery { mockCache.getCachedScanResult(any(), any(), any()) } returns null
        coEvery {
            mockDelegate.scanProject(any(), any(), any(), any(), any(), any())
        } returns scanResult
        coEvery { mockCache.cacheScanResult(any(), any(), any(), any(), any(), any(), any(), any()) } returns UUID.randomUUID()

        val customTtl = 48
        val config = ScanConfiguration(
            useCache = true,
            cacheTtlHours = customTtl
        )

        cachingWrapper.scanProject(
            projectDir = tempDir,
            projectUrl = "https://github.com/example/ttl",
            revision = "abc123def456",
            config = config
        )

        coVerify {
            mockCache.cacheScanResult(
                projectUrl = any(),
                revision = any(),
                configHash = any(),
                ortVersion = any(),
                ortResult = any(),
                packageCount = any(),
                issueCount = any(),
                ttlHours = customTtl
            )
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private fun createSampleScanResult(projectName: String): ScanResult {
        return ScanResult(
            projectName = projectName,
            projectVersion = "1.0.0",
            scanDate = "2024-01-01",
            dependencies = listOf(
                Dependency(
                    id = "Maven:org.example:lib:1.0.0",
                    name = "org.example:lib",
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
    }
}
