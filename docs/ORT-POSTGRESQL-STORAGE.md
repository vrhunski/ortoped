# ORT PostgreSQL Storage Integration Plan

This document outlines the architecture and implementation plan for integrating PostgreSQL-based caching and storage for ORT (OSS Review Toolkit) scan results in Ortoped.

## Table of Contents

- [Current State Analysis](#current-state-analysis)
- [ORT Storage Architecture](#ort-storage-architecture)
- [Integration Options](#integration-options)
- [Recommended Implementation](#recommended-implementation-hybrid-approach)
- [Database Schema](#phase-1-database-schema-extensions)
- [ORT Configuration](#phase-2-ort-configuration-file)
- [Kotlin Implementation](#phase-3-kotlin-implementation)
- [API Integration](#phase-4-api-integration)
- [Cache Management](#phase-5-cache-management-api)
- [Cache Invalidation Strategy](#cache-invalidation-strategy)
- [Implementation Timeline](#implementation-timeline)

---

## Current State Analysis

| Aspect | Current Implementation |
|--------|----------------------|
| **ORT Usage** | Analyzer only (via `SimpleScannerWrapper`) |
| **Storage** | Custom JSON TEXT in `scans` table |
| **Database** | PostgreSQL 16 with Exposed ORM |
| **Caching** | In-memory only (SPDX, LicenseGraph) |

### Current Limitations

- No caching of ORT analyzer results between scans
- Repeated analysis of same packages across different projects
- No provenance tracking for scan results
- AI license resolutions not cached (repeated API calls)

---

## ORT Storage Architecture

ORT provides three storage types that can use PostgreSQL:

```
┌─────────────────────────────────────────────────────────────────┐
│                    ORT Storage Backends                          │
├─────────────────────┬─────────────────────┬─────────────────────┤
│  Scan Results       │  Provenance         │  Package Metadata   │
│  Storage            │  Storage            │  Storage            │
├─────────────────────┼─────────────────────┼─────────────────────┤
│ - License findings  │ - VCS info          │ - Package curations │
│ - Copyright info    │ - Source artifacts  │ - Concluded licenses│
│ - Scan summaries    │ - Download URLs     │ - Vulnerability data│
└─────────────────────┴─────────────────────┴─────────────────────┘
```

### ORT Native PostgreSQL Configuration

ORT supports PostgreSQL storage out of the box with the following configuration options:

```yaml
ort:
  scanner:
    storages:
      postgresStorage:
        connection:
          url: "jdbc:postgresql://example.com:5444/database"
          schema: "public"
          username: "username"
          password: "password"
          sslmode: "verify-full"
    storageReaders: ["postgresStorage"]
    storageWriters: ["postgresStorage"]
```

**Key Features:**
- Requires PostgreSQL 9.4+ with UTF8 encoding
- Automatically creates `scan_results` table using JSONB columns
- Supports connection pooling with HikariCP settings
- SSL/TLS configuration options

---

## Integration Options

### Option A: Native ORT PostgreSQL Storage

Use ORT's built-in PostgreSQL storage backend for scan result caching.

| Pros | Cons |
|------|------|
| Native ORT integration | Requires ORT config file setup |
| Automatic deduplication by package ID + scanner version | Separate from existing schema |
| Provenance-based caching (same repo revision = skip rescan) | Less control over data structure |
| Compatible with ORT ecosystem | |

### Option B: Custom PostgreSQL Cache Layer

Build a caching layer in ortoped that stores ORT results in the existing schema.

| Pros | Cons |
|------|------|
| Full control over schema | More development effort |
| Integrated with existing tables | Need to manage cache invalidation |
| Can cache at dependency level | Must implement deduplication |

### Option C: Hybrid Approach (Recommended)

Use ORT's PostgreSQL storage for raw scan data + custom tables for processed results.

| Pros | Cons |
|------|------|
| Best of both worlds | Slightly more complex setup |
| Native ORT caching for analyzer | Two storage layers to manage |
| Custom caching for AI/SPDX resolutions | |
| Full integration with ortoped schema | |

---

## Recommended Implementation: Hybrid Approach

The hybrid approach provides:

1. **ORT-native caching** for analyzer results (package metadata, declared licenses)
2. **Custom caching** for ortoped-specific data (AI resolutions, SPDX validations)
3. **Full project scan caching** with compression for fast retrieval

---

## Phase 1: Database Schema Extensions

Create migration `V5__ort_scan_cache.sql`:

```sql
-- Cache for ORT analyzer results (per-package)
CREATE TABLE ort_package_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Package identifier (Maven:group:artifact:version)
    package_id VARCHAR(500) NOT NULL,
    package_type VARCHAR(50) NOT NULL,  -- Maven, NPM, PyPI, etc.

    -- Version info for cache invalidation
    ort_version VARCHAR(50) NOT NULL,
    analyzer_version VARCHAR(50),

    -- Cached data
    declared_licenses JSONB,
    concluded_license VARCHAR(255),
    homepage_url VARCHAR(1000),
    vcs_url VARCHAR(1000),
    description TEXT,

    -- Provenance for deduplication
    source_artifact_hash VARCHAR(128),
    vcs_revision VARCHAR(128),

    -- Metadata
    created_at TIMESTAMP DEFAULT NOW(),
    last_accessed_at TIMESTAMP DEFAULT NOW(),
    access_count INTEGER DEFAULT 1,

    -- Unique constraint for deduplication
    UNIQUE(package_id, ort_version)
);

CREATE INDEX idx_ort_package_cache_type ON ort_package_cache(package_type);
CREATE INDEX idx_ort_package_cache_accessed ON ort_package_cache(last_accessed_at);

-- Cache for full scan results (per-project)
CREATE TABLE ort_scan_result_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Project identification
    project_url VARCHAR(1000),
    project_revision VARCHAR(128),
    project_path VARCHAR(500),

    -- Cache key components
    ort_version VARCHAR(50) NOT NULL,
    config_hash VARCHAR(64) NOT NULL,  -- Hash of analyzer config

    -- Full ORT result (compressed)
    ort_result_json BYTEA,  -- GZIP compressed
    result_size_bytes INTEGER,

    -- Statistics
    package_count INTEGER,
    issue_count INTEGER,

    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP,  -- Optional TTL

    UNIQUE(project_url, project_revision, config_hash, ort_version)
);

CREATE INDEX idx_ort_scan_cache_url ON ort_scan_result_cache(project_url);
CREATE INDEX idx_ort_scan_cache_expires ON ort_scan_result_cache(expires_at);

-- License resolution cache (AI + SPDX results)
CREATE TABLE license_resolution_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Input
    package_id VARCHAR(500) NOT NULL,
    declared_license_raw TEXT,

    -- Resolution result
    resolved_spdx_id VARCHAR(255),
    resolution_source VARCHAR(50),  -- AI, SPDX_MATCH, SCANNER, MANUAL
    confidence VARCHAR(20),  -- HIGH, MEDIUM, LOW
    reasoning TEXT,

    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW(),

    UNIQUE(package_id, declared_license_raw)
);

CREATE INDEX idx_license_cache_spdx ON license_resolution_cache(resolved_spdx_id);
```

### Entity Definitions

```kotlin
// api/src/main/kotlin/com/ortoped/api/model/CacheEntities.kt

object OrtPackageCacheTable : UUIDTable("ort_package_cache") {
    val packageId = varchar("package_id", 500)
    val packageType = varchar("package_type", 50)
    val ortVersion = varchar("ort_version", 50)
    val analyzerVersion = varchar("analyzer_version", 50).nullable()
    val declaredLicenses = text("declared_licenses").nullable()
    val concludedLicense = varchar("concluded_license", 255).nullable()
    val homepageUrl = varchar("homepage_url", 1000).nullable()
    val vcsUrl = varchar("vcs_url", 1000).nullable()
    val description = text("description").nullable()
    val sourceArtifactHash = varchar("source_artifact_hash", 128).nullable()
    val vcsRevision = varchar("vcs_revision", 128).nullable()
    val createdAt = timestamp("created_at").default(Instant.now())
    val lastAccessedAt = timestamp("last_accessed_at").default(Instant.now())
    val accessCount = integer("access_count").default(1)
}

object OrtScanResultCacheTable : UUIDTable("ort_scan_result_cache") {
    val projectUrl = varchar("project_url", 1000).nullable()
    val projectRevision = varchar("project_revision", 128).nullable()
    val projectPath = varchar("project_path", 500).nullable()
    val ortVersion = varchar("ort_version", 50)
    val configHash = varchar("config_hash", 64)
    val ortResultJson = binary("ort_result_json").nullable()
    val resultSizeBytes = integer("result_size_bytes").nullable()
    val packageCount = integer("package_count").nullable()
    val issueCount = integer("issue_count").nullable()
    val createdAt = timestamp("created_at").default(Instant.now())
    val expiresAt = timestamp("expires_at").nullable()
}

object LicenseResolutionCacheTable : UUIDTable("license_resolution_cache") {
    val packageId = varchar("package_id", 500)
    val declaredLicenseRaw = text("declared_license_raw").nullable()
    val resolvedSpdxId = varchar("resolved_spdx_id", 255).nullable()
    val resolutionSource = varchar("resolution_source", 50).nullable()
    val confidence = varchar("confidence", 20).nullable()
    val reasoning = text("reasoning").nullable()
    val createdAt = timestamp("created_at").default(Instant.now())
}
```

---

## Phase 2: ORT Configuration File

Create `config/ort.conf.yml`:

```yaml
ort:
  # Analyzer settings
  analyzer:
    allowDynamicVersions: true
    skipExcluded: true

  # Scanner settings (for future use with source code scanning)
  scanner:
    skipConcluded: true

    # PostgreSQL storage configuration
    storages:
      postgresStorage:
        connection:
          url: ${DATABASE_URL}
          schema: "ort_cache"
          username: ${DATABASE_USER}
          password: ${DATABASE_PASSWORD}
          sslmode: "disable"  # Set to "verify-full" in production

        # Connection pool settings (HikariCP)
        connectionTimeout: 30000      # 30 seconds
        idleTimeout: 600000           # 10 minutes
        maxLifetime: 1800000          # 30 minutes
        maximumPoolSize: 5
        minimumIdle: 1

    # Use PostgreSQL for both reading and writing
    storageReaders:
      - postgresStorage
    storageWriters:
      - postgresStorage

    # Archive storage for provenance data
    archive:
      storage:
        postgresStorage:
          connection:
            url: ${DATABASE_URL}
            schema: "ort_archive"
            username: ${DATABASE_USER}
            password: ${DATABASE_PASSWORD}
            sslmode: "disable"

  # Provenance storage
  provenanceStorage:
    postgresStorage:
      connection:
        url: ${DATABASE_URL}
        schema: "ort_provenance"
        username: ${DATABASE_USER}
        password: ${DATABASE_PASSWORD}
```

### Environment Variables

Add to your deployment configuration:

```bash
# Database connection (existing)
DATABASE_URL=jdbc:postgresql://localhost:5432/ortoped
DATABASE_USER=ortoped
DATABASE_PASSWORD=ortoped

# ORT configuration
ORT_CONFIG_DIR=/app/config
ORT_DATA_DIR=/app/data/ort
```

---

## Phase 3: Kotlin Implementation

### 3.1 Cache Repository

```kotlin
// api/src/main/kotlin/com/ortoped/api/repository/OrtCacheRepository.kt

package com.ortoped.api.repository

import com.ortoped.api.model.*
import com.ortoped.api.plugins.dbQuery
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import java.time.Instant
import java.util.UUID

class OrtCacheRepository {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Get cached package metadata by package ID and ORT version
     */
    suspend fun getCachedPackage(packageId: String, ortVersion: String): CachedPackage? {
        return dbQuery {
            OrtPackageCacheTable.selectAll()
                .where {
                    (OrtPackageCacheTable.packageId eq packageId) and
                    (OrtPackageCacheTable.ortVersion eq ortVersion)
                }
                .singleOrNull()
                ?.let { row ->
                    // Update access statistics
                    OrtPackageCacheTable.update({ OrtPackageCacheTable.id eq row[OrtPackageCacheTable.id] }) {
                        it[lastAccessedAt] = Instant.now()
                        it[accessCount] = accessCount + 1
                    }
                    toCachedPackage(row)
                }
        }
    }

    /**
     * Cache package metadata
     */
    suspend fun cachePackage(pkg: CachedPackage): UUID {
        return dbQuery {
            OrtPackageCacheTable.insertAndGetId {
                it[packageId] = pkg.packageId
                it[packageType] = pkg.packageType
                it[ortVersion] = pkg.ortVersion
                it[analyzerVersion] = pkg.analyzerVersion
                it[declaredLicenses] = pkg.declaredLicenses?.let { licenses ->
                    json.encodeToString(licenses)
                }
                it[concludedLicense] = pkg.concludedLicense
                it[homepageUrl] = pkg.homepageUrl
                it[vcsUrl] = pkg.vcsUrl
                it[description] = pkg.description
                it[sourceArtifactHash] = pkg.sourceArtifactHash
                it[vcsRevision] = pkg.vcsRevision
            }.value
        }
    }

    /**
     * Get cached scan result for a project
     */
    suspend fun getCachedScanResult(
        projectUrl: String,
        revision: String,
        configHash: String
    ): ByteArray? {
        return dbQuery {
            OrtScanResultCacheTable.selectAll()
                .where {
                    (OrtScanResultCacheTable.projectUrl eq projectUrl) and
                    (OrtScanResultCacheTable.projectRevision eq revision) and
                    (OrtScanResultCacheTable.configHash eq configHash) and
                    (OrtScanResultCacheTable.expiresAt.isNull() or
                     (OrtScanResultCacheTable.expiresAt greater Instant.now()))
                }
                .singleOrNull()
                ?.get(OrtScanResultCacheTable.ortResultJson)
        }
    }

    /**
     * Cache a full scan result (compressed)
     */
    suspend fun cacheScanResult(
        projectUrl: String,
        revision: String,
        configHash: String,
        ortVersion: String,
        ortResult: ByteArray,
        packageCount: Int,
        issueCount: Int = 0,
        ttlHours: Int = 24 * 7  // 1 week default
    ): UUID {
        return dbQuery {
            // Upsert - replace existing cache entry
            OrtScanResultCacheTable.deleteWhere {
                (OrtScanResultCacheTable.projectUrl eq projectUrl) and
                (OrtScanResultCacheTable.projectRevision eq revision) and
                (OrtScanResultCacheTable.configHash eq configHash)
            }

            OrtScanResultCacheTable.insertAndGetId {
                it[this.projectUrl] = projectUrl
                it[projectRevision] = revision
                it[this.configHash] = configHash
                it[this.ortVersion] = ortVersion
                it[ortResultJson] = ortResult
                it[resultSizeBytes] = ortResult.size
                it[this.packageCount] = packageCount
                it[this.issueCount] = issueCount
                it[expiresAt] = Instant.now().plusSeconds(ttlHours * 3600L)
            }.value
        }
    }

    /**
     * Clean up expired cache entries
     */
    suspend fun cleanExpiredCache(): Int {
        return dbQuery {
            OrtScanResultCacheTable.deleteWhere {
                expiresAt.isNotNull() and (expiresAt less Instant.now())
            }
        }
    }

    /**
     * Invalidate cache for a specific project
     */
    suspend fun invalidateProject(projectUrl: String): Int {
        return dbQuery {
            OrtScanResultCacheTable.deleteWhere {
                OrtScanResultCacheTable.projectUrl eq projectUrl
            }
        }
    }

    /**
     * Get cache statistics
     */
    suspend fun getStats(): CacheStats {
        return dbQuery {
            val packageCount = OrtPackageCacheTable.selectAll().count()
            val scanCount = OrtScanResultCacheTable.selectAll().count()
            val totalSize = OrtScanResultCacheTable
                .select(OrtScanResultCacheTable.resultSizeBytes.sum())
                .singleOrNull()
                ?.get(OrtScanResultCacheTable.resultSizeBytes.sum()) ?: 0
            val expiredCount = OrtScanResultCacheTable
                .selectAll()
                .where {
                    OrtScanResultCacheTable.expiresAt.isNotNull() and
                    (OrtScanResultCacheTable.expiresAt less Instant.now())
                }
                .count()

            CacheStats(
                cachedPackages = packageCount,
                cachedScans = scanCount,
                totalSizeBytes = totalSize.toLong(),
                expiredEntries = expiredCount
            )
        }
    }

    /**
     * Get package distribution by type
     */
    suspend fun getPackageTypeDistribution(): Map<String, Long> {
        return dbQuery {
            OrtPackageCacheTable
                .select(OrtPackageCacheTable.packageType, OrtPackageCacheTable.id.count())
                .groupBy(OrtPackageCacheTable.packageType)
                .associate { row ->
                    row[OrtPackageCacheTable.packageType] to row[OrtPackageCacheTable.id.count()]
                }
        }
    }

    private fun toCachedPackage(row: ResultRow): CachedPackage {
        return CachedPackage(
            id = row[OrtPackageCacheTable.id].value,
            packageId = row[OrtPackageCacheTable.packageId],
            packageType = row[OrtPackageCacheTable.packageType],
            ortVersion = row[OrtPackageCacheTable.ortVersion],
            analyzerVersion = row[OrtPackageCacheTable.analyzerVersion],
            declaredLicenses = row[OrtPackageCacheTable.declaredLicenses]?.let {
                json.decodeFromString<List<String>>(it)
            },
            concludedLicense = row[OrtPackageCacheTable.concludedLicense],
            homepageUrl = row[OrtPackageCacheTable.homepageUrl],
            vcsUrl = row[OrtPackageCacheTable.vcsUrl],
            description = row[OrtPackageCacheTable.description],
            sourceArtifactHash = row[OrtPackageCacheTable.sourceArtifactHash],
            vcsRevision = row[OrtPackageCacheTable.vcsRevision],
            createdAt = row[OrtPackageCacheTable.createdAt],
            lastAccessedAt = row[OrtPackageCacheTable.lastAccessedAt],
            accessCount = row[OrtPackageCacheTable.accessCount]
        )
    }
}

// Data classes
data class CachedPackage(
    val id: UUID? = null,
    val packageId: String,
    val packageType: String,
    val ortVersion: String,
    val analyzerVersion: String? = null,
    val declaredLicenses: List<String>? = null,
    val concludedLicense: String? = null,
    val homepageUrl: String? = null,
    val vcsUrl: String? = null,
    val description: String? = null,
    val sourceArtifactHash: String? = null,
    val vcsRevision: String? = null,
    val createdAt: Instant = Instant.now(),
    val lastAccessedAt: Instant = Instant.now(),
    val accessCount: Int = 1
)

data class CacheStats(
    val cachedPackages: Long,
    val cachedScans: Long,
    val totalSizeBytes: Long,
    val expiredEntries: Long
)
```

### 3.2 Enhanced Scanner Wrapper with Caching

```kotlin
// core/src/main/kotlin/com/ortoped/core/scanner/CachingAnalyzerWrapper.kt

package com.ortoped.core.scanner

import com.ortoped.api.repository.OrtCacheRepository
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

data class ScanConfiguration(
    val allowDynamicVersions: Boolean = true,
    val skipExcluded: Boolean = true,
    val disabledPackageManagers: List<String> = emptyList(),
    val enableSourceScan: Boolean = false,
    val enableAi: Boolean = false,
    val enableSpdx: Boolean = false,
    val useCache: Boolean = true,
    val cacheTtlHours: Int = 168  // 1 week
)

class CachingAnalyzerWrapper(
    private val cacheRepository: OrtCacheRepository,
    private val delegate: SimpleScannerWrapper = SimpleScannerWrapper()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    private val compressionLevel = 6

    /**
     * Scan a project with caching support
     */
    suspend fun scanProject(
        projectDir: File,
        projectUrl: String? = null,
        revision: String? = null,
        config: ScanConfiguration
    ): ScanResult {

        // Generate cache key from configuration
        val configHash = generateConfigHash(config)
        val effectiveRevision = revision ?: detectGitRevision(projectDir)
        val ortVersion = getOrtVersion()

        // Check cache first (if enabled and we have project identification)
        if (config.useCache && projectUrl != null && effectiveRevision != null) {
            try {
                val cached = cacheRepository.getCachedScanResult(
                    projectUrl, effectiveRevision, configHash
                )
                if (cached != null) {
                    logger.info { "Cache HIT for $projectUrl@$effectiveRevision" }
                    return decompressAndDeserialize(cached)
                }
                logger.info { "Cache MISS for $projectUrl@$effectiveRevision" }
            } catch (e: Exception) {
                logger.warn { "Cache lookup failed: ${e.message}" }
            }
        }

        // Perform actual scan using delegate
        val result = delegate.scanProject(
            projectDir = projectDir,
            demoMode = false,
            enableSourceScan = config.enableSourceScan,
            disabledPackageManagers = config.disabledPackageManagers,
            allowDynamicVersions = config.allowDynamicVersions,
            skipExcluded = config.skipExcluded
        )

        // Store in cache (if enabled)
        if (config.useCache && projectUrl != null && effectiveRevision != null) {
            try {
                val compressed = compressAndSerialize(result)
                cacheRepository.cacheScanResult(
                    projectUrl = projectUrl,
                    revision = effectiveRevision,
                    configHash = configHash,
                    ortVersion = ortVersion,
                    ortResult = compressed,
                    packageCount = result.dependencies.size,
                    issueCount = result.warnings?.size ?: 0,
                    ttlHours = config.cacheTtlHours
                )
                logger.info {
                    "Cached scan result: ${result.dependencies.size} packages, " +
                    "${compressed.size} bytes compressed"
                }
            } catch (e: Exception) {
                logger.warn { "Failed to cache scan result: ${e.message}" }
            }
        }

        return result
    }

    /**
     * Generate a hash of the scan configuration for cache keying
     */
    private fun generateConfigHash(config: ScanConfiguration): String {
        val configString = buildString {
            append("v2:")  // Version prefix for cache invalidation
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
     * Compress and serialize scan result to bytes
     */
    private fun compressAndSerialize(result: ScanResult): ByteArray {
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
    private fun decompressAndDeserialize(compressed: ByteArray): ScanResult {
        val jsonString = GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
            gzip.bufferedReader(Charsets.UTF_8).readText()
        }
        return json.decodeFromString(jsonString)
    }

    /**
     * Detect git revision of project directory
     */
    private fun detectGitRevision(projectDir: File): String? {
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
    private fun getOrtVersion(): String {
        return try {
            org.ossreviewtoolkit.model.OrtResult::class.java
                .`package`.implementationVersion ?: "unknown"
        } catch (e: Exception) {
            "76.0.0"  // Fallback to known version
        }
    }
}
```

### 3.3 License Resolution Cache

```kotlin
// core/src/main/kotlin/com/ortoped/core/cache/LicenseResolutionCache.kt

package com.ortoped.core.cache

import com.ortoped.api.repository.LicenseResolutionCacheRepository
import com.ortoped.core.model.LicenseResolution
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

data class CachedResolution(
    val resolution: LicenseResolution,
    val cachedAt: Instant
)

class LicenseResolutionCache(
    private val repository: LicenseResolutionCacheRepository,
    private val memoryCacheTtlMinutes: Int = 60
) {

    private val memoryCache = ConcurrentHashMap<String, CachedResolution>()

    /**
     * Get cached resolution or resolve and cache
     */
    suspend fun getOrResolve(
        packageId: String,
        declaredLicense: String?,
        resolver: suspend () -> LicenseResolution
    ): LicenseResolution {

        val cacheKey = generateCacheKey(packageId, declaredLicense)

        // L1: Memory cache (fast path)
        memoryCache[cacheKey]?.let { cached ->
            if (isMemoryCacheValid(cached)) {
                logger.debug { "Memory cache HIT for $packageId" }
                return cached.resolution
            }
            memoryCache.remove(cacheKey)
        }

        // L2: Database cache
        try {
            repository.findResolution(packageId, declaredLicense)?.let { cached ->
                logger.debug { "Database cache HIT for $packageId" }
                memoryCache[cacheKey] = CachedResolution(cached, Instant.now())
                return cached
            }
        } catch (e: Exception) {
            logger.warn { "Database cache lookup failed: ${e.message}" }
        }

        // Cache miss - resolve
        logger.debug { "Cache MISS for $packageId - resolving" }
        val resolution = resolver()

        // Store in caches
        try {
            repository.saveResolution(packageId, declaredLicense, resolution)
        } catch (e: Exception) {
            logger.warn { "Failed to save resolution to database: ${e.message}" }
        }

        memoryCache[cacheKey] = CachedResolution(resolution, Instant.now())

        return resolution
    }

    /**
     * Invalidate cache for a specific package
     */
    fun invalidatePackage(packageId: String) {
        val keysToRemove = memoryCache.keys.filter { it.startsWith("$packageId:") }
        keysToRemove.forEach { memoryCache.remove(it) }
        logger.debug { "Invalidated ${keysToRemove.size} memory cache entries for $packageId" }
    }

    /**
     * Clear all memory cache entries
     */
    fun clearMemoryCache() {
        val size = memoryCache.size
        memoryCache.clear()
        logger.info { "Cleared $size memory cache entries" }
    }

    /**
     * Get memory cache statistics
     */
    fun getMemoryCacheStats(): Map<String, Any> {
        return mapOf(
            "entries" to memoryCache.size,
            "validEntries" to memoryCache.values.count { isMemoryCacheValid(it) }
        )
    }

    private fun generateCacheKey(packageId: String, declaredLicense: String?): String {
        return "$packageId:${declaredLicense?.hashCode() ?: 0}"
    }

    private fun isMemoryCacheValid(cached: CachedResolution): Boolean {
        val ttl = memoryCacheTtlMinutes * 60L
        return cached.cachedAt.plusSeconds(ttl).isAfter(Instant.now())
    }
}
```

---

## Phase 4: API Integration

### Update ScanService

```kotlin
// api/src/main/kotlin/com/ortoped/api/service/ScanService.kt

// Add to existing ScanService class:

class ScanService(
    private val scanRepository: ScanRepository,
    private val ortCacheRepository: OrtCacheRepository,
    private val projectRepository: ProjectRepository,
    private val scanOrchestrator: ScanOrchestrator
) {
    private val cachingAnalyzer = CachingAnalyzerWrapper(ortCacheRepository)

    suspend fun triggerScan(request: TriggerScanRequest): UUID {
        // ... existing validation code ...

        val config = ScanConfiguration(
            allowDynamicVersions = request.allowDynamicVersions ?: true,
            skipExcluded = request.skipExcluded ?: true,
            disabledPackageManagers = request.disabledPackageManagers ?: emptyList(),
            enableSourceScan = request.enableSourceScan ?: false,
            enableAi = request.enableAi ?: false,
            enableSpdx = request.enableSpdx ?: false,
            useCache = request.useCache ?: true,
            cacheTtlHours = request.cacheTtlHours ?: 168
        )

        // Use caching analyzer for the base scan
        val baseResult = cachingAnalyzer.scanProject(
            projectDir = projectDir,
            projectUrl = request.repositoryUrl,
            revision = request.branch,
            config = config
        )

        // Continue with AI/SPDX enhancement as before...
    }
}
```

### Update Request Model

```kotlin
// Add to TriggerScanRequest:

@Serializable
data class TriggerScanRequest(
    // ... existing fields ...

    val useCache: Boolean? = true,
    val cacheTtlHours: Int? = 168,  // 1 week default
    val forceRescan: Boolean? = false  // Bypass cache
)
```

---

## Phase 5: Cache Management API

```kotlin
// api/src/main/kotlin/com/ortoped/api/routes/CacheRoutes.kt

package com.ortoped.api.routes

import com.ortoped.api.repository.OrtCacheRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class CacheStatsResponse(
    val cachedPackages: Long,
    val cachedScans: Long,
    val totalSizeBytes: Long,
    val totalSizeMB: Double,
    val expiredEntries: Long
)

@Serializable
data class CleanupResponse(
    val deletedEntries: Int,
    val message: String
)

@Serializable
data class PackageDistributionResponse(
    val distribution: Map<String, Long>,
    val total: Long
)

fun Route.cacheRoutes(cacheRepository: OrtCacheRepository) {
    route("/cache") {

        // Get cache statistics
        get("/stats") {
            val stats = cacheRepository.getStats()
            call.respond(CacheStatsResponse(
                cachedPackages = stats.cachedPackages,
                cachedScans = stats.cachedScans,
                totalSizeBytes = stats.totalSizeBytes,
                totalSizeMB = stats.totalSizeBytes / (1024.0 * 1024.0),
                expiredEntries = stats.expiredEntries
            ))
        }

        // Clean up expired cache entries
        post("/cleanup") {
            val deleted = cacheRepository.cleanExpiredCache()
            call.respond(CleanupResponse(
                deletedEntries = deleted,
                message = "Cleaned up $deleted expired cache entries"
            ))
        }

        // Invalidate cache for a specific project
        delete("/project") {
            val url = call.request.queryParameters["url"]
            if (url.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "URL parameter required"))
                return@delete
            }

            val deleted = cacheRepository.invalidateProject(url)
            call.respond(mapOf(
                "deletedEntries" to deleted,
                "projectUrl" to url
            ))
        }

        // Get cached packages count by type
        get("/packages/distribution") {
            val distribution = cacheRepository.getPackageTypeDistribution()
            call.respond(PackageDistributionResponse(
                distribution = distribution,
                total = distribution.values.sum()
            ))
        }

        // Get cache entries for a specific project
        get("/project") {
            val url = call.request.queryParameters["url"]
            if (url.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "URL parameter required"))
                return@get
            }

            val entries = cacheRepository.getProjectCacheEntries(url)
            call.respond(entries)
        }
    }
}
```

### Register Routes

```kotlin
// In Application.kt or Routes.kt:

fun Application.configureRouting() {
    routing {
        // ... existing routes ...

        authenticate("api-key") {
            cacheRoutes(ortCacheRepository)
        }
    }
}
```

---

## Cache Invalidation Strategy

```
┌─────────────────────────────────────────────────────────┐
│                 Cache Invalidation Triggers              │
├─────────────────────┬───────────────────────────────────┤
│ Trigger             │ Action                            │
├─────────────────────┼───────────────────────────────────┤
│ ORT version upgrade │ Automatic (version in cache key) │
│ Config change       │ Automatic (hash mismatch)        │
│ TTL expiration      │ Background cleanup job           │
│ Manual invalidation │ API endpoint / Admin UI          │
│ Git revision change │ Automatic (different cache key)  │
│ Force rescan flag   │ Bypass cache, update entry       │
└─────────────────────┴───────────────────────────────────┘
```

### Scheduled Cleanup Job

```kotlin
// api/src/main/kotlin/com/ortoped/api/jobs/CacheCleanupJob.kt

package com.ortoped.api.jobs

import com.ortoped.api.repository.OrtCacheRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.time.Duration

private val logger = KotlinLogging.logger {}

class CacheCleanupJob(
    private val cacheRepository: OrtCacheRepository,
    private val intervalHours: Int = 6
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch {
            while (isActive) {
                try {
                    val deleted = cacheRepository.cleanExpiredCache()
                    if (deleted > 0) {
                        logger.info { "Cache cleanup: removed $deleted expired entries" }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Cache cleanup failed" }
                }

                delay(Duration.ofHours(intervalHours.toLong()).toMillis())
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}
```

---

## Implementation Timeline

| Phase | Description | Estimated Effort |
|-------|-------------|------------------|
| **Phase 1** | Database schema migration | 1 day |
| **Phase 2** | ORT config file setup | 0.5 day |
| **Phase 3** | Cache repositories & wrapper | 2-3 days |
| **Phase 4** | Service integration | 1 day |
| **Phase 5** | Cache management API | 0.5 day |
| **Testing** | Integration tests | 1-2 days |
| **Total** | | **6-8 days** |

---

## Benefits Summary

| Benefit | Description |
|---------|-------------|
| **Faster rescans** | Cached results for unchanged projects (instant retrieval) |
| **Reduced API calls** | License resolutions cached (fewer Claude API calls) |
| **Lower resource usage** | Skip re-analyzing same packages across projects |
| **Provenance tracking** | Know which git revision was scanned |
| **Flexible TTL** | Configure cache duration per scan |
| **Statistics** | Track cache hit rates and storage usage |
| **Cost savings** | Reduced compute and API costs |

---

## Future Enhancements

1. **Distributed caching** - Redis integration for multi-instance deployments
2. **Cache warming** - Pre-populate cache with common packages
3. **Partial cache hits** - Reuse cached packages in new scans
4. **Cache export/import** - Share cache between environments
5. **Analytics dashboard** - Visualize cache performance metrics

---

## References

- [ORT Scanner Documentation](https://oss-review-toolkit.org/ort/docs/tools/scanner)
- [ORT Configuration Reference](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/resources/reference.yml)
- [PostgreSQL JSONB Documentation](https://www.postgresql.org/docs/current/datatype-json.html)
