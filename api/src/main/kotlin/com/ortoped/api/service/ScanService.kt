package com.ortoped.api.service

import com.ortoped.api.model.*
import com.ortoped.api.plugins.BadRequestException
import com.ortoped.api.plugins.NotFoundException
import com.ortoped.api.repository.ProjectRepository
import com.ortoped.api.repository.ScanEntity
import com.ortoped.api.repository.ScanRepository
import com.ortoped.core.model.ScanResult
import com.ortoped.core.scanner.ScanOrchestrator
import com.ortoped.core.scanner.ScannerConfig
import com.ortoped.core.scanner.SimpleScannerWrapper
import com.ortoped.core.scanner.SourceCodeScanner
import com.ortoped.core.spdx.SpdxLicenseClient
import com.ortoped.core.vcs.RemoteRepositoryHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class ScanService(
    private val scanRepository: ScanRepository,
    private val projectRepository: ProjectRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    // In-memory job tracking for active scans
    private val activeJobs = ConcurrentHashMap<UUID, Job>()

    // Coroutine scope for background scan jobs
    private val scanScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun listScans(
        projectId: String? = null,
        status: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): ScanListResponse {
        val offset = ((page - 1) * pageSize).toLong()
        val projectUuid = projectId?.let { parseUUID(it) }
        val scanStatus = status?.let { ScanStatus.fromString(it) }

        val scans = scanRepository.findAll(projectUuid, scanStatus, pageSize, offset)
        val total = scanRepository.count(projectUuid, scanStatus).toInt()

        return ScanListResponse(
            scans = scans.map { it.toSummaryResponse() },
            total = total,
            page = page,
            pageSize = pageSize
        )
    }

    fun getScan(id: String): ScanStatusResponse {
        val uuid = parseUUID(id)
        val scan = scanRepository.findById(uuid)
            ?: throw NotFoundException("Scan not found: $id")
        return scan.toStatusResponse()
    }

    fun getScanResult(id: String): ScanResult {
        val uuid = parseUUID(id)
        val scan = scanRepository.findById(uuid)
            ?: throw NotFoundException("Scan not found: $id")

        if (scan.status != ScanStatus.COMPLETE.value) {
            throw BadRequestException("Scan is not complete. Current status: ${scan.status}")
        }

        val resultJson = scan.result
            ?: throw NotFoundException("Scan result not available")

        // Handle potentially double-encoded JSON from previous jsonb<String> storage
        val normalizedResultJson = if (resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
            json.decodeFromString<String>(resultJson)
        } else {
            resultJson
        }

        return json.decodeFromString<ScanResult>(normalizedResultJson)
    }

    fun getDependencies(id: String, page: Int = 1, pageSize: Int = 20): DependencyListResponse {
        val scanResult = getScanResult(id)

        val offset = (page - 1) * pageSize
        val dependencies = scanResult.dependencies
            .drop(offset)
            .take(pageSize)
            .map { dep ->
                DependencyResponse(
                    id = dep.id,
                    name = dep.name,
                    version = dep.version,
                    declaredLicenses = dep.declaredLicenses,
                    detectedLicenses = dep.detectedLicenses,
                    concludedLicense = dep.concludedLicense,
                    scope = dep.scope,
                    isResolved = dep.isResolved,
                    aiSuggestion = dep.aiSuggestion?.let { ai ->
                        AiSuggestionResponse(
                            suggestedLicense = ai.suggestedLicense,
                            confidence = ai.confidence,
                            reasoning = ai.reasoning,
                            spdxId = ai.spdxId,
                            alternatives = ai.alternatives
                        )
                    },
                    spdxValidated = dep.spdxValidated,
                    spdxLicense = dep.spdxLicense?.let { license ->
                        SpdxLicenseInfo(
                            licenseId = license.licenseId,
                            name = license.name,
                            isOsiApproved = license.isOsiApproved,
                            isFsfLibre = license.isFsfLibre,
                            isDeprecated = license.isDeprecated,
                            seeAlso = license.seeAlso
                        )
                    },
                    spdxSuggestion = dep.spdxSuggestion?.let { license ->
                        SpdxLicenseInfo(
                            licenseId = license.licenseId,
                            name = license.name,
                            isOsiApproved = license.isOsiApproved,
                            isFsfLibre = license.isFsfLibre,
                            isDeprecated = license.isDeprecated,
                            seeAlso = license.seeAlso
                        )
                    }
                )
            }

        return DependencyListResponse(
            dependencies = dependencies,
            total = scanResult.dependencies.size,
            page = page,
            pageSize = pageSize
        )
    }

    fun triggerScan(request: TriggerScanRequest): ScanStatusResponse {
        val projectUuid = parseUUID(request.projectId)
        val project = projectRepository.findById(projectUuid)
            ?: throw NotFoundException("Project not found: ${request.projectId}")

        val effectiveEnableSpdx = request.enableSpdx || request.demoMode

        // Create scan record
        val scan = scanRepository.create(
            projectId = projectUuid,
            enableAi = request.enableAi,
            enableSpdx = effectiveEnableSpdx
        )

        // Start async scan job
        val job = scanScope.launch {
            executeScan(scan.id, project.repositoryUrl, request)
        }

        activeJobs[scan.id] = job

        return scan.toStatusResponse()
    }

    private suspend fun executeScan(
        scanId: UUID,
        repositoryUrl: String?,
        request: TriggerScanRequest
    ) {
        logger.info { "Starting scan job: $scanId" }

        try {
            val effectiveEnableSpdx = request.enableSpdx || request.demoMode

            // Update status to scanning
            scanRepository.updateStatus(
                id = scanId,
                status = ScanStatus.SCANNING,
                startedAt = Clock.System.now()
            )

            val projectDir: File
            var cleanupFunction: (() -> Unit)? = null

            // Handle repository cloning if remote URL provided
            if (!repositoryUrl.isNullOrBlank()) {
                val remoteHandler = RemoteRepositoryHandler()
                val (clonedDir, cleanup) = remoteHandler.cloneRepository(
                    repoUrl = repositoryUrl,
                    branch = request.branch,
                    tag = request.tag,
                    commit = request.commit
                )
                projectDir = clonedDir
                cleanupFunction = cleanup
            } else if (request.demoMode) {
                // Demo mode - create temp directory
                projectDir = File(System.getProperty("java.io.tmpdir"), "ortoped-demo")
                projectDir.mkdirs()
            } else {
                throw IllegalArgumentException("Repository URL for the project is missing or empty.")
            }

            try {
                // Create scanner
                val scannerConfig = ScannerConfig(
                    enabled = request.enableSourceScan
                )
                val sourceCodeScanner = if (request.enableSourceScan) {
                    SourceCodeScanner(scannerConfig)
                } else {
                    null
                }
                val scanner = SimpleScannerWrapper(sourceCodeScanner)
                val orchestrator = ScanOrchestrator(
                    scanner = scanner,
                    scannerConfig = scannerConfig,
                    spdxClient = SpdxLicenseClient()
                )

                // Run scan
                val scanResult = orchestrator.scanWithAiEnhancement(
                    projectDir = projectDir,
                    enableAiResolution = request.enableAi,
                    enableSpdx = effectiveEnableSpdx,
                    enableSourceScan = request.enableSourceScan,
                    parallelAiCalls = request.parallelAiCalls,
                    demoMode = request.demoMode,
                    disabledPackageManagers = request.disabledPackageManagers,
                    allowDynamicVersions = request.allowDynamicVersions,
                    skipExcluded = request.skipExcluded
                )

                // Store result
                val resultJson = json.encodeToString(scanResult)
                val summaryJson = json.encodeToString(
                    mapOf(
                        "totalDependencies" to scanResult.summary.totalDependencies,
                        "resolvedLicenses" to scanResult.summary.resolvedLicenses,
                        "unresolvedLicenses" to scanResult.summary.unresolvedLicenses,
                        "aiResolvedLicenses" to scanResult.summary.aiResolvedLicenses,
                        "spdxResolvedLicenses" to scanResult.summary.spdxResolvedLicenses
                    )
                )

                scanRepository.updateResult(
                    id = scanId,
                    result = resultJson,
                    summary = summaryJson
                )

                logger.info { "Scan completed successfully: $scanId" }

            } finally {
                cleanupFunction?.invoke()
            }

        } catch (e: Exception) {
            logger.error(e) { "Scan failed: $scanId" }
            scanRepository.updateStatus(
                id = scanId,
                status = ScanStatus.FAILED,
                completedAt = Clock.System.now(),
                errorMessage = e.message ?: "Unknown error"
            )
        } finally {
            activeJobs.remove(scanId)
        }
    }

    fun cancelScan(id: String) {
        val uuid = parseUUID(id)
        val job = activeJobs[uuid]

        if (job != null) {
            job.cancel()
            scanRepository.updateStatus(
                id = uuid,
                status = ScanStatus.FAILED,
                completedAt = Clock.System.now(),
                errorMessage = "Scan cancelled by user"
            )
        } else {
            throw BadRequestException("Scan is not active or already completed")
        }
    }

    /**
     * Get list of available package managers supported by ORT
     */
    fun getAvailablePackageManagers(): PackageManagerListResponse {
        val packageManagers = listOf(
            // JVM
            PackageManagerInfo("Maven", "Maven", "Java/Kotlin dependency management via pom.xml", listOf("pom.xml"), "JVM"),
            PackageManagerInfo("Gradle", "Gradle", "Build automation for JVM projects", listOf("build.gradle", "build.gradle.kts"), "JVM"),
            PackageManagerInfo("SBT", "SBT", "Scala build tool", listOf("build.sbt"), "JVM"),

            // JavaScript
            PackageManagerInfo("NPM", "NPM", "Node.js package manager", listOf("package.json", "package-lock.json"), "JavaScript"),
            PackageManagerInfo("Yarn", "Yarn", "Fast, reliable JavaScript package manager", listOf("yarn.lock"), "JavaScript"),
            PackageManagerInfo("PNPM", "PNPM", "Fast, disk-efficient package manager", listOf("pnpm-lock.yaml"), "JavaScript"),
            PackageManagerInfo("Bower", "Bower", "Frontend package manager (deprecated)", listOf("bower.json"), "JavaScript"),

            // Python
            PackageManagerInfo("Pip", "Pip", "Python package installer", listOf("requirements.txt", "setup.py"), "Python"),
            PackageManagerInfo("Poetry", "Poetry", "Python dependency management and packaging", listOf("pyproject.toml", "poetry.lock"), "Python"),
            PackageManagerInfo("Pipenv", "Pipenv", "Python packaging tool", listOf("Pipfile", "Pipfile.lock"), "Python"),

            // Other Languages
            PackageManagerInfo("Cargo", "Cargo", "Rust package manager", listOf("Cargo.toml", "Cargo.lock"), "Rust"),
            PackageManagerInfo("GoMod", "Go Modules", "Go dependency management", listOf("go.mod", "go.sum"), "Go"),
            PackageManagerInfo("NuGet", "NuGet", ".NET package manager", listOf("*.csproj", "packages.config"), ".NET"),
            PackageManagerInfo("CocoaPods", "CocoaPods", "iOS/macOS dependency manager", listOf("Podfile", "Podfile.lock"), "iOS/macOS"),
            PackageManagerInfo("SwiftPM", "Swift Package Manager", "Apple Swift packages", listOf("Package.swift"), "iOS/macOS"),
            PackageManagerInfo("Composer", "Composer", "PHP dependency manager", listOf("composer.json", "composer.lock"), "PHP"),
            PackageManagerInfo("Bundler", "Bundler", "Ruby dependency manager", listOf("Gemfile", "Gemfile.lock"), "Ruby"),
            PackageManagerInfo("Pub", "Pub", "Dart/Flutter package manager", listOf("pubspec.yaml", "pubspec.lock"), "Dart"),
            PackageManagerInfo("Conan", "Conan", "C/C++ package manager", listOf("conanfile.txt", "conanfile.py"), "C/C++")
        )

        val categories = packageManagers.map { it.category }.distinct().sorted()

        return PackageManagerListResponse(
            packageManagers = packageManagers,
            categories = categories
        )
    }

    /**
     * Generate ORT config.yml for download based on scan settings
     */
    fun generateOrtConfig(
        allowDynamicVersions: Boolean = true,
        skipExcluded: Boolean = true,
        disabledPackageManagers: List<String> = emptyList()
    ): OrtConfigExport {
        val configYml = buildString {
            appendLine("# ORT Analyzer Configuration")
            appendLine("# Generated by OrtoPed - https://github.com/ortoped")
            appendLine("# Place this file in your ORT_CONFIG_DIR directory")
            appendLine()
            appendLine("ort:")
            appendLine("  analyzer:")
            appendLine("    allowDynamicVersions: $allowDynamicVersions")
            appendLine("    skipExcluded: $skipExcluded")

            if (disabledPackageManagers.isNotEmpty()) {
                appendLine("    disabledPackageManagers:")
                disabledPackageManagers.forEach { pm ->
                    appendLine("      - $pm")
                }
            }

            appendLine()
            appendLine("# Usage:")
            appendLine("# 1. Set ORT_CONFIG_DIR environment variable to the directory containing this file")
            appendLine("# 2. Run: ort analyze -i <project-dir> -o <output-dir>")
            appendLine("# Or with ortoped CLI:")
            appendLine("# ortoped scan --project <project-dir>")
        }

        return OrtConfigExport(
            configYml = configYml,
            filename = "ort-config.yml"
        )
    }

    private fun parseUUID(id: String): UUID = try {
        UUID.fromString(id)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid UUID format: $id")
    }

    private fun ScanEntity.toStatusResponse() = ScanStatusResponse(
        id = id.toString(),
        projectId = projectId?.toString(),
        status = status,
        enableAi = enableAi,
        startedAt = startedAt,
        completedAt = completedAt,
        errorMessage = errorMessage,
        createdAt = createdAt
    )

    private fun ScanEntity.toSummaryResponse(): ScanSummaryResponse {
        val summaryData = summary?.let { summaryJson ->
            try {
                // Handle potentially double-encoded JSON from previous jsonb<String> storage
                val normalizedSummary = if (summaryJson.startsWith("\"") && summaryJson.endsWith("\"")) {
                    json.decodeFromString<String>(summaryJson)
                } else {
                    summaryJson
                }
                json.decodeFromString<Map<String, Int>>(normalizedSummary)
            } catch (e: Exception) {
                null
            }
        }

        return ScanSummaryResponse(
            id = id.toString(),
            projectId = projectId?.toString(),
            status = status,
            totalDependencies = summaryData?.get("totalDependencies") ?: 0,
            resolvedLicenses = summaryData?.get("resolvedLicenses") ?: 0,
            unresolvedLicenses = summaryData?.get("unresolvedLicenses") ?: 0,
            aiResolvedLicenses = summaryData?.get("aiResolvedLicenses") ?: 0,
            spdxResolvedLicenses = summaryData?.get("spdxResolvedLicenses") ?: 0,
            startedAt = startedAt,
            completedAt = completedAt
        )
    }
}
