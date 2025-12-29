# OrtoPed - Quick Start Guide

Get started with OrtoPed in 5 minutes!

## ✅ Current Status

OrtoPed is production-ready with all core features implemented:

✅ **Real ORT Integration** - Analyzes dependencies across 27+ package managers
✅ **AI-Powered License Resolution** - Automatically resolves unknown licenses
✅ **Source Code Scanning** - Extracts license text from source code (optional)
✅ **SBOM Generation** - Export to CycloneDX and SPDX formats
✅ **Policy Evaluation** - Enforce compliance rules with YAML-based policies
✅ **Remote Repository Scanning** - Scan Git repositories without cloning
✅ **JSON & Console Reports** - Multiple output formats
✅ **Parallel AI Processing** - Fast resolution of multiple licenses

## Prerequisites

- **Java 21** (required by ORT)
- **ANTHROPIC_API_KEY** environment variable (for AI license resolution)

Check your Java version:
```bash
java -version  # Should show 21.x
```

## Installation

```bash
# Clone the repository
git clone https://github.com/yourusername/ortoped.git
cd ortoped

# Build the project
./gradlew build

# Create distribution
./gradlew installDist

# The executable will be at:
# ./build/install/ortoped/bin/ortoped
```

## Quick Start: Scan Your First Project

### 1. Basic Scan (Local Project)

```bash
# Scan the current directory
./build/install/ortoped/bin/ortoped scan -p . -o scan-report.json

# Scan specific directory
./build/install/ortoped/bin/ortoped scan -p /path/to/project -o report.json
```

### 2. Scan Remote Repository

```bash
# Scan a GitHub repository
./build/install/ortoped/bin/ortoped scan -p https://github.com/user/repo.git

# Scan specific branch
./build/install/ortoped/bin/ortoped scan -p https://github.com/user/repo.git --branch develop

# Scan specific tag
./build/install/ortoped/bin/ortoped scan -p https://github.com/user/repo.git --tag v1.0.0
```

### 3. Enable AI License Resolution

Set your Claude API key to enable AI-powered license resolution:

```bash
export ANTHROPIC_API_KEY="your-api-key-here"

# Scan with AI enhancement (default)
./build/install/ortoped/bin/ortoped scan -p . -o scan-report.json
```

The AI will analyze unresolved licenses and provide:
- Suggested SPDX license identifiers
- Confidence levels (HIGH, MEDIUM, LOW)
- Reasoning for each suggestion
- Alternative licenses if uncertain

### 4. Generate SBOM (Software Bill of Materials)

```bash
# Generate CycloneDX JSON (default)
./build/install/ortoped/bin/ortoped sbom -i scan-report.json -o project.cdx.json

# Generate SPDX JSON
./build/install/ortoped/bin/ortoped sbom -i scan-report.json -f spdx-json -o project.spdx.json

# Generate CycloneDX XML
./build/install/ortoped/bin/ortoped sbom -i scan-report.json -f cyclonedx-xml -o project.cdx.xml
```

### 5. Evaluate License Compliance Policy

```bash
# Use default policy (flags unknown licenses)
./build/install/ortoped/bin/ortoped policy -i scan-report.json

# Use custom policy
./build/install/ortoped/bin/ortoped policy -i scan-report.json -p my-policy.yaml

# Strict mode (fail on warnings too)
./build/install/ortoped/bin/ortoped policy -i scan-report.json -p my-policy.yaml --strict
```

## Full Compliance Workflow

Combine all features for complete license compliance:

```bash
# 1. Scan project with AI enhancement
./build/install/ortoped/bin/ortoped scan -p . -o scan.json

# 2. Evaluate against compliance policy
./build/install/ortoped/bin/ortoped policy -i scan.json -p policy.yaml --strict

# 3. Generate SBOM for distribution
./build/install/ortoped/bin/ortoped sbom -i scan.json -o project.cdx.json
```

## Example Policy File

Create a simple policy file `policy.yaml`:

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
    scopes: ["compile", "runtime"]
    action: "DENY"
    message: "Copyleft licenses not allowed in production"

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

See [Policy Evaluation Guide](POLICY-EVALUATION.md) for detailed documentation.

## Output Examples

### Scan Report (JSON)

```json
{
  "projectName": "my-project",
  "totalDependencies": 150,
  "aiEnhanced": true,
  "summary": {
    "resolvedLicenses": 145,
    "unresolvedLicenses": 5,
    "aiResolvedLicenses": 4
  },
  "dependencies": [
    {
      "id": "Maven:com.example:lib:1.0.0",
      "declaredLicenses": [],
      "isResolved": false,
      "aiSuggestion": {
        "suggestedLicense": "MIT License",
        "spdxId": "MIT",
        "confidence": "HIGH",
        "reasoning": "License text matches MIT template"
      }
    }
  ]
}
```

### Console Output

```
================================================================================
ORTOPED SCAN REPORT
================================================================================

Project: my-project (1.0.0)
Scan Date: 2025-12-29

Summary:
  Total Dependencies:    150
  Resolved Licenses:     145
  Unresolved Licenses:   5
  AI-Resolved:           4 (80% success rate)

License Distribution:
  MIT:           80 (53%)
  Apache-2.0:    45 (30%)
  GPL-3.0:       10 (7%)
  BSD-2-Clause:  10 (7%)
```

## Performance

**Typical Performance (Medium Project ~200 deps):**
- ORT Analysis: 1-3 minutes
- AI Resolution (10 unresolved, parallel): ~15-30 seconds
- SBOM Generation: < 5 seconds
- Policy Evaluation: < 5 seconds

**Optimization Tips:**
```bash
# Parallel AI processing (default, faster)
ortoped scan -p . --parallel-ai

# Sequential processing (for API rate limits)
ortoped scan -p . --no-parallel-ai
```

## Advanced Features

### Source Code Scanning

Extract license text directly from source code (slower, more thorough):

```bash
ortoped scan -p . --source-scan -o detailed-scan.json
```

### Demo Mode

Test OrtoPed with mock data without scanning a real project:

```bash
ortoped scan --demo -o demo-report.json
```

## All CLI Commands

OrtoPed has four main commands:

### 1. `ortoped scan` - Analyze Dependencies

```bash
ortoped scan [OPTIONS]

Options:
  -p, --project PATH       Project directory or Git repository URL (default: .)
  -o, --output FILE        Output JSON file (default: ortoped-report.json)
  --enable-ai              Enable AI license resolution (default: true)
  --parallel-ai            Parallel AI processing (default: true)
  --source-scan            Extract license text from source code (default: false)
  --console                Print to console (default: true)
  --demo                   Use demo mode with mock data (default: false)

  # Remote repository options
  --branch TEXT            Git branch to checkout
  --tag TEXT               Git tag to checkout
  --commit TEXT            Git commit hash to checkout
  --keep-clone             Keep cloned repository after scan

  -h, --help               Show help
```

### 2. `ortoped sbom` - Generate SBOM

```bash
ortoped sbom [OPTIONS]

Options:
  -i, --input FILE         Input scan report JSON (required)
  -o, --output FILE        Output SBOM file (default: ortoped-sbom.cdx.json)
  -f, --format FORMAT      SBOM format:
                           - cyclonedx-json (default)
                           - cyclonedx-xml
                           - spdx-json
                           - spdx-tv
  --include-ai             Include AI suggestions (default: true)
  --no-ai                  Exclude AI suggestions
  -h, --help               Show help
```

### 3. `ortoped policy` - Evaluate Compliance

```bash
ortoped policy [OPTIONS]

Options:
  -i, --input FILE         Input scan report JSON (required)
  -p, --policy FILE        Policy YAML file (default: built-in policy)
  -o, --output FILE        Output policy report (default: ortoped-policy-report.json)
  -f, --format FORMAT      Output format: json, console, both (default: both)
  --strict                 Fail on warnings too
  --enable-ai              Get AI fix suggestions (default: true)
  --no-console             Suppress console output
  -h, --help               Show help
```

### 4. `ortoped version` - Show Version

```bash
ortoped version
```

## Next Steps

Now that you have OrtoPed running, here are some suggestions:

### 1. Create Your Policy File

Define your organization's license compliance rules:

```bash
# Copy the example policy
cp examples/default-policy.yaml .ortoped/policy.yaml

# Edit to match your requirements
# See docs/POLICY-EVALUATION.md for details
```

### 2. Integrate into CI/CD

Add OrtoPed to your build pipeline:

- [GitHub Actions Example](../README.md#github-actions)
- [GitLab CI Example](../README.md#gitlab-ci)
- Jenkins, Azure DevOps, etc.

### 3. Generate SBOMs for Compliance

Export your scan results to standard SBOM formats for:
- Customer requirements
- Security audits
- Vulnerability tracking
- Supply chain transparency

### 4. Explore Advanced Features

- **Source code scanning**: Extract license text from package source
- **Remote repository scanning**: Scan any Git repo without cloning
- **AI-enhanced reports**: Get fix suggestions for policy violations

### 5. Learn More

- [Full README](../README.md) - Comprehensive documentation
- [Policy Evaluation Guide](POLICY-EVALUATION.md) - Detailed policy documentation
- [POC Summary](POC-SUMMARY.md) - Technical proof-of-concept details

## Troubleshooting

### Java Version Issues

**Error**: Build fails with "unsupported class file version"

**Solution**: OrtoPed requires Java 21 (Java 22+ may have compatibility issues)

```bash
# Check your Java version
java -version  # Should show 21.x

# Install Java 21 (macOS with SDKMAN)
sdk install java 21.0.1-tem
sdk use java 21.0.1-tem
```

### No AI Suggestions

**Error**: AI suggestions not appearing in reports

**Solution**: Check your ANTHROPIC_API_KEY environment variable

```bash
# Verify API key is set
echo $ANTHROPIC_API_KEY  # Should not be empty

# Set API key
export ANTHROPIC_API_KEY="your-api-key-here"
```

### ORT Analysis Fails

**Error**: "Failed to analyze dependencies"

**Possible causes**:
- Missing build files (pom.xml, package.json, Cargo.toml, etc.)
- Network issues (downloading dependencies)
- Unsupported package manager

**Solution**: Check `.ort/ortoped.log` for detailed error messages

### Policy Evaluation Fails

**Error**: "Policy file not found" or YAML syntax errors

**Solution**:
```bash
# Verify policy file exists
ls -la my-policy.yaml

# Validate YAML syntax
python3 -c "import yaml; yaml.safe_load(open('my-policy.yaml'))"

# Use default policy if unsure
ortoped policy -i scan.json  # No -p flag
```

### Slow Performance

**Issue**: Scans taking too long

**Solutions**:
1. Disable source code scanning (faster):
   ```bash
   ortoped scan -p . --no-source-scan
   ```

2. Use sequential AI processing (if hitting rate limits):
   ```bash
   ortoped scan -p . --no-parallel-ai
   ```

3. Check network connectivity (ORT downloads package metadata)

### Remote Repository Clone Fails

**Error**: "Failed to clone repository"

**Solutions**:
- Verify repository URL is correct
- Check network/firewall settings
- For private repos, ensure SSH keys are configured
- Try HTTPS URL instead of SSH URL

## Getting Help

- **Documentation**: Check [README.md](../README.md) and [POLICY-EVALUATION.md](POLICY-EVALUATION.md)
- **Logs**: Review `.ort/ortoped.log` for detailed execution logs
- **Issues**: Report bugs at [GitHub Issues](https://github.com/yourusername/ortoped/issues)

## Summary

You now have a complete understanding of:
- ✅ Installing and building OrtoPed
- ✅ Scanning projects (local and remote)
- ✅ Using AI license resolution
- ✅ Generating SBOMs
- ✅ Evaluating compliance policies
- ✅ Integrating into CI/CD pipelines

**Ready to start scanning!** 🚀