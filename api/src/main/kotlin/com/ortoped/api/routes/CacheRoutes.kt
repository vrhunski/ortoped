package com.ortoped.api.routes

import com.ortoped.api.repository.OrtCacheRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * Cache management API routes
 */
fun Route.cacheRoutes(cacheRepository: OrtCacheRepository) {
    route("/cache") {

        /**
         * GET /cache/stats
         * Get cache statistics
         */
        get("/stats") {
            val stats = cacheRepository.getStats()
            call.respond(CacheStatsResponse(
                cachedPackages = stats.cachedPackages,
                cachedScans = stats.cachedScans,
                cachedResolutions = stats.cachedResolutions,
                totalSizeBytes = stats.totalSizeBytes,
                totalSizeMB = stats.totalSizeMB,
                expiredEntries = stats.expiredEntries
            ))
        }

        /**
         * POST /cache/cleanup
         * Clean up expired cache entries
         */
        post("/cleanup") {
            val deleted = cacheRepository.cleanExpiredCache()
            call.respond(CleanupResponse(
                deletedEntries = deleted,
                message = if (deleted > 0) {
                    "Cleaned up $deleted expired cache entries"
                } else {
                    "No expired cache entries found"
                }
            ))
        }

        /**
         * DELETE /cache/project?url={projectUrl}
         * Invalidate cache for a specific project
         */
        delete("/project") {
            val url = call.request.queryParameters["url"]
            if (url.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    CacheErrorResponse("URL parameter required")
                )
                return@delete
            }

            val deleted = cacheRepository.invalidateProject(url)
            call.respond(InvalidateResponse(
                deletedEntries = deleted,
                projectUrl = url,
                message = if (deleted > 0) {
                    "Invalidated $deleted cache entries for project"
                } else {
                    "No cache entries found for project"
                }
            ))
        }

        /**
         * DELETE /cache/package?id={packageId}
         * Invalidate cache for a specific package
         */
        delete("/package") {
            val packageId = call.request.queryParameters["id"]
            if (packageId.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    CacheErrorResponse("Package ID parameter required")
                )
                return@delete
            }

            val deleted = cacheRepository.invalidatePackage(packageId)
            call.respond(InvalidateResponse(
                deletedEntries = deleted,
                packageId = packageId,
                message = if (deleted > 0) {
                    "Invalidated $deleted cache entries for package"
                } else {
                    "No cache entries found for package"
                }
            ))
        }

        /**
         * GET /cache/project?url={projectUrl}
         * Get cache entries for a specific project
         */
        get("/project") {
            val url = call.request.queryParameters["url"]
            if (url.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    CacheErrorResponse("URL parameter required")
                )
                return@get
            }

            val entries = cacheRepository.getProjectCacheEntries(url)
            call.respond(ProjectCacheResponse(
                projectUrl = url,
                entries = entries.map { entry ->
                    CacheEntryResponse(
                        id = entry.id,
                        revision = entry.projectRevision,
                        ortVersion = entry.ortVersion,
                        configHash = entry.configHash,
                        sizeBytes = entry.resultSizeBytes,
                        packageCount = entry.packageCount,
                        createdAt = entry.createdAt,
                        expiresAt = entry.expiresAt
                    )
                },
                totalEntries = entries.size
            ))
        }

        /**
         * GET /cache/packages/distribution
         * Get cached packages count by type
         */
        get("/packages/distribution") {
            val distribution = cacheRepository.getPackageTypeDistribution()
            call.respond(DistributionResponse(
                distribution = distribution,
                total = distribution.values.sum()
            ))
        }

        /**
         * GET /cache/resolutions/distribution
         * Get cached resolutions count by source
         */
        get("/resolutions/distribution") {
            val distribution = cacheRepository.getResolutionSourceDistribution()
            call.respond(DistributionResponse(
                distribution = distribution,
                total = distribution.values.sum()
            ))
        }
    }
}

// ============================================================================
// RESPONSE DTOs
// ============================================================================

@Serializable
data class CacheStatsResponse(
    val cachedPackages: Long,
    val cachedScans: Long,
    val cachedResolutions: Long,
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
data class InvalidateResponse(
    val deletedEntries: Int,
    val projectUrl: String? = null,
    val packageId: String? = null,
    val message: String
)

@Serializable
data class ProjectCacheResponse(
    val projectUrl: String,
    val entries: List<CacheEntryResponse>,
    val totalEntries: Int
)

@Serializable
data class CacheEntryResponse(
    val id: String,
    val revision: String?,
    val ortVersion: String,
    val configHash: String,
    val sizeBytes: Int?,
    val packageCount: Int?,
    val createdAt: String,
    val expiresAt: String?
)

@Serializable
data class DistributionResponse(
    val distribution: Map<String, Long>,
    val total: Long
)

@Serializable
data class CacheErrorResponse(
    val error: String
)
