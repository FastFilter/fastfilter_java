package org.fastfilter.ribbon;

import static org.junit.Assert.assertTrue;

import org.fastfilter.utils.RandomGenerator;
import org.junit.Test;

public class RibbonNaiveFilterTest {

    @Test
    public void noFalseNegativesSmall() {
        assertNoFalseNegatives(10);
        assertNoFalseNegatives(1_000);
    }

    @Test
    public void noFalseNegativesLarge() {
        assertNoFalseNegatives(1_000_000);
    }

    @Test
    public void falsePositiveRateNearExpected() {
        int len = 1_000_000;
        long[] list = new long[len * 2];
        RandomGenerator.createRandomUniqueListFast(list, 0);
        long[] keys = new long[len];
        long[] nonKeys = new long[len];
        for (int i = 0; i < len; i++) {
            keys[i] = list[i];
            nonKeys[i] = list[i + len];
        }
        RibbonNaiveFilter filter = RibbonNaiveFilter.construct(keys);
        int falsePositives = 0;
        for (long nonKey : nonKeys) {
            if (filter.mayContain(nonKey)) {
                falsePositives++;
            }
        }
        double fpp = (double) falsePositives / len;
        // expected ~= 1/256 ~= 0.0039; generous margin to avoid flakiness
        assertTrue("fpp too high: " + fpp, fpp < 0.01);
    }

    private void assertNoFalseNegatives(int len) {
        long[] keys = new long[len];
        RandomGenerator.createRandomUniqueListFast(keys, 0);
        RibbonNaiveFilter filter = RibbonNaiveFilter.construct(keys);
        for (long key : keys) {
            assertTrue("key " + key + " should be present", filter.mayContain(key));
        }
    }
}
