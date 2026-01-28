package com.ortoped.api.repository

import com.ortoped.api.model.CuratedScans
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * Repository for tracking curated scans (for incremental curation support)
 */
class CuratedScanRepository {

    fun findById(id: UUID): CuratedScanEntity? = transaction {
        CuratedScans.selectAll()
            .where { CuratedScans.id eq id }
            .singleOrNull()
            ?.toEntity()
    }

    fun findByScanId(scanId: UUID): CuratedScanEntity? = transaction {
        CuratedScans.selectAll()
            .where { CuratedScans.scanId eq scanId }
            .singleOrNull()
            ?.toEntity()
    }

    /**
     * Find the most recent curated scan for a project
     * Used to determine the previous scan for incremental curation
     */
    fun findLatestForProject(projectId: UUID): CuratedScanEntity? = transaction {
        // Join with scans to get project info
        CuratedScans.innerJoin(
            com.ortoped.api.model.Scans,
            { CuratedScans.scanId },
            { com.ortoped.api.model.Scans.id }
        )
            .selectAll()
            .where { com.ortoped.api.model.Scans.projectId eq projectId }
            .orderBy(CuratedScans.curatedAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let {
                CuratedScanEntity(
                    id = it[CuratedScans.id].value,
                    scanId = it[CuratedScans.scanId],
                    previousScanId = it[CuratedScans.previousScanId],
                    sessionId = it[CuratedScans.sessionId],
                    curatorId = it[CuratedScans.curatorId],
                    dependencyHash = it[CuratedScans.dependencyHash],
                    dependencyCount = it[CuratedScans.dependencyCount],
                    curatedAt = it[CuratedScans.curatedAt].toString()
                )
            }
    }

    /**
     * Find all curated scans ordered by date
     */
    fun findAll(limit: Int = 100, offset: Long = 0): List<CuratedScanEntity> = transaction {
        CuratedScans.selectAll()
            .orderBy(CuratedScans.curatedAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { it.toEntity() }
    }

    fun count(): Long = transaction {
        CuratedScans.selectAll().count()
    }

    fun create(
        scanId: UUID,
        previousScanId: UUID? = null,
        sessionId: UUID? = null,
        curatorId: String? = null,
        dependencyHash: String? = null,
        dependencyCount: Int = 0
    ): CuratedScanEntity = transaction {
        val id = UUID.randomUUID()
        val now = Clock.System.now()

        CuratedScans.insert {
            it[CuratedScans.id] = id
            it[CuratedScans.scanId] = scanId
            it[CuratedScans.previousScanId] = previousScanId
            it[CuratedScans.sessionId] = sessionId
            it[CuratedScans.curatorId] = curatorId
            it[CuratedScans.dependencyHash] = dependencyHash
            it[CuratedScans.dependencyCount] = dependencyCount
            it[CuratedScans.curatedAt] = now
        }

        CuratedScanEntity(
            id = id,
            scanId = scanId,
            previousScanId = previousScanId,
            sessionId = sessionId,
            curatorId = curatorId,
            dependencyHash = dependencyHash,
            dependencyCount = dependencyCount,
            curatedAt = now.toString()
        )
    }

    fun updatePreviousScan(id: UUID, previousScanId: UUID?): Boolean = transaction {
        val updated = CuratedScans.update({ CuratedScans.id eq id }) {
            it[CuratedScans.previousScanId] = previousScanId
        }
        updated > 0
    }

    fun delete(id: UUID): Boolean = transaction {
        CuratedScans.deleteWhere { CuratedScans.id eq id } > 0
    }

    fun deleteByScanId(scanId: UUID): Boolean = transaction {
        CuratedScans.deleteWhere { CuratedScans.scanId eq scanId } > 0
    }

    private fun ResultRow.toEntity() = CuratedScanEntity(
        id = this[CuratedScans.id].value,
        scanId = this[CuratedScans.scanId],
        previousScanId = this[CuratedScans.previousScanId],
        sessionId = this[CuratedScans.sessionId],
        curatorId = this[CuratedScans.curatorId],
        dependencyHash = this[CuratedScans.dependencyHash],
        dependencyCount = this[CuratedScans.dependencyCount],
        curatedAt = this[CuratedScans.curatedAt].toString()
    )
}

data class CuratedScanEntity(
    val id: UUID,
    val scanId: UUID,
    val previousScanId: UUID?,
    val sessionId: UUID?,
    val curatorId: String?,
    val dependencyHash: String?,
    val dependencyCount: Int,
    val curatedAt: String
)
