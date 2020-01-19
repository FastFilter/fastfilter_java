package org.fastfilter.xor;

import java.util.Locale;

import org.fastfilter.utils.Hash;
import org.fastfilter.utils.RandomGenerator;

public class ProbabilityFuse {
    
    private static final int HASHES = 3;
    private static final int FUSE_ARITY = 3;
    private static final int FUSE_SEGMENT_COUNT = 100;
    private static final int FUSE_SLOTS = FUSE_SEGMENT_COUNT + FUSE_ARITY - 1;

    public static void main(String... args) {
        for(int size = 10; size < 500000; size *= 1.1) {
            System.out.print("size " + size);
            double start = Math.max(0.1, Math.min(0.8, Math.log10(size / 100) /4));
            double change = 0.1;
            int lastDirection = 1;
            double p = 0;
            double factor = start + 0.1;
            for(; factor > 0.0;) {
                int successCount = 0;
                int testCount = Math.max(10, 1000000 / size);
                for(int seed = 0; seed < testCount; seed++) { 
                    long[] keys = new long[size];
                    RandomGenerator.createRandomUniqueListFast(keys, seed);
                    boolean success = testMapping(keys, factor, seed);
                    if (success) {
                        successCount++;
                    }
                }
                p = 1.0 * successCount / testCount;
                double minP = 0.01;
                if (p < minP && factor > 0.1) {
                    factor -= change;
                    if (lastDirection != -1) {
                        lastDirection = -1;
                        change = change / 2;
                    }
                } else if (p > minP * 1.1) {
                    if (change < 0.0001) {
                        break;
                    }
                    if (factor > 0.8) {
                        break;
                    }
                    factor += change;
                    if (lastDirection != 1) {
                        lastDirection = 1;
                        change = change / 2;
                    }
                } else {
                    break;
                }
                // System.out.printf(Locale.ENGLISH, " %2.5f %2.3f %2.20f\n", factor, p, change);
            }
            System.out.printf(Locale.ENGLISH, " %2.5f %2.3f\n", factor, p);
        }
    }

    /**
     * Get the fill rate for a certain size for a 95% probability.
     *
     * @param size the size
     * @return the factor
     */
    public static double getFactor(int size) {
        if (size < 100) {
            return 0.13;
        }
        if (size > 170000) {
            return 0.879;
        }
        // this formula is weird, using cosine and log base 10, but it works.
        // it was found manually trying to fit the curve
        return Math.cos(Math.log10(size) / 1.2 - 4.7) / 2.7 + 0.5;
    }
    
    public static boolean testMapping(long[] keys, double factor, long seed) {
        int size = keys.length;
        int arrayLength = getArrayLength(size, factor);
        int segmentLength = arrayLength / FUSE_SLOTS;
        int m = arrayLength;
        long[] reverseOrder = new long[size];
        byte[] reverseH = new byte[size];
        int reverseOrderPos;
        seed = Hash.randomSeed();
        byte[] t2count = new byte[m];
        long[] t2 = new long[m];
        for (long k : keys) {
            for (int hi = 0; hi < HASHES; hi++) {
                int h = getHash(segmentLength, k, seed, hi);
                t2[h] ^= k;
                if (t2count[h] > 120) {
                    // probably something wrong with the hash function
                    throw new IllegalArgumentException();
                }
                t2count[h]++;
            }
        }
        reverseOrderPos = 0;
        int[] alone = new int[arrayLength];
        int alonePos = 0;
        for (int i = 0; i < arrayLength; i++) {
            if (t2count[ i] == 1) {
                alone[alonePos++] = i;
            }
        }
        int found = -1;
        while (alonePos > 0) {
            int i = alone[--alonePos];
            if (t2count[i] <= 0) {
                continue;
            }
            if (t2count[i] != 1) {
                throw new AssertionError();
            }
            --t2count[i];
            long k = t2[i];
            for (int hi = 0; hi < HASHES; hi++) {
                int h = getHash(segmentLength, k, seed, hi);
                int newCount = --t2count[h];
                if (h == i) {
                    found = hi;
                } else {
                    if (newCount == 1) {
                        alone[alonePos++] = h;
                    }
                    t2[h] ^= k;
                }
            }
            reverseOrder[reverseOrderPos] = k;
            reverseH[reverseOrderPos] = (byte) found;
            reverseOrderPos++;
        }
        return reverseOrderPos == size;
    }
    
    private static int getHash(int segmentLength, long key, long seed, int index) {
        long hash = Hash.hash64(key, seed);
        int r0 = (int) ((0xBF58476D1CE4E5B9L * hash) >> 32);
        int seg = Hash.reduce(r0, FUSE_SEGMENT_COUNT);
        int r = (int) Long.rotateLeft(hash, 21 * index);
        return (seg + index) * segmentLength + Hash.reduce(r, segmentLength);
    }

    private static int getArrayLength(int size, double factor) {
      int capacity = (int) (1.0 / factor * size) + 64;
      capacity = (capacity + FUSE_SLOTS - 1) / FUSE_SLOTS * FUSE_SLOTS;
      return capacity;
  }

}
