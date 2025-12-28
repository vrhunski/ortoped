package com.ortoped.scanner

import com.ortoped.ai.LicenseResolver
import com.ortoped.model.Dependency
import com.ortoped.model.ScanResult
import com.ortoped.model.ScanSummary
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

private val logger = KotlinLogging.logger {}

class ScanOrchestrator(
    private val scanner: SimpleScannerWrapper = SimpleScannerWrapper(),
    private val licenseResolver: LicenseResolver = LicenseResolver(),
    private val scannerConfig: ScannerConfig = ScannerConfig()
) {

    suspend fun scanWithAiEnhancement(
        projectDir: File,
        enableAiResolution: Boolean = true,
        enableSourceScan: Boolean = false,
        parallelAiCalls: Boolean = true,
        demoMode: Boolean = false
    ): ScanResult {
        logger.info { "Starting orchestrated scan for: ${projectDir.absolutePath}" }
        logger.info { "Source code scanning: $enableSourceScan" }
        logger.info { "AI enhancement: $enableAiResolution" }

        // Step 1/4: Run analyzer scan
        logger.info { "Step 1/4: Running analyzer..." }

        // Step 2/4: Run source code scanner (if enabled)
        logger.info { "Step 2/4: ${if (enableSourceScan) "Running source code scanner..." else "Skipping source scanner"}" }
        val scanResult = scanner.scanProject(
            projectDir = projectDir,
            demoMode = demoMode,
            enableSourceScan = enableSourceScan && scannerConfig.enabled
        )

        if (scanResult.sourceCodeScanned) {
            logger.info { "Source scan complete. Scanned ${scanResult.packagesScanned} packages" }
        }

        // Step 3: AI enhancement for unresolved licenses
        if (!enableAiResolution || scanResult.unresolvedLicenses.isEmpty()) {
            logger.info { "Skipping AI enhancement. Unresolved licenses: ${scanResult.unresolvedLicenses.size}" }
            return scanResult
        }

        logger.info { "Step 3/3: AI-enhancing ${scanResult.unresolvedLicenses.size} unresolved licenses..." }
        val enhancedDependencies = enhanceWithAi(
            scanResult.dependencies,
            scanResult.unresolvedLicenses,
            parallelAiCalls
        )

        // Calculate new summary
        val aiResolvedCount = enhancedDependencies.count {
            !it.isResolved && it.aiSuggestion != null && it.aiSuggestion.confidence == "HIGH"
        }

        val updatedSummary = scanResult.summary.copy(
            aiResolvedLicenses = aiResolvedCount
        )

        logger.info { "AI enhancement complete. Resolved: $aiResolvedCount licenses" }

        return scanResult.copy(
            dependencies = enhancedDependencies,
            summary = updatedSummary,
            aiEnhanced = true
        )
    }

    private suspend fun enhanceWithAi(
        dependencies: List<Dependency>,
        unresolvedLicenses: List<com.ortoped.model.UnresolvedLicense>,
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
                suggestion?.let {
                    dependencyMap[depId] = dependencyMap[depId]!!.copy(aiSuggestion = it)
                }
            }
        } else {
            // Sequential AI calls
            unresolvedLicenses.forEach { unresolved ->
                try {
                    val suggestion = licenseResolver.resolveLicense(unresolved)
                    suggestion?.let {
                        dependencyMap[unresolved.dependencyId] =
                            dependencyMap[unresolved.dependencyId]!!.copy(aiSuggestion = it)
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to resolve license for ${unresolved.dependencyName}" }
                }
            }
        }

        dependencyMap.values.toList()
    }
}