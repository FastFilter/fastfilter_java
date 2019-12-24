package org.fastfilter;

import org.fastfilter.utils.Hash;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.LongStream;

import static junit.framework.TestCase.assertTrue;

public class SimpleFuzzer {

    // implementations with bugs which may not be worth fixing
    private static final EnumSet<FilterType> IGNORED = EnumSet.of(FilterType.GCS2, FilterType.MPHF);

    public static void main(String... args) {
        long seed = 0;
        for (int keyLength = 3; keyLength < 1_000_000; keyLength += 100) {
            long[] keys = LongStream.range(0, keyLength).map(i -> ThreadLocalRandom.current().nextLong()).toArray();
            for (FilterType type : FilterType.values()) {
                if (IGNORED.contains(type)) {
                    continue;
                }
                try {
                    for (int i = 0; i < 1_000; ++i) {
                        seed = ThreadLocalRandom.current().nextLong();
                        Hash.setSeed(seed);
                        Filter filter = type.construct(keys, 8);
                        for (long key : keys) {
                            assertTrue(seed + "/" + type + "/" + Arrays.toString(keys), filter.mayContain(key));
                        }
                    }
                } catch (Exception e) {
                    System.out.println(seed + "/" + type + "/" + Arrays.toString(keys));
                    throw e;
                }
            }
        }
    }
}
