package com.ortoped.sbom

/**
 * Configuration for SBOM generation
 */
data class SbomConfig(
    val format: SbomFormat = SbomFormat.CYCLONEDX_JSON,
    val includeAiSuggestions: Boolean = true,
    val toolName: String = "OrtoPed",
    val toolVersion: String = "1.0.0-SNAPSHOT",
    val toolVendor: String = "OrtoPed Project"
)
