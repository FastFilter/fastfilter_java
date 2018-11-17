package org.fastfilter.gcs;

import java.util.Arrays;

public class Sort {

    public static void sortUnsigned(long[] data) {
        sortUnsigned(data, 0, data.length);
    }

    public static void sortUnsigned(long[] data, int offset, int len) {
        int left = offset, right = offset + len - 1;
        while(true) {
            while (left < data.length && data[left] >= 0) {
                left++;
            }
            while (right > 0 && data[right] < 0) {
                right--;
            }
            if (left >= right) {
                break;
            }
            long temp = data[left];
            data[left++] = data[right];
            data[right--] = temp;
        }
        Arrays.sort(data, offset, left);
        Arrays.sort(data, left, len);
    }

}
