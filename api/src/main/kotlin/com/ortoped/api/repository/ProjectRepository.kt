package com.ortoped.api.repository

import com.ortoped.api.model.Projects
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class ProjectRepository {

    fun findAll(limit: Int = 100, offset: Long = 0): List<ProjectEntity> = transaction {
        Projects.selectAll()
            .orderBy(Projects.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { it.toProjectEntity() }
    }

    fun count(): Long = transaction {
        Projects.selectAll().count()
    }

    fun findById(id: UUID): ProjectEntity? = transaction {
        Projects.selectAll()
            .where { Projects.id eq id }
            .singleOrNull()
            ?.toProjectEntity()
    }

    fun findByName(name: String): ProjectEntity? = transaction {
        Projects.selectAll()
            .where { Projects.name eq name }
            .singleOrNull()
            ?.toProjectEntity()
    }

    fun create(
        name: String,
        repositoryUrl: String? = null,
        defaultBranch: String = "main",
        policyId: UUID? = null
    ): ProjectEntity = transaction {
        val id = UUID.randomUUID()
        val now = Clock.System.now()

        Projects.insert {
            it[Projects.id] = id
            it[Projects.name] = name
            it[Projects.repositoryUrl] = repositoryUrl
            it[Projects.defaultBranch] = defaultBranch
            it[Projects.policyId] = policyId
            it[Projects.createdAt] = now
        }

        ProjectEntity(
            id = id,
            name = name,
            repositoryUrl = repositoryUrl,
            defaultBranch = defaultBranch,
            policyId = policyId,
            createdAt = now.toString()
        )
    }

    fun update(id: UUID, name: String? = null, repositoryUrl: String? = null, policyId: UUID? = null): Boolean = transaction {
        val updated = Projects.update({ Projects.id eq id }) {
            name?.let { n -> it[Projects.name] = n }
            repositoryUrl?.let { r -> it[Projects.repositoryUrl] = r }
            policyId?.let { p -> it[Projects.policyId] = p }
        }
        updated > 0
    }

    fun delete(id: UUID): Boolean = transaction {
        Projects.deleteWhere { Projects.id eq id } > 0
    }

    private fun ResultRow.toProjectEntity() = ProjectEntity(
        id = this[Projects.id].value,
        name = this[Projects.name],
        repositoryUrl = this[Projects.repositoryUrl],
        defaultBranch = this[Projects.defaultBranch],
        policyId = this[Projects.policyId],
        createdAt = this[Projects.createdAt].toString()
    )
}

data class ProjectEntity(
    val id: UUID,
    val name: String,
    val repositoryUrl: String?,
    val defaultBranch: String,
    val policyId: UUID?,
    val createdAt: String
)
