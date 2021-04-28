package com.github.fastfilter.bloom.count;

import com.github.fastfilter.Filter;
import com.github.fastfilter.utils.Hash;

/**
 * A standard counting Bloom filter, with 4 bits per "data bit" (entry).
 */
public class CountingBloom implements Filter {

    public static CountingBloom construct(long[] keys, double bitsPerKey) {
        long n = keys.length;
        int k = getBestK(bitsPerKey);
        CountingBloom f = new CountingBloom((int) n, bitsPerKey, k);
        for(long x : keys) {
            f.add(x);
        }
        return f;
    }

    private static int getBestK(double bitsPerKey) {
        return Math.max(1, (int) Math.round(bitsPerKey * Math.log(2)));
    }

    private final int k;
    private final long bits;
    private final long seed;
    private final int arraySize;
    private final long[] counts;

    public long getBitCount() {
        return counts.length * 64L;
    }

    CountingBloom(int entryCount, double bitsPerKey, int k) {
        entryCount = Math.max(1, entryCount);
        this.k = k;
        this.seed = Hash.randomSeed();
        // if the entryCount is very small, then there is a relatively high
        // probability that one of the counter overflows, so we add
        // a fixed number of bits (64 in this case) to reduce the probability of this
        // (this is a workaround only)
        this.bits = (long) (4 * entryCount * bitsPerKey) + 64;
        arraySize = (int) ((bits + 63) / 64);
        counts = new long[arraySize];
    }

    @Override
    public boolean supportsAdd() {
        return true;
    }

    @Override
    public void add(long key) {
        long hash = Hash.hash64(key, seed);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        for (int i = 0; i < k; i++) {
            int index = Hash.reduce(a, arraySize << 4);
            int oldCount = (int) (counts[index >>> 4] >>> (index << 2)) & 0xf;            
            if (oldCount >= 15) {
                // TODO we should also undo what was added so far
                throw new UnsupportedOperationException("Counter overflow");
            }
            counts[index >>> 4] += getBit(index);
            a += b;
        }
    }

    @Override
    public boolean supportsRemove() {
        return true;
    }

    @Override
    public void remove(long key) {
        long hash = Hash.hash64(key, seed);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        for (int i = 0; i < k; i++) {
            int index = Hash.reduce(a, arraySize << 4);
            counts[index >>> 4] -= getBit(index);
            a += b;
        }
    }

    private static long getBit(int index) {
        return 1L << (index << 2);
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        for (int i = 0; i < k; i++) {
            int index = Hash.reduce(a, arraySize << 4);
            if (((counts[index >>> 4] >>> (index << 2)) & 0xf) == 0) {
                return false;
            }
            a += b;
        }
        return true;
    }

    @Override
    public long cardinality() {
        long sum = 0;
        for (long x : counts) {
            sum += Long.bitCount(x);
        }
        return sum;
    }

}
