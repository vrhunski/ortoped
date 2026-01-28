package com.ortoped.api.routes

import com.ortoped.api.model.*
import com.ortoped.api.service.TemplateService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Curation Template API routes
 */
fun Route.templateRoutes(templateService: TemplateService) {

    route("/curation/templates") {

        // ====================================================================
        // Template CRUD
        // ====================================================================

        /**
         * List all templates
         * GET /curation/templates?activeOnly=true
         */
        get {
            val activeOnly = call.request.queryParameters["activeOnly"]?.toBooleanStrictOrNull() ?: true
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20

            // TODO: Get user ID from authentication
            val userId = call.request.headers["X-Curator-Id"]

            val response = templateService.listTemplates(activeOnly, userId, page, pageSize)
            call.respond(HttpStatusCode.OK, response)
        }

        /**
         * Get a template by ID
         * GET /curation/templates/{id}
         */
        get("/{id}") {
            val templateId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Template ID required"))

            val response = templateService.getTemplate(templateId)
            call.respond(HttpStatusCode.OK, response)
        }

        /**
         * Create a new template
         * POST /curation/templates
         */
        post {
            val request = call.receive<CreateTemplateRequest>()
            val userId = call.request.headers["X-Curator-Id"]

            val response = templateService.createTemplate(request, userId)
            call.respond(HttpStatusCode.Created, response)
        }

        /**
         * Update a template
         * PUT /curation/templates/{id}
         */
        put("/{id}") {
            val templateId = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Template ID required"))

            val request = call.receive<CreateTemplateRequest>()
            val userId = call.request.headers["X-Curator-Id"]

            val response = templateService.updateTemplate(templateId, request, userId)
            call.respond(HttpStatusCode.OK, response)
        }

        /**
         * Delete a template
         * DELETE /curation/templates/{id}
         */
        delete("/{id}") {
            val templateId = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Template ID required"))

            val userId = call.request.headers["X-Curator-Id"]

            templateService.deleteTemplate(templateId, userId)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    // ====================================================================
    // Template Application (nested under scan curation)
    // ====================================================================

    route("/scans/{scanId}/curation/templates") {

        /**
         * Apply a template to a curation session
         * POST /scans/{scanId}/curation/templates/apply
         */
        post("/apply") {
            val scanId = call.parameters["scanId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))

            val request = call.receive<ApplyTemplateRequest>()
            val userId = call.request.headers["X-Curator-Id"]

            val response = templateService.applyTemplate(scanId, request, userId)
            call.respond(HttpStatusCode.OK, response)
        }

        /**
         * Preview which items would match template conditions
         * POST /scans/{scanId}/curation/templates/preview
         */
        post("/preview") {
            val scanId = call.parameters["scanId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))

            val request = call.receive<PreviewConditionsRequest>()

            val matchedItems = templateService.previewConditions(scanId, request.conditions)
            call.respond(HttpStatusCode.OK, PreviewConditionsResponse(
                matchedCount = matchedItems.size,
                items = matchedItems.take(50) // Limit preview to 50 items
            ))
        }
    }
}

// Request/Response models for preview
@kotlinx.serialization.Serializable
data class PreviewConditionsRequest(
    val conditions: List<TemplateCondition>
)

@kotlinx.serialization.Serializable
data class PreviewConditionsResponse(
    val matchedCount: Int,
    val items: List<CurationItemResponse>
)
