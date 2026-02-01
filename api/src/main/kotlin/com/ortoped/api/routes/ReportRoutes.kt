package com.ortoped.api.routes

import com.ortoped.api.service.ReportService
import com.ortoped.core.model.ReportOptions
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Report generation API routes
 */
fun Route.reportRoutes(reportService: ReportService) {

    route("/scans/{scanId}/reports") {

        /**
         * Get report summary (quick overview without generating full report)
         * GET /scans/{scanId}/reports/summary
         */
        get("/summary") {
            val scanId = call.parameters["scanId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))

            val summary = reportService.getReportSummary(scanId)
            call.respond(HttpStatusCode.OK, summary)
        }

        /**
         * Generate a comprehensive report
         * POST /scans/{scanId}/reports/generate
         */
        post("/generate") {
            val scanId = call.parameters["scanId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))

            val options = try {
                call.receive<ReportOptions>()
            } catch (e: Exception) {
                ReportOptions() // Use defaults
            }

            val generatedBy = call.request.headers["X-Curator-Id"]

            val report = reportService.generateReport(scanId, options, generatedBy)
            call.respond(HttpStatusCode.OK, report)
        }

        /**
         * Generate and download report as file
         * GET /scans/{scanId}/reports/download?format=json|html
         */
        get("/download") {
            val scanId = call.parameters["scanId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))

            val format = call.request.queryParameters["format"] ?: "json"
            val includeDetails = call.request.queryParameters["includeDetails"]?.toBooleanStrictOrNull() ?: false

            val options = ReportOptions(
                format = format,
                includePolicy = true,
                includeCuration = true,
                includeAuditTrail = true,
                includeDependencyDetails = includeDetails,
                includeAiReasoning = false
            )

            val generatedBy = call.request.headers["X-Curator-Id"]
            val report = reportService.generateReport(scanId, options, generatedBy)

            // Set appropriate content type
            val contentType = when (format.lowercase()) {
                "html" -> ContentType.Text.Html
                else -> ContentType.Application.Json
            }

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    report.filename
                ).toString()
            )

            call.respondText(report.content, contentType)
        }

        /**
         * Generate JSON report (shorthand)
         * GET /scans/{scanId}/reports/json
         */
        get("/json") {
            val scanId = call.parameters["scanId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))

            val includeDetails = call.request.queryParameters["includeDetails"]?.toBooleanStrictOrNull() ?: false

            val options = ReportOptions(
                format = "json",
                includeDependencyDetails = includeDetails
            )

            val report = reportService.generateReport(scanId, options, null)
            call.respond(HttpStatusCode.OK, report)
        }

        /**
         * Generate HTML report (shorthand)
         * GET /scans/{scanId}/reports/html
         */
        get("/html") {
            val scanId = call.parameters["scanId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))

            val options = ReportOptions(
                format = "html",
                includeDependencyDetails = false
            )

            val report = reportService.generateReport(scanId, options, null)

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    report.filename
                ).toString()
            )

            call.respondText(report.content, ContentType.Text.Html)
        }

        /**
         * Generate ORT Evaluator compatible export
         * GET /scans/{scanId}/reports/ort
         */
        get("/ort") {
            val scanId = call.parameters["scanId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))

            val export = reportService.generateOrtExport(scanId)

            // Return the full response object so frontend can extract content and filename
            call.respond(HttpStatusCode.OK, export)
        }

        /**
         * Generate EU Compliance Report
         * GET /scans/{scanId}/reports/eu-compliance
         *
         * This report is designed for EU/German regulatory requirements and includes:
         * - Complete audit trail of all actions
         * - Structured justifications for all license decisions
         * - Two-role approval chain (curator and approver)
         * - OR license resolution documentation
         * - Distribution scope for each dependency
         *
         * Note: Curation must be approved before generating this report
         */
        get("/eu-compliance") {
            val scanId = call.parameters["scanId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Scan ID required"))

            val format = call.request.queryParameters["format"] ?: "json"

            val report = reportService.generateEuComplianceReport(scanId, format)

            if (format == "html") {
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        report.filename
                    ).toString()
                )
                call.respondText(report.content, ContentType.Text.Html)
            } else {
                call.respond(HttpStatusCode.OK, report)
            }
        }
    }
}
