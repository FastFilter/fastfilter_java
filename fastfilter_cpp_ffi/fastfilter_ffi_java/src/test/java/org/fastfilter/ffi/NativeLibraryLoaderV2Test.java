package org.fastfilter.ffi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeLibraryLoaderV2Test
{

    @Test
    void testGetPlatform() {
        String platform = NativeLibraryLoader.getPlatform();
        assertNotNull(platform);
        assertFalse(platform.isEmpty());
        assertTrue(platform.matches("^(linux|macos|windows)-(x86_64|arm64)$|^unknown-.*"));
    }

    @Test
    void testGetLibraryFileName() {
        String filename = NativeLibraryLoader.getLibraryFileName();
        assertNotNull(filename);
        assertFalse(filename.isEmpty());
        assertTrue(filename.contains("fastfilter_cpp_ffi"));
        assertTrue(filename.endsWith(".so") || filename.endsWith(".dylib") || filename.endsWith(".dll"));
    }

    @Test
    void testGetPlatformInfo() {
        String info = NativeLibraryLoader.getPlatformInfo();
        assertNotNull(info);
        assertFalse(info.isEmpty());
        assertTrue(info.contains("Platform:"));
        assertTrue(info.contains("OS Name:"));
        assertTrue(info.contains("OS Arch:"));
        assertTrue(info.contains("Library File:"));
        assertTrue(info.contains("Loaded:"));
    }

    @Test
    void testLoadLibraryDoesNotThrow() {
        // This test might fail if native library is not available
        // but should not throw unexpected exceptions
        assertDoesNotThrow(() -> {
            try {
                NativeLibraryLoader.loadLibrary();
            } catch (UnsatisfiedLinkError e) {
                // Expected if native library is not available
                assertTrue(e.getMessage().contains("Failed to load native library") ||
                          e.getMessage().contains("Native library not found"));
            }
        });
    }
}