# Environment Configuration (.env Files)

FastFilter Java uses `.env` files to configure build environments across different platforms and build systems. This allows for consistent configuration between Bazel, Maven, and Nix.

## Quick Start

1. Copy the appropriate platform template:
   ```bash
   # macOS ARM64 (Apple Silicon)
   cp .env.macos-arm64 .env
   
   # Linux x86_64
   cp .env.linux-x86_64 .env
   
   # Windows x86_64
   cp .env.windows .env
   ```

2. Customize the `.env` file for your system
3. Build with your preferred system - environment variables are automatically loaded

## Platform-Specific Templates

### macOS ARM64 (Apple Silicon) - `.env.macos-arm64`
- JDK 24 via Homebrew
- Clang compiler with ARM64 optimizations
- Native library paths for macOS

### Linux x86_64 - `.env.linux-x86_64` 
- JDK 24 via system package manager
- GCC with AVX2 optimizations
- Cross-compilation toolchain support
- QEMU integration for testing

### Windows x86_64 - `.env.windows`
- JDK 24 via Eclipse Adoptium
- MSYS2/MinGW-w64 compiler (recommended)
- Visual Studio compiler support (alternative)
- Docker Desktop integration

## Build System Integration

### Bazel
`.bazelrc` automatically loads environment variables from `.env`:
```bash
bazel build //...  # Uses .env automatically
```

### Maven  
`pom.xml` includes properties-maven-plugin to load `.env` files:
```bash
mvn compile  # Uses .env automatically
```

### Nix
`flake.nix` sources `.env` files in the development shell:
```bash
nix develop  # Sources .env automatically
```

## Key Configuration Variables

### JDK Settings
```bash
JAVA_HOME=/path/to/jdk24
JVM_OPTS=--enable-preview --enable-native-access=ALL-UNNAMED
MAVEN_OPTS=--enable-preview --enable-native-access=ALL-UNNAMED -Xmx4g
```

### Compiler Configuration
```bash
CC=/usr/bin/gcc
CXX=/usr/bin/g++
CFLAGS=-O3 -march=native -mavx2 -Wall -Wextra
CXXFLAGS=-O3 -march=native -mavx2 -Wall -Wextra -std=c++17
```

### Library Paths
```bash
LIBRARY_PATH=/usr/local/lib64:/usr/lib64
LD_LIBRARY_PATH=/usr/local/lib64:/usr/lib64
NATIVE_LIB_PATH=/usr/local/lib64:/usr/lib64
```

### Testing Configuration
```bash
TEST_FFI_ENABLED=true
TEST_DOCKER_ENABLED=true
TEST_QEMU_ENABLED=true
DOCKER_DEFAULT_PLATFORM=linux/amd64
```

## Platform Detection

The build systems automatically detect your platform and load the appropriate `.env` file:

1. **Platform-specific**: `.env.${os.name}-${os.arch}` (e.g., `.env.linux-x86_64`)
2. **Generic**: `.env` (fallback/override)

## Cross-Platform Development

### Linux Cross-Compilation
Enable cross-compilation toolchains in `.env.linux-x86_64`:
```bash
ARM64_CC=aarch64-linux-gnu-gcc
ARM64_CXX=aarch64-linux-gnu-g++
ARMHF_CC=arm-linux-gnueabihf-gcc
ARMHF_CXX=arm-linux-gnueabihf-g++
```

### QEMU Testing
Configure QEMU for architecture emulation:
```bash
QEMU_LD_PREFIX=/usr/aarch64-linux-gnu
QEMU_CPU=cortex-a72
TEST_QEMU_ENABLED=true
```

### Docker Multi-Platform
Set up Docker for multi-architecture builds:
```bash
DOCKER_BUILDKIT=1
DOCKER_DEFAULT_PLATFORM=linux/amd64
```

## Performance Tuning

### JMH Benchmarks
```bash
JMH_FORKS=3
JMH_WARMUP_ITERATIONS=5
JMH_MEASUREMENT_ITERATIONS=10
JMH_THREADS=4
```

### Build Parallelization
```bash
BAZEL_BUILD_OPTS=--jobs=auto --compilation_mode=opt
NINJA_JOBS=8
CMAKE_BUILD_TYPE=Release
```

## Troubleshooting

### JDK 24 Not Found
If JDK 24 is not available, the build will fall back to JDK 21:
```bash
# Verify JDK version
java --version
javac --version
```

### Compiler Not Found
Ensure compilers are installed and paths are correct:
```bash
# Linux
sudo apt-get install build-essential
# macOS
xcode-select --install
# Windows
# Install MSYS2 or Visual Studio Build Tools
```

### Native Library Loading Issues
Check library paths and permissions:
```bash
# Linux/macOS
export LD_LIBRARY_PATH="${NATIVE_LIB_PATH}"
ldd path/to/library.so

# Windows
echo %PATH%
dumpbin /dependents path\to\library.dll
```

### Environment Not Loading
Verify `.env` file exists and has correct permissions:
```bash
ls -la .env*
# Should be readable (644 permissions)
```

## Examples

### Complete Linux Setup
```bash
# Install JDK 24
sudo apt-get install openjdk-24-jdk

# Copy template
cp .env.linux-x86_64 .env

# Customize JAVA_HOME
echo "JAVA_HOME=/usr/lib/jvm/java-24-openjdk-amd64" >> .env

# Build
bazel build //...
mvn compile
```

### macOS Development
```bash
# Install JDK 24
brew install openjdk@24

# Copy template  
cp .env.macos-arm64 .env

# Update JAVA_HOME
echo "JAVA_HOME=$(brew --prefix openjdk@24)/libexec/openjdk.jdk/Contents/Home" >> .env

# Build with Nix
nix develop
bazel build //...
```

### Windows Setup
```powershell
# Install JDK 24 from Eclipse Adoptium
# Install MSYS2

# Copy template
copy .env.windows .env

# Edit .env to match your installation paths
notepad .env

# Build
bazel build //...
mvn compile
```