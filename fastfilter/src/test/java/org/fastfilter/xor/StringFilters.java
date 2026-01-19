package org.fastfilter.xor;

import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.fastfilter.Filter;
import org.junit.Test;

public class StringFilters {

    private static final int NUM_STRINGS = 100_000;
    private static final int NUM_TEST_STRINGS = 1_000;
    private static final Random random = new Random(42);

    private static final long[] keys = generateKeys();
    private static final long[] testKeys = generateTestKeys();

    private static long[] generateKeys() {
        String[] strings = new String[NUM_STRINGS];
        for (int i = 0; i < NUM_STRINGS; i++) {
            strings[i] = generateRandomString();
        }
        long[] k = new long[NUM_STRINGS];
        for (int i = 0; i < NUM_STRINGS; i++) {
            k[i] = hashString(strings[i]);
        }
        checkUniqueness(k, "keys");
        return k;
    }

    private static long[] generateTestKeys() {
        String[] strings = new String[NUM_TEST_STRINGS];
        for (int i = 0; i < NUM_TEST_STRINGS; i++) {
            strings[i] = generateRandomString();
        }
        long[] k = new long[NUM_TEST_STRINGS];
        for (int i = 0; i < NUM_TEST_STRINGS; i++) {
            k[i] = hashString(strings[i]);
        }
        checkUniqueness(k, "test keys");
        return k;
    }

    private static void checkUniqueness(long[] array, String name) {
        Set<Long> set = new HashSet<>();
        int collisions = 0;
        for (long l : array) {
            if (!set.add(l)) {
                collisions++;
            }
        }
        if (collisions > 0) {
            System.out.println("Warning: " + collisions + " hash collisions in " + name);
        } else {
            System.out.println("No hash collisions in " + name);
        }
    }

    private static String generateRandomString() {
        int length = 5 + random.nextInt(16); // 5 to 20 chars
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }

    private static long hashString(String s) {
        long h = 0;
        for (char c : s.toCharArray()) {
            h = h * 31 + c;
        }
        return h;
    }

    @Test
    public void testXor8() {
        testFilter(Xor8.class);
    }

    @Test
    public void testXor16() {
        testFilter(Xor16.class);
    }

    @Test
    public void testXorBinaryFuse8() {
        testFilter(XorBinaryFuse8.class);
    }

    @Test
    public void testXorBinaryFuse16() {
        testFilter(XorBinaryFuse16.class);
    }

    @Test
    public void testXorBinaryFuse32() {
        testFilter(XorBinaryFuse32.class);
    }

    private void testFilter(Class<?> filterClass) {
        // Construct filter
        Filter filter;
        try {
            filter = (Filter) filterClass.getMethod("construct", long[].class).invoke(null, (Object) keys);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Check all keys are in the filter
        for (int i = 0; i < NUM_STRINGS; i++) {
            assertTrue("Key " + i + " should be in filter", filter.mayContain(keys[i]));
        }

        // Check false positives on test keys
        int falsePositives = 0;
        for (int i = 0; i < NUM_TEST_STRINGS; i++) {
            if (filter.mayContain(testKeys[i])) {
                falsePositives++;
            }
        }

        // Expect low false positive rate (less than 1% for most filters)
        double fpp = (double) falsePositives / NUM_TEST_STRINGS;
        System.out.println(filterClass.getSimpleName() + " false positive rate: " + fpp);
        assertTrue("False positive rate should be low: " + fpp, fpp < 0.01); // Allow up to 1%
    }
}
