package org.fastfilter.bloom;

import org.fastfilter.Filter;
import org.fastfilter.utils.Hash;

/**
 * A blocked bloom filter. I little bit faster, but needs more space. Not that
 * useful beyond about 20 bits per key, as fpp doesn't decreased further.
 */
public class BlockedBloom implements Filter {

    // TODO not cache line aligned

    // Should match the size of a cache line
    private static final int BITS_PER_BLOCK = 64 * 8;
    private static final int LONGS_PER_BLOCK = BITS_PER_BLOCK / 64;
    private static final int BLOCK_MASK = BITS_PER_BLOCK - 1;

    public static BlockedBloom construct(long[] keys, int bitsPerKey) {
        long n = keys.length;
        long m = n * bitsPerKey;
        int k = getBestK(m, n);
        BlockedBloom f = new BlockedBloom((int) n, bitsPerKey, k);
        for(long x : keys) {
            f.add(x);
        }
        return f;
    }

    private static int getBestK(long m, long n) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    private final int k;
    private final int blocks;
    private final long seed;
    private final long[] data;

    public long getBitCount() {
        return data.length * 64L;
    }

    BlockedBloom(int entryCount, int bitsPerKey, int k) {
        entryCount = Math.max(1, entryCount);
        this.k = k;
        this.seed = Hash.randomSeed();
        long bits = (long) entryCount * bitsPerKey;
        this.blocks = (int) (bits + BITS_PER_BLOCK - 1) / BITS_PER_BLOCK;
        data = new long[(int) (blocks * LONGS_PER_BLOCK) + 8];
    }

    @Override
    public boolean supportsAdd() {
        return true;
    }

    @Override
    public void add(long key) {
        long hash = Hash.hash64(key, seed);
        int start = Hash.reduce((int) hash, blocks) * LONGS_PER_BLOCK;
        int a = (int) hash;
        int b = (int) (hash >>> 32);
        for (int i = 0; i < k; i++) {
            data[start + ((a & BLOCK_MASK) >>> 6)] |= getBit(a);
            a += b;
        }
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        int start = Hash.reduce((int) hash, blocks) * LONGS_PER_BLOCK;
        int a = (int) hash;
        int b = (int) (hash >>> 32);
        for (int i = 0; i < k; i++) {
            if ((data[start + ((a & BLOCK_MASK) >>> 6)] & getBit(a)) == 0) {
                return false;
            }
            a += b;
        }
        return true;
    }

    private static long getBit(int index) {
        return 1L << index;
    }

}
