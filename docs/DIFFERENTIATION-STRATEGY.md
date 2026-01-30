# OrtoPed Differentiation Strategy

## Competitive Landscape Analysis

### Current Market Leaders

| Vendor | Focus | Strengths | Weaknesses |
|--------|-------|-----------|------------|
| **Snyk** | Developer-first security | Real-time IDE integration, fix suggestions, container scanning | Rule-based, limited license intelligence |
| **Black Duck** | Enterprise compliance | Comprehensive database, audit workflows | Complex, expensive, legacy architecture |
| **FOSSA** | License compliance | Attribution reports, policy-as-code | Limited AI, basic compatibility checking |
| **WhiteSource (Mend)** | Vulnerability + license | Good integration, auto-remediation | Generic license handling |
| **OSS Review Toolkit** | Open source | Flexible, extensible | Complex setup, no AI |

### OrtoPed Positioning

**Vision**: Transform from a license scanner into an **Intelligent License Compliance Platform** that doesn't just detect issues but **explains, guides, and automates** compliance.

**Tagline**: "License compliance that thinks with you, not just for you."

---

## Strategic Differentiators

### 1. License Knowledge Graph

**Status**: Architecture complete, ready for implementation

**What it does**: Models licenses as interconnected entities with semantic relationships, enabling reasoning about compatibility, obligations, and conflicts.

**Competitor comparison**:
- Competitors: Flat rule tables ("GPL-3.0 is copyleft")
- OrtoPed: Rich graph with paths, inference, aggregation

**Key capabilities**:
- Path-based compatibility analysis
- Obligation aggregation across dependency tree
- Conflict detection with resolution suggestions
- Use-case aware evaluation (SaaS vs embedded vs internal)

**Documentation**: [LICENSE-KNOWLEDGE-GRAPH-ARCHITECTURE.md](./LICENSE-KNOWLEDGE-GRAPH-ARCHITECTURE.md)

---

### 2. "Why Not?" Explanations

**Status**: Design phase

**What it does**: Every violation includes clear reasoning, not just "violation detected."

**Example**:
```
┌─────────────────────────────────────────────────────────────────────────────┐
│  VIOLATION: GPL-3.0-only in runtime dependency                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Package: some-lib@1.0.0                                                    │
│  License: GPL-3.0-only                                                      │
│                                                                              │
│  WHY THIS IS A PROBLEM:                                                     │
│  ─────────────────────                                                      │
│  1. Your project intent states "commercial SaaS product"                    │
│  2. GPL-3.0 requires derivative works to be GPL-licensed                    │
│  3. Linking some-lib makes your application a derivative work               │
│  4. You would need to release your SaaS source code                         │
│                                                                              │
│  RESOLUTION OPTIONS:                                                        │
│  ─────────────────────                                                      │
│  A. Replace with MIT-licensed alternative: other-lib                        │
│  B. Isolate via network boundary (microservice)                             │
│  C. Contact author for commercial license                                   │
│  D. Accept GPL obligations for this project                                 │
│                                                                              │
│  SIMILAR PAST DECISIONS:                                                    │
│  ─────────────────────                                                      │
│  • Project X: Chose option A (replaced with other-lib)                      │
│  • Project Y: Chose option B (isolated as service)                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Implementation approach**:
- Integrate with Knowledge Graph for reasoning
- Template-based explanation generation
- AI enhancement for natural language explanations
- Learning from past curation decisions

---

### 3. Obligation Fulfillment Tracker

**Status**: Design phase

**What it does**: Track not just what obligations exist, but whether they've been fulfilled.

**Competitor comparison**:
- Competitors: List obligations per license
- OrtoPed: Aggregate, track fulfillment status, auto-generate artifacts

**Dashboard concept**:
```
┌─────────────────────────────────────────────────────────────────────────────┐
│           OBLIGATION FULFILLMENT DASHBOARD                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Project: my-saas-app                                                       │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │ ATTRIBUTION REQUIRED              │ Status      │ Action                │ │
│  ├────────────────────────────────────────────────────────────────────────┤ │
│  │ MIT (47 packages)                 │ ✓ Complete  │ [View file]           │ │
│  │ Apache-2.0 (12 packages)          │ ✓ Complete  │ [View file]           │ │
│  │ BSD-3-Clause (8 packages)         │ ⚠️ Missing 2│ [Fix now]             │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │ NOTICE FILE REQUIREMENTS          │ Status                             │ │
│  ├────────────────────────────────────────────────────────────────────────┤ │
│  │ Apache-2.0 NOTICE files           │ ⚠️ 3 missing                       │ │
│  │ Action: Include NOTICE from these packages in your distribution        │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  [Generate Attribution File]  [Generate NOTICE File]  [Export Checklist]    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Key features**:
- Auto-generate ATTRIBUTION.md / THIRD_PARTY_LICENSES files
- Auto-generate NOTICE file aggregations
- Track source disclosure requirements
- CI/CD integration for compliance verification

---

### 4. License Provenance Verification

**Status**: Design phase

**What it does**: Verify that declared licenses match actual source code.

**Problem**: Package managers often have incorrect or outdated license metadata.

**Verification sources**:
```
┌─────────────────────────────────────────────────────────────────────────────┐
│              LICENSE PROVENANCE VERIFICATION                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Package: lodash@4.17.21                                                    │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │ SOURCE                          │ DETECTED LICENSE                    │   │
│  ├──────────────────────────────────────────────────────────────────────┤   │
│  │ DECLARED (package.json)         │ MIT                                 │   │
│  │ DETECTED (LICENSE file)         │ MIT                                 │   │
│  │ SOURCE SCAN (code headers)      │ MIT                                 │   │
│  │ REGISTRY (npm)                  │ MIT                                 │   │
│  │ GITHUB (repository license)     │ MIT                                 │   │
│  │ SPDX DATABASE                   │ MIT (valid SPDX ID)                 │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Provenance Score: 100% ✓  (All sources agree)                              │
│                                                                              │
│  ────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  Package: sketchy-lib@2.0.0                                                 │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │ DECLARED (package.json)         │ MIT                                 │   │
│  │ DETECTED (LICENSE file)         │ GPL-3.0 ⚠️                          │   │
│  │ SOURCE SCAN (code headers)      │ Mixed (MIT + GPL)                   │   │
│  │ REGISTRY                        │ MIT                                 │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Provenance Score: 45% ⚠️  CONFLICT DETECTED - REQUIRES REVIEW              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Implementation**:
- Multi-source license detection (already partially implemented)
- Provenance scoring algorithm
- Conflict flagging and alerting
- Historical tracking of license changes

---

### 5. Intent-Based Policy Language

**Status**: Concept phase

**What it does**: Allow business users to express compliance requirements in natural language.

**Traditional approach** (Black Duck, FOSSA):
```yaml
rules:
  - deny:
      licenses: [GPL-3.0, AGPL-3.0]
      scopes: [runtime, compile]
  - warn:
      categories: [copyleft]
```

**OrtoPed Intent-Based approach**:
```yaml
intent: |
  We are building a commercial SaaS product.
  We cannot use strong copyleft licenses in our shipped code.
  Development tools and test dependencies are fine.
  We need attribution for all permissive licenses.
  Internal microservices can use LGPL if dynamically linked.

  Our industry: Financial Services
  Regulations: SOX compliance required
```

**AI Translation Pipeline**:
```
Intent Statement
      │
      ▼
┌─────────────────┐
│  AI Parser      │ ──▶ Clarification questions if ambiguous
│  (Claude API)   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Structured      │
│ Policy Rules    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Policy          │
│ Evaluation      │
└─────────────────┘
```

**Benefits**:
- Non-technical stakeholders can define policies
- Reduces misconfiguration errors
- Self-documenting compliance requirements
- AI can suggest policy improvements

---

### 6. Historical License Change Tracking

**Status**: Concept phase

**What it does**: Track license changes across package versions over time.

**Real-world example**: The `faker.js` incident
```
┌─────────────────────────────────────────────────────────────────────────────┐
│           LICENSE TIMELINE: faker.js                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  v5.5.3 (2020-12) ────▶ MIT                                                 │
│       │                                                                      │
│  v6.0.0 (2022-01) ────▶ UNLICENSED ⚠️ (author protest)                      │
│       │                                                                      │
│  @faker-js/faker v7.0.0 (2022-02) ────▶ MIT (community fork)                │
│                                                                              │
│  ⚠️ ALERT: You're using faker@5.5.3                                         │
│     Risk: Original project abandoned, unclear maintenance                   │
│     Recommendation: Migrate to @faker-js/faker                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Use cases**:
- Detect license "rug pulls" (sudden license changes)
- Track relicensing patterns (e.g., SSPL conversions)
- Alert when pinned version has different license than latest
- Historical audit for compliance reviews

---

### 7. Community Curation Intelligence

**Status**: Concept phase

**What it does**: Learn from anonymized curation decisions across organizations.

**Architecture**:
```
┌──────────────────────────────────────────────────────────────────────────────┐
│            FEDERATED CURATION INTELLIGENCE                                    │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  Org A Curations ─┐                                                          │
│                   │   ┌─────────────────────┐                                │
│  Org B Curations ─┼──▶│ Anonymization Layer │──▶ Global Intelligence Model   │
│                   │   │ - Strip org data    │                                │
│  Org C Curations ─┘   │ - Aggregate signals │                                │
│                       └─────────────────────┘                                │
│                                                                               │
│  Global Model provides:                                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐   │
│  │ "lodash@4.17.21 → MIT" (99.2% confidence, 12,456 curations)           │   │
│  │ "react@18.2.0 → MIT" (99.8% confidence, 34,567 curations)             │   │
│  │ "obscure-lib@1.0.0 → No community data available"                     │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                               │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Privacy considerations**:
- Opt-in only
- No organization-identifiable data
- Only aggregate statistics shared
- Local-first processing

**Benefits**:
- Higher accuracy for common packages
- Faster curation for known packages
- Community-validated license conclusions
- Crowdsourced edge case handling

---

### 8. Regulatory Compliance Mapping

**Status**: Concept phase

**What it does**: Map license implications to specific regulations and industries.

**Regulation mappings**:

| Regulation | License Implications |
|------------|---------------------|
| **GDPR** | Data processing in dependencies, privacy obligations |
| **HIPAA** | Healthcare data handling, audit requirements |
| **SOX** | Financial controls, audit trails |
| **Export Controls** | Cryptography restrictions, country limitations |
| **FedRAMP** | Government cloud compliance |

**Industry profiles**:
```yaml
industry_profile: financial_services
  regulations:
    - SOX
    - PCI-DSS
    - GLBA
  license_concerns:
    - Prefer licenses with patent grants (Apache-2.0)
    - Avoid viral copyleft in core systems
    - Require full audit trail
  special_requirements:
    - Third-party risk assessment required
    - Vendor due diligence for all dependencies
```

---

## Implementation Priority Matrix

| Feature | Differentiation Value | Implementation Effort | Priority |
|---------|----------------------|----------------------|----------|
| License Knowledge Graph | Very High | Medium (4-5 weeks) | **P0** |
| "Why Not?" Explanations | High | Low (1-2 weeks) | **P1** |
| Obligation Fulfillment Tracker | High | Low (2 weeks) | **P1** |
| Provenance Verification | Very High | Medium (3-4 weeks) | **P2** |
| Intent-Based Policies | Very High | High (6-8 weeks) | **P3** |
| Historical License Tracking | Medium | Low (2 weeks) | **P3** |
| Community Curation Intelligence | Very High | High (8-10 weeks) | **P4** |
| Regulatory Compliance Mapping | Medium | Medium (4 weeks) | **P4** |

---

## Competitive Messaging

### vs Snyk

**Snyk says**: "Find and fix vulnerabilities"
**OrtoPed says**: "Understand and manage license compliance intelligently"

**Key differentiators**:
- OrtoPed explains *why* licenses are problematic, not just *that* they are
- Knowledge Graph enables complex compatibility queries
- Curation workflow designed for license decisions, not security patches

### vs Black Duck

**Black Duck says**: "Comprehensive open source management"
**OrtoPed says**: "License compliance that thinks with you"

**Key differentiators**:
- AI-powered license detection and suggestions
- Modern, developer-friendly interface
- ORT compatibility for existing toolchains
- Transparent pricing (not enterprise-only)

### vs FOSSA

**FOSSA says**: "Open source license compliance and security"
**OrtoPed says**: "From detection to compliance - guided every step"

**Key differentiators**:
- Knowledge Graph for intelligent reasoning
- Obligation tracking beyond just detection
- Human-in-the-loop curation workflow
- "Why Not?" explanations for every decision

---

## Success Metrics

### Short-term (3-6 months)
- [ ] Knowledge Graph implementation complete
- [ ] 50% reduction in curation time via incremental curation
- [ ] "Why Not?" explanations for all violations
- [ ] Attribution file auto-generation

### Medium-term (6-12 months)
- [ ] Provenance verification live
- [ ] Intent-based policies beta
- [ ] 10+ enterprise customers using curation workflow
- [ ] Community curation pilot program

### Long-term (12-24 months)
- [ ] Industry-leading license intelligence platform
- [ ] Regulatory compliance mapping for 3+ industries
- [ ] Community curation network with 100+ organizations
- [ ] Natural language policy queries

---

## Appendix: Feature Comparison Matrix

| Feature | Snyk | Black Duck | FOSSA | ORT | OrtoPed |
|---------|------|------------|-------|-----|---------|
| License Detection | ✓ | ✓✓ | ✓✓ | ✓✓ | ✓✓ |
| AI Enhancement | ✗ | ✗ | ✗ | ✗ | ✓✓ |
| Knowledge Graph | ✗ | ✗ | ✗ | ✗ | ✓✓ |
| Compatibility Reasoning | Basic | Basic | Basic | Rules | Graph |
| Obligation Aggregation | ✗ | Partial | Partial | ✗ | ✓✓ |
| Curation Workflow | ✗ | ✓ | ✗ | ✓ | ✓✓ |
| "Why Not?" Explanations | ✗ | ✗ | ✗ | ✗ | ✓✓ |
| Provenance Verification | ✗ | Partial | ✗ | Partial | ✓✓ |
| Intent-Based Policies | ✗ | ✗ | ✗ | ✗ | Planned |
| Historical Tracking | ✗ | ✗ | ✗ | ✗ | Planned |
| Community Intelligence | ✗ | ✗ | ✗ | ✗ | Planned |

Legend: ✗ = No, ✓ = Basic, ✓✓ = Advanced
