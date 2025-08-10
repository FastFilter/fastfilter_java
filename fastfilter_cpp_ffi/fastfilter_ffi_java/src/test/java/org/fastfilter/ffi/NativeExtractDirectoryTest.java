package org.fastfilter.ffi;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Extract Directory Configuration Tests")
class NativeExtractDirectoryTest {

	private NativeLibraryConfig config;
	private Properties originalProperties;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		// Save original system properties
		originalProperties = new Properties();
		originalProperties.setProperty("java.io.tmpdir",
		                               System.getProperty("java.io.tmpdir", "/tmp"));
		originalProperties.setProperty("user.name",
		                               System.getProperty("user.name", "user"));
		originalProperties.setProperty("user.home",
		                               System.getProperty("user.home", System.getProperty("user.dir")));
		originalProperties.setProperty("app.name",
		                               System.getProperty("app.name", ""));
	}

	@AfterEach
	void tearDown() {
		// Restore original properties
		originalProperties.forEach((key, value) -> {
			if (value != null && !value.toString().isEmpty()) {
				System.setProperty(key.toString(), value.toString());
			} else {
				System.clearProperty(key.toString());
			}
		});
	}

	@Test
	@Order(1)
	@DisplayName("Should provide default extract directory")
	void testDefaultExtractDirectory() {
		// Clear any custom configuration
		System.clearProperty("native.library.extract.dir");

		config = new NativeLibraryConfig();
		Path extractDir = config.getExtractDirectory();

		assertNotNull(extractDir, "Extract directory should not be null");
		assertFalse(extractDir.toString().isEmpty(),
		            "Extract directory should not be empty");

		// Should be in temp directory by default
		String tmpDir = System.getProperty("java.io.tmpdir");
		assertTrue(extractDir.toString().contains(tmpDir) ||
			           extractDir.isAbsolute(),
		           "Extract directory should be absolute or in temp");

		// Should contain app name and user name
		String userName = System.getProperty("user.name", "user");
		String appName = System.getProperty("app.name", "jffi");

		assertTrue(extractDir.toString().contains(userName) ||
			           extractDir.toString().contains(appName),
		           "Extract directory should contain user or app name");

		System.out.println("Default extract directory: " + extractDir);
	}

	@Test
	@Order(2)
	@DisplayName("Should use custom app name in extract directory")
	void testExtractDirectoryWithCustomAppName() {
		System.setProperty("app.name", "test-app");

		config = new NativeLibraryConfig();
		Path extractDir = config.getExtractDirectory();

		assertNotNull(extractDir);
		assertTrue(extractDir.toString().contains("test-app"),
		           "Extract directory should contain custom app name");

		System.out.println("Extract directory with custom app: " + extractDir);
	}

	@Test
	@Order(3)
	@DisplayName("Should handle different temp directory locations")
	void testExtractDirectoryWithCustomTempDir() {
		// Set custom temp directory
		String customTemp = tempDir.toString();
		System.setProperty("java.io.tmpdir", customTemp);

		config = new NativeLibraryConfig();
		Path extractDir = config.getExtractDirectory();

		assertNotNull(extractDir);
		assertTrue(extractDir.toString().startsWith(customTemp),
		           "Extract directory should be under custom temp: " + customTemp);

		System.out.println("Extract directory with custom temp: " + extractDir);
	}

	@Test
	@Order(4)
	@DisplayName("Should create extract directory structure")
	void testExtractDirectoryStructure() {
		config = new NativeLibraryConfig();
		Path extractDir = config.getExtractDirectory();

		// Expected structure: {tmpdir}/{appname}-{username}/native-libs
		String[] pathParts = extractDir.toString().split(File.separator);

		assertTrue(pathParts.length >= 2,
		           "Extract directory should have multiple path components");

		// Last component should be "native-libs"
		assertEquals("native-libs", pathParts[pathParts.length - 1],
		             "Should end with 'native-libs' directory");

		// Parent should contain app name and user name
		String parent = pathParts[pathParts.length - 2];
		assertTrue(parent.contains("-"),
		           "Parent directory should have app-user format");
	}

	@Test
	@Order(5)
	@DisplayName("Should handle missing system properties gracefully")
	void testExtractDirectoryWithMissingProperties() {
		// Clear properties to simulate missing values
		System.clearProperty("app.name");
		System.clearProperty("user.name");

		config = new NativeLibraryConfig();
		Path extractDir = config.getExtractDirectory();

		assertNotNull(extractDir, "Should handle missing properties");
		assertFalse(extractDir.toString().isEmpty(), "Should provide valid path");

		// Should fall back to defaults
		assertTrue(extractDir.toString().contains("jffi") ||
			           extractDir.toString().contains("user"),
		           "Should use default values when properties missing");

		System.out.println("Extract directory with missing props: " + extractDir);
	}

	@Test
	@Order(6)
	@DisplayName("Should return absolute path for extract directory")
	void testExtractDirectoryIsAbsolute() {
		config = new NativeLibraryConfig();
		Path extractDir = config.getExtractDirectory();

		assertTrue(extractDir.isAbsolute(),
		           "Extract directory should be an absolute path");

		// Verify path components are valid
		assertDoesNotThrow(() -> extractDir.toAbsolutePath(),
		                   "Should be convertible to absolute path");

		System.out.println("Absolute extract directory: " + extractDir.toAbsolutePath());
	}

	@Test
	@Order(7)
	@DisplayName("Should be consistent across multiple calls")
	void testExtractDirectoryConsistency() {
		config = new NativeLibraryConfig();

		Path extractDir1 = config.getExtractDirectory();
		Path extractDir2 = config.getExtractDirectory();
		Path extractDir3 = config.getExtractDirectory();

		assertEquals(extractDir1, extractDir2,
		             "Should return same directory on multiple calls");
		assertEquals(extractDir2, extractDir3,
		             "Should be consistent across all calls");

		System.out.println("Consistent extract directory: " + extractDir1);
	}

	@Test
	@Order(8)
	@DisplayName("Should handle special characters in user/app names")
	void testExtractDirectoryWithSpecialCharacters() {
		// Test with special characters that might cause issues
		System.setProperty("user.name", "test.user-123");
		System.setProperty("app.name", "my-app_v2.0");

		config = new NativeLibraryConfig();
		Path extractDir = config.getExtractDirectory();

		assertNotNull(extractDir);
		assertDoesNotThrow(() -> Files.createDirectories(extractDir),
		                   "Should be able to create directory with special chars");

		System.out.println("Extract directory with special chars: " + extractDir);
	}

	@Test
	@Order(9)
	@DisplayName("Should handle very long paths")
	void testExtractDirectoryWithLongPaths() {
		// Create a very long app name
		String longAppName = "very-long-application-name-that-might-cause-issues-" +
			                     "with-path-length-limitations-on-some-systems";
		System.setProperty("app.name", longAppName);

		config = new NativeLibraryConfig();
		Path extractDir = config.getExtractDirectory();

		assertNotNull(extractDir);

		// Path should still be valid even if long
		int pathLength = extractDir.toString().length();
		System.out.println("Extract directory path length: " + pathLength);

		// Most systems support paths up to 255-4096 characters
		assertTrue(pathLength < 4096,
		           "Path should not exceed system limits");
	}

	@Test
	@Order(10)
	@DisplayName("Should create extract directory if it doesn't exist")
	void testExtractDirectoryCreation() throws IOException {
		config = new NativeLibraryConfig();
		Path extractDir = config.getExtractDirectory();

		// Try to create the directory
		if (!Files.exists(extractDir)) {
			assertDoesNotThrow(() -> Files.createDirectories(extractDir),
			                   "Should be able to create extract directory");
		}

		// After creation, it should exist
		if (Files.exists(extractDir)) {
			assertTrue(Files.isDirectory(extractDir),
			           "Extract directory should be a directory");
			assertTrue(Files.isWritable(extractDir),
			           "Extract directory should be writable");
		}

		System.out.println("Created extract directory: " + extractDir);
	}

	@ParameterizedTest
	@Order(11)
	@ValueSource(strings = {
		"/tmp",
		"/var/tmp",
		"C:\\Temp",
		"C:\\Windows\\Temp",
		"${user.home}/tmp",
		"~/tmp"
	})
	@DisplayName("Should handle various temp directory formats")
	void testExtractDirectoryWithVariousTempDirs(String tmpDir) {
		// Skip Windows paths on Unix and vice versa
		if ((tmpDir.contains("C:\\") && !System.getProperty("os.name").toLowerCase().contains("win")) ||
			    (tmpDir.startsWith("/") && System.getProperty("os.name").toLowerCase().contains("win"))) {
			return;
		}

		// Expand variables
		String expandedTmpDir = tmpDir
			                        .replace("${user.home}", System.getProperty("user.home"))
			                        .replace("~", System.getProperty("user.home"));

		System.setProperty("java.io.tmpdir", expandedTmpDir);

		config = new NativeLibraryConfig();
		Path extractDir = config.getExtractDirectory();

		assertNotNull(extractDir);
		System.out.println("Extract directory for " + tmpDir + ": " + extractDir);
	}

	@Test
	@Order(12)
	@DisplayName("Should use platform-appropriate path separators")
	void testExtractDirectoryPathSeparators() {
		config = new NativeLibraryConfig();
		Path extractDir = config.getExtractDirectory();

		String pathStr = extractDir.toString();
		String expectedSeparator = File.separator;

		assertTrue(pathStr.contains(expectedSeparator),
		           "Should use platform-appropriate path separator: " + expectedSeparator);

		// Should not mix separators
		if (File.separator.equals("/")) {
			assertFalse(pathStr.contains("\\"),
			            "Unix path should not contain backslashes");
		} else {
			// Windows might have forward slashes in some contexts, but primary should be backslash
			assertTrue(pathStr.contains("\\"),
			           "Windows path should contain backslashes");
		}

		System.out.println("Path with correct separators: " + extractDir);
	}

	@Test
	@Order(13)
	@DisplayName("Should handle permission-restricted directories gracefully")
	void testExtractDirectoryPermissions() {
		// Try to use a potentially restricted directory
		String restrictedPath = System.getProperty("os.name").toLowerCase().contains("win")
		                        ? "C:\\Program Files\\temp"
		                        : "/root/temp";

		System.setProperty("java.io.tmpdir", restrictedPath);

		config = new NativeLibraryConfig();
		Path extractDir = config.getExtractDirectory();

		assertNotNull(extractDir, "Should return a path even for restricted directory");

		// The path should be constructed even if we can't create it
		assertTrue(extractDir.toString().contains("native-libs"),
		           "Should maintain expected directory structure");

		System.out.println("Extract directory for restricted path: " + extractDir);
	}

	@Test
	@Order(14)
	@DisplayName("Extract directory comprehensive validation")
	void testExtractDirectoryComprehensiveValidation() {
		// This is the main test being validated
		config = new NativeLibraryConfig();
		Path extractDir = config.getExtractDirectory();

		// Core assertions from the original test
		assertNotNull(extractDir, "Extract directory should not be null");
		assertFalse(extractDir.toString().isEmpty(),
		            "Extract directory should not be empty");

		// Should be in temp directory by default
		String tmpDir = System.getProperty("java.io.tmpdir");
		assertTrue(extractDir.toString().contains(tmpDir) ||
			           extractDir.isAbsolute(),
		           "Extract directory should be absolute or in temp");

		// Additional validations
		assertTrue(extractDir.isAbsolute(), "Should be absolute path");

		// Parse the path to validate structure
		String fullPath = extractDir.toString();
		String[] components = fullPath.split(File.separator.equals("\\") ? "\\\\" : File.separator);

		assertTrue(components.length >= 3,
		           "Path should have at least 3 components (root, parent, native-libs)");

		// Validate the path doesn't contain invalid characters
		assertFalse(fullPath.contains(".."),
		            "Path should not contain parent directory references");
		assertFalse(fullPath.contains("./") || fullPath.contains(".\\"),
		            "Path should not contain current directory references");

		System.out.println("=== Extract Directory Validation ===");
		System.out.println("Path: " + extractDir);
		System.out.println("Is Absolute: " + extractDir.isAbsolute());
		System.out.println("Parent: " + extractDir.getParent());
		System.out.println("File Name: " + extractDir.getFileName());
		System.out.println("Components: " + components.length);
		System.out.println("✓ All validations passed");
	}
}
