package com.ortoped.core.curation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CurationLoaderTest {

    private val loader = CurationLoader()

    @Test
    fun `should load ORT curations from YAML`(@TempDir tempDir: File) {
        val yamlContent = """
            curations:
              - id: "Maven:com.example:lib:1.0.0"
                concluded_license: "MIT"
                comment: "Verified manually"
              - id: "Maven:org.apache:commons-lang3:3.12.0"
                concluded_license: "Apache-2.0"
        """.trimIndent()

        val file = File(tempDir, "curations.yml").apply { writeText(yamlContent) }
        val curations = loader.loadOrtCurations(file)

        assertEquals(2, curations.size)
        assertEquals("Maven:com.example:lib:1.0.0", curations[0].id)
        assertEquals("MIT", curations[0].concludedLicense)
        assertEquals("Verified manually", curations[0].comment)
        assertEquals("Apache-2.0", curations[1].concludedLicense)
    }

    @Test
    fun `should load native curations from JSON`(@TempDir tempDir: File) {
        val jsonContent = """
            {
                "version": "1.0",
                "curations": [
                    {
                        "package_id": "Maven:com.example:lib:1.0.0",
                        "concluded_license": "MIT",
                        "ai_confidence": "HIGH",
                        "reasoning": "License header matches MIT",
                        "curated_by": "claude-ai",
                        "curated_at": "2024-01-01T00:00:00Z"
                    }
                ]
            }
        """.trimIndent()

        val file = File(tempDir, "curations.ortoped.json").apply { writeText(jsonContent) }
        val curations = loader.loadNativeCurations(file)

        assertEquals(1, curations.size)
        assertEquals("Maven:com.example:lib:1.0.0", curations[0].packageId)
        assertEquals("MIT", curations[0].concludedLicense)
        assertEquals("HIGH", curations[0].aiConfidence)
        assertEquals("claude-ai", curations[0].curatedBy)
    }

    @Test
    fun `should auto-detect curations from project directory`(@TempDir tempDir: File) {
        val ortopedDir = File(tempDir, ".ortoped").apply { mkdirs() }

        File(ortopedDir, "curations.yml").writeText("""
            curations:
              - id: "Maven:com.example:lib:1.0.0"
                concluded_license: "MIT"
        """.trimIndent())

        File(ortopedDir, "curations.ortoped.json").writeText("""
            {
                "version": "1.0",
                "curations": [
                    {
                        "package_id": "Maven:org.other:thing:2.0.0",
                        "concluded_license": "Apache-2.0"
                    }
                ]
            }
        """.trimIndent())

        val curationSet = loader.loadFromProject(tempDir)

        assertFalse(curationSet.isEmpty)
        assertEquals(2, curationSet.totalCount)
        assertEquals(1, curationSet.ortCurations.size)
        assertEquals(1, curationSet.nativeCurations.size)
    }

    @Test
    fun `should return empty set when no ortoped directory exists`(@TempDir tempDir: File) {
        val curationSet = loader.loadFromProject(tempDir)

        assertTrue(curationSet.isEmpty)
        assertEquals(0, curationSet.totalCount)
    }

    @Test
    fun `should return empty list for missing files`(@TempDir tempDir: File) {
        val nonExistent = File(tempDir, "does-not-exist.yml")
        assertEquals(emptyList(), loader.loadOrtCurations(nonExistent))
        assertEquals(emptyList(), loader.loadNativeCurations(nonExistent))
    }

    @Test
    fun `should return empty list for invalid YAML`(@TempDir tempDir: File) {
        val file = File(tempDir, "bad.yml").apply { writeText("not: [valid: yaml: curations") }
        val curations = loader.loadOrtCurations(file)
        assertEquals(emptyList(), curations)
    }

    @Test
    fun `should return empty list for invalid JSON`(@TempDir tempDir: File) {
        val file = File(tempDir, "bad.json").apply { writeText("{invalid json}") }
        val curations = loader.loadNativeCurations(file)
        assertEquals(emptyList(), curations)
    }

    @Test
    fun `should build lookup map with native curations taking precedence`() {
        val set = CurationSet(
            ortCurations = listOf(
                OrtCuration("Maven:com.example:lib:1.0.0", "BSD-2-Clause"),
                OrtCuration("Maven:org.other:pkg:2.0.0", "Apache-2.0")
            ),
            nativeCurations = listOf(
                OrtopedNativeCuration("Maven:com.example:lib:1.0.0", "MIT")
            )
        )

        val map = set.buildLookupMap()

        // Native curation should override ORT curation for same ID
        assertEquals("MIT", map["Maven:com.example:lib:1.0.0"])
        assertEquals("Apache-2.0", map["Maven:org.other:pkg:2.0.0"])
    }

    @Test
    fun `should handle empty YAML file`(@TempDir tempDir: File) {
        val file = File(tempDir, "empty.yml").apply { writeText("") }
        assertEquals(emptyList(), loader.loadOrtCurations(file))
    }

    @Test
    fun `should handle empty JSON file`(@TempDir tempDir: File) {
        val file = File(tempDir, "empty.json").apply { writeText("") }
        assertEquals(emptyList(), loader.loadNativeCurations(file))
    }
}
