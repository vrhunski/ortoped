package com.ortoped.core.policy

import com.ortoped.core.policy.explanation.EnhancedViolation
import kotlinx.serialization.Serializable

/**
 * Complete policy evaluation report
 */
@Serializable
data class PolicyReport(
    val projectName: String,
    val projectVersion: String,
    val policyName: String,
    val policyVersion: String,
    val evaluationDate: String,
    val summary: PolicySummary,
    val violations: List<PolicyViolation>,
    val exemptedDependencies: List<ExemptedDependency>,
    val passed: Boolean,
    val aiEnhanced: Boolean = false,
    val enhancedViolations: List<EnhancedViolation>? = null
)

/**
 * Summary statistics for policy evaluation
 */
@Serializable
data class PolicySummary(
    val totalDependencies: Int,
    val evaluatedDependencies: Int,
    val exemptedDependencies: Int,
    val totalViolations: Int,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val licenseDistributionByCategory: Map<String, Int>
)

/**
 * Dependency that was exempted from policy evaluation
 */
@Serializable
data class ExemptedDependency(
    val dependencyId: String,
    val dependencyName: String,
    val exemptionReason: String,
    val approvedBy: String?
)
