package com.ortoped.api.adapter

import com.ortoped.api.repository.CachedLicenseResolution
import com.ortoped.api.repository.OrtCacheRepository
import com.ortoped.core.ai.CachedResolution
import com.ortoped.core.ai.LicenseResolutionCache
import com.ortoped.core.model.LicenseSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adapter that bridges OrtCacheRepository with the LicenseResolutionCache interface
 * used by CachingLicenseResolver in the core module.
 * 
 * Uses suspend functions to properly integrate with coroutine-based code.
 */
class LicenseResolutionCacheAdapter(
    private val repository: OrtCacheRepository
) : LicenseResolutionCache {

    override suspend fun getCachedResolution(packageId: String, declaredLicense: String?): CachedResolution? {
        return withContext(Dispatchers.IO) {
            val cached = repository.getCachedResolution(packageId, declaredLicense)
            cached?.let {
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
    }

    override suspend fun cacheResolution(
        packageId: String,
        declaredLicense: String?,
        resolution: LicenseSuggestion,
        source: String
    ) {
        withContext(Dispatchers.IO) {
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
}
