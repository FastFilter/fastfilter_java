package org.fastfilter;

import org.fastfilter.utils.Hash;
import org.junit.Test;

import java.util.concurrent.ThreadLocalRandom;

import static junit.framework.TestCase.assertTrue;

public class SimpleFuzzer {


    @Test
    public void fuzzTest() {
        long[] keys = new long[]{ 1, 2, 3};
        long seed = 0;

        for (FilterType type : FilterType.values()) {
            try {
                for (int i = 0; i < 1000_000; ++i) {
                    seed = ThreadLocalRandom.current().nextLong();
                    Hash.setSeed(seed);
                    Filter filter = type.construct(keys, 8);
                    assertTrue(seed + "/" + type, filter.mayContain(1));
                    assertTrue(seed + "/" + type, filter.mayContain(2));
                    assertTrue(seed + "/" + type, filter.mayContain(3));
                }
            } catch (Exception e) {
                System.out.println(seed + "/" + type);
                throw e;
            }
        }
    }
}
