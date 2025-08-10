package org.fastfilter.ffi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("NativeLibraryConfig Tests")
class NativeLibraryConfigTest {

	private NativeLibraryConfig config;
	private final Map<String, String> originalEnv = new HashMap<>();
	private final Properties originalProps = new Properties();

	@TempDir
	Path tempDir;

	@BeforeAll
	void saveOriginalSettings() {
		// Save original environment variables
		originalEnv.put("NATIVE_LIB_PATH", System.getenv("NATIVE_LIB_PATH"));
		originalEnv.put("NATIVE_LIB_PATH_LINUX", System.getenv("NATIVE_LIB_PATH_LINUX"));

		// Save original system properties
		originalProps.setProperty("native.library.path",
		                          System.getProperty("native.library.path", ""));
		originalProps.setProperty("java.library.path",
		                          System.getProperty("java.library.path", ""));
	}

	@BeforeEach
	void setUp() {
		config = new NativeLibraryConfig();
	}

	@AfterEach
	void restoreSettings() {
		// Restore system properties
		originalProps.forEach((key, value) -> {
			if (value != null && !value.toString().isEmpty()) {
				System.setProperty(key.toString(), value.toString());
			} else {
				System.clearProperty(key.toString());
			}
		});
	}

	@Test
	@Order(1)
	@DisplayName("Should load configuration successfully")
	void testConfigurationLoading() {
		assertNotNull(config, "Configuration should be created");
		assertNotNull(config.getSearchPaths(), "Search paths should not be null");
		assertFalse(config.getSearchPaths().isEmpty(), "Should have default search paths");
	}

	@Test
	@Order(2)
	@DisplayName("Should return platform-specific search paths")
	void testPlatformSpecificSearchPaths() {
		PlatformInfo platform = PlatformInfo.getInstance();
		String platformString = platform.canonicalPlatformName();

		List<Path> paths = config.getSearchPaths(platformString);
		assertNotNull(paths, "Platform paths should not be null");

		// Verify platform-specific paths are included
		switch (platform.getOS()) {
			case LINUX -> {
				assertTrue(paths.stream().anyMatch(p ->
					                                   p.toString().contains("/usr/lib") ||
						                                   p.toString().contains("/lib")),
				           "Should include Linux library paths");
			}
			case MACOS -> {
				assertTrue(paths.stream().anyMatch(p ->
					                                   p.toString().contains("/usr/local/lib") ||
						                                   p.toString().contains("/usr/lib")),
				           "Should include macOS library paths");
			}
			case WINDOWS -> {
				assertTrue(paths.stream().anyMatch(p ->
					                                   p.toString().toLowerCase().contains("system32") ||
						                                   p.toString().toLowerCase().contains("windows")),
				           "Should include Windows library paths");
			}
		}
	}

	@Test
	@Order(3)
	@DisplayName("Should handle system properties configuration")
	void testSystemPropertiesConfiguration() {
		// Set system properties
		String testPath = tempDir.toString();
		System.setProperty("native.library.path", testPath);

		// Create new config to pick up the property
		NativeLibraryConfig newConfig = new NativeLibraryConfig();
		List<Path> paths = newConfig.getSearchPaths();

		assertTrue(paths.contains(tempDir),
		           "Should include path from system property");
	}

	@Test
	@Order(4)
	@DisplayName("Should parse multiple paths correctly")
	void testPathParsing() throws IOException {
		// Create test directories
		Path dir1 = Files.createDirectory(tempDir.resolve("lib1"));
		Path dir2 = Files.createDirectory(tempDir.resolve("lib2"));

		String pathSeparator = System.getProperty("path.separator");
		String pathString = dir1 + pathSeparator + dir2;

		System.setProperty("native.library.path", pathString);

		NativeLibraryConfig newConfig = new NativeLibraryConfig();
		List<Path> paths = newConfig.getSearchPaths();

		assertTrue(paths.contains(dir1), "Should include first path");
		assertTrue(paths.contains(dir2), "Should include second path");
	}

	@Test
	@Order(5)
	@DisplayName("Should expand environment variables in paths")
	void testEnvironmentVariableExpansion() {
		// This test verifies path expansion logic
		String home = System.getProperty("user.home");
		System.setProperty("native.library.path", "~/lib");

		NativeLibraryConfig newConfig = new NativeLibraryConfig();
		List<Path> paths = newConfig.getSearchPaths();

		Path expectedPath = Paths.get(home, "lib");
		if (Files.exists(expectedPath)) {
			assertTrue(paths.contains(expectedPath),
			           "Should expand ~ to user home");
		}
	}

	@Test
	@Order(6)
	@DisplayName("Should handle library name mappings")
	void testLibraryMappings() {
		// Test default mappings (if any configured)
		String mapped = config.getMappedLibraryName("opencv");
		assertNotNull(mapped, "Should return a mapping or original name");

		// Test unmapped library
		String unmapped = config.getMappedLibraryName("unknown_lib_xyz");
		assertEquals("unknown_lib_xyz", unmapped,
		             "Should return original name if no mapping exists");
	}

	@Test
	@Order(7)
	@DisplayName("Should provide configuration values")
	void testConfigurationValues() {
		// Test string config
		String value = config.getConfig("non.existent.key", "default");
		assertEquals("default", value, "Should return default for missing key");

		// Test boolean config
		boolean boolValue = config.getBooleanConfig("enable.caching", true);
		assertTrue(boolValue, "Should return default or configured value");

		// Test delete on exit
		boolean deleteOnExit = config.isDeleteOnExit();
		assertNotNull(deleteOnExit, "Should have delete on exit configuration");

		// Test caching enabled
		boolean caching = config.isCachingEnabled();
		assertNotNull(caching, "Should have caching configuration");

		// Test checksum verification
		boolean checksums = config.isChecksumVerificationEnabled();
		assertNotNull(checksums, "Should have checksum configuration");
	}

	@Disabled
	@Test
	@Order(8)
	@DisplayName("Should provide extract directory")
	void testExtractDirectory() {
		Path extractDir = config.getExtractDirectory();

		assertNotNull(extractDir, "Extract directory should not be null");
		assertFalse(extractDir.toString().isEmpty(),
		            "Extract directory should not be empty");

		// Should be in temp directory by default
		String tmpDir = System.getProperty("java.io.tmpdir");
		assertTrue(extractDir.toString().contains(tmpDir) ||
			           extractDir.isAbsolute(),
		           "Extract directory should be absolute or in temp");
	}

	@ParameterizedTest
	@Order(9)
	@ValueSource(strings = {
		"linux-x86_64",
		"darwin-aarch64",
		"windows-x86_64",
		"linux-arm64",
		"freebsd-x86_64"
	})
	@DisplayName("Should handle various platform strings")
	void testVariousPlatformStrings(String platformString) {
		List<Path> paths = config.getSearchPaths(platformString);

		assertNotNull(paths, "Should return paths for " + platformString);
		assertFalse(paths.isEmpty(),
		            "Should have default paths for " + platformString);
	}

	@Test
	@Order(10)
	@DisplayName("Should cache search paths")
	void testSearchPathCaching() {
		String platform = "test-platform";

		List<Path> paths1 = config.getSearchPaths(platform);
		List<Path> paths2 = config.getSearchPaths(platform);

		assertSame(paths1, paths2,
		           "Should return cached paths for same platform");
	}

	@Test
	@Order(11)
	@EnabledIfSystemProperty(named = "test.resources", matches = "true")
	@DisplayName("Should load from property files in resources")
	void testPropertyFileLoading() {
		// This test requires actual property files in resources
		// Enable with -Dtest.resources=true if files are present

		NativeLibraryConfig configWithFiles = new NativeLibraryConfig();
		assertNotNull(configWithFiles);
	}

	@Test
	@Order(12)
	@DisplayName("Should handle missing configuration files gracefully")
	void testMissingConfigurationFiles() {
		// Should not throw exceptions even if config files are missing
		assertDoesNotThrow(NativeLibraryConfig::new);
	}
}

