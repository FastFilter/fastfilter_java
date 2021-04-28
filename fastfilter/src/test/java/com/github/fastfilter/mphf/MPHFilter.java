package org.fastfilter.mphf;

import org.fastfilter.Filter;
import org.fastfilter.gcs.BitBuffer;

public class MPHFilter implements Filter {

    private final int mask;
    private final FastEvaluator eval;
    private final int bitCount;

    public static Filter construct(long[] keys, int bitsPerKey) {
        return new MPHFilter(keys, keys.length, bitsPerKey);
    }

    MPHFilter(long[] hashes, int len, int bits) {
        int averageBucketSize = 16;
        int leafSize = 6;
        mask = (1 << bits) - 1;
        BitBuffer fingerprints = new BitBuffer(bits * len);
        // long time = System.nanoTime();
        BitBuffer buff = new Builder().
                averageBucketSize(averageBucketSize).
                leafSize(leafSize).
                fingerprintBits(bits).
                generate(hashes, len, fingerprints);
        // time = System.nanoTime() - time;
        bitCount = len * bits + buff.position();
        // System.out.println("    generate: " + ((double) time / len) + " ns/key");
        buff.seek(0);
        eval = new Builder().
                averageBucketSize(averageBucketSize).
                leafSize(leafSize).
                fingerprintBits(bits).
                evaluator(buff, fingerprints);
    }

    @Override
    public long getBitCount() {
        return bitCount;
    }

    @Override
    public boolean mayContain(long hashCode) {
        int h = eval.getFingerprint(hashCode);
        long h2 = hashCode & mask;
        return h == h2;
    }

}
