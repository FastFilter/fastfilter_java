package org.fastfilter;

/**
 * An approximate membership filter.
 */
public interface Filter {

    /**
     * Whether the set may contain the key.
     *
     * @param key the key
     * @return true if the set might contain the key, and false if not
     */
    boolean mayContain(long key);

    /**
     * Get the number of bits in thhe filter.
     *
     * @return the number of bits
     */
    long getBitCount();

    /**
     * Get the number of set bits. This should be 0 for an empty filter.
     *
     */
    default long cardinality() {
        return -1;
    }


}
