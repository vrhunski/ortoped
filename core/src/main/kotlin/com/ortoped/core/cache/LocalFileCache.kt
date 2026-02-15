package com.ortoped.core.cache

import com.ortoped.core.model.ScanResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * File-system cache for scan results keyed by lockfile hash.
 * Stores results as JSON files in the `.ortoped/cache/` directory.
 */
class LocalFileCache(
    private val cacheDir: File
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    init {
        cacheDir.mkdirs()
    }

    /**
     * Check if a cached scan exists for the given lockfile hash.
     */
    fun hasCachedScan(lockfileHash: String): Boolean {
        return cacheFile(lockfileHash).exists()
    }

    /**
     * Load a cached scan result. Returns null if not cached or corrupted.
     */
    fun loadCachedScan(lockfileHash: String): ScanResult? {
        val file = cacheFile(lockfileHash)
        if (!file.exists()) return null

        return try {
            val content = file.readText()
            json.decodeFromString(ScanResult.serializer(), content).also {
                logger.info { "Loaded cached scan for hash $lockfileHash (${it.dependencies.size} deps)" }
            }
        } catch (e: Exception) {
            logger.warn { "Corrupted cache file for hash $lockfileHash, ignoring: ${e.message}" }
            null
        }
    }

    /**
     * Cache a scan result for the given lockfile hash.
     */
    fun cacheScan(lockfileHash: String, scanResult: ScanResult) {
        try {
            val file = cacheFile(lockfileHash)
            file.writeText(json.encodeToString(ScanResult.serializer(), scanResult))
            logger.info { "Cached scan result for hash $lockfileHash" }
        } catch (e: Exception) {
            logger.warn { "Failed to cache scan result: ${e.message}" }
        }
    }

    /**
     * Clear all cached scan results.
     */
    fun clearCache() {
        cacheDir.listFiles()?.filter { it.extension == "json" }?.forEach { it.delete() }
        logger.info { "Cache cleared" }
    }

    private fun cacheFile(lockfileHash: String): File {
        return File(cacheDir, "$lockfileHash.json")
    }

    companion object {
        /**
         * Create a LocalFileCache rooted in the project's `.ortoped/cache/` directory.
         */
        fun fromProject(projectDir: File): LocalFileCache {
            return LocalFileCache(File(projectDir, ".ortoped/cache"))
        }
    }
}
