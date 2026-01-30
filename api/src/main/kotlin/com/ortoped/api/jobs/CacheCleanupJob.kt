package com.ortoped.api.jobs

import com.ortoped.api.repository.OrtCacheRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.hours

private val logger = KotlinLogging.logger {}

/**
 * Background job that periodically cleans up expired cache entries.
 *
 * @param cacheRepository Repository for cache operations
 * @param intervalHours How often to run cleanup (default: 6 hours)
 */
class CacheCleanupJob(
    private val cacheRepository: OrtCacheRepository,
    private val intervalHours: Int = 6
) {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Start the cleanup job
     */
    fun start() {
        if (job?.isActive == true) {
            logger.warn { "Cache cleanup job is already running" }
            return
        }

        job = scope.launch {
            logger.info { "Starting cache cleanup job (interval: ${intervalHours}h)" }

            while (isActive) {
                try {
                    val deleted = cacheRepository.cleanExpiredCache()
                    if (deleted > 0) {
                        logger.info { "Cache cleanup: removed $deleted expired entries" }
                    } else {
                        logger.debug { "Cache cleanup: no expired entries found" }
                    }

                    // Log cache stats periodically
                    val stats = cacheRepository.getStats()
                    logger.info {
                        "Cache stats: ${stats.cachedPackages} packages, " +
                        "${stats.cachedScans} scans, " +
                        "${stats.cachedResolutions} resolutions, " +
                        "${String.format("%.2f", stats.totalSizeMB)} MB"
                    }
                } catch (e: CancellationException) {
                    throw e  // Let cancellation propagate
                } catch (e: Exception) {
                    logger.error(e) { "Cache cleanup failed" }
                }

                delay(intervalHours.hours)
            }
        }
    }

    /**
     * Stop the cleanup job
     */
    fun stop() {
        job?.cancel()
        job = null
        logger.info { "Cache cleanup job stopped" }
    }

    /**
     * Check if the job is running
     */
    fun isRunning(): Boolean = job?.isActive == true

    /**
     * Run cleanup immediately (outside of scheduled interval)
     */
    suspend fun runNow(): Int {
        return try {
            val deleted = cacheRepository.cleanExpiredCache()
            logger.info { "Manual cache cleanup: removed $deleted expired entries" }
            deleted
        } catch (e: Exception) {
            logger.error(e) { "Manual cache cleanup failed" }
            0
        }
    }
}
