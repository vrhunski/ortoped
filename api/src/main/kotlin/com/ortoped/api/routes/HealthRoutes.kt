package com.ortoped.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val timestamp: Long
)

fun Route.healthRoutes() {
    get("/health") {
        call.respond(
            HttpStatusCode.OK,
            HealthResponse(
                status = "healthy",
                version = "1.0.0-SNAPSHOT",
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
