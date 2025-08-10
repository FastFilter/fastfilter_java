package org.fastfilter.cpp;

import org.fastfilter.Filter;

/**
 * Enumeration of C++ filter types available through FFI integration.
 * These provide access to the high-performance C++ implementations
 * of FastFilter algorithms for performance comparison.
 */
public enum CppFilterType {
    /**
     * C++ XOR8 filter implementation from fastfilter_cpp library.
     * Provides single-header optimized XOR filter with ~0.39% false positive rate.
     */
    XOR8_CPP {
        @Override
        public Filter construct(long[] keys, int bitsPerKey) {
            return new Xor8Filter(keys);
        }
        
        @Override
        public String toString() {
            return "Xor8 (C++)";
        }
    },
    
    /**
     * C++ Binary Fuse8 filter implementation from fastfilter_cpp library.
     * More space-efficient than XOR filters, typically ~9.1 bits per key.
     */
    BINARY_FUSE8_CPP {
        @Override
        public Filter construct(long[] keys, int bitsPerKey) {
            return new BinaryFuse8Filter(keys);
        }
        
        @Override
        public String toString() {
            return "BinaryFuse8 (C++)";
        }
    };
    
    /**
     * Construct a C++ filter instance with the given keys.
     * 
     * @param keys the keys to add to the filter
     * @param bitsPerKey bits per key (may be ignored by some C++ implementations)
     * @return a Filter instance wrapping the C++ implementation
     */
    public abstract Filter construct(long[] keys, int bitsPerKey);
}