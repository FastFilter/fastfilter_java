package org.fastfilter.gcs;

import java.nio.ByteBuffer;

public class BitBufferDirect {

    public final ByteBuffer data;
    private int pos;
    private long current;

    public BitBufferDirect(long bits) {
        this.data = ByteBuffer.allocateDirect((int)((bits + 7) / 8));
        // this.data.order(ByteOrder.nativeOrder());
    }

    public BitBufferDirect(byte[] byteArray) {
        this.data = ByteBuffer.allocateDirect(byteArray.length);
        data.put(byteArray);
        data.flip();
        // this.data.order(ByteOrder.nativeOrder());
    }

    public int position() {
        return pos;
    }

    public void seek(int pos) {
        this.pos = pos;
        current = data.getLong(pos >>> 3);
    }

    public long readBit() {
        return readNumber(1);
    }

    public void writeBit(long x) {
        writeNumber(x, 1);
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

    public long readNumber(long pos, int bitCount) {
        long current = data.getLong((int) (pos >>> 3));
        long x = current >>> (64 - (pos & 7) - bitCount);
        return x & ((1L << bitCount) - 1);
    }

    public long readNumber(int bitCount) {
        long x = current >>> (64 - (pos & 7) - bitCount);
        pos += bitCount;
        current = data.getLong(pos >>> 3);
        return x & ((1L << bitCount) - 1);
    }

    public void writeNumber(long x, int bitCount) {
        if (bitCount == 0) {
            return;
        }
        int remainingBits = 64 - (pos & 63);
        int index = pos >>> 6;
        if (bitCount <= remainingBits) {
            long old = data.getLong(index << 3);
            data.putLong(index << 3, old | (x << (remainingBits - bitCount)));
        } else {
            long old = data.getLong(index << 3);
            data.putLong(index << 3, old | (x >>> (bitCount - remainingBits)));
            old = data.getLong((index + 1) << 3);
            data.putLong((index + 1) << 3, old | (x << (64 - bitCount + remainingBits)));
        }
        pos += bitCount;
    }

    public int readUntilZero(int pos) {
        long current = data.getLong((int) (pos >>> 3));
        long x = ~current << ((pos & 7));
        return Long.numberOfLeadingZeros(x);
    }

    public void write(BitBufferDirect bits) {
        int count = bits.pos;
        bits.seek(0);
        int i = 0;
        for (; i < count - 31; i += 32) {
            writeNumber(bits.readNumber(32), 32);
        }
        for (; i < count; i++) {
            writeBit(bits.readBit());
        }
    }

    public byte[] toByteArray() {
        int oldPos = data.position();
        byte[] d = new byte[(pos + 64 + 63) / 64 * 8];
        data.position(0);
        data.get(d, 0, d.length);
        data.position(oldPos);
        return d;
    }

}
