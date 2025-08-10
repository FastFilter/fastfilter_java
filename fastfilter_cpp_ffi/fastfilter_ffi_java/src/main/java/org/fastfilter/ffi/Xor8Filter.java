package org.fastfilter.ffi;

// Use local Filter interface to avoid circular dependencies

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Java wrapper for the C++ XOR8 filter implementation using JDK 24 Foreign Function Interface.
 * This provides access to the highly optimized C++ XOR8 filter from the fastfilter_cpp library.
 * 
 * The XOR8 filter has approximately 0.39% false positive probability and uses about 9.84 bits per key.
 * This C++ implementation is typically faster than the Java version for large datasets.
 */
public class Xor8Filter implements Filter {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LIBRARY;
    
    // Function handles for C++ library functions
    private static final MethodHandle XOR8_ALLOCATE;
    private static final MethodHandle XOR8_POPULATE;
    private static final MethodHandle XOR8_CONTAIN;
    private static final MethodHandle XOR8_FREE;
    private static final MethodHandle XOR8_SIZE_IN_BYTES;
    
    private final MemorySegment filterHandle;
    private final long size;
    private final Arena arena;
    private boolean isFreed = false;
    
    static {
        try {
            // Load the native library using the new interface
            NativeLibraryLoaderInterface loader = NativeLibraryLoaderAdapter.getInstance();
            loader.loadLibrary();
            LIBRARY = SymbolLookup.loaderLookup();
            
            // Define C function signatures
            FunctionDescriptor allocateDesc = FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
            FunctionDescriptor populateDesc = FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, 
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
            FunctionDescriptor containDesc = FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, 
                ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
            FunctionDescriptor freeDesc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);
            FunctionDescriptor sizeDesc = FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
            
            // Link C functions
            XOR8_ALLOCATE = LINKER.downcallHandle(
                LIBRARY.find("xor8_allocate_wrapper").orElseThrow(), allocateDesc);
            XOR8_POPULATE = LINKER.downcallHandle(
                LIBRARY.find("xor8_populate_wrapper").orElseThrow(), populateDesc);
            XOR8_CONTAIN = LINKER.downcallHandle(
                LIBRARY.find("xor8_contain_wrapper").orElseThrow(), containDesc);
            XOR8_FREE = LINKER.downcallHandle(
                LIBRARY.find("xor8_free_wrapper").orElseThrow(), freeDesc);
            XOR8_SIZE_IN_BYTES = LINKER.downcallHandle(
                LIBRARY.find("xor8_size_in_bytes_wrapper").orElseThrow(), sizeDesc);
                
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize C++ XOR8 filter library: " + e.getMessage(), e);
        }
    }
    
    /**
     * Construct a new XOR8 filter with the given keys.
     * 
     * @param keys the keys to add to the filter
     */
    public Xor8Filter(long[] keys) {
        if (keys == null || keys.length == 0) {
            throw new IllegalArgumentException("Keys array cannot be null or empty");
        }
        
        this.size = keys.length;
        this.arena = Arena.ofConfined();
        
        try {
            // Allocate C++ filter
            this.filterHandle = (MemorySegment) XOR8_ALLOCATE.invokeExact((long) size);
            if (filterHandle.address() == 0) {
                throw new RuntimeException("Failed to allocate C++ XOR8 filter");
            }
            
            // Copy keys to native memory
            MemorySegment keysSegment = arena.allocateFrom(ValueLayout.JAVA_LONG, keys);
            
            // Populate the filter
            boolean success = (boolean) XOR8_POPULATE.invokeExact(filterHandle, keysSegment, (long) keys.length);
            if (!success) {
                freeFilter();
                throw new RuntimeException("Failed to populate C++ XOR8 filter - construction failed");
            }
            
        } catch (Throwable e) {
            arena.close();
            throw new RuntimeException("Failed to construct C++ XOR8 filter: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean mayContain(long key) {
        checkNotFreed();
        try {
            return (boolean) XOR8_CONTAIN.invokeExact(filterHandle, key);
        } catch (Throwable e) {
            throw new RuntimeException("Error checking key in C++ XOR8 filter: " + e.getMessage(), e);
        }
    }
    
    @Override
    public long getBitCount() {
        checkNotFreed();
        try {
            return (long) XOR8_SIZE_IN_BYTES.invokeExact(filterHandle) * 8L;
        } catch (Throwable e) {
            throw new RuntimeException("Error getting C++ XOR8 filter size: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get the size in bytes of this filter.
     * 
     * @return size in bytes
     */
    public long getSizeInBytes() {
        return getBitCount() / 8L;
    }
    
    /**
     * Get the number of keys in this filter.
     * 
     * @return number of keys
     */
    public long getSize() {
        return size;
    }
    
    private void checkNotFreed() {
        if (isFreed) {
            throw new IllegalStateException("Filter has been freed and is no longer usable");
        }
    }
    
    private void freeFilter() {
        if (!isFreed && filterHandle != null && filterHandle.address() != 0) {
            try {
                XOR8_FREE.invokeExact(filterHandle);
            } catch (Throwable e) {
                // Log error but don't throw - this is cleanup code
                System.err.println("Warning: Failed to free C++ XOR8 filter: " + e.getMessage());
            }
            isFreed = true;
        }
    }
    
    /**
     * Explicitly free the native C++ filter resources.
     * After calling this method, the filter cannot be used anymore.
     */
    public void free() {
        freeFilter();
        if (arena != null) {
            arena.close();
        }
    }
    
    @Override
    @SuppressWarnings("removal")
    @Deprecated(since="18", forRemoval=true)
    protected void finalize() throws Throwable {
        try {
            free();
        } finally {
            super.finalize();
        }
    }
    
    @Override
    public String toString() {
        return "Xor8Filter(C++)[size=" + size + ", bits=" + getBitCount() + "]";
    }
}