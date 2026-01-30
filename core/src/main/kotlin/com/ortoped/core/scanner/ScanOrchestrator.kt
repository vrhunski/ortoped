package com.ortoped.core.scanner

import com.ortoped.core.ai.LicenseResolver
import com.ortoped.core.model.Dependency
import com.ortoped.core.model.ScanResult
import com.ortoped.core.model.ScanSummary
import com.ortoped.core.model.UnresolvedLicense
import com.ortoped.core.spdx.SpdxLicenseClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

private val logger = KotlinLogging.logger {}

class ScanOrchestrator(
    private val scanner: SimpleScannerWrapper = SimpleScannerWrapper(),
    private val licenseResolver: LicenseResolver = LicenseResolver(),
    private val spdxClient: SpdxLicenseClient = SpdxLicenseClient(),
    private val scannerConfig: ScannerConfig = ScannerConfig()
) {

    suspend fun scanWithAiEnhancement(
        projectDir: File,
        enableAiResolution: Boolean = true,
        enableSpdx: Boolean = false,
        enableSourceScan: Boolean = false,
        parallelAiCalls: Boolean = true,
        demoMode: Boolean = false,
        disabledPackageManagers: List<String> = emptyList()
    ): ScanResult {
        logger.info { "Starting orchestrated scan for: ${projectDir.absolutePath}" }
        logger.info { "Source code scanning: $enableSourceScan" }
        logger.info { "AI enhancement: $enableAiResolution" }
        logger.info { "SPDX enhancement: $enableSpdx" }

        // Step 1/4: Run analyzer scan
        logger.info { "Step 1/4: Running analyzer..." }

        // Step 2/4: Run source code scanner (if enabled)
        logger.info { "Step 2/4: ${if (enableSourceScan) "Running source code scanner..." else "Skipping source scanner"}" }
        val scanResult = scanner.scanProject(
            projectDir = projectDir,
            demoMode = demoMode,
            enableSourceScan = enableSourceScan && scannerConfig.enabled,
            disabledPackageManagers = disabledPackageManagers
        )

        if (scanResult.sourceCodeScanned) {
            logger.info { "Source scan complete. Scanned ${scanResult.packagesScanned} packages" }
        }

        // Step 3: AI enhancement for unresolved licenses
        logger.info { "Total dependencies found: ${scanResult.dependencies.size}" }
        logger.info { "Resolved licenses: ${scanResult.summary.resolvedLicenses}" }
        logger.info { "Unresolved licenses: ${scanResult.unresolvedLicenses.size}" }

        if (scanResult.unresolvedLicenses.isNotEmpty()) {
            logger.info { "Unresolved dependencies:" }
            scanResult.unresolvedLicenses.forEach { unresolved ->
                logger.info { "  - ${unresolved.dependencyName} (${unresolved.dependencyId}): ${unresolved.reason}" }
            }
        }
/*

        if (!enableAiResolution) {
            logger.info { "AI resolution is disabled. Returning scan result without AI enhancement." }
            return scanResult
        }
*/

        logger.info { "Step 3/4: ${if (enableAiResolution) "AI-enhancing ${scanResult.unresolvedLicenses.size} unresolved licenses..." else "Skipping AI enhancement"}" }
        val aiEnhancedDependencies =  if (enableAiResolution && scanResult.unresolvedLicenses.isNotEmpty()) {
            enhanceWithAi(
                scanResult.dependencies,
                scanResult.unresolvedLicenses,
                parallelAiCalls
            )
        } else{
            if (enableAiResolution) {
                logger.info { "AI resolution is enabled but no unresolved licenses to enhance." }
            } else {
                logger.info { "AI resolution is disabled. Skipping AI enhancement." }
            }
            scanResult.dependencies
        }

        // Calculate new summary
        val aiResolvedCount = if (enableAiResolution) {
            aiEnhancedDependencies.count {
                !it.isResolved && it.aiSuggestion != null && it.aiSuggestion.confidence == "HIGH"
            }
        } else 0

        val updatedSummary = scanResult.summary.copy(
            aiResolvedLicenses = aiResolvedCount
        )

        logger.info { "AI enhancement complete. Resolved: $aiResolvedCount licenses" }

        // Step 4: SPDX enhancement for license validation and suggestions
        val spdxEnhancedDependencies = if (enableSpdx) {
            logger.info { "Step 4/4: SPDX-enhancing dependencies..." }
            enhanceWithSpdx(aiEnhancedDependencies)
        } else {
            logger.info { "SPDX enhancement is disabled. Skipping." }
            aiEnhancedDependencies
        }

        val spdxResolvedCount = if (enableSpdx) {
            spdxEnhancedDependencies.count { it.spdxSuggestion != null }
        } else 0

        val finalSummary = updatedSummary.copy(
            spdxResolvedLicenses = spdxResolvedCount
        )

        logger.info { "SPDX enhancement complete. Validated: $spdxResolvedCount licenses" }

        return scanResult.copy(
            dependencies = spdxEnhancedDependencies,
            summary = finalSummary,
            aiEnhanced = enableAiResolution,
            spdxEnhanced = enableSpdx
        )
    }

    private suspend fun enhanceWithAi(
        dependencies: List<Dependency>,
        unresolvedLicenses: List<UnresolvedLicense>,
        parallel: Boolean
    ): List<Dependency> = coroutineScope {
        val dependencyMap = dependencies.associateBy { it.id }.toMutableMap()

        if (parallel) {
            // Parallel AI calls for better performance
            val suggestions = unresolvedLicenses.map { unresolved ->
                async {
                    try {
                        unresolved.dependencyId to licenseResolver.resolveLicense(unresolved)
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to resolve license for ${unresolved.dependencyName}" }
                        unresolved.dependencyId to null
                    }
                }
            }.awaitAll()

            suggestions.forEach { (depId, suggestion) ->
                if (suggestion != null) {
                    logger.info { "AI suggestion for $depId: ${suggestion.suggestedLicense} (${suggestion.confidence})" }
                    dependencyMap[depId]?.let { dep ->
                        dependencyMap[depId] = dep.copy(aiSuggestion = suggestion)
                    } ?: logger.warn { "Dependency not found in map: $depId" }
                } else {
                    logger.warn { "No AI suggestion returned for $depId" }
                }
            }
        } else {
            // Sequential AI calls
            unresolvedLicenses.forEach { unresolved ->
                try {
                    val suggestion = licenseResolver.resolveLicense(unresolved)
                    if (suggestion != null) {
                        logger.info { "AI suggestion for ${unresolved.dependencyId}: ${suggestion.suggestedLicense} (${suggestion.confidence})" }
                        dependencyMap[unresolved.dependencyId]?.let { dep ->
                            dependencyMap[unresolved.dependencyId] = dep.copy(aiSuggestion = suggestion)
                        } ?: logger.warn { "Dependency not found in map: ${unresolved.dependencyId}" }
                    } else {
                        logger.warn { "No AI suggestion returned for ${unresolved.dependencyId}" }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to resolve license for ${unresolved.dependencyName}" }
                }
            }
        }

        dependencyMap.values.toList()
    }

    private suspend fun enhanceWithSpdx(dependencies: List<Dependency>): List<Dependency> = coroutineScope {
        val dependencyMap = dependencies.associateBy { it.id }.toMutableMap()

        dependencies.forEach { dependency ->
            try {
                // Validate existing license against SPDX
                val declaredLicense = dependency.declaredLicenses.firstOrNull()
                if (declaredLicense != null) {
                    val spdxValidation = spdxClient.validateLicenseId(declaredLicense)
                    if (spdxValidation.isValid && spdxValidation.licenseId != null) {
                        val license = spdxClient.getLicenseById(spdxValidation.licenseId)
                        if (license != null) {
                            logger.debug { "SPDX validation for ${dependency.name}: $declaredLicense ✓" }
                            dependencyMap[dependency.id] = dependency.copy(
                                spdxValidated = true,
                                spdxLicense = license
                            )
                        }
                    } else {
                        logger.debug { "SPDX validation for ${dependency.name}: $declaredLicense ✗" }
                        // Try to find similar SPDX license as suggestion
                        val similarLicenses = spdxClient.findSimilarLicenses(declaredLicense)
                        if (similarLicenses.isNotEmpty()) {
                            val suggestion = similarLicenses.first()
                            logger.info { "SPDX suggestion for ${dependency.name}: ${suggestion.licenseId}" }
                            dependencyMap[dependency.id] = dependency.copy(
                                spdxValidated = false,
                                spdxSuggestion = suggestion
                            )
                        }
                    }
                } else {
                    // No declared license, try to find SPDX match for package name
                    val possibleLicenses = spdxClient.searchLicenses(dependency.name, 5)
                    if (possibleLicenses.isNotEmpty()) {
                        val suggestion = possibleLicenses.first()
                        logger.info { "SPDX suggestion for ${dependency.name} (no declared license): ${suggestion.licenseId}" }
                        dependencyMap[dependency.id] = dependency.copy(
                            spdxSuggestion = suggestion
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to enhance dependency ${dependency.name} with SPDX" }
            }
        }

        dependencyMap.values.toList()
    }
}