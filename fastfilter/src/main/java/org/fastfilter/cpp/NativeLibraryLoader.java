package org.fastfilter.cpp;

/**
 * Platform-specific native library loader for FastFilter C++ integration.
 * 
 * @deprecated Use {@link org.fastfilter.ffi.NativeLibraryLoader} instead.
 *             This class is kept for backward compatibility and delegates to the new FFI implementation.
 */
@Deprecated(since = "1.0.3", forRemoval = true)
public class NativeLibraryLoader {

    /**
     * Load the native library if not already loaded.
     * 
     * @throws UnsatisfiedLinkError if the library cannot be loaded
     * @deprecated Use {@link org.fastfilter.ffi.NativeLibraryLoader#loadLibrary()} instead
     */
    @Deprecated(since = "1.0.3", forRemoval = true)
    public static void loadLibrary() throws UnsatisfiedLinkError {
        org.fastfilter.ffi.NativeLibraryLoader.loadLibrary();
    }

    /**
     * Check if the native library is loaded.
     * 
     * @return true if loaded, false otherwise
     * @deprecated Use {@link org.fastfilter.ffi.NativeLibraryLoader#isLoaded()} instead
     */
    @Deprecated(since = "1.0.3", forRemoval = true)
    public static boolean isLoaded() {
        return org.fastfilter.ffi.NativeLibraryLoader.isLoaded();
    }

    /**
     * Get the current platform identifier.
     * 
     * @return platform identifier string
     * @deprecated Use {@link org.fastfilter.ffi.NativeLibraryLoader#getPlatform()} instead
     */
    @Deprecated(since = "1.0.3", forRemoval = true)
    public static String getPlatform() {
        return org.fastfilter.ffi.NativeLibraryLoader.getPlatform();
    }

    /**
     * Get the expected library filename for the current platform.
     * 
     * @return library filename
     * @deprecated Use {@link org.fastfilter.ffi.NativeLibraryLoader#getLibraryFileName()} instead
     */
    @Deprecated(since = "1.0.3", forRemoval = true)
    public static String getLibraryFileName() {
        return org.fastfilter.ffi.NativeLibraryLoader.getLibraryFileName();
    }

    /**
     * Get detailed information about the current platform and library loading status.
     * 
     * @return platform information string
     * @deprecated Use {@link org.fastfilter.ffi.NativeLibraryLoader#getPlatformInfo()} instead
     */
    @Deprecated(since = "1.0.3", forRemoval = true)
    public static String getPlatformInfo() {
        return org.fastfilter.ffi.NativeLibraryLoader.getPlatformInfo();
    }

    // Prevent instantiation
    private NativeLibraryLoader() {}
}