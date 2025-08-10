# FastFilter Java Build Guide

Comprehensive build instructions for FastFilter Java with C++ FFI integration using JDK 24 and Bazel.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Maven Build System](#maven-build-system)
- [Bazel Build System](#bazel-build-system)
- [C++ Integration Build](#c-integration-build)
- [Platform-Specific Instructions](#platform-specific-instructions)
- [Testing](#testing)
- [Benchmarking](#benchmarking)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### Software Requirements

#### Essential
- **Java Development Kit (JDK) 24** with preview features enabled
  - Oracle JDK 24: https://jdk.java.net/24/
  - OpenJDK 24: https://adoptium.net/
  - Homebrew: `brew install openjdk@24`
- **Git** for version control
- **Docker** for cross-platform testing (optional)

#### For Maven Builds
- **Apache Maven 3.9.0+**
  - Download: https://maven.apache.org/download.cgi
  - Homebrew: `brew install maven`

#### For Bazel Builds  
- **Bazel 8.0.0+** or **Bazelisk** (recommended)
  - Install: https://bazel.build/install
  - Bazelisk: `brew install bazelisk`
  - Direct: `brew install bazel`

#### For C++ Integration
- **C/C++ Compiler**
  - Linux: GCC 8+ or Clang 7+
  - macOS: Xcode Command Line Tools or Clang 7+
  - Windows: MSVC 2019+ or MinGW-w64
- **AVX2-capable processor** (recommended for SIMD optimizations)

### Hardware Requirements
- **Memory**: 4GB+ RAM (8GB+ recommended for large benchmarks)
- **Storage**: 2GB+ free space
- **CPU**: x64 architecture, AVX2 support recommended

## Quick Start

### 1. Clone the Repository
```bash
git clone https://github.com/FastFilter/fastfilter_java.git
cd fastfilter_java
```

### 2. Verify JDK 24 Installation
```bash
java --version
# Should show: openjdk 24.0.2 2025-07-15 or similar

javac --version
# Should show: javac 24.0.2

# Verify preview features are available
java --enable-preview --version
# Should work without error
```

### 3. Choose Your Build System

#### Option A: Maven (Recommended - Fully Working)
```bash
# Build and test Java components
mvn clean compile test

# Build with JMH benchmarks
cd jmh && mvn clean package

# Run benchmarks
cd jmh && java -jar target/benchmarks.jar
```

#### Option B: Bazel (Java + C++ Support)
```bash
# Build Java library
bazel build //fastfilter:fastfilter

# Build C++ core library from external repository
bazel build @fastfilter_cpp//:fastfilter_cpp_core

# Run C++ benchmarks
bazel run @fastfilter_cpp//:bulk_insert_and_query -- 100000

# Note: Full Bazel test integration requires additional configuration
# Use Maven for complete testing functionality
```

#### Option C: Maven + Bazel Integration (New!)
```bash
# Build with Maven Bazel integration
mvn compile -Pbazel -Dbazel=true

# Run C++ benchmarks through Maven
mvn exec:exec@bazel-run-cpp-benchmarks -Pbazel-benchmarks -Dbazel.benchmark.size=50000

# Build and test with Bazel backend
mvn test -Pbazel -Dbazel=true
```

### 4. Current Build Status

✅ **Maven**: Fully functional with JDK 21+
- All Java components build and test successfully
- JMH benchmarks working
- JUnit 5 test suite passes

⚠️ **Bazel**: Basic library builds, integration in progress
- Core Java library builds successfully
- C++ integration requires JDK 24 with --enable-preview
- Test integration requires toolchain configuration

## Maven Build System

### Standard Java Build

#### 1. Build Core Library
```bash
# Compile main library
mvn compile

# Run tests
mvn test

# Package JAR
mvn package

# Install to local repository
mvn install
```

#### 2. Build JMH Benchmarks
```bash
# Navigate to JMH module
cd jmh

# Build benchmark JAR
mvn clean package

# Run benchmarks
java -jar target/benchmarks.jar
```

### Maven Profiles

The project includes several Maven profiles:

#### Standard Profiles
```bash
# Use alternative Maven repository (if needed)
mvn test -P artagon-oss-dev

# Build with debug information
mvn compile -P debug

# Skip tests for faster builds
mvn package -DskipTests
```

#### Bazel Integration Profiles (New!)

**Basic Bazel Profile** (`bazel`):
```bash
# Build Java and C++ libraries with Bazel
mvn compile -Pbazel -Dbazel=true

# Run tests with Bazel backend
mvn test -Pbazel -Dbazel=true

# Full lifecycle with Bazel integration
mvn package -Pbazel -Dbazel=true
```

**Benchmark Profile** (`bazel-benchmarks`):
```bash
# Build and run C++ benchmarks
mvn integration-test -Pbazel-benchmarks -Dbazel.benchmarks=true

# Run with custom dataset size
mvn integration-test -Pbazel-benchmarks -Dbazel.benchmarks=true -Dbazel.benchmark.size=100000

# Run only C++ benchmarks
mvn exec:exec@bazel-run-cpp-benchmarks -Pbazel-benchmarks -Dbazel.benchmark.size=25000
```

#### Bazel Profile Properties
```bash
# Configure benchmark dataset size (default: 100000)
-Dbazel.benchmark.size=50000

# Enable Bazel profile
-Dbazel=true

# Enable benchmark profile  
-Dbazel.benchmarks=true
```

#### Native Library Profiles (New!)

**Native Build Profile** (`native-libraries`):
```bash
# Build native libraries for current platform
mvn compile -Pnative-libraries -Dbuild.native=true

# Package current platform native library
mvn package -Pnative-libraries -Dbuild.native=true
```

**Cross-Platform Profile** (`native-cross-compile`):
```bash
# Build native libraries for all supported platforms
mvn package -Pnative-cross-compile -Dbuild.native.cross=true

# This creates platform-specific JARs:
# - fastfilter-native-linux-x86_64-1.0.3-SNAPSHOT.jar
# - fastfilter-native-macos-x86_64-1.0.3-SNAPSHOT.jar  
# - fastfilter-native-macos-arm64-1.0.3-SNAPSHOT.jar
# - fastfilter-native-windows-x86_64-1.0.3-SNAPSHOT.jar
```

**Release Profile** (`release-native`):
```bash
# Full release build with all platforms
mvn clean package -PperformRelease=true

# Includes all Java sources, docs, native libraries, and signatures
```

### Maven Build Lifecycle

```bash
# Complete build cycle
mvn clean                    # Clean previous builds
mvn validate                # Validate project structure
mvn compile                 # Compile source code
mvn test                    # Run unit tests
mvn package                 # Create JAR files
mvn verify                  # Run integration tests
mvn install                 # Install to local repository
mvn deploy                  # Deploy to remote repository (if configured)
```

### Maven Dependencies

Key dependencies are managed in the root `pom.xml`:

```xml
<properties>
    <maven.compiler.release>24</maven.compiler.release>
    <junit.jupiter.version>5.11.0</junit.jupiter.version>
    <jmh.version>1.37</jmh.version>
</properties>
```

## Bazel Build System

### Basic Bazel Commands

#### 1. Build Targets
```bash
# Build Java library
bazel build //fastfilter:fastfilter

# Build all Java components
bazel build //fastfilter:all

# Build JMH benchmarks
bazel build //jmh:jmh_benchmarks

# Build everything
bazel build //...
```

#### 2. Run Tests
```bash
# Run specific test
bazel test //fastfilter:RegressionTests

# Run all FastFilter tests
bazel test //fastfilter:all_tests

# Run all tests in project
bazel test //...

# Run tests with output
bazel test //fastfilter:all_tests --test_output=all
```

#### 3. Run Applications
```bash
# Run specific benchmark
bazel run //jmh:comprehensive_benchmark

# Run Java vs C++ comparison
bazel run //jmh:java_vs_cpp_benchmark

# Run all benchmarks
bazel run //jmh:all_benchmarks
```

### Bazel Configuration

#### .bazelrc (Create in project root)
```bash
# JDK 24 configuration
build --java_language_version=24
build --tool_java_language_version=24
test --java_language_version=24

# JVM flags for FFI
build --jvmopt="--enable-native-access=ALL-UNNAMED"
build --jvmopt="--enable-preview"
test --jvmopt="--enable-native-access=ALL-UNNAMED"
test --jvmopt="--enable-preview"

# Performance optimizations
build --compilation_mode=opt
build -c opt

# Platform-specific settings
build:linux --copt=-mavx2
build:macos --copt=-mavx2
build:windows --copt=/arch:AVX2
```

### Bazel Workspaces and Modules

The project structure:
```
fastfilter_java/
├── WORKSPACE              # Main workspace definition
├── BUILD.bazel            # Root build file
├── fastfilter/
│   └── BUILD.bazel        # Java library build
├── jmh/
│   └── BUILD.bazel        # JMH benchmarks build
└── fastfilter_cpp/
    └── BUILD.bazel        # C++ library build
```

## C++ Integration Build

### 1. Prerequisites Check
```bash
# Check C++ compiler
gcc --version        # or clang --version
g++ --version        # or clang++ --version

# Check for AVX2 support
grep -m1 "^flags" /proc/cpuinfo | grep avx2    # Linux
sysctl -n machdep.cpu.features | grep AVX2     # macOS
```

### 2. Build C++ Components

#### Using Bazel (Recommended)
```bash
# Build C++ library with FFI wrapper
bazel build //fastfilter_cpp:fastfilter_cpp_ffi

# Build C++ benchmarks (optional)
bazel build //fastfilter_cpp:bulk_insert_and_query

# Build Java with C++ integration
bazel build //fastfilter:fastfilter_with_cpp

# Test integration
bazel run //jmh:java_vs_cpp_benchmark
```

#### Manual C++ Build (Alternative)
```bash
# Navigate to C++ directory
cd fastfilter_cpp

# Build using Make (basic)
cd benchmarks
make clean
make

# Run C++ benchmarks
./bulk-insert-and-query.exe 1000000
```

### 3. Library Path Configuration

#### Set Library Path
```bash
# Linux
export LD_LIBRARY_PATH=$PWD/bazel-bin/fastfilter_cpp:$LD_LIBRARY_PATH

# macOS  
export DYLD_LIBRARY_PATH=$PWD/bazel-bin/fastfilter_cpp:$DYLD_LIBRARY_PATH

# Or set Java system property
java -Dfastfilter.cpp.library.path=./bazel-bin/fastfilter_cpp/libfastfilter_cpp_ffi.so YourApp
```

## Platform-Specific Instructions

### Linux (Ubuntu/Debian)

#### 1. Install Dependencies
```bash
# Update package list
sudo apt update

# Install JDK 24
sudo apt install openjdk-24-jdk

# Install build tools
sudo apt install build-essential maven git

# Install Bazel
curl -fsSL https://bazel.build/bazel-release.pub.gpg | gpg --dearmor >bazel-archive-keyring.gpg
sudo mv bazel-archive-keyring.gpg /usr/share/keyrings
echo "deb [arch=amd64 signed-by=/usr/share/keyrings/bazel-archive-keyring.gpg] https://storage.googleapis.com/bazel-apt stable jdk1.8" | sudo tee /etc/apt/sources.list.d/bazel.list
sudo apt update && sudo apt install bazel
```

#### 2. Build with AVX2 Support
```bash
# Verify AVX2 support
grep avx2 /proc/cpuinfo

# Build with AVX2 optimizations
bazel build //fastfilter_cpp:fastfilter_cpp_ffi --copt=-mavx2 --copt=-O3
```

### macOS

#### 1. Install Dependencies
```bash
# Install Homebrew (if not installed)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install JDK 24
brew install openjdk@24
echo 'export PATH="/opt/homebrew/opt/openjdk@24/bin:$PATH"' >> ~/.zshrc

# Install build tools
brew install maven bazel git

# Install Xcode Command Line Tools
xcode-select --install
```

#### 2. Build Configuration
```bash
# Build with Clang optimizations
bazel build //fastfilter_cpp:fastfilter_cpp_ffi --copt=-mavx2 --copt=-O3 --copt=-march=native
```

### Windows

#### 1. Install Dependencies
```powershell
# Install using Chocolatey (recommended)
choco install openjdk24 maven bazel git visualstudio2019buildtools

# Or download manually:
# - JDK 24: https://jdk.java.net/24/
# - Maven: https://maven.apache.org/download.cgi
# - Bazel: https://github.com/bazelbuild/bazel/releases
```

#### 2. Build Configuration
```powershell
# Build with MSVC
bazel build //fastfilter_cpp:fastfilter_cpp_ffi --copt=/O2 --copt=/arch:AVX2

# Use MinGW (alternative)
bazel build //fastfilter_cpp:fastfilter_cpp_ffi --compiler=mingw-gcc --copt=-mavx2 --copt=-O3
```

## Testing

### Unit Tests

#### Maven
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=RegressionTests

# Run with verbose output
mvn test -Dtest=RegressionTests -Dsurefire.printSummary=true

# Skip tests
mvn package -DskipTests
```

#### Bazel
```bash
# Run all tests
bazel test //...

# Run specific test
bazel test //fastfilter:RegressionTests

# Run tests with output
bazel test //fastfilter:RegressionTests --test_output=all

# Run tests in parallel
bazel test //... --jobs=auto
```

### Integration Tests

```bash
# Test Java-C++ integration
bazel test //fastfilter:all_tests
bazel run //jmh:java_vs_cpp_benchmark -- -f 1 -wi 1 -i 1

# Verify C++ library loading
java --enable-native-access=ALL-UNNAMED --enable-preview \
     -Dfastfilter.cpp.library.path=./bazel-bin/fastfilter_cpp/libfastfilter_cpp_ffi.so \
     -cp bazel-bin/fastfilter/libfastfilter.jar \
     org.fastfilter.TestAllFilters
```

### Test Configuration

#### JVM Test Settings
```bash
# For FFI tests
export JAVA_OPTS="--enable-native-access=ALL-UNNAMED --enable-preview"

# For memory testing
export MAVEN_OPTS="-Xmx4g -XX:+UseG1GC"
```

## Benchmarking

### JMH Benchmarks

#### Maven Approach
```bash
cd jmh

# Build benchmark JAR
mvn clean package

# Run all benchmarks
java -jar target/benchmarks.jar

# Run specific benchmark
java -jar target/benchmarks.jar ComprehensiveFilterBenchmark

# Run with custom parameters
java -jar target/benchmarks.jar -f 3 -wi 5 -i 10 -t 4
```

#### Bazel Approach
```bash
# Run comprehensive benchmarks
bazel run //jmh:comprehensive_benchmark

# Run Java vs C++ comparison
bazel run //jmh:java_vs_cpp_benchmark

# Run with custom JMH arguments
bazel run //jmh:comprehensive_benchmark -- -f 2 -wi 3 -i 5
```

### Benchmark Configuration

#### JMH Parameters
```bash
# Warmup iterations: -wi <count>
# Measurement iterations: -i <count>  
# Forks: -f <count>
# Threads: -t <count>
# Output format: -rf json|csv|text
# Results file: -rff results.json

# Example: Quick benchmark
java -jar benchmarks.jar -wi 1 -i 3 -f 1 -rf json -rff quick_results.json

# Example: Thorough benchmark
java -jar benchmarks.jar -wi 5 -i 10 -f 3 -rf json -rff thorough_results.json
```

### C++ Benchmarks

#### Available C++ Benchmarks

**Main Benchmark Suite** (`bulk_insert_and_query`):
```bash
# Build and run comprehensive filter benchmark
bazel build @fastfilter_cpp//:bulk_insert_and_query
bazel run @fastfilter_cpp//:bulk_insert_and_query -- 100000

# Run via Maven integration
mvn exec:exec@bazel-run-cpp-benchmarks -Pbazel-benchmarks -Dbazel.benchmark.size=50000
```

**Available Filter Types Tested**:
- XOR8, XOR16 Filters 
- Cuckoo12, Cuckoo16 Filters
- Bloom12, Bloom16 Filters with batched operations
- BlockedBloom Filter
- XorBinaryFuse8, XorBinaryFuse16 Filters
- XorBinaryFuse 4-wise variants
- HomogRibbon64 Filters (8-bit and 15-bit)
- StandardRibbon64 Filters (8-bit and 15-bit)

**Benchmark Metrics**:
- Addition time (million keys/second)
- Lookup time at different false positive rates (0%, 25%, 50%, 75%, 100%)
- Memory efficiency (bits per item)
- Space overhead percentage

#### Example Output
```
                                     add  remove      0%     25%     50%     75%    100%  3*find      ε%  bits/item  bits/item  space%    keys
-batched add             .-.-.-.-.-.                                         Xor8   44.43    0.00    2.93    3.01    2.64    2.94    2.88   53.07  0.3710       9.84       8.07    21.9   0.100
-batched add             .-.-.-.-.-.                               XorBinaryFuse8   20.37    0.00    2.25    2.13    2.19    2.24    2.14   26.94  0.3900       9.50       8.00    18.7   0.100
```

## Troubleshooting

### Common Issues

#### 1. JDK Version Problems
```bash
# Problem: Wrong JDK version
java --version
# java 17.0.1 2021-10-19

# Solution: Set JAVA_HOME
export JAVA_HOME=/path/to/jdk-24
export PATH=$JAVA_HOME/bin:$PATH

# Verify
java --version
# openjdk 24-ea 2024-09-17
```

#### 2. Maven Compilation Errors
```bash
# Problem: Compilation failed with language level
[ERROR] Source option 24 is not supported. Use 21 or lower.

# Solution: Update Maven compiler plugin in pom.xml
<maven.compiler.release>24</maven.compiler.release>
<maven.compiler.plugin.version>3.13.0</maven.compiler.plugin.version>
```

#### 3. Bazel Build Failures
```bash
# Problem: "Target '//fastfilter:fastfilter' failed to build"
# Solution: Clean and rebuild
bazel clean
bazel build //fastfilter:fastfilter

# Problem: JDK not found
# Solution: Set explicit JDK in .bazelrc
echo "build --java_runtime_version=remotejdk_24" >> .bazelrc
```

#### 4. C++ Integration Issues
```bash
# Problem: "Failed to initialize C++ library"
# Check library exists
ls -la bazel-bin/fastfilter_cpp/libfastfilter_cpp_ffi.so

# Check dependencies
ldd bazel-bin/fastfilter_cpp/libfastfilter_cpp_ffi.so

# Set library path
export LD_LIBRARY_PATH=$PWD/bazel-bin/fastfilter_cpp:$LD_LIBRARY_PATH
```

#### 5. FFI Access Errors
```bash
# Problem: "IllegalCallerException: Access to restricted method"
# Solution: Enable native access
java --enable-native-access=ALL-UNNAMED --enable-preview YourApp

# For Bazel builds, add to .bazelrc:
build --jvmopt="--enable-native-access=ALL-UNNAMED"
test --jvmopt="--enable-preview"
```

#### 6. Performance Issues
```bash
# Problem: Poor C++ performance
# Check compiler optimizations
bazel build //fastfilter_cpp:fastfilter_cpp_ffi --compilation_mode=opt

# Verify AVX2 usage
objdump -d bazel-bin/fastfilter_cpp/libfastfilter_cpp_ffi.so | grep -i avx

# Check CPU features
grep avx2 /proc/cpuinfo
```

### Debug Build Options

#### Maven Debug
```bash
# Verbose Maven output
mvn -X compile

# Debug Maven tests  
mvn test -Dmaven.surefire.debug

# Skip compilation optimization
mvn compile -Doptimize=false
```

#### Bazel Debug
```bash
# Verbose Bazel output
bazel build //fastfilter:fastfilter --verbose_failures --sandbox_debug

# Debug mode build
bazel build //fastfilter:fastfilter --compilation_mode=dbg

# Show build commands
bazel build //fastfilter:fastfilter -s
```

### Performance Tuning

#### JVM Performance
```bash
# Heap size tuning
export MAVEN_OPTS="-Xms2g -Xmx8g"
export JAVA_OPTS="-Xms2g -Xmx8g -XX:+UseG1GC"

# For benchmarks
java -Xms4g -Xmx8g -XX:+UseG1GC -jar benchmarks.jar
```

#### Build Performance
```bash
# Parallel Maven builds
mvn compile -T 4

# Parallel Bazel builds
bazel build //... --jobs=auto

# Local build cache
bazel build //... --disk_cache=/tmp/bazel-cache
```

## Environment Configuration (.env Files)

FastFilter Java supports `.env` files for consistent environment configuration across all build systems (Maven, Bazel, Nix). This provides platform-specific settings while maintaining build reproducibility.

### Quick Setup

1. **Copy platform template**:
   ```bash
   # macOS ARM64 (Apple Silicon)
   cp .env.macos-arm64 .env
   
   # Linux x86_64  
   cp .env.linux-x86_64 .env
   
   # Windows x86_64
   cp .env.windows .env
   ```

2. **Customize for your system**:
   ```bash
   # Edit paths and settings
   nano .env
   ```

3. **Build automatically uses .env**:
   ```bash
   mvn compile    # Uses .env settings
   bazel build //...  # Uses .env settings  
   nix develop    # Sources .env in shell
   ```

### Platform Templates

**Available templates**:
- `.env.macos-arm64` - macOS Apple Silicon
- `.env.linux-x86_64` - Linux x86_64 with AVX2
- `.env.windows` - Windows x86_64

**Key settings in .env files**:
```bash
# JDK 24 configuration
JAVA_HOME=/path/to/jdk24
JVM_OPTS=--enable-preview --enable-native-access=ALL-UNNAMED

# Compiler settings
CC=/usr/bin/gcc
CXX=/usr/bin/g++
CFLAGS=-O3 -march=native -mavx2
CXXFLAGS=-O3 -march=native -mavx2 -std=c++17

# Library paths
LIBRARY_PATH=/usr/local/lib64:/usr/lib64
NATIVE_LIB_PATH=/usr/local/lib64:/usr/lib64

# Testing configuration
TEST_FFI_ENABLED=true
TEST_DOCKER_ENABLED=true
DOCKER_DEFAULT_PLATFORM=linux/amd64
```

### Build System Integration

**Maven**: Automatically loads `.env` via properties-maven-plugin
**Bazel**: Reads environment variables configured in `.bazelrc`
**Nix**: Sources `.env` files in development shell

See **[ENV_USAGE.md](ENV_USAGE.md)** for complete documentation and troubleshooting.

### Complete Environment Setup Script

Create `setup.sh`:
```bash
#!/bin/bash

# FastFilter Java Environment Setup

# Set JDK 24
export JAVA_HOME="/path/to/jdk-24"
export PATH="$JAVA_HOME/bin:$PATH"

# Maven configuration
export MAVEN_OPTS="-Xmx4g -XX:+UseG1GC"

# JVM options for FFI
export JAVA_OPTS="--enable-native-access=ALL-UNNAMED --enable-preview"

# C++ library path (after building)
export LD_LIBRARY_PATH="$PWD/bazel-bin/fastfilter_cpp:$LD_LIBRARY_PATH"

# Verify setup
echo "Java version:"
java --version
echo "Maven version:"
mvn --version
echo "Bazel version:"
bazel version

echo "Environment configured for FastFilter Java development!"
```

Run with:
```bash
chmod +x setup.sh
source setup.sh
```

## Summary

### ✅ Fully Working (Recommended)
**Maven Build System**:
- Complete Java library compilation ✅
- All JUnit 5 tests passing ✅  
- JMH benchmarks functional ✅
- Cross-platform support (Linux, macOS, Windows) ✅
- JDK 21+ compatible ✅

```bash
# Complete working build:
mvn clean compile test
cd jmh && mvn clean package && java -jar target/benchmarks.jar
```

### ✅ Working (New!)
**Maven + Bazel Integration**:
- Maven profiles for Bazel execution ✅
- C++ benchmark integration ✅
- External fastfilter_cpp repository ✅
- Cross-platform compilation ✅

**Platform-Specific Native Libraries**:
- Multi-platform shared library build configuration ✅
- Native library auto-loading with platform detection ✅
- Platform-specific JAR generation ✅
- Release build with all platforms ✅

```bash
# Maven Bazel integration:
mvn compile -Pbazel -Dbazel=true                                              # Build Java + C++
mvn exec:exec@bazel-run-cpp-benchmarks -Pbazel-benchmarks -Dbazel.benchmark.size=25000   # C++ benchmarks

# Native library builds:
mvn compile -Pbuild.native=true                                               # Build for current platform
mvn package -Pnative-cross-compile -Dbuild.native.cross=true                 # Build all platforms
mvn package -PperformRelease=true                                            # Release build with natives
```

### 🚧 In Progress
**Bazel Build System**:
- Basic Java library builds ✅
- External fastfilter_cpp repository integration ✅
- C++ core library compilation ✅
- C++ benchmark compilation and execution ✅
- C++ FFI integration code created ✅
- Modern MODULE.bazel configuration ✅
- Platform-specific AVX2 compilation fixes ✅
- Test integration needs toolchain fixes ⚠️
- FFI wrapper linkage issues ⚠️

```bash
# Working builds:
bazel build //fastfilter:fastfilter                    # Java library
bazel build @fastfilter_cpp//:fastfilter_cpp_core     # C++ core library
bazel build @fastfilter_cpp//:bulk_insert_and_query   # C++ benchmarks
bazel run @fastfilter_cpp//:bulk_insert_and_query -- 100000  # Run C++ benchmarks

# In progress:
bazel build @fastfilter_cpp//:fastfilter_cpp_ffi      # FFI wrapper (linkage issues)
bazel test //fastfilter:all_tests                     # Test suite (dependency conflicts)
```

## Native Library Usage

### Automatic Platform Detection

The FastFilter project now includes automatic platform detection and native library loading:

```java
import org.fastfilter.cpp.Xor8Filter;
import org.fastfilter.cpp.BinaryFuse8Filter;
import org.fastfilter.cpp.NativeLibraryLoader;

// Check platform and library status
System.out.println(NativeLibraryLoader.getPlatformInfo());

// Use C++ filters - libraries load automatically
long[] keys = {1, 2, 3, 4, 5};
Xor8Filter filter = new Xor8Filter(keys);        // C++ XOR8 implementation
BinaryFuse8Filter fuse = new BinaryFuse8Filter(keys);  // C++ Binary Fuse8

// Check if key exists (with C++ performance)
boolean exists = filter.mayContain(3);  // Fast C++ lookup
```

### Supported Platforms

| Platform | Architecture | AVX2 Support | Library File |
|----------|-------------|--------------|--------------|
| Linux | x86_64 | ✅ | `libfastfilter_cpp_ffi.so` |
| macOS | x86_64 (Intel) | ✅ | `libfastfilter_cpp_ffi.dylib` |
| macOS | ARM64 (Apple Silicon) | ❌ | `libfastfilter_cpp_ffi.dylib` |
| Windows | x86_64 | ✅ | `fastfilter_cpp_ffi.dll` |

### Maven Dependencies

When releasing, include platform-specific dependencies:

```xml
<dependencies>
  <!-- Core FastFilter library -->
  <dependency>
    <groupId>io.github.fastfilter</groupId>
    <artifactId>fastfilter</artifactId>
    <version>1.0.3-SNAPSHOT</version>
  </dependency>

  <!-- Platform-specific native libraries (choose one or include all) -->
  <dependency>
    <groupId>io.github.fastfilter</groupId>
    <artifactId>fastfilter-native-linux-x86_64</artifactId>
    <version>1.0.3-SNAPSHOT</version>
    <classifier>linux-x86_64</classifier>
  </dependency>
  
  <dependency>
    <groupId>io.github.fastfilter</groupId>
    <artifactId>fastfilter-native-macos-arm64</artifactId>
    <version>1.0.3-SNAPSHOT</version>
    <classifier>macos-arm64</classifier>
  </dependency>
  
  <!-- Add other platforms as needed -->
</dependencies>
```

### Library Loading Priority

1. **System Library**: Checks `java.library.path` and system paths
2. **Explicit Path**: Uses `fastfilter.cpp.library.path` system property  
3. **Embedded Library**: Extracts from JAR to temporary directory
4. **Fallback**: Java-only implementations (slower but compatible)

```bash
# Override library location
java -Dfastfilter.cpp.library.path=/path/to/native/libs YourApp

# Disable native libraries (use Java-only)
java -Dfastfilter.native.disable=true YourApp
```

### 📋 Current Limitations & Workarounds

1. **Bazel Test Integration**: Tests require additional Java toolchain configuration
   - **Workaround**: Use Maven for testing: `mvn test`

2. **C++ FFI Integration**: Requires JDK 24 with preview features enabled  
   - **Workaround**: Use Java-only implementations, comparable performance for most use cases

3. **Cross-Platform Bazel Builds**: Platform-specific shared library linking in progress
   - **Workaround**: Use Maven for native library packaging and distribution

### 🎯 Recommended Workflow

1. **Development**: Use Maven for reliable builds and testing
2. **Benchmarking**: Use Maven JMH benchmarks for performance analysis  
3. **Production**: Use Maven-built JAR files for deployment
4. **Future**: Bazel integration will provide enhanced C++ performance when completed

---

This build guide covers the complete FastFilter Java project with working Maven builds and in-progress Bazel integration. The Maven build system provides full functionality while Bazel integration continues development for enhanced C++ performance features.