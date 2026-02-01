package com.ortoped.api.adapter

import com.ortoped.api.repository.CachedLicenseResolution
import com.ortoped.api.repository.OrtCacheRepository
import com.ortoped.core.model.LicenseSuggestion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LicenseResolutionCacheAdapterTest {

    private lateinit var mockRepository: OrtCacheRepository
    private lateinit var adapter: LicenseResolutionCacheAdapter

    @BeforeEach
    fun setup() {
        mockRepository = mockk(relaxed = true)
        adapter = LicenseResolutionCacheAdapter(mockRepository)
    }

    @Test
    fun `getCachedResolution should return CachedResolution when found`() = runTest {
        val cachedInDb = CachedLicenseResolution(
            packageId = "Maven:org.example:lib:1.0.0",
            declaredLicenseRaw = "MIT License",
            resolvedSpdxId = "MIT",
            resolutionSource = "AI",
            confidence = "HIGH",
            reasoning = "Exact match"
        )

        coEvery {
            mockRepository.getCachedResolution("Maven:org.example:lib:1.0.0", "MIT License")
        } returns cachedInDb

        val result = adapter.getCachedResolution("Maven:org.example:lib:1.0.0", "MIT License")

        assertNotNull(result)
        assertEquals("Maven:org.example:lib:1.0.0", result.packageId)
        assertEquals("MIT License", result.declaredLicenseRaw)
        assertEquals("MIT", result.resolvedSpdxId)
        assertEquals("AI", result.resolutionSource)
        assertEquals("HIGH", result.confidence)
        assertEquals("Exact match", result.reasoning)
    }

    @Test
    fun `getCachedResolution should return null when not found`() = runTest {
        coEvery {
            mockRepository.getCachedResolution(any(), any())
        } returns null

        val result = adapter.getCachedResolution("Maven:nonexistent:pkg:1.0.0", null)

        assertNull(result)
    }

    @Test
    fun `getCachedResolution should handle null declared license`() = runTest {
        val cachedInDb = CachedLicenseResolution(
            packageId = "Maven:org.example:noLicense:1.0.0",
            declaredLicenseRaw = null,
            resolvedSpdxId = "Apache-2.0",
            resolutionSource = "AI",
            confidence = "MEDIUM"
        )

        coEvery {
            mockRepository.getCachedResolution("Maven:org.example:noLicense:1.0.0", null)
        } returns cachedInDb

        val result = adapter.getCachedResolution("Maven:org.example:noLicense:1.0.0", null)

        assertNotNull(result)
        assertNull(result.declaredLicenseRaw)
        assertEquals("Apache-2.0", result.resolvedSpdxId)
    }

    @Test
    fun `getCachedResolution should handle missing resolution source`() = runTest {
        val cachedInDb = CachedLicenseResolution(
            packageId = "Maven:org.example:legacy:1.0.0",
            resolvedSpdxId = "GPL-3.0",
            resolutionSource = null,
            confidence = "HIGH"
        )

        coEvery {
            mockRepository.getCachedResolution("Maven:org.example:legacy:1.0.0", null)
        } returns cachedInDb

        val result = adapter.getCachedResolution("Maven:org.example:legacy:1.0.0", null)

        assertNotNull(result)
        assertEquals("UNKNOWN", result.resolutionSource)
    }

    @Test
    fun `cacheResolution should store resolution via repository`() = runTest {
        val suggestion = LicenseSuggestion(
            suggestedLicense = "BSD-3-Clause License",
            confidence = "HIGH",
            reasoning = "Pattern match with BSD template",
            spdxId = "BSD-3-Clause",
            alternatives = listOf("MIT", "ISC")
        )

        adapter.cacheResolution(
            packageId = "Maven:org.example:new:1.0.0",
            declaredLicense = "BSD 3-Clause",
            resolution = suggestion,
            source = "AI"
        )

        coVerify {
            mockRepository.cacheResolution(match { cached ->
                cached.packageId == "Maven:org.example:new:1.0.0" &&
                cached.declaredLicenseRaw == "BSD 3-Clause" &&
                cached.resolvedSpdxId == "BSD-3-Clause" &&
                cached.resolutionSource == "AI" &&
                cached.confidence == "HIGH" &&
                cached.reasoning == "Pattern match with BSD template"
            })
        }
    }

    @Test
    fun `cacheResolution should use suggestedLicense when spdxId is null`() = runTest {
        val suggestion = LicenseSuggestion(
            suggestedLicense = "Custom License",
            confidence = "LOW",
            reasoning = "Unknown license",
            spdxId = null,
            alternatives = emptyList()
        )

        adapter.cacheResolution(
            packageId = "Maven:org.example:custom:1.0.0",
            declaredLicense = "Custom",
            resolution = suggestion,
            source = "AI"
        )

        coVerify {
            mockRepository.cacheResolution(match { cached ->
                cached.resolvedSpdxId == "Custom License"
            })
        }
    }

    @Test
    fun `cacheResolution should handle null declared license`() = runTest {
        val suggestion = LicenseSuggestion(
            suggestedLicense = "MIT",
            confidence = "MEDIUM",
            reasoning = "Inferred from metadata",
            spdxId = "MIT"
        )

        adapter.cacheResolution(
            packageId = "Maven:org.example:inferred:1.0.0",
            declaredLicense = null,
            resolution = suggestion,
            source = "AI"
        )

        coVerify {
            mockRepository.cacheResolution(match { cached ->
                cached.packageId == "Maven:org.example:inferred:1.0.0" &&
                cached.declaredLicenseRaw == null &&
                cached.resolvedSpdxId == "MIT"
            })
        }
    }

    @Test
    fun `cacheResolution should use provided source`() = runTest {
        val suggestion = LicenseSuggestion(
            suggestedLicense = "Apache-2.0",
            confidence = "HIGH",
            reasoning = "SPDX match",
            spdxId = "Apache-2.0"
        )

        adapter.cacheResolution(
            packageId = "Maven:org.example:spdx:1.0.0",
            declaredLicense = "Apache 2.0",
            resolution = suggestion,
            source = "SPDX_MATCH"
        )

        coVerify {
            mockRepository.cacheResolution(match { cached ->
                cached.resolutionSource == "SPDX_MATCH"
            })
        }
    }
}
