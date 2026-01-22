package com.ortoped.api.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun Application.configureRateLimit() {
    install(RateLimit) {
        // Global rate limit
        global {
            rateLimiter(limit = 100, refillPeriod = 1.minutes)
        }

        // Stricter limit for scan endpoints (resource intensive)
        register(RateLimitName("scan")) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
        }

        // Stricter limit for auth endpoints (prevent brute force)
        register(RateLimitName("auth")) {
            rateLimiter(limit = 5, refillPeriod = 30.seconds)
        }
    }
}
