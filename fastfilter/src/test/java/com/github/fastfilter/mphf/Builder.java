package com.github.fastfilter.mphf;

import com.github.fastfilter.gcs.BitBuffer;

public class Builder {

    private static final int[] SHIFT = { 0, 0, 0, 1, 3, 4, 5, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
            3, 3 };

    private int fingerprintBits = 0;
    private int averageBucketSize = 16;
    private int leafSize = 5;

    public Builder leafSize(int leafSize) {
        this.leafSize = leafSize;
        return this;
    }

    public Builder averageBucketSize(int averageBucketSize) {
        this.averageBucketSize = averageBucketSize;
        return this;
    }

    public Builder fingerprintBits(int fingerprintBits) {
        this.fingerprintBits = fingerprintBits;
        return this;
    }

    public BitBuffer generate(long[] keys) {
        return new FastGenerator(leafSize, averageBucketSize).generate(keys);
    }

    public BitBuffer generate(long[] keys, int len) {
        return new FastGenerator(leafSize, averageBucketSize).generate(keys, len, null);
    }

    public BitBuffer generate(long[] keys, int len, BitBuffer fingerprints) {
        return new FastGenerator(leafSize, averageBucketSize, fingerprintBits).generate(keys, len, fingerprints);
    }

    public FastEvaluator evaluator(BitBuffer buff) {
        return new FastEvaluator(buff, averageBucketSize, leafSize, 0, null, 0);
    }

    public FastEvaluator evaluator(BitBuffer buff, BitBuffer fingerprints) {
        return new FastEvaluator(buff, averageBucketSize, leafSize, 0, fingerprints, fingerprintBits);
    }

    public static int supplementalHash(long x, int index) {
            // TODO can save one multiplication for generation
            //System.out.println("   " + x + " [" + index + "]");
            return supplementalHashWeyl(x, index);
    }
    
    public static int supplementalHashWeyl(long hash, long index) {
        long x = hash + (index * 0xbf58476d1ce4e5b9L);
        x = (x ^ (x >>> 32)) * 0xbf58476d1ce4e5b9L;
        x = ((x >>> 32) ^ x);
        return (int) x;
    }

    public static int reduce(int hash, int n) {
        // http://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
        return (int) (((hash & 0xffffffffL) * n) >>> 32);
    }

    public static int getBucketCount(long size, int averageBucketSize) {
        int bucketCount = (int) ((size + averageBucketSize - 1) / averageBucketSize);
        return nextPowerOf2(bucketCount);
    }

    static int nextPowerOf2(int x) {
        if (Integer.bitCount(x) == 1) {
            return x;
        }
        return Integer.highestOneBit(x) * 2;
    }

    public static int getGolombRiceShift(int size) {
        return SHIFT[size];
    }

}
