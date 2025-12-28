package com.ortoped.scanner

import com.ortoped.demo.DemoDataProvider
import com.ortoped.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import java.io.File
import java.time.Instant
import java.util.ServiceLoader

private val logger = KotlinLogging.logger {}

/**
 * Scanner wrapper that integrates with ORT Analyzer for real dependency scanning
 */
class SimpleScannerWrapper {

    suspend fun scanProject(
        projectDir: File,
        demoMode: Boolean = true
    ): ScanResult {
        logger.info { "Starting scan for project: ${projectDir.absolutePath}" }
        logger.info { "Demo mode: $demoMode" }

        if (demoMode) {
            logger.info { "Using demo data to showcase AI license resolution" }
            return DemoDataProvider.generateDemoScanResult()
        }

        return performRealScan(projectDir)
    }

    private fun performRealScan(projectDir: File): ScanResult {
        logger.info { "Starting real ORT scan..." }

        // Step 1: Configure the analyzer with sensible defaults
        val analyzerConfig = AnalyzerConfiguration(
            allowDynamicVersions = true,  // Handle version ranges
            skipExcluded = false          // Include all dependencies
        )

        val analyzer = Analyzer(analyzerConfig)

        // Step 2: Find all managed files (package manager manifests)
        // Use ServiceLoader to discover all available package managers
        val allPackageManagers = ServiceLoader.load(PackageManagerFactory::class.java).toList()
        // Filter out duplicate Gradle managers - keep only "Gradle", not "Gradle Inspector"
        val packageManagers = allPackageManagers.filterNot { it.javaClass.simpleName.contains("GradleInspector") }
        logger.info { "Discovered ${packageManagers.size} package managers (filtered from ${allPackageManagers.size})" }

        val managedFileInfo = analyzer.findManagedFiles(
            absoluteProjectPath = projectDir.canonicalFile,
            packageManagers = packageManagers
        )

        if (managedFileInfo.managedFiles.isEmpty()) {
            logger.warn { "No package manager files found in ${projectDir.absolutePath}" }
            return createEmptyScanResult(projectDir)
        }

        logger.info { "Found ${managedFileInfo.managedFiles.size} package manager file(s)" }

        // Step 3: Run the analyzer
        logger.info { "Analyzing dependencies..." }
        val ortResult = analyzer.analyze(managedFileInfo)

        // Step 4: Extract packages and convert to OrtoPed format
        return convertOrtResultToScanResult(ortResult, projectDir)
    }

    private fun convertOrtResultToScanResult(
        ortResult: org.ossreviewtoolkit.model.OrtResult,
        projectDir: File
    ): ScanResult {
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
}