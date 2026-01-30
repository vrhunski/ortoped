package com.ortoped.api.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import java.util.UUID

// =====================
// Project Models
// =====================

@Serializable
data class CreateProjectRequest(
    val name: String,
    val repositoryUrl: String? = null,
    val defaultBranch: String = "main",
    val policyId: String? = null
)

@Serializable
data class ProjectResponse(
    val id: String,
    val name: String,
    val repositoryUrl: String?,
    val defaultBranch: String,
    val policyId: String?,
    val createdAt: String
)

@Serializable
data class ProjectListResponse(
    val projects: List<ProjectResponse>,
    val total: Int
)

// =====================
// Scan Models
// =====================

@Serializable
data class TriggerScanRequest(
    val projectId: String,
    val enableAi: Boolean = true,
    val enableSpdx: Boolean = false,
    val enableSourceScan: Boolean = false,
    val parallelAiCalls: Boolean = true,
    val demoMode: Boolean = false,
    val disabledPackageManagers: List<String> = emptyList(),
    val branch: String? = null,
    val tag: String? = null,
    val commit: String? = null,
    // Analyzer Configuration
    val allowDynamicVersions: Boolean = true,
    val skipExcluded: Boolean = true,
    // Cache Configuration
    val useCache: Boolean? = true,      // Enable result caching (default: true)
    val cacheTtlHours: Int? = 168,      // Cache TTL in hours (default: 1 week)
    val forceRescan: Boolean? = false   // Bypass cache and force fresh scan
)

@Serializable
data class ScanStatusResponse(
    val id: String,
    val projectId: String?,
    val status: String,
    val enableAi: Boolean,
    val startedAt: String?,
    val completedAt: String?,
    val errorMessage: String?,
    val createdAt: String
)

@Serializable
data class ScanSummaryResponse(
    val id: String,
    val projectId: String?,
    val status: String,
    val totalDependencies: Int,
    val resolvedLicenses: Int,
    val unresolvedLicenses: Int,
    val aiResolvedLicenses: Int,
    val spdxResolvedLicenses: Int,
    val startedAt: String?,
    val completedAt: String?
)

@Serializable
data class ScanListResponse(
    val scans: List<ScanSummaryResponse>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)

@Serializable
data class DependencyResponse(
    val id: String,
    val name: String,
    val version: String,
    val declaredLicenses: List<String>,
    val detectedLicenses: List<String>,
    val concludedLicense: String?,
    val scope: String,
    val isResolved: Boolean,
    val aiSuggestion: AiSuggestionResponse?,
    val spdxValidated: Boolean,
    val spdxLicense: SpdxLicenseInfo?,
    val spdxSuggestion: SpdxLicenseInfo?
)

@Serializable
data class AiSuggestionResponse(
    val suggestedLicense: String,
    val confidence: String,
    val reasoning: String,
    val spdxId: String?,
    val alternatives: List<String>
)

@Serializable
data class DependencyListResponse(
    val dependencies: List<DependencyResponse>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)

// =====================
// Package Manager Models
// =====================

@Serializable
data class PackageManagerInfo(
    val name: String,
    val displayName: String,
    val description: String,
    val filePatterns: List<String>,
    val category: String
)

@Serializable
data class PackageManagerListResponse(
    val packageManagers: List<PackageManagerInfo>,
    val categories: List<String>
)

// =====================
// ORT Config Export Models
// =====================

@Serializable
data class OrtConfigExport(
    val configYml: String,
    val filename: String = "ort-config.yml"
)

@Serializable
data class AnalyzerConfigResponse(
    val allowDynamicVersions: Boolean,
    val skipExcluded: Boolean,
    val disabledPackageManagers: List<String>
)

// =====================
// Policy Models
// =====================

@Serializable
data class CreatePolicyRequest(
    val name: String,
    val config: String, // JSON string of PolicyConfig
    val isDefault: Boolean = false
)

@Serializable
data class PolicyResponse(
    val id: String,
    val name: String,
    val config: String, // JSON string of PolicyConfig
    val isDefault: Boolean,
    val createdAt: String
)

@Serializable
data class PolicyListResponse(
    val policies: List<PolicyResponse>,
    val total: Int
)

@Serializable
data class EvaluatePolicyRequest(
    val policyId: String
)

@Serializable
data class PolicyEvaluationResponse(
    val id: String,
    val scanId: String,
    val policyId: String,
    val passed: Boolean,
    val errorCount: Int,
    val warningCount: Int,
    val report: String, // JSON string of PolicyReport
    val createdAt: String
)

// =====================
// SBOM Models
// =====================

@Serializable
data class GenerateSbomRequest(
    val format: String = "cyclonedx-json", // cyclonedx-json, cyclonedx-xml, spdx-json, spdx-tv
    val includeAiSuggestions: Boolean = true
)

@Serializable
data class SbomResponse(
    val content: String,
    val format: String,
    val filename: String
)

// =====================
// Auth Models
// =====================

@Serializable
data class CreateApiKeyRequest(
    val name: String
)

@Serializable
data class ApiKeyResponse(
    val id: String,
    val name: String,
    val keyPrefix: String,
    val apiKey: String?, // Only returned on creation
    val createdAt: String
)

@Serializable
data class ApiKeyListResponse(
    val apiKeys: List<ApiKeyResponse>,
    val total: Int
)

// =====================
// Pagination
// =====================

@Serializable
data class PaginationParams(
    val page: Int = 1,
    val pageSize: Int = 20
) {
    val offset: Int get() = (page - 1) * pageSize

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
