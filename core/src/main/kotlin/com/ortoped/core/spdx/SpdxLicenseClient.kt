package com.ortoped.core.spdx

import com.ortoped.core.model.SpdxLicense
import com.ortoped.core.model.SpdxValidationResult
import com.ortoped.core.model.LicenseCompatibilityResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Client for fetching and caching SPDX license data from the official SPDX license list.
 * Data is cached in memory with a configurable TTL.
 */
class SpdxLicenseClient(
    private val cacheTtlMinutes: Long = 60 // Default 1 hour cache
) {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // Cache for license list
    private var licenseCache: List<SpdxLicenseData>? = null
    private var licenseCacheTime: Instant? = null

    // Cache for individual license details (keyed by license ID)
    private val licenseDetailCache = ConcurrentHashMap<String, SpdxLicenseDetail>()

    // Normalized ID mapping for fuzzy matching
    private val normalizedIdMap = ConcurrentHashMap<String, String>()

    companion object {
        private const val SPDX_LICENSES_URL =
            "https://raw.githubusercontent.com/spdx/license-list-data/main/json/licenses.json"
        private const val SPDX_LICENSE_DETAIL_URL =
            "https://raw.githubusercontent.com/spdx/license-list-data/main/json/details/"

        // Common license aliases for fuzzy matching
        private val LICENSE_ALIASES = mapOf(
            "apache2" to "Apache-2.0",
            "apache-2" to "Apache-2.0",
            "apache 2.0" to "Apache-2.0",
            "apache license 2.0" to "Apache-2.0",
            "mit license" to "MIT",
            "mit" to "MIT",
            "bsd" to "BSD-3-Clause",
            "bsd-3" to "BSD-3-Clause",
            "bsd 3-clause" to "BSD-3-Clause",
            "bsd-2" to "BSD-2-Clause",
            "gpl" to "GPL-3.0-only",
            "gpl3" to "GPL-3.0-only",
            "gpl-3" to "GPL-3.0-only",
            "gpl2" to "GPL-2.0-only",
            "gpl-2" to "GPL-2.0-only",
            "lgpl" to "LGPL-3.0-only",
            "lgpl3" to "LGPL-3.0-only",
            "lgpl-3" to "LGPL-3.0-only",
            "agpl" to "AGPL-3.0-only",
            "agpl3" to "AGPL-3.0-only",
            "mpl" to "MPL-2.0",
            "mpl2" to "MPL-2.0",
            "isc" to "ISC",
            "unlicense" to "Unlicense",
            "public domain" to "CC0-1.0",
            "cc0" to "CC0-1.0",
            "wtfpl" to "WTFPL",
            "zlib" to "Zlib",
            "artistic" to "Artistic-2.0",
            "epl" to "EPL-2.0",
            "epl2" to "EPL-2.0",
            "cddl" to "CDDL-1.0"
        )

        // License categories for compatibility checking
        private val PERMISSIVE_LICENSES = setOf(
            "MIT", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause", "ISC",
            "Unlicense", "CC0-1.0", "0BSD", "WTFPL", "Zlib"
        )

        private val WEAK_COPYLEFT_LICENSES = setOf(
            "LGPL-2.0-only", "LGPL-2.1-only", "LGPL-3.0-only",
            "MPL-2.0", "EPL-1.0", "EPL-2.0", "CDDL-1.0"
        )

        private val STRONG_COPYLEFT_LICENSES = setOf(
            "GPL-2.0-only", "GPL-2.0-or-later", "GPL-3.0-only", "GPL-3.0-or-later",
            "AGPL-3.0-only", "AGPL-3.0-or-later"
        )
    }

    /**
     * Get all SPDX licenses (cached)
     */
    suspend fun getAllLicenses(): List<SpdxLicense> {
        ensureCacheLoaded()
        return licenseCache?.map { it.toSpdxLicense() } ?: emptyList()
    }

    /**
     * Search licenses by query (name or ID)
     */
    suspend fun searchLicenses(query: String, limit: Int = 20): List<SpdxLicense> {
        ensureCacheLoaded()

        val normalizedQuery = query.lowercase().trim()

        return licenseCache?.filter { license ->
            license.licenseId.lowercase().contains(normalizedQuery) ||
            license.name.lowercase().contains(normalizedQuery)
        }?.take(limit)?.map { it.toSpdxLicense() } ?: emptyList()
    }

    /**
     * Get a specific license by ID
     */
    suspend fun getLicenseById(licenseId: String): SpdxLicense? {
        ensureCacheLoaded()

        // Try exact match first
        val license = licenseCache?.find {
            it.licenseId.equals(licenseId, ignoreCase = true)
        }

        if (license != null) {
            return license.toSpdxLicense()
        }

        // Try normalized match
        val normalizedId = normalizedIdMap[licenseId.lowercase()]
        if (normalizedId != null) {
            return licenseCache?.find {
                it.licenseId.equals(normalizedId, ignoreCase = true)
            }?.toSpdxLicense()
        }

        return null
    }

    /**
     * Get license details including full text
     */
    suspend fun getLicenseDetails(licenseId: String): SpdxLicenseDetail? {
        // Check cache first
        licenseDetailCache[licenseId]?.let { return it }

        // Fetch from SPDX
        try {
            val url = "$SPDX_LICENSE_DETAIL_URL$licenseId.json"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val detail = json.decodeFromString<SpdxLicenseDetail>(response.body())
                licenseDetailCache[licenseId] = detail
                return detail
            }
        } catch (e: Exception) {
            logger.warn { "Failed to fetch license details for $licenseId: ${e.message}" }
        }

        return null
    }

    /**
     * Validate a license ID and return normalized form
     */
    suspend fun validateLicenseId(licenseId: String): SpdxValidationResult {
        ensureCacheLoaded()

        val normalized = normalizeLicenseId(licenseId)
        val exactMatch = licenseCache?.find {
            it.licenseId.equals(licenseId, ignoreCase = true)
        }

        if (exactMatch != null) {
            return SpdxValidationResult(
                isValid = true,
                licenseId = exactMatch.licenseId,
                normalizedId = exactMatch.licenseId,
                suggestions = emptyList(),
                message = if (exactMatch.isDeprecatedLicenseId) {
                    "Warning: This license ID is deprecated"
                } else null
            )
        }

        // Check aliases
        val aliasMatch = LICENSE_ALIASES[licenseId.lowercase()]
        if (aliasMatch != null) {
            val license = licenseCache?.find { it.licenseId == aliasMatch }
            if (license != null) {
                return SpdxValidationResult(
                    isValid = true,
                    licenseId = aliasMatch,
                    normalizedId = aliasMatch,
                    suggestions = emptyList(),
                    message = "Normalized from '$licenseId' to '$aliasMatch'"
                )
            }
        }

        // Find similar licenses for suggestions
        val suggestions = findSimilarLicenses(licenseId, 5)

        return SpdxValidationResult(
            isValid = false,
            licenseId = licenseId,
            normalizedId = null,
            suggestions = suggestions.map { it.licenseId },
            message = "Unknown license ID. Did you mean one of the suggestions?"
        )
    }

    /**
     * Find similar licenses using fuzzy matching
     */
    suspend fun findSimilarLicenses(query: String, limit: Int = 5): List<SpdxLicense> {
        ensureCacheLoaded()

        val normalizedQuery = query.lowercase().replace(Regex("[^a-z0-9]"), "")

        return licenseCache
            ?.map { license ->
                val normalizedId = license.licenseId.lowercase().replace(Regex("[^a-z0-9]"), "")
                val normalizedName = license.name.lowercase().replace(Regex("[^a-z0-9]"), "")
                val score = maxOf(
                    calculateSimilarity(normalizedQuery, normalizedId),
                    calculateSimilarity(normalizedQuery, normalizedName)
                )
                license to score
            }
            ?.filter { it.second > 0.3 } // Minimum similarity threshold
            ?.sortedByDescending { it.second }
            ?.take(limit)
            ?.map { it.first.toSpdxLicense() }
            ?: emptyList()
    }

    /**
     * Check license compatibility
     */
    fun checkCompatibility(license1: String, license2: String): LicenseCompatibilityResult {
        val l1 = license1.uppercase().replace(Regex("[^A-Z0-9-.]"), "")
        val l2 = license2.uppercase().replace(Regex("[^A-Z0-9-.]"), "")

        // Same license is always compatible
        if (l1 == l2) {
            return LicenseCompatibilityResult(
                license1 = license1,
                license2 = license2,
                compatible = true,
                reason = "Same license",
                notes = emptyList()
            )
        }

        val l1Category = getLicenseCategory(license1)
        val l2Category = getLicenseCategory(license2)

        // Permissive + anything is usually fine
        if (l1Category == "permissive" && l2Category == "permissive") {
            return LicenseCompatibilityResult(
                license1, license2, true,
                "Both licenses are permissive",
                listOf("Check specific attribution requirements")
            )
        }

        // Permissive + copyleft - generally ok if copyleft terms are followed
        if ((l1Category == "permissive" && l2Category in listOf("weak_copyleft", "strong_copyleft")) ||
            (l2Category == "permissive" && l1Category in listOf("weak_copyleft", "strong_copyleft"))) {
            return LicenseCompatibilityResult(
                license1, license2, true,
                "Permissive license can be combined with copyleft",
                listOf("Combined work must follow copyleft license terms")
            )
        }

        // Strong copyleft licenses may conflict
        if (l1Category == "strong_copyleft" && l2Category == "strong_copyleft" && l1 != l2) {
            return LicenseCompatibilityResult(
                license1, license2, false,
                "Different strong copyleft licenses may be incompatible",
                listOf("GPL-2.0 and GPL-3.0 are not compatible", "Legal review recommended")
            )
        }

        // Default: unknown compatibility
        return LicenseCompatibilityResult(
            license1, license2, true,
            "Compatibility could not be determined automatically",
            listOf("Legal review recommended for production use")
        )
    }

    /**
     * Get license category
     */
    fun getLicenseCategory(licenseId: String): String {
        return when {
            PERMISSIVE_LICENSES.any { licenseId.contains(it, ignoreCase = true) } -> "permissive"
            WEAK_COPYLEFT_LICENSES.any { licenseId.contains(it, ignoreCase = true) } -> "weak_copyleft"
            STRONG_COPYLEFT_LICENSES.any { licenseId.contains(it, ignoreCase = true) } -> "strong_copyleft"
            licenseId.contains("proprietary", ignoreCase = true) -> "proprietary"
            else -> "unknown"
        }
    }

    /**
     * Get all OSI-approved licenses
     */
    suspend fun getOsiApprovedLicenses(): List<SpdxLicense> {
        ensureCacheLoaded()
        return licenseCache?.filter { it.isOsiApproved }?.map { it.toSpdxLicense() } ?: emptyList()
    }

    /**
     * Get all FSF-approved free licenses
     */
    suspend fun getFsfFreeLicenses(): List<SpdxLicense> {
        ensureCacheLoaded()
        return licenseCache?.filter { it.isFsfLibre }?.map { it.toSpdxLicense() } ?: emptyList()
    }

    // ========================================================================
    // Private methods
    // ========================================================================

    private suspend fun ensureCacheLoaded() {
        val now = Instant.now()
        val cacheTime = licenseCacheTime

        // Check if cache is valid
        if (licenseCache != null && cacheTime != null) {
            val age = Duration.between(cacheTime, now)
            if (age.toMinutes() < cacheTtlMinutes) {
                return // Cache is still valid
            }
        }

        // Load from SPDX
        try {
            logger.info { "Loading SPDX license list..." }

            val request = HttpRequest.newBuilder()
                .uri(URI.create(SPDX_LICENSES_URL))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val licenseList = json.decodeFromString<SpdxLicenseList>(response.body())
                licenseCache = licenseList.licenses
                licenseCacheTime = now

                // Build normalized ID map
                buildNormalizedIdMap()

                logger.info { "Loaded ${licenseList.licenses.size} SPDX licenses" }
            } else {
                logger.error { "Failed to load SPDX licenses: ${response.statusCode()}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error loading SPDX license list" }
            // Keep using stale cache if available
            if (licenseCache == null) {
                licenseCache = emptyList()
            }
        }
    }

    private fun buildNormalizedIdMap() {
        normalizedIdMap.clear()

        // Add aliases
        LICENSE_ALIASES.forEach { (alias, canonical) ->
            normalizedIdMap[alias.lowercase()] = canonical
        }

        // Add all license IDs in lowercase
        licenseCache?.forEach { license ->
            normalizedIdMap[license.licenseId.lowercase()] = license.licenseId
        }
    }

    private fun normalizeLicenseId(licenseId: String): String {
        return normalizedIdMap[licenseId.lowercase()] ?: licenseId
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        if (s1 == s2) return 1.0

        // Simple Levenshtein-based similarity
        val maxLen = maxOf(s1.length, s2.length)
        val distance = levenshteinDistance(s1, s2)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[s1.length][s2.length]
    }

    private fun SpdxLicenseData.toSpdxLicense() = SpdxLicense(
        licenseId = licenseId,
        name = name,
        text = null, // Full text loaded on demand
        isOsiApproved = isOsiApproved,
        isFsfLibre = isFsfLibre,
        isDeprecated = isDeprecatedLicenseId,
        seeAlso = seeAlso
    )
}

// ============================================================================
// Internal data classes for JSON parsing
// ============================================================================

@Serializable
internal data class SpdxLicenseList(
    val licenseListVersion: String,
    val licenses: List<SpdxLicenseData>,
    val releaseDate: String
)

@Serializable
internal data class SpdxLicenseData(
    val licenseId: String,
    val name: String,
    val isOsiApproved: Boolean = false,
    val isFsfLibre: Boolean = false,
    val isDeprecatedLicenseId: Boolean = false,
    val seeAlso: List<String> = emptyList(),
    val reference: String = "",
    val detailsUrl: String = ""
)

@Serializable
data class SpdxLicenseDetail(
    val licenseId: String,
    val name: String,
    val licenseText: String,
    val standardLicenseHeader: String? = null,
    val isOsiApproved: Boolean = false,
    val isFsfLibre: Boolean = false,
    val isDeprecatedLicenseId: Boolean = false,
    val seeAlso: List<String> = emptyList()
)
