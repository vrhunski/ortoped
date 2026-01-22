package com.ortoped.api.routes

import com.ortoped.api.model.GenerateSbomRequest
import com.ortoped.api.model.SbomResponse
import com.ortoped.api.model.TriggerScanRequest
import com.ortoped.api.service.ScanService
import com.ortoped.core.sbom.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.scanRoutes(scanService: ScanService) {
    route("/scans") {
        // List all scans
        get {
            val projectId = call.request.queryParameters["projectId"]
            val status = call.request.queryParameters["status"]
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

            val response = scanService.listScans(projectId, status, page, pageSize.coerceAtMost(100))
            call.respond(HttpStatusCode.OK, response)
        }

        // Trigger a new scan (rate limited)
        rateLimit(RateLimitName("scan")) {
            post {
                val request = call.receive<TriggerScanRequest>()
                val response = scanService.triggerScan(request)
                call.respond(HttpStatusCode.Accepted, response)
            }
        }

        // Get scan status
        get("/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing scan ID")

            val response = scanService.getScan(id)
            call.respond(HttpStatusCode.OK, response)
        }

        // Get full scan result
        get("/{id}/result") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing scan ID")

            val result = scanService.getScanResult(id)
            call.respond(HttpStatusCode.OK, result)
        }

        // Get paginated dependencies
        get("/{id}/dependencies") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing scan ID")

            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

            val response = scanService.getDependencies(id, page, pageSize.coerceAtMost(100))
            call.respond(HttpStatusCode.OK, response)
        }

        // Generate SBOM from scan
        post("/{id}/sbom") {
            val id = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing scan ID")

            val request = try {
                call.receive<GenerateSbomRequest>()
            } catch (e: Exception) {
                GenerateSbomRequest()
            }

            val scanResult = scanService.getScanResult(id)

            // Parse format
            val format = when (request.format.lowercase()) {
                "cyclonedx-json" -> SbomFormat.CYCLONEDX_JSON
                "cyclonedx-xml" -> SbomFormat.CYCLONEDX_XML
                "spdx-json" -> SbomFormat.SPDX_JSON
                "spdx-tv" -> SbomFormat.SPDX_TV
                else -> SbomFormat.CYCLONEDX_JSON
            }

            val config = SbomConfig(
                format = format,
                includeAiSuggestions = request.includeAiSuggestions
            )

            // Generate SBOM
            val generator: SbomGenerator = when (format) {
                SbomFormat.CYCLONEDX_JSON, SbomFormat.CYCLONEDX_XML -> CycloneDxGenerator()
                SbomFormat.SPDX_JSON, SbomFormat.SPDX_TV -> SpdxGenerator()
            }

            val content = generator.generate(scanResult, config)

            // Determine filename
            val filename = "${scanResult.projectName}-sbom.${format.extension}"

            call.respond(
                HttpStatusCode.OK,
                SbomResponse(
                    content = content,
                    format = format.displayName,
                    filename = filename
                )
            )
        }

        // Cancel a running scan
        delete("/{id}") {
            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing scan ID")

            scanService.cancelScan(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
