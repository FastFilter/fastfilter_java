package org.fastfilter.ffi;


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("LibC Type Detection Tests")
class LibCTypeTest {

	private LibCType.DetectionContext context;

	@BeforeEach
	void setUp() {
		context = new LibCType.DetectionContext();
		LibCType.clearCache(); // Clear cached detection
	}

	@Test
	@Order(1)
	@DisplayName("Should detect current system libc")
	void testDetectCurrentSystem() {
		LibCInfo libc = LibCType.detectCurrent();

		assertNotNull(libc, "LibC detection should not return null");
		assertNotNull(libc.getType(), "LibC type should not be null");
		assertNotEquals(LibCType.UNKNOWN, libc.getType(),
		                "Should detect actual libc, not UNKNOWN (unless in unusual environment)");

		System.out.println("Detected LibC: " + libc);
		System.out.println("Version: " + libc.version());
		System.out.println("Confidence: " + libc.getConfidenceLevel() + "%");

		// Verify detected type matches system
		String osName = System.getProperty("os.name").toLowerCase();
		if (osName.contains("linux")) {
			assertTrue(
				libc.getType() == LibCType.GLIBC ||
					libc.getType() == LibCType.MUSL ||
					libc.getType() == LibCType.BIONIC,
				"Linux should detect glibc, musl, or bionic"
			);
		} else if (osName.contains("mac") || osName.contains("darwin")) {
			assertEquals(LibCType.DARWIN_LIBC, libc.getType(),
			             "macOS should detect Darwin libc");
		} else if (osName.contains("windows")) {
			assertEquals(LibCType.MSVCRT, libc.getType(),
			             "Windows should detect MSVCRT");
		}
	}

	@Test
	@Order(2)
	@DisplayName("Should cache detection results")
	void testDetectionCaching() {
		LibCInfo first = LibCType.detectCurrent();
		LibCInfo second = LibCType.detectCurrent();

		assertSame(first, second, "Should return cached instance");

		// Clear cache and detect again
		LibCType.clearCache();
		LibCInfo third = LibCType.detectCurrent();

		assertNotSame(first, third, "Should create new instance after cache clear");
		assertEquals(first.getType(), third.getType(),
		             "Should detect same libc type");
	}

	@Test
	@Order(3)
	@DisplayName("Should provide library paths for detected libc")
	void testLibraryPaths() {
		LibCInfo libc = LibCType.detectCurrent();
		List<Path> paths = libc.getLibraryPaths();

		assertNotNull(paths, "Library paths should not be null");
		assertFalse(paths.isEmpty(), "Should have at least one library path");

		// At least one path should exist on the system
		boolean hasValidPath = paths.stream().anyMatch(Files::exists);
		assertTrue(hasValidPath, "At least one library path should exist");

		paths.forEach(path ->
			              System.out.println("Library path: " + path +
				                                 " (exists: " + Files.exists(path) + ")"));
	}

	@Test
	@Order(4)
	@DisplayName("Should provide features for detected libc")
	void testLibCFeatures() {
		LibCInfo libc = LibCType.detectCurrent();
		Set<String> features = libc.getFeatures();

		assertNotNull(features, "Features should not be null");
		assertFalse(features.isEmpty(), "Should have at least one feature");

		System.out.println("LibC features: " + features);

		// Verify feature consistency
		if (libc.getType() == LibCType.GLIBC) {
			assertTrue(features.contains("gnu-extensions"),
			           "Glibc should have GNU extensions");
		} else if (libc.getType() == LibCType.MUSL) {
			assertTrue(features.contains("lightweight"),
			           "Musl should be lightweight");
		}
	}

	@Test
	@Order(5)
	@DisplayName("Should have valid confidence level")
	void testConfidenceLevel() {
		LibCInfo libc = LibCType.detectCurrent();
		int confidence = libc.getConfidenceLevel();

		assertTrue(confidence >= 0 && confidence <= 100,
		           "Confidence level should be between 0 and 100");

		// Known systems should have high confidence
		String osName = System.getProperty("os.name").toLowerCase();
		if (osName.contains("linux") || osName.contains("mac") || osName.contains("windows")) {
			assertTrue(confidence >= 60,
			           "Known systems should have confidence >= 60%");
		}
	}

	@ParameterizedTest
	@Order(6)
	@EnumSource(LibCType.class)
	@DisplayName("Each LibC type should have valid metadata")
	void testLibCMetadata(LibCType type) {
		assertNotNull(type.getName(), "Name should not be null");
		assertNotNull(type.getDisplayName(), "Display name should not be null");
		assertNotNull(type.getMinVersion(), "Min version should not be null");
		assertNotNull(type.getMaxVersion(), "Max version should not be null");

		assertFalse(type.getName().isEmpty(), "Name should not be empty");
		assertFalse(type.getDisplayName().isEmpty(), "Display name should not be empty");
	}

	@Test
	@Order(7)
	@DisplayName("Should handle detection context properly")
	void testDetectionContext() {
		LibCType.DetectionContext ctx = new LibCType.DetectionContext();

		assertNotNull(ctx.getLinker(), "Linker should not be null");
		assertNotNull(ctx.getStdLibLookup(), "StdLib lookup should not be null");
		assertEquals("unknown", ctx.getDetectedVersion(),
		             "Initial version should be unknown");
		assertEquals(0, ctx.getConfidenceLevel(),
		             "Initial confidence should be 0");

		// Test setters
		ctx.setDetectedVersion("1.2.3");
		ctx.setConfidenceLevel(75);

		assertEquals("1.2.3", ctx.getDetectedVersion());
		assertEquals(75, ctx.getConfidenceLevel());

		// Test reset
		ctx.reset();
		assertEquals("unknown", ctx.getDetectedVersion());
		assertEquals(0, ctx.getConfidenceLevel());
	}

	@Test
	@Order(8)
	@DisplayName("LibCInfo convenience methods should work correctly")
	void testLibCInfoConvenienceMethods() {
		LibCInfo libc = LibCType.detectCurrent();

		// Only one should be true (except UNKNOWN)
		if (libc.getType() != LibCType.UNKNOWN) {
			int trueCount = 0;
			if (libc.isMusl()) trueCount++;
			if (libc.isGlibc()) trueCount++;
			if (libc.isAndroid()) trueCount++;
			if (libc.isBSD()) trueCount++;
			if (libc.isMacOS()) trueCount++;
			if (libc.isWindows()) trueCount++;

			assertEquals(1, trueCount, "Only one platform check should be true");
		}
	}

	@Test
	@Order(9)
	@EnabledOnOs(OS.LINUX)
	@DisplayName("Should detect glibc or musl on Linux")
	void testLinuxDetection() {
		LibCInfo libc = LibCType.detectCurrent();

		assertTrue(
			libc.isGlibc() || libc.isMusl() || libc.isAndroid(),
			"Linux should detect glibc, musl, or Android"
		);

		// Check for Alpine Linux (musl)
		if (Files.exists(Paths.get("/etc/alpine-release"))) {
			assertTrue(libc.isMusl(), "Alpine Linux should detect musl");
		}
	}

	@Test
	@Order(10)
	@EnabledOnOs(OS.MAC)
	@DisplayName("Should detect Darwin libc on macOS")
	void testMacOSDetection() {
		LibCInfo libc = LibCType.detectCurrent();

		assertTrue(libc.isMacOS(), "macOS should detect Darwin libc");
		assertEquals(LibCType.DARWIN_LIBC, libc.getType());
		assertTrue(libc.version().contains("macos"),
		           "Version should contain 'macos'");
	}

	@Test
	@Order(11)
	@EnabledOnOs(OS.WINDOWS)
	@DisplayName("Should detect MSVCRT on Windows")
	void testWindowsDetection() {
		LibCInfo libc = LibCType.detectCurrent();

		assertTrue(libc.isWindows(), "Windows should detect MSVCRT");
		assertEquals(LibCType.MSVCRT, libc.getType());
	}
}
