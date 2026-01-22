package com.ortoped.api.routes

import com.ortoped.api.model.CreateApiKeyRequest
import com.ortoped.api.service.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class TokenRequest(
    val apiKey: String
)

@Serializable
data class TokenResponse(
    val token: String,
    val type: String = "Bearer",
    val expiresIn: Int = 86400 // 24 hours in seconds
)

fun Route.authRoutes(authService: AuthService) {
    route("/auth") {
        // API Key Management
        route("/api-keys") {
            // List API keys
            get {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

                val response = authService.listApiKeys(page, pageSize.coerceAtMost(100))
                call.respond(HttpStatusCode.OK, response)
            }

            // Create new API key
            rateLimit(RateLimitName("auth")) {
                post {
                    val request = call.receive<CreateApiKeyRequest>()
                    val response = authService.createApiKey(request)
                    call.respond(HttpStatusCode.Created, response)
                }
            }

            // Delete API key
            delete("/{id}") {
                val id = call.parameters["id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing API key ID")

                authService.deleteApiKey(id)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        // Token Exchange (API key -> JWT)
        rateLimit(RateLimitName("auth")) {
            post("/token") {
                val request = call.receive<TokenRequest>()
                val token = authService.authenticateWithApiKey(request.apiKey)

                call.respond(
                    HttpStatusCode.OK,
                    TokenResponse(token = token)
                )
            }
        }
    }
}
