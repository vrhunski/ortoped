# OrtoPed - AI-Enhanced ORT Scanner
# Multi-stage Dockerfile for minimal runtime image

# ============================================================================
# Stage 1: Build with Gradle
# ============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

# Install git (required for VCS operations)
RUN apk add --no-cache git

WORKDIR /app

# Copy Gradle wrapper and dependencies first (for better caching)
COPY gradle gradle
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts ./

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src src

# Build the application
RUN ./gradlew installDist --no-daemon

# ============================================================================
# Stage 2: Minimal runtime image
# ============================================================================
FROM eclipse-temurin:21-jre-alpine

# Install git (required for remote repository cloning) and other runtime dependencies
RUN apk add --no-cache \
    git \
    bash \
    && rm -rf /var/cache/apk/*

# Create app directory
WORKDIR /app

# Copy built application from builder stage
COPY --from=builder /app/build/install/ortoped .

# Create volume mount points for projects and reports
VOLUME ["/projects", "/reports"]

# Set entrypoint
ENTRYPOINT ["/app/bin/ortoped"]

# Default command: show help
CMD ["--help"]

# Labels
LABEL org.opencontainers.image.title="OrtoPed"
LABEL org.opencontainers.image.description="AI-enhanced license compliance scanning with ORT"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.vendor="OrtoPed Team"
LABEL org.opencontainers.image.source="https://github.com/yourusername/ortoped"
