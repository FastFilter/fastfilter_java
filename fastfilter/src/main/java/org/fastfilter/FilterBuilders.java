package org.fastfilter;

import org.fastfilter.Filters.Xor;
import org.fastfilter.bloom.BlockedBloom;
import org.fastfilter.bloom.Bloom;
import org.fastfilter.bloom.count.*;
import org.fastfilter.cuckoo.Cuckoo16;
import org.fastfilter.cuckoo.Cuckoo8;
import org.fastfilter.cuckoo.CuckooPlus16;
import org.fastfilter.cuckoo.CuckooPlus8;
import org.fastfilter.xor.Xor16;
import org.fastfilter.xor.Xor8;
import org.fastfilter.xor.XorSimple2;
import org.fastfilter.xorplus.XorPlus8;

import java.util.Objects;
import java.util.SplittableRandom;
import java.util.function.LongSupplier;

class FilterBuilders {

    static final long SEED = 0xACE_5EEDL;

    static LongSupplier defaultSeedingStrategy() {
        return new SplittableRandom(SEED)::nextLong;
    }

    abstract static class FilterConfigs<T extends Filter, U extends Filters.FilterConfiguration<T, U>>
            implements Filters.FilterConfiguration<T, U> {

        protected int bitsPerKey = 64;
        private LongSupplier seedingStrategy;

        @Override
        @SuppressWarnings("unchecked")
        public U withBitsPerKey(int bitsPerKey) {
            this.bitsPerKey = bitsPerKey;
            return (U)this;
        }

        @Override
        public U withSeedingStrategy(LongSupplier strategy) {
            this.seedingStrategy = strategy;
            return (U)this;
        }

        protected LongSupplier seedingStrategy() {
            return Objects.requireNonNullElseGet(seedingStrategy, FilterBuilders::defaultSeedingStrategy);
        }
    }

    static class XorFilterBuilder extends FilterConfigs<Filter, Xor> implements Xor {

        private boolean plus = false;

        @Override
        public Xor plus() {
            this.plus = true;
            return this;
        }


        @Override
        public Filter build(long[] keys) {
            if (plus) {
                return new XorPlus8(keys, seedingStrategy());
            }
            switch (bitsPerKey >>> 3) {
                case 0:
                case 1:
                    return new Xor8(keys, seedingStrategy());
                case 2:
                    return new Xor16(keys, seedingStrategy());
                default:
                    return new XorSimple2(keys);
            }
        }
    }

    static class CuckooFilterBuilder extends FilterConfigs<MutableFilter, Filters.Cuckoo> implements Filters.Cuckoo {

        private boolean plus;

        @Override
        public Filters.Cuckoo plus() {
            this.plus = true;
            return this;
        }

        @Override
        public MutableFilter build(long[] keys) {
            switch (bitsPerKey >>> 3) {
                case 0:
                case 1:
                    return plus
                            ? CuckooPlus8.construct(keys, seedingStrategy())
                            : Cuckoo8.construct(keys, seedingStrategy());
                default:
                    return plus
                            ? CuckooPlus16.construct(keys, seedingStrategy())
                            : Cuckoo16.construct(keys, seedingStrategy());
            }
        }
    }

    private static final int COUNTING = 1;
    private static final int BLOCKED = 2;
    private static final int SUCCINCT = 4;
    private static final int RANKED = 8;

    static class BloomFilterBuilder extends FilterConfigs<MutableFilter, Filters.Bloom>
            implements Filters.Bloom {

        private int flags = 0;

        @Override
        public Filters.CountingBloom counting() {
            return new CountingBloomFilterBuilder(flags);
        }

        @Override
        public Filters.Bloom blocked() {
            flags |= BLOCKED;
            return this;
        }

        @Override
        public MutableFilter build(long[] keys) {
            long seed = seedingStrategy().getAsLong();
            switch (flags) {
                case 0:
                    return Bloom.construct(keys, bitsPerKey, seed);
                case BLOCKED:
                    return BlockedBloom.construct(keys, bitsPerKey, seed);
                default:
                    throw new IllegalStateException("Impossible state reached");
            }
        }
    }

    static class CountingBloomFilterBuilder extends FilterConfigs<RemovableFilter, Filters.CountingBloom>
            implements Filters.CountingBloom, Filters.SuccinctBloom {

        private int flags;

        CountingBloomFilterBuilder(int flags) {
            this.flags = COUNTING | flags;
        }

        @Override
        public Filters.SuccinctBloom ranked() {
            flags |= RANKED;
            return this;
        }

        @Override
        public Filters.CountingBloom blocked() {
            flags |= BLOCKED;
            return succinct();
        }

        @Override
        public Filters.SuccinctBloom succinct() {
            flags |= SUCCINCT;
            return this;
        }

        @Override
        public RemovableFilter build(long[] keys) {
            long seed = seedingStrategy().getAsLong();
            switch (flags) {
                case COUNTING:
                    return CountingBloom.construct(keys, bitsPerKey, seed);
                case SUCCINCT | COUNTING:
                    return SuccinctCountingBloom.construct(keys, bitsPerKey, seed);
                case SUCCINCT | COUNTING | BLOCKED:
                    return SuccinctCountingBlockedBloom.construct(keys, bitsPerKey, seed);
                case SUCCINCT | COUNTING | RANKED:
                    return SuccinctCountingBloomRanked.construct(keys, bitsPerKey, seed);
                case SUCCINCT | COUNTING | BLOCKED | RANKED:
                    return SuccinctCountingBlockedBloomRanked.construct(keys, bitsPerKey, seed);
                default:
                    throw new IllegalStateException("Impossible state reached");
            }
        }
    }

    @FunctionalInterface
    interface ConstructFromArray<T extends Filter> {
        T construct(long[] keys, int bitsPerKey, long seed);
    }

    @FunctionalInterface
    interface ConstructFromArrayUnseeded<T extends Filter> {
        T construct(long[] keys, int bitsPerKey);
    }

    static class GenericBuilder<T extends Filter, U extends Filters.FilterConfiguration<T, U>> extends FilterConfigs<T, U> {
        private final ConstructFromArray<T> constructFromArray;

        GenericBuilder(ConstructFromArray<T> constructFromArray) {
            this.constructFromArray = constructFromArray;
        }

        GenericBuilder(ConstructFromArrayUnseeded<T> constructFromArrayUnseeded) {
            this.constructFromArray = (keys, bitsPerKey, seed) -> constructFromArrayUnseeded.construct(keys, bitsPerKey);
        }

        @Override
        public T build(long[] keys) {
            return constructFromArray.construct(keys, bitsPerKey, seedingStrategy().getAsLong());
        }
    }

}
