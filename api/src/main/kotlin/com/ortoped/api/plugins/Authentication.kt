package com.ortoped.api.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureAuthentication() {
    val jwtSecret = System.getenv("JWT_SECRET") ?: "ortoped-development-secret-change-in-production"
    val jwtIssuer = System.getenv("JWT_ISSUER") ?: "ortoped-api"
    val jwtAudience = System.getenv("JWT_AUDIENCE") ?: "ortoped-users"
    val jwtRealm = "OrtoPed API"

    install(Authentication) {
        // JWT authentication for web dashboard
        jwt("jwt") {
            realm = jwtRealm
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("apiKeyId").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }

        // API Key authentication for programmatic access
        bearer("api-key") {
            realm = jwtRealm
            authenticate { tokenCredential ->
                // Token will be validated by AuthService
                // For now, we just extract the principal
                ApiKeyPrincipal(tokenCredential.token)
            }
        }
    }
}

/**
 * Principal for API key authentication
 */
data class ApiKeyPrincipal(val apiKey: String) : Principal

/**
 * Get API key ID from JWT claims
 */
fun JWTPrincipal.apiKeyId(): String? = payload.getClaim("apiKeyId").asString()
