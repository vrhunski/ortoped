package com.ortoped.core.curation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ORT-compatible curation format.
 * ID format: "Type:namespace:name:version" (e.g., "Maven:com.example:lib:1.0.0")
 */
@Serializable
data class OrtCuration(
    val id: String,
    @SerialName("concluded_license")
    val concludedLicense: String,
    val comment: String? = null
)

/**
 * Wrapper for ORT-compatible curations YAML file.
 */
@Serializable
data class OrtCurationsFile(
    val curations: List<OrtCuration> = emptyList()
)

/**
 * Rich OrtoPed-native curation format with AI provenance tracking.
 */
@Serializable
data class OrtopedNativeCuration(
    @SerialName("package_id")
    val packageId: String,
    @SerialName("concluded_license")
    val concludedLicense: String,
    @SerialName("ai_confidence")
    val aiConfidence: String? = null,
    val reasoning: String? = null,
    @SerialName("curated_by")
    val curatedBy: String? = null,
    @SerialName("curated_at")
    val curatedAt: String? = null,
    val justification: String? = null,
    @SerialName("approved_by")
    val approvedBy: String? = null
)

/**
 * Wrapper for OrtoPed-native curations JSON file.
 */
@Serializable
data class OrtopedNativeCurationsFile(
    val version: String = "1.0",
    val curations: List<OrtopedNativeCuration> = emptyList()
)

/**
 * Unified container holding curations from both ORT and native formats.
 * Provides lookup by dependency ID for fast matching during scans.
 */
data class CurationSet(
    val ortCurations: List<OrtCuration> = emptyList(),
    val nativeCurations: List<OrtopedNativeCuration> = emptyList()
) {
    val isEmpty: Boolean
        get() = ortCurations.isEmpty() && nativeCurations.isEmpty()

    val totalCount: Int
        get() = ortCurations.size + nativeCurations.size

    /**
     * Build a lookup map from dependency ID to concluded license.
     * Native curations take precedence over ORT curations for the same ID.
     */
    fun buildLookupMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        // ORT curations first (lower precedence)
        ortCurations.forEach { map[it.id] = it.concludedLicense }
        // Native curations override (higher precedence)
        nativeCurations.forEach { map[it.packageId] = it.concludedLicense }
        return map
    }
}
