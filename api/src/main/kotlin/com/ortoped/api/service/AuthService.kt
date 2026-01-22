package com.ortoped.api.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.ortoped.api.model.*
import com.ortoped.api.plugins.NotFoundException
import com.ortoped.api.plugins.UnauthorizedException
import com.ortoped.api.repository.ApiKeyRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.security.SecureRandom
import java.util.*

private val logger = KotlinLogging.logger {}

class AuthService(
    private val apiKeyRepository: ApiKeyRepository
) {
    private val jwtSecret = System.getenv("JWT_SECRET") ?: "ortoped-development-secret-change-in-production"
    private val jwtIssuer = System.getenv("JWT_ISSUER") ?: "ortoped-api"
    private val jwtAudience = System.getenv("JWT_AUDIENCE") ?: "ortoped-users"

    private val secureRandom = SecureRandom()

    fun listApiKeys(page: Int = 1, pageSize: Int = 20): ApiKeyListResponse {
        val offset = ((page - 1) * pageSize).toLong()
        val apiKeys = apiKeyRepository.findAll(pageSize, offset)
        val total = apiKeyRepository.count().toInt()

        return ApiKeyListResponse(
            apiKeys = apiKeys.map { it.toResponse(includeKey = false) },
            total = total
        )
    }

    fun createApiKey(request: CreateApiKeyRequest): ApiKeyResponse {
        // Generate a secure API key
        val apiKey = generateApiKey()
        val keyPrefix = apiKey.take(8)
        val keyHash = hashApiKey(apiKey)

        val entity = apiKeyRepository.create(
            name = request.name,
            keyHash = keyHash,
            keyPrefix = keyPrefix
        )

        logger.info { "Created API key: ${entity.name} (prefix: $keyPrefix)" }

        // Return with the full API key (only time it's visible)
        return ApiKeyResponse(
            id = entity.id.toString(),
            name = entity.name,
            keyPrefix = entity.keyPrefix,
            apiKey = apiKey, // Only returned on creation
            createdAt = entity.createdAt
        )
    }

    fun deleteApiKey(id: String) {
        val uuid = parseUUID(id)
        if (!apiKeyRepository.delete(uuid)) {
            throw NotFoundException("API key not found: $id")
        }
        logger.info { "Deleted API key: $id" }
    }

    fun validateApiKey(apiKey: String): ApiKeyValidation {
        val keyPrefix = apiKey.take(8)
        val candidates = apiKeyRepository.findByKeyPrefix(keyPrefix)

        for (candidate in candidates) {
            if (verifyApiKey(apiKey, candidate.keyHash)) {
                return ApiKeyValidation(
                    valid = true,
                    apiKeyId = candidate.id.toString(),
                    name = candidate.name
                )
            }
        }

        return ApiKeyValidation(valid = false)
    }

    fun generateJwtToken(apiKeyId: String): String {
        val expiresAt = Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000) // 24 hours

        return JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withClaim("apiKeyId", apiKeyId)
            .withExpiresAt(expiresAt)
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    fun authenticateWithApiKey(apiKey: String): String {
        val validation = validateApiKey(apiKey)
        if (!validation.valid) {
            throw UnauthorizedException("Invalid API key")
        }
        return generateJwtToken(validation.apiKeyId!!)
    }

    private fun generateApiKey(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return "op_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashApiKey(apiKey: String): String {
        return BCrypt.withDefaults().hashToString(12, apiKey.toCharArray())
    }

    private fun verifyApiKey(apiKey: String, hash: String): Boolean {
        return BCrypt.verifyer().verify(apiKey.toCharArray(), hash).verified
    }

    private fun parseUUID(id: String): UUID = try {
        UUID.fromString(id)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid UUID format: $id")
    }

    private fun com.ortoped.api.repository.ApiKeyEntity.toResponse(includeKey: Boolean = false) = ApiKeyResponse(
        id = id.toString(),
        name = name,
        keyPrefix = keyPrefix,
        apiKey = if (includeKey) null else null, // Never return the key after creation
        createdAt = createdAt
    )
}

data class ApiKeyValidation(
    val valid: Boolean,
    val apiKeyId: String? = null,
    val name: String? = null
)
