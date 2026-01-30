package com.ortoped.api.routes

import com.ortoped.api.model.LicenseResolutionCache
import com.ortoped.api.model.OrtPackageCache
import com.ortoped.api.model.OrtScanResultCache
import com.ortoped.api.repository.CacheStats
import com.ortoped.api.repository.OrtCacheRepository
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CacheRoutesTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("ortoped_test")
            .withUsername("test")
            .withPassword("test")
    }

    private lateinit var repository: OrtCacheRepository
    private val json = Json { ignoreUnknownKeys = true }

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

    private fun Application.testModule() {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
        routing {
            cacheRoutes(repository)
        }
    }

    // ========================================================================
    // GET /cache/stats Tests
    // ========================================================================

    @Test
    fun `GET stats should return empty statistics`() = testApplication {
        application { testModule() }

        val response = client.get("/cache/stats")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
        assertEquals(0L, body["cachedPackages"]?.jsonPrimitive?.long)
        assertEquals(0L, body["cachedScans"]?.jsonPrimitive?.long)
        assertEquals(0L, body["cachedResolutions"]?.jsonPrimitive?.long)
    }

    @Test
    fun `GET stats should return populated statistics`() = testApplication {
        application { testModule() }

        // Add some data
        kotlinx.coroutines.runBlocking {
            repository.cachePackage(com.ortoped.api.repository.CachedPackage(
                packageId = "Maven:test:pkg:1.0.0",
                packageType = "Maven",
                ortVersion = "76.0.0"
            ))
            repository.cacheScanResult(
                projectUrl = "https://github.com/test/project",
                revision = "abc123",
                configHash = "hash123",
                ortVersion = "76.0.0",
                ortResult = "{}".toByteArray(),
                packageCount = 5
            )
            repository.cacheResolution(com.ortoped.api.repository.CachedLicenseResolution(
                packageId = "Maven:test:res:1.0.0",
                resolvedSpdxId = "MIT",
                resolutionSource = "AI",
                confidence = "HIGH"
            ))
        }

        val response = client.get("/cache/stats")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
        assertEquals(1L, body["cachedPackages"]?.jsonPrimitive?.long)
        assertEquals(1L, body["cachedScans"]?.jsonPrimitive?.long)
        assertEquals(1L, body["cachedResolutions"]?.jsonPrimitive?.long)
    }

    // ========================================================================
    // POST /cache/cleanup Tests
    // ========================================================================

    @Test
    fun `POST cleanup should remove expired entries`() = testApplication {
        application { testModule() }

        // Add expired entry
        kotlinx.coroutines.runBlocking {
            repository.cacheScanResult(
                projectUrl = "https://github.com/test/expired",
                revision = "exp123",
                configHash = "hash1",
                ortVersion = "76.0.0",
                ortResult = "{}".toByteArray(),
                packageCount = 1,
                ttlHours = -24 // Already expired
            )
        }

        val response = client.post("/cache/cleanup")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
        assertTrue(body["deletedEntries"]?.jsonPrimitive?.long?.let { it >= 1 } ?: false)
    }

    // ========================================================================
    // DELETE /cache/project Tests
    // ========================================================================

    @Test
    fun `DELETE project should require url parameter`() = testApplication {
        application { testModule() }

        val response = client.delete("/cache/project")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE project should invalidate project cache`() = testApplication {
        application { testModule() }

        val projectUrl = "https://github.com/test/to-delete"

        kotlinx.coroutines.runBlocking {
            repository.cacheScanResult(
                projectUrl = projectUrl,
                revision = "rev1",
                configHash = "hash1",
                ortVersion = "76.0.0",
                ortResult = "{}".toByteArray(),
                packageCount = 1
            )
        }

        val response = client.delete("/cache/project?url=$projectUrl")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
        assertEquals(1L, body["deletedEntries"]?.jsonPrimitive?.long)
    }

    // ========================================================================
    // DELETE /cache/package Tests
    // ========================================================================

    @Test
    fun `DELETE package should require packageId parameter`() = testApplication {
        application { testModule() }

        val response = client.delete("/cache/package")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE package should invalidate package cache`() = testApplication {
        application { testModule() }

        val packageId = "Maven:test:to-delete:1.0.0"

        kotlinx.coroutines.runBlocking {
            repository.cachePackage(com.ortoped.api.repository.CachedPackage(
                packageId = packageId,
                packageType = "Maven",
                ortVersion = "76.0.0"
            ))
        }

        val response = client.delete("/cache/package?packageId=$packageId")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
        assertEquals(1L, body["deletedEntries"]?.jsonPrimitive?.long)
    }

    // ========================================================================
    // GET /cache/project Tests
    // ========================================================================

    @Test
    fun `GET project should require url parameter`() = testApplication {
        application { testModule() }

        val response = client.get("/cache/project")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET project should return cache entries for project`() = testApplication {
        application { testModule() }

        val projectUrl = "https://github.com/test/entries"

        kotlinx.coroutines.runBlocking {
            repository.cacheScanResult(
                projectUrl = projectUrl,
                revision = "rev1",
                configHash = "hash1",
                ortVersion = "76.0.0",
                ortResult = "{}".toByteArray(),
                packageCount = 5
            )
            repository.cacheScanResult(
                projectUrl = projectUrl,
                revision = "rev2",
                configHash = "hash2",
                ortVersion = "76.0.0",
                ortResult = "{}".toByteArray(),
                packageCount = 10
            )
        }

        val response = client.get("/cache/project?url=$projectUrl")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        assertTrue(body.contains("rev1"))
        assertTrue(body.contains("rev2"))
    }

    // ========================================================================
    // GET /cache/packages/distribution Tests
    // ========================================================================

    @Test
    fun `GET packages distribution should return type distribution`() = testApplication {
        application { testModule() }

        kotlinx.coroutines.runBlocking {
            repository.cachePackage(com.ortoped.api.repository.CachedPackage(
                packageId = "Maven:test:pkg1:1.0.0",
                packageType = "Maven",
                ortVersion = "76.0.0"
            ))
            repository.cachePackage(com.ortoped.api.repository.CachedPackage(
                packageId = "Maven:test:pkg2:1.0.0",
                packageType = "Maven",
                ortVersion = "76.0.0"
            ))
            repository.cachePackage(com.ortoped.api.repository.CachedPackage(
                packageId = "NPM:test-pkg:1.0.0",
                packageType = "NPM",
                ortVersion = "76.0.0"
            ))
        }

        val response = client.get("/cache/packages/distribution")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
        val distribution = body["distribution"] as JsonObject

        assertEquals(2L, distribution["Maven"]?.jsonPrimitive?.long)
        assertEquals(1L, distribution["NPM"]?.jsonPrimitive?.long)
        assertEquals(3L, body["total"]?.jsonPrimitive?.long)
    }

    // ========================================================================
    // GET /cache/resolutions/distribution Tests
    // ========================================================================

    @Test
    fun `GET resolutions distribution should return source distribution`() = testApplication {
        application { testModule() }

        kotlinx.coroutines.runBlocking {
            repository.cacheResolution(com.ortoped.api.repository.CachedLicenseResolution(
                packageId = "Maven:test:ai1:1.0.0",
                resolvedSpdxId = "MIT",
                resolutionSource = "AI",
                confidence = "HIGH"
            ))
            repository.cacheResolution(com.ortoped.api.repository.CachedLicenseResolution(
                packageId = "Maven:test:ai2:1.0.0",
                resolvedSpdxId = "Apache-2.0",
                resolutionSource = "AI",
                confidence = "HIGH"
            ))
            repository.cacheResolution(com.ortoped.api.repository.CachedLicenseResolution(
                packageId = "Maven:test:spdx1:1.0.0",
                resolvedSpdxId = "GPL-3.0",
                resolutionSource = "SPDX_MATCH",
                confidence = "HIGH"
            ))
        }

        val response = client.get("/cache/resolutions/distribution")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
        val distribution = body["distribution"] as JsonObject

        assertEquals(2L, distribution["AI"]?.jsonPrimitive?.long)
        assertEquals(1L, distribution["SPDX_MATCH"]?.jsonPrimitive?.long)
        assertEquals(3L, body["total"]?.jsonPrimitive?.long)
    }

    // ========================================================================
    // Edge Cases and Error Handling Tests
    // ========================================================================

    @Test
    fun `GET stats should handle empty database`() = testApplication {
        application { testModule() }

        val response = client.get("/cache/stats")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
        assertEquals(0L, body["cachedPackages"]?.jsonPrimitive?.long)
        assertEquals(0L, body["cachedScans"]?.jsonPrimitive?.long)
        assertEquals(0L, body["cachedResolutions"]?.jsonPrimitive?.long)
        assertEquals(0L, body["expiredEntries"]?.jsonPrimitive?.long)
    }

    @Test
    fun `DELETE project with non-existent url should return zero deleted`() = testApplication {
        application { testModule() }

        val response = client.delete("/cache/project?url=https://github.com/nonexistent/project")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
        assertEquals(0L, body["deletedEntries"]?.jsonPrimitive?.long)
    }

    @Test
    fun `GET packages distribution should handle empty database`() = testApplication {
        application { testModule() }

        val response = client.get("/cache/packages/distribution")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
        assertEquals(0L, body["total"]?.jsonPrimitive?.long)
    }

    @Test
    fun `GET resolutions distribution should handle empty database`() = testApplication {
        application { testModule() }

        val response = client.get("/cache/resolutions/distribution")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
        assertEquals(0L, body["total"]?.jsonPrimitive?.long)
    }
}
