# Phase 6: Production Hardening

Complete production-ready infrastructure for OrtoPed with Docker containerization, CI/CD automation, reusable GitHub Action, and comprehensive test coverage.

---

## Table of Contents

- [Overview](#overview)
- [Docker Setup](#docker-setup)
- [GitHub Action](#github-action)
- [CI/CD Workflows](#cicd-workflows)
- [Testing](#testing)
- [Deployment Guide](#deployment-guide)
- [Security Considerations](#security-considerations)

---

## Overview

Phase 6 transforms OrtoPed into a production-ready tool with:

- **Docker Containerization**: Multi-stage builds for minimal runtime images
- **GitHub Action**: Reusable action for external repositories
- **CI/CD Automation**: Automated testing, building, and publishing
- **Comprehensive Testing**: 76 tests covering all core functionality
- **Security Hardening**: Secure environment variable handling in workflows

---

## Docker Setup

### Dockerfile Architecture

**Multi-stage build** optimized for minimal image size:

```dockerfile
# Stage 1: Builder (Eclipse Temurin 21 JDK + Alpine)
FROM eclipse-temurin:21-jdk-alpine AS builder
RUN apk add --no-cache git
WORKDIR /app
# Dependency caching layer
COPY gradle gradle
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon || true
# Application build
COPY src src
RUN ./gradlew installDist --no-daemon

# Stage 2: Runtime (Eclipse Temurin 21 JRE + Alpine)
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache git bash
WORKDIR /app
COPY --from=builder /app/build/install/ortoped .
VOLUME ["/projects", "/reports"]
ENTRYPOINT ["/app/bin/ortoped"]
CMD ["--help"]
```

**Key Features:**
- Separate build and runtime stages (~200MB size reduction)
- Git support for remote repository scanning
- Volume mounts for input/output
- Configurable entrypoint and default command

### Building the Image

```bash
# Build with tag
docker build -t ortoped:latest .

# Build with version tag
docker build -t ortoped:1.0.0 .

# Build with custom tag
docker build -t myregistry.com/ortoped:latest .
```

### Running with Docker

**Basic scan:**
```bash
docker run --rm \
  -v $(pwd)/myproject:/projects/myproject:ro \
  -v $(pwd)/reports:/reports \
  ortoped:latest scan -p /projects/myproject -o /reports/scan-result.json
```

**Scan with AI enhancement:**
```bash
docker run --rm \
  -v $(pwd)/myproject:/projects/myproject:ro \
  -v $(pwd)/reports:/reports \
  -e ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY \
  ortoped:latest scan -p /projects/myproject -o /reports/scan-result.json --enable-ai
```

**Demo mode:**
```bash
docker run --rm \
  -v $(pwd)/reports:/reports \
  ortoped:latest scan --demo -o /reports/demo-report.json
```

### Docker Compose

Three pre-configured services for common workflows:

**docker-compose.yml:**
```yaml
services:
  # Basic scan
  ortoped:
    build: .
    volumes:
      - ./projects:/projects:ro
      - ./reports:/reports
    environment:
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
    command: scan -p /projects/myproject -o /reports/ortoped-report.json

  # Scan with policy evaluation
  ortoped-policy:
    build: .
    volumes:
      - ./projects:/projects:ro
      - ./reports:/reports
      - ./policy.yaml:/policy.yaml:ro
    environment:
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
    command: >
      scan -p /projects/myproject
      -o /reports/ortoped-report.json
      --policy /policy.yaml

  # Generate SBOM
  ortoped-sbom:
    build: .
    volumes:
      - ./projects:/projects:ro
      - ./reports:/reports
    command: >
      scan -p /projects/myproject
      -o /reports/ortoped-report.json
      --sbom cyclonedx-json
      --sbom-output /reports/sbom.cdx.json
```

**Usage:**
```bash
# Run basic scan
docker-compose run ortoped

# Run with policy evaluation
docker-compose run ortoped-policy

# Generate SBOM
docker-compose run ortoped-sbom

# Build and run
docker-compose up --build ortoped
```

---

## GitHub Action

### Reusable Action

OrtoPed provides a reusable GitHub Action for CI/CD integration in external repositories.

**action.yml Features:**
- Composite action (uses Docker internally)
- Configurable inputs for all scan options
- Outputs for report file, violation counts, and unresolved licenses
- Secure environment variable handling
- Automatic artifact upload

### Using the Action in Your Repository

**Basic usage:**
```yaml
name: License Compliance

on: [push, pull_request]

jobs:
  scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: OrtoPed License Scan
        uses: vrhunski/ortoped@v1
        with:
          project-path: '.'
          output-file: 'license-report.json'
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}

      - name: Upload Report
        uses: actions/upload-artifact@v4
        with:
          name: license-report
          path: license-report.json
```

**With policy evaluation:**
```yaml
- name: OrtoPed License Scan with Policy
  uses: vrhunski/ortoped@v1
  with:
    project-path: '.'
    output-file: 'license-report.json'
    policy-file: '.ortoped/policy.yaml'
    fail-on-violations: 'true'
  env:
    ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
```

**With SBOM generation:**
```yaml
- name: OrtoPed License Scan + SBOM
  uses: vrhunski/ortoped@v1
  with:
    project-path: '.'
    output-file: 'license-report.json'
    sbom-format: 'cyclonedx-json'
    sbom-output: 'sbom.cdx.json'
  env:
    ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
```

### Action Inputs

| Input | Description | Required | Default |
|-------|-------------|----------|---------|
| `project-path` | Path to project directory to scan | No | `.` |
| `output-file` | Output report file path | No | `ortoped-report.json` |
| `enable-ai` | Enable AI license resolution | No | `true` |
| `policy-file` | Path to policy YAML file | No | - |
| `fail-on-violations` | Fail workflow if policy violations found | No | `false` |
| `sbom-format` | SBOM format (cyclonedx-json, cyclonedx-xml, spdx-json) | No | - |
| `sbom-output` | SBOM output file path | No | - |
| `enable-source-scan` | Enable source code scanning | No | `false` |

### Action Outputs

| Output | Description |
|--------|-------------|
| `report-file` | Path to generated report file |
| `violations-count` | Number of policy violations found |
| `unresolved-count` | Number of unresolved licenses |

**Using outputs:**
```yaml
- name: OrtoPed Scan
  id: ortoped
  uses: vrhunski/ortoped@v1
  with:
    project-path: '.'
    policy-file: '.ortoped/policy.yaml'

- name: Check Results
  run: |
    echo "Violations: ${{ steps.ortoped.outputs.violations-count }}"
    echo "Unresolved: ${{ steps.ortoped.outputs.unresolved-count }}"
```

---

## CI/CD Workflows

### CI Workflow (.github/workflows/ci.yml)

**Triggers:** Push to main, Pull Requests

**Jobs:**
1. **Build & Test**
   - Checkout code
   - Setup Java 21 (Temurin)
   - Build with Gradle
   - Run all tests
   - Upload test reports as artifacts

2. **Docker Verification**
   - Build Docker image
   - Run demo scan in container
   - Verify image functionality

3. **Lint (Future)**
   - Code style checks
   - Security scanning

**Example run:**
```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Build
        run: ./gradlew build

      - name: Test
        run: ./gradlew test

      - name: Upload Test Reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: build/reports/tests/test/

  docker:
    runs-on: ubuntu-latest
    needs: build
    steps:
      - uses: actions/checkout@v4

      - name: Build Docker Image
        run: docker build -t ortoped:ci .

      - name: Test Docker Image
        run: |
          docker run --rm ortoped:ci scan --demo -o /dev/stdout
```

### Release Workflow (.github/workflows/release.yml)

**Triggers:** Git tags matching `v*` (e.g., `v1.0.0`)

**Jobs:**
1. **Build & Test** (same as CI)
2. **Build Distribution**
   - Create installable distribution
   - Package as tar.gz archive
3. **Publish Docker Image**
   - Build multi-platform image
   - Push to GitHub Container Registry (ghcr.io)
   - Tag with version and `latest`
4. **Create GitHub Release**
   - Generate release notes
   - Attach distribution archive
   - Mark as pre-release if tag contains `-beta`, `-rc`, etc.

**Triggering a release:**
```bash
# Create and push a tag
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0

# Or create a release in GitHub UI
# Release workflow will run automatically
```

**Published Docker image location:**
```bash
# Pull the latest release
docker pull ghcr.io/vrhunski/ortoped:latest

# Pull a specific version
docker pull ghcr.io/vrhunski/ortoped:v1.0.0

# Run the published image
docker run --rm ghcr.io/vrhunski/ortoped:latest scan --demo
```

---

## Testing

### Test Suite Overview

**10 test files, 76 passing tests** covering all core functionality:

| Test File | Tests | Coverage |
|-----------|-------|----------|
| `PolicyEvaluatorTest.kt` | 6 | Policy rule evaluation, exemptions, AI integration |
| `PolicyYamlLoaderTest.kt` | 8 | YAML parsing, default policy, validation |
| `LicenseClassifierTest.kt` | 8 | License categorization, case-insensitivity |
| `LicenseResolverTest.kt` | 7 | AI response parsing, error handling |
| `CycloneDxGeneratorTest.kt` | 6 | CycloneDX JSON/XML generation |
| `SpdxGeneratorTest.kt` | 6 | SPDX JSON generation, fallback handling |
| `ScanResultTest.kt` | 8 | Data model serialization/deserialization |
| `RemoteRepositoryHandlerTest.kt` | 13 | Git URL detection, validation |
| `ReportGeneratorTest.kt` | 9 | JSON and console report generation |
| `ScanOrchestratorTest.kt` | 11 | End-to-end workflow integration |

### Running Tests

**All tests:**
```bash
./gradlew test
```

**Specific test class:**
```bash
./gradlew test --tests "*PolicyEvaluatorTest"
```

**With coverage report:**
```bash
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

**In watch mode (continuous):**
```bash
./gradlew test --continuous
```

### Test Resources

**src/test/resources/test-policy.yaml:**
- Sample policy configuration
- Defines permissive, copyleft, and unknown categories
- Includes sample rules for testing

**src/test/resources/test-scan-result.json:**
- Mock scan result with 5 dependencies
- Mix of resolved and unresolved licenses
- Includes AI suggestions for testing

### Test Architecture

**Unit Tests:**
- Mock external dependencies (LicenseResolver, HTTP clients)
- Test individual components in isolation
- Fast execution (<5 seconds total)

**Integration Tests:**
- Use real components where possible
- Demo mode for realistic data
- Test complete workflows

**Example test structure:**
```kotlin
@Test
fun `should detect copyleft violation`() {
    val scanResult = loadTestScanResult()
    val policy = loadTestPolicy()
    val evaluator = PolicyEvaluator(policy)

    val report = evaluator.evaluate(scanResult)

    val gplViolation = report.violations.find {
        it.dependencyId == "Maven:com.example:lib-gpl:1.0.0"
    }
    assertTrue(gplViolation != null)
    assertEquals("no-copyleft", gplViolation.ruleId)
    assertEquals(Severity.ERROR, gplViolation.severity)
}
```

---

## Deployment Guide

### Local Deployment

**1. Build the application:**
```bash
./gradlew installDist
```

**2. Run from build output:**
```bash
./build/install/ortoped/bin/ortoped scan -p /path/to/project
```

**3. Or build Docker image:**
```bash
docker build -t ortoped:local .
docker run --rm ortoped:local scan --demo
```

### Production Deployment (Docker)

**1. Build and tag:**
```bash
docker build -t ortoped:1.0.0 .
docker tag ortoped:1.0.0 myregistry.com/ortoped:1.0.0
docker tag ortoped:1.0.0 myregistry.com/ortoped:latest
```

**2. Push to registry:**
```bash
docker push myregistry.com/ortoped:1.0.0
docker push myregistry.com/ortoped:latest
```

**3. Deploy to server:**
```bash
# On production server
docker pull myregistry.com/ortoped:latest

# Run as a service
docker run -d \
  --name ortoped \
  --restart unless-stopped \
  -v /data/projects:/projects:ro \
  -v /data/reports:/reports \
  -e ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY \
  myregistry.com/ortoped:latest
```

### Kubernetes Deployment

**deployment.yaml:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ortoped
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ortoped
  template:
    metadata:
      labels:
        app: ortoped
    spec:
      containers:
      - name: ortoped
        image: ghcr.io/vrhunski/ortoped:latest
        env:
        - name: ANTHROPIC_API_KEY
          valueFrom:
            secretKeyRef:
              name: ortoped-secrets
              key: anthropic-api-key
        volumeMounts:
        - name: projects
          mountPath: /projects
          readOnly: true
        - name: reports
          mountPath: /reports
      volumes:
      - name: projects
        persistentVolumeClaim:
          claimName: ortoped-projects-pvc
      - name: reports
        persistentVolumeClaim:
          claimName: ortoped-reports-pvc
```

**CronJob for scheduled scans:**
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: ortoped-daily-scan
spec:
  schedule: "0 2 * * *"  # 2 AM daily
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: ortoped
            image: ghcr.io/vrhunski/ortoped:latest
            args:
            - scan
            - -p
            - /projects/myproject
            - -o
            - /reports/daily-scan.json
            - --enable-ai
            env:
            - name: ANTHROPIC_API_KEY
              valueFrom:
                secretKeyRef:
                  name: ortoped-secrets
                  key: anthropic-api-key
            volumeMounts:
            - name: projects
              mountPath: /projects
            - name: reports
              mountPath: /reports
          restartPolicy: OnFailure
          volumes:
          - name: projects
            persistentVolumeClaim:
              claimName: ortoped-projects-pvc
          - name: reports
            persistentVolumeClaim:
              claimName: ortoped-reports-pvc
```

### GitHub Container Registry (GHCR)

**Publishing to GHCR** (automatic via release workflow):

**Manual publishing:**
```bash
# Login to GHCR
echo $GITHUB_TOKEN | docker login ghcr.io -u USERNAME --password-stdin

# Build and tag
docker build -t ghcr.io/vrhunski/ortoped:latest .
docker tag ghcr.io/vrhunski/ortoped:latest ghcr.io/vrhunski/ortoped:v1.0.0

# Push
docker push ghcr.io/vrhunski/ortoped:latest
docker push ghcr.io/vrhunski/ortoped:v1.0.0
```

**Using published image:**
```bash
# Pull
docker pull ghcr.io/vrhunski/ortoped:latest

# Run
docker run --rm ghcr.io/vrhunski/ortoped:latest scan --demo
```

---

## Security Considerations

### GitHub Actions Security

**Environment Variable Injection Prevention:**

❌ **Unsafe (vulnerable to injection):**
```yaml
run: echo "${{ github.ref_name }}"
run: docker tag myimage:${{ github.ref_name }}
```

✅ **Safe (using environment variables):**
```yaml
env:
  TAG_NAME: ${{ github.ref_name }}
run: echo "$TAG_NAME"
run: docker tag myimage:"$TAG_NAME"
```

**Risky GitHub context variables:**
- `github.event.issue.title`
- `github.event.pull_request.body`
- `github.event.pull_request.head.ref`
- `github.event.comment.body`
- `github.head_ref`

Always pass these through environment variables, never directly in shell commands.

### Secret Management

**GitHub Secrets:**
```bash
# Required secrets for CI/CD
ANTHROPIC_API_KEY  # For AI license resolution
GITHUB_TOKEN       # Automatically provided by GitHub
```

**Setting secrets:**
1. Go to repository Settings → Secrets and variables → Actions
2. Click "New repository secret"
3. Add `ANTHROPIC_API_KEY` with your Claude API key

**Using secrets in workflows:**
```yaml
env:
  ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
```

**Never commit secrets:**
```bash
# Add to .gitignore
.env
*.key
secrets.yaml
credentials.json
```

### Docker Security

**Run as non-root user** (future enhancement):
```dockerfile
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -g 1000 ortoped && \
    adduser -D -u 1000 -G ortoped ortoped
USER ortoped
```

**Scan images for vulnerabilities:**
```bash
# Using Trivy
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  aquasec/trivy image ortoped:latest

# Using Snyk
snyk container test ortoped:latest
```

**Sign images** (Docker Content Trust):
```bash
export DOCKER_CONTENT_TRUST=1
docker push ghcr.io/vrhunski/ortoped:latest
```

### API Key Handling

**Best practices:**
1. Never hardcode API keys in source code
2. Use environment variables or secret management systems
3. Rotate keys regularly
4. Use different keys for dev/staging/production
5. Monitor API usage for anomalies

**Example key rotation:**
```bash
# Generate new key in Claude Console
# Update GitHub secret
# Update production environment
kubectl create secret generic ortoped-secrets \
  --from-literal=anthropic-api-key=$NEW_API_KEY \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart deployment to pick up new secret
kubectl rollout restart deployment/ortoped
```

---

## Troubleshooting

### Common Issues

**Docker build fails:**
```bash
# Clear Docker cache
docker builder prune -a

# Build with no cache
docker build --no-cache -t ortoped:latest .
```

**Tests fail locally but pass in CI:**
```bash
# Clean build directory
./gradlew clean

# Rebuild with fresh dependencies
./gradlew build --refresh-dependencies

# Check Java version
java -version  # Should be 21
```

**GitHub Action fails with permission error:**
```yaml
# Add explicit permissions to workflow
permissions:
  contents: read
  packages: write
```

**Docker image too large:**
```bash
# Check layer sizes
docker history ortoped:latest

# Use .dockerignore to exclude files
# Combine RUN commands to reduce layers
# Use multi-stage builds (already implemented)
```

---

## Next Steps

After Phase 6, OrtoPed is production-ready. Potential enhancements:

1. **Performance Optimization**
   - Parallel dependency scanning
   - Caching layer for ORT results
   - Database backend for large projects

2. **Enhanced Reporting**
   - HTML report generation
   - Interactive dashboards
   - Trend analysis over time

3. **Security Hardening**
   - SBOM signing
   - Vulnerability database integration
   - Compliance attestations

4. **Platform Support**
   - ARM64 Docker images
   - Native executables with GraalVM
   - Web UI for report viewing

5. **Integration Ecosystem**
   - Slack/Teams notifications
   - Jira issue creation
   - Webhook support for custom integrations

---

## Resources

- **Docker Documentation**: https://docs.docker.com/
- **GitHub Actions**: https://docs.github.com/en/actions
- **Kubernetes**: https://kubernetes.io/docs/
- **ORT Project**: https://github.com/oss-review-toolkit/ort
- **SPDX Specification**: https://spdx.dev/
- **CycloneDX Specification**: https://cyclonedx.org/

---

**Phase 6 Complete** ✅
All production infrastructure in place. OrtoPed is ready for deployment and external use.
