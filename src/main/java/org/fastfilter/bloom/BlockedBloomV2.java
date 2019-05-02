package org.fastfilter.bloom;

import org.fastfilter.Filter;
import org.fastfilter.utils.Hash;

/**
 * A special kind of blocked Bloom filter. It sets 6 bits in 3 consecutive
 * 64-bit words, and exactly 2 bits per word. It is faster than a regular Bloom
 * filter, but needs slightly more space / has a slightly worse false positive
 * rate.
 */
public class BlockedBloomV2 implements Filter {

    public static BlockedBloomV2 construct(long[] keys, int bitsPerKey) {
        long n = keys.length;
        BlockedBloomV2 f = new BlockedBloomV2((int) n, bitsPerKey);
        for(long x : keys) {
            f.add(x);
        }
        return f;
    }

    private final int blocks;
    private final long seed;
    private final long[] data;

    public long getBitCount() {
        return data.length * 64L;
    }

    BlockedBloomV2(int entryCount, int bitsPerKey) {
        entryCount = Math.max(1, entryCount);
        this.seed = Hash.randomSeed();
        long bits = (long) entryCount * bitsPerKey;
        this.blocks = (int) bits / 64;
        data = new long[(int) (blocks + 8)];
    }

    @Override
    public boolean supportsAdd() {
        return true;
    }

    @Override
    public void add(long key) {
        long hash = Hash.hash64(key, seed);
        int start = Hash.reduce((int) hash, blocks);
        int a = (int) hash;
        int b = (int) (hash >>> 32);
        for (int i = 0; i < 3; i++) {
            a += b;
            data[start] |= (1L << a) | (1L << (a >> 8));
            start++;
        }
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        int start = Hash.reduce((int) hash, blocks);
        int a = (int) hash;
        int b = (int) (hash >>> 32);
        for (int i = 0; i < 3; i++) {
            a += b;
            long x = data[start];
            if (((x >> a) & (x >> (a >> 8)) & 1) == 0) {
                return false;
            }
            start++;
        }
        return true;
    }

}
