package com.ortoped.sbom

import com.ortoped.model.Dependency
import com.ortoped.model.ScanResult
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cyclonedx.Version
import org.cyclonedx.generators.json.BomJsonGenerator
import org.cyclonedx.generators.xml.BomXmlGenerator
import org.cyclonedx.model.*
import java.time.Instant
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * CycloneDX SBOM generator
 */
class CycloneDxGenerator : SbomGenerator {

    override fun supportedFormats(): List<SbomFormat> = listOf(
        SbomFormat.CYCLONEDX_JSON,
        SbomFormat.CYCLONEDX_XML
    )

    override fun generate(scanResult: ScanResult, config: SbomConfig): String {
        logger.info { "Generating ${config.format.displayName} SBOM for ${scanResult.projectName}" }

        val bom = Bom()
        bom.serialNumber = "urn:uuid:${UUID.randomUUID()}"
        bom.version = 1

        // Set metadata
        bom.metadata = createMetadata(scanResult, config)

        // Add components (dependencies)
        val components = scanResult.dependencies.map { dep ->
            createComponent(dep, config.includeAiSuggestions)
        }
        bom.components = components

        // Add dependencies (relationships)
        bom.dependencies = createDependencies(scanResult)

        // Generate output based on format
        return when (config.format) {
            SbomFormat.CYCLONEDX_JSON -> {
                val generator = BomJsonGenerator(bom, Version.VERSION_15)
                generator.toJsonString()
            }
            SbomFormat.CYCLONEDX_XML -> {
                val generator = BomXmlGenerator(bom, Version.VERSION_15)
                generator.toXmlString()
            }
            else -> throw IllegalArgumentException(
                "Unsupported format: ${config.format}"
            )
        }
    }

    private fun createMetadata(
        scanResult: ScanResult,
        config: SbomConfig
    ): Metadata {
        val metadata = Metadata()

        // Set timestamp
        try {
            metadata.timestamp = Date.from(Instant.parse(scanResult.scanDate))
        } catch (e: Exception) {
            metadata.timestamp = Date()
        }

        // Set tool information
        val tool = Tool()
        tool.name = config.toolName
        tool.version = config.toolVersion
        metadata.tools = mutableListOf(tool)

        // Set root component (the project itself)
        val rootComponent = Component()
        rootComponent.type = Component.Type.APPLICATION
        rootComponent.name = scanResult.projectName
        rootComponent.version = scanResult.projectVersion
        rootComponent.bomRef = "root-${scanResult.projectName}"
        metadata.component = rootComponent

        // Add AI enhancement property if applicable
        if (scanResult.aiEnhanced) {
            val aiProperty = Property()
            aiProperty.name = "ortoped:aiEnhanced"
            aiProperty.value = "true"
            metadata.properties = mutableListOf(aiProperty)
        }

        return metadata
    }

    private fun createComponent(
        dependency: Dependency,
        includeAiSuggestions: Boolean
    ): Component {
        val component = Component()

        component.type = Component.Type.LIBRARY
        component.bomRef = dependency.id
        component.name = dependency.name
        component.version = dependency.version
        component.purl = SbomMapper.toPurl(dependency.id)

        // Set scope
        val scope = SbomMapper.toCycloneDxScope(dependency.scope)
        when (scope) {
            "required" -> component.scope = Component.Scope.REQUIRED
            "optional" -> component.scope = Component.Scope.OPTIONAL
            else -> component.scope = Component.Scope.REQUIRED
        }

        // Set license
        val effectiveLicense = dependency.concludedLicense
            ?: dependency.aiSuggestion?.spdxId
            ?: dependency.aiSuggestion?.suggestedLicense

        if (effectiveLicense != null) {
            val licenseChoice = LicenseChoice()
            val license = License()
            license.id = effectiveLicense
            licenseChoice.addLicense(license)
            component.licenses = licenseChoice
        }

        // Add properties
        val properties = mutableListOf<Property>()

        // Add resolved status
        val resolvedProp = Property()
        resolvedProp.name = "ortoped:isResolved"
        resolvedProp.value = dependency.isResolved.toString()
        properties.add(resolvedProp)

        // Add AI suggestions if enabled and available
        if (includeAiSuggestions && dependency.aiSuggestion != null) {
            val suggestion = dependency.aiSuggestion

            properties.add(Property().apply {
                name = "ortoped:ai:suggestedLicense"
                value = suggestion.suggestedLicense
            })
            properties.add(Property().apply {
                name = "ortoped:ai:confidence"
                value = suggestion.confidence
            })
            suggestion.spdxId?.let { spdxId ->
                properties.add(Property().apply {
                    name = "ortoped:ai:spdxId"
                    value = spdxId
                })
            }
            properties.add(Property().apply {
                name = "ortoped:ai:reasoning"
                value = suggestion.reasoning
            })
            if (suggestion.alternatives.isNotEmpty()) {
                properties.add(Property().apply {
                    name = "ortoped:ai:alternatives"
                    value = suggestion.alternatives.joinToString(", ")
                })
            }
        }

        component.properties = properties

        return component
    }

    private fun createDependencies(
        scanResult: ScanResult
    ): List<org.cyclonedx.model.Dependency> {
        // Create root dependency
        val rootDep = org.cyclonedx.model.Dependency("root-${scanResult.projectName}")

        val childDeps = scanResult.dependencies.map { dep ->
            org.cyclonedx.model.Dependency(dep.id)
        }
        rootDep.dependencies = childDeps

        return listOf(rootDep)
    }
}
