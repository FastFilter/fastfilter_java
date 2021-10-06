package org.fastfilter.xorplus;

import org.junit.Test;

/**
 * Test small sets.
 */

public class SmallSetTest {

    @Test
    public void small() {
        XorPlus8.construct(new long[]{0xef9bddc5166c081cL, 0x33bf87adaa46dcfcL});
    }
}
