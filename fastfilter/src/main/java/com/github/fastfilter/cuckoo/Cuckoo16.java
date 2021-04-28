package org.fastfilter.cuckoo;

import org.fastfilter.Filter;
import org.fastfilter.utils.Hash;

import java.util.Random;

/**
 * This is a Cuckoo Filter implementation.
 * It uses log(1/fpp)+3 bits per key.
 *
 * See "Cuckoo Filter: Practically Better Than Bloom".
 */
public class Cuckoo16 implements Filter {

    private static final int FINGERPRINT_BITS = 16;
    private static final int ENTRIES_PER_BUCKET = 4;
    private static final long FINGERPRINT_MASK = (1L << FINGERPRINT_BITS) - 1;

    private final long seed;
    private final long[] data;
    private final int bucketCount;
    private final Random random = new Random(1);

    public static Cuckoo16 construct(long[] keys) {
        int len = keys.length;
        while (true) {
            try {
                Cuckoo16 f = new Cuckoo16((int) (len / 0.95));
                for (long k : keys) {
                    f.insert(k);
                }
                return f;
            } catch (IllegalStateException e) {
                // table full: try again
            }
        }
    }

    public Cuckoo16(int capacity) {
        // bucketCount needs to be even for bucket2 to work
        bucketCount = Math.max(1, (int) Math.ceil((double) capacity / ENTRIES_PER_BUCKET) / 2 * 2);
        this.data = new long[bucketCount];
        this.seed = Hash.randomSeed();
    }

    public void insert(long key) {
        long hash = Hash.hash64(key, seed);
        insertFingerprint(getBucket(hash), getFingerprint(hash));
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        int bucket = getBucket(hash);
        int fingerprint = getFingerprint(hash);
        if (bucketContains(bucket, fingerprint)) {
            return true;
        }
        int bucket2 = getBucket2(bucket, fingerprint);
        return bucketContains(bucket2, fingerprint);
    }

    private int getBucket(long hash) {
        return Hash.reduce((int) hash, bucketCount);
    }

    private int getFingerprint(long hash) {
        // unfortunately, this is needed, otherwise the fpp increases with a few
        // million of entries
        hash = Hash.hash64(hash, seed);
        int fingerprint =  (int) (hash & FINGERPRINT_MASK);
        // fingerprint 0 is not allowed -
        // an alternative, with a slightly lower false positive rate with a
        // small fingerprint, would be: shift until it's not zero (but it
        // doesn't sound like it would be faster)
        // assume that this doesn't use branching
        return Math.max(1, fingerprint);
    }

    private int getBucket2(int bucket, int fingerprint) {
        // from the Murmur hash algorithm
        // some mixing (possibly not that great, but should be fast)
        long hash = fingerprint * 0xc4ceb9fe1a85ec53L;
        // we don't use xor; instead, we ensure bucketCount is even,
        // and bucket2 = bucketCount - bucket - y,
        // and if negative add the bucketCount,
        // where y is 1..bucketCount - 1 and odd -
        // that way, bucket2 is never the original bucket,
        // and running this twice will give the original bucket, as needed
        int r = (Hash.reduce((int) hash, bucketCount >> 1) << 1) + 1;
        int b2 = bucketCount - bucket - r;
        // not sure how to avoid this branch
        if (b2 < 0) {
            b2 += bucketCount;
        }
        return b2;
    }

    private boolean bucketContains(int bucket, int fingerprint) {
        long allFingerprints = data[bucket];
        // from https://graphics.stanford.edu/~seander/bithacks.html#ZeroInWord
        long v = allFingerprints ^ (fingerprint * 0x0001000100010001L);
        long hasZeroByte = ~((((v & 0x7fff7fff7fff7fffL) + 0x7fff7fff7fff7fffL) | v) | 0x7fff7fff7fff7fffL);
        return hasZeroByte != 0;
    }

    private int getFingerprintAt(int bucket, int entry) {
        return (int) ((data[bucket] >>> (FINGERPRINT_BITS * entry)) & FINGERPRINT_MASK);
    }

    private void setFingerprintAt(int bucket, int entry, int fingerprint) {
        data[bucket] &= ~(FINGERPRINT_MASK << (FINGERPRINT_BITS * entry));
        data[bucket] |= (long) fingerprint << (FINGERPRINT_BITS * entry);
    }

    private boolean bucketInsert(int bucket, int fingerprint) {
        for (int entry = 0; entry < ENTRIES_PER_BUCKET; entry++) {
            int fp = getFingerprintAt(bucket, entry);
            if (fp == 0) {
                setFingerprintAt(bucket, entry, fingerprint);
                return true;
            } else if (fp == fingerprint) {
                return true;
            }
        }
        return false;
    }

    private void insertFingerprint(int bucket, int fingerprint) {
        if (bucketInsert(bucket, fingerprint)) {
            return;
        }
        int bucket2 = getBucket2(bucket, fingerprint);
        if (bucketInsert(bucket2, fingerprint)) {
            return;
        }
        swap(bucket2, fingerprint);
    }

    private void swap(int bucket, int fingerprint) {
        for (int n = 0; n < 1000; n++) {
            int entry = random.nextInt() & (ENTRIES_PER_BUCKET - 1);
            fingerprint = bucketsSwap(bucket, entry, fingerprint);
            bucket = getBucket2(bucket, fingerprint);
            if (bucketInsert(bucket, fingerprint)) {
                return;
            }
        }
        throw new IllegalStateException("Table full");
    }

    private int bucketsSwap(int bucket, int entry, int fingerprint) {
        int old = getFingerprintAt(bucket, entry);
        setFingerprintAt(bucket, entry, fingerprint);
        return old;
    }

    public long getBitCount() {
        return FINGERPRINT_BITS * ENTRIES_PER_BUCKET * bucketCount;
    }

}
