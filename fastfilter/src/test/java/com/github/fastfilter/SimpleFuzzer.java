package com.github.fastfilter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.LongStream;

import com.github.fastfilter.Filter;
import com.github.fastfilter.utils.Hash;

import static com.github.fastfilter.TestFilterType.*;
import static junit.framework.TestCase.assertTrue;

public class SimpleFuzzer {

    public static void main(String... args) {
        long seed = 0;
        for (int bitsPerKey = 8; bitsPerKey < 32; bitsPerKey += 8) {
            for (int keyLength = 3; keyLength < 1_000_000; keyLength += ThreadLocalRandom.current().nextInt(10000)) {
                long[] keys = LongStream.range(0, keyLength).map(i -> ThreadLocalRandom.current().nextLong()).toArray();
                for (TestFilterType type : TestFilterType.values()) {
                    try {
                        for (int i = 0; i < 1_000_000; ++i) {
                            seed = ThreadLocalRandom.current().nextLong();
                            Hash.setSeed(seed);
                            Filter filter = type.construct(keys, bitsPerKey);
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
}
