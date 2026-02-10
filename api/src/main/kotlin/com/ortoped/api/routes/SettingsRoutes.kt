package com.ortoped.api.routes

import com.ortoped.api.repository.SettingsRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class SettingsResponse(
    val requireApproval: Boolean
)

@Serializable
data class SettingsUpdateRequest(
    val requireApproval: Boolean? = null
)

fun Route.settingsRoutes(settingsRepository: SettingsRepository) {

    route("/settings") {

        get {
            val requireApproval = settingsRepository.isApprovalRequired()
            call.respond(HttpStatusCode.OK, SettingsResponse(requireApproval = requireApproval))
        }

        put {
            val request = call.receive<SettingsUpdateRequest>()

            request.requireApproval?.let {
                settingsRepository.set("require_approval", it.toString())
            }

            val requireApproval = settingsRepository.isApprovalRequired()
            call.respond(HttpStatusCode.OK, SettingsResponse(requireApproval = requireApproval))
        }
    }
}
