package com.github.fastfilter;

import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

public enum KeyGenerationStrategy {
    RANDOM64 {
        SplittableRandom random = new SplittableRandom(Environment.SEED);

        @Override
        long nextKey() {
            return random.nextLong();
        }
    },
    RANDOM32 {
        SplittableRandom random = new SplittableRandom(Environment.SEED);

        @Override
        long nextKey() {
            return random.nextInt() & 0xFFFFFFFFL;
        }
    };


    abstract long nextKey();
    void fill(long[] keys) {
        Set<Long> distinct = new HashSet<>();
        for (int i = 0; i < keys.length; ++i) {
            do {
                long key = nextKey();
                if (!distinct.contains(key)) {
                    keys[i] = key;
                    distinct.add(key);
                    break;
                }
            } while (true);
        }
    }
}
