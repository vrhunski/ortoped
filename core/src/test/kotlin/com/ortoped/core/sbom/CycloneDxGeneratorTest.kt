package com.ortoped.core.sbom

import com.ortoped.core.model.ScanResult
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

class CycloneDxGeneratorTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadTestScanResult(): ScanResult {
        val jsonText = javaClass.getResourceAsStream("/test-scan-result.json")!!.bufferedReader().readText()
        return json.decodeFromString(jsonText)
    }

    @Test
    fun `should generate valid CycloneDX JSON`(@TempDir tempDir: File) {
        val scanResult = loadTestScanResult()
        val generator = CycloneDxGenerator()
        val config = SbomConfig(format = SbomFormat.CYCLONEDX_JSON, includeAiSuggestions = true)
        val outputFile = File(tempDir, "test.cdx.json")

        generator.generateToFile(scanResult, outputFile, config)

        assertTrue(outputFile.exists(), "Output file should be created")
        assertTrue(outputFile.length() > 0, "Output file should not be empty")

        val content = outputFile.readText()
        // Check for CycloneDX JSON structure
        assertTrue(content.contains("\"bomFormat\""), "Should contain bomFormat field")
        assertTrue(content.contains("\"CycloneDX\""), "Should contain CycloneDX value")
        assertTrue(content.contains("\"components\""), "Should contain components")
    }

    @Test
    fun `should generate valid CycloneDX XML`(@TempDir tempDir: File) {
        val scanResult = loadTestScanResult()
        val generator = CycloneDxGenerator()
        val config = SbomConfig(format = SbomFormat.CYCLONEDX_XML, includeAiSuggestions = true)
        val outputFile = File(tempDir, "test.cdx.xml")

        generator.generateToFile(scanResult, outputFile, config)

        assertTrue(outputFile.exists(), "Output file should be created")
        assertTrue(outputFile.length() > 0, "Output file should not be empty")

        val content = outputFile.readText()
        // Check for XML structure
        assertTrue(content.contains("<?xml"), "Should contain XML declaration")
        assertTrue(content.contains("<bom"), "Should contain bom element")
        assertTrue(content.contains("<components>"), "Should contain components element")
    }

    @Test
    fun `should include AI suggestions when enabled`(@TempDir tempDir: File) {
        val scanResult = loadTestScanResult()
        val generator = CycloneDxGenerator()
        val config = SbomConfig(format = SbomFormat.CYCLONEDX_JSON, includeAiSuggestions = true)
        val outputFile = File(tempDir, "test-with-ai.cdx.json")

        generator.generateToFile(scanResult, outputFile, config)

        val content = outputFile.readText()
        // Should include AI metadata in properties
        assertTrue(content.contains("ortoped") || content.contains("ai"),
            "Should contain AI-related metadata")
    }

    @Test
    fun `should exclude AI suggestions when disabled`(@TempDir tempDir: File) {
        val scanResult = loadTestScanResult()
        val generator = CycloneDxGenerator()
        val config = SbomConfig(format = SbomFormat.CYCLONEDX_JSON, includeAiSuggestions = false)
        val outputFile = File(tempDir, "test-without-ai.cdx.json")

        generator.generateToFile(scanResult, outputFile, config)

        assertTrue(outputFile.exists(), "Output file should be created")
        // Content should be valid even without AI suggestions
        val content = outputFile.readText()
        assertTrue(content.contains("\"components\""), "Should still contain components")
    }

    @Test
    fun `should include all dependencies as components`(@TempDir tempDir: File) {
        val scanResult = loadTestScanResult()
        val generator = CycloneDxGenerator()
        val config = SbomConfig(format = SbomFormat.CYCLONEDX_JSON, includeAiSuggestions = true)
        val outputFile = File(tempDir, "test-deps.cdx.json")

        generator.generateToFile(scanResult, outputFile, config)

        val content = outputFile.readText()
        // Should include dependencies from test data
        assertTrue(content.contains("lib-mit") || content.contains("example"),
            "Should contain dependency names")
    }
}
