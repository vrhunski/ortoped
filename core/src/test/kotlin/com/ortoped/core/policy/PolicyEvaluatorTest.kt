package com.ortoped.core.policy

import com.ortoped.core.model.ScanResult
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolicyEvaluatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadTestScanResult(): ScanResult {
        val jsonText = javaClass.getResourceAsStream("/test-scan-result.json")!!.bufferedReader().readText()
        return json.decodeFromString(jsonText)
    }

    private fun loadTestPolicy(): PolicyConfig {
        val loader = PolicyYamlLoader()
        val policyFile = javaClass.getResource("/test-policy.yaml")!!.toURI().let { java.io.File(it) }
        return loader.load(policyFile)
    }

    @Test
    fun `should detect copyleft violation`() {
        val scanResult = loadTestScanResult()
        val policy = loadTestPolicy()
        val evaluator = PolicyEvaluator(policy)

        val report = evaluator.evaluate(scanResult)

        // Should find GPL violation
        val gplViolation = report.violations.find { it.dependencyId == "Maven:com.example:lib-gpl:1.0.0" }
        assertTrue(gplViolation != null, "Should detect GPL violation")
        assertEquals("no-copyleft", gplViolation.ruleId)
        assertEquals(Severity.ERROR, gplViolation.severity)
    }

    @Test
    fun `should respect exemptions`() {
        val scanResult = loadTestScanResult()
        val policy = loadTestPolicy()

        // Add an exemption for the GPL library
        val modifiedPolicy = policy.copy(
            settings = policy.settings.copy(
                exemptions = listOf(
                    Exemption(
                        dependency = "Maven:com.example:lib-gpl:*",
                        reason = "Exempted for testing",
                        approvedBy = "test@example.com"
                    )
                )
            )
        )

        val evaluator = PolicyEvaluator(modifiedPolicy)
        val report = evaluator.evaluate(scanResult)

        // GPL library should be exempted
        val gplViolation = report.violations.find { it.dependencyId == "Maven:com.example:lib-gpl:1.0.0" }
        assertTrue(gplViolation == null, "GPL library should be exempted")

        // Should be in exempted list
        val exempted = report.exemptedDependencies.find { it.dependencyId == "Maven:com.example:lib-gpl:1.0.0" }
        assertTrue(exempted != null, "Should be in exempted list")
    }

    @Test
    fun `should handle AI suggestions at HIGH confidence`() {
        val scanResult = loadTestScanResult()
        val policy = loadTestPolicy()
        val evaluator = PolicyEvaluator(policy)

        val report = evaluator.evaluate(scanResult)

        // lib-unresolved has HIGH confidence AI suggestion for MIT
        // With acceptHighConfidence=true, it should be treated as resolved
        val unresolvedViolation = report.violations.find {
            it.dependencyId == "Maven:com.example:lib-unresolved:3.0.0"
        }

        // Should not have "no-unknown" violation because AI suggested MIT (HIGH confidence)
        assertTrue(
            unresolvedViolation == null || unresolvedViolation.ruleId != "no-unknown",
            "HIGH confidence AI suggestion should resolve unknown license"
        )
    }

    @Test
    fun `should pass when no violations`() {
        // Create a scan result with only permissive licenses
        val cleanScanResult = ScanResult(
            projectName = "clean-project",
            projectVersion = "1.0.0",
            scanDate = "2025-12-29T12:00:00Z",
            aiEnhanced = false,
            summary = com.ortoped.core.model.ScanSummary(
                totalDependencies = 2,
                resolvedLicenses = 2,
                unresolvedLicenses = 0,
                aiResolvedLicenses = 0,
                licenseDistribution = mapOf("MIT" to 2)
            ),
            dependencies = listOf(
                com.ortoped.core.model.Dependency(
                    id = "Maven:com.example:lib1:1.0.0",
                    name = "lib1",
                    version = "1.0.0",
                    declaredLicenses = listOf("MIT"),
                    detectedLicenses = emptyList(),
                    concludedLicense = "MIT",
                    scope = "compile",
                    isResolved = true
                ),
                com.ortoped.core.model.Dependency(
                    id = "Maven:com.example:lib2:1.0.0",
                    name = "lib2",
                    version = "1.0.0",
                    declaredLicenses = listOf("MIT"),
                    detectedLicenses = emptyList(),
                    concludedLicense = "MIT",
                    scope = "runtime",
                    isResolved = true
                )
            ),
            unresolvedLicenses = emptyList()
        )

        val policy = loadTestPolicy()
        val evaluator = PolicyEvaluator(policy)
        val report = evaluator.evaluate(cleanScanResult)

        assertTrue(report.passed, "Policy should pass with only permissive licenses")
        assertEquals(0, report.summary.errorCount)
        assertEquals(0, report.summary.warningCount)
    }

    @Test
    fun `should fail when errors found`() {
        val scanResult = loadTestScanResult()
        val policy = loadTestPolicy()
        val evaluator = PolicyEvaluator(policy)

        val report = evaluator.evaluate(scanResult)

        // Should fail because GPL violation is an ERROR
        assertFalse(report.passed, "Policy should fail when ERROR violations found")
        assertTrue(report.summary.errorCount > 0, "Should have error count > 0")
    }

    @Test
    fun `should categorize licenses correctly`() {
        val scanResult = loadTestScanResult()
        val policy = loadTestPolicy()
        val evaluator = PolicyEvaluator(policy)

        val report = evaluator.evaluate(scanResult)

        // Check category distribution
        assertTrue(report.summary.licenseDistributionByCategory.containsKey("permissive"))
        assertTrue(report.summary.licenseDistributionByCategory.containsKey("copyleft"))

        // MIT and Apache should be in permissive
        val permissiveCount = report.summary.licenseDistributionByCategory["permissive"] ?: 0
        assertTrue(permissiveCount >= 2, "Should have at least 2 permissive licenses")
    }
}
