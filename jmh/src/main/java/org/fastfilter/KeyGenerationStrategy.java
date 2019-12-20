package org.fastfilter;

import java.util.SplittableRandom;

public enum KeyGenerationStrategy {
    RANDOM64 {
        SplittableRandom random = new SplittableRandom(Environment.SEED);
        @Override
        void fill(long[] keys) {
            for (int i = 0; i < keys.length; ++i) {
                keys[i] = random.nextLong();
            }
        }
    },
    RANDOM32 {
        SplittableRandom random = new SplittableRandom(Environment.SEED);
        @Override
        void fill(long[] keys) {
            for (int i = 0; i < keys.length; ++i) {
                keys[i] = random.nextInt();
            }
        }
    };

    abstract void fill(long[] keys);
}
