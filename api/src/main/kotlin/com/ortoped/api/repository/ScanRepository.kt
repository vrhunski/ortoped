package com.ortoped.api.repository

import com.ortoped.api.model.ScanStatus
import com.ortoped.api.model.Scans
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class ScanRepository {

    fun findAll(
        projectId: UUID? = null,
        status: ScanStatus? = null,
        limit: Int = 100,
        offset: Long = 0
    ): List<ScanEntity> = transaction {
        var query = Scans.selectAll()

        projectId?.let {
            query = query.andWhere { Scans.projectId eq it }
        }

        status?.let {
            query = query.andWhere { Scans.status eq it.value }
        }

        query
            .orderBy(Scans.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { it.toScanEntity() }
    }

    fun count(projectId: UUID? = null, status: ScanStatus? = null): Long = transaction {
        var query = Scans.selectAll()

        projectId?.let {
            query = query.andWhere { Scans.projectId eq it }
        }

        status?.let {
            query = query.andWhere { Scans.status eq it.value }
        }

        query.count()
    }

    fun findById(id: UUID): ScanEntity? = transaction {
        Scans.selectAll()
            .where { Scans.id eq id }
            .singleOrNull()
            ?.toScanEntity()
    }

    fun create(
        projectId: UUID?,
        enableAi: Boolean = true,
        enableSpdx: Boolean = false
    ): ScanEntity = transaction {
        val id = UUID.randomUUID()
        val now = Clock.System.now()

        Scans.insert {
            it[Scans.id] = id
            it[Scans.projectId] = projectId
            it[status] = ScanStatus.PENDING.value
            it[Scans.enableAi] = enableAi
            it[Scans.enableSpdx] = enableSpdx
            it[createdAt] = now
        }

        ScanEntity(
            id = id,
            projectId = projectId,
            status = ScanStatus.PENDING.value,
            enableAi = enableAi,
            result = null,
            summary = null,
            startedAt = null,
            completedAt = null,
            errorMessage = null,
            createdAt = now.toString()
        )
    }

    fun updateStatus(
        id: UUID,
        status: ScanStatus,
        startedAt: Instant? = null,
        completedAt: Instant? = null,
        errorMessage: String? = null
    ): Boolean = transaction {
        val updated = Scans.update({ Scans.id eq id }) {
            it[Scans.status] = status.value
            startedAt?.let { ts -> it[Scans.startedAt] = ts }
            completedAt?.let { ts -> it[Scans.completedAt] = ts }
            errorMessage?.let { msg -> it[Scans.errorMessage] = msg }
        }
        updated > 0
    }

    fun updateResult(
        id: UUID,
        result: String,
        summary: String
    ): Boolean = transaction {
        val updated = Scans.update({ Scans.id eq id }) {
            it[Scans.result] = result
            it[Scans.summary] = summary
            it[status] = ScanStatus.COMPLETE.value
            it[completedAt] = Clock.System.now()
        }
        updated > 0
    }

    fun delete(id: UUID): Boolean = transaction {
        Scans.deleteWhere { Scans.id eq id } > 0
    }

    private fun ResultRow.toScanEntity() = ScanEntity(
        id = this[Scans.id].value,
        projectId = this[Scans.projectId],
        status = this[Scans.status],
        enableAi = this[Scans.enableAi],
        result = this[Scans.result],
        summary = this[Scans.summary],
        startedAt = this[Scans.startedAt]?.toString(),
        completedAt = this[Scans.completedAt]?.toString(),
        errorMessage = this[Scans.errorMessage],
        createdAt = this[Scans.createdAt].toString()
    )
}

data class ScanEntity(
    val id: UUID,
    val projectId: UUID?,
    val status: String,
    val enableAi: Boolean,
    val result: String?,
    val summary: String?,
    val startedAt: String?,
    val completedAt: String?,
    val errorMessage: String?,
    val createdAt: String
)
