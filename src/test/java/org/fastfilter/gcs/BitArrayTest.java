package org.fastfilter.gcs;

import java.util.Random;

import org.fastfilter.gcs.BitBuffer;
import org.fastfilter.utils.RandomGenerator;
import org.junit.Test;

public class BitArrayTest {

    public static void main(String... args) {
        performanceTest();
    }

    private static void performanceTest() {
        for (int bitSize = 4; bitSize < 16; bitSize++) {
            for (int size = 1_000_000; size <= 1_000_000_000; size *= 10) {
                testBitBuffer(bitSize, size);
            }
        }
    }

    private static void testBitBuffer(int bitCount, int size) {
        long bits = (long) size * bitCount;
        if (bits / 64 > Integer.MAX_VALUE / 2) {
            return;
        }
        BitBuffer buff = new BitBuffer(bits);
        long[] list = buff.data;
        RandomGenerator.createRandomUniqueListFast(list, list.length);
        long time = System.nanoTime();
        long sum = 0;
        int count = 1_000_000;
        for (int i = 0, j = 0; i < count; i++) {
            long pos = reduce((int) list[j++], size);
            if (j >= list.length) {
                j = 0;
            }
            sum += buff.readNumber(pos * bitCount, bitCount);
        }
        time = (System.nanoTime() - time) / count;
        System.out.println("BitBuffer size: " + size + " bitCount: " + bitCount + " time: " + time + " ns/op dummy: " + sum);
    }

    private static int reduce(int hash, int n) {
        // http://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
        return (int) (((hash & 0xffffffffL) * n) >>> 32);
    }

}
