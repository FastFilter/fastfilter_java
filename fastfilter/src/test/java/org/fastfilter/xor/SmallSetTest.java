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
        XorBinaryFuse16.construct(new long[]{0xef9bddc5166c081cL, 0x33bf87adaa46dcfcL});
    }
    
    @Test
    public void verySmallSizes() {
        int n = 1;
        for (; n < 2000; n++) {
            testWithSize(n);
        }
        for (; n < 20000; n += 7) {
            testWithSize(n);
        }
    }
        
    @Test
    public void smallSizes() {
        long lastTime = System.currentTimeMillis();
        for (int n = 1; n < 1_500_000; n = (int) ((n * 1.01) + 7)) {
            XorBinaryFuse8 f = testWithSize(n);
            long now = System.currentTimeMillis();
            if (now - lastTime > 5000) {
                lastTime = now;
                System.out.println("n=" + n + " " + f.toString());
            }
        }
    }
    
    @Test
    public void smallSizes16() {
        long lastTime = System.currentTimeMillis();
        for (int n = 1; n < 1_500_000; n = (int) ((n * 1.01) + 7)) {
            XorBinaryFuse16 f = testWithSize16(n);
            long now = System.currentTimeMillis();
            if (now - lastTime > 5000) {
                lastTime = now;
                System.out.println("n=" + n + " " + f.toString());
            }
        }
    }
    
    private static XorBinaryFuse8 testWithSize(int n) {
        long[] keys = new long[n];
        for (int i = 0; i < n; i++) {
            keys[i] = i;
        }
        return XorBinaryFuse8.construct(keys);
    }

    private static XorBinaryFuse16 testWithSize16(int n) {
        long[] keys = new long[n];
        for (int i = 0; i < n; i++) {
            keys[i] = i;
        }
        return XorBinaryFuse16.construct(keys);
    }

}
