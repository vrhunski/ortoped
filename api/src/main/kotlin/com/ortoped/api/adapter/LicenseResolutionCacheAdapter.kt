package com.ortoped.api.adapter

import com.ortoped.api.repository.CachedLicenseResolution
import com.ortoped.api.repository.OrtCacheRepository
import com.ortoped.core.ai.CachedResolution
import com.ortoped.core.ai.LicenseResolutionCache
import com.ortoped.core.model.LicenseSuggestion

/**
 * Adapter that bridges OrtCacheRepository with the LicenseResolutionCache interface
 * used by CachingLicenseResolver in the core module.
 */
class LicenseResolutionCacheAdapter(
    private val repository: OrtCacheRepository
) : LicenseResolutionCache {

    override fun getCachedResolution(packageId: String, declaredLicense: String?): CachedResolution? {
        val cached = repository.getCachedResolution(packageId, declaredLicense)
        return cached?.let {
            CachedResolution(
                packageId = it.packageId,
                declaredLicenseRaw = it.declaredLicenseRaw,
                resolvedSpdxId = it.resolvedSpdxId,
                resolutionSource = it.resolutionSource ?: "UNKNOWN",
                confidence = it.confidence,
                reasoning = it.reasoning
            )
        }
    }

    override fun cacheResolution(
        packageId: String,
        declaredLicense: String?,
        resolution: LicenseSuggestion,
        source: String
    ) {
        repository.cacheResolution(
            CachedLicenseResolution(
                packageId = packageId,
                declaredLicenseRaw = declaredLicense,
                resolvedSpdxId = resolution.spdxId ?: resolution.suggestedLicense,
                resolutionSource = source,
                confidence = resolution.confidence,
                reasoning = resolution.reasoning
            )
        )
    }
}
