package org.fastfilter.gcs;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.fastfilter.gcs.BitBuffer;
import org.fastfilter.gcs.MonotoneList;
import org.junit.Test;

/**
 * Test the MonotoneList implementations.
 */
public class MonotoneListTest {

    public static void main(String... args) {
        testPerformance();
        testPerformance();
        testPerformance();
        testBestSize(100, 8);
        new MonotoneListTest().testSaving();
    }

    private static void testPerformance() {
        int bucketSize = 16, size = 1000000;
        test(bucketSize, size);
    }

    @Test
    public void test() {
        for (int bucketSize = 8; bucketSize < 256; bucketSize *= 2) {
            for (int size = 100; size <= 1000000; size *= 10) {
                if (size >= bucketSize) {
                    test(bucketSize, size);
                }
            }
        }
    }

    public void testSaving() {
        for (int bucketSize = 8; bucketSize < 256; bucketSize *= 2) {
            for (int size = 100; size <= 100000000; size *= 10) {
                if (size >= bucketSize) {
                    test(bucketSize, size);
                }
            }
        }
    }

    private static void testBestSize(int bucketSize, int size) {
        int bucketCount = size / bucketSize;
        int[] sizes = randomSizes(size, bucketCount);
        int[] posList = posList(sizes);
        int bestLen = Integer.MAX_VALUE;
        String best = "";
        BitBuffer buffer = new BitBuffer(100 * bucketCount);
        MonotoneList.generate(posList, buffer);
        assertEquals(MonotoneList.getSize(posList), buffer.position());
        int len2 = buffer.position();
        for (int shift1 = 2; shift1 <= 16; shift1++) {
            for (int shift2 = 1; shift2 <= shift1; shift2++) {
                for (int factor1 = 4; factor1 < 256; factor1 *= 2) {
                    for (int factor2 = 2; factor2 <= factor1; factor2 *= 2) {
                        buffer = new BitBuffer(100 * bucketCount);
                        // MultiStageMonotoneList.SHIFT1 = shift1;
                        // MultiStageMonotoneList.SHIFT2 = shift2;
                        // MultiStageMonotoneList.FACTOR1 = factor1;
                        // MultiStageMonotoneList.FACTOR2 = factor2;
                        MonotoneList list = MonotoneList
                                .generate(posList, buffer);
                        for (int i = 0; i < posList.length; i++) {
                            assertEquals("i:" + i, posList[i], list.get(i));
                        }
                        int len = buffer.position();
                        if (len < bestLen) {
                            best = "    shift " + shift1 + "/" + shift2 +
                                            " factor " +
                                            factor1 + "/" + factor2 +
                                            " len " + len + " len2 " + len2;
                            bestLen = len;
                        }
                    }
                }
            }
        }
        log(size + " " + best + " " + len2);
    }

    private static void log(String msg) {
        // System.out.println(msg);
    }

    private static void test(int bucketSize, int size) {
        int bucketCount = size / bucketSize;
        int[] sizes = randomSizes(size, bucketCount);
        int[] posList = posList(sizes);
        int[] expected = expectedPosList(size, bucketCount);
        int[] offsets = offsets(posList, expected);
        int min = min(offsets), max = max(offsets);
        int entryBits = 32 - Integer.numberOfLeadingZeros(-min + max);
        int diff = min(sizes);
        int[] sizeOffsets = plus(sizes, -diff);
        int[] posList2 = posList(sizeOffsets);
        // System.out.println(size + " avg gap: " + (double) posList2[posList2.length - 1] / posList2.length);
        for (int i = 0; i < bucketCount; i++) {
            assertEquals(posList[i], posList2[i] + i * diff);
        }
        BitBuffer buffer = new BitBuffer(1000 + 100 * size);
        MonotoneList list = MonotoneList.generate(posList2, buffer);
        assertEquals(MonotoneList.getSize(posList2), buffer.position());
        int bitCount = buffer.position();
        double oldBits = (double) (entryBits * bucketCount) / size;
        double newBits =  (double) bitCount / size;
        log("bucketSize " + bucketSize + " bucketCount " + bucketCount
                + " old " + oldBits + " (" + entryBits + "*" + bucketCount + ") new " + newBits
                + " saving " + (oldBits - newBits));
        for (int i = 0; i < bucketCount; i++) {
            assertEquals("i: " + i, posList2[i], list.get(i));
        }
        buffer.seek(0);
        list = MonotoneList.load(buffer);
        buffer.seek(0);
        MonotoneList list2 = MonotoneList.load(buffer);
        assertEquals(bitCount, buffer.position());
        for (int i = 0; i < bucketCount; i++) {
            assertEquals(posList2[i], list.get(i));
        }
        for (int i = 0; i < bucketCount - 1; i++) {
            int a = list.get(i);
            int b = list.get(i + 1);
            long ab = list.getPair(i);
            assertEquals(((long) a << 32) + b, ab);
        }
        Random r = new Random(1);
        // memory access elsewhere
        byte[] data = new byte[bucketCount * 8];
        int[] readList = new int[bucketCount];
        r.nextBytes(data);
        for (int i = 1; i < bucketCount; i++) {
            readList[i] = r.nextInt(bucketCount - 1);
        }
        int dummy = 0;
        long time = System.nanoTime();
        for (int test = 0; test < 10; test++) {
            for (int i = 0; i < bucketCount - 1; i++) {
                int x = readList[i];
                dummy += list.get(x);
                dummy += list2.get(x);
            }
        }
        time = System.nanoTime() - time;
        if (bucketCount > 1) {
            log("Time: " + time / 1000000 + " ms; " + time / 10 / 2 / (bucketCount - 1) + " ns/key dummy " + dummy);
        }
    }

    private static int[] randomSizes(int size, int bucketCount) {
        Random r = new Random(size + bucketCount);
        int[] sizes = new int[bucketCount];
        for (int i = 0; i < size; i++) {
            sizes[r.nextInt(bucketCount)]++;
        }
        return sizes;
    }

    private static int[] expectedPosList(int size, int bucketCount) {
        int[] sizes = new int[bucketCount];
        for (int i = 0; i < bucketCount; i++) {
            sizes[i] = (int) ((long) size * i / bucketCount);
        }
        return sizes;
    }

    private static int[] offsets(int[] got, int[] expected) {
        int[] offsets = new int[got.length];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = got[i] - expected[i];
        }
        return offsets;
    }

    private static int min(int[] sizes) {
        int min = Integer.MAX_VALUE;
        for (int x : sizes) {
            min = Math.min(min, x);
        }
        return min;
    }

    private static int max(int[] sizes) {
        int max = Integer.MIN_VALUE;
        for (int x : sizes) {
            max = Math.max(max, x);
        }
        return max;
    }

    private static int[] plus(int[] list, int plus) {
        int[] list2 = new int[list.length];
        for (int i = 0; i < list.length; i++) {
            list2[i] = list[i] + plus;
        }
        return list2;
    }

    private static int[] posList(int[] sizes) {
        int[] posList = new int[sizes.length];
        int p = 0;
        for (int i = 0; i < sizes.length; i++) {
            posList[i] = p;
            p += sizes[i];
        }
        return posList;
    }

}
