package com.ortoped.core.graph

import com.ortoped.core.graph.model.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

class LicenseKnowledgeGraphTest {

    private lateinit var graph: LicenseKnowledgeGraph

    @BeforeEach
    fun setup() {
        graph = LicenseKnowledgeGraph()
        LicenseGraphLoader(graph).loadAll()
    }

    @Nested
    inner class LicenseQueries {

        @Test
        fun `should find MIT license by ID`() {
            val license = graph.getLicense("MIT")
            assertNotNull(license)
            assertEquals("MIT", license!!.id)
            assertEquals("MIT License", license.name)
            assertEquals(LicenseCategory.PERMISSIVE, license.category)
            assertEquals(CopyleftStrength.NONE, license.copyleftStrength)
        }

        @Test
        fun `should find license with case insensitive ID`() {
            val license = graph.getLicense("mit")
            assertNotNull(license)
            assertEquals("MIT", license!!.id)
        }

        @Test
        fun `should find GPL 3 only license`() {
            val license = graph.getLicense("GPL-3.0-ONLY")
            assertNotNull(license)
            assertEquals(LicenseCategory.STRONG_COPYLEFT, license!!.category)
            assertEquals(CopyleftStrength.STRONG, license.copyleftStrength)
            assertEquals("GPL", license.family)
        }

        @Test
        fun `should find AGPL license with network copyleft`() {
            val license = graph.getLicense("AGPL-3.0-ONLY")
            assertNotNull(license)
            assertEquals(LicenseCategory.NETWORK_COPYLEFT, license!!.category)
            assertEquals(CopyleftStrength.NETWORK, license.copyleftStrength)
        }

        @Test
        fun `should return null for unknown license`() {
            val license = graph.getLicense("NONEXISTENT-LICENSE")
            assertNull(license)
        }

        @Test
        fun `should get licenses by category`() {
            val permissive = graph.getLicensesByCategory(LicenseCategory.PERMISSIVE)
            assertTrue(permissive.isNotEmpty())
            assertTrue(permissive.any { it.id == "MIT" })
            assertTrue(permissive.any { it.id == "APACHE-2.0" })
        }

        @Test
        fun `should get licenses by family`() {
            val gplFamily = graph.getLicensesByFamily("GPL")
            assertTrue(gplFamily.isNotEmpty())
            assertTrue(gplFamily.any { it.id == "GPL-2.0-ONLY" })
            assertTrue(gplFamily.any { it.id == "GPL-3.0-ONLY" })
        }

        @Test
        fun `should search licenses`() {
            val results = graph.searchLicenses("apache")
            assertTrue(results.isNotEmpty())
            assertTrue(results.any { it.id == "APACHE-2.0" })
        }
    }

    @Nested
    inner class CompatibilityChecks {

        @Test
        fun `same license should be fully compatible`() {
            val result = graph.checkCompatibility("MIT", "MIT")
            assertTrue(result.compatible)
            assertEquals(CompatibilityLevel.FULL, result.level)
        }

        @Test
        fun `permissive licenses should be compatible`() {
            val result = graph.checkCompatibility("MIT", "APACHE-2.0")
            assertTrue(result.compatible)
            assertEquals(CompatibilityLevel.FULL, result.level)
        }

        @Test
        fun `MIT and BSD-3 should be compatible`() {
            val result = graph.checkCompatibility("MIT", "BSD-3-CLAUSE")
            assertTrue(result.compatible)
        }

        @Test
        fun `GPL 2 only and GPL 3 only should be incompatible`() {
            val result = graph.checkCompatibility("GPL-2.0-ONLY", "GPL-3.0-ONLY")
            assertFalse(result.compatible)
            assertEquals(CompatibilityLevel.INCOMPATIBLE, result.level)
        }

        @Test
        fun `Apache 2 and GPL 2 only should be incompatible`() {
            val result = graph.checkCompatibility("APACHE-2.0", "GPL-2.0-ONLY")
            assertFalse(result.compatible)
            assertEquals(CompatibilityLevel.INCOMPATIBLE, result.level)
        }

        @Test
        fun `Apache 2 and GPL 3 only should be one-way compatible`() {
            val result = graph.checkCompatibility("APACHE-2.0", "GPL-3.0-ONLY")
            assertTrue(result.compatible)
            assertEquals(CompatibilityLevel.ONE_WAY, result.level)
        }

        @Test
        fun `permissive with copyleft should be conditionally compatible`() {
            val result = graph.checkCompatibility("MIT", "GPL-3.0-ONLY")
            assertTrue(result.compatible)
            assertEquals(CompatibilityLevel.CONDITIONAL, result.level)
            assertEquals("GPL-3.0-ONLY", result.dominantLicense)
        }

        @Test
        fun `unknown license should require review`() {
            val result = graph.checkCompatibility("MIT", "UNKNOWN-LICENSE-XYZ")
            assertTrue(result.requiresReview)
            assertEquals(CompatibilityLevel.UNKNOWN, result.level)
        }

        @Test
        fun `GPL 2 or later should be compatible with GPL 3`() {
            val result = graph.checkCompatibility("GPL-2.0-OR-LATER", "GPL-3.0-ONLY")
            assertTrue(result.compatible)
            assertEquals(CompatibilityLevel.CONDITIONAL, result.level)
        }
    }

    @Nested
    inner class ObligationQueries {

        @Test
        fun `MIT should have attribution obligation`() {
            val obligations = graph.getObligationsForLicense("MIT")
            assertTrue(obligations.isNotEmpty())
            assertTrue(obligations.any { it.obligation.id == StandardObligations.ATTRIBUTION })
        }

        @Test
        fun `GPL 3 should have source disclosure obligation`() {
            val obligations = graph.getObligationsForLicense("GPL-3.0-ONLY")
            assertTrue(obligations.any { it.obligation.id == StandardObligations.SOURCE_DISCLOSURE })
            assertTrue(obligations.any { it.obligation.id == StandardObligations.SAME_LICENSE })
        }

        @Test
        fun `AGPL-3 should have network disclosure obligation`() {
            val obligations = graph.getObligationsForLicense("AGPL-3.0-ONLY")
            assertTrue(obligations.any { it.obligation.id == StandardObligations.NETWORK_DISCLOSURE })
        }

        @Test
        fun `Apache 2 should have patent grant`() {
            val obligations = graph.getObligationsForLicense("APACHE-2.0")
            assertTrue(obligations.any { it.obligation.id == StandardObligations.PATENT_GRANT })
        }

        @Test
        fun `aggregated obligations should include all unique obligations`() {
            val licenses = listOf("MIT", "APACHE-2.0", "GPL-3.0-ONLY")
            val aggregated = graph.aggregateObligations(licenses)

            assertTrue(aggregated.obligations.isNotEmpty())
            assertEquals(3, aggregated.totalLicenses)

            // Should have attribution from all three
            val attribution = aggregated.obligations.find { it.obligationId == StandardObligations.ATTRIBUTION }
            assertNotNull(attribution)
            assertEquals(3, attribution!!.sources.size)

            // Should have source disclosure from GPL
            assertTrue(aggregated.obligations.any { it.obligationId == StandardObligations.SOURCE_DISCLOSURE })
        }

        @Test
        fun `aggregated obligations should find most restrictive scope`() {
            val licenses = listOf("MIT", "GPL-3.0-ONLY")
            val aggregated = graph.aggregateObligations(licenses)

            val attribution = aggregated.obligations.find { it.obligationId == StandardObligations.ATTRIBUTION }
            assertNotNull(attribution)
            // GPL-3.0 requires attribution at DERIVATIVE_WORK scope
            assertEquals(ObligationScope.DERIVATIVE_WORK, attribution!!.mostRestrictiveScope)
        }
    }

    @Nested
    inner class DependencyTreeAnalysis {

        @Test
        fun `should analyze clean dependency tree`() {
            val deps = listOf(
                DependencyLicense("dep1", "express", "4.18.0", "MIT"),
                DependencyLicense("dep2", "lodash", "4.17.0", "MIT"),
                DependencyLicense("dep3", "axios", "1.0.0", "MIT")
            )

            val analysis = graph.analyzeDependencyTree(deps)

            assertEquals(3, analysis.totalDependencies)
            assertEquals(1, analysis.uniqueLicenses.size)
            assertTrue(analysis.conflicts.isEmpty())
            assertEquals(ComplianceStatus.COMPLIANT, analysis.complianceStatus)
        }

        @Test
        fun `should detect conflicts in dependency tree`() {
            val deps = listOf(
                DependencyLicense("dep1", "lib-a", "1.0.0", "GPL-2.0-ONLY"),
                DependencyLicense("dep2", "lib-b", "1.0.0", "GPL-3.0-ONLY")
            )

            val analysis = graph.analyzeDependencyTree(deps)

            assertTrue(analysis.conflicts.isNotEmpty())
            assertEquals(ConflictSeverity.BLOCKING, analysis.conflicts[0].severity)
            assertEquals(ComplianceStatus.BLOCKED, analysis.complianceStatus)
        }

        @Test
        fun `should find dominant license`() {
            val deps = listOf(
                DependencyLicense("dep1", "express", "4.18.0", "MIT"),
                DependencyLicense("dep2", "lodash", "4.17.0", "MIT"),
                DependencyLicense("dep3", "gpl-lib", "1.0.0", "GPL-3.0-ONLY")
            )

            val analysis = graph.analyzeDependencyTree(deps)

            assertNotNull(analysis.dominantLicense)
            assertEquals("GPL-3.0-ONLY", analysis.dominantLicense!!.licenseId)
        }

        @Test
        fun `should calculate license distribution`() {
            val deps = listOf(
                DependencyLicense("dep1", "lib1", "1.0.0", "MIT"),
                DependencyLicense("dep2", "lib2", "1.0.0", "MIT"),
                DependencyLicense("dep3", "lib3", "1.0.0", "APACHE-2.0")
            )

            val analysis = graph.analyzeDependencyTree(deps)

            assertEquals(2, analysis.licenseDistribution["MIT"])
            assertEquals(1, analysis.licenseDistribution["APACHE-2.0"])
        }

        @Test
        fun `should generate recommendations for high effort obligations`() {
            val deps = listOf(
                DependencyLicense("dep1", "gpl-lib", "1.0.0", "GPL-3.0-ONLY")
            )

            val analysis = graph.analyzeDependencyTree(deps)

            assertTrue(analysis.recommendations.isNotEmpty())
            assertTrue(analysis.recommendations.any {
                it.type == RecommendationType.FULFILL_OBLIGATION
            })
        }

        @Test
        fun `should calculate risk score`() {
            // Clean project - low risk
            val cleanDeps = listOf(
                DependencyLicense("dep1", "lib1", "1.0.0", "MIT"),
                DependencyLicense("dep2", "lib2", "1.0.0", "MIT")
            )
            val cleanAnalysis = graph.analyzeDependencyTree(cleanDeps)
            assertTrue(cleanAnalysis.riskScore < 0.3)

            // Risky project - higher risk
            val riskyDeps = listOf(
                DependencyLicense("dep1", "lib1", "1.0.0", "GPL-2.0-ONLY"),
                DependencyLicense("dep2", "lib2", "1.0.0", "GPL-3.0-ONLY")
            )
            val riskyAnalysis = graph.analyzeDependencyTree(riskyDeps)
            assertTrue(riskyAnalysis.riskScore > 0.3)
        }
    }

    @Nested
    inner class PathFinding {

        @Test
        fun `should find path for same license`() {
            val path = graph.findCompatibilityPath("MIT", "MIT")
            assertNotNull(path)
            assertEquals(1, path!!.licenses.size)
            assertEquals(CompatibilityLevel.FULL, path.overallCompatibility)
        }

        @Test
        fun `should find direct path for compatible licenses`() {
            val path = graph.findCompatibilityPath("MIT", "APACHE-2.0")
            assertNotNull(path)
            assertEquals(2, path!!.licenses.size)
        }

        @Test
        fun `should return null for incompatible licenses with no path`() {
            // Create a fresh graph with only incompatible licenses
            val testGraph = LicenseKnowledgeGraph()
            testGraph.addLicense(LicenseNode(
                id = "A", spdxId = "A", name = "License A",
                category = LicenseCategory.STRONG_COPYLEFT,
                copyleftStrength = CopyleftStrength.STRONG
            ))
            testGraph.addLicense(LicenseNode(
                id = "B", spdxId = "B", name = "License B",
                category = LicenseCategory.STRONG_COPYLEFT,
                copyleftStrength = CopyleftStrength.STRONG
            ))
            testGraph.addEdge(CompatibilityEdge(
                id = "A-B",
                sourceId = "A",
                targetId = "B",
                compatibility = CompatibilityLevel.INCOMPATIBLE,
                direction = CompatibilityDirection.BIDIRECTIONAL
            ))

            val path = testGraph.findCompatibilityPath("A", "B")
            assertNull(path)
        }
    }

    @Nested
    inner class LicenseDetails {

        @Test
        fun `should get complete license details`() {
            val details = graph.getLicenseDetails("APACHE-2.0")
            assertNotNull(details)
            assertEquals("APACHE-2.0", details!!.license.id)
            assertTrue(details.obligations.isNotEmpty())
            assertTrue(details.rights.isNotEmpty())
            assertTrue(details.compatibleWith.isNotEmpty())
        }

        @Test
        fun `should include obligations with scope`() {
            val details = graph.getLicenseDetails("GPL-3.0-ONLY")
            assertNotNull(details)

            val sourceDisclosure = details!!.obligations.find {
                it.obligation.id == StandardObligations.SOURCE_DISCLOSURE
            }
            assertNotNull(sourceDisclosure)
            assertEquals(ObligationScope.DERIVATIVE_WORK, sourceDisclosure!!.scope)
        }

        @Test
        fun `should return null for unknown license`() {
            val details = graph.getLicenseDetails("NONEXISTENT")
            assertNull(details)
        }
    }

    @Nested
    inner class Statistics {

        @Test
        fun `should return valid statistics`() {
            val stats = graph.getStatistics()

            assertTrue(stats.totalLicenses > 20)
            assertTrue(stats.totalObligations > 5)
            assertTrue(stats.totalRights > 3)
            assertTrue(stats.totalEdges > 50)
            assertTrue(stats.licenseFamilies.isNotEmpty())
            assertTrue(stats.licensesByCategory.isNotEmpty())
        }
    }
}
