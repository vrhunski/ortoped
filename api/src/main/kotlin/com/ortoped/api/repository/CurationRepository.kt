package com.ortoped.api.repository

import com.ortoped.api.model.CurationStatus
import com.ortoped.api.model.Curations
import com.ortoped.api.model.PriorityLevel
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

class CurationRepository {

    fun findById(id: UUID): CurationEntity? = transaction {
        Curations.selectAll()
            .where { Curations.id eq id }
            .singleOrNull()
            ?.toEntity()
    }

    fun findBySessionId(
        sessionId: UUID,
        status: CurationStatus? = null,
        priority: PriorityLevel? = null,
        hasAiSuggestion: Boolean? = null,
        limit: Int = 100,
        offset: Long = 0,
        sortBy: String = "priority",
        sortOrder: String = "desc"
    ): List<CurationEntity> = transaction {
        var query = Curations.selectAll()
            .andWhere { Curations.sessionId eq sessionId }

        status?.let {
            query = query.andWhere { Curations.status eq it.value }
        }

        priority?.let {
            query = query.andWhere { Curations.priorityLevel eq it.value }
        }

        hasAiSuggestion?.let { has ->
            query = if (has) {
                query.andWhere { Curations.aiSuggestedLicense.isNotNull() }
            } else {
                query.andWhere { Curations.aiSuggestedLicense.isNull() }
            }
        }

        // Apply sorting
        val sortColumn = when (sortBy) {
            "name" -> Curations.dependencyName
            "status" -> Curations.status
            "confidence" -> Curations.aiConfidence
            "createdAt" -> Curations.createdAt
            else -> Curations.priorityScore // default to priority
        }

        val order = if (sortOrder.lowercase() == "asc") SortOrder.ASC else SortOrder.DESC

        // For priority, sort by level first, then score
        if (sortBy == "priority") {
            query = query
                .orderBy(Curations.priorityLevel, SortOrder.ASC) // CRITICAL < HIGH < MEDIUM < LOW
                .orderBy(Curations.priorityScore, SortOrder.DESC)
        } else {
            query = query.orderBy(sortColumn, order)
        }

        query
            .limit(limit, offset)
            .map { it.toEntity() }
    }

    fun findByDependencyId(sessionId: UUID, dependencyId: String): CurationEntity? = transaction {
        Curations.selectAll()
            .where { (Curations.sessionId eq sessionId) and (Curations.dependencyId eq dependencyId) }
            .singleOrNull()
            ?.toEntity()
    }

    fun countBySessionId(
        sessionId: UUID,
        status: CurationStatus? = null,
        priority: PriorityLevel? = null,
        hasAiSuggestion: Boolean? = null
    ): Long = transaction {
        var query = Curations.selectAll()
            .andWhere { Curations.sessionId eq sessionId }

        status?.let {
            query = query.andWhere { Curations.status eq it.value }
        }

        priority?.let {
            query = query.andWhere { Curations.priorityLevel eq it.value }
        }

        hasAiSuggestion?.let { has ->
            query = if (has) {
                query.andWhere { Curations.aiSuggestedLicense.isNotNull() }
            } else {
                query.andWhere { Curations.aiSuggestedLicense.isNull() }
            }
        }

        query.count()
    }

    fun getStatisticsBySessionId(sessionId: UUID): CurationStats = transaction {
        val counts = Curations.selectAll()
            .where { Curations.sessionId eq sessionId }
            .groupBy { it[Curations.status] }
            .mapValues { (_, rows) -> rows.size }

        CurationStats(
            total = counts.values.sum(),
            pending = counts[CurationStatus.PENDING.value] ?: 0,
            accepted = counts[CurationStatus.ACCEPTED.value] ?: 0,
            rejected = counts[CurationStatus.REJECTED.value] ?: 0,
            modified = counts[CurationStatus.MODIFIED.value] ?: 0
        )
    }

    fun create(
        sessionId: UUID,
        scanId: UUID,
        dependencyId: String,
        dependencyName: String,
        dependencyVersion: String,
        dependencyScope: String?,
        originalLicense: String?,
        declaredLicenses: String?, // JSON
        detectedLicenses: String?, // JSON
        aiSuggestedLicense: String?,
        aiConfidence: String?,
        aiReasoning: String?,
        aiAlternatives: String?, // JSON
        priorityLevel: PriorityLevel? = null,
        priorityScore: Double? = null,
        priorityFactors: String? = null // JSON
    ): CurationEntity = transaction {
        val id = UUID.randomUUID()
        val now = Clock.System.now()

        Curations.insert {
            it[Curations.id] = id
            it[Curations.sessionId] = sessionId
            it[Curations.scanId] = scanId
            it[Curations.dependencyId] = dependencyId
            it[Curations.dependencyName] = dependencyName
            it[Curations.dependencyVersion] = dependencyVersion
            it[Curations.dependencyScope] = dependencyScope
            it[Curations.originalLicense] = originalLicense
            it[Curations.declaredLicenses] = declaredLicenses
            it[Curations.detectedLicenses] = detectedLicenses
            it[Curations.aiSuggestedLicense] = aiSuggestedLicense
            it[Curations.aiConfidence] = aiConfidence
            it[Curations.aiReasoning] = aiReasoning
            it[Curations.aiAlternatives] = aiAlternatives
            it[Curations.status] = CurationStatus.PENDING.value
            it[Curations.priorityLevel] = priorityLevel?.value
            it[Curations.priorityScore] = priorityScore?.let { BigDecimal(it) }
            it[Curations.priorityFactors] = priorityFactors
            it[Curations.createdAt] = now
        }

        CurationEntity(
            id = id,
            sessionId = sessionId,
            scanId = scanId,
            dependencyId = dependencyId,
            dependencyName = dependencyName,
            dependencyVersion = dependencyVersion,
            dependencyScope = dependencyScope,
            originalLicense = originalLicense,
            declaredLicenses = declaredLicenses,
            detectedLicenses = detectedLicenses,
            aiSuggestedLicense = aiSuggestedLicense,
            aiConfidence = aiConfidence,
            aiReasoning = aiReasoning,
            aiAlternatives = aiAlternatives,
            status = CurationStatus.PENDING.value,
            curatedLicense = null,
            curatorComment = null,
            curatorId = null,
            priorityLevel = priorityLevel?.value,
            priorityScore = priorityScore,
            priorityFactors = priorityFactors,
            spdxValidated = false,
            spdxLicenseData = null,
            createdAt = now.toString(),
            curatedAt = null
        )
    }

    fun updateDecision(
        id: UUID,
        status: CurationStatus,
        curatedLicense: String?,
        curatorComment: String?,
        curatorId: String?
    ): Boolean = transaction {
        val updated = Curations.update({ Curations.id eq id }) {
            it[Curations.status] = status.value
            it[Curations.curatedLicense] = curatedLicense
            it[Curations.curatorComment] = curatorComment
            it[Curations.curatorId] = curatorId
            it[curatedAt] = Clock.System.now()
        }
        updated > 0
    }

    fun updatePriority(
        id: UUID,
        priorityLevel: PriorityLevel,
        priorityScore: Double,
        priorityFactors: String?
    ): Boolean = transaction {
        val updated = Curations.update({ Curations.id eq id }) {
            it[Curations.priorityLevel] = priorityLevel.value
            it[Curations.priorityScore] = BigDecimal(priorityScore)
            it[Curations.priorityFactors] = priorityFactors
        }
        updated > 0
    }

    fun updateSpdxValidation(
        id: UUID,
        validated: Boolean,
        spdxData: String?
    ): Boolean = transaction {
        val updated = Curations.update({ Curations.id eq id }) {
            it[spdxValidated] = validated
            it[spdxLicenseData] = spdxData
        }
        updated > 0
    }

    fun bulkUpdateStatus(
        sessionId: UUID,
        dependencyIds: List<String>,
        status: CurationStatus,
        curatedLicense: String?,
        curatorComment: String?,
        curatorId: String?
    ): Int = transaction {
        val now = Clock.System.now()
        Curations.update({
            (Curations.sessionId eq sessionId) and (Curations.dependencyId inList dependencyIds)
        }) {
            it[Curations.status] = status.value
            it[Curations.curatedLicense] = curatedLicense
            it[Curations.curatorComment] = curatorComment
            it[Curations.curatorId] = curatorId
            it[curatedAt] = now
        }
    }

    fun delete(id: UUID): Boolean = transaction {
        Curations.deleteWhere { Curations.id eq id } > 0
    }

    fun deleteBySessionId(sessionId: UUID): Int = transaction {
        Curations.deleteWhere { Curations.sessionId eq sessionId }
    }

    /**
     * Find all curations by scan ID (across all sessions)
     */
    fun findByScanId(scanId: UUID): List<CurationEntity> = transaction {
        Curations.selectAll()
            .where { Curations.scanId eq scanId }
            .map { it.toEntity() }
    }

    private fun ResultRow.toEntity() = CurationEntity(
        id = this[Curations.id].value,
        sessionId = this[Curations.sessionId],
        scanId = this[Curations.scanId],
        dependencyId = this[Curations.dependencyId],
        dependencyName = this[Curations.dependencyName],
        dependencyVersion = this[Curations.dependencyVersion],
        dependencyScope = this[Curations.dependencyScope],
        originalLicense = this[Curations.originalLicense],
        declaredLicenses = this[Curations.declaredLicenses],
        detectedLicenses = this[Curations.detectedLicenses],
        aiSuggestedLicense = this[Curations.aiSuggestedLicense],
        aiConfidence = this[Curations.aiConfidence],
        aiReasoning = this[Curations.aiReasoning],
        aiAlternatives = this[Curations.aiAlternatives],
        status = this[Curations.status],
        curatedLicense = this[Curations.curatedLicense],
        curatorComment = this[Curations.curatorComment],
        curatorId = this[Curations.curatorId],
        priorityLevel = this[Curations.priorityLevel],
        priorityScore = this[Curations.priorityScore]?.toDouble(),
        priorityFactors = this[Curations.priorityFactors],
        spdxValidated = this[Curations.spdxValidated],
        spdxLicenseData = this[Curations.spdxLicenseData],
        createdAt = this[Curations.createdAt].toString(),
        curatedAt = this[Curations.curatedAt]?.toString()
    )
}

data class CurationEntity(
    val id: UUID,
    val sessionId: UUID,
    val scanId: UUID,
    val dependencyId: String,
    val dependencyName: String,
    val dependencyVersion: String,
    val dependencyScope: String?,
    val originalLicense: String?,
    val declaredLicenses: String?, // JSON
    val detectedLicenses: String?, // JSON
    val aiSuggestedLicense: String?,
    val aiConfidence: String?,
    val aiReasoning: String?,
    val aiAlternatives: String?, // JSON
    val status: String,
    val curatedLicense: String?,
    val curatorComment: String?,
    val curatorId: String?,
    val priorityLevel: String?,
    val priorityScore: Double?,
    val priorityFactors: String?, // JSON
    val spdxValidated: Boolean,
    val spdxLicenseData: String?, // JSON
    val createdAt: String,
    val curatedAt: String?
)

data class CurationStats(
    val total: Int,
    val pending: Int,
    val accepted: Int,
    val rejected: Int,
    val modified: Int
)
