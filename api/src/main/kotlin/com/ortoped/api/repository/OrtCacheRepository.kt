package com.ortoped.api.repository

import com.ortoped.api.model.LicenseResolutionCache
import com.ortoped.api.model.OrtPackageCache
import com.ortoped.api.model.OrtScanResultCache
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.time.Duration.Companion.hours

/**
 * Repository for ORT scan result caching
 */
class OrtCacheRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // ========================================================================
    // PACKAGE CACHE
    // ========================================================================

    /**
     * Get cached package metadata by package ID and ORT version
     */
    fun getCachedPackage(packageId: String, ortVersion: String): CachedPackage? = transaction {
        OrtPackageCache.selectAll()
            .where {
                (OrtPackageCache.packageId eq packageId) and
                (OrtPackageCache.ortVersion eq ortVersion)
            }
            .singleOrNull()
            ?.let { row ->
                // Update access statistics
                OrtPackageCache.update({ OrtPackageCache.id eq row[OrtPackageCache.id] }) {
                    it[lastAccessedAt] = Clock.System.now()
                    it[accessCount] = row[accessCount] + 1
                }
                toCachedPackage(row)
            }
    }

    /**
     * Get multiple cached packages by IDs
     */
    fun getCachedPackages(packageIds: List<String>, ortVersion: String): Map<String, CachedPackage> {
        if (packageIds.isEmpty()) return emptyMap()

        return transaction {
            OrtPackageCache.selectAll()
                .where {
                    (OrtPackageCache.packageId inList packageIds) and
                    (OrtPackageCache.ortVersion eq ortVersion)
                }
                .associate { row ->
                    row[OrtPackageCache.packageId] to toCachedPackage(row)
                }
        }
    }

    /**
     * Cache package metadata
     */
    fun cachePackage(pkg: CachedPackage): UUID = transaction {
        val now = Clock.System.now()

        // Upsert - delete existing and insert new
        OrtPackageCache.deleteWhere {
            (packageId eq pkg.packageId) and (ortVersion eq pkg.ortVersion)
        }

        OrtPackageCache.insertAndGetId {
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
            it[createdAt] = now
            it[lastAccessedAt] = now
            it[accessCount] = 1
        }.value
    }

    /**
     * Cache multiple packages in batch
     */
    fun cachePackages(packages: List<CachedPackage>): Int {
        if (packages.isEmpty()) return 0

        return transaction {
            val now = Clock.System.now()
            var count = 0
            packages.forEach { pkg ->
                OrtPackageCache.deleteWhere {
                    (packageId eq pkg.packageId) and (ortVersion eq pkg.ortVersion)
                }

                OrtPackageCache.insert {
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
                    it[createdAt] = now
                    it[lastAccessedAt] = now
                    it[accessCount] = 1
                }
                count++
            }
            count
        }
    }

    // ========================================================================
    // SCAN RESULT CACHE
    // ========================================================================

    /**
     * Get cached scan result for a project
     */
    fun getCachedScanResult(
        projectUrl: String,
        revision: String,
        configHash: String
    ): ByteArray? = transaction {
        val now = Clock.System.now()
        OrtScanResultCache.selectAll()
            .where {
                (OrtScanResultCache.projectUrl eq projectUrl) and
                (OrtScanResultCache.projectRevision eq revision) and
                (OrtScanResultCache.configHash eq configHash) and
                (OrtScanResultCache.expiresAt.isNull() or
                 (OrtScanResultCache.expiresAt greaterEq now))
            }
            .singleOrNull()
            ?.get(OrtScanResultCache.ortResultJson)
    }

    /**
     * Cache a full scan result (compressed)
     */
    fun cacheScanResult(
        projectUrl: String,
        revision: String,
        configHash: String,
        ortVersion: String,
        ortResult: ByteArray,
        packageCount: Int,
        issueCount: Int = 0,
        ttlHours: Int = 24 * 7  // 1 week default
    ): UUID = transaction {
        // Upsert - replace existing cache entry
        OrtScanResultCache.deleteWhere {
            (OrtScanResultCache.projectUrl eq projectUrl) and
            (OrtScanResultCache.projectRevision eq revision) and
            (OrtScanResultCache.configHash eq configHash)
        }

        val now = Clock.System.now()
        val expiresAt = now.plus(ttlHours.hours)

        OrtScanResultCache.insertAndGetId {
            it[this.projectUrl] = projectUrl
            it[projectRevision] = revision
            it[this.configHash] = configHash
            it[this.ortVersion] = ortVersion
            it[ortResultJson] = ortResult
            it[resultSizeBytes] = ortResult.size
            it[this.packageCount] = packageCount
            it[this.issueCount] = issueCount
            it[createdAt] = now
            it[this.expiresAt] = expiresAt
        }.value
    }

    /**
     * Get all cache entries for a project
     */
    fun getProjectCacheEntries(projectUrl: String): List<ScanCacheEntry> = transaction {
        OrtScanResultCache.selectAll()
            .where { OrtScanResultCache.projectUrl eq projectUrl }
            .orderBy(OrtScanResultCache.createdAt, SortOrder.DESC)
            .map { row ->
                ScanCacheEntry(
                    id = row[OrtScanResultCache.id].value.toString(),
                    projectUrl = row[OrtScanResultCache.projectUrl],
                    projectRevision = row[OrtScanResultCache.projectRevision],
                    ortVersion = row[OrtScanResultCache.ortVersion],
                    configHash = row[OrtScanResultCache.configHash],
                    resultSizeBytes = row[OrtScanResultCache.resultSizeBytes],
                    packageCount = row[OrtScanResultCache.packageCount],
                    createdAt = row[OrtScanResultCache.createdAt].toString(),
                    expiresAt = row[OrtScanResultCache.expiresAt]?.toString()
                )
            }
    }

    // ========================================================================
    // LICENSE RESOLUTION CACHE
    // ========================================================================

    /**
     * Get cached license resolution
     */
    fun getCachedResolution(packageId: String, declaredLicense: String?): CachedLicenseResolution? = transaction {
        val query = if (declaredLicense != null) {
            LicenseResolutionCache.selectAll()
                .where {
                    (LicenseResolutionCache.packageId eq packageId) and
                    (LicenseResolutionCache.declaredLicenseRaw eq declaredLicense)
                }
        } else {
            LicenseResolutionCache.selectAll()
                .where {
                    (LicenseResolutionCache.packageId eq packageId) and
                    (LicenseResolutionCache.declaredLicenseRaw.isNull())
                }
        }
        query.singleOrNull()?.let { toCachedResolution(it) }
    }

    /**
     * Cache a license resolution result
     */
    fun cacheResolution(resolution: CachedLicenseResolution): UUID = transaction {
        // Upsert - delete existing first
        if (resolution.declaredLicenseRaw != null) {
            LicenseResolutionCache.deleteWhere {
                (LicenseResolutionCache.packageId eq resolution.packageId) and
                (LicenseResolutionCache.declaredLicenseRaw eq resolution.declaredLicenseRaw)
            }
        } else {
            // Delete entries where declaredLicenseRaw is null
            LicenseResolutionCache.deleteWhere { LicenseResolutionCache.packageId eq resolution.packageId }
                .let { } // Only delete matching packageId, null check handled by unique constraint
        }

        LicenseResolutionCache.insertAndGetId {
            it[packageId] = resolution.packageId
            it[declaredLicenseRaw] = resolution.declaredLicenseRaw
            it[resolvedSpdxId] = resolution.resolvedSpdxId
            it[resolutionSource] = resolution.resolutionSource
            it[confidence] = resolution.confidence
            it[reasoning] = resolution.reasoning
            it[createdAt] = Clock.System.now()
        }.value
    }

    /**
     * Get multiple cached resolutions
     */
    fun getCachedResolutions(packageIds: List<String>): Map<String, CachedLicenseResolution> {
        if (packageIds.isEmpty()) return emptyMap()

        return transaction {
            LicenseResolutionCache.selectAll()
                .where { LicenseResolutionCache.packageId inList packageIds }
                .associate { row ->
                    row[LicenseResolutionCache.packageId] to toCachedResolution(row)
                }
        }
    }

    // ========================================================================
    // CACHE MANAGEMENT
    // ========================================================================

    /**
     * Clean up expired cache entries
     */
    fun cleanExpiredCache(): Int = transaction {
        val now = Clock.System.now()
        // Find and delete expired entries
        val expiredIds = OrtScanResultCache.selectAll()
            .where { OrtScanResultCache.expiresAt lessEq now }
            .map { it[OrtScanResultCache.id].value }

        var deletedCount = 0
        expiredIds.forEach { expiredId ->
            deletedCount += OrtScanResultCache.deleteWhere { OrtScanResultCache.id eq expiredId }
        }
        deletedCount
    }

    /**
     * Invalidate cache for a specific project
     */
    fun invalidateProject(projectUrl: String): Int = transaction {
        OrtScanResultCache.deleteWhere {
            OrtScanResultCache.projectUrl eq projectUrl
        }
    }

    /**
     * Invalidate package cache by package ID
     */
    fun invalidatePackage(packageId: String): Int = transaction {
        OrtPackageCache.deleteWhere {
            OrtPackageCache.packageId eq packageId
        } + LicenseResolutionCache.deleteWhere {
            LicenseResolutionCache.packageId eq packageId
        }
    }

    /**
     * Get cache statistics
     */
    fun getStats(): CacheStats = transaction {
        val packageCount = OrtPackageCache.selectAll().count()
        val scanCount = OrtScanResultCache.selectAll().count()
        val resolutionCount = LicenseResolutionCache.selectAll().count()

        val totalSize = OrtScanResultCache
            .select(OrtScanResultCache.resultSizeBytes.sum())
            .singleOrNull()
            ?.get(OrtScanResultCache.resultSizeBytes.sum()) ?: 0

        val now = Clock.System.now()
        val expiredCount = OrtScanResultCache
            .selectAll()
            .where { OrtScanResultCache.expiresAt lessEq now }
            .count()

        CacheStats(
            cachedPackages = packageCount,
            cachedScans = scanCount,
            cachedResolutions = resolutionCount,
            totalSizeBytes = totalSize.toLong(),
            expiredEntries = expiredCount
        )
    }

    /**
     * Get package distribution by type
     */
    fun getPackageTypeDistribution(): Map<String, Long> = transaction {
        OrtPackageCache
            .select(OrtPackageCache.packageType, OrtPackageCache.id.count())
            .groupBy(OrtPackageCache.packageType)
            .associate { row ->
                row[OrtPackageCache.packageType] to row[OrtPackageCache.id.count()]
            }
    }

    /**
     * Get resolution distribution by source
     */
    fun getResolutionSourceDistribution(): Map<String, Long> = transaction {
        LicenseResolutionCache
            .select(LicenseResolutionCache.resolutionSource, LicenseResolutionCache.id.count())
            .groupBy(LicenseResolutionCache.resolutionSource)
            .associate { row ->
                (row[LicenseResolutionCache.resolutionSource] ?: "UNKNOWN") to
                    row[LicenseResolutionCache.id.count()]
            }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private fun toCachedPackage(row: ResultRow): CachedPackage {
        return CachedPackage(
            id = row[OrtPackageCache.id].value.toString(),
            packageId = row[OrtPackageCache.packageId],
            packageType = row[OrtPackageCache.packageType],
            ortVersion = row[OrtPackageCache.ortVersion],
            analyzerVersion = row[OrtPackageCache.analyzerVersion],
            declaredLicenses = row[OrtPackageCache.declaredLicenses]?.let {
                json.decodeFromString<List<String>>(it)
            },
            concludedLicense = row[OrtPackageCache.concludedLicense],
            homepageUrl = row[OrtPackageCache.homepageUrl],
            vcsUrl = row[OrtPackageCache.vcsUrl],
            description = row[OrtPackageCache.description],
            sourceArtifactHash = row[OrtPackageCache.sourceArtifactHash],
            vcsRevision = row[OrtPackageCache.vcsRevision],
            createdAt = row[OrtPackageCache.createdAt].toString(),
            lastAccessedAt = row[OrtPackageCache.lastAccessedAt].toString(),
            accessCount = row[OrtPackageCache.accessCount]
        )
    }

    private fun toCachedResolution(row: ResultRow): CachedLicenseResolution {
        return CachedLicenseResolution(
            id = row[LicenseResolutionCache.id].value.toString(),
            packageId = row[LicenseResolutionCache.packageId],
            declaredLicenseRaw = row[LicenseResolutionCache.declaredLicenseRaw],
            resolvedSpdxId = row[LicenseResolutionCache.resolvedSpdxId],
            resolutionSource = row[LicenseResolutionCache.resolutionSource],
            confidence = row[LicenseResolutionCache.confidence],
            reasoning = row[LicenseResolutionCache.reasoning],
            createdAt = row[LicenseResolutionCache.createdAt].toString()
        )
    }
}

// ============================================================================
// DATA CLASSES
// ============================================================================

@Serializable
data class CachedPackage(
    val id: String? = null,
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
    val createdAt: String? = null,
    val lastAccessedAt: String? = null,
    val accessCount: Int = 1
)

@Serializable
data class CachedLicenseResolution(
    val id: String? = null,
    val packageId: String,
    val declaredLicenseRaw: String? = null,
    val resolvedSpdxId: String? = null,
    val resolutionSource: String? = null,
    val confidence: String? = null,
    val reasoning: String? = null,
    val createdAt: String? = null
)

@Serializable
data class ScanCacheEntry(
    val id: String,
    val projectUrl: String?,
    val projectRevision: String?,
    val ortVersion: String,
    val configHash: String,
    val resultSizeBytes: Int?,
    val packageCount: Int?,
    val createdAt: String,
    val expiresAt: String?
)

@Serializable
data class CacheStats(
    val cachedPackages: Long,
    val cachedScans: Long,
    val cachedResolutions: Long,
    val totalSizeBytes: Long,
    val expiredEntries: Long
) {
    val totalSizeMB: Double get() = totalSizeBytes / (1024.0 * 1024.0)
}
