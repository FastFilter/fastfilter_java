package org.fastfilter.gcs;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.fastfilter.gcs.BitBufferDirect;
import org.junit.Test;

public class BitBufferTest {

    public static void main(String... args) {
        performanceTest();
        performanceTest2();
        performanceTest();
        performanceTest2();
        performanceTest();
        performanceTest2();
    }

    private static void performanceTest() {
        int size = 1024;
        BitBufferDirect buff = new BitBufferDirect(100 * size);
        long x0 = 0;
        for (int i = 0; i < size; i++) {
            buff.writeNumber(i, 10);
            x0 += i;
        }
        long time = System.nanoTime();
        for (int test = 0; test < 100000; test++) {
            long x1 = 0;
            buff.seek(0);
            for (int i = 0; i < size; i++) {
                long a = buff.readNumber(10);
                x1 += a;
            }
            if (x0 != x1) {
                throw new AssertionError();
            }
        }
        time = System.nanoTime() - time;
        System.out.println(time / 100000. / size + " ns/key BitBufferDirect");
    }

    private static void performanceTest2() {
        int size = 1024;
        BitBufferDirect buff = new BitBufferDirect(100 * size);
        long x0 = 0;
        for (int i = 0; i < size; i++) {
            buff.writeNumber(i, 10);
            x0 += i;
        }
        long time = System.nanoTime();
        for (int test = 0; test < 100000; test++) {
            long x1 = 0;
            buff.seek(0);
            for (int i = 0; i < size; i++) {
                long a = buff.readNumber(10);
                x1 += a;
            }
            if (x0 != x1) {
                throw new AssertionError();
            }
        }
        time = System.nanoTime() - time;
        System.out.println(time / 100000. / size + " ns/key BitBuffer");
    }

    @Test
    public void testGolombRiceCoding() {
        assertEquals("0", getRice(0, 0));
        assertEquals("10", getRice(1, 0));
        assertEquals("110", getRice(2, 0));
        assertEquals("11..10", getRice(15, 0));
        assertEquals("00", getRice(0, 1));
        assertEquals("01", getRice(1, 1));
        assertEquals("100", getRice(2, 1));
        assertEquals("11..101", getRice(15, 1));
        assertEquals("000", getRice(0, 2));
        assertEquals("001", getRice(1, 2));
        assertEquals("010", getRice(2, 2));
        assertEquals("111011", getRice(15, 2));
        assertEquals("0000", getRice(0, 3));
        assertEquals("0001", getRice(1, 3));
        assertEquals("0010", getRice(2, 3));
        assertEquals("10111", getRice(15, 3));
        for (int shift = 1; shift < 60; shift++) {
            for (int i = 1; i < 100; i++) {
                getRice(i, shift);
            }
            for (int i = 10; i < 100000; i *= 4) {
                getRice(i, shift);
            }
        }
        Random r = new Random();
        for (int i = 0; i < 1000; i++) {
            getRice(r.nextLong() & 0x7fffffffL, 60);
        }
    }

    private static String getRice(long value, int shift) {
        BitBufferDirect buff = new BitBufferDirect(128 * 1024);
        buff.writeGolombRice(shift, value);
        int size = getGolombRiceSize(shift, value);
        buff.seek(0);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < size; i++) {
            b.append((char) ('0' + buff.readBit()));
        }
        String s = b.toString();
        s = s.replaceFirst("^111111*", "11..1");
        return s;
    }

    public static int getGolombRiceSize(int shift, long value) {
        return (int) ((value >>> shift) + 1 + shift);
    }

    @Test
    public void testEliasDeltaRoundtrip() {
        Random r = new Random(1);
        for (int i = 0; i < 1000; i++) {
            BitBufferDirect buff = new BitBufferDirect(8 * 1024 * 1024);
            long val = (r.nextLong() & 0xfffffffL) + 1;
            buff.writeEliasDelta(val);
            assertEquals(buff.position(), getEliasDeltaSize(val));
            buff.writeNumber(123, 10);
            int pos = buff.position();
            byte[] data = buff.toByteArray();
            assertEquals((pos + 64 + 63) / 64 * 8, data.length);
            buff = new BitBufferDirect(buff.toByteArray());
            assertEquals(val, buff.readEliasDelta());
            assertEquals(123, buff.readNumber(10));
            assertEquals(pos, buff.position());
        }
    }

    @Test
    public void testNumberRoundtrip() {
        // with regular BitBuffer, the max bit count is 65 (that is, 0..64)
        int maxBitCount = 58;
        Random r = new Random(1);
        BitBufferDirect buff = new BitBufferDirect(8 * 1024 * 1024);
        for (int i = 0; i < 1000; i++) {
            long val = r.nextLong();
            int bitCount = r.nextInt(maxBitCount);
            if (bitCount < 64) {
                val &= ((1L << bitCount) - 1);
            }
            buff.writeNumber(val, bitCount);
        }
        buff.seek(0);
        r = new Random(1);
        for (int i = 0; i < 1000; i++) {
            long val = r.nextLong();
            int bitCount = r.nextInt(maxBitCount);
            if (bitCount < 64) {
                val &= ((1L << bitCount) - 1);
            }
            long x = buff.readNumber(bitCount);
            assertEquals(val, x);
        }
    }

    @Test
    public void testEliasDeltaCoding() {
        assertEquals("1", getEliasDelta(1));
        assertEquals("0100", getEliasDelta(2));
        assertEquals("0101", getEliasDelta(3));
        assertEquals("01100", getEliasDelta(4));
        assertEquals("01111", getEliasDelta(7));
        for (int i = 1; i < 100; i++) {
            getEliasDelta(i);
        }
        for (int i = 10; i < 1000000000; i *= 1.1) {
            getEliasDelta(i);
        }
    }

    static String getEliasDelta(int value) {
        BitBufferDirect buff = new BitBufferDirect(8 * 1024 * 1024);
        buff.writeEliasDelta(value);
        assertEquals(buff.position(), getEliasDeltaSize(value));
        int size = buff.position();
        buff.seek(0);
        long test = buff.readEliasDelta();
        assertEquals(value, test);
        assertEquals(size, buff.position());
        buff.seek(0);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < size; i++) {
            b.append((char) ('0' + buff.readBit()));
        }
        String s = b.toString();
        return s;
    }

    @Test
    public void testWriteBuffer() {
        BitBufferDirect buff = new BitBufferDirect(8000);
        for (int i = 1; i < 100; i++) {
            BitBufferDirect b = new BitBufferDirect(160);
            b.writeEliasDelta(i);
            assertEquals(b.position(), getEliasDeltaSize(i));
            buff.write(b);
        }
        buff.seek(0);
        for (int i = 1; i < 100; i++) {
            assertEquals(i, buff.readEliasDelta());
        }
    }

    @Test
    public void testSeek() {
        BitBufferDirect buff = new BitBufferDirect(8000);
        for (int i = 0; i < 100; i++) {
            buff.seek(10 * i);
            buff.writeNumber(i, 8);
        }
        buff = new BitBufferDirect(buff.toByteArray());
        for (int i = 0; i < 100; i++) {
            buff.seek(10 * i);
            assertEquals(i, buff.readNumber(8));
        }
    }

    public static int getEliasDeltaSize(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException();
        }
        int q = 64 - Long.numberOfLeadingZeros(value);
        int qq = 31 - Integer.numberOfLeadingZeros(q);
        int len = qq + qq + q;
        return len;
    }

}
