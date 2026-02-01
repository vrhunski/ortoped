package com.ortoped.core.ai

import com.ortoped.core.model.LicenseSuggestion

/**
 * Interface for caching license resolution results.
 * This allows caching AI-resolved licenses to avoid repeated API calls.
 */
interface LicenseResolutionCache {
    /**
     * Get a cached license resolution for a package.
     *
     * @param packageId The package identifier (e.g., "Maven:group:artifact:version")
     * @param declaredLicense The original declared license string (can be null)
     * @return Cached resolution if found, null otherwise
     */
    suspend fun getCachedResolution(packageId: String, declaredLicense: String?): CachedResolution?

    /**
     * Cache a license resolution result.
     *
     * @param packageId The package identifier
     * @param declaredLicense The original declared license string
     * @param resolution The resolved license suggestion
     * @param source The resolution source (e.g., "AI", "SPDX_MATCH")
     */
    suspend fun cacheResolution(
        packageId: String,
        declaredLicense: String?,
        resolution: LicenseSuggestion,
        source: String = "AI"
    )
}

/**
 * Data class representing a cached license resolution
 */
data class CachedResolution(
    val packageId: String,
    val declaredLicenseRaw: String?,
    val resolvedSpdxId: String?,
    val resolutionSource: String,
    val confidence: String?,
    val reasoning: String?
) {
    /**
     * Convert to LicenseSuggestion for use in scan results
     */
    fun toLicenseSuggestion(): LicenseSuggestion {
        return LicenseSuggestion(
            suggestedLicense = resolvedSpdxId ?: "UNKNOWN",
            confidence = confidence ?: "MEDIUM",
            reasoning = reasoning ?: "Resolved from cache",
            spdxId = resolvedSpdxId,
            alternatives = emptyList()
        )
    }
}
