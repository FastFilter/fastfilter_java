package org.fastfilter.bloom;

import java.math.BigInteger;

import org.fastfilter.Filter;
import org.fastfilter.utils.Hash;

/**
 * A succinct counting Bloom filter.
 *
 * Compared to a regular Bloom filter, lookup speed is exactly the same (the
 * data structure for lookup is identical), but it supports removing entries. It
 * needs a bit more than twice the space of a regular Bloom filter.
 *
 * Compared to a counting Bloom filter, lookup speed is much faster, and space
 * usage is about half. However, adding and removing entries is about half as
 * fast.
 */
public class SuccinctCountingBloom implements Filter {

    // whether to verify the counts
    // this is only needed during debugging
    private static final boolean VERIFY_COUNTS = false;

    public static SuccinctCountingBloom construct(long[] keys, double bitsPerKey) {
        long n = keys.length;
        int k = getBestK(bitsPerKey);
        SuccinctCountingBloom f = new SuccinctCountingBloom((int) n, bitsPerKey, k);
        for(long x : keys) {
            f.add(x);
        }
        if (VERIFY_COUNTS) {
            f.verifyCounts(0, f.realCounts.length);
        }
        return f;
    }

    private static int getBestK(double bitsPerKey) {
        return Math.max(1, (int) Math.round(bitsPerKey * Math.log(2)));
    }

    // the number of data bits set for each key
    private final int k;

    // the random seed
    private final long seed;

    // the logical array size (the actual data and counter arrays are slightly
    // larger due to the continue bits)
    private final int arraySize;

    // the "data bits" exactly as in a regular Bloom filter
    private final BitField data;

    // the counter bits
    // the same size as the "data bits" currently
    private final BitField counts;

    private int nextFreeOverflow;
    private final long[] overflow;

    // only allocated and used if VERIFY_COUNTS is set:
    // each byte contains the count for a "data bit"
    private final byte[] realCounts;

    public long getBitCount() {
        return data.getBitCount() + counts.getBitCount() + 64 * overflow.length;
    }

    SuccinctCountingBloom(int entryCount, double bitsPerKey, int k) {
        entryCount = Math.max(1, entryCount);
        this.k = k;
        this.seed = Hash.randomSeed();
        long bits = (long) (entryCount * bitsPerKey);
        arraySize = (int) ((bits + 63) / 64);
        data = new BitField(64 * (arraySize + 10));
        counts = new BitField(64 * (arraySize + 10));
        overflow = new long[100 + arraySize / 100 * 12];
        for (int i = 0; i < overflow.length; i += 4) {
            overflow[i] = i + 4;
        }
        realCounts = VERIFY_COUNTS ? new byte[arraySize * 64] : null;
    }

    @Override
    public boolean supportsAdd() {
        return true;
    }

    @Override
    public void add(long key) {
        long hash = Hash.hash64(key, seed);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        for (int i = 0; i < k; i++) {
            int index = (Hash.reduce(a, arraySize) << 6) + (a & 63);
            if (VERIFY_COUNTS) {
                realCounts[index]++;
            }
            increment(index);
            a += b;
        }
    }

    @Override
    public boolean supportsRemove() {
        return true;
    }

    @Override
    public void remove(long key) {
        long hash = Hash.hash64(key, seed);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        for (int i = 0; i < k; i++) {
            int index = (Hash.reduce(a, arraySize) << 6) + (a & 63);
            if (VERIFY_COUNTS) {
                realCounts[index]--;
            }
            decrement(index);
            a += b;
        }
    }

    @Override
    public long cardinality() {
        if (VERIFY_COUNTS) {
            verifyCounts(0, realCounts.length);
        }
        return data.cardinality() + counts.cardinality();
    }

    private void increment(int x) {
        int group = x >>> 6;
        long m = data.getLong(group);
        long d = (m >>> x) & 1;
        long c = counts.getLong(group);
        if ((c & 0xc000000000000000L) != 0) {
            // an overflow entry, or overflowing now
            int index;
            if ((c & 0x8000000000000000L) == 0) {
                // convert to an overflow entry
                index = allocateOverflow();
                // convert to a pointer
                for (int i = 0; i < 64; i++) {
                    int n = readCount((group << 6) + i);
                    overflow[index + i / 16] += n * getBit(i);
                }
                long count = 64;
                c = 0x8000000000000000L | (count << 32) | index;
                counts.setLong(group, c);
            } else {
                // already
                index = (int) (c & 0x0fffffff);
                c += 1L << 32;
                counts.setLong(group, c);
            }
            int bitIndex = x & 63;
            overflow[index + bitIndex / 16] += getBit(bitIndex);
            data.set(x);
            return;
        }
        data.set(x);
        int bitsBefore = Long.bitCount(m & (-1L >>> (63 - x)));
        int before = Select.selectInLong((c << 1) | 1, bitsBefore);
        int insertAt = before - (int) d;
        long mask = (1L << insertAt) - 1;
        long left = c & ~mask;
        long right = c & mask;
        c = (left << 1) | ((1 ^ d) << insertAt) | right;
        counts.setLong(group, c);
    }

    private int allocateOverflow() {
        int result = nextFreeOverflow;
        nextFreeOverflow = (int) overflow[result];
        overflow[result] = 0;
        overflow[result + 1] = 0;
        overflow[result + 2] = 0;
        overflow[result + 3] = 0;
        return result;
    }

    private void freeOverflow(int index) {
        overflow[index] = nextFreeOverflow;
        nextFreeOverflow = index;
    }

    private static long getBit(int index) {
        return 1L << (index * 4);
    }

    private void decrement(int x) {
        int group = x >>> 6;
        long m = data.getLong(group);
        long c = counts.getLong(group);
        if ((c & 0x8000000000000000L) != 0) {
            // an overflow entry
            int count = (int) (c >>> 32) & 0x0fffffff;
            c -= 1L << 32;
            counts.setLong(group, c);
            int index = (int) (c & 0x0fffffff);
            int bitIndex = x & 63;
            long n = overflow[index + bitIndex / 16];
            overflow[index + bitIndex / 16] = n - getBit(bitIndex);
            n >>>= 4 * (bitIndex & 0xf);
            if ((n & 15) == 1) {
                data.clear(x);
            }
            if (count < 64) {
                // convert back to an inline entry, and free up the overflow entry
                long c2 = 0;
                for(int j = 63; j >= 0; j--) {
                    int cj = (int) ((overflow[index + j / 16] >>> (4 * j)) & 0xf);
                    if (cj > 0) {
                        c2 = ((c2 << 1) | 1) << (cj - 1);
                    }
                }
                counts.setLong(group,  c2);
                freeOverflow(index);
            }
            return;
        }
        int bitsBefore = Long.bitCount(m & (-1L >>> (63 - x)));
        int before = Select.selectInLong((c << 1) | 1, bitsBefore) - 1;
        int removeAt = Math.max(0, before - 1);
        // remove the bit from the counter
        long mask = (1L << removeAt) - 1;
        long left = (c >>> 1) & ~mask;
        long right= c & mask;
        counts.setLong(group, left | right);
        long removed = (c >> removeAt) & 1;
        // possibly reset the data bit
        data.setLong(group, m & ~(removed << x));
    }

    private int readCount(int x) {
        int group = x >>> 6;
        long m = data.getLong(group);
        long d = (m >>> x) & 1;
        if (d == 0) {
            return 0;
        }
        long c = counts.getLong(group);
        if ((c & 0x8000000000000000L) != 0) {
            int index = (int) (c & 0x0fffffff);
            int bitIndex = x & 63;
            long n = overflow[index + bitIndex / 16];
            n >>>= 4 * (bitIndex & 0xf);
            return (int) (n & 15);
        }
        int bitsBefore = Long.bitCount(m & (-1L >>> (63 - x)));
        int bitPos = Select.selectInLong(c, bitsBefore - 1);
        long y = ((c << (63 - bitPos)) << 1) | (1L << (63 - bitPos));
        return Long.numberOfLeadingZeros(y) + 1;
    }

    private void verifyCounts(int from, int to) {
        if (!VERIFY_COUNTS) {
            return;
        }
        for (int i = Math.max(0, from); i < to && i < realCounts.length; i++) {
            if (readCount(i) != realCounts[i]) {
                throw new AssertionError(" at " + i + " " + (i & 63) + " got " + readCount(i) + " expected " + realCounts[i]);
            }
        }
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        for (int i = 0; i < k; i++) {
            int index = Hash.reduce(a, arraySize) * 64 + (a & 63);
            if (data.get(index) == 0) {
                return false;
            }
            a += b;
        }
        return true;
    }

    public static class BitField {

        private final long[] data;

        BitField(int bitCount) {
            data = new long[(bitCount + 63) / 64];
        }

        public long cardinality() {
            long sum = 0;
            for(long x : data) {
                sum += Long.bitCount(x);
            }
            return sum;
        }

        void clear(int index) {
            data[index >>> 6] &= ~(1L << index);
        }

        public void setLong(int longIndex, long x) {
            data[longIndex] = x;
        }

        public long getLong(int longIndex) {
            return data[longIndex];
        }

        public long getBitCount() {
            return data.length << 6;
        }

        long get(int index) {
            return (data[index >>> 6] >> index) & 1;
        }

        void set(int index) {
            data[index >>> 6] |= 1L << index;
        }

        public String toString() {
            StringBuilder buff = new StringBuilder();
            for (int i = 0; i < data.length * 64; i++) {
                if ((i & 63) == 0) {
                    if (data[i >>> 6] == 0) {
                        i += 63;
                    } else {
                        buff.append("\n" + i + ":");
                    }
                } else {
                    if (get(i) == 0) {
                        buff.append('0');
                    } else {
                        buff.append('1');
                    }
                }
            }
            return buff.toString();
        }

    }

}
