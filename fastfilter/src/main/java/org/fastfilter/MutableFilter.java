package org.fastfilter;

public interface MutableFilter extends Filter {
    /**
     * Add a key.
     *
     * @param key the key
     */
    void add(long key);
}
