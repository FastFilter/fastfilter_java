# GitHub CI/CD Setup Guide

Complete setup guide for FastFilter Java's GitHub Actions CI/CD pipeline with cross-platform testing, native library builds, and automated releases.

## Overview

The FastFilter Java project uses GitHub Actions for continuous integration and deployment with the following workflows:

1. **PR Build** (`.github/workflows/pr-build.yml`) - Build and test on every PR and push
2. **Nightly Benchmarks** (`.github/workflows/nightly-benchmarks.yml`) - Performance regression testing  
3. **Release** (`.github/workflows/release.yml`) - Automated release builds with native libraries

## Features

- ✅ **JDK 24 Support**: All workflows use JDK 24 with preview features
- ✅ **Multi-Platform**: Tests on Ubuntu, macOS (Intel + Apple Silicon), and Windows
- ✅ **Cross-Architecture**: ARM64/AArch64 testing via QEMU and Docker
- ✅ **Bazel Integration**: C++ builds with platform-specific optimizations
- ✅ **Native Libraries**: Automated cross-compilation and packaging
- ✅ **Benchmarking**: JMH and C++ performance testing with regression detection
- ✅ **Testcontainers**: Docker-based cross-platform LibC testing
- ✅ **Environment Configuration**: Platform-specific .env file support

## Workflow Configuration

### 1. PR Build Workflow

Triggered on:
- Pull requests to `master`/`main`
- Pushes to `master`/`main`

**Matrix Testing**:
- **OS**: Ubuntu Latest, macOS Latest, Windows Latest  
- **Java**: 24 (updated from 21, 24 matrix)
- **Platforms**: Includes native and cross-compiled builds

**Key Steps**:
```yaml
- name: Set up JDK 24
  uses: actions/setup-java@v4
  with:
    java-version: '24'
    distribution: 'temurin'
    cache: maven

- name: Set up Bazel
  uses: bazelbuild/setup-bazelisk@v2  # Updated from bazel-contrib/setup-bazel@v1
  with:
    bazelisk-version: latest

- name: Build with Maven
  run: mvn clean compile -B

- name: Run tests  
  run: mvn test -B

- name: Build native libraries
  run: mvn package -Pnative-libraries -Dbuild.native=true -B
```

**Artifacts:**
- Platform-specific native library JARs
- Benchmark results
- Cross-compiled libraries

### 2. Release Build (`.github/workflows/release.yml`)

**Triggers:** GitHub releases or manual dispatch

**Features:**
- **Native library builds**: All supported platforms
  - Linux x86_64 (with AVX2)
  - macOS x86_64 (with AVX2) 
  - macOS ARM64 (Apple Silicon)
  - Windows x86_64 (with AVX2)
- **Optimized builds**: Production-ready with all optimizations
- **Maven Central deployment**: Automated release to Maven Central
- **Release artifacts**: All JARs, native libraries, and benchmarks

**Supported Platforms:**
```
ubuntu-latest  -> linux-x86_64
ubuntu-latest (Docker ARM64) -> linux-arm64
macos-latest   -> macos-x86_64  
macos-14       -> macos-arm64 (M1 runner)
windows-latest -> windows-x86_64
```

### 3. Nightly Benchmarks (`.github/workflows/nightly-benchmarks.yml`)

**Triggers:** Daily at 2 AM UTC or manual dispatch

**Features:**
- **Comprehensive benchmarking**: Full JMH benchmark suite
- **C++ benchmarks**: All fastfilter_cpp benchmark executables
- **Performance tracking**: Historical performance data
- **Regression detection**: Automated alerts for performance regressions
- **Multi-platform comparison**: Performance across all platforms and JDK versions

**Benchmark Types:**
- `bulk_insert_and_query`: Large dataset insertion and lookup performance
- `stream`: Streaming filter operations
- `construction_failure`: Edge case construction scenarios
- JMH comprehensive suite: All Java filter implementations

## Architecture Changes

### External Dependency Management

The fastfilter_cpp source code has been removed from the main tree. Instead:

1. **Bazel fetches from GitHub**: `MODULE.bazel` configures automatic download
   ```starlark
   http_archive(
       name = "fastfilter_cpp",
       urls = ["https://github.com/FastFilter/fastfilter_cpp/archive/refs/heads/master.zip"],
       strip_prefix = "fastfilter_cpp-master",
       build_file = "@//bazel:fastfilter_cpp.BUILD",
   )
   ```

2. **Clean process**: Maven clean now removes all Bazel artifacts
   ```xml
   <fileset>
       <directory>${project.parent.basedir}</directory>
       <includes><include>bazel-*</include></includes>
   </fileset>
   ```

### Platform-Specific Builds

Native library modules now build only for their target platform by default:

```xml
<profile>
    <id>native-macos-arm64</id>
    <activation>
        <os>
            <family>mac</family>
            <arch>aarch64</arch>
        </os>
    </activation>
    <modules>
        <module>macos-arm64</module>
    </modules>
</profile>
```

Cross-compilation is available via the `native.cross.compile` property.

## Build Commands

### Local Development
```bash
# Build for current platform
mvn clean install

# Build native libraries
mvn package -Pnative-libraries -Dbuild.native=true

# Cross-compile all platforms  
mvn package -Pnative-cross-compile -Dnative.cross.compile=true

# Run benchmarks
cd jmh && mvn clean package && java -jar target/benchmarks.jar

# Run C++ benchmarks
mvn exec:exec@bazel-run-cpp-benchmarks -Pbazel-benchmarks
```

### CI/CD Commands
```bash
# PR build simulation
mvn clean compile test package -Pnative-libraries -Dbuild.native=true

# Release build simulation  
mvn clean package -PperformRelease=true

# Benchmark run
java -jar jmh/target/benchmarks.jar -f 1 -wi 2 -i 5 -t 2 -rf json
```

## Secrets Required

For full CI/CD functionality, configure these GitHub secrets:

```
MAVEN_USERNAME       - Maven Central username
MAVEN_PASSWORD       - Maven Central password  
GPG_PASSPHRASE      - GPG signing passphrase for releases
GITHUB_TOKEN        - Auto-provided, used for benchmark tracking
```

## Monitoring and Alerts

### Performance Regression Detection
- Nightly benchmarks compare with historical data
- Automatic GitHub issues created for regressions >150% baseline
- Benchmark results stored in `benchmarks/` branch for trending

### Build Health
- All PRs must pass multi-platform builds
- Native library functionality verified on each platform
- Integration tests ensure C++ bindings work correctly

## Environment Configuration for CI

### Platform-Specific .env Files

FastFilter uses `.env` files for consistent build environments across all platforms. GitHub Actions can use these files to configure builds:

```yaml
# In GitHub Actions workflow
- name: Setup environment
  run: |
    # Copy platform-specific .env file
    cp .env.linux-x86_64 .env
    
    # Load environment variables
    set -a
    source .env
    set +a
    
    # Use in subsequent steps
    echo "JAVA_HOME=$JAVA_HOME" >> $GITHUB_ENV
    echo "CC=$CC" >> $GITHUB_ENV
    echo "CXX=$CXX" >> $GITHUB_ENV
```

**Available .env templates for CI**:
- `.env.linux-x86_64` - Ubuntu runners with AVX2
- `.env.macos-arm64` - macOS ARM64 (M1) runners
- `.env.windows` - Windows runners with MSVC/MinGW

### CI Environment Variables

Key variables for GitHub Actions:

```yaml
env:
  JAVA_HOME: ${{ steps.setup-java.outputs.path }}
  MAVEN_OPTS: "--enable-preview --enable-native-access=ALL-UNNAMED -Xmx4g"
  BAZEL_BUILD_OPTS: "--jobs=auto --compilation_mode=opt"
  TEST_FFI_ENABLED: "true"
  TEST_DOCKER_ENABLED: "true"
```

## Local Testing

To test the full CI pipeline locally with .env support:

```bash
# Copy platform-specific environment
cp .env.linux-x86_64 .env  # or appropriate platform

# Test build matrix simulation  
for java in 24; do  # Updated to JDK 24 only
  export JAVA_HOME=/path/to/jdk-$java
  mvn clean test package -Pnative-libraries -Dbuild.native=true
done

# Test cross-compilation
mvn package -Pnative-cross-compile -Dnative.cross.compile=true

# Test benchmarks with environment
cd jmh
mvn clean package
java -jar target/benchmarks.jar -f 1 -wi 1 -i 3 -t 1 -foe true

# Test Nix development environment
nix develop  # Automatically sources .env files
```

## Performance Characteristics

Expected benchmark performance (indicative):
- **Java filters**: 50-100M operations/sec (Xor8, BinaryFuse8)
- **C++ filters**: 80-150M operations/sec (with AVX2 optimizations)
- **Memory efficiency**: 8-12 bits per key depending on filter type
- **Construction time**: Linear with dataset size, ~1-2M keys/sec

## File Structure

```
.github/workflows/
├── pr-build.yml           # PR and main branch builds
├── release.yml            # Release builds and deployment
└── nightly-benchmarks.yml # Performance monitoring

native-libs/               # Platform-specific native library modules
├── linux-x86_64/         # Linux with AVX2 support
├── linux-arm64/           # Linux ARM64 (AArch64)
├── macos-x86_64/          # Intel Mac with AVX2 support
├── macos-arm64/           # Apple Silicon Mac
├── windows-x86_64/        # Windows with AVX2 support
└── pom.xml               # Parent POM with platform profiles

bazel/
└── fastfilter_cpp.BUILD   # External dependency build configuration
```

This setup ensures robust, automated testing and deployment while maintaining high performance across all supported platforms.