package com.ortoped.api.service

import com.ortoped.api.model.*
import com.ortoped.api.plugins.BadRequestException
import com.ortoped.api.plugins.NotFoundException
import com.ortoped.api.repository.PolicyRepository
import com.ortoped.api.repository.ScanRepository
import com.ortoped.core.model.ScanResult
import com.ortoped.core.policy.PolicyConfig
import com.ortoped.core.policy.PolicyEvaluator
import com.ortoped.core.policy.PolicyReport
import com.ortoped.core.policy.explanation.ExplanationGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val logger = KotlinLogging.logger {}

class PolicyService(
    private val policyRepository: PolicyRepository,
    private val licenseGraphService: LicenseGraphService? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun listPolicies(page: Int = 1, pageSize: Int = 20): PolicyListResponse {
        val offset = ((page - 1) * pageSize).toLong()
        val policies = policyRepository.findAll(pageSize, offset)
        val total = policyRepository.count().toInt()

        return PolicyListResponse(
            policies = policies.map { it.toResponse() },
            total = total
        )
    }

    fun getPolicy(id: String): PolicyResponse {
        val uuid = parseUUID(id)
        val policy = policyRepository.findById(uuid)
            ?: throw NotFoundException("Policy not found: $id")
        return policy.toResponse()
    }

    fun createPolicy(request: CreatePolicyRequest): PolicyResponse {
        // Validate config is valid JSON
        try {
            json.decodeFromString<PolicyConfig>(request.config)
        } catch (e: Exception) {
            throw BadRequestException("Invalid policy configuration: ${e.message}")
        }

        val policy = policyRepository.create(
            name = request.name,
            config = request.config,
            isDefault = request.isDefault
        )

        return policy.toResponse()
    }

    fun updatePolicy(id: String, request: CreatePolicyRequest): PolicyResponse {
        val uuid = parseUUID(id)
        policyRepository.findById(uuid)
            ?: throw NotFoundException("Policy not found: $id")

        // Validate config is valid JSON
        try {
            json.decodeFromString<PolicyConfig>(request.config)
        } catch (e: Exception) {
            throw BadRequestException("Invalid policy configuration: ${e.message}")
        }

        policyRepository.update(
            id = uuid,
            name = request.name,
            config = request.config,
            isDefault = request.isDefault
        )

        return policyRepository.findById(uuid)!!.toResponse()
    }

    fun deletePolicy(id: String) {
        val uuid = parseUUID(id)
        if (!policyRepository.delete(uuid)) {
            throw NotFoundException("Policy not found: $id")
        }
    }

    fun evaluatePolicy(
        scanId: String,
        policyId: String,
        scanRepository: ScanRepository
    ): PolicyEvaluationResponse {
        val scanUuid = parseUUID(scanId)
        val policyUuid = parseUUID(policyId)

        // Get scan result
        val scan = scanRepository.findById(scanUuid)
            ?: throw NotFoundException("Scan not found: $scanId")

        if (scan.status != ScanStatus.COMPLETE.value) {
            throw BadRequestException("Scan is not complete. Current status: ${scan.status}")
        }

        val resultJson = scan.result
            ?: throw NotFoundException("Scan result not available")

        // Handle potentially double-encoded JSON from previous jsonb<String> storage
        val normalizedResultJson = if (resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
            // Unescape the double-encoded JSON string
            json.decodeFromString<String>(resultJson)
        } else {
            resultJson
        }

        val scanResult = json.decodeFromString<ScanResult>(normalizedResultJson)

        // Get policy
        val policy = policyRepository.findById(policyUuid)
            ?: throw NotFoundException("Policy not found: $policyId")

        // Handle potentially double-encoded JSON from previous jsonb<String> storage
        val normalizedPolicyConfig = if (policy.config.startsWith("\"") && policy.config.endsWith("\"")) {
            json.decodeFromString<String>(policy.config)
        } else {
            policy.config
        }

        val policyConfig = json.decodeFromString<PolicyConfig>(normalizedPolicyConfig)

        // Evaluate with optional explanation generation
        logger.info { "Evaluating policy '${policy.name}' against scan $scanId" }
        val explanationGenerator = licenseGraphService?.let { graphService ->
            ExplanationGenerator(graphService.getGraph())
        }
        val evaluator = PolicyEvaluator(policyConfig, explanationGenerator)
        val report = evaluator.evaluate(scanResult)

        // Store evaluation
        val reportJson = json.encodeToString(report)
        val evaluation = policyRepository.createEvaluation(
            scanId = scanUuid,
            policyId = policyUuid,
            passed = report.passed,
            report = reportJson,
            errorCount = report.summary.errorCount
        )

        return PolicyEvaluationResponse(
            id = evaluation.id.toString(),
            scanId = scanId,
            policyId = policyId,
            passed = evaluation.passed,
            errorCount = evaluation.errorCount,
            warningCount = report.summary.warningCount,
            report = reportJson,
            createdAt = evaluation.createdAt
        )
    }

    fun getEvaluations(scanId: String): List<PolicyEvaluationResponse> {
        val uuid = parseUUID(scanId)
        val evaluations = policyRepository.findEvaluationsByScan(uuid)

        return evaluations.map { eval ->
            val report = try {
                json.decodeFromString<PolicyReport>(eval.report)
            } catch (e: Exception) {
                null
            }

            PolicyEvaluationResponse(
                id = eval.id.toString(),
                scanId = eval.scanId.toString(),
                policyId = eval.policyId.toString(),
                passed = eval.passed,
                errorCount = eval.errorCount,
                warningCount = report?.summary?.warningCount ?: 0,
                report = eval.report,
                createdAt = eval.createdAt
            )
        }
    }

    private fun parseUUID(id: String): UUID = try {
        UUID.fromString(id)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid UUID format: $id")
    }

    private fun com.ortoped.api.repository.PolicyEntity.toResponse() = PolicyResponse(
        id = id.toString(),
        name = name,
        config = config,
        isDefault = isDefault,
        createdAt = createdAt
    )
}
