package com.ortoped.api

import com.ortoped.api.adapter.LicenseResolutionCacheAdapter
import com.ortoped.api.plugins.*
import com.ortoped.api.routes.*
import com.ortoped.api.service.*
import com.ortoped.api.repository.*
import com.ortoped.api.jobs.CacheCleanupJob
import com.ortoped.core.ai.CachingLicenseResolver
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

private val logger = KotlinLogging.logger {}

fun main() {
    // Load .env file if it exists (for local development)
    loadEnvFile()
    
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"

    logger.info { "Starting OrtoPed API server on $host:$port" }
    
    // Log API key status for debugging
    val anthropicKey = System.getenv("ANTHROPIC_API_KEY")
    logger.info { "ANTHROPIC_API_KEY: ${if (anthropicKey != null) "configured (${anthropicKey.take(15)}...)" else "NOT SET"}" }

    embeddedServer(Netty, port = port, host = host, module = Application::module)
        .start(wait = true)
}

/**
 * Load environment variables from .env file if present.
 * This allows local development without requiring shell environment variables.
 * Searches in multiple locations to find the .env file.
 */
private fun loadEnvFile() {
    // Try multiple possible locations for .env file
    val cwd = System.getProperty("user.dir")
    val possiblePaths = listOf(
        java.io.File(cwd, ".env"),
        java.io.File(cwd, "../.env").normalize(),
        java.io.File(cwd, "api/.env").normalize(),
        java.io.File(cwd, "../../.env").normalize(),
        java.io.File("/Users/branislavvrtunski/IdeaProjects/ortoped/.env")
    )
    
    val envFile = possiblePaths.firstOrNull { it.exists() }
    
    if (envFile != null) {
        logger.info { "Found .env file at: ${envFile.absolutePath}" }
        logger.info { "Loading environment variables from .env file" }
        
        envFile.readLines()
            .filter { line -> line.isNotBlank() && !line.startsWith("#") }
            .forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    // Only set if not already present in environment
                    if (System.getenv(key) == null) {
                        System.setProperty(key, value)
                        logger.debug { "Loaded env: $key" }
                    }
                }
            }
        logger.info { ".env file loaded successfully" }
    } else {
        logger.warn { ".env file not found. Checked: ${possiblePaths.map { it.absolutePath }}" }
    }
}

fun Application.module() {
    // Configure plugins
    configureDatabase()
    configureSerialization()
    configureCORS()
    configureStatusPages()
    configureCallLogging()
    configureRateLimit()
    configureAuthentication()

    // Initialize repositories
    val projectRepository = ProjectRepository()
    val scanRepository = ScanRepository()
    val policyRepository = PolicyRepository()
    val apiKeyRepository = ApiKeyRepository()
    val curationRepository = CurationRepository()
    val curationSessionRepository = CurationSessionRepository()
    val curatedScanRepository = CuratedScanRepository()
    val curationTemplateRepository = CurationTemplateRepository()
    val ortCacheRepository = OrtCacheRepository()
    val settingsRepository = SettingsRepository()

    // Initialize graph service first (needed by other services)
    val licenseGraphService = LicenseGraphService()

    // Initialize services
    val projectService = ProjectService(projectRepository)
    val scanService = ScanService(scanRepository, projectRepository, ortCacheRepository)
    val policyService = PolicyService(policyRepository, licenseGraphService)
    val authService = AuthService(apiKeyRepository)
    val spdxService = SpdxService()
    // Create CachingLicenseResolver for on-demand AI resolution (Phase B)
    val licenseResolutionCache = LicenseResolutionCacheAdapter(ortCacheRepository)
    val cachingLicenseResolver = try {
        CachingLicenseResolver(cache = licenseResolutionCache)
    } catch (e: Exception) {
        logger.warn { "CachingLicenseResolver not available (ANTHROPIC_API_KEY may not be set): ${e.message}" }
        null
    }

    val curationService = CurationService(
        curationRepository = curationRepository,
        curationSessionRepository = curationSessionRepository,
        curatedScanRepository = curatedScanRepository,
        scanRepository = scanRepository,
        licenseGraphService = licenseGraphService,
        licenseResolver = cachingLicenseResolver,
        settingsRepository = settingsRepository
    )
    val templateService = TemplateService(
        templateRepository = curationTemplateRepository,
        curationRepository = curationRepository,
        curationSessionRepository = curationSessionRepository
    )
    val reportService = ReportService(
        scanRepository = scanRepository,
        projectRepository = projectRepository,
        policyRepository = policyRepository,
        curationRepository = curationRepository,
        curationSessionRepository = curationSessionRepository
    )

    // Configure routes
    configureRouting(
        projectService = projectService,
        scanService = scanService,
        policyService = policyService,
        authService = authService,
        scanRepository = scanRepository,
        spdxService = spdxService,
        curationService = curationService,
        templateService = templateService,
        reportService = reportService,
        licenseGraphService = licenseGraphService,
        ortCacheRepository = ortCacheRepository,
        settingsRepository = settingsRepository
    )

    // Start background jobs
    val cacheCleanupJob = CacheCleanupJob(ortCacheRepository, intervalHours = 6)
    cacheCleanupJob.start()

    // Register shutdown hook for cleanup job
    environment.monitor.subscribe(ApplicationStopped) {
        cacheCleanupJob.stop()
        logger.info { "Cache cleanup job stopped on application shutdown" }
    }

    logger.info { "OrtoPed API server started successfully" }
}
