package org.fastfilter.gcs;

import java.util.Arrays;

public class Sort {

    public static void sortUnsigned(long[] data) {
        sortUnsigned(data, 0, data.length);
    }

    public static void sortUnsigned(long[] data, int offset, int len) {
        int[] histogram = new int[257];
        int shift = 0;
        long mask = 0xFF;
        long[] buffer = new long[Math.min(len, data.length)];
        while (shift < Long.SIZE) {
            Arrays.fill(histogram, 0);
            for (int i = offset; i + offset < buffer.length; ++i) {
                ++histogram[(int)((data[i] & mask) >>> shift) + 1];
            }
            for (int i = 0; i + 1 < histogram.length; ++i) {
                histogram[i + 1] += histogram[i];
            }
            for (int i = offset; i + offset < buffer.length; ++i) {
                buffer[histogram[(int)((data[i] & mask) >>> shift)]++] = data[i];
            }
            System.arraycopy(buffer, 0, data, offset, buffer.length);
            shift += 8;
            mask <<= 8;
        }
    }

}
