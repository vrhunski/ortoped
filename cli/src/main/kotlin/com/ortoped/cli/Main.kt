package com.ortoped.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import com.ortoped.core.cache.LocalFileCache
import com.ortoped.core.policy.PolicyYamlLoader
import com.ortoped.core.report.ReportGenerator
import com.ortoped.core.scanner.ScanOrchestrator
import com.ortoped.core.scanner.ScannerConfig
import com.ortoped.core.scanner.SimpleScannerWrapper
import com.ortoped.core.scanner.SourceCodeScanner
import com.ortoped.core.vcs.RemoteRepositoryHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import java.io.File

private val logger = KotlinLogging.logger {}

class OrtopedCli : CliktCommand(
    name = "ortoped",
    help = """
        OrtoPed - ORT Scanner with AI-powered license resolution

        A wrapper around OSS Review Toolkit (ORT) that enhances license detection
        using AI to automatically resolve unidentified licenses.
    """.trimIndent()
) {
    override fun run() = Unit
}

class InitCommand : CliktCommand(
    name = "init",
    help = "Initialize .ortoped/ directory with default configuration"
) {
    private val projectPath by option(
        "-p", "--project",
        help = "Project directory to initialize"
    ).default(".")

    override fun run() {
        val projectDir = File(projectPath).canonicalFile
        val ortopedDir = File(projectDir, ".ortoped")

        echo("Initializing OrtoPed in: ${projectDir.absolutePath}")

        // Create .ortoped/ directory
        ortopedDir.mkdirs()

        // Create curations.yml with commented examples
        val curationsFile = File(ortopedDir, "curations.yml")
        if (!curationsFile.exists()) {
            curationsFile.writeText("""
                |# OrtoPed Curations File (ORT-compatible format)
                |# Add license curations for dependencies that ORT cannot auto-detect.
                |#
                |# Example:
                |# curations:
                |#   - id: "Maven:com.example:library:1.0.0"
                |#     concluded_license: "MIT"
                |#     comment: "Verified from LICENSE file in repository"
                |#
                |#   - id: "NPM:@scope:package:2.3.4"
                |#     concluded_license: "Apache-2.0"
                |#     comment: "Confirmed by maintainer"
                |curations: []
            """.trimMargin())
            echo("  Created curations.yml")
        } else {
            echo("  curations.yml already exists, skipping")
        }

        // Create default policy.yml
        val policyFile = File(ortopedDir, "policy.yml")
        if (!policyFile.exists()) {
            policyFile.writeText("""
                |version: "1.0"
                |name: "Default OrtoPed Policy"
                |description: "Default policy that flags unknown licenses"
                |categories:
                |  permissive:
                |    description: "Permissive open source licenses"
                |    licenses:
                |      - "MIT"
                |      - "Apache-2.0"
                |      - "BSD-2-Clause"
                |      - "BSD-3-Clause"
                |      - "ISC"
                |      - "Unlicense"
                |      - "0BSD"
                |      - "CC0-1.0"
                |  copyleft:
                |    description: "Strong copyleft licenses"
                |    licenses:
                |      - "GPL-2.0-only"
                |      - "GPL-2.0-or-later"
                |      - "GPL-3.0-only"
                |      - "GPL-3.0-or-later"
                |      - "AGPL-3.0-only"
                |      - "AGPL-3.0-or-later"
                |  copyleft-limited:
                |    description: "Weak copyleft licenses with limited scope"
                |    licenses:
                |      - "LGPL-2.0-only"
                |      - "LGPL-2.1-only"
                |      - "LGPL-3.0-only"
                |      - "MPL-2.0"
                |      - "EPL-1.0"
                |      - "EPL-2.0"
                |  unknown:
                |    description: "Unknown or unresolved licenses"
                |    licenses:
                |      - "NOASSERTION"
                |      - "Unknown"
                |rules:
                |  - id: "no-unknown"
                |    name: "No Unknown Licenses"
                |    description: "All dependencies must have identified licenses"
                |    severity: "ERROR"
                |    category: "unknown"
                |    action: "DENY"
                |    message: "Dependency {{dependency}} has unresolved license - manual review required"
                |settings:
                |  aiSuggestions:
                |    acceptHighConfidence: true
                |    treatMediumAsWarning: true
                |    rejectLowConfidence: true
                |  failOn:
                |    errors: true
                |    warnings: false
            """.trimMargin())
            echo("  Created policy.yml")
        } else {
            echo("  policy.yml already exists, skipping")
        }

        // Create cache directory
        val cacheDir = File(ortopedDir, "cache")
        cacheDir.mkdirs()
        echo("  Created cache/ directory")

        echo()
        echo("OrtoPed initialized! Next steps:")
        echo("  1. Run a scan:          ortoped scan -p .")
        echo("  2. Add curations:       Edit .ortoped/curations.yml")
        echo("  3. Customize policy:    Edit .ortoped/policy.yml")
        echo("  4. Scan with AI:        ortoped scan -p . --auto-resolve")
    }
}

class ScanCommand : CliktCommand(
    name = "scan",
    help = "Scan a project for dependencies and licenses"
) {
    private val projectPath by option(
        "-p", "--project",
        help = "Project directory or Git repository URL to scan"
    ).default(".")

    private val outputFile by option(
        "-o", "--output",
        help = "Output file for JSON report"
    ).file()
        .default(File("ortoped-report.json"))

    private val enableAi by option(
        "--enable-ai",
        help = "Enable AI-powered license resolution"
    ).flag(default = false)

    private val autoResolve by option(
        "--auto-resolve",
        help = "Automatically resolve licenses using AI (implies --enable-ai)"
    ).flag(default = false)

    private val parallelAi by option(
        "--parallel-ai",
        help = "Run AI license resolution in parallel (faster)"
    ).flag(default = true)

    private val demoMode by option(
        "--demo",
        help = "Use demo mode with mock data instead of real ORT scanning"
    ).flag(default = false)

    private val consoleOutput by option(
        "--console",
        help = "Also print report to console"
    ).flag(default = true)

    // Package manager options
    private val disablePackageManagers by option(
        "--disable-package-managers",
        help = "Disable specific package managers (comma-separated list, e.g., 'Bower,NuGet')"
    ).convert { it.split(",").map { pm -> pm.trim() } }
        .default(emptyList())

    // Scanner options
    private val enableSourceScan by option(
        "--source-scan",
        help = "Enable source code scanning to extract actual license text (slower)"
    ).flag(default = false)

    private val scanCacheDir by option(
        "--scan-cache",
        help = "Directory for scanner cache"
    ).file()

    // VCS options (for remote repositories)
    private val branch by option(
        "--branch",
        help = "Git branch to checkout (for remote repositories)"
    )

    private val tag by option(
        "--tag",
        help = "Git tag to checkout (for remote repositories)"
    )

    private val commit by option(
        "--commit",
        help = "Git commit hash to checkout (for remote repositories)"
    )

    private val keepClone by option(
        "--keep-clone",
        help = "Keep cloned repository after scan (for debugging)"
    ).flag(default = false)

    // Curation options
    private val curationsFile by option(
        "--curations",
        help = "Explicit curations YAML file (overrides auto-detection)"
    ).file()

    private val noCurations by option(
        "--no-curations",
        help = "Skip auto-detection of .ortoped/curations.yml"
    ).flag(default = false)

    // Cache options
    private val noCache by option(
        "--no-cache",
        help = "Skip fast-path lockfile cache"
    ).flag(default = false)

    // Inline SBOM generation
    private val sbomFormat by option(
        "--sbom",
        help = "Generate SBOM inline: cyclonedx-json, cyclonedx-xml, spdx-json, spdx-tv"
    )

    private val sbomOutput by option(
        "--sbom-output",
        help = "Output file for inline SBOM (defaults based on format)"
    ).file()

    override fun run() = runBlocking {
        logger.info { "Starting Ortoped scan..." }

        val effectiveEnableAi = enableAi || autoResolve

        // Detect if projectPath is a remote URL or local directory
        val remoteHandler = RemoteRepositoryHandler()
        val isRemote = remoteHandler.isRemoteUrl(projectPath)

        var cleanupFunction: (() -> Unit)? = null
        val projectDir: File

        try {
            // Handle remote repository or local directory
            if (isRemote) {
                echo("Cloning remote repository: $projectPath")
                if (branch != null) echo("  Branch: $branch")
                if (tag != null) echo("  Tag: $tag")
                if (commit != null) echo("  Commit: $commit")
                echo()

                logger.info { "Remote repository detected: $projectPath" }
                val (clonedDir, cleanup) = remoteHandler.cloneRepository(
                    repoUrl = projectPath,
                    branch = branch,
                    tag = tag,
                    commit = commit
                )

                projectDir = clonedDir
                if (!keepClone) {
                    cleanupFunction = cleanup
                }

                echo("Repository cloned to: ${projectDir.absolutePath}")
                echo()
            } else {
                // Local directory
                projectDir = File(projectPath).canonicalFile
                if (!projectDir.exists()) {
                    throw IllegalArgumentException("Project directory does not exist: ${projectDir.absolutePath}")
                }
                if (!projectDir.isDirectory) {
                    throw IllegalArgumentException("Project path is not a directory: ${projectDir.absolutePath}")
                }
                logger.info { "Local directory: ${projectDir.absolutePath}" }
            }

            logger.info { "Demo Mode: $demoMode" }
            logger.info { "Project: ${projectDir.absolutePath}" }
            logger.info { "Output: ${outputFile.absolutePath}" }
            logger.info { "AI Enhancement: $effectiveEnableAi" }
            logger.info { "Source Scanning: $enableSourceScan" }

            if (demoMode) {
                echo("Running in DEMO mode - using mock data to showcase AI license resolution")
                echo()
            }

            if (enableSourceScan) {
                echo("Source code scanning ENABLED - downloading and extracting license text")
                echo("Note: This may take significantly longer as source code must be downloaded")
                echo()
            }

            // Create scanner configuration
            val scannerConfig = ScannerConfig(
                enabled = enableSourceScan,
                cacheDir = scanCacheDir ?: File(System.getProperty("user.home"), ".ortoped/scanner-cache")
            )

            // Create source code scanner if enabled
            val sourceCodeScanner = if (enableSourceScan) {
                SourceCodeScanner(scannerConfig)
            } else null

            // Create local file cache unless disabled
            val localCache = if (!noCache && !demoMode) {
                LocalFileCache.fromProject(projectDir)
            } else null

            // Resolve curations file
            val effectiveCurationsFile = when {
                noCurations -> null
                curationsFile != null -> curationsFile
                else -> null // auto-detection handled by CurationLoader in orchestrator
            }

            // Create orchestrator with scanner
            val scanner = SimpleScannerWrapper(sourceCodeScanner)
            val orchestrator = ScanOrchestrator(
                scanner = scanner,
                scannerConfig = scannerConfig,
                localCache = localCache
            )
            val reportGenerator = ReportGenerator()

            // Run scan
            val scanResult = orchestrator.scanWithAiEnhancement(
                projectDir = projectDir,
                enableAiResolution = effectiveEnableAi,
                enableSourceScan = enableSourceScan,
                parallelAiCalls = parallelAi,
                demoMode = demoMode,
                disabledPackageManagers = disablePackageManagers,
                curationsFile = effectiveCurationsFile,
                useFastPath = !noCache
            )

            // Generate reports
            reportGenerator.generateJsonReport(scanResult, outputFile)

            if (consoleOutput) {
                reportGenerator.generateConsoleReport(scanResult)
            }

            // Inline SBOM generation
            if (sbomFormat != null) {
                generateInlineSbom(scanResult)
            }

            // Auto-detect policy and evaluate
            val policyFile = File(projectDir, ".ortoped/policy.yml")
            if (policyFile.exists()) {
                echo()
                echo("Policy auto-evaluation (.ortoped/policy.yml):")
                val loader = PolicyYamlLoader()
                val config = loader.load(policyFile)
                val evaluator = com.ortoped.core.policy.PolicyEvaluator(config)
                val policyReport = evaluator.evaluate(scanResult)

                if (policyReport.passed) {
                    echo("  Policy: PASSED")
                } else {
                    echo("  Policy: FAILED (${policyReport.summary.errorCount} errors, ${policyReport.summary.warningCount} warnings)")
                    val unresolvedCount = scanResult.unresolvedLicenses.size
                    if (unresolvedCount > 0) {
                        echo("  Unresolved dependencies: $unresolvedCount")
                        echo("  Tip: Run with --auto-resolve or add curations to .ortoped/curations.yml")
                    }
                    throw CliktError("Policy evaluation failed with ${policyReport.summary.errorCount} error(s)")
                }
            }

            logger.info { "Scan completed successfully!" }
            logger.info { "Report saved to: ${outputFile.absolutePath}" }

            if (isRemote && keepClone) {
                echo()
                echo("Repository kept at: ${projectDir.absolutePath}")
            }

        } catch (e: CliktError) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Scan failed" }
            echo("Error: ${e.message}", err = true)
            throw e
        } finally {
            // Cleanup cloned repository if needed
            cleanupFunction?.invoke()
        }
    }

    private fun generateInlineSbom(scanResult: com.ortoped.core.model.ScanResult) {
        val format = when (sbomFormat) {
            "cyclonedx-json" -> com.ortoped.core.sbom.SbomFormat.CYCLONEDX_JSON
            "cyclonedx-xml" -> com.ortoped.core.sbom.SbomFormat.CYCLONEDX_XML
            "spdx-json" -> com.ortoped.core.sbom.SbomFormat.SPDX_JSON
            "spdx-tv" -> com.ortoped.core.sbom.SbomFormat.SPDX_TV
            else -> {
                echo("Unknown SBOM format: $sbomFormat", err = true)
                return
            }
        }

        val defaultOutput = File("ortoped-sbom.${format.extension}")
        val output = sbomOutput ?: defaultOutput

        val config = com.ortoped.core.sbom.SbomConfig(format = format)
        val generator: com.ortoped.core.sbom.SbomGenerator = when (format) {
            com.ortoped.core.sbom.SbomFormat.CYCLONEDX_JSON,
            com.ortoped.core.sbom.SbomFormat.CYCLONEDX_XML ->
                com.ortoped.core.sbom.CycloneDxGenerator()
            com.ortoped.core.sbom.SbomFormat.SPDX_JSON,
            com.ortoped.core.sbom.SbomFormat.SPDX_TV ->
                com.ortoped.core.sbom.SpdxGenerator()
        }

        echo()
        echo("Generating ${format.displayName} SBOM...")
        generator.generateToFile(scanResult, output, config)
        echo("SBOM saved to: ${output.absolutePath}")
    }
}

class SbomCommand : CliktCommand(
    name = "sbom",
    help = "Generate SBOM (Software Bill of Materials) from scan results"
) {
    private val inputFile by option(
        "-i", "--input",
        help = "Input JSON report file from ortoped scan"
    ).file(mustExist = true)
        .required()

    private val outputFile by option(
        "-o", "--output",
        help = "Output SBOM file"
    ).file()
        .default(File("ortoped-sbom.cdx.json"))

    private val format by option(
        "-f", "--format",
        help = "SBOM format: cyclonedx-json, cyclonedx-xml, spdx-json, spdx-tv"
    ).convert { formatString ->
        when (formatString) {
            "cyclonedx-json" -> com.ortoped.core.sbom.SbomFormat.CYCLONEDX_JSON
            "cyclonedx-xml" -> com.ortoped.core.sbom.SbomFormat.CYCLONEDX_XML
            "spdx-json" -> com.ortoped.core.sbom.SbomFormat.SPDX_JSON
            "spdx-tv" -> com.ortoped.core.sbom.SbomFormat.SPDX_TV
            else -> fail("Invalid format: $formatString")
        }
    }.default(com.ortoped.core.sbom.SbomFormat.CYCLONEDX_JSON)

    private val includeAiSuggestions by option(
        "--include-ai",
        help = "Include AI license suggestions in SBOM"
    ).flag(default = true)

    private val noAiSuggestions by option(
        "--no-ai",
        help = "Exclude AI license suggestions from SBOM"
    ).flag(default = false)

    override fun run() {
        logger.info { "Generating SBOM from: ${inputFile.absolutePath}" }

        try {
            // Load scan result
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val scanResult = json.decodeFromString<com.ortoped.core.model.ScanResult>(inputFile.readText())

            // Configure SBOM generation
            val config = com.ortoped.core.sbom.SbomConfig(
                format = format,
                includeAiSuggestions = includeAiSuggestions && !noAiSuggestions
            )

            // Select appropriate generator
            val generator: com.ortoped.core.sbom.SbomGenerator = when (format) {
                com.ortoped.core.sbom.SbomFormat.CYCLONEDX_JSON,
                com.ortoped.core.sbom.SbomFormat.CYCLONEDX_XML ->
                    com.ortoped.core.sbom.CycloneDxGenerator()
                com.ortoped.core.sbom.SbomFormat.SPDX_JSON,
                com.ortoped.core.sbom.SbomFormat.SPDX_TV ->
                    com.ortoped.core.sbom.SpdxGenerator()
            }

            // Generate SBOM
            echo("Generating ${format.displayName} SBOM...")
            generator.generateToFile(scanResult, outputFile, config)

            echo("SBOM generated successfully!")
            echo()
            echo("Output file: ${outputFile.absolutePath}")
            echo("Format: ${format.displayName}")
            echo("Components: ${scanResult.dependencies.size}")
            if (scanResult.aiEnhanced && config.includeAiSuggestions) {
                echo("AI suggestions included: Yes")
            }

            logger.info { "SBOM generated: ${outputFile.absolutePath}" }

        } catch (e: Exception) {
            logger.error(e) { "SBOM generation failed" }
            echo("Error: ${e.message}", err = true)
            throw e
        }
    }
}

class PolicyCommand : CliktCommand(
    name = "policy",
    help = "Evaluate scan results against license compliance policies"
) {
    private val inputFile by option(
        "-i", "--input",
        help = "Input JSON report file from ortoped scan"
    ).file(mustExist = true)
        .required()

    private val policyFile by option(
        "-p", "--policy",
        help = "YAML policy file (auto-detects .ortoped/policy.yml if not specified)"
    ).file()

    private val outputFile by option(
        "-o", "--output",
        help = "Output file for policy report"
    ).file()
        .default(File("ortoped-policy-report.json"))

    private val format by option(
        "-f", "--format",
        help = "Output format: json, console, both"
    ).default("both")

    private val strict by option(
        "--strict",
        help = "Fail on any violation (errors and warnings)"
    ).flag(default = false)

    private val enableAi by option(
        "--enable-ai",
        help = "Enable AI suggestions for fixing violations"
    ).flag(default = true)

    private val noConsole by option(
        "--no-console",
        help = "Suppress console output"
    ).flag(default = false)

    override fun run() = runBlocking {
        logger.info { "Starting policy evaluation..." }

        try {
            // Load scan result
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val scanResult = json.decodeFromString<com.ortoped.core.model.ScanResult>(inputFile.readText())

            // Resolve policy file: explicit > auto-detect > default
            val effectivePolicyFile = policyFile ?: run {
                val autoDetect = File(inputFile.parentFile ?: File("."), ".ortoped/policy.yml")
                if (autoDetect.exists()) {
                    logger.info { "Auto-detected policy file: ${autoDetect.absolutePath}" }
                    echo("Using auto-detected policy: ${autoDetect.absolutePath}")
                    autoDetect
                } else {
                    null
                }
            }

            // Load policy
            val loader = com.ortoped.core.policy.PolicyYamlLoader()
            var config = loader.loadOrDefault(effectivePolicyFile)

            // Apply strict mode
            if (strict) {
                config = config.copy(
                    settings = config.settings.copy(
                        failOn = com.ortoped.core.policy.FailOnSettings(errors = true, warnings = true)
                    )
                )
            }

            // Evaluate policy
            val evaluator = com.ortoped.core.policy.PolicyEvaluator(config)
            var report = evaluator.evaluate(scanResult)

            // Add AI suggestions if enabled
            if (enableAi && report.violations.isNotEmpty()) {
                echo("Generating AI suggestions for ${report.violations.size} violations...")
                val advisor = com.ortoped.core.policy.ai.PolicyAiAdvisor()
                val fixes = advisor.suggestFixes(report.violations)

                // Update violations with AI suggestions
                val updatedViolations = report.violations.map { violation ->
                    fixes[violation.dependencyId]?.let {
                        violation.copy(aiSuggestion = it)
                    } ?: violation
                }

                report = report.copy(
                    violations = updatedViolations,
                    aiEnhanced = fixes.isNotEmpty()
                )
            }

            // Generate output
            val generator = com.ortoped.core.policy.PolicyReportGenerator()

            when (format.lowercase()) {
                "json" -> generator.generateJsonReport(report, outputFile)
                "console" -> generator.generateConsoleReport(report)
                "both" -> {
                    generator.generateJsonReport(report, outputFile)
                    if (!noConsole) {
                        generator.generateConsoleReport(report)
                    }
                }
            }

            echo()
            echo("Policy report saved to: ${outputFile.absolutePath}")

            // Exit with error code if failed
            if (!report.passed) {
                throw CliktError("Policy evaluation failed with ${report.summary.errorCount} errors")
            }

        } catch (e: com.ortoped.core.policy.PolicyLoadException) {
            echo("Policy Error: ${e.message}", err = true)
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Policy evaluation failed" }
            throw e
        }
    }
}

class VersionCommand : CliktCommand(
    name = "version",
    help = "Show version information"
) {
    override fun run() {
        echo("Ortoped v1.0.0-SNAPSHOT")
        echo("Built with ORT 74.1.0")
        echo("Powered by Claude AI")
    }
}

fun main(args: Array<String>) {
    OrtopedCli()
        .subcommands(InitCommand(), ScanCommand(), SbomCommand(), PolicyCommand(), VersionCommand())
        .main(args)
}
