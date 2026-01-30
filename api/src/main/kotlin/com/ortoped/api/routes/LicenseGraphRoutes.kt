package com.ortoped.api.routes

import com.ortoped.api.service.LicenseGraphService
import com.ortoped.core.graph.model.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * API routes for the License Knowledge Graph.
 *
 * Provides endpoints for:
 * - License queries and details
 * - Compatibility checking
 * - Obligation aggregation
 * - Dependency tree analysis
 */
fun Route.licenseGraphRoutes(graphService: LicenseGraphService) {

    route("/graph") {

        // =====================================================================
        // Statistics & Health
        // =====================================================================

        /**
         * GET /graph/statistics
         * Get overall graph statistics
         */
        get("/statistics") {
            val stats = graphService.getStatistics()
            call.respond(stats)
        }

        /**
         * GET /graph/health
         * Check if the graph is initialized and healthy
         */
        get("/health") {
            val stats = graphService.getStatistics()
            call.respond(GraphHealthResponse(
                initialized = graphService.isInitialized(),
                totalLicenses = stats.totalLicenses,
                totalEdges = stats.totalEdges,
                lastUpdated = stats.lastUpdated
            ))
        }

        // =====================================================================
        // License Endpoints
        // =====================================================================

        route("/licenses") {

            /**
             * GET /graph/licenses
             * List all licenses, optionally filtered by category or family
             */
            get {
                val category = call.request.queryParameters["category"]
                val family = call.request.queryParameters["family"]
                val search = call.request.queryParameters["search"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100

                val licenses = when {
                    search != null -> graphService.searchLicenses(search, limit)
                    category != null -> {
                        val cat = LicenseCategory.fromString(category)
                        graphService.getLicensesByCategory(cat)
                    }
                    family != null -> graphService.getLicensesByFamily(family)
                    else -> graphService.getAllLicenses().take(limit)
                }

                call.respond(LicenseListResponse(
                    licenses = licenses,
                    total = licenses.size
                ))
            }

            /**
             * GET /graph/licenses/{id}
             * Get license by SPDX ID
             */
            get("/{id}") {
                val id = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing license ID"))

                val license = graphService.getLicense(id)
                if (license == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("License not found: $id"))
                } else {
                    call.respond(license)
                }
            }

            /**
             * GET /graph/licenses/{id}/details
             * Get complete license details including obligations, rights, compatibility
             */
            get("/{id}/details") {
                val id = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing license ID"))

                val details = graphService.getLicenseDetails(id)
                if (details == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("License not found: $id"))
                } else {
                    call.respond(details)
                }
            }

            /**
             * GET /graph/licenses/{id}/obligations
             * Get obligations for a specific license
             */
            get("/{id}/obligations") {
                val id = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing license ID"))

                val obligations = graphService.getObligationsForLicense(id)
                call.respond(ObligationListResponse(
                    licenseId = id,
                    obligations = obligations,
                    total = obligations.size
                ))
            }

            /**
             * GET /graph/licenses/{id}/rights
             * Get rights for a specific license
             */
            get("/{id}/rights") {
                val id = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing license ID"))

                val rights = graphService.getRightsForLicense(id)
                call.respond(RightListResponse(
                    licenseId = id,
                    rights = rights,
                    total = rights.size
                ))
            }
        }

        // =====================================================================
        // Compatibility Endpoints
        // =====================================================================

        route("/compatibility") {

            /**
             * POST /graph/compatibility/check
             * Check compatibility between two licenses
             */
            post("/check") {
                val request = call.receive<CompatibilityCheckRequest>()

                if (request.license1.isBlank() || request.license2.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Both license1 and license2 are required")
                    )
                }

                val result = graphService.checkCompatibility(
                    request.license1,
                    request.license2,
                    request.useCase
                )
                call.respond(result)
            }

            /**
             * POST /graph/compatibility/matrix
             * Check compatibility between multiple licenses (all pairs)
             */
            post("/matrix") {
                val request = call.receive<CompatibilityMatrixRequest>()

                if (request.licenses.size < 2) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("At least 2 licenses are required")
                    )
                }

                val results = graphService.checkCompatibilityMatrix(request.licenses)
                call.respond(CompatibilityMatrixResponse(
                    licenses = request.licenses,
                    results = results,
                    totalChecks = results.size
                ))
            }

            /**
             * POST /graph/compatibility/path
             * Find a compatibility path between two licenses
             */
            post("/path") {
                val request = call.receive<CompatibilityPathRequest>()

                if (request.source.isBlank() || request.target.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Both source and target licenses are required")
                    )
                }

                val path = graphService.findCompatibilityPath(
                    request.source,
                    request.target,
                    request.maxDepth ?: 3
                )

                if (path != null) {
                    call.respond(path)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("No compatibility path found between ${request.source} and ${request.target}")
                    )
                }
            }

            /**
             * GET /graph/compatibility/quick?license1=MIT&license2=GPL-3.0
             * Quick compatibility check via GET
             */
            get("/quick") {
                val license1 = call.request.queryParameters["license1"]
                val license2 = call.request.queryParameters["license2"]

                if (license1.isNullOrBlank() || license2.isNullOrBlank()) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Both license1 and license2 query parameters are required")
                    )
                }

                val result = graphService.checkCompatibility(license1, license2)
                call.respond(result)
            }
        }

        // =====================================================================
        // Obligation Endpoints
        // =====================================================================

        route("/obligations") {

            /**
             * GET /graph/obligations
             * List all standard obligations
             */
            get {
                val obligations = graphService.getAllObligations()
                call.respond(AllObligationsResponse(
                    obligations = obligations,
                    total = obligations.size
                ))
            }

            /**
             * GET /graph/obligations/{id}
             * Get a specific obligation by ID
             */
            get("/{id}") {
                val id = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing obligation ID"))

                val obligation = graphService.getObligation(id)
                if (obligation == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Obligation not found: $id"))
                } else {
                    call.respond(obligation)
                }
            }

            /**
             * POST /graph/obligations/aggregate
             * Aggregate obligations from multiple licenses
             */
            post("/aggregate") {
                val request = call.receive<AggregateObligationsRequest>()

                if (request.licenses.isEmpty()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("At least one license is required")
                    )
                }

                val aggregated = graphService.getAggregatedObligations(request.licenses)
                call.respond(aggregated)
            }
        }

        // =====================================================================
        // Rights Endpoints
        // =====================================================================

        route("/rights") {

            /**
             * GET /graph/rights
             * List all standard rights
             */
            get {
                val rights = graphService.getAllRights()
                call.respond(AllRightsResponse(
                    rights = rights,
                    total = rights.size
                ))
            }
        }

        // =====================================================================
        // Analysis Endpoints
        // =====================================================================

        route("/analyze") {

            /**
             * POST /graph/analyze
             * Analyze a dependency tree for conflicts, obligations, etc.
             */
            post {
                val request = call.receive<AnalyzeTreeRequest>()

                if (request.dependencies.isEmpty()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("At least one dependency is required")
                    )
                }

                val analysis = graphService.analyzeDependencyTree(
                    request.dependencies,
                    request.useCase
                )
                call.respond(analysis)
            }

            /**
             * POST /graph/analyze/conflicts
             * Quick conflict check without full analysis
             */
            post("/conflicts") {
                val request = call.receive<AnalyzeTreeRequest>()

                if (request.dependencies.isEmpty()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("At least one dependency is required")
                    )
                }

                val conflicts = graphService.findConflicts(request.dependencies)
                call.respond(ConflictsResponse(
                    conflicts = conflicts,
                    total = conflicts.size,
                    hasBlockingConflicts = conflicts.any { it.severity == ConflictSeverity.BLOCKING }
                ))
            }
        }

        // =====================================================================
        // Use Case Endpoints
        // =====================================================================

        route("/usecases") {

            /**
             * GET /graph/usecases
             * List all use cases
             */
            get {
                val useCases = graphService.getAllUseCases()
                call.respond(UseCaseListResponse(
                    useCases = useCases,
                    total = useCases.size
                ))
            }

            /**
             * GET /graph/usecases/{id}
             * Get a specific use case
             */
            get("/{id}") {
                val id = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing use case ID"))

                val useCase = graphService.getUseCase(id)
                if (useCase == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Use case not found: $id"))
                } else {
                    call.respond(useCase)
                }
            }
        }

        // =====================================================================
        // Admin Endpoints
        // =====================================================================

        route("/admin") {

            /**
             * POST /graph/admin/reload
             * Reload the graph with fresh data
             */
            post("/reload") {
                graphService.reload()
                call.respond(SuccessResponse("Graph reloaded successfully"))
            }
        }
    }
}

// =============================================================================
// Request DTOs
// =============================================================================

@Serializable
data class CompatibilityCheckRequest(
    val license1: String,
    val license2: String,
    val useCase: String? = null
)

@Serializable
data class CompatibilityMatrixRequest(
    val licenses: List<String>
)

@Serializable
data class CompatibilityPathRequest(
    val source: String,
    val target: String,
    val maxDepth: Int? = 3
)

@Serializable
data class AggregateObligationsRequest(
    val licenses: List<String>
)

@Serializable
data class AnalyzeTreeRequest(
    val dependencies: List<DependencyLicense>,
    val useCase: String? = null
)

// =============================================================================
// Response DTOs
// =============================================================================

@Serializable
data class GraphHealthResponse(
    val initialized: Boolean,
    val totalLicenses: Int,
    val totalEdges: Int,
    val lastUpdated: String?
)

@Serializable
data class LicenseListResponse(
    val licenses: List<LicenseNode>,
    val total: Int
)

@Serializable
data class ObligationListResponse(
    val licenseId: String,
    val obligations: List<ObligationWithScope>,
    val total: Int
)

@Serializable
data class RightListResponse(
    val licenseId: String,
    val rights: List<RightNode>,
    val total: Int
)

@Serializable
data class AllObligationsResponse(
    val obligations: List<ObligationNode>,
    val total: Int
)

@Serializable
data class AllRightsResponse(
    val rights: List<RightNode>,
    val total: Int
)

@Serializable
data class CompatibilityMatrixResponse(
    val licenses: List<String>,
    val results: List<CompatibilityResult>,
    val totalChecks: Int
)

@Serializable
data class ConflictsResponse(
    val conflicts: List<LicenseConflict>,
    val total: Int,
    val hasBlockingConflicts: Boolean
)

@Serializable
data class UseCaseListResponse(
    val useCases: List<UseCaseNode>,
    val total: Int
)

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class SuccessResponse(
    val message: String
)
