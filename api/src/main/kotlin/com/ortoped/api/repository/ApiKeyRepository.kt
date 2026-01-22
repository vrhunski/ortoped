package com.ortoped.api.repository

import com.ortoped.api.model.ApiKeys
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class ApiKeyRepository {

    fun findAll(limit: Int = 100, offset: Long = 0): List<ApiKeyEntity> = transaction {
        ApiKeys.selectAll()
            .orderBy(ApiKeys.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { it.toApiKeyEntity() }
    }

    fun count(): Long = transaction {
        ApiKeys.selectAll().count()
    }

    fun findById(id: UUID): ApiKeyEntity? = transaction {
        ApiKeys.selectAll()
            .where { ApiKeys.id eq id }
            .singleOrNull()
            ?.toApiKeyEntity()
    }

    fun findByKeyPrefix(prefix: String): List<ApiKeyEntity> = transaction {
        ApiKeys.selectAll()
            .where { ApiKeys.keyPrefix eq prefix }
            .map { it.toApiKeyEntity() }
    }

    fun create(
        name: String,
        keyHash: String,
        keyPrefix: String
    ): ApiKeyEntity = transaction {
        val id = UUID.randomUUID()
        val now = Clock.System.now()

        ApiKeys.insert {
            it[ApiKeys.id] = id
            it[ApiKeys.name] = name
            it[ApiKeys.keyHash] = keyHash
            it[ApiKeys.keyPrefix] = keyPrefix
            it[ApiKeys.createdAt] = now
        }

        ApiKeyEntity(
            id = id,
            name = name,
            keyHash = keyHash,
            keyPrefix = keyPrefix,
            createdAt = now.toString()
        )
    }

    fun delete(id: UUID): Boolean = transaction {
        ApiKeys.deleteWhere { ApiKeys.id eq id } > 0
    }

    private fun ResultRow.toApiKeyEntity() = ApiKeyEntity(
        id = this[ApiKeys.id].value,
        name = this[ApiKeys.name],
        keyHash = this[ApiKeys.keyHash],
        keyPrefix = this[ApiKeys.keyPrefix],
        createdAt = this[ApiKeys.createdAt].toString()
    )
}

data class ApiKeyEntity(
    val id: UUID,
    val name: String,
    val keyHash: String,
    val keyPrefix: String,
    val createdAt: String
)
