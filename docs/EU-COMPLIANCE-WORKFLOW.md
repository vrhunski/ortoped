# EU Compliance Workflow

OrtoPed implements a comprehensive EU/German regulatory compliance workflow for open source license management. This document describes the complete workflow, architecture, and API endpoints.

## Workflow Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        EU COMPLIANCE WORKFLOW                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   1. SCAN ──▶ 2. POLICY CHECK ──▶ 3. CURATION ──▶ 4. EU REPORT              │
│                                                                              │
│   Dependencies    Violations        Human review     Audit-ready            │
│   analyzed        identified        with AI assist   documentation          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Phase 1: Scan
- Analyze project dependencies
- Detect declared and concluded licenses
- AI-assisted license resolution for ambiguous cases

### Phase 2: Policy Check
- Evaluate scan results against configured policies
- Identify license violations and warnings
- Generate "Why Not?" explanations for violations

### Phase 3: Curation
- Human review of all dependencies
- AI suggestions with confidence levels
- Structured justifications for non-permissive licenses
- OR license resolution (explicit human choice)
- Two-role approval (curator ≠ approver)

### Phase 4: EU Compliance Report
- Complete audit trail
- Structured justifications
- Approval chain documentation
- Regulatory-ready format

---

## Core Principles

### 1. UNKNOWN = FORBIDDEN
All unknown or unresolved licenses must be curated before the project can be considered compliant. No automatic pass-through of unknown licenses.

### 2. Four-Eyes Principle
The person who curates (curator) cannot be the same person who approves (approver). This is enforced at the database level.

### 3. Complete Audit Trail
Every action is logged with:
- Timestamp
- Actor ID and role
- Previous and new state
- Change description

### 4. Structured Justifications
Non-permissive licenses require documented justification including:
- Justification type (AI_ACCEPTED, MANUAL_OVERRIDE, EVIDENCE_BASED)
- Evidence reference (license file, vendor confirmation, legal opinion)
- Distribution scope context

---

## Database Schema

### Core Tables

#### `curation_sessions`
Tracks overall curation progress for a scan.

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| scan_id | UUID | Reference to scan |
| status | VARCHAR | IN_PROGRESS, COMPLETED, APPROVED |
| curator_id | VARCHAR | ID of the curator |
| curator_name | VARCHAR | Display name of curator |
| approved_by | VARCHAR | ID of the approver |
| approver_name | VARCHAR | Display name of approver |
| approver_role | VARCHAR | Role (LEGAL, COMPLIANCE, MANAGER) |
| submitted_for_approval | BOOLEAN | Whether submitted for approval |
| submitted_at | TIMESTAMP | When submitted |

#### `curations`
Individual curation decisions for each dependency.

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| session_id | UUID | Reference to session |
| dependency_id | VARCHAR | Package identifier |
| status | VARCHAR | PENDING, ACCEPTED, REJECTED, MODIFIED |
| curated_license | VARCHAR | Final license decision |
| requires_justification | BOOLEAN | Whether justification needed |
| justification_complete | BOOLEAN | Whether justification provided |
| is_or_license | BOOLEAN | Whether OR license expression |
| or_license_choice | VARCHAR | Chosen license from OR expression |
| distribution_scope | VARCHAR | INTERNAL, BINARY, SOURCE, SAAS |

#### `curation_justifications`
Structured justifications for license decisions.

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| curation_id | UUID | Reference to curation |
| spdx_id | VARCHAR | SPDX license identifier |
| license_category | VARCHAR | PERMISSIVE, WEAK_COPYLEFT, etc. |
| justification_type | VARCHAR | AI_ACCEPTED, MANUAL_OVERRIDE, etc. |
| justification_text | TEXT | Human-readable justification |
| evidence_type | VARCHAR | LICENSE_FILE, VENDOR_CONFIRMATION, etc. |
| evidence_reference | TEXT | URL or document reference |
| distribution_scope | VARCHAR | Distribution context |
| justification_hash | VARCHAR | SHA-256 for tamper detection |

#### `curation_approvals`
Two-role approval workflow tracking.

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| session_id | UUID | Reference to session |
| submitter_id | VARCHAR | Curator who submitted |
| approver_id | VARCHAR | Person who approved |
| decision | VARCHAR | APPROVED, REJECTED, RETURNED |
| decision_comment | TEXT | Approval notes |

#### `audit_logs`
Immutable audit trail for all actions.

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| entity_type | VARCHAR | CURATION, SESSION, APPROVAL |
| entity_id | UUID | Reference to entity |
| action | VARCHAR | CREATE, DECIDE, APPROVE, etc. |
| actor_id | VARCHAR | Who performed action |
| actor_role | VARCHAR | CURATOR, APPROVER, SYSTEM |
| previous_state | JSONB | State before change |
| new_state | JSONB | State after change |
| change_summary | TEXT | Human-readable summary |

---

## API Endpoints

### Curation Endpoints

#### Start Curation Session
```
POST /api/v1/scans/{scanId}/curation/start
```
Creates a new curation session for a scan.

**Request Headers:**
- `X-Curator-Id`: Curator identifier
- `X-Curator-Name`: Curator display name (optional)

**Response:**
```json
{
  "sessionId": "uuid",
  "status": "IN_PROGRESS",
  "totalItems": 150,
  "statistics": {
    "pending": 150,
    "accepted": 0,
    "rejected": 0,
    "modified": 0
  }
}
```

#### Get Curation Items
```
GET /api/v1/scans/{scanId}/curation/items?status=PENDING&priority=HIGH
```
Lists dependencies requiring curation.

**Query Parameters:**
- `status`: Filter by status (PENDING, ACCEPTED, REJECTED, MODIFIED)
- `priority`: Filter by priority (HIGH, MEDIUM, LOW)
- `hasAiSuggestion`: Filter for items with AI suggestions

#### Submit Curation Decision
```
PUT /api/v1/scans/{scanId}/curation/items/{dependencyId}
```
Submit a curation decision for a dependency.

**Request Body:**
```json
{
  "action": "ACCEPT",
  "curatedLicense": "MIT",
  "comment": "Verified license in repository"
}
```

#### Submit Justification
```
POST /api/v1/scans/{scanId}/curation/items/{dependencyId}/justify
```
Submit structured justification for a curation decision.

**Request Body:**
```json
{
  "spdxId": "GPL-3.0-only",
  "licenseCategory": "STRONG_COPYLEFT",
  "justificationType": "EVIDENCE_BASED",
  "justificationText": "License verified via vendor confirmation",
  "evidenceType": "VENDOR_CONFIRMATION",
  "evidenceReference": "https://vendor.com/license-confirmation",
  "distributionScope": "BINARY"
}
```

#### Get Explanations
```
GET /api/v1/scans/{scanId}/curation/items/{dependencyId}/explanations
```
Get "Why Not?" explanations, obligations, and compatibility info.

**Response:**
```json
{
  "whyNotExplanations": [
    {
      "ruleId": "COPYLEFT_NOT_ALLOWED",
      "ruleName": "No Copyleft Licenses",
      "explanation": "GPL-3.0 requires source disclosure",
      "resolutions": ["Choose alternative library", "Request exemption"]
    }
  ],
  "obligations": [
    {
      "type": "SOURCE_DISCLOSURE",
      "description": "Must provide source code",
      "effort": "HIGH"
    }
  ],
  "compatibilityIssues": []
}
```

#### Submit for Approval
```
POST /api/v1/scans/{scanId}/curation/submit-for-approval
```
Curator submits completed curation for approval.

**Request Headers:**
- `X-Curator-Id`: Curator identifier

**Response:**
```json
{
  "success": true,
  "submittedAt": "2024-01-31T12:00:00Z",
  "readinessCheck": {
    "allItemsCurated": true,
    "justificationsComplete": true,
    "orLicensesResolved": true
  }
}
```

#### Approve/Reject Session
```
POST /api/v1/scans/{scanId}/curation/approval/decide
```
Approver makes decision on curation session.

**Request Headers:**
- `X-Approver-Id`: Approver identifier (must differ from curator)
- `X-Approver-Name`: Approver display name
- `X-Approver-Role`: Role (LEGAL, COMPLIANCE, MANAGER)

**Request Body:**
```json
{
  "decision": "APPROVED",
  "comment": "All licenses verified and documented"
}
```

**Error Response (same person):**
```json
{
  "error": "EU Compliance Violation: Approver must be different from curator (four-eyes principle)"
}
```

#### Get OR Licenses
```
GET /api/v1/scans/{scanId}/curation/or-licenses
```
List unresolved OR license expressions.

#### Resolve OR License
```
POST /api/v1/scans/{scanId}/curation/items/{dependencyId}/resolve-or
```
Choose one license from OR expression.

**Request Body:**
```json
{
  "chosenLicense": "MIT",
  "reason": "MIT is more permissive and compatible with our policy"
}
```

#### Get Audit Logs
```
GET /api/v1/scans/{scanId}/curation/audit-logs
```
Query audit trail for curation session.

---

### Report Endpoints

#### Generate EU Compliance Report
```
GET /api/v1/scans/{scanId}/reports/eu-compliance?format=json|html
```
Generate comprehensive EU compliance report.

**Requirements:**
- Curation must be APPROVED

**Response (JSON):**
```json
{
  "reportId": "uuid",
  "scanId": "uuid",
  "format": "json",
  "filename": "eu-compliance-project-2024-01-31.json",
  "content": "...",
  "generatedAt": "2024-01-31T12:00:00Z",
  "complianceStatus": "COMPLIANT"
}
```

**Report Structure:**
```json
{
  "reportId": "uuid",
  "reportVersion": "1.0",
  "generatedAt": "2024-01-31T12:00:00Z",
  "regulatory": {
    "framework": "EU Cyber Resilience Act / German IT Security Act",
    "complianceLevel": "FULL",
    "auditReady": true,
    "fourEyesPrincipleApplied": true,
    "allLicensesDocumented": true
  },
  "project": {
    "name": "my-project",
    "repositoryUrl": "https://github.com/org/project",
    "distributionScope": "BINARY"
  },
  "workflowSummary": {
    "scanCompletedAt": "2024-01-30T10:00:00Z",
    "policyEvaluatedAt": "2024-01-30T10:01:00Z",
    "policyPassed": false,
    "curationStartedAt": "2024-01-30T11:00:00Z",
    "curationSubmittedAt": "2024-01-31T09:00:00Z",
    "curationApprovedAt": "2024-01-31T11:00:00Z",
    "curatorId": "john.doe",
    "approverId": "jane.smith"
  },
  "statistics": {
    "totalDependencies": 150,
    "licensesResolvedByDeclared": 120,
    "licensesResolvedByAi": 20,
    "licensesResolvedByCuration": 10,
    "policyViolationsFound": 5,
    "policyViolationsResolved": 5,
    "orLicensesResolved": 3,
    "justificationsProvided": 8
  },
  "licenseDecisions": [...],
  "approvalChain": {
    "curator": {
      "id": "john.doe",
      "name": "John Doe",
      "role": "CURATOR",
      "actionDate": "2024-01-31T09:00:00Z"
    },
    "approver": {
      "id": "jane.smith",
      "name": "Jane Smith",
      "role": "LEGAL",
      "actionDate": "2024-01-31T11:00:00Z"
    },
    "fourEyesCompliant": true
  },
  "auditTrail": [...]
}
```

#### Export ORT Curations
```
GET /api/v1/scans/{scanId}/curation/export/curations-yaml
```
Export curations in ORT-compatible format.

#### Export NOTICE File
```
GET /api/v1/scans/{scanId}/curation/export/notice
```
Generate NOTICE file for distribution.

---

## Justification Types

| Type | Description | When to Use |
|------|-------------|-------------|
| `AI_ACCEPTED` | AI suggestion accepted | When AI suggestion is verified correct |
| `MANUAL_OVERRIDE` | Human override of AI | When AI suggestion is wrong or incomplete |
| `EVIDENCE_BASED` | Based on external evidence | When you have license file, vendor confirmation |
| `POLICY_EXEMPTION` | Exempted by policy | When policy allows specific exception |

## Evidence Types

| Type | Description |
|------|-------------|
| `LICENSE_FILE` | License file found in repository |
| `REPO_INSPECTION` | Manual inspection of repository |
| `VENDOR_CONFIRMATION` | Written confirmation from vendor |
| `LEGAL_OPINION` | Legal department opinion |

## Distribution Scopes

| Scope | Description | Typical Obligations |
|-------|-------------|---------------------|
| `INTERNAL` | Internal use only | Minimal |
| `BINARY` | Binary distribution | Standard disclosure |
| `SOURCE` | Source code distribution | Full source disclosure |
| `SAAS` | SaaS/Cloud deployment | AGPL network disclosure |
| `EMBEDDED` | Embedded systems | Hardware-specific obligations |

---

## Frontend Integration

### Curation Session View

The `CurationSessionView.vue` component provides:

1. **Item List**: Filterable list of dependencies to curate
2. **AI Suggestions**: Display AI-suggested licenses with confidence
3. **Justification Form**: Structured form for non-permissive licenses
4. **OR License Resolver**: Dialog for choosing from OR expressions
5. **Explanations Panel**: "Why Not?" explanations and obligations
6. **Submit for Approval**: Workflow to submit completed curation
7. **Approval Decision**: Interface for approvers to approve/reject

### Key UI Elements

```vue
<!-- Status indicators -->
<Tag :severity="getStatusSeverity(item.status)">{{ item.status }}</Tag>

<!-- AI confidence badge -->
<Badge v-if="item.aiConfidence" :value="item.aiConfidence"
       :severity="getConfidenceSeverity(item.aiConfidence)" />

<!-- Justification required indicator -->
<i v-if="item.requiresJustification && !item.justificationComplete"
   class="pi pi-exclamation-triangle text-warning" />

<!-- OR license indicator -->
<Tag v-if="item.isOrLicense" severity="info">OR License</Tag>
```

---

## Database Migrations

### V6: EU Compliance Audit Tables
- `audit_logs` - Immutable audit trail
- `curation_justifications` - Structured justifications
- `curation_approvals` - Two-role approval
- `or_license_resolutions` - OR license tracking

### V7: EU Workflow Columns
- `curator_id`, `curator_name` on curation_sessions
- `approver_name`, `approver_role` on curation_sessions

---

## Compliance Checklist

Before generating EU Compliance Report:

- [ ] All dependencies have been curated (no PENDING items)
- [ ] All justifications are complete for non-permissive licenses
- [ ] All OR licenses have been resolved
- [ ] Session has been submitted for approval
- [ ] Session has been approved by different person than curator
- [ ] Audit trail is complete

---

## Error Handling

### Common Errors

| Error | Cause | Resolution |
|-------|-------|------------|
| `Curation must be completed` | Attempting to generate report before approval | Complete and approve curation |
| `Four-eyes principle violation` | Same person trying to approve own work | Different person must approve |
| `Justification required` | Non-permissive license without justification | Provide structured justification |
| `OR license unresolved` | OR expression not explicitly chosen | Use resolve-or endpoint |

---

## Security Considerations

1. **Authentication Required**: All curation endpoints require authentication
2. **Role-Based Access**: Curators and approvers have different permissions
3. **Audit Immutability**: Audit logs cannot be modified or deleted
4. **Hash Verification**: Justification hashes detect tampering
5. **Four-Eyes Enforcement**: Database trigger prevents self-approval
