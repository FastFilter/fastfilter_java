package org.fastfilter.xor;

import org.fastfilter.Filter;
import org.fastfilter.utils.Hash;

/**
 * The Xor Fuse Filter, a new algorithm that can replace a bloom filter.
 *
 * It is related to the BDZ algorithm [1] (a minimal perfect hash function
 * algorithm).
 *
 * [1] paper: Simple and Space-Efficient Minimal Perfect Hash Functions -
 * http://cmph.sourceforge.net/papers/wads07.pdf
 */
public class XorFuse8 implements Filter {

    private static final int BITS_PER_FINGERPRINT = 8;
    private static final int HASHES = 3;

    private static final int FUSE_ARITY = 3;
    private static final int FUSE_SEGMENT_COUNT = 100;
    private static final int FUSE_SLOTS = FUSE_SEGMENT_COUNT + FUSE_ARITY - 1;
    
    private final int size;
    private final int segmentLength;
    private final int arrayLength;
    private long seed;
    private byte[] fingerprints;
    private final int bitCount;

    public long getBitCount() {
        return bitCount;
    }

    private static int getArrayLength(int size, double factor) {
        int capacity = (int) (1.0 / factor * size);
        capacity = (capacity + FUSE_SLOTS - 1) / FUSE_SLOTS * FUSE_SLOTS;
        return capacity;
    }

    public static XorFuse8 construct(long[] keys) {
        int size = keys.length;
        double factor = 0.879;
        if (size < 1_000) {
            factor = 0.5;
        } else if (size < 10_000) {
            factor = 0.7;
        } else if (size < 100_000) {
            factor = 0.8;
        }
        while (true) {
            try {
                return new XorFuse8(keys, factor);
            } catch (UnsupportedOperationException e) {
                // try again with a lower load
                factor -= 0.1;
            }
        }
    }
    
    public XorFuse8(long[] keys, double factor) {
        this.size = keys.length;
        arrayLength = getArrayLength(size, factor);
        segmentLength = arrayLength / FUSE_SLOTS;
        bitCount = arrayLength * BITS_PER_FINGERPRINT;
        int m = arrayLength;
        long[] reverseOrder = new long[size];
        byte[] reverseH = new byte[size];
        int reverseOrderPos;
        long seed;
        int x = 0;
        do {
            x++;
            if (x > 10) {
                throw new UnsupportedOperationException();
            }
            seed = Hash.randomSeed();
            byte[] t2count = new byte[m];
            long[] t2 = new long[m];
            for (long k : keys) {
                for (int hi = 0; hi < HASHES; hi++) {
                    int h = getHash(k, seed, hi);
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
                    int h = getHash(k, seed, hi);
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
        } while (reverseOrderPos != size);
        this.seed = seed;
        byte[] fp = new byte[m];
        for (int i = reverseOrderPos - 1; i >= 0; i--) {
            long k = reverseOrder[i];
            int found = reverseH[i];
            int change = -1;
            long hash = Hash.hash64(k, seed);
            int xor = fingerprint(hash);
            for (int hi = 0; hi < HASHES; hi++) {
                int h = getHash(k, seed, hi);
                if (found == hi) {
                    change = h;
                } else {
                    xor ^= fp[h];
                }
            }
            fp[change] = (byte) xor;
        }
        fingerprints = new byte[m];
        System.arraycopy(fp, 0, fingerprints, 0, fp.length);
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        int f = fingerprint(hash);
        int r0 = (int) ((0xBF58476D1CE4E5B9L * hash) >> 32);
        int r1 = (int) hash;
        int r2 = (int) Long.rotateLeft(hash, 21);
        int r3 = (int) Long.rotateLeft(hash, 42);
        int seg = Hash.reduce(r0, FUSE_SEGMENT_COUNT);
        int h0 = (seg + 0) * segmentLength + Hash.reduce(r1, segmentLength);
        int h1 = (seg + 1) * segmentLength + Hash.reduce(r2, segmentLength);
        int h2 = (seg + 2) * segmentLength + Hash.reduce(r3, segmentLength);
        f ^= fingerprints[h0] ^ fingerprints[h1] ^ fingerprints[h2];
        return (f & 0xff) == 0;
    }

    private int getHash(long key, long seed, int index) {
        long hash = Hash.hash64(key, seed);
        int r0 = (int) ((0xBF58476D1CE4E5B9L * hash) >> 32);
        int seg = Hash.reduce(r0, FUSE_SEGMENT_COUNT);
        int r = (int) Long.rotateLeft(hash, 21 * index);
        return (seg + index) * segmentLength + Hash.reduce(r, segmentLength);
    }

    private int fingerprint(long hash) {
        return (int) (hash & ((1 << BITS_PER_FINGERPRINT) - 1));
    }

}
