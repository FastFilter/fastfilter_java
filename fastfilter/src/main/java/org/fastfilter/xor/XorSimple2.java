package org.fastfilter.xor;

import org.fastfilter.utils.Hash;

/**
 * The same as xor simple, but with the alternative construction that ensures
 * most empty entries are in the third part of the table.
 */
public class XorSimple2 extends XorSimple {

    public static XorSimple construct(long[] keys) {
        return new XorSimple(keys);
    }

    public XorSimple2(long[] keys) {
        super(keys);
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
        int[][] Q = new int[3][blockLength];
        int[] qi = new int[3];
        for (int i = 0; i < C.length; i++) {
            if (C[i] == 1) {
                int b = i / blockLength;
                Q[b][qi[b]++] = i;
            }
        }
        int si = 0;
        while (si < 2 * keys.length && qi[0] > 0 || qi[1] > 0 || qi[2] > 0) {
            int i;
            if (qi[0] > 0) {
                i = Q[0][--qi[0]];
            } else if (qi[1] > 0) {
                i = Q[1][--qi[1]];
            } else if (qi[2] > 0) {
                i = Q[2][--qi[2]];
            } else {
                throw new AssertionError();
            }
            if (C[i] == 1) {
                long x = H[i];
                stack[si++] = x;
                stack[si++] = i;
                for (int j = 0; j < 3; j++) {
                    int index = h(x, j);
                    C[index]--;
                    if (C[index] == 1) {
                        int b = index / blockLength;
                        Q[b][qi[b]++] = index;
                    }
                    H[index] ^= x;
                }
            }
        }
        return si == 2 * keys.length;
    }

}
