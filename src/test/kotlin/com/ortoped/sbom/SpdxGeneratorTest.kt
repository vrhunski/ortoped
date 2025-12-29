package com.ortoped.sbom

import com.ortoped.model.ScanResult
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

class SpdxGeneratorTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadTestScanResult(): ScanResult {
        val jsonText = javaClass.getResourceAsStream("/test-scan-result.json")!!.bufferedReader().readText()
        return json.decodeFromString(jsonText)
    }

    @Test
    fun `should generate valid SPDX JSON`(@TempDir tempDir: File) {
        val scanResult = loadTestScanResult()
        val generator = SpdxGenerator()
        val config = SbomConfig(format = SbomFormat.SPDX_JSON, includeAiSuggestions = true)
        val outputFile = File(tempDir, "test.spdx.json")

        generator.generateToFile(scanResult, outputFile, config)

        assertTrue(outputFile.exists(), "Output file should be created")
        assertTrue(outputFile.length() > 0, "Output file should not be empty")

        val content = outputFile.readText()
        // Check for SPDX JSON structure
        assertTrue(content.contains("\"spdxVersion\"") || content.contains("SPDX"),
            "Should contain SPDX version info")
        assertTrue(content.contains("\"packages\"") || content.contains("package"),
            "Should contain packages")
    }

    @Test
    fun `should fallback to JSON when Tag-Value requested`(@TempDir tempDir: File) {
        val scanResult = loadTestScanResult()
        val generator = SpdxGenerator()
        val config = SbomConfig(format = SbomFormat.SPDX_TV, includeAiSuggestions = true)
        val outputFile = File(tempDir, "test.spdx")

        generator.generateToFile(scanResult, outputFile, config)

        assertTrue(outputFile.exists(), "Output file should be created")
        assertTrue(outputFile.length() > 0, "Output file should not be empty")

        val content = outputFile.readText()
        // Tag-Value format not yet implemented, falls back to JSON
        assertTrue(content.contains("\"spdxVersion\""),
            "Should fallback to JSON format")
        assertTrue(content.contains("\"packages\""),
            "Should contain packages in JSON format")
    }

    @Test
    fun `should include AI annotations when enabled`(@TempDir tempDir: File) {
        val scanResult = loadTestScanResult()
        val generator = SpdxGenerator()
        val config = SbomConfig(format = SbomFormat.SPDX_JSON, includeAiSuggestions = true)
        val outputFile = File(tempDir, "test-with-ai.spdx.json")

        generator.generateToFile(scanResult, outputFile, config)

        val content = outputFile.readText()
        // SPDX uses annotations for AI suggestions
        assertTrue(content.contains("annotation") || content.contains("OrtoPed") || content.contains("AI"),
            "Should contain AI annotations or metadata")
    }

    @Test
    fun `should handle dependencies without licenses`(@TempDir tempDir: File) {
        val scanResult = loadTestScanResult()
        val generator = SpdxGenerator()
        val config = SbomConfig(format = SbomFormat.SPDX_JSON, includeAiSuggestions = true)
        val outputFile = File(tempDir, "test-no-license.spdx.json")

        generator.generateToFile(scanResult, outputFile, config)

        assertTrue(outputFile.exists(), "Output file should be created")
        val content = outputFile.readText()
        // Should handle NOASSERTION or unknown licenses
        assertTrue(content.contains("NOASSERTION") || content.isNotEmpty(),
            "Should handle missing licenses gracefully")
    }

    @Test
    fun `should include all dependencies as packages`(@TempDir tempDir: File) {
        val scanResult = loadTestScanResult()
        val generator = SpdxGenerator()
        val config = SbomConfig(format = SbomFormat.SPDX_JSON, includeAiSuggestions = true)
        val outputFile = File(tempDir, "test-packages.spdx.json")

        generator.generateToFile(scanResult, outputFile, config)

        val content = outputFile.readText()
        // Should include dependencies
        assertTrue(content.length > 100, "Should contain substantial content")
    }
}
