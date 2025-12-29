package com.ortoped.policy

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LicenseClassifierTest {

    private fun createTestPolicy(): PolicyConfig {
        return PolicyConfig(
            version = "1.0",
            name = "Test",
            description = null,
            categories = mapOf(
                "permissive" to LicenseCategoryDefinition(
                    description = "Permissive",
                    licenses = listOf("MIT", "Apache-2.0", "BSD-2-Clause", "ISC")
                ),
                "copyleft" to LicenseCategoryDefinition(
                    description = "Strong copyleft",
                    licenses = listOf("GPL-3.0-only", "AGPL-3.0-only")
                ),
                "copyleft-limited" to LicenseCategoryDefinition(
                    description = "Weak copyleft",
                    licenses = listOf("LGPL-2.1-only", "MPL-2.0")
                ),
                "unknown" to LicenseCategoryDefinition(
                    description = "Unknown",
                    licenses = listOf("NOASSERTION", "Unknown")
                )
            ),
            rules = emptyList(),
            settings = PolicySettings()
        )
    }

    @Test
    fun `should classify MIT as permissive`() {
        val policy = createTestPolicy()
        val classifier = LicenseClassifier(policy)

        val category = classifier.classify("MIT")

        assertEquals("permissive", category)
    }

    @Test
    fun `should classify Apache-2-0 as permissive`() {
        val policy = createTestPolicy()
        val classifier = LicenseClassifier(policy)

        val category = classifier.classify("Apache-2.0")

        assertEquals("permissive", category)
    }

    @Test
    fun `should classify GPL as copyleft`() {
        val policy = createTestPolicy()
        val classifier = LicenseClassifier(policy)

        val category = classifier.classify("GPL-3.0-only")

        assertEquals("copyleft", category)
    }

    @Test
    fun `should classify LGPL as copyleft-limited`() {
        val policy = createTestPolicy()
        val classifier = LicenseClassifier(policy)

        val category = classifier.classify("LGPL-2.1-only")

        assertEquals("copyleft-limited", category)
    }

    @Test
    fun `should classify NOASSERTION as unknown`() {
        val policy = createTestPolicy()
        val classifier = LicenseClassifier(policy)

        val category = classifier.classify("NOASSERTION")

        assertEquals("unknown", category)
    }

    @Test
    fun `should classify unlisted license as unknown`() {
        val policy = createTestPolicy()
        val classifier = LicenseClassifier(policy)

        val category = classifier.classify("Some-Unknown-License")

        assertEquals("unknown", category)
    }

    @Test
    fun `should handle null license as unknown`() {
        val policy = createTestPolicy()
        val classifier = LicenseClassifier(policy)

        val category = classifier.classify(null)

        assertEquals("unknown", category)
    }

    @Test
    fun `should be case insensitive for SPDX identifiers`() {
        val policy = createTestPolicy()
        val classifier = LicenseClassifier(policy)

        // Classifier is case-insensitive to be lenient with real-world data
        val upperCase = classifier.classify("MIT")
        val lowerCase = classifier.classify("mit")
        val mixedCase = classifier.classify("Mit")

        assertEquals("permissive", upperCase)
        assertEquals("permissive", lowerCase)
        assertEquals("permissive", mixedCase)
    }
}
