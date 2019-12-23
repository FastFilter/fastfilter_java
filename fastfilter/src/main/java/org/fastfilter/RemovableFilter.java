package org.fastfilter;

public interface RemovableFilter extends MutableFilter {

    /**
     * Remove a key.
     *
     * @param key the key
     */
    void remove(long key);
}
