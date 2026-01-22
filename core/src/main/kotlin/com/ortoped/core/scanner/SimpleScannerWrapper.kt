package com.ortoped.core.scanner

import com.ortoped.core.demo.DemoDataProvider
import com.ortoped.core.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import java.io.File
import java.time.Instant
import java.util.ServiceLoader

private val logger = KotlinLogging.logger {}

/**
 * Scanner wrapper that integrates with ORT Analyzer for real dependency scanning
 */
class SimpleScannerWrapper(
    private val sourceCodeScanner: SourceCodeScanner? = null
) {

    suspend fun scanProject(
        projectDir: File,
        demoMode: Boolean = false,
        enableSourceScan: Boolean = false
    ): ScanResult {
        logger.info { "Starting scan for project: ${projectDir.absolutePath}" }
        logger.info { "Demo mode: $demoMode" }
        logger.info { "Source scan: $enableSourceScan" }

        if (demoMode) {
            logger.info { "Using demo data to showcase AI license resolution" }
            return DemoDataProvider.generateDemoScanResult()
        }

        val (ortResult, baseResult) = performRealScan(projectDir)

        // Enhance with source code scanner if enabled
        if (enableSourceScan && sourceCodeScanner != null) {
            return enhanceWithSourceScan(ortResult, baseResult)
        }

        return baseResult
    }

    private fun performRealScan(projectDir: File): Pair<org.ossreviewtoolkit.model.OrtResult?, ScanResult> {
        logger.info { "Starting real ORT scan..." }

        // Step 1: Configure the analyzer with sensible defaults
        val analyzerConfig = AnalyzerConfiguration(
            allowDynamicVersions = true,  // Handle version ranges
            skipExcluded = false          // Include all dependencies
        )

        val analyzer = Analyzer(analyzerConfig)

        // Step 2: Find all managed files (package manager manifests)
        val packageManagers = ServiceLoader.load(PackageManagerFactory::class.java).toList()
        logger.info { "Discovered ${packageManagers.size} package managers." }

        val managedFileInfo = analyzer.findManagedFiles(
            absoluteProjectPath = projectDir.canonicalFile,
            packageManagers = packageManagers
        )

        if (managedFileInfo.managedFiles.isEmpty()) {
            logger.warn { "No package manager files found in ${projectDir.absolutePath}. Listing all files found:" }
            val allFiles = projectDir.walkTopDown().toList()
            allFiles.forEach { file ->
                logger.warn { "  - ${file.relativeTo(projectDir)}" }
            }
            
            return null to createEmptyScanResult(projectDir)
        }

        logger.info { "Found ${managedFileInfo.managedFiles.size} package manager file(s):" }
        managedFileInfo.managedFiles.forEach { (manager, files) ->
            files.forEach { file ->
                logger.info { "  - [${manager::class.java.simpleName}] ${file.relativeTo(projectDir)}" }
            }
        }

        // Step 3: Run the analyzer
        logger.info { "Analyzing dependencies..." }
        val ortResult = analyzer.analyze(managedFileInfo)

        // Step 4: Extract packages and convert to OrtoPed format
        return ortResult to convertOrtResultToScanResult(ortResult, projectDir)
    }

    private fun convertOrtResultToScanResult(
        ortResult: org.ossreviewtoolkit.model.OrtResult,
        projectDir: File
    ): ScanResult {
        // Log any analyzer issues
        val result = ortResult.analyzer?.result
        if (result != null) {
            logger.info { "Analyzer Result: ${result.projects.size} projects, ${result.packages.size} packages" }
            
            // Get all issues from the analyzer result.
            val issues = result.issues.values.flatten()

            if (issues.isNotEmpty()) {
                logger.error { "Analyzer found ${issues.size} issues:" }
                issues.forEach { issue ->
                    logger.error { "  [${issue.source}] ${issue.message} (Severity: ${issue.severity})" }
                }
            }
        }

        val packages = ortResult.getPackages(omitExcluded = false)
        logger.info { "Found ${packages.size} packages" }

        val dependencies = mutableListOf<Dependency>()
        val unresolvedLicenses = mutableListOf<UnresolvedLicense>()

        packages.forEach { curatedPackage ->
            val pkg = curatedPackage.metadata
            val id = pkg.id

            // Build dependency ID in OrtoPed format: "Type:namespace:name:version"
            val dependencyId = id.toCoordinates()

            // Extract declared licenses
            val declaredLicenses = pkg.declaredLicenses.toList()

            // Check if license is resolved via SPDX expression
            val spdxExpression = pkg.declaredLicensesProcessed.spdxExpression
            val concludedLicense = pkg.concludedLicense?.toString()

            val isResolved = spdxExpression != null || concludedLicense != null

            val dependency = Dependency(
                id = dependencyId,
                name = id.name,
                version = id.version,
                declaredLicenses = declaredLicenses,
                detectedLicenses = if (spdxExpression != null) listOf(spdxExpression.toString()) else emptyList(),
                concludedLicense = concludedLicense ?: spdxExpression?.toString(),
                scope = id.type,  // Use package type as scope (Maven, NPM, etc.)
                isResolved = isResolved
            )
            dependencies.add(dependency)

            // Track unresolved licenses for AI enhancement
            if (!isResolved) {
                unresolvedLicenses.add(
                    UnresolvedLicense(
                        dependencyId = dependencyId,
                        dependencyName = id.name,
                        licenseText = null,  // Could fetch from source if needed
                        licenseUrl = pkg.homepageUrl.takeIf { it.isNotBlank() },
                        reason = determineUnresolvedReason(pkg)
                    )
                )
            }
        }

        // Create summary
        val licenseDistribution = dependencies
            .filter { it.isResolved }
            .groupingBy { it.concludedLicense ?: "NOASSERTION" }
            .eachCount()

        val summary = ScanSummary(
            totalDependencies = dependencies.size,
            resolvedLicenses = dependencies.count { it.isResolved },
            unresolvedLicenses = unresolvedLicenses.size,
            aiResolvedLicenses = 0,
            licenseDistribution = licenseDistribution
        )

        // Get project name from directory or repository info
        val projectName = ortResult.repository.vcs.url
            .takeIf { it.isNotBlank() }
            ?.substringAfterLast('/')
            ?.removeSuffix(".git")
            ?: projectDir.name

        return ScanResult(
            projectName = projectName,
            projectVersion = ortResult.labels["version"] ?: "unknown",
            scanDate = Instant.now().toString(),
            dependencies = dependencies,
            summary = summary,
            unresolvedLicenses = unresolvedLicenses,
            aiEnhanced = false
        )
    }

    private fun determineUnresolvedReason(pkg: org.ossreviewtoolkit.model.Package): String {
        return when {
            pkg.declaredLicenses.isEmpty() -> "No license declared in package metadata"
            pkg.declaredLicensesProcessed.unmapped.isNotEmpty() ->
                "License could not be mapped to SPDX: ${pkg.declaredLicensesProcessed.unmapped.joinToString()}"
            pkg.declaredLicenses.size > 1 && pkg.concludedLicense == null ->
                "Multiple licenses declared without clear SPDX expression"
            else -> "License information incomplete or ambiguous"
        }
    }

    private fun createEmptyScanResult(projectDir: File): ScanResult {
        logger.warn { "Creating empty scan result - no package managers found" }
        return ScanResult(
            projectName = projectDir.name,
            projectVersion = "unknown",
            scanDate = Instant.now().toString(),
            dependencies = emptyList(),
            summary = ScanSummary(
                totalDependencies = 0,
                resolvedLicenses = 0,
                unresolvedLicenses = 0,
                aiResolvedLicenses = 0,
                licenseDistribution = emptyMap()
            ),
            unresolvedLicenses = emptyList(),
            aiEnhanced = false
        )
    }

    /**
     * Enhance scan results with source code scanning to extract license text
     */
    private suspend fun enhanceWithSourceScan(
        ortResult: org.ossreviewtoolkit.model.OrtResult?,
        baseResult: ScanResult
    ): ScanResult {
        if (ortResult == null || sourceCodeScanner == null) {
            return baseResult
        }

        // Get IDs of packages with unresolved licenses
        val unresolvedIds = baseResult.unresolvedLicenses
            .map { org.ossreviewtoolkit.model.Identifier(it.dependencyId) }
            .toSet()

        if (unresolvedIds.isEmpty()) {
            logger.info { "No unresolved licenses to scan" }
            return baseResult.copy(sourceCodeScanned = true, packagesScanned = 0)
        }

        logger.info { "Running source code scan for ${unresolvedIds.size} unresolved packages" }

        // Run source code scanner
        val scanResults = sourceCodeScanner.scanPackages(ortResult, unresolvedIds)

        // Enhance unresolved licenses with extracted text
        val enhancedUnresolved = baseResult.unresolvedLicenses.map { unresolved ->
            val id = org.ossreviewtoolkit.model.Identifier(unresolved.dependencyId)
            val scanResult = scanResults[id]

            if (scanResult != null && scanResult.licenseFindings.isNotEmpty()) {
                val primaryFinding = scanResult.licenseFindings.first()
                unresolved.copy(
                    licenseText = primaryFinding.matchedText,
                    licenseFilePath = primaryFinding.filePath,
                    detectedByScanner = true
                )
            } else {
                unresolved
            }
        }

        val scannedCount = scanResults.values.count { it.licenseFindings.isNotEmpty() }

        logger.info { "Source scan complete. Successfully scanned $scannedCount packages" }

        return baseResult.copy(
            unresolvedLicenses = enhancedUnresolved,
            sourceCodeScanned = true,
            scannerType = "ScanCode",
            packagesScanned = scanResults.size,
            summary = baseResult.summary.copy(
                scannerResolvedLicenses = scannedCount
            )
        )
    }
}