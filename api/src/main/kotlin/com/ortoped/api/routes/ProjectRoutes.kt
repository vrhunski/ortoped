package com.ortoped.api.routes

import com.ortoped.api.model.CreateProjectRequest
import com.ortoped.api.service.ProjectService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.projectRoutes(projectService: ProjectService) {
    route("/projects") {
        // List all projects
        get {
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

            val response = projectService.listProjects(page, pageSize.coerceAtMost(100))
            call.respond(HttpStatusCode.OK, response)
        }

        // Create a new project
        post {
            val request = call.receive<CreateProjectRequest>()
            val response = projectService.createProject(request)
            call.respond(HttpStatusCode.Created, response)
        }

        // Get a specific project
        get("/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing project ID")

            val response = projectService.getProject(id)
            call.respond(HttpStatusCode.OK, response)
        }

        // Update a project
        put("/{id}") {
            val id = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing project ID")

            val request = call.receive<CreateProjectRequest>()
            val response = projectService.updateProject(id, request)
            call.respond(HttpStatusCode.OK, response)
        }

        // Delete a project
        delete("/{id}") {
            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing project ID")

            projectService.deleteProject(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
