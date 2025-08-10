# Native Library Loader with Platform-Specific LibC Detection

A high-performance, cross-platform native library loader for JDK 24's Foreign Function & Memory API with comprehensive LibC detection and architecture-specific configuration support.

## 🚀 Features

- **Singleton Pattern**: Thread-safe global instance for library management
- **Multi-Architecture Support**: x86_64, ARM64, ARMv7, RISC-V64, s390x, PowerPC64
- **LibC Detection**: Automatic detection of glibc, musl, uClibc, Bionic, Darwin, MSVCRT
- **Platform Intelligence**: Architecture-specific library paths and naming conventions
- **Environment Configuration**: Flexible configuration via environment variables, properties, and config files
- **Resource Extraction**: Embedded native libraries in JAR with checksums
- **QEMU Testing**: Cross-platform testing without physical hardware
- **CI/CD Ready**: Full GitHub Actions integration

## 📋 Table of Contents

- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [Configuration](#configuration)
- [Testing](#testing)
    - [Local Testing](#local-testing)
    - [QEMU Testing](#qemu-testing)
    - [Docker Testing](#docker-testing)
    - [CI/CD](#cicd)
- [Platform Support](#platform-support)
- [Development](#development)
- [Troubleshooting](#troubleshooting)

## Quick Start

### Installation

#### Maven
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>native-library-loader</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### Homebrew (macOS)
```bash
# Install the library and development tools
brew tap example/native-library-loader
brew install native-library-loader

# Or install directly from formula
brew install --HEAD https://raw.githubusercontent.com/example/native-library-loader/main/Formula/native-library-loader.rb
```

### Basic Usage

```java
import com.example.ffi.platform.*;

// Get singleton loader instance
NativeLibraryLoader loader = NativeLibraryLoader.getInstance();

// Detect LibC type
LibCInfo libc = LibCType.detectCurrent();
System.out.println("Detected: " + libc.getType()); // GLIBC, MUSL, etc.

// Load native library
SymbolLookup myLib = loader.loadLibrary("mylib");

// Load versioned library
SymbolLookup opencv = loader.loadLibrary("opencv", "4.5.0");
```

## Architecture

### Core Components

```
com.example.ffi.platform/
├── PlatformInfo.java          # OS/Architecture detection
├── LibCType.java              # LibC implementation detection
├── NativeLibraryLoader.java   # Singleton library loader
├── NativeLibraryConfig.java   # Configuration management
└── FFIHelper.java             # JDK 24 FFI integration
```

### LibC Detection Strategy

The system uses multiple detection strategies in order of accuracy:

1. **FFI-based**: Direct symbol lookup using JDK 24 FFI (most accurate)
2. **Command-based**: Execute `ldd --version` and parse output
3. **File-based**: Check for LibC-specific files (`/etc/alpine-release`, etc.)
4. **Fallback**: OS properties and heuristics

## Configuration

### Configuration Hierarchy

Configuration is loaded in the following priority order:

1. **Environment Variables** (highest priority)
2. **System Properties**
3. **Property Files** in resources
4. **Default Platform Paths** (lowest priority)

### Environment Variables

```bash
# Platform-specific paths (highest priority)
export NATIVE_LIB_PATH_LINUX_X86_64=/opt/libs:/usr/local/lib64

# OS-specific paths
export NATIVE_LIB_PATH_LINUX=/usr/lib:/usr/local/lib

# Generic paths (all platforms)
export NATIVE_LIB_PATH=/custom/libs
```

### Property Files

Create platform-specific property files in `src/main/resources/`:

#### `/native-library-config.properties` (Global)
```properties
# Search paths
search.paths=/usr/local/lib:/usr/lib:${HOME}/lib
extract.dir=${java.io.tmpdir}/native-libs
delete.on.exit=true
enable.caching=true
verify.checksums=true

# Library name mappings
library.mapping.opencv=opencv_java453
library.mapping.sqlite=sqlite3
```

#### Platform-Specific Configurations

- `/native-library-config-linux.properties` - Linux-specific
- `/native-library-config-linux-x86_64.properties` - Linux x86_64
- `/native-library-config-darwin.properties` - macOS
- `/native-library-config-darwin-aarch64.properties` - Apple Silicon
- `/native-library-config-windows.properties` - Windows

### Using .env Files

Install the DotENV VS Code extension and create `.env`:

```bash
# Compiler Configuration
CXX_COMPILER=/opt/homebrew/opt/llvm/bin/clang++
CC_COMPILER=/opt/homebrew/opt/llvm/bin/clang

# Build Settings
CXX_STANDARD=c++23
WARNING_FLAGS=-Wall -Wextra -Wpedantic
DEBUG_FLAGS=-g -O0
RELEASE_FLAGS=-O3 -DNDEBUG

# Library Paths
INCLUDE_PATH_1=/opt/homebrew/include
LIBRARY_PATH_1=/opt/homebrew/lib
```

## Testing

### Prerequisites

#### macOS Setup with Homebrew

```bash
# Install Xcode Command Line Tools (if not already installed)
xcode-select --install

# Install Homebrew (if not already installed)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install required development tools
brew update
brew upgrade

# Core development tools
brew install maven gradle
brew install openjdk@21
brew install cmake ninja

# Compilers and toolchains
brew install llvm gcc
brew install gcc-cross-toolchains/avr/avr-gcc@11  # Cross-compilation

# LibC and native libraries
brew install musl-cross
brew install glibc

# QEMU for cross-platform testing
brew install qemu
brew install --cask docker  # For container testing

# Debugging and analysis tools
brew install gdb lldb
brew install valgrind
brew install strace
brew install binutils
brew install coreutils

# Additional tools for library management
brew install patchelf
brew install dylibbundler
brew install otool-ng

# Install act for local GitHub Actions testing
brew install act

# Nix package manager (optional, for reproducible builds)
brew install nix

# Set up environment variables
echo 'export PATH="/opt/homebrew/opt/llvm/bin:$PATH"' >> ~/.zshrc
echo 'export LDFLAGS="-L/opt/homebrew/opt/llvm/lib"' >> ~/.zshrc
echo 'export CPPFLAGS="-I/opt/homebrew/opt/llvm/include"' >> ~/.zshrc
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc

# Apple Silicon specific
if [[ $(uname -m) == 'arm64' ]]; then
    echo 'export PATH="/opt/homebrew/bin:$PATH"' >> ~/.zshrc
    echo 'export LIBRARY_PATH="/opt/homebrew/lib:$LIBRARY_PATH"' >> ~/.zshrc
fi

# Intel Mac specific
if [[ $(uname -m) == 'x86_64' ]]; then
    echo 'export PATH="/usr/local/bin:$PATH"' >> ~/.zshrc
    echo 'export LIBRARY_PATH="/usr/local/lib:$LIBRARY_PATH"' >> ~/.zshrc
fi

# Reload shell configuration
source ~/.zshrc

# Verify installations
java --version
mvn --version
qemu-system-aarch64 --version
docker --version
act --version
```

#### Linux Setup (Ubuntu/Debian)

```bash
# Update package manager
sudo apt-get update

# Install development tools
sudo apt-get install -y \
    build-essential \
    maven \
    gradle \
    openjdk-21-jdk \
    cmake \
    ninja-build

# Install compilers and cross-compilation tools
sudo apt-get install -y \
    gcc \
    g++ \
    clang \
    llvm \
    gcc-aarch64-linux-gnu \
    gcc-arm-linux-gnueabihf \
    gcc-riscv64-linux-gnu

# Install QEMU for multi-arch testing
sudo apt-get install -y \
    qemu-user-static \
    binfmt-support \
    qemu-system-arm \
    qemu-system-aarch64 \
    qemu-system-riscv64

# Install Docker
curl -fsSL https://get.docker.com | sudo bash
sudo usermod -aG docker $USER

# Install debugging tools
sudo apt-get install -y \
    gdb \
    valgrind \
    strace \
    ltrace \
    binutils

# Install act for GitHub Actions testing
curl https://raw.githubusercontent.com/nektos/act/master/install.sh | sudo bash
```

#### Windows Setup

```powershell
# Install Chocolatey package manager
Set-ExecutionPolicy Bypass -Scope Process -Force
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# Install development tools
choco install -y openjdk21
choco install -y maven
choco install -y gradle
choco install -y git
choco install -y vscode

# Install Docker Desktop
choco install -y docker-desktop

# Install WSL2 for Linux testing
wsl --install

# Install MSYS2 for Unix-like environment
choco install -y msys2

# Install debugging tools
choco install -y windbg
choco install -y sysinternals
```

### Local Testing

#### 1. Run Unit Tests

```bash
# Basic unit tests
mvn clean test

# Specific test class
mvn test -Dtest=LibCTypeTest

# With coverage
mvn clean test jacoco:report
```

#### 2. Run LibC Detection

```bash
# Compile and run detection
mvn compile exec:java -Dexec.mainClass="com.example.ffi.platform.LibCType"
```

### macOS-Specific Testing

#### Testing on Apple Silicon (M1/M2/M3)

```bash
# Verify architecture
uname -m  # Should output: arm64

# Test native ARM64 libraries
brew install --build-from-source your-library

# Test x86_64 libraries with Rosetta 2
arch -x86_64 /bin/bash
# Now running in x86_64 mode
java -version  # Will use x86_64 JVM

# Test universal binaries
lipo -info /path/to/library.dylib
# Output: Architectures in the fat file: library.dylib are: x86_64 arm64

# Create universal binary from separate architectures
lipo -create -output universal.dylib x86_64.dylib arm64.dylib
```

#### macOS Library Management

```bash
# Find library dependencies
otool -L library.dylib

# Fix library paths
install_name_tool -change /old/path/lib.dylib @rpath/lib.dylib library.dylib

# Add rpath
install_name_tool -add_rpath @loader_path/../lib library.dylib

# Verify code signing
codesign -v library.dylib

# Sign library (required for macOS 10.15+)
codesign --sign - library.dylib

# Bundle libraries for distribution
dylibbundler -od -b -x ./MyApp.app/Contents/MacOS/MyApp -d ./MyApp.app/Contents/libs/

# Test different macOS versions
xcrun --sdk macosx --show-sdk-version
xcrun --sdk macosx11.0 clang++ -o test test.cpp
```

#### Homebrew Formula for Your Library

Create `Formula/native-library-loader.rb`:

```ruby
class NativeLibraryLoader < Formula
  desc "Cross-platform native library loader with LibC detection"
  homepage "https://github.com/example/native-library-loader"
  url "https://github.com/example/native-library-loader/archive/v1.0.0.tar.gz"
  sha256 "YOUR_SHA256_HASH_HERE"
  license "MIT"
  head "https://github.com/example/native-library-loader.git", branch: "main"

  depends_on "maven" => :build
  depends_on "openjdk@21"
  depends_on "llvm" => :recommended
  depends_on "cmake" => :build

  # Apple Silicon specific dependencies
  on_macos do
    on_arm do
      depends_on "gcc" => :build
    end
  end

  def install
    ENV["JAVA_HOME"] = Language::Java.java_home("21")
    
    system "mvn", "clean", "package", "-DskipTests"
    
    libexec.install Dir["target/*.jar"]
    bin.write_jar_script libexec/"native-library-loader-#{version}.jar", "native-loader"
    
    # Install native libraries
    lib.install Dir["target/native/darwin-*/*.dylib"]
    
    # Install headers
    include.install Dir["src/main/c/include/*.h"]
  end

  test do
    system "#{bin}/native-loader", "--version"
    
    # Test LibC detection
    output = shell_output("#{bin}/native-loader --detect-libc")
    assert_match "Darwin", output
  end
end
```

Deploy your formula:

```bash
# Create tap repository
mkdir homebrew-tap
cd homebrew-tap
mkdir Formula
cp native-library-loader.rb Formula/

# Push to GitHub
git init
git add .
git commit -m "Add native-library-loader formula"
git remote add origin https://github.com/example/homebrew-tap
git push -u origin main

# Users can now install with:
brew tap example/tap
brew install native-library-loader
```

### QEMU Testing

#### Setup QEMU

```bash
# Install QEMU and binfmt support
sudo apt-get update
sudo apt-get install -y qemu-user-static binfmt-support

# Register QEMU handlers
docker run --rm --privileged multiarch/qemu-user-static --reset -p yes

# Verify installation
docker run --rm arm64v8/alpine:latest uname -m  # Should output: aarch64
```

#### Run Cross-Architecture Tests

```bash
# Test Alpine (musl) on ARM64
docker run --rm --platform linux/arm64 alpine:latest sh -c \
  "ldd --version 2>&1 | grep -i musl"

# Test Ubuntu (glibc) on ARM64
docker run --rm --platform linux/arm64 ubuntu:22.04 sh -c \
  "ldd --version | head -1"

# Run QEMU tests with Maven
mvn test -Dtest=LibCTypeQemuTest -Dtest.qemu=true
```

### Docker Testing

#### Test Different LibC Implementations

```bash
# Alpine Linux (musl)
docker run --rm alpine:latest sh -c \
  "echo 'LibC:' && ldd --version 2>&1 || echo 'musl detected'"

# Ubuntu (glibc)
docker run --rm ubuntu:22.04 sh -c \
  "echo 'LibC:' && ldd --version | head -1"

# Run Docker integration tests
mvn test -Dtest=LibCDetectionDockerTest -Dtest.docker=true
```

#### Docker Compose Multi-Arch Testing

```yaml
# docker-compose.yml
version: '3.8'
services:
  alpine-x64:
    image: alpine:latest
    platform: linux/amd64
    command: sleep 3600
    
  alpine-arm64:
    image: alpine:latest
    platform: linux/arm64
    command: sleep 3600
    
  ubuntu-x64:
    image: ubuntu:22.04
    platform: linux/amd64
    command: sleep 3600
```

Run: `docker-compose up -d` then test each container.

### Nix Testing

#### Using Nix Flake

```bash
# Enter development shell
nix develop

# Build for different architectures
nix build .#detector-aarch64
nix build .#detector-musl-x64

# Run tests
nix run .#default
```

#### Classic Nix Shell

```bash
# Enter shell with all tools
nix-shell

# Available tools: QEMU, cross-compilers, Docker
qemu-system-aarch64 --version
```

### CI/CD

#### GitHub Actions Setup

1. **Enable GitHub Actions**
   ```
   Settings → Actions → General → Allow all actions
   ```

2. **Add Workflow Files**
   Create `.github/workflows/libc-detection-tests.yml`

3. **Configure Secrets**
   ```
   Settings → Secrets → Actions
   - CACHIX_AUTH_TOKEN (optional)
   - CODECOV_TOKEN (optional)
   ```

4. **Run Tests**
   Tests run automatically on:
    - Push to main/develop
    - Pull requests
    - Daily schedule (2 AM UTC)
    - Manual trigger

#### Local CI Testing with act

```bash
# Install act
brew install act  # macOS
curl https://raw.githubusercontent.com/nektos/act/master/install.sh | sudo bash  # Linux

# Run workflows locally
act -j unit-tests
act pull_request
act -s GITHUB_TOKEN=$GITHUB_TOKEN
```

## Platform Support

### Supported Platforms

| OS | Architecture | LibC | Library Extension | Homebrew Support | Status |
|---|---|---|---|---|---|
| macOS | x86_64 | Darwin | .dylib | ✅ Native | ✅ Full |
| macOS | ARM64 (M1/M2/M3) | Darwin | .dylib | ✅ Native | ✅ Full |
| Linux | x86_64 | glibc, musl | .so | ✅ Linuxbrew | ✅ Full |
| Linux | ARM64 | glibc, musl | .so | ✅ Linuxbrew | ✅ Full |
| Linux | ARMv7 | glibc, musl, uclibc | .so | ⚠️ Limited | ✅ Full |
| Linux | RISC-V64 | glibc, musl | .so | ❌ | ⚠️ Experimental |
| Windows | x86_64 | MSVCRT | .dll | ❌ | ✅ Full |
| Windows | ARM64 | MSVCRT | .dll | ❌ | ⚠️ Experimental |
| Android | ARM64 | Bionic | .so | ❌ | ⚠️ Experimental |
| FreeBSD | x86_64 | BSD libc | .so | ✅ Ports | ⚠️ Experimental |

### LibC Compatibility Matrix

| LibC | Version Range | Features | Use Cases |
|---|---|---|---|
| glibc | 2.17 - 2.38 | Full POSIX, GNU extensions, NSS | Standard Linux |
| musl | 1.1.0 - 1.2.4 | Lightweight, static-friendly | Alpine, embedded |
| uClibc | 0.9.33 - 1.0.42 | Configurable, small | Embedded systems |
| Bionic | API 21+ | Android-optimized | Android devices |
| Darwin | macOS 10.15+ | Frameworks, GCD | macOS/iOS |
| MSVCRT | Windows 7+ | Windows CRT | Windows apps |

### Default Library Paths

#### macOS
- **Intel Macs**:
    - `/usr/local/lib` (Homebrew default)
    - `/usr/local/opt/*/lib` (Homebrew kegs)
    - `/usr/lib` (System)
    - `/System/Library/Frameworks`
- **Apple Silicon (M1/M2/M3)**:
    - `/opt/homebrew/lib` (Homebrew default)
    - `/opt/homebrew/opt/*/lib` (Homebrew kegs)
    - `/usr/lib` (System)
    - `/System/Library/Frameworks`
- **Universal**:
    - `~/Library/Frameworks` (User)
    - `/Library/Frameworks` (System-wide)

#### Linux
- **glibc x86_64**: `/usr/lib64`, `/lib64`, `/usr/lib/x86_64-linux-gnu`
- **glibc ARM64**: `/usr/lib/aarch64-linux-gnu`
- **musl**: `/usr/lib`, `/lib` (no multiarch)
- **Linuxbrew**: `/home/linuxbrew/.linuxbrew/lib`

#### Windows
- `C:\Windows\System32`, `C:\Windows\SysWOW64`
- `%ProgramFiles%`, `%ProgramFiles(x86)%`
- `C:\msys64\mingw64\bin` (MSYS2)

## Development

### Project Structure

```
project/
├── src/
│   └── main/
│       ├── java/           # Java sources
│       └── resources/      # Config files
│           ├── native-library-config.properties
│           └── native/     # Embedded libraries
│               ├── linux-x86_64/
│               ├── darwin-aarch64/
│               └── windows-x86_64/
├── .github/
│   └── workflows/          # CI/CD workflows
├── .env.example            # Environment template
├── docker-compose.yml      # Multi-arch testing
├── flake.nix              # Nix configuration
└── pom.xml                # Maven configuration
```

### Building from Source

#### macOS Build

```bash
# Clone repository
git clone https://github.com/example/native-library-loader
cd native-library-loader

# Ensure correct Java version
brew install openjdk@21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Build with Maven
mvn clean package

# Build native components with CMake
mkdir build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
make -j$(sysctl -n hw.ncpu)

# Create macOS app bundle (optional)
jpackage \
  --input target \
  --main-jar native-library-loader.jar \
  --main-class com.example.ffi.platform.Main \
  --type app-image \
  --name "NativeLibraryLoader" \
  --app-version 1.0.0 \
  --vendor "Example Inc" \
  --mac-package-identifier com.example.native-loader \
  --mac-sign \
  --mac-signing-key-user-name "Developer ID Application: Your Name"

# Notarize for distribution (requires Apple Developer account)
xcrun notarytool submit NativeLibraryLoader.app.zip \
  --apple-id your-apple-id@example.com \
  --team-id YOUR_TEAM_ID \
  --wait
```

#### Linux Build

```bash
# Clone repository
git clone https://github.com/example/native-library-loader
cd native-library-loader

# Build with Maven
mvn clean package

# Run tests
mvn test

# Generate documentation
mvn javadoc:javadoc
```

#### Windows Build

```powershell
# Clone repository
git clone https://github.com/example/native-library-loader
cd native-library-loader

# Build with Maven
mvn clean package

# Create Windows installer
jpackage `
  --input target `
  --main-jar native-library-loader.jar `
  --main-class com.example.ffi.platform.Main `
  --type msi `
  --name "NativeLibraryLoader" `
  --app-version 1.0.0 `
  --vendor "Example Inc" `
  --win-dir-chooser `
  --win-menu `
  --win-shortcut
```

### VS Code Setup

1. Install extensions:
    - Java Extension Pack
    - DotENV
    - C/C++ Extension Pack

2. Copy configuration files to `.vscode/`:
    - `settings.json`
    - `tasks.json`
    - `launch.json`
    - `c_cpp_properties.json`

3. Create `.env` file from `.env.example`

### Running Benchmarks

```bash
# JMH benchmarks
mvn clean compile
mvn exec:java -Dexec.mainClass="com.example.ffi.platform.LibCBenchmark"
```

## Troubleshooting

### Common Issues

#### Library Not Found

```java
// Check search paths
NativeLibraryConfig config = loader.getConfiguration();
System.out.println("Search paths: " + config.getSearchPaths());

// Add custom path
loader.addSearchPath(Paths.get("/custom/lib"));
```

**macOS Specific:**
```bash
# Check System Integrity Protection (SIP) status
csrutil status

# If library blocked by Gatekeeper
xattr -d com.apple.quarantine library.dylib

# Check library architecture
file library.dylib
lipo -info library.dylib

# Verify library is signed
codesign -dv library.dylib

# Sign unsigned library
codesign --sign - --force library.dylib
```

#### Permission Denied

```bash
# Check file permissions
ls -la /path/to/library.so

# Fix permissions
chmod 755 library.so
```

#### Wrong LibC Version

```bash
# Linux - Check library dependencies
ldd library.so

# macOS - Check library dependencies
otool -L library.dylib

# macOS - Check which libraries are loaded at runtime
DYLD_PRINT_LIBRARIES=1 java -jar myapp.jar

# macOS - Fix library paths
install_name_tool -change /old/path/lib.dylib @rpath/lib.dylib library.dylib

# Windows - Check dependencies
dumpbin /dependents library.dll

# Universal tool (requires Homebrew)
brew install tree
tree /opt/homebrew/lib  # Apple Silicon
tree /usr/local/lib     # Intel Mac
```

#### QEMU Not Working

```bash
# Re-register QEMU handlers
docker run --rm --privileged multiarch/qemu-user-static --reset -p yes

# Check registered architectures
ls -la /proc/sys/fs/binfmt_misc/
```

### Debug Mode

```java
// Enable debug logging
System.setProperty("java.util.logging.level", "FINE");

// Check detected platform
PlatformInfo platform = PlatformInfo.getInstance();
System.out.println("Platform: " + platform.getPlatformString());
System.out.println("LibC: " + LibCType.detectCurrent());
```

## Performance

### Typical Load Times

- First library load: 50-200ms (includes detection)
- Cached library load: <5ms
- Resource extraction: 100-500ms (one-time)
- LibC detection: 10-50ms (cached after first call)

### Memory Usage

- Base overhead: ~2MB
- Per loaded library: ~100KB metadata
- Extracted libraries: Varies by library size

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Support

- **Issues**: [GitHub Issues](https://github.com/example/native-library-loader/issues)
- **Discussions**: [GitHub Discussions](https://github.com/example/native-library-loader/discussions)
- **Documentation**: [Wiki](https://github.com/example/native-library-loader/wiki)

## Acknowledgments

- JDK 24 Foreign Function & Memory API team
- QEMU project for cross-platform emulation
- Alpine Linux for musl testing environment
- Contributors and testers