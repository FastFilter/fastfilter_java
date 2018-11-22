package org.fastfilter.bloom;

import org.fastfilter.Filter;
import org.fastfilter.utils.Hash;

/**
 * A succinct counting Bloom filter.
 * It uses almost half the space of a regular counting Bloom filter,
 * and lookup is faster (exactly as fast as with a regular Bloom filter).
 *
 * Remove isn't implemented yet.
 * Adding entries is relatively slow (about 20 times slower than lookup).
 */
public class SuccintCountingBloom implements Filter {

    // whether to verify the counts
    // this is only needed during debugging
    private static final boolean VERIFY_COUNTS = false;

    public static SuccintCountingBloom construct(long[] keys, double bitsPerKey) {
        long n = keys.length;
        int k = getBestK(bitsPerKey);
        SuccintCountingBloom f = new SuccintCountingBloom((int) n, bitsPerKey, k);
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

    // "continue bits":
    // whether a 64 bit block continues for 64 more bits
    // (about 1% of these are set if the filter is full)
    // it might not be needed if blocks are larger than 64 bit
    private final BitField cont;

    // only allocated and used if VERIFY_COUNTS is set:
    // each byte contains the count for a "data bit"
    private final byte[] realCounts;

    public long getBitCount() {
        return data.getBitCount() + counts.getBitCount() + cont.getBitCount();
    }

    SuccintCountingBloom(int entryCount, double bitsPerKey, int k) {
        entryCount = Math.max(1, entryCount);
        this.k = k;
        this.seed = Hash.randomSeed();
        long bits = (long) (entryCount * bitsPerKey);
        arraySize = (int) ((bits + 63) / 64);
        data = new BitField(64 * (arraySize + 10));
        counts = new BitField(64 * (arraySize + 10));
        cont = new BitField(arraySize + 10);
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
            int index = Hash.reduce(a, arraySize) * 64 + (a & 63);
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
            int index = Hash.reduce(a, arraySize) * 64 + (a & 63);
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
        return data.cardinality() + counts.cardinality() + cont.cardinality();
    }

    private void increment(int x) {
        int group = x >>> 6;
        long m = data.getLong(group);
        long d = (m >>> x) & 1;
        data.set(x);
        int bitsBefore = Long.bitCount(m & (-1L >>> (63 - x)));
        while (group > 0 && cont.get(group - 1) != 0) {
            group--;
            bitsBefore += Long.bitCount(data.getLong(group));
        }
        int before = counts.selectInLong(group, bitsBefore) + 1;
        int insertAt = before - (int) d;
        int insertGroup = insertAt >>> 6;
        long last = counts.insertInLong(insertAt, 1 ^ d);
        if (last != 0 || cont.get(group) != 0 || insertGroup > group) {
            cont.set(group);
            group = insertGroup;
            while (last != 0 || cont.get(group) != 0) {
                cont.set(group);
                group++;
                before = group * 64;
                last = counts.insertInLong(before, last);
            }
        }
    }

    private void decrement(int x) {
        int group = x >>> 6;
        long m = data.getLong(group);
        int bitsBefore = Long.bitCount(m & (-1L >>> (63 - x)));
        while (group > 0 && cont.get(group - 1) != 0) {
            group--;
            bitsBefore += Long.bitCount(data.getLong(group));
        }
        int firstGroup = group;
        int before = counts.selectInLong(group, bitsBefore) - 64 * group;
        int removeAt = Math.max(0, before - 1) + 64 * group;
        int endGroup = group;
        while (cont.get(endGroup) != 0) {
            endGroup++;
        }
        int lastGroup = endGroup;
        long last = 0;
        int first = 64 * endGroup;
        while (endGroup >= group) {
            if (first > removeAt) {
                last = counts.removeFromLong(first, last);
            } else {
                last = counts.removeFromLong(removeAt, last);
                break;
            }
            first -= 64;
            endGroup--;
        }
        if (last == 1) {
            data.clear(x);
        }
        if (firstGroup != lastGroup) {
            if (lastGroup - firstGroup > 10) {
                System.out.println("? " + firstGroup + ".. " + lastGroup);
            }
            reInsert(firstGroup << 6, (lastGroup + 1) << 6);
        }
        if(test) {
            verifyCounts(x - 1000, x + 1000);
        }
    }

    private void reInsert(int from, int to) {
        int[] oldCounts = new int[to - from];
        int sum = 0;
        int groupCount = 0;
        boolean rewrite = false;
        for (int i = from; i < to; i++) {
            int x = readCount(i);
            if (x > 0) {
                sum += x;
                oldCounts[i - from] = x;
            }
            if ((i & 63) == 63) {
                groupCount++;
                if (i < to - 1) {
                    if (sum < groupCount * 64) {
                        rewrite = true;
                    }
                }
            }
        }
        if (!rewrite) {
            return;
        }
        for (int i = from; i < to; i += 64) {
            int bits = 0;
            for (int j = 0; j < 64; j++) {
                if (oldCounts[j + i - from] > 0) {
                    bits++;
                }
            }
            counts.setLong(i >>> 6, (1L << bits) - 1);
            cont.clear(i >>> 6);
        }
        for (int i = from; i < to; i += 64) {
            for (int j = 0; j < 64; j++) {
                int n = oldCounts[j + i - from] - 1;
                for (int k = 0; k < n; k++) {
                    increment(i + j);
                }
            }
        }
        verifyCounts(from, to);
    }

    private int readCount(int x) {
        long d = data.get(x);
        if (d == 0) {
            return 0;
        }
        int group = x / 64;
        while (group > 0 && cont.get(group - 1) != 0) {
            group--;
        }
        int bitsBefore = data.getBitCount(group * 64, x);
        int before = counts.select(group * 64, bitsBefore);
        int after = counts.select(before + 1, 1);
        return after - before;
    }
    boolean test;

    private void verifyCounts(int from, int to) {
        if (!VERIFY_COUNTS) {
            return;
        }
        test=true;
        for (int i = Math.max(0, from); i < to && i < realCounts.length; i++) {
            if (readCount(i) != realCounts[i]) {
                throw new AssertionError(" at " + i + " got " + readCount(i) + " expected " + realCounts[i]);
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

        public long removeFromLong(int index, long topBit) {
            long x = data[index >>> 6];
            long mask = (1L << index) - 1;
            long left = (x >>> 1) & ~mask;
            long right= x & mask;
            data[index >>> 6] = (topBit << 63) | left | right;
            return (x >> index) & 1;
        }

        public long insertInLong(int index, long bit) {
            long x = data[index >>> 6];
            long mask = (1L << index) - 1;
            long left = x & ~mask;
            long right= x & mask;
            long result = left >>> 63;
            data[index >>> 6] = (left << 1) | (bit << index) | right;
            return result;
            /*
            int to = index / 64 * 64 + 64;
            long old = get(index);
            if (bit == 0) {
                clear(index);
            } else {
                set(index);
            }
            index++;
            while (index < to) {
                long x = get(index);
                if (old == 0) {
                    clear(index);
                } else {
                    set(index);
                }
                old = x;
                index++;
            }
            return old;
            */
        }

        void clear(int index) {
            data[index >>> 6] &= ~(1L << index);
        }

        public int selectInLong(int fromLongIndex, int bitCount) {
            if (bitCount == 0) {
                return fromLongIndex * 64 - 1;
            }
            while (true) {
                long x = data[fromLongIndex];
                int bc = Long.bitCount(x);
                if (bitCount <= bc) {
                    return fromLongIndex * 64 + Select.selectInLong(x, bitCount - 1);
                }
                bitCount -= bc;
                fromLongIndex++;
            }
        }

        public int select(int from, int bitCount) {
            for (int i = from; bitCount > 0; i++) {
                if (get(i) != 0) {
                    bitCount--;
                    if (bitCount == 0) {
                        return i;
                    }
                }
            }
            return from - 1;
        }

        public void setLong(int longIndex, long x) {
            data[longIndex] = x;
        }

        public long getLong(int longIndex) {
            return data[longIndex];
        }

        public int getBitCount(int from, int to) {
            int result = 0;
            for (int i = from; i < to; i++) {
                if (get(i) != 0) {
                    result++;
                }
            }
            return result;
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
