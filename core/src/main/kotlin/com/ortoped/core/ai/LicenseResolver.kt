package com.ortoped.core.ai

import com.ortoped.core.model.LicenseSuggestion
import com.ortoped.core.model.UnresolvedLicense
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val logger = KotlinLogging.logger {}

// Load API key once at class initialization - returns null if not set
private val anthropicApiKey: String? by lazy {
    System.getenv("ANTHROPIC_API_KEY")?.takeIf { it.isNotBlank() }
}

class LicenseResolver {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    suspend fun resolveLicense(unresolved: UnresolvedLicense): LicenseSuggestion? {
        val apiKey = anthropicApiKey
        if (apiKey == null) {
            logger.warn { "ANTHROPIC_API_KEY not set. Skipping AI resolution for: ${unresolved.dependencyName}" }
            return null
        }

        logger.info { "Resolving license for: ${unresolved.dependencyName}" }

        val prompt = buildPrompt(unresolved)
        val response = callClaudeAPI(prompt, apiKey)

        return parseLicenseSuggestion(response)
    }

    private fun buildPrompt(unresolved: UnresolvedLicense): String {
        return buildString {
            appendLine("You are a software license expert. Your task is to identify the correct SPDX license identifier for a dependency.")
            appendLine()
            appendLine("Dependency Information:")
            appendLine("- Name: ${unresolved.dependencyName}")
            appendLine("- ID: ${unresolved.dependencyId}")
            if (unresolved.licenseUrl != null) {
                appendLine("- Source URL: ${unresolved.licenseUrl}")
            }
            if (unresolved.licenseText != null) {
                appendLine("- License Text Preview:")
                appendLine("```")
                appendLine(unresolved.licenseText.take(1000)) // First 1000 chars
                appendLine("```")
            }
            appendLine()
            appendLine("Please analyze and provide:")
            appendLine("1. The most likely SPDX license identifier (e.g., MIT, Apache-2.0, GPL-3.0)")
            appendLine("2. Confidence level (HIGH, MEDIUM, LOW)")
            appendLine("3. Brief reasoning for your suggestion")
            appendLine("4. Alternative licenses if uncertain")
            appendLine()
            appendLine("Respond ONLY in this JSON format:")
            appendLine("""
                {
                  "suggestedLicense": "license name",
                  "spdxId": "SPDX-Identifier",
                  "confidence": "HIGH|MEDIUM|LOW",
                  "reasoning": "explanation",
                  "alternatives": ["license1", "license2"]
                }
            """.trimIndent())
        }
    }

    private fun callClaudeAPI(prompt: String, apiKey: String): String {
        val requestBody = """
            {
              "model": "claude-sonnet-4-20250514",
              "max_tokens": 1024,
              "messages": [
                {
                  "role": "user",
                  "content": ${json.encodeToString(kotlinx.serialization.serializer(), prompt)}
                }
              ]
            }
        """.trimIndent()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            logger.error { "Claude API error: ${response.statusCode()} - ${response.body()}" }
            throw RuntimeException("Claude API request failed: ${response.statusCode()}")
        }

        return response.body()
    }

    private fun parseLicenseSuggestion(apiResponse: String): LicenseSuggestion? {
        return try {
            val responseJson = json.parseToJsonElement(apiResponse).jsonObject
            // Claude API returns content as an array of content blocks
            val contentArray = responseJson["content"]?.jsonArray ?: return null
            val firstContent = contentArray.firstOrNull()?.jsonObject ?: return null
            val content = firstContent["text"]?.jsonPrimitive?.content ?: return null

            // Extract JSON from the response (Claude might wrap it in markdown)
            val jsonText = content
                .substringAfter('{')
                .substringBeforeLast('}')
                .let { "{$it}" }

            val suggestionJson = json.parseToJsonElement(jsonText).jsonObject

            LicenseSuggestion(
                suggestedLicense = suggestionJson["suggestedLicense"]?.jsonPrimitive?.content ?: "Unknown",
                confidence = suggestionJson["confidence"]?.jsonPrimitive?.content ?: "LOW",
                reasoning = suggestionJson["reasoning"]?.jsonPrimitive?.content ?: "",
                spdxId = suggestionJson["spdxId"]?.jsonPrimitive?.content,
                alternatives = suggestionJson["alternatives"]?.let { arr ->
                    arr.toString().removeSurrounding("[", "]")
                        .split(",")
                        .map { it.trim().removeSurrounding("\"") }
                } ?: emptyList()
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse license suggestion from AI response" }
            null
        }
    }
}