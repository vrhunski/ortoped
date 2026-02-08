# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OrtoPed is an AI-enhanced wrapper around the OSS Review Toolkit (ORT) that automatically resolves unidentified software licenses using Claude AI. It reduces license curation time from hours to seconds while maintaining accuracy through confidence scoring.

**Key capabilities:**
- 27+ package manager support via ORT (Maven, Gradle, npm, pip, Cargo, etc.)
- AI-powered license resolution for NOASSERTION cases (on-demand, not default)
- SBOM export (CycloneDX JSON/XML, SPDX JSON/Tag-Value)
- YAML-based policy evaluation with AI fix suggestions
- Portable YAML/JSON curation files for deterministic license overrides
- Fast-path lockfile caching for CI/CD

## Build and Development Commands

```bash
# Build entire project
./gradlew build

# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :core:test
./gradlew :api:test

# Run a single test class
./gradlew :core:test --tests "com.ortoped.core.ai.LicenseResolverTest"

# Create distribution
./gradlew installDist

# Run CLI directly
./gradlew :cli:run --args="scan -p . --demo"

# Run API server
./gradlew :api:run

# Build Docker image
docker build -t ortoped:latest .
```

## Architecture

### Multi-Module Structure

```
ortoped/
├── core/          # Core library - AI resolution, scanning, SBOM, policy, curation, cache
├── cli/           # CLI interface using Clikt
├── api/           # REST API using Ktor + PostgreSQL
└── dashboard/     # Vue 3 web dashboard
```

### `.ortoped/` Directory Convention

Projects using OrtoPed should have a `.ortoped/` directory at the root:

```
project/
├── .ortoped/
│   ├── curations.yml          # ORT-compatible license curations
│   ├── curations.ortoped.json # OrtoPed-native curations (optional)
│   ├── policy.yml             # License compliance policy
│   └── cache/                 # Lockfile-based scan cache (gitignored)
├── src/
└── ...
```

Run `ortoped init` to scaffold this directory with defaults.

### Core Components and Data Flow

```
User Input (CLI/API)
    ↓
RemoteRepositoryHandler (clone Git repos if URL provided)
    ↓
ScanOrchestrator
    ├─→ CurationLoader (apply .ortoped/curations.yml overrides)
    ├─→ LockfileHasher + LocalFileCache (fast-path cache)
    ├─→ SimpleScannerWrapper (ORT Analyzer - dependency detection)
    ├─→ SourceCodeScanner (ORT Scanner + ScanCode - license text extraction)
    └─→ LicenseResolver (Claude API - AI resolution, opt-in via --auto-resolve)
    ↓
Report Processing
    ├─→ ReportGenerator (JSON)
    ├─→ SbomGenerator (CycloneDX/SPDX via CycloneDxGenerator, SpdxGenerator)
    └─→ PolicyEvaluator (YAML rules + AI fix suggestions)
```

### Key Packages

**core/src/main/kotlin/com/ortoped/core/**
- `ai/` - Claude API integration: `LicenseResolver`, `CachingLicenseResolver`
- `scanner/` - ORT wrappers: `ScanOrchestrator`, `SimpleScannerWrapper`, `SourceCodeScanner`
- `curation/` - Curation support: `CurationLoader`, `CurationModels` (ORT + native formats)
- `cache/` - Fast-path caching: `LockfileHasher`, `LocalFileCache`
- `sbom/` - SBOM generation: `CycloneDxGenerator`, `SpdxGenerator`
- `policy/` - Policy engine: `PolicyEvaluator`, `PolicyConfig`, `LicenseClassifier`
- `model/` - Domain models: `ScanResult`, `Dependency`, `LicenseSuggestion`

**api/src/main/kotlin/com/ortoped/api/**
- `routes/` - REST endpoints
- `repository/` - Database access using Exposed ORM
- `adapter/` - Cache adapters for PostgreSQL persistence
- `db/migration/` - Flyway SQL migrations

### Design Patterns Used

- **Wrapper Pattern**: ORT integration insulated via `SimpleScannerWrapper`
- **Strategy Pattern**: Swappable license resolvers (interface `LicenseResolver`)
- **Coroutines**: Parallel AI API calls for performance
- **Three-Layer Caching**: In-memory → Redis → PostgreSQL
- **Fast-path Caching**: Lockfile hash → local file cache for CI/CD speed

## Tech Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin | 2.1.0 |
| Build | Gradle (Kotlin DSL) | 8.11 |
| Runtime | Java | **21 required** (22+ may fail) |
| ORT | OSS Review Toolkit | 76.0.0 |
| HTTP Client | OkHttp3 | 4.12.0 |
| CLI | Clikt | 4.4.0 |
| API Server | Ktor | 2.3.12 |
| Database | PostgreSQL + Exposed | 0.52.0 |
| Serialization | kotlinx-serialization | 1.7.1 |
| Testing | JUnit 5 + MockK | 1.13.12 |

## Environment Variables

```bash
ANTHROPIC_API_KEY    # Required only when using --auto-resolve or --enable-ai
ORT_CONFIG_DIR       # Optional, defaults to $HOME/.ort/config
```

## Important Conventions

### Code Style
- Kotlin with `-Xjsr305=strict` compiler flag
- Coroutine-first async design
- All domain models are `@Serializable` data classes
- Package structure: `com.ortoped.{core|cli|api}.*`

### Scan Defaults
- AI resolution is **off by default** (deterministic, no API key needed)
- Curations from `.ortoped/curations.yml` are auto-applied
- Lockfile cache is enabled by default (skip with `--no-cache`)
- Policy auto-evaluated when `.ortoped/policy.yml` exists

### Error Handling
- Fail-safe: AI failures never block scanning
- Graceful degradation to manual review
- Comprehensive try-catch with logging

### API Key Handling
- Lazy-loaded singleton from environment to prevent multiple reads
- Direct HTTP to Claude API (no SDK) for full control

### Database
- Flyway migrations in `api/db/migration/`
- Exposed ORM with SQL DSL
- PostgreSQL required for API module; CLI works standalone

## Testing

Tests use JUnit 5, MockK for mocking, and OkHttp MockWebServer for API mocks.

```bash
# Run all tests
./gradlew test

# Run with specific pattern
./gradlew test --tests "*PolicyEvaluator*"

# Core module only
./gradlew :core:test
```

Key test files:
- `LicenseResolverTest.kt` - AI response parsing
- `ScanOrchestratorTest.kt` - End-to-end workflow + curation/cache tests
- `PolicyEvaluatorTest.kt` - Policy rule evaluation
- `CycloneDxGeneratorTest.kt` - SBOM generation
- `CurationLoaderTest.kt` - Curation format parsing
- `LockfileHasherTest.kt` - Lockfile hashing
- `LocalFileCacheTest.kt` - File-based scan caching

## CLI Usage Examples

```bash
# Initialize .ortoped/ directory
ortoped init

# Scan local project (fast, deterministic, no AI)
ortoped scan -p /path/to/project

# Scan with AI license resolution
ortoped scan -p . --auto-resolve

# Scan with inline SBOM generation
ortoped scan -p . --sbom cyclonedx-json

# Scan remote repository
ortoped scan -p https://github.com/user/repo.git --branch main

# Scan with explicit curations file
ortoped scan -p . --curations my-curations.yml

# Scan without cache (force fresh)
ortoped scan -p . --no-cache

# Generate SBOM from scan report
ortoped sbom -i report.json -f cyclonedx-json -o project.cdx.json

# Evaluate policy
ortoped policy -i report.json -p policy.yaml --strict
```

### CI/CD Integration

```yaml
# GitHub Actions example
- name: License compliance check
  run: |
    ortoped scan -p . -o scan-report.json --sbom cyclonedx-json
    # Exit code 0 = all deps resolved + policy passes
    # Exit code 1 = unresolved deps or policy violation
```

## Common Development Workflows

### Adding a New Package Manager
ORT package managers are auto-discovered via Service Loader. No changes needed in OrtoPed.

### Modifying AI Prompts
Edit `LicenseResolver.kt` in `core/src/main/kotlin/com/ortoped/core/ai/`. The prompt template is in the `buildPrompt()` function.

### Adding New Policy Rules
Policy rules are defined in YAML. See `docs/POLICY-EVALUATION.md` for schema.

### Adding Curations
1. Run `ortoped init` if `.ortoped/` doesn't exist
2. Edit `.ortoped/curations.yml` with ORT-compatible format
3. Curations are auto-applied on next scan

### Database Schema Changes
1. Create migration in `api/db/migration/` with format `V{N}__{description}.sql`
2. Flyway auto-applies on startup
