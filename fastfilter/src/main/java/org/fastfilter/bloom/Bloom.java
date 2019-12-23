package org.fastfilter.bloom;

import org.fastfilter.MutableFilter;
import org.fastfilter.utils.Hash;

/**
 * A standard Bloom filter.
 *
 */
public class Bloom implements MutableFilter {

    public static Bloom construct(long[] keys, double bitsPerKey, long seed) {
        long n = keys.length;
        int k = getBestK(bitsPerKey);
        Bloom f = new Bloom((int) n, bitsPerKey, k, seed);
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
    private final long[] data;

    public long getBitCount() {
        return data.length * 64L;
    }

    Bloom(int entryCount, double bitsPerKey, int k, long seed) {
        entryCount = Math.max(1, entryCount);
        this.k = k;
        this.seed = seed;
        this.bits = (long) (entryCount * bitsPerKey);
        arraySize = (int) ((bits + 63) / 64);
        data = new long[arraySize];
    }

    @Override
    public void add(long key) {
        long hash = Hash.hash64(key, seed);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        for (int i = 0; i < k; i++) {
            data[Hash.reduce(a, arraySize)] |= 1L << a;
            a += b;
        }
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        for (int i = 0; i < k; i++) {
            if ((data[Hash.reduce(a, arraySize)] & 1L << a) == 0) {
                return false;
            }
            a += b;
        }
        return true;
    }



}
