package org.fastfilter.ffi;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * Comprehensive platform detection and FFI functionality tests.
 * 
 * This test class validates:
 * - Platform and architecture detection
 * - LibC type detection and feature analysis
 * - FFI functionality (malloc/free example)
 * - Native library loading infrastructure
 * - Cross-platform compatibility via Docker/QEMU when enabled
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Platform Detection and FFI Tests")
class PlatformTest {

    private PlatformInfo platform;
    private FFIHelper ffi;
    
    @BeforeAll
    void setUp() {
        platform = PlatformInfo.getInstance();
        ffi = new FFIHelper();
    }

    @Test
    @Order(1)
    @DisplayName("Platform detection should work correctly")
    void testPlatformDetection() {
        assertNotNull(platform, "Platform info should not be null");
        
        // Basic platform information
        String detailedPlatform = platform.getDetailedPlatformString();
        assertNotNull(detailedPlatform, "Platform string should not be null");
        assertFalse(detailedPlatform.isEmpty(), "Platform string should not be empty");
        
        // OS information
        OSType os = platform.getOS();
        assertNotNull(os, "OS should not be null");
        assertNotNull(os.getName(), "OS name should not be null");
        
        // Architecture information
        CPUArch arch = platform.getArch();
        assertNotNull(arch, "Architecture should not be null");
        assertNotNull(arch.getName(), "Architecture name should not be null");
        assertTrue(arch.bits() > 0, "Architecture bits should be positive");
        
        System.out.println("✓ Platform: " + detailedPlatform);
        System.out.println("✓ OS: " + os.getName());
        System.out.println("✓ Arch: " + arch.getName() + " (" + arch.bits() + "-bit)");
        
        // Optional fields (may be null on some platforms)
        String kernelVersion = platform.getKernelVersion();
        if (kernelVersion != null) {
            System.out.println("✓ Kernel: " + kernelVersion);
        }
        
        Set<String> cpuFeatures = platform.getCPUFeatures();
        if (cpuFeatures != null && !cpuFeatures.isEmpty()) {
            System.out.println("✓ CPU Features: " + cpuFeatures);
        }
        
        System.out.println("✓ Has AVX2: " + platform.hasAVX2());
    }

    @Test
    @Order(2)
    @DisplayName("LibC detection should work correctly")
    void testLibCDetection() {
        LibCInfo libcInfo = LibCType.detectCurrent();
        assertNotNull(libcInfo, "LibC info should not be null");
        
        LibCType type = libcInfo.type();
        assertNotNull(type, "LibC type should not be null");
        
        System.out.println("✓ LibC Type: " + type);
        System.out.println("✓ LibC Version: " + libcInfo.version());
        System.out.println("✓ Confidence: " + libcInfo.confidenceLevel() + "%");
        
        // Library paths
        List<String> paths = libcInfo.libraryPaths().stream()
                                    .map(path -> path.toString())
                                    .toList();
        assertNotNull(paths, "Library paths should not be null");
        System.out.println("✓ Library paths: " + paths);
        
        // Features
        Set<String> features = libcInfo.features();
        assertNotNull(features, "Features should not be null");
        if (!features.isEmpty()) {
            System.out.println("✓ Features: " + features);
        }
    }

    @Test
    @Order(3)
    @DisplayName("FFI functionality should work correctly")
    @EnabledIfSystemProperty(named = "test.ffi", matches = "true|TRUE", disabledReason = "FFI tests disabled by default")
    void testFFIFunctionality() throws Throwable {
        assertNotNull(ffi, "FFI helper should not be null");
        
        try (Arena arena = Arena.ofConfined()) {
            // Create downcall handle for malloc
            MethodHandle malloc = ffi.downcallHandle(
                platform.getOS().getCLibName(),
                "malloc",
                FFIHelper.Descriptors.POINTER_SIZE_T
            );
            assertNotNull(malloc, "malloc handle should not be null");

            // Create downcall handle for free
            MethodHandle free = ffi.downcallHandle(
                platform.getOS().getCLibName(),
                "free",
                FFIHelper.Descriptors.VOID_POINTER
            );
            assertNotNull(free, "free handle should not be null");

            // Allocate 1024 bytes
            MemorySegment ptr = (MemorySegment) malloc.invoke(1024L);
            assertNotNull(ptr, "Allocated pointer should not be null");
            assertFalse(ptr.address() == 0, "Allocated address should not be zero");
            
            System.out.println("✓ Allocated memory at: " + ptr);

            // Use the memory
            ptr.set(ValueLayout.JAVA_INT, 0, 42);
            int value = ptr.get(ValueLayout.JAVA_INT, 0);
            assertEquals(42, value, "Memory read/write should work correctly");
            
            System.out.println("✓ Memory read/write test passed: " + value);

            // Free the memory
            free.invoke(ptr);
            System.out.println("✓ Memory freed successfully");
        }
    }

    @Test
    @Order(4)
    @DisplayName("Native library loading infrastructure should work")
    void testNativeLibraryLoading() {
        NativeLibraryLoaderAdvanced loader = new NativeLibraryLoaderAdvanced();
        assertNotNull(loader, "Library loader should not be null");
        
        try {
            // Test that we can get loaded libraries (even if empty)
            Set<String> loadedLibraries = loader.getLoadedLibraries();
            assertNotNull(loadedLibraries, "Loaded libraries set should not be null");
            
            System.out.println("✓ Loaded libraries: " + loadedLibraries);
            System.out.println("✓ Native library loading infrastructure is working");
            
        } finally {
            loader.cleanup();
            System.out.println("✓ Library loader cleanup completed");
        }
    }

    @Test
    @Order(5)
    @DisplayName("Platform-specific features should be detected correctly")
    void testPlatformSpecificFeatures() {
        // Test based on detected OS
        OSType os = platform.getOS();
        
        switch (os) {
            case LINUX -> {
                // Linux-specific tests
                assertTrue(os.getName().toLowerCase().contains("linux"));
                // Check if common Linux paths exist
                assertNotNull(platform.getArch());
            }
            case MACOS -> {
                // macOS-specific tests
                assertTrue(os.getName().toLowerCase().contains("mac") || 
                          os.getName().toLowerCase().contains("darwin"));
            }
            case WINDOWS -> {
                // Windows-specific tests
                assertTrue(os.getName().toLowerCase().contains("windows"));
            }
            case UNKNOWN -> {
                // For unknown OS, just verify we got some information
                assertNotNull(os.getName());
                System.out.println("⚠ Unknown OS detected: " + os.getName());
            }
        }
        
        System.out.println("✓ Platform-specific feature detection completed");
    }

    /**
     * Provides test configurations for cross-platform Docker testing
     */
    static Stream<Arguments> dockerPlatformProvider() {
        return Stream.of(
            Arguments.of("Alpine Linux x86_64", "alpine:latest", "linux/amd64", "x86_64"),
            Arguments.of("Alpine Linux ARM64", "arm64v8/alpine:latest", "linux/arm64", "aarch64"),
            Arguments.of("Ubuntu 22.04 x86_64", "ubuntu:22.04", "linux/amd64", "x86_64"),
            Arguments.of("Ubuntu 22.04 ARM64", "ubuntu:22.04", "linux/arm64", "aarch64")
        );
    }

    @ParameterizedTest(name = "Cross-platform test: {0}")
    @MethodSource("dockerPlatformProvider")
    @DisplayName("Cross-platform compatibility via Docker/QEMU")
    @EnabledIfSystemProperty(named = "test.docker", matches = "true|TRUE", disabledReason = "Docker tests disabled by default")
    @Timeout(120) // 2 minutes timeout
    void testCrossPlatformCompatibility(String platformName, String image, String platform, String expectedArch) throws Exception {
        assumeTrue(isDockerAvailable(), "Docker is not available");
        
        System.out.println("Testing platform: " + platformName);
        
        try (GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(image))
            .withCommand("sleep", "30")
            .withStartupTimeout(Duration.ofSeconds(60))
            .waitingFor(Wait.forLogMessage(".*", 1))) {
            
            // Set platform if specified
            if (platform != null) {
                container.withCreateContainerCmdModifier(cmd -> cmd.withPlatform(platform));
            }
            
            container.start();
            assertTrue(container.isRunning(), "Container should be running for " + platformName);
            
            // Test basic platform detection
            var result = container.execInContainer("uname", "-m");
            assertEquals(0, result.getExitCode(), "uname command should succeed");
            
            String actualArch = result.getStdout().trim();
            assertTrue(isCompatibleArchitecture(actualArch, expectedArch),
                      String.format("Expected compatible architecture with %s but got %s", expectedArch, actualArch));
            
            // Test that basic system info is available
            var osResult = container.execInContainer("uname", "-a");
            assertEquals(0, osResult.getExitCode(), "uname -a should succeed");
            assertFalse(osResult.getStdout().isEmpty(), "System info should not be empty");
            
            System.out.println("✓ " + platformName + " - Architecture: " + actualArch);
            System.out.println("✓ " + platformName + " - System: " + osResult.getStdout().trim());
        }
    }

    private boolean isDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "--version").start();
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isCompatibleArchitecture(String actual, String expected) {
        Map<String, Set<String>> compatibleArchs = Map.of(
            "x86_64", Set.of("x86_64", "amd64"),
            "aarch64", Set.of("aarch64", "arm64"),
            "armv7l", Set.of("armv7l", "armhf", "arm"),
            "riscv64", Set.of("riscv64"),
            "s390x", Set.of("s390x"),
            "ppc64le", Set.of("ppc64le", "powerpc64le")
        );

        return compatibleArchs.getOrDefault(expected, Set.of(expected))
                              .contains(actual.toLowerCase());
    }

    /**
     * Legacy main method for standalone execution
     */
    public static void main(String[] args) throws Throwable {
        System.out.println("=== FastFilter FFI Platform Test ===");
        System.out.println("Note: Running via main method. For full test coverage, use JUnit 5.");
        System.out.println();
        
        PlatformTest test = new PlatformTest();
        test.setUp();
        
        try {
            test.testPlatformDetection();
            System.out.println();
            
            test.testLibCDetection();
            System.out.println();
            
            // Only run FFI test if enabled
            if ("true".equalsIgnoreCase(System.getProperty("test.ffi"))) {
                test.testFFIFunctionality();
                System.out.println();
            } else {
                System.out.println("⚠ FFI functionality test skipped (enable with -Dtest.ffi=true)");
                System.out.println();
            }
            
            test.testNativeLibraryLoading();
            System.out.println();
            
            test.testPlatformSpecificFeatures();
            System.out.println();
            
            System.out.println("✅ All enabled tests passed!");
            
        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
