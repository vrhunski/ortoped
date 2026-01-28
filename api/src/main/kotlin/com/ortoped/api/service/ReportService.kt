package com.ortoped.api.service

import com.ortoped.api.plugins.BadRequestException
import com.ortoped.api.plugins.NotFoundException
import com.ortoped.api.repository.*
import com.ortoped.core.model.*
import com.ortoped.core.policy.PolicyReport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Service for generating comprehensive reports
 */
class ReportService(
    private val scanRepository: ScanRepository,
    private val projectRepository: ProjectRepository,
    private val policyRepository: PolicyRepository,
    private val curationRepository: CurationRepository,
    private val curationSessionRepository: CurationSessionRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /**
     * Generate a comprehensive report for a scan
     */
    fun generateReport(
        scanId: String,
        options: ReportOptions,
        generatedBy: String? = null
    ): GenerateReportResponse {
        val scanUuid = UUID.fromString(scanId)

        // Get scan
        val scanEntity = scanRepository.findById(scanUuid)
            ?: throw NotFoundException("Scan not found: $scanId")

        if (scanEntity.status != "complete") {
            throw BadRequestException("Cannot generate report for incomplete scan")
        }

        val scanResult = scanEntity.result?.let {
            try {
                json.decodeFromString<ScanResult>(it)
            } catch (e: Exception) {
                logger.error(e) { "Failed to parse scan result" }
                throw BadRequestException("Invalid scan result format")
            }
        } ?: throw BadRequestException("Scan has no result")

        // Get project info
        val project = scanEntity.projectId?.let { projectRepository.findById(it) }

        // Build report
        val reportId = UUID.randomUUID().toString()
        val now = Clock.System.now().toString()

        val report = ComprehensiveReport(
            id = reportId,
            generatedAt = now,
            reportVersion = "1.0",
            project = buildProjectInfo(project, scanResult),
            scan = buildScanPhaseReport(scanEntity, scanResult, options),
            policyEvaluation = if (options.includePolicy) {
                buildPolicyPhaseReport(scanUuid)
            } else null,
            curation = if (options.includeCuration) {
                buildCurationPhaseReport(scanUuid, options)
            } else null,
            finalResults = buildFinalResults(scanResult, scanUuid),
            auditTrail = if (options.includeAuditTrail) {
                buildAuditTrail(scanUuid)
            } else emptyList()
        )

        // Generate output based on format
        val content = when (options.format.lowercase()) {
            "html" -> generateHtmlReport(report)
            else -> json.encodeToString(report)
        }

        val filename = buildFilename(scanResult.projectName, options.format)

        logger.info { "Generated ${options.format} report for scan $scanId: $filename" }

        return GenerateReportResponse(
            reportId = reportId,
            scanId = scanId,
            format = options.format,
            filename = filename,
            content = content,
            generatedAt = now,
            metadata = ReportMetadata(
                id = reportId,
                scanId = scanId,
                format = options.format,
                generatedAt = now,
                generatedBy = generatedBy,
                fileSize = content.length.toLong(),
                options = options
            )
        )
    }

    /**
     * Generate report summary (without full content)
     */
    fun getReportSummary(scanId: String): ReportSummaryResponse {
        val scanUuid = UUID.fromString(scanId)

        val scanEntity = scanRepository.findById(scanUuid)
            ?: throw NotFoundException("Scan not found: $scanId")

        val scanResult = scanEntity.result?.let {
            json.decodeFromString<ScanResult>(it)
        }

        val session = curationSessionRepository.findByScanId(scanUuid)
        val policyEvals = policyRepository.findEvaluationsByScan(scanUuid)

        return ReportSummaryResponse(
            scanId = scanId,
            projectName = scanResult?.projectName ?: "Unknown",
            scanStatus = scanEntity.status,
            scanDate = scanEntity.createdAt,
            totalDependencies = scanResult?.summary?.totalDependencies ?: 0,
            resolvedLicenses = scanResult?.summary?.resolvedLicenses ?: 0,
            unresolvedLicenses = scanResult?.summary?.unresolvedLicenses ?: 0,
            aiResolvedLicenses = scanResult?.summary?.aiResolvedLicenses ?: 0,
            hasPolicyEvaluation = policyEvals.isNotEmpty(),
            policyPassed = policyEvals.firstOrNull()?.passed,
            hasCuration = session != null,
            curationStatus = session?.status,
            curationApproved = session?.status == "APPROVED"
        )
    }

    // ========================================================================
    // Report Building Helpers
    // ========================================================================

    private fun buildProjectInfo(
        project: ProjectEntity?,
        scanResult: ScanResult
    ): ProjectInfo {
        return ProjectInfo(
            id = project?.id?.toString(),
            name = project?.name ?: scanResult.projectName,
            repositoryUrl = project?.repositoryUrl,
            branch = project?.defaultBranch,
            scanDate = scanResult.scanDate
        )
    }

    private fun buildScanPhaseReport(
        scanEntity: ScanEntity,
        scanResult: ScanResult,
        options: ReportOptions
    ): ScanPhaseReport {
        val duration = if (scanEntity.startedAt != null && scanEntity.completedAt != null) {
            try {
                val start = kotlinx.datetime.Instant.parse(scanEntity.startedAt)
                val end = kotlinx.datetime.Instant.parse(scanEntity.completedAt)
                (end - start).inWholeMilliseconds
            } catch (e: Exception) { null }
        } else null

        return ScanPhaseReport(
            scanId = scanEntity.id.toString(),
            status = scanEntity.status,
            duration = duration,
            configuration = ScanConfiguration(
                enableAi = scanEntity.enableAi,
                enableSourceScan = scanResult.sourceCodeScanned,
                parallelAiCalls = false // TODO: Track this
            ),
            summary = ScanPhaseSummary(
                totalDependencies = scanResult.summary.totalDependencies,
                resolvedLicenses = scanResult.summary.resolvedLicenses,
                unresolvedLicenses = scanResult.summary.unresolvedLicenses,
                aiResolvedLicenses = scanResult.summary.aiResolvedLicenses,
                scannerResolvedLicenses = scanResult.summary.scannerResolvedLicenses
            ),
            dependencies = if (options.includeDependencyDetails) {
                scanResult.dependencies.map { dep ->
                    DependencyReport(
                        id = dep.id,
                        name = dep.name,
                        version = dep.version,
                        scope = dep.scope,
                        licenseResolution = LicenseResolutionJourney(
                            declaredLicenses = dep.declaredLicenses,
                            detectedLicenses = dep.detectedLicenses,
                            aiSuggestion = dep.aiSuggestion?.let {
                                AiSuggestionSummary(
                                    suggestedLicense = it.suggestedLicense,
                                    confidence = it.confidence,
                                    reasoning = if (options.includeAiReasoning) it.reasoning else null
                                )
                            },
                            sourceCodeLicenses = null, // TODO: Add source scan data
                            curationDecision = null, // Added later if curation exists
                            finalLicense = dep.concludedLicense,
                            resolutionMethod = determineResolutionMethod(dep)
                        )
                    )
                }
            } else null
        )
    }

    private fun buildPolicyPhaseReport(scanId: UUID): PolicyPhaseReport? {
        val evaluations = policyRepository.findEvaluationsByScan(scanId)
        val latestEval = evaluations.firstOrNull() ?: return null

        val policy = policyRepository.findById(latestEval.policyId)

        val policyReport = try {
            json.decodeFromString<PolicyReport>(latestEval.report)
        } catch (e: Exception) {
            logger.warn { "Failed to parse policy report" }
            return null
        }

        return PolicyPhaseReport(
            policyId = latestEval.policyId.toString(),
            policyName = policy?.name ?: policyReport.policyName,
            policyVersion = policyReport.policyVersion,
            evaluatedAt = latestEval.createdAt,
            passed = latestEval.passed,
            summary = PolicyPhaseSummary(
                totalDependencies = policyReport.summary.totalDependencies,
                evaluatedDependencies = policyReport.summary.evaluatedDependencies,
                exemptedDependencies = policyReport.summary.exemptedDependencies,
                totalViolations = policyReport.summary.totalViolations,
                errorCount = policyReport.summary.errorCount,
                warningCount = policyReport.summary.warningCount,
                infoCount = policyReport.summary.infoCount,
                licenseDistributionByCategory = policyReport.summary.licenseDistributionByCategory
            ),
            violations = policyReport.violations.map { v ->
                PolicyViolationReport(
                    ruleId = v.ruleId,
                    ruleName = v.ruleName,
                    severity = v.severity.name,
                    dependencyName = v.dependencyName,
                    dependencyVersion = v.dependencyVersion,
                    license = v.license,
                    message = v.message,
                    aiSuggestedFix = v.aiSuggestion?.suggestion,
                    resolved = false, // TODO: Check against curation
                    resolutionMethod = null
                )
            },
            exemptions = policyReport.exemptedDependencies.map { e ->
                ExemptionReport(
                    dependencyName = e.dependencyName,
                    reason = e.exemptionReason,
                    exemptedBy = e.approvedBy,
                    exemptedAt = null
                )
            }
        )
    }

    private fun buildCurationPhaseReport(scanId: UUID, options: ReportOptions): CurationPhaseReport? {
        val session = curationSessionRepository.findByScanId(scanId) ?: return null
        val curations = curationRepository.findBySessionId(session.id)

        val completionPct = if (session.totalItems > 0) {
            ((session.totalItems - session.pendingItems).toDouble() / session.totalItems * 100)
        } else 0.0

        return CurationPhaseReport(
            sessionId = session.id.toString(),
            status = session.status,
            startedAt = session.createdAt,
            completedAt = if (session.status == "APPROVED") session.approvedAt else null,
            statistics = CurationPhaseSummary(
                total = session.totalItems,
                pending = session.pendingItems,
                accepted = session.acceptedItems,
                rejected = session.rejectedItems,
                modified = session.modifiedItems,
                completionPercentage = completionPct
            ),
            decisions = if (options.includeDependencyDetails) {
                curations.filter { it.status != "PENDING" }.map { c ->
                    CurationDecisionReport(
                        dependencyName = c.dependencyName,
                        dependencyVersion = c.dependencyVersion,
                        originalLicense = c.originalLicense,
                        aiSuggestedLicense = c.aiSuggestedLicense,
                        aiConfidence = c.aiConfidence,
                        action = c.status,
                        finalLicense = c.curatedLicense,
                        comment = c.curatorComment,
                        curatedBy = c.curatorId,
                        curatedAt = c.curatedAt
                    )
                }
            } else null,
            approval = if (session.approvedBy != null && session.approvedAt != null) {
                CurationApprovalReport(
                    approvedBy = session.approvedBy,
                    approvedAt = session.approvedAt,
                    comment = session.approvalComment
                )
            } else null
        )
    }

    private fun buildFinalResults(scanResult: ScanResult, scanId: UUID): FinalResultsReport {
        // Get curation data to adjust final counts
        val session = curationSessionRepository.findByScanId(scanId)
        val curations = session?.let { curationRepository.findBySessionId(it.id) } ?: emptyList()

        val curatedCount = curations.count { it.status != "PENDING" }

        // Build license distribution including curated licenses
        val licenseDistribution = scanResult.summary.licenseDistribution.toMutableMap()
        curations.forEach { c ->
            if (c.curatedLicense != null && c.status != "PENDING") {
                // Remove old license count
                c.originalLicense?.let { old ->
                    licenseDistribution[old] = (licenseDistribution[old] ?: 1) - 1
                    if (licenseDistribution[old] == 0) licenseDistribution.remove(old)
                }
                // Add curated license count
                licenseDistribution[c.curatedLicense!!] =
                    (licenseDistribution[c.curatedLicense] ?: 0) + 1
            }
        }

        // Categorize licenses
        val categoryDistribution = mutableMapOf<String, Int>()
        licenseDistribution.forEach { (license, count) ->
            val category = categorizeLicense(license)
            categoryDistribution[category] = (categoryDistribution[category] ?: 0) + count
        }

        // Determine compliance status
        val unresolvedCount = scanResult.summary.unresolvedLicenses - curatedCount.coerceAtMost(scanResult.summary.unresolvedLicenses)
        val complianceStatus = when {
            unresolvedCount > 0 -> "REQUIRES_REVIEW"
            session?.status == "APPROVED" -> "COMPLIANT"
            else -> "REQUIRES_REVIEW"
        }

        return FinalResultsReport(
            licenseDistribution = licenseDistribution.filter { it.value > 0 },
            categoryDistribution = categoryDistribution,
            complianceStatus = complianceStatus,
            unresolvedIssues = unresolvedCount,
            totalDependencies = scanResult.summary.totalDependencies,
            resolvedByDeclared = scanResult.dependencies.count {
                it.declaredLicenses.isNotEmpty() && it.aiSuggestion == null
            },
            resolvedByDetected = scanResult.dependencies.count {
                it.detectedLicenses.isNotEmpty() && it.declaredLicenses.isEmpty() && it.aiSuggestion == null
            },
            resolvedByAi = scanResult.summary.aiResolvedLicenses,
            resolvedBySourceScan = scanResult.summary.scannerResolvedLicenses,
            resolvedByCuration = curatedCount,
            unresolved = unresolvedCount
        )
    }

    private fun buildAuditTrail(scanId: UUID): List<AuditEvent> {
        val events = mutableListOf<AuditEvent>()

        // Scan events
        val scan = scanRepository.findById(scanId)
        if (scan != null) {
            events.add(AuditEvent(
                timestamp = scan.createdAt,
                phase = "SCAN",
                action = "SCAN_STARTED",
                actor = "system",
                details = "Scan initiated"
            ))
            if (scan.completedAt != null) {
                events.add(AuditEvent(
                    timestamp = scan.completedAt,
                    phase = "SCAN",
                    action = "SCAN_COMPLETED",
                    actor = "system",
                    details = "Scan completed with status: ${scan.status}"
                ))
            }
        }

        // Policy events
        val policyEvals = policyRepository.findEvaluationsByScan(scanId)
        policyEvals.forEach { eval ->
            events.add(AuditEvent(
                timestamp = eval.createdAt,
                phase = "POLICY",
                action = "POLICY_EVALUATED",
                actor = "system",
                details = "Policy evaluation: ${if (eval.passed) "PASSED" else "FAILED"} (${eval.errorCount} errors)"
            ))
        }

        // Curation events
        val session = curationSessionRepository.findByScanId(scanId)
        if (session != null) {
            events.add(AuditEvent(
                timestamp = session.createdAt,
                phase = "CURATION",
                action = "CURATION_STARTED",
                actor = "system",
                details = "Curation session started with ${session.totalItems} items"
            ))

            if (session.approvedBy != null && session.approvedAt != null) {
                events.add(AuditEvent(
                    timestamp = session.approvedAt,
                    phase = "CURATION",
                    action = "CURATION_APPROVED",
                    actor = session.approvedBy,
                    details = session.approvalComment ?: "Curation approved"
                ))
            }
        }

        return events.sortedBy { it.timestamp }
    }

    // ========================================================================
    // HTML Report Generation
    // ========================================================================

    private fun generateHtmlReport(report: ComprehensiveReport): String {
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("  <meta charset=\"UTF-8\">")
            appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            appendLine("  <title>License Compliance Report - ${report.project.name}</title>")
            appendLine("  <style>")
            appendLine(getReportStyles())
            appendLine("  </style>")
            appendLine("</head>")
            appendLine("<body>")

            // Header
            appendLine("  <header>")
            appendLine("    <h1>License Compliance Report</h1>")
            appendLine("    <p class=\"subtitle\">${report.project.name}</p>")
            appendLine("    <p class=\"meta\">Generated: ${report.generatedAt}</p>")
            appendLine("  </header>")

            // Executive Summary
            appendLine("  <section class=\"summary\">")
            appendLine("    <h2>Executive Summary</h2>")
            appendLine("    <div class=\"stats-grid\">")
            appendLine("      <div class=\"stat\">")
            appendLine("        <span class=\"value\">${report.finalResults.totalDependencies}</span>")
            appendLine("        <span class=\"label\">Total Dependencies</span>")
            appendLine("      </div>")
            appendLine("      <div class=\"stat\">")
            appendLine("        <span class=\"value\">${report.finalResults.totalDependencies - report.finalResults.unresolved}</span>")
            appendLine("        <span class=\"label\">Resolved Licenses</span>")
            appendLine("      </div>")
            appendLine("      <div class=\"stat ${if (report.finalResults.unresolved > 0) "warning" else "success"}\">")
            appendLine("        <span class=\"value\">${report.finalResults.unresolved}</span>")
            appendLine("        <span class=\"label\">Unresolved</span>")
            appendLine("      </div>")
            appendLine("      <div class=\"stat ${getStatusClass(report.finalResults.complianceStatus)}\">")
            appendLine("        <span class=\"value\">${report.finalResults.complianceStatus}</span>")
            appendLine("        <span class=\"label\">Compliance Status</span>")
            appendLine("      </div>")
            appendLine("    </div>")
            appendLine("  </section>")

            // License Distribution
            appendLine("  <section>")
            appendLine("    <h2>License Distribution</h2>")
            appendLine("    <table>")
            appendLine("      <thead><tr><th>License</th><th>Count</th></tr></thead>")
            appendLine("      <tbody>")
            report.finalResults.licenseDistribution
                .entries.sortedByDescending { it.value }
                .forEach { (license, count) ->
                    appendLine("        <tr><td>$license</td><td>$count</td></tr>")
                }
            appendLine("      </tbody>")
            appendLine("    </table>")
            appendLine("  </section>")

            // Category Distribution
            appendLine("  <section>")
            appendLine("    <h2>License Categories</h2>")
            appendLine("    <table>")
            appendLine("      <thead><tr><th>Category</th><th>Count</th></tr></thead>")
            appendLine("      <tbody>")
            report.finalResults.categoryDistribution.forEach { (category, count) ->
                appendLine("        <tr><td>$category</td><td>$count</td></tr>")
            }
            appendLine("      </tbody>")
            appendLine("    </table>")
            appendLine("  </section>")

            // Policy Evaluation
            report.policyEvaluation?.let { policy ->
                appendLine("  <section>")
                appendLine("    <h2>Policy Evaluation</h2>")
                appendLine("    <p><strong>Policy:</strong> ${policy.policyName}</p>")
                appendLine("    <p><strong>Result:</strong> <span class=\"${if (policy.passed) "success" else "error"}\">${if (policy.passed) "PASSED" else "FAILED"}</span></p>")
                if (policy.violations.isNotEmpty()) {
                    appendLine("    <h3>Violations (${policy.violations.size})</h3>")
                    appendLine("    <table>")
                    appendLine("      <thead><tr><th>Severity</th><th>Dependency</th><th>License</th><th>Message</th></tr></thead>")
                    appendLine("      <tbody>")
                    policy.violations.forEach { v ->
                        appendLine("        <tr class=\"${v.severity.lowercase()}\">")
                        appendLine("          <td>${v.severity}</td>")
                        appendLine("          <td>${v.dependencyName}@${v.dependencyVersion}</td>")
                        appendLine("          <td>${v.license ?: "Unknown"}</td>")
                        appendLine("          <td>${v.message}</td>")
                        appendLine("        </tr>")
                    }
                    appendLine("      </tbody>")
                    appendLine("    </table>")
                }
                appendLine("  </section>")
            }

            // Curation Summary
            report.curation?.let { curation ->
                appendLine("  <section>")
                appendLine("    <h2>Curation Summary</h2>")
                appendLine("    <p><strong>Status:</strong> ${curation.status}</p>")
                appendLine("    <div class=\"stats-grid\">")
                appendLine("      <div class=\"stat\"><span class=\"value\">${curation.statistics.accepted}</span><span class=\"label\">Accepted</span></div>")
                appendLine("      <div class=\"stat\"><span class=\"value\">${curation.statistics.rejected}</span><span class=\"label\">Rejected</span></div>")
                appendLine("      <div class=\"stat\"><span class=\"value\">${curation.statistics.modified}</span><span class=\"label\">Modified</span></div>")
                appendLine("      <div class=\"stat\"><span class=\"value\">${curation.statistics.pending}</span><span class=\"label\">Pending</span></div>")
                appendLine("    </div>")
                curation.approval?.let { approval ->
                    appendLine("    <p class=\"approval\">Approved by ${approval.approvedBy} on ${approval.approvedAt}</p>")
                }
                appendLine("  </section>")
            }

            // Footer
            appendLine("  <footer>")
            appendLine("    <p>Report ID: ${report.id}</p>")
            appendLine("    <p>Generated by OrtoPed v${report.reportVersion}</p>")
            appendLine("  </footer>")

            appendLine("</body>")
            appendLine("</html>")
        }
    }

    private fun getReportStyles(): String = """
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; color: #333; max-width: 1200px; margin: 0 auto; padding: 20px; }
        header { text-align: center; margin-bottom: 40px; padding-bottom: 20px; border-bottom: 2px solid #e0e0e0; }
        h1 { font-size: 2.5em; color: #1a1a2e; }
        .subtitle { font-size: 1.4em; color: #666; margin-top: 10px; }
        .meta { color: #999; font-size: 0.9em; margin-top: 5px; }
        section { margin-bottom: 40px; }
        h2 { color: #1a1a2e; margin-bottom: 20px; padding-bottom: 10px; border-bottom: 1px solid #e0e0e0; }
        h3 { color: #444; margin: 20px 0 10px; }
        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 20px; }
        .stat { background: #f8f9fa; padding: 20px; border-radius: 8px; text-align: center; }
        .stat .value { display: block; font-size: 2em; font-weight: bold; color: #1a1a2e; }
        .stat .label { display: block; color: #666; font-size: 0.9em; margin-top: 5px; }
        .stat.success { background: #d4edda; }
        .stat.warning { background: #fff3cd; }
        .stat.error { background: #f8d7da; }
        table { width: 100%; border-collapse: collapse; margin-top: 10px; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #e0e0e0; }
        th { background: #f8f9fa; font-weight: 600; }
        tr:hover { background: #f8f9fa; }
        tr.error { background: #fff5f5; }
        tr.warning { background: #fffbeb; }
        .success { color: #28a745; }
        .error { color: #dc3545; }
        .warning { color: #ffc107; }
        .approval { background: #d4edda; padding: 10px; border-radius: 4px; margin-top: 15px; }
        footer { margin-top: 40px; padding-top: 20px; border-top: 1px solid #e0e0e0; text-align: center; color: #999; font-size: 0.9em; }
    """.trimIndent()

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun determineResolutionMethod(dep: Dependency): String {
        return when {
            dep.aiSuggestion != null -> "AI"
            dep.declaredLicenses.isNotEmpty() -> "DECLARED"
            dep.detectedLicenses.isNotEmpty() -> "DETECTED"
            dep.concludedLicense != null -> "MANUAL"
            else -> "UNRESOLVED"
        }
    }

    private fun categorizeLicense(license: String): String {
        val permissive = listOf("MIT", "Apache-2.0", "BSD", "ISC", "Unlicense", "CC0")
        val copyleft = listOf("GPL", "AGPL", "LGPL", "MPL", "EPL", "CDDL")

        return when {
            permissive.any { license.contains(it, ignoreCase = true) } -> "Permissive"
            copyleft.any { license.contains(it, ignoreCase = true) } -> "Copyleft"
            license.contains("proprietary", ignoreCase = true) -> "Proprietary"
            license in listOf("NOASSERTION", "Unknown", "") -> "Unknown"
            else -> "Other"
        }
    }

    private fun getStatusClass(status: String): String = when (status) {
        "COMPLIANT" -> "success"
        "NON_COMPLIANT" -> "error"
        else -> "warning"
    }

    private fun buildFilename(projectName: String, format: String): String {
        val safeName = projectName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val timestamp = Clock.System.now().toString().take(10)
        val extension = when (format.lowercase()) {
            "html" -> "html"
            "ort" -> "ort.yml"
            else -> "json"
        }
        return "report-$safeName-$timestamp.$extension"
    }

    // ========================================================================
    // ORT Evaluator Export
    // ========================================================================

    /**
     * Generate ORT-compatible evaluator result format
     */
    fun generateOrtExport(scanId: String): OrtExportResponse {
        val scanUuid = UUID.fromString(scanId)

        val scanEntity = scanRepository.findById(scanUuid)
            ?: throw NotFoundException("Scan not found: $scanId")

        if (scanEntity.status != "complete") {
            throw BadRequestException("Cannot export incomplete scan")
        }

        val scanResult = scanEntity.result?.let {
            json.decodeFromString<ScanResult>(it)
        } ?: throw BadRequestException("Scan has no result")

        val project = scanEntity.projectId?.let { projectRepository.findById(it) }
        val session = curationSessionRepository.findByScanId(scanUuid)
        val curations = session?.let { curationRepository.findBySessionId(it.id) } ?: emptyList()
        val policyEvals = policyRepository.findEvaluationsByScan(scanUuid)

        // Build ORT-compatible structure
        val ortResult = OrtEvaluatorResult(
            repository = OrtRepository(
                vcs = OrtVcsInfo(
                    type = "Git",
                    url = project?.repositoryUrl ?: "",
                    revision = project?.defaultBranch ?: "main",
                    path = ""
                ),
                config = OrtRepositoryConfig()
            ),
            analyzer = OrtAnalyzerResult(
                startTime = scanEntity.startedAt ?: scanEntity.createdAt,
                endTime = scanEntity.completedAt ?: scanEntity.createdAt,
                projects = listOf(
                    OrtProject(
                        id = "${scanResult.projectName}:${project?.defaultBranch ?: "main"}",
                        definitionFilePath = "build.gradle.kts",
                        declaredLicenses = emptyList(),
                        processedDeclaredLicenses = OrtProcessedLicenses(
                            spdxExpression = null,
                            unmapped = emptyList()
                        ),
                        vcs = OrtVcsInfo(
                            type = "Git",
                            url = project?.repositoryUrl ?: "",
                            revision = project?.defaultBranch ?: "main",
                            path = ""
                        ),
                        homepageUrl = project?.repositoryUrl ?: "",
                        scopeNames = scanResult.dependencies.map { it.scope }.distinct()
                    )
                ),
                packages = scanResult.dependencies.map { dep ->
                    val curation = curations.find { it.dependencyId == dep.id }
                    OrtPackage(
                        id = dep.id,
                        purl = "pkg:maven/${dep.id.replace(":", "/")}@${dep.version}",
                        declaredLicenses = dep.declaredLicenses,
                        processedDeclaredLicenses = OrtProcessedLicenses(
                            spdxExpression = dep.concludedLicense ?: curation?.curatedLicense,
                            unmapped = dep.declaredLicenses.filter {
                                it != dep.concludedLicense && it != curation?.curatedLicense
                            }
                        ),
                        concludedLicense = curation?.curatedLicense ?: dep.concludedLicense,
                        description = "",
                        homepageUrl = "",
                        binaryArtifact = OrtRemoteArtifact(),
                        sourceArtifact = OrtRemoteArtifact(),
                        vcs = OrtVcsInfo(type = "", url = "", revision = "", path = ""),
                        curations = if (curation != null) {
                            listOf(
                                OrtPackageCuration(
                                    id = dep.id,
                                    curations = OrtCurationData(
                                        concludedLicense = curation.curatedLicense,
                                        comment = curation.curatorComment
                                    )
                                )
                            )
                        } else emptyList()
                    )
                },
                issues = emptyList()
            ),
            evaluator = if (policyEvals.isNotEmpty()) {
                val latestEval = policyEvals.first()
                val policyReport = try {
                    json.decodeFromString<PolicyReport>(latestEval.report)
                } catch (e: Exception) { null }

                OrtEvaluatorRun(
                    startTime = latestEval.createdAt,
                    endTime = latestEval.createdAt,
                    violations = policyReport?.violations?.map { v ->
                        OrtRuleViolation(
                            rule = v.ruleName,
                            packageId = v.dependencyId,
                            license = v.license,
                            licenseSource = "DECLARED",
                            severity = v.severity.name,
                            message = v.message,
                            howToFix = v.aiSuggestion?.suggestion ?: ""
                        )
                    } ?: emptyList()
                )
            } else null,
            labels = mapOf(
                "ortoped.version" to "1.0",
                "ortoped.scanId" to scanId,
                "ortoped.projectName" to scanResult.projectName,
                "ortoped.curationStatus" to (session?.status ?: "NONE"),
                "ortoped.aiEnhanced" to scanResult.aiEnhanced.toString()
            )
        )

        val content = json.encodeToString(ortResult)
        val filename = buildFilename(scanResult.projectName, "ort")

        logger.info { "Generated ORT export for scan $scanId" }

        return OrtExportResponse(
            scanId = scanId,
            filename = filename,
            content = content,
            format = "ort-evaluator",
            generatedAt = Clock.System.now().toString()
        )
    }
}

// ============================================================================
// Response Models
// ============================================================================

@kotlinx.serialization.Serializable
data class GenerateReportResponse(
    val reportId: String,
    val scanId: String,
    val format: String,
    val filename: String,
    val content: String,
    val generatedAt: String,
    val metadata: ReportMetadata
)

@kotlinx.serialization.Serializable
data class ReportSummaryResponse(
    val scanId: String,
    val projectName: String,
    val scanStatus: String,
    val scanDate: String,
    val totalDependencies: Int,
    val resolvedLicenses: Int,
    val unresolvedLicenses: Int,
    val aiResolvedLicenses: Int,
    val hasPolicyEvaluation: Boolean,
    val policyPassed: Boolean?,
    val hasCuration: Boolean,
    val curationStatus: String?,
    val curationApproved: Boolean
)

@kotlinx.serialization.Serializable
data class OrtExportResponse(
    val scanId: String,
    val filename: String,
    val content: String,
    val format: String,
    val generatedAt: String
)

// ============================================================================
// ORT Evaluator Data Models
// ============================================================================

@kotlinx.serialization.Serializable
data class OrtEvaluatorResult(
    val repository: OrtRepository,
    val analyzer: OrtAnalyzerResult,
    val evaluator: OrtEvaluatorRun? = null,
    val labels: Map<String, String> = emptyMap()
)

@kotlinx.serialization.Serializable
data class OrtRepository(
    val vcs: OrtVcsInfo,
    val config: OrtRepositoryConfig
)

@kotlinx.serialization.Serializable
data class OrtRepositoryConfig(
    val excludes: OrtExcludes = OrtExcludes(),
    val curations: OrtCurations = OrtCurations(),
    val resolutions: OrtResolutions = OrtResolutions()
)

@kotlinx.serialization.Serializable
data class OrtExcludes(
    val paths: List<String> = emptyList(),
    val scopes: List<String> = emptyList()
)

@kotlinx.serialization.Serializable
data class OrtCurations(
    val packages: List<OrtPackageCuration> = emptyList(),
    val licenseFindings: List<String> = emptyList()
)

@kotlinx.serialization.Serializable
data class OrtResolutions(
    val issues: List<String> = emptyList(),
    val ruleViolations: List<String> = emptyList(),
    val vulnerabilities: List<String> = emptyList()
)

@kotlinx.serialization.Serializable
data class OrtVcsInfo(
    val type: String,
    val url: String,
    val revision: String,
    val path: String
)

@kotlinx.serialization.Serializable
data class OrtAnalyzerResult(
    val startTime: String,
    val endTime: String,
    val projects: List<OrtProject>,
    val packages: List<OrtPackage>,
    val issues: List<OrtIssue> = emptyList()
)

@kotlinx.serialization.Serializable
data class OrtProject(
    val id: String,
    val definitionFilePath: String,
    val declaredLicenses: List<String>,
    val processedDeclaredLicenses: OrtProcessedLicenses,
    val vcs: OrtVcsInfo,
    val homepageUrl: String,
    val scopeNames: List<String>
)

@kotlinx.serialization.Serializable
data class OrtPackage(
    val id: String,
    val purl: String,
    val declaredLicenses: List<String>,
    val processedDeclaredLicenses: OrtProcessedLicenses,
    val concludedLicense: String?,
    val description: String,
    val homepageUrl: String,
    val binaryArtifact: OrtRemoteArtifact,
    val sourceArtifact: OrtRemoteArtifact,
    val vcs: OrtVcsInfo,
    val curations: List<OrtPackageCuration> = emptyList()
)

@kotlinx.serialization.Serializable
data class OrtProcessedLicenses(
    val spdxExpression: String?,
    val unmapped: List<String>
)

@kotlinx.serialization.Serializable
data class OrtRemoteArtifact(
    val url: String = "",
    val hash: OrtHash = OrtHash()
)

@kotlinx.serialization.Serializable
data class OrtHash(
    val value: String = "",
    val algorithm: String = ""
)

@kotlinx.serialization.Serializable
data class OrtPackageCuration(
    val id: String,
    val curations: OrtCurationData
)

@kotlinx.serialization.Serializable
data class OrtCurationData(
    val concludedLicense: String? = null,
    val comment: String? = null,
    val declaredLicenseMapping: Map<String, String> = emptyMap()
)

@kotlinx.serialization.Serializable
data class OrtIssue(
    val timestamp: String,
    val source: String,
    val message: String,
    val severity: String
)

@kotlinx.serialization.Serializable
data class OrtEvaluatorRun(
    val startTime: String,
    val endTime: String,
    val violations: List<OrtRuleViolation>
)

@kotlinx.serialization.Serializable
data class OrtRuleViolation(
    val rule: String,
    val packageId: String,
    val license: String,
    val licenseSource: String,
    val severity: String,
    val message: String,
    val howToFix: String
)
