package com.ortoped.core.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.security.MessageDigest

private val logger = KotlinLogging.logger {}

/**
 * Computes a deterministic SHA-256 hash over all lockfiles in a project directory.
 * Used to detect whether dependencies have changed since the last scan.
 */
class LockfileHasher {

    companion object {
        val LOCKFILE_NAMES = setOf(
            "package-lock.json",
            "yarn.lock",
            "pnpm-lock.yaml",
            "Cargo.lock",
            "poetry.lock",
            "Pipfile.lock",
            "go.sum",
            "composer.lock",
            "Gemfile.lock",
            "packages.lock.json"
        )

        private val EXCLUDED_DIRS = setOf(
            "node_modules", ".git", "build", "target", ".gradle", ".idea"
        )
    }

    /**
     * Hash all lockfiles found in the project directory.
     * @return SHA-256 hex string, or null if no lockfiles found.
     */
    fun hashLockfiles(projectDir: File): String? {
        val lockfiles = findLockfiles(projectDir)
        if (lockfiles.isEmpty()) {
            logger.debug { "No lockfiles found in ${projectDir.absolutePath}" }
            return null
        }

        logger.debug { "Found ${lockfiles.size} lockfile(s) to hash" }

        val digest = MessageDigest.getInstance("SHA-256")

        // Sort by relative path for determinism
        lockfiles.sortedBy { it.relativeTo(projectDir).path }.forEach { file ->
            try {
                val relativePath = file.relativeTo(projectDir).path
                // Include relative path in hash so renames are detected
                digest.update(relativePath.toByteArray())
                digest.update(file.readBytes())
                logger.debug { "  Hashed: $relativePath" }
            } catch (e: Exception) {
                logger.warn { "Failed to read lockfile ${file.absolutePath}: ${e.message}" }
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun findLockfiles(dir: File): List<File> {
        val result = mutableListOf<File>()
        walkDirectory(dir, result)
        return result
    }

    private fun walkDirectory(dir: File, result: MutableList<File>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (file.name !in EXCLUDED_DIRS) {
                    walkDirectory(file, result)
                }
            } else if (file.name in LOCKFILE_NAMES) {
                result.add(file)
            }
        }
    }
}
