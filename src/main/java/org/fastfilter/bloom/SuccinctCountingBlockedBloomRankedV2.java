package org.fastfilter.bloom;

import org.fastfilter.Filter;
import org.fastfilter.utils.Hash;

/**
 * A succinct counting blocked Bloom filter. The lookup speed is the same as for
 * a blocked Bloom filter, but it support remove operations. Remove and add
 * operations are slower than for a regular blocked Bloom filter, but if needed
 * they can be performed asynchronously.
 *
 * Unlike the regular counting Bloom that typically uses 4 bits per entry, for
 * most entries, the counters for 64 entries are shared in a 64-bit long. In
 * case of overflow, the counter is 8 bits per entry, plus some overhead. This
 * is only needed if the filter (locally) has a high load.
 */
public class SuccinctCountingBlockedBloomRankedV2 implements Filter {

    // whether to verify the counts
    // this is only needed during debugging
    private static final boolean VERIFY_COUNTS = false;

    // static boolean debugPrint;

    public static SuccinctCountingBlockedBloomRankedV2 construct(long[] keys, int bitsPerKey) {
        long n = keys.length;
        int k = getBestK(bitsPerKey);
        SuccinctCountingBlockedBloomRankedV2 f = new SuccinctCountingBlockedBloomRankedV2((int) n, bitsPerKey, k);
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

    private final int buckets;
    private final long seed;
    private final long[] data;

    // the counter bits
    // the same size as the "data bits" currently
    private final long[] counts;

    private int nextFreeOverflow;
    private final long[] overflow;

    // only allocated and used if VERIFY_COUNTS is set:
    // each byte contains the count for a "data bit"
    private final byte[] realCounts;

    public long getBitCount() {
        return 64L * data.length + 64L * counts.length + 64L * overflow.length;
    }

    SuccinctCountingBlockedBloomRankedV2(int entryCount, int bitsPerKey, int k) {
        entryCount = Math.max(1, entryCount);
        this.seed = Hash.randomSeed();
        long bits = (long) entryCount * bitsPerKey;
        this.buckets = (int) bits / 64;
        int arrayLength = (int) (buckets + 16);
        data = new long[arrayLength];
        counts = new long[arrayLength];
        overflow = new long[100 + arrayLength * 10 / 100];
        for (int i = 0; i < overflow.length; i += 8) {
            overflow[i] = i + 8;
        }
        realCounts = VERIFY_COUNTS ? new byte[arrayLength * 64] : null;
    }

    @Override
    public boolean supportsAdd() {
        return true;
    }

    @Override
    public void add(long key) {
        long hash = Hash.hash64(key, seed);
        int start = Hash.reduce((int) hash, buckets);
        hash = hash ^ Long.rotateLeft(hash, 32);
        int a1 = (int) (hash & 63);
        int a2 = (int) ((hash >> 6) & 63);
        increment(start, a1);
        if (a2 != a1) {
            increment(start, a2);
        }
        int second = start + 1 + (int) (hash >>> 60);
        int a3 = (int) ((hash >> 12) & 63);
        int a4 = (int) ((hash >> 18) & 63);
        increment(second, a3);
        if (a4 != a3) {
            increment(second, a4);
        }
    }

    @Override
    public boolean supportsRemove() {
        return true;
    }

    @Override
    public void remove(long key) {
        long hash = Hash.hash64(key, seed);
        int start = Hash.reduce((int) hash, buckets);
        hash = hash ^ Long.rotateLeft(hash, 32);
        int a1 = (int) (hash & 63);
        int a2 = (int) ((hash >> 6) & 63);
        decrement(start, a1);
        if (a2 != a1) {
            decrement(start, a2);
        }
        int second = start + 1 + (int) (hash >>> 60);
        int a3 = (int) ((hash >> 12) & 63);
        int a4 = (int) ((hash >> 18) & 63);
        decrement(second, a3);
        if (a4 != a3) {
            decrement(second, a4);
        }
    }

    @Override
    public long cardinality() {
        if (VERIFY_COUNTS) {
            verifyCounts(0, realCounts.length);
        }
        long sum = 0;
        for(long x : data) {
            sum += Long.bitCount(x);
        }
        for(long x : counts) {
            sum += Long.bitCount(x);
        }
        return sum;
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        int start = Hash.reduce((int) hash, buckets);
        hash = hash ^ Long.rotateLeft(hash, 32);
        long a = data[start];
        long b = data[start + 1 + (int) (hash >>> 60)];
        long m1 = (1L << hash) | (1L << (hash >> 6));
        long m2 = (1L << (hash >> 12)) | (1L << (hash >> 18));
        return ((m1 & a) == m1) && ((m2 & b) == m2);
    }

    private void increment(int group, int x) {
        if (VERIFY_COUNTS) {
            realCounts[(group << 6) + (x & 63)]++;
        }
        long m = data[group];
        long d = (m >>> x) & 1;
        long c = counts[group];

        if ((c & 0x8000000000000000L) != 0) {
            // already an overflow
            int index = (int) (c & 0x0fffffff);
            c += 1L << 32;
            counts[group] = c;
            int bitIndex = x & 63;
            overflow[index + bitIndex / 8] += getBit(bitIndex);
            data[group] |= (1L << x);
            return;
        }
        if (d == 0 && c == 0) {
            data[group] |= 1L << x;
            return;
        }

        // number of bits in the counter at this level
        int bitsSet = Long.bitCount(m);
        // if(debugPrint) debugPrint("insert ----------------------");
        // if(debugPrint) debugPrint(bitsSet + ": bitsSet");

        // number of bits before the bit to test (at the current level)
        int bitsBefore = x == 0 ? 0 : Long.bitCount(m << (64 - x));
        // if(debugPrint) debugPrint(bitsBefore + ": bitsBefore");

        // the point where the bit should be inserted (insert 0), or set
        int insertAt = bitsBefore;
        // if(debugPrint) debugPrint(insertAt + ": insertAt");

        if (d == 1) {
            // space was already inserted
            int startLevel = 0;
            long bitsForLevel;
            while (true) {
                // the mask for the current level
                long levelMask = ((1L << bitsSet) - 1) << startLevel;
                // if(debugPrint) debugPrint(getBitsNumber(levelMask) + ": levelMask");
                // the relevant bits for the current level
                bitsForLevel = c & levelMask;
                // if(debugPrint) debugPrint(getBitsNumber(bitsForLevel) + ": bitsForLevel");
                if (((c >>> insertAt) & 1) == 0) {
                    break;
                }
                // if(debugPrint) debugPrint("bit is already set");
                // at this level, the bit is already set: loop until it's not set
                startLevel += bitsSet;
                if (startLevel >= 64) {
                    // convert to overflow later
                    insertAt = 64;
                    break;
                }
                // if(debugPrint) debugPrint("startLevel=" + startLevel);
                bitsSet = Long.bitCount(bitsForLevel);
                // if(debugPrint) debugPrint("bitsSet=" + bitsSet);
                bitsBefore = insertAt == 0 ? 0 : Long.bitCount(bitsForLevel << (64 - insertAt));
                // if(debugPrint) debugPrint("bitsBefore=" + bitsBefore);
                insertAt = startLevel + bitsBefore;
                // if(debugPrint) debugPrint("insertAt=" + insertAt);
            }
            // if(debugPrint) debugPrint("bit is not yet set, set it");
            // bit is not set: set it, and insert a space in the next level if needed
            c |= 1L << insertAt;
            // if(debugPrint) debugPrint(getBitsNumber(c) + ": c");
            int bitsBeforeLevel = insertAt == 0 ? 0 : Long.bitCount(bitsForLevel << (64 - insertAt));
            // if(debugPrint) debugPrint("bitsBeforeLevel=" + bitsBeforeLevel);
            int bitsSetLevel = Long.bitCount(bitsForLevel);
            // if(debugPrint) debugPrint("bitsSetLevel=" + bitsSetLevel);
            insertAt = startLevel + bitsSet + bitsBeforeLevel;
            bitsSet = bitsSetLevel;
            // if(debugPrint) debugPrint("insertAt=" + insertAt);
        }
        // insert a space
        long mask = (1L << insertAt) - 1;
        // if(debugPrint) debugPrint(getBitsNumber(mask) + ": mask");
        long left = c & ~mask;
        // if(debugPrint) debugPrint(getBitsNumber(left) + ": left");
        long right = c & mask;
        // if(debugPrint) debugPrint(getBitsNumber(right) + ": right");
        c = (left << 1) | right;
        // if(debugPrint) debugPrint(getBitsNumber(c) + ": c");

        if (insertAt >= 64 || (c & 0x8000000000000000L) != 0) {
            // if(debugPrint) debugPrint("convert to overflow");
            // an overflow entry, or overflowing now
            int index = allocateOverflow();
            // convert to a pointer
            long count = 1;
            for (int i = 0; i < 64; i++) {
                int n = readCount((group << 6) + i);
                count += n;
                overflow[index + i / 8] += n * getBit(i);
            }
            c = 0x8000000000000000L | (count << 32) | index;
            // if(debugPrint) debugPrint("convert to overflow: " + getBitsNumber(c));
            data[group] |= 1L << x;
            counts[group] = c;
            int bitIndex = x & 63;
            overflow[index + bitIndex / 8] += getBit(bitIndex);

            if (VERIFY_COUNTS) {
                verifyCounts((group << 6), ((group + 1) << 6));
            }

            return;
        }


        data[group] |= 1L << x;
        counts[group] = c;

        if (VERIFY_COUNTS) {
            verifyCounts((group << 6), ((group + 1) << 6));
        }

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
        if (VERIFY_COUNTS) {
            realCounts[(group << 6) + (x & 63)]--;
        }
        long m = data[group];
        long c = counts[group];
        if ((c & 0x8000000000000000L) != 0) {
            // an overflow entry
            // if(debugPrint) debugPrint("overflow ------------------");

            int count = (int) (c >>> 32) & 0x0fffffff;
            c -= 1L << 32;
            counts[group] = c;
            int index = (int) (c & 0x0fffffff);
            int bitIndex = x & 63;
            long n = overflow[index + bitIndex / 8];
            overflow[index + bitIndex / 8] = n - getBit(bitIndex);
            n >>>= 8 * (bitIndex & 7);
            if ((n & 0xff) == 1) {
                data[group] &= ~(1L << x);
            }
            if (count < 64) {
                // convert back to an inline entry, and free up the overflow entry
                int count2 = 0;
                int[] temp = new int[64];
                for(int j = 63; j >= 0; j--) {
                    int cj = (int) ((overflow[index + j / 8] >>> (8 * j)) & 0xff);
                    temp[j] = cj;
                    count2 += cj;
                }
                long c2 = 0;
                int off = 0;
                while (count2 > 0) {
                    for (int i = 0; i < 64; i++) {
                        int t = temp[i];
                        if (t > 0) {
                            temp[i]--;
                            count2--;
                            c2 |= ((t > 1) ? 1L : 0L) << off;
                            off++;
                        }
                    }
                }
                counts[group] = c2;
                freeOverflow(index);
                if (VERIFY_COUNTS) {
                    verifyCounts((group << 6), ((group + 1) << 6));
                }
            }
            return;
        }

        // if(debugPrint) debugPrint("---------------- decrement");
        // if(debugPrint) debugPrint(getBitsNumber(m) + ": m");
        // if(debugPrint) debugPrint(getBitsNumber(c) + ": c");
        // if(debugPrint) debugPrint((x & 63) + ": x");

        // number of bits in the counter at this level
        int bitsSet = Long.bitCount(m);
        // if(debugPrint) debugPrint(bitsSet + ": bitsSet");

        // number of bits before the bit to test (at the current level)
        int bitsBefore = x == 0 ? 0 : Long.bitCount(m << (64 - x));
        // if(debugPrint) debugPrint(bitsBefore + ": bitsBefore");

        // the point where the bit should be removed (remove 0), or reset
        int removeAt = bitsBefore;
        // if(debugPrint) debugPrint(removeAt + ": removeAt");

        long d = (c >>> bitsBefore) & 1;
        // if(debugPrint) debugPrint(d + ": d");

        if (d == 1) {
            // bit is set: loop until it's not set
            int startLevel = 0;
            long bitsForLevel;
            int resetAt = removeAt;
            while (true) {
                // the mask for the current level
                long levelMask = ((1L << bitsSet) - 1) << startLevel;
                // if(debugPrint) debugPrint(getBitsNumber(levelMask) + ": levelMask");

                // the relevant bits for the current level
                bitsForLevel = c & levelMask;
                // if(debugPrint) debugPrint(getBitsNumber(bitsForLevel) + ": bitsForLevel");

                if (((c >>> removeAt) & 1) == 0) {
                    break;
                }
                // if(debugPrint) debugPrint("bit is set");

                // at this level, the bit is already set: loop until it's not set
                startLevel += bitsSet;
                // if(debugPrint) debugPrint("startLevel=" + startLevel);
                bitsSet = Long.bitCount(bitsForLevel);
                // if(debugPrint) debugPrint("bitsSet=" + bitsSet);
                bitsBefore = removeAt == 0 ? 0 : Long.bitCount(bitsForLevel << (64 - removeAt));
                // if(debugPrint) debugPrint("bitsBefore=" + bitsBefore);
                resetAt = removeAt;
                // if(debugPrint) debugPrint("resetAt=" + resetAt);
                removeAt = startLevel + bitsBefore;
                // if(debugPrint) debugPrint("removeAt=" + removeAt);
                if (removeAt > 63) {
                    break;
                }
            }
            // if(debugPrint) debugPrint("bit is set, reset it");
            c ^= 1L << resetAt;
            // if(debugPrint) debugPrint(getBitsNumber(c) + ": c");
        }
        if (removeAt < 64) {
            // remove the bit from the counter
            long mask = (1L << removeAt) - 1;
            // if(debugPrint) debugPrint(getBitsNumber(mask) + ": mask");
            long left = (c >>> 1) & ~mask;
            // if(debugPrint) debugPrint(getBitsNumber(left) + ": left");
            long right= c & mask;
            // if(debugPrint) debugPrint(getBitsNumber(right) + ": right");
            c = left | right;
            // if(debugPrint) debugPrint(getBitsNumber(c) + ": c");
        }
        counts[group] = c;
        // possibly reset the data bit
        // if(debugPrint) debugPrint(getBitsNumber(~((d==0?1L:0L) << x)) + ": data mask");
        // if(debugPrint) debugPrint(getBitsNumber(m & ~((d==0?1L:0L) << x)) + ": data");
        data[group] = m & ~((d==0?1L:0L) << x);
        if (VERIFY_COUNTS) {
            verifyCounts((group << 6), ((group + 1) << 6));
        }
    }

    private void freeOverflow(int index) {
        overflow[index] = nextFreeOverflow;
        nextFreeOverflow = index;
    }

    private static long getBit(int index) {
        return 1L << (index * 8);
    }

    private int readCount(int x) {
        int group = x >>> 6;
        long m = data[group];
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
        if (c == 0) {
            return 1;
        }
        // if(debugPrint) debugPrint("getCount ---------------------------------- " + group + " " + (x & 63));
        // if(debugPrint) debugPrint(getBitsNumber(m) + ": data bits");
        // if(debugPrint) debugPrint(getBitsNumber(c) + ": counters");
        int bitsSet = Long.bitCount(m);
        x &= 63;
        // if(debugPrint) debugPrint("x=" + x);
        // if(debugPrint) debugPrint("bitsSet=" + bitsSet);
        int bitsBefore = x == 0 ? 0 : Long.bitCount(m << (64 - x));
        // if(debugPrint) debugPrint("bitsBefore=" + bitsBefore);
        // if(debugPrint) debugPrint(getBitsNumber(c) + ": current");
        int count = 1;
        int insertAt = bitsBefore;
        // if(debugPrint) debugPrint(insertAt + ": insertAt");
        while (true) {
            // the mask for the current level
            long levelMask = (1L << bitsSet) - 1;
            // if (debugPrint) debugPrint(getBitsNumber(levelMask) + ": levelMask");
            // the relevant bits for the current level
            long bitsForLevel = c & levelMask;
            // if (debugPrint) debugPrint(getBitsNumber(bitsForLevel) + ": bitsForLevel");
            if (((c >>> insertAt) & 1) == 1) {
                count++;
                // if (debugPrint) debugPrint("bit is set, count=" + count);
                // at this level, the bit is already set: loop until it's not set
                c >>>= bitsSet;
                bitsSet = Long.bitCount(bitsForLevel);
                // if (debugPrint) debugPrint("bitsSet=" + bitsSet);
                bitsBefore = insertAt == 0 ? 0 : Long.bitCount(bitsForLevel << (64 - insertAt));
                // if (debugPrint) debugPrint("bitsBefore=" + bitsBefore);
                insertAt = bitsBefore;
                // if (debugPrint) debugPrint("insertAt=" + insertAt);
                // if (debugPrint) debugPrint("c=" + getBitsNumber(c));
            } else {
                break;
            }
            if (count > 16) {
                throw new AssertionError();
            }
        }
        // if (debugPrint) debugPrint("done count=" + count);
        return count;
    }

    private void verifyCounts(int from, int to) {
        if (!VERIFY_COUNTS) {
            return;
        }
        for (int i = Math.max(0, from); i < to && i < realCounts.length; i++) {
            if (readCount(i) != realCounts[i]) {
                throw new AssertionError(" at group " + (from >> 6) + " i " + i + " " + (i & 63) + " got "
                        + readCount(i) + " expected " + realCounts[i]);
            }
        }
    }

    // void debugPrint(String msg) {
    //     System.out.println(msg);
    // }

    static String getBitsNumber(long x) {
        String s = "0000000000000000000000000000000000000000000000000000000000000000000000" + Long.toBinaryString(x);
        s = s.substring(s.length() - 64);
        return s;
    }

}
