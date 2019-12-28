package org.fastfilter.gcs;

import java.util.Arrays;

/**
 * A simple bit buffer. It is partially optimized for reading, but writing is
 * relatively slow. Writing will only add bits (bitwise or with existing bits).
 */
public class BitBuffer {

    public final long[] data;
    private int pos;

    public BitBuffer(long bits) {
        this.data = new long[(int)((bits + 63) / 64)];
    }

    public void write(BitBuffer bits) {
        int count = bits.pos;
        bits.pos = 0;
        int i = 0;
        for (; i < count - 31; i += 32) {
            writeNumber(bits.readNumber(32), 32);
        }
        for (; i < count; i++) {
            writeBit(bits.readBit());
        }
    }

    public int position() {
        return pos;
    }

    public void seek(int pos) {
        this.pos = pos;
    }

    public long readBit() {
        return (data[pos >>> 6] >>> (63 - (pos++ & 63))) & 1;
    }

    public void writeBit(long x) {
        if (x == 1) {
            data[pos >>> 6] |= 1L << (63 - (pos & 63));
        }
        pos++;
    }

    public void writeGolombRice(int shift, long value) {
        writeGolombRiceFast(shift, value);
    }

    public void writeGolombRiceFast(int shift, long value) {
        long q = value >>> shift;
        if (q < 63) {
            long m = (2L << q) - 2;
            writeNumber(m, (int) (q + 1));
        } else {
            for (int i = 0; i < q; i++) {
                writeBit(1);
            }
            writeBit(0);
        }
        writeNumber(value & ((1L << shift) - 1), shift);
    }

    public void writeEliasDelta(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException();
        }
        int q = 64 - Long.numberOfLeadingZeros(value);
        int qq = 31 - Integer.numberOfLeadingZeros(q);
        for (int i = 0; i < qq; i++) {
            writeBit(0);
        }
        for (int i = qq; i >= 0; i--) {
            writeBit((q >>> i) & 1);
        }
        for (int i = q - 2; i >= 0; i--) {
            writeBit((value >>> i) & 1);
        }
    }

    public long readEliasDelta() {
        int qq = 0;
        while (readBit() == 0) {
            qq++;
        }
        long q = 1;
        for (int i = qq; i > 0; i--) {
            q = (q << 1) | readBit();
        }
        long x = 1;
        for (long i = q - 2; i >= 0; i--) {
            x = (x << 1) | readBit();
        }
        return x;
    }

    /**
     * Read a number.
     *
     * @param bitCount the number of bits, at most 63
     * @return the value
     */
    public long readNumber(int bitCount) {
        long x = readNumber(pos, bitCount);
        pos += bitCount;
        return x;
    }

    /**
     * Read a number.
     *
     * @param pos the position
     * @param bitCount the number of bits, at most 63
     * @return the value
     */
    public long readNumber(long pos, int bitCount) {
        if (bitCount == 0) {
            return 0;
        }
        int remainingBits = 64 - ((int) pos & 63);
        int index = (int) (pos >>> 6);
        long x = data[index];
        if (bitCount <= remainingBits) {
            x >>>= remainingBits - bitCount;
            return x & ((1L << bitCount) - 1);
        }
        x = x & ((1L << remainingBits) - 1);
        return (x << (bitCount - remainingBits)) |
                (data[index + 1] >>> (64 - bitCount + remainingBits));
    }

    /**
     * Fold a signed number into an unsigned number. Negative numbers are odd,
     * and positive numbers are even. For example, -5 is converted to 11, and 5
     * to 10.
     *
     * @param x a signed number
     * @return an unsigned number
     */
    public static long foldSigned(long x) {
        return x > 0 ? x * 2 - 1 : -x * 2;
    }

    /**
     * Unfold an unsigned number into a signed number.
     *
     * @param x an unsigned number
     * @return a signed number
     */
    public static long unfoldSigned(long x) {
        return ((x & 1) == 1) ? (x + 1) / 2 : -(x / 2);
    }

    public int readUntilZero(int pos) {
        int remainingBits = 64 - (pos & 63);
        int index = pos >>> 6;
        long x = data[index] << (64 - remainingBits);
        int count = Long.numberOfLeadingZeros(~x);
        if (count < remainingBits) {
            return count;
        }
        return readUntilZeroMore(count, index);
    }

    private int readUntilZeroMore(int count, int index) {
        while (true) {
            long x = data[++index];
            if (x == -1L) {
                count += 64;
                continue;
            }
            return count + Long.numberOfLeadingZeros(~x);
        }
    }

    /**
     * Write a number of bits. The most significant bit is written first.
     *
     * @param x the number
     * @param bitCount the number of bits, at most 63
     */
    public void writeNumber(long x, int bitCount) {
        if (bitCount == 0) {
            return;
        }
        int remainingBits = 64 - (pos & 63);
        int index = pos >>> 6;
        if (bitCount <= remainingBits) {
            data[index] |= x << (remainingBits - bitCount);
        } else {
            data[index] |= x >>> (bitCount - remainingBits);
            data[index + 1] |= x << (64 - bitCount + remainingBits);
        }
        pos += bitCount;
    }

    public void skipGolombRice(int shift) {
        pos = skipGolombRice(pos, shift);
    }

    public int skipGolombRice(int pos, int shift) {
        int q = readUntilZero(pos);
        return pos + q + 1 + shift;
    }

    public void clear() {
        Arrays.fill(data, 0);
    }

    public static int getEliasDeltaSize(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException();
        }
        int q = 64 - Long.numberOfLeadingZeros(value);
        int qq = 31 - Integer.numberOfLeadingZeros(q);
        return qq + qq + q;
    }

}
