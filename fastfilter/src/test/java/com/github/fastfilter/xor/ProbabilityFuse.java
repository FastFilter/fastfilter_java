package com.github.fastfilter.xor;

import java.util.Locale;

import com.github.fastfilter.utils.Hash;
import com.github.fastfilter.utils.RandomGenerator;

/**
 * Calculate the best segment length for various sizes, and the probability of
 * mapping, for the fuse filter. Specially interesting are "small" set sizes
 * between 100 and 1 million.
 *
 * See also "Dense Peelable Random Uniform Hypergraphs"
 */
public class ProbabilityFuse {

    private static final int HASHES = 3;

//    size 10 load 0.40 segmentLength 8 bits/key 20.0 p 0.90
//    size 100 load 0.60 segmentLength 32 bits/key 13.3 p 0.93
//    size 1000 load 0.70 segmentLength 64 bits/key 11.4 p 0.89
//    size 10000 load 0.80 segmentLength 256 bits/key 10.0 p 0.86
//    size 100000 load 0.85 segmentLength 1024 bits/key 9.4 p 0.98

    public static void main(String... args) {
        for(int size = 1; size < 1_000_000; size *= 10) {
        // for(int size = 1; size < 1_000_000; size = (size < 100) ? (size + 1) : (int) (size * 1.1)) {
            Data best = null;
            for (int segmentLengthBits = 3; segmentLengthBits < 14; segmentLengthBits++) {
                int segmentLength = 1 << segmentLengthBits;
                if (segmentLength > size) {
                    break;
                }
                for(double load = 0.85; load > 0.3; load-= 0.05) {
                    Data d = getProbability(size, segmentLengthBits, load, best);
                    if (d != null && d.p > 0.85) {
                        if (best == null || d.bitsPerKey < best.bitsPerKey) {
                            best = d;
                        }
                        break;
                    }
                }
            }
            if (best != null) {
                System.out.println(best);
            }
        }
    }

    static Data getProbability(int size, int segmentLengthBits, double load, Data best) {
        int segmentLength = 1 << segmentLengthBits;
        int arrayLength = (int) (size / load);
        if (arrayLength <= 0) {
            return null;
        }
        int segmentCount = (arrayLength - 2 * segmentLength) / segmentLength;
        if (segmentCount <= 0) {
            return null;
        }
        Data d = new Data();
        d.size = size;
        d.load = load;
        d.segmentLength = segmentLength;
        d.bitsPerKey = (double) arrayLength * 8 / size;
        if (best != null && d.bitsPerKey > best.bitsPerKey) {
            return null;
        }
        // System.out.println("  test " + d);
        int successCount = 0;
        int testCount = Math.max(10, 10_000_000 / size);
        for(int seed = 0; seed < testCount; seed++) {
            long[] keys = new long[size];
            RandomGenerator.createRandomUniqueListFast(keys, seed);
            boolean success = testMapping(keys, segmentLengthBits, segmentCount, arrayLength, seed);
            if (success) {
                successCount++;
            }
        }
        double p = 1.0 * successCount / testCount;
        d.p = p;
        return d;
    }

    public static boolean testMapping(long[] keys, int segmentLengthBits, int segmentCount, int arrayLength, long seed) {
        int segmentLength = 1 << segmentLengthBits;
        int size = keys.length;
        int m = arrayLength;
        long[] reverseOrder = new long[size];
        byte[] reverseH = new byte[size];
        int reverseOrderPos;
        seed = Hash.randomSeed();
        byte[] t2count = new byte[m];
        long[] t2 = new long[m];
        for (long k : keys) {
            for (int hi = 0; hi < HASHES; hi++) {
                int h = getHash(segmentLengthBits, segmentLength, segmentCount, k, seed, hi);
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
                int h = getHash(segmentLengthBits, segmentLength, segmentCount, k, seed, hi);
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

    private static int getHash(int segmentLengthBits, int segmentLength, int segmentCount, long key, long seed, int index) {
        long hash = Hash.hash64(key, seed);
        int seg = Hash.reduce((int) hash, segmentCount);
        long hh = (hash ^ (hash >>> 32));
        int h0 = (seg + 0) * segmentLength + (int) ((hh >> (0 * segmentLengthBits)) & (segmentLength - 1));
        int h1 = (seg + 1) * segmentLength + (int) ((hh >> (1 * segmentLengthBits)) & (segmentLength - 1));
        int h2 = (seg + 2) * segmentLength + (int) ((hh >> (2 * segmentLengthBits)) & (segmentLength - 1));
        return index == 0 ? h0 : index == 1 ? h1 : h2;
    }

    static class Data {
        int size;
        double load;
        int segmentLength;
        double bitsPerKey;
        double p;

        public String toString() {
            return String.format(Locale.ENGLISH, "size %d load %.2f " +
                "segmentLength %d bits/key %.1f p %.2f"
                , size, load, segmentLength, bitsPerKey, p);
        }

    }

}
