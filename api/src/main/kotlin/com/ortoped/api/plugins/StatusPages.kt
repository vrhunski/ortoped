package com.ortoped.api.plugins

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

private val logger = KotlinLogging.logger {}

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    val status: Int
)

class NotFoundException(message: String) : Exception(message)
class BadRequestException(message: String) : Exception(message)
class UnauthorizedException(message: String) : Exception(message)
class ConflictException(message: String) : Exception(message)
class InternalException(message: String) : Exception(message)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<NotFoundException> { call, cause ->
            logger.debug { "Not found: ${cause.message}" }
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("not_found", cause.message ?: "Resource not found", 404)
            )
        }

        exception<BadRequestException> { call, cause ->
            logger.debug { "Bad request: ${cause.message}" }
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("bad_request", cause.message ?: "Invalid request", 400)
            )
        }

        exception<UnauthorizedException> { call, cause ->
            logger.debug { "Unauthorized: ${cause.message}" }
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("unauthorized", cause.message ?: "Unauthorized", 401)
            )
        }

        exception<ConflictException> { call, cause ->
            logger.debug { "Conflict: ${cause.message}" }
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse("conflict", cause.message ?: "Conflict", 409)
            )
        }

        exception<InternalException> { call, cause ->
            logger.error { "Internal error: ${cause.message}" }
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", cause.message ?: "Internal server error", 500)
            )
        }

        exception<IllegalArgumentException> { call, cause ->
            logger.debug { "Invalid argument: ${cause.message}" }
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("invalid_argument", cause.message ?: "Invalid argument", 400)
            )
        }

        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled exception" }
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", "An internal error occurred", 500)
            )
        }
    }
}
