package org.fastfilter.xor;

import org.junit.Test;

/**
 * Test small sets.
 */

public class SmallSetTest {

    @Test
    public void small() {
        Xor8.construct(new long[]{0xef9bddc5166c081cL, 0x33bf87adaa46dcfcL});
        Xor16.construct(new long[]{0xef9bddc5166c081cL, 0x33bf87adaa46dcfcL});
        XorBinaryFuse8.construct(new long[]{0xef9bddc5166c081cL, 0x33bf87adaa46dcfcL});
        XorSimple.construct(new long[]{0xef9bddc5166c081cL, 0x33bf87adaa46dcfcL});
        XorSimple2.construct(new long[]{0xef9bddc5166c081cL, 0x33bf87adaa46dcfcL});
    }
}
