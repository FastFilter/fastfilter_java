package org.fastfilter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

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
     * Get the number of bits in the filter.
     *
     * @return the number of bits
     */
    long getBitCount();

    /**
     * Whether the add operation (after construction) is supported.
     *
     * @return true if yes
     */
    default boolean supportsAdd() {
        return false;
    }

    /**
     * Add a key.
     *
     * @param key the key
     */
    default void add(long key) {
        throw new UnsupportedOperationException();
    }

    /**
     * Whether the remove operation is supported.
     *
     * @return true if yes
     */
    default boolean supportsRemove() {
        return false;
    }

    /**
     * Remove a key.
     *
     * @param key the key
     */
    default void remove(long key) {
        throw new UnsupportedOperationException();
    }

    /**
     * Get the number of set bits. This should be 0 for an empty filter.
     *
     * @return the number of bits
     */
    default long cardinality() {
        return -1;
    }

    /**
     * Get the serialized size of the filter.
     *
     * @return the size in bytes
     */
    default int getSerializedSize() {
        return -1;
    }

    /**
     * Serializes the filter state into the provided {@code ByteBuffer}.
     *
     * @param buffer the byte buffer where the serialized state of the filter will be written
     * @throws UnsupportedOperationException if the operation is not supported by the filter implementation
     */
    default void serialize(ByteBuffer buffer) {
        throw new UnsupportedOperationException();
    }

    /**
     * Serializes the filter state into the provided {@code OutputStream}.
     *
     * @param out the output stream where the serialized state of the filter will be written
     * @throws IOException if writing to the stream fails
     * @throws UnsupportedOperationException if the operation is not supported by the filter implementation
     */
    default void serialize(OutputStream out) throws IOException {
        throw new UnsupportedOperationException();
    }
}
