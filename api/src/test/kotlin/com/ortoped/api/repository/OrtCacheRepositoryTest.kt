package com.ortoped.api.repository

import com.ortoped.api.model.LicenseResolutionCache
import com.ortoped.api.model.OrtPackageCache
import com.ortoped.api.model.OrtScanResultCache
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.time.Duration.Companion.hours

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrtCacheRepositoryTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("ortoped_test")
            .withUsername("test")
            .withPassword("test")
    }

    private lateinit var repository: OrtCacheRepository

    @BeforeAll
    fun setup() {
        postgres.start()
        Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password
        )

        transaction {
            SchemaUtils.create(
                OrtPackageCache,
                OrtScanResultCache,
                LicenseResolutionCache
            )
        }

        repository = OrtCacheRepository()
    }

    @AfterAll
    fun teardown() {
        postgres.stop()
    }

    @BeforeEach
    fun cleanTables() {
        transaction {
            SchemaUtils.drop(OrtPackageCache, OrtScanResultCache, LicenseResolutionCache)
            SchemaUtils.create(OrtPackageCache, OrtScanResultCache, LicenseResolutionCache)
        }
    }

    // ========================================================================
    // Package Cache Tests
    // ========================================================================

    @Test
    fun `should cache and retrieve package`() = runBlocking {
        val pkg = CachedPackage(
            packageId = "Maven:org.example:library:1.0.0",
            packageType = "Maven",
            ortVersion = "76.0.0",
            declaredLicenses = listOf("MIT", "Apache-2.0"),
            concludedLicense = "MIT",
            homepageUrl = "https://example.org",
            vcsUrl = "https://github.com/example/library"
        )

        val id = repository.cachePackage(pkg)
        assertNotNull(id)

        val cached = repository.getCachedPackage("Maven:org.example:library:1.0.0", "76.0.0")
        assertNotNull(cached)
        assertEquals("Maven:org.example:library:1.0.0", cached?.packageId)
        assertEquals("Maven", cached?.packageType)
        assertEquals(listOf("MIT", "Apache-2.0"), cached?.declaredLicenses)
        assertEquals("MIT", cached?.concludedLicense)
    }

    @Test
    fun `should return null for non-existent package`() = runBlocking {
        val cached = repository.getCachedPackage("Maven:non.existent:package:1.0.0", "76.0.0")
        assertNull(cached)
    }

    @Test
    fun `should update access count on package retrieval`() = runBlocking {
        val pkg = CachedPackage(
            packageId = "Maven:org.example:counted:1.0.0",
            packageType = "Maven",
            ortVersion = "76.0.0"
        )

        repository.cachePackage(pkg)

        // First access - returns the count BEFORE incrementing
        val first = repository.getCachedPackage("Maven:org.example:counted:1.0.0", "76.0.0")
        assertEquals(1, first?.accessCount) // Initial value before first access increment

        // Second access - DB was incremented after first access, now returns 2
        val second = repository.getCachedPackage("Maven:org.example:counted:1.0.0", "76.0.0")
        assertEquals(2, second?.accessCount) // After first increment

        // Third access - confirms increments are working
        val third = repository.getCachedPackage("Maven:org.example:counted:1.0.0", "76.0.0")
        assertEquals(3, third?.accessCount) // After second increment
    }

    @Test
    fun `should distinguish packages by ORT version`() = runBlocking {
        val pkg1 = CachedPackage(
            packageId = "Maven:org.example:lib:1.0.0",
            packageType = "Maven",
            ortVersion = "75.0.0",
            concludedLicense = "MIT"
        )
        val pkg2 = CachedPackage(
            packageId = "Maven:org.example:lib:1.0.0",
            packageType = "Maven",
            ortVersion = "76.0.0",
            concludedLicense = "Apache-2.0"
        )

        repository.cachePackage(pkg1)
        repository.cachePackage(pkg2)

        val cached75 = repository.getCachedPackage("Maven:org.example:lib:1.0.0", "75.0.0")
        val cached76 = repository.getCachedPackage("Maven:org.example:lib:1.0.0", "76.0.0")

        assertEquals("MIT", cached75?.concludedLicense)
        assertEquals("Apache-2.0", cached76?.concludedLicense)
    }

    // ========================================================================
    // Scan Result Cache Tests
    // ========================================================================

    @Test
    fun `should cache and retrieve scan result`() = runBlocking {
        val compressed = compressData("""{"projectName":"test","dependencies":[]}""")

        val id = repository.cacheScanResult(
            projectUrl = "https://github.com/example/project",
            revision = "abc123",
            configHash = "config-hash-123",
            ortVersion = "76.0.0",
            ortResult = compressed,
            packageCount = 10,
            issueCount = 2,
            ttlHours = 24
        )
        assertNotNull(id)

        val cached = repository.getCachedScanResult(
            projectUrl = "https://github.com/example/project",
            revision = "abc123",
            configHash = "config-hash-123"
        )
        assertNotNull(cached)
        assertArrayEquals(compressed, cached)
    }

    @Test
    fun `should return null for expired scan result`() = runBlocking {
        val compressed = compressData("""{"expired":true}""")

        repository.cacheScanResult(
            projectUrl = "https://github.com/example/expired",
            revision = "def456",
            configHash = "config-hash-456",
            ortVersion = "76.0.0",
            ortResult = compressed,
            packageCount = 5,
            ttlHours = -1 // Already expired
        )

        val cached = repository.getCachedScanResult(
            projectUrl = "https://github.com/example/expired",
            revision = "def456",
            configHash = "config-hash-456"
        )
        assertNull(cached)
    }

    @Test
    fun `should return null for non-existent scan result`() = runBlocking {
        val cached = repository.getCachedScanResult(
            projectUrl = "https://github.com/nonexistent/project",
            revision = "xyz789",
            configHash = "no-such-hash"
        )
        assertNull(cached)
    }

    @Test
    fun `should replace existing scan result on cache`() = runBlocking {
        val original = compressData("""{"version":1}""")
        val updated = compressData("""{"version":2}""")

        repository.cacheScanResult(
            projectUrl = "https://github.com/example/replace",
            revision = "rev1",
            configHash = "hash1",
            ortVersion = "76.0.0",
            ortResult = original,
            packageCount = 5
        )

        repository.cacheScanResult(
            projectUrl = "https://github.com/example/replace",
            revision = "rev1",
            configHash = "hash1",
            ortVersion = "76.0.0",
            ortResult = updated,
            packageCount = 10
        )

        val cached = repository.getCachedScanResult(
            projectUrl = "https://github.com/example/replace",
            revision = "rev1",
            configHash = "hash1"
        )
        assertArrayEquals(updated, cached)
    }

    @Test
    fun `should distinguish scan results by config hash`() = runBlocking {
        val result1 = compressData("""{"config":"a"}""")
        val result2 = compressData("""{"config":"b"}""")

        repository.cacheScanResult(
            projectUrl = "https://github.com/example/multi",
            revision = "rev1",
            configHash = "hash-a",
            ortVersion = "76.0.0",
            ortResult = result1,
            packageCount = 5
        )

        repository.cacheScanResult(
            projectUrl = "https://github.com/example/multi",
            revision = "rev1",
            configHash = "hash-b",
            ortVersion = "76.0.0",
            ortResult = result2,
            packageCount = 5
        )

        val cachedA = repository.getCachedScanResult(
            projectUrl = "https://github.com/example/multi",
            revision = "rev1",
            configHash = "hash-a"
        )
        val cachedB = repository.getCachedScanResult(
            projectUrl = "https://github.com/example/multi",
            revision = "rev1",
            configHash = "hash-b"
        )

        assertArrayEquals(result1, cachedA)
        assertArrayEquals(result2, cachedB)
    }

    // ========================================================================
    // License Resolution Cache Tests
    // ========================================================================

    @Test
    fun `should cache and retrieve license resolution`() = runBlocking {
        val resolution = CachedLicenseResolution(
            packageId = "Maven:org.example:lib:1.0.0",
            declaredLicenseRaw = "Apache License, Version 2.0",
            resolvedSpdxId = "Apache-2.0",
            resolutionSource = "AI",
            confidence = "HIGH",
            reasoning = "Exact match with Apache 2.0 license text"
        )

        repository.cacheResolution(resolution)

        val cached = repository.getCachedResolution(
            packageId = "Maven:org.example:lib:1.0.0",
            declaredLicense = "Apache License, Version 2.0"
        )
        assertNotNull(cached)
        assertEquals("Apache-2.0", cached?.resolvedSpdxId)
        assertEquals("AI", cached?.resolutionSource)
        assertEquals("HIGH", cached?.confidence)
    }

    @Test
    fun `should return null for non-existent resolution`() = runBlocking {
        val cached = repository.getCachedResolution(
            packageId = "Maven:nonexistent:package:1.0.0",
            declaredLicense = "Unknown License"
        )
        assertNull(cached)
    }

    @Test
    fun `should handle null declared license in resolution`() = runBlocking {
        val resolution = CachedLicenseResolution(
            packageId = "Maven:org.example:noLicense:1.0.0",
            declaredLicenseRaw = null,
            resolvedSpdxId = "MIT",
            resolutionSource = "AI",
            confidence = "MEDIUM",
            reasoning = "Inferred from package metadata"
        )

        repository.cacheResolution(resolution)

        val cached = repository.getCachedResolution(
            packageId = "Maven:org.example:noLicense:1.0.0",
            declaredLicense = null
        )
        assertNotNull(cached)
        assertEquals("MIT", cached?.resolvedSpdxId)
    }

    // ========================================================================
    // Cache Cleanup Tests
    // ========================================================================

    @Test
    fun `should clean expired cache entries`() = runBlocking {
        // Add expired entry
        repository.cacheScanResult(
            projectUrl = "https://github.com/example/expired1",
            revision = "rev1",
            configHash = "hash1",
            ortVersion = "76.0.0",
            ortResult = compressData("{}"),
            packageCount = 1,
            ttlHours = -24 // Expired 24 hours ago
        )

        // Add valid entry
        repository.cacheScanResult(
            projectUrl = "https://github.com/example/valid",
            revision = "rev2",
            configHash = "hash2",
            ortVersion = "76.0.0",
            ortResult = compressData("{}"),
            packageCount = 1,
            ttlHours = 24 // Valid for 24 more hours
        )

        val deleted = repository.cleanExpiredCache()
        assertTrue(deleted >= 1)

        // Valid entry should still exist
        val valid = repository.getCachedScanResult(
            projectUrl = "https://github.com/example/valid",
            revision = "rev2",
            configHash = "hash2"
        )
        assertNotNull(valid)
    }

    @Test
    fun `should invalidate project cache`() = runBlocking {
        val projectUrl = "https://github.com/example/to-invalidate"

        repository.cacheScanResult(
            projectUrl = projectUrl,
            revision = "rev1",
            configHash = "hash1",
            ortVersion = "76.0.0",
            ortResult = compressData("{}"),
            packageCount = 1
        )

        repository.cacheScanResult(
            projectUrl = projectUrl,
            revision = "rev2",
            configHash = "hash2",
            ortVersion = "76.0.0",
            ortResult = compressData("{}"),
            packageCount = 1
        )

        val deleted = repository.invalidateProject(projectUrl)
        assertEquals(2, deleted)

        val cached1 = repository.getCachedScanResult(projectUrl, "rev1", "hash1")
        val cached2 = repository.getCachedScanResult(projectUrl, "rev2", "hash2")

        assertNull(cached1)
        assertNull(cached2)
    }

    @Test
    fun `should invalidate package cache`() = runBlocking {
        val pkg = CachedPackage(
            packageId = "Maven:org.example:to-invalidate:1.0.0",
            packageType = "Maven",
            ortVersion = "76.0.0"
        )

        repository.cachePackage(pkg)

        val deleted = repository.invalidatePackage("Maven:org.example:to-invalidate:1.0.0")
        assertEquals(1, deleted)

        val cached = repository.getCachedPackage("Maven:org.example:to-invalidate:1.0.0", "76.0.0")
        assertNull(cached)
    }

    // ========================================================================
    // Statistics Tests
    // ========================================================================

    @Test
    fun `should return cache statistics`() = runBlocking {
        // Add some entries
        repository.cachePackage(CachedPackage(
            packageId = "Maven:org.example:pkg1:1.0.0",
            packageType = "Maven",
            ortVersion = "76.0.0"
        ))
        repository.cachePackage(CachedPackage(
            packageId = "NPM:example-pkg:2.0.0",
            packageType = "NPM",
            ortVersion = "76.0.0"
        ))

        repository.cacheScanResult(
            projectUrl = "https://github.com/example/stats",
            revision = "rev1",
            configHash = "hash1",
            ortVersion = "76.0.0",
            ortResult = compressData("{}"),
            packageCount = 5
        )

        repository.cacheResolution(CachedLicenseResolution(
            packageId = "Maven:org.example:res:1.0.0",
            resolvedSpdxId = "MIT",
            resolutionSource = "AI",
            confidence = "HIGH"
        ))

        val stats = repository.getStats()

        assertEquals(2L, stats.cachedPackages)
        assertEquals(1L, stats.cachedScans)
        assertEquals(1L, stats.cachedResolutions)
        assertTrue(stats.totalSizeBytes > 0)
    }

    @Test
    fun `should return package type distribution`() = runBlocking {
        repository.cachePackage(CachedPackage(
            packageId = "Maven:org.example:pkg1:1.0.0",
            packageType = "Maven",
            ortVersion = "76.0.0"
        ))
        repository.cachePackage(CachedPackage(
            packageId = "Maven:org.example:pkg2:1.0.0",
            packageType = "Maven",
            ortVersion = "76.0.0"
        ))
        repository.cachePackage(CachedPackage(
            packageId = "NPM:example:1.0.0",
            packageType = "NPM",
            ortVersion = "76.0.0"
        ))

        val distribution = repository.getPackageTypeDistribution()

        assertEquals(2L, distribution["Maven"])
        assertEquals(1L, distribution["NPM"])
    }

    @Test
    fun `should return resolution source distribution`() = runBlocking {
        repository.cacheResolution(CachedLicenseResolution(
            packageId = "Maven:pkg1:1.0.0",
            resolvedSpdxId = "MIT",
            resolutionSource = "AI",
            confidence = "HIGH"
        ))
        repository.cacheResolution(CachedLicenseResolution(
            packageId = "Maven:pkg2:1.0.0",
            resolvedSpdxId = "Apache-2.0",
            resolutionSource = "AI",
            confidence = "HIGH"
        ))
        repository.cacheResolution(CachedLicenseResolution(
            packageId = "Maven:pkg3:1.0.0",
            resolvedSpdxId = "GPL-3.0",
            resolutionSource = "SPDX_MATCH",
            confidence = "HIGH"
        ))

        val distribution = repository.getResolutionSourceDistribution()

        assertEquals(2L, distribution["AI"])
        assertEquals(1L, distribution["SPDX_MATCH"])
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private fun compressData(data: String): ByteArray {
        return ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gzip ->
                gzip.write(data.toByteArray(Charsets.UTF_8))
            }
            baos.toByteArray()
        }
    }
}
