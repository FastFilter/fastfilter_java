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

    public static SuccintCountingBloom construct(long[] keys, double bitsPerKey) {
        long n = keys.length;
        int k = getBestK(bitsPerKey);
        // TODO this is a hack to limit the fill rate to lower than 50%;
        // it speed up construction but hurts the fpp
        k--;
        SuccintCountingBloom f = new SuccintCountingBloom((int) n, bitsPerKey, k);
        for(long x : keys) {
            f.add(x);
        }
        return f;
    }

    private static int getBestK(double bitsPerKey) {
        return Math.max(1, (int) Math.round(bitsPerKey * Math.log(2)));
    }

    private final int k;
    private final long bits;
    private final long seed;
    private final int arraySize;

    // the "data bits" exactly as in a regular Bloom filter
    private final BitField data;

    // the counter bits
    // the same size as the "data bits"
    private final BitField counts;

    private final BitField cont;

    // each byte contains the real count for a "data bit"
    // (this is just used for verification)
//    private final byte[] realCounts;

    public long getBitCount() {
        return data.getBitCount() + counts.getBitCount() + cont.getBitCount();
    }

    SuccintCountingBloom(int entryCount, double bitsPerKey, int k) {
        entryCount = Math.max(1, entryCount);
        this.k = k;
        this.seed = Hash.randomSeed();
        this.bits = (long) (entryCount * bitsPerKey);
        arraySize = (int) ((bits + 63) / 64);
        data = new BitField(64 * (arraySize + 10));
        counts = new BitField(64 * (arraySize + 10));
        cont = new BitField(arraySize + 10);
//        realCounts = new byte[arraySize * 64];
    }

    private void add(long key) {
        long hash = Hash.hash64(key, seed);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        for (int i = 0; i < k; i++) {
            int index = Hash.reduce(a, arraySize) * 64 + (a & 63);
            increment(index);
            a += b;
        }
    }

    private void increment(int x) {
//        realCounts[x]++;
        int group = x >>> 6;
        long m = data.getLong(group);
        long d = (m >>> x) & 1;
        data.set(x);
        int bitsBefore = Long.bitCount(m & (-1L >>> (63 - x)));
//        int mm = 0;
        while (group > 0 && cont.get(group - 1) != 0) {
            group--;
            bitsBefore += Long.bitCount(data.getLong(group));
//            mm++;
//            if (mm > 10) {
//                System.out.println("??");
//            }
        }
        int before = counts.selectInLong(group, bitsBefore) + 1;
        long last;
        last = counts.insertInLong(before - (int) d, 1 ^ d);
//        int mmm = 0;
        while (last != 0 || cont.get(group) != 0) {
            cont.set(group);
            group++;
            before = (before + 63) / 64 * 64;
            last = counts.insertInLong(before, last);
//            mmm++;
//            if (mmm % 5 == 0) {
//                System.out.println(mmm + " ?? " + x + " group " + group + " before " + before + " " + data + " " + counts);
//            }
//            if (mmm > 10) {
//                for(int xx = x - 10 * 64; xx < x + 10 * 64; xx++) {
//                    int sum = 0;
//                    for (int xxx = xx; xxx < xx + 64; xxx++) {
//                        sum += realCounts[xxx];
//                    }
//                    System.out.println(xx + ": " + realCounts[xx] + " sum " + sum + " " + (sum > 64 ? "xxx" : ""));
//                }
//            }
        }
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

//    public String toString() {
//
//
//        StringBuilder buff = new StringBuilder();
//        for (int i = 0; i < realCounts.length; i++) {
//            if ((data.get(i)) == 0) {
//                buff.append('0');
//            } else {
//                buff.append('1');
//            }
//        }
//        buff.append("  data\n");
//        for (int i = 0; i < realCounts.length; i++) {
//            if ((counts.get(i)) == 0) {
//                buff.append('0');
//            } else {
//                buff.append('1');
//            }
//        }
//        buff.append("  counts\n");
//        for (int i = 0; i < realCounts.length; i++) {
//            buff.append((char) ('0' + (realCounts[i] % 10)));
//        }
//        buff.append("  realCounts\n");
//        for (int i = 0; i < realCounts.length; i++) {
//            buff.append((char) ('0' + (i % 10)));
//        }
//        buff.append("  index\n");
//        for (int i = 0; i < realCounts.length; i++) {
//            if (i % 10 == 0 && i > 0) {
//                buff.append(i / 10);
//            } else {
//                buff.append('0');
//            }
//        }
//        buff.append("  index\n");
//        return buff.toString();
//    }

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
            long sum = 0;
            for(long x : data) {
                sum += Long.bitCount(x);
            }
            if(true)
            return "" + sum + " = " + (100*sum/data.length/64) + "%";

            StringBuilder buff = new StringBuilder();
            for (int i = 0; i < data.length * 64; i++) {
                if (get(i) == 0) {
                    buff.append('0');
                } else {
                    buff.append('1');
                }
            }
            buff.append("  data\n");
            for (int i = 0; i < data.length * 64; i++) {
                buff.append((char) ('0' + (i % 10)));
            }
            buff.append("  index\n");
            for (int i = 0; i < data.length * 64; i++) {
                if (i % 10 == 0 && i > 0) {
                    buff.append(i / 10);
                } else {
                    buff.append('0');
                }
            }
            return buff.toString();
        }

    }


}
