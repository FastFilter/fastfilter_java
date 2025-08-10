package org.fastfilter.ffi;

import java.util.Optional;

/**
 * Adapter to make the static NativeLibraryLoader work with the NativeLibraryLoaderInterface.
 * This provides a bridge between the existing static API and the new interface.
 */
public class NativeLibraryLoaderAdapter implements NativeLibraryLoaderInterface {
    
    private static final NativeLibraryLoaderAdapter INSTANCE = new NativeLibraryLoaderAdapter();
    
    private NativeLibraryLoaderAdapter() {}
    
    public static NativeLibraryLoaderAdapter getInstance() {
        return INSTANCE;
    }
    
    @Override
    public void loadLibrary() throws UnsatisfiedLinkError {
        NativeLibraryLoader.loadLibrary();
    }
    
    @Override
    public boolean isLoaded() {
        return NativeLibraryLoader.isLoaded();
    }
    
    @Override
    public String getPlatform() {
        return NativeLibraryLoader.getPlatform();
    }
    
    @Override
    public String getLibraryFileName() {
        return NativeLibraryLoader.getLibraryFileName();
    }
    
    @Override
    public String getPlatformInfo() {
        return NativeLibraryLoader.getPlatformInfo();
    }
    
    @Override
    public Optional<Throwable> getLoadError() {
        // The static loader doesn't expose the load error, but we can try to infer it
        if (!isLoaded()) {
            return Optional.of(new UnsatisfiedLinkError("Library not loaded"));
        }
        return Optional.empty();
    }
    
    @Override
    public String getLoadStrategy() {
        return "static-embedded";
    }
    
    @Override
    public boolean verifyLibraryIntegrity() {
        // The static loader doesn't support checksum verification
        return true;
    }
}