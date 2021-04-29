package org.fastfilter;

import static org.junit.Assert.assertEquals;

import org.fastfilter.Filter;
import org.fastfilter.TestFilterType;
import org.fastfilter.utils.Hash;
import org.fastfilter.utils.RandomGenerator;
import org.junit.Test;

/*

## Should I Use an Approximate Member Filter?

In some cases, if the false positive rate of a filter is low enough, using _just_ a filter is good enough,
an one does not need the original data altogether.
For example, a simple spell checker might just use a filter that contains
the known words. It might be OK if a mistyped word is not detected, if this is rare enough.
Another example is using a filter to reject known passwords: the complete list of all known passwords
is very large, so using a filter makes sense. The application (or user) can deal
with the possibility of false positives: the filter will simplify mark a password
as "known" even if it's not in the list.

But in most cases the original data is needed, and filters are only used to avoid unnecessary lookups.
Whether or not using a filter makes sense, and which filter to use, depends on multiple factors:

* Is it worth the additional complexity?
* How much time is saved? One has to consider the time saved by true positives,
   minus the time needed to do lookups in the filter.
   Typically, avoiding I/O make sense,
   but avoiding memory lookups usually doesn't save time.
* The memory needed by the filter often also plays a role,
   as it means less memory is available for a cache,
   and a smaller cache can slow things down.

Specially the last point makes it harder to estimate how much time can be saved by which filter type and configuration,
as many factors come into play.

To compare accurately, it might be best to write a benchmark application that is close to the real-world,
and then run this benchmark with different filters.

(Best would be to have a benchmark that simulates such an application, but it takes some time.
Or change e.g. RocksDB to use different filters.
Would it be worth it? For caching, typically "trace files" are used to compare algorithms,
but for filters this is probably harder.)

## Which Features Do I Need?

... do you need a mutable filter, do you want to store satellite data, ...

## What are the Risks?

... (I think some filters have risks, for example the cuckoo filter and other fingerprint based ones
may not be able to store an entry in rare cases, if used in the mutable way)



---------------

## Which Filter Should I Use?

For a certain false positive rate, some filter types are faster but need more memory,
others use less memory but are slower.



To decide which type to use, the average time can be estimated as follows:

* filterFpp: false positive rate of the filter (0.01 for 1%)
* applicationFpp: false positive rate of the application (how often does the application perform a lookup if the entry doesn't exist)
* filterLookupTime: average time needed by the filter to perform a lookup
* falsePositiveTime: average time needed in case of a false positive, in nanoseconds

time = (1 - applicationFpp) * filterLookupTime +
           applicationFpp * (filterLookupTime + filterFpp * falsePositiveTime)

This could be, for a LSM tree:

* applicationFpp: 0.9
* falsePositiveTime: 40000 nanoseconds (0.04 milliseconds access time for a random read in an SSD)

...

 */

public class TestAllFilters {

    public static void main(String... args) {
        Hash.setSeed(1);
        /*
        for (int size = 1_000_000; size <= 10_000_000; size *= 10) {
            System.out.println("size " + size);
            for (int test = 0; test < 10; test++) {
                test(TestFilterType.BLOOM, size, test, true);
                test(TestFilterType.BLOCKED_BLOOM, size, test, true);
                test(TestFilterType.COUNTING_BLOOM, size, test, true);
                test(TestFilterType.SUCCINCT_COUNTING_BLOOM, size, test, true);
                test(TestFilterType.SUCCINCT_COUNTING_BLOOM_RANKED, size, test, true);
            }
        }
        */
        for (int size = 1; size <= 100; size++) {
            System.out.println("size " + size);
            test(TestFilterType.XOR_FUSE_8, size, 0, true);
        }
        for (int size = 100; size <= 100000; size *= 1.1) {
            System.out.println("size " + size);
            test(TestFilterType.XOR_FUSE_8, size, 0, true);
        }
        for (int size = 1_000_000; size <= 8_000_000; size *= 2) {
            System.out.println("size " + size);
            testAll(size, true);
            System.out.println();
        }
        System.out.println();
        for (int size = 10_000_000; size <= 80_000_000; size *= 2) {
            System.out.println("size " + size);
            testAll(size, true);
            System.out.println();
        }
        testAll(100_000_000, true);
    }

    @Test
    public void test() {
        testAll(1000000, false);
    }

    private static void testAll(int len, boolean log) {
        for (TestFilterType type : TestFilterType.values()) {
            test(type, len, 0, log);
        }
    }

    private static void test(TestFilterType type, int len, int seed, boolean log) {
        long[] list = new long[len * 2];
        RandomGenerator.createRandomUniqueListFast(list, len + seed);
        long[] keys = new long[len];
        long[] nonKeys = new long[len];
        // first half is keys, second half is non-keys
        for (int i = 0; i < len; i++) {
            keys[i] = list[i];
            nonKeys[i] = list[i + len];
        }
        long time = System.nanoTime();
        Filter f = type.construct(keys, 10);
        time = System.nanoTime() - time;
        double nanosPerAdd = time / len;
        time = System.nanoTime();
        // each key in the set needs to be found
        int falseNegatives = 0;
        for (int i = 0; i < len; i++) {
            if (!f.mayContain(keys[i])) {
                falseNegatives++;
                // f.mayContain(keys[i]);
                // throw new AssertionError();
            }
        }
        if (falseNegatives > 0) {
            throw new AssertionError("false negatives: " + falseNegatives);
        }
        time = System.nanoTime() - time;
        double nanosPerLookupAllInSet = time / 2 / len;
        time = System.nanoTime();
        // non keys _may_ be found - this is used to calculate false
        // positives
        int falsePositives = 0;
        for (int i = 0; i < len; i++) {
            if (f.mayContain(nonKeys[i])) {
                falsePositives++;
            }
        }
        time = System.nanoTime() - time;
        double nanosPerLookupNoneInSet = time / 2 / len;
        double fpp = (double) falsePositives / len;
        long bitCount = f.getBitCount();
        double bitsPerKey = (double) bitCount / len;
        double nanosPerRemove = -1;
        if (f.supportsRemove()) {
            time = System.nanoTime();
            for (int i = 0; i < len; i++) {
                f.remove(keys[i]);
            }
            time = System.nanoTime() - time;
            nanosPerRemove = time / len;
if (f.cardinality() != 0) {
    System.out.println(f.cardinality());
}
            assertEquals(f.toString(), 0, f.cardinality());
        }
        if (log) {
            System.out.println(type + " fpp: " + fpp +
                    " size: " + len +
                    " bits/key: " + bitsPerKey +
                    " add ns/key: " + nanosPerAdd +
                    " lookup 0% ns/key: " + nanosPerLookupNoneInSet +
                    " lookup 100% ns/key: " + nanosPerLookupAllInSet +
                    (nanosPerRemove < 0 ? "" : (" remove ns/key: " + nanosPerRemove)));
        }
    }

}
