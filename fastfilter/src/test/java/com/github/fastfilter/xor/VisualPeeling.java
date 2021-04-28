package com.github.fastfilter.xor;

import java.util.Locale;

import com.github.fastfilter.utils.Hash;
import com.github.fastfilter.utils.RandomGenerator;

/**
 * Visualize the peeling / burning of fuse filters.
 *
 * See also "Peeling Close to the Orientability Threshold - Spatial Coupling in
 * Hashing-Based Data Structures"
 *
 */
public class VisualPeeling {

    // true for regular xor filter, false for everything else
    private static final boolean BPZ = false;

    // true for fuse filter, false for coupled
    private static final boolean FUSE = true;

    // only for coupled: true for mirrored (one fuse), two for regular (two fuses)
    private static final boolean MIRROR = true;

    private static final int HASHES = 3;

    static void testOneMillion() {
        int size = 1_000_000;
        int segmentLengthBits = 13;
        int segmentLength = 1 << segmentLengthBits;
        System.out.println("cf2 segmentLength " + segmentLength + " ");
        double min = 0.87, max = 0.888, step = 0.001;
        if (FUSE) {
            min = 0.885;
            max = 0.9;
            step = 0.001;
        }
        if (BPZ) {
            min = 0.81;
            max = 0.82;
            step = 0.002;
        }
        for (double load = min; load <= max; load += step) {
            Data d = getProbability(size, segmentLengthBits, load, null);
            System.out.println(d);
        }
    }

    public static void main(String... args) {
        testOneMillion();
    }

    static Data getProbability(int size, int segmentLengthBits, double load, Data best) {
        int segmentLength = 1 << segmentLengthBits;
        int arrayLength = (int) (size / load);
        if (arrayLength <= 0) {
            return null;
        }
        int segmentCount = arrayLength - 1 * segmentLength;
        if (FUSE) {
            segmentCount = (arrayLength - 2 * segmentLength) / segmentLength;
        }
        if (BPZ) {
            segmentCount = 3;
            segmentLength = (arrayLength - 2) / 3;
        }
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
        int testCount = Math.max(5, 10_000_000 / size);
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
        if (BPZ) {
            segmentLength = (arrayLength - 2) / 3;
        }
        int size = keys.length;
        int m = arrayLength;
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
        int count = 0;
        int[] alone = new int[arrayLength];
        int[] alone2 = new int[arrayLength];
        int alonePos = 0;
        for (int i = 0; i < arrayLength; i++) {
            if (t2count[i] == 1) {
                alone[alonePos++] = i;
            }
        }
        System.out.println();
        int levels = 0;
        while (count < size) {
            int mod = BPZ ? 3 : 200;
            if (levels % mod == 0) {
                int[] nonZeroCount = new int[40];
                for (int i = 0; i < t2count.length; i++) {
                    if (t2count[i] > 0) {
                        nonZeroCount[i * 40 / t2count.length]++;
                    }
                }
                int max = t2count.length / 40;
                for (int i = 0; i < 40; i++) {
                    System.out.print(nonZeroCount[i] == 0 ? 0 : (1 + nonZeroCount[i] * 8 / max));
                }
                System.out.println();
            }
            levels++;
            if (alonePos == 0) {
                System.out.println("FAIL: levels=" + levels);
                return null;
            }
            int alonePos2 = 0;
            while (alonePos > 0) {
                int i = alone[--alonePos];
                if (t2count[i] <= 0) {
                    continue;
                }
                if (t2count[i] != 1) {
                    throw new AssertionError();
                }
                --t2count[i];
                count++;
                long k = t2[i];
                for (int hi = 0; hi < HASHES; hi++) {
                    int h = getHash(segmentLengthBits, segmentLength, segmentCount, k, seed, hi);
                    int newCount = --t2count[h];
                    if (h == i) {
                        // ignore
                    } else {
                        if (newCount == 1) {
                            alone2[alonePos2++] = h;
                        }
                        t2[h] ^= k;
                    }
                }
            }
            System.arraycopy(alone2, 0, alone, 0, alonePos2);
            alonePos = alonePos2;

        }
        System.out.println("SUCCESS: levels=" + levels);
        return new int[0];
    }

    private static int getHash(int segmentLengthBits, int segmentLength, int segmentCount, long key, long seed, int index) {
        if (BPZ) {
            long hash = Hash.hash64(key, seed + index);
            return index * segmentLength + Hash.reduce((int) hash, segmentLength);
        }
        if (FUSE) {
            long hash = Hash.hash64(key, seed);
            int seg = Hash.reduce((int) hash, segmentCount);
            long hh = (hash ^ (hash >>> 32));
            int h0 = (seg + 0) * segmentLength + (int) ((hh >> (0 * segmentLengthBits)) & (segmentLength - 1));
            int h1 = (seg + 1) * segmentLength + (int) ((hh >> (1 * segmentLengthBits)) & (segmentLength - 1));
            int h2 = (seg + 2) * segmentLength + (int) ((hh >> (2 * segmentLengthBits)) & (segmentLength - 1));
            return index == 0 ? h0 : index == 1 ? h1 : h2;
        }
        if (MIRROR) {
            long hash = Hash.hash64(key, seed);
            int r0 = (int) Hash.hash64(hash, 1);
            int x = Hash.reduce(r0, segmentCount * 2 + segmentLength - 1);
            int h0 = x + (int) (Hash.hash64(hash, 2) & (segmentLength - 1));
            int h1 = x + (int) (Hash.hash64(hash, 3) & (segmentLength - 1));
            int h2 = x + (int) (Hash.hash64(hash, 4) & (segmentLength - 1));
            h0 = Math.abs(h0 - segmentCount - segmentLength + 1);
            h1 = Math.abs(h1 - segmentCount - segmentLength + 1);
            h2 = Math.abs(h2 - segmentCount - segmentLength + 1);
            return index == 0 ? h0 : index == 1 ? h1 : h2;
        } else {
            long hash = Hash.hash64(key, seed);
            int r0 = (int) Hash.hash64(hash, 1);
            int x = Hash.reduce(r0, segmentCount);
            int h0 = x + (int) (Hash.hash64(hash, 2) & (segmentLength - 1));
            int h1 = x + (int) (Hash.hash64(hash, 3) & (segmentLength - 1));
            int h2 = x + (int) (Hash.hash64(hash, 4) & (segmentLength - 1));
            return index == 0 ? h0 : index == 1 ? h1 : h2;
        }
    }

    static class Data {
        int size;
        double load;
        int segmentLength;
        double bitsPerKey;
        double p;
        int[] data;

        public String toString() {
            return String.format(Locale.ENGLISH, "size %d load %.3f " +
                "segmentLength %d bits/key %.1f p %.2f"
                , size, load, segmentLength, bitsPerKey, p);
        }

    }

}
