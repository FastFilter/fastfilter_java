# FastFilter FFI Java Bindings

This module provides Java Foreign Function Interface (FFI) bindings for the FastFilter C++ library using JDK 24's new Foreign Function & Memory API.

## Native Library Loading System

The module implements a sophisticated native library loading system with multiple loading strategies, security features, and comprehensive platform support.

### Architecture Overview

The native library loading system consists of several key components:

1. **NativeLibraryLoaderInterface** - Common interface for all loading strategies
2. **NativeLibraryLoader** - Original static-based loader (legacy compatibility)
3. **NativeLibraryLoaderAdapter** - Bridge between static loader and new interface
4. **NativeLibraryLoaderV2** - Advanced singleton loader with enhanced features
5. **NativeLibraryConfig** - Configuration management system
6. **PlatformInfo** - Comprehensive platform detection

### Loading Strategies

#### 1. Static Legacy Loader (NativeLibraryLoader)
- Simple static-based approach
- Embedded library extraction
- Basic platform detection
- Maintained for backward compatibility

#### 2. Advanced Singleton Loader (NativeLibraryLoaderV2)
- Singleton pattern for resource management
- Multiple loading strategies (system, embedded, explicit path)
- SHA-256 checksum verification
- Configurable behavior via properties
- Comprehensive logging and error reporting
- Platform-specific optimization detection (AVX2, NEON, etc.)

### Security Features

#### SHA-256 Checksum Verification
All native libraries include SHA-256 checksums generated during the build process:

```bash
# Generated checksums for each platform:
# - libfastfilter_cpp_ffi.so.sha256      (Linux)
# - libfastfilter_cpp_ffi.dylib.sha256   (macOS)
# - fastfilter_cpp_ffi.dll.sha256        (Windows)
```

The advanced loader verifies these checksums during library loading to ensure integrity:

```java
NativeLibraryLoaderV2 loader = NativeLibraryLoaderV2.getInstance();
// Checksum verification enabled by default in configuration
loader.loadLibrary("fastfilter_cpp_ffi");
```

#### Library Extraction Security
- Temporary files created with secure permissions
- Atomic file operations to prevent corruption
- Cleanup on JVM shutdown
- Protection against path traversal attacks

### Configuration System

The configuration system supports multiple sources in order of precedence:

1. **Architecture-specific config**: `/native-library-config-{os}-{arch}.properties`
2. **Platform-specific config**: `/native-library-config-{os}.properties`
3. **Global config**: `/native-library-config.properties`
4. **System properties**: `native.library.path.*`
5. **Environment variables**: `NATIVE_LIB_PATH_*`

#### Configuration Properties

```properties
# Global configuration
search.paths=/usr/local/lib:/usr/lib:${HOME}/lib
delete.on.exit=true
enable.caching=true
verify.checksums=true
log.level=INFO

# Library name mappings
library.mapping.opencv=opencv_java
library.mapping.tensorflow=tensorflow_jni
library.mapping.sqlite=sqlite3
```

#### Environment Variables

```bash
# Platform-specific paths
export NATIVE_LIB_PATH_LINUX_X86_64="/opt/fastfilter/lib"
export NATIVE_LIB_PATH_MACOS_ARM64="/usr/local/lib"

# Generic paths
export NATIVE_LIB_PATH="/usr/local/lib:/opt/lib"
```

#### System Properties

```bash
# Platform-specific
-Dnative.library.path.linux.x86_64=/opt/fastfilter/lib
-Dnative.library.path.macos.arm64=/usr/local/lib

# Generic
-Dnative.library.path=/usr/local/lib:/opt/lib

# Extract directory
-Dnative.library.extract.dir=/tmp/custom-native-libs
```

### Platform Support

#### Supported Platforms
- **Linux**: x86_64, ARM64 (AArch64)
- **macOS**: x86_64 (Intel), ARM64 (Apple Silicon)
- **Windows**: x86_64

#### CPU Feature Detection
The system automatically detects CPU capabilities:

```java
PlatformInfo platform = PlatformInfo.getInstance();
boolean hasAVX2 = platform.hasAVX2();
boolean hasNEON = platform.hasNEON();
boolean hasAVX512 = platform.hasAVX512();
```

#### Platform-Specific Optimizations
- **Linux x86_64**: AVX2 optimizations
- **macOS x86_64**: AVX2 optimizations  
- **macOS ARM64**: NEON optimizations
- **Linux ARM64**: NEON optimizations
- **Windows x86_64**: AVX2 optimizations

### Usage Examples

#### Basic Usage (Recommended)
```java
// Using the adapter for compatibility
NativeLibraryLoaderInterface loader = NativeLibraryLoaderAdapter.getInstance();
loader.loadLibrary(); // Loads fastfilter_cpp_ffi

// Create filters
Xor8Filter filter = new Xor8Filter(keys);
BinaryFuse8Filter fuseFilter = new BinaryFuse8Filter(keys);
```

#### Advanced Usage
```java
// Direct use of advanced loader
NativeLibraryLoaderV2 loader = NativeLibraryLoaderV2.getInstance();

// Load with custom library name
SymbolLookup symbols = loader.loadLibrary("custom_filter", "1.0.0");

// Get platform information
PlatformInfo platform = loader.getPlatformInfoObject();
System.out.println("Platform: " + platform.getDetailedPlatformString());
System.out.println("CPU Features: " + platform.getCPUFeatures());

// Check loading status
if (loader.isLoaded()) {
    System.out.println("Library loaded successfully");
    System.out.println("Strategy: " + loader.getLoadStrategy());
}
```

#### Configuration Override
```java
// Add custom search paths
NativeLibraryLoaderV2 loader = NativeLibraryLoaderV2.getInstance();
loader.addSearchPath(Paths.get("/opt/custom/lib"));
loader.addSearchPath(Paths.get("/home/user/.local/lib"));

// Load with custom configuration
loader.loadLibrary("fastfilter_cpp_ffi");
```

### Build System Integration

The build system automatically generates SHA-256 checksums for all native libraries:

#### Maven Build Process
1. **Compile Phase**: Bazel builds platform-specific libraries
2. **Process-Classes Phase**: 
   - Copy libraries to `target/classes/native/{platform}/`
   - Generate SHA-256 checksums using `shasum`/`sha256sum`
   - Include checksums in JAR files
3. **Package Phase**: Create platform-specific JARs with classifiers

#### Generated Files Structure
```
target/classes/native/
├── linux-x86_64/
│   ├── libfastfilter_cpp_ffi.so
│   └── libfastfilter_cpp_ffi.so.sha256
├── macos-arm64/
│   ├── libfastfilter_cpp_ffi.dylib
│   └── libfastfilter_cpp_ffi.dylib.sha256
└── windows-x86_64/
    ├── fastfilter_cpp_ffi.dll
    └── fastfilter_cpp_ffi.dll.sha256
```

### Error Handling

The system provides comprehensive error handling and diagnostics:

#### Common Error Scenarios
1. **Library Not Found**: Clear messages indicating search paths
2. **Checksum Mismatch**: Security warning with expected vs actual values
3. **Platform Unsupported**: Detailed platform information in error
4. **Permission Denied**: Extraction path and permission guidance

#### Diagnostic Information
```java
NativeLibraryLoaderInterface loader = NativeLibraryLoaderAdapter.getInstance();
System.out.println(loader.getPlatformInfo());
// Output:
// Platform: macos-arm64
// OS Name: Mac OS X
// OS Arch: aarch64
// Library File: libfastfilter_cpp_ffi.dylib
// Loaded: true
// Load Error: None
```

### Testing

The module includes comprehensive test suites:

- **NativeLibraryLoaderV2Test**: Core loader functionality
- **NativeLibraryConfigTest**: Configuration system
- **NativeExtractDirectoryTest**: File extraction and permissions
- **PlatformTest**: Platform detection accuracy

Run tests with:
```bash
mvn test
```

### Migration Guide

#### From Legacy Static Loader
```java
// Old approach
NativeLibraryLoader.loadLibrary();

// New approach (recommended)
NativeLibraryLoaderInterface loader = NativeLibraryLoaderAdapter.getInstance();
loader.loadLibrary();

// Or direct advanced usage
NativeLibraryLoaderV2 loader = NativeLibraryLoaderV2.getInstance();
loader.loadLibrary();
```

### Troubleshooting

#### Enable Debug Logging
```bash
-Djava.util.logging.level=FINE
```

#### Common Issues
1. **Missing AVX2 on older CPUs**: Library falls back to baseline implementation
2. **Permission errors**: Ensure temp directory is writable
3. **Checksum failures**: Verify JAR integrity, re-download if necessary
4. **Platform detection issues**: Check OS and architecture properties

#### Getting Help
- Check platform compatibility: `PlatformInfo.getInstance().getPlatformInfo()`
- Verify library search paths in configuration
- Enable detailed logging for diagnostic information
- Check build logs for checksum generation

A high-performance, configurable singleton library loader for JDK 24's Foreign Function & Memory API with comprehensive platform detection and architecture-specific path management.

## 🚀 Features

- **Singleton Pattern**: Thread-safe global instance
- **Platform Detection**: Automatic OS and architecture detection
- **Configurable Paths**: Environment variables, properties, and runtime configuration
- **Architecture-Specific**: Separate configurations per platform/architecture
- **Library Mapping**: Cross-platform library name resolution
- **Resource Extraction**: Embedded native libraries in JAR
- **Caching**: Intelligent library and extraction caching
- **Version Management**: Support for versioned libraries

## 📋 Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
    - [Configuration Hierarchy](#configuration-hierarchy)
    - [Environment Variables](#environment-variables)
    - [System Properties](#system-properties)
    - [Property Files](#property-files)
- [Usage](#usage)
    - [Basic Usage](#basic-usage)
    - [Advanced Usage](#advanced-usage)
- [Platform Support](#platform-support)
- [Project Structure](#project-structure)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

## 🔧 Installation

### Maven

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>native-library-loader</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
implementation 'com.example:native-library-loader:1.0.0'
```

### Requirements

- JDK 24 or later (for Foreign Function & Memory API)
- Maven 3.8+ or Gradle 7+

## ⚡ Quick Start

### 1. Basic Usage

```java
import com.example.ffi.platform.NativeLibraryLoader;
import java.lang.foreign.SymbolLookup;

public class QuickStart {
    public static void main(String[] args) throws IOException {
        // Get singleton instance
        NativeLibraryLoader loader = NativeLibraryLoader.getInstance();
        
        // Load a native library
        SymbolLookup myLib = loader.loadLibrary("mylib");
        
        // Load a versioned library
        SymbolLookup opencv = loader.loadLibrary("opencv", "4.5.0");
    }
}
```

### 2. Add Configuration File

Create `src/main/resources/native-library-config.properties`:

```properties
# Global configuration
search.paths=/usr/local/lib:/usr/lib:${HOME}/lib
delete.on.exit=true
enable.caching=true

# Library mappings
library.mapping.opencv=opencv_java453
library.mapping.sqlite=sqlite3
```

## 📁 Configuration

### Configuration Hierarchy

The loader searches for configuration in the following order (highest to lowest priority):

1. **Environment Variables** - Override everything
2. **System Properties** - JVM-level configuration
3. **Property Files** - Application resources
4. **Default Paths** - Built-in platform defaults

### Environment Variables

Set platform-specific library paths using environment variables:

```bash
# Platform-specific (highest priority)
export NATIVE_LIB_PATH_LINUX_X86_64=/opt/cuda/lib64:/opt/intel/mkl/lib

# OS-specific
export NATIVE_LIB_PATH_LINUX=/usr/lib:/usr/local/lib

# Generic (applies to all platforms)
export NATIVE_LIB_PATH=/custom/libs:/another/path
```

**Variable Naming Convention:**
- Generic: `NATIVE_LIB_PATH`
- OS-specific: `NATIVE_LIB_PATH_{OS}`
- Platform-specific: `NATIVE_LIB_PATH_{OS}_{ARCH}`

Where:
- `{OS}`: WINDOWS, LINUX, MACOS, FREEBSD, OPENBSD, SOLARIS, AIX
- `{ARCH}`: X86_64, X86, ARM64, ARM32, etc.

### System Properties

Configure via JVM system properties:

```java
// At startup
java -Dnative.library.path=/custom/libs \
     -Dnative.library.path.linux=/usr/lib \
     -Dnative.library.path.linux.x86_64=/usr/lib64 \
     -jar myapp.jar

// Or programmatically
System.setProperty("native.library.path", "/custom/libs");
System.setProperty("native.library.path.linux.x86_64", "/usr/lib64");
```

### Property Files

Create platform-specific property files in `src/main/resources/`:

#### Global Configuration
**File:** `/native-library-config.properties`

```properties
# Search paths (use : on Unix, ; on Windows)
search.paths=/usr/local/lib:/usr/lib:${HOME}/lib

# Extraction settings
extract.dir=${java.io.tmpdir}/native-libs
delete.on.exit=true

# Performance settings
enable.caching=true
verify.checksums=true

# Logging
log.level=INFO

# Library name mappings
library.mapping.opencv=opencv_java
library.mapping.tensorflow=tensorflow_jni
library.mapping.sqlite=sqlite3
```

#### Platform-Specific Configuration

**Linux:** `/native-library-config-linux.properties`
```properties
search.paths=/usr/lib:/usr/local/lib:/lib:${HOME}/.local/lib
library.mapping.crypto=libcrypto
library.mapping.ssl=libssl
```

**Linux x86_64:** `/native-library-config-linux-x86_64.properties`
```properties
search.paths=/usr/lib64:/lib64:/usr/lib/x86_64-linux-gnu
library.mapping.cuda=libcuda.so.1
library.mapping.cudnn=libcudnn.so.8
```

**macOS:** `/native-library-config-darwin.properties`
```properties
search.paths=/usr/local/lib:/usr/lib:${HOME}/lib
library.mapping.crypto=libcrypto.3
library.mapping.ssl=libssl.3
```

**Apple Silicon:** `/native-library-config-darwin-aarch64.properties`
```properties
search.paths=/opt/homebrew/lib:/opt/homebrew/opt:/usr/local/lib
```

**Windows:** `/native-library-config-windows.properties`
```properties
search.paths=C:\\Windows\\System32;${ProgramFiles}\\Common Files
library.mapping.crypto=libcrypto-3-x64
library.mapping.ssl=libssl-3-x64
```

## 💻 Usage

### Basic Usage

```java
import com.example.ffi.platform.*;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class Example {
    private static final NativeLibraryLoader LOADER = 
        NativeLibraryLoader.getInstance();
    
    static {
        try {
            // Load native library at startup
            LOADER.loadLibrary("mylib");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    public static void main(String[] args) throws Throwable {
        // Use with FFI
        FFIHelper ffi = new FFIHelper();
        MethodHandle malloc = ffi.downcallHandle(
            "c", "malloc",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
        );
        
        MemorySegment ptr = (MemorySegment) malloc.invoke(1024L);
        System.out.println("Allocated: " + ptr);
    }
}
```

### Advanced Usage

#### Custom Search Paths

```java
NativeLibraryLoader loader = NativeLibraryLoader.getInstance();

// Add custom search paths at runtime
loader.addSearchPath(Paths.get("/opt/cuda/lib64"));
loader.addSearchPath(Paths.get("/custom/libs"));

// Remove a path
loader.removeSearchPath(Paths.get("/old/path"));
```

#### Library Name Mapping

```java
// Configure in properties:
// library.mapping.opencv=opencv_java453

// Use simplified name in code:
loader.loadLibrary("opencv");  // Automatically maps to opencv_java453
```

#### Version Management

```java
// Load specific versions
SymbolLookup lib_v1 = loader.loadLibrary("mylib", "1.0.0");
SymbolLookup lib_v2 = loader.loadLibrary("mylib", "2.0.0");

// Libraries are cached by name+version
```

#### Configuration Access

```java
NativeLibraryLoader loader = NativeLibraryLoader.getInstance();
NativeLibraryConfig config = loader.getConfiguration();

// Check configuration
boolean caching = config.isCachingEnabled();
Path extractDir = config.getExtractDirectory();
List<Path> paths = config.getSearchPaths();

// Get platform info
PlatformInfo platform = loader.getPlatform();
System.out.println("Platform: " + platform.getPlatformString());
System.out.println("CPU Features: " + platform.getCPUFeatures());
```

#### Cache Management

```java
// Clear library cache (if caching is enabled)
loader.clearCache();

// Get loaded libraries
Set<String> loaded = loader.getLoadedLibraries();
System.out.println("Loaded: " + loaded);
```

## 🖥️ Platform Support

### Supported Operating Systems

| OS | Architectures | Library Extension | Prefix |
|---|---|---|---|
| Linux | x86, x86_64, ARM, ARM64, RISC-V | .so | lib |
| macOS | x86_64, ARM64 (Apple Silicon) | .dylib | lib |
| Windows | x86, x86_64, ARM64 | .dll | - |
| FreeBSD | x86_64, ARM64 | .so | lib |
| OpenBSD | x86_64, ARM64 | .so | lib |
| Solaris | x86_64, SPARC | .so | lib |
| AIX | PPC64 | .so | lib |

### Default Search Paths

#### Linux
- `/usr/lib`, `/usr/local/lib`, `/lib`
- x86_64: `/usr/lib64`, `/lib64`, `/usr/lib/x86_64-linux-gnu`
- ARM64: `/usr/lib/aarch64-linux-gnu`
- ARM32: `/usr/lib/arm-linux-gnueabihf`

#### macOS
- `/usr/local/lib`, `/usr/lib`
- Apple Silicon: `/opt/homebrew/lib`, `/opt/homebrew/opt`
- Intel: `/usr/local/opt`
- MacPorts: `/opt/local/lib`

#### Windows
- `C:\Windows\System32`, `C:\Windows\SysWOW64`
- `%ProgramFiles%`, `%ProgramFiles(x86)%`
- MSYS2: `C:\msys64\mingw64\bin`, `C:\msys64\mingw32\bin`

## 📂 Project Structure

### Resource Layout

```
src/main/resources/
├── native-library-config.properties              # Global config
├── native-library-config-linux.properties        # Linux config
├── native-library-config-linux-x86_64.properties # Linux x64 config
├── native-library-config-darwin.properties       # macOS config
├── native-library-config-darwin-aarch64.properties # Apple Silicon
├── native-library-config-windows.properties      # Windows config
└── native/                                       # Embedded libraries
    ├── linux-x86_64/
    │   ├── libmylib.so
    │   └── libmylib-1.0.0.so
    ├── darwin-aarch64/
    │   ├── libmylib.dylib
    │   └── libmylib-1.0.0.dylib
    └── windows-x86_64/
        ├── mylib.dll
        └── mylib-1.0.0.dll
```

### Package Structure

```
com.example.ffi.platform/
├── PlatformInfo.java          # Platform detection
├── NativeLibraryLoader.java   # Singleton loader
├── NativeLibraryConfig.java   # Configuration management
└── FFIHelper.java             # FFI integration helpers
```

## 🧪 Testing

Run the test suite:

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=NativeLibraryLoaderTest

# With coverage
mvn test jacoco:report
```

## 🔍 Troubleshooting

### Library Not Found

1. **Check search paths:**
```java
NativeLibraryConfig config = loader.getConfiguration();
System.out.println("Search paths: " + config.getSearchPaths());
```

2. **Enable debug logging:**
```properties
# In native-library-config.properties
log.level=FINE
```

3. **Verify library name:**
```bash
# Linux/macOS
ls -la /usr/lib | grep mylib

# Windows
dir C:\Windows\System32 | findstr mylib
```

### Unsatisfied Link Error

1. **Check architecture compatibility:**
```java
PlatformInfo platform = PlatformInfo.getInstance();
System.out.println("Architecture: " + platform.getArch());
```

2. **Verify library dependencies:**
```bash
# Linux
ldd libmylib.so

# macOS
otool -L libmylib.dylib

# Windows (using Dependency Walker or dumpbin)
dumpbin /dependents mylib.dll
```

### Permission Issues

1. **Check file permissions:**
```bash
ls -la /path/to/library
```

2. **Verify extraction directory:**
```java
Path extractDir = config.getExtractDirectory();
System.out.println("Extract dir: " + extractDir);
System.out.println("Writable: " + Files.isWritable(extractDir));
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- JDK 24 Foreign Function & Memory API team
- Contributors and testers
- Open source community

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/example/native-library-loader/issues)
- **Discussions**: [GitHub Discussions](https://github.com/example/native-library-loader/discussions)
- **Email**: support@example.com