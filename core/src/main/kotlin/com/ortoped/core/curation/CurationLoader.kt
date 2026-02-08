package com.ortoped.core.curation

import com.charleskorn.kaml.Yaml
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Loads curations from ORT YAML and OrtoPed-native JSON files.
 * Supports auto-detection from the `.ortoped/` project directory convention.
 */
class CurationLoader {

    private val yaml = Yaml.default
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val ORT_CURATIONS_FILENAME = "curations.yml"
        const val NATIVE_CURATIONS_FILENAME = "curations.ortoped.json"
        const val ORTOPED_DIR = ".ortoped"
    }

    /**
     * Load ORT-compatible curations from a YAML file.
     * Returns empty list if the file does not exist or is invalid.
     */
    fun loadOrtCurations(file: File): List<OrtCuration> {
        if (!file.exists()) {
            logger.debug { "ORT curations file not found: ${file.absolutePath}" }
            return emptyList()
        }
        return try {
            val content = file.readText().trim()
            if (content.isEmpty()) return emptyList()
            val curationsFile = yaml.decodeFromString(OrtCurationsFile.serializer(), content)
            logger.info { "Loaded ${curationsFile.curations.size} ORT curation(s) from ${file.name}" }
            curationsFile.curations
        } catch (e: Exception) {
            logger.warn { "Failed to parse ORT curations file ${file.absolutePath}: ${e.message}" }
            emptyList()
        }
    }

    /**
     * Load OrtoPed-native curations from a JSON file.
     * Returns empty list if the file does not exist or is invalid.
     */
    fun loadNativeCurations(file: File): List<OrtopedNativeCuration> {
        if (!file.exists()) {
            logger.debug { "Native curations file not found: ${file.absolutePath}" }
            return emptyList()
        }
        return try {
            val content = file.readText().trim()
            if (content.isEmpty()) return emptyList()
            val curationsFile = json.decodeFromString(OrtopedNativeCurationsFile.serializer(), content)
            logger.info { "Loaded ${curationsFile.curations.size} native curation(s) from ${file.name}" }
            curationsFile.curations
        } catch (e: Exception) {
            logger.warn { "Failed to parse native curations file ${file.absolutePath}: ${e.message}" }
            emptyList()
        }
    }

    /**
     * Auto-detect and load curations from a project directory.
     * Looks for `.ortoped/curations.yml` and `.ortoped/curations.ortoped.json`.
     */
    fun loadFromProject(projectDir: File): CurationSet {
        val ortopedDir = File(projectDir, ORTOPED_DIR)
        if (!ortopedDir.isDirectory) {
            logger.debug { "No .ortoped/ directory found in ${projectDir.absolutePath}" }
            return CurationSet()
        }

        val ortCurations = loadOrtCurations(File(ortopedDir, ORT_CURATIONS_FILENAME))
        val nativeCurations = loadNativeCurations(File(ortopedDir, NATIVE_CURATIONS_FILENAME))

        val set = CurationSet(ortCurations, nativeCurations)
        if (!set.isEmpty) {
            logger.info { "Loaded ${set.totalCount} total curation(s) from .ortoped/" }
        }
        return set
    }
}
