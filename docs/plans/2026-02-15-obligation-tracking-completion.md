# Obligation Tracking & Fulfillment Verification — Completion Plan

**Date:** 2026-02-15
**Status:** Draft
**Focus:** Track, verify, and document fulfillment of license obligations across the dependency tree

---

## Current State

The obligation **detection** layer is fully built — the Knowledge Graph knows which obligations each license triggers, can aggregate them across dependency trees, and can filter by distribution scope. What's entirely missing is the **tracking and verification** layer — recording that obligations have been fulfilled and generating the artifacts that prove it.

| Component | Status |
|-----------|--------|
| `ObligationNode` model + 10 standard obligations | Done |
| License-to-obligation edges (12+ licenses) | Done |
| `aggregateObligations()` — cross-tree aggregation | Done |
| `getObligationsForDistribution()` — scope-aware filtering | Done |
| ExplanationGenerator — obligation surfacing in "Why Not?" | Done |
| Dashboard — read-only obligation display per dependency | Done |
| Graph API — obligation query endpoints | Done |
| NOTICE file generation (name+version only) | Done (minimal) |
| **Obligation fulfillment tracking (DB)** | **Not started** |
| **Fulfillment verification (automated checks)** | **Not started** |
| **Compliance checklist UI** | **Not started** |
| **Obligation status per scan/project** | **Not started** |
| **Rich NOTICE file (copyright text + license text)** | **Not started** |
| **Source disclosure packaging** | **Not started** |
| **SBOM obligation annotations** | **Not started** |
| **CLI obligation summary** | **Not started** |
| **Obligation summary in reports** | **Not started** |
| **Obligation dashboard component** | **Not started** (planned in KG plan) |

---

## Phase 1: Obligation Fulfillment Data Model & Persistence

**Goal:** Create the database and domain model for tracking per-obligation fulfillment status.

### 1.1 Obligation Tracking Tables

```sql
-- Per-scan obligation instances (what needs to be done)
CREATE TABLE scan_obligations (
    id              UUID PRIMARY KEY,
    scan_id         UUID NOT NULL REFERENCES scans(id),
    obligation_id   VARCHAR(100) NOT NULL,     -- "attribution", "source-disclosure"
    obligation_name VARCHAR(255) NOT NULL,
    trigger         VARCHAR(50) NOT NULL,       -- TriggerCondition
    effort_level    VARCHAR(20) NOT NULL,       -- EffortLevel
    scope           VARCHAR(50) NOT NULL,       -- ObligationScope
    distribution    VARCHAR(50) NOT NULL,       -- distribution context
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, IN_PROGRESS, FULFILLED, VERIFIED, WAIVED
    source_licenses TEXT NOT NULL,              -- JSON array of license IDs that trigger this
    source_dependencies TEXT NOT NULL,          -- JSON array of dependency IDs
    assigned_to     VARCHAR(255),
    due_date        TIMESTAMP WITH TIME ZONE,
    fulfilled_at    TIMESTAMP WITH TIME ZONE,
    fulfilled_by    VARCHAR(255),
    evidence_type   VARCHAR(50),               -- FILE, URL, COMMENT, ARTIFACT
    evidence_ref    TEXT,                       -- path/URL to proof
    notes           TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Audit trail for obligation status changes
-- (Uses existing audit_logs table with entity_type = 'OBLIGATION')
```

Obligation statuses:
- `PENDING` — detected, not yet addressed
- `IN_PROGRESS` — someone is working on it
- `FULFILLED` — self-reported as done, awaiting verification
- `VERIFIED` — independently confirmed (automated or by approver)
- `WAIVED` — explicitly skipped with justification (e.g., internal-only distribution)
- `NOT_APPLICABLE` — obligation doesn't apply in this context

**Files:**
- New migration: `api/src/main/resources/db/migration/V10__obligation_tracking.sql`
- New: `api/src/main/kotlin/com/ortoped/api/model/ObligationEntities.kt` (Exposed table objects)
- New: `core/src/main/kotlin/com/ortoped/core/model/ObligationTrackingModels.kt`

### 1.2 Obligation Materialization Service

When a scan completes (or curation is finalized), materialize the aggregated obligations into `scan_obligations` rows.

- Call `graph.aggregateObligations()` with all concluded licenses from the scan
- Filter by project's `distribution_scope`
- Create one `scan_obligations` row per unique obligation (deduplicated across licenses)
- Track which licenses and dependencies contribute to each obligation
- Re-materialize on curation changes (new license conclusions may add/remove obligations)

**Files:**
- New: `api/src/main/kotlin/com/ortoped/api/service/ObligationService.kt`
- New: `api/src/main/kotlin/com/ortoped/api/repository/ObligationRepository.kt`
- Modify: `CurationService.kt` — trigger obligation materialization on finalize/approve

---

## Phase 2: Obligation API & Dashboard

**Goal:** CRUD for obligation fulfillment and a "What do I need to do?" checklist UI.

### 2.1 Obligation API Endpoints

```
GET  /api/v1/scans/{scanId}/obligations              -- list all obligations for scan
GET  /api/v1/scans/{scanId}/obligations/{id}          -- single obligation detail
PUT  /api/v1/scans/{scanId}/obligations/{id}/status    -- update status + evidence
POST /api/v1/scans/{scanId}/obligations/materialize    -- re-generate from graph
GET  /api/v1/scans/{scanId}/obligations/summary        -- aggregated stats
POST /api/v1/scans/{scanId}/obligations/{id}/verify    -- mark as verified (approver)
POST /api/v1/scans/{scanId}/obligations/{id}/waive     -- waive with justification
```

Status update payload:
```json
{
  "status": "FULFILLED",
  "evidenceType": "FILE",
  "evidenceRef": "NOTICE.md",
  "notes": "Added copyright notices for all MIT dependencies"
}
```

**Files:**
- New: `api/src/main/kotlin/com/ortoped/api/routes/ObligationRoutes.kt`
- Extend: `ObligationService.kt` — status transitions, validation, audit logging

### 2.2 Obligation Checklist Dashboard

The core compliance experience — a checklist showing all obligations for a scan with fulfillment status.

- Grouped by effort level (high-effort first) or by obligation type
- Each item shows: obligation name, description, effort badge, trigger condition, affected dependencies count, status chip
- Expand to see: list of dependencies + licenses that trigger this obligation, examples, evidence link
- Actions: mark fulfilled (with evidence upload/URL), assign to team member, waive with justification
- Progress bar: "7/10 obligations fulfilled"
- Filter: by status, effort level, obligation type

**Files:**
- New: `dashboard/src/views/ObligationChecklistView.vue`
- New: `dashboard/src/components/obligations/ObligationCard.vue`
- New: `dashboard/src/components/obligations/ObligationProgress.vue`
- New: `dashboard/src/components/obligations/EvidenceUpload.vue`
- Modify: `dashboard/src/router/index.ts` — add route `/scans/:scanId/obligations`
- Modify: `dashboard/src/api/client.ts` — add obligation API calls
- Link from: Scan detail view, Compliance view

### 2.3 Obligation Summary in Scan Detail

Add an "Obligations" tab or card to the scan detail view showing a compact summary:

- Total obligations, fulfilled count, pending count
- Highest-effort obligation highlighted
- Link to full checklist
- Warning badge on scan list if obligations are unfulfilled

**Files:**
- Modify: `dashboard/src/views/ScanDetailView.vue` (or `ScansView.vue`)
- New: `dashboard/src/components/obligations/ObligationSummaryCard.vue`

---

## Phase 3: Automated Fulfillment Verification

**Goal:** Where possible, automatically verify that obligations have been met.

### 3.1 Attribution Verification

Check that attribution/copyright notices exist in the project.

- Scan for NOTICE, NOTICE.md, NOTICE.txt, THIRD-PARTY-NOTICES files in project root
- Verify that each dependency requiring attribution is mentioned
- Check README for a "Third-Party Licenses" section
- Auto-mark `attribution` obligation as `VERIFIED` when all attributed dependencies are found in the NOTICE file

**Files:**
- New: `core/src/main/kotlin/com/ortoped/core/obligation/AttributionVerifier.kt`
- Interface: `core/src/main/kotlin/com/ortoped/core/obligation/ObligationVerifier.kt`

### 3.2 License Inclusion Verification

Check that license texts are bundled with the distribution.

- Scan for LICENSE, LICENSE.md, licenses/ directory
- Verify that each required license text is present (match by SPDX ID → known license text)
- For `include-license` obligation: check that the license file exists
- For `include-copyright`: check that copyright statements are present

**Files:**
- New: `core/src/main/kotlin/com/ortoped/core/obligation/LicenseInclusionVerifier.kt`

### 3.3 Source Disclosure Check

For copyleft obligations, verify that source code is available.

- Check for a `SOURCE-CODE-OFFER` or similar file in project root
- Verify that a source archive URL is reachable (HTTP HEAD check)
- For AGPL `network-disclosure`: check that a "source code" link is referenced in configuration
- This is advisory — full verification requires human review

**Files:**
- New: `core/src/main/kotlin/com/ortoped/core/obligation/SourceDisclosureVerifier.kt`

### 3.4 Verification Orchestrator

Run all verifiers and update obligation statuses.

- `VerificationOrchestrator` takes a scan ID, project path, and list of obligations
- Runs applicable verifiers based on obligation type
- Updates `scan_obligations` status to `VERIFIED` or leaves as `FULFILLED` (unverified)
- CLI: `ortoped obligations verify -p .`
- API: `POST /api/v1/scans/{scanId}/obligations/verify-all`

**Files:**
- New: `core/src/main/kotlin/com/ortoped/core/obligation/VerificationOrchestrator.kt`
- Extend: `ObligationRoutes.kt` — add verify-all endpoint
- Extend: CLI — add `obligations verify` subcommand

---

## Phase 4: Obligation Artifact Generation

**Goal:** Generate the artifacts that fulfill obligations, not just track them.

### 4.1 Rich NOTICE File Generation

The current NOTICE file is name+version only. Upgrade to Apache-style with actual content.

- Include copyright text per dependency (from SBOM/scanner data or SPDX `copyrightText`)
- Include license name and SPDX identifier
- Include full license text for each unique license (appended or in separate `licenses/` directory)
- Configurable format: minimal (current), standard (copyright + license name), full (+ license text)
- CLI: `ortoped obligations generate-notice -p . --format full`

**Files:**
- Modify: `api/src/main/kotlin/com/ortoped/api/service/CurationService.kt` — `exportNoticeFile()`
- New: `core/src/main/kotlin/com/ortoped/core/obligation/NoticeFileGenerator.kt`
- Resource: `core/src/main/resources/license-texts/` — bundled SPDX license texts (or fetch from SPDX)

### 4.2 Source Code Offer Letter

For GPL/LGPL/AGPL obligations, generate a written offer for source code.

- Template-based generation with project name, contact info, offer duration (3 years per GPL)
- Outputs `SOURCE-CODE-OFFER.txt` for inclusion in binary distributions
- Lists all copyleft-licensed dependencies and their versions

**Files:**
- New: `core/src/main/kotlin/com/ortoped/core/obligation/SourceOfferGenerator.kt`
- Resource: `core/src/main/resources/templates/source-offer.txt`

### 4.3 Modification Documentation

For `state-changes` obligation, generate a changes manifest.

- Scan git log for modifications to vendored/forked dependencies
- Generate `CHANGES.md` documenting what was modified and when
- Template with per-dependency sections

**Files:**
- New: `core/src/main/kotlin/com/ortoped/core/obligation/ChangesDocGenerator.kt`

---

## Phase 5: SBOM & Report Integration

**Goal:** Embed obligation data into SBOMs, CLI output, and compliance reports.

### 5.1 SBOM Obligation Annotations

Add obligation metadata to CycloneDX and SPDX output.

**CycloneDX:**
- Add `ortoped:obligations` property per component listing triggered obligation IDs
- Add `ortoped:obligation:status` with fulfillment status
- Use CycloneDX `externalReferences` to link to NOTICE file, source offer

**SPDX:**
- Populate `copyrightText` from actual scanner data instead of `NOASSERTION`
- Add `comment` field with obligation summary per package
- Use `relationship` types to express obligation linkage

**Files:**
- Modify: `core/src/main/kotlin/com/ortoped/core/sbom/CycloneDxGenerator.kt`
- Modify: `core/src/main/kotlin/com/ortoped/core/sbom/SpdxGenerator.kt`

### 5.2 CLI Obligation Summary

Add obligation summary to `ortoped scan` console output and a dedicated subcommand.

```
=== Obligation Summary ===
  Total obligations: 5
  Attribution required:     12 dependencies  [FULFILLED]
  Include license text:      8 dependencies  [FULFILLED]
  Source code disclosure:     2 dependencies  [PENDING]
  State changes documented:  1 dependency    [PENDING]
  Network disclosure:        1 dependency    [NOT APPLICABLE]

  Highest effort: HIGH (source code disclosure)
  Run 'ortoped obligations verify -p .' to check fulfillment
```

New subcommand group:
```bash
ortoped obligations list -p .                    # Show obligations for last scan
ortoped obligations verify -p .                  # Run automated verification
ortoped obligations generate-notice -p .         # Generate NOTICE file
ortoped obligations generate-source-offer -p .   # Generate source code offer
ortoped obligations status -p .                  # Show fulfillment summary
```

**Files:**
- New: `cli/src/main/kotlin/com/ortoped/cli/commands/ObligationCommands.kt`
- Modify: `cli/src/main/kotlin/com/ortoped/cli/Main.kt` — register subcommands
- Modify: `core/src/main/kotlin/com/ortoped/core/report/ReportGenerator.kt` — add `printObligationSummary()`

### 5.3 EU Compliance Report — Obligation Section

Add an obligations section to the EU compliance report.

- List all obligations with fulfillment status
- Per-obligation: evidence reference, who fulfilled, when verified
- Obligation completion percentage
- Unfulfilled obligations flagged as compliance risks
- Link to generated artifacts (NOTICE file, source offer)

**Files:**
- Modify: `api/src/main/kotlin/com/ortoped/api/service/ReportService.kt` — add obligation section
- New models in `EUComplianceModels.kt`: `EuObligationReport`, `EuObligationEntry`

---

## Phase 6: Obligation Intelligence

**Goal:** Leverage the Knowledge Graph for smarter obligation management.

### 6.1 Obligation Deduplication & Dominance

When multiple licenses trigger the same obligation at different scopes, show only the most restrictive.

- MIT `attribution` (TRIVIAL) + GPL-3.0 `attribution` (LOW, broader scope) → show GPL-3.0 version
- Already partially handled by `aggregateObligations()` — surface this in the UI with "dominated by" labels
- Reduce noise: 200 dependencies might trigger 200 attribution obligations, but it's really one task

**Files:**
- Modify: `ObligationService.kt` — deduplication logic during materialization

### 6.2 Obligation Impact Preview in Curation

When a curator is about to accept a license, show what obligations it adds.

- "Accepting GPL-3.0 for this dependency will add: source-disclosure (HIGH effort), same-license (MEDIUM effort)"
- "Switching to MIT would only require: attribution (TRIVIAL effort)"
- Diff view: obligations before vs. after the curation decision

**Files:**
- New API endpoint: `POST /api/v1/scans/{scanId}/obligations/preview` — takes proposed license, returns obligation diff
- New: `dashboard/src/components/obligations/ObligationImpactPreview.vue`
- Wire into curation workbench accept/modify flow

### 6.3 Obligation-Aware Alternative Suggestions

When suggesting alternative dependencies (ExplanationGenerator), factor in obligation burden.

- Rank alternatives not just by license compatibility but by obligation effort reduction
- "Replacing log4j (LGPL-2.1, source-disclosure HIGH) with SLF4J (MIT, attribution TRIVIAL) eliminates 2 obligations"
- Feed into the dependency alternatives database (planned in KG completion plan Phase 2.2)

**Files:**
- Modify: `core/src/main/kotlin/com/ortoped/core/policy/explanation/ExplanationGenerator.kt` — include obligation comparison in alternatives

---

## Priority and Sequencing

| Phase | Priority | Effort | Dependencies |
|-------|----------|--------|-------------|
| 1 — Data Model & Persistence | P0 | Medium | None — foundational |
| 2 — API & Dashboard Checklist | P0 | Medium | Phase 1 |
| 3 — Automated Verification | P1 | Medium | Phase 1 |
| 4 — Artifact Generation | P1 | Medium | Phase 1 |
| 5 — SBOM & Report Integration | P1 | Small-Medium | Phase 1 |
| 6 — Obligation Intelligence | P2 | Medium | Phase 1 + KG completion |

Phase 1 is the foundation — everything depends on it. Phase 2 is the user-facing value. Phases 3, 4, and 5 can be worked in parallel after Phase 1. Phase 6 is intelligence/polish.

---

## Cross-Plan Dependencies

| This Plan | Depends On | From Plan |
|-----------|-----------|-----------|
| Phase 1 (distribution scope) | Phase 3.2 (project distribution scope) | EU Compliance Plan |
| Phase 3 (verification auth) | Phase 1 (real authentication) | EU Compliance Plan |
| Phase 6.3 (alternatives) | Phase 2.2 (alternatives database) | Knowledge Graph Plan |
| Phase 6.1 (graph queries) | Phase 1 (graph-powered policy) | Knowledge Graph Plan |

---

## Success Criteria

- Every scan produces a materialized obligation checklist in the database (Phase 1)
- Compliance officer can track obligation fulfillment per scan in the dashboard (Phase 2)
- `ortoped obligations verify -p .` auto-checks attribution and license inclusion (Phase 3)
- `ortoped obligations generate-notice -p .` produces an Apache-style NOTICE file with copyright text (Phase 4)
- CycloneDX SBOM includes `ortoped:obligations` and `ortoped:obligation:status` per component (Phase 5)
- CLI scan output shows obligation summary with fulfillment counts (Phase 5)
- EU compliance report includes obligation section with evidence references (Phase 5)
- Curation workbench shows obligation impact preview before accepting a license (Phase 6)
- All existing tests continue to pass
