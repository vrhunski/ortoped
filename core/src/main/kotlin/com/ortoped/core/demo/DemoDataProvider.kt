package com.ortoped.core.demo

import com.ortoped.core.model.*
import java.time.Instant

/**
 * Provides realistic demo data to showcase AI license resolution without ORT integration
 */
object DemoDataProvider {

    fun generateDemoScanResult(): ScanResult {
        val unresolvedLicenses = listOf(
            UnresolvedLicense(
                dependencyId = "npm:lodash:4.17.21",
                dependencyName = "lodash",
                licenseText = "Copyright JS Foundation and other contributors <https://js.foundation/>\n\nBased on Underscore.js, copyright Jeremy Ashkenas,\nDocumentCloud and Investigative Reporters & Editors <http://underscorejs.org/>",
                licenseUrl = "https://github.com/lodash/lodash/blob/master/LICENSE",
                reason = "License file doesn't contain standard SPDX identifier"
            ),
            UnresolvedLicense(
                dependencyId = "Maven:com.example:custom-lib:2.1.0",
                dependencyName = "custom-lib",
                licenseText = "Permission is hereby granted, free of charge, to any person obtaining a copy of this software...",
                licenseUrl = "https://github.com/example/custom-lib/blob/main/LICENSE.txt",
                reason = "Non-standard license text format"
            ),
            UnresolvedLicense(
                dependencyId = "PyPI:mysterious-package:1.0.0",
                dependencyName = "mysterious-package",
                licenseText = null,
                licenseUrl = "https://pypi.org/project/mysterious-package/",
                reason = "No license information in package metadata"
            ),
            UnresolvedLicense(
                dependencyId = "Cargo:serde_json:1.0.108",
                dependencyName = "serde_json",
                licenseText = "Licensed under either of Apache License, Version 2.0 or MIT license at your option.",
                licenseUrl = "https://github.com/serde-rs/json",
                reason = "Dual license without clear SPDX expression"
            ),
            UnresolvedLicense(
                dependencyId = "npm:react:18.2.0",
                dependencyName = "react",
                licenseText = "MIT License\n\nCopyright (c) Facebook, Inc. and its affiliates.",
                licenseUrl = "https://github.com/facebook/react/blob/main/LICENSE",
                reason = "ORT scanner found conflicting license information"
            )
        )

        val dependencies = createDependencies(unresolvedLicenses)
        val summary = createSummary(dependencies)

        return ScanResult(
            projectName = "demo-project",
            projectVersion = "1.0.0-SNAPSHOT",
            scanDate = Instant.now().toString(),
            dependencies = dependencies,
            summary = summary,
            unresolvedLicenses = unresolvedLicenses,
            aiEnhanced = false
        )
    }

    private fun createDependencies(unresolvedLicenses: List<UnresolvedLicense>): List<Dependency> {
        val resolved = listOf(
            Dependency(
                id = "npm:axios:1.6.0",
                name = "axios",
                version = "1.6.0",
                declaredLicenses = listOf("MIT"),
                detectedLicenses = listOf("MIT"),
                concludedLicense = "MIT",
                scope = "dependencies",
                isResolved = true
            ),
            Dependency(
                id = "Maven:org.springframework:spring-core:6.1.0",
                name = "spring-core",
                version = "6.1.0",
                declaredLicenses = listOf("Apache-2.0"),
                detectedLicenses = listOf("Apache-2.0"),
                concludedLicense = "Apache-2.0",
                scope = "compile",
                isResolved = true
            ),
            Dependency(
                id = "PyPI:requests:2.31.0",
                name = "requests",
                version = "2.31.0",
                declaredLicenses = listOf("Apache-2.0"),
                detectedLicenses = listOf("Apache-2.0"),
                concludedLicense = "Apache-2.0",
                scope = "install",
                isResolved = true
            ),
            Dependency(
                id = "npm:express:4.18.2",
                name = "express",
                version = "4.18.2",
                declaredLicenses = listOf("MIT"),
                detectedLicenses = listOf("MIT"),
                concludedLicense = "MIT",
                scope = "dependencies",
                isResolved = true
            ),
            Dependency(
                id = "Maven:com.google.guava:guava:32.1.3",
                name = "guava",
                version = "32.1.3",
                declaredLicenses = listOf("Apache-2.0"),
                detectedLicenses = listOf("Apache-2.0"),
                concludedLicense = "Apache-2.0",
                scope = "compile",
                isResolved = true
            )
        )

        val unresolved = unresolvedLicenses.map { unresolved ->
            Dependency(
                id = unresolved.dependencyId,
                name = unresolved.dependencyName,
                version = unresolved.dependencyId.substringAfterLast(":"),
                declaredLicenses = emptyList(),
                detectedLicenses = emptyList(),
                concludedLicense = null,
                scope = "dependencies",
                isResolved = false
            )
        }

        return resolved + unresolved
    }

    private fun createSummary(dependencies: List<Dependency>): ScanSummary {
        val licenseDistribution = dependencies
            .filter { it.isResolved }
            .groupingBy { it.concludedLicense ?: "NOASSERTION" }
            .eachCount()

        return ScanSummary(
            totalDependencies = dependencies.size,
            resolvedLicenses = dependencies.count { it.isResolved },
            unresolvedLicenses = dependencies.count { !it.isResolved },
            aiResolvedLicenses = 0,
            licenseDistribution = licenseDistribution
        )
    }
}