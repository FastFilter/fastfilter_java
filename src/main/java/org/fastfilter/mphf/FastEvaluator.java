package org.fastfilter.mphf;

import org.fastfilter.gcs.BitBuffer;
import org.fastfilter.gcs.MonotoneList;

public class FastEvaluator {

    private final BitBuffer buffer;
    private final long size;
    private final int leafSize;
    private final int bucketCount;
    private final int bucketShift;
    private final MonotoneList startList;
    private final MonotoneList offsetList;
    private final int startBuckets;
    private final BitBuffer fingerprints;
    private final int fingerprintBits;

    FastEvaluator(BitBuffer buffer, int averageBucketSize, int leafSize, int shift, BitBuffer fingerprints, int fingerprintBits) {
        this.buffer = buffer;
        this.fingerprints = fingerprints;
        this.size = (int) (buffer.readEliasDelta() - 1);
        this.bucketCount = Builder.getBucketCount(size, averageBucketSize);
        this.leafSize = leafSize;
        int bucketBitCount = 31 - Integer.numberOfLeadingZeros(bucketCount);
        this.bucketShift = 64 - bucketBitCount - shift;
        this.startList = MonotoneList.load(buffer);
        this.offsetList = MonotoneList.load(buffer);
        this.startBuckets = buffer.position();
        this.fingerprintBits = fingerprintBits;
    }

    public int getFingerprint(long hashCode) {
        int index = evaluate(hashCode);
        if (index < 0 || index > size) {
            return -1;
        }
        return (int) fingerprints.readNumber(index * fingerprintBits, fingerprintBits);
    }

    public int evaluate(long hashCode) {
        int b;
        if (bucketCount == 1) {
            b = 0;
        } else {
            b = (int) (hashCode >>> bucketShift);
        }
        int startPos;
        long offsetPair = offsetList.getPair(b);
        int offset = (int) (offsetPair >>> 32);
        int offsetNext = (int) offsetPair;
        if (offsetNext == offset) {
            // entry not found
            return -1;
        }
        int bucketSize = offsetNext - offset;
        startPos = startBuckets + startList.get(b);
        return evaluate(startPos, hashCode, 0, offset, bucketSize);
    }

    private int skip(int pos, int size) {
        if (size < 2) {
            return pos;
        }
        pos = buffer.skipGolombRice(pos, Builder.getGolombRiceShift(size));
        if (size <= leafSize) {
            return pos;
        }
        int firstPart = size / 2;
        pos = skip(pos, firstPart);
        pos = skip(pos, size - firstPart);
        return pos;
    }

    private int evaluate(int pos, long hashCode,
            int index, int add, int size) {
        while (true) {
            if (size < 2) {
                return add;
            }
            int shift = Builder.getGolombRiceShift(size);
            long q = buffer.readUntilZero(pos);
            pos += q + 1;
            long value = (q << shift) | buffer.readNumber(pos, shift);
            pos += shift;
            index += value;
            if (size <= leafSize) {
                int h = Builder.supplementalHash(hashCode, index);
                switch(size) {
                case 2:
                    h = h & 1;
                    break;
                case 4:
                    h = h & 3;
                    break;
                default:
                    h = Builder.reduce(h, size);
                }
                return add + h;
            }
            int firstPart = size / 2;
            int h = Builder.supplementalHash(hashCode, index++);
            h = h & 1;
            if (h == 1) {
                pos = skip(pos, firstPart);
                add += firstPart;
                size = size - firstPart;
            } else {
                size = firstPart;
            }
        }
    }

}
