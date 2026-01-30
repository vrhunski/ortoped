package com.ortoped.core.scanner

import com.ortoped.core.demo.DemoDataProvider
import com.ortoped.core.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
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
        enableSourceScan: Boolean = true,
        disabledPackageManagers: List<String> = emptyList(),
        allowDynamicVersions: Boolean = true,
        skipExcluded: Boolean = true
    ): ScanResult {
        logger.info { "Starting scan for project: ${projectDir.absolutePath}" }
        logger.info { "Demo mode: $demoMode" }
        logger.info { "Source scan: $enableSourceScan" }
        logger.info { "Allow dynamic versions: $allowDynamicVersions" }
        logger.info { "Skip excluded: $skipExcluded" }

        if (demoMode) {
            logger.info { "Using demo data to showcase AI license resolution" }
            return DemoDataProvider.generateDemoScanResult()
        }

        val (ortResult, baseResult) = performRealScan(
            projectDir,
            disabledPackageManagers,
            allowDynamicVersions,
            skipExcluded
        )

        // Enhance with source code scanner if enabled
        if (enableSourceScan) {
            if (sourceCodeScanner != null) {
                return enhanceWithSourceScan(ortResult, baseResult)
            } else {
                logger.warn { "Source scan requested but SourceCodeScanner is not configured. Skipping source scan." }
            }
        }

        return baseResult
    }

    private fun performRealScan(
        projectDir: File,
        disabledPackageManagers: List<String> = emptyList(),
        allowDynamicVersions: Boolean = true,
        skipExcluded: Boolean = true
    ): Pair<org.ossreviewtoolkit.model.OrtResult?, ScanResult> {
        logger.info { "Starting real ORT scan..." }

        // Workaround for NPM cache permission issues
        configureNpmCache(projectDir)

        // Ensure lock files exist for NPM projects
        ensureNpmLockFiles(projectDir)

        // Step 1: Configure the analyzer with user-specified settings
        val analyzerConfig = AnalyzerConfiguration(
            allowDynamicVersions = allowDynamicVersions,  // Handle version ranges - configurable
            skipExcluded = skipExcluded,                  // Skip excluded dependencies - configurable
            disabledPackageManagers = disabledPackageManagers,
            packageManagers = mapOf(
                "Npm" to PackageManagerConfiguration() // Explicitly configure Npm with default/empty settings
            )
        )

        val analyzer = Analyzer(analyzerConfig)

        // Step 2: Find all managed files (package manager manifests)
        val allPackageManagers = ServiceLoader.load(PackageManagerFactory::class.java).toList()
        logger.info { "Discovered ${allPackageManagers.size} package managers." }
        
        // Filter out disabled package managers BEFORE passing to findManagedFiles
        val disabledLowercase = disabledPackageManagers.map { it.lowercase() }.toSet()
        val enabledPackageManagers = if (disabledLowercase.isNotEmpty()) {
            allPackageManagers.filter { factory ->
                val factoryName = factory::class.java.simpleName.lowercase()
                    .removeSuffix("factory")
                    .removeSuffix("packagemanager")
                val isDisabled = disabledLowercase.any { disabled ->
                    factoryName.contains(disabled) || disabled.contains(factoryName)
                }
                if (isDisabled) {
                    logger.info { "Disabling package manager: ${factory::class.java.simpleName}" }
                }
                !isDisabled
            }
        } else {
            allPackageManagers
        }
        
        logger.info { "Using ${enabledPackageManagers.size} package managers (${allPackageManagers.size - enabledPackageManagers.size} disabled)" }

        val managedFileInfo = analyzer.findManagedFiles(
            absoluteProjectPath = projectDir.canonicalFile,
            packageManagers = enabledPackageManagers
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

        // Step 3: Run the analyzer with error handling for missing references
        logger.info { "Analyzing dependencies..." }
        val ortResult = try {
            analyzer.analyze(managedFileInfo)
        } catch (e: IllegalArgumentException) {
            // Handle "references do not actually refer to packages" error
            if (e.message?.contains("do not actually refer to packages") == true) {
                logger.warn { "Analyzer found unresolved package references: ${e.message}" }
                logger.warn { "This typically happens when dependencies are listed but not installed." }
                
                // Try to install dependencies and retry
                logger.info { "Attempting to install dependencies with 'npm install'..." }
                val installSuccess = tryInstallNpmDependencies(projectDir)
                
                if (installSuccess) {
                    logger.info { "Dependencies installed, retrying analysis..." }
                    try {
                        analyzer.analyze(managedFileInfo)
                    } catch (e2: Exception) {
                        logger.warn { "Analysis still failed after npm install: ${e2.message}" }
                        logger.warn { "Falling back to direct package.json parsing..." }
                        return null to parsePackageJsonFallback(projectDir, e2.message)
                    }
                } else {
                    logger.warn { "Could not install dependencies. Falling back to direct package.json parsing..." }
                    return null to parsePackageJsonFallback(projectDir, e.message)
                }
            } else {
                throw e
            }
        }

        // Step 4: Extract packages and convert to OrtoPed format
        return ortResult to convertOrtResultToScanResult(ortResult, projectDir)
    }

    private fun configureNpmCache(projectDir: File) {
        try {
            // Create a local cache directory to avoid permission issues with ~/.npm
            val cacheDir = projectDir.resolve(".ort-npm-cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val npmrc = projectDir.resolve(".npmrc")
            val config = "cache=${cacheDir.absolutePath}"
            
            if (npmrc.exists()) {
                val content = npmrc.readText()
                if (!content.contains(config)) {
                    npmrc.appendText("\n$config")
                }
            } else {
                npmrc.writeText(config)
            }
            logger.info { "Configured local NPM cache at ${cacheDir.absolutePath}" }
        } catch (e: Exception) {
            logger.warn { "Failed to configure local NPM cache: ${e.message}" }
        }
    }

    private fun ensureNpmLockFiles(projectDir: File) {
        try {
            val packageJsonFiles = projectDir.walkTopDown()
                .filter { it.name == "package.json" }
                .filter { !it.parentFile.resolve("package-lock.json").exists() &&
                        !it.parentFile.resolve("yarn.lock").exists() &&
                        !it.parentFile.resolve("pnpm-lock.yaml").exists() }
                .toList()

            if (packageJsonFiles.isNotEmpty()) {
                logger.info { "Found ${packageJsonFiles.size} package.json file(s) without lock files. Generating package-lock.json..." }
            }

            packageJsonFiles.forEach { packageJson ->
                logger.info { "Generating package-lock.json for ${packageJson.parentFile.absolutePath}" }
                try {
                    val process = ProcessBuilder("npm", "install", "--package-lock-only", "--legacy-peer-deps")
                        .directory(packageJson.parentFile)
                        .redirectErrorStream(true)
                        .start()

                    val exitCode = process.waitFor()
                    if (exitCode != 0) {
                        val output = process.inputStream.bufferedReader().readText()
                        logger.warn { "Failed to generate package-lock.json: $output" }
                    } else {
                        logger.info { "Successfully generated package-lock.json" }
                    }
                } catch (e: Exception) {
                    logger.warn { "Error running npm install: ${e.message}" }
                }
            }
        } catch (e: Exception) {
            logger.warn { "Error checking/generating lock files: ${e.message}" }
        }
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
     * Try to install NPM dependencies to resolve missing package references
     */
    private fun tryInstallNpmDependencies(projectDir: File): Boolean {
        return try {
            // Find all package.json files
            val packageJsonFiles = projectDir.walkTopDown()
                .filter { it.name == "package.json" && !it.path.contains("node_modules") }
                .toList()

            if (packageJsonFiles.isEmpty()) {
                logger.warn { "No package.json files found" }
                return false
            }

            var anySuccess = false
            packageJsonFiles.forEach { packageJson ->
                logger.info { "Running npm install in ${packageJson.parentFile.absolutePath}" }
                try {
                    val process = ProcessBuilder("npm", "install", "--legacy-peer-deps", "--ignore-scripts")
                        .directory(packageJson.parentFile)
                        .redirectErrorStream(true)
                        .start()

                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        logger.info { "npm install succeeded for ${packageJson.parentFile.name}" }
                        anySuccess = true
                    } else {
                        val output = process.inputStream.bufferedReader().readText()
                        logger.warn { "npm install failed for ${packageJson.parentFile.name}: $output" }
                    }
                } catch (e: Exception) {
                    logger.warn { "Error running npm install: ${e.message}" }
                }
            }
            anySuccess
        } catch (e: Exception) {
            logger.warn { "Failed to install NPM dependencies: ${e.message}" }
            false
        }
    }

    /**
     * Create an empty scan result with a warning message about unresolved references
     */
    private fun createEmptyScanResultWithWarning(projectDir: File, errorMessage: String): ScanResult {
        logger.warn { "Creating empty scan result due to unresolved references" }
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
            aiEnhanced = false,
            warnings = listOf("Scan completed with warnings: $errorMessage. Try running 'npm install' in the project directory first.")
        )
    }

    /**
     * Fallback parser that reads package.json files directly when ORT analyzer fails.
     * This extracts declared dependencies even when node_modules is not installed.
     */
    private fun parsePackageJsonFallback(projectDir: File, errorMessage: String?): ScanResult {
        logger.info { "Parsing package.json files directly as fallback..." }
        
        val json = Json { ignoreUnknownKeys = true }
        val dependencies = mutableListOf<Dependency>()
        val unresolvedLicenses = mutableListOf<UnresolvedLicense>()
        
        // Find all package.json files (excluding node_modules)
        val packageJsonFiles = projectDir.walkTopDown()
            .filter { it.name == "package.json" && !it.path.contains("node_modules") }
            .toList()
        
        logger.info { "Found ${packageJsonFiles.size} package.json file(s)" }
        
        packageJsonFiles.forEach { packageJson ->
            try {
                val content = packageJson.readText()
                val jsonObj = json.parseToJsonElement(content).jsonObject
                
                // Extract project license if available
                val projectLicense = jsonObj["license"]?.jsonPrimitive?.content
                
                // Parse dependencies
                listOf("dependencies", "devDependencies", "peerDependencies", "optionalDependencies").forEach { depType ->
                    jsonObj[depType]?.jsonObject?.forEach { (name, versionElement) ->
                        val version = versionElement.jsonPrimitive.content
                            .removePrefix("^")
                            .removePrefix("~")
                            .removePrefix(">=")
                            .removePrefix(">")
                            .removePrefix("<=")
                            .removePrefix("<")
                            .trim()
                        
                        val depId = "NPM::$name:$version"
                        
                        // Check if we already have this dependency
                        if (dependencies.none { it.id == depId }) {
                            dependencies.add(
                                Dependency(
                                    id = depId,
                                    name = name,
                                    version = version,
                                    declaredLicenses = emptyList(), // Unknown without node_modules
                                    detectedLicenses = emptyList(),
                                    concludedLicense = null,
                                    scope = depType,
                                    isResolved = false
                                )
                            )
                            
                            unresolvedLicenses.add(
                                UnresolvedLicense(
                                    dependencyId = depId,
                                    dependencyName = name,
                                    reason = "License unknown - dependencies not installed. Run 'npm install' for full license detection."
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn { "Failed to parse ${packageJson.absolutePath}: ${e.message}" }
            }
        }
        
        logger.info { "Extracted ${dependencies.size} dependencies from package.json files" }
        
        val summary = ScanSummary(
            totalDependencies = dependencies.size,
            resolvedLicenses = 0,
            unresolvedLicenses = unresolvedLicenses.size,
            aiResolvedLicenses = 0,
            licenseDistribution = emptyMap()
        )
        
        return ScanResult(
            projectName = projectDir.name,
            projectVersion = "unknown",
            scanDate = Instant.now().toString(),
            dependencies = dependencies,
            summary = summary,
            unresolvedLicenses = unresolvedLicenses,
            aiEnhanced = false,
            warnings = listOf(
                "Partial scan: Dependencies extracted from package.json but licenses could not be resolved.",
                "For complete license information, run 'npm install' in the project directory and scan again.",
                errorMessage ?: ""
            ).filter { it.isNotBlank() }
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