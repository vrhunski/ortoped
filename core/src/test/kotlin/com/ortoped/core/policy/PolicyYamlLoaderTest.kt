package com.ortoped.core.policy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PolicyYamlLoaderTest {

    private val loader = PolicyYamlLoader()

    @Test
    fun `should load valid policy file`() {
        val policyFile = javaClass.getResource("/test-policy.yaml")!!.toURI().let { File(it) }

        val policy = loader.load(policyFile)

        assertEquals("1.0", policy.version)
        assertEquals("Test Policy", policy.name)

        // Check categories
        assertTrue(policy.categories.containsKey("permissive"))
        assertTrue(policy.categories.containsKey("copyleft"))
        assertTrue(policy.categories.containsKey("unknown"))

        // Check rules
        assertTrue(policy.rules.isNotEmpty())
        val noCopyleftRule = policy.rules.find { it.id == "no-copyleft" }
        assertNotNull(noCopyleftRule)
        assertEquals(Severity.ERROR, noCopyleftRule.severity)
        assertEquals(RuleAction.DENY, noCopyleftRule.action)
    }

    @Test
    fun `should fail on non-existent file`() {
        val nonExistentFile = File("/nonexistent/policy.yaml")

        assertThrows<PolicyLoadException> {
            loader.load(nonExistentFile)
        }
    }

    @Test
    fun `should load default policy when file is null`() {
        val policy = loader.loadOrDefault(null)

        assertEquals("1.0", policy.version)
        assertEquals("Default OrtoPed Policy", policy.name)

        // Default policy should have basic categories
        assertTrue(policy.categories.containsKey("permissive"))
        assertTrue(policy.categories.containsKey("copyleft"))
        assertTrue(policy.categories.containsKey("unknown"))

        // Default policy should have at least one rule
        assertTrue(policy.rules.isNotEmpty())
    }

    @Test
    fun `should load default policy when file does not exist`() {
        val nonExistentFile = File("/nonexistent/policy.yaml")

        val policy = loader.loadOrDefault(nonExistentFile)

        assertEquals("1.0", policy.version)
        assertEquals("Default OrtoPed Policy", policy.name)
    }

    @Test
    fun `should parse policy categories correctly`() {
        val policyFile = javaClass.getResource("/test-policy.yaml")!!.toURI().let { File(it) }
        val policy = loader.load(policyFile)

        val permissive = policy.categories["permissive"]
        assertNotNull(permissive)
        assertTrue(permissive.licenses.contains("MIT"))
        assertTrue(permissive.licenses.contains("Apache-2.0"))

        val copyleft = policy.categories["copyleft"]
        assertNotNull(copyleft)
        assertTrue(copyleft.licenses.contains("GPL-3.0-only"))
    }

    @Test
    fun `should parse policy rules correctly`() {
        val policyFile = javaClass.getResource("/test-policy.yaml")!!.toURI().let { File(it) }
        val policy = loader.load(policyFile)

        val noCopyleftRule = policy.rules.find { it.id == "no-copyleft" }
        assertNotNull(noCopyleftRule)
        assertEquals("copyleft", noCopyleftRule.category)
        assertTrue(noCopyleftRule.scopes.contains("compile"))
        assertTrue(noCopyleftRule.scopes.contains("runtime"))
    }

    @Test
    fun `should parse policy settings correctly`() {
        val policyFile = javaClass.getResource("/test-policy.yaml")!!.toURI().let { File(it) }
        val policy = loader.load(policyFile)

        val settings = policy.settings
        assertTrue(settings.aiSuggestions.acceptHighConfidence)
        assertTrue(settings.failOn.errors)
        assertTrue(settings.exemptions.isNotEmpty())

        val exemption = settings.exemptions.first()
        assertTrue(exemption.dependency.contains("com.internal"))
    }
}
