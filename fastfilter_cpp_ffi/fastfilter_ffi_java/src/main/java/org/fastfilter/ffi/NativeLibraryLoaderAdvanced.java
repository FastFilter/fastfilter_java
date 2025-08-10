package org.fastfilter.ffi;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Advanced native library loader for JDK 24 FFI
 * Supports embedded resources, version management, and platform-specific loading
 */
public class NativeLibraryLoaderAdvanced
{

	private static final String NATIVE_LIB_PREFIX = "/native";
	private static final Map<String, SymbolLookup> loadedLibraries = new ConcurrentHashMap<>();
	private static final Map<String, Path> extractedLibraries = new ConcurrentHashMap<>();
	private static final ReentrantLock extractionLock = new ReentrantLock();

	private final PlatformInfo platform;
	private final Path tempDirectory;
	private final boolean deleteOnExit;
	private final List<Path> searchPaths;

	public NativeLibraryLoaderAdvanced() {
		this(true);
	}

	public NativeLibraryLoaderAdvanced(boolean deleteOnExit) {
		this.platform = PlatformInfo.getInstance();
		this.deleteOnExit = deleteOnExit;
		this.searchPaths = new ArrayList<>();
		this.tempDirectory = createTempDirectory();

		initializeSearchPaths();
	}

	private Path createTempDirectory() {
		try {
			String tmpDir = System.getProperty("java.io.tmpdir");
			String appName = System.getProperty("app.name", "jffi");
			String userName = System.getProperty("user.name", "user");

			Path baseDir = Paths.get(tmpDir, appName + "-" + userName, "native-libs");
			Files.createDirectories(baseDir);

			if (deleteOnExit) {
				Runtime.getRuntime().addShutdownHook(new Thread(() -> {
					try {
						deleteDirectory(baseDir);
					} catch (IOException e) {
						// Ignore cleanup errors
					}
				}));
			}

			return baseDir;
		} catch (IOException e) {
			throw new RuntimeException("Failed to create temp directory", e);
		}
	}

	private void initializeSearchPaths() {
		// Add system library paths
		String libraryPath = System.getProperty("java.library.path");
		if (libraryPath != null) {
			Arrays.stream(libraryPath.split(File.pathSeparator))
			      .map(Paths::get)
			      .filter(Files::exists)
			      .forEach(searchPaths::add);
		}

		// Add platform-specific paths
		switch (platform.getOS()) {
			case LINUX -> {
				searchPaths.add(Paths.get("/usr/lib"));
				searchPaths.add(Paths.get("/usr/local/lib"));
				searchPaths.add(Paths.get("/lib"));

				if (platform.getArch() == CPUArch.X86_64) {
					searchPaths.add(Paths.get("/usr/lib64"));
					searchPaths.add(Paths.get("/lib64"));
					searchPaths.add(Paths.get("/usr/lib/x86_64-linux-gnu"));
				} else if (platform.getArch() == CPUArch.ARM64) {
					searchPaths.add(Paths.get("/usr/lib/aarch64-linux-gnu"));
				}
			}
			case MACOS -> {
				searchPaths.add(Paths.get("/usr/local/lib"));
				searchPaths.add(Paths.get("/opt/homebrew/lib"));  // Apple Silicon
				searchPaths.add(Paths.get("/usr/lib"));
				searchPaths.add(Paths.get("/System/Library/Frameworks"));
			}
			case WINDOWS -> {
				searchPaths.add(Paths.get("C:\\Windows\\System32"));
				searchPaths.add(Paths.get("C:\\Windows\\SysWOW64"));

				String programFiles = System.getenv("ProgramFiles");
				if (programFiles != null) {
					searchPaths.add(Paths.get(programFiles));
				}
			}
		}

		// Add current directory
		searchPaths.add(Paths.get("."));
	}

	/**
	 * Load a native library with automatic platform detection
	 */
	public SymbolLookup loadLibrary(String libraryName) throws IOException {
		return loadLibrary(libraryName, null);
	}

	/**
	 * Load a native library with version specification
	 */
	public SymbolLookup loadLibrary(String libraryName, String version) throws IOException {
		String key = libraryName + (version != null ? "-" + version : "");

		return loadedLibraries.computeIfAbsent(key, k -> {
			try {
				// Try to load from system first
				Path libraryPath = findSystemLibrary(libraryName, version);
				if (libraryPath != null) {
					return loadNativeLibrary(libraryPath);
				}

				// Try to extract from resources
				libraryPath = extractEmbeddedLibrary(libraryName, version);
				if (libraryPath != null) {
					return loadNativeLibrary(libraryPath);
				}

				// Try to load using system loader as fallback
				try {
					System.loadLibrary(libraryName);
					return SymbolLookup.loaderLookup();
				} catch (UnsatisfiedLinkError e) {
					throw new RuntimeException("Failed to load library: " + libraryName, e);
				}
			} catch (IOException e) {
				throw new RuntimeException("Failed to load library: " + libraryName, e);
			}
		});
	}

	/**
	 * Load native library using JDK 24 FFM API
	 */
	private SymbolLookup loadNativeLibrary(Path libraryPath) {
		try {
			// Use Arena for automatic resource management
			Arena arena = Arena.global();
			return SymbolLookup.libraryLookup(libraryPath, arena);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException("Failed to load library: " + libraryPath, e);
		}
	}

	/**
	 * Find system library
	 */
	private Path findSystemLibrary(String libraryName, String version) {
		List<String> possibleNames = generateLibraryNames(libraryName, version);

		for (Path searchPath : searchPaths) {
			for (String name : possibleNames) {
				Path libPath = searchPath.resolve(name);
				if (Files.exists(libPath)) {
					return libPath;
				}
			}
		}

		return null;
	}

	/**
	 * Generate possible library file names
	 */
	private List<String> generateLibraryNames(String libraryName, String version) {
		List<String> names = new ArrayList<>();
		OSType os = platform.getOS();
		String prefix = os.getLibPrefix();
		String extension = os.getLibExtension();

		// Basic name
		names.add(prefix + libraryName + "." + extension);

		// With version
		if (version != null) {
			switch (os) {
				case LINUX, FREEBSD, OPENBSD -> {
					names.add(prefix + libraryName + ".so." + version);
					names.add(prefix + libraryName + "-" + version + ".so");
				}
				case MACOS -> {
					names.add(prefix + libraryName + "." + version + ".dylib");
					names.add(prefix + libraryName + "-" + version + ".dylib");
				}
				case WINDOWS -> {
					names.add(libraryName + "-" + version + ".dll");
					names.add(libraryName + version + ".dll");
				}
			}
		}

		// Platform-specific variations
		if (os == OSType.WINDOWS) {
			names.add(libraryName + ".dll");
			if (platform.getArch() == CPUArch.X86_64) {
				names.add(libraryName + "64.dll");
				names.add(libraryName + "_x64.dll");
			} else if (platform.getArch() == CPUArch.X86) {
				names.add(libraryName + "32.dll");
				names.add(libraryName + "_x86.dll");
			}
		}

		return names;
	}

	/**
	 * Extract embedded library from JAR resources
	 */
	private Path extractEmbeddedLibrary(String libraryName, String version) throws IOException {
		String resourcePath = buildResourcePath(libraryName, version);

		return extractedLibraries.computeIfAbsent(resourcePath, path -> {
			extractionLock.lock();
			try {
				return extractResource(path, libraryName);
			} catch (IOException e) {
				throw new RuntimeException("Failed to extract library: " + path, e);
			} finally {
				extractionLock.unlock();
			}
		});
	}

	/**
	 * Build resource path for embedded library
	 */
	private String buildResourcePath(String libraryName, String version) {
		StringBuilder path = new StringBuilder(NATIVE_LIB_PREFIX);
		path.append("/").append(platform.canonicalPlatformName());
		path.append("/").append(platform.getOS().getLibPrefix());
		path.append(libraryName);
		if (version != null) {
			path.append("-").append(version);
		}
		path.append(".").append(platform.getOS().getLibExtension());
		return path.toString();
	}

	/**
	 * Extract resource to file system
	 */
	private Path extractResource(String resourcePath, String libraryName) throws IOException {
		InputStream is = getClass().getResourceAsStream(resourcePath);
		if (is == null) {
			return null;
		}

		// Generate unique filename based on content hash
		String hash = calculateHash(is);
		is = getClass().getResourceAsStream(resourcePath); // Re-open stream

		String fileName = platform.getOS().getLibPrefix() + libraryName +
			                  "-" + hash + "." + platform.getOS().getLibExtension();
		Path targetPath = tempDirectory.resolve(fileName);

		// Check if already extracted
		if (Files.exists(targetPath)) {
			return targetPath;
		}

		// Extract with file locking to prevent concurrent extraction
		Path tempFile = Files.createTempFile(tempDirectory, "extract-", ".tmp");
		try (FileOutputStream fos = new FileOutputStream(tempFile.toFile());
		     FileLock lock = fos.getChannel().lock()) {

			is.transferTo(fos);
			Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE);

			// Set executable permissions on Unix-like systems
			if (platform.getOS() != OSType.WINDOWS) {
				Set<PosixFilePermission> perms = new HashSet<>();
				perms.add(PosixFilePermission.OWNER_READ);
				perms.add(PosixFilePermission.OWNER_WRITE);
				perms.add(PosixFilePermission.OWNER_EXECUTE);
				perms.add(PosixFilePermission.GROUP_READ);
				perms.add(PosixFilePermission.GROUP_EXECUTE);
				perms.add(PosixFilePermission.OTHERS_READ);
				perms.add(PosixFilePermission.OTHERS_EXECUTE);
				Files.setPosixFilePermissions(targetPath, perms);
			}

			if (deleteOnExit) {
				targetPath.toFile().deleteOnExit();
			}

			return targetPath;
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	/**
	 * Calculate SHA-256 hash of input stream
	 */
	private String calculateHash(InputStream is) throws IOException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[8192];
			int read;
			while ((read = is.read(buffer)) != -1) {
				md.update(buffer, 0, read);
			}

			byte[] hash = md.digest();
			StringBuilder sb = new StringBuilder();
			for (byte b : hash) {
				sb.append(String.format("%02x", b));
			}
			return sb.substring(0, 16); // Use first 16 chars
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 not available", e);
		}
	}

	/**
	 * Delete directory recursively
	 */
	private void deleteDirectory(Path directory) throws IOException {
		if (Files.exists(directory)) {
			try (Stream<Path> walk = Files.walk(directory)) {
				walk.sorted(Comparator.reverseOrder())
				    .forEach(path -> {
					    try {
						    Files.delete(path);
					    } catch (IOException e) {
						    // Ignore
					    }
				    });
			}
		}
	}

	/**
	 * Add custom search path
	 */
	public void addSearchPath(Path path) {
		if (Files.exists(path) && Files.isDirectory(path)) {
			searchPaths.add(path);
		}
	}

	/**
	 * Get loaded libraries
	 */
	public Set<String> getLoadedLibraries() {
		return new HashSet<>(loadedLibraries.keySet());
	}

	/**
	 * Unload all libraries and cleanup
	 */
	public void cleanup() {
		loadedLibraries.clear();
		extractedLibraries.clear();

		if (deleteOnExit) {
			try {
				deleteDirectory(tempDirectory);
			} catch (IOException e) {
				// Ignore cleanup errors
			}
		}
	}
}