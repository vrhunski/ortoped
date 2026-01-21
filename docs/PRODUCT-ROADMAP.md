# OrtoPed Product Roadmap & Differentiation Strategy

This document outlines the strategic vision for OrtoPed, focusing on features that differentiate it from existing compliance tools (Snyk, FOSSA, Black Duck) by moving beyond simple reporting into intelligent context and automated remediation.

## Core Philosophy
**From "Reporter" to "Fixer"**: Shift the value proposition from simply identifying legal risks to actively managing and resolving them through context-aware AI and automation.

---

## Strategic Pillars

### 1. Context-Aware Legal Engine ("The Smart Filter")
**Problem:** Traditional scanners flag every copyleft license, even if it's only used in a test environment or internally, causing "alert fatigue."
**Solution:** Usage-Based Risk Analysis.

*   **Scope Awareness:** Automatically categorize risks based on dependency scope (e.g., `test`, `provided`, `compile`, `runtime`).
    *   *Example:* "GPL-3.0 in `test` scope → **IGNORED** (Safe)."
    *   *Example:* "GPL-3.0 in `runtime` scope → **CRITICAL** (Violation)."
*   **Linkage Analysis:** Detect if a library is statically or dynamically linked (where language support exists, e.g., C/C++, Go).
    *   *Example:* "LGPL-2.1 (Dynamic Linking) → **WARNING** (Compliance Required)."
    *   *Example:* "LGPL-2.1 (Static Linking) → **ERROR** (Viral Risk)."

### 2. Automated Remediation ("The Auto-Fixer")
**Problem:** Developers are told a library is "bad," but they have to manually research alternatives.
**Solution:** Proactive Dependency Resolution.

*   **Version Hopping:** If a version violates policy (e.g., "GPL"), query the package repository (Maven Central, NPM) to check if other versions of the same package use a permissive license (e.g., "MIT").
*   **Auto-PR Generation:** Automatically generate a Pull Request/Patch:
    > "Bumped `library-x` from v1.2 (GPL) to v1.3 (MIT) to resolve policy violation."

### 3. Interactive Curation Memory ("The Human-in-the-Loop")
**Problem:** Lawyers and compliance officers don't trust "black box" AI decisions completely. They need verification.
**Solution:** A persistent "Compliance Memory."

*   **Interactive Mode:** A CLI wizard (`ortoped curate`) that presents AI findings for human confirmation.
    *   *AI:* "I am 85% confident this is MIT based on this text snippet..."
    *   *Human:* "Confirm."
*   **Persistent Storage:** Save these decisions to a local `.ort.yml` or a shared curation database.
*   **Learning:** Future scans respect these manual decisions, preventing repetitive work.

### 4. Holistic Project Health ("The Zombie Detector")
**Problem:** A library might have a safe license (MIT) but be unmaintained ("abandonware"), posing a security risk.
**Solution:** Correlate License Risk with Project Vitality.

*   **Abandonware Detection:** Flag packages that haven't been updated in X years.
*   **Maintainer Health:** Check for repository archival status or lack of recent commits.
    *   *Report:* "Warning: `lib-legacy` is MIT (Safe) but hasn't been updated since 2018 (High Security Risk)."

---

## Implementation Roadmap

### Phase 1: Intelligence (Current)
- [x] AI-based License Resolution
- [x] Basic Policy Enforcement
- [x] SBOM Generation

### Phase 2: Context (Next Steps)
- [ ] **Scope Filtering:** Enhance `ScanResult` to include dependency scopes.
- [ ] **Curation Persistence:** Implement reading/writing to `.ort.yml` curation files.
- [ ] **Interactive CLI:** Add `ortoped curate` command.

### Phase 3: Remediation (Future)
- [ ] **Repository Querying:** Add clients for Maven/NPM to fetch version metadata.
- [ ] **Smart Suggestions:** Logic to compare licenses across versions.
- [ ] **Git Integration:** Ability to create branches and commit `build.gradle` / `package.json` updates.

### Phase 4: Holistic Health (Long Term)
- [ ] **Metadata Analysis:** Fetch "Last Updated" dates for dependencies.
- [ ] **Risk Scoring:** Create a composite score (License Risk + Security Risk + Maintenance Risk).
