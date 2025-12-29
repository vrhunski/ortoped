# OrtoPed - AI-Enhanced ORT Scanner

OrtoPed is an intelligent wrapper around the OSS Review Toolkit (ORT) that leverages AI to automatically resolve unidentified software licenses, significantly reducing manual curation effort.

## 📚 Documentation

- **[Quick Start Guide](docs/QUICKSTART.md)** - Get started in 5 minutes
- **[Architecture Guide](docs/ARCHITECTURE.md)** - System architecture and design
- **[Policy Evaluation](docs/POLICY-EVALUATION.md)** - License policy configuration and enforcement
- **[Production Deployment](docs/PHASE6-PRODUCTION.md)** - Docker, CI/CD, and production guide
- **[POC Summary](docs/POC-SUMMARY.md)** - Detailed proof-of-concept documentation

## Features

- **Automated Dependency Scanning**: Analyzes projects across 27+ package managers (Maven, Gradle, npm, pip, Cargo, etc.)
- **AI-Powered License Resolution**: Uses Claude AI to automatically identify licenses that ORT cannot resolve
- **SBOM Generation**: Export scan results to industry-standard formats (CycloneDX, SPDX)
- **Policy Evaluation**: Enforce license compliance with customizable YAML-based policies
- **Source Code Scanning**: Extract actual license text from package source code
- **Docker Support**: Production-ready multi-stage Docker images for easy deployment
- **GitHub Action**: Reusable action for seamless CI/CD integration
- **JSON Report Generation**: Generates comprehensive, machine-readable reports
- **Performance Optimized**: Supports caching and parallel AI processing
- **Easy Integration**: Simple CLI interface for standalone use or CI/CD integration

## The Problem

ORT is excellent at analyzing dependencies, but sometimes it cannot determine licenses and returns `NOASSERTION`. This requires manual curation, which is:
- Time-consuming
- Requires license expertise
- Delays compliance reviews

## The Solution

OrtoPed automatically:
1. Runs ORT analysis to detect dependencies and licenses
2. Identifies unresolved licenses (NOASSERTION cases)
3. Uses Claude AI to analyze license text and suggest correct SPDX identifiers
4. Generates enhanced reports with AI suggestions and confidence levels

## Architecture

```
┌─────────────────────────────────────────────────────┐
│              OrtoPed CLI/API                        │
├─────────────────────────────────────────────────────┤
│          Scan Orchestrator                          │
│   ┌───────────────┐         ┌─────────────────┐   │
│   │  ORT Wrapper  │         │ License Resolver│   │
│   │               │────────▶│   (Claude AI)   │   │
│   │  - Analyzer   │         │                 │   │
│   │  - Scanner    │         │  - SPDX Match   │   │
│   └───────────────┘         │  - Confidence   │   │
│                              └─────────────────┘   │
├─────────────────────────────────────────────────────┤
│          Report Generator (JSON)                    │
└─────────────────────────────────────────────────────┘
```

## Requirements

- **Java 21** (required by ORT - **Note: Java 22+ may have compatibility issues**)
- **ANTHROPIC_API_KEY** environment variable (for AI license resolution)

### Java Version Note

OrtoPed requires Java 21. If you're using Java 22 or higher, you may encounter build failures. To check your Java version:

```bash
java -version
```

If you have Java 25 or newer, please install Java 21 from:
- [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21)
- [Oracle JDK 21](https://www.oracle.com/java/technologies/downloads/#java21)

On macOS, you can use SDKMAN to manage Java versions:
```bash
# Install SDKMAN
curl -s "https://get.sdkman.io" | bash

# Install Java 21
sdk install java 21.0.1-tem

# Use Java 21 for this project
sdk use java 21.0.1-tem
```

## Installation

### Build from Source

```bash
git clone https://github.com/yourusername/ortoped.git
cd ortoped

# Build the project
./gradlew build

# Create distribution
./gradlew installDist

# The executable will be in build/install/ortoped/bin/
```

### Using Gradle Wrapper

```bash
./gradlew run --args="scan -p /path/to/project"
```

## Usage

### Basic Scan (Local Directory)

```bash
ortoped scan -p /path/to/project
```

### Scan Remote Repository

```bash
# Scan any Git repository directly
ortoped scan -p https://github.com/user/repo.git

# Scan specific branch
ortoped scan -p https://github.com/user/repo.git --branch develop

# Scan specific tag or commit
ortoped scan -p https://github.com/user/repo.git --tag v1.0.0
ortoped scan -p https://github.com/user/repo.git --commit abc123def
```

### Custom Output File

```bash
ortoped scan -p /path/to/project -o report.json
```

### Disable AI Enhancement

```bash
ortoped scan -p /path/to/project --no-enable-ai
```

### Sequential AI Processing (for rate limiting)

```bash
ortoped scan -p /path/to/project --no-parallel-ai
```

### All Options

```bash
ortoped scan [OPTIONS]

Options:
  -p, --project PATH       Project directory or Git repository URL to scan (default: current dir)
  -o, --output FILE        Output file for JSON report (default: ortoped-report.json)
  --enable-ai              Enable AI-powered license resolution (default: true)
  --parallel-ai            Run AI license resolution in parallel (default: true)
  --source-scan            Enable source code scanning to extract license text (default: false)
  --console                Also print report to console (default: true)

  # VCS Options (for remote repositories)
  --branch TEXT            Git branch to checkout
  --tag TEXT               Git tag to checkout
  --commit TEXT            Git commit hash to checkout
  --keep-clone             Keep cloned repository after scan (for debugging)

  -h, --help               Show this message and exit
```

## Configuration

### Environment Variables

```bash
# Required for AI license resolution
export ANTHROPIC_API_KEY="your-api-key-here"

# Optional: Adjust ORT behavior
export ORT_CONFIG_DIR="$HOME/.ort/config"
```

## Output Format

OrtoPed generates a JSON report with the following structure:

```json
{
  "projectName": "my-project",
  "projectVersion": "1.0.0",
  "scanDate": "2025-12-28T12:00:00Z",
  "aiEnhanced": true,
  "summary": {
    "totalDependencies": 150,
    "resolvedLicenses": 145,
    "unresolvedLicenses": 5,
    "aiResolvedLicenses": 4,
    "licenseDistribution": {
      "MIT": 80,
      "Apache-2.0": 45,
      "GPL-3.0": 10
    }
  },
  "dependencies": [
    {
      "id": "Maven:com.example:library:1.0.0",
      "name": "library",
      "version": "1.0.0",
      "declaredLicenses": [],
      "detectedLicenses": [],
      "concludedLicense": null,
      "isResolved": false,
      "aiSuggestion": {
        "suggestedLicense": "MIT License",
        "confidence": "HIGH",
        "reasoning": "License text matches MIT template with 99% similarity",
        "spdxId": "MIT",
        "alternatives": ["ISC", "BSD-2-Clause"]
      }
    }
  ],
  "unresolvedLicenses": [
    {
      "dependencyId": "Maven:com.example:library:1.0.0",
      "dependencyName": "library",
      "reason": "License could not be determined by ORT"
    }
  ]
}
```

## AI License Resolution

### How It Works

1. **Extract Context**: For each unresolved license, OrtoPed collects:
   - Dependency name and version
   - Source URL
   - License text (if available)

2. **AI Analysis**: Claude AI analyzes the context and provides:
   - Suggested SPDX license identifier
   - Confidence level (HIGH, MEDIUM, LOW)
   - Reasoning for the suggestion
   - Alternative licenses if uncertain

3. **Enhancement**: Results are added to the JSON report for review

### Confidence Levels

- **HIGH**: AI is very confident (>90% match)
- **MEDIUM**: Likely correct but manual review recommended
- **LOW**: Multiple possibilities, requires expert review

## SBOM Generation

OrtoPed can export scan results to industry-standard Software Bill of Materials (SBOM) formats.

### Supported Formats

- **CycloneDX**: JSON and XML (version 1.5)
- **SPDX**: JSON (version 2.3)

### Usage

```bash
# Generate CycloneDX JSON (default)
ortoped sbom -i ortoped-report.json -o project.cdx.json

# Generate SPDX JSON
ortoped sbom -i ortoped-report.json -f spdx-json -o project.spdx.json

# Generate CycloneDX XML
ortoped sbom -i ortoped-report.json -f cyclonedx-xml -o project.cdx.xml

# Exclude AI suggestions from SBOM
ortoped sbom -i ortoped-report.json --no-ai -o project.cdx.json
```

### AI-Enhanced SBOMs

AI license suggestions are embedded in the SBOM output:
- **CycloneDX**: Custom properties (`ortoped:ai:*`)
- **SPDX**: Annotations with type REVIEW

### Full Pipeline Example

```bash
# Scan project and generate SBOM in one workflow
ortoped scan -p /path/to/project -o scan-report.json
ortoped sbom -i scan-report.json -o project.cdx.json
```

## Policy Evaluation

Enforce license compliance policies with customizable YAML-based rules.

### Features

- **License Categories**: Group licenses (permissive, copyleft, commercial, unknown)
- **Flexible Rules**: Category-based, allowlist, or denylist matching
- **Scope Filtering**: Different rules for production vs dev dependencies
- **AI Suggestions**: Get AI-powered fix recommendations for violations
- **CI/CD Ready**: Fail builds on policy violations with `--strict` mode

### Quick Start

```bash
# Evaluate with default policy (flags unknown licenses)
ortoped policy -i ortoped-report.json

# Use custom policy file
ortoped policy -i ortoped-report.json -p enterprise-policy.yaml

# Strict mode (fail on warnings)
ortoped policy -i ortoped-report.json --strict
```

### Example Policy File

```yaml
version: "1.0"
name: "Enterprise License Policy"

categories:
  permissive:
    licenses: ["MIT", "Apache-2.0", "BSD-2-Clause", "ISC"]
  copyleft:
    licenses: ["GPL-3.0-only", "AGPL-3.0-only"]
  unknown:
    licenses: ["NOASSERTION", "Unknown"]

rules:
  - id: "no-copyleft"
    severity: "ERROR"
    category: "copyleft"
    scopes: ["compile", "runtime"]
    action: "DENY"
    message: "Copyleft license '{{license}}' not allowed in production"

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

See [Policy Evaluation Guide](docs/POLICY-EVALUATION.md) for detailed documentation.

### Full Compliance Pipeline

```bash
# Scan → Policy Check → SBOM Generation
ortoped scan -p . -o scan.json
ortoped policy -i scan.json -p policy.yaml --strict
ortoped sbom -i scan.json -o project.cdx.json
```

## Performance Optimization

### Caching

OrtoPed leverages ORT's built-in caching to avoid re-analyzing unchanged dependencies:

```bash
# Enable caching (default)
ortoped scan --enable-caching

# Disable caching for fresh scan
ortoped scan --no-enable-caching
```

### Parallel AI Processing

For projects with many unresolved licenses, parallel processing significantly speeds up AI resolution:

```bash
# Parallel processing (default, faster)
ortoped scan --parallel-ai

# Sequential processing (for API rate limits)
ortoped scan --no-parallel-ai
```

## CI/CD Integration

### GitHub Actions

```yaml
name: License Compliance Scan

on: [push, pull_request]

jobs:
  license-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Java 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Run OrtoPed Scan
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
        run: |
          ./gradlew installDist
          ./build/install/ortoped/bin/ortoped scan -o license-report.json

      - name: Upload Report
        uses: actions/upload-artifact@v4
        with:
          name: license-report
          path: license-report.json
```

### GitLab CI

```yaml
license-scan:
  stage: test
  image: eclipse-temurin:21-jdk
  script:
    - ./gradlew installDist
    - ./build/install/ortoped/bin/ortoped scan -o license-report.json
  artifacts:
    paths:
      - license-report.json
    expire_in: 30 days
  variables:
    ANTHROPIC_API_KEY: $ANTHROPIC_API_KEY
```

## Development

### Project Structure

```
ortoped/
├── src/main/kotlin/com/ortoped/
│   ├── ai/
│   │   └── LicenseResolver.kt      # AI-powered license resolution
│   ├── model/
│   │   └── ScanResult.kt           # Data models
│   ├── report/
│   │   └── ReportGenerator.kt      # JSON/console report generation
│   ├── scanner/
│   │   ├── OrtScannerWrapper.kt    # ORT integration
│   │   └── ScanOrchestrator.kt     # Workflow orchestration
│   └── Main.kt                      # CLI entry point
├── build.gradle.kts                 # Build configuration
└── README.md
```

### Building

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Create distribution
./gradlew installDist
```

## Roadmap

- [x] Scanner integration (ScanCode source code scanning)
- [x] SBOM generation (SPDX, CycloneDX)
- [x] Policy evaluation and compliance rules
- [x] Docker image (Phase 6 - Complete)
- [x] GitHub Action (Phase 6 - Complete)
- [x] CI/CD workflows (Phase 6 - Complete)
- [x] Comprehensive test suite (Phase 6 - Complete)
- [ ] Web dashboard for visualization
- [ ] REST API
- [ ] Vulnerability scanning integration
- [ ] Multi-architecture Docker images (ARM64)

## License

MIT License - see LICENSE file for details

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.

## Acknowledgments

- Built on top of [OSS Review Toolkit (ORT)](https://github.com/oss-review-toolkit/ort)
- Powered by [Claude AI](https://www.anthropic.com/claude)