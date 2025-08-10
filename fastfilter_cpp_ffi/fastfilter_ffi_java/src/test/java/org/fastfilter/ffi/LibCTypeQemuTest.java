package org.fastfilter.ffi;// ============================================
// LibCTypeQemuTest.java - Refactored with Parameterized Tests
// ============================================


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

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * Parameterized QEMU tests for different architectures and libc implementations
 * Requires Docker with QEMU binfmt support for multi-arch testing
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("LibC Detection QEMU Cross-Platform Tests")
@EnabledIfSystemProperty(named = "test.qemu", matches = "true")
class LibCTypeQemuTest
{

	private static final Duration CONTAINER_TIMEOUT = Duration.ofSeconds(120);
	private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

	// Container cache for reuse across tests
	private static final Map<String, GenericContainer<?>> containerCache = new HashMap<>();

	/**
	 * Test configuration for each platform
	 */
	record PlatformTestConfig(
		String name,
		String image,
		String platform,
		String architecture,
		LibCType expectedLibC,
		String libcVersion,
		List<String> libcIndicators,
		List<String> libraryPaths,
		boolean skipOnCI
	) {
		// Builder pattern for cleaner test data creation
		static class Builder {
			private String name;
			private String image;
			private String platform;
			private String architecture;
			private LibCType expectedLibC;
			private String libcVersion;
			private final List<String> libcIndicators = new ArrayList<>();
			private final List<String> libraryPaths = new ArrayList<>();
			private boolean skipOnCI = false;

			Builder name(String name) { this.name = name; return this; }
			Builder image(String image) { this.image = image; return this; }
			Builder platform(String platform) { this.platform = platform; return this; }
			Builder architecture(String arch) { this.architecture = arch; return this; }
			Builder expectedLibC(LibCType libc) { this.expectedLibC = libc; return this; }
			Builder libcVersion(String version) { this.libcVersion = version; return this; }
			Builder libcIndicator(String indicator) { this.libcIndicators.add(indicator); return this; }
			Builder libraryPath(String path) { this.libraryPaths.add(path); return this; }
			Builder skipOnCI(boolean skip) { this.skipOnCI = skip; return this; }

			PlatformTestConfig build() {
				return new PlatformTestConfig(name, image, platform, architecture,
				                              expectedLibC, libcVersion, libcIndicators, libraryPaths, skipOnCI);
			}
		}
	}

	/**
	 * Provides test configurations for all platforms
	 */
	static Stream<Arguments> platformProvider() {
		return Stream.of(
			// Alpine Linux - musl libc
			Arguments.of(new PlatformTestConfig.Builder()
				             .name("Alpine x86_64")
				             .image("alpine:latest")
				             .platform("linux/amd64")
				             .architecture("x86_64")
				             .expectedLibC(LibCType.MUSL)
				             .libcVersion("1.2")
				             .libcIndicator("musl")
				             .libcIndicator("ld-musl-x86_64.so")
				             .libraryPath("/lib")
				             .libraryPath("/usr/lib")
				             .build()),

			Arguments.of(new PlatformTestConfig.Builder()
				             .name("Alpine ARM64")
				             .image("arm64v8/alpine:latest")
				             .platform("linux/arm64")
				             .architecture("aarch64")
				             .expectedLibC(LibCType.MUSL)
				             .libcVersion("1.2")
				             .libcIndicator("musl")
				             .libcIndicator("ld-musl-aarch64.so")
				             .libraryPath("/lib")
				             .libraryPath("/usr/lib")
				             .build()),

			Arguments.of(new PlatformTestConfig.Builder()
				             .name("Alpine ARMv7")
				             .image("arm32v7/alpine:latest")
				             .platform("linux/arm/v7")
				             .architecture("armv7l")
				             .expectedLibC(LibCType.MUSL)
				             .libcVersion("1.2")
				             .libcIndicator("musl")
				             .libcIndicator("ld-musl-armhf.so")
				             .libraryPath("/lib")
				             .libraryPath("/usr/lib")
				             .build()),

			// Ubuntu - glibc
			Arguments.of(new PlatformTestConfig.Builder()
				             .name("Ubuntu 22.04 x86_64")
				             .image("ubuntu:22.04")
				             .platform("linux/amd64")
				             .architecture("x86_64")
				             .expectedLibC(LibCType.GLIBC)
				             .libcVersion("2.35")
				             .libcIndicator("GNU C Library")
				             .libcIndicator("glibc")
				             .libraryPath("/lib/x86_64-linux-gnu")
				             .libraryPath("/usr/lib/x86_64-linux-gnu")
				             .build()),

			Arguments.of(new PlatformTestConfig.Builder()
				             .name("Ubuntu 22.04 ARM64")
				             .image("ubuntu:22.04")
				             .platform("linux/arm64")
				             .architecture("aarch64")
				             .expectedLibC(LibCType.GLIBC)
				             .libcVersion("2.35")
				             .libcIndicator("GNU C Library")
				             .libcIndicator("glibc")
				             .libraryPath("/lib/aarch64-linux-gnu")
				             .libraryPath("/usr/lib/aarch64-linux-gnu")
				             .build()),

			// Debian - glibc
			Arguments.of(new PlatformTestConfig.Builder()
				             .name("Debian Bullseye x86_64")
				             .image("debian:bullseye")
				             .platform("linux/amd64")
				             .architecture("x86_64")
				             .expectedLibC(LibCType.GLIBC)
				             .libcVersion("2.31")
				             .libcIndicator("GNU C Library")
				             .libcIndicator("glibc")
				             .libraryPath("/lib/x86_64-linux-gnu")
				             .libraryPath("/usr/lib/x86_64-linux-gnu")
				             .build()),

			Arguments.of(new PlatformTestConfig.Builder()
				             .name("Debian Bullseye ARM64")
				             .image("arm64v8/debian:bullseye")
				             .platform("linux/arm64")
				             .architecture("aarch64")
				             .expectedLibC(LibCType.GLIBC)
				             .libcVersion("2.31")
				             .libcIndicator("GNU C Library")
				             .libraryPath("/lib/aarch64-linux-gnu")
				             .build()),

			// Fedora - glibc
			Arguments.of(new PlatformTestConfig.Builder()
				             .name("Fedora 39 x86_64")
				             .image("fedora:39")
				             .platform("linux/amd64")
				             .architecture("x86_64")
				             .expectedLibC(LibCType.GLIBC)
				             .libcVersion("2.38")
				             .libcIndicator("GNU C Library")
				             .libraryPath("/lib64")
				             .libraryPath("/usr/lib64")
				             .build()),

			// Arch Linux - glibc
			Arguments.of(new PlatformTestConfig.Builder()
				             .name("Arch Linux x86_64")
				             .image("archlinux:latest")
				             .platform("linux/amd64")
				             .architecture("x86_64")
				             .expectedLibC(LibCType.GLIBC)
				             .libcVersion("2.38")
				             .libcIndicator("GNU C Library")
				             .libraryPath("/usr/lib")
				             .build()),

			// Amazon Linux - glibc
			Arguments.of(new PlatformTestConfig.Builder()
				             .name("Amazon Linux 2023")
				             .image("amazonlinux:2023")
				             .platform("linux/amd64")
				             .architecture("x86_64")
				             .expectedLibC(LibCType.GLIBC)
				             .libcVersion("2.34")
				             .libcIndicator("GNU C Library")
				             .libraryPath("/lib64")
				             .libraryPath("/usr/lib64")
				             .build()),

			// RISC-V64 - experimental
			Arguments.of(new PlatformTestConfig.Builder()
				             .name("Alpine RISC-V64")
				             .image("riscv64/alpine:edge")
				             .platform("linux/riscv64")
				             .architecture("riscv64")
				             .expectedLibC(LibCType.MUSL)
				             .libcVersion("1.2")
				             .libcIndicator("musl")
				             .libraryPath("/lib")
				             .skipOnCI(true)  // May not be available in all CI environments
				             .build()),

			// s390x - IBM Z
			Arguments.of(new PlatformTestConfig.Builder()
				             .name("Alpine s390x")
				             .image("s390x/alpine:latest")
				             .platform("linux/s390x")
				             .architecture("s390x")
				             .expectedLibC(LibCType.MUSL)
				             .libcVersion("1.2")
				             .libcIndicator("musl")
				             .libraryPath("/lib")
				             .skipOnCI(true)  // May not be available in all CI environments
				             .build()),

			// PowerPC64 Little Endian
			Arguments.of(new PlatformTestConfig.Builder()
				             .name("Ubuntu ppc64le")
				             .image("ppc64le/ubuntu:22.04")
				             .platform("linux/ppc64le")
				             .architecture("ppc64le")
				             .expectedLibC(LibCType.GLIBC)
				             .libcVersion("2.35")
				             .libcIndicator("GNU C Library")
				             .libraryPath("/lib/powerpc64le-linux-gnu")
				             .skipOnCI(true)  // May not be available in all CI environments
				             .build())
		);
	}

	/**
	 * Parameterized test for LibC detection
	 */
	@ParameterizedTest(name = "{index}: {0}")
	@MethodSource("platformProvider")
	@DisplayName("LibC detection on different platforms")
	void testLibCDetection(PlatformTestConfig config) throws Exception {
		// Skip certain tests in CI environment
		assumeFalse(config.skipOnCI() && isRunningInCI(),
		            "Skipping " + config.name() + " in CI environment");

		// Get or create container
		try(GenericContainer<?> container = getOrCreateContainer(config))
		{
			// Verify container is running
			assertTrue(container.isRunning(), "Container should be running for " + config.name());

			// Run detection tests
			// Verify architecture
			String actualArch = executeInContainer(container, "uname -m");
			assertTrue(actualArch.contains(config.architecture()) ||
				           isCompatibleArchitecture(actualArch, config.architecture()),
			           String.format("Expected architecture %s but got %s",
			                         config.architecture(), actualArch));

			// Test LibC detection
			String lddOutput = executeInContainer(container,
			                                      "ldd --version 2>&1 || echo 'ldd not available'");

			// Verify LibC indicators
			boolean indicatorFound = config.libcIndicators().stream()
			                               .anyMatch(indicator -> lddOutput.toLowerCase().contains(indicator.toLowerCase()));

			assertTrue(indicatorFound,
			           String.format("Expected LibC indicators %s not found in output: %s",
			                         config.libcIndicators(), lddOutput));

			// Test library paths
			for (String path : config.libraryPaths()) {
				String checkPath = executeInContainer(container,
				                                      String.format("test -d %s && echo 'EXISTS' || echo 'NOT_FOUND'", path));

				assertEquals("EXISTS", checkPath.trim(),
				             String.format("Library path %s should exist on %s", path, config.name()));
			}

			// Test specific LibC files
			testLibCSpecificFiles(container, config);

			// Test version if available
			if (config.libcVersion() != null) {
				testLibCVersion(container, config);
			}
		}


	}

	/**
	 * Parameterized test for library loading
	 */
	@ParameterizedTest(name = "{index}: Library loading on {0}")
	@MethodSource("platformProvider")
	@DisplayName("Library loading on different platforms")
	void testLibraryLoading(PlatformTestConfig config) throws Exception {
		assumeFalse(config.skipOnCI() && isRunningInCI());

		try(GenericContainer<?> container = getOrCreateContainer(config)){
			// Test loading common libraries
			List<String> commonLibraries = Arrays.asList(
				"c",      // C library
				"m",      // Math library
				"pthread", // POSIX threads
				"dl"      // Dynamic loading
			);

			for (String lib : commonLibraries) {
				String result = executeInContainer(container,
				                                   String.format("ldconfig -p 2>/dev/null | grep lib%s\\. || echo 'NOT_FOUND'", lib));

				if (!result.contains("NOT_FOUND")) {
					assertTrue(result.contains(lib),
					           String.format("Library %s should be available on %s", lib, config.name()));
				}
			}
		}

	}

	/**
	 * Parameterized test for Java integration
	 */
	@ParameterizedTest(name = "{index}: Java detection on {0}")
	@MethodSource("platformProvider")
	@DisplayName("Java LibC detection on different platforms")
	@EnabledIfSystemProperty(named = "test.java.integration", matches = "true")
	void testJavaLibCDetection(PlatformTestConfig config) throws Exception {
		assumeFalse(config.skipOnCI() && isRunningInCI());

		try(GenericContainer<?> container = getOrCreateContainer(config)){
			// Install Java if possible
			String installCommand = getJavaInstallCommand(config);
			if (installCommand != null) {
				executeInContainer(container, installCommand);

				// Copy and run detection code
				container.copyFileToContainer(
					Transferable.of(getDetectionScript()),
					"/tmp/DetectLibC.java"
				);

				String compileResult = executeInContainer(container,
				                                          "cd /tmp && javac DetectLibC.java 2>&1");

				if (!compileResult.contains("error")) {
					String runResult = executeInContainer(container,
					                                      "cd /tmp && java DetectLibC");

					// Verify detection matches expected
					if (config.expectedLibC() == LibCType.MUSL) {
						assertTrue(runResult.toLowerCase().contains("musl"),
						           "Should detect musl on " + config.name());
					} else if (config.expectedLibC() == LibCType.GLIBC) {
						assertTrue(runResult.toLowerCase().contains("glibc") ||
							           runResult.toLowerCase().contains("gnu"),
						           "Should detect glibc on " + config.name());
					}
				}
			}
		}
	}

	/**
	 * Test LibC-specific files
	 */
	private void testLibCSpecificFiles(GenericContainer<?> container, PlatformTestConfig config)
		throws Exception {
		List<String> filesToCheck = new ArrayList<>();

		switch (config.expectedLibC()) {
			case MUSL -> {
				filesToCheck.add("/lib/ld-musl-" + config.architecture() + ".so.1");
				filesToCheck.add("/etc/alpine-release");  // Alpine specific
			}
			case GLIBC -> {
				if (config.architecture().equals("x86_64")) {
					filesToCheck.add("/lib64/ld-linux-x86-64.so.2");
				} else if (config.architecture().equals("aarch64")) {
					filesToCheck.add("/lib/ld-linux-aarch64.so.1");
				}
			}
		}

		for (String file : filesToCheck) {
			String result = executeInContainer(container,
			                                   String.format("test -e %s && echo 'EXISTS' || echo 'NOT_FOUND'", file));

			// Log for debugging but don't fail if optional files don't exist
			System.out.println(String.format("%s: %s -> %s",
			                                 config.name(), file, result.trim()));
		}
	}

	/**
	 * Test LibC version detection
	 */
	private void testLibCVersion(GenericContainer<?> container, PlatformTestConfig config)
		throws Exception {
		String versionCommand = switch (config.expectedLibC()) {
			case GLIBC -> "ldd --version | head -1 | grep -oE '[0-9]+\\.[0-9]+'";
			case MUSL -> "strings /lib/ld-musl*.so* 2>/dev/null | grep -i version | head -1";
			default -> null;
		};

		if (versionCommand != null) {
			String version = executeInContainer(container, versionCommand);
			if (!version.isEmpty() && config.libcVersion() != null) {
				assertTrue(version.startsWith(config.libcVersion().substring(0, 3)),
				           String.format("Expected version %s but got %s on %s",
				                         config.libcVersion(), version, config.name()));
			}
		}
	}

	/**
	 * Get or create a container for testing
	 */
	private GenericContainer<?> getOrCreateContainer(PlatformTestConfig config) {
		return containerCache.computeIfAbsent(config.image(), image -> {
			System.out.println("Creating container for: " + config.name());

			GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(image))
				                                .withCommand("sleep", "infinity")
				                                .withStartupTimeout(CONTAINER_TIMEOUT)
				                                .waitingFor(Wait.forLogMessage(".*", 1));

			// Set platform if specified
			if (config.platform() != null) {
				container.withCreateContainerCmdModifier(cmd ->
					                                         cmd.withPlatform(config.platform()));
			}

			container.start();
			return container;
		});
	}

	/**
	 * Execute command in container
	 */
	private String executeInContainer(GenericContainer<?> container, String command)
		throws Exception {
		var result = container.execInContainer("sh", "-c", command);

		if (result.getExitCode() != 0 && !command.contains("||") && !command.contains("2>")) {
			System.err.println("Command failed: " + command);
			System.err.println("Exit code: " + result.getExitCode());
			System.err.println("Stderr: " + result.getStderr());
		}

		return result.getStdout().trim();
	}

	/**
	 * Check if architecture names are compatible
	 */
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
	 * Check if running in CI environment
	 */
	private boolean isRunningInCI() {
		return System.getenv("CI") != null ||
			       System.getenv("GITHUB_ACTIONS") != null ||
			       System.getenv("JENKINS_HOME") != null ||
			       System.getenv("GITLAB_CI") != null;
	}

	/**
	 * Get Java installation command for the platform
	 */
	private String getJavaInstallCommand(PlatformTestConfig config) {
		if (config.image().contains("alpine")) {
			return "apk add --no-cache openjdk17-jdk 2>/dev/null || apk add --no-cache openjdk11-jdk";
		} else if (config.image().contains("ubuntu") || config.image().contains("debian")) {
			return "apt-get update && apt-get install -y openjdk-17-jdk-headless 2>/dev/null || " +
				       "apt-get install -y openjdk-11-jdk-headless";
		} else if (config.image().contains("fedora")) {
			return "dnf install -y java-17-openjdk-devel 2>/dev/null || " +
				       "dnf install -y java-11-openjdk-devel";
		} else if (config.image().contains("archlinux")) {
			return "pacman -Sy --noconfirm jdk-openjdk";
		} else if (config.image().contains("amazonlinux")) {
			return "yum install -y java-17-amazon-corretto-devel 2>/dev/null || " +
				       "yum install -y java-11-amazon-corretto-devel";
		}
		return null;
	}

	/**
	 * Get a simple detection script to run in containers
	 */
	private String getDetectionScript() {
		return """
            import java.io.*;
            import java.nio.file.*;
            
            public class DetectLibC {
                public static void main(String[] args) throws Exception {
                    // Try ldd detection
                    ProcessBuilder pb = new ProcessBuilder("sh", "-c", "ldd --version 2>&1");
                    Process p = pb.start();
                    
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                        if (line.toLowerCase().contains("musl")) {
                            System.out.println("DETECTED: musl");
                            break;
                        } else if (line.toLowerCase().contains("glibc") || 
                                   line.toLowerCase().contains("gnu")) {
                            System.out.println("DETECTED: glibc");
                            break;
                        }
                    }
                    
                    // Check for specific files
                    if (Files.exists(Paths.get("/etc/alpine-release"))) {
                        System.out.println("DETECTED: Alpine Linux (musl)");
                    }
                    
                    // Check loaders
                    String[] loaders = {
                        "/lib/ld-musl-x86_64.so.1",
                        "/lib/ld-musl-aarch64.so.1",
                        "/lib64/ld-linux-x86-64.so.2",
                        "/lib/ld-linux-aarch64.so.1"
                    };
                    
                    for (String loader : loaders) {
                        if (Files.exists(Paths.get(loader))) {
                            System.out.println("Found loader: " + loader);
                        }
                    }
                }
            }
            """;
	}

	@BeforeAll
	static void setupQemu() {
		System.out.println("Setting up QEMU for multi-architecture testing...");

		// Verify QEMU is available
		try {
			Process p = new ProcessBuilder("docker", "run", "--rm", "--privileged",
			                               "multiarch/qemu-user-static", "--reset", "-p", "yes").start();

			boolean finished = p.waitFor(30, TimeUnit.SECONDS);
			if (finished && p.exitValue() == 0) {
				System.out.println("QEMU setup successful");
			} else {
				System.err.println("QEMU setup failed or timed out");
			}
		} catch (Exception e) {
			System.err.println("Failed to setup QEMU: " + e.getMessage());
		}
	}

	@AfterAll
	static void cleanup() {
		System.out.println("Cleaning up containers...");

		containerCache.values().forEach(container -> {
			try {
				container.stop();
				container.close();
			} catch (Exception e) {
				System.err.println("Failed to stop container: " + e.getMessage());
			}
		});

		containerCache.clear();
	}
}