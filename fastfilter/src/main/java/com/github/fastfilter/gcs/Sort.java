package com.github.fastfilter.gcs;

import java.util.Arrays;

public class Sort {

    public static void sortUnsigned(long[] data) {
        sortUnsigned(data, 0, data.length);
    }

    public static void sortUnsigned(long[] data, int offset, int len) {
        int[] histogram = new int[257];
        len = Math.min(len, data.length - offset);
        long[] buffer = new long[len];
        for(int shift = 0; shift < Long.SIZE; shift += 8) {
            Arrays.fill(histogram, 0);
            for (int i = 0; i < len; i++) {
                int b = (int)((data[i + offset] >>> shift) & 0xff);
                histogram[b + 1]++;
            }
            for (int i = 0; i + 1 < histogram.length; i++) {
                histogram[i + 1] += histogram[i];
            }
            for (int i = 0; i < len; i++) {
                int b = (int)((data[i + offset] >>> shift) & 0xff);
                int index = histogram[b]++;
                buffer[index] = data[i + offset];
            }
            System.arraycopy(buffer, 0, data, offset, len);
        }
    }

}
