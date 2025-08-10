package org.fastfilter.bloom.count;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.BitSet;
import java.util.Random;

import org.fastfilter.bloom.count.SuccinctCountingBloom.BitField;
import org.junit.jupiter.api.Test;

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
            }
        }

    }

}
