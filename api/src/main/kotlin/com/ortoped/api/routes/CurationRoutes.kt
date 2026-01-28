package com.ortoped.api.routes

import com.ortoped.api.model.*
import com.ortoped.api.service.CurationService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Curation API routes
 */
fun Route.curationRoutes(curationService: CurationService) {

    route("/scans/{scanId}/curation") {

        // ====================================================================
        // Session Management
        // ====================================================================

        /**
         * Start a new curation session
         * POST /scans/{scanId}/curation/start
         */
        post("/start") {
            val scanId = call.parameters["scanId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))

            val request = try {
                call.receive<StartCurationRequest>()
            } catch (e: Exception) {
                StartCurationRequest() // Use defaults
            }

            // TODO: Get curator ID from authentication
            val curatorId = call.request.headers["X-Curator-Id"]

            val response = curationService.startCurationSession(scanId, request, curatorId)
            call.respond(HttpStatusCode.Created, response)
        }

        /**
         * Get curation session status
         * GET /scans/{scanId}/curation
         */
        get {
            val scanId = call.parameters["scanId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))

            val response = curationService.getSession(scanId)
            call.respond(HttpStatusCode.OK, response)
        }

        /**
         * Approve curation session (final sign-off)
         * POST /scans/{scanId}/curation/approve
         */
        post("/approve") {
            val scanId = call.parameters["scanId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))

            val request = try {
                call.receive<ApprovalRequest>()
            } catch (e: Exception) {
                ApprovalRequest()
            }

            // TODO: Get approver from authentication
            val approvedBy = call.request.headers["X-Curator-Id"] ?: "anonymous"

            val response = curationService.approveSession(scanId, request, approvedBy)
            call.respond(HttpStatusCode.OK, response)
        }

        // ====================================================================
        // Curation Items
        // ====================================================================

        /**
         * List curation items with filtering
         * GET /scans/{scanId}/curation/items?status=PENDING&priority=HIGH&hasAiSuggestion=true
         */
        get("/items") {
            val scanId = call.parameters["scanId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))

            val status = call.request.queryParameters["status"]
            val priority = call.request.queryParameters["priority"]
            val hasAiSuggestion = call.request.queryParameters["hasAiSuggestion"]?.toBooleanStrictOrNull()
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            val sortBy = call.request.queryParameters["sortBy"] ?: "priority"
            val sortOrder = call.request.queryParameters["sortOrder"] ?: "desc"

            val response = curationService.getCurationItems(
                scanId = scanId,
                status = status,
                priority = priority,
                hasAiSuggestion = hasAiSuggestion,
                page = page,
                pageSize = pageSize,
                sortBy = sortBy,
                sortOrder = sortOrder
            )
            call.respond(HttpStatusCode.OK, response)
        }

        /**
         * Get a single curation item
         * GET /scans/{scanId}/curation/items/{dependencyId}
         */
        get("/items/{dependencyId}") {
            val scanId = call.parameters["scanId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))
            val dependencyId = call.parameters["dependencyId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Dependency ID required"))

            val response = curationService.getCurationItem(scanId, dependencyId)
            call.respond(HttpStatusCode.OK, response)
        }

        /**
         * Submit curation decision for a single item
         * PUT /scans/{scanId}/curation/items/{dependencyId}
         */
        put("/items/{dependencyId}") {
            val scanId = call.parameters["scanId"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))
            val dependencyId = call.parameters["dependencyId"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Dependency ID required"))

            val request = call.receive<CurationDecisionRequest>()
            val curatorId = call.request.headers["X-Curator-Id"]

            val response = curationService.submitDecision(scanId, dependencyId, request, curatorId)
            call.respond(HttpStatusCode.OK, response)
        }

        /**
         * Bulk curation decisions
         * POST /scans/{scanId}/curation/bulk
         */
        post("/bulk") {
            val scanId = call.parameters["scanId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))

            val request = call.receive<BulkCurationRequest>()

            if (request.decisions.isEmpty()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "At least one decision required")
                )
            }

            if (request.decisions.size > 100) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Maximum 100 decisions per request")
                )
            }

            val curatorId = call.request.headers["X-Curator-Id"]
            val response = curationService.bulkCurate(scanId, request, curatorId)
            call.respond(HttpStatusCode.OK, response)
        }

        // ====================================================================
        // Incremental Curation
        // ====================================================================

        /**
         * Get incremental changes (diff from previous curated scan)
         * GET /scans/{scanId}/curation/incremental
         */
        get("/incremental") {
            val scanId = call.parameters["scanId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))

            val response = curationService.getIncrementalChanges(scanId)
            call.respond(HttpStatusCode.OK, response)
        }

        /**
         * Apply previous curation decisions to unchanged dependencies
         * POST /scans/{scanId}/curation/incremental/apply
         */
        post("/incremental/apply") {
            val scanId = call.parameters["scanId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))

            val request = try {
                call.receive<ApplyPreviousCurationsRequest>()
            } catch (e: Exception) {
                ApplyPreviousCurationsRequest()
            }

            val curatorId = call.request.headers["X-Curator-Id"]
            val response = curationService.applyPreviousCurations(scanId, request, curatorId)
            call.respond(HttpStatusCode.OK, response)
        }
    }
}
