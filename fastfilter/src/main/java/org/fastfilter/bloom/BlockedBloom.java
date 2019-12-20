package org.fastfilter.bloom;

import org.fastfilter.Filter;
import org.fastfilter.utils.Hash;

/**
 * A special kind of blocked Bloom filter. It sets 2 to 4 (usually 4) bits in
 * two 64-bit words; 1 or 2 (usually 2) per word. It is faster than a regular
 * Bloom filter, but needs slightly more space / has a slightly worse false
 * positive rate.
 */
public class BlockedBloom implements Filter {

    public static BlockedBloom construct(long[] keys, int bitsPerKey) {
        long n = keys.length;
        BlockedBloom f = new BlockedBloom((int) n, bitsPerKey);
        for(long x : keys) {
            f.add(x);
        }
        return f;
    }

    private final int buckets;
    private final long seed;
    private final long[] data;

    public long getBitCount() {
        return data.length * 64L;
    }

    BlockedBloom(int entryCount, int bitsPerKey) {
        // bitsPerKey = 11;
        entryCount = Math.max(1, entryCount);
        this.seed = Hash.randomSeed();
        long bits = (long) entryCount * bitsPerKey;
        this.buckets = (int) bits / 64;
        data = new long[(int) (buckets + 16)];
    }

    @Override
    public boolean supportsAdd() {
        return true;
    }

    @Override
    public void add(long key) {
        long hash = Hash.hash64(key, seed);
        int start = Hash.reduce((int) hash, buckets);
        hash = hash ^ Long.rotateLeft(hash, 32);
        long m1 = (1L << hash) | (1L << (hash >> 6));
        long m2 = (1L << (hash >> 12)) | (1L << (hash >> 18));
        data[start] |= m1;
        data[start + 1 + (int) (hash >>> 60)] |= m2;
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        int start = Hash.reduce((int) hash, buckets);
        hash = hash ^ Long.rotateLeft(hash, 32);
        long a = data[start];
        long b = data[start + 1 + (int) (hash >>> 60)];
        long m1 = (1L << hash) | (1L << (hash >> 6));
        long m2 = (1L << (hash >> 12)) | (1L << (hash >> 18));
        return ((m1 & a) == m1) && ((m2 & b) == m2);
    }

}
