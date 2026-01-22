package com.ortoped.core.sbom

/**
 * Supported SBOM output formats
 */
enum class SbomFormat(val extension: String, val displayName: String) {
    CYCLONEDX_JSON("cdx.json", "CycloneDX JSON"),
    CYCLONEDX_XML("cdx.xml", "CycloneDX XML"),
    SPDX_JSON("spdx.json", "SPDX JSON"),
    SPDX_TV("spdx", "SPDX Tag-Value")
}
