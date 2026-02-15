package com.ortoped.core.policy

import com.ortoped.core.graph.LicenseKnowledgeGraph
import com.ortoped.core.graph.LicenseGraphLoader
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GraphAwareLicenseClassifierTest {

    private lateinit var graph: LicenseKnowledgeGraph
    private lateinit var classifier: GraphAwareLicenseClassifier

    private val minimalConfig = PolicyConfig(
        version = "1.0",
        name = "test-policy",
        categories = mapOf(
            "permissive" to LicenseCategoryDefinition(
                description = "Permissive",
                licenses = listOf("MIT", "Apache-2.0", "BSD-3-Clause")
            ),
            "copyleft" to LicenseCategoryDefinition(
                description = "Copyleft",
                licenses = listOf("GPL-2.0-only", "GPL-3.0-only")
            ),
            "unknown" to LicenseCategoryDefinition(
                description = "Unknown",
                licenses = listOf("NOASSERTION")
            )
        ),
        rules = emptyList()
    )

    @BeforeEach
    fun setup() {
        graph = LicenseKnowledgeGraph()
        LicenseGraphLoader(graph).loadAll()
        classifier = GraphAwareLicenseClassifier(minimalConfig, graph)
    }

    @Test
    fun `classify returns permissive for MIT from graph`() {
        assertEquals("permissive", classifier.classify("MIT"))
    }

    @Test
    fun `classify returns permissive for Apache-2_0 from graph`() {
        assertEquals("permissive", classifier.classify("Apache-2.0"))
    }

    @Test
    fun `classify returns copyleft for GPL-3_0-only from graph`() {
        // Graph classifies GPL-3.0-only as STRONG_COPYLEFT which maps to "copyleft"
        assertEquals("copyleft", classifier.classify("GPL-3.0-only"))
    }

    @Test
    fun `classify returns weak-copyleft for LGPL from graph`() {
        // LGPL is WEAK_COPYLEFT in graph but not in our minimal policy config
        // So graph takes precedence since it's not in policy config
        assertEquals("weak-copyleft", classifier.classify("LGPL-2.1-only"))
    }

    @Test
    fun `classify returns network-copyleft for AGPL from graph`() {
        assertEquals("network-copyleft", classifier.classify("AGPL-3.0-only"))
    }

    @Test
    fun `classify returns unknown for null`() {
        assertEquals("unknown", classifier.classify(null))
    }

    @Test
    fun `classify returns unknown for NOASSERTION`() {
        assertEquals("unknown", classifier.classify("NOASSERTION"))
    }

    @Test
    fun `classify returns unknown for blank`() {
        assertEquals("unknown", classifier.classify(""))
    }

    @Test
    fun `classify is case-insensitive`() {
        assertEquals("permissive", classifier.classify("mit"))
        assertEquals("permissive", classifier.classify("MIT"))
        assertEquals("permissive", classifier.classify("Mit"))
    }

    @Test
    fun `classify handles OR expression with permissive licenses`() {
        assertEquals("permissive", classifier.classify("MIT OR Apache-2.0"))
    }

    @Test
    fun `classify handles OR expression with copyleft returns dual-license`() {
        assertEquals("dual-license", classifier.classify("MIT OR GPL-3.0-only"))
    }

    @Test
    fun `classify handles AND expression returns most restrictive`() {
        val result = classifier.classify("MIT AND GPL-3.0-only")
        assertEquals("copyleft", result)
    }

    @Test
    fun `policy config overrides graph when license is explicitly listed`() {
        // MIT is in both the graph (PERMISSIVE) and the policy config (permissive)
        // They agree, so result should be permissive
        assertEquals("permissive", classifier.classify("MIT"))
    }

    @Test
    fun `graph provides category for licenses not in policy config`() {
        // MPL-2.0 is in the graph (WEAK_COPYLEFT) but not in our minimal policy config
        assertEquals("weak-copyleft", classifier.classify("MPL-2.0"))
    }

    @Test
    fun `unknown license not in graph or config returns unknown`() {
        assertEquals("unknown", classifier.classify("SuperCustomLicense-1.0"))
    }
}
