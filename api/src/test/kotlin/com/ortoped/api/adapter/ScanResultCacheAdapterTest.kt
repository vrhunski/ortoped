package com.ortoped.api.adapter

import com.ortoped.api.repository.OrtCacheRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScanResultCacheAdapterTest {

    private lateinit var mockRepository: OrtCacheRepository
    private lateinit var adapter: ScanResultCacheAdapter

    @BeforeEach
    fun setup() {
        mockRepository = mockk(relaxed = true)
        adapter = ScanResultCacheAdapter(mockRepository)
    }

    @Test
    fun `getCachedScanResult should return compressed data when found`() = runBlocking {
        val compressedData = "compressed-scan-result".toByteArray()

        every {
            mockRepository.getCachedScanResult(
                projectUrl = "https://github.com/example/project",
                revision = "abc123",
                configHash = "config-hash-1"
            )
        } returns compressedData

        val result = adapter.getCachedScanResult(
            projectUrl = "https://github.com/example/project",
            revision = "abc123",
            configHash = "config-hash-1"
        )

        assertNotNull(result)
        assertContentEquals(compressedData, result)
    }

    @Test
    fun `getCachedScanResult should return null when not found`() = runBlocking {
        every {
            mockRepository.getCachedScanResult(any(), any(), any())
        } returns null

        val result = adapter.getCachedScanResult(
            projectUrl = "https://github.com/nonexistent/project",
            revision = "xyz789",
            configHash = "unknown-hash"
        )

        assertNull(result)
    }

    @Test
    fun `getCachedScanResult should pass correct parameters to repository`() = runBlocking {
        val projectUrl = "https://github.com/test/repo"
        val revision = "feature-branch"
        val configHash = "sha256-hash"

        every {
            mockRepository.getCachedScanResult(projectUrl, revision, configHash)
        } returns null

        adapter.getCachedScanResult(projectUrl, revision, configHash)

        verify(exactly = 1) {
            mockRepository.getCachedScanResult(projectUrl, revision, configHash)
        }
    }

    @Test
    fun `cacheScanResult should store data via repository`() = runBlocking {
        val projectUrl = "https://github.com/example/new-project"
        val revision = "v1.0.0"
        val configHash = "new-config-hash"
        val ortVersion = "76.0.0"
        val ortResult = "compressed-result".toByteArray()
        val packageCount = 42
        val issueCount = 3
        val ttlHours = 168

        val generatedId = UUID.randomUUID()
        every {
            mockRepository.cacheScanResult(
                projectUrl = projectUrl,
                revision = revision,
                configHash = configHash,
                ortVersion = ortVersion,
                ortResult = ortResult,
                packageCount = packageCount,
                issueCount = issueCount,
                ttlHours = ttlHours
            )
        } returns generatedId

        adapter.cacheScanResult(
            projectUrl = projectUrl,
            revision = revision,
            configHash = configHash,
            ortVersion = ortVersion,
            ortResult = ortResult,
            packageCount = packageCount,
            issueCount = issueCount,
            ttlHours = ttlHours
        )

        verify(exactly = 1) {
            mockRepository.cacheScanResult(
                projectUrl = projectUrl,
                revision = revision,
                configHash = configHash,
                ortVersion = ortVersion,
                ortResult = ortResult,
                packageCount = packageCount,
                issueCount = issueCount,
                ttlHours = ttlHours
            )
        }
    }

    @Test
    fun `cacheScanResult should use default TTL when not specified`() = runBlocking {
        val generatedId = UUID.randomUUID()
        every {
            mockRepository.cacheScanResult(
                projectUrl = any(),
                revision = any(),
                configHash = any(),
                ortVersion = any(),
                ortResult = any(),
                packageCount = any(),
                issueCount = any(),
                ttlHours = 168 // Default 1 week
            )
        } returns generatedId

        adapter.cacheScanResult(
            projectUrl = "https://github.com/test/default-ttl",
            revision = "main",
            configHash = "hash",
            ortVersion = "76.0.0",
            ortResult = "data".toByteArray(),
            packageCount = 10,
            issueCount = 0,
            ttlHours = 168
        )

        verify {
            mockRepository.cacheScanResult(
                projectUrl = any(),
                revision = any(),
                configHash = any(),
                ortVersion = any(),
                ortResult = any(),
                packageCount = any(),
                issueCount = any(),
                ttlHours = 168
            )
        }
    }

    @Test
    fun `cacheScanResult should handle large compressed data`() = runBlocking {
        val largeData = ByteArray(1024 * 1024) { it.toByte() } // 1MB of data
        val generatedId = UUID.randomUUID()

        every {
            mockRepository.cacheScanResult(
                projectUrl = any(),
                revision = any(),
                configHash = any(),
                ortVersion = any(),
                ortResult = largeData,
                packageCount = any(),
                issueCount = any(),
                ttlHours = any()
            )
        } returns generatedId

        adapter.cacheScanResult(
            projectUrl = "https://github.com/test/large",
            revision = "main",
            configHash = "hash",
            ortVersion = "76.0.0",
            ortResult = largeData,
            packageCount = 1000,
            issueCount = 50,
            ttlHours = 24
        )

        verify {
            mockRepository.cacheScanResult(
                projectUrl = any(),
                revision = any(),
                configHash = any(),
                ortVersion = any(),
                ortResult = largeData,
                packageCount = 1000,
                issueCount = 50,
                ttlHours = 24
            )
        }
    }

    @Test
    fun `cacheScanResult should handle zero issue count`() = runBlocking {
        val generatedId = UUID.randomUUID()

        every {
            mockRepository.cacheScanResult(
                projectUrl = any(),
                revision = any(),
                configHash = any(),
                ortVersion = any(),
                ortResult = any(),
                packageCount = any(),
                issueCount = 0,
                ttlHours = any()
            )
        } returns generatedId

        adapter.cacheScanResult(
            projectUrl = "https://github.com/test/no-issues",
            revision = "clean",
            configHash = "hash",
            ortVersion = "76.0.0",
            ortResult = "{}".toByteArray(),
            packageCount = 5,
            issueCount = 0,
            ttlHours = 48
        )

        verify {
            mockRepository.cacheScanResult(
                projectUrl = any(),
                revision = any(),
                configHash = any(),
                ortVersion = any(),
                ortResult = any(),
                packageCount = 5,
                issueCount = 0,
                ttlHours = 48
            )
        }
    }
}
