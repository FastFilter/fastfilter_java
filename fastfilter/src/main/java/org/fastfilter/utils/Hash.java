package org.fastfilter.utils;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class Hash {

    private static Random random = new Random();

    public static void setSeed(long seed) {
        random = new Random(seed);
    }

    public static long hash64(long x, long seed) {
        x += seed;
        x = (x ^ (x >>> 33)) * 0xff51afd7ed558ccdL;
        x = (x ^ (x >>> 33)) * 0xc4ceb9fe1a85ec53L;
        x = x ^ (x >>> 33);
        return x;
    }

    public static long randomSeed() {
        return random.nextLong();
    }

    /**
     * Shrink the hash to a value 0..n. Kind of like modulo, but using
     * multiplication and shift, which are faster to compute.
     *
     * @param hash the hash
     * @param n the maximum of the result
     * @return the reduced value
     */
    public static int reduce(int hash, int n) {
        // http://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
        return (int) (((hash & 0xffffffffL) * (n & 0xffffffffL)) >>> 32);
    }

    /**
     * Multiply two unsigned 64-bit values.
     * See https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8188044
     *
     * @param a the first value
     * @param b the second value
     * @return the result
     */
    public static long multiplyHighUnsigned(long a, long b) {
        return Math.unsignedMultiplyHigh(a, b);
    }

}
