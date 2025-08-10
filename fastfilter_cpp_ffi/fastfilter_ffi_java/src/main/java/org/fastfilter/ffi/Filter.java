package org.fastfilter.ffi;

/**
 * Interface for probabilistic set membership data structures (approximate membership query filters).
 * 
 * This is a minimal Filter interface for the FFI module to avoid circular dependencies.
 * The main fastfilter module has a more complete Filter interface.
 */
public interface Filter {
    
    /**
     * Check whether an element is in the set.
     * False positives are possible, but false negatives are not.
     * 
     * @param key the element
     * @return true if the element may be in the set, false if it is definitely not in the set
     */
    boolean mayContain(long key);
    
    /**
     * Get the number of bits used by this filter.
     * 
     * @return the number of bits
     */
    long getBitCount();
}