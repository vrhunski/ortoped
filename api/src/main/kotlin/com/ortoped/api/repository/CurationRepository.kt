package com.ortoped.api.repository

import com.ortoped.api.model.*
import com.ortoped.api.service.ApprovalRecordEntity
import com.ortoped.api.service.AuditLogEntity
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

class CurationRepository {

    private val json = Json { encodeDefaults = true }

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
        curatedAt = this[Curations.curatedAt]?.toString(),
        // EU Compliance fields
        requiresJustification = this[Curations.requiresJustification],
        justificationComplete = this[Curations.justificationComplete],
        blockingPolicyRule = this[Curations.blockingPolicyRule],
        isOrLicense = this[Curations.isOrLicense],
        orLicenseOptions = this.getOrNull(OrLicenseResolutions.licenseOptions),
        orLicenseChoice = this[Curations.orLicenseChoice],
        distributionScope = this[Curations.distributionScope]
    )

    // ========================================================================
    // EU Compliance Methods
    // ========================================================================

    /**
     * Update curation with EU compliance fields
     */
    fun updateDecisionWithCompliance(
        id: UUID,
        status: CurationStatus,
        curatedLicense: String?,
        curatorComment: String?,
        curatorId: String?,
        requiresJustification: Boolean,
        justificationComplete: Boolean,
        distributionScope: String
    ): Boolean = transaction {
        val updated = Curations.update({ Curations.id eq id }) {
            it[Curations.status] = status.value
            it[Curations.curatedLicense] = curatedLicense
            it[Curations.curatorComment] = curatorComment
            it[Curations.curatorId] = curatorId
            it[Curations.requiresJustification] = requiresJustification
            it[Curations.justificationComplete] = justificationComplete
            it[Curations.distributionScope] = distributionScope
            it[curatedAt] = Clock.System.now()
        }
        updated > 0
    }

    /**
     * Update justification status
     */
    fun updateJustificationStatus(id: UUID, complete: Boolean): Boolean = transaction {
        val updated = Curations.update({ Curations.id eq id }) {
            it[justificationComplete] = complete
        }
        updated > 0
    }

    /**
     * Flag curation as OR license
     */
    fun flagAsOrLicense(id: UUID, options: List<String>): Boolean = transaction {
        val updated = Curations.update({ Curations.id eq id }) {
            it[isOrLicense] = true
        }
        updated > 0
    }

    /**
     * Resolve OR license with chosen option
     */
    fun resolveOrLicense(id: UUID, chosenLicense: String, curatorId: String): Boolean = transaction {
        val updated = Curations.update({ Curations.id eq id }) {
            it[orLicenseChoice] = chosenLicense
            it[Curations.curatorId] = curatorId
            it[curatedAt] = Clock.System.now()
        }
        updated > 0
    }

    /**
     * Count unresolved OR licenses in session
     */
    fun countUnresolvedOrLicenses(sessionId: UUID): Int = transaction {
        Curations.selectAll()
            .where { (Curations.sessionId eq sessionId) and (Curations.isOrLicense eq true) and (Curations.orLicenseChoice.isNull()) }
            .count().toInt()
    }

    /**
     * Count pending justifications in session
     */
    fun countPendingJustifications(sessionId: UUID): Int = transaction {
        Curations.selectAll()
            .where {
                (Curations.sessionId eq sessionId) and
                (Curations.requiresJustification eq true) and
                (Curations.justificationComplete eq false) and
                (Curations.status neq CurationStatus.PENDING.value)
            }
            .count().toInt()
    }

    /**
     * Find OR licenses in session
     */
    fun findOrLicenses(sessionId: UUID): List<CurationEntity> = transaction {
        Curations.selectAll()
            .where { (Curations.sessionId eq sessionId) and (Curations.isOrLicense eq true) }
            .map { it.toEntity() }
    }

    // ========================================================================
    // Justification Methods
    // ========================================================================

    /**
     * Save justification for a curation
     */
    fun saveJustification(
        curationId: UUID,
        spdxId: String,
        licenseCategory: String,
        concludedLicense: String,
        justificationType: String,
        justificationText: String,
        policyRuleId: String?,
        policyRuleName: String?,
        evidenceType: String?,
        evidenceReference: String?,
        distributionScope: String,
        curatorId: String,
        curatorName: String?,
        curatorEmail: String?
    ): JustificationResponse = transaction {
        val id = UUID.randomUUID()
        val now = Clock.System.now()

        // Delete existing justification if any
        CurationJustifications.deleteWhere { CurationJustifications.curationId eq curationId }

        CurationJustifications.insert {
            it[CurationJustifications.id] = id
            it[CurationJustifications.curationId] = curationId
            it[CurationJustifications.spdxId] = spdxId
            it[CurationJustifications.licenseCategory] = licenseCategory
            it[CurationJustifications.concludedLicense] = concludedLicense
            it[CurationJustifications.justificationType] = justificationType
            it[CurationJustifications.justificationText] = justificationText
            it[CurationJustifications.policyRuleId] = policyRuleId
            it[CurationJustifications.policyRuleName] = policyRuleName
            it[CurationJustifications.evidenceType] = evidenceType
            it[CurationJustifications.evidenceReference] = evidenceReference
            it[CurationJustifications.distributionScope] = distributionScope
            it[CurationJustifications.curatorId] = curatorId
            it[CurationJustifications.curatorName] = curatorName
            it[CurationJustifications.curatorEmail] = curatorEmail
            it[CurationJustifications.curatedAt] = now
        }

        JustificationResponse(
            id = id.toString(),
            spdxId = spdxId,
            licenseCategory = licenseCategory,
            concludedLicense = concludedLicense,
            justificationType = justificationType,
            justificationText = justificationText,
            policyRuleId = policyRuleId,
            policyRuleName = policyRuleName,
            evidenceType = evidenceType,
            evidenceReference = evidenceReference,
            distributionScope = distributionScope,
            curatorId = curatorId,
            curatorName = curatorName,
            curatedAt = now.toString()
        )
    }

    /**
     * Get justification for a curation
     */
    fun getJustification(curationId: UUID): JustificationResponse? = transaction {
        CurationJustifications.selectAll()
            .where { CurationJustifications.curationId eq curationId }
            .singleOrNull()
            ?.let {
                JustificationResponse(
                    id = it[CurationJustifications.id].value.toString(),
                    spdxId = it[CurationJustifications.spdxId],
                    licenseCategory = it[CurationJustifications.licenseCategory],
                    concludedLicense = it[CurationJustifications.concludedLicense],
                    justificationType = it[CurationJustifications.justificationType],
                    justificationText = it[CurationJustifications.justificationText],
                    policyRuleId = it[CurationJustifications.policyRuleId],
                    policyRuleName = it[CurationJustifications.policyRuleName],
                    evidenceType = it[CurationJustifications.evidenceType],
                    evidenceReference = it[CurationJustifications.evidenceReference],
                    distributionScope = it[CurationJustifications.distributionScope],
                    curatorId = it[CurationJustifications.curatorId],
                    curatorName = it[CurationJustifications.curatorName],
                    curatedAt = it[CurationJustifications.curatedAt].toString(),
                    justificationHash = it[CurationJustifications.justificationHash]
                )
            }
    }

    // ========================================================================
    // Approval Methods
    // ========================================================================

    /**
     * Create approval record when session submitted
     */
    fun createApprovalRecord(
        sessionId: UUID,
        submitterId: String,
        submitterName: String?
    ) = transaction {
        val id = UUID.randomUUID()
        val now = Clock.System.now()

        // Delete existing if any
        CurationApprovals.deleteWhere { CurationApprovals.sessionId eq sessionId }

        CurationApprovals.insert {
            it[CurationApprovals.id] = id
            it[CurationApprovals.sessionId] = sessionId
            it[CurationApprovals.submitterId] = submitterId
            it[CurationApprovals.submitterName] = submitterName
            it[CurationApprovals.submittedAt] = now
            it[CurationApprovals.createdAt] = now
        }
    }

    /**
     * Get approval record for session
     */
    fun getApprovalRecord(sessionId: UUID): ApprovalRecordEntity? = transaction {
        CurationApprovals.selectAll()
            .where { CurationApprovals.sessionId eq sessionId }
            .singleOrNull()
            ?.let {
                ApprovalRecordEntity(
                    id = it[CurationApprovals.id].value,
                    sessionId = it[CurationApprovals.sessionId],
                    submitterId = it[CurationApprovals.submitterId],
                    submitterName = it[CurationApprovals.submitterName],
                    submittedAt = it[CurationApprovals.submittedAt].toString(),
                    approverId = it[CurationApprovals.approverId],
                    approverName = it[CurationApprovals.approverName],
                    approverRole = it[CurationApprovals.approverRole],
                    decision = it[CurationApprovals.decision],
                    decisionComment = it[CurationApprovals.decisionComment],
                    decidedAt = it[CurationApprovals.decidedAt]?.toString(),
                    returnReason = it[CurationApprovals.returnReason],
                    revisionItems = it[CurationApprovals.revisionItems]
                )
            }
    }

    /**
     * Update approval decision
     */
    fun updateApprovalDecision(
        sessionId: UUID,
        approverId: String,
        approverName: String?,
        approverRole: String?,
        decision: String,
        comment: String?,
        returnReason: String?,
        revisionItems: String?
    ): Boolean = transaction {
        val updated = CurationApprovals.update({ CurationApprovals.sessionId eq sessionId }) {
            it[CurationApprovals.approverId] = approverId
            it[CurationApprovals.approverName] = approverName
            it[CurationApprovals.approverRole] = approverRole
            it[CurationApprovals.decision] = decision
            it[CurationApprovals.decisionComment] = comment
            it[CurationApprovals.decidedAt] = Clock.System.now()
            it[CurationApprovals.returnReason] = returnReason
            it[CurationApprovals.revisionItems] = revisionItems
        }
        updated > 0
    }

    // ========================================================================
    // OR License Resolution Methods
    // ========================================================================

    /**
     * Create OR license resolution record
     */
    fun createOrLicenseResolution(
        curationId: UUID,
        originalExpression: String,
        options: List<String>
    ) = transaction {
        val id = UUID.randomUUID()

        OrLicenseResolutions.insert {
            it[OrLicenseResolutions.id] = id
            it[OrLicenseResolutions.curationId] = curationId
            it[OrLicenseResolutions.originalExpression] = originalExpression
            it[OrLicenseResolutions.licenseOptions] = json.encodeToString(options)
        }
    }

    /**
     * Update OR license resolution
     */
    fun updateOrLicenseResolution(
        curationId: UUID,
        chosenLicense: String,
        choiceReason: String,
        resolvedBy: String
    ): Boolean = transaction {
        val updated = OrLicenseResolutions.update({ OrLicenseResolutions.curationId eq curationId }) {
            it[OrLicenseResolutions.chosenLicense] = chosenLicense
            it[OrLicenseResolutions.choiceReason] = choiceReason
            it[OrLicenseResolutions.resolvedBy] = resolvedBy
            it[OrLicenseResolutions.resolvedAt] = Clock.System.now()
            it[OrLicenseResolutions.isResolved] = true
        }
        updated > 0
    }

    // ========================================================================
    // Audit Log Methods
    // ========================================================================

    /**
     * Log audit event
     */
    fun logAuditEvent(
        entityType: String,
        entityId: UUID,
        action: String,
        actorId: String,
        actorRole: String,
        previousState: String?,
        newState: String,
        changeSummary: String,
        ipAddress: String? = null,
        userAgent: String? = null
    ) = transaction {
        val id = UUID.randomUUID()
        val now = Clock.System.now()

        AuditLogs.insert {
            it[AuditLogs.id] = id
            it[AuditLogs.entityType] = entityType
            it[AuditLogs.entityId] = entityId
            it[AuditLogs.action] = action
            it[AuditLogs.actorId] = actorId
            it[AuditLogs.actorRole] = actorRole
            it[AuditLogs.previousState] = previousState
            it[AuditLogs.newState] = newState
            it[AuditLogs.changeSummary] = changeSummary
            it[AuditLogs.ipAddress] = ipAddress
            it[AuditLogs.userAgent] = userAgent
            it[AuditLogs.createdAt] = now
        }
    }

    /**
     * Get audit logs
     */
    fun getAuditLogs(
        entityType: String?,
        entityId: UUID?,
        actorId: String?,
        action: String?,
        limit: Int = 50,
        offset: Long = 0
    ): List<AuditLogEntity> = transaction {
        var query = AuditLogs.selectAll()

        entityType?.let { query = query.andWhere { AuditLogs.entityType eq it } }
        entityId?.let { query = query.andWhere { AuditLogs.entityId eq it } }
        actorId?.let { query = query.andWhere { AuditLogs.actorId eq it } }
        action?.let { query = query.andWhere { AuditLogs.action eq it } }

        query.orderBy(AuditLogs.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map {
                AuditLogEntity(
                    id = it[AuditLogs.id].value,
                    entityType = it[AuditLogs.entityType],
                    entityId = it[AuditLogs.entityId],
                    action = it[AuditLogs.action],
                    actorId = it[AuditLogs.actorId],
                    actorRole = it[AuditLogs.actorRole],
                    previousState = it[AuditLogs.previousState],
                    newState = it[AuditLogs.newState],
                    changeSummary = it[AuditLogs.changeSummary],
                    createdAt = it[AuditLogs.createdAt].toString()
                )
            }
    }

    /**
     * Count audit logs
     */
    fun countAuditLogs(
        entityType: String?,
        entityId: UUID?,
        actorId: String?,
        action: String?
    ): Long = transaction {
        var query = AuditLogs.selectAll()

        entityType?.let { query = query.andWhere { AuditLogs.entityType eq it } }
        entityId?.let { query = query.andWhere { AuditLogs.entityId eq it } }
        actorId?.let { query = query.andWhere { AuditLogs.actorId eq it } }
        action?.let { query = query.andWhere { AuditLogs.action eq it } }

        query.count()
    }
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
    val curatedAt: String?,
    // EU Compliance fields
    val requiresJustification: Boolean = true,
    val justificationComplete: Boolean = false,
    val blockingPolicyRule: String? = null,
    val isOrLicense: Boolean = false,
    val orLicenseOptions: String? = null, // JSON
    val orLicenseChoice: String? = null,
    val distributionScope: String? = "BINARY"
)

data class CurationStats(
    val total: Int,
    val pending: Int,
    val accepted: Int,
    val rejected: Int,
    val modified: Int
)
