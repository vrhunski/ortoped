package com.ortoped.api.routes

import com.ortoped.api.model.CreatePolicyRequest
import com.ortoped.api.model.EvaluatePolicyRequest
import com.ortoped.api.repository.ScanRepository
import com.ortoped.api.service.PolicyService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.policyRoutes(policyService: PolicyService, scanRepository: ScanRepository) {
    route("/policies") {
        // List all policies
        get {
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

            val response = policyService.listPolicies(page, pageSize.coerceAtMost(100))
            call.respond(HttpStatusCode.OK, response)
        }

        // Create a new policy
        post {
            val request = call.receive<CreatePolicyRequest>()
            val response = policyService.createPolicy(request)
            call.respond(HttpStatusCode.Created, response)
        }

        // Get a specific policy
        get("/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing policy ID")

            val response = policyService.getPolicy(id)
            call.respond(HttpStatusCode.OK, response)
        }

        // Update a policy
        put("/{id}") {
            val id = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing policy ID")

            val request = call.receive<CreatePolicyRequest>()
            val response = policyService.updatePolicy(id, request)
            call.respond(HttpStatusCode.OK, response)
        }

        // Delete a policy
        delete("/{id}") {
            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing policy ID")

            policyService.deletePolicy(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    // Scan policy evaluation
    route("/scans/{scanId}/evaluate") {
        post {
            val scanId = call.parameters["scanId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing scan ID")

            val request = call.receive<EvaluatePolicyRequest>()

            val response = policyService.evaluatePolicy(scanId, request.policyId, scanRepository)
            call.respond(HttpStatusCode.OK, response)
        }

        // Get evaluations for a scan
        get {
            val scanId = call.parameters["scanId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing scan ID")

            val evaluations = policyService.getEvaluations(scanId)
            call.respond(HttpStatusCode.OK, evaluations)
        }
    }
}
