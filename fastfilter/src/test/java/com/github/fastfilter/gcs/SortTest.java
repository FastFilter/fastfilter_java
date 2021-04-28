package com.github.fastfilter.gcs;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

import com.github.fastfilter.gcs.Sort;

public class SortTest {

    public static void main(String... args) {
        new SortTest().sortUnsigned();
        int count = 128 * 1024 * 1024; // * 1024 * 1024;
        for(int test = 0; test < 4; test++) {
            System.out.println("test " + test);
            Random r = new Random(test);
            long[] data = new long[count];
            long sum = 0;
            for(int i = 0; i < data.length; i++) {
                long x = r.nextLong();
                sum += x;
                data[i] = x;
            }
            long time = System.nanoTime();
            Sort.sortUnsigned(data);
            time = System.nanoTime() - time;
            System.out.println("sorted in " + (time / 1_000_000_000.) + " secs");
            long sum2 = 0;
            for(long x : data) {
                sum2 += x;
            }
            if (sum != sum2) {
                throw new AssertionError("sum changed");
            }
            for (int i = 1; i < data.length; i++) {
                if (Long.compareUnsigned(data[i - 1], data[i]) > 0) {
                    throw new AssertionError("index " + i);
                }
            }
            System.out.println("compared");
        }
    }

    @Test
    public void sortUnsigned() {
        Random r = new Random(1);
        for (int test = 0; test < 1000; test++) {
            int len = r.nextInt(10);
            long[] data = new long[len];
            for (int i = 0; i < len; i++) {
                data[i] = r.nextInt(5) - 2;
            }
            Sort.sortUnsigned(data);
            // sortUnsignedSimple(data);
            for (int i = 1; i < data.length; i++) {
                if (Long.compareUnsigned(data[i - 1], data[i]) > 0) {
                    throw new AssertionError("index " + i);
                }
            }
        }
    }

    @Test
    public void sortUnsignedShifted() {
        Random r = new Random(1);
        for (int test = 0; test < 1000; test++) {
            int len = r.nextBoolean() ? r.nextInt(10) : r.nextInt(1000);
            int rotate = r.nextInt(60);
            long[] data = new long[len];
            long xor = 0;
            for (int i = 0; i < len; i++) {
                long x = Long.rotateLeft((r.nextInt(5) - 2), rotate);
                data[i] = x;
                xor ^= x;
            }
            long[] d2 = Arrays.copyOf(data, data.length);
            int offset = len <= 0 ? 0 : r.nextInt(len);
            int sortLen = len <= 0 ? 0 : r.nextInt(len);
            Sort.sortUnsigned(data, offset, sortLen);
            // sortUnsignedSimple(data);
            long xor2 = 0;
            for(long x : data) {
                xor2 ^= x;
            }
            assertEquals(xor2, xor);
            for (int i = 0; i < len; i++) {
                if (i < offset || i > offset + len) {
                    assertEquals(d2[i], data[i]);
                }
            }
            for (int i = offset + 1; i < Math.min(offset + sortLen, data.length); i++) {
                if (Long.compareUnsigned(data[i - 1], data[i]) > 0) {
                    throw new AssertionError("index " + i);
                }
            }
        }
    }

}
