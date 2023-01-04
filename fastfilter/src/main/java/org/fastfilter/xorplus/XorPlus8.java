package org.fastfilter.xorplus;

import java.io.*;
import java.util.BitSet;

import org.fastfilter.Filter;
import org.fastfilter.utils.Hash;

/**
 * A slower implementation of the Xor filter, which uses slightly less space
 * (7% less at 8 bit, but more if the fingerprint is larger).
 */
public class XorPlus8 implements Filter {

    private static final int BITS_PER_FINGERPRINT = 8;

    // TODO how to construct from a larger, mutable data structure
    // (GolombCompressedSet, Cuckoo filter,...)?
    // how many additional bits are needed to support merging?
    // Multi-layered design as described in "Don’t Thrash: How to Cache Your Hash on Flash"?

    // TODO could multiple entries for a key be in the same cache line (64 bytes)?
    // maybe with a blocked approach?
    // the number of hashes per key (see the BDZ algorithm)

    // TODO compression; now we have 9% / 11.5% / 36.5% free entries

    // the number of hash slots which are XORed to compute the stored hash for each key
    private static final int HASHES = 3;

    // this figure is added when computing the size of the table, i.e. it is the 32 in "1.23 * size + 32"
    private static final int OFFSET = 32;

    // the table needs to be 1.23 times the number of keys to store
    // with 2 hashes, we would need 232 (factor 2.32) for a 50% chance,
    // 240 for 55%, 250 for a 60%, 264 for 65%, 282 for 67%, as for
    // 2 hashes, p = sqrt(1 - ((2/factor)^2));
    private static final int FACTOR_TIMES_100 = 123;

    // the number of keys in the filter
    private final int size;

    // the table (array) length, that is size * 1.23 + 32
    private final int arrayLength;

    // the table is divided into 3 (HASHES) blocks, one block for each hash. Each block holds this number of entries.
    // this allows to better compress the filter, because the last block contains more zero entries than the first two.
    private final int blockLength;

    private long seed;

    // the fingerprints
    private byte[] fingerprints;

    // the table (array) length, in bits
    private int bitCount;

    private Rank9 rank;

    /**
     * The size of the filter, in bits.
     *
     * @return the size
     */
    public long getBitCount() {
        return bitCount;
    }

    /**
     * Calculate the table (array) length. This is 1.23 times the size, plus an offset of 32 (see paper, Fig. 1).
     * We round down to a multiple of HASHES, as any excess entries couldn't be used.
     *
     * @param size the number of entries
     * @return the table length
     */
    private static int getArrayLength(int size) {
        int arrayLength = (int) (OFFSET + (long) FACTOR_TIMES_100 * size / 100);
        return arrayLength - (arrayLength % HASHES);
    }

    public static XorPlus8 construct(long[] keys) {
        return new XorPlus8(keys);
    }

    /**
     * Construct the filter. This is basically the BDZ algorithm. The algorithm
     * itself is basically the same as BDZ, except that xor is used to store the
     * fingerprints.
     *
     * We use cuckoo hashing, so that each key is stored in one entry in the
     * hash table. We use 3 hash functions: h0, h1, h2. But we don't want to use
     * any additional bits per entry to calculate which of the entries in the
     * table contains the key. For this, we ensure that the fingerprint of each
     * key can be calculated as table[h0(key)] xor table[h1(key)] xor
     * table[h2(key)]. If we insert the entries in the right order, this is
     * possible, as one the 3 possible entries for the key can be set as we
     * like. So we first need to find the right order to insert the keys. Once
     * we have that, we can insert the data.
     *
     * @param keys the list of entries (keys)
     */
    public XorPlus8(long[] keys) {
        this.size = keys.length;
        this.arrayLength = getArrayLength(size);
        this.blockLength = arrayLength / HASHES;
        int m = arrayLength;

        // the order in which the fingerprints are inserted, where
        // reverseOrder[0] is the last key to insert,
        // reverseOrder[1] the second to last
        long[] reverseOrder = new long[size];
        // when inserting fingerprints, whether to set fp[h0], fp[h1] or fp[h2]
        byte[] reverseH = new byte[size];
        // current index in the reverseOrder list
        int reverseOrderPos;

        // == mapping step ==
        // we usually execute this loop just once. If we detect a cycle (which is extremely unlikely)
        // then we try again, with a new random seed.
        long seed = 0;
        int attempts = 0;
        do {
            attempts++;
            if (attempts >= 100) {
                // if the same key appears more than once in the keys array, every attempt to build the table will yield a collision
                for(int i = 0; i < fingerprints.length; i++) {
                    fingerprints[i] = (byte)0xFF;
                }
                return;
            }

            seed = Hash.randomSeed();
            // we use an second table t2 to keep the list of all keys that map
            // to a given entry (with a broken hash function, all keys could map
            // to entry zero).
            // t2count: the number of keys in a given location
            byte[] t2count = new byte[m];
            // t2 is the table - but we don't store each key, only the xor of
            // keys this is possible as when removing a key, we simply xor
            // again, and once only one is remaining, we know which one it was
            long[] t2 = new long[m];
            // now we loop over all keys and insert them into the t2 table
            for (long k : keys) {
                for (int hi = 0; hi < HASHES; hi++) {
                    int h = getHash(k, seed, hi);
                    t2[h] ^= k;
                    if (t2count[h] > 120) {
                        // probably something wrong with the hash function; or, the keys[] array contains many copies
                        // of the same value
                        throw new IllegalArgumentException("More than 120 keys hashed to the same location; indicates duplicate keys, or a bad hash function");
                    }
                    t2count[h]++;
                }
            }

            // == generate the queue ==
            // for each entry that is alone,
            // we remove it from t2, and add it to the reverseOrder list
            reverseOrderPos = 0;
            // the list of indexes in the table that are "alone", that is,
            // only have one key pointing to them
            // we have one list per block, so that one block can have more empty entries
            int[][] alone = new int[HASHES][blockLength];
            int[] alonePos = new int[HASHES];
            // nextAloneCheck loops over all entries, to find an entry that is alone
            // once we found one, we remove it, and while removing it, we check
            // if this resulted in yet another entry that is alone -
            // the BDZ algorithm loops over _all_ entries in the beginning,
            // but this results in adding more entries to the alone list multiple times
            for (int nextAlone = 0; nextAlone < HASHES; nextAlone++) {
                for (int i = 0; i < blockLength; i++) {
                    if (t2count[nextAlone * blockLength + i] == 1) {
                        alone[nextAlone][alonePos[nextAlone]++] = nextAlone * blockLength + i;
                    }
                }
            }
            int found = -1;
            while (true) {
                int i = -1;
                for (int hi = 0; hi < HASHES; hi++) {
                    if (alonePos[hi] > 0) {
                        i = alone[hi][--alonePos[hi]];
                        found = hi;
                        break;
                    }
                }
                if (i == -1) {
                    // no entry found
                    break;
                }
                if (t2count[i] <= 0) {
                    continue; // if a key is the sole occupant for more than one of its hashes, it will wind up
                              // being listed in multiple slots of the "alone" table; in that case, when we come
                              // to the second or third "alone" entry for that key, it will already have been
                              // removed, and so t2count will be 0.
                }
                long k = t2[i];
                if (t2count[i] != 1) {
                    throw new AssertionError();
                }
                --t2count[i];
                // which index (0, 1, 2) the entry was found
                for (int hi = 0; hi < HASHES; hi++) {
                    if (hi != found) {
                        int h = getHash(k, seed, hi);
                        int newCount = --t2count[h];
                        if (newCount == 1) {
                            // we found a key that is _now_ alone
                            alone[hi][alonePos[hi]++] = h;
                        }
                        // remove this key from the t2 table, using xor
                        t2[h] ^= k;
                    }
                }
                reverseOrder[reverseOrderPos] = k;
                reverseH[reverseOrderPos] = (byte) found;
                reverseOrderPos++;
            }
            // this means there was no cycle
        } while (reverseOrderPos != size);
        this.seed = seed;
        // == assignment step ==
        // fingerprints (array, then converted to a bit buffer)
        byte[] fp = new byte[m];
        // set all entries to some keys fingerprint
        // to support early stopping in some cases
        // for(long k : keys) {
        //     for (int hi = 0; hi < HASHES; hi++) {
        //         int h = getHash(k, hashIndex, hi);
        //         long hash = Mix.hash64(k + hashIndex);
        //         fp[h] = fingerprint(hash);
        //     }
        // }
        for (int i = reverseOrderPos - 1; i >= 0; i--) {
            // the key we insert next
            long k = reverseOrder[i];
            int found = reverseH[i];
            // which entry in the table we can change
            int change = -1;
            // we set table[change] to the fingerprint of the key,
            // unless the other two entries are already occupied
            long hash = Hash.hash64(k, seed);
            int xor = fingerprint(hash);
            for (int hi = 0; hi < HASHES; hi++) {
                int h = getHash(k, seed, hi);
                if (found == hi) {
                    change = h;
                } else {
                    // this is different from BDZ: using xor to calculate the
                    // fingerprint
                    xor ^= fp[h];
                }
            }
            fp[change] = (byte) xor;
        }
        BitSet set = new BitSet(blockLength);
        for (int i = 0; i < blockLength; i++) {
            int f = fp[i + 2 * blockLength];
            if (f != 0) {
                set.set(i);
            }
        }
        this.rank = new Rank9(set, blockLength);

        this.fingerprints = new byte[2 * blockLength + set.cardinality()];
        if (2 * blockLength >= 0) {
            System.arraycopy(fp, 0, fingerprints, 0, 2 * blockLength);
        }
        for (int i = 2 * blockLength, j = i; i < fp.length;) {
            int f = fp[i++];
            if (f != 0) {
                fingerprints[j++] = (byte) f;
            }
        }
        bitCount = fingerprints.length * 8 + rank.getBitCount();
    }

    /**
     * Whether the filter _may_ contain a key.
     *
     * @param key the key to test
     * @return true if the key may be in the filter
     */
    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        int f = fingerprint(hash);
        int r0 = (int) hash;
        int r1 = (int) (hash >>> 16);
        int r2 = (int) (hash >>> 32);
        int h0 = Hash.reduce(r0, blockLength);
        int h1 = Hash.reduce(r1, blockLength) + blockLength;
        int h2 = Hash.reduce(r2, blockLength);
        f ^= fingerprints[h0] ^ fingerprints[h1];
        long getAndPartialRank = rank.getAndPartialRank(h2);
        if ((getAndPartialRank & 1) == 1) {
            int h2x = (int) ((getAndPartialRank >> 1) + rank.remainingRank(h2));
            f ^= fingerprints[h2x + 2 * blockLength];
        }
        return (f & 0xff) == 0;
    }

    /**
     * Calculate the hash for a key.
     *
     * @param key the key
     * @param seed the hash seed
     * @param index the index (0..2)
     * @return the hash (0..arrayLength)
     */
    private int getHash(long key, long seed, int index) {
        // TODO use only one copy of this code
        long hash = Hash.hash64(key, seed);
        int r;
        switch(index) {
        case 0:
            r = (int) (hash);
            break;
        case 1:
            r = (int) (hash >>> 16);
            break;
        default:
            r = (int) (hash >>> 32);
            break;
        }

        // this would be slightly faster, but means we only have one range
        // also, there is a small risk that for the same key and different index,
        // the same value is returned
        // r = reduce((int) r, arrayLength);

        // use one distinct block of entries for each hash index
        r = Hash.reduce(r, blockLength);
        r = r + index * blockLength;

        return r;
    }


    /**
     * Calculate the fingerprint.
     *
     * @param hash the hash of the key
     * @return the fingerprint
     */
    private int fingerprint(long hash) {
        return (int) (hash & ((1 << BITS_PER_FINGERPRINT) - 1));
    }

    public byte[] getData() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(out);
            d.writeInt(size);
            d.writeLong(seed);
            d.writeInt(fingerprints.length);
            d.write(fingerprints);
            rank.write(d);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public XorPlus8(InputStream in) {
        try {
            DataInputStream din = new DataInputStream(in);
            size = din.readInt();
            arrayLength = getArrayLength(size);
            blockLength = arrayLength / HASHES;
            seed = din.readLong();
            int fingerprintLength = din.readInt();
            bitCount = fingerprintLength * BITS_PER_FINGERPRINT;
            fingerprints = new byte[fingerprintLength];
            din.readFully(fingerprints);
            rank = new Rank9(din);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
