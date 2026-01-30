package com.ortoped.api.adapter

import com.ortoped.api.repository.OrtCacheRepository
import com.ortoped.core.scanner.ScanResultCache
import java.util.UUID

/**
 * Adapter that bridges OrtCacheRepository with the ScanResultCache interface
 * used by CachingAnalyzerWrapper in the core module.
 */
class ScanResultCacheAdapter(
    private val repository: OrtCacheRepository
) : ScanResultCache {

    override suspend fun getCachedScanResult(
        projectUrl: String,
        revision: String,
        configHash: String
    ): ByteArray? {
        return repository.getCachedScanResult(projectUrl, revision, configHash)
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
        return repository.cacheScanResult(
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
