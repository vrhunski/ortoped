# Phase 8: Curation System Implementation

This document describes the complete implementation of the License Curation System, including manual review of AI suggestions, comprehensive report generation, and ORT-compatible exports.

## Overview

Phase 8 adds a complete curation workflow that allows users to:
- Review and approve AI-suggested license identifications
- Apply bulk actions and templates for efficient curation
- Generate comprehensive reports in multiple formats
- Export results in ORT Evaluator-compatible format

## Architecture

### Database Schema

New tables added via Flyway migration `V3__curation_schema.sql`:

```
curation_sessions
├── id (UUID, PK)
├── scan_id (UUID, FK -> scans)
├── status (VARCHAR: IN_PROGRESS, COMPLETED, APPROVED)
├── total_items, pending_items, accepted_items, rejected_items, modified_items
├── approved_by, approved_at, approval_comment
└── created_at, updated_at

curations
├── id (UUID, PK)
├── session_id (UUID, FK -> curation_sessions)
├── dependency_id, dependency_name, dependency_version, scope
├── original_license, ai_suggested_license, ai_confidence, ai_reasoning
├── curated_license, curator_id, curator_comment
├── status (VARCHAR: PENDING, ACCEPTED, REJECTED, MODIFIED)
├── priority (VARCHAR: HIGH, MEDIUM, LOW)
└── curated_at, created_at

curated_scans (for incremental curation)
├── id (UUID, PK)
├── scan_id (UUID, FK -> scans)
├── previous_scan_id (UUID, nullable)
├── session_id (UUID, FK -> curation_sessions)
└── is_incremental, carried_over_count, new_items_count

curation_templates
├── id (UUID, PK)
├── name, description
├── conditions (JSONB), actions (JSONB)
├── is_active, usage_count
└── created_at, updated_at
```

### Backend Components

#### Repositories
- `CurationSessionRepository` - Session CRUD operations
- `CurationRepository` - Individual curation item operations with filtering/sorting
- `CuratedScanRepository` - Incremental curation tracking
- `CurationTemplateRepository` - Template CRUD with usage tracking

#### Services
- `CurationService` - Main business logic for curation workflow
- `TemplateService` - Template application engine with condition matching
- `ReportService` - Comprehensive report generation (JSON, HTML, ORT)
- `SpdxService` - SPDX license validation and lookup

#### API Routes
- `CurationRoutes` - `/api/v1/scans/{scanId}/curation/*`
- `TemplateRoutes` - `/api/v1/curation/templates/*`
- `ReportRoutes` - `/api/v1/scans/{scanId}/reports/*`
- `LicenseRoutes` - `/api/v1/licenses/*`

### Frontend Components

#### Views
- `CurationsView.vue` - Main curation sessions list
- `CurationSessionView.vue` - Detailed curation workspace
- `CurationTemplatesView.vue` - Template management interface

#### Features
- Filtering by status, priority, confidence level
- Bulk accept/reject operations
- Quick action buttons for common decisions
- Session approval workflow
- Real-time statistics and progress tracking

## API Reference

### Curation Sessions

#### Start Curation Session
```
POST /api/v1/scans/{scanId}/curation/start
Response: CurationSession
```

#### Get Curation Session
```
GET /api/v1/scans/{scanId}/curation
Response: CurationSession
```

#### List Curation Items
```
GET /api/v1/scans/{scanId}/curation/items
Query params: status, priority, confidence, search, sortBy, sortDirection, page, pageSize
Response: { items: CurationItem[], total, page, pageSize }
```

#### Submit Decision
```
PUT /api/v1/scans/{scanId}/curation/items/{dependencyId}
Body: { action: "ACCEPT"|"REJECT"|"MODIFY", curatedLicense?, comment? }
Response: CurationItem
```

#### Bulk Decision
```
POST /api/v1/scans/{scanId}/curation/bulk
Body: { decisions: [{ dependencyId, action, curatedLicense?, comment? }] }
Response: { updated: number, results: CurationItem[] }
```

#### Approve Session
```
POST /api/v1/scans/{scanId}/curation/approve
Body: { comment? }
Response: CurationSession
```

### Curation Templates

#### List Templates
```
GET /api/v1/curation/templates
Response: { templates: CurationTemplate[], total }
```

#### Create Template
```
POST /api/v1/curation/templates
Body: { name, description?, conditions, actions, isActive }
Response: CurationTemplate
```

#### Update Template
```
PUT /api/v1/curation/templates/{id}
Body: Partial<CurationTemplate>
Response: CurationTemplate
```

#### Delete Template
```
DELETE /api/v1/curation/templates/{id}
Response: 204 No Content
```

### Reports

#### Get Report Summary
```
GET /api/v1/scans/{scanId}/reports/summary
Response: ReportSummary
```

#### Generate Report
```
POST /api/v1/scans/{scanId}/reports/generate
Body: { format?, includePolicy?, includeCuration?, includeAuditTrail?, includeDependencyDetails? }
Response: { reportId, scanId, format, filename, content, generatedAt, metadata }
```

#### Download Report
```
GET /api/v1/scans/{scanId}/reports/download?format=json|html
Response: File download
```

#### ORT Export
```
GET /api/v1/scans/{scanId}/reports/ort
Response: { scanId, filename, content, format, generatedAt }
```

### SPDX Licenses

#### Search Licenses
```
GET /api/v1/licenses/spdx/search?q={query}&osiOnly={boolean}&limit={number}
Response: { licenses: SpdxLicenseInfo[], total }
```

#### Get License Details
```
GET /api/v1/licenses/spdx/{licenseId}
Response: SpdxLicenseDetailResponse
```

#### Validate Licenses
```
POST /api/v1/licenses/validate
Body: { licenseIds: string[] }
Response: { results: ValidationResult[], validCount, invalidCount }
```

## Data Models

### CurationSession
```typescript
interface CurationSession {
  id: string
  scanId: string
  status: 'IN_PROGRESS' | 'COMPLETED' | 'APPROVED'
  statistics: {
    total: number
    pending: number
    accepted: number
    rejected: number
    modified: number
  }
  approval: {
    approvedBy: string
    approvedAt: string
    comment?: string
  } | null
  createdAt: string
  updatedAt: string
}
```

### CurationItem
```typescript
interface CurationItem {
  id: string
  dependencyId: string
  dependencyName: string
  dependencyVersion: string
  scope: string | null
  declaredLicenses: string[]
  detectedLicenses: string[]
  originalConcludedLicense: string | null
  aiSuggestion: AiSuggestion | null
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'MODIFIED'
  curatedLicense: string | null
  curatorComment: string | null
  curatorId: string | null
  curatedAt: string | null
  priority: PriorityInfo | null
  spdxValidated: boolean
  spdxLicense: SpdxLicenseInfo | null
}
```

### CurationTemplate
```typescript
interface CurationTemplate {
  id: string
  name: string
  description: string | null
  conditions: TemplateCondition[]
  actions: TemplateAction[]
  isActive: boolean
  usageCount: number
  createdAt: string
}

interface TemplateCondition {
  field: string  // aiConfidence, aiSuggestedLicense, originalLicense, dependencyName, scope, priority
  operator: string  // EQUALS, NOT_EQUALS, CONTAINS, STARTS_WITH, ENDS_WITH, MATCHES, IS_EMPTY, IS_NOT_EMPTY
  value: string
}

interface TemplateAction {
  type: string  // SET_STATUS, SET_LICENSE, ADD_COMMENT, SET_PRIORITY
  value: string
}
```

## Priority Calculation

Items are prioritized based on:
1. **AI Confidence** - Lower confidence = higher priority
2. **License Category** - Copyleft/unknown licenses = higher priority
3. **Scope** - Production dependencies = higher priority
4. **Resolution Status** - Unresolved = higher priority

## ORT Export Format

The ORT export generates a JSON structure compatible with OSS Review Toolkit's evaluator format:

```json
{
  "repository": {
    "vcs": { "type": "Git", "url": "...", "revision": "...", "path": "" },
    "config": { "excludes": {}, "curations": {}, "resolutions": {} }
  },
  "analyzer": {
    "startTime": "...",
    "endTime": "...",
    "projects": [...],
    "packages": [...],
    "issues": []
  },
  "evaluator": {
    "startTime": "...",
    "endTime": "...",
    "violations": [...]
  },
  "labels": {
    "ortoped.version": "1.0",
    "ortoped.scanId": "...",
    "ortoped.curationStatus": "..."
  }
}
```

## Usage Workflow

1. **Complete a scan** with AI-enabled license resolution
2. **Navigate to Curations** page and start a new curation session
3. **Review items** sorted by priority (high priority items first)
4. **Make decisions**: Accept AI suggestions, reject incorrect ones, or modify with custom license
5. **Apply templates** for bulk operations on matching items
6. **Approve session** when all items are reviewed
7. **Download reports** in JSON, HTML, or ORT format

## Configuration

### Default Templates (created via migration)

1. **Auto-accept High Confidence** - Accepts items where AI confidence is HIGH
2. **Flag Copyleft Licenses** - Sets high priority for GPL/AGPL/LGPL licenses
3. **Accept Permissive Licenses** - Auto-accepts MIT, Apache-2.0, BSD licenses

## Future Enhancements

- Real-time collaboration (multiple curators)
- Incremental curation (carry over decisions from previous scans)
- AI-powered template suggestions
- Integration with external vulnerability databases
- Custom policy rules based on curation decisions
