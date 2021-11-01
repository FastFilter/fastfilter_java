package org.fastfilter.quotient;

import java.util.Arrays;

public class InsertOrderTest {
    public static void main(String... args) {
        for(int size = 1_000_000; size <= 100_000_000; size *= 10) {
            long[] keys = new long[size];
            for(int i=0; i<size; i++) {
                // guaranteed unique
                keys[i] = hash64(i, 0);
            }
            System.out.print(size + " unsorted keys:");
            for (double fillRatePercent = 99; fillRatePercent > 50; fillRatePercent--) {
                boolean success = test(keys, fillRatePercent / 100.0);
                if (success) {
                    System.out.println(" success at fill rate " + fillRatePercent + "%");
                    break;
                }
            }
            Arrays.sort(keys);
            System.out.print(size + " sorted keys:");
            for (double fillRatePercent = 99; fillRatePercent > 50; fillRatePercent--) {
                boolean success = test(keys, fillRatePercent / 100.0);
                if (success) {
                    System.out.println(" success at fill rate " + fillRatePercent + "%");
                    break;
                }
            }
        }
    }

    public static long hash64(long x, long seed) {
        x += seed;
        x = (x ^ (x >>> 33)) * 0xff51afd7ed558ccdL;
        x = (x ^ (x >>> 33)) * 0xc4ceb9fe1a85ec53L;
        x = x ^ (x >>> 33);
        return x;
    }

    public static int reduce(int hash, int n) {
        // http://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
        return (int) (((hash & 0xffffffffL) * n) >>> 32);
    }

    static boolean test(long[] keys, double fillRate) {
        // System.out.println("fillRate " + fillRate);
        int slotsPerBlock = 48;
        int slots = (int) (keys.length / fillRate);
        int blocks = (slots + slotsPerBlock) / slotsPerBlock;
        slots = blocks * slotsPerBlock;

        int[] used = new int[blocks];
        for (int i = 0; i < keys.length; i++) {
//            if ((i % (keys.length / 10)) == 0) {
//                for (int j = 0; j < used.length; j += 10) {
//                    System.out.print(used[j] / 10);
//                }
//                System.out.println();
//            }
            long k = keys[i];
            int index = reduce((int) k, blocks);
            int index2 = reduce((int) (k >> 32), blocks);
            if (used[index] < used[index2]) {
                used[index]++;
                if (used[index] > slotsPerBlock) {
                    return false;
                }
            } else {
                used[index2]++;
                if (used[index2] > slotsPerBlock) {
                    return false;
                }
            }
        }
        return true;
    }
}
