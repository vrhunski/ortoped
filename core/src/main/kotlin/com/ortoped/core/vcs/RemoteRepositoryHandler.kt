package com.ortoped.core.vcs

import io.github.oshai.kotlinlogging.KotlinLogging
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Handles cloning and managing remote Git repositories for scanning
 */
class RemoteRepositoryHandler {

    /**
     * Clone a remote repository to a temporary directory
     *
     * @param repoUrl Git repository URL (https or git protocol)
     * @param branch Optional branch name
     * @param tag Optional tag name
     * @param commit Optional commit hash
     * @return Pair of (cloned directory, cleanup function)
     */
    fun cloneRepository(
        repoUrl: String,
        branch: String? = null,
        tag: String? = null,
        commit: String? = null
    ): Pair<File, () -> Unit> {
        logger.info { "Cloning remote repository: $repoUrl" }

        // Create temporary directory for clone
        val tempDir = createTempDirectory("ortoped-clone-")

        try {
            // Get appropriate VCS handler
            val vcs = VersionControlSystem.forType(VcsType.GIT)
                ?: throw IllegalStateException("Git VCS handler not found")

            val normalizedUrl = normalizeGitUrl(repoUrl)

            // 1. Determine the target revision we want to eventually check out.
            var targetRevision = commit ?: tag ?: branch ?: ""
            if (targetRevision.isBlank()) {
                logger.info { "No revision specified, determining default branch for $normalizedUrl..." }
                targetRevision = vcs.getDefaultBranchName(normalizedUrl)
                logger.info { "Default branch is '$targetRevision'." }
            }

            // 2. Initialize the working tree with a blank revision. This is like `git init` and `git remote add`.
            val initVcsInfo = VcsInfo(type = VcsType.GIT, url = normalizedUrl, revision = "")
            logger.info { "Initializing repository in: ${tempDir.absolutePath}" }
            val workingTree = vcs.initWorkingTree(tempDir, initVcsInfo)

            // 3. Update the working tree to the target revision. This is like `git fetch` and `git checkout`.
            logger.info { "Checking out revision '$targetRevision'..." }
            vcs.updateWorkingTree(workingTree, targetRevision)

            logger.info { "Successfully cloned and checked out repository at revision '$targetRevision'" }
            logger.debug { "Working tree at: ${tempDir.absolutePath}" }

            // Return directory and cleanup function
            val cleanup = {
                logger.debug { "Cleaning up cloned repository at: ${tempDir.absolutePath}" }
                tempDir.deleteRecursively()
                logger.info { "Cleanup complete" }
            }

            return tempDir to cleanup

        } catch (e: Exception) {
            // Clean up on error
            tempDir.deleteRecursively()
            throw CloneException("Failed to clone repository: ${e.message}", e)
        }
    }

    /**
     * Check if a string looks like a Git repository URL
     */
    fun isRemoteUrl(input: String): Boolean {
        return input.startsWith("http://") ||
               input.startsWith("https://") ||
               input.startsWith("git@") ||
               input.startsWith("git://") ||
               input.startsWith("ssh://")
    }

    /**
     * Normalize Git URL to standard format
     */
    private fun normalizeGitUrl(url: String): String {
        // Convert git@github.com:user/repo.git to https://github.com/user/repo.git
        if (url.startsWith("git@")) {
            val normalized = url
                .replace("git@", "https://")
                .replaceFirst(":", "/")
            logger.debug { "Normalized git@ URL: $url -> $normalized" }
            return normalized
        }

        // Ensure .git suffix for GitHub URLs
        if (url.contains("github.com") && !url.endsWith(".git")) {
            return "$url.git"
        }

        return url
    }

    /**
     * Create a temporary directory with given prefix
     */
    private fun createTempDirectory(prefix: String): File {
        val tempDir = File(System.getProperty("java.io.tmpdir"), prefix + System.currentTimeMillis())
        tempDir.mkdirs()
        return tempDir
    }
}

/**
 * Exception thrown when repository cloning fails
 */
class CloneException(message: String, cause: Throwable? = null) : Exception(message, cause)