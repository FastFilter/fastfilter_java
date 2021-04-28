package com.github.fastfilter.xor;

import java.util.Locale;

import com.github.fastfilter.utils.Hash;
import com.github.fastfilter.utils.RandomGenerator;

/**
 * Calculate the best segment length for various sizes, and the probability of
 * mapping, for the fuse filter. Specially interesting are "small" set sizes
 * between 100 and 1 million.
 *
 * Unlike the regular fuse filter, which has less density at the beginning and
 * the end of the array, here density is only lower at the end of the array.
 * Index computation is slower, but load should be slightly higher.
 *
 * See also "Peeling Close to the Orientability Threshold - Spatial Coupling in
 * Hashing-Based Data Structures"
 *
 */
public class ProbabilityCFuse2 {

    private static final int HASHES = 3;
    private static final int BITS_PER_FINGERPRINT = 8;

//    size 100 load 0.45 segmentLength 64 bits/key 17.8 p 0.86
//    size 1000 load 0.70 segmentLength 256 bits/key 11.4 p 0.88
//    size 10000 load 0.80 segmentLength 1024 bits/key 10.0 p 0.90
//    size 100000 load 0.85 segmentLength 4096 bits/key 9.4 p 0.87

    private static void testProb() {
        int segmentLength = 16;
        int segmentCount = 100;
        int[] counts = new int[200];
        for(int i=0; i<1000000; i++) {
            for(int index = 0; index < 3; index++) {
                long seed = 0;
                long key = i;
                long hash = Hash.hash64(key, seed);

//                int r0 = (int) Hash.hash64(hash, 1);
//                int x = Hash.reduce(r0, segmentCount);
//                int h0 = x + (int) (Hash.hash64(hash, 2) & (segmentLength - 1));
//                int h1 = x + (int) (Hash.hash64(hash, 3) & (segmentLength - 1));
//                int h2 = x + (int) (Hash.hash64(hash, 4) & (segmentLength - 1));
//
                int r0 = (int) Hash.hash64(hash, 1);
                int x = Hash.reduce(r0, segmentCount * 2 + segmentLength - 1);
                int h0 = x + (int) (Hash.hash64(hash, 2) & (segmentLength - 1));
                int h1 = x + (int) (Hash.hash64(hash, 3) & (segmentLength - 1));
                int h2 = x + (int) (Hash.hash64(hash, 4) & (segmentLength - 1));
                h0 = Math.abs(h0 - segmentCount - segmentLength + 1);
                h1 = Math.abs(h1 - segmentCount - segmentLength + 1);
                h2 = Math.abs(h2 - segmentCount - segmentLength + 1);
                int idx = index == 0 ? h0 : index == 1 ? h1 : h2;
                counts[idx]++;
            }
        }
        for(int i=0; i<counts.length; i++) {
            System.out.println(i + " " + counts[i]);
        }

    }

    public static void main(String... args) {
//testProb();
//if(true)return;


//        for(int size = 100_000; size < 1_000_000; size *= 10) {
        for(int size = 1; size < 1_000_000; size *= 10) {
        // for(int size = 1; size < 1_000_000; size = (size < 100) ? (size + 1) : (int) (size * 1.1)) {
            Data best = null;
            for (int segmentLengthBits = 3; segmentLengthBits <= 12; segmentLengthBits++) {
//            for (int segmentLengthBits = 3; segmentLengthBits < 14; segmentLengthBits++) {
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
//                for(int i=0; i<100; i++) {
//                    System.out.println(i + ": " + best.data[i]);
//                }
            }
        }
    }

    static Data getProbability(int size, int segmentLengthBits, double load, Data best) {
        int segmentLength = 1 << segmentLengthBits;
        int arrayLength = (int) (size / load);
        if (arrayLength <= 0) {
            return null;
        }
        int segmentCount = arrayLength - 1 * segmentLength;
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
            int[] success = testMapping(keys, segmentLengthBits, segmentCount, arrayLength, seed);
            if (success != null) {
                d.data = success;
                successCount++;
            }
        }
        double p = 1.0 * successCount / testCount;
        d.p = p;
        return d;
    }

    public static int[] testMapping(long[] keys, int segmentLengthBits, int segmentCount, int arrayLength, long seed) {
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
        if (reverseOrderPos != size) {
            return null;
        }
        byte[] fp = new byte[m];
        for (int i = reverseOrderPos - 1; i >= 0; i--) {
            long k = reverseOrder[i];
            found = reverseH[i];
            int change = -1;
            long hash = Hash.hash64(k, seed);
            int xor = fingerprint(hash);
            for (int hi = 0; hi < HASHES; hi++) {
                int h = getHash(segmentLengthBits, segmentLength, segmentCount, k, seed, hi);
                if (found == hi) {
                    change = h;
                } else {
                    xor ^= fp[h];
                }
            }
            fp[change] = (byte) xor;
        }
        int[] nonZero = new int[100];
        for(int i=0; i<fp.length; i++) {
            if (fp[i] != 0) {
                nonZero[i * 100 / fp.length]++;
            }
        }
        return nonZero;
    }

    private static int fingerprint(long hash) {
        return (int) (hash & ((1 << BITS_PER_FINGERPRINT) - 1));
    }

    private static int getHash(int segmentLengthBits, int segmentLength, int segmentCount, long key, long seed, int index) {
        long hash = Hash.hash64(key, seed);
        int r0 = (int) Hash.hash64(hash, 1);
        /*
        int x = Hash.reduce(r0, segmentCount * 2 - 1);
        int h0 = x + (int) (Hash.hash64(hash, 2) & (2 * segmentLength - 1));
        int h1 = x + (int) (Hash.hash64(hash, 3) & (2 * segmentLength - 1));
        int h2 = x + (int) (Hash.hash64(hash, 4) & (2 * segmentLength - 1));
        h0 = Math.abs(h0 - segmentCount - segmentLength + 1);
        h1 = Math.abs(h1 - segmentCount - segmentLength + 1);
        h2 = Math.abs(h2 - segmentCount - segmentLength + 1);
        */

        int x = Hash.reduce(r0, segmentCount * 2 + segmentLength - 1);
        int h0 = x + (int) (Hash.hash64(hash, 2) & (segmentLength - 1));
        int h1 = x + (int) (Hash.hash64(hash, 3) & (segmentLength - 1));
        int h2 = x + (int) (Hash.hash64(hash, 4) & (segmentLength - 1));
        h0 = Math.abs(h0 - segmentCount - segmentLength + 1);
        h1 = Math.abs(h1 - segmentCount - segmentLength + 1);
        h2 = Math.abs(h2 - segmentCount - segmentLength + 1);

        return index == 0 ? h0 : index == 1 ? h1 : h2;
    }

    static class Data {
        int size;
        double load;
        int segmentLength;
        double bitsPerKey;
        double p;
        int[] data;

        public String toString() {
            return String.format(Locale.ENGLISH, "size %d load %.2f " +
                "segmentLength %d bits/key %.1f p %.2f"
                , size, load, segmentLength, bitsPerKey, p);
        }

    }

}
