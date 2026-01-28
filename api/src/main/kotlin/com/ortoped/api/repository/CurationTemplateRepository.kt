package com.ortoped.api.repository

import com.ortoped.api.model.CurationTemplates
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * Repository for curation templates
 */
class CurationTemplateRepository {

    fun findById(id: UUID): CurationTemplateEntity? = transaction {
        CurationTemplates.selectAll()
            .where { CurationTemplates.id eq id }
            .singleOrNull()
            ?.toEntity()
    }

    fun findByName(name: String): CurationTemplateEntity? = transaction {
        CurationTemplates.selectAll()
            .where { CurationTemplates.name eq name }
            .singleOrNull()
            ?.toEntity()
    }

    fun findAll(
        activeOnly: Boolean = true,
        globalOnly: Boolean = false,
        createdBy: String? = null,
        limit: Int = 100,
        offset: Long = 0
    ): List<CurationTemplateEntity> = transaction {
        var query = CurationTemplates.selectAll()

        if (activeOnly) {
            query = query.andWhere { CurationTemplates.isActive eq true }
        }

        if (globalOnly) {
            query = query.andWhere { CurationTemplates.isGlobal eq true }
        }

        createdBy?.let {
            query = query.andWhere { CurationTemplates.createdBy eq it }
        }

        query
            .orderBy(CurationTemplates.usageCount, SortOrder.DESC)
            .orderBy(CurationTemplates.name, SortOrder.ASC)
            .limit(limit, offset)
            .map { it.toEntity() }
    }

    /**
     * Find templates available for a user (global + user's own)
     */
    fun findAvailableForUser(
        userId: String,
        activeOnly: Boolean = true,
        limit: Int = 100,
        offset: Long = 0
    ): List<CurationTemplateEntity> = transaction {
        var query = CurationTemplates.selectAll()
            .andWhere {
                (CurationTemplates.isGlobal eq true) or (CurationTemplates.createdBy eq userId)
            }

        if (activeOnly) {
            query = query.andWhere { CurationTemplates.isActive eq true }
        }

        query
            .orderBy(CurationTemplates.usageCount, SortOrder.DESC)
            .limit(limit, offset)
            .map { it.toEntity() }
    }

    fun count(
        activeOnly: Boolean = true,
        globalOnly: Boolean = false
    ): Long = transaction {
        var query = CurationTemplates.selectAll()

        if (activeOnly) {
            query = query.andWhere { CurationTemplates.isActive eq true }
        }

        if (globalOnly) {
            query = query.andWhere { CurationTemplates.isGlobal eq true }
        }

        query.count()
    }

    fun create(
        name: String,
        description: String?,
        conditions: String, // JSON
        actions: String, // JSON
        createdBy: String?,
        isGlobal: Boolean = false
    ): CurationTemplateEntity = transaction {
        val id = UUID.randomUUID()
        val now = Clock.System.now()

        CurationTemplates.insert {
            it[CurationTemplates.id] = id
            it[CurationTemplates.name] = name
            it[CurationTemplates.description] = description
            it[CurationTemplates.conditions] = conditions
            it[CurationTemplates.actions] = actions
            it[CurationTemplates.createdBy] = createdBy
            it[CurationTemplates.isGlobal] = isGlobal
            it[isActive] = true
            it[usageCount] = 0
            it[createdAt] = now
            it[updatedAt] = now
        }

        CurationTemplateEntity(
            id = id,
            name = name,
            description = description,
            conditions = conditions,
            actions = actions,
            createdBy = createdBy,
            isGlobal = isGlobal,
            isActive = true,
            usageCount = 0,
            createdAt = now.toString(),
            updatedAt = now.toString()
        )
    }

    fun update(
        id: UUID,
        name: String? = null,
        description: String? = null,
        conditions: String? = null,
        actions: String? = null,
        isGlobal: Boolean? = null,
        isActive: Boolean? = null
    ): Boolean = transaction {
        val updated = CurationTemplates.update({ CurationTemplates.id eq id }) {
            name?.let { n -> it[CurationTemplates.name] = n }
            description?.let { d -> it[CurationTemplates.description] = d }
            conditions?.let { c -> it[CurationTemplates.conditions] = c }
            actions?.let { a -> it[CurationTemplates.actions] = a }
            isGlobal?.let { g -> it[CurationTemplates.isGlobal] = g }
            isActive?.let { a -> it[CurationTemplates.isActive] = a }
            it[updatedAt] = Clock.System.now()
        }
        updated > 0
    }

    fun incrementUsageCount(id: UUID): Boolean = transaction {
        val updated = CurationTemplates.update({ CurationTemplates.id eq id }) {
            with(SqlExpressionBuilder) {
                it[usageCount] = usageCount + 1
            }
            it[updatedAt] = Clock.System.now()
        }
        updated > 0
    }

    fun deactivate(id: UUID): Boolean = transaction {
        val updated = CurationTemplates.update({ CurationTemplates.id eq id }) {
            it[isActive] = false
            it[updatedAt] = Clock.System.now()
        }
        updated > 0
    }

    fun delete(id: UUID): Boolean = transaction {
        CurationTemplates.deleteWhere { CurationTemplates.id eq id } > 0
    }

    private fun ResultRow.toEntity() = CurationTemplateEntity(
        id = this[CurationTemplates.id].value,
        name = this[CurationTemplates.name],
        description = this[CurationTemplates.description],
        conditions = this[CurationTemplates.conditions],
        actions = this[CurationTemplates.actions],
        createdBy = this[CurationTemplates.createdBy],
        isGlobal = this[CurationTemplates.isGlobal],
        isActive = this[CurationTemplates.isActive],
        usageCount = this[CurationTemplates.usageCount],
        createdAt = this[CurationTemplates.createdAt].toString(),
        updatedAt = this[CurationTemplates.updatedAt].toString()
    )
}

data class CurationTemplateEntity(
    val id: UUID,
    val name: String,
    val description: String?,
    val conditions: String, // JSON
    val actions: String, // JSON
    val createdBy: String?,
    val isGlobal: Boolean,
    val isActive: Boolean,
    val usageCount: Int,
    val createdAt: String,
    val updatedAt: String
)
