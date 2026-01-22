package com.ortoped.api.service

import com.ortoped.api.model.*
import com.ortoped.api.plugins.ConflictException
import com.ortoped.api.plugins.NotFoundException
import com.ortoped.api.repository.ProjectRepository
import java.util.UUID

class ProjectService(
    private val projectRepository: ProjectRepository
) {

    fun listProjects(page: Int = 1, pageSize: Int = 20): ProjectListResponse {
        val offset = ((page - 1) * pageSize).toLong()
        val projects = projectRepository.findAll(pageSize, offset)
        val total = projectRepository.count().toInt()

        return ProjectListResponse(
            projects = projects.map { it.toResponse() },
            total = total
        )
    }

    fun getProject(id: String): ProjectResponse {
        val uuid = parseUUID(id)
        val project = projectRepository.findById(uuid)
            ?: throw NotFoundException("Project not found: $id")
        return project.toResponse()
    }

    fun createProject(request: CreateProjectRequest): ProjectResponse {
        // Check for duplicate name
        val existing = projectRepository.findByName(request.name)
        if (existing != null) {
            throw ConflictException("Project with name '${request.name}' already exists")
        }

        val policyId = request.policyId?.let { parseUUID(it) }

        val project = projectRepository.create(
            name = request.name,
            repositoryUrl = request.repositoryUrl,
            defaultBranch = request.defaultBranch,
            policyId = policyId
        )

        return project.toResponse()
    }

    fun updateProject(id: String, request: CreateProjectRequest): ProjectResponse {
        val uuid = parseUUID(id)
        val existing = projectRepository.findById(uuid)
            ?: throw NotFoundException("Project not found: $id")

        val policyId = request.policyId?.let { parseUUID(it) }

        val updated = projectRepository.update(
            id = uuid,
            name = request.name,
            repositoryUrl = request.repositoryUrl,
            policyId = policyId
        )

        if (!updated) {
            throw NotFoundException("Project not found: $id")
        }

        return projectRepository.findById(uuid)!!.toResponse()
    }

    fun deleteProject(id: String) {
        val uuid = parseUUID(id)
        if (!projectRepository.delete(uuid)) {
            throw NotFoundException("Project not found: $id")
        }
    }

    private fun parseUUID(id: String): UUID = try {
        UUID.fromString(id)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid UUID format: $id")
    }

    private fun com.ortoped.api.repository.ProjectEntity.toResponse() = ProjectResponse(
        id = id.toString(),
        name = name,
        repositoryUrl = repositoryUrl,
        defaultBranch = defaultBranch,
        policyId = policyId?.toString(),
        createdAt = createdAt
    )
}
