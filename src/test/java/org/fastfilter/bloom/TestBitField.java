package org.fastfilter.bloom;

import static org.junit.Assert.assertEquals;

import java.util.BitSet;
import java.util.Random;

import org.fastfilter.bloom.SuccintCountingBloom.BitField;
import org.junit.Test;

public class TestBitField {

    @Test
    public void testRandomized() {
        int size = 1000;
        BitSet set = new BitSet();
        set.set(size + 200);
        BitField field = new BitField(size + 200);
        Random r = new Random(1);
        for (int i = 0; i < 1000000; i++) {
            int index = r.nextInt(size);
            int n = r.nextInt(200);
            int toIndex = index + n;
            long x = r.nextInt(2);
            switch (r.nextInt(5)) {
            case 0:
                set.set(index);
                field.set(index);
                assertEquals(set.get(index) ? 1 : 0, field.get(index));
                break;
            case 1:
                set.clear(index);
                field.clear(index);
                assertEquals(set.get(index) ? 1 : 0, field.get(index));
                break;
            case 2:
                assertEquals(set.get(index, toIndex).cardinality(), field.getBitCount(index, toIndex));
                break;
            case 3:
                long a = insert(set, index, x);
                long b = field.insertInLong(index, x);
                assertEquals(a, b);
                for (int idx = index; idx < index + 100; idx++) {
                    assertEquals(set.get(idx) ? 1 : 0, field.get(idx));
                }
                break;
            case 4:
                n = Math.min(n, set.get(index, index + 200).cardinality());
                assertEquals(select(set, index, n), field.select(index, n));
                break;
            }
        }

    }

    private int select(BitSet set, int index, int bitCount) {
        for (; bitCount > 0; index++) {
            if (set.get(index)) {
                bitCount--;
            }
            if (bitCount == 0) {
                return index;
            }
        }
        return index - 1;
    }

    private long insert(BitSet set, int index, long x) {
        int to = (index + 64) / 64 * 64;
        boolean result = set.get(to - 1);
        for (int i = to - 1; i > index; i--) {
            set.set(i, set.get(i - 1));
        }
        set.set(index, x == 1);
        return result ? 1 : 0;
    }

}
