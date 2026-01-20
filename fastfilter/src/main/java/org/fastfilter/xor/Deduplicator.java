package org.fastfilter.xor;

import java.util.Arrays;

public class Deduplicator {

    /**
     * Sorts the keys array and removes duplicates in place.
     * Returns the new length of the array (number of unique elements).
     *
     * @param keys the array of keys to deduplicate
     * @param length the current length of the array
     * @return the new length after removing duplicates
     */
    public static int sortAndRemoveDup(long[] keys, int length) {
        Arrays.sort(keys, 0, length);
        int j = 1;
        for (int i = 1; i < length; i++) {
            if (keys[i] != keys[i - 1]) {
                keys[j] = keys[i];
                j++;
            }
        }
        return j;
    }

}
