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
 * Java wrapper for the C++ Binary Fuse8 filter implementation using JDK 24 Foreign Function Interface.
 * This provides access to the highly optimized C++ Binary Fuse8 filter from the fastfilter_cpp library.
 * 
 * Binary Fuse filters are more space-efficient than XOR filters, typically using around 9.1 bits per key
 * with similar false positive rates. The C++ implementation provides better performance for large datasets.
 */
public class BinaryFuse8Filter implements Filter, AutoCloseable
{
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LIBRARY;
    
    // Function handles for C++ library functions
    private static final MethodHandle BINARY_FUSE8_ALLOCATE;
    private static final MethodHandle BINARY_FUSE8_POPULATE;
    private static final MethodHandle BINARY_FUSE8_CONTAIN;
    private static final MethodHandle BINARY_FUSE8_FREE;
    private static final MethodHandle BINARY_FUSE8_SIZE_IN_BYTES;
    
    private final MemorySegment filterHandle;
    private final long size;
    private final Arena arena;
    private boolean isFreed = false;

	enum BinaryFuse8Functions
	{
		BINARY_FUSE8_ALLOCATE("binary_fuse8_allocate_wrapper"),
		BINARY_FUSE8_POPULATE("binary_fuse8_populate_wrapper"),
		BINARY_FUSE8_CONTAIN("binary_fuse8_contain_wrapper"),
		BINARY_FUSE8_FREE("binary_fuse8_free_wrapper"),
		BINARY_FUSE8_SIZE_IN_BYTES("binary_fuse8_size_in_bytes_wrapper");

		private final String name;

		BinaryFuse8Functions(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}
    
    static {
        try {
            // Load the native library using the new interface
            NativeLibraryLoaderInterface loader = NativeLibraryLoaderAdapter.getInstance();
            loader.loadLibrary();
            LIBRARY = SymbolLookup.loaderLookup();
            
            // Define C function signatures
            FunctionDescriptor allocateDesc =
	            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	        BINARY_FUSE8_ALLOCATE = LINKER.downcallHandle(
				LIBRARY.find("binary_fuse8_allocate_wrapper")
				       .orElseThrow(), allocateDesc);

            FunctionDescriptor populateDesc = FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, 
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
            FunctionDescriptor containDesc = FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, 
                ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
            FunctionDescriptor freeDesc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);
            FunctionDescriptor sizeDesc = FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
            
            // Link C functions

            BINARY_FUSE8_POPULATE = LINKER.downcallHandle(
                LIBRARY.find("binary_fuse8_populate_wrapper").orElseThrow(), populateDesc);
            BINARY_FUSE8_CONTAIN = LINKER.downcallHandle(
                LIBRARY.find("binary_fuse8_contain_wrapper").orElseThrow(), containDesc);
            BINARY_FUSE8_FREE = LINKER.downcallHandle(
                LIBRARY.find("binary_fuse8_free_wrapper").orElseThrow(), freeDesc);
            BINARY_FUSE8_SIZE_IN_BYTES = LINKER.downcallHandle(
                LIBRARY.find("binary_fuse8_size_in_bytes_wrapper").orElseThrow(), sizeDesc);
                
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize C++ Binary Fuse8 filter library: " + e.getMessage(), e);
        }
    }
    
    /**
     * Construct a new Binary Fuse8 filter with the given keys.
     * 
     * @param keys the keys to add to the filter
     */
    public BinaryFuse8Filter(long[] keys) {
        if (keys == null || keys.length == 0) {
            throw new IllegalArgumentException("Keys array cannot be null or empty");
        }
        
        this.size = keys.length;
        this.arena = Arena.ofConfined();
        
        try {
            // Allocate C++ filter
            this.filterHandle = (MemorySegment) BINARY_FUSE8_ALLOCATE.invokeExact((long) size);
            if (filterHandle.address() == 0) {
                throw new RuntimeException("Failed to allocate C++ Binary Fuse8 filter");
            }
            
            // Copy keys to native memory
            MemorySegment keysSegment = arena.allocateFrom(ValueLayout.JAVA_LONG, keys);
            
            // Populate the filter
            boolean success = (boolean) BINARY_FUSE8_POPULATE.invokeExact(filterHandle, keysSegment, (long) keys.length);
            if (!success) {
                freeFilter();
                throw new RuntimeException("Failed to populate C++ Binary Fuse8 filter - construction failed");
            }
            
        } catch (Throwable e) {
            arena.close();
            throw new RuntimeException("Failed to construct C++ Binary Fuse8 filter: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean mayContain(long key) {
        checkNotFreed();
        try {
            return (boolean) BINARY_FUSE8_CONTAIN.invokeExact(filterHandle, key);
        } catch (Throwable e) {
            throw new RuntimeException("Error checking key in C++ Binary Fuse8 filter: " + e.getMessage(), e);
        }
    }
    
    @Override
    public long getBitCount() {
        checkNotFreed();
        try {
            return (long) BINARY_FUSE8_SIZE_IN_BYTES.invokeExact(filterHandle) * 8L;
        } catch (Throwable e) {
            throw new RuntimeException("Error getting C++ Binary Fuse8 filter size: " + e.getMessage(), e);
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
                BINARY_FUSE8_FREE.invokeExact(filterHandle);
            } catch (Throwable e) {
                // Log error but don't throw - this is cleanup code
                System.err.println("Warning: Failed to free C++ Binary Fuse8 filter: " + e.getMessage());
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
	public void close() throws Exception {
		free();
	}

    
    @Override
    public String toString() {
        return "BinaryFuse8Filter(C++)[size=" + size + ", bits=" + getBitCount() + "]";
    }
}