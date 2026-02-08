package com.ortoped.core.cache

import com.ortoped.core.model.Dependency
import com.ortoped.core.model.ScanResult
import com.ortoped.core.model.ScanSummary
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class LocalFileCacheTest {

    private fun createTestScanResult(): ScanResult = ScanResult(
        projectName = "test-project",
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

    @Test
    fun `should cache and retrieve scan result`(@TempDir tempDir: File) {
        val cache = LocalFileCache(tempDir)
        val scanResult = createTestScanResult()
        val hash = "abc123"

        cache.cacheScan(hash, scanResult)

        assertTrue(cache.hasCachedScan(hash))
        val loaded = cache.loadCachedScan(hash)
        assertNotNull(loaded)
        assertEquals("test-project", loaded.projectName)
        assertEquals(1, loaded.dependencies.size)
    }

    @Test
    fun `should return null for missing cache entry`(@TempDir tempDir: File) {
        val cache = LocalFileCache(tempDir)

        assertFalse(cache.hasCachedScan("nonexistent"))
        assertNull(cache.loadCachedScan("nonexistent"))
    }

    @Test
    fun `should handle corrupted cache file gracefully`(@TempDir tempDir: File) {
        val cache = LocalFileCache(tempDir)
        val hash = "corrupted"

        // Write invalid JSON
        File(tempDir, "$hash.json").writeText("not valid json at all")

        assertTrue(cache.hasCachedScan(hash)) // File exists
        assertNull(cache.loadCachedScan(hash)) // But content is corrupted
    }

    @Test
    fun `should clear cache`(@TempDir tempDir: File) {
        val cache = LocalFileCache(tempDir)
        val scanResult = createTestScanResult()

        cache.cacheScan("hash1", scanResult)
        cache.cacheScan("hash2", scanResult)
        assertTrue(cache.hasCachedScan("hash1"))
        assertTrue(cache.hasCachedScan("hash2"))

        cache.clearCache()

        assertFalse(cache.hasCachedScan("hash1"))
        assertFalse(cache.hasCachedScan("hash2"))
    }

    @Test
    fun `should create from project directory`(@TempDir tempDir: File) {
        val cache = LocalFileCache.fromProject(tempDir)
        val scanResult = createTestScanResult()

        cache.cacheScan("test-hash", scanResult)

        // Verify it's stored in .ortoped/cache/
        val cacheFile = File(tempDir, ".ortoped/cache/test-hash.json")
        assertTrue(cacheFile.exists())
    }
}
