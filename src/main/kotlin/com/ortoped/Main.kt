package com.ortoped

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import com.ortoped.report.ReportGenerator
import com.ortoped.scanner.ScanOrchestrator
import com.ortoped.scanner.ScannerConfig
import com.ortoped.scanner.SimpleScannerWrapper
import com.ortoped.scanner.SourceCodeScanner
import com.ortoped.vcs.RemoteRepositoryHandler
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
    ).flag(default = true)

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

    override fun run() = runBlocking {
        logger.info { "Starting Ortoped scan..." }

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
            logger.info { "AI Enhancement: $enableAi" }
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

            // Create orchestrator with scanner
            val scanner = SimpleScannerWrapper(sourceCodeScanner)
            val orchestrator = ScanOrchestrator(
                scanner = scanner,
                scannerConfig = scannerConfig
            )
            val reportGenerator = ReportGenerator()

            // Run scan with AI enhancement
            val scanResult = orchestrator.scanWithAiEnhancement(
                projectDir = projectDir,
                enableAiResolution = enableAi,
                enableSourceScan = enableSourceScan,
                parallelAiCalls = parallelAi,
                demoMode = demoMode
            )

            // Generate reports
            reportGenerator.generateJsonReport(scanResult, outputFile)

            if (consoleOutput) {
                reportGenerator.generateConsoleReport(scanResult)
            }

            logger.info { "Scan completed successfully!" }
            logger.info { "Report saved to: ${outputFile.absolutePath}" }

            if (isRemote && keepClone) {
                echo()
                echo("Repository kept at: ${projectDir.absolutePath}")
            }

        } catch (e: Exception) {
            logger.error(e) { "Scan failed" }
            echo("Error: ${e.message}", err = true)
            throw e
        } finally {
            // Cleanup cloned repository if needed
            cleanupFunction?.invoke()
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
        .subcommands(ScanCommand(), VersionCommand())
        .main(args)
}