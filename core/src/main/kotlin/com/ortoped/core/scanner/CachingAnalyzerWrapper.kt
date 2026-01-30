package com.ortoped.core.scanner

import com.ortoped.core.model.ScanResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private val logger = KotlinLogging.logger {}

/**
 * Configuration for scan operations
 */
data class ScanConfiguration(
    val allowDynamicVersions: Boolean = true,
    val skipExcluded: Boolean = true,
    val disabledPackageManagers: List<String> = emptyList(),
    val enableSourceScan: Boolean = false,
    val enableAi: Boolean = false,
    val enableSpdx: Boolean = false,
    val useCache: Boolean = true,
    val cacheTtlHours: Int = 168,  // 1 week default
    val forceRescan: Boolean = false
)

/**
 * Interface for scan result cache operations
 */
interface ScanResultCache {
    suspend fun getCachedScanResult(projectUrl: String, revision: String, configHash: String): ByteArray?
    suspend fun cacheScanResult(
        projectUrl: String,
        revision: String,
        configHash: String,
        ortVersion: String,
        ortResult: ByteArray,
        packageCount: Int,
        issueCount: Int,
        ttlHours: Int
    ): java.util.UUID
}

/**
 * Wrapper around SimpleScannerWrapper that adds caching capabilities.
 *
 * Cache key is composed of:
 * - Project URL (repository URL)
 * - Git revision (commit hash)
 * - Configuration hash (analyzer settings)
 * - ORT version
 */
class CachingAnalyzerWrapper(
    private val cache: ScanResultCache? = null,
    private val delegate: SimpleScannerWrapper = SimpleScannerWrapper()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    /**
     * Scan a project with caching support.
     *
     * @param projectDir The project directory to scan
     * @param projectUrl Optional repository URL for cache keying
     * @param revision Optional git revision for cache keying
     * @param config Scan configuration
     * @return ScanResult from cache or fresh scan
     */
    suspend fun scanProject(
        projectDir: File,
        projectUrl: String? = null,
        revision: String? = null,
        config: ScanConfiguration = ScanConfiguration()
    ): ScanResult {

        val configHash = generateConfigHash(config)
        val effectiveRevision = revision ?: detectGitRevision(projectDir)
        val ortVersion = getOrtVersion()

        // Check cache first (if enabled and we have project identification)
        if (shouldUseCache(config, projectUrl, effectiveRevision)) {
            val url = projectUrl!!
            val rev = effectiveRevision!!
            try {
                val cached = cache?.getCachedScanResult(url, rev, configHash)
                if (cached != null) {
                    logger.info { "Cache HIT for $url@${rev.take(8)}" }
                    return decompressAndDeserialize(cached)
                }
                logger.info { "Cache MISS for $url@${rev.take(8)}" }
            } catch (e: Exception) {
                logger.warn { "Cache lookup failed: ${e.message}" }
            }
        }

        // Perform actual scan using delegate
        logger.info { "Performing fresh scan for ${projectDir.name}" }
        val result = delegate.scanProject(
            projectDir = projectDir,
            demoMode = false,
            enableSourceScan = config.enableSourceScan,
            disabledPackageManagers = config.disabledPackageManagers,
            allowDynamicVersions = config.allowDynamicVersions,
            skipExcluded = config.skipExcluded
        )

        // Store in cache (if enabled)
        if (shouldCacheResult(config, projectUrl, effectiveRevision)) {
            val url = projectUrl!!
            val rev = effectiveRevision!!
            try {
                val compressed = compressAndSerialize(result)
                cache?.cacheScanResult(
                    projectUrl = url,
                    revision = rev,
                    configHash = configHash,
                    ortVersion = ortVersion,
                    ortResult = compressed,
                    packageCount = result.dependencies.size,
                    issueCount = result.warnings?.size ?: 0,
                    ttlHours = config.cacheTtlHours
                )
                logger.info {
                    "Cached scan result: ${result.dependencies.size} packages, " +
                    "${formatBytes(compressed.size)} compressed"
                }
            } catch (e: Exception) {
                logger.warn { "Failed to cache scan result: ${e.message}" }
            }
        }

        return result
    }

    /**
     * Scan without caching (direct passthrough to delegate)
     */
    suspend fun scanProjectDirect(
        projectDir: File,
        demoMode: Boolean = false,
        enableSourceScan: Boolean = false,
        disabledPackageManagers: List<String> = emptyList(),
        allowDynamicVersions: Boolean = true,
        skipExcluded: Boolean = true
    ): ScanResult {
        return delegate.scanProject(
            projectDir = projectDir,
            demoMode = demoMode,
            enableSourceScan = enableSourceScan,
            disabledPackageManagers = disabledPackageManagers,
            allowDynamicVersions = allowDynamicVersions,
            skipExcluded = skipExcluded
        )
    }

    /**
     * Generate a hash of the scan configuration for cache keying.
     * This ensures that different configurations produce different cache keys.
     */
    fun generateConfigHash(config: ScanConfiguration): String {
        val configString = buildString {
            append("v2:")  // Version prefix for cache format changes
            append(config.allowDynamicVersions)
            append(":")
            append(config.skipExcluded)
            append(":")
            append(config.disabledPackageManagers.sorted().joinToString(","))
            append(":")
            append(config.enableSourceScan)
            append(":")
            append(getOrtVersion())
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(configString.toByteArray())
            .fold("") { str, byte -> str + "%02x".format(byte) }
            .take(16)
    }

    /**
     * Compress and serialize scan result to bytes using GZIP
     */
    fun compressAndSerialize(result: ScanResult): ByteArray {
        val jsonString = json.encodeToString(result)
        return ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gzip ->
                gzip.write(jsonString.toByteArray(Charsets.UTF_8))
            }
            baos.toByteArray()
        }
    }

    /**
     * Decompress and deserialize scan result from bytes
     */
    fun decompressAndDeserialize(compressed: ByteArray): ScanResult {
        val jsonString = GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
            gzip.bufferedReader(Charsets.UTF_8).readText()
        }
        return json.decodeFromString(jsonString)
    }

    /**
     * Detect git revision of project directory
     */
    fun detectGitRevision(projectDir: File): String? {
        return try {
            val process = ProcessBuilder("git", "rev-parse", "HEAD")
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                process.inputStream.bufferedReader().readLine()?.trim()
            } else {
                null
            }
        } catch (e: Exception) {
            logger.debug { "Could not detect git revision: ${e.message}" }
            null
        }
    }

    /**
     * Get current ORT version for cache keying
     */
    fun getOrtVersion(): String {
        return try {
            org.ossreviewtoolkit.model.OrtResult::class.java
                .`package`.implementationVersion ?: "76.0.0"
        } catch (e: Exception) {
            "76.0.0"  // Fallback to known version
        }
    }

    private fun shouldUseCache(
        config: ScanConfiguration,
        projectUrl: String?,
        revision: String?
    ): Boolean {
        return config.useCache &&
               !config.forceRescan &&
               cache != null &&
               projectUrl != null &&
               revision != null
    }

    private fun shouldCacheResult(
        config: ScanConfiguration,
        projectUrl: String?,
        revision: String?
    ): Boolean {
        return config.useCache &&
               cache != null &&
               projectUrl != null &&
               revision != null
    }

    private fun formatBytes(bytes: Int): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
