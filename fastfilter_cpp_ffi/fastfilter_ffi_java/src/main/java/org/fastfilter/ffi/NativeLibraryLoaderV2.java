package org.fastfilter.ffi;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Singleton native library loader for JDK 24 FFI
 * Supports embedded resources, version management, and configurable platform-specific loading
 */
public class NativeLibraryLoaderV2 implements NativeLibraryLoaderInterface
{
	private static final Logger LOGGER = Logger.getLogger(NativeLibraryLoaderV2.class.getName());
	private static final String NATIVE_LIB_PREFIX = "/native";

	// Singleton instance
	private static final class Holder {
		private static final NativeLibraryLoaderV2 INSTANCE = new NativeLibraryLoaderV2();
	}

	// Instance fields
	private final Map<String, SymbolLookup> loadedLibraries = new ConcurrentHashMap<>();
	private final Map<String, Path> extractedLibraries = new ConcurrentHashMap<>();
	private final ReentrantLock extractionLock = new ReentrantLock();
	
	// Interface implementation fields
	private volatile boolean defaultLibraryLoaded = false;
	private volatile Throwable loadError = null;

	private final PlatformInfo platform;
	private final NativeLibraryConfig config;
	private final Path tempDirectory;
	private final List<Path> customSearchPaths;

	// Private constructor for singleton
	private NativeLibraryLoaderV2() {
		this.platform = PlatformInfo.getInstance();
		this.config = new NativeLibraryConfig();
		this.customSearchPaths = Collections.synchronizedList(new ArrayList<>());
		this.tempDirectory = createTempDirectory();

		// Register shutdown hook if configured
		if (config.isDeleteOnExit()) {
			Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));
		}

		LOGGER.info("NativeLibraryLoader initialized for platform: " + platform.canonicalPlatformName());
	}

	/**
	 * Get singleton instance
	 */
	public static NativeLibraryLoaderV2 getInstance() {
		return Holder.INSTANCE;
	}

	/**
	 * Create temp directory for extracted libraries
	 */
	private Path createTempDirectory() {
		try {
			Path dir = config.getExtractDirectory();
			Files.createDirectories(dir);
			LOGGER.info("Using temp directory: " + dir);
			return dir;
		} catch (IOException e) {
			throw new RuntimeException("Failed to create temp directory", e);
		}
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
		// Apply library name mapping from configuration
		String mappedName = config.getMappedLibraryName(libraryName);

		String key = mappedName + (version != null ? "-" + version : "");

		if (config.isCachingEnabled()) {
			SymbolLookup cached = loadedLibraries.get(key);
			if (cached != null) {
				LOGGER.fine("Returning cached library: " + key);
				return cached;
			}
		}

		return loadedLibraries.computeIfAbsent(key, k -> {
			try {
				LOGGER.info("Loading library: " + k);

				// Try to load from system first
				Path libraryPath = findSystemLibrary(mappedName, version);
				if (libraryPath != null) {
					LOGGER.info("Found system library: " + libraryPath);
					return loadNativeLibrary(libraryPath);
				}

				// Try to extract from resources
				libraryPath = extractEmbeddedLibrary(mappedName, version);
				if (libraryPath != null) {
					LOGGER.info("Extracted embedded library: " + libraryPath);
					return loadNativeLibrary(libraryPath);
				}

				// Try to load using system loader as fallback
				try {
					System.loadLibrary(mappedName);
					LOGGER.info("Loaded via System.loadLibrary: " + mappedName);
					return SymbolLookup.loaderLookup();
				} catch (UnsatisfiedLinkError e) {
					throw new RuntimeException("Failed to load library: " + mappedName, e);
				}
			} catch (IOException e) {
				throw new RuntimeException("Failed to load library: " + mappedName, e);
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
		List<Path> searchPaths = getAllSearchPaths();

		LOGGER.fine("Searching for library in " + searchPaths.size() + " paths");

		for (Path searchPath : searchPaths) {
			for (String name : possibleNames) {
				Path libPath = searchPath.resolve(name);
				if (Files.exists(libPath)) {
					LOGGER.fine("Found library at: " + libPath);
					return libPath;
				}
			}
		}

		return null;
	}

	/**
	 * Get all search paths including configured and custom paths
	 */
	private List<Path> getAllSearchPaths() {
		List<Path> allPaths = new ArrayList<>();

		// Add configured paths for current platform
		allPaths.addAll(config.getSearchPaths());

		// Add custom paths
		allPaths.addAll(customSearchPaths);

		return allPaths;
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

		if (config.isCachingEnabled()) {
			Path cached = extractedLibraries.get(resourcePath);
			if (cached != null && Files.exists(cached)) {
				LOGGER.fine("Using cached extracted library: " + cached);
				return cached;
			}
		}

		extractionLock.lock();
		try {
			Path extracted = extractResource(resourcePath, libraryName);
			if (extracted != null && config.isCachingEnabled()) {
				extractedLibraries.put(resourcePath, extracted);
			}
			return extracted;
		} finally {
			extractionLock.unlock();
		}
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
			LOGGER.fine("Resource not found: " + resourcePath);
			return null;
		}

		// Verify checksum if enabled
		if (config.isChecksumVerificationEnabled()) {
			try {
				if (!verifyChecksum(resourcePath, is)) {
					throw new IOException("Checksum verification failed for: " + resourcePath);
				}
			} catch (IOException e) {
				LOGGER.severe("Checksum verification error: " + e.getMessage());
				throw e;
			}
			// Re-open stream after verification
			is = getClass().getResourceAsStream(resourcePath);
		}

		String fileName;
		if (config.isChecksumVerificationEnabled()) {
			// Generate unique filename based on content hash
			String hash = calculateHash(is);
			is = getClass().getResourceAsStream(resourcePath); // Re-open stream

			fileName = platform.getOS().getLibPrefix() + libraryName +
				           "-" + hash + "." + platform.getOS().getLibExtension();
		} else {
			fileName = platform.getOS().getLibPrefix() + libraryName +
				           "." + platform.getOS().getLibExtension();
		}

		Path targetPath = tempDirectory.resolve(fileName);

		// Check if already extracted
		if (Files.exists(targetPath)) {
			LOGGER.fine("Library already extracted: " + targetPath);
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

			if (config.isDeleteOnExit()) {
				targetPath.toFile().deleteOnExit();
			}

			LOGGER.info("Extracted library to: " + targetPath);
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
	 * Calculate full SHA-256 hash of input stream
	 */
	private String calculateFullHash(InputStream is) throws IOException {
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
			return sb.toString(); // Return full hash
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 not available", e);
		}
	}
	
	/**
	 * Verify library checksum against expected value from .sha256 file
	 */
	private boolean verifyChecksum(String libraryResourcePath, InputStream libraryStream) throws IOException {
		// Look for corresponding .sha256 file
		String checksumResourcePath = libraryResourcePath + ".sha256";
		InputStream checksumStream = getClass().getResourceAsStream(checksumResourcePath);
		
		if (checksumStream == null) {
			LOGGER.warning("No checksum file found for: " + libraryResourcePath);
			return false;
		}
		
		try {
			// Read expected checksum
			String expectedChecksum = new String(checksumStream.readAllBytes()).trim();
			// Extract just the hash part (checksum files contain "hash filename")
			expectedChecksum = expectedChecksum.split("\\s+")[0];
			
			// Calculate actual checksum
			String actualChecksum = calculateFullHash(libraryStream);
			
			boolean matches = expectedChecksum.equalsIgnoreCase(actualChecksum);
			LOGGER.info("Checksum verification for " + libraryResourcePath + ": " + 
			           (matches ? "PASSED" : "FAILED"));
			
			if (!matches) {
				LOGGER.warning("Expected: " + expectedChecksum);
				LOGGER.warning("Actual: " + actualChecksum);
			}
			
			return matches;
		} finally {
			checksumStream.close();
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
						    LOGGER.warning("Failed to delete: " + path);
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
			customSearchPaths.add(path);
			LOGGER.info("Added search path: " + path);
		}
	}

	/**
	 * Remove custom search path
	 */
	public void removeSearchPath(Path path) {
		customSearchPaths.remove(path);
		LOGGER.info("Removed search path: " + path);
	}

	/**
	 * Get loaded libraries
	 */
	public Set<String> getLoadedLibraries() {
		return new HashSet<>(loadedLibraries.keySet());
	}

	/**
	 * Clear library cache
	 */
	public void clearCache() {
		if (!config.isCachingEnabled()) {
			LOGGER.warning("Caching is disabled in configuration");
			return;
		}

		loadedLibraries.clear();
		extractedLibraries.clear();
		LOGGER.info("Library cache cleared");
	}

	/**
	 * Reload configuration
	 */
	public void reloadConfiguration() {
		// Note: This would require making config non-final
		// or providing a reload mechanism in NativeLibraryConfig
		LOGGER.info("Configuration reload requested");
	}

	/**
	 * Cleanup extracted libraries and caches
	 */
	private void cleanup() {
		LOGGER.info("Cleaning up native library loader");

		loadedLibraries.clear();
		extractedLibraries.clear();

		if (config.isDeleteOnExit()) {
			try {
				deleteDirectory(tempDirectory);
				LOGGER.info("Deleted temp directory: " + tempDirectory);
			} catch (IOException e) {
				LOGGER.warning("Failed to delete temp directory: " + e.getMessage());
			}
		}
	}

	/**
	 * Get configuration
	 */
	public NativeLibraryConfig getConfiguration() {
		return config;
	}

	/**
	 * Get platform info object
	 */
	public PlatformInfo getPlatformInfoObject() {
		return platform;
	}
	
	// Implementation of NativeLibraryLoaderInterface
	
	@Override
	public void loadLibrary() throws UnsatisfiedLinkError {
		if (defaultLibraryLoaded) {
			return;
		}
		
		if (loadError != null) {
			if (loadError instanceof UnsatisfiedLinkError) {
				throw (UnsatisfiedLinkError) loadError;
			} else {
				throw new UnsatisfiedLinkError("Failed to load default library: " + loadError.getMessage());
			}
		}
		
		try {
			loadLibrary("fastfilter_cpp_ffi");
			defaultLibraryLoaded = true;
		} catch (Exception e) {
			loadError = e;
			throw new UnsatisfiedLinkError("Failed to load default library: " + e.getMessage());
		}
	}
	
	@Override
	public boolean isLoaded() {
		return defaultLibraryLoaded;
	}
	
	@Override
	public String getPlatform() {
		return platform.canonicalPlatformName();
	}
	
	@Override
	public String getLibraryFileName() {
		return platform.getOS().getLibPrefix() + "fastfilter_cpp_ffi." + platform.getOS().getLibExtension();
	}
	
	@Override
	public String getPlatformInfo() {
		StringBuilder info = new StringBuilder();
		info.append("Platform: ").append(platform.canonicalPlatformName()).append("\n");
		info.append("OS: ").append(platform.getOS().getName()).append("\n");
		info.append("Arch: ").append(platform.getArch().getName()).append("\n");
		info.append("Library File: ").append(getLibraryFileName()).append("\n");
		info.append("Loaded: ").append(defaultLibraryLoaded).append("\n");
		info.append("Loaded Libraries: ").append(loadedLibraries.keySet()).append("\n");
		if (loadError != null) {
			info.append("Load Error: ").append(loadError.getMessage()).append("\n");
		}
		return info.toString();
	}
	
	@Override
	public Optional<Throwable> getLoadError() {
		return Optional.ofNullable(loadError);
	}
	
	@Override
	public String getLoadStrategy() {
		return "advanced-singleton";
	}
	
	@Override
	public boolean verifyLibraryIntegrity() {
		return config.isChecksumVerificationEnabled();
	}
}
