# OrtoPed Redesign: Simplicity, Curation & Speed

**Date:** 2026-02-08
**Status:** Approved brainstorm design
**Focus:** Simple to use, curation workflow, raw speed

---

## Core Principles

1. **Unified engine** — CLI and dashboard are thin clients to the same core/API
2. **CI/CD-first** — Primary flow is pipeline automation with dashboard as curation workbench
3. **Convention over configuration** — `.ortoped/` directory with auto-detection
4. **Raw speed** — Re-scans with curations target <5 seconds
5. **Focus over features** — Defer templates and source scanning, polish core flows
6. **Scan detects, Curation resolves** — AI runs during curation (on-demand), not during scan. Scans are fast and deterministic by default.

---

## Primary Workflow

```
1. CI/CD: ortoped scan -p .
   → Detect deps (ORT analyzer) → flag unresolved licenses
   → Apply .ortoped/curations.yml if present
   → Evaluate policy → exit code 0 or 1
   → NO AI calls — fast, deterministic, no API key needed

2. If violations/unresolved → import results to dashboard
   → Trigger "Resolve with AI" (bulk or per-dependency)
   → Review AI suggestions → accept/reject/modify
   → Export curations.yml → commit to repo

3. CI/CD: ortoped scan -p .
   → Detects .ortoped/curations.yml → applies curations
   → All deps covered → policy eval → PASS
   → Generates SBOM + compliance report
```

### AI Resolution Strategy: Scan Detects, Curation Resolves

**Key architectural decision:** AI does NOT run during scanning by default. Scanning is pure detection (deterministic). AI runs on-demand during curation in the dashboard.

**Why:**

| Concern | AI during scan (old) | AI during curation (new) |
|---------|---------------------|--------------------------|
| Scan speed | Slow (3-30s AI latency) | Fast (ORT only) |
| Cost | Every scan pays for API | Only pay when curating |
| API key | Always required | Only for curation |
| Determinism | Non-deterministic | Deterministic |
| AI context | Limited (just package info) | Rich (policy failures, similar curations, compliance needs) |

**Opt-in auto-resolution for CI/CD:**

For teams that want AI in the pipeline without human review:

```bash
# Default: no AI, fast, deterministic
ortoped scan -p .

# Opt-in: AI resolves unresolved, auto-accepts high confidence
ortoped scan -p . --auto-resolve --confidence-threshold 0.9
```

`--auto-resolve` calls AI for unresolved deps. If confidence >= threshold, auto-writes to `.ortoped/curations.yml`. Below threshold, flags for manual curation.

**Dashboard AI flow:**

```
Curation Workbench → user sees 15 unresolved deps
  → "Resolve All with AI" button (bulk)
  → or per-dependency "Get AI Suggestion" button
  → AI returns: SPDX ID, confidence, reasoning, alternatives
  → User reviews, accepts/rejects/modifies
  → Cached in PostgreSQL for future use
```

---

## Architecture

### Unified Engine

```
ortoped = unified API server (engine)
  ├── CLI = thin client (CI/CD, local dev)
  │   ├── Standalone mode (no server, local cache)
  │   └── Connected mode (--server, results in dashboard)
  ├── Dashboard = curation workbench (browser UI)
  └── Core = shared library (scan, AI, policy, SBOM)
```

### Convention-Based Config

```
project/
└── .ortoped/
    ├── curations.yml          # ORT-compatible curated decisions
    ├── curations.ortoped.json # Full OrtoPed format (optional, richer)
    ├── policy.yml             # License policy rules
    ├── config.yml             # ORT analyzer config overrides
    └── cache/                 # Local scan cache (CI/CD persistable)
```

Auto-detection with explicit overrides: `--curations`, `--policy`, `--no-curations`.

---

## Speed Strategy

### Three Tiers

| Tier | Scenario | Target | How |
|------|----------|--------|-----|
| 1 | Re-scan with curations | <5 seconds | Skip ORT (lockfile hash match), curations cover all, policy eval only |
| 2 | Incremental scan | <30 seconds | ORT cache hits, flag new unresolved deps, no AI calls |
| 3 | First scan | 30s-2 min (ORT only) | Full ORT analyzer, no AI — just detection + policy eval |

Note: All tiers are faster than before because AI is removed from the scan path. AI calls happen during curation in the dashboard (or opt-in via `--auto-resolve`).

### Lockfile Fingerprinting

Hash `package-lock.json`, `pom.xml`, `build.gradle.kts`, etc. If hash matches previous scan, skip ORT analyzer entirely and load cached dependency tree.

### No AI in Scan Path

Default scan has zero API calls. This is the single biggest speed improvement — removes 3-30 seconds of network latency from every scan.

### Batch AI Resolution (Curation-Time)

When AI is triggered in the dashboard (or via `--auto-resolve`), send multiple unresolved licenses in a single Claude API call (up to 10 per request). Reduces curation time from N round-trips to ceil(N/10).

### Curations as Ground Truth

When `.ortoped/curations.yml` covers all dependencies, zero AI calls needed. Pure local policy evaluation.

### Local File Cache

`.ortoped/cache/` stores scan result snapshots. CI/CD can persist this directory between pipeline runs for Tier 1 speed.

---

## CLI Design

### Commands

```bash
# Core commands (80% use case)
ortoped scan -p <path|url>              # Scan + policy eval (no AI, fast)
ortoped scan -p . --sbom cdx-json       # Scan + SBOM in one step
ortoped scan -p . --auto-resolve        # Scan + AI auto-resolve (opt-in)
ortoped curate import -i report.json    # Import results to dashboard
ortoped curate export -s <scanId>       # Export curations from dashboard

# Setup
ortoped init                            # Create .ortoped/ with defaults

# Supporting
ortoped policy -i report.json           # Standalone policy evaluation
ortoped sbom -i report.json             # Standalone SBOM generation
ortoped version
```

### AI-Related Flags

| Flag | Effect |
|------|--------|
| (default) | No AI calls. Fast, deterministic scan. |
| `--auto-resolve` | Call AI for unresolved deps, auto-accept above threshold |
| `--confidence-threshold 0.9` | Minimum confidence for auto-accept (default: 0.9) |
| `--no-curations` | Ignore `.ortoped/curations.yml` even if present |

### Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Pass — no policy violations |
| 1 | Fail — policy violations found |
| 2 | Error — scan failed |

### Auto-Detection

- `.ortoped/curations.yml` exists → applied automatically
- `.ortoped/policy.yml` exists → evaluated automatically
- `.ortoped/config.yml` exists → ORT config applied automatically
- Override with `--curations path`, `--policy path`, `--no-curations`

---

## Curation File Formats

### ORT-Compatible (`curations.yml`)

```yaml
# .ortoped/curations.yml — commits to repo, used by CI/CD
- id: "Maven:org.apache.commons:commons-lang3:3.14.0"
  curations:
    comment: "AI-resolved with HIGH confidence"
    concluded_license: "Apache-2.0"
- id: "npm:lodash:4.17.21"
  curations:
    concluded_license: "MIT"
```

Minimal, portable, works with vanilla ORT.

### OrtoPed-Native (`curations.ortoped.json`)

```json
{
  "version": "1.0",
  "scanId": "abc-123",
  "exportedAt": "2026-02-08T10:00:00Z",
  "curations": [
    {
      "packageId": "Maven:org.apache.commons:commons-lang3:3.14.0",
      "concludedLicense": "Apache-2.0",
      "aiConfidence": 0.95,
      "aiReasoning": "Apache header found in source files",
      "curatedBy": "john@company.com",
      "curatedAt": "2026-02-07T15:30:00Z",
      "status": "ACCEPTED",
      "justification": "Standard Apache library",
      "approvedBy": "jane@company.com"
    }
  ]
}
```

Full audit trail, EU compliance metadata, AI provenance.

---

## Dashboard Design

### Three-Screen Architecture

#### Screen 1: Scan Overview (landing page)
- List of scans (imported from CI/CD or triggered from dashboard)
- Status badges: needs curation / in progress / curated / exported
- Quick stats: total deps, unresolved count, policy violations
- Import: drag-drop report.json or paste scan ID
- Click scan → enters curation workbench

#### Screen 2: Curation Workbench (core experience)
- **Left panel:** dependency list with filters (status, confidence, severity)
- **Right panel:** selected dependency detail
  - "Get AI Suggestion" button (on-demand per dependency)
  - AI suggestion with confidence score + reasoning (when resolved)
  - SPDX validation status
  - Policy impact ("accepting this resolves 3 violations")
  - Accept / Reject / Modify actions
- **Top bar:** "Resolve All with AI" bulk button, progress stats (42/100 curated)
- **Bottom bar:** export curations.yml / curations.ortoped.json

**AI is on-demand:** User explicitly triggers AI resolution, either per-dependency or bulk. This gives the user control over cost and lets them skip AI for obvious cases.

#### Screen 3: Policy & Compliance
- Policy rule editor (YAML with preview)
- Compliance status per scan
- EU audit trail (justifications, approvals, two-role workflow)
- Advanced features live here, not in curation flow

### What Gets Cut From Current Dashboard
- Dedicated "Projects" CRUD → scans are the entry point
- Template management view → deferred
- License knowledge graph view → reference link only
- Separate SBOM generation view → export button on scan overview

---

## Codebase Changes

### Keep As-Is
- `core/ai/LicenseResolver` — AI resolution (used by curation, not scan)
- `core/ai/CachingLicenseResolver` — Three-layer caching
- `core/scanner/SimpleScannerWrapper` — ORT analyzer
- `core/sbom/CycloneDxGenerator`, `SpdxGenerator` — SBOM output
- `core/policy/PolicyEvaluator` — Policy engine
- `core/model/*` — Domain models
- `api/db/migration/V1-V7` — Database schema
- `api/routes/` — Most API endpoints

### Modify
- **`ScanOrchestrator`** — Remove AI from default scan path. Add lockfile hashing, curations-aware fast path. AI only called when `--auto-resolve` flag is set.
- **`LicenseResolver`** — Add batch resolution (multiple licenses per Claude call). Now called from curation service, not scan orchestrator.
- **CLI commands** — Add `init`, `curate`, `--sbom` on scan, `--auto-resolve`, auto-detection. Remove `--enable-ai` and `--parallel-ai` flags (replaced by `--auto-resolve`).
- **`ScanRoutes`** — Import endpoint (accept report.json upload)
- **`CurationRoutes`** — Add AI resolution endpoint (trigger AI for specific deps or bulk). Simplify: remove template routes from active surface.

### Add New
- `core/cache/LockfileHasher` — Hash lockfiles for fast-path detection
- `core/cache/LocalFileCache` — File-based cache in `.ortoped/cache/`
- `core/curations/CurationLoader` — Parse ORT + OrtoPed formats
- `core/curations/CurationExporter` — Export both formats
- `api/routes/AiResolutionRoutes` — On-demand AI resolution endpoints for dashboard
- CLI `init` command
- CLI `curate` command group

### Defer (keep in code, deprioritize)
- `CurationTemplateRoutes` — Not exposed in dashboard
- `SourceCodeScanner` — Disabled by default
- `LicenseKnowledgeGraph` — Reference API only

---

## Implementation Phases

### Phase A: Fast Engine (core + CLI)
*Delivers: CI/CD works end-to-end, scans are fast and deterministic, no API key needed*

1. Remove AI from default scan path in `ScanOrchestrator`
2. `ortoped init` — scaffold `.ortoped/` with defaults
3. Auto-detection of `.ortoped/curations.yml` and `.ortoped/policy.yml`
4. `CurationLoader` — parse both ORT and OrtoPed formats
5. `LockfileHasher` — hash-based scan caching for fast path
6. `--sbom` flag on scan command (merge two-step into one)
7. `--auto-resolve` flag with `--confidence-threshold` for opt-in CI/CD AI
8. Exit codes: 0/1/2
9. `LocalFileCache` for `.ortoped/cache/`

### Phase B: Curation Workbench (dashboard)
*Delivers: Compliance officer can trigger AI on-demand, curate, and export in one sitting*

1. Strip dashboard to three screens (overview, workbench, compliance)
2. Scan Overview — list, import report.json, status badges
3. Curation Workbench with on-demand AI:
   - "Resolve All with AI" bulk button
   - Per-dependency "Get AI Suggestion" button
   - Batch AI resolution (multiple licenses per Claude call)
   - Accept/reject/modify with bulk actions
4. Export flow — one-click curations.yml + curations.ortoped.json download
5. Incremental diff view — highlight new/changed deps
6. SPDX validation indicators in workbench
7. Policy impact preview ("accepting this resolves 3 violations")

### Phase C: Enterprise Polish
*Delivers: EU compliance, team workflow, production hardening*

1. EU audit trail in compliance screen
2. Two-role approval workflow in dashboard
3. `ortoped curate import/export` CLI commands
4. Connected mode (`--server`) for team workflows
5. Batch AI optimization tuning
6. Integration tests for full CI/CD → dashboard → CI/CD loop
