# License Knowledge Graph — Completion Plan

**Date:** 2026-02-15
**Status:** Draft
**Focus:** Close remaining gaps in the Knowledge Graph to unlock its full potential as OrtoPed's P0 differentiator

---

## Current State

The core graph engine, data model, API routes, and "Why Not?" explanation system are **fully implemented**. What remains is connecting the graph to the rest of the system and exposing it to users.

| Component | Status |
|-----------|--------|
| In-memory graph engine (`LicenseKnowledgeGraph`) | Done |
| Graph data model (nodes, edges, results) | Done |
| Pre-loaded license data (`LicenseGraphLoader`) | Done |
| REST API — 16 endpoints under `/graph` | Done |
| "Why Not?" explanations (`ExplanationGenerator`) | Done (2 TODOs) |
| Unit tests | Done |
| Dashboard visualization | Not started (deferred) |
| External data feeds | Not started |
| Policy engine integration | Not started |
| Regulation / case law nodes | Not started |

---

## Phase 1: Graph-Powered Policy Engine

**Goal:** Replace flat `LicenseClassifier` with the Knowledge Graph as the primary policy evaluation source.

### 1.1 Unify LicenseClassifier with the Graph

`PolicyEvaluator` currently uses `LicenseClassifier` which reads categories from YAML. The graph already has richer category/copyleft data.

- Add a `GraphAwareLicenseClassifier` that delegates to `LicenseKnowledgeGraph` for category lookups, copyleft strength, and family membership
- Fall back to YAML-based classification for licenses not in the graph
- Wire into `PolicyEvaluator` as a drop-in replacement (same interface)
- Preserve backward compatibility — existing `policy.yml` files work unchanged

**Files:**
- New: `core/src/main/kotlin/com/ortoped/core/policy/GraphAwareLicenseClassifier.kt`
- Modify: `core/src/main/kotlin/com/ortoped/core/policy/PolicyEvaluator.kt` — accept classifier via constructor
- Tests: `GraphAwareLicenseClassifierTest.kt`

### 1.2 Add Compatibility Rules to Policy Engine

Enable policy rules that leverage graph compatibility:

```yaml
rules:
  - id: "no-gpl-in-proprietary"
    type: "compatibility"
    check: "all-compatible-with"
    target_license: "Proprietary"
    severity: "ERROR"
```

- Extend `PolicyConfig` with a `compatibility` rule type
- `PolicyEvaluator` calls `graph.checkCompatibility()` for these rules
- Return graph-enriched violations with compatibility path and reasoning

**Files:**
- Modify: `core/src/main/kotlin/com/ortoped/core/policy/PolicyConfig.kt`
- Modify: `core/src/main/kotlin/com/ortoped/core/policy/PolicyEvaluator.kt`
- Tests: extend `PolicyEvaluatorTest.kt`

---

## Phase 2: Close ExplanationGenerator TODOs

**Goal:** Wire the two remaining TODOs so explanations are fully data-driven.

### 2.1 Similar Past Decisions from Curation History

`ExplanationGenerator.kt:41` — `similarPastDecisions = emptyList()`

- Query curation database for past decisions on the same license (or same family)
- Map to `PastDecision` model (already defined in `ExplanationModels.kt`)
- In CLI standalone mode, query `LocalFileCache` curations; in API mode, query PostgreSQL
- Show in explanations: "Your team accepted Apache-2.0 for commons-lang3 on 2026-01-15"

**Files:**
- New: `core/src/main/kotlin/com/ortoped/core/policy/explanation/PastDecisionLookup.kt` (interface)
- New: `api/src/main/kotlin/com/ortoped/api/service/DatabasePastDecisionLookup.kt`
- Modify: `core/src/main/kotlin/com/ortoped/core/policy/explanation/ExplanationGenerator.kt`

### 2.2 Dependency Alternatives Database

`ExplanationGenerator.kt:571` — `findAlternatives()` has 3 hardcoded entries.

- Create a static alternatives registry (JSON/YAML resource file) mapping popular packages to alternatives with license info
- Cover top 50 commonly-flagged packages (GPL utilities, deprecated libs, etc.)
- Allow user-supplied alternatives via `.ortoped/alternatives.yml`
- Long-term: query libraries.io or deps.dev API for ecosystem alternatives

**Files:**
- New: `core/src/main/resources/alternatives.json` — static registry
- Modify: `core/src/main/kotlin/com/ortoped/core/policy/explanation/ExplanationGenerator.kt`
- Tests: extend explanation tests

---

## Phase 3: External Data Sources

**Goal:** Replace hardcoded `LicenseGraphLoader` data with authoritative external sources.

### 3.1 SPDX License List Integration

- Parse SPDX license list JSON (https://spdx.org/licenses/licenses.json) at build time or on first load
- Populate `LicenseNode` fields: `isOsiApproved`, `isFsfFree`, `isDeprecated`, `seeAlso` URLs
- Covers 500+ licenses vs the ~15 currently hardcoded

**Files:**
- New: `core/src/main/kotlin/com/ortoped/core/graph/loader/SpdxLicenseListLoader.kt`
- Resource: bundled snapshot of SPDX license list JSON
- Modify: `LicenseGraphLoader.kt` — delegate license node creation to SPDX loader

### 3.2 GNU Compatibility Data

- Encode the GNU license compatibility matrix (https://www.gnu.org/licenses/license-list.html) as compatibility edges
- Covers GPL/LGPL/AGPL family interactions authoritatively
- Mark edges with `source = "GNU"` for traceability

**Files:**
- New: `core/src/main/kotlin/com/ortoped/core/graph/loader/GnuCompatibilityLoader.kt`
- Resource: `core/src/main/resources/gnu-compatibility.json`

### 3.3 Obligation Data Enrichment

- Map SPDX license exceptions to obligation modifications
- Add distribution-scope-specific obligation triggers (e.g., LGPL dynamic linking exemption)
- Source from choosealicense.com data (MIT-licensed, machine-readable)

---

## Phase 4: Regulation and Case Law Nodes

**Goal:** Add legal context nodes referenced in the architecture doc.

### 4.1 Regulation Nodes

`NodeType.REGULATION` exists in the enum but has no implementation.

- Create `RegulationNode` data class (id, name, jurisdiction, effectiveDate, requirements)
- Load key regulations: EU Cyber Resilience Act, GDPR (software licensing implications), US Export Controls
- Link to licenses via `REQUIRES_COMPLIANCE` edges
- Enable policy rules: "flag dependencies requiring export control review"

**Files:**
- New: `core/src/main/kotlin/com/ortoped/core/graph/model/RegulationNode.kt`
- New: `core/src/main/kotlin/com/ortoped/core/graph/loader/RegulationLoader.kt`
- Modify: `GraphNodes.kt` — add RegulationNode
- Modify: `LicenseKnowledgeGraph.kt` — add regulation storage and queries

### 4.2 Case Law References

- Add notable case law nodes (Oracle v Google, SCO v IBM) as context
- Link to licenses they interpret
- Display in "Why Not?" explanations as legal precedent

**Files:**
- New: `core/src/main/kotlin/com/ortoped/core/graph/loader/CaseLawLoader.kt`
- Resource: `core/src/main/resources/case-law.json`

---

## Phase 5: Dashboard Visualization

**Goal:** Expose the graph to users visually in the dashboard.

### 5.1 Compatibility Matrix View

- Grid showing all-pairs compatibility for licenses in the current scan
- Color-coded: green (FULL), yellow (CONDITIONAL/ONE_WAY), red (INCOMPATIBLE), gray (UNKNOWN)
- Click a cell to see the compatibility reasoning and path
- API: already available via `POST /graph/compatibility/matrix`

**Files:**
- New: `dashboard/src/views/CompatibilityMatrixView.vue`
- New: `dashboard/src/components/graph/CompatibilityCell.vue`
- Modify: `dashboard/src/router/index.ts`

### 5.2 Interactive License Graph

- Force-directed graph visualization (use D3.js or vis-network)
- Nodes = licenses in the scan, edges = compatibility relationships
- Click a node to see obligations, rights, conditions
- Highlight conflict paths in red
- Filter by family, category, or search

**Files:**
- New: `dashboard/src/views/LicenseGraphView.vue`
- New: `dashboard/src/components/graph/ForceGraph.vue`
- Add dependency: `d3` or `vis-network`

### 5.3 Obligation Summary Dashboard

- Aggregated obligation view for the entire scan
- Grouped by trigger condition (distribution, modification, etc.)
- "What do I need to do?" checklist format
- API: already available via `POST /graph/obligations/aggregate`

**Files:**
- New: `dashboard/src/components/graph/ObligationSummary.vue`

---

## Priority and Sequencing

| Phase | Priority | Effort | Dependencies |
|-------|----------|--------|-------------|
| 1 — Graph-Powered Policy | P0 | Medium | None |
| 2 — ExplanationGenerator TODOs | P1 | Small | None |
| 3 — External Data Sources | P1 | Medium | None |
| 4 — Regulation / Case Law | P2 | Medium | Phase 3 (data patterns) |
| 5 — Dashboard Visualization | P2 | Large | Phase 1 (needs graph in policy flow) |

Phases 1, 2, and 3 can be worked on in parallel. Phase 4 builds on the loader patterns from Phase 3. Phase 5 is independent but most valuable after Phase 1 connects the graph to policy evaluation.

---

## Success Criteria

- `PolicyEvaluator` uses graph data for category classification (Phase 1)
- Compatibility-based policy rules work end-to-end (Phase 1)
- "Why Not?" explanations show past team decisions (Phase 2)
- Graph covers 500+ licenses from SPDX list (Phase 3)
- Compliance officer can view compatibility matrix in dashboard (Phase 5)
- All existing tests continue to pass (backward compatibility)
