package org.fastfilter.xor;

import java.util.Arrays;

import org.fastfilter.Filter;
import org.fastfilter.utils.Hash;

/**
 * The xor binary fuse filter, a new algorithm that can replace a Bloom filter.
 */
public class XorBinaryFuse8 implements Filter {

    private static final int ARITY = 3;

    private final int segmentCount;
    private final int segmentCountLength;
    private final int segmentLength;
    private final int segmentLengthMask;
    private final int arrayLength;
    private final byte[] fingerprints;
    private long seed;

    public XorBinaryFuse8(int segmentCount, int segmentLength) {
        if (segmentLength < 0 || Integer.bitCount(segmentLength) != 1) {
            throw new IllegalArgumentException("Segment length needs to be a power of 2, is " + segmentLength);
        }
        if (segmentCount <= 0) {
            throw new IllegalArgumentException("Illegal segment count: " + segmentCount);
        }
        this.segmentLength = segmentLength;
        this.segmentCount = segmentCount;
        this.segmentLengthMask = segmentLength - 1;
        this.segmentCountLength = segmentCount * segmentLength;
        this.arrayLength = (segmentCount + ARITY - 1) * segmentLength;
        this.fingerprints = new byte[arrayLength];
    }

    public long getBitCount() {
        return arrayLength * 8L;
    }

    static int calculateSegmentLength(int arity, int size) {
        int segmentLength;
        if (arity == 3) {
            segmentLength = 1 << (int) Math.floor(Math.log(size) / Math.log(3.33) + 2.11);
        } else if (arity == 4) {
            segmentLength = 1 << (int) Math.floor(Math.log(size) / Math.log(2.91) - 0.5);
        } else {
            // not supported
            segmentLength = 65536;
        }
        return segmentLength;
    }

    static double calculateSizeFactor(int arity, int size) {
        double sizeFactor;
        if (arity == 3) {
            sizeFactor = Math.max(1.125, 0.875 + 0.25 * Math.log(1000000) / Math.log(size));
        } else if (arity == 4) {
            sizeFactor = Math.max(1.075, 0.77 + 0.305 * Math.log(600000) / Math.log(size));
        } else {
            // not supported
            sizeFactor = 2.0;
        }
        return sizeFactor;
    }

    private static int mod3(int x) {
        if (x > 2) {
            x -= 3;
        }
        return x;
    }

    public static XorBinaryFuse8 construct(long[] keys) {
        int size = keys.length;
        int segmentLength = calculateSegmentLength(ARITY, size);
        // the current implementation hardcodes a 18-bit limit to
        // to the segment length.
        if (segmentLength > (1 << 18)) {
            segmentLength = (1 << 18);
        }
        double sizeFactor = calculateSizeFactor(ARITY, size);
        int capacity = (int) (size * sizeFactor);
        int segmentCount = (capacity + segmentLength - 1) / segmentLength - (ARITY - 1);
        int arrayLength = (segmentCount + ARITY - 1) * segmentLength;
        segmentCount = (arrayLength + segmentLength - 1) / segmentLength;
        segmentCount = segmentCount <= ARITY - 1 ? 1 : segmentCount - (ARITY - 1);
        XorBinaryFuse8 filter = new XorBinaryFuse8(segmentCount, segmentLength);
        filter.addAll(keys);
        return filter;
    }

    private void addAll(long[] keys) {
        int size = keys.length;
        long[] reverseOrder = new long[size + 1];
        byte[] reverseH = new byte[size];
        int reverseOrderPos = 0;

        // the lowest 2 bits are the h index (0, 1, or 2)
        // so we only have 6 bits for counting;
        // but that's sufficient
        byte[] t2count = new byte[arrayLength];
        long[] t2hash = new long[arrayLength];
        int[] alone = new int[arrayLength];
        int hashIndex = 0;
        // the array h0, h1, h2, h0, h1, h2
        int[] h012 = new int[5];
        int blockBits = 1;
        while ((1 << blockBits) < segmentCount) {
            blockBits++;
        }
        int block = 1 << blockBits;
        while (true) {
            reverseOrder[size] = 1;
            int[] startPos = new int[block];
            for (int i = 0; i < 1 << blockBits; i++) {
                startPos[i] = (int) ((long) i * size / block);
            }
            // counting sort

            for (long key : keys) {
                long hash = Hash.hash64(key, seed);
                int segmentIndex = (int) (hash >>> (64 - blockBits));
                // We only overwrite when the hash was zero. Zero hash values
                // may be misplaced (unlikely).
                while (reverseOrder[startPos[segmentIndex]] != 0) {
                    segmentIndex++;
                    segmentIndex &= (1 << blockBits) - 1;
                }
                reverseOrder[startPos[segmentIndex]] = hash;
                startPos[segmentIndex]++;
            }
            byte countMask = 0;
            for (int i = 0; i < size; i++) {
                long hash = reverseOrder[i];
                for (int hi = 0; hi < 3; hi++) {
                    int index = getHashFromHash(hash, hi);
                    t2count[index] += 4;
                    t2count[index] ^= hi;
                    t2hash[index] ^= hash;
                    countMask |= t2count[index];
                }
            }
            startPos = null;
            if (countMask < 0) {
                // we have a possible counter overflow
                // this branch is never taken except if there is a problem in the hash code
                // in which case construction fails
                for(int i = 0; i < fingerprints.length; i++) {
                    fingerprints[i] = (byte)0xFF;
                }
                return;
            }

            reverseOrderPos = 0;
            int alonePos = 0;
            for (int i = 0; i < arrayLength; i++) {
                alone[alonePos] = i;
                int inc = (t2count[i] >> 2) == 1 ? 1 : 0;
                alonePos += inc;
            }

            while (alonePos > 0) {
                alonePos--;
                int index = alone[alonePos];
                if ((t2count[index] >> 2) == 1) {
                    // It is still there!
                    long hash = t2hash[index];
                    byte found = (byte) (t2count[index] & 3);

                    reverseH[reverseOrderPos] = found;
                    reverseOrder[reverseOrderPos] = hash;

                    h012[0] = getHashFromHash(hash, 0);
                    h012[1] = getHashFromHash(hash, 1);
                    h012[2] = getHashFromHash(hash, 2);

                    int index3 = h012[mod3(found + 1)];
                    alone[alonePos] = index3;
                    alonePos += ((t2count[index3] >> 2) == 2 ? 1 : 0);
                    t2count[index3] -= 4;
                    t2count[index3] ^= mod3(found + 1);
                    t2hash[index3] ^= hash;

                    index3 = h012[mod3(found + 2)];
                    alone[alonePos] = index3;
                    alonePos += ((t2count[index3] >> 2) == 2 ? 1 : 0);
                    t2count[index3] -= 4;
                    t2count[index3] ^= mod3(found + 2);
                    t2hash[index3] ^= hash;

                    reverseOrderPos++;
                }
            }

            if (reverseOrderPos == size) {
                break;
            }
            hashIndex++;
            Arrays.fill(t2count, (byte) 0);
            Arrays.fill(t2hash, 0);
            Arrays.fill(reverseOrder, 0);

            if (hashIndex > 100) {
                // if construction doesn't succeed eventually,
                // then there is likely a problem with the hash function
                // let us not crash the system:
                for(int i = 0; i < fingerprints.length; i++) {
                    fingerprints[i] = (byte)0xFF;
                }
                return;
            }
            // use a new random numbers
            seed = Hash.randomSeed();
        }
        alone = null;
        t2count = null;
        t2hash = null;

        for (int i = reverseOrderPos - 1; i >= 0; i--) {
            long hash = reverseOrder[i];
            int found = reverseH[i];
            byte xor2 = fingerprint(hash);
            h012[0] = getHashFromHash(hash, 0);
            h012[1] = getHashFromHash(hash, 1);
            h012[2] = getHashFromHash(hash, 2);
            h012[3] = h012[0];
            h012[4] = h012[1];
            fingerprints[h012[found]] = (byte) (xor2 ^ fingerprints[h012[found + 1]] ^ fingerprints[h012[found + 2]]);
        }
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        byte f = fingerprint(hash);
        int h0 = Hash.reduce((int) (hash >>> 32), segmentCountLength);
        int h1 = h0 + segmentLength;
        int h2 = h1 + segmentLength;
        long hh = hash;
        h1 ^= (int) ((hh >> 18) & segmentLengthMask);
        h2 ^= (int) ((hh) & segmentLengthMask);
        f ^= fingerprints[h0] ^ fingerprints[h1] ^ fingerprints[h2];
        return (f & 0xff) == 0;
    }

    @Override
    public String toString() {
        return "segmentLength " + segmentLength + " segmentCount " + segmentCount;
    }

    int getHashFromHash(long hash, int index) {
        long h = Hash.reduce((int) (hash >>> 32), segmentCountLength);
        // long h = Hash.multiplyHighUnsigned(hash, segmentCountLength);
        h += index * segmentLength;
        // keep the lower 36 bits
        long hh = hash & ((1L << 36) - 1);
        // index 0: right shift by 36; index 1: right shift by 18; index 2: no shift
        h ^= (int) ((hh >>> (36 - 18 * index)) & segmentLengthMask);
        return (int) h;
    }

    private byte fingerprint(long hash) {
        return (byte) hash;
    }

}
