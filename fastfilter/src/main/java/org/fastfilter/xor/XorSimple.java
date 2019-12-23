package org.fastfilter.xor;

import org.fastfilter.Filter;
import org.fastfilter.utils.Hash;

import java.util.Random;

public class XorSimple implements Filter {

    private long seed;
    private byte[] data;
    int blockLength;

    public long getBitCount() {
        return data.length * 8;
    }

    public static XorSimple construct(long[] keys) {
        return new XorSimple(keys);
    }

    XorSimple(long[] keys) {
        blockLength = (int) ((1.23 * keys.length) + 32) / 3;
        data = new byte[3 * blockLength];
        while (true) {
            seed = new Random().nextLong();
            long[] stack = new long[keys.length * 2];
            if (map(keys, seed, stack)) {
                assign(stack, data);
                return;
            }
        }
    }

    boolean map(long[] keys, long seed, long[] stack) {
        int[] C = new int[3 * blockLength];
        long[] H = new long[3 * blockLength];
        for (long k : keys) {
            long x = Hash.hash64(k, seed);
            for (int j = 0; j < 3; j++) {
                int index = h(x, j);
                C[index]++;
                H[index] ^= x;
            }
        }
        int[] Q = new int[3 * blockLength];
        int qi = 0;
        for (int i = 0; i < C.length; i++) {
            if (C[i] == 1) {
                Q[qi++] = i;
            }
        }
        int si = 0;
        while (si < 2 * keys.length && qi > 0) {
            int i = Q[--qi];
            if (C[i] == 1) {
                long x = H[i];
                stack[si++] = x;
                stack[si++] = i;
                for (int j = 0; j < 3; j++) {
                    int index = h(x, j);
                    C[index]--;
                    if (C[index] == 1) {
                        Q[qi++] = index;
                    }
                    H[index] ^= x;
                }
            }
        }
        return si == 2 * keys.length;
    }

    void assign(long[] stack, byte[] b) {
        for(int stackPos = stack.length; stackPos > 0;) {
            int index = (int) stack[--stackPos];
            long x = stack[--stackPos];
            b[index] = (byte) (fingerprint(x) ^ b[h(x, 0)] ^ b[h(x, 1)] ^ b[h(x, 2)]);
        }
    }

    int h(long x, int index) {
        return Hash.reduce((int) Long.rotateLeft(x, index * 21), blockLength) + index * blockLength;
    }

    @Override
    public boolean mayContain(long key) {
        long x = Hash.hash64(key, seed);
        return fingerprint(x) == (data[h(x, 0)] ^ data[h(x, 1)] ^ data[h(x, 2)]);
    }

    private byte fingerprint(long x) {
        return (byte) x;
    }

}
