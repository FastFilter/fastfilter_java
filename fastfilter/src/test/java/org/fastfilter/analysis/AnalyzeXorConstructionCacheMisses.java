package org.fastfilter.analysis;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class AnalyzeXorConstructionCacheMisses {
    public static void main(String... args) throws Exception {
        int size = 1000000;
        long[] keys = new long[size];
        for (int i = 0; i < size; i++) {
            keys[i] = hash64(i, 0);
        }
        for (Class<? extends Xor> c : Arrays.asList(Xor.class, Fuse.class)) {
            Xor filter = c.getConstructor(int.class).newInstance(size);
            CacheAccessCallback cache;
            cache = new CacheAccessCallback();
            filter.access = cache;
            filter.construct1(keys);
            System.out.println(filter + " constuct1 cache misses: " + cache.cacheMisses);
            cache = new CacheAccessCallback();
            filter.access = cache;
            filter.construct2();
            System.out.println(filter + " constuct2 cache misses: " + cache.cacheMisses);
        }
    }

    public static long hash64(long x, long seed) {
        x += seed;
        x = (x ^ (x >>> 33)) * 0xff51afd7ed558ccdL;
        x = (x ^ (x >>> 33)) * 0xc4ceb9fe1a85ec53L;
        x = x ^ (x >>> 33);
        return x;
    }

    public static int reduce(int hash, int n) {
        return (int) (((hash & 0xffffffffL) * n) >>> 32);
    }

    static class AccessCallback {
        void read(int index) {
        }
        void write(int index) {
        }
    }

    static class CacheAccessCallback extends AccessCallback {
        int cacheMisses;

        // assume 2 MB L3 cache, and 64 bytes per cache line
        final int MAX_ENTRIES = 2 * 1024 * 1024 / 64;
        Map<Integer, Integer> cache = new LinkedHashMap<Integer, Integer>(MAX_ENTRIES+1, .75F, true) {
            public boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
                return size() > MAX_ENTRIES;
            }
        };

        int cacheLine(int index) {
            // assume 64 bytes per cache line, and cells to be 8 bytes; so divide by 8
            return index >> 3;
        }
        void read(int index) {
            int cacheLine = cacheLine(index);
            if (cache.get(cacheLine) != null) {
                return;
            }
            cacheMisses++;
            cache.put(cacheLine, 1);
        }
        void write(int index) {
            // small simplification
            read(index);
        }
    }


    static class Xor {
        AccessCallback access = new AccessCallback();
        int size;
        long[] values;
        int[] counts;
        int segmentLength;

        Xor() {
        }

        public Xor(int size) {
            this.size = size;
            int length = (int) (32 + 1.23 * size + 2) / 3 * 3;
            this.segmentLength = length / 3;
            this.counts = new int[length];
            this.values = new long[length];
        }

        public String toString() {
            return "Xor";
        }

        long getValue(int index) {
            access.read(index);
            return values[index];
        }

        void xorValue(int index, long value) {
            access.write(index);
            values[index] ^= value;
        }

        protected int[] indexes(long hash) {
            int h0 = 0 * segmentLength + reduce((int) hash64(hash, 0), segmentLength);
            int h1 = 1 * segmentLength + reduce((int) hash64(hash, 1), segmentLength);
            int h2 = 2 * segmentLength + reduce((int) hash64(hash, 2), segmentLength);
            return new int[] { h0, h1, h2 };
        }

        void construct1(long[] keys) {
            for (long k : keys) {
                for (int x : indexes(k)) {
                    counts[x]++;
                    xorValue(x, k);
                }
            }
        }

        void construct2() {
            int[] alone = new int[counts.length];
            int alonePos = 0;
            for (int i = 0; i < counts.length; i++) {
                if (counts[i] == 1) {
                    alone[alonePos++] = i;
                }
            }
            int reverseOrderPos = 0;
            while (alonePos > 0) {
                alonePos--;
                int index = alone[alonePos];
                if (counts[index] == 1) {
                    // It is still there!
                    long k = getValue(index);
                    // reverseOrder[reverseOrderPos] = hash;
                    for (int index3 : indexes(k)) {
                        if (index3 == index) {
                            // reverseH[reverseOrderPos] = hi;
                            // no need to decrement & remove
                            continue;
                        } else if (counts[index3] == 2) {
                            // Found a new candidate !
                            alone[alonePos++] = index3;
                        }
                        counts[index3]--;
                        xorValue(index3, k);
                    }
                    reverseOrderPos++;
                }
            }
            if (reverseOrderPos != size) {
                throw new IllegalStateException();
            }
        }

    }

    static class Fuse extends Xor {
        int segmentCount;

        public Fuse(int size) {
            this.size = size;
            // TODO hardcoded, for about 0.4 to 1.2 million keys
            this.segmentLength = 4096;
            int length = (int) (1.13 * size + segmentLength) / segmentLength * segmentLength;
            this.segmentCount = Math.max(1, length / segmentLength - 2);
            length = (segmentCount + 2) * segmentLength;
            this.counts = new int[length];
            this.values = new long[length];
        }

        public String toString() {
            return "Fuse";
        }

        protected int[] indexes(long hash) {
            int seg = reduce((int) hash64(hash, 0), segmentCount);
            int h0 = (seg + 0) * segmentLength + (int) (hash64(hash, 1) & (segmentLength - 1));
            int h1 = (seg + 1) * segmentLength + (int) (hash64(hash, 2) & (segmentLength - 1));
            int h2 = (seg + 2) * segmentLength + (int) (hash64(hash, 3) & (segmentLength - 1));
            return new int[] { h0, h1, h2 };
        }
    }
}
