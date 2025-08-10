# GitHub Workflows Testing Guide

Complete guide for testing all GitHub Actions workflows locally using `act` and other tools.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Workflow Overview](#workflow-overview)
- [Local Testing with `act`](#local-testing-with-act)
- [Manual Local Testing](#manual-local-testing)
- [Workflow-Specific Testing](#workflow-specific-testing)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Tools

1. **Act** - Local GitHub Actions runner
   ```bash
   # macOS
   brew install act
   
   # Linux
   curl https://raw.githubusercontent.com/nektos/act/master/install.sh | sudo bash
   
   # Windows
   choco install act-cli
   ```

2. **Docker** - Required by act for running workflows
   ```bash
   # Verify Docker is running
   docker --version
   docker ps
   ```

3. **GitHub CLI** (optional)
   ```bash
   # macOS
   brew install gh
   
   # Login to GitHub
   gh auth login
   ```

4. **Local Development Environment**
   ```bash
   # JDK 24
   java --version
   
   # Maven
   mvn --version
   
   # Bazel
   bazel version
   ```

## Workflow Overview

FastFilter Java has 6 GitHub Actions workflows:

| Workflow | File | Trigger | Purpose |
|----------|------|---------|---------|
| **PR Build** | `pr-build.yml` | PR/Push to master | Multi-platform build, test, benchmarks |
| **Nightly Benchmarks** | `nightly-benchmarks.yml` | Daily 2 AM UTC | Performance regression testing |
| **Release Build** | `release.yml` | Release creation | Full release with native libraries |
| **Java CI** | `java.yml` | Push/PR | Simple Java build (legacy) |
| **Security Scan** | `security.yaml` | Weekly/PR | Security vulnerability scanning |
| **Test Local** | `test-local.yaml` | Manual dispatch | Local testing helper |

## Local Testing with `act`

### Setup Act Configuration

Create `.actrc` file in project root:
```bash
# .actrc - Act configuration
-P ubuntu-latest=catthehacker/ubuntu:act-latest
-P ubuntu-22.04=catthehacker/ubuntu:act-22.04
-P macos-latest=catthehacker/ubuntu:act-latest
-P macos-14=catthehacker/ubuntu:act-latest
-P windows-latest=catthehacker/ubuntu:act-latest
--container-architecture linux/amd64
--artifact-server-path /tmp/artifacts
```

### Environment Variables Setup

Create `.env.act` for secrets (don't commit):
```bash
# .env.act - Act environment variables
GITHUB_TOKEN=ghp_your_token_here
MAVEN_USERNAME=your_maven_username
MAVEN_PASSWORD=your_maven_password
GPG_PASSPHRASE=your_gpg_passphrase
```

### Basic Act Commands

```bash
# List all workflows
act -l

# List all events
act -l -e pull_request

# Dry run (show what would execute)
act --dry-run

# Run with verbose output
act -v
```

## Manual Local Testing

### Environment Setup

```bash
# Copy appropriate .env template
cp .env.linux-x86_64 .env  # Adjust for your platform

# Source environment
set -a; source .env; set +a

# Verify Java setup
java --version
javac --version
```

### Core Build Tests

```bash
# Test Maven build (mirrors PR workflow)
mvn clean compile -B
mvn test -B
mvn package -DskipTests -B

# Test Bazel build
bazel build //fastfilter:fastfilter
bazel build @fastfilter_cpp//:fastfilter_cpp_core
bazel build @fastfilter_cpp//:bulk_insert_and_query

# Test JMH benchmarks
cd jmh
mvn clean package -B
java -jar target/benchmarks.jar -f 1 -wi 1 -i 3 -t 1 -foe true org.fastfilter.ConstructionBenchmark
cd ..
```

### Cross-Platform Simulation

```bash
# Simulate different platforms using Docker
# Linux ARM64
docker run --rm --platform linux/arm64 \
  -v $PWD:/workspace -w /workspace \
  eclipse-temurin:24-jdk \
  bash -c "apt-get update && apt-get install -y maven && mvn clean compile -B"

# Alpine Linux (musl)
docker run --rm \
  -v $PWD:/workspace -w /workspace \
  openjdk:24-alpine \
  sh -c "apk add --no-cache maven && mvn clean compile -B"
```

## Workflow-Specific Testing

### 1. PR Build Workflow (`pr-build.yml`)

#### Using Act
```bash
# Test PR event
act pull_request -j build-and-test

# Test specific job
act pull_request -j build-and-test -s GITHUB_TOKEN

# Test with platform matrix
act pull_request -j build-and-test --matrix os:ubuntu-latest --matrix java:24
```

#### Manual Testing
```bash
# Simulate PR build steps
echo "=== PR Build Simulation ==="

# Checkout (manual: ensure clean working directory)
git status

# Setup environment
cp .env.linux-x86_64 .env
source .env

# Build with Maven
echo "Building with Maven..."
mvn clean compile -B || exit 1

# Run tests
echo "Running tests..."
mvn test -B || exit 1

# Build native libraries
echo "Building native libraries..."
mvn package -Pnative-libraries -Dbuild.native=true -B

# Run sample benchmarks
echo "Running sample benchmarks..."
cd jmh
mvn clean package -B
java -jar target/benchmarks.jar -f 1 -wi 1 -i 3 -t 1 -foe true org.fastfilter.ConstructionBenchmark
cd ..

echo "PR Build simulation complete!"
```

### 2. Nightly Benchmarks (`nightly-benchmarks.yml`)

#### Using Act
```bash
# Test scheduled trigger
act schedule -j comprehensive-benchmarks

# Test workflow dispatch
act workflow_dispatch -j comprehensive-benchmarks
```

#### Manual Testing
```bash
echo "=== Nightly Benchmarks Simulation ==="

# Setup
cp .env.linux-x86_64 .env
source .env

# Build project
mvn clean package -DskipTests -B

# Build native libraries
mvn package -Pnative-libraries -Dbuild.native=true -DskipTests -B

# Run Java benchmarks (full)
cd jmh
mvn clean package -B
java -jar target/benchmarks.jar -f 3 -wi 5 -i 10 -t 4 -rf json -rff benchmark-results-local.json
cd ..

# Run C++ benchmarks
bazel run @fastfilter_cpp//:bulk_insert_and_query -- 1000000 > cpp-bench-bulk-local.txt
bazel run @fastfilter_cpp//:stream > cpp-bench-stream-local.txt

echo "Nightly benchmarks simulation complete!"
echo "Results in: jmh/benchmark-results-local.json, cpp-bench-*.txt"
```

### 3. Release Workflow (`release.yml`)

#### Using Act
```bash
# Test release event (mock)
act release -j build-and-release -s GITHUB_TOKEN

# Test manual dispatch
act workflow_dispatch -j build-and-release --input version=1.0.4-test
```

#### Manual Testing
```bash
echo "=== Release Build Simulation ==="

# Setup
cp .env.linux-x86_64 .env
source .env

# Set mock version
VERSION="1.0.4-test"
mvn versions:set -DnewVersion=${VERSION} -B

# Build all modules
mvn clean package -PperformRelease=true -B

# Run full test suite
mvn verify -B

# Simulate native library building (local platform only)
echo "Native library built for current platform"

echo "Release build simulation complete!"
echo "Version: ${VERSION}"
```

### 4. Java CI (`java.yml`) - Legacy

#### Using Act
```bash
# Test basic Java CI
act push -j build
```

#### Manual Testing
```bash
echo "=== Java CI Simulation ==="
mvn package
mvn test
echo "Java CI simulation complete!"
```

### 5. Security Scan (`security.yaml`)

#### Using Act
```bash
# Test security workflow (may need Docker adjustments)
act push -j security --container-architecture linux/amd64
```

#### Manual Testing
```bash
echo "=== Security Scan Simulation ==="

# Install Trivy (if not installed)
# brew install aquasec/trivy/trivy  # macOS
# apt-get install trivy            # Ubuntu

# Run Trivy filesystem scan
trivy fs . --format sarif --output trivy-results.sarif

# Run Maven dependency check
mvn org.owasp:dependency-check-maven:check

echo "Security scan simulation complete!"
echo "Results in: trivy-results.sarif, target/dependency-check-report.html"
```

### 6. Test Local (`test-local.yaml`)

#### Using Act
```bash
# Test local workflow
act workflow_dispatch -j test
```

#### Manual Testing
```bash
echo "=== Test Local Simulation ==="
mvn clean compile -B
echo "Test local simulation complete!"
```

## Advanced Act Usage

### Testing Multiple Jobs
```bash
# Run all jobs in pr-build.yml
act pull_request -W .github/workflows/pr-build.yml

# Run specific jobs in parallel
act pull_request -j build-and-test,cpp-benchmarks
```

### Custom Events
```bash
# Create custom event file
cat > custom-event.json << 'EOF'
{
  "ref": "refs/heads/master",
  "repository": {
    "name": "fastfilter_java",
    "full_name": "FastFilter/fastfilter_java"
  }
}
EOF

# Use custom event
act push -e custom-event.json
```

### Resource Limits
```bash
# Limit memory and CPU for large workflows
act pull_request --memory 4g --cpus 2 -j build-and-test
```

### Artifact Handling
```bash
# Enable artifact server
act pull_request --artifact-server-path /tmp/artifacts -j build-and-test

# Check artifacts after run
ls -la /tmp/artifacts
```

## Debugging and Inspection

### Interactive Debugging
```bash
# Start with shell access
act pull_request -j build-and-test --shell

# Debug specific step
act pull_request -j build-and-test --step "Run tests" --shell
```

### Workflow Inspection
```bash
# Show workflow graph
act -g

# Show job dependencies
act pull_request --list

# Show environment
act pull_request -j build-and-test --env
```

### Log Analysis
```bash
# Capture logs
act pull_request -j build-and-test > workflow-logs.txt 2>&1

# Analyze logs
grep -E "ERROR|FAIL|Exception" workflow-logs.txt
```

## CI/CD Testing Best Practices

### 1. Test Early and Often
```bash
# Quick smoke test
act push -j test --dry-run

# Full PR simulation
act pull_request -j build-and-test
```

### 2. Platform-Specific Testing
```bash
# Use platform-specific .env files
cp .env.macos-arm64 .env && act pull_request -j build-and-test
cp .env.linux-x86_64 .env && act pull_request -j build-and-test
cp .env.windows .env && act pull_request -j build-and-test
```

### 3. Performance Testing
```bash
# Monitor resource usage during benchmark tests
htop &
act schedule -j comprehensive-benchmarks
```

### 4. Failure Scenarios
```bash
# Test with intentional failures
echo "failing test" > fastfilter/src/test/java/FailingTest.java
act pull_request -j build-and-test

# Clean up
rm fastfilter/src/test/java/FailingTest.java
```

## Troubleshooting

### Common Issues

1. **Docker Permission Denied**
   ```bash
   # Add user to docker group
   sudo usermod -aG docker $USER
   # Logout and login again
   ```

2. **Act Container Issues**
   ```bash
   # Clean Docker images
   docker system prune -a
   
   # Update act images
   docker pull catthehacker/ubuntu:act-latest
   ```

3. **Memory Issues**
   ```bash
   # Increase Docker memory limit
   # Docker Desktop: Settings > Resources > Memory
   
   # Or use smaller workflows
   act push -j test  # Instead of full PR workflow
   ```

4. **JDK Version Issues**
   ```bash
   # Verify Java setup in container
   act push -j build --shell
   # Inside container: java --version
   ```

5. **Bazel Issues**
   ```bash
   # Clean Bazel cache
   bazel clean --expunge
   
   # Test Bazel in container
   act pull_request -j cpp-benchmarks --shell
   ```

### Getting Help
```bash
# Act help
act --help
act pull_request --help

# Workflow validation
act --dry-run -j build-and-test

# GitHub CLI workflow info
gh workflow list
gh workflow view pr-build.yml
```

## Example Testing Scripts

### Complete Workflow Test Script
```bash
#!/bin/bash
# test-workflows.sh - Complete workflow testing

set -e

echo "=== FastFilter Java Workflow Testing ==="

# 1. Setup
echo "1. Setting up environment..."
cp .env.linux-x86_64 .env
source .env

# 2. Basic builds
echo "2. Testing basic builds..."
mvn clean compile -B
bazel build //fastfilter:fastfilter

# 3. Test workflows with act
echo "3. Testing workflows with act..."
act --dry-run -l

# Test key workflows
act push -j test --dry-run
act pull_request -j build-and-test --dry-run

# 4. Manual workflow simulation
echo "4. Running manual workflow simulation..."
mvn clean test -B
cd jmh && mvn clean package && cd ..
bazel run @fastfilter_cpp//:bulk_insert_and_query -- 10000

echo "=== All tests complete! ==="
```

### Benchmark Testing Script
```bash
#!/bin/bash
# test-benchmarks.sh - Benchmark workflow testing

echo "=== Benchmark Testing ==="

# Quick benchmarks (for testing)
cd jmh
mvn clean package -B
java -jar target/benchmarks.jar -f 1 -wi 1 -i 3 -t 1 -rf json -rff test-results.json

# C++ benchmarks
cd ..
bazel run @fastfilter_cpp//:bulk_insert_and_query -- 10000

echo "Benchmark testing complete!"
echo "Java results: jmh/test-results.json"
```

This comprehensive guide covers all aspects of testing the GitHub workflows both locally with `act` and manually. The workflows are well-designed and follow GitHub Actions best practices for a multi-platform Java project with native dependencies.