package com.ortoped.core.sbom

import com.ortoped.core.model.ScanResult
import java.io.File

/**
 * Interface for SBOM generators
 */
interface SbomGenerator {
    /**
     * Generate SBOM from scan result
     * @param scanResult The OrtoPed scan result to convert
     * @param config SBOM generation configuration
     * @return SBOM content as string
     */
    fun generate(scanResult: ScanResult, config: SbomConfig): String

    /**
     * Write SBOM to file
     * @param scanResult The OrtoPed scan result to convert
     * @param outputFile Output file path
     * @param config SBOM generation configuration
     */
    fun generateToFile(scanResult: ScanResult, outputFile: File, config: SbomConfig) {
        val content = generate(scanResult, config)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(content)
    }

    /**
     * Supported formats for this generator
     */
    fun supportedFormats(): List<SbomFormat>
}
