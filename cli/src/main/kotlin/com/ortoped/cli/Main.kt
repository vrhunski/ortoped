package com.ortoped.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.ConnectException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.net.URI
import java.time.Duration

private val logger = KotlinLogging.logger {}

// ============================================================================
// HTTP Client Helper (lightweight, no new dependencies)
// ============================================================================

private data class HttpResult(val statusCode: Int, val body: String)

private object OrtopedHttpClient {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun post(url: String, body: String): HttpResult {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(120))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return execute(request)
    }

    fun get(url: String): HttpResult {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()
        return execute(request)
    }

    private fun execute(request: HttpRequest): HttpResult {
        logger.debug { "HTTP ${request.method()} ${request.uri()}" }
        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            logger.debug { "Response: ${response.statusCode()}" }
            return HttpResult(response.statusCode(), response.body())
        } catch (e: ConnectException) {
            throw CliktError("Cannot connect to server at ${request.uri().host}:${request.uri().port}. Is the server running?")
        } catch (e: HttpTimeoutException) {
            throw CliktError("Request timed out connecting to ${request.uri()}")
        }
    }
}

// Minimal response models (CLI doesn't depend on :api module)

@Serializable
private data class ImportResponse(
    val id: String,
    val projectId: String? = null,
    val status: String,
    val createdAt: String? = null
)

@Serializable
private data class CurationsYamlResponse(
    val yaml: String,
    val filename: String = "curations.yml"
)

@Serializable
private data class NativeCurationExportResponse(
    val version: String = "1.0",
    val scanId: String = "",
    val exportedAt: String = "",
    val curations: List<NativeCurationExportItem> = emptyList()
)

@Serializable
private data class NativeCurationExportItem(
    val packageId: String = "",
    val concludedLicense: String = "",
    val status: String = ""
)

@Serializable
private data class NoticeFileResponse(
    val content: String,
    val filename: String = "NOTICE"
)

@Serializable
private data class ImportScanRequestCli(
    val projectId: String? = null,
    val projectName: String? = null,
    val result: com.ortoped.core.model.ScanResult
)

private val cliJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

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

// ============================================================================
// Curate Commands
// ============================================================================

class CurateCommand : CliktCommand(
    name = "curate",
    help = "Import scan results to dashboard and export curations"
) {
    override fun run() = Unit // group command, delegates to subcommands
}

class CurateImportCommand : CliktCommand(
    name = "import",
    help = "Import a scan report into the OrtoPed dashboard for curation"
) {
    private val inputFile by option(
        "-i", "--input",
        help = "Scan report JSON file (from ortoped scan)"
    ).file(mustExist = true)
        .required()

    private val serverUrl by option(
        "-s", "--server",
        help = "OrtoPed server URL (default: ORTOPED_SERVER env or http://localhost:8080)"
    ).default(System.getenv("ORTOPED_SERVER") ?: "http://localhost:8080")

    private val projectName by option(
        "--project-name",
        help = "Project name (default: from scan result)"
    )

    private val projectId by option(
        "--project-id",
        help = "Existing project UUID to associate the scan with"
    )

    override fun run() {
        echo("Importing scan report: ${inputFile.name}")

        // Read and validate the JSON
        val rawJson = inputFile.readText()
        val scanResult = try {
            cliJson.decodeFromString<com.ortoped.core.model.ScanResult>(rawJson)
        } catch (e: Exception) {
            throw CliktError("Invalid scan report: ${e.message}")
        }

        // Build the import request using proper JSON serialization
        val effectiveProjectName = projectName ?: inputFile.nameWithoutExtension
        val importRequest = ImportScanRequestCli(
            projectId = projectId,
            projectName = effectiveProjectName,
            result = scanResult
        )
        val requestBody = cliJson.encodeToString(ImportScanRequestCli.serializer(), importRequest)

        // POST to server
        val url = "${serverUrl.trimEnd('/')}/api/v1/scans/import"
        echo("Sending to: $url")

        val result = OrtopedHttpClient.post(url, requestBody)

        when {
            result.statusCode == 201 -> {
                val response = try {
                    cliJson.decodeFromString<ImportResponse>(result.body)
                } catch (e: Exception) {
                    throw CliktError("Unexpected server response: ${result.body}")
                }

                echo()
                echo("Scan imported successfully!")
                echo("  Scan ID:  ${response.id}")
                if (response.projectId != null) {
                    echo("  Project:  ${response.projectId}")
                }
                echo("  Status:   ${response.status}")
                echo()
                echo("Next steps:")
                echo("  1. Open the dashboard to curate licenses")
                echo("  2. Export curations when done:")
                echo("     ortoped curate export -s ${response.id}")
            }
            result.statusCode in 400..499 -> {
                throw CliktError("Import rejected (HTTP ${result.statusCode}): ${result.body}")
            }
            result.statusCode in 500..599 -> {
                throw CliktError("Server error (HTTP ${result.statusCode}). Try again later.")
            }
            else -> {
                throw CliktError("Import failed (HTTP ${result.statusCode}): ${result.body}")
            }
        }
    }
}

class CurateExportCommand : CliktCommand(
    name = "export",
    help = "Export curations from the dashboard as files for your project"
) {
    private val scanId by option(
        "-s", "--scan-id",
        help = "Scan ID to export curations from"
    ).required().validate {
        require(it.matches(Regex("[a-zA-Z0-9_-]+"))) {
            "Scan ID must contain only alphanumeric characters, hyphens, and underscores"
        }
    }

    private val serverUrl by option(
        "--server",
        help = "OrtoPed server URL (default: ORTOPED_SERVER env or http://localhost:8080)"
    ).default(System.getenv("ORTOPED_SERVER") ?: "http://localhost:8080")

    private val format by option(
        "-f", "--format",
        help = "Export format: yaml (default), native, notice, or all"
    ).choice("yaml", "native", "notice", "all")
        .default("yaml")

    private val outputDir by option(
        "-o", "--output-dir",
        help = "Output directory (default: .ortoped)"
    ).file()
        .default(File(".ortoped"))

    override fun run() {
        val baseUrl = "${serverUrl.trimEnd('/')}/api/v1/scans/$scanId/curation/export"
        val formats = if (format == "all") listOf("yaml", "native", "notice") else listOf(format)
        val writtenFiles = mutableListOf<String>()
        val canonicalOutputDir = outputDir.canonicalFile

        canonicalOutputDir.mkdirs()

        for (fmt in formats) {
            try {
                when (fmt) {
                    "yaml" -> {
                        val result = OrtopedHttpClient.get("$baseUrl/curations-yaml")
                        if (result.statusCode != 200) {
                            echo("Warning: Failed to export curations YAML (HTTP ${result.statusCode})", err = true)
                            continue
                        }
                        val response = cliJson.decodeFromString<CurationsYamlResponse>(result.body)
                        val file = safeOutputFile(canonicalOutputDir, response.filename)
                        file.writeText(response.yaml)
                        writtenFiles.add(file.path)
                        echo("  Wrote ${file.path}")
                    }
                    "native" -> {
                        val result = OrtopedHttpClient.get("$baseUrl/curations-native")
                        if (result.statusCode != 200) {
                            echo("Warning: Failed to export native curations (HTTP ${result.statusCode})", err = true)
                            continue
                        }
                        // Pretty-print the native JSON
                        val parsed = cliJson.parseToJsonElement(result.body)
                        val prettyJson = cliJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), parsed)
                        val file = safeOutputFile(canonicalOutputDir, "curations.ortoped.json")
                        file.writeText(prettyJson)
                        writtenFiles.add(file.path)
                        echo("  Wrote ${file.path}")
                    }
                    "notice" -> {
                        val result = OrtopedHttpClient.get("$baseUrl/notice")
                        if (result.statusCode != 200) {
                            echo("Warning: Failed to export NOTICE file (HTTP ${result.statusCode})", err = true)
                            continue
                        }
                        val response = cliJson.decodeFromString<NoticeFileResponse>(result.body)
                        val file = safeOutputFile(canonicalOutputDir, response.filename)
                        file.writeText(response.content)
                        writtenFiles.add(file.path)
                        echo("  Wrote ${file.path}")
                    }
                }
            } catch (e: CliktError) {
                throw e // Re-throw connection errors
            } catch (e: Exception) {
                echo("Warning: Failed to export $fmt format: ${e.message}", err = true)
            }
        }

        echo()
        if (writtenFiles.isEmpty()) {
            throw CliktError("No files were exported. Check the scan ID and server connection.")
        }

        echo("Exported ${writtenFiles.size} file(s) to ${outputDir.path}/")
        if (writtenFiles.any { it.endsWith("curations.yml") }) {
            echo("Curations will be auto-applied on next scan: ortoped scan -p .")
        }
    }

    private fun safeOutputFile(baseDir: File, filename: String): File {
        val sanitized = filename.replace("..", "").replace("/", "").replace("\\", "")
        val file = File(baseDir, sanitized).canonicalFile
        require(file.startsWith(baseDir)) {
            "Filename '$filename' escapes output directory"
        }
        return file
    }
}

fun main(args: Array<String>) {
    OrtopedCli()
        .subcommands(
            InitCommand(), ScanCommand(), SbomCommand(), PolicyCommand(),
            CurateCommand().subcommands(CurateImportCommand(), CurateExportCommand()),
            VersionCommand()
        )
        .main(args)
}
