package com.ortoped.api.adapter

import com.ortoped.api.repository.OrtCacheRepository
import com.ortoped.core.scanner.ScanResultCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Adapter that bridges OrtCacheRepository with the ScanResultCache interface
 * used by CachingAnalyzerWrapper in the core module.
 * 
 * Uses suspend functions with Dispatchers.IO to properly handle database operations
 * in a coroutine-friendly way.
 */
class ScanResultCacheAdapter(
    private val repository: OrtCacheRepository
) : ScanResultCache {

    override suspend fun getCachedScanResult(
        projectUrl: String,
        revision: String,
        configHash: String
    ): ByteArray? {
        return withContext(Dispatchers.IO) {
            repository.getCachedScanResult(projectUrl, revision, configHash)
        }
    }

    override suspend fun cacheScanResult(
        projectUrl: String,
        revision: String,
        configHash: String,
        ortVersion: String,
        ortResult: ByteArray,
        packageCount: Int,
        issueCount: Int,
        ttlHours: Int
    ): UUID {
        return withContext(Dispatchers.IO) {
            repository.cacheScanResult(
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
}
