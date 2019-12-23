package org.fastfilter.bloom.count;

import org.fastfilter.utils.Hash;
import org.junit.Test;

import static org.junit.Assert.*;

public class SuccinctCountingBlockedBloomTest {

    @Test
    public void indexOutOfBoundsRegression() {
        long seed = 6049486880293779298L;
        long[] keys = new long[]{1, 2, 3};
        Hash.setSeed(seed);
        SuccinctCountingBlockedBloom filter = SuccinctCountingBlockedBloom.construct(keys, 8);
        assertTrue(filter.mayContain(1));
        assertTrue(filter.mayContain(2));
        assertTrue(filter.mayContain(3));
    }

}