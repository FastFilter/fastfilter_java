package org.fastfilter.ffi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Configuration manager for native library paths
 * Loads configuration from:
 * 1. System environment variables
 * 2. System properties
 * 3. Property files in resources
 * 4. Default platform-specific paths
 */
public class NativeLibraryConfig {
	private static final Logger LOGGER = Logger.getLogger(NativeLibraryConfig.class.getName());

	// Configuration file names
	private static final String GLOBAL_CONFIG = "/native-library-config.properties";
	private static final String PLATFORM_CONFIG_TEMPLATE = "/native-library-config-%s.properties";
	private static final String ARCH_CONFIG_TEMPLATE = "/native-library-config-%s-%s.properties";

	// Environment variable and property prefixes
	private static final String ENV_PREFIX = "NATIVE_LIB_PATH";
	private static final String PROP_PREFIX = "native.library.path";

	// Configuration keys
	private static final String KEY_SEARCH_PATHS = "search.paths";
	private static final String KEY_LIBRARY_MAPPINGS = "library.mappings";
	private static final String KEY_EXTRACT_DIR = "extract.dir";
	private static final String KEY_DELETE_ON_EXIT = "delete.on.exit";
	private static final String KEY_ENABLE_CACHING = "enable.caching";
	private static final String KEY_VERIFY_CHECKSUMS = "verify.checksums";
	private static final String KEY_LOG_LEVEL = "log.level";

	private final PlatformInfo platform;
	private final Properties globalConfig;
	private final Properties platformConfig;
	private final Properties archConfig;
	private final Map<String, List<Path>> searchPathsCache;
	private final Map<String, String> libraryMappings;

	public NativeLibraryConfig() {
		this.platform = PlatformInfo.getInstance();
		this.searchPathsCache = new ConcurrentHashMap<>();
		this.libraryMappings = new ConcurrentHashMap<>();

		// Load configurations in order of precedence
		this.globalConfig = loadGlobalConfig();
		this.platformConfig = loadPlatformConfig();
		this.archConfig = loadArchitectureConfig();

		// Initialize library mappings
		loadLibraryMappings();
	}

	/**
	 * Get search paths for current architecture
	 */
	public List<Path> getSearchPaths() {
		return getSearchPaths(platform.canonicalPlatformName());
	}

	/**
	 * Get search paths for specific platform/architecture
	 */
	public List<Path> getSearchPaths(String platformArch) {
		return searchPathsCache.computeIfAbsent(platformArch, this::buildSearchPaths);
	}

	private List<Path> buildSearchPaths(String platformArch) {
		List<Path> paths = new ArrayList<>();

		// 1. Environment variable paths (highest priority)
		paths.addAll(getEnvironmentPaths(platformArch));

		// 2. System property paths
		paths.addAll(getSystemPropertyPaths(platformArch));

		// 3. Configuration file paths
		paths.addAll(getConfigFilePaths(platformArch));

		// 4. Default platform-specific paths
		paths.addAll(getDefaultPaths(platformArch));

		// Remove duplicates and non-existent paths
		return paths.stream()
		            .distinct()
		            .filter(Files::exists)
		            .filter(Files::isDirectory)
		            .collect(Collectors.toList());
	}

	/**
	 * Get paths from environment variables
	 */
	private List<Path> getEnvironmentPaths(String platformArch) {
		List<Path> paths = new ArrayList<>();

		// Check platform-specific environment variable
		String archEnvVar = String.format("%s_%s", ENV_PREFIX,
		                                  platformArch.toUpperCase().replace("-", "_"));
		String archPaths = System.getenv(archEnvVar);
		if (archPaths != null) {
			paths.addAll(parsePaths(archPaths));
		}

		// Check OS-specific environment variable
		String osEnvVar = String.format("%s_%s", ENV_PREFIX,
		                                platform.getOS().name());
		String osPaths = System.getenv(osEnvVar);
		if (osPaths != null) {
			paths.addAll(parsePaths(osPaths));
		}

		// Check generic environment variable
		String genericPaths = System.getenv(ENV_PREFIX);
		if (genericPaths != null) {
			paths.addAll(parsePaths(genericPaths));
		}

		return paths;
	}

	/**
	 * Get paths from system properties
	 */
	private List<Path> getSystemPropertyPaths(String platformArch) {
		List<Path> paths = new ArrayList<>();

		// Check platform-specific property
		String archProp = String.format("%s.%s", PROP_PREFIX,
		                                platformArch.toLowerCase().replace("-", "."));
		String archPaths = System.getProperty(archProp);
		if (archPaths != null) {
			paths.addAll(parsePaths(archPaths));
		}

		// Check OS-specific property
		String osProp = String.format("%s.%s", PROP_PREFIX,
		                              platform.getOS().getName());
		String osPaths = System.getProperty(osProp);
		if (osPaths != null) {
			paths.addAll(parsePaths(osPaths));
		}

		// Check generic property
		String genericPaths = System.getProperty(PROP_PREFIX);
		if (genericPaths != null) {
			paths.addAll(parsePaths(genericPaths));
		}

		// Also check java.library.path
		String javaLibPath = System.getProperty("java.library.path");
		if (javaLibPath != null) {
			paths.addAll(parsePaths(javaLibPath));
		}

		return paths;
	}

	/**
	 * Get paths from configuration files
	 */
	private List<Path> getConfigFilePaths(String platformArch) {
		List<Path> paths = new ArrayList<>();

		// Get from architecture-specific config
		String archPaths = archConfig.getProperty(KEY_SEARCH_PATHS);
		if (archPaths != null) {
			paths.addAll(parsePaths(archPaths));
		}

		// Get from platform-specific config
		String platformPaths = platformConfig.getProperty(KEY_SEARCH_PATHS);
		if (platformPaths != null) {
			paths.addAll(parsePaths(platformPaths));
		}

		// Get from global config
		String globalPaths = globalConfig.getProperty(KEY_SEARCH_PATHS);
		if (globalPaths != null) {
			paths.addAll(parsePaths(globalPaths));
		}

		return paths;
	}

	/**
	 * Get default platform-specific paths
	 */
	private List<Path> getDefaultPaths(String platformArch) {
		List<Path> paths = new ArrayList<>();
		String[] parts = platformArch.split("-");
		String os = parts[0];
		String arch = parts.length > 1 ? parts[1] : "";

		switch (os) {
			case "linux" -> {
				paths.add(Paths.get("/usr/lib"));
				paths.add(Paths.get("/usr/local/lib"));
				paths.add(Paths.get("/lib"));

				if ("x86_64".equals(arch) || "amd64".equals(arch)) {
					paths.add(Paths.get("/usr/lib64"));
					paths.add(Paths.get("/lib64"));
					paths.add(Paths.get("/usr/lib/x86_64-linux-gnu"));
				} else if ("aarch64".equals(arch) || "arm64".equals(arch)) {
					paths.add(Paths.get("/usr/lib/aarch64-linux-gnu"));
				} else if ("arm".equals(arch)) {
					paths.add(Paths.get("/usr/lib/arm-linux-gnueabihf"));
				}

				// Snap and Flatpak paths
				paths.add(Paths.get("/snap/usr/lib"));
				paths.add(Paths.get("/app/lib"));
			}
			case "darwin", "macos" -> {
				paths.add(Paths.get("/usr/local/lib"));
				paths.add(Paths.get("/usr/lib"));
				paths.add(Paths.get("/System/Library/Frameworks"));

				if ("aarch64".equals(arch) || "arm64".equals(arch)) {
					paths.add(Paths.get("/opt/homebrew/lib"));
					paths.add(Paths.get("/opt/homebrew/opt"));
				} else {
					paths.add(Paths.get("/usr/local/opt"));
				}

				// MacPorts paths
				paths.add(Paths.get("/opt/local/lib"));
			}
			case "windows" -> {
				paths.add(Paths.get("C:\\Windows\\System32"));
				paths.add(Paths.get("C:\\Windows\\SysWOW64"));

				String programFiles = System.getenv("ProgramFiles");
				if (programFiles != null) {
					paths.add(Paths.get(programFiles));
				}

				String programFilesX86 = System.getenv("ProgramFiles(x86)");
				if (programFilesX86 != null && "x86".equals(arch)) {
					paths.add(Paths.get(programFilesX86));
				}

				// MSYS2/MinGW paths
				paths.add(Paths.get("C:\\msys64\\mingw64\\bin"));
				paths.add(Paths.get("C:\\msys64\\mingw32\\bin"));
			}
			case "freebsd" -> {
				paths.add(Paths.get("/usr/local/lib"));
				paths.add(Paths.get("/usr/lib"));
				paths.add(Paths.get("/lib"));
			}
			case "openbsd" -> {
				paths.add(Paths.get("/usr/local/lib"));
				paths.add(Paths.get("/usr/lib"));
			}
			case "solaris" -> {
				paths.add(Paths.get("/usr/lib"));
				paths.add(Paths.get("/usr/local/lib"));
				paths.add(Paths.get("/opt/csw/lib"));
				if ("x86_64".equals(arch)) {
					paths.add(Paths.get("/usr/lib/64"));
				}
			}
		}

		// Add current directory and common paths
		paths.add(Paths.get("."));
		paths.add(Paths.get("lib"));
		paths.add(Paths.get("libs"));
		paths.add(Paths.get("native"));

		return paths;
	}

	/**
	 * Parse path string into list of Paths
	 */
	private List<Path> parsePaths(String pathString) {
		if (pathString == null || pathString.isEmpty()) {
			return Collections.emptyList();
		}

		String separator = System.getProperty("path.separator", ":");
		return Arrays.stream(pathString.split(separator))
		             .map(String::trim)
		             .filter(s -> !s.isEmpty())
		             .map(this::expandPath)
		             .filter(Objects::nonNull)
		             .collect(Collectors.toList());
	}

	/**
	 * Expand path with environment variables and system properties
	 */
	private Path expandPath(String path) {
		if (path == null) return null;

		// Expand environment variables (${VAR} or $VAR format)
		String expanded = path;
		for (Map.Entry<String, String> env : System.getenv().entrySet()) {
			expanded = expanded.replace("${" + env.getKey() + "}", env.getValue());
			expanded = expanded.replace("$" + env.getKey(), env.getValue());
		}
		
		// Expand Java system properties (${java.property} format)
		for (Map.Entry<Object, Object> prop : System.getProperties().entrySet()) {
			String key = prop.getKey().toString();
			String value = prop.getValue().toString();
			expanded = expanded.replace("${" + key + "}", value);
		}

		// Expand ~ to user home
		if (expanded.startsWith("~")) {
			expanded = System.getProperty("user.home") + expanded.substring(1);
		}

		try {
			return Paths.get(expanded);
		} catch (Exception e) {
			LOGGER.warning("Invalid path: " + expanded);
			return null;
		}
	}

	/**
	 * Load global configuration
	 */
	private Properties loadGlobalConfig() {
		return loadConfig(GLOBAL_CONFIG);
	}

	/**
	 * Load platform-specific configuration
	 */
	private Properties loadPlatformConfig() {
		String configFile = String.format(PLATFORM_CONFIG_TEMPLATE,
		                                  platform.getOS().getName().toLowerCase());
		return loadConfig(configFile);
	}

	/**
	 * Load architecture-specific configuration
	 */
	private Properties loadArchitectureConfig() {
		String configFile = String.format(ARCH_CONFIG_TEMPLATE,
		                                  platform.getOS().getName().toLowerCase(),
		                                  platform.getArch().getName().toLowerCase());
		return loadConfig(configFile);
	}

	/**
	 * Load configuration from resource file
	 */
	private Properties loadConfig(String resourcePath) {
		Properties props = new Properties();

		try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
			if (is != null) {
				props.load(is);
				LOGGER.info("Loaded configuration from: " + resourcePath);
			}
		} catch (IOException e) {
			LOGGER.fine("Could not load configuration: " + resourcePath);
		}

		return props;
	}

	/**
	 * Load library name mappings
	 */
	private void loadLibraryMappings() {
		// Load from all config sources
		Stream.of(archConfig, platformConfig, globalConfig)
		      .forEach(props -> {
			      props.stringPropertyNames().stream()
			           .filter(key -> key.startsWith("library.mapping."))
			           .forEach(key -> {
				           String libName = key.substring("library.mapping.".length());
				           String mapping = props.getProperty(key);
				           libraryMappings.put(libName, mapping);
			           });
		      });
	}

	/**
	 * Get mapped library name
	 */
	public String getMappedLibraryName(String libraryName) {
		return libraryMappings.getOrDefault(libraryName, libraryName);
	}

	/**
	 * Get configuration value
	 */
	public String getConfig(String key, String defaultValue) {
		// Check in order of precedence
		String value = archConfig.getProperty(key);
		if (value == null) value = platformConfig.getProperty(key);
		if (value == null) value = globalConfig.getProperty(key);
		return value != null ? value : defaultValue;
	}

	/**
	 * Get boolean configuration value
	 */
	public boolean getBooleanConfig(String key, boolean defaultValue) {
		String value = getConfig(key, String.valueOf(defaultValue));
		return Boolean.parseBoolean(value);
	}


	/**
	 * Get extract directory
	 */
	public Path getExtractDirectory() {
		String dir = getConfig(KEY_EXTRACT_DIR, null);
		if (dir != null) {
			Path expandedPath = expandPath(dir);
			return expandedPath != null ? expandedPath.toAbsolutePath() : getDefaultExtractDirectory();
		}

		return getDefaultExtractDirectory();
	}
	
	private Path getDefaultExtractDirectory() {
		// Default to temp directory
		String tmpDir = System.getProperty("java.io.tmpdir");
		String appName = System.getProperty("app.name", "jffi");
		String userName = System.getProperty("user.name", "user");

		return Paths.get(tmpDir, appName + "-" + userName, "native-libs").toAbsolutePath();
	}

	public boolean isDeleteOnExit() {
		return getBooleanConfig(KEY_DELETE_ON_EXIT, true);
	}

	public boolean isCachingEnabled() {
		return getBooleanConfig(KEY_ENABLE_CACHING, true);
	}

	public boolean isChecksumVerificationEnabled() {
		return getBooleanConfig(KEY_VERIFY_CHECKSUMS, true);
	}
}