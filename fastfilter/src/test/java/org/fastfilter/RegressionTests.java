package org.fastfilter;

import org.fastfilter.utils.Hash;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.fastfilter.FilterType.*;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class RegressionTests {


    @Parameterized.Parameters(name = "{0}/{1}/*")
    public static Object[][] regressionCases() {
        return new Object[][] {
                {BLOCKED_BLOOM, 872153271794238865L, new long[]{1, 2, 3}},
                {SUCCINCT_COUNTING_BLOCKED_BLOOM_RANKED, -401700599714690558L, new long[]{1, 2, 3}},
                {SUCCINCT_COUNTING_BLOCKED_BLOOM, 6049486880293779298L, new long[]{1, 2, 3}},
                // actual this one is impossible to reproduce because of the volatile seed
                {XOR_SIMPLE, 6831634639270950343L, new long[]{1, 2, 3}},
                {CUCKOO_8, 6335419348330489927L, new long[]{1, 2, 3}},
                {CUCKOO_16, -9087718164446355442L, new long[]{1, 2, 3}}
        };
    }

    private final FilterType type;
    private final long seed;
    private final long[] keys;

    public RegressionTests(FilterType type, long seed, long[] keys) {
        this.type = type;
        this.seed = seed;
        this.keys = keys;
    }

    @Test
    public void regressionTest() {
        Hash.setSeed(seed);
        Filter filter = type.construct(keys, 8);
        for (long key : keys) {
            assertTrue(filter.mayContain(key));
        }
    }
}
