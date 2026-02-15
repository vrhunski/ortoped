# EU Compliance Workflow & CRA Audit Trail — Completion Plan

**Date:** 2026-02-15
**Status:** Draft
**Focus:** Close remaining gaps to achieve full CRA-compliant audit trail and reporting

---

## Current State

The EU compliance foundation is **largely built** — database schema, 4-eyes enforcement, audit logging, curation approval workflow, and the EU compliance report generator all exist. What remains is hardening, dashboard exposure, authentication, and covering CRA requirements beyond SBOM/audit.

| Component | Status |
|-----------|--------|
| `audit_logs` table + immutable logging | Done (V6 migration) |
| 4-eyes principle (DB trigger + app check) | Done, **off by default** |
| Curation approval workflow (submit/approve/reject/return) | Done |
| `curation_justifications` table (structured evidence) | Done |
| `curation_approvals` table (approver chain) | Done |
| EU compliance report (`/reports/eu-compliance`) | Done, requires APPROVED status |
| Audit log API (`/curation/audit-logs`) | Done |
| Dashboard Audit Trail tab | **Stub only** — placeholder text |
| Dashboard EU report viewer | **Not started** |
| Real authentication (curator/approver identity) | **Not started** — uses HTTP headers |
| Compliance status dashboard | **Not started** |
| Native export with structured justification | **Incomplete** — flat string only |
| Vulnerability disclosure (CRA Art. 11) | **Not started** |
| Security assessment (CRA Art. 10) | **Not started** |
| Distribution scope from project settings | **Hardcoded** `"BINARY"` |
| Report without approval workflow | **Blocked** — requires APPROVED status |

---

## Phase 1: Authentication & Identity Integrity

**Goal:** Replace trust-me HTTP headers with real identity so the 4-eyes principle is enforceable.

The entire audit trail is undermined if `X-Curator-Id` and `X-Approver-Id` can be freely set by any caller.

### 1.1 API Key-Based Identity

OrtoPed already has an `api_keys` table. Extend it to carry user identity.

- Add `user_id`, `user_name`, `user_role` columns to `api_keys` table
- On every curation/approval request, resolve identity from the API key (Bearer token), not from headers
- Fall back to header-based identity only in development mode (configurable flag)
- Audit log `actor_id` comes from the authenticated key, not request headers

**Files:**
- New migration: `V10__api_key_identity.sql`
- Modify: `api/src/main/kotlin/com/ortoped/api/repository/ApiKeyRepository.kt`
- New: `api/src/main/kotlin/com/ortoped/api/auth/ApiKeyAuthPlugin.kt` (Ktor auth plugin)
- Modify: `CurationRoutes.kt` — extract identity from auth context, not headers

### 1.2 Role-Based Access Control

Define minimum roles for EU compliance:

| Role | Can Curate | Can Approve | Can View Reports | Can Manage Policies |
|------|-----------|-------------|-----------------|-------------------|
| CURATOR | Yes | No | Yes | No |
| APPROVER | No | Yes | Yes | No |
| COMPLIANCE_OFFICER | Yes | Yes | Yes | Yes |
| ADMIN | Yes | Yes | Yes | Yes |

- Enforce roles at route level (Ktor plugin)
- 4-eyes check becomes: different `user_id` AND `APPROVER`-capable role
- Existing `approver_role` field (LEGAL, COMPLIANCE, MANAGER, SECURITY) is preserved as a descriptive label

**Files:**
- New: `api/src/main/kotlin/com/ortoped/api/auth/RoleAuthorization.kt`
- Modify: `CurationRoutes.kt`, `ReportRoutes.kt` — add role checks

---

## Phase 2: Dashboard — Audit Trail & Compliance UI

**Goal:** Replace the stub Audit Trail tab with a fully functional compliance dashboard.

### 2.1 Audit Trail Viewer

The API endpoint (`GET /scans/{scanId}/curation/audit-logs`) already exists and supports filtering. Wire it into the dashboard.

- Searchable, filterable audit log table in the Compliance view
- Filters: entity type, action, actor, date range
- Timeline visualization showing the full curation lifecycle
- Each entry shows: timestamp, actor, action, entity, change summary, before/after states
- Expandable rows for full state diffs

**Files:**
- Rewrite: `dashboard/src/views/ComplianceView.vue` — add real Audit Trail tab
- New: `dashboard/src/components/compliance/AuditLogTable.vue`
- New: `dashboard/src/components/compliance/AuditTimeline.vue`
- Extend: `dashboard/src/api/client.ts` — add audit log API calls

### 2.2 EU Compliance Report Viewer

- Generate and display the EU compliance report for any approved scan
- Sections: regulatory info, workflow summary, statistics, license decisions, approval chain, full audit trail
- Export as PDF (html2pdf or server-side PDF generation)
- Export as JSON for machine consumption
- Print-friendly layout for auditors

**Files:**
- New: `dashboard/src/views/EuComplianceReportView.vue`
- New: `dashboard/src/components/compliance/ReportSection.vue`
- New: `dashboard/src/components/compliance/ApprovalChainCard.vue`
- Modify: `dashboard/src/router/index.ts` — add route `/compliance/report/:scanId`

### 2.3 Compliance Status Overview

A landing section in the Compliance view showing cross-scan compliance health:

- Number of scans awaiting curation / approval / approved
- Policy pass rate across recent scans
- Overdue curations (submitted but not reviewed within SLA)
- 4-eyes principle enforcement status (enabled/disabled, violations count)

**Files:**
- New: `dashboard/src/components/compliance/ComplianceOverview.vue`
- New API endpoint: `GET /api/v1/compliance/dashboard` in new `ComplianceRoutes.kt`

---

## Phase 3: Report Generation Hardening

**Goal:** Fix gaps in the EU compliance report and make it usable without the full approval workflow.

### 3.1 Support Report Without Approval Workflow

Currently the report **requires** `APPROVED` status, but 4-eyes is off by default. This blocks most users from generating compliance reports.

- Add a `FINALIZED` status that bypasses approval (already exists as `finalize` route)
- Allow EU report generation for `FINALIZED` sessions with a `fourEyesPrincipleApplied = false` flag
- Report clearly marks whether 4-eyes was applied or bypassed
- Add a warning banner when generating without approval: "This report was generated without independent approval"

**Files:**
- Modify: `api/src/main/kotlin/com/ortoped/api/service/ReportService.kt` — relax status check
- Modify: `EuRegulatoryInfo` model — add `approvalBypassed` field

### 3.2 Distribution Scope from Project Settings

Line 849 of `ReportService.kt` has `distributionScope = "BINARY" // TODO: Get from project settings`.

- Add `distribution_scope` column to `projects` table (enum: INTERNAL, BINARY, SOURCE, SAAS, EMBEDDED)
- Expose in project settings API and dashboard
- Read from project in report generation
- Distribution scope affects obligation aggregation (already supported by `LicenseKnowledgeGraph.getObligationsForDistribution()`)

**Files:**
- New migration: `V11__project_distribution_scope.sql`
- Modify: `Entities.kt` — add column to Projects table
- Modify: `ReportService.kt` — read from project
- Modify: dashboard project settings (if exists) or add to scan config

### 3.3 Enrich Native Export with Structured Justification

`curations.ortoped.json` currently has a flat `justification` string. The full structured data (evidence type, reference, distribution scope, hash) exists only in the database.

- Extend `OrtopedNativeCuration` model with structured justification fields
- Export includes: `evidenceType`, `evidenceReference`, `distributionScope`, `justificationHash`
- Backward-compatible: old files still parse (new fields are optional)

**Files:**
- Modify: `core/src/main/kotlin/com/ortoped/core/curation/CurationModels.kt`
- Modify: export logic in `CurationRoutes.kt` (`/export/curations-native`)

### 3.4 Justification Hash Verification

The `curation_justifications` table has a SHA-256 hash trigger for tamper detection. Expose this to the report.

- Include `justification_hash` in the EU compliance report per decision
- Add a `verifyIntegrity()` endpoint that re-hashes all justifications and flags mismatches
- Report includes an integrity check result: "All N justification hashes verified"

**Files:**
- New API endpoint: `GET /api/v1/scans/{scanId}/compliance/verify-integrity`
- Modify: `ReportService.kt` — add integrity section to EU report

---

## Phase 4: CRA Article Coverage

**Goal:** Address CRA requirements beyond SBOM and audit trail.

### 4.1 Vulnerability Disclosure Support (CRA Art. 11)

CRA requires manufacturers to report actively exploited vulnerabilities within 24 hours and provide security advisories.

- Integrate with OSV (Open Source Vulnerabilities) API to check known vulnerabilities for scanned dependencies
- Store vulnerability scan results alongside license scan results
- Add vulnerability section to EU compliance report
- Flag dependencies with known CVEs in the curation workbench
- CLI flag: `ortoped scan -p . --vuln-check`

**Files:**
- New: `core/src/main/kotlin/com/ortoped/core/vulnerability/OsvClient.kt`
- New: `core/src/main/kotlin/com/ortoped/core/vulnerability/VulnerabilityModels.kt`
- New: `core/src/main/kotlin/com/ortoped/core/vulnerability/VulnerabilityScanner.kt`
- New migration: `V12__vulnerability_results.sql`
- Modify: `ScanOrchestrator.kt` — optional vuln scan step
- Modify: EU compliance report — add vulnerability disclosure section

### 4.2 Technical Documentation Support (CRA Art. 10)

CRA requires technical documentation including: description of design, development, and production; security risk assessment; and a list of applied harmonized standards.

- Add a "Technical Documentation" section to the EU report
- Allow users to attach security assessment metadata to scans (free-form markdown or structured fields)
- Track which standards are applied (ISO 27001, IEC 62443, etc.)
- Store as project-level metadata

**Files:**
- New migration: `V13__technical_documentation.sql` (project-level metadata table)
- New: `api/src/main/kotlin/com/ortoped/api/routes/TechnicalDocRoutes.kt`
- Modify: EU compliance report — include technical documentation section

### 4.3 SBOM Completeness Validation

CRA mandates accurate SBOMs. Add validation that the generated SBOM meets minimum CRA requirements.

- Validate all dependencies have: name, version, supplier, license, unique identifier (purl)
- Flag incomplete entries in the compliance report
- Add SBOM completeness score (percentage of fields populated)
- Validate against NTIA minimum elements for SBOM

**Files:**
- New: `core/src/main/kotlin/com/ortoped/core/sbom/SbomValidator.kt`
- Modify: EU compliance report — add SBOM completeness section

---

## Phase 5: Exposed ORM Cleanup & Observability

**Goal:** Technical debt and operational readiness.

### 5.1 ORM Table Objects for Audit Tables

`audit_logs`, `curation_justifications`, `curation_approvals`, and `or_license_resolutions` use raw SQL. Add proper Exposed table objects for consistency and type safety.

**Files:**
- Modify: `api/src/main/kotlin/com/ortoped/api/model/Entities.kt` — add table objects
- Modify: `CurationRepository.kt` — replace raw SQL with Exposed DSL

### 5.2 Audit Log Retention & Export

- Add configurable retention period for audit logs (default: 10 years per EU requirements)
- Bulk export endpoint: `GET /api/v1/compliance/audit-export?format=csv`
- Signed audit export with hash chain for integrity verification

**Files:**
- New: `api/src/main/kotlin/com/ortoped/api/service/AuditExportService.kt`
- New API endpoint in `ComplianceRoutes.kt`

### 5.3 Compliance Notifications

- Webhook support for compliance events: curation submitted, approval needed, report generated
- Email notification stub (interface + configuration, not full SMTP)
- Configurable SLA timers: "approval required within 48 hours"

**Files:**
- New: `api/src/main/kotlin/com/ortoped/api/service/ComplianceNotificationService.kt`
- New migration: `V14__notification_config.sql`

---

## Priority and Sequencing

| Phase | Priority | Effort | Dependencies |
|-------|----------|--------|-------------|
| 1 — Authentication & Identity | P0 | Medium | None — blocks trustworthy audit trail |
| 2 — Dashboard Audit Trail & Report UI | P0 | Medium | None — APIs already exist |
| 3 — Report Generation Hardening | P1 | Small-Medium | None |
| 4 — CRA Article Coverage | P1 | Large | Phase 3 (report structure) |
| 5 — ORM Cleanup & Observability | P2 | Medium | Phase 1 (identity model) |

Phase 1 and Phase 2 can run in parallel. Phase 3 is independent. Phase 4 builds on Phase 3's report structure. Phase 5 is ongoing technical debt.

---

## CRA Compliance Coverage After Completion

| CRA Requirement | Current | After Plan |
|----------------|---------|------------|
| SBOM generation (Art. 10.6) | Done | Done + completeness validation |
| Vulnerability handling (Art. 11) | Not started | Phase 4.1 — OSV integration |
| Technical documentation (Art. 10) | Not started | Phase 4.2 — metadata + report section |
| Audit trail / traceability | Done (DB) | Phase 2 — exposed in dashboard |
| Independent review (4-eyes) | Done (off by default) | Phase 1 — enforced with real auth |
| Tamper-proof records | Done (hash trigger) | Phase 3.4 — integrity verification |
| Reporting | Done (JSON) | Phase 2.2 — PDF export, dashboard viewer |
| Security risk assessment | Not started | Phase 4.2 — structured metadata |
| Standards compliance | Not started | Phase 4.2 — standards tracking |
| Notification/disclosure | Not started | Phase 5.3 — webhook notifications |

---

## Success Criteria

- Curator and approver identities are cryptographically tied to API keys (Phase 1)
- Compliance officer can view full audit trail and EU report in the dashboard (Phase 2)
- EU compliance report can be generated for finalized scans without approval workflow (Phase 3)
- Vulnerability scan results appear in compliance reports (Phase 4)
- SBOM completeness is validated against NTIA minimum elements (Phase 4)
- All existing tests continue to pass (backward compatibility)
- `./gradlew build` succeeds across all modules
