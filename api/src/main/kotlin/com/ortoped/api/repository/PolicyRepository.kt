package com.ortoped.api.repository

import com.ortoped.api.model.Policies
import com.ortoped.api.model.PolicyEvaluations
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class PolicyRepository {

    fun findAll(limit: Int = 100, offset: Long = 0): List<PolicyEntity> = transaction {
        Policies.selectAll()
            .orderBy(Policies.isDefault, SortOrder.DESC)
            .orderBy(Policies.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { it.toPolicyEntity() }
    }

    fun count(): Long = transaction {
        Policies.selectAll().count()
    }

    fun findById(id: UUID): PolicyEntity? = transaction {
        Policies.selectAll()
            .where { Policies.id eq id }
            .singleOrNull()
            ?.toPolicyEntity()
    }

    fun findDefault(): PolicyEntity? = transaction {
        Policies.selectAll()
            .where { Policies.isDefault eq true }
            .singleOrNull()
            ?.toPolicyEntity()
    }

    fun create(
        name: String,
        config: String,
        isDefault: Boolean = false
    ): PolicyEntity = transaction {
        // If this is marked as default, unset other defaults
        if (isDefault) {
            Policies.update({ Policies.isDefault eq true }) {
                it[Policies.isDefault] = false
            }
        }

        val id = UUID.randomUUID()
        val now = Clock.System.now()

        Policies.insert {
            it[Policies.id] = id
            it[Policies.name] = name
            it[Policies.config] = config
            it[Policies.isDefault] = isDefault
            it[Policies.createdAt] = now
        }

        PolicyEntity(
            id = id,
            name = name,
            config = config,
            isDefault = isDefault,
            createdAt = now.toString()
        )
    }

    fun update(id: UUID, name: String? = null, config: String? = null, isDefault: Boolean? = null): Boolean = transaction {
        // If this is marked as default, unset other defaults
        if (isDefault == true) {
            Policies.update({ Policies.isDefault eq true }) {
                it[Policies.isDefault] = false
            }
        }

        val updated = Policies.update({ Policies.id eq id }) {
            name?.let { n -> it[Policies.name] = n }
            config?.let { c -> it[Policies.config] = c }
            isDefault?.let { d -> it[Policies.isDefault] = d }
        }
        updated > 0
    }

    fun delete(id: UUID): Boolean = transaction {
        Policies.deleteWhere { Policies.id eq id } > 0
    }

    // Policy Evaluations

    fun createEvaluation(
        scanId: UUID,
        policyId: UUID,
        passed: Boolean,
        report: String,
        errorCount: Int
    ): PolicyEvaluationEntity = transaction {
        val id = UUID.randomUUID()
        val now = Clock.System.now()

        PolicyEvaluations.insert {
            it[PolicyEvaluations.id] = id
            it[PolicyEvaluations.scanId] = scanId
            it[PolicyEvaluations.policyId] = policyId
            it[PolicyEvaluations.passed] = passed
            it[PolicyEvaluations.report] = report
            it[PolicyEvaluations.errorCount] = errorCount
            it[PolicyEvaluations.createdAt] = now
        }

        PolicyEvaluationEntity(
            id = id,
            scanId = scanId,
            policyId = policyId,
            passed = passed,
            report = report,
            errorCount = errorCount,
            createdAt = now.toString()
        )
    }

    fun findEvaluationsByScan(scanId: UUID): List<PolicyEvaluationEntity> = transaction {
        PolicyEvaluations.selectAll()
            .where { PolicyEvaluations.scanId eq scanId }
            .orderBy(PolicyEvaluations.createdAt, SortOrder.DESC)
            .map { it.toEvaluationEntity() }
    }

    private fun ResultRow.toPolicyEntity() = PolicyEntity(
        id = this[Policies.id].value,
        name = this[Policies.name],
        config = this[Policies.config],
        isDefault = this[Policies.isDefault],
        createdAt = this[Policies.createdAt].toString()
    )

    private fun ResultRow.toEvaluationEntity() = PolicyEvaluationEntity(
        id = this[PolicyEvaluations.id].value,
        scanId = this[PolicyEvaluations.scanId],
        policyId = this[PolicyEvaluations.policyId],
        passed = this[PolicyEvaluations.passed],
        report = this[PolicyEvaluations.report],
        errorCount = this[PolicyEvaluations.errorCount],
        createdAt = this[PolicyEvaluations.createdAt].toString()
    )
}

data class PolicyEntity(
    val id: UUID,
    val name: String,
    val config: String,
    val isDefault: Boolean,
    val createdAt: String
)

data class PolicyEvaluationEntity(
    val id: UUID,
    val scanId: UUID,
    val policyId: UUID,
    val passed: Boolean,
    val report: String,
    val errorCount: Int,
    val createdAt: String
)
