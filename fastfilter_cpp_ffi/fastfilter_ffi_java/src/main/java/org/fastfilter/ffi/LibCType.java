package org.fastfilter.ffi;

// ============================================
// LibCType.java - Comprehensive LibC Detection with JDK 24 FFI
// ============================================


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enumeration of C Library implementations with detection strategies
 * Using JDK 24 Foreign Function & Memory API for native detection
 */
public enum LibCType {

	/**
	 * GNU C Library - The most common libc on Linux
	 */
	GLIBC("glibc", "GNU C Library", "2.0", "50.0") {
		@Override
		public boolean detect(DetectionContext context) {
			// Strategy 1: Check for __glibc_version symbol using FFI
			try {
				Optional<MemorySegment> glibcVersion = context.getStdLibLookup()
				                                              .find("gnu_get_libc_version");

				if (glibcVersion.isPresent()) {
					// Call gnu_get_libc_version() to get version string
					MethodHandle getVersion = context.getLinker().downcallHandle(
						glibcVersion.get(),
						FunctionDescriptor.of(ValueLayout.ADDRESS)
					);

					MemorySegment versionPtr = (MemorySegment) getVersion.invoke();
					String version = versionPtr.getString(0);

					context.setDetectedVersion(version);
					context.setConfidenceLevel(100);
					LOGGER.info("Detected glibc version: " + version);
					return true;
				}
			} catch (Throwable e) {
				LOGGER.fine("FFI detection failed for glibc: " + e.getMessage());
			}

			// Strategy 2: Check ldd version
			try {
				String lddOutput = context.executeCommand("ldd", "--version");
				if (lddOutput.toLowerCase().contains("glibc") ||
					    lddOutput.toLowerCase().contains("gnu libc")) {

					// Extract version
					Pattern versionPattern = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)");
					Matcher matcher = versionPattern.matcher(lddOutput);
					if (matcher.find()) {
						context.setDetectedVersion(matcher.group(1));
					}

					context.setConfidenceLevel(90);
					return true;
				}
			} catch (Exception e) {
				LOGGER.fine("ldd detection failed: " + e.getMessage());
			}

			// Strategy 3: Check for glibc-specific files
			if (Files.exists(Paths.get("/lib/x86_64-linux-gnu/libc.so.6")) ||
				    Files.exists(Paths.get("/lib64/libc.so.6")) ||
				    Files.exists(Paths.get("/lib/i386-linux-gnu/libc.so.6"))) {

				context.setConfidenceLevel(70);
				return true;
			}

			// Strategy 4: Check for GNU-specific symbols
			try {
				String[] gnuSymbols = {"__glibc_version", "__libc_start_main", "__gmon_start__"};
				for (String symbol : gnuSymbols) {
					if (context.getStdLibLookup().find(symbol).isPresent()) {
						context.setConfidenceLevel(60);
						return true;
					}
				}
			} catch (Exception e) {
				// Ignore
			}

			return false;
		}

		@Override
		public LibCInfo getInfo(DetectionContext context) {
			return new LibCInfo(
				this,
				context.getDetectedVersion(),
				getLibraryPaths(),
				getFeatures(),
				context.getConfidenceLevel()
			);
		}

		@Override
		public List<Path> getLibraryPaths() {
			return Arrays.asList(
				Paths.get("/lib/x86_64-linux-gnu"),
				Paths.get("/lib64"),
				Paths.get("/usr/lib/x86_64-linux-gnu"),
				Paths.get("/usr/lib64"),
				Paths.get("/lib"),
				Paths.get("/usr/lib")
			);
		}

		@Override
		public Set<String> getFeatures() {
			return Set.of(
				"NSS", "nscd", "locales", "iconv",
				"dynamic-linker", "pthread", "dl",
				"gnu-extensions", "versioned-symbols"
			);
		}
	},

	/**
	 * Musl libc - Lightweight alternative used in Alpine Linux
	 */
	MUSL("musl", "musl libc", "0.5.0", "2.0.0") {
		@Override
		public boolean detect(DetectionContext context) {
			// Strategy 1: Direct detection via musl-specific function
			try {
				// Musl doesn't have gnu_get_libc_version but we can check for musl-specific symbols
				Optional<MemorySegment> muslSymbol = context.getStdLibLookup()
				                                            .find("__musl_libc_version");

				if (muslSymbol.isPresent()) {
					context.setConfidenceLevel(100);
					LOGGER.info("Detected musl via __musl_libc_version symbol");
					return true;
				}
			} catch (Throwable e) {
				LOGGER.fine("FFI detection failed for musl: " + e.getMessage());
			}

			// Strategy 2: Check ldd version for musl
			try {
				String lddOutput = context.executeCommand("ldd", "--version");
				if (lddOutput.toLowerCase().contains("musl")) {
					// Extract version
					Pattern versionPattern = Pattern.compile("musl libc.*?(\\d+\\.\\d+\\.\\d+)");
					Matcher matcher = versionPattern.matcher(lddOutput);
					if (matcher.find()) {
						context.setDetectedVersion(matcher.group(1));
					}

					context.setConfidenceLevel(95);
					return true;
				}
			} catch (Exception e) {
				// ldd might not support --version on musl
			}

			// Strategy 3: Check for Alpine Linux
			if (Files.exists(Paths.get("/etc/alpine-release"))) {
				try {
					String version = Files.readString(Paths.get("/etc/alpine-release")).trim();
					context.setDetectedVersion("alpine-" + version);
				} catch (Exception e) {
					// Ignore
				}
				context.setConfidenceLevel(90);
				return true;
			}

			// Strategy 4: Check for musl-specific files
			if (Files.exists(Paths.get("/lib/ld-musl-x86_64.so.1")) ||
				    Files.exists(Paths.get("/lib/ld-musl-aarch64.so.1")) ||
				    Files.exists(Paths.get("/lib/libc.musl-x86_64.so.1"))) {

				context.setConfidenceLevel(85);
				return true;
			}

			// Strategy 5: Check interpreter of a system binary
			try {
				String readelfOutput = context.executeCommand("readelf", "-l", "/bin/ls");
				if (readelfOutput.contains("ld-musl")) {
					context.setConfidenceLevel(80);
					return true;
				}
			} catch (Exception e) {
				// Ignore
			}

			return false;
		}

		@Override
		public LibCInfo getInfo(DetectionContext context) {
			return new LibCInfo(
				this,
				context.getDetectedVersion(),
				getLibraryPaths(),
				getFeatures(),
				context.getConfidenceLevel()
			);
		}

		@Override
		public List<Path> getLibraryPaths() {
			return Arrays.asList(
				Paths.get("/usr/lib"),
				Paths.get("/lib"),
				Paths.get("/usr/local/lib")
			);
		}

		@Override
		public Set<String> getFeatures() {
			return Set.of(
				"lightweight", "static-friendly", "posix-compliant",
				"no-nss", "simple-dns", "mit-licensed"
			);
		}
	},

	/**
	 * uClibc - Embedded systems C library
	 */
	UCLIBC("uclibc", "uClibc", "0.9.0", "1.0.50") {
		@Override
		public boolean detect(DetectionContext context) {
			// Strategy 1: Check for uClibc-specific symbols
			try {
				Optional<MemorySegment> uclibcSymbol = context.getStdLibLookup()
				                                              .find("__uclibc_main");

				if (uclibcSymbol.isPresent()) {
					context.setConfidenceLevel(95);
					return true;
				}
			} catch (Throwable e) {
				// Ignore
			}

			// Strategy 2: Check ldd output
			try {
				String lddOutput = context.executeCommand("ldd", "--version");
				if (lddOutput.toLowerCase().contains("uclibc")) {
					Pattern versionPattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");
					Matcher matcher = versionPattern.matcher(lddOutput);
					if (matcher.find()) {
						context.setDetectedVersion(matcher.group(1));
					}
					context.setConfidenceLevel(90);
					return true;
				}
			} catch (Exception e) {
				// Ignore
			}

			// Strategy 3: Check for uClibc files
			if (Files.exists(Paths.get("/lib/ld-uClibc.so.0")) ||
				    Files.exists(Paths.get("/lib/libuClibc-0.9.33.2.so"))) {
				context.setConfidenceLevel(80);
				return true;
			}

			return false;
		}

		@Override
		public LibCInfo getInfo(DetectionContext context) {
			return new LibCInfo(
				this,
				context.getDetectedVersion(),
				getLibraryPaths(),
				getFeatures(),
				context.getConfidenceLevel()
			);
		}

		@Override
		public List<Path> getLibraryPaths() {
			return Arrays.asList(
				Paths.get("/lib"),
				Paths.get("/usr/lib")
			);
		}

		@Override
		public Set<String> getFeatures() {
			return Set.of(
				"embedded", "configurable", "small-footprint",
				"mmu-optional", "posix-threads"
			);
		}
	},

	/**
	 * Bionic - Android's C library
	 */
	BIONIC("bionic", "Android Bionic", "1.0", "14.0") {
		@Override
		public boolean detect(DetectionContext context) {
			// Strategy 1: Check for Android property
			if ("The Android Project".equals(System.getProperty("java.vendor"))) {
				context.setConfidenceLevel(100);
				return true;
			}

			// Strategy 2: Check for Android-specific files
			if (Files.exists(Paths.get("/system/build.prop")) ||
				    Files.exists(Paths.get("/system/lib/libc.so")) ||
				    Files.exists(Paths.get("/system/lib64/libc.so"))) {

				// Try to get Android version
				try {
					String buildProp = Files.readString(Paths.get("/system/build.prop"));
					Pattern versionPattern = Pattern.compile("ro.build.version.release=(.+)");
					Matcher matcher = versionPattern.matcher(buildProp);
					if (matcher.find()) {
						context.setDetectedVersion("android-" + matcher.group(1));
					}
				} catch (Exception e) {
					// Ignore
				}

				context.setConfidenceLevel(95);
				return true;
			}

			// Strategy 3: Check for Bionic-specific symbols via FFI
			try {
				Optional<MemorySegment> bionicSymbol = context.getStdLibLookup()
				                                              .find("__bionic_clone");

				if (bionicSymbol.isPresent()) {
					context.setConfidenceLevel(90);
					return true;
				}
			} catch (Throwable e) {
				// Ignore
			}

			// Strategy 4: Check system properties
			try {
				String osName = System.getProperty("os.name", "").toLowerCase();
				if (osName.contains("android")) {
					context.setConfidenceLevel(85);
					return true;
				}
			} catch (Exception e) {
				// Ignore
			}

			return false;
		}

		@Override
		public LibCInfo getInfo(DetectionContext context) {
			return new LibCInfo(
				this,
				context.getDetectedVersion(),
				getLibraryPaths(),
				getFeatures(),
				context.getConfidenceLevel()
			);
		}

		@Override
		public List<Path> getLibraryPaths() {
			return Arrays.asList(
				Paths.get("/system/lib64"),
				Paths.get("/system/lib"),
				Paths.get("/vendor/lib64"),
				Paths.get("/vendor/lib"),
				Paths.get("/apex/com.android.runtime/lib64"),
				Paths.get("/apex/com.android.runtime/lib")
			);
		}

		@Override
		public Set<String> getFeatures() {
			return Set.of(
				"android", "bsd-based", "no-versioned-symbols",
				"jemalloc", "linker-namespaces", "fortify-source"
			);
		}
	},

	/**
	 * dietlibc - Minimalist C library
	 */
	DIETLIBC("dietlibc", "diet libc", "0.1", "0.34") {
		@Override
		public boolean detect(DetectionContext context) {
			// Strategy 1: Check for dietlibc-specific files
			if (Files.exists(Paths.get("/opt/diet/lib/libc.a")) ||
				    Files.exists(Paths.get("/usr/lib/diet/lib/libc.a"))) {
				context.setConfidenceLevel(80);
				return true;
			}

			// Strategy 2: Check if compiled with dietlibc
			try {
				String nmOutput = context.executeCommand("nm", "/proc/self/exe");
				if (nmOutput.contains("__dietlibc__")) {
					context.setConfidenceLevel(75);
					return true;
				}
			} catch (Exception e) {
				// Ignore
			}

			return false;
		}

		@Override
		public LibCInfo getInfo(DetectionContext context) {
			return new LibCInfo(
				this,
				context.getDetectedVersion(),
				getLibraryPaths(),
				getFeatures(),
				context.getConfidenceLevel()
			);
		}

		@Override
		public List<Path> getLibraryPaths() {
			return Arrays.asList(
				Paths.get("/opt/diet/lib"),
				Paths.get("/usr/lib/diet/lib")
			);
		}

		@Override
		public Set<String> getFeatures() {
			return Set.of(
				"minimalist", "static-only", "small-size",
				"limited-features", "security-focused"
			);
		}
	},

	/**
	 * Newlib - Embedded systems C library
	 */
	NEWLIB("newlib", "Newlib", "1.0", "4.3.0") {
		@Override
		public boolean detect(DetectionContext context) {
			// Strategy 1: Check for newlib-specific symbols
			try {
				Optional<MemorySegment> newlibSymbol = context.getStdLibLookup()
				                                              .find("_newlib_version");

				if (newlibSymbol.isPresent()) {
					context.setConfidenceLevel(85);
					return true;
				}
			} catch (Throwable e) {
				// Ignore
			}

			// Strategy 2: Common in Cygwin
			if (Files.exists(Paths.get("/usr/include/newlib.h"))) {
				context.setConfidenceLevel(70);
				return true;
			}

			return false;
		}

		@Override
		public LibCInfo getInfo(DetectionContext context) {
			return new LibCInfo(
				this,
				context.getDetectedVersion(),
				getLibraryPaths(),
				getFeatures(),
				context.getConfidenceLevel()
			);
		}

		@Override
		public List<Path> getLibraryPaths() {
			return Arrays.asList(
				Paths.get("/usr/lib"),
				Paths.get("/usr/local/lib")
			);
		}

		@Override
		public Set<String> getFeatures() {
			return Set.of(
				"embedded", "portable", "configurable",
				"reentrant", "cross-platform"
			);
		}
	},

	/**
	 * BSD libc - Used in FreeBSD, OpenBSD, NetBSD
	 */
	BSD_LIBC("bsd-libc", "BSD libc", "1.0", "14.0") {
		@Override
		public boolean detect(DetectionContext context) {
			// Strategy 1: Check OS type
			String osName = System.getProperty("os.name", "").toLowerCase();
			if (osName.contains("bsd")) {
				context.setConfidenceLevel(95);

				// Determine specific BSD variant
				if (osName.contains("freebsd")) {
					context.setDetectedVersion("freebsd");
				} else if (osName.contains("openbsd")) {
					context.setDetectedVersion("openbsd");
				} else if (osName.contains("netbsd")) {
					context.setDetectedVersion("netbsd");
				}

				return true;
			}

			// Strategy 2: Check for BSD-specific files
			if (Files.exists(Paths.get("/usr/lib/libc.so")) &&
				    Files.exists(Paths.get("/usr/include/sys/cdefs.h"))) {

				try {
					String cdefs = Files.readString(Paths.get("/usr/include/sys/cdefs.h"));
					if (cdefs.contains("__FreeBSD__") ||
						    cdefs.contains("__OpenBSD__") ||
						    cdefs.contains("__NetBSD__")) {
						context.setConfidenceLevel(85);
						return true;
					}
				} catch (Exception e) {
					// Ignore
				}
			}

			return false;
		}

		@Override
		public LibCInfo getInfo(DetectionContext context) {
			return new LibCInfo(
				this,
				context.getDetectedVersion(),
				getLibraryPaths(),
				getFeatures(),
				context.getConfidenceLevel()
			);
		}

		@Override
		public List<Path> getLibraryPaths() {
			return Arrays.asList(
				Paths.get("/usr/lib"),
				Paths.get("/usr/local/lib"),
				Paths.get("/lib")
			);
		}

		@Override
		public Set<String> getFeatures() {
			return Set.of(
				"bsd-licensed", "secure", "pledge-unveil",
				"capsicum", "jemalloc", "kqueue"
			);
		}
	},

	/**
	 * macOS/Darwin libc
	 */
	DARWIN_LIBC("darwin-libc", "Darwin/macOS libc", "1.0", "1500.0") {
		@Override
		public boolean detect(DetectionContext context) {
			String osName = System.getProperty("os.name", "").toLowerCase();
			if (osName.contains("mac") || osName.contains("darwin")) {
				// Get macOS version
				try {
					String version = context.executeCommand("sw_vers", "-productVersion");
					context.setDetectedVersion("macos-" + version.trim());
				} catch (Exception e) {
					context.setDetectedVersion("macos");
				}

				context.setConfidenceLevel(100);
				return true;
			}

			// Check for macOS-specific files
			if (Files.exists(Paths.get("/usr/lib/libSystem.dylib"))) {
				context.setConfidenceLevel(95);
				return true;
			}

			return false;
		}

		@Override
		public LibCInfo getInfo(DetectionContext context) {
			return new LibCInfo(
				this,
				context.getDetectedVersion(),
				getLibraryPaths(),
				getFeatures(),
				context.getConfidenceLevel()
			);
		}

		@Override
		public List<Path> getLibraryPaths() {
			return Arrays.asList(
				Paths.get("/usr/lib"),
				Paths.get("/usr/local/lib"),
				Paths.get("/System/Library/Frameworks"),
				Paths.get("/opt/homebrew/lib"),     // Apple Silicon
				Paths.get("/usr/local/opt")          // Intel
			);
		}

		@Override
		public Set<String> getFeatures() {
			return Set.of(
				"darwin", "libSystem", "dyld", "frameworks",
				"objective-c", "grand-central-dispatch", "corefoundation"
			);
		}
	},

	/**
	 * Microsoft C Runtime (Windows)
	 */
	MSVCRT("msvcrt", "Microsoft C Runtime", "1.0", "14.0") {
		@Override
		public boolean detect(DetectionContext context) {
			String osName = System.getProperty("os.name", "").toLowerCase();
			if (osName.contains("windows")) {
				// Detect MSVC version
				try {
					// Try to load msvcrt.dll and get version
					Optional<MemorySegment> msvcSymbol = context.getStdLibLookup()
					                                            .find("_get_printf_count_output");

					if (msvcSymbol.isPresent()) {
						context.setConfidenceLevel(100);

						// Try to determine version
						if (Files.exists(Paths.get("C:\\Windows\\System32\\msvcrt.dll"))) {
							context.setDetectedVersion("msvcrt");
						}

						return true;
					}
				} catch (Throwable e) {
					// Still on Windows, just can't determine exact version
					context.setConfidenceLevel(90);
					return true;
				}
			}

			return false;
		}

		@Override
		public LibCInfo getInfo(DetectionContext context) {
			return new LibCInfo(
				this,
				context.getDetectedVersion(),
				getLibraryPaths(),
				getFeatures(),
				context.getConfidenceLevel()
			);
		}

		@Override
		public List<Path> getLibraryPaths() {
			return Arrays.asList(
				Paths.get("C:\\Windows\\System32"),
				Paths.get("C:\\Windows\\SysWOW64"),
				Paths.get(System.getenv("ProgramFiles") + "\\Microsoft Visual Studio"),
				Paths.get(System.getenv("ProgramFiles(x86)") + "\\Microsoft Visual Studio")
			);
		}

		@Override
		public Set<String> getFeatures() {
			return Set.of(
				"windows", "seh", "wide-char", "locale",
				"msvc-extensions", "security-enhanced"
			);
		}
	},

	/**
	 * Unknown or custom libc implementation
	 */
	UNKNOWN("unknown", "Unknown libc", "0.0", "0.0") {
		@Override
		public boolean detect(DetectionContext context) {
			// This is the fallback - always returns true
			context.setConfidenceLevel(0);
			return true;
		}

		@Override
		public LibCInfo getInfo(DetectionContext context) {
			return new LibCInfo(
				this,
				"unknown",
				getLibraryPaths(),
				getFeatures(),
				0
			);
		}

		@Override
		public List<Path> getLibraryPaths() {
			return Arrays.asList(
				Paths.get("/usr/lib"),
				Paths.get("/usr/local/lib"),
				Paths.get("/lib")
			);
		}

		@Override
		public Set<String> getFeatures() {
			return Set.of("unknown");
		}
	};

	// ============================================
	// Instance fields and methods
	// ============================================

	private static final Logger LOGGER = Logger.getLogger(LibCType.class.getName());
	private static volatile LibCInfo detectedLibC = null;

	private final String name;
	private final String displayName;
	private final String minVersion;
	private final String maxVersion;

	LibCType(String name, String displayName, String minVersion, String maxVersion) {
		this.name = name;
		this.displayName = displayName;
		this.minVersion = minVersion;
		this.maxVersion = maxVersion;
	}

	/**
	 * Detect if this is the current libc implementation
	 */
	public abstract boolean detect(DetectionContext context);

	/**
	 * Get detailed information about this libc
	 */
	public abstract LibCInfo getInfo(DetectionContext context);

	/**
	 * Get typical library search paths for this libc
	 */
	public abstract List<Path> getLibraryPaths();

	/**
	 * Get features supported by this libc
	 */
	public abstract Set<String> getFeatures();

	// Getters
	public String getName() { return name; }
	public String getDisplayName() { return displayName; }
	public String getMinVersion() { return minVersion; }
	public String getMaxVersion() { return maxVersion; }

	// ============================================
	// Static detection methods
	// ============================================

	/**
	 * Detect the current system's libc implementation
	 */
	public static LibCInfo detectCurrent() {
		if (detectedLibC != null) {
			return detectedLibC;
		}

		synchronized (LibCType.class) {
			if (detectedLibC != null) {
				return detectedLibC;
			}

			DetectionContext context = new DetectionContext();

			// Try each libc type in order (UNKNOWN is last and always succeeds)
			for (LibCType type : values()) {
				if (type.detect(context)) {
					detectedLibC = type.getInfo(context);
					LOGGER.info("Detected libc: " + detectedLibC);
					return detectedLibC;
				}
				context.reset(); // Reset context for next detection
			}

			// Should never reach here as UNKNOWN always returns true
			detectedLibC = UNKNOWN.getInfo(context);
			return detectedLibC;
		}
	}

	/**
	 * Force re-detection of libc
	 */
	public static void clearCache() {
		detectedLibC = null;
	}

	// ============================================
	// Helper Classes
	// ============================================

	/**
	 * Detection context for passing state between detection strategies
	 */
	public static class DetectionContext {
		private final Linker linker;
		private final SymbolLookup stdLibLookup;
		private String detectedVersion = "unknown";
		private int confidenceLevel = 0;

		public DetectionContext() {
			this.linker = Linker.nativeLinker();
			this.stdLibLookup = linker.defaultLookup();
		}

		public Linker getLinker() { return linker; }
		public SymbolLookup getStdLibLookup() { return stdLibLookup; }

		public String getDetectedVersion() { return detectedVersion; }
		public void setDetectedVersion(String version) { this.detectedVersion = version; }

		public int getConfidenceLevel() { return confidenceLevel; }
		public void setConfidenceLevel(int level) { this.confidenceLevel = level; }

		public void reset() {
			this.detectedVersion = "unknown";
			this.confidenceLevel = 0;
		}

		/**
		 * Execute a system command and return output
		 */
		public String executeCommand(String... command) throws Exception {
			ProcessBuilder pb = new ProcessBuilder(command);
			Process process = pb.start();

			StringBuilder output = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line).append("\n");
				}
			}

			process.waitFor();
			return output.toString();
		}
	}
}
