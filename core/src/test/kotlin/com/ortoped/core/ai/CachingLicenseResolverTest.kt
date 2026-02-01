package com.ortoped.core.ai

import com.ortoped.core.model.LicenseSuggestion
import com.ortoped.core.model.UnresolvedLicense
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CachingLicenseResolverTest {

    private lateinit var mockCache: LicenseResolutionCache
    private lateinit var mockDelegate: LicenseResolver
    private lateinit var cachingResolver: CachingLicenseResolver

    @BeforeEach
    fun setup() {
        mockCache = mockk(relaxed = true)
        mockDelegate = mockk()
        cachingResolver = CachingLicenseResolver(
            cache = mockCache,
            delegate = mockDelegate
        )
    }

    @Test
    fun `should return cached resolution on cache hit`() = runTest {
        val unresolved = UnresolvedLicense(
            dependencyId = "Maven:org.example:lib:1.0.0",
            dependencyName = "org.example:lib",
            reason = "License not found",
            licenseText = "MIT License text..."
        )

        val cachedResolution = CachedResolution(
            packageId = "Maven:org.example:lib:1.0.0",
            declaredLicenseRaw = "MIT License text...",
            resolvedSpdxId = "MIT",
            resolutionSource = "AI",
            confidence = "HIGH",
            reasoning = "Cached AI resolution"
        )

        coEvery { mockCache.getCachedResolution(any(), any()) } returns cachedResolution

        val result = cachingResolver.resolveLicense(unresolved)

        assertNotNull(result)
        assertEquals("MIT", result.suggestedLicense)
        assertEquals("HIGH", result.confidence)

        // Verify delegate was NOT called (cache hit)
        coVerify(exactly = 0) { mockDelegate.resolveLicense(any()) }
    }

    @Test
    fun `should call delegate and cache result on cache miss`() = runTest {
        val unresolved = UnresolvedLicense(
            dependencyId = "Maven:org.example:uncached:1.0.0",
            dependencyName = "org.example:uncached",
            reason = "License not found",
            licenseText = "Apache License 2.0 text..."
        )

        val aiSuggestion = LicenseSuggestion(
            suggestedLicense = "Apache License 2.0",
            confidence = "HIGH",
            reasoning = "Exact match with Apache 2.0",
            spdxId = "Apache-2.0",
            alternatives = listOf("MIT")
        )

        coEvery { mockCache.getCachedResolution(any(), any()) } returns null
        coEvery { mockDelegate.resolveLicense(any()) } returns aiSuggestion

        val result = cachingResolver.resolveLicense(unresolved)

        assertNotNull(result)
        assertEquals("Apache License 2.0", result.suggestedLicense)
        assertEquals("Apache-2.0", result.spdxId)

        // Verify delegate was called
        coVerify(exactly = 1) { mockDelegate.resolveLicense(unresolved) }

        // Verify result was cached
        coVerify(exactly = 1) {
            mockCache.cacheResolution(
                packageId = "Maven:org.example:uncached:1.0.0",
                declaredLicense = any(),
                resolution = aiSuggestion,
                source = "AI"
            )
        }
    }

    @Test
    fun `should not cache when delegate returns null`() = runTest {
        val unresolved = UnresolvedLicense(
            dependencyId = "Maven:org.example:unknown:1.0.0",
            dependencyName = "org.example:unknown",
            reason = "License not found"
        )

        coEvery { mockCache.getCachedResolution(any(), any()) } returns null
        coEvery { mockDelegate.resolveLicense(any()) } returns null

        val result = cachingResolver.resolveLicense(unresolved)

        assertNull(result)

        // Verify cacheResolution was NOT called
        coVerify(exactly = 0) { mockCache.cacheResolution(any(), any(), any(), any()) }
    }

    @Test
    fun `should work without cache (null cache)`() = runTest {
        val resolverWithoutCache = CachingLicenseResolver(
            cache = null,
            delegate = mockDelegate
        )

        val unresolved = UnresolvedLicense(
            dependencyId = "Maven:org.example:nocache:1.0.0",
            dependencyName = "org.example:nocache",
            reason = "License not found"
        )

        val suggestion = LicenseSuggestion(
            suggestedLicense = "BSD-3-Clause",
            confidence = "MEDIUM",
            reasoning = "Pattern match",
            spdxId = "BSD-3-Clause"
        )

        coEvery { mockDelegate.resolveLicense(any()) } returns suggestion

        val result = resolverWithoutCache.resolveLicense(unresolved)

        assertNotNull(result)
        assertEquals("BSD-3-Clause", result.suggestedLicense)

        // Delegate should be called
        coVerify(exactly = 1) { mockDelegate.resolveLicense(unresolved) }
    }

    @Test
    fun `should truncate license text for cache key`() = runTest {
        val longLicenseText = "A".repeat(1000) // Longer than 500 chars

        val unresolved = UnresolvedLicense(
            dependencyId = "Maven:org.example:longtext:1.0.0",
            dependencyName = "org.example:longtext",
            reason = "License not found",
            licenseText = longLicenseText
        )

        coEvery { mockCache.getCachedResolution(any(), any()) } returns null
        coEvery { mockDelegate.resolveLicense(any()) } returns LicenseSuggestion(
            suggestedLicense = "MIT",
            confidence = "HIGH",
            reasoning = "Match",
            spdxId = "MIT"
        )

        cachingResolver.resolveLicense(unresolved)

        // Verify cache was called with truncated text
        coVerify {
            mockCache.getCachedResolution(
                "Maven:org.example:longtext:1.0.0",
                match { it != null && it.length == 500 }
            )
        }
    }

    @Test
    fun `should handle cache exception gracefully`() = runTest {
        val unresolved = UnresolvedLicense(
            dependencyId = "Maven:org.example:error:1.0.0",
            dependencyName = "org.example:error",
            reason = "License not found"
        )

        val suggestion = LicenseSuggestion(
            suggestedLicense = "GPL-3.0",
            confidence = "HIGH",
            reasoning = "Exact match",
            spdxId = "GPL-3.0-only"
        )

        coEvery { mockCache.getCachedResolution(any(), any()) } throws RuntimeException("Cache error")
        coEvery { mockDelegate.resolveLicense(any()) } returns suggestion

        // Should not throw, should fall back to delegate
        val result = cachingResolver.resolveLicense(unresolved)

        assertNotNull(result)
        assertEquals("GPL-3.0", result.suggestedLicense)
    }

    @Test
    fun `should handle cache write exception gracefully`() = runTest {
        val unresolved = UnresolvedLicense(
            dependencyId = "Maven:org.example:writeerror:1.0.0",
            dependencyName = "org.example:writeerror",
            reason = "License not found"
        )

        val suggestion = LicenseSuggestion(
            suggestedLicense = "LGPL-2.1",
            confidence = "HIGH",
            reasoning = "Match",
            spdxId = "LGPL-2.1-only"
        )

        coEvery { mockCache.getCachedResolution(any(), any()) } returns null
        coEvery { mockDelegate.resolveLicense(any()) } returns suggestion
        coEvery { mockCache.cacheResolution(any(), any(), any(), any()) } throws RuntimeException("Write error")

        // Should not throw, should return result despite cache write failure
        val result = cachingResolver.resolveLicense(unresolved)

        assertNotNull(result)
        assertEquals("LGPL-2.1", result.suggestedLicense)
    }

    @Test
    fun `resolveLicenseDirect should bypass cache`() = runTest {
        val unresolved = UnresolvedLicense(
            dependencyId = "Maven:org.example:direct:1.0.0",
            dependencyName = "org.example:direct",
            reason = "License not found"
        )

        val suggestion = LicenseSuggestion(
            suggestedLicense = "ISC",
            confidence = "HIGH",
            reasoning = "Direct resolution",
            spdxId = "ISC"
        )

        coEvery { mockDelegate.resolveLicense(any()) } returns suggestion

        val result = cachingResolver.resolveLicenseDirect(unresolved)

        assertNotNull(result)
        assertEquals("ISC", result.suggestedLicense)

        // Verify cache was NOT accessed
        coVerify(exactly = 0) { mockCache.getCachedResolution(any(), any()) }
        coVerify(exactly = 0) { mockCache.cacheResolution(any(), any(), any(), any()) }
    }

    @Test
    fun `should convert CachedResolution to LicenseSuggestion correctly`() {
        val cached = CachedResolution(
            packageId = "Maven:test:pkg:1.0.0",
            declaredLicenseRaw = "MIT License",
            resolvedSpdxId = "MIT",
            resolutionSource = "AI",
            confidence = "HIGH",
            reasoning = "Exact match with MIT license"
        )

        val suggestion = cached.toLicenseSuggestion()

        assertEquals("MIT", suggestion.suggestedLicense)
        assertEquals("MIT", suggestion.spdxId)
        assertEquals("HIGH", suggestion.confidence)
        assertEquals("Exact match with MIT license", suggestion.reasoning)
        assertEquals(emptyList<String>(), suggestion.alternatives)
    }

    @Test
    fun `should handle null values in CachedResolution`() {
        val cached = CachedResolution(
            packageId = "Maven:test:pkg:1.0.0",
            declaredLicenseRaw = null,
            resolvedSpdxId = null,
            resolutionSource = "UNKNOWN",
            confidence = null,
            reasoning = null
        )

        val suggestion = cached.toLicenseSuggestion()

        assertEquals("UNKNOWN", suggestion.suggestedLicense)
        assertNull(suggestion.spdxId)
        assertEquals("MEDIUM", suggestion.confidence) // Default
        assertEquals("Resolved from cache", suggestion.reasoning) // Default
    }
}
