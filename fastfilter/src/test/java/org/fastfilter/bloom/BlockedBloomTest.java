package org.fastfilter.bloom;

import org.fastfilter.utils.Hash;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class BlockedBloomTest {

    @Test
    public void testCreateSmallBlockedBloomFilter() {
        Hash.setSeed(872153271794238865L);
        BlockedBloom filter = BlockedBloom.construct(new long[]{1, 2, 3}, 8);
        assertTrue(filter.mayContain(1));
        assertTrue(filter.mayContain(2));
        assertTrue(filter.mayContain(3));
    }

}