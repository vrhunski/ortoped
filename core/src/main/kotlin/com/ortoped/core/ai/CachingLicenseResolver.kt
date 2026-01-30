package com.ortoped.core.ai

import com.ortoped.core.model.LicenseSuggestion
import com.ortoped.core.model.UnresolvedLicense
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * License resolver with caching support.
 * Wraps the base LicenseResolver and adds caching to avoid repeated API calls.
 */
class CachingLicenseResolver(
    private val cache: LicenseResolutionCache? = null,
    private val delegate: LicenseResolver = LicenseResolver()
) {
    /**
     * Resolve a license with caching.
     * Checks cache first, falls back to AI resolution if not cached.
     *
     * @param unresolved The unresolved license to resolve
     * @return License suggestion from cache or AI, null if resolution fails
     */
    suspend fun resolveLicense(unresolved: UnresolvedLicense): LicenseSuggestion? {
        val packageId = unresolved.dependencyId
        val declaredLicense = unresolved.licenseText?.take(500) // Use truncated license text as key

        // Check cache first
        if (cache != null) {
            try {
                val cached = cache.getCachedResolution(packageId, declaredLicense)
                if (cached != null) {
                    logger.info { "Cache HIT for license resolution: ${unresolved.dependencyName}" }
                    return cached.toLicenseSuggestion()
                }
                logger.debug { "Cache MISS for license resolution: ${unresolved.dependencyName}" }
            } catch (e: Exception) {
                logger.warn { "Cache lookup failed for ${unresolved.dependencyName}: ${e.message}" }
                // Fall through to delegate
            }
        }

        // Call AI resolver
        val suggestion = delegate.resolveLicense(unresolved)

        // Cache the result if successful
        if (suggestion != null && cache != null) {
            try {
                cache.cacheResolution(
                    packageId = packageId,
                    declaredLicense = declaredLicense,
                    resolution = suggestion,
                    source = "AI"
                )
                logger.debug { "Cached AI resolution for: ${unresolved.dependencyName}" }
            } catch (e: Exception) {
                logger.warn { "Failed to cache license resolution: ${e.message}" }
            }
        }

        return suggestion
    }

    /**
     * Resolve license without caching (direct passthrough to AI)
     */
    suspend fun resolveLicenseDirect(unresolved: UnresolvedLicense): LicenseSuggestion? {
        return delegate.resolveLicense(unresolved)
    }

    /**
     * Get cache statistics (if cache is available)
     */
    fun getCacheStats(): Map<String, Any>? {
        // This would need to be implemented by the cache
        return null
    }
}
