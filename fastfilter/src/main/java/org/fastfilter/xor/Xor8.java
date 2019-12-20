package org.fastfilter.xor;

import org.fastfilter.Filter;
import org.fastfilter.utils.Hash;

import java.io.*;

/**
 * The Xor Filter, a new algorithm that can replace a bloom filter.
 *
 * It needs 1.23 log(1/fpp) bits per key. It is related to the BDZ algorithm [1]
 * (a minimal perfect hash function algorithm).
 *
 * [1] paper: Simple and Space-Efficient Minimal Perfect Hash Functions -
 * http://cmph.sourceforge.net/papers/wads07.pdf
 */
public class Xor8 implements Filter {

    private static final int BITS_PER_FINGERPRINT = 8;
    private static final int HASHES = 3;
    private static final int FACTOR_TIMES_100 = 123;
    private final int size;
    private final int arrayLength;
    private final int blockLength;
    private long seed;
    private byte[] fingerprints;
    private final int bitCount;

    public long getBitCount() {
        return bitCount;
    }

    private static int getArrayLength(int size) {
        return (int) (HASHES + (long) FACTOR_TIMES_100 * size / 100);
    }

    public static Xor8 construct(long[] keys) {
        return new Xor8(keys);
    }

    public Xor8(long[] keys) {
        this.size = keys.length;
        arrayLength = getArrayLength(size);
        bitCount = arrayLength * BITS_PER_FINGERPRINT;
        blockLength = arrayLength / HASHES;
        int m = arrayLength;
        long[] reverseOrder = new long[size];
        byte[] reverseH = new byte[size];
        int reverseOrderPos;
        long seed;
        do {
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
            int[][] alone = new int[HASHES][blockLength];
            int[] alonePos = new int[HASHES];
            for (int nextAlone = 0; nextAlone < HASHES; nextAlone++) {
                for (int i = 0; i < blockLength; i++) {
                    if (t2count[nextAlone * blockLength + i] == 1) {
                        alone[nextAlone][alonePos[nextAlone]++] = nextAlone * blockLength + i;
                    }
                }
            }
            int found = -1;
            while (true) {
                int i = -1;
                for (int hi = 0; hi < HASHES; hi++) {
                    if (alonePos[hi] > 0) {
                        i = alone[hi][--alonePos[hi]];
                        found = hi;
                        break;
                    }
                }
                if (i == -1) {
                    // no entry found
                    break;
                }
                if (t2count[i] <= 0) {
                    continue;
                }
                long k = t2[i];
                if (t2count[i] != 1) {
                    throw new AssertionError();
                }
                --t2count[i];
                for (int hi = 0; hi < HASHES; hi++) {
                    if (hi != found) {
                        int h = getHash(k, seed, hi);
                        int newCount = --t2count[h];
                        if (newCount == 1) {
                            alone[hi][alonePos[hi]++] = h;
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
        int r0 = (int) hash;
        int r1 = (int) Long.rotateLeft(hash, 21);
        int r2 = (int) Long.rotateLeft(hash, 42);
        int h0 = Hash.reduce(r0, blockLength);
        int h1 = Hash.reduce(r1, blockLength) + blockLength;
        int h2 = Hash.reduce(r2, blockLength) + 2 * blockLength;
        f ^= fingerprints[h0] ^ fingerprints[h1] ^ fingerprints[h2];
        return (f & 0xff) == 0;
    }

    private int getHash(long key, long seed, int index) {
        long r = Long.rotateLeft(Hash.hash64(key, seed), 21 * index);
        r = Hash.reduce((int) r, blockLength);
        r = r + index * blockLength;
        return (int) r;
    }

    private int fingerprint(long hash) {
        return (int) (hash & ((1 << BITS_PER_FINGERPRINT) - 1));
    }

    public byte[] getData() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(out);
            d.writeInt(size);
            d.writeLong(seed);
            d.write(fingerprints);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Xor8(InputStream in) {
        try {
            DataInputStream din = new DataInputStream(in);
            size = din.readInt();
            arrayLength = getArrayLength(size);
            bitCount = arrayLength * BITS_PER_FINGERPRINT;
            blockLength = arrayLength / HASHES;
            seed = din.readLong();
            fingerprints = new byte[arrayLength];
            din.readFully(fingerprints);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
