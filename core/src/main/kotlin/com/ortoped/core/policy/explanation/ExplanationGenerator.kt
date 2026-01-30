package com.ortoped.core.policy.explanation

import com.ortoped.core.graph.LicenseKnowledgeGraph
import com.ortoped.core.graph.model.*
import com.ortoped.core.policy.PolicyViolation
import com.ortoped.core.policy.Severity
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Generates rich "Why Not?" explanations for policy violations using the Knowledge Graph.
 *
 * This service transforms simple violation messages into comprehensive explanations
 * that help developers understand WHY a license is problematic, not just THAT it is.
 */
class ExplanationGenerator(
    private val graph: LicenseKnowledgeGraph
) {

    /**
     * Generate a fully enhanced violation with explanations and resolutions.
     */
    fun enhance(violation: PolicyViolation): EnhancedViolation {
        val explanations = generateExplanations(violation)
        val resolutions = generateResolutions(violation)

        return EnhancedViolation(
            ruleId = violation.ruleId,
            ruleName = violation.ruleName,
            severity = violation.severity.name,
            dependencyId = violation.dependencyId,
            dependencyName = violation.dependencyName,
            dependencyVersion = violation.dependencyVersion,
            license = violation.license,
            licenseCategory = violation.licenseCategory,
            scope = violation.scope,
            message = violation.message,
            explanations = explanations,
            resolutions = resolutions,
            similarPastDecisions = emptyList() // TODO: Integrate with curation history
        )
    }

    /**
     * Generate explanations for why this violation occurred.
     */
    fun generateExplanations(violation: PolicyViolation): List<ViolationExplanation> {
        val explanations = mutableListOf<ViolationExplanation>()
        val licenseId = normalizeLicenseId(violation.license)
        val licenseNode = graph.getLicense(licenseId)

        logger.debug { "Generating explanations for ${violation.license} (normalized: $licenseId)" }

        // 1. WHY_PROHIBITED - Explain the rule match
        explanations.add(generateWhyProhibitedExplanation(violation, licenseNode))

        // 2. COPYLEFT_RISK - If license has copyleft
        licenseNode?.let { license ->
            if (license.copyleftStrength != CopyleftStrength.NONE) {
                explanations.add(generateCopyleftRiskExplanation(violation, license))
            }
        }

        // 3. OBLIGATION_CONCERN - Key obligations from the license
        val obligations = graph.getObligationsForLicense(licenseId)
        if (obligations.isNotEmpty()) {
            explanations.add(generateObligationExplanation(violation, obligations))
        }

        // 4. RISK_LEVEL - Overall risk assessment
        licenseNode?.let { license ->
            if (license.category.riskLevel >= 3) {
                explanations.add(generateRiskLevelExplanation(violation, license))
            }
        }

        // 5. PROPAGATION_RISK - For copyleft licenses in certain scopes
        licenseNode?.let { license ->
            if (license.copyleftStrength.propagationLevel >= 2 &&
                violation.scope in listOf("compile", "runtime", "implementation")) {
                explanations.add(generatePropagationRiskExplanation(violation, license))
            }
        }

        return explanations
    }

    /**
     * Generate resolution options for the violation.
     */
    fun generateResolutions(violation: PolicyViolation): List<ResolutionOption> {
        val resolutions = mutableListOf<ResolutionOption>()
        val licenseId = normalizeLicenseId(violation.license)
        val licenseNode = graph.getLicense(licenseId)
        val copyleftStrength = licenseNode?.copyleftStrength ?: CopyleftStrength.NONE

        // Resolution 1: Replace with permissive alternative (usually best option)
        resolutions.add(
            ResolutionOption(
                type = ResolutionType.REPLACE_DEPENDENCY,
                title = "Replace with a permissively licensed alternative",
                description = "Find and use an alternative library with a more permissive license like MIT, Apache-2.0, or BSD.",
                effort = EffortLevel.MEDIUM,
                recommended = copyleftStrength.propagationLevel >= 2,
                tradeoffs = listOf(
                    "May require code changes to adapt to different API",
                    "Need to verify the alternative provides equivalent functionality",
                    "Check if the alternative is actively maintained"
                ),
                steps = listOf(
                    "Search for alternatives on libraries.io or npm/maven search",
                    "Compare functionality and API compatibility",
                    "Check license of the alternative (MIT, Apache-2.0, BSD preferred)",
                    "Update dependency and adapt code if needed",
                    "Run tests to verify functionality"
                ),
                alternatives = findAlternatives(violation)
            )
        )

        // Resolution 2: Isolate as service (for copyleft)
        if (copyleftStrength.propagationLevel >= 2) {
            resolutions.add(
                ResolutionOption(
                    type = ResolutionType.ISOLATE_SERVICE,
                    title = "Isolate in a separate service",
                    description = "Run the dependency in a separate microservice behind an API boundary. " +
                            "This creates a process boundary that typically prevents copyleft propagation.",
                    effort = EffortLevel.HIGH,
                    tradeoffs = listOf(
                        "Adds operational complexity (separate deployment)",
                        "Network latency for cross-service calls",
                        "May require significant architectural changes",
                        "Consult legal counsel for AGPL/network copyleft"
                    ),
                    steps = listOf(
                        "Create a new microservice project",
                        "Move the ${violation.license}-licensed code to the service",
                        "Expose functionality via REST API or gRPC",
                        "Update main application to call the service",
                        "Document the service boundary in compliance records"
                    )
                )
            )
        }

        // Resolution 3: Accept obligations (if feasible)
        val obligations = graph.getObligationsForLicense(licenseId)
        if (obligations.isNotEmpty()) {
            val obligationList = obligations.map { it.obligation.name }
            resolutions.add(
                ResolutionOption(
                    type = ResolutionType.ACCEPT_OBLIGATIONS,
                    title = "Accept license obligations",
                    description = "If your use case allows, accept the license obligations and ensure compliance.",
                    effort = calculateObligationEffort(obligations),
                    tradeoffs = listOf(
                        "Must fulfill all license obligations: ${obligationList.joinToString(", ")}",
                        "May affect how you can distribute your software",
                        "Requires ongoing compliance monitoring"
                    ),
                    steps = buildObligationSteps(obligations)
                )
            )
        }

        // Resolution 4: Request policy exception
        resolutions.add(
            ResolutionOption(
                type = ResolutionType.REQUEST_EXCEPTION,
                title = "Request a policy exception",
                description = "If the dependency is critical and alternatives aren't viable, request a formal exception from your compliance team.",
                effort = EffortLevel.LOW,
                tradeoffs = listOf(
                    "Requires justification and approval process",
                    "Exception may be time-limited or conditional",
                    "Creates precedent that may need monitoring"
                ),
                steps = listOf(
                    "Document why this dependency is necessary",
                    "List alternatives considered and why they won't work",
                    "Assess the actual risk given your specific use case",
                    "Submit exception request to compliance team",
                    "If approved, add to policy exemptions list"
                )
            )
        )

        // Resolution 5: Change scope (if applicable)
        if (violation.scope in listOf("compile", "runtime", "implementation")) {
            resolutions.add(
                ResolutionOption(
                    type = ResolutionType.CHANGE_SCOPE,
                    title = "Change dependency scope to development-only",
                    description = "If the dependency is only needed for development/testing, move it to a dev/test scope.",
                    effort = EffortLevel.TRIVIAL,
                    tradeoffs = listOf(
                        "Only works if the dependency isn't needed at runtime",
                        "Changing scope may break your application",
                        "Verify all usages are truly development-time only"
                    ),
                    steps = listOf(
                        "Verify the dependency is not used in production code",
                        "Change scope to testImplementation/devDependencies",
                        "Run full test suite to verify nothing breaks",
                        "Re-run policy evaluation to confirm resolution"
                    )
                )
            )
        }

        // Resolution 6: Contact author for commercial license
        if (copyleftStrength != CopyleftStrength.NONE) {
            resolutions.add(
                ResolutionOption(
                    type = ResolutionType.CONTACT_AUTHOR,
                    title = "Contact author for commercial license",
                    description = "Many copyleft projects offer dual licensing. Contact the author to purchase a commercial license.",
                    effort = EffortLevel.MEDIUM,
                    tradeoffs = listOf(
                        "Ongoing license costs",
                        "Author may not offer commercial licensing",
                        "Need to track license renewal"
                    ),
                    steps = listOf(
                        "Find author/maintainer contact information",
                        "Inquire about commercial licensing options",
                        "Negotiate terms and pricing",
                        "If obtained, update license records"
                    )
                )
            )
        }

        // Sort resolutions: recommended first, then by effort
        return resolutions.sortedWith(
            compareByDescending<ResolutionOption> { it.recommended }
                .thenBy { it.effort.ordinal }
        )
    }

    // =========================================================================
    // Explanation Generators
    // =========================================================================

    private fun generateWhyProhibitedExplanation(
        violation: PolicyViolation,
        licenseNode: LicenseNode?
    ): ViolationExplanation {
        val details = mutableListOf<String>()

        // Explain the rule
        details.add("Your policy rule '${violation.ruleName}' was triggered")

        // Add category context
        violation.licenseCategory?.let {
            details.add("The license ${violation.license} falls into the '$it' category")
        }

        // Add license-specific context from graph
        licenseNode?.let { license ->
            details.add("${license.name} is classified as ${license.category.displayName}")
            if (license.copyleftStrength != CopyleftStrength.NONE) {
                details.add("It has ${license.copyleftStrength.displayName.lowercase()} copyleft requirements")
            }
        }

        // Add scope context
        details.add("This dependency is in the '${violation.scope}' scope, which affects your distributed code")

        return ViolationExplanation(
            type = ExplanationType.WHY_PROHIBITED,
            title = "Why ${violation.license} Violated Your Policy",
            summary = violation.message,
            details = details,
            context = ExplanationContext(
                licenseCategory = licenseNode?.category?.displayName,
                copyleftStrength = licenseNode?.copyleftStrength?.displayName
            )
        )
    }

    private fun generateCopyleftRiskExplanation(
        violation: PolicyViolation,
        license: LicenseNode
    ): ViolationExplanation {
        val details = mutableListOf<String>()

        details.add("${license.name} is a ${license.copyleftStrength.displayName.lowercase()} copyleft license")

        when (license.copyleftStrength) {
            CopyleftStrength.STRONG -> {
                details.add("Strong copyleft requires derivative works to use the same license")
                details.add("If you link this library into your code, your entire application may need to be ${license.spdxId}-licensed")
                details.add("This typically means releasing your source code under the same license")
            }
            CopyleftStrength.NETWORK -> {
                details.add("Network copyleft (like AGPL) extends to users interacting over a network")
                details.add("Even if you don't distribute the software, users accessing it over the internet trigger the copyleft requirements")
                details.add("You may need to provide source code to anyone who uses your service")
            }
            CopyleftStrength.LIBRARY -> {
                details.add("Library-level copyleft (like LGPL) allows linking without licensing your code")
                details.add("However, modifications to the LGPL code itself must be shared")
                details.add("You must allow users to re-link with modified versions of the library")
            }
            CopyleftStrength.FILE -> {
                details.add("File-level copyleft (like MPL) only affects files containing the licensed code")
                details.add("Your own files can remain proprietary")
                details.add("Changes to the MPL-licensed files must be shared")
            }
            else -> {}
        }

        return ViolationExplanation(
            type = ExplanationType.COPYLEFT_RISK,
            title = "Copyleft Risk: ${license.copyleftStrength.displayName}",
            summary = "This license has ${license.copyleftStrength.displayName.lowercase()} copyleft requirements that may affect your project licensing.",
            details = details,
            context = ExplanationContext(
                copyleftStrength = license.copyleftStrength.displayName,
                propagationLevel = license.copyleftStrength.propagationLevel,
                riskLevel = license.category.riskLevel
            )
        )
    }

    private fun generateObligationExplanation(
        violation: PolicyViolation,
        obligations: List<ObligationWithScope>
    ): ViolationExplanation {
        val details = mutableListOf<String>()
        val obligationNames = mutableListOf<String>()

        details.add("Using ${violation.license} comes with the following obligations:")

        obligations.forEach { (obligation, scope) ->
            obligationNames.add(obligation.name)
            val scopeNote = "required for ${scope.displayName.lowercase()}"
            details.add("- ${obligation.name}: ${obligation.description} ($scopeNote)")
        }

        return ViolationExplanation(
            type = ExplanationType.OBLIGATION_CONCERN,
            title = "License Obligations You Must Fulfill",
            summary = "This license requires you to fulfill ${obligations.size} obligation(s): ${obligationNames.joinToString(", ")}",
            details = details,
            context = ExplanationContext(
                triggeredObligations = obligationNames
            )
        )
    }

    private fun generateRiskLevelExplanation(
        violation: PolicyViolation,
        license: LicenseNode
    ): ViolationExplanation {
        val details = mutableListOf<String>()

        details.add("Risk Level: ${license.category.riskLevel}/6 (${license.category.displayName})")

        when {
            license.category == LicenseCategory.PROPRIETARY -> {
                details.add("Proprietary licenses typically restrict modification and redistribution")
                details.add("You may need explicit permission or a commercial license")
            }
            license.category == LicenseCategory.SOURCE_AVAILABLE -> {
                details.add("Source-available licenses may have restrictions on commercial use")
                details.add("Carefully review the specific terms for your use case")
            }
            license.category == LicenseCategory.NETWORK_COPYLEFT -> {
                details.add("Network copyleft has the highest propagation risk")
                details.add("Even network users may trigger source disclosure requirements")
            }
            license.category == LicenseCategory.STRONG_COPYLEFT -> {
                details.add("Strong copyleft can 'infect' your entire codebase")
                details.add("Consider architectural isolation if you must use this dependency")
            }
        }

        if (!license.isOsiApproved) {
            details.add("This license is NOT OSI-approved, which may indicate non-standard terms")
        }
        if (license.isDeprecated) {
            details.add("This license identifier is deprecated; check for updated version")
        }

        return ViolationExplanation(
            type = ExplanationType.RISK_LEVEL,
            title = "Risk Assessment: ${license.category.displayName}",
            summary = "This license has a risk level of ${license.category.riskLevel}/6 due to its ${license.category.displayName.lowercase()} nature.",
            details = details,
            context = ExplanationContext(
                riskLevel = license.category.riskLevel,
                licenseCategory = license.category.displayName
            )
        )
    }

    private fun generatePropagationRiskExplanation(
        violation: PolicyViolation,
        license: LicenseNode
    ): ViolationExplanation {
        val details = mutableListOf<String>()

        details.add("The dependency is in '${violation.scope}' scope, meaning it's included in your distributed application")
        details.add("${license.name} has propagation level ${license.copyleftStrength.propagationLevel}/4")

        when (license.copyleftStrength) {
            CopyleftStrength.STRONG -> {
                details.add("Strong copyleft propagates to ALL code that links with this library")
                details.add("Your entire application would need to be released under ${license.spdxId}")
            }
            CopyleftStrength.NETWORK -> {
                details.add("Network copyleft propagates even when software is only provided as a service")
                details.add("Users of your web application/API could request your source code")
            }
            CopyleftStrength.LIBRARY -> {
                details.add("Library copyleft allows dynamic linking without propagation")
                details.add("However, static linking or modifications would trigger copyleft")
            }
            else -> {}
        }

        return ViolationExplanation(
            type = ExplanationType.PROPAGATION_RISK,
            title = "Copyleft Propagation Risk",
            summary = "Using this dependency in '${violation.scope}' scope may cause license obligations to propagate to your code.",
            details = details,
            context = ExplanationContext(
                copyleftStrength = license.copyleftStrength.displayName,
                propagationLevel = license.copyleftStrength.propagationLevel,
                affectedUseCases = listOf("Distribution", "SaaS", "Embedded")
            )
        )
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun normalizeLicenseId(license: String): String {
        return license.uppercase().replace(" ", "-")
    }

    private fun findAlternatives(violation: PolicyViolation): List<AlternativeDependency> {
        // TODO: Integrate with a dependency alternatives database
        // For now, return common alternatives for well-known libraries
        return when {
            violation.dependencyName.contains("moment", ignoreCase = true) -> listOf(
                AlternativeDependency("date-fns", "3.x", "MIT", "Modern, modular date library"),
                AlternativeDependency("dayjs", "1.x", "MIT", "Lightweight Moment.js alternative")
            )
            violation.dependencyName.contains("request", ignoreCase = true) -> listOf(
                AlternativeDependency("axios", "1.x", "MIT", "Promise-based HTTP client"),
                AlternativeDependency("node-fetch", "3.x", "MIT", "Lightweight fetch implementation")
            )
            violation.dependencyName.contains("underscore", ignoreCase = true) -> listOf(
                AlternativeDependency("lodash", "4.x", "MIT", "Modern utility library"),
                AlternativeDependency("ramda", "0.x", "MIT", "Functional programming utilities")
            )
            else -> emptyList()
        }
    }

    private fun calculateObligationEffort(obligations: List<ObligationWithScope>): EffortLevel {
        val maxEffort = obligations.maxOfOrNull { it.obligation.effort.ordinal } ?: 0
        return when {
            obligations.any { it.obligation.effort == com.ortoped.core.graph.model.EffortLevel.VERY_HIGH } -> EffortLevel.SIGNIFICANT
            obligations.any { it.obligation.effort == com.ortoped.core.graph.model.EffortLevel.HIGH } -> EffortLevel.HIGH
            obligations.size > 3 -> EffortLevel.MEDIUM
            else -> EffortLevel.LOW
        }
    }

    private fun buildObligationSteps(obligations: List<ObligationWithScope>): List<String> {
        val steps = mutableListOf<String>()

        obligations.forEach { (obligation, _) ->
            when (obligation.id) {
                "DISCLOSE_SOURCE" -> {
                    steps.add("Prepare source code for distribution")
                    steps.add("Include build instructions")
                }
                "INCLUDE_LICENSE" -> {
                    steps.add("Copy license text to your distribution")
                    steps.add("Update LICENSES folder or NOTICE file")
                }
                "INCLUDE_COPYRIGHT" -> {
                    steps.add("Preserve copyright notices in source files")
                    steps.add("Add attribution to NOTICE file")
                }
                "STATE_CHANGES" -> {
                    steps.add("Document any modifications made")
                    steps.add("Keep a CHANGELOG of changes")
                }
                "INCLUDE_NOTICE" -> {
                    steps.add("Include NOTICE file from original project")
                    steps.add("Append to your project's NOTICE file")
                }
                "NETWORK_DISCLOSURE" -> {
                    steps.add("Provide source code access link in your service")
                    steps.add("Ensure source is available to network users")
                }
                else -> {
                    steps.add("Fulfill obligation: ${obligation.name}")
                }
            }
        }

        steps.add("Document compliance in your project records")
        return steps.distinct()
    }
}
