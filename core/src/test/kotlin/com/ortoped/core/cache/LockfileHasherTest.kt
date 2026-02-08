package com.ortoped.core.cache

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LockfileHasherTest {

    private val hasher = LockfileHasher()

    @Test
    fun `should return null when no lockfiles found`(@TempDir tempDir: File) {
        // Empty directory - no lockfiles
        assertNull(hasher.hashLockfiles(tempDir))
    }

    @Test
    fun `should hash a single lockfile`(@TempDir tempDir: File) {
        File(tempDir, "package-lock.json").writeText("""{"lockfileVersion": 3}""")

        val hash = hasher.hashLockfiles(tempDir)
        assertNotNull(hash)
        assertEquals(64, hash.length) // SHA-256 hex length
    }

    @Test
    fun `should produce stable hash for same content`(@TempDir tempDir: File) {
        File(tempDir, "package-lock.json").writeText("""{"lockfileVersion": 3}""")

        val hash1 = hasher.hashLockfiles(tempDir)
        val hash2 = hasher.hashLockfiles(tempDir)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `should change hash when lockfile content changes`(@TempDir tempDir: File) {
        val lockfile = File(tempDir, "yarn.lock")
        lockfile.writeText("# yarn lockfile v1\nresolved version 1.0.0")
        val hash1 = hasher.hashLockfiles(tempDir)

        lockfile.writeText("# yarn lockfile v1\nresolved version 2.0.0")
        val hash2 = hasher.hashLockfiles(tempDir)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `should include multiple lockfiles in hash`(@TempDir tempDir: File) {
        File(tempDir, "package-lock.json").writeText("{}")
        val hashWithOne = hasher.hashLockfiles(tempDir)

        File(tempDir, "Cargo.lock").writeText("[metadata]")
        val hashWithTwo = hasher.hashLockfiles(tempDir)

        assertNotEquals(hashWithOne, hashWithTwo)
    }

    @Test
    fun `should exclude node_modules directory`(@TempDir tempDir: File) {
        File(tempDir, "package-lock.json").writeText("""{"name": "root"}""")

        // Add lockfile inside node_modules - should be ignored
        File(tempDir, "node_modules/some-package").mkdirs()
        File(tempDir, "node_modules/some-package/package-lock.json").writeText("""{"name": "nested"}""")

        val hash = hasher.hashLockfiles(tempDir)
        assertNotNull(hash)

        // Remove the node_modules lockfile - hash should remain the same
        File(tempDir, "node_modules").deleteRecursively()
        val hashAfterCleanup = hasher.hashLockfiles(tempDir)

        assertEquals(hash, hashAfterCleanup)
    }

    @Test
    fun `should find lockfiles in subdirectories`(@TempDir tempDir: File) {
        File(tempDir, "packages/frontend").mkdirs()
        File(tempDir, "packages/frontend/package-lock.json").writeText("{}")

        val hash = hasher.hashLockfiles(tempDir)
        assertNotNull(hash)
    }

    @Test
    fun `should recognize all supported lockfile types`(@TempDir tempDir: File) {
        LockfileHasher.LOCKFILE_NAMES.forEach { name ->
            File(tempDir, name).writeText("content-$name")
        }

        val hash = hasher.hashLockfiles(tempDir)
        assertNotNull(hash)
    }
}
