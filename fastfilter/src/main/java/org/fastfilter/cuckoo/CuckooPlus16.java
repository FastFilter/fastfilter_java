package org.fastfilter.cuckoo;

import org.fastfilter.MutableFilter;
import org.fastfilter.utils.Hash;

import java.util.Random;

/**
 * This is a Cuckoo Filter implementation.
 * It uses (1/0.94) * (log(1/fpp)+2) bits per key.
 * Space and speed should be between the regular cuckoo filter and the semi-sort variant.
 *
 * See "Cuckoo Filter: Practically Better Than Bloom".
 */
public class CuckooPlus16 implements MutableFilter {

    private static final int SHIFTED = 1;
    private static final int SECOND = 2;

    private static final int FINGERPRINT_MASK = (1 << (16 - 2)) - 1;

    private short[] data;
    private final long seed;
    private final int bucketCount;
    private final Random random = new Random(1);

    public static CuckooPlus16 construct(long[] keys, long seed) {
        int len = keys.length;
        while (true) {
	        CuckooPlus16 f = new CuckooPlus16((int) (len / 0.94), seed);
	        try {
		        for (long k : keys) {
		            f.add(k);
		        }
		        return f;
	        } catch (IllegalStateException e) {
	        	// table full: try again
	        }
        }
    }

    public CuckooPlus16(int capacity, long seed) {
        // bucketCount needs to be even for bucket2 to work
        bucketCount = (int) Math.ceil((double) capacity) / 2 * 2;
        this.data = new short[bucketCount + 1];
        this.seed = seed;
    }

    @Override
    public void add(long key) {
        long hash = Hash.hash64(key, seed);
        int bucket = getBucket(hash);
        long fingerprint = getFingerprint(hash);
        long x = fingerprint << 2;
        if (bucketInsert(bucket, x)) {
            return;
        }
        int bucket2 = getBucket2(bucket, x);
        if (bucketInsert(bucket2, x | SECOND)) {
            return;
        }
        if (random.nextBoolean()) {
            swap(bucket, x);
        } else {
            swap(bucket2, x | SECOND);
        }
    }

    private void set(int index, long x) {
    	data[index] = (short) x;
    }

    private long get(int index) {
    	return data[index] & 0xffff;
    }

    private boolean bucketInsert(int index, long x) {
        long fp = get(index);
        if (fp == 0) {
            set(index, x);
            return true;
        } else if (fp == x) {
            // already inserted
            return true;
        }
        index++;
        x |= SHIFTED;
        fp = get(index);
        if (fp == 0) {
            set(index, x);
            return true;
        } else {
            // already inserted?
            return fp == x;
        }
    }

    private void swap(int index, long x) {
        for (int n = 0; n < 10000; n++) {
            if (random.nextBoolean()) {
                index++;
                x |= SHIFTED;
            }
            long old = get(index);
            set(index, x);
            if (old == 0) {
                throw new AssertionError();
            }
            index = getBucket2(index, old);
            old ^= SECOND;
            old &= ~SHIFTED;
            if (bucketInsert(index, old)) {
                return;
            }
            x = old;
        }
        throw new IllegalStateException("Table full");
    }

    private int getBucket2(int index, long x) {
        if ((x & SHIFTED) != 0) {
            index--;
        }
        // TODO we know whether this was the second or the first,
        // and could use that info - would it make sense to use it?
        long fingerprint = x >> 2;
        // from the Murmur hash algorithm
        // some mixing (possibly not that great, but should be fast)
        long hash = fingerprint * 0xc4ceb9fe1a85ec53L;
        // we don't use xor; instead, we ensure bucketCount is even,
        // and bucket2 = bucketCount - bucket - reduce(hash(fingerprint)),
        // and if negative add the bucketCount,
        // where y is 1..bucketCount - 1 and odd -
        // that way, bucket2 is never the original bucket,
        // and running this twice will give the original bucket, as needed
        int r = Hash.reduce((int) hash, bucketCount);
        int b2 = bucketCount - index - r;
        // not sure how to avoid this branch
        if (b2 < 0) {
            b2 += bucketCount;
        }
        return b2;
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        int bucket = getBucket(hash);
        long fingerprint = getFingerprint(hash);
        long x = fingerprint << 2;
        if (get(bucket) == x) {
            return true;
        }
        if (get(bucket + 1) == (x | SHIFTED)) {
            return true;
        }
        int bucket2 = getBucket2(bucket, x);
        x |= SECOND;
        if (get(bucket2) == x) {
            return true;
        }
        return get(bucket2 + 1) == (x | SHIFTED);
    }

    private int getBucket(long hash) {
        return Hash.reduce((int) hash, bucketCount);
    }

    private long getFingerprint(long hash) {
        // TODO is this needed?
    	hash = Hash.hash64(hash, seed);
        long fingerprint =  (int) (hash & FINGERPRINT_MASK);
        // fingerprint 0 is not allowed -
        // an alternative, with a slightly lower false positive rate with a
        // small fingerprint, would be: shift until it's not zero (but it
        // doesn't sound like it would be faster)
        // assume that this doesn't use branching
        return Math.max(1, fingerprint);
    }

    public long getBitCount() {
        return 16 * (bucketCount + 1);
    }

}
