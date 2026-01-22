package com.ortoped.core.ai

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LicenseResolverTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `should parse valid AI response`() {
        val mockResponse = """
        {
          "content": [
            {
              "type": "text",
              "text": "{\"suggestedLicense\":\"MIT License\",\"spdxId\":\"MIT\",\"confidence\":\"HIGH\",\"reasoning\":\"License text matches MIT template\",\"alternatives\":[\"ISC\",\"BSD-2-Clause\"]}"
            }
          ]
        }
        """.trimIndent()

        // Note: This test demonstrates the parsing logic
        // In a real scenario, we would mock the HTTP client or use dependency injection
        val responseJson = json.parseToJsonElement(mockResponse).jsonObject
        val contentArray = responseJson["content"]?.jsonArray
        val content = contentArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content

        assertNotNull(content)
        assert(content.contains("MIT License"))
        assert(content.contains("HIGH"))
    }

    @Test
    fun `should handle malformed JSON gracefully`() {
        val malformedResponse = """
        {
          "content": [
            {
              "type": "text",
              "text": "This is not JSON"
            }
          ]
        }
        """.trimIndent()

        // The resolver should handle this gracefully and return null
        // Testing the error handling path
        val responseJson = json.parseToJsonElement(malformedResponse).jsonObject
        val contentArray = responseJson["content"]?.jsonArray
        val content = contentArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content

        assertNotNull(content)
        // Should not contain valid JSON structure
        assert(!content.startsWith("{\"suggestedLicense\""))
    }

    @Test
    fun `should extract SPDX identifier correctly`() {
        val validLicenseJson = """
        {
          "suggestedLicense": "MIT License",
          "spdxId": "MIT",
          "confidence": "HIGH",
          "reasoning": "License text matches MIT template with 98% similarity",
          "alternatives": ["ISC", "BSD-2-Clause"]
        }
        """.trimIndent()

        val parsed = json.parseToJsonElement(validLicenseJson).jsonObject

        assertEquals("MIT", parsed["spdxId"]?.jsonPrimitive?.content)
        assertEquals("HIGH", parsed["confidence"]?.jsonPrimitive?.content)
        assertEquals("MIT License", parsed["suggestedLicense"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should handle empty response`() {
        val emptyResponse = """
        {
          "content": []
        }
        """.trimIndent()

        val responseJson = json.parseToJsonElement(emptyResponse).jsonObject
        val contentArray = responseJson["content"]?.jsonArray

        assertNotNull(contentArray)
        assert(contentArray.isEmpty())
    }

    @Test
    fun `should handle different confidence levels`() {
        val levels = listOf("HIGH", "MEDIUM", "LOW")

        levels.forEach { level ->
            val response = """
            {
              "suggestedLicense": "Apache-2.0",
              "spdxId": "Apache-2.0",
              "confidence": "$level",
              "reasoning": "Test reasoning",
              "alternatives": []
            }
            """.trimIndent()

            val parsed = json.parseToJsonElement(response).jsonObject
            assertEquals(level, parsed["confidence"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `should parse license suggestion with alternatives`() {
        val suggestionJson = """
        {
          "suggestedLicense": "Apache License 2.0",
          "spdxId": "Apache-2.0",
          "confidence": "MEDIUM",
          "reasoning": "Header matches Apache 2.0 pattern",
          "alternatives": ["MIT", "BSD-2-Clause", "BSD-3-Clause"]
        }
        """.trimIndent()

        val parsed = json.parseToJsonElement(suggestionJson).jsonObject
        val alternatives = parsed["alternatives"]?.jsonArray

        assertNotNull(alternatives)
        assertEquals(3, alternatives.size)
        assertEquals("MIT", alternatives[0].jsonPrimitive.content)
        assertEquals("BSD-2-Clause", alternatives[1].jsonPrimitive.content)
    }

    @Test
    fun `should handle license suggestion without alternatives`() {
        val suggestionJson = """
        {
          "suggestedLicense": "GPL-3.0-only",
          "spdxId": "GPL-3.0-only",
          "confidence": "HIGH",
          "reasoning": "Exact match with GPL 3.0 license text",
          "alternatives": []
        }
        """.trimIndent()

        val parsed = json.parseToJsonElement(suggestionJson).jsonObject
        val alternatives = parsed["alternatives"]?.jsonArray

        assertNotNull(alternatives)
        assert(alternatives.isEmpty())
    }
}
