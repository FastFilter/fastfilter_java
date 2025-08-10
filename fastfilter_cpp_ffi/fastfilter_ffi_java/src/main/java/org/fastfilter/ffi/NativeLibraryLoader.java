package org.fastfilter.ffi;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Platform-specific native library loader for FastFilter C++ integration.
 * 
 * This class automatically detects the current platform and loads the appropriate
 * native library from the classpath or from platform-specific JAR files.
 */
public class NativeLibraryLoader {
    
    private static final String LIBRARY_NAME = "fastfilter_cpp_ffi";
    private static final ReentrantLock LOAD_LOCK = new ReentrantLock();
    private static volatile boolean loaded = false;
    private static volatile Throwable loadError = null;

    // Platform detection constants
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final String OS_ARCH = System.getProperty("os.arch").toLowerCase();
    
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");
    private static final boolean IS_MACOS = OS_NAME.contains("mac") || OS_NAME.contains("darwin");
    private static final boolean IS_LINUX = OS_NAME.contains("linux");
    
    private static final boolean IS_X86_64 = OS_ARCH.contains("x86_64") || OS_ARCH.contains("amd64");
    private static final boolean IS_ARM64 = OS_ARCH.contains("aarch64") || OS_ARCH.contains("arm64");

    /**
     * Load the native library if not already loaded.
     * 
     * @throws UnsatisfiedLinkError if the library cannot be loaded
     */
    public static void loadLibrary() throws UnsatisfiedLinkError {
        if (loaded) {
            return;
        }
        
        if (loadError != null) {
            if (loadError instanceof UnsatisfiedLinkError) {
                throw (UnsatisfiedLinkError) loadError;
            } else {
                throw new UnsatisfiedLinkError("Failed to load native library: " + loadError.getMessage());
            }
        }

        LOAD_LOCK.lock();
        try {
            if (loaded) {
                return;
            }

            if (loadError != null) {
                if (loadError instanceof UnsatisfiedLinkError) {
                    throw (UnsatisfiedLinkError) loadError;
                } else {
                    throw new UnsatisfiedLinkError("Failed to load native library: " + loadError.getMessage());
                }
            }

            try {
                loadNativeLibrary();
                loaded = true;
            } catch (Throwable t) {
                loadError = t;
                if (t instanceof UnsatisfiedLinkError) {
                    throw (UnsatisfiedLinkError) t;
                } else {
                    throw new UnsatisfiedLinkError("Failed to load native library: " + t.getMessage());
                }
            }
        } finally {
            LOAD_LOCK.unlock();
        }
    }

    /**
     * Check if the native library is loaded.
     * 
     * @return true if loaded, false otherwise
     */
    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Get the current platform identifier.
     * 
     * @return platform identifier string
     */
    public static String getPlatform() {
        if (IS_WINDOWS && IS_X86_64) {
            return "windows-x86_64";
        } else if (IS_MACOS && IS_X86_64) {
            return "macos-x86_64";
        } else if (IS_MACOS && IS_ARM64) {
            return "macos-arm64";
        } else if (IS_LINUX && IS_X86_64) {
            return "linux-x86_64";
        } else if (IS_LINUX && IS_ARM64) {
            return "linux-arm64";
        } else {
            return "unknown-" + OS_NAME + "-" + OS_ARCH;
        }
    }

    /**
     * Get the expected library filename for the current platform.
     * 
     * @return library filename
     */
    public static String getLibraryFileName() {
        if (IS_WINDOWS) {
            return LIBRARY_NAME + ".dll";
        } else if (IS_MACOS) {
            return "lib" + LIBRARY_NAME + ".dylib";
        } else {
            return "lib" + LIBRARY_NAME + ".so";
        }
    }

    private static void loadNativeLibrary() throws UnsatisfiedLinkError {
        // Try to load from system library path first
        try {
            System.loadLibrary(LIBRARY_NAME);
            return;
        } catch (UnsatisfiedLinkError e) {
            // Continue with embedded library loading
        }

        // Try to load from explicit system property path
        String libraryPath = System.getProperty("fastfilter.cpp.library.path");
        if (libraryPath != null) {
            try {
                Path libPath = Paths.get(libraryPath, getLibraryFileName());
                if (Files.exists(libPath)) {
                    System.load(libPath.toAbsolutePath().toString());
                    return;
                }
            } catch (UnsatisfiedLinkError e) {
                // Continue with embedded library loading
            }
        }

        // Load embedded library from classpath
        loadEmbeddedLibrary();
    }

    private static void loadEmbeddedLibrary() throws UnsatisfiedLinkError {
        String platform = getPlatform();
        String libraryFileName = getLibraryFileName();
        String resourcePath = "/native/" + platform + "/" + libraryFileName;

        try (InputStream inputStream = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new UnsatisfiedLinkError(
                    "Native library not found for platform: " + platform + 
                    " (expected: " + resourcePath + ")");
            }

            // Create temporary file
            Path tempDir = createTempDirectory();
            Path tempLibraryPath = tempDir.resolve(libraryFileName);
            
            // Copy library to temporary file
            try (FileOutputStream outputStream = new FileOutputStream(tempLibraryPath.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            // Make library executable on Unix systems
            if (!IS_WINDOWS) {
                tempLibraryPath.toFile().setExecutable(true);
                tempLibraryPath.toFile().setReadable(true);
            }

            // Load the library
            System.load(tempLibraryPath.toAbsolutePath().toString());

            // Schedule cleanup (best effort)
            tempLibraryPath.toFile().deleteOnExit();
            tempDir.toFile().deleteOnExit();

        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to extract and load native library: " + e.getMessage());
        }
    }

    private static Path createTempDirectory() throws IOException {
        String prefix = "fastfilter_native_" + getPlatform() + "_";
        return Files.createTempDirectory(prefix);
    }

    /**
     * Get detailed information about the current platform and library loading status.
     * 
     * @return platform information string
     */
    public static String getPlatformInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Platform: ").append(getPlatform()).append("\n");
        info.append("OS Name: ").append(OS_NAME).append("\n");
        info.append("OS Arch: ").append(OS_ARCH).append("\n");
        info.append("Library File: ").append(getLibraryFileName()).append("\n");
        info.append("Loaded: ").append(loaded).append("\n");
        if (loadError != null) {
            info.append("Load Error: ").append(loadError.getMessage()).append("\n");
        }
        return info.toString();
    }

    // Prevent instantiation
    private NativeLibraryLoader() {}
}