package com.ortoped.api.routes

import com.ortoped.api.service.SpdxService
import com.ortoped.api.service.ValidateLicensesRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * License-related API routes for SPDX integration
 */
fun Route.licenseRoutes(spdxService: SpdxService) {

    route("/licenses") {

        /**
         * Search SPDX licenses
         * GET /licenses/spdx/search?q=mit&osiOnly=false&limit=20
         */
        get("/spdx/search") {
            val query = call.request.queryParameters["q"] ?: ""
            val osiOnly = call.request.queryParameters["osiOnly"]?.toBooleanStrictOrNull() ?: false
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20

            val response = spdxService.searchLicenses(query, osiOnly, limit)
            call.respond(HttpStatusCode.OK, response)
        }

        /**
         * Get common/popular licenses for quick selection
         * GET /licenses/spdx/common
         */
        get("/spdx/common") {
            val licenses = spdxService.getCommonLicenses()
            call.respond(HttpStatusCode.OK, mapOf("licenses" to licenses))
        }

        /**
         * Get all licenses grouped by category
         * GET /licenses/spdx/categories
         */
        get("/spdx/categories") {
            val response = spdxService.getLicensesByCategory()
            call.respond(HttpStatusCode.OK, response)
        }

        /**
         * Get a specific SPDX license by ID
         * GET /licenses/spdx/{id}
         */
        get("/spdx/{id}") {
            val licenseId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "License ID required"))

            val license = spdxService.getLicenseById(licenseId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "License not found"))

            call.respond(HttpStatusCode.OK, license)
        }

        /**
         * Validate a single license ID
         * GET /licenses/validate/{id}
         */
        get("/validate/{id}") {
            val licenseId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "License ID required"))

            val result = spdxService.validateLicense(licenseId)
            call.respond(HttpStatusCode.OK, result)
        }

        /**
         * Validate multiple license IDs
         * POST /licenses/validate
         * Body: { "licenseIds": ["MIT", "Apache-2.0", "Unknown"] }
         */
        post("/validate") {
            val request = call.receive<ValidateLicensesRequest>()

            if (request.licenseIds.isEmpty()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "At least one license ID required")
                )
            }

            if (request.licenseIds.size > 100) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Maximum 100 license IDs per request")
                )
            }

            val result = spdxService.validateLicenses(request.licenseIds)
            call.respond(HttpStatusCode.OK, result)
        }

        /**
         * Check compatibility between two licenses
         * GET /licenses/compatibility/{id1}/{id2}
         */
        get("/compatibility/{id1}/{id2}") {
            val license1 = call.parameters["id1"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "First license ID required"))
            val license2 = call.parameters["id2"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Second license ID required"))

            val result = spdxService.checkCompatibility(license1, license2)
            call.respond(HttpStatusCode.OK, result)
        }

        /**
         * Find similar licenses (for autocomplete)
         * GET /licenses/suggest?q=apa&limit=5
         */
        get("/suggest") {
            val query = call.request.queryParameters["q"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Query parameter 'q' required"))
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 20) ?: 5

            val suggestions = spdxService.findSimilarLicenses(query, limit)
            call.respond(HttpStatusCode.OK, mapOf("suggestions" to suggestions))
        }
    }
}
