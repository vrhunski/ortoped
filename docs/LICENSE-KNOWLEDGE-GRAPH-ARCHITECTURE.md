# License Knowledge Graph Architecture

## Executive Summary

The License Knowledge Graph transforms OrtoPed from a **rule-based license checker** into an **intelligent license reasoning engine**. Instead of flat "allow/deny" rules, it models licenses as interconnected entities with semantic relationships, enabling complex queries and intelligent compliance guidance.

### Key Differentiators vs Competitors (Snyk, Black Duck, FOSSA)

| Capability | Traditional SCA | OrtoPed Knowledge Graph |
|------------|-----------------|-------------------------|
| Compatibility Check | Static rule tables | Graph traversal with path finding |
| Conflict Explanation | "Violation detected" | "Why" + resolution options |
| Obligation Tracking | Per-license list | Aggregated across dependency tree |
| Use Case Awareness | None | Context-aware (SaaS, embedded, etc.) |
| Legal References | None | Linked to sources and case law |

---

## 1. Core Concepts

### 1.1 What is a License Knowledge Graph?

A knowledge graph represents licenses and their relationships as a network of interconnected nodes and edges:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         LICENSE KNOWLEDGE GRAPH                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   [MIT] ──compatible──► [Apache-2.0] ──compatible──► [BSD-3]                │
│     │                        │                                               │
│     │                   ╱conflict╲                                           │
│     ▼                  ▼          ▼                                          │
│   [GPL-3.0] ◄──copyleft──► [LGPL-3.0]                                       │
│     │                                                                        │
│     ├──requires──► [Source Disclosure]                                      │
│     ├──grants──► [Commercial Use]                                           │
│     └──has_condition──► [Same License]                                      │
│                                                                              │
│   Nodes: Licenses, Obligations, Rights, Conditions, Use Cases               │
│   Edges: compatible, conflicts, requires, grants, triggers                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Key Capabilities

1. **Compatibility Queries**: "Can I combine GPL-3.0 with Apache-2.0 in a commercial product?"
2. **Path Analysis**: Find the "license pollution" path through your dependency tree
3. **Obligation Aggregation**: "What are ALL obligations from my 200 dependencies?"
4. **Conflict Detection**: Identify incompatible license combinations automatically
5. **Resolution Suggestions**: Provide actionable fixes for compliance issues
6. **Use Case Awareness**: Different rules for SaaS vs embedded vs internal tools

---

## 2. Graph Data Model

### 2.1 Node Types

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         GRAPH NODE TYPES                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐      │
│  │   LICENSE   │   │ OBLIGATION  │   │   RIGHT     │   │ CONDITION   │      │
│  │             │   │             │   │             │   │             │      │
│  │ GPL-3.0     │   │ Disclose    │   │ Commercial  │   │ Same        │      │
│  │ MIT         │   │ Source      │   │ Use         │   │ License     │      │
│  │ Apache-2.0  │   │ Attribution │   │ Modify      │   │ Notice      │      │
│  │ BSD-3       │   │ State       │   │ Distribute  │   │ Copyleft    │      │
│  │ ...         │   │ Changes     │   │ Patent Use  │   │ ...         │      │
│  └─────────────┘   └─────────────┘   └─────────────┘   └─────────────┘      │
│                                                                              │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐      │
│  │ LIMITATION  │   │  USE CASE   │   │ REGULATION  │   │ CASE LAW    │      │
│  │             │   │             │   │             │   │             │      │
│  │ No Warranty │   │ SaaS        │   │ GDPR        │   │ Oracle v    │      │
│  │ No Liability│   │ Embedded    │   │ Export Ctrl │   │ Google      │      │
│  │ Trademark   │   │ Library     │   │ HIPAA       │   │ SCO v IBM   │      │
│  │ Patent      │   │ CLI Tool    │   │ FedRAMP     │   │ ...         │      │
│  └─────────────┘   └─────────────┘   └─────────────┘   └─────────────┘      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### License Node

The primary node type representing a software license.

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Unique identifier (SPDX ID uppercase) |
| `spdxId` | String | Canonical SPDX identifier |
| `name` | String | Human-readable name |
| `category` | Enum | PERMISSIVE, WEAK_COPYLEFT, STRONG_COPYLEFT, etc. |
| `copyleftStrength` | Enum | NONE, FILE, LIBRARY, STRONG, NETWORK |
| `family` | String? | License family (GPL, BSD, Apache, etc.) |
| `version` | String? | Version number if applicable |
| `isOsiApproved` | Boolean | OSI approval status |
| `isFsfFree` | Boolean | FSF free software approval |
| `isDeprecated` | Boolean | SPDX deprecation status |

#### License Categories

```kotlin
enum class LicenseCategory {
    PUBLIC_DOMAIN,      // CC0, Unlicense, WTFPL
    PERMISSIVE,         // MIT, BSD, Apache
    WEAK_COPYLEFT,      // LGPL, MPL, EPL
    STRONG_COPYLEFT,    // GPL
    NETWORK_COPYLEFT,   // AGPL
    PROPRIETARY,        // Commercial licenses
    SOURCE_AVAILABLE,   // BSL, Commons Clause
    UNKNOWN
}
```

#### Copyleft Strength Levels

```kotlin
enum class CopyleftStrength {
    NONE,               // Permissive licenses - no copyleft
    FILE,               // MPL - copyleft applies to modified files only
    LIBRARY,            // LGPL - copyleft if statically linked
    STRONG,             // GPL - derivative works must be same license
    NETWORK             // AGPL - network distribution triggers copyleft
}
```

#### Obligation Node

Represents a requirement imposed by a license.

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Unique identifier |
| `name` | String | Human-readable name |
| `description` | String | What must be done |
| `triggerCondition` | Enum | When this obligation applies |
| `effort` | Enum | Compliance effort level |
| `examples` | List<String> | Example compliance actions |

#### Standard Obligations

| Obligation ID | Name | Trigger | Effort |
|---------------|------|---------|--------|
| `attribution` | Attribution | On Distribution | Low |
| `source-disclosure` | Source Code Disclosure | On Distribution | High |
| `state-changes` | State Changes | On Modification | Medium |
| `same-license` | Same License | On Derivative | Very High |
| `network-disclosure` | Network Source Disclosure | On Network Use | Very High |
| `patent-grant` | Patent Grant | Always | Trivial |
| `notice-file` | NOTICE File | On Distribution | Low |

#### Trigger Conditions

```kotlin
enum class TriggerCondition {
    ALWAYS,             // Always applies (e.g., attribution)
    ON_DISTRIBUTION,    // When distributing binaries
    ON_MODIFICATION,    // When modifying source code
    ON_DERIVATIVE,      // When creating derivative work
    ON_NETWORK_USE,     // When providing as network service (AGPL)
    ON_PATENT_CLAIM,    // When making patent claims
    CONDITIONAL         // Complex conditions (see properties)
}
```

#### Effort Levels

```kotlin
enum class EffortLevel {
    TRIVIAL,            // Include license file
    LOW,                // Add copyright notice
    MEDIUM,             // Document modifications
    HIGH,               // Provide source code
    VERY_HIGH           // Full source disclosure + build instructions
}
```

#### Use Case Node

Represents how software is being used/distributed.

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Unique identifier |
| `name` | String | Human-readable name |
| `distributionType` | Enum | How software is distributed |
| `linkingType` | Enum | How dependencies are linked |

#### Distribution Types

```kotlin
enum class DistributionType {
    NONE,               // Internal use only
    BINARY,             // Distribute compiled binaries
    SOURCE,             // Distribute source code
    NETWORK,            // SaaS/network service
    EMBEDDED            // Embedded in hardware
}
```

#### Linking Types

```kotlin
enum class LinkingType {
    STATIC,             // Compiled into binary
    DYNAMIC,            // Dynamically linked at runtime
    PROCESS_BOUNDARY,   // Separate process communication
    NETWORK_BOUNDARY    // Network API communication
}
```

### 2.2 Edge Types (Relationships)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         GRAPH EDGE TYPES                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  License ←→ License:                                                        │
│    • COMPATIBLE_WITH      - Can be combined in same project                 │
│    • INCOMPATIBLE_WITH    - Cannot be combined                              │
│    • UPGRADABLE_TO        - Can upgrade to newer version                    │
│    • DERIVED_FROM         - Based on another license                        │
│                                                                              │
│  License → Obligation:                                                      │
│    • REQUIRES             - License requires this obligation                │
│                                                                              │
│  License → Right:                                                           │
│    • GRANTS               - License grants this right                       │
│                                                                              │
│  License → Condition:                                                       │
│    • HAS_CONDITION        - License has this condition                      │
│                                                                              │
│  License → Limitation:                                                      │
│    • HAS_LIMITATION       - License has this limitation                     │
│                                                                              │
│  UseCase → Obligation:                                                      │
│    • TRIGGERS             - Use case triggers obligation                    │
│    • EXEMPT_FROM          - Use case exempt from obligation                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Compatibility Edge

Represents compatibility relationship between two licenses.

| Field | Type | Description |
|-------|------|-------------|
| `sourceId` | String | First license ID |
| `targetId` | String | Second license ID |
| `compatibility` | Enum | FULL, CONDITIONAL, ONE_WAY, INCOMPATIBLE, UNKNOWN |
| `direction` | Enum | BIDIRECTIONAL, FORWARD, REVERSE |
| `conditions` | List<String> | Conditions for compatibility |
| `notes` | List<String> | Explanatory notes |
| `sources` | List<String> | Legal references/sources |

#### Compatibility Levels

```kotlin
enum class CompatibilityLevel {
    FULL,                   // Fully compatible, no restrictions
    CONDITIONAL,            // Compatible with specific conditions
    ONE_WAY,                // A can incorporate B, but not reverse
    INCOMPATIBLE,           // Cannot be combined
    UNKNOWN                 // Requires legal review
}
```

#### Obligation Edge

Links a license to its obligations.

| Field | Type | Description |
|-------|------|-------------|
| `sourceId` | String | License ID |
| `targetId` | String | Obligation ID |
| `trigger` | Enum | When obligation applies |
| `scope` | Enum | What the obligation applies to |

#### Obligation Scope

```kotlin
enum class ObligationScope {
    MODIFIED_FILES,         // Only modified files (MPL style)
    COMPONENT,              // The specific component only
    DERIVATIVE_WORK,        // Entire derivative work (GPL style)
    DISTRIBUTED_WORK        // Anything distributed
}
```

---

## 3. Storage Architecture

### 3.1 Hybrid Storage Approach

OrtoPed uses a hybrid storage model optimizing for both query speed and persistence:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    HYBRID GRAPH STORAGE ARCHITECTURE                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌──────────────────────────────────────────────────────────────────────┐  │
│   │                     IN-MEMORY GRAPH ENGINE                            │  │
│   │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐          │  │
│   │  │ License Nodes  │  │   Edges        │  │  Indexes       │          │  │
│   │  │ (~500 licenses)│  │ (~2000+ edges) │  │ (by SPDX ID)   │          │  │
│   │  └────────────────┘  └────────────────┘  └────────────────┘          │  │
│   │                                                                       │  │
│   │            Fast traversal, path finding, reasoning                    │  │
│   └───────────────────────────────────────────────────────────────────────┘  │
│                               ▲                                              │
│                               │ Load on startup                              │
│                               │ Refresh periodically                         │
│                               │                                              │
│   ┌───────────────────────────┴───────────────────────────────────────────┐  │
│   │                    POSTGRESQL (Persistence)                           │  │
│   │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐          │  │
│   │  │ license_nodes  │  │ graph_edges    │  │ custom_rules   │          │  │
│   │  │ (authoritative)│  │ (relationships)│  │ (user-defined) │          │  │
│   │  └────────────────┘  └────────────────┘  └────────────────┘          │  │
│   │                                                                       │  │
│   │  ┌────────────────┐  ┌────────────────┐                              │  │
│   │  │ compatibility_ │  │ obligation_    │                              │  │
│   │  │ overrides      │  │ interpretations│                              │  │
│   │  └────────────────┘  └────────────────┘                              │  │
│   └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Why Hybrid?

| Concern | In-Memory | PostgreSQL |
|---------|-----------|------------|
| Query Speed | Sub-millisecond | 10-100ms |
| Path Finding | Native support | Complex CTEs |
| Persistence | None | Full ACID |
| Custom Rules | Runtime only | Permanent |
| Scaling | Memory limited | Disk-based |

**Decision**: Use in-memory for reads (99% of operations), PostgreSQL for persistence and custom rules.

### 3.3 Database Schema

#### license_nodes Table

```sql
CREATE TABLE license_nodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    spdx_id VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(500) NOT NULL,
    category VARCHAR(50) NOT NULL,
    copyleft_strength VARCHAR(50) NOT NULL,
    family VARCHAR(100),
    version VARCHAR(50),
    is_osi_approved BOOLEAN DEFAULT FALSE,
    is_fsf_free BOOLEAN DEFAULT FALSE,
    is_deprecated BOOLEAN DEFAULT FALSE,
    text_hash VARCHAR(64),
    properties JSONB DEFAULT '{}',
    source VARCHAR(50) NOT NULL,  -- 'spdx', 'custom', 'inferred'
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_license_nodes_category ON license_nodes(category);
CREATE INDEX idx_license_nodes_family ON license_nodes(family);
```

#### obligation_nodes Table

```sql
CREATE TABLE obligation_nodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    obligation_id VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    trigger_condition VARCHAR(50) NOT NULL,
    effort VARCHAR(50) NOT NULL,
    examples JSONB DEFAULT '[]',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

#### graph_edges Table

```sql
CREATE TABLE graph_edges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    edge_type VARCHAR(50) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(100) NOT NULL,

    -- Compatibility-specific
    compatibility_level VARCHAR(50),
    direction VARCHAR(50),
    conditions JSONB,
    notes JSONB,
    sources JSONB,

    -- Obligation-specific
    trigger VARCHAR(50),
    scope VARCHAR(50),

    -- General
    properties JSONB DEFAULT '{}',
    confidence DECIMAL(3,2) DEFAULT 1.0,
    source VARCHAR(50) NOT NULL,  -- 'curated', 'inferred', 'spdx'
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE(source_type, source_id, edge_type, target_type, target_id)
);

CREATE INDEX idx_graph_edges_source ON graph_edges(source_type, source_id);
CREATE INDEX idx_graph_edges_target ON graph_edges(target_type, target_id);
CREATE INDEX idx_graph_edges_type ON graph_edges(edge_type);
```

#### compatibility_overrides Table

```sql
CREATE TABLE compatibility_overrides (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id VARCHAR(100),
    license_1 VARCHAR(100) NOT NULL,
    license_2 VARCHAR(100) NOT NULL,
    override_compatibility VARCHAR(50) NOT NULL,
    reason TEXT NOT NULL,
    approved_by VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE(organization_id, license_1, license_2)
);
```

---

## 4. Core Algorithms

### 4.1 Compatibility Checking

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    COMPATIBILITY CHECK ALGORITHM                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Input: license1, license2, useCase (optional)                              │
│                                                                              │
│  1. Same License Check                                                      │
│     └─► If license1 == license2 → FULL compatibility                        │
│                                                                              │
│  2. Direct Edge Lookup                                                      │
│     └─► Check compatibilityIndex[(license1, license2)]                      │
│         └─► If found → Return stored compatibility                          │
│                                                                              │
│  3. Category-Based Inference                                                │
│     ├─► Permissive + Permissive → FULL                                     │
│     ├─► Permissive + Copyleft → CONDITIONAL (copyleft dominates)           │
│     ├─► Strong Copyleft + Strong Copyleft (different) → INCOMPATIBLE       │
│     ├─► GPL-2.0 + GPL-3.0 → INCOMPATIBLE (special case)                    │
│     └─► Unknown → Requires legal review                                     │
│                                                                              │
│  4. Use Case Adjustment (if provided)                                       │
│     ├─► Internal only → More permissive                                    │
│     ├─► SaaS → AGPL triggers apply                                         │
│     └─► Embedded → Static linking rules apply                              │
│                                                                              │
│  Output: CompatibilityResult with level, reason, conditions, suggestions    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Compatibility Inference Rules

| License 1 Category | License 2 Category | Result | Reasoning |
|--------------------|-------------------|--------|-----------|
| Permissive | Permissive | FULL | Both allow unrestricted use |
| Permissive | Weak Copyleft | CONDITIONAL | Copyleft terms apply to modified files |
| Permissive | Strong Copyleft | CONDITIONAL | Entire work under copyleft |
| Weak Copyleft | Weak Copyleft | CONDITIONAL | Most restrictive terms apply |
| Strong Copyleft | Strong Copyleft (same family) | CONDITIONAL | Version compatibility matters |
| Strong Copyleft | Strong Copyleft (different) | INCOMPATIBLE | Conflicting copyleft requirements |
| Any | Network Copyleft | CONDITIONAL | Network use triggers source disclosure |

### 4.3 Obligation Aggregation

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    OBLIGATION AGGREGATION ALGORITHM                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Input: List<licenseId>                                                     │
│                                                                              │
│  1. Collect All Obligations                                                 │
│     FOR each license:                                                       │
│       └─► Get outgoing REQUIRES edges                                       │
│           └─► Map to ObligationNode + source license                        │
│                                                                              │
│  2. Group by Obligation ID                                                  │
│     └─► Multiple licenses may require same obligation                       │
│                                                                              │
│  3. Determine Most Restrictive Scope                                        │
│     └─► MODIFIED_FILES < COMPONENT < DERIVATIVE_WORK < DISTRIBUTED_WORK    │
│                                                                              │
│  4. Build Aggregated Result                                                 │
│     FOR each unique obligation:                                             │
│       └─► List all triggering licenses                                      │
│       └─► Use most restrictive scope                                        │
│       └─► Provide compliance examples                                       │
│                                                                              │
│  Output: AggregatedObligations with per-obligation sources and scope        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.4 Dependency Tree Analysis

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DEPENDENCY TREE ANALYSIS                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Input: List<DependencyLicense>, targetUseCase                              │
│                                                                              │
│  1. Pairwise Compatibility Check                                            │
│     FOR i = 0 to n-1:                                                       │
│       FOR j = i+1 to n:                                                     │
│         └─► checkCompatibility(deps[i], deps[j], useCase)                   │
│         └─► If incompatible → Add to conflicts list                         │
│                                                                              │
│  2. Find Dominant License                                                   │
│     └─► Sort by (copyleftStrength * 10 + category.ordinal)                 │
│     └─► Most restrictive = dominant                                         │
│                                                                              │
│  3. Aggregate Obligations                                                   │
│     └─► Run obligation aggregation on all license IDs                       │
│                                                                              │
│  4. Determine Compliance Status                                             │
│     ├─► BLOCKING conflicts → BLOCKED                                        │
│     ├─► WARNING conflicts → WARNINGS                                        │
│     └─► No conflicts → COMPLIANT                                            │
│                                                                              │
│  5. Generate Recommendations                                                │
│     └─► For each conflict: suggest resolutions                              │
│     └─► For high-effort obligations: highlight actions                      │
│                                                                              │
│  Output: DependencyTreeAnalysis with conflicts, dominant license,           │
│          aggregated obligations, compliance status, recommendations         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.5 Path Finding

BFS-based algorithm to find compatibility paths between licenses:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    COMPATIBILITY PATH FINDING                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Input: source, target, maxDepth=3                                          │
│                                                                              │
│  Algorithm: Breadth-First Search                                            │
│                                                                              │
│  visited = {}                                                               │
│  queue = [[source]]                                                         │
│                                                                              │
│  WHILE queue not empty:                                                     │
│    path = queue.removeFirst()                                               │
│    current = path.last()                                                    │
│                                                                              │
│    IF current == target:                                                    │
│      RETURN buildPath(path)                                                 │
│                                                                              │
│    IF path.length > maxDepth OR current in visited:                         │
│      CONTINUE                                                               │
│                                                                              │
│    visited.add(current)                                                     │
│                                                                              │
│    FOR each compatible neighbor:                                            │
│      IF neighbor not in visited:                                            │
│        queue.add(path + [neighbor])                                         │
│                                                                              │
│  RETURN null  // No path found                                              │
│                                                                              │
│  Output: CompatibilityPath or null                                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Pre-Loaded Knowledge Base

### 5.1 Standard Licenses

The graph is pre-loaded with ~500 SPDX licenses, categorized as:

#### Permissive Licenses
- MIT, ISC, BSD-2-Clause, BSD-3-Clause
- Apache-2.0 (with patent grant)
- CC0-1.0, Unlicense, WTFPL, 0BSD
- Zlib, Artistic-2.0

#### Weak Copyleft Licenses
- LGPL-2.0, LGPL-2.1, LGPL-3.0 (library copyleft)
- MPL-2.0 (file-level copyleft)
- EPL-1.0, EPL-2.0 (module copyleft)
- CDDL-1.0

#### Strong Copyleft Licenses
- GPL-2.0-only, GPL-2.0-or-later
- GPL-3.0-only, GPL-3.0-or-later

#### Network Copyleft Licenses
- AGPL-3.0-only, AGPL-3.0-or-later

### 5.2 Pre-Loaded Compatibility Rules

#### Known Incompatibilities

| License 1 | License 2 | Reason |
|-----------|-----------|--------|
| GPL-2.0-only | GPL-3.0-only | GPL-3.0 added incompatible patent provisions |
| Apache-2.0 | GPL-2.0-only | Apache patent clause incompatible with GPL-2.0 |
| GPL-3.0 | AGPL-3.0 | Can combine, but AGPL terms dominate |

#### Known Compatibilities

| License 1 | License 2 | Direction | Conditions |
|-----------|-----------|-----------|------------|
| Apache-2.0 | GPL-3.0 | One-way (Apache→GPL) | Combined work must be GPL-3.0 |
| MIT | Any permissive | Bidirectional | Maintain attribution |
| LGPL-3.0 | Proprietary | One-way (LGPL→Prop) | Dynamic linking, provide LGPL source |
| MPL-2.0 | Proprietary | One-way (MPL→Prop) | Modified MPL files stay MPL |

### 5.3 Obligation Mappings

#### MIT
- `attribution` (On Distribution, Low effort)

#### Apache-2.0
- `attribution` (On Distribution, Low effort)
- `state-changes` (On Modification, Medium effort)
- `notice-file` (On Distribution, Low effort)
- `patent-grant` (Always, Trivial)

#### GPL-3.0
- `attribution` (On Distribution, Low effort)
- `source-disclosure` (On Distribution, High effort)
- `same-license` (On Derivative, Very High effort)
- `state-changes` (On Modification, Medium effort)

#### AGPL-3.0
- All GPL-3.0 obligations, plus:
- `network-disclosure` (On Network Use, Very High effort)

#### LGPL-3.0
- `attribution` (On Distribution, Low effort)
- `source-disclosure` (On Distribution, scope: COMPONENT only)

#### MPL-2.0
- `attribution` (On Distribution, scope: MODIFIED_FILES)
- `source-disclosure` (On Distribution, scope: MODIFIED_FILES)

---

## 6. API Design

### 6.1 Endpoints

```
License Graph API
================

# Statistics
GET  /graph/statistics                    → Graph overview stats

# License Information
GET  /graph/licenses/{id}                 → Get license details
GET  /graph/licenses/{id}/obligations     → Get license obligations
GET  /graph/licenses/{id}/rights          → Get license rights

# Compatibility Checks
POST /graph/compatibility/check           → Check two licenses
POST /graph/compatibility/path            → Find compatibility path
POST /graph/compatibility/matrix          → Check multiple pairs

# Tree Analysis
POST /graph/analyze                       → Analyze dependency tree
POST /graph/analyze/conflicts             → Get conflicts only
POST /graph/analyze/dominant              → Get dominant license

# Obligation Aggregation
POST /graph/obligations/aggregate         → Aggregate from license list
POST /graph/obligations/for-usecase       → Filter by use case
```

### 6.2 Request/Response Examples

#### Check Compatibility

**Request:**
```json
POST /graph/compatibility/check
{
  "license1": "Apache-2.0",
  "license2": "GPL-3.0-only",
  "useCase": "saas"
}
```

**Response:**
```json
{
  "license1": "Apache-2.0",
  "license2": "GPL-3.0-only",
  "compatible": true,
  "level": "ONE_WAY",
  "reason": "Apache-2.0 code can be included in GPL-3.0 projects",
  "conditions": [
    "Combined work must be distributed under GPL-3.0",
    "Apache-2.0 attribution must be maintained"
  ],
  "notes": [
    "This is a one-way compatibility",
    "GPL-3.0 code cannot be relicensed under Apache-2.0"
  ],
  "dominantLicense": "GPL-3.0-only",
  "sources": [
    "https://www.gnu.org/licenses/license-list.html#apache2",
    "https://www.apache.org/licenses/GPL-compatibility.html"
  ]
}
```

#### Analyze Dependency Tree

**Request:**
```json
POST /graph/analyze
{
  "dependencies": [
    {"dependencyName": "express", "dependencyVersion": "4.18.2", "license": "MIT"},
    {"dependencyName": "lodash", "dependencyVersion": "4.17.21", "license": "MIT"},
    {"dependencyName": "readline-sync", "dependencyVersion": "1.4.10", "license": "MIT"},
    {"dependencyName": "mysql2", "dependencyVersion": "3.6.0", "license": "MIT"},
    {"dependencyName": "some-gpl-lib", "dependencyVersion": "1.0.0", "license": "GPL-3.0-only"}
  ]
}
```

**Response:**
```json
{
  "totalDependencies": 5,
  "uniqueLicenses": ["MIT", "GPL-3.0-only"],
  "conflicts": [],
  "dominantLicense": "GPL-3.0-only",
  "complianceStatus": "COMPLIANT",
  "aggregatedObligations": {
    "obligations": [
      {
        "obligation": {
          "id": "attribution",
          "name": "Attribution",
          "description": "Include copyright notice and license text",
          "effort": "LOW"
        },
        "sources": [
          {"license": "MIT", "trigger": "ON_DISTRIBUTION"},
          {"license": "GPL-3.0-only", "trigger": "ON_DISTRIBUTION"}
        ],
        "mostRestrictiveScope": "DERIVATIVE_WORK"
      },
      {
        "obligation": {
          "id": "source-disclosure",
          "name": "Source Code Disclosure",
          "description": "Make source code available to recipients",
          "effort": "HIGH"
        },
        "sources": [
          {"license": "GPL-3.0-only", "trigger": "ON_DISTRIBUTION"}
        ],
        "mostRestrictiveScope": "DERIVATIVE_WORK"
      },
      {
        "obligation": {
          "id": "same-license",
          "name": "Same License",
          "description": "Derivative works must use the same license",
          "effort": "VERY_HIGH"
        },
        "sources": [
          {"license": "GPL-3.0-only", "trigger": "ON_DERIVATIVE"}
        ],
        "mostRestrictiveScope": "DERIVATIVE_WORK"
      }
    ],
    "totalLicenses": 5
  }
}
```

---

## 7. Integration with OrtoPed Workflow

### 7.1 Enhanced Policy Evaluation

The License Knowledge Graph integrates with the existing policy evaluation workflow:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ENHANCED POLICY EVALUATION FLOW                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   Scan Result                                                               │
│       │                                                                      │
│       ▼                                                                      │
│   ┌──────────────────┐                                                      │
│   │ Extract Licenses │                                                      │
│   └────────┬─────────┘                                                      │
│            │                                                                 │
│            ▼                                                                 │
│   ┌──────────────────┐     ┌──────────────────┐                            │
│   │ Traditional      │     │ Knowledge Graph   │                            │
│   │ Policy Check     │     │ Analysis          │                            │
│   │ (allow/deny)     │     │ (compatibility)   │                            │
│   └────────┬─────────┘     └────────┬─────────┘                            │
│            │                         │                                       │
│            └──────────┬──────────────┘                                       │
│                       ▼                                                      │
│            ┌──────────────────┐                                             │
│            │ Enhanced Report   │                                             │
│            │ • Violations      │                                             │
│            │ • Conflicts       │                                             │
│            │ • Obligations     │                                             │
│            │ • Recommendations │                                             │
│            └──────────────────┘                                             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.2 Curation Integration

During curation, the graph provides:

1. **License Validation**: Verify curated license is valid SPDX ID
2. **Compatibility Preview**: Show how curated license affects project
3. **Obligation Preview**: Show what obligations the curated license adds
4. **Conflict Detection**: Warn if curated license conflicts with others

### 7.3 Report Enhancement

Comprehensive reports include:

1. **License Compatibility Matrix**: Visual grid of all license combinations
2. **Obligation Summary**: All obligations aggregated from dependency tree
3. **Conflict List**: Detailed conflict explanations with resolutions
4. **Compliance Recommendations**: Actionable steps for each issue

---

## 8. UI Visualization

### 8.1 Dependency License Graph

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    LICENSE COMPATIBILITY GRAPH                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                            ┌─────────┐                                       │
│                     ┌──────│  MIT    │──────┐                               │
│                     │      │ (35)    │      │                               │
│                     │      └────┬────┘      │                               │
│                     │           │           │                               │
│              ┌──────▼──────┐    │    ┌──────▼──────┐                        │
│              │ Apache-2.0  │    │    │  BSD-3      │                        │
│              │    (8)      │    │    │   (4)       │                        │
│              └──────┬──────┘    │    └─────────────┘                        │
│                     │           │                                            │
│            ┌────────┼───────────┼────────┐                                  │
│            │        │           │        │                                  │
│     ┌──────▼──────┐ │    ┌──────▼──────┐ │                                  │
│     │   GPL-3.0   │ │    │  LGPL-3.0   │ │                                  │
│     │   ⚠️ (3)    │ │    │   ✓ (5)     │ │                                  │
│     └──────┬──────┘ │    └─────────────┘ │                                  │
│            │        │                     │                                  │
│            ╳ CONFLICT                     │                                  │
│            │        │                     │                                  │
│     ┌──────▼──────┐ │                     │                                  │
│     │  GPL-2.0    │◄┘                     │                                  │
│     │   ❌ (1)    │                       │                                  │
│     └─────────────┘                       │                                  │
│                                                                              │
│  Legend:                                                                    │
│  ──── Compatible   ╳ Incompatible   (n) = dependency count                  │
│  ⚠️ Has warnings   ❌ Blocking conflict   ✓ No issues                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 8.2 Obligation Summary Dashboard

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AGGREGATED OBLIGATIONS                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Your project uses 47 dependencies with 12 unique licenses.                 │
│  Dominant license: GPL-3.0 (from 3 dependencies)                            │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │ OBLIGATION              │ EFFORT │ TRIGGERED BY           │ STATUS     │ │
│  ├────────────────────────────────────────────────────────────────────────┤ │
│  │ ● Source Disclosure     │ HIGH   │ GPL-3.0 (lib-a, lib-b) │ ⚠️ Review  │ │
│  │ ● Same License          │ V.HIGH │ GPL-3.0 (lib-a, lib-b) │ ⚠️ Review  │ │
│  │ ● Attribution           │ LOW    │ MIT (35), Apache (8)   │ ✓ Ready    │ │
│  │ ● State Changes         │ MED    │ Apache-2.0 (8 deps)    │ ✓ Ready    │ │
│  │ ● NOTICE File           │ LOW    │ Apache-2.0 (3 deps)    │ ⚠️ Missing │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  [Generate Attribution File]  [Generate NOTICE File]  [Export Obligations]  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 8.3 Conflict Detail View

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  ❌ LICENSE CONFLICT DETECTED                                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  GPL-2.0-only ←──INCOMPATIBLE──→ GPL-3.0-only                               │
│                                                                              │
│  AFFECTED DEPENDENCIES:                                                     │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │ • legacy-parser@2.1.0 (GPL-2.0-only)                                   │ │
│  │   └─ Required by: data-processor@1.0.0                                 │ │
│  │                                                                         │ │
│  │ • modern-utils@3.0.0 (GPL-3.0-only)                                    │ │
│  │   └─ Required by: api-handler@2.0.0                                    │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  WHY THIS IS A PROBLEM:                                                     │
│  ─────────────────────                                                      │
│  • GPL-2.0-only and GPL-3.0-only are different license versions            │
│  • GPL-3.0 added patent provisions that GPL-2.0-only cannot accept         │
│  • Both require derivative works under their own terms                     │
│  • This creates an irreconcilable licensing conflict                       │
│                                                                              │
│  RESOLUTION OPTIONS:                                                        │
│  ─────────────────────                                                      │
│  A. Check if GPL-2.0 code is "or later" licensed (compatible with GPL-3.0)│
│  B. Replace legacy-parser with a GPL-3.0 or permissive alternative        │
│  C. Replace modern-utils with a GPL-2.0 compatible alternative             │
│  D. Contact upstream maintainers for dual-licensing options                │
│                                                                              │
│  LEGAL REFERENCES:                                                          │
│  • https://www.gnu.org/licenses/gpl-faq.html#AllCompatibility              │
│                                                                              │
│  [Find Alternatives]  [Contact Maintainer]  [Add Exemption]                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Implementation Roadmap

### Phase 1: Core Infrastructure (Week 1)

| Task | Description | Deliverable |
|------|-------------|-------------|
| 1.1 | Define Kotlin data models | `GraphNodes.kt`, `GraphEdges.kt` |
| 1.2 | Implement in-memory graph engine | `LicenseKnowledgeGraph.kt` |
| 1.3 | Add node/edge storage with indexes | Graph operations |
| 1.4 | Implement compatibility checking | `checkCompatibility()` |

### Phase 2: Data Loading (Week 2)

| Task | Description | Deliverable |
|------|-------------|-------------|
| 2.1 | Create obligation definitions | Standard obligations |
| 2.2 | Create rights definitions | Standard rights |
| 2.3 | Load ~50 key licenses | Initial license set |
| 2.4 | Define compatibility rules | Key compatibility edges |
| 2.5 | Map licenses to obligations | Obligation edges |

### Phase 3: API Layer (Week 2-3)

| Task | Description | Deliverable |
|------|-------------|-------------|
| 3.1 | Create `LicenseGraphService` | Service layer |
| 3.2 | Implement graph API endpoints | REST API |
| 3.3 | Add request/response models | API DTOs |
| 3.4 | Integration tests | Test coverage |

### Phase 4: Integration (Week 3)

| Task | Description | Deliverable |
|------|-------------|-------------|
| 4.1 | Integrate with `PolicyEvaluator` | Enhanced evaluation |
| 4.2 | Integrate with curation workflow | Curation enhancements |
| 4.3 | Update comprehensive reports | Graph-enhanced reports |

### Phase 5: Persistence (Week 4)

| Task | Description | Deliverable |
|------|-------------|-------------|
| 5.1 | Create database schema | Migration files |
| 5.2 | Implement repository layer | Graph repositories |
| 5.3 | Add SPDX sync mechanism | Auto-update from SPDX |
| 5.4 | Support custom overrides | Org-specific rules |

### Phase 6: UI (Week 4-5)

| Task | Description | Deliverable |
|------|-------------|-------------|
| 6.1 | License graph visualization | D3.js/Cytoscape component |
| 6.2 | Obligation summary view | Dashboard component |
| 6.3 | Conflict detail modal | Detail view |
| 6.4 | Compatibility matrix view | Matrix component |

### Phase 7: Advanced Features (Week 5+)

| Task | Description | Deliverable |
|------|-------------|-------------|
| 7.1 | AI-powered natural language queries | "Can I use X with Y?" |
| 7.2 | Use case profiles | SaaS, embedded, internal |
| 7.3 | Regulatory mapping | GDPR, export controls |
| 7.4 | Legal reference linking | Case law integration |

---

## 10. File Structure

```
core/src/main/kotlin/com/ortoped/core/
├── graph/
│   ├── model/
│   │   ├── GraphNodes.kt           # Node type definitions
│   │   ├── GraphEdges.kt           # Edge type definitions
│   │   └── GraphResults.kt         # Result data classes
│   ├── LicenseKnowledgeGraph.kt    # In-memory graph engine
│   ├── LicenseGraphLoader.kt       # Initial data loader
│   └── LicenseGraphQueryEngine.kt  # Complex query support

api/src/main/kotlin/com/ortoped/api/
├── model/
│   └── GraphEntities.kt            # Database table definitions
├── repository/
│   ├── LicenseNodeRepository.kt    # License persistence
│   ├── GraphEdgeRepository.kt      # Edge persistence
│   └── CompatibilityOverrideRepository.kt
├── service/
│   └── LicenseGraphService.kt      # Service layer
└── routes/
    └── LicenseGraphRoutes.kt       # API endpoints

dashboard/src/
├── components/
│   ├── LicenseGraph.vue            # D3 visualization
│   ├── ObligationSummary.vue       # Obligation dashboard
│   ├── ConflictDetail.vue          # Conflict modal
│   └── CompatibilityMatrix.vue     # Matrix view
└── views/
    └── LicenseAnalysisView.vue     # Main analysis page
```

---

## 11. Future Enhancements

### 11.1 AI-Powered Queries

Natural language interface:
- "Can I use this library in my commercial SaaS product?"
- "What obligations do I have from using React?"
- "Find me an MIT alternative to this GPL library"

### 11.2 Legal Precedent Database

Track and link to:
- Court cases (Oracle v. Google, SCO v. IBM)
- FSF/OSI interpretations
- Industry guidance documents

### 11.3 Regulatory Compliance Mapping

Map licenses to regulations:
- GDPR (data processing requirements)
- Export controls (cryptography)
- Industry-specific (HIPAA, FedRAMP)

### 11.4 Community Intelligence

Anonymized learning from:
- Curation decisions across organizations
- Common resolution patterns
- Emerging license trends

---

## References

- [SPDX License List](https://spdx.org/licenses/)
- [GNU License Compatibility](https://www.gnu.org/licenses/gpl-faq.html)
- [Apache License Compatibility](https://www.apache.org/licenses/GPL-compatibility.html)
- [OSI License List](https://opensource.org/licenses)
- [Choose a License](https://choosealicense.com/)
- [TLDRLegal](https://tldrlegal.com/)
