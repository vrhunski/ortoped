package com.ortoped.policy.ai

import com.ortoped.policy.AiFix
import com.ortoped.policy.PolicyViolation
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Uses AI to suggest fixes for policy violations
 */
class PolicyAiAdvisor(
    private val apiKey: String = System.getenv("ANTHROPIC_API_KEY") ?: ""
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(30))
        .readTimeout(Duration.ofSeconds(60))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    /**
     * Suggest fixes for multiple violations in parallel
     */
    suspend fun suggestFixes(violations: List<PolicyViolation>): Map<String, AiFix> {
        if (apiKey.isBlank()) {
            logger.warn { "ANTHROPIC_API_KEY not set. Skipping AI fix suggestions." }
            return emptyMap()
        }

        if (violations.isEmpty()) {
            return emptyMap()
        }

        logger.info { "Generating AI fix suggestions for ${violations.size} violations..." }

        val fixes = mutableMapOf<String, AiFix>()

        return withContext(Dispatchers.IO) {
            violations.map { violation ->
                async {
                    try {
                        val fix = suggestFix(violation)
                        if (fix != null) {
                            violation.dependencyId to fix
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to get AI suggestion for ${violation.dependencyId}" }
                        null
                    }
                }
            }.awaitAll()
                .filterNotNull()
                .toMap()
        }
    }

    /**
     * Suggest a fix for a single violation
     */
    private fun suggestFix(violation: PolicyViolation): AiFix? {
        val prompt = buildPrompt(violation)
        val response = callClaudeAPI(prompt)
        return parseAiFix(response)
    }

    /**
     * Build the prompt for AI fix suggestion
     */
    private fun buildPrompt(violation: PolicyViolation): String {
        return buildString {
            appendLine("You are a software licensing compliance expert.")
            appendLine()
            appendLine("A dependency has violated a license policy rule:")
            appendLine("- Dependency: ${violation.dependencyName} v${violation.dependencyVersion}")
            appendLine("- Current License: ${violation.license}")
            appendLine("- License Category: ${violation.licenseCategory}")
            appendLine("- Rule Violated: ${violation.ruleName}")
            appendLine("- Violation Message: ${violation.message}")
            appendLine()
            appendLine("Please suggest how to resolve this compliance issue. Consider:")
            appendLine("1. Alternative packages with permissive licenses that provide similar functionality")
            appendLine("2. Whether an exception might be appropriate (e.g., if used only in tests)")
            appendLine("3. Steps to properly evaluate or mitigate the license risk")
            appendLine()
            appendLine("Respond ONLY in this JSON format:")
            appendLine("""
                {
                  "suggestion": "Primary recommendation",
                  "alternativeDependencies": ["alt-package-1", "alt-package-2"],
                  "reasoning": "Explanation of the recommendation",
                  "confidence": "HIGH|MEDIUM|LOW"
                }
            """.trimIndent())
        }
    }

    /**
     * Call Claude API
     */
    private fun callClaudeAPI(prompt: String): String {
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

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Claude API request failed: ${response.code}")
            }
            return response.body?.string() ?: throw RuntimeException("Empty response from Claude API")
        }
    }

    /**
     * Parse AI fix suggestion from API response
     */
    private fun parseAiFix(apiResponse: String): AiFix? {
        return try {
            val responseJson = json.parseToJsonElement(apiResponse).jsonObject
            val contentArray = responseJson["content"]?.jsonArray ?: return null
            val content = contentArray.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?: return null

            // Extract JSON from the response
            val jsonText = content
                .substringAfter('{')
                .substringBeforeLast('}')
                .let { "{$it}" }

            val fixJson = json.parseToJsonElement(jsonText).jsonObject

            AiFix(
                suggestion = fixJson["suggestion"]?.jsonPrimitive?.content ?: "",
                alternativeDependencies = fixJson["alternativeDependencies"]?.jsonArray
                    ?.map { it.jsonPrimitive.content } ?: emptyList(),
                reasoning = fixJson["reasoning"]?.jsonPrimitive?.content ?: "",
                confidence = fixJson["confidence"]?.jsonPrimitive?.content ?: "LOW"
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse AI fix suggestion" }
            null
        }
    }
}
