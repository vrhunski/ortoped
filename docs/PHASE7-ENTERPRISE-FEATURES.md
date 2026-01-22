# Phase 7: Enterprise Features

Complete enterprise-ready infrastructure for OrtoPed with REST API (Ktor), Web Dashboard (Vue.js), and PostgreSQL persistence.

---

## Table of Contents

- [Overview](#overview)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [REST API](#rest-api)
- [Database Schema](#database-schema)
- [Web Dashboard](#web-dashboard)
- [Implementation Phases](#implementation-phases)
- [Docker Deployment](#docker-deployment)
- [API Reference](#api-reference)
- [Configuration](#configuration)

---

## Overview

Phase 7 transforms OrtoPed from a CLI tool into an enterprise platform with:

- **REST API**: Ktor-based async API for programmatic access
- **Web Dashboard**: Vue.js + TypeScript interactive UI
- **Database Layer**: PostgreSQL for scan history and persistence
- **Multi-Project Support**: Manage multiple projects with different policies
- **Async Scanning**: Background job processing for long-running scans

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Web Dashboard (Vue.js)                    │
│              localhost:8080 (served by Ktor)                 │
└──────────────────────────┬──────────────────────────────────┘
                           │ REST API calls
┌──────────────────────────▼──────────────────────────────────┐
│                    Ktor REST API                             │
│                                                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ Scan Routes │  │Policy Routes│  │   Project Routes    │ │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘ │
│         │                │                     │            │
│  ┌──────▼────────────────▼─────────────────────▼──────────┐│
│  │                   Service Layer                         ││
│  │  ScanService  │  PolicyService  │  ProjectService      ││
│  └──────────────────────┬──────────────────────────────────┘│
│                         │                                    │
│  ┌──────────────────────▼──────────────────────────────────┐│
│  │              Core Business Logic                         ││
│  │  ScanOrchestrator │ PolicyEvaluator │ SbomGenerator     ││
│  └──────────────────────────────────────────────────────────┘│
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                     PostgreSQL                               │
│  projects │ scans │ policies │ policy_evaluations │ api_keys│
└─────────────────────────────────────────────────────────────┘
```

---

## Technology Stack

| Component | Technology | Version | Rationale |
|-----------|------------|---------|-----------|
| **Backend Framework** | Ktor | 2.3.12 | Kotlin-native, async-first, lightweight |
| **Database** | PostgreSQL | 16+ | Robust, JSONB support for scan results |
| **ORM** | Exposed | 0.52.0 | Type-safe Kotlin DSL, JetBrains ecosystem |
| **Migrations** | Flyway | 10.15.0 | Industry standard, version-controlled SQL |
| **Connection Pool** | HikariCP | 5.1.0 | High-performance pooling |
| **Frontend** | Vue.js 3 | 3.4+ | Composition API, TypeScript support |
| **Build Tool** | Vite | 5.0+ | Fast development, optimized production |
| **State Management** | Pinia | 2.1+ | Official Vue store, TypeScript-first |
| **UI Components** | PrimeVue | 3.x | Enterprise-grade, accessible |
| **Charts** | Chart.js | 4.x | Flexible, well-documented |
| **Authentication** | JWT + API Keys | - | API keys for CI/CD, JWT for dashboard |

---

## Project Structure

Phase 7 requires converting to a **Gradle multi-module project**:

```
ortoped/
├── build.gradle.kts              # Root build (dependency versions)
├── settings.gradle.kts           # Multi-module configuration
│
├── core/                         # Shared business logic
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/ortoped/core/
│       ├── model/                # ScanResult, Dependency, etc.
│       │   ├── ScanResult.kt
│       │   ├── Dependency.kt
│       │   ├── LicenseSuggestion.kt
│       │   └── UnresolvedLicense.kt
│       ├── scanner/              # Scanning logic
│       │   ├── ScanOrchestrator.kt
│       │   ├── SimpleScannerWrapper.kt
│       │   └── SourceCodeScanner.kt
│       ├── policy/               # Policy evaluation
│       │   ├── PolicyEvaluator.kt
│       │   ├── PolicyConfig.kt
│       │   └── PolicyReport.kt
│       ├── sbom/                 # SBOM generation
│       │   ├── SbomGenerator.kt
│       │   ├── CycloneDxGenerator.kt
│       │   └── SpdxGenerator.kt
│       ├── ai/                   # AI integration
│       │   └── LicenseResolver.kt
│       └── report/               # Report generation
│           └── ReportGenerator.kt
│
├── cli/                          # CLI module
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/ortoped/cli/
│       └── Main.kt               # Clikt CLI commands
│
├── api/                          # Ktor REST API
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── kotlin/com/ortoped/api/
│       │   │   ├── Application.kt
│       │   │   ├── config/
│       │   │   │   ├── DatabaseConfig.kt
│       │   │   │   └── AppConfig.kt
│       │   │   ├── routes/
│       │   │   │   ├── ScanRoutes.kt
│       │   │   │   ├── ProjectRoutes.kt
│       │   │   │   ├── PolicyRoutes.kt
│       │   │   │   └── AuthRoutes.kt
│       │   │   ├── service/
│       │   │   │   ├── ScanService.kt
│       │   │   │   ├── ProjectService.kt
│       │   │   │   └── PolicyService.kt
│       │   │   ├── repository/
│       │   │   │   ├── ScanRepository.kt
│       │   │   │   ├── ProjectRepository.kt
│       │   │   │   └── PolicyRepository.kt
│       │   │   ├── model/
│       │   │   │   ├── dto/
│       │   │   │   │   ├── ScanRequest.kt
│       │   │   │   │   ├── ScanResponse.kt
│       │   │   │   │   └── ProjectDto.kt
│       │   │   │   └── entity/
│       │   │   │       ├── ScanEntity.kt
│       │   │   │       └── ProjectEntity.kt
│       │   │   ├── job/
│       │   │   │   └── ScanJobManager.kt
│       │   │   └── plugins/
│       │   │       ├── Routing.kt
│       │   │       ├── Serialization.kt
│       │   │       ├── Authentication.kt
│       │   │       └── RateLimiting.kt
│       │   └── resources/
│       │       ├── application.conf
│       │       └── db/migration/
│       │           └── V1__initial_schema.sql
│       └── test/
│           └── kotlin/com/ortoped/api/
│               └── ScanRoutesTest.kt
│
├── dashboard/                    # Vue.js frontend
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── index.html
│   └── src/
│       ├── main.ts
│       ├── App.vue
│       ├── router/
│       │   └── index.ts
│       ├── stores/
│       │   ├── auth.ts
│       │   ├── projects.ts
│       │   └── scans.ts
│       ├── api/
│       │   └── client.ts
│       ├── components/
│       │   ├── layout/
│       │   │   ├── Sidebar.vue
│       │   │   ├── Header.vue
│       │   │   └── MainLayout.vue
│       │   ├── scan/
│       │   │   ├── ScanCard.vue
│       │   │   ├── ScanProgress.vue
│       │   │   └── DependencyTable.vue
│       │   ├── policy/
│       │   │   └── ViolationList.vue
│       │   └── charts/
│       │       ├── LicensePieChart.vue
│       │       └── TrendLineChart.vue
│       ├── views/
│       │   ├── DashboardView.vue
│       │   ├── ProjectsView.vue
│       │   ├── ProjectDetailView.vue
│       │   ├── ScanDetailView.vue
│       │   ├── PoliciesView.vue
│       │   └── SettingsView.vue
│       └── types/
│           └── index.ts
│
├── website/                      # Static marketing site
├── docs/                         # Documentation
├── Dockerfile                    # API server image
├── Dockerfile.cli                # CLI-only image
└── docker-compose.yml            # Full stack
```

---

## REST API

### Base URL

```
http://localhost:8080/api/v1
```

### Authentication

Two authentication methods are supported:

**1. API Keys (for CI/CD and programmatic access)**
```bash
curl -H "X-API-Key: op_xxxxxxxxxxxx" http://localhost:8080/api/v1/scans
```

**2. JWT Tokens (for dashboard)**
```bash
curl -H "Authorization: Bearer eyJhbGc..." http://localhost:8080/api/v1/scans
```

### Rate Limiting

- **API Keys**: 100 requests/minute
- **JWT**: 200 requests/minute
- **Unauthenticated**: 10 requests/minute

### Endpoints Summary

#### Health & Info
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Health check |
| GET | `/version` | API version info |

#### Projects
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/projects` | List all projects |
| POST | `/projects` | Create new project |
| GET | `/projects/{id}` | Get project details |
| PUT | `/projects/{id}` | Update project |
| DELETE | `/projects/{id}` | Delete project |
| GET | `/projects/{id}/stats` | Get project statistics |

#### Scans
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/scans` | Trigger async scan |
| GET | `/scans` | List scans (filterable) |
| GET | `/scans/{id}` | Get scan status |
| GET | `/scans/{id}/result` | Get full ScanResult |
| GET | `/scans/{id}/dependencies` | Get paginated dependencies |
| DELETE | `/scans/{id}` | Cancel running scan |

#### Policies
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/policies` | List policies |
| POST | `/policies` | Create policy |
| GET | `/policies/{id}` | Get policy details |
| PUT | `/policies/{id}` | Update policy |
| DELETE | `/policies/{id}` | Delete policy |
| POST | `/scans/{id}/evaluate` | Evaluate scan against policy |

#### SBOM
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/scans/{id}/sbom` | Generate SBOM |
| GET | `/scans/{id}/sbom/{format}` | Download SBOM |

#### Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/api-keys` | Create API key |
| GET | `/auth/api-keys` | List API keys |
| DELETE | `/auth/api-keys/{id}` | Revoke API key |

---

## Database Schema

### Migration V1: Initial Schema

```sql
-- Projects
CREATE TABLE projects (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) UNIQUE NOT NULL,
    description     TEXT,
    repository_url  VARCHAR(500),
    default_branch  VARCHAR(100) DEFAULT 'main',
    policy_id       UUID,
    settings        JSONB DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Policies
CREATE TABLE policies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    version         VARCHAR(50) NOT NULL,
    description     TEXT,
    config          JSONB NOT NULL,
    is_default      BOOLEAN DEFAULT false,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Add FK from projects to policies
ALTER TABLE projects
    ADD CONSTRAINT fk_projects_policy
    FOREIGN KEY (policy_id) REFERENCES policies(id) ON DELETE SET NULL;

-- Scans
CREATE TABLE scans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID REFERENCES projects(id) ON DELETE CASCADE,
    status          VARCHAR(50) NOT NULL DEFAULT 'pending',
    scan_type       VARCHAR(50) DEFAULT 'full',
    branch          VARCHAR(100),
    commit_sha      VARCHAR(40),
    enable_ai       BOOLEAN DEFAULT true,
    enable_source_scan BOOLEAN DEFAULT false,
    result          JSONB,
    summary         JSONB,
    started_at      TIMESTAMP WITH TIME ZONE,
    completed_at    TIMESTAMP WITH TIME ZONE,
    duration_ms     INTEGER,
    error_message   TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Policy Evaluations
CREATE TABLE policy_evaluations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_id         UUID REFERENCES scans(id) ON DELETE CASCADE,
    policy_id       UUID REFERENCES policies(id) ON DELETE SET NULL,
    passed          BOOLEAN NOT NULL,
    report          JSONB NOT NULL,
    error_count     INTEGER DEFAULT 0,
    warning_count   INTEGER DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- API Keys
CREATE TABLE api_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    key_hash        VARCHAR(255) NOT NULL,
    key_prefix      VARCHAR(10) NOT NULL,
    scopes          TEXT[] DEFAULT '{}',
    expires_at      TIMESTAMP WITH TIME ZONE,
    last_used_at    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_scans_project_id ON scans(project_id);
CREATE INDEX idx_scans_status ON scans(status);
CREATE INDEX idx_scans_created_at ON scans(created_at DESC);
CREATE INDEX idx_policy_evaluations_scan_id ON policy_evaluations(scan_id);
CREATE INDEX idx_api_keys_key_prefix ON api_keys(key_prefix);
```

### Scan Status Flow

```
pending → scanning → analyzing → complete
    │         │          │
    └─────────┴──────────┴──────→ failed
```

---

## Web Dashboard

### Pages

| Page | Route | Description |
|------|-------|-------------|
| Dashboard | `/` | Overview with stats, charts, recent scans |
| Projects | `/projects` | List and manage projects |
| Project Detail | `/projects/:id` | Project info, scan history |
| Scan Detail | `/scans/:id` | Dependencies, violations, SBOM export |
| Policies | `/policies` | Create and manage policies |
| Settings | `/settings` | API keys, preferences |

### Component Architecture

```
App.vue
└── MainLayout.vue
    ├── Sidebar.vue
    │   └── NavItem.vue (repeated)
    ├── Header.vue
    │   ├── Breadcrumbs.vue
    │   └── UserMenu.vue
    └── <router-view>
        ├── DashboardView.vue
        │   ├── StatCard.vue (x4)
        │   ├── LicensePieChart.vue
        │   └── RecentScansTable.vue
        ├── ProjectsView.vue
        │   └── ProjectCard.vue (repeated)
        ├── ScanDetailView.vue
        │   ├── ScanHeader.vue
        │   ├── ScanSummary.vue
        │   ├── DependencyTable.vue
        │   │   └── DependencyRow.vue
        │   ├── ViolationList.vue
        │   │   └── ViolationCard.vue
        │   └── SbomExportPanel.vue
        └── PoliciesView.vue
            └── PolicyEditor.vue
```

### State Management (Pinia)

```typescript
// stores/scans.ts
export const useScansStore = defineStore('scans', () => {
  const scans = ref<Scan[]>([])
  const currentScan = ref<Scan | null>(null)
  const isLoading = ref(false)

  async function fetchScans(projectId?: string) { ... }
  async function triggerScan(request: ScanRequest) { ... }
  async function pollScanStatus(id: string) { ... }

  return { scans, currentScan, isLoading, fetchScans, triggerScan, pollScanStatus }
})
```

---

## Implementation Phases

### Phase 7.1: Foundation (Week 1-2)

**Goals:**
- Multi-module Gradle project structure
- Ktor application skeleton
- PostgreSQL + Flyway setup
- Health endpoint

**Tasks:**
1. Update `settings.gradle.kts` with module includes
2. Create `core/build.gradle.kts` with shared dependencies
3. Move existing source to `core/src/main/kotlin/com/ortoped/core/`
4. Create `cli/build.gradle.kts` dependent on core
5. Refactor CLI Main.kt imports
6. Create `api/build.gradle.kts` with Ktor dependencies
7. Implement basic Ktor Application.kt
8. Configure HikariCP + PostgreSQL connection
9. Add Flyway migrations
10. Implement `/api/v1/health` endpoint

**Verification:**
```bash
./gradlew :cli:run --args="scan --demo"  # CLI still works
./gradlew :api:run                        # API starts
curl http://localhost:8080/api/v1/health  # Returns 200
```

### Phase 7.2: Scan API (Week 2-3)

**Goals:**
- Async scan job processing
- Scan CRUD endpoints
- Database persistence

**Tasks:**
1. Implement ScanJobManager with coroutine workers
2. Create ScanRepository (Exposed DAO)
3. Implement POST `/scans` - trigger scan
4. Implement GET `/scans` - list scans
5. Implement GET `/scans/{id}` - get status
6. Implement GET `/scans/{id}/result` - get full result
7. Store ScanResult as JSONB
8. Add request validation

**Verification:**
```bash
# Trigger scan
curl -X POST http://localhost:8080/api/v1/scans \
  -H "Content-Type: application/json" \
  -d '{"projectId": "...", "demoMode": true}'

# Poll until complete
curl http://localhost:8080/api/v1/scans/{id}

# Get result
curl http://localhost:8080/api/v1/scans/{id}/result
```

### Phase 7.3: Basic Dashboard (Week 3-4)

**Goals:**
- Vue.js project setup
- Core dashboard pages
- API integration

**Tasks:**
1. Create Vue 3 + Vite + TypeScript project
2. Configure Vue Router with routes
3. Set up Pinia stores
4. Install PrimeVue components
5. Create MainLayout with Sidebar
6. Implement DashboardView with stats
7. Implement ScanDetailView with DependencyTable
8. Create API client with axios
9. Configure Ktor to serve static files

**Verification:**
```bash
cd dashboard && npm run dev  # Development
./gradlew :api:run           # API with static serving
open http://localhost:8080   # See dashboard
```

### Phase 7.4: Projects & Policies (Week 4-5)

**Goals:**
- Project management
- Policy CRUD
- Automatic policy evaluation

**Tasks:**
1. Implement ProjectRepository
2. Create project endpoints (CRUD)
3. Implement PolicyRepository
4. Create policy endpoints (CRUD)
5. Auto-evaluate policy after scan completion
6. Store PolicyReport in database
7. Create ProjectsView in dashboard
8. Create PoliciesView in dashboard
9. Add ViolationList to ScanDetailView

**Verification:**
```bash
# Create project
curl -X POST http://localhost:8080/api/v1/projects \
  -H "Content-Type: application/json" \
  -d '{"name": "My Project", "repositoryUrl": "https://github.com/..."}'

# Create policy
curl -X POST http://localhost:8080/api/v1/policies \
  -H "Content-Type: application/json" \
  -d '{"name": "Default", "config": {...}}'

# Scan with policy
curl -X POST http://localhost:8080/api/v1/scans \
  -d '{"projectId": "...", "policyId": "..."}'
```

### Phase 7.5: Authentication (Week 5-6)

**Goals:**
- API key authentication
- Rate limiting
- Settings UI

**Tasks:**
1. Implement API key generation (secure random)
2. Store hashed keys with bcrypt
3. Create auth middleware for Ktor
4. Implement rate limiting plugin
5. Create SettingsView in dashboard
6. Add API key management UI
7. Protect all endpoints

**Verification:**
```bash
# Create API key
curl -X POST http://localhost:8080/api/v1/auth/api-keys \
  -d '{"name": "CI Pipeline"}'
# Returns: {"key": "op_xxxx...", "prefix": "op_xxxx"}

# Use API key
curl -H "X-API-Key: op_xxxx..." http://localhost:8080/api/v1/scans
```

### Phase 7.6: Docker & Polish (Week 6)

**Goals:**
- Production Docker setup
- Full stack compose
- Documentation

**Tasks:**
1. Update Dockerfile for API server
2. Create docker-compose.yml with PostgreSQL
3. Add health checks
4. Configure environment variables
5. Write deployment documentation
6. End-to-end testing

**Verification:**
```bash
docker-compose up -d
curl http://localhost:8080/api/v1/health
open http://localhost:8080
```

---

## Docker Deployment

### docker-compose.yml

```yaml
version: '3.8'

services:
  api:
    build: .
    ports:
      - "8080:8080"
    environment:
      - DATABASE_URL=jdbc:postgresql://db:5432/ortoped
      - DATABASE_USER=ortoped
      - DATABASE_PASSWORD=ortoped
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
    depends_on:
      db:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/v1/health"]
      interval: 10s
      timeout: 5s
      retries: 5

  db:
    image: postgres:16-alpine
    environment:
      - POSTGRES_DB=ortoped
      - POSTGRES_USER=ortoped
      - POSTGRES_PASSWORD=ortoped
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ortoped"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  pgdata:
```

### Dockerfile

```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY core core
COPY api api
RUN ./gradlew :api:installDist --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=builder /app/api/build/install/api .
COPY dashboard/dist /app/static

EXPOSE 8080
ENTRYPOINT ["/app/bin/api"]
```

---

## API Reference

### Trigger Scan

**Request:**
```http
POST /api/v1/scans
Content-Type: application/json
X-API-Key: op_xxxxxxxxxxxx

{
  "projectId": "550e8400-e29b-41d4-a716-446655440000",
  "branch": "main",
  "enableAi": true,
  "enableSourceScan": false
}
```

**Response (202 Accepted):**
```json
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "status": "pending",
  "createdAt": "2025-01-21T10:30:00Z"
}
```

### Get Scan Status

**Request:**
```http
GET /api/v1/scans/660e8400-e29b-41d4-a716-446655440001
X-API-Key: op_xxxxxxxxxxxx
```

**Response:**
```json
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "projectId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "scanning",
  "progress": {
    "step": "Analyzing dependencies",
    "percentage": 45
  },
  "startedAt": "2025-01-21T10:30:05Z",
  "createdAt": "2025-01-21T10:30:00Z"
}
```

### Get Scan Result

**Request:**
```http
GET /api/v1/scans/660e8400-e29b-41d4-a716-446655440001/result
X-API-Key: op_xxxxxxxxxxxx
```

**Response:**
```json
{
  "projectName": "my-project",
  "projectVersion": "1.0.0",
  "scanDate": "2025-01-21T10:35:00Z",
  "aiEnhanced": true,
  "summary": {
    "totalDependencies": 150,
    "resolvedLicenses": 145,
    "unresolvedLicenses": 5,
    "aiResolvedLicenses": 4,
    "licenseDistribution": {
      "MIT": 80,
      "Apache-2.0": 45,
      "BSD-3-Clause": 15
    }
  },
  "dependencies": [...]
}
```

### Generate SBOM

**Request:**
```http
POST /api/v1/scans/660e8400-e29b-41d4-a716-446655440001/sbom
Content-Type: application/json
X-API-Key: op_xxxxxxxxxxxx

{
  "format": "cyclonedx-json",
  "includeAiSuggestions": true
}
```

**Response:**
```json
{
  "bomFormat": "CycloneDX",
  "specVersion": "1.5",
  "version": 1,
  "metadata": {...},
  "components": [...]
}
```

---

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/ortoped` |
| `DATABASE_USER` | Database username | `ortoped` |
| `DATABASE_PASSWORD` | Database password | `ortoped` |
| `ANTHROPIC_API_KEY` | Claude API key | (required for AI) |
| `API_PORT` | Server port | `8080` |
| `JWT_SECRET` | JWT signing secret | (auto-generated) |
| `RATE_LIMIT_REQUESTS` | Requests per minute | `100` |

### application.conf

```hocon
ktor {
    deployment {
        port = 8080
        port = ${?API_PORT}
    }
    application {
        modules = [ com.ortoped.api.ApplicationKt.module ]
    }
}

database {
    url = "jdbc:postgresql://localhost:5432/ortoped"
    url = ${?DATABASE_URL}
    user = "ortoped"
    user = ${?DATABASE_USER}
    password = "ortoped"
    password = ${?DATABASE_PASSWORD}
    maxPoolSize = 10
}

auth {
    jwtSecret = ${?JWT_SECRET}
    apiKeyPrefix = "op_"
}
```

---

## Next Steps (Post-MVP)

After Phase 7 MVP completion, potential enhancements:

1. **Multi-tenancy**: Organizations with member roles
2. **User Management**: Email/password auth, SSO
3. **Webhooks**: Notifications for scan completion
4. **Approval Workflows**: Request/approve policy violations
5. **WebSocket**: Real-time scan progress
6. **Caching**: Redis for scan results
7. **Analytics**: Trend analysis, compliance metrics

---

## Resources

- **Ktor Documentation**: https://ktor.io/docs/
- **Exposed ORM**: https://github.com/JetBrains/Exposed
- **Vue.js 3**: https://vuejs.org/guide/
- **PrimeVue**: https://primevue.org/
- **Flyway**: https://flywaydb.org/documentation/

---

**Document Version:** 1.0
**Created:** January 21, 2025
**Status:** Planning Complete
