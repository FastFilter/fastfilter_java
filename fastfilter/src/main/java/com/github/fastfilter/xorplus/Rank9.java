/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2016 Sebastiano Vigna
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 */
package org.fastfilter.xorplus;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;

/**
 * A fast rank implementation that uses 25% additional space. This is a copy of
 * the (very good) implementation in Sux4J it.unimi.dsi.sux4j.bits.Rank9 by
 * Sebastiano Vigna (see copyright), with small extensions.
 */
public class Rank9 {

    private final long[] bits;
    private final long[] counts;

    public Rank9(BitSet set, long bitCount) {
        long[] bits = set.toLongArray();
        // One zero entry is needed at the end
        bits = Arrays.copyOf(bits, 1 + (int) ((bitCount + 63) / 64));
        this.bits = bits;
        long length = bits.length * 64;
        int numWords = (int) ((length + 63) / 64);
        int numCounts = (int) ((length + 8 * 64 - 1) / (8 * 64)) * 2;
        counts = new long[numCounts + 1];
        long c = 0;
        int pos = 0;
        for (int i = 0; i < numWords; i += 8, pos += 2) {
            counts[pos] = c;
            c += Long.bitCount(bits[i]);
            for (int j = 1; j < 8; j++) {
                counts[pos + 1] |= (i + j <= numWords ? c - counts[pos] : 0x1ffL) << 9 * (j - 1);
                if (i + j < numWords) {
                    c += Long.bitCount(bits[i + j]);
                }
            }
        }
        counts[numCounts] = c;
    }

    /**
     * Get the number of bits set before this position.
     *
     * @param pos the position
     * @return the number of ones
     */
    public long rank(long pos) {
        int word = (int) (pos >>> 6);
        int block = (word >> 2) & ~1;
        int offset = (word & 7) - 1;
        return counts[block] +
                (counts[block + 1] >>> (offset + (offset >>> 32 - 4 & 8)) * 9 & 0x1ff) +
                Long.bitCount(bits[word] & ((1L << pos) - 1));
    }

    /**
     * Get the bit at this position
     *
     * @param pos the position
     * @return 0 or 1
     */
    public long get(long pos) {
        return (bits[(int) (pos >>> 6)] >>> pos) & 1;
    }

    /**
     * Get the bit itself, and a part of the rank (use remainingRank to get the
     * rest).
     *
     * @param pos the position
     * @return the number of ones multiplied by 2, plus the bit (0 or 1)
     */
    public long getAndPartialRank(long pos) {
        int word = (int) (pos >>> 6);
        long x = bits[word];
        return ((Long.bitCount(x & ((1L << pos) - 1))) << 1) + ((x >>> pos) & 1);
    }

    /**
     * Get the second part of the rank (see getAndPartialRank).
     *
     * @param pos the position
     * @return the number of ones
     */
    public long remainingRank(long pos) {
        int word = (int) (pos >>> 6);
        int block = (word >> 2) & ~1;
        int offset = (word & 7) - 1;
        return counts[block] + (counts[block + 1] >>> (offset + (offset >>> 32 - 4 & 8)) * 9 & 0x1ff);
    }

    public int getBitCount() {
        return bits.length * 64 + counts.length * 64;
    }

    public void write(DataOutputStream d) throws IOException {
        d.writeInt(bits.length);
        for (long bit : bits) {
            d.writeLong(bit);
        }
        d.writeInt(counts.length);
        for (long count : counts) {
            d.writeLong(count);
        }
    }

    public Rank9(DataInputStream in) throws IOException {
        bits = new long[in.readInt()];
        for (int i = 0; i < bits.length; i++) {
            bits[i] = in.readLong();
        }
        counts = new long[in.readInt()];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = in.readLong();
        }
    }

}
