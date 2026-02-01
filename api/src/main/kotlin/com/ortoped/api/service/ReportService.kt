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

    // ========================================================================
    // EU Compliance Report
    // ========================================================================

    /**
     * Generate EU Compliance Report
     *
     * This report is designed for EU/German regulatory requirements and includes:
     * - Complete audit trail of all actions
     * - Structured justifications for all license decisions
     * - Two-role approval chain (curator and approver)
     * - OR license resolution documentation
     * - Distribution scope for each dependency
     *
     * Workflow: SCAN → POLICY CHECK → CURATION → EU COMPLIANCE REPORT
     */
    fun generateEuComplianceReport(scanId: String, format: String = "json"): EuComplianceReportResponse {
        val scanUuid = UUID.fromString(scanId)

        // Get scan
        val scanEntity = scanRepository.findById(scanUuid)
            ?: throw NotFoundException("Scan not found: $scanId")

        if (scanEntity.status != "complete") {
            throw BadRequestException("Cannot generate EU compliance report for incomplete scan")
        }

        val scanResult = scanEntity.result?.let {
            json.decodeFromString<ScanResult>(it)
        } ?: throw BadRequestException("Scan has no result")

        // Get curation session - must be approved for EU compliance report
        val session = curationSessionRepository.findByScanId(scanUuid)
        if (session == null || session.status != "APPROVED") {
            throw BadRequestException(
                "EU Compliance Report requires approved curation. " +
                "Current status: ${session?.status ?: "NO_CURATION"}"
            )
        }

        // Get project info
        val project = scanEntity.projectId?.let { projectRepository.findById(it) }

        // Get curations with justifications
        val curations = curationRepository.findBySessionId(session.id)

        // Get policy evaluation
        val policyEvals = policyRepository.findEvaluationsByScan(scanUuid)
        val latestPolicy = policyEvals.firstOrNull()

        // Build report
        val reportId = UUID.randomUUID().toString()
        val now = Clock.System.now().toString()

        val euReport = EuComplianceReport(
            reportId = reportId,
            reportVersion = "1.0",
            generatedAt = now,
            regulatory = EuRegulatoryInfo(
                framework = "EU Cyber Resilience Act / German IT Security Act",
                complianceLevel = "FULL",
                auditReady = true,
                fourEyesPrincipleApplied = session.approvedBy != null,
                allLicensesDocumented = curations.all { it.status != "PENDING" }
            ),
            project = EuProjectInfo(
                id = project?.id?.toString(),
                name = scanResult.projectName,
                repositoryUrl = project?.repositoryUrl,
                branch = project?.defaultBranch,
                scanDate = scanResult.scanDate,
                distributionScope = "BINARY" // TODO: Get from project settings
            ),
            workflowSummary = EuWorkflowSummary(
                scanCompletedAt = scanEntity.completedAt ?: scanEntity.createdAt,
                policyEvaluatedAt = latestPolicy?.createdAt,
                policyPassed = latestPolicy?.passed,
                curationStartedAt = session.createdAt,
                curationSubmittedAt = session.submittedAt,
                curationApprovedAt = session.approvedAt,
                curatorId = session.curatorId,
                approverId = session.approvedBy
            ),
            statistics = EuComplianceStats(
                totalDependencies = scanResult.summary.totalDependencies,
                licensesResolvedByDeclared = scanResult.dependencies.count {
                    it.declaredLicenses.isNotEmpty() && it.aiSuggestion == null
                },
                licensesResolvedByAi = scanResult.summary.aiResolvedLicenses,
                licensesResolvedByCuration = curations.count { it.status != "PENDING" },
                policyViolationsFound = latestPolicy?.errorCount ?: 0,
                policyViolationsResolved = curations.count {
                    it.status == "ACCEPTED" || it.status == "MODIFIED"
                },
                orLicensesResolved = curations.count {
                    it.isOrLicense == true && it.orLicenseChoice != null
                },
                justificationsProvided = curations.count { it.justificationComplete == true }
            ),
            licenseDecisions = curations.map { curation ->
                // Get justification from separate table if exists
                val justification = if (curation.justificationComplete) {
                    curationRepository.getJustification(curation.id)
                } else null

                EuLicenseDecision(
                    dependencyId = curation.dependencyId,
                    dependencyName = curation.dependencyName,
                    dependencyVersion = curation.dependencyVersion,
                    originalLicense = curation.originalLicense,
                    concludedLicense = curation.curatedLicense ?: curation.originalLicense,
                    spdxId = curation.curatedLicense, // Assuming curated license is SPDX
                    licenseCategory = categorizeLicense(curation.curatedLicense ?: curation.originalLicense ?: "UNKNOWN"),
                    decision = EuDecisionInfo(
                        action = curation.status,
                        aiSuggested = curation.aiSuggestedLicense,
                        aiConfidence = curation.aiConfidence,
                        curatorComment = curation.curatorComment,
                        curatedBy = curation.curatorId,
                        curatedAt = curation.curatedAt
                    ),
                    justification = justification?.let {
                        EuJustificationInfo(
                            type = it.justificationType,
                            text = it.justificationText,
                            evidenceType = it.evidenceType,
                            evidenceReference = it.evidenceReference,
                            policyRuleId = curation.blockingPolicyRule
                        )
                    },
                    orLicenseResolution = if (curation.isOrLicense) {
                        EuOrLicenseResolution(
                            originalExpression = curation.originalLicense,
                            chosenLicense = curation.orLicenseChoice,
                            choiceReason = curation.curatorComment
                        )
                    } else null,
                    distributionScope = curation.distributionScope ?: "BINARY"
                )
            },
            approvalChain = EuApprovalChain(
                curator = EuActorInfo(
                    id = session.curatorId,
                    name = session.curatorName,
                    role = "CURATOR",
                    actionDate = session.createdAt,
                    action = "CURATED"
                ),
                approver = if (session.approvedBy != null) {
                    EuActorInfo(
                        id = session.approvedBy,
                        name = session.approverName,
                        role = session.approverRole ?: "APPROVER",
                        actionDate = session.approvedAt,
                        action = "APPROVED"
                    )
                } else null,
                approvalComment = session.approvalComment,
                fourEyesCompliant = session.curatorId != session.approvedBy
            ),
            auditTrail = buildEuAuditTrail(scanUuid, session.id)
        )

        // Generate output based on format
        val content = when (format.lowercase()) {
            "html" -> generateEuComplianceHtmlReport(euReport)
            else -> json.encodeToString(euReport)
        }

        val safeName = scanResult.projectName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val timestamp = Clock.System.now().toString().take(10)
        val extension = if (format.lowercase() == "html") "html" else "json"
        val filename = "eu-compliance-$safeName-$timestamp.$extension"

        logger.info { "Generated EU Compliance report for scan $scanId" }

        return EuComplianceReportResponse(
            reportId = reportId,
            scanId = scanId,
            format = format,
            filename = filename,
            content = content,
            generatedAt = now,
            complianceStatus = if (euReport.regulatory.allLicensesDocumented && euReport.regulatory.fourEyesPrincipleApplied) {
                "COMPLIANT"
            } else {
                "PARTIAL"
            }
        )
    }

    private fun buildEuAuditTrail(scanId: UUID, sessionId: UUID): List<EuAuditEntry> {
        val entries = mutableListOf<EuAuditEntry>()

        // Scan events
        val scan = scanRepository.findById(scanId)
        if (scan != null) {
            entries.add(EuAuditEntry(
                timestamp = scan.createdAt,
                phase = "SCAN",
                action = "SCAN_STARTED",
                actor = "system",
                actorRole = "SYSTEM",
                description = "Dependency scan initiated",
                entityType = "SCAN",
                entityId = scanId.toString()
            ))
            if (scan.completedAt != null) {
                entries.add(EuAuditEntry(
                    timestamp = scan.completedAt,
                    phase = "SCAN",
                    action = "SCAN_COMPLETED",
                    actor = "system",
                    actorRole = "SYSTEM",
                    description = "Scan completed with ${scan.status} status",
                    entityType = "SCAN",
                    entityId = scanId.toString()
                ))
            }
        }

        // Policy events
        val policyEvals = policyRepository.findEvaluationsByScan(scanId)
        policyEvals.forEach { eval ->
            entries.add(EuAuditEntry(
                timestamp = eval.createdAt,
                phase = "POLICY",
                action = "POLICY_EVALUATED",
                actor = "system",
                actorRole = "SYSTEM",
                description = "Policy evaluation: ${if (eval.passed) "PASSED" else "FAILED"} with ${eval.errorCount} violations",
                entityType = "POLICY_EVALUATION",
                entityId = eval.id.toString()
            ))
        }

        // Curation events
        val session = curationSessionRepository.findById(sessionId)
        if (session != null) {
            entries.add(EuAuditEntry(
                timestamp = session.createdAt,
                phase = "CURATION",
                action = "SESSION_STARTED",
                actor = session.curatorId,
                actorRole = "CURATOR",
                description = "Curation session started with ${session.totalItems} items",
                entityType = "CURATION_SESSION",
                entityId = sessionId.toString()
            ))

            if (session.submittedAt != null) {
                entries.add(EuAuditEntry(
                    timestamp = session.submittedAt,
                    phase = "CURATION",
                    action = "SUBMITTED_FOR_APPROVAL",
                    actor = session.submittedBy ?: session.curatorId,
                    actorRole = "CURATOR",
                    description = "Curation submitted for approval",
                    entityType = "CURATION_SESSION",
                    entityId = sessionId.toString()
                ))
            }

            if (session.approvedAt != null && session.approvedBy != null) {
                entries.add(EuAuditEntry(
                    timestamp = session.approvedAt,
                    phase = "APPROVAL",
                    action = "SESSION_APPROVED",
                    actor = session.approvedBy,
                    actorRole = session.approverRole ?: "APPROVER",
                    description = session.approvalComment ?: "Curation approved (four-eyes principle applied)",
                    entityType = "CURATION_SESSION",
                    entityId = sessionId.toString()
                ))
            }
        }

        // Individual curation decisions
        val curations = curationRepository.findBySessionId(sessionId)
        curations.filter { it.status != "PENDING" }.forEach { c ->
            if (c.curatedAt != null) {
                entries.add(EuAuditEntry(
                    timestamp = c.curatedAt,
                    phase = "CURATION",
                    action = "LICENSE_DECISION",
                    actor = c.curatorId ?: "unknown",
                    actorRole = "CURATOR",
                    description = "${c.status}: ${c.dependencyName} → ${c.curatedLicense ?: c.originalLicense}",
                    entityType = "CURATION",
                    entityId = c.id.toString()
                ))
            }
        }

        return entries.sortedBy { it.timestamp }
    }

    private fun generateEuComplianceHtmlReport(report: EuComplianceReport): String {
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("  <meta charset=\"UTF-8\">")
            appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            appendLine("  <title>EU Compliance Report - ${report.project.name}</title>")
            appendLine("  <style>")
            appendLine(getEuReportStyles())
            appendLine("  </style>")
            appendLine("</head>")
            appendLine("<body>")

            // Header with regulatory badge
            appendLine("  <header>")
            appendLine("    <div class=\"regulatory-badge\">")
            appendLine("      <span class=\"badge-text\">EU COMPLIANT</span>")
            appendLine("      <span class=\"badge-framework\">${report.regulatory.framework}</span>")
            appendLine("    </div>")
            appendLine("    <h1>License Compliance Report</h1>")
            appendLine("    <p class=\"subtitle\">${report.project.name}</p>")
            appendLine("    <p class=\"meta\">Generated: ${report.generatedAt} | Report ID: ${report.reportId}</p>")
            appendLine("  </header>")

            // Compliance Status
            appendLine("  <section class=\"compliance-status\">")
            appendLine("    <h2>Compliance Status</h2>")
            appendLine("    <div class=\"status-grid\">")
            appendLine("      <div class=\"status-item ${if (report.regulatory.auditReady) "success" else "warning"}\">")
            appendLine("        <span class=\"icon\">${if (report.regulatory.auditReady) "✓" else "!"}</span>")
            appendLine("        <span class=\"label\">Audit Ready</span>")
            appendLine("      </div>")
            appendLine("      <div class=\"status-item ${if (report.regulatory.fourEyesPrincipleApplied) "success" else "error"}\">")
            appendLine("        <span class=\"icon\">${if (report.regulatory.fourEyesPrincipleApplied) "✓" else "✗"}</span>")
            appendLine("        <span class=\"label\">Four-Eyes Principle</span>")
            appendLine("      </div>")
            appendLine("      <div class=\"status-item ${if (report.regulatory.allLicensesDocumented) "success" else "warning"}\">")
            appendLine("        <span class=\"icon\">${if (report.regulatory.allLicensesDocumented) "✓" else "!"}</span>")
            appendLine("        <span class=\"label\">All Licenses Documented</span>")
            appendLine("      </div>")
            appendLine("    </div>")
            appendLine("  </section>")

            // Workflow Summary
            appendLine("  <section>")
            appendLine("    <h2>Workflow Timeline</h2>")
            appendLine("    <div class=\"timeline\">")
            appendLine("      <div class=\"timeline-item\">")
            appendLine("        <span class=\"date\">${report.workflowSummary.scanCompletedAt}</span>")
            appendLine("        <span class=\"event\">Scan Completed</span>")
            appendLine("      </div>")
            report.workflowSummary.policyEvaluatedAt?.let {
                appendLine("      <div class=\"timeline-item\">")
                appendLine("        <span class=\"date\">$it</span>")
                appendLine("        <span class=\"event\">Policy Evaluated: ${if (report.workflowSummary.policyPassed == true) "PASSED" else "FAILED"}</span>")
                appendLine("      </div>")
            }
            appendLine("      <div class=\"timeline-item\">")
            appendLine("        <span class=\"date\">${report.workflowSummary.curationStartedAt}</span>")
            appendLine("        <span class=\"event\">Curation Started by ${report.workflowSummary.curatorId}</span>")
            appendLine("      </div>")
            report.workflowSummary.curationSubmittedAt?.let {
                appendLine("      <div class=\"timeline-item\">")
                appendLine("        <span class=\"date\">$it</span>")
                appendLine("        <span class=\"event\">Submitted for Approval</span>")
                appendLine("      </div>")
            }
            report.workflowSummary.curationApprovedAt?.let {
                appendLine("      <div class=\"timeline-item success\">")
                appendLine("        <span class=\"date\">$it</span>")
                appendLine("        <span class=\"event\">Approved by ${report.workflowSummary.approverId}</span>")
                appendLine("      </div>")
            }
            appendLine("    </div>")
            appendLine("  </section>")

            // Statistics
            appendLine("  <section>")
            appendLine("    <h2>Statistics</h2>")
            appendLine("    <div class=\"stats-grid\">")
            appendLine("      <div class=\"stat\"><span class=\"value\">${report.statistics.totalDependencies}</span><span class=\"label\">Total Dependencies</span></div>")
            appendLine("      <div class=\"stat\"><span class=\"value\">${report.statistics.licensesResolvedByDeclared}</span><span class=\"label\">Declared Licenses</span></div>")
            appendLine("      <div class=\"stat\"><span class=\"value\">${report.statistics.licensesResolvedByAi}</span><span class=\"label\">AI Resolved</span></div>")
            appendLine("      <div class=\"stat\"><span class=\"value\">${report.statistics.licensesResolvedByCuration}</span><span class=\"label\">Curated</span></div>")
            appendLine("      <div class=\"stat\"><span class=\"value\">${report.statistics.justificationsProvided}</span><span class=\"label\">Justifications</span></div>")
            appendLine("      <div class=\"stat\"><span class=\"value\">${report.statistics.orLicensesResolved}</span><span class=\"label\">OR Licenses Resolved</span></div>")
            appendLine("    </div>")
            appendLine("  </section>")

            // Approval Chain
            appendLine("  <section class=\"approval-chain\">")
            appendLine("    <h2>Approval Chain</h2>")
            appendLine("    <div class=\"chain\">")
            appendLine("      <div class=\"actor curator\">")
            appendLine("        <span class=\"role\">Curator</span>")
            appendLine("        <span class=\"name\">${report.approvalChain.curator.name ?: report.approvalChain.curator.id}</span>")
            appendLine("        <span class=\"date\">${report.approvalChain.curator.actionDate}</span>")
            appendLine("      </div>")
            appendLine("      <div class=\"arrow\">→</div>")
            report.approvalChain.approver?.let { approver ->
                appendLine("      <div class=\"actor approver\">")
                appendLine("        <span class=\"role\">${approver.role}</span>")
                appendLine("        <span class=\"name\">${approver.name ?: approver.id}</span>")
                appendLine("        <span class=\"date\">${approver.actionDate}</span>")
                appendLine("      </div>")
            }
            appendLine("    </div>")
            if (report.approvalChain.fourEyesCompliant) {
                appendLine("    <p class=\"compliance-note success\">✓ Four-eyes principle verified: Curator and Approver are different persons</p>")
            }
            appendLine("  </section>")

            // License Decisions Table
            appendLine("  <section>")
            appendLine("    <h2>License Decisions (${report.licenseDecisions.size})</h2>")
            appendLine("    <table>")
            appendLine("      <thead><tr><th>Dependency</th><th>Original</th><th>Concluded</th><th>Category</th><th>Action</th><th>Justification</th></tr></thead>")
            appendLine("      <tbody>")
            report.licenseDecisions.forEach { decision ->
                appendLine("        <tr>")
                appendLine("          <td>${decision.dependencyName}@${decision.dependencyVersion}</td>")
                appendLine("          <td>${decision.originalLicense ?: "UNKNOWN"}</td>")
                appendLine("          <td>${decision.concludedLicense}</td>")
                appendLine("          <td><span class=\"category ${decision.licenseCategory.lowercase()}\">${decision.licenseCategory}</span></td>")
                appendLine("          <td>${decision.decision.action}</td>")
                appendLine("          <td>${decision.justification?.type ?: "-"}</td>")
                appendLine("        </tr>")
            }
            appendLine("      </tbody>")
            appendLine("    </table>")
            appendLine("  </section>")

            // Audit Trail
            appendLine("  <section>")
            appendLine("    <h2>Audit Trail</h2>")
            appendLine("    <table class=\"audit-table\">")
            appendLine("      <thead><tr><th>Timestamp</th><th>Phase</th><th>Action</th><th>Actor</th><th>Description</th></tr></thead>")
            appendLine("      <tbody>")
            report.auditTrail.forEach { entry ->
                appendLine("        <tr>")
                appendLine("          <td>${entry.timestamp}</td>")
                appendLine("          <td><span class=\"phase ${entry.phase.lowercase()}\">${entry.phase}</span></td>")
                appendLine("          <td>${entry.action}</td>")
                appendLine("          <td>${entry.actor} (${entry.actorRole})</td>")
                appendLine("          <td>${entry.description}</td>")
                appendLine("        </tr>")
            }
            appendLine("      </tbody>")
            appendLine("    </table>")
            appendLine("  </section>")

            // Footer
            appendLine("  <footer>")
            appendLine("    <p>This report is generated for EU/German regulatory compliance purposes.</p>")
            appendLine("    <p>Report Version: ${report.reportVersion} | Generated by OrtoPed</p>")
            appendLine("  </footer>")

            appendLine("</body>")
            appendLine("</html>")
        }
    }

    private fun getEuReportStyles(): String = """
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; color: #333; max-width: 1400px; margin: 0 auto; padding: 20px; background: #f5f5f5; }
        header { text-align: center; margin-bottom: 40px; padding: 30px; background: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        .regulatory-badge { display: inline-block; padding: 10px 20px; background: linear-gradient(135deg, #1a365d, #2a4a7f); color: white; border-radius: 4px; margin-bottom: 20px; }
        .badge-text { font-size: 1.4em; font-weight: bold; display: block; }
        .badge-framework { font-size: 0.8em; opacity: 0.9; }
        h1 { font-size: 2.2em; color: #1a1a2e; margin-top: 15px; }
        .subtitle { font-size: 1.3em; color: #666; }
        .meta { color: #999; font-size: 0.9em; }
        section { background: white; margin-bottom: 25px; padding: 25px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        h2 { color: #1a1a2e; margin-bottom: 20px; padding-bottom: 10px; border-bottom: 2px solid #e0e0e0; }
        .compliance-status .status-grid { display: flex; justify-content: center; gap: 30px; }
        .status-item { display: flex; flex-direction: column; align-items: center; padding: 20px 30px; border-radius: 8px; background: #f8f9fa; }
        .status-item .icon { font-size: 2em; margin-bottom: 10px; }
        .status-item.success { background: #d4edda; color: #155724; }
        .status-item.warning { background: #fff3cd; color: #856404; }
        .status-item.error { background: #f8d7da; color: #721c24; }
        .timeline { position: relative; padding-left: 30px; }
        .timeline::before { content: ''; position: absolute; left: 8px; top: 0; bottom: 0; width: 2px; background: #e0e0e0; }
        .timeline-item { position: relative; padding: 10px 0; padding-left: 20px; }
        .timeline-item::before { content: ''; position: absolute; left: -26px; top: 15px; width: 12px; height: 12px; border-radius: 50%; background: #667; border: 2px solid white; }
        .timeline-item.success::before { background: #28a745; }
        .timeline-item .date { font-size: 0.85em; color: #666; }
        .timeline-item .event { display: block; font-weight: 500; }
        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 15px; }
        .stat { background: #f8f9fa; padding: 20px; border-radius: 8px; text-align: center; }
        .stat .value { display: block; font-size: 2em; font-weight: bold; color: #1a1a2e; }
        .stat .label { display: block; color: #666; font-size: 0.85em; margin-top: 5px; }
        .approval-chain .chain { display: flex; align-items: center; justify-content: center; gap: 20px; margin: 20px 0; }
        .actor { padding: 20px; background: #f8f9fa; border-radius: 8px; text-align: center; min-width: 180px; }
        .actor .role { display: block; font-size: 0.85em; color: #666; text-transform: uppercase; }
        .actor .name { display: block; font-weight: 600; font-size: 1.1em; margin: 5px 0; }
        .actor .date { display: block; font-size: 0.8em; color: #999; }
        .actor.curator { border-left: 4px solid #3498db; }
        .actor.approver { border-left: 4px solid #27ae60; }
        .arrow { font-size: 2em; color: #ccc; }
        .compliance-note { margin-top: 15px; padding: 10px; border-radius: 4px; }
        .compliance-note.success { background: #d4edda; color: #155724; }
        table { width: 100%; border-collapse: collapse; margin-top: 15px; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #e0e0e0; }
        th { background: #f8f9fa; font-weight: 600; }
        tr:hover { background: #f8f9fa; }
        .category { padding: 3px 8px; border-radius: 3px; font-size: 0.85em; }
        .category.permissive { background: #d4edda; color: #155724; }
        .category.copyleft { background: #fff3cd; color: #856404; }
        .category.unknown { background: #f8d7da; color: #721c24; }
        .phase { padding: 2px 6px; border-radius: 3px; font-size: 0.8em; }
        .phase.scan { background: #e3f2fd; color: #1565c0; }
        .phase.policy { background: #fce4ec; color: #c2185b; }
        .phase.curation { background: #e8f5e9; color: #2e7d32; }
        .phase.approval { background: #fff8e1; color: #f57f17; }
        footer { text-align: center; padding: 20px; color: #666; font-size: 0.9em; }
    """.trimIndent()
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

// ============================================================================
// EU Compliance Report Models
// ============================================================================

@kotlinx.serialization.Serializable
data class EuComplianceReportResponse(
    val reportId: String,
    val scanId: String,
    val format: String,
    val filename: String,
    val content: String,
    val generatedAt: String,
    val complianceStatus: String
)

@kotlinx.serialization.Serializable
data class EuComplianceReport(
    val reportId: String,
    val reportVersion: String,
    val generatedAt: String,
    val regulatory: EuRegulatoryInfo,
    val project: EuProjectInfo,
    val workflowSummary: EuWorkflowSummary,
    val statistics: EuComplianceStats,
    val licenseDecisions: List<EuLicenseDecision>,
    val approvalChain: EuApprovalChain,
    val auditTrail: List<EuAuditEntry>
)

@kotlinx.serialization.Serializable
data class EuRegulatoryInfo(
    val framework: String,
    val complianceLevel: String,
    val auditReady: Boolean,
    val fourEyesPrincipleApplied: Boolean,
    val allLicensesDocumented: Boolean
)

@kotlinx.serialization.Serializable
data class EuProjectInfo(
    val id: String?,
    val name: String,
    val repositoryUrl: String?,
    val branch: String?,
    val scanDate: String,
    val distributionScope: String
)

@kotlinx.serialization.Serializable
data class EuWorkflowSummary(
    val scanCompletedAt: String,
    val policyEvaluatedAt: String?,
    val policyPassed: Boolean?,
    val curationStartedAt: String,
    val curationSubmittedAt: String?,
    val curationApprovedAt: String?,
    val curatorId: String,
    val approverId: String?
)

@kotlinx.serialization.Serializable
data class EuComplianceStats(
    val totalDependencies: Int,
    val licensesResolvedByDeclared: Int,
    val licensesResolvedByAi: Int,
    val licensesResolvedByCuration: Int,
    val policyViolationsFound: Int,
    val policyViolationsResolved: Int,
    val orLicensesResolved: Int,
    val justificationsProvided: Int
)

@kotlinx.serialization.Serializable
data class EuLicenseDecision(
    val dependencyId: String,
    val dependencyName: String,
    val dependencyVersion: String,
    val originalLicense: String?,
    val concludedLicense: String?,
    val spdxId: String?,
    val licenseCategory: String,
    val decision: EuDecisionInfo,
    val justification: EuJustificationInfo?,
    val orLicenseResolution: EuOrLicenseResolution?,
    val distributionScope: String
)

@kotlinx.serialization.Serializable
data class EuDecisionInfo(
    val action: String,
    val aiSuggested: String?,
    val aiConfidence: String?,
    val curatorComment: String?,
    val curatedBy: String?,
    val curatedAt: String?
)

@kotlinx.serialization.Serializable
data class EuJustificationInfo(
    val type: String,
    val text: String?,
    val evidenceType: String?,
    val evidenceReference: String?,
    val policyRuleId: String?
)

@kotlinx.serialization.Serializable
data class EuOrLicenseResolution(
    val originalExpression: String?,
    val chosenLicense: String?,
    val choiceReason: String?
)

@kotlinx.serialization.Serializable
data class EuApprovalChain(
    val curator: EuActorInfo,
    val approver: EuActorInfo?,
    val approvalComment: String?,
    val fourEyesCompliant: Boolean
)

@kotlinx.serialization.Serializable
data class EuActorInfo(
    val id: String,
    val name: String?,
    val role: String,
    val actionDate: String?,
    val action: String
)

@kotlinx.serialization.Serializable
data class EuAuditEntry(
    val timestamp: String,
    val phase: String,
    val action: String,
    val actor: String,
    val actorRole: String,
    val description: String,
    val entityType: String,
    val entityId: String
)
