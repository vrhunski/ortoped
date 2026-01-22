package com.ortoped.api

import com.ortoped.api.plugins.*
import com.ortoped.api.routes.*
import com.ortoped.api.service.*
import com.ortoped.api.repository.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

private val logger = KotlinLogging.logger {}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"

    logger.info { "Starting OrtoPed API server on $host:$port" }

    embeddedServer(Netty, port = port, host = host, module = Application::module)
        .start(wait = true)
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

    // Initialize services
    val projectService = ProjectService(projectRepository)
    val scanService = ScanService(scanRepository, projectRepository)
    val policyService = PolicyService(policyRepository)
    val authService = AuthService(apiKeyRepository)

    // Configure routes
    configureRouting(
        projectService = projectService,
        scanService = scanService,
        policyService = policyService,
        authService = authService,
        scanRepository = scanRepository
    )

    logger.info { "OrtoPed API server started successfully" }
}
