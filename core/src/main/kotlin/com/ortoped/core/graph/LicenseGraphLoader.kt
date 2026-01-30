package com.ortoped.core.graph

import com.ortoped.core.graph.model.*
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Loads initial license knowledge graph data with standard licenses,
 * obligations, rights, and compatibility rules.
 */
class LicenseGraphLoader(private val graph: LicenseKnowledgeGraph) {

    /**
     * Load all standard data into the graph
     */
    fun loadAll() {
        logger.info { "Loading license knowledge graph data..." }

        loadObligations()
        loadRights()
        loadLicenses()
        loadCompatibilityRules()
        loadLicenseObligationEdges()
        loadLicenseRightEdges()
        loadUseCases()

        val stats = graph.getStatistics()
        logger.info {
            "Graph loaded: ${stats.totalLicenses} licenses, " +
            "${stats.totalObligations} obligations, " +
            "${stats.totalRights} rights, " +
            "${stats.totalEdges} edges"
        }
    }

    // =========================================================================
    // Obligations
    // =========================================================================

    private fun loadObligations() {
        val standardObligations = listOf(
            ObligationNode(
                id = StandardObligations.ATTRIBUTION,
                name = "Attribution",
                description = "Include copyright notice and license text in distributions",
                triggerCondition = TriggerCondition.ON_DISTRIBUTION,
                effort = EffortLevel.LOW,
                examples = listOf(
                    "Include LICENSE file in distribution",
                    "Add copyright notice in documentation",
                    "Display attribution in 'About' dialog or credits",
                    "Include attribution in README"
                )
            ),
            ObligationNode(
                id = StandardObligations.SOURCE_DISCLOSURE,
                name = "Source Code Disclosure",
                description = "Make source code available to recipients of the software",
                triggerCondition = TriggerCondition.ON_DISTRIBUTION,
                effort = EffortLevel.HIGH,
                examples = listOf(
                    "Provide source alongside binary distribution",
                    "Offer source via written offer for 3 years",
                    "Host source on public repository",
                    "Include source in downloadable archive"
                )
            ),
            ObligationNode(
                id = StandardObligations.STATE_CHANGES,
                name = "State Changes",
                description = "Document modifications made to the original code",
                triggerCondition = TriggerCondition.ON_MODIFICATION,
                effort = EffortLevel.MEDIUM,
                examples = listOf(
                    "Add modification notice to changed files",
                    "Maintain changelog of modifications",
                    "Document changes in commit messages",
                    "Include modification date and description"
                )
            ),
            ObligationNode(
                id = StandardObligations.SAME_LICENSE,
                name = "Same License (Copyleft)",
                description = "Derivative works must be distributed under the same license",
                triggerCondition = TriggerCondition.ON_DERIVATIVE,
                effort = EffortLevel.VERY_HIGH,
                examples = listOf(
                    "Release entire application under GPL",
                    "Apply copyleft license to derivative work",
                    "Cannot combine with incompatible licenses",
                    "All linked code must be under same terms"
                )
            ),
            ObligationNode(
                id = StandardObligations.NETWORK_DISCLOSURE,
                name = "Network Source Disclosure (AGPL)",
                description = "Provide source to users accessing software via network",
                triggerCondition = TriggerCondition.ON_NETWORK_USE,
                effort = EffortLevel.VERY_HIGH,
                examples = listOf(
                    "Provide download link in SaaS application",
                    "Include source access in terms of service",
                    "Offer source code to all network users",
                    "Display 'Source Code' link in web interface"
                )
            ),
            ObligationNode(
                id = StandardObligations.PATENT_GRANT,
                name = "Patent Grant",
                description = "Grant patent license to users of the software",
                triggerCondition = TriggerCondition.ALWAYS,
                effort = EffortLevel.TRIVIAL,
                examples = listOf(
                    "Automatic with Apache-2.0 license",
                    "Includes defensive termination clause",
                    "Covers patents necessary to use the software"
                )
            ),
            ObligationNode(
                id = StandardObligations.NOTICE_FILE,
                name = "NOTICE File Preservation",
                description = "Include NOTICE file if present in the original distribution",
                triggerCondition = TriggerCondition.ON_DISTRIBUTION,
                effort = EffortLevel.LOW,
                examples = listOf(
                    "Copy NOTICE file to distribution",
                    "Include NOTICE content in attribution",
                    "Preserve all notices from original"
                )
            ),
            ObligationNode(
                id = StandardObligations.INCLUDE_LICENSE,
                name = "Include License Text",
                description = "Include full license text with distribution",
                triggerCondition = TriggerCondition.ON_DISTRIBUTION,
                effort = EffortLevel.LOW,
                examples = listOf(
                    "Include LICENSE file",
                    "Embed license in documentation",
                    "Include in package metadata"
                )
            ),
            ObligationNode(
                id = StandardObligations.INCLUDE_COPYRIGHT,
                name = "Include Copyright Notice",
                description = "Preserve and include original copyright notices",
                triggerCondition = TriggerCondition.ON_DISTRIBUTION,
                effort = EffortLevel.LOW,
                examples = listOf(
                    "Keep copyright headers in source files",
                    "Include copyright in documentation",
                    "Display copyright in application"
                )
            ),
            ObligationNode(
                id = StandardObligations.DISCLOSE_SOURCE,
                name = "Disclose Source (Weak Copyleft)",
                description = "Disclose source for modified files only (file-level copyleft)",
                triggerCondition = TriggerCondition.ON_MODIFICATION,
                effort = EffortLevel.MEDIUM,
                examples = listOf(
                    "Provide source for modified MPL files",
                    "New files can remain proprietary",
                    "Only changed files require disclosure"
                )
            )
        )

        standardObligations.forEach { graph.addObligation(it) }
        logger.debug { "Loaded ${standardObligations.size} obligations" }
    }

    // =========================================================================
    // Rights
    // =========================================================================

    private fun loadRights() {
        val standardRights = listOf(
            RightNode(
                id = StandardRights.COMMERCIAL_USE,
                name = "Commercial Use",
                description = "Use the software for commercial purposes",
                scope = RightScope.UNLIMITED
            ),
            RightNode(
                id = StandardRights.MODIFY,
                name = "Modify",
                description = "Make changes and modifications to the source code",
                scope = RightScope.UNLIMITED
            ),
            RightNode(
                id = StandardRights.DISTRIBUTE,
                name = "Distribute",
                description = "Distribute copies of the software",
                scope = RightScope.UNLIMITED
            ),
            RightNode(
                id = StandardRights.PRIVATE_USE,
                name = "Private Use",
                description = "Use the software privately without any obligations",
                scope = RightScope.UNLIMITED
            ),
            RightNode(
                id = StandardRights.PATENT_USE,
                name = "Patent Use",
                description = "Use patents covered by the license",
                scope = RightScope.LIMITED
            ),
            RightNode(
                id = StandardRights.SUBLICENSE,
                name = "Sublicense",
                description = "Grant sublicenses to others",
                scope = RightScope.LIMITED
            )
        )

        standardRights.forEach { graph.addRight(it) }
        logger.debug { "Loaded ${standardRights.size} rights" }
    }

    // =========================================================================
    // Licenses
    // =========================================================================

    private fun loadLicenses() {
        // Public Domain
        loadPublicDomainLicenses()

        // Permissive
        loadPermissiveLicenses()

        // Weak Copyleft
        loadWeakCopyleftLicenses()

        // Strong Copyleft
        loadStrongCopyleftLicenses()

        // Network Copyleft
        loadNetworkCopyleftLicenses()
    }

    private fun loadPublicDomainLicenses() {
        val licenses = listOf(
            LicenseNode(
                id = "CC0-1.0",
                spdxId = "CC0-1.0",
                name = "Creative Commons Zero v1.0 Universal",
                category = LicenseCategory.PUBLIC_DOMAIN,
                copyleftStrength = CopyleftStrength.NONE,
                isOsiApproved = false,
                isFsfFree = true,
                version = "1.0",
                family = LicenseFamilies.CC
            ),
            LicenseNode(
                id = "UNLICENSE",
                spdxId = "Unlicense",
                name = "The Unlicense",
                category = LicenseCategory.PUBLIC_DOMAIN,
                copyleftStrength = CopyleftStrength.NONE,
                isOsiApproved = true,
                isFsfFree = true
            ),
            LicenseNode(
                id = "WTFPL",
                spdxId = "WTFPL",
                name = "Do What The F*ck You Want To Public License",
                category = LicenseCategory.PUBLIC_DOMAIN,
                copyleftStrength = CopyleftStrength.NONE,
                isOsiApproved = false,
                isFsfFree = true
            ),
            LicenseNode(
                id = "0BSD",
                spdxId = "0BSD",
                name = "BSD Zero Clause License",
                category = LicenseCategory.PUBLIC_DOMAIN,
                copyleftStrength = CopyleftStrength.NONE,
                isOsiApproved = true,
                isFsfFree = true,
                family = LicenseFamilies.BSD
            )
        )
        licenses.forEach { graph.addLicense(it) }
    }

    private fun loadPermissiveLicenses() {
        val licenses = listOf(
            LicenseNode(
                id = "MIT",
                spdxId = "MIT",
                name = "MIT License",
                category = LicenseCategory.PERMISSIVE,
                copyleftStrength = CopyleftStrength.NONE,
                isOsiApproved = true,
                isFsfFree = true,
                family = LicenseFamilies.MIT
            ),
            LicenseNode(
                id = "APACHE-2.0",
                spdxId = "Apache-2.0",
                name = "Apache License 2.0",
                category = LicenseCategory.PERMISSIVE,
                copyleftStrength = CopyleftStrength.NONE,
                isOsiApproved = true,
                isFsfFree = true,
                version = "2.0",
                family = LicenseFamilies.APACHE
            ),
            LicenseNode(
                id = "BSD-2-CLAUSE",
                spdxId = "BSD-2-Clause",
                name = "BSD 2-Clause \"Simplified\" License",
                category = LicenseCategory.PERMISSIVE,
                copyleftStrength = CopyleftStrength.NONE,
                isOsiApproved = true,
                isFsfFree = true,
                family = LicenseFamilies.BSD
            ),
            LicenseNode(
                id = "BSD-3-CLAUSE",
                spdxId = "BSD-3-Clause",
                name = "BSD 3-Clause \"New\" or \"Revised\" License",
                category = LicenseCategory.PERMISSIVE,
                copyleftStrength = CopyleftStrength.NONE,
                isOsiApproved = true,
                isFsfFree = true,
                family = LicenseFamilies.BSD
            ),
            LicenseNode(
                id = "ISC",
                spdxId = "ISC",
                name = "ISC License",
                category = LicenseCategory.PERMISSIVE,
                copyleftStrength = CopyleftStrength.NONE,
                isOsiApproved = true,
                isFsfFree = true
            ),
            LicenseNode(
                id = "ZLIB",
                spdxId = "Zlib",
                name = "zlib License",
                category = LicenseCategory.PERMISSIVE,
                copyleftStrength = CopyleftStrength.NONE,
                isOsiApproved = true,
                isFsfFree = true
            ),
            LicenseNode(
                id = "X11",
                spdxId = "X11",
                name = "X11 License",
                category = LicenseCategory.PERMISSIVE,
                copyleftStrength = CopyleftStrength.NONE,
                isOsiApproved = false,
                isFsfFree = true
            ),
            LicenseNode(
                id = "ARTISTIC-2.0",
                spdxId = "Artistic-2.0",
                name = "Artistic License 2.0",
                category = LicenseCategory.PERMISSIVE,
                copyleftStrength = CopyleftStrength.NONE,
                isOsiApproved = true,
                isFsfFree = true,
                version = "2.0"
            ),
            LicenseNode(
                id = "BSL-1.0",
                spdxId = "BSL-1.0",
                name = "Boost Software License 1.0",
                category = LicenseCategory.PERMISSIVE,
                copyleftStrength = CopyleftStrength.NONE,
                isOsiApproved = true,
                isFsfFree = true,
                version = "1.0"
            )
        )
        licenses.forEach { graph.addLicense(it) }
    }

    private fun loadWeakCopyleftLicenses() {
        val licenses = listOf(
            // LGPL family
            LicenseNode(
                id = "LGPL-2.0-ONLY",
                spdxId = "LGPL-2.0-only",
                name = "GNU Lesser General Public License v2.0 only",
                category = LicenseCategory.WEAK_COPYLEFT,
                copyleftStrength = CopyleftStrength.LIBRARY,
                isOsiApproved = true,
                isFsfFree = true,
                version = "2.0",
                family = LicenseFamilies.LGPL
            ),
            LicenseNode(
                id = "LGPL-2.0-OR-LATER",
                spdxId = "LGPL-2.0-or-later",
                name = "GNU Lesser General Public License v2.0 or later",
                category = LicenseCategory.WEAK_COPYLEFT,
                copyleftStrength = CopyleftStrength.LIBRARY,
                isOsiApproved = true,
                isFsfFree = true,
                version = "2.0",
                family = LicenseFamilies.LGPL
            ),
            LicenseNode(
                id = "LGPL-2.1-ONLY",
                spdxId = "LGPL-2.1-only",
                name = "GNU Lesser General Public License v2.1 only",
                category = LicenseCategory.WEAK_COPYLEFT,
                copyleftStrength = CopyleftStrength.LIBRARY,
                isOsiApproved = true,
                isFsfFree = true,
                version = "2.1",
                family = LicenseFamilies.LGPL
            ),
            LicenseNode(
                id = "LGPL-2.1-OR-LATER",
                spdxId = "LGPL-2.1-or-later",
                name = "GNU Lesser General Public License v2.1 or later",
                category = LicenseCategory.WEAK_COPYLEFT,
                copyleftStrength = CopyleftStrength.LIBRARY,
                isOsiApproved = true,
                isFsfFree = true,
                version = "2.1",
                family = LicenseFamilies.LGPL
            ),
            LicenseNode(
                id = "LGPL-3.0-ONLY",
                spdxId = "LGPL-3.0-only",
                name = "GNU Lesser General Public License v3.0 only",
                category = LicenseCategory.WEAK_COPYLEFT,
                copyleftStrength = CopyleftStrength.LIBRARY,
                isOsiApproved = true,
                isFsfFree = true,
                version = "3.0",
                family = LicenseFamilies.LGPL
            ),
            LicenseNode(
                id = "LGPL-3.0-OR-LATER",
                spdxId = "LGPL-3.0-or-later",
                name = "GNU Lesser General Public License v3.0 or later",
                category = LicenseCategory.WEAK_COPYLEFT,
                copyleftStrength = CopyleftStrength.LIBRARY,
                isOsiApproved = true,
                isFsfFree = true,
                version = "3.0",
                family = LicenseFamilies.LGPL
            ),
            // MPL
            LicenseNode(
                id = "MPL-2.0",
                spdxId = "MPL-2.0",
                name = "Mozilla Public License 2.0",
                category = LicenseCategory.WEAK_COPYLEFT,
                copyleftStrength = CopyleftStrength.FILE,
                isOsiApproved = true,
                isFsfFree = true,
                version = "2.0",
                family = LicenseFamilies.MPL
            ),
            LicenseNode(
                id = "MPL-1.1",
                spdxId = "MPL-1.1",
                name = "Mozilla Public License 1.1",
                category = LicenseCategory.WEAK_COPYLEFT,
                copyleftStrength = CopyleftStrength.FILE,
                isOsiApproved = true,
                isFsfFree = true,
                version = "1.1",
                family = LicenseFamilies.MPL
            ),
            // EPL
            LicenseNode(
                id = "EPL-1.0",
                spdxId = "EPL-1.0",
                name = "Eclipse Public License 1.0",
                category = LicenseCategory.WEAK_COPYLEFT,
                copyleftStrength = CopyleftStrength.FILE,
                isOsiApproved = true,
                isFsfFree = true,
                version = "1.0",
                family = LicenseFamilies.EPL
            ),
            LicenseNode(
                id = "EPL-2.0",
                spdxId = "EPL-2.0",
                name = "Eclipse Public License 2.0",
                category = LicenseCategory.WEAK_COPYLEFT,
                copyleftStrength = CopyleftStrength.FILE,
                isOsiApproved = true,
                isFsfFree = true,
                version = "2.0",
                family = LicenseFamilies.EPL
            ),
            // CDDL
            LicenseNode(
                id = "CDDL-1.0",
                spdxId = "CDDL-1.0",
                name = "Common Development and Distribution License 1.0",
                category = LicenseCategory.WEAK_COPYLEFT,
                copyleftStrength = CopyleftStrength.FILE,
                isOsiApproved = true,
                isFsfFree = true,
                version = "1.0",
                family = LicenseFamilies.CDDL
            ),
            LicenseNode(
                id = "CDDL-1.1",
                spdxId = "CDDL-1.1",
                name = "Common Development and Distribution License 1.1",
                category = LicenseCategory.WEAK_COPYLEFT,
                copyleftStrength = CopyleftStrength.FILE,
                isOsiApproved = false,
                isFsfFree = true,
                version = "1.1",
                family = LicenseFamilies.CDDL
            )
        )
        licenses.forEach { graph.addLicense(it) }
    }

    private fun loadStrongCopyleftLicenses() {
        val licenses = listOf(
            LicenseNode(
                id = "GPL-2.0-ONLY",
                spdxId = "GPL-2.0-only",
                name = "GNU General Public License v2.0 only",
                category = LicenseCategory.STRONG_COPYLEFT,
                copyleftStrength = CopyleftStrength.STRONG,
                isOsiApproved = true,
                isFsfFree = true,
                version = "2.0",
                family = LicenseFamilies.GPL
            ),
            LicenseNode(
                id = "GPL-2.0-OR-LATER",
                spdxId = "GPL-2.0-or-later",
                name = "GNU General Public License v2.0 or later",
                category = LicenseCategory.STRONG_COPYLEFT,
                copyleftStrength = CopyleftStrength.STRONG,
                isOsiApproved = true,
                isFsfFree = true,
                version = "2.0",
                family = LicenseFamilies.GPL
            ),
            LicenseNode(
                id = "GPL-3.0-ONLY",
                spdxId = "GPL-3.0-only",
                name = "GNU General Public License v3.0 only",
                category = LicenseCategory.STRONG_COPYLEFT,
                copyleftStrength = CopyleftStrength.STRONG,
                isOsiApproved = true,
                isFsfFree = true,
                version = "3.0",
                family = LicenseFamilies.GPL
            ),
            LicenseNode(
                id = "GPL-3.0-OR-LATER",
                spdxId = "GPL-3.0-or-later",
                name = "GNU General Public License v3.0 or later",
                category = LicenseCategory.STRONG_COPYLEFT,
                copyleftStrength = CopyleftStrength.STRONG,
                isOsiApproved = true,
                isFsfFree = true,
                version = "3.0",
                family = LicenseFamilies.GPL
            )
        )
        licenses.forEach { graph.addLicense(it) }
    }

    private fun loadNetworkCopyleftLicenses() {
        val licenses = listOf(
            LicenseNode(
                id = "AGPL-3.0-ONLY",
                spdxId = "AGPL-3.0-only",
                name = "GNU Affero General Public License v3.0 only",
                category = LicenseCategory.NETWORK_COPYLEFT,
                copyleftStrength = CopyleftStrength.NETWORK,
                isOsiApproved = true,
                isFsfFree = true,
                version = "3.0",
                family = LicenseFamilies.AGPL
            ),
            LicenseNode(
                id = "AGPL-3.0-OR-LATER",
                spdxId = "AGPL-3.0-or-later",
                name = "GNU Affero General Public License v3.0 or later",
                category = LicenseCategory.NETWORK_COPYLEFT,
                copyleftStrength = CopyleftStrength.NETWORK,
                isOsiApproved = true,
                isFsfFree = true,
                version = "3.0",
                family = LicenseFamilies.AGPL
            )
        )
        licenses.forEach { graph.addLicense(it) }
    }

    // =========================================================================
    // Compatibility Rules
    // =========================================================================

    private fun loadCompatibilityRules() {
        loadPermissiveCompatibility()
        loadGPLIncompatibilities()
        loadApacheGPLCompatibility()
        loadLGPLCompatibility()
        loadMPLCompatibility()
    }

    private fun loadPermissiveCompatibility() {
        // All permissive licenses are compatible with each other
        val permissive = listOf(
            "MIT", "APACHE-2.0", "BSD-2-CLAUSE", "BSD-3-CLAUSE", "ISC",
            "CC0-1.0", "UNLICENSE", "0BSD", "WTFPL", "ZLIB", "BSL-1.0"
        )

        for (i in permissive.indices) {
            for (j in i + 1 until permissive.size) {
                graph.addEdge(CompatibilityEdge(
                    id = createCompatibilityEdgeId(permissive[i], permissive[j]),
                    sourceId = permissive[i],
                    targetId = permissive[j],
                    compatibility = CompatibilityLevel.FULL,
                    direction = CompatibilityDirection.BIDIRECTIONAL,
                    conditions = listOf("Maintain attribution notices from both licenses"),
                    notes = listOf("Permissive licenses are fully compatible with each other"),
                    sources = listOf("OSI License Compatibility Guidelines")
                ))
            }
        }
        logger.debug { "Loaded permissive compatibility rules" }
    }

    private fun loadGPLIncompatibilities() {
        // GPL-2.0-only and GPL-3.0 incompatibility
        graph.addEdge(CompatibilityEdge(
            id = "GPL-2.0-ONLY--GPL-3.0-ONLY",
            sourceId = "GPL-2.0-ONLY",
            targetId = "GPL-3.0-ONLY",
            compatibility = CompatibilityLevel.INCOMPATIBLE,
            direction = CompatibilityDirection.BIDIRECTIONAL,
            notes = listOf(
                "GPL-2.0-only and GPL-3.0-only are not compatible",
                "GPL-3.0 added patent provisions that GPL-2.0-only code cannot accept",
                "Check if the GPL-2.0 code is 'or later' licensed - that IS compatible with GPL-3.0"
            ),
            sources = listOf("https://www.gnu.org/licenses/gpl-faq.html#AllCompatibility")
        ))

        // GPL-2.0-or-later IS compatible with GPL-3.0
        graph.addEdge(CompatibilityEdge(
            id = "GPL-2.0-OR-LATER--GPL-3.0-ONLY",
            sourceId = "GPL-2.0-OR-LATER",
            targetId = "GPL-3.0-ONLY",
            compatibility = CompatibilityLevel.CONDITIONAL,
            direction = CompatibilityDirection.FORWARD,
            conditions = listOf("Combined work must be GPL-3.0"),
            notes = listOf("GPL-2.0-or-later code can be used under GPL-3.0 terms"),
            sources = listOf("https://www.gnu.org/licenses/gpl-faq.html#AllCompatibility")
        ))

        logger.debug { "Loaded GPL incompatibility rules" }
    }

    private fun loadApacheGPLCompatibility() {
        // Apache-2.0 and GPL-2.0-only incompatibility
        graph.addEdge(CompatibilityEdge(
            id = "APACHE-2.0--GPL-2.0-ONLY",
            sourceId = "APACHE-2.0",
            targetId = "GPL-2.0-ONLY",
            compatibility = CompatibilityLevel.INCOMPATIBLE,
            direction = CompatibilityDirection.BIDIRECTIONAL,
            notes = listOf(
                "Apache-2.0 patent termination clause is incompatible with GPL-2.0",
                "Apache-2.0 IS compatible with GPL-3.0"
            ),
            sources = listOf("https://www.apache.org/licenses/GPL-compatibility.html")
        ))

        // Apache-2.0 and GPL-3.0 compatibility (one-way)
        graph.addEdge(CompatibilityEdge(
            id = "APACHE-2.0--GPL-3.0-ONLY",
            sourceId = "APACHE-2.0",
            targetId = "GPL-3.0-ONLY",
            compatibility = CompatibilityLevel.ONE_WAY,
            direction = CompatibilityDirection.FORWARD,
            conditions = listOf("Combined work must be distributed under GPL-3.0"),
            notes = listOf(
                "Apache-2.0 code can be included in GPL-3.0 projects",
                "Result must be distributed under GPL-3.0",
                "GPL-3.0 code cannot be relicensed under Apache-2.0"
            ),
            sources = listOf(
                "https://www.gnu.org/licenses/license-list.html#apache2",
                "https://www.apache.org/licenses/GPL-compatibility.html"
            )
        ))

        // Apache-2.0 and AGPL-3.0 compatibility
        graph.addEdge(CompatibilityEdge(
            id = "APACHE-2.0--AGPL-3.0-ONLY",
            sourceId = "APACHE-2.0",
            targetId = "AGPL-3.0-ONLY",
            compatibility = CompatibilityLevel.ONE_WAY,
            direction = CompatibilityDirection.FORWARD,
            conditions = listOf(
                "Combined work must be distributed under AGPL-3.0",
                "Network disclosure requirements apply"
            ),
            notes = listOf("Apache-2.0 code can be included in AGPL-3.0 projects")
        ))

        logger.debug { "Loaded Apache/GPL compatibility rules" }
    }

    private fun loadLGPLCompatibility() {
        // LGPL with GPL - compatible (GPL dominates)
        val lgplVersions = listOf("LGPL-2.0-ONLY", "LGPL-2.1-ONLY", "LGPL-3.0-ONLY",
                                  "LGPL-2.0-OR-LATER", "LGPL-2.1-OR-LATER", "LGPL-3.0-OR-LATER")
        val gplVersions = listOf("GPL-2.0-ONLY", "GPL-3.0-ONLY", "GPL-2.0-OR-LATER", "GPL-3.0-OR-LATER")

        // LGPL-3.0 with GPL-3.0
        graph.addEdge(CompatibilityEdge(
            id = "LGPL-3.0-ONLY--GPL-3.0-ONLY",
            sourceId = "LGPL-3.0-ONLY",
            targetId = "GPL-3.0-ONLY",
            compatibility = CompatibilityLevel.ONE_WAY,
            direction = CompatibilityDirection.FORWARD,
            conditions = listOf("Combined work must follow GPL-3.0 terms"),
            notes = listOf("LGPL code can be combined with GPL, result is GPL")
        ))

        logger.debug { "Loaded LGPL compatibility rules" }
    }

    private fun loadMPLCompatibility() {
        // MPL-2.0 with GPL-3.0 (secondary license compatibility)
        graph.addEdge(CompatibilityEdge(
            id = "MPL-2.0--GPL-3.0-ONLY",
            sourceId = "MPL-2.0",
            targetId = "GPL-3.0-ONLY",
            compatibility = CompatibilityLevel.CONDITIONAL,
            direction = CompatibilityDirection.FORWARD,
            conditions = listOf(
                "MPL-2.0 code can be relicensed under GPL-3.0",
                "This is explicitly allowed by MPL-2.0 Section 3.3"
            ),
            notes = listOf("MPL-2.0 has explicit GPL compatibility through secondary license clause"),
            sources = listOf("https://www.mozilla.org/en-US/MPL/2.0/FAQ/")
        ))

        logger.debug { "Loaded MPL compatibility rules" }
    }

    // =========================================================================
    // License-Obligation Edges
    // =========================================================================

    private fun loadLicenseObligationEdges() {
        // MIT obligations
        addObligationEdges("MIT", listOf(
            Triple(StandardObligations.ATTRIBUTION, TriggerCondition.ON_DISTRIBUTION, ObligationScope.COMPONENT),
            Triple(StandardObligations.INCLUDE_LICENSE, TriggerCondition.ON_DISTRIBUTION, ObligationScope.COMPONENT),
            Triple(StandardObligations.INCLUDE_COPYRIGHT, TriggerCondition.ON_DISTRIBUTION, ObligationScope.COMPONENT)
        ))

        // Apache-2.0 obligations
        addObligationEdges("APACHE-2.0", listOf(
            Triple(StandardObligations.ATTRIBUTION, TriggerCondition.ON_DISTRIBUTION, ObligationScope.COMPONENT),
            Triple(StandardObligations.STATE_CHANGES, TriggerCondition.ON_MODIFICATION, ObligationScope.MODIFIED_FILES),
            Triple(StandardObligations.NOTICE_FILE, TriggerCondition.ON_DISTRIBUTION, ObligationScope.COMPONENT),
            Triple(StandardObligations.INCLUDE_LICENSE, TriggerCondition.ON_DISTRIBUTION, ObligationScope.COMPONENT),
            Triple(StandardObligations.PATENT_GRANT, TriggerCondition.ALWAYS, ObligationScope.COMPONENT)
        ))

        // BSD obligations
        listOf("BSD-2-CLAUSE", "BSD-3-CLAUSE").forEach { license ->
            addObligationEdges(license, listOf(
                Triple(StandardObligations.ATTRIBUTION, TriggerCondition.ON_DISTRIBUTION, ObligationScope.COMPONENT),
                Triple(StandardObligations.INCLUDE_COPYRIGHT, TriggerCondition.ON_DISTRIBUTION, ObligationScope.COMPONENT)
            ))
        }

        // GPL-2.0 obligations
        listOf("GPL-2.0-ONLY", "GPL-2.0-OR-LATER").forEach { license ->
            addObligationEdges(license, listOf(
                Triple(StandardObligations.ATTRIBUTION, TriggerCondition.ON_DISTRIBUTION, ObligationScope.DERIVATIVE_WORK),
                Triple(StandardObligations.SOURCE_DISCLOSURE, TriggerCondition.ON_DISTRIBUTION, ObligationScope.DERIVATIVE_WORK),
                Triple(StandardObligations.SAME_LICENSE, TriggerCondition.ON_DERIVATIVE, ObligationScope.DERIVATIVE_WORK),
                Triple(StandardObligations.STATE_CHANGES, TriggerCondition.ON_MODIFICATION, ObligationScope.MODIFIED_FILES),
                Triple(StandardObligations.INCLUDE_LICENSE, TriggerCondition.ON_DISTRIBUTION, ObligationScope.DERIVATIVE_WORK)
            ))
        }

        // GPL-3.0 obligations
        listOf("GPL-3.0-ONLY", "GPL-3.0-OR-LATER").forEach { license ->
            addObligationEdges(license, listOf(
                Triple(StandardObligations.ATTRIBUTION, TriggerCondition.ON_DISTRIBUTION, ObligationScope.DERIVATIVE_WORK),
                Triple(StandardObligations.SOURCE_DISCLOSURE, TriggerCondition.ON_DISTRIBUTION, ObligationScope.DERIVATIVE_WORK),
                Triple(StandardObligations.SAME_LICENSE, TriggerCondition.ON_DERIVATIVE, ObligationScope.DERIVATIVE_WORK),
                Triple(StandardObligations.STATE_CHANGES, TriggerCondition.ON_MODIFICATION, ObligationScope.MODIFIED_FILES),
                Triple(StandardObligations.INCLUDE_LICENSE, TriggerCondition.ON_DISTRIBUTION, ObligationScope.DERIVATIVE_WORK),
                Triple(StandardObligations.PATENT_GRANT, TriggerCondition.ALWAYS, ObligationScope.DERIVATIVE_WORK)
            ))
        }

        // AGPL-3.0 obligations (same as GPL-3.0 + network disclosure)
        listOf("AGPL-3.0-ONLY", "AGPL-3.0-OR-LATER").forEach { license ->
            addObligationEdges(license, listOf(
                Triple(StandardObligations.ATTRIBUTION, TriggerCondition.ON_DISTRIBUTION, ObligationScope.DERIVATIVE_WORK),
                Triple(StandardObligations.SOURCE_DISCLOSURE, TriggerCondition.ON_DISTRIBUTION, ObligationScope.DERIVATIVE_WORK),
                Triple(StandardObligations.SAME_LICENSE, TriggerCondition.ON_DERIVATIVE, ObligationScope.DERIVATIVE_WORK),
                Triple(StandardObligations.STATE_CHANGES, TriggerCondition.ON_MODIFICATION, ObligationScope.MODIFIED_FILES),
                Triple(StandardObligations.NETWORK_DISCLOSURE, TriggerCondition.ON_NETWORK_USE, ObligationScope.DERIVATIVE_WORK),
                Triple(StandardObligations.INCLUDE_LICENSE, TriggerCondition.ON_DISTRIBUTION, ObligationScope.DERIVATIVE_WORK),
                Triple(StandardObligations.PATENT_GRANT, TriggerCondition.ALWAYS, ObligationScope.DERIVATIVE_WORK)
            ))
        }

        // LGPL obligations
        listOf("LGPL-2.0-ONLY", "LGPL-2.1-ONLY", "LGPL-3.0-ONLY",
               "LGPL-2.0-OR-LATER", "LGPL-2.1-OR-LATER", "LGPL-3.0-OR-LATER").forEach { license ->
            addObligationEdges(license, listOf(
                Triple(StandardObligations.ATTRIBUTION, TriggerCondition.ON_DISTRIBUTION, ObligationScope.COMPONENT),
                Triple(StandardObligations.SOURCE_DISCLOSURE, TriggerCondition.ON_DISTRIBUTION, ObligationScope.COMPONENT),
                Triple(StandardObligations.INCLUDE_LICENSE, TriggerCondition.ON_DISTRIBUTION, ObligationScope.COMPONENT)
            ))
        }

        // MPL-2.0 obligations
        addObligationEdges("MPL-2.0", listOf(
            Triple(StandardObligations.ATTRIBUTION, TriggerCondition.ON_DISTRIBUTION, ObligationScope.MODIFIED_FILES),
            Triple(StandardObligations.DISCLOSE_SOURCE, TriggerCondition.ON_DISTRIBUTION, ObligationScope.MODIFIED_FILES),
            Triple(StandardObligations.INCLUDE_LICENSE, TriggerCondition.ON_DISTRIBUTION, ObligationScope.MODIFIED_FILES)
        ))

        // EPL obligations
        listOf("EPL-1.0", "EPL-2.0").forEach { license ->
            addObligationEdges(license, listOf(
                Triple(StandardObligations.ATTRIBUTION, TriggerCondition.ON_DISTRIBUTION, ObligationScope.MODIFIED_FILES),
                Triple(StandardObligations.DISCLOSE_SOURCE, TriggerCondition.ON_DISTRIBUTION, ObligationScope.MODIFIED_FILES),
                Triple(StandardObligations.INCLUDE_LICENSE, TriggerCondition.ON_DISTRIBUTION, ObligationScope.MODIFIED_FILES),
                Triple(StandardObligations.PATENT_GRANT, TriggerCondition.ALWAYS, ObligationScope.COMPONENT)
            ))
        }

        logger.debug { "Loaded license-obligation edges" }
    }

    private fun addObligationEdges(
        licenseId: String,
        obligations: List<Triple<String, TriggerCondition, ObligationScope>>
    ) {
        obligations.forEach { (obligationId, trigger, scope) ->
            graph.addEdge(ObligationEdge(
                id = createObligationEdgeId(licenseId, obligationId),
                sourceId = licenseId,
                targetId = obligationId,
                trigger = trigger,
                scope = scope
            ))
        }
    }

    // =========================================================================
    // License-Right Edges
    // =========================================================================

    private fun loadLicenseRightEdges() {
        // Most open source licenses grant these basic rights
        val allLicenses = listOf(
            "MIT", "APACHE-2.0", "BSD-2-CLAUSE", "BSD-3-CLAUSE", "ISC",
            "CC0-1.0", "UNLICENSE", "0BSD", "WTFPL", "ZLIB", "BSL-1.0",
            "GPL-2.0-ONLY", "GPL-2.0-OR-LATER", "GPL-3.0-ONLY", "GPL-3.0-OR-LATER",
            "LGPL-2.0-ONLY", "LGPL-2.1-ONLY", "LGPL-3.0-ONLY",
            "LGPL-2.0-OR-LATER", "LGPL-2.1-OR-LATER", "LGPL-3.0-OR-LATER",
            "AGPL-3.0-ONLY", "AGPL-3.0-OR-LATER",
            "MPL-2.0", "EPL-1.0", "EPL-2.0"
        )

        val standardRightsForAll = listOf(
            StandardRights.COMMERCIAL_USE,
            StandardRights.MODIFY,
            StandardRights.DISTRIBUTE,
            StandardRights.PRIVATE_USE
        )

        allLicenses.forEach { license ->
            standardRightsForAll.forEach { right ->
                graph.addEdge(RightEdge(
                    id = createRightEdgeId(license, right),
                    sourceId = license,
                    targetId = right
                ))
            }
        }

        // Patent rights (only for licenses with explicit patent grants)
        val patentLicenses = listOf(
            "APACHE-2.0", "GPL-3.0-ONLY", "GPL-3.0-OR-LATER",
            "AGPL-3.0-ONLY", "AGPL-3.0-OR-LATER", "EPL-1.0", "EPL-2.0", "MPL-2.0"
        )

        patentLicenses.forEach { license ->
            graph.addEdge(RightEdge(
                id = createRightEdgeId(license, StandardRights.PATENT_USE),
                sourceId = license,
                targetId = StandardRights.PATENT_USE
            ))
        }

        logger.debug { "Loaded license-right edges" }
    }

    // =========================================================================
    // Use Cases
    // =========================================================================

    private fun loadUseCases() {
        val useCases = listOf(
            UseCaseNode(
                id = "internal",
                name = "Internal Use",
                description = "Software used only within the organization, not distributed",
                distributionType = DistributionType.NONE
            ),
            UseCaseNode(
                id = "saas",
                name = "SaaS Application",
                description = "Software provided as a service over the network",
                distributionType = DistributionType.NETWORK
            ),
            UseCaseNode(
                id = "desktop-app",
                name = "Desktop Application",
                description = "Distributed desktop application (binary distribution)",
                distributionType = DistributionType.BINARY,
                linkingType = LinkingType.STATIC
            ),
            UseCaseNode(
                id = "library",
                name = "Library/SDK",
                description = "Distributed as a library for other developers",
                distributionType = DistributionType.BINARY,
                linkingType = LinkingType.DYNAMIC
            ),
            UseCaseNode(
                id = "embedded",
                name = "Embedded System",
                description = "Software embedded in hardware devices",
                distributionType = DistributionType.EMBEDDED,
                linkingType = LinkingType.STATIC
            ),
            UseCaseNode(
                id = "open-source",
                name = "Open Source Project",
                description = "Distributed as open source with full source code",
                distributionType = DistributionType.SOURCE
            ),
            UseCaseNode(
                id = "microservice",
                name = "Microservice",
                description = "Internal service communicating via network API",
                distributionType = DistributionType.NONE,
                linkingType = LinkingType.NETWORK_BOUNDARY
            )
        )

        useCases.forEach { graph.addUseCase(it) }
        logger.debug { "Loaded ${useCases.size} use cases" }
    }
}
