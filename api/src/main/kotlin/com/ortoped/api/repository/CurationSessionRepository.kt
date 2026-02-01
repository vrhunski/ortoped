package com.ortoped.api.repository

import com.ortoped.api.model.CurationSessionStatus
import com.ortoped.api.model.CurationSessions
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class CurationSessionRepository {

    fun findById(id: UUID): CurationSessionEntity? = transaction {
        CurationSessions.selectAll()
            .where { CurationSessions.id eq id }
            .singleOrNull()
            ?.toEntity()
    }

    fun findByScanId(scanId: UUID): CurationSessionEntity? = transaction {
        CurationSessions.selectAll()
            .where { CurationSessions.scanId eq scanId }
            .singleOrNull()
            ?.toEntity()
    }

    fun findAll(
        status: CurationSessionStatus? = null,
        limit: Int = 100,
        offset: Long = 0
    ): List<CurationSessionEntity> = transaction {
        var query = CurationSessions.selectAll()

        status?.let {
            query = query.andWhere { CurationSessions.status eq it.value }
        }

        query
            .orderBy(CurationSessions.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { it.toEntity() }
    }

    fun count(status: CurationSessionStatus? = null): Long = transaction {
        var query = CurationSessions.selectAll()

        status?.let {
            query = query.andWhere { CurationSessions.status eq it.value }
        }

        query.count()
    }

    fun create(
        scanId: UUID,
        totalItems: Int = 0,
        curatorId: String = "system",
        curatorName: String? = null
    ): CurationSessionEntity = transaction {
        val id = UUID.randomUUID()
        val now = Clock.System.now()

        CurationSessions.insert {
            it[CurationSessions.id] = id
            it[CurationSessions.scanId] = scanId
            it[CurationSessions.status] = CurationSessionStatus.IN_PROGRESS.value
            it[CurationSessions.totalItems] = totalItems
            it[CurationSessions.pendingItems] = totalItems
            it[CurationSessions.acceptedItems] = 0
            it[CurationSessions.rejectedItems] = 0
            it[CurationSessions.modifiedItems] = 0
            it[CurationSessions.curatorId] = curatorId
            it[CurationSessions.curatorName] = curatorName
            it[CurationSessions.createdAt] = now
            it[CurationSessions.updatedAt] = now
        }

        CurationSessionEntity(
            id = id,
            scanId = scanId,
            status = CurationSessionStatus.IN_PROGRESS.value,
            totalItems = totalItems,
            pendingItems = totalItems,
            acceptedItems = 0,
            rejectedItems = 0,
            modifiedItems = 0,
            curatorId = curatorId,
            curatorName = curatorName,
            approvedBy = null,
            approverName = null,
            approverRole = null,
            approvedAt = null,
            approvalComment = null,
            createdAt = now.toString(),
            updatedAt = now.toString()
        )
    }

    fun updateStatistics(
        id: UUID,
        pending: Int,
        accepted: Int,
        rejected: Int,
        modified: Int
    ): Boolean = transaction {
        val updated = CurationSessions.update({ CurationSessions.id eq id }) {
            it[pendingItems] = pending
            it[acceptedItems] = accepted
            it[rejectedItems] = rejected
            it[modifiedItems] = modified
            it[updatedAt] = Clock.System.now()
        }
        updated > 0
    }

    fun updateStatus(id: UUID, status: CurationSessionStatus): Boolean = transaction {
        val updated = CurationSessions.update({ CurationSessions.id eq id }) {
            it[CurationSessions.status] = status.value
            it[updatedAt] = Clock.System.now()
        }
        updated > 0
    }

    fun approve(
        id: UUID,
        approvedBy: String,
        approverName: String? = null,
        approverRole: String? = null,
        comment: String? = null
    ): Boolean = transaction {
        val now = Clock.System.now()
        val updated = CurationSessions.update({ CurationSessions.id eq id }) {
            it[status] = CurationSessionStatus.APPROVED.value
            it[CurationSessions.approvedBy] = approvedBy
            it[CurationSessions.approverName] = approverName
            it[CurationSessions.approverRole] = approverRole
            it[approvedAt] = now
            it[approvalComment] = comment
            it[updatedAt] = now
        }
        updated > 0
    }

    fun delete(id: UUID): Boolean = transaction {
        CurationSessions.deleteWhere { CurationSessions.id eq id } > 0
    }

    fun deleteByScanId(scanId: UUID): Boolean = transaction {
        CurationSessions.deleteWhere { CurationSessions.scanId eq scanId } > 0
    }

    /**
     * Recalculate and update statistics from actual curation records
     */
    fun recalculateStatistics(id: UUID, curationRepository: CurationRepository): Boolean = transaction {
        val stats = curationRepository.getStatisticsBySessionId(id)
        updateStatistics(
            id = id,
            pending = stats.pending,
            accepted = stats.accepted,
            rejected = stats.rejected,
            modified = stats.modified
        )
    }

    // ========================================================================
    // EU Compliance Methods
    // ========================================================================

    /**
     * Mark session as submitted for approval
     */
    fun markSubmittedForApproval(id: UUID, submitterId: String): Boolean = transaction {
        val now = Clock.System.now()
        val updated = CurationSessions.update({ CurationSessions.id eq id }) {
            it[submittedForApproval] = true
            it[submittedAt] = now
            it[submittedBy] = submitterId
            it[updatedAt] = now
        }
        updated > 0
    }

    /**
     * Reset submission (when returned for revision)
     */
    fun resetSubmission(id: UUID): Boolean = transaction {
        val now = Clock.System.now()
        val updated = CurationSessions.update({ CurationSessions.id eq id }) {
            it[submittedForApproval] = false
            it[submittedAt] = null
            it[status] = CurationSessionStatus.IN_PROGRESS.value
            it[updatedAt] = now
        }
        updated > 0
    }

    private fun ResultRow.toEntity() = CurationSessionEntity(
        id = this[CurationSessions.id].value,
        scanId = this[CurationSessions.scanId],
        status = this[CurationSessions.status],
        totalItems = this[CurationSessions.totalItems],
        pendingItems = this[CurationSessions.pendingItems],
        acceptedItems = this[CurationSessions.acceptedItems],
        rejectedItems = this[CurationSessions.rejectedItems],
        modifiedItems = this[CurationSessions.modifiedItems],
        curatorId = this[CurationSessions.curatorId],
        curatorName = this[CurationSessions.curatorName],
        approvedBy = this[CurationSessions.approvedBy],
        approverName = this[CurationSessions.approverName],
        approverRole = this[CurationSessions.approverRole],
        approvedAt = this[CurationSessions.approvedAt]?.toString(),
        approvalComment = this[CurationSessions.approvalComment],
        submittedForApproval = this[CurationSessions.submittedForApproval],
        submittedAt = this[CurationSessions.submittedAt]?.toString(),
        submittedBy = this[CurationSessions.submittedBy],
        createdAt = this[CurationSessions.createdAt].toString(),
        updatedAt = this[CurationSessions.updatedAt].toString()
    )
}

data class CurationSessionEntity(
    val id: UUID,
    val scanId: UUID,
    val status: String,
    val totalItems: Int,
    val pendingItems: Int,
    val acceptedItems: Int,
    val rejectedItems: Int,
    val modifiedItems: Int,
    // Curator info
    val curatorId: String,
    val curatorName: String?,
    // Approval info
    val approvedBy: String?,
    val approverName: String?,
    val approverRole: String?,
    val approvedAt: String?,
    val approvalComment: String?,
    // EU Compliance fields
    val submittedForApproval: Boolean = false,
    val submittedAt: String? = null,
    val submittedBy: String? = null,
    val createdAt: String,
    val updatedAt: String
)
