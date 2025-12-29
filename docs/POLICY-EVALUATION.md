# Policy Evaluation Guide

## Table of Contents

1. [Overview](#overview)
2. [Quick Start](#quick-start)
3. [Policy File Format](#policy-file-format)
4. [License Categories](#license-categories)
5. [Policy Rules](#policy-rules)
6. [Rule Evaluation](#rule-evaluation)
7. [AI Suggestions](#ai-suggestions)
8. [Settings and Configuration](#settings-and-configuration)
9. [CLI Usage](#cli-usage)
10. [Example Policies](#example-policies)
11. [CI/CD Integration](#cicd-integration)
12. [Best Practices](#best-practices)
13. [Troubleshooting](#troubleshooting)

---

## Overview

OrtoPed's policy evaluation engine enables organizations to enforce license compliance rules by defining policies in YAML format. The engine evaluates scan results against these policies and optionally uses AI to suggest fixes for violations.

### Key Features

- **YAML-based configuration**: Human-readable, version-controllable policy files
- **License categorization**: Group licenses by type (permissive, copyleft, commercial, etc.)
- **Flexible rule matching**: Category-based, allowlist, denylist, or scope-specific rules
- **Severity levels**: ERROR, WARNING, or INFO
- **AI-powered fix suggestions**: Get recommendations for resolving violations
- **Exemption support**: Skip specific dependencies with documented reasons
- **CI/CD ready**: Fail builds on policy violations with `--strict` mode

### Workflow

```
┌─────────────┐
│ Scan Result │
│   (JSON)    │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Policy    │
│   (YAML)    │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  Evaluator  │
│   Engine    │
└──────┬──────┘
       │
       ▼
┌─────────────┐      ┌─────────────┐
│ Violations  │─────▶│  AI Advisor │
│   Detected  │      │ (optional)  │
└──────┬──────┘      └──────┬──────┘
       │                    │
       └────────┬───────────┘
                ▼
       ┌─────────────────┐
       │  Policy Report  │
       │  (JSON/Console) │
       └─────────────────┘
```

---

## Quick Start

### 1. Basic Evaluation

Use the default policy to flag unknown licenses:

```bash
ortoped policy -i ortoped-report.json
```

### 2. Custom Policy

Create a policy file `my-policy.yaml`:

```yaml
version: "1.0"
name: "My License Policy"

categories:
  permissive:
    licenses: ["MIT", "Apache-2.0", "BSD-2-Clause"]
  copyleft:
    licenses: ["GPL-3.0-only", "AGPL-3.0-only"]
  unknown:
    licenses: ["NOASSERTION", "Unknown"]

rules:
  - id: "no-copyleft"
    severity: "ERROR"
    category: "copyleft"
    action: "DENY"
    message: "Copyleft licenses not allowed"

  - id: "no-unknown"
    severity: "ERROR"
    category: "unknown"
    action: "DENY"
    message: "All licenses must be identified"

settings:
  failOn:
    errors: true
    warnings: false
```

Evaluate with your policy:

```bash
ortoped policy -i ortoped-report.json -p my-policy.yaml
```

### 3. Strict Mode

Fail on both errors and warnings:

```bash
ortoped policy -i ortoped-report.json -p my-policy.yaml --strict
```

### 4. Full Compliance Pipeline

```bash
# Scan → Policy Check → SBOM Generation
ortoped scan -p /path/to/project -o scan.json
ortoped policy -i scan.json -p policy.yaml --strict
ortoped sbom -i scan.json -o project.cdx.json
```

---

## Policy File Format

### Structure

```yaml
version: "1.0"              # Policy format version (required)
name: "Policy Name"         # Human-readable name (required)
description: "Description"  # Optional description

categories:                 # License category definitions
  category-name:
    description: "..."
    licenses: [...]

rules:                      # Policy rules (evaluated in order)
  - id: "rule-id"
    name: "Rule Name"
    severity: "ERROR|WARNING|INFO"
    # ... rule configuration

settings:                   # Global settings
  aiSuggestions: {...}
  failOn: {...}
  exemptions: [...]
```

### Version

**Required**: Yes
**Type**: String
**Current version**: `"1.0"`

Specifies the policy file format version. Future versions may introduce new features.

### Name

**Required**: Yes
**Type**: String

Human-readable policy name displayed in reports.

### Description

**Required**: No
**Type**: String

Optional detailed description of the policy's purpose.

---

## License Categories

Categories group SPDX license identifiers for easier rule definition.

### Defining Categories

```yaml
categories:
  permissive:
    description: "Licenses with minimal restrictions"
    licenses:
      - "MIT"
      - "Apache-2.0"
      - "BSD-2-Clause"
      - "BSD-3-Clause"
      - "ISC"
      - "Unlicense"
      - "0BSD"

  copyleft:
    description: "Strong copyleft licenses"
    licenses:
      - "GPL-2.0-only"
      - "GPL-2.0-or-later"
      - "GPL-3.0-only"
      - "GPL-3.0-or-later"
      - "AGPL-3.0-only"
      - "AGPL-3.0-or-later"

  copyleft-limited:
    description: "Weak copyleft (library-scoped)"
    licenses:
      - "LGPL-2.0-only"
      - "LGPL-2.1-only"
      - "LGPL-3.0-only"
      - "MPL-2.0"
      - "EPL-1.0"
      - "EPL-2.0"
      - "CDDL-1.0"

  commercial:
    description: "Commercial/proprietary licenses"
    licenses:
      - "Proprietary"
      - "Commercial"
      - "BUSL-1.1"

  public-domain:
    description: "Public domain dedications"
    licenses:
      - "CC0-1.0"
      - "Unlicense"
      - "WTFPL"

  unknown:
    description: "Unresolved licenses"
    licenses:
      - "NOASSERTION"
      - "Unknown"
```

### Category Properties

| Property | Required | Description |
|----------|----------|-------------|
| `description` | No | Human-readable description |
| `licenses` | Yes | List of SPDX license identifiers |

### Built-in Categories (Default Policy)

If you don't specify a policy file, OrtoPed uses these default categories:

- **permissive**: MIT, Apache-2.0, BSD variants, ISC, Unlicense, 0BSD, CC0-1.0
- **copyleft**: GPL-2.0, GPL-3.0, AGPL-3.0 (all variants)
- **copyleft-limited**: LGPL variants, MPL-2.0, EPL variants
- **unknown**: NOASSERTION, Unknown

---

## Policy Rules

Rules define what actions to take for specific licenses or categories.

### Rule Structure

```yaml
rules:
  - id: "unique-rule-id"
    name: "Human Readable Name"
    description: "What this rule does"
    severity: "ERROR|WARNING|INFO"
    enabled: true|false

    # Matching (choose ONE):
    category: "category-name"        # Match by category
    allowlist: ["MIT", "Apache-2.0"] # Explicit allow list
    denylist: ["GPL-3.0-only"]       # Explicit deny list

    # Optional scope filtering
    scopes:
      - "compile"
      - "runtime"

    # Action to take
    action: "DENY|REVIEW|ALLOW"

    # Custom message (supports placeholders)
    message: "License '{{license}}' in {{dependency}}"
```

### Rule Properties

#### id
**Required**: Yes
**Type**: String
**Unique**: Yes

Unique identifier for the rule. Used in violation reports.

```yaml
id: "no-copyleft-prod"
```

#### name
**Required**: No
**Type**: String

Human-readable rule name.

```yaml
name: "No Copyleft in Production"
```

#### description
**Required**: No
**Type**: String

Detailed explanation of the rule's purpose.

```yaml
description: "Strong copyleft licenses are prohibited in production dependencies"
```

#### severity
**Required**: Yes
**Type**: Enum (`ERROR`, `WARNING`, `INFO`)

Determines the severity level of violations:

- **ERROR**: Critical issues that should fail builds
- **WARNING**: Issues requiring review but not blocking
- **INFO**: Informational notices

```yaml
severity: "ERROR"
```

#### enabled
**Required**: No
**Type**: Boolean
**Default**: `true`

Enable or disable the rule without removing it.

```yaml
enabled: false  # Temporarily disable
```

#### Matching: category
**Required**: No (mutually exclusive with allowlist/denylist)
**Type**: String

Match dependencies with licenses in the specified category.

```yaml
category: "copyleft"
```

#### Matching: allowlist
**Required**: No (mutually exclusive with category/denylist)
**Type**: List of strings

Explicitly allowed licenses. Dependencies with these licenses will trigger the rule.

```yaml
allowlist:
  - "MIT"
  - "Apache-2.0"
  - "BSD-2-Clause"
```

#### Matching: denylist
**Required**: No (mutually exclusive with category/allowlist)
**Type**: List of strings

Explicitly denied licenses. Dependencies with these licenses will trigger the rule.

```yaml
denylist:
  - "GPL-3.0-only"
  - "AGPL-3.0-only"
  - "SSPL-1.0"
```

#### scopes
**Required**: No
**Type**: List of strings
**Default**: Empty (all scopes)

Limit rule to specific dependency scopes. Common scope values:

- `compile` - Compile-time dependencies
- `runtime` - Runtime dependencies
- `dependencies` - Production dependencies (npm/Maven)
- `test` - Test dependencies
- `dev` - Development dependencies
- `devDependencies` - Dev dependencies (npm)
- `provided` - Provided dependencies (Maven)

```yaml
scopes:
  - "compile"
  - "runtime"
```

**Empty scopes = all scopes**:
```yaml
scopes: []  # Matches all scopes
```

#### action
**Required**: Yes
**Type**: Enum (`DENY`, `REVIEW`, `ALLOW`)

Action to take when the rule matches:

- **DENY**: Create a violation (respects severity)
- **REVIEW**: Flag for manual review (typically WARNING)
- **ALLOW**: Explicitly allow (use with allowlist for permissive rules)

```yaml
action: "DENY"
```

#### message
**Required**: No
**Type**: String
**Default**: Auto-generated message

Custom violation message. Supports placeholders:

- `{{license}}` - License identifier
- `{{dependency}}` - Dependency name
- `{{version}}` - Dependency version

```yaml
message: "License '{{license}}' found in {{dependency}} v{{version}} is prohibited"
```

### Rule Evaluation Order

Rules are evaluated **in the order they appear** in the policy file. This allows you to:

1. Define specific exceptions first
2. Apply broad rules later

**Example**:
```yaml
rules:
  # Exception: Allow LGPL in dev dependencies
  - id: "dev-lgpl-allowed"
    severity: "INFO"
    category: "copyleft-limited"
    scopes: ["test", "dev"]
    action: "ALLOW"

  # General rule: Block all copyleft
  - id: "no-copyleft"
    severity: "ERROR"
    category: "copyleft-limited"
    action: "DENY"
```

---

## Rule Evaluation

### License Resolution Priority

For each dependency, OrtoPed determines the "effective license" in this order:

1. **Concluded license** (manually curated in ORT)
2. **AI suggestion** (if confidence is HIGH and `acceptHighConfidence: true`)
3. **Declared license** (from package metadata)
4. **Detected license** (from source scanning)
5. **NOASSERTION** (if nothing found)

### Matching Logic

For each dependency:

1. **Check exemptions** - Skip if dependency matches an exemption pattern
2. **Determine category** - Classify the effective license
3. **Evaluate each rule** - In order, check if rule matches:
   - Does the license match the rule's category/allowlist/denylist?
   - Does the dependency scope match the rule's scopes?
4. **Record violation** - If match and action is DENY/REVIEW

### Example Evaluation

**Dependency**: `Maven:com.example:lib:1.0.0`
**License**: `GPL-3.0-only`
**Scope**: `compile`

**Policy**:
```yaml
categories:
  copyleft:
    licenses: ["GPL-3.0-only"]

rules:
  - id: "no-copyleft"
    severity: "ERROR"
    category: "copyleft"
    scopes: ["compile", "runtime"]
    action: "DENY"
    message: "Copyleft not allowed in production"
```

**Result**: **VIOLATION**
- License `GPL-3.0-only` is in category `copyleft`
- Scope `compile` matches rule scopes
- Action is `DENY` → Violation recorded

---

## AI Suggestions

When violations are detected, OrtoPed can use Claude AI to suggest fixes.

### Enabling AI Suggestions

```bash
# AI suggestions enabled by default
ortoped policy -i scan.json -p policy.yaml

# Disable AI suggestions
ortoped policy -i scan.json -p policy.yaml --no-enable-ai
```

### AI Suggestion Format

For each violation, the AI provides:

```json
{
  "suggestion": "Replace with alternative-lib:2.0.0 which uses MIT license",
  "alternativeDependencies": [
    "alternative-lib:2.0.0",
    "another-option:1.5.0"
  ],
  "reasoning": "The current dependency uses GPL-3.0 which conflicts with your policy. The suggested alternatives provide similar functionality with permissive licenses.",
  "confidence": "HIGH"
}
```

### Confidence Levels

- **HIGH**: AI is very confident in the suggestion
- **MEDIUM**: Suggestion is likely correct but review recommended
- **LOW**: Multiple possibilities, requires expert review

### Example Output

```
[ERROR] com.example:gpl-library:1.0.0
  License: GPL-3.0-only (copyleft)
  Rule: No Strong Copyleft
  Message: Copyleft license 'GPL-3.0-only' not allowed in production

  AI Suggestion (HIGH confidence):
    Replace with mit-alternative:2.1.0 which uses MIT license

    Alternatives:
      - mit-alternative:2.1.0
      - apache-option:1.8.0

    Reasoning: The current dependency uses GPL-3.0 which requires
    derivative works to be open-sourced. The suggested alternatives
    provide equivalent functionality under permissive licenses.
```

---

## Settings and Configuration

### Global Settings Structure

```yaml
settings:
  aiSuggestions:
    acceptHighConfidence: true
    treatMediumAsWarning: true
    rejectLowConfidence: true

  failOn:
    errors: true
    warnings: false

  exemptions:
    - dependency: "Maven:com.internal:*"
      reason: "Internal library with custom agreement"
      approvedBy: "legal@company.com"
      approvedDate: "2025-01-15"
```

### AI Suggestions Settings

Controls how AI-suggested licenses are treated during evaluation.

```yaml
aiSuggestions:
  acceptHighConfidence: true   # Treat HIGH confidence AI licenses as resolved
  treatMediumAsWarning: true   # Downgrade MEDIUM confidence violations to WARNING
  rejectLowConfidence: true    # Treat LOW confidence as unknown
```

**Default behavior** (if not specified):
```yaml
aiSuggestions:
  acceptHighConfidence: true
  treatMediumAsWarning: true
  rejectLowConfidence: true
```

### Fail On Settings

Controls when the policy evaluation should fail (exit code 1).

```yaml
failOn:
  errors: true      # Fail if any ERROR violations found
  warnings: false   # Fail if any WARNING violations found
```

**Strict mode override**:
```bash
ortoped policy --strict  # Fails on both errors and warnings
```

### Exemptions

Exclude specific dependencies from policy evaluation.

#### Exemption Structure

```yaml
exemptions:
  - dependency: "Maven:com.internal:legacy-lib:*"
    reason: "Internal library with custom license agreement"
    approvedBy: "legal@company.com"
    approvedDate: "2025-01-15"

  - dependency: "npm:@company/*"
    reason: "Internal packages exempt from policy"
    approvedBy: "architect@company.com"
    approvedDate: "2025-01-10"
```

#### Pattern Matching

Exemptions support glob patterns:

- `*` - Matches any characters within a segment
- `**` - Matches any characters across segments

**Examples**:

| Pattern | Matches |
|---------|---------|
| `Maven:com.internal:*` | All artifacts in `com.internal` group |
| `npm:@company/*` | All packages in `@company` scope |
| `Maven:*:legacy-*:*` | All artifacts starting with `legacy-` |

#### Required Fields

| Field | Required | Description |
|-------|----------|-------------|
| `dependency` | Yes | Glob pattern for dependency ID |
| `reason` | Yes | Justification for exemption |
| `approvedBy` | No | Who approved the exemption |
| `approvedDate` | No | When the exemption was approved |

---

## CLI Usage

### Basic Command

```bash
ortoped policy -i <input-file> [OPTIONS]
```

### Options

| Option | Short | Description | Default |
|--------|-------|-------------|---------|
| `--input` | `-i` | Input JSON scan report (required) | - |
| `--policy` | `-p` | Policy YAML file | Default policy |
| `--output` | `-o` | Output file for policy report | `ortoped-policy-report.json` |
| `--format` | `-f` | Output format: `json`, `console`, `both` | `both` |
| `--strict` | - | Fail on warnings too | `false` |
| `--enable-ai` | - | Enable AI fix suggestions | `true` |
| `--no-console` | - | Suppress console output | `false` |

### Examples

#### Default Policy

```bash
ortoped policy -i ortoped-report.json
```

Uses built-in default policy that flags unknown licenses.

#### Custom Policy

```bash
ortoped policy -i ortoped-report.json -p enterprise-policy.yaml
```

#### JSON Output Only

```bash
ortoped policy -i ortoped-report.json -f json -o report.json
```

#### Strict Mode

```bash
ortoped policy -i ortoped-report.json --strict
```

Fails on both errors and warnings.

#### Without AI Suggestions

```bash
ortoped policy -i ortoped-report.json --no-enable-ai
```

Skips AI fix suggestions (faster).

#### Silent JSON Output

```bash
ortoped policy -i ortoped-report.json -f json --no-console
```

Only writes JSON file, no console output.

---

## Example Policies

### 1. Permissive Policy (Allow Most Licenses)

```yaml
version: "1.0"
name: "Permissive Open Source Policy"
description: "Allow most open source licenses, flag only unknown"

categories:
  unknown:
    licenses: ["NOASSERTION", "Unknown"]
  copyleft-strong:
    licenses: ["AGPL-3.0-only", "AGPL-3.0-or-later"]

rules:
  - id: "no-agpl"
    severity: "ERROR"
    category: "copyleft-strong"
    action: "DENY"
    message: "AGPL license not permitted"

  - id: "warn-unknown"
    severity: "WARNING"
    category: "unknown"
    action: "REVIEW"
    message: "License should be identified"

settings:
  failOn:
    errors: true
    warnings: false
```

### 2. Strict Enterprise Policy

```yaml
version: "1.0"
name: "Enterprise Strict Policy"
description: "Strict compliance for commercial products"

categories:
  permissive:
    licenses:
      - "MIT"
      - "Apache-2.0"
      - "BSD-2-Clause"
      - "BSD-3-Clause"

  copyleft:
    licenses:
      - "GPL-2.0-only"
      - "GPL-3.0-only"
      - "AGPL-3.0-only"
      - "LGPL-2.1-only"
      - "LGPL-3.0-only"

  commercial:
    licenses:
      - "Proprietary"
      - "Commercial"

  unknown:
    licenses:
      - "NOASSERTION"
      - "Unknown"

rules:
  # Block all copyleft
  - id: "no-copyleft"
    severity: "ERROR"
    category: "copyleft"
    action: "DENY"
    message: "Copyleft license '{{license}}' prohibited"

  # Block commercial licenses
  - id: "no-commercial"
    severity: "ERROR"
    category: "commercial"
    action: "DENY"
    message: "Commercial license not allowed"

  # Require all licenses identified
  - id: "no-unknown"
    severity: "ERROR"
    category: "unknown"
    action: "DENY"
    message: "License must be identified for {{dependency}}"

  # Only allow specific permissive licenses
  - id: "permissive-only"
    severity: "ERROR"
    allowlist:
      - "MIT"
      - "Apache-2.0"
      - "BSD-2-Clause"
      - "BSD-3-Clause"
    scopes: ["compile", "runtime"]
    action: "ALLOW"

settings:
  aiSuggestions:
    acceptHighConfidence: true
  failOn:
    errors: true
    warnings: true
  exemptions:
    - dependency: "Maven:com.internal:*"
      reason: "Internal libraries"
      approvedBy: "legal@company.com"
```

### 3. Dual License Policy (Different Rules for Prod vs Dev)

```yaml
version: "1.0"
name: "Dual Environment Policy"
description: "Strict for production, relaxed for development"

categories:
  permissive:
    licenses: ["MIT", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause"]
  copyleft:
    licenses: ["GPL-3.0-only", "AGPL-3.0-only"]
  copyleft-limited:
    licenses: ["LGPL-2.1-only", "LGPL-3.0-only", "MPL-2.0"]
  unknown:
    licenses: ["NOASSERTION", "Unknown"]

rules:
  # Production: Strict rules
  - id: "prod-no-copyleft"
    severity: "ERROR"
    category: "copyleft"
    scopes: ["compile", "runtime", "dependencies"]
    action: "DENY"
    message: "Copyleft not allowed in production"

  - id: "prod-warn-limited-copyleft"
    severity: "WARNING"
    category: "copyleft-limited"
    scopes: ["compile", "runtime"]
    action: "REVIEW"
    message: "Weak copyleft requires review"

  # Development: Relaxed rules
  - id: "dev-allow-copyleft"
    severity: "INFO"
    category: "copyleft"
    scopes: ["test", "dev", "devDependencies"]
    action: "ALLOW"

  # All scopes: No unknown
  - id: "no-unknown"
    severity: "ERROR"
    category: "unknown"
    action: "DENY"
    message: "All licenses must be identified"

settings:
  failOn:
    errors: true
    warnings: false
```

### 4. Healthcare/Finance (Highly Regulated)

```yaml
version: "1.0"
name: "Regulated Industry Policy"
description: "For healthcare, finance, or other regulated industries"

categories:
  approved:
    description: "Pre-approved licenses only"
    licenses:
      - "MIT"
      - "Apache-2.0"

  review-required:
    description: "Requires legal review"
    licenses:
      - "BSD-2-Clause"
      - "BSD-3-Clause"
      - "ISC"

  prohibited:
    description: "Never allowed"
    licenses:
      - "GPL-2.0-only"
      - "GPL-3.0-only"
      - "AGPL-3.0-only"
      - "SSPL-1.0"
      - "Proprietary"

  unknown:
    licenses: ["NOASSERTION", "Unknown"]

rules:
  # Only approved licenses in production
  - id: "approved-only"
    severity: "ERROR"
    category: "approved"
    scopes: ["compile", "runtime"]
    action: "ALLOW"

  # Flag licenses requiring review
  - id: "review-required-licenses"
    severity: "WARNING"
    category: "review-required"
    action: "REVIEW"
    message: "License '{{license}}' requires legal review"

  # Block prohibited licenses
  - id: "blocked-licenses"
    severity: "ERROR"
    category: "prohibited"
    action: "DENY"
    message: "License '{{license}}' is prohibited by policy"

  # No unknown licenses
  - id: "no-unknown"
    severity: "ERROR"
    category: "unknown"
    action: "DENY"
    message: "All licenses must be identified and approved"

settings:
  aiSuggestions:
    acceptHighConfidence: false  # Require manual review of all AI suggestions
    treatMediumAsWarning: true
    rejectLowConfidence: true
  failOn:
    errors: true
    warnings: true  # Warnings also fail in regulated environments
  exemptions: []  # No exemptions allowed
```

---

## CI/CD Integration

### GitHub Actions

```yaml
name: License Compliance

on: [push, pull_request]

jobs:
  license-check:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Setup Java 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Build OrtoPed
        run: ./gradlew installDist

      - name: Scan Dependencies
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
        run: |
          ./build/install/ortoped/bin/ortoped scan \
            -p . \
            -o scan-report.json

      - name: Evaluate Policy
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
        run: |
          ./build/install/ortoped/bin/ortoped policy \
            -i scan-report.json \
            -p .ortoped/policy.yaml \
            --strict \
            -o policy-report.json

      - name: Upload Reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: compliance-reports
          path: |
            scan-report.json
            policy-report.json

      - name: Comment PR
        if: failure() && github.event_name == 'pull_request'
        uses: actions/github-script@v7
        with:
          script: |
            const fs = require('fs');
            const report = JSON.parse(fs.readFileSync('policy-report.json'));
            const comment = `## License Compliance Failed

            ${report.summary.errorCount} errors, ${report.summary.warningCount} warnings

            See artifacts for full report.`;

            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: comment
            });
```

### GitLab CI

```yaml
stages:
  - scan
  - policy-check
  - report

variables:
  ORTOPED_BIN: "./build/install/ortoped/bin/ortoped"

license-scan:
  stage: scan
  image: eclipse-temurin:21-jdk
  script:
    - ./gradlew installDist
    - $ORTOPED_BIN scan -p . -o scan-report.json
  artifacts:
    paths:
      - scan-report.json
    expire_in: 30 days
  only:
    - merge_requests
    - main

policy-check:
  stage: policy-check
  image: eclipse-temurin:21-jdk
  dependencies:
    - license-scan
  script:
    - ./gradlew installDist
    - $ORTOPED_BIN policy -i scan-report.json -p policy.yaml --strict -o policy-report.json
  artifacts:
    paths:
      - policy-report.json
    expire_in: 30 days
    reports:
      junit: policy-report.json
  only:
    - merge_requests
    - main
  allow_failure: false
```

### Jenkins Pipeline

```groovy
pipeline {
    agent any

    environment {
        ANTHROPIC_API_KEY = credentials('anthropic-api-key')
    }

    stages {
        stage('Build OrtoPed') {
            steps {
                sh './gradlew installDist'
            }
        }

        stage('Scan Dependencies') {
            steps {
                sh '''
                    ./build/install/ortoped/bin/ortoped scan \
                        -p ${WORKSPACE} \
                        -o scan-report.json
                '''
            }
        }

        stage('Policy Evaluation') {
            steps {
                sh '''
                    ./build/install/ortoped/bin/ortoped policy \
                        -i scan-report.json \
                        -p jenkins/policy.yaml \
                        --strict \
                        -o policy-report.json
                '''
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: '*-report.json', allowEmptyArchive: false
        }
        failure {
            emailext(
                subject: "License Compliance Failed: ${env.JOB_NAME}",
                body: "Policy evaluation failed. Check attached reports.",
                attachmentsPattern: 'policy-report.json'
            )
        }
    }
}
```

---

## Best Practices

### 1. Version Control Your Policy

Store policy files in version control alongside your code:

```
project/
├── .ortoped/
│   └── policy.yaml
├── src/
└── pom.xml
```

### 2. Start Permissive, Then Tighten

Begin with a permissive policy that only flags unknown licenses:

```yaml
rules:
  - id: "warn-unknown"
    severity: "WARNING"
    category: "unknown"
    action: "REVIEW"
```

Gradually add stricter rules as you clean up your dependencies.

### 3. Use Meaningful Rule IDs

```yaml
# Good
- id: "prod-no-gpl"
- id: "require-apache-or-mit"
- id: "flag-unknown-licenses"

# Bad
- id: "rule1"
- id: "rule2"
```

### 4. Document Exemptions

Always provide clear reasons and approvals for exemptions:

```yaml
exemptions:
  - dependency: "Maven:com.legacy:old-lib:1.0"
    reason: "Legacy library scheduled for replacement in Q2 2025"
    approvedBy: "architect@company.com"
    approvedDate: "2025-01-15"
    expiryDate: "2025-06-30"
```

### 5. Separate Prod and Dev Rules

Use scope filtering to apply different rules:

```yaml
# Strict for production
- id: "prod-permissive-only"
  severity: "ERROR"
  category: "permissive"
  scopes: ["compile", "runtime"]
  action: "ALLOW"

# Relaxed for development
- id: "dev-allow-all"
  severity: "INFO"
  scopes: ["test", "dev"]
  action: "ALLOW"
```

### 6. Regular Policy Reviews

Review and update your policy quarterly:
- Remove expired exemptions
- Add newly approved licenses
- Tighten rules as dependencies are cleaned up

### 7. Use AI Suggestions in Strict Mode

Even in strict environments, AI suggestions can help:

```yaml
settings:
  aiSuggestions:
    acceptHighConfidence: false  # Don't auto-accept
    treatMediumAsWarning: true   # Still get suggestions
```

### 8. Test Policy Changes

Before applying a new policy to CI/CD:

```bash
# Test locally first
ortoped policy -i scan-report.json -p new-policy.yaml --no-enable-ai
```

### 9. Combine with SBOM Generation

```bash
# Full compliance workflow
ortoped scan -p . -o scan.json
ortoped policy -i scan.json -p policy.yaml --strict
ortoped sbom -i scan.json -o project.cdx.json
```

### 10. Monitor AI Confidence Trends

Track how often AI suggestions are needed:
- High count of MEDIUM/LOW → Improve dependency metadata
- Recurring unknown licenses → Add to category definitions

---

## Troubleshooting

### Policy File Not Loading

**Error**: `Policy file not found: /path/to/policy.yaml`

**Solution**: Check file path and permissions:
```bash
ls -la /path/to/policy.yaml
```

---

### YAML Syntax Errors

**Error**: `Invalid policy file: ... expected <key>, but got ...`

**Solution**: Validate YAML syntax:
```bash
# Use online YAML validator or
python3 -c "import yaml; yaml.safe_load(open('policy.yaml'))"
```

Common issues:
- Inconsistent indentation (use spaces, not tabs)
- Missing quotes around special characters
- Invalid list syntax

---

### No Violations Detected (But Expected Some)

**Check**:
1. Is the rule enabled?
   ```yaml
   enabled: true
   ```

2. Does the license match the category?
   ```yaml
   categories:
     copyleft:
       licenses: ["GPL-3.0-only"]  # Exact SPDX ID
   ```

3. Are scopes correct?
   ```yaml
   scopes: ["compile", "runtime"]  # Empty = all scopes
   ```

---

### Too Many Violations

**Solution**: Use staged rollout:

```yaml
# Phase 1: Warnings only
rules:
  - id: "copyleft-warning"
    severity: "WARNING"
    category: "copyleft"
    action: "REVIEW"

settings:
  failOn:
    warnings: false  # Don't fail yet

# Phase 2 (later): Errors
# severity: "ERROR"
# failOn:
#   warnings: true
```

---

### AI Suggestions Not Working

**Check**:
1. `ANTHROPIC_API_KEY` environment variable set?
   ```bash
   echo $ANTHROPIC_API_KEY
   ```

2. AI enabled in command?
   ```bash
   ortoped policy --enable-ai  # Not --no-enable-ai
   ```

3. API quota/rate limits?
   ```bash
   # Use sequential processing
   # (AI suggestions run per-violation, not per-scan)
   ```

---

### Exemptions Not Working

**Check pattern syntax**:

```yaml
# Correct
dependency: "Maven:com.example:*"        # Glob pattern
dependency: "npm:@company/*"

# Incorrect
dependency: "com.example:*"              # Missing package manager prefix
dependency: "Maven:com.example:**"       # Use *, not **
```

**Pattern matching**:
- Dependency ID format: `<type>:<namespace>:<name>:<version>`
- Example: `Maven:com.google.guava:guava:31.1-jre`

---

### Build Failing in CI/CD

**Debug locally**:

```bash
# Run exact CI command locally
ortoped scan -p . -o scan.json
ortoped policy -i scan.json -p policy.yaml --strict -o policy-report.json

# Check exit code
echo $?  # Non-zero = failure
```

**Common causes**:
1. New dependency introduced with violation
2. AI suggestions changed (confidence downgraded)
3. Policy file not in repository
4. API key not set in CI environment

---

### Performance Issues

**Large projects** (1000+ dependencies):

1. Disable AI suggestions for faster evaluation:
   ```bash
   ortoped policy --no-enable-ai
   ```

2. Use cached scan results:
   ```bash
   # Scan once
   ortoped scan -p . -o scan.json

   # Evaluate multiple times
   ortoped policy -i scan.json -p policy-v1.yaml
   ortoped policy -i scan.json -p policy-v2.yaml
   ```

---

## Advanced Topics

### Custom License Categories

Define domain-specific categories:

```yaml
categories:
  medical-approved:
    description: "FDA-approved licenses for medical devices"
    licenses:
      - "MIT"
      - "Apache-2.0"

  finance-prohibited:
    description: "Prohibited in financial systems"
    licenses:
      - "AGPL-3.0-only"
      - "SSPL-1.0"
```

### Multi-Policy Evaluation

Evaluate against multiple policies:

```bash
# Development policy
ortoped policy -i scan.json -p dev-policy.yaml -o dev-report.json

# Production policy
ortoped policy -i scan.json -p prod-policy.yaml -o prod-report.json --strict
```

### Policy Templates

Create reusable policy templates:

```yaml
# templates/base-policy.yaml
version: "1.0"
categories: &default-categories
  permissive:
    licenses: ["MIT", "Apache-2.0"]
  copyleft:
    licenses: ["GPL-3.0-only"]

# Extend in specific policies
# enterprise-policy.yaml
version: "1.0"
categories:
  <<: *default-categories
  custom:
    licenses: ["Custom-License"]
```

---

## Summary

OrtoPed's policy evaluation engine provides:

- ✅ YAML-based policy configuration
- ✅ Flexible license categorization
- ✅ Category, allowlist, and denylist rules
- ✅ Scope-based filtering (prod vs dev)
- ✅ AI-powered fix suggestions
- ✅ Exemption support with documentation
- ✅ CI/CD integration
- ✅ JSON and console reports

**Next Steps**:
1. Create your policy file
2. Run a test evaluation
3. Integrate into CI/CD
4. Monitor and refine over time

For more information:
- [Quick Start Guide](QUICKSTART.md)
- [Example Policies](../examples/)
- [GitHub Repository](https://github.com/yourusername/ortoped)
