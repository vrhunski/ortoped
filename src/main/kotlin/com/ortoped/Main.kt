package com.ortoped

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import com.ortoped.report.ReportGenerator
import com.ortoped.scanner.ScanOrchestrator
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
    private val projectDir by option(
        "-p", "--project",
        help = "Project directory to scan"
    ).file(mustExist = true, canBeFile = false, mustBeReadable = true)
        .default(File("."))

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

    override fun run() = runBlocking {
        logger.info { "Starting Ortoped scan..." }
        logger.info { "Demo Mode: $demoMode" }
        logger.info { "Project: ${projectDir.absolutePath}" }
        logger.info { "Output: ${outputFile.absolutePath}" }
        logger.info { "AI Enhancement: $enableAi" }

        if (demoMode) {
            echo("Running in DEMO mode - using mock data to showcase AI license resolution")
            echo()
        }

        try {
            val orchestrator = ScanOrchestrator()
            val reportGenerator = ReportGenerator()

            // Run scan with AI enhancement
            val scanResult = orchestrator.scanWithAiEnhancement(
                projectDir = projectDir,
                enableAiResolution = enableAi,
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

        } catch (e: Exception) {
            logger.error(e) { "Scan failed" }
            echo("Error: ${e.message}", err = true)
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
        .subcommands(ScanCommand(), VersionCommand())
        .main(args)
}