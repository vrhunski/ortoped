package com.ortoped.api.service

import com.ortoped.api.model.*
import com.ortoped.core.spdx.SpdxLicenseClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking

private val logger = KotlinLogging.logger {}

/**
 * Service for SPDX license operations
 */
class SpdxService(
    private val spdxClient: SpdxLicenseClient = SpdxLicenseClient()
) {

    /**
     * Search SPDX licenses by query
     */
    suspend fun searchLicenses(
        query: String,
        osiApprovedOnly: Boolean = false,
        limit: Int = 20
    ): SpdxLicenseSearchResponse {
        logger.debug { "Searching SPDX licenses: query=$query, osiOnly=$osiApprovedOnly" }

        val licenses = if (query.isBlank()) {
            if (osiApprovedOnly) {
                spdxClient.getOsiApprovedLicenses().take(limit)
            } else {
                spdxClient.getAllLicenses().take(limit)
            }
        } else {
            val results = spdxClient.searchLicenses(query, limit * 2) // Get extra for filtering
            if (osiApprovedOnly) {
                results.filter { it.isOsiApproved }.take(limit)
            } else {
                results.take(limit)
            }
        }

        return SpdxLicenseSearchResponse(
            licenses = licenses.map { it.toResponse() },
            total = licenses.size
        )
    }

    /**
     * Get a specific license by ID
     */
    suspend fun getLicenseById(licenseId: String): SpdxLicenseDetailResponse? {
        logger.debug { "Getting SPDX license: $licenseId" }

        val license = spdxClient.getLicenseById(licenseId) ?: return null
        val details = spdxClient.getLicenseDetails(license.licenseId)

        return SpdxLicenseDetailResponse(
            licenseId = license.licenseId,
            name = license.name,
            isOsiApproved = license.isOsiApproved,
            isFsfLibre = license.isFsfLibre,
            isDeprecated = license.isDeprecated,
            seeAlso = license.seeAlso,
            licenseText = details?.licenseText,
            standardHeader = details?.standardLicenseHeader,
            category = spdxClient.getLicenseCategory(license.licenseId)
        )
    }

    /**
     * Validate a license ID
     */
    suspend fun validateLicense(licenseId: String): SpdxValidationResponse {
        logger.debug { "Validating license ID: $licenseId" }

        val result = spdxClient.validateLicenseId(licenseId)
        val normalizedId = result.normalizedId

        return SpdxValidationResponse(
            isValid = result.isValid,
            licenseId = result.licenseId,
            normalizedId = normalizedId,
            suggestions = result.suggestions,
            message = result.message,
            license = if (result.isValid && normalizedId != null) {
                spdxClient.getLicenseById(normalizedId)?.toResponse()
            } else null
        )
    }

    /**
     * Validate multiple license IDs
     */
    suspend fun validateLicenses(licenseIds: List<String>): BulkValidationResponse {
        logger.debug { "Validating ${licenseIds.size} license IDs" }

        val results = licenseIds.map { licenseId ->
            val result = spdxClient.validateLicenseId(licenseId)
            BulkValidationItem(
                input = licenseId,
                isValid = result.isValid,
                normalizedId = result.normalizedId,
                message = result.message
            )
        }

        return BulkValidationResponse(
            results = results,
            validCount = results.count { it.isValid },
            invalidCount = results.count { !it.isValid }
        )
    }

    /**
     * Check compatibility between two licenses
     */
    fun checkCompatibility(license1: String, license2: String): LicenseCompatibilityResponse {
        logger.debug { "Checking compatibility: $license1 <-> $license2" }

        val result = spdxClient.checkCompatibility(license1, license2)

        return LicenseCompatibilityResponse(
            license1 = license1,
            license2 = license2,
            compatible = result.compatible,
            reason = result.reason,
            notes = result.notes,
            category1 = spdxClient.getLicenseCategory(license1),
            category2 = spdxClient.getLicenseCategory(license2)
        )
    }

    /**
     * Get all licenses grouped by category
     */
    suspend fun getLicensesByCategory(): LicenseCategoriesResponse {
        logger.debug { "Getting licenses by category" }

        val allLicenses = spdxClient.getAllLicenses()

        val grouped = allLicenses.groupBy { spdxClient.getLicenseCategory(it.licenseId) }

        return LicenseCategoriesResponse(
            categories = grouped.map { (category, licenses) ->
                LicenseCategoryGroup(
                    category = category,
                    description = getCategoryDescription(category),
                    licenses = licenses.map { it.toResponse() },
                    count = licenses.size
                )
            }
        )
    }

    /**
     * Find similar licenses (for autocomplete/suggestions)
     */
    suspend fun findSimilarLicenses(query: String, limit: Int = 5): List<SpdxLicenseInfo> {
        return spdxClient.findSimilarLicenses(query, limit).map { it.toResponse() }
    }

    /**
     * Get popular/common licenses for quick selection
     */
    suspend fun getCommonLicenses(): List<SpdxLicenseInfo> {
        val commonIds = listOf(
            "MIT", "Apache-2.0", "GPL-3.0-only", "GPL-2.0-only",
            "BSD-3-Clause", "BSD-2-Clause", "ISC", "LGPL-3.0-only",
            "MPL-2.0", "AGPL-3.0-only", "Unlicense", "CC0-1.0"
        )

        return commonIds.mapNotNull { id ->
            spdxClient.getLicenseById(id)?.toResponse()
        }
    }

    private fun com.ortoped.core.model.SpdxLicense.toResponse() = SpdxLicenseInfo(
        licenseId = licenseId,
        name = name,
        isOsiApproved = isOsiApproved,
        isFsfLibre = isFsfLibre,
        isDeprecated = isDeprecated,
        seeAlso = seeAlso
    )

    private fun getCategoryDescription(category: String): String = when (category) {
        "permissive" -> "Permissive licenses allow broad use with minimal restrictions"
        "weak_copyleft" -> "Weak copyleft licenses require sharing changes to the library itself"
        "strong_copyleft" -> "Strong copyleft licenses require sharing the entire combined work"
        "proprietary" -> "Proprietary or commercial licenses with usage restrictions"
        else -> "Other or unknown license category"
    }
}

// ============================================================================
// Response Models
// ============================================================================

@kotlinx.serialization.Serializable
data class SpdxLicenseSearchResponse(
    val licenses: List<SpdxLicenseInfo>,
    val total: Int
)

@kotlinx.serialization.Serializable
data class SpdxLicenseDetailResponse(
    val licenseId: String,
    val name: String,
    val isOsiApproved: Boolean,
    val isFsfLibre: Boolean,
    val isDeprecated: Boolean,
    val seeAlso: List<String>,
    val licenseText: String?,
    val standardHeader: String?,
    val category: String
)

@kotlinx.serialization.Serializable
data class SpdxValidationResponse(
    val isValid: Boolean,
    val licenseId: String?,
    val normalizedId: String?,
    val suggestions: List<String>,
    val message: String?,
    val license: SpdxLicenseInfo?
)

@kotlinx.serialization.Serializable
data class BulkValidationResponse(
    val results: List<BulkValidationItem>,
    val validCount: Int,
    val invalidCount: Int
)

@kotlinx.serialization.Serializable
data class BulkValidationItem(
    val input: String,
    val isValid: Boolean,
    val normalizedId: String?,
    val message: String?
)

@kotlinx.serialization.Serializable
data class LicenseCompatibilityResponse(
    val license1: String,
    val license2: String,
    val compatible: Boolean,
    val reason: String?,
    val notes: List<String>,
    val category1: String,
    val category2: String
)

@kotlinx.serialization.Serializable
data class LicenseCategoriesResponse(
    val categories: List<LicenseCategoryGroup>
)

@kotlinx.serialization.Serializable
data class LicenseCategoryGroup(
    val category: String,
    val description: String,
    val licenses: List<SpdxLicenseInfo>,
    val count: Int
)

// Request models
@kotlinx.serialization.Serializable
data class ValidateLicensesRequest(
    val licenseIds: List<String>
)
