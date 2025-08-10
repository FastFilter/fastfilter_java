package org.fastfilter.ffi;

import java.util.Optional;

/**
 * Common interface for native library loading implementations.
 * This interface provides a consistent API for loading platform-specific native libraries
 * with support for multiple loading strategies and verification.
 */
public interface NativeLibraryLoaderInterface {
    
    /**
     * Load the native library if not already loaded.
     * 
     * @throws UnsatisfiedLinkError if the library cannot be loaded
     */
    void loadLibrary() throws UnsatisfiedLinkError;
    
    /**
     * Check if the native library is loaded.
     * 
     * @return true if loaded, false otherwise
     */
    boolean isLoaded();
    
    /**
     * Get the current platform identifier.
     * 
     * @return platform identifier string (e.g., "linux-x86_64", "macos-arm64")
     */
    String getPlatform();
    
    /**
     * Get the expected library filename for the current platform.
     * 
     * @return library filename (e.g., "libfastfilter_cpp_ffi.so", "fastfilter_cpp_ffi.dll")
     */
    String getLibraryFileName();
    
    /**
     * Get detailed information about the current platform and library loading status.
     * 
     * @return platform and loading information
     */
    String getPlatformInfo();
    
    /**
     * Get the load error if library loading failed.
     * 
     * @return Optional containing the load error, or empty if no error occurred
     */
    Optional<Throwable> getLoadError();
    
    /**
     * Attempt to unload the library if supported by the platform.
     * Note: This operation is not guaranteed to succeed on all platforms.
     * 
     * @return true if unload was attempted, false if not supported
     */
    default boolean unloadLibrary() {
        return false; // Default implementation - most platforms don't support unloading
    }
    
    /**
     * Get the library load strategy being used.
     * 
     * @return description of the load strategy (e.g., "embedded", "system", "explicit-path")
     */
    default String getLoadStrategy() {
        return "default";
    }
    
    /**
     * Verify the integrity of the loaded library (if supported).
     * 
     * @return true if verification passed, false if verification failed or is not supported
     */
    default boolean verifyLibraryIntegrity() {
        return true; // Default implementation - assume verification passes
    }
}