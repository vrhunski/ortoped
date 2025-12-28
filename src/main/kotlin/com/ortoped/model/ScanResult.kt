package com.ortoped.model

import kotlinx.serialization.Serializable

@Serializable
data class ScanResult(
    val projectName: String,
    val projectVersion: String,
    val scanDate: String,
    val dependencies: List<Dependency>,
    val summary: ScanSummary,
    val unresolvedLicenses: List<UnresolvedLicense>,
    val aiEnhanced: Boolean = false,
    // Scanner integration fields
    val sourceCodeScanned: Boolean = false,
    val scannerType: String? = null,
    val packagesScanned: Int = 0
)

@Serializable
data class Dependency(
    val id: String,
    val name: String,
    val version: String,
    val declaredLicenses: List<String>,
    val detectedLicenses: List<String>,
    val concludedLicense: String?,
    val scope: String,
    val isResolved: Boolean,
    val aiSuggestion: LicenseSuggestion? = null
)

@Serializable
data class UnresolvedLicense(
    val dependencyId: String,
    val dependencyName: String,
    val licenseText: String? = null,
    val licenseUrl: String? = null,
    val reason: String,
    // Scanner integration fields
    val licenseFilePath: String? = null,
    val detectedByScanner: Boolean = false
)

@Serializable
data class LicenseSuggestion(
    val suggestedLicense: String,
    val confidence: String, // HIGH, MEDIUM, LOW
    val reasoning: String,
    val spdxId: String?,
    val alternatives: List<String> = emptyList()
)

@Serializable
data class ScanSummary(
    val totalDependencies: Int,
    val resolvedLicenses: Int,
    val unresolvedLicenses: Int,
    val aiResolvedLicenses: Int = 0,
    val licenseDistribution: Map<String, Int>,
    // Scanner integration field
    val scannerResolvedLicenses: Int = 0
)