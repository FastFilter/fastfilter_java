package org.fastfilter.ribbon;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.fastfilter.Filter;
import org.fastfilter.utils.Hash;

/**
 * A (non-homogeneous) Ribbon filter with a fixed ribbon width w = 64.
 *
 * A Ribbon filter is a retrieval data structure: for every key x in the
 * set, solving a banded linear system over GF(2) yields a small function
 * f(x) that returns an r-bit fingerprint. Construction can fail (linear
 * dependence among the equations) and is retried with a new seed, and -
 * if all seed attempts at a given slot count fail - with more slots.
 *
 * Martin Dietzfelbinger, Stefan Walzer, [Efficient Gauss Elimination for
 * Near-Quadratic Matrices with One Short Random Block per
 * Row](https://arxiv.org/abs/1907.04750), ESA 2019.
 *
 * Peter C. Dillinger, Stefan Walzer, [Ribbon filter: practically smaller
 * than Bloom and Xor](https://arxiv.org/abs/2103.02515), arXiv:2103.02515,
 * 2021.
 *
 * This is the "naive" query implementation: each lookup does an O(w * r)
 * per-column dot product against the solved band, rather than the
 * Interleaved Column-Major Layout (ICML) the papers above use for fast
 * queries. It is kept under this name so a future ICML-backed
 * implementation can use the plain RibbonFilter / RIBBON name once it
 * exists.
 */
public class RibbonNaiveFilter implements Filter {

    private static final int DEFAULT_RESULT_BITS = 8;
    private static final int COEFF_BITS = 64;
    private static final int MAX_ATTEMPTS_PER_SIZE = 256;
    private static final double INITIAL_OVERHEAD_FACTOR = 0.10;
    private static final double OVERHEAD_GROWTH_FACTOR = 1.5;

    private final int size;
    private final int resultBits;
    private final int numSlots;
    private final int numStarts;
    private final long seed;
    private final byte[] solution;
    private final int bitCount;

    private RibbonNaiveFilter(int size, int resultBits, int numSlots, int numStarts, long seed, byte[] solution) {
        this.size = size;
        this.resultBits = resultBits;
        this.numSlots = numSlots;
        this.numStarts = numStarts;
        this.seed = seed;
        this.solution = solution;
        this.bitCount = numSlots * resultBits;
    }

    public static RibbonNaiveFilter construct(long[] keys) {
        return construct(keys, DEFAULT_RESULT_BITS);
    }

    public static RibbonNaiveFilter construct(long[] keys, int resultBits) {
        if (resultBits < 1 || resultBits > 8) {
            throw new IllegalArgumentException("resultBits must be between 1 and 8");
        }
        int size = keys.length;
        double overheadFactor = INITIAL_OVERHEAD_FACTOR;
        while (true) {
            int numStarts = (int) Math.ceil(size * (1 + overheadFactor));
            if (numStarts < 1) {
                numStarts = 1;
            }
            int numSlots = numStarts + COEFF_BITS - 1;
            for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_SIZE; attempt++) {
                long seed = Hash.randomSeed();
                long[] coeff = new long[numSlots];
                byte[] result = new byte[numSlots];
                if (band(keys, seed, numStarts, resultBits, coeff, result)) {
                    byte[] solution = backSubstitute(coeff, result, numSlots, resultBits);
                    return new RibbonNaiveFilter(size, resultBits, numSlots, numStarts, seed, solution);
                }
            }
            overheadFactor *= OVERHEAD_GROWTH_FACTOR;
        }
    }

    private static boolean band(long[] keys, long seed, int numStarts, int resultBits, long[] coeff, byte[] result) {
        int mask = (1 << resultBits) - 1;
        for (long key : keys) {
            long hash = Hash.hash64(key, seed);
            int s = Hash.reduce((int) hash, numStarts);
            long c = Long.rotateLeft(hash, 21) | 1L;
            int r = (int) (Long.rotateLeft(hash, 42) & mask);
            while (true) {
                int i = Long.numberOfTrailingZeros(c);
                int p = s + i;
                c >>>= i;
                if (coeff[p] == 0) {
                    coeff[p] = c;
                    result[p] = (byte) r;
                    break;
                }
                c ^= coeff[p];
                r ^= result[p];
                s = p;
                if (c == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static byte[] backSubstitute(long[] coeff, byte[] result, int numSlots, int resultBits) {
        byte[] solution = new byte[numSlots];
        int mask = (1 << resultBits) - 1;
        for (int i = numSlots - 1; i >= 0; i--) {
            long c = coeff[i];
            if (c == 0) {
                continue;
            }
            int contribution = 0;
            long rest = c & ~1L;
            while (rest != 0) {
                int k = Long.numberOfTrailingZeros(rest);
                rest &= rest - 1;
                contribution ^= solution[i + k];
            }
            solution[i] = (byte) ((result[i] ^ contribution) & mask);
        }
        return solution;
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        int s = Hash.reduce((int) hash, numStarts);
        long c = Long.rotateLeft(hash, 21) | 1L;
        int mask = (1 << resultBits) - 1;
        int expected = (int) (Long.rotateLeft(hash, 42) & mask);

        int actual = 0;
        for (int j = 0; j < resultBits; j++) {
            long columnBits = 0;
            for (int k = 0; k < COEFF_BITS; k++) {
                if (((solution[s + k] >>> j) & 1) != 0) {
                    columnBits |= (1L << k);
                }
            }
            int bit = Long.bitCount(columnBits & c) & 1;
            actual |= bit << j;
        }
        return actual == expected;
    }

    @Override
    public long getBitCount() {
        return bitCount;
    }

    @Override
    public int getSerializedSize() {
        return Integer.BYTES * 5 + Long.BYTES + solution.length * Byte.BYTES;
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        if (buffer.remaining() < getSerializedSize()) {
            throw new IllegalArgumentException("Buffer too small");
        }
        buffer.putInt(size);
        buffer.putInt(resultBits);
        buffer.putInt(numSlots);
        buffer.putInt(numStarts);
        buffer.putLong(seed);
        buffer.putInt(solution.length);
        buffer.put(solution);
    }

    public static RibbonNaiveFilter deserialize(ByteBuffer buffer) {
        if (buffer.remaining() < Integer.BYTES * 5 + Long.BYTES) {
            throw new IllegalArgumentException("Buffer too small");
        }
        final int size = buffer.getInt();
        final int resultBits = buffer.getInt();
        final int numSlots = buffer.getInt();
        final int numStarts = buffer.getInt();
        final long seed = buffer.getLong();
        final int len = buffer.getInt();
        if (buffer.remaining() < len * Byte.BYTES) {
            throw new IllegalArgumentException("Buffer too small");
        }
        final byte[] solution = new byte[len];
        buffer.get(solution);
        return new RibbonNaiveFilter(size, resultBits, numSlots, numStarts, seed, solution);
    }

    @Override
    public void serialize(OutputStream out) throws IOException {
        DataOutputStream dout = new DataOutputStream(out);
        dout.writeInt(size);
        dout.writeInt(resultBits);
        dout.writeInt(numSlots);
        dout.writeInt(numStarts);
        dout.writeLong(seed);
        dout.writeInt(solution.length);
        dout.write(solution);
    }

    public static RibbonNaiveFilter deserialize(InputStream in) throws IOException {
        DataInputStream din = new DataInputStream(in);
        final int size = din.readInt();
        final int resultBits = din.readInt();
        final int numSlots = din.readInt();
        final int numStarts = din.readInt();
        final long seed = din.readLong();
        final int len = din.readInt();
        final byte[] solution = new byte[len];
        din.readFully(solution);
        return new RibbonNaiveFilter(size, resultBits, numSlots, numStarts, seed, solution);
    }
}
