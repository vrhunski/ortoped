package com.ortoped.scanner

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Wrapper around ORT Scanner that downloads and scans source code
 * to extract actual license text and findings.
 */
class SourceCodeScanner(
    private val config: ScannerConfig = ScannerConfig()
) {

    /**
     * Result of scanning a package's source code
     */
    data class PackageScanResult(
        val packageId: String,
        val licenseFindings: List<LicenseTextFinding>,
        val detectedLicenses: List<String>,
        val issues: List<String>
    )

    /**
     * A single license finding with extracted text
     */
    data class LicenseTextFinding(
        val license: String,
        val filePath: String,
        val startLine: Int,
        val endLine: Int,
        val matchedText: String?,
        val score: Float?
    )

    /**
     * Scan packages from an ORT analyzer result to extract license text
     *
     * NOTE: Currently simplified to extract license files without downloading.
     * Source code downloading via ORT Downloader will be added in a future enhancement.
     */
    suspend fun scanPackages(
        ortResult: OrtResult,
        packagesToScan: Set<Identifier>
    ): Map<Identifier, PackageScanResult> = withContext(Dispatchers.IO) {

        if (!config.enabled) {
            logger.info { "Source code scanning disabled" }
            return@withContext emptyMap()
        }

        logger.info { "Starting source code scan for ${packagesToScan.size} packages" }

        // Get packages from ORT result
        val packages = ortResult.getPackages(omitExcluded = false)
            .filter { it.metadata.id in packagesToScan }
            .map { it.metadata }

        val results = if (config.parallelScanning) {
            scanPackagesInParallel(packages)
        } else {
            scanPackagesSequentially(packages)
        }

        logger.info { "Source code scan complete. Scanned ${results.size} packages" }
        results
    }

    private suspend fun scanPackagesInParallel(
        packages: List<Package>
    ): Map<Identifier, PackageScanResult> = coroutineScope {
        packages.chunked(config.maxConcurrentScans).flatMap { chunk ->
            chunk.map { pkg ->
                async {
                    pkg.id to scanSinglePackage(pkg)
                }
            }.awaitAll()
        }.toMap()
    }

    private suspend fun scanPackagesSequentially(
        packages: List<Package>
    ): Map<Identifier, PackageScanResult> {
        val results = mutableMapOf<Identifier, PackageScanResult>()
        packages.forEach { pkg ->
            results[pkg.id] = scanSinglePackage(pkg)
        }
        return results
    }

    /**
     * Scan a single package by extracting license text from source
     *
     * NOTE: Currently simplified - expects source to be in download directory.
     * Will be enhanced with ORT Downloader integration in future version.
     */
    private suspend fun scanSinglePackage(pkg: Package): PackageScanResult = withContext(Dispatchers.IO) {
        logger.info { "Scanning source code for: ${pkg.id}" }

        // Check cache first
        val cacheFile = getCacheFile(pkg.id)
        if (cacheFile.exists()) {
            logger.debug { "Using cached scan result for ${pkg.id}" }
            return@withContext loadFromCache(cacheFile, pkg.id)
        }

        // Look for source directory (could be from manual placement or previous ORT run)
        val sourceDir = File(config.downloadDir, pkg.id.toPath())

        try {
            // Check if source directory exists
            if (!sourceDir.exists() || !sourceDir.isDirectory) {
                logger.debug { "Source directory not found for ${pkg.id}: $sourceDir" }
                return@withContext PackageScanResult(
                    packageId = pkg.id.toCoordinates(),
                    licenseFindings = emptyList(),
                    detectedLicenses = emptyList(),
                    issues = listOf("Source directory not available (download not yet implemented)")
                )
            }

            // Extract license text from source directory
            val licenseFindings = extractLicenseText(sourceDir, pkg.id)

            // Get detected licenses from findings
            val detectedLicenses = licenseFindings
                .map { it.license }
                .distinct()
                .filter { it != "NOASSERTION" }

            val result = PackageScanResult(
                packageId = pkg.id.toCoordinates(),
                licenseFindings = licenseFindings,
                detectedLicenses = detectedLicenses,
                issues = emptyList()
            )

            // Cache the result
            saveToCache(cacheFile, result)

            result

        } catch (e: Exception) {
            logger.error(e) { "Failed to scan package ${pkg.id}" }
            PackageScanResult(
                packageId = pkg.id.toCoordinates(),
                licenseFindings = emptyList(),
                detectedLicenses = emptyList(),
                issues = listOf("Scan failed: ${e.message}")
            )
        }
    }

    /**
     * Extract license text from downloaded source directory
     */
    private fun extractLicenseText(
        sourceDir: File,
        packageId: Identifier
    ): List<LicenseTextFinding> {
        val findings = mutableListOf<LicenseTextFinding>()

        // Common license file patterns
        val licensePatterns = listOf(
            "LICENSE", "LICENSE.txt", "LICENSE.md", "LICENSE.TXT", "LICENSE.MD",
            "LICENCE", "LICENCE.txt", "LICENCE.md",
            "COPYING", "COPYING.txt",
            "LICENSE-MIT", "LICENSE-APACHE", "LICENSE-MIT.txt", "LICENSE-APACHE.txt",
            "MIT-LICENSE", "APACHE-LICENSE", "MIT-LICENSE.txt",
            "license", "licence", "copying"
        )

        // Find license files
        sourceDir.walkTopDown()
            .filter { file ->
                file.isFile && licensePatterns.any { pattern ->
                    file.name.equals(pattern, ignoreCase = true) ||
                            file.name.startsWith(pattern, ignoreCase = true)
                }
            }
            .take(5) // Limit to first 5 license files
            .forEach { licenseFile ->
                try {
                    val content = licenseFile.readText()
                    if (content.isBlank()) {
                        return@forEach
                    }

                    val relativePath = try {
                        licenseFile.relativeTo(sourceDir).path
                    } catch (e: IllegalArgumentException) {
                        licenseFile.name
                    }

                    // Simple license detection from content
                    val detectedLicense = detectLicenseFromText(content)

                    findings.add(
                        LicenseTextFinding(
                            license = detectedLicense ?: "NOASSERTION",
                            filePath = relativePath,
                            startLine = 1,
                            endLine = content.lines().size,
                            matchedText = content.take(4000), // First 4000 chars for AI
                            score = if (detectedLicense != null) 1.0f else 0.5f
                        )
                    )

                    logger.debug { "Found license file: $relativePath -> $detectedLicense" }

                } catch (e: Exception) {
                    logger.warn(e) { "Failed to read license file: ${licenseFile.path}" }
                }
            }

        return findings
    }

    /**
     * Simple pattern-based license detection from text content
     */
    private fun detectLicenseFromText(text: String): String? {
        val upperText = text.uppercase()

        return when {
            "MIT LICENSE" in upperText || "PERMISSION IS HEREBY GRANTED, FREE OF CHARGE" in upperText -> "MIT"
            "APACHE LICENSE" in upperText && "VERSION 2.0" in upperText -> "Apache-2.0"
            "GNU GENERAL PUBLIC LICENSE" in upperText && "VERSION 3" in upperText -> "GPL-3.0-only"
            "GNU GENERAL PUBLIC LICENSE" in upperText && "VERSION 2" in upperText -> "GPL-2.0-only"
            "GNU LESSER GENERAL PUBLIC LICENSE" in upperText && "VERSION 3" in upperText -> "LGPL-3.0-only"
            "GNU LESSER GENERAL PUBLIC LICENSE" in upperText && "VERSION 2.1" in upperText -> "LGPL-2.1-only"
            "BSD 3-CLAUSE" in upperText || "BSD-3-CLAUSE" in upperText || ("REDISTRIBUTION AND USE" in upperText && "THREE" in upperText) -> "BSD-3-Clause"
            "BSD 2-CLAUSE" in upperText || "BSD-2-CLAUSE" in upperText || ("REDISTRIBUTION AND USE" in upperText && "TWO" in upperText) -> "BSD-2-Clause"
            "ISC LICENSE" in upperText && "PERMISSION TO USE, COPY, MODIFY" in upperText -> "ISC"
            "MOZILLA PUBLIC LICENSE" in upperText && "VERSION 2.0" in upperText -> "MPL-2.0"
            "THE UNLICENSE" in upperText || "THIS IS FREE AND UNENCUMBERED SOFTWARE" in upperText -> "Unlicense"
            "CC0" in upperText || ("PUBLIC DOMAIN" in upperText && "WAIVER" in upperText) -> "CC0-1.0"
            "BOOST SOFTWARE LICENSE" in upperText -> "BSL-1.0"
            "ECLIPSE PUBLIC LICENSE" in upperText && "VERSION 2.0" in upperText -> "EPL-2.0"
            "ECLIPSE PUBLIC LICENSE" in upperText && "VERSION 1.0" in upperText -> "EPL-1.0"
            else -> null
        }
    }

    private fun getCacheFile(packageId: Identifier): File {
        val cacheKey = packageId.toCoordinates().replace(":", "_").replace("/", "_")
        return File(config.cacheDir, "$cacheKey.txt")
    }

    private fun saveToCache(cacheFile: File, result: PackageScanResult) {
        try {
            cacheFile.parentFile?.mkdirs()
            // Simple cache: just store the first license finding text
            val text = result.licenseFindings.firstOrNull()?.matchedText ?: ""
            cacheFile.writeText(text)
            logger.debug { "Cached scan result for ${result.packageId}" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to cache scan result" }
        }
    }

    private fun loadFromCache(cacheFile: File, packageId: Identifier): PackageScanResult {
        return try {
            val text = cacheFile.readText()
            val detectedLicense = detectLicenseFromText(text)
            PackageScanResult(
                packageId = packageId.toCoordinates(),
                licenseFindings = if (text.isNotBlank()) {
                    listOf(
                        LicenseTextFinding(
                            license = detectedLicense ?: "NOASSERTION",
                            filePath = "LICENSE (cached)",
                            startLine = 1,
                            endLine = text.lines().size,
                            matchedText = text.take(4000),
                            score = if (detectedLicense != null) 1.0f else 0.5f
                        )
                    )
                } else {
                    emptyList()
                },
                detectedLicenses = if (detectedLicense != null) listOf(detectedLicense) else emptyList(),
                issues = emptyList()
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load from cache" }
            PackageScanResult(
                packageId = packageId.toCoordinates(),
                licenseFindings = emptyList(),
                detectedLicenses = emptyList(),
                issues = listOf("Cache load failed")
            )
        }
    }
}