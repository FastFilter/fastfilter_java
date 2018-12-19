package org.fastfilter.bloom;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import org.fastfilter.Filter;
import org.fastfilter.utils.Hash;

/**
 * A succinct counting blocked Bloom filter. The lookup speed is the same as for
 * a blocked Bloom filter, but it support remove operations. Remove and add
 * operations are slower than for a regular blocked Bloom filter.
 *
 * The counter is up to 8 bits per entry, unlike the regular counting Bloom
 * where 4 bits are sufficient. For most entries, the counters for 64 entries
 * are shared in a 64-bit long.
 */
public class SuccinctCountingBlockedBloom implements Filter {

    // Cache line aligned

    // whether to verify the counts
    // this is only needed during debugging
    private static final boolean VERIFY_COUNTS = false;

    // Should match the size of a cache line
    private static final int BITS_PER_BLOCK = 64 * 8;
    private static final int LONGS_PER_BLOCK = BITS_PER_BLOCK / 64;
    private static final int BYTES_PER_BLOCK = BITS_PER_BLOCK / 8;
    private static final int BLOCK_MASK = BITS_PER_BLOCK - 1;

    public static SuccinctCountingBlockedBloom construct(long[] keys, int bitsPerKey) {
        long n = keys.length;
        int k = getBestK(bitsPerKey);
        SuccinctCountingBlockedBloom f = new SuccinctCountingBlockedBloom((int) n, bitsPerKey, k);
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

    private final int k;
    private final int blocks;
    private final long seed;
    private final LongBuffer data;

    // the counter bits
    // the same size as the "data bits" currently
    private final long[] counts;

    private int nextFreeOverflow;
    private final long[] overflow;

    // only allocated and used if VERIFY_COUNTS is set:
    // each byte contains the count for a "data bit"
    private final byte[] realCounts;


    public long getBitCount() {
        return 64L * data.capacity() + 64L * counts.length + 64L * overflow.length;
    }

    SuccinctCountingBlockedBloom(int entryCount, int bitsPerKey, int k) {
        entryCount = Math.max(1, entryCount);
        this.k = k;
        this.seed = Hash.randomSeed();
        long bits = (long) entryCount * bitsPerKey;
        this.blocks = (int) (bits + BITS_PER_BLOCK - 1) / BITS_PER_BLOCK;
        // ByteBuffer.allocateDirect allocation are cache line aligned
        // (regular arrays are aligned to 8 bytes; finding the offset is tricky, and GC could move arrays)
        data = ByteBuffer.allocateDirect((int) (blocks * BYTES_PER_BLOCK)).asLongBuffer();
        // data = ByteBuffer.allocate((int) (blocks * BYTES_PER_BLOCK) + 8);

        int arraySize = (blocks * BYTES_PER_BLOCK) / 8;
        counts = new long[arraySize];
        overflow = new long[100 + arraySize / 100 * 24];
        for (int i = 0; i < overflow.length; i += 8) {
            overflow[i] = i + 8;
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
        int start = Hash.reduce((int) hash, blocks) * LONGS_PER_BLOCK;
        int a = (int) hash;
        int b = (int) (hash >>> 32);
        for (int i = 0; i < k; i++) {
            // ByteBuffer:
            // int index = start + ((a & BLOCK_MASK) >>> 3);
            // byte x = (byte) (data.get(index) | (1 << (a & 7)));
            // data.put(index, x);
            int index = start + ((a & BLOCK_MASK) >>> 6);
            if (VERIFY_COUNTS) {
                realCounts[(index << 6) + (a & 63)]++;
            }
            increment(index, a);
            // long x = data.get(index) | (1L << a);
            // data.put(index, x);
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
        int start = Hash.reduce((int) hash, blocks) * LONGS_PER_BLOCK;
        int a = (int) hash;
        int b = (int) (hash >>> 32);
        for (int i = 0; i < k; i++) {
            int index = start + ((a & BLOCK_MASK) >>> 6);
            if (VERIFY_COUNTS) {
                realCounts[(index << 6) + (a & 63)]--;
            }
            decrement(index, a);
            a += b;
        }
    }

    @Override
    public long cardinality() {
        if (VERIFY_COUNTS) {
            verifyCounts(0, realCounts.length);
        }
        long sum = 0;
        for (int i = 0; i < data.capacity(); i++) {
            sum += data.get(i);
        }
        for(long x : counts) {
            sum += Long.bitCount(x);
        }
        return sum;
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        int start = Hash.reduce((int) hash, blocks) * LONGS_PER_BLOCK;
        int a = (int) hash;
        int b = (int) (hash >>> 32);
        for (int i = 0; i < k; i++) {
            // ByteBuffer:
            // int index = start + ((a & BLOCK_MASK) >>> 3);
            // if (((data.get(index) >>> (a & 7)) & 1) == 0) {
            int index = start + ((a & BLOCK_MASK) >>> 6);
            if (((data.get(index) >>> a) & 1) == 0) {
                return false;
            }
            a += b;
        }
        return true;
    }

    private void increment(int group, int x) {
        long m = data.get(group);
        long d = (m >>> x) & 1;
        long c = counts[group];
        if ((c & 0xc000000000000000L) != 0) {
            // an overflow entry, or overflowing now
            int index;
            if ((c & 0x8000000000000000L) == 0) {
                // convert to an overflow entry
                index = allocateOverflow();
                // convert to a pointer
                for (int i = 0; i < 64; i++) {
                    int n = readCount((group << 6) + i);
                    overflow[index + i / 8] += n * getBit(i);
                }
                long count = 64;
                c = 0x8000000000000000L | (count << 32) | index;
            } else {
                // already
                index = (int) (c & 0x0fffffff);
                c += 1L << 32;
            }
            counts[group] = c;
            int bitIndex = x & 63;
            overflow[index + bitIndex / 8] += getBit(bitIndex);
            data.put(group, data.get(group) | (1L << x));
            return;
        }
        data.put(group, data.get(group) | (1L << x));
        int bitsBefore = Long.bitCount(m & (-1L >>> (63 - x)));
        int before = Select.selectInLong((c << 1) | 1, bitsBefore);
        int insertAt = before - (int) d;
        long mask = (1L << insertAt) - 1;
        long left = c & ~mask;
        long right = c & mask;
        c = (left << 1) | ((1 ^ d) << insertAt) | right;
        counts[group] = c;
    }

    private int allocateOverflow() {
        int result = nextFreeOverflow;
        nextFreeOverflow = (int) overflow[result];
        for (int i = 0; i < 8; i++) {
            overflow[result + i] = 0;
        }
        return result;
    }

    private void decrement(int group, int x) {
        long m = data.get(group);
        long c = counts[group];
        if ((c & 0x8000000000000000L) != 0) {
            // an overflow entry
            int count = (int) (c >>> 32) & 0x0fffffff;
            c -= 1L << 32;
            counts[group] = c;
            int index = (int) (c & 0x0fffffff);
            int bitIndex = x & 63;
            long n = overflow[index + bitIndex / 8];
            overflow[index + bitIndex / 8] = n - getBit(bitIndex);
            n >>>= 8 * (bitIndex & 7);
            if ((n & 0xff) == 1) {
                data.put(group, data.get(group) & ~(1L << x));
            }
            if (count < 64) {
                // convert back to an inline entry, and free up the overflow entry
                long c2 = 0;
                for(int j = 63; j >= 0; j--) {
                    int cj = (int) ((overflow[index + j / 8] >>> (8 * j)) & 0xff);
                    if (cj > 0) {
                        c2 = ((c2 << 1) | 1) << (cj - 1);
                    }
                }
                counts[group] = c2;
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
        counts[group] = left | right;
        long removed = (c >> removeAt) & 1;
        // possibly reset the data bit
        data.put(group, m & ~(removed << x));
    }

    private void freeOverflow(int index) {
        overflow[index] = nextFreeOverflow;
        nextFreeOverflow = index;
    }

    private static long getBit(int index) {
        return 1L << (index * 8);
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

    private int readCount(int x) {
        int group = x >>> 6;
        long m = data.get(group);
        long d = (m >>> x) & 1;
        if (d == 0) {
            return 0;
        }
        long c = counts[group];
        if ((c & 0x8000000000000000L) != 0) {
            int index = (int) (c & 0x0fffffff);
            int bitIndex = x & 63;
            long n = overflow[index + bitIndex / 8];
            n >>>= 8 * (bitIndex & 7);
            return (int) (n & 0xff);
        }
        int bitsBefore = Long.bitCount(m & (-1L >>> (63 - x)));
        int bitPos = Select.selectInLong(c, bitsBefore - 1);
        long y = ((c << (63 - bitPos)) << 1) | (1L << (63 - bitPos));
        return Long.numberOfLeadingZeros(y) + 1;
    }

}
