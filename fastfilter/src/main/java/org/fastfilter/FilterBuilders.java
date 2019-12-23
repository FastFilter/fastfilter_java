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

class FilterBuilders {

    abstract static class ConfigurableBitsPerKey<T extends Filter, U extends Filters.BitsPerKeyChoice<T, U>>
            implements Filters.BitsPerKeyChoice<T, U> {

        protected int bitsPerKey = 64;

        /**
         * How many bits to use per key (affects false positive rate)
         * @param bitsPerKey the (preferred) number of bits per key stored in the filter
         * @return a builder with bits per key set
         */
        @Override
        @SuppressWarnings("unchecked")
        public U withBitsPerKey(int bitsPerKey) {
            this.bitsPerKey = bitsPerKey;
            return (U)this;
        }
    }

    static class XorFilterBuilder extends ConfigurableBitsPerKey<Filter, Xor> implements Xor {

        private boolean plus = false;

        @Override
        public Xor plus() {
            this.plus = true;
            return this;
        }


        @Override
        public Filter build(long[] keys) {
            if (plus) {
                return new XorPlus8(keys);
            }
            switch (bitsPerKey >>> 3) {
                case 0:
                case 1:
                    return new Xor8(keys);
                case 2:
                    return new Xor16(keys);
                default:
                    return new XorSimple2(keys);
            }
        }
    }

    static class CuckooFilterBuilder extends ConfigurableBitsPerKey<MutableFilter, Filters.Cuckoo> implements Filters.Cuckoo {

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
                    return plus ? CuckooPlus8.construct(keys) : Cuckoo8.construct(keys);
                default:
                    return plus ? CuckooPlus16.construct(keys) : Cuckoo16.construct(keys);
            }
        }
    }

    private static final int COUNTING = 1;
    private static final int BLOCKED = 2;
    private static final int SUCCINCT = 4;
    private static final int RANKED = 8;

    static class BloomFilterBuilder extends ConfigurableBitsPerKey<MutableFilter, Filters.Bloom>
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
            // these casts are safe so long as the caller abides by the public API
            switch (flags) {
                case 0:
                    return Bloom.construct(keys, bitsPerKey);
                case BLOCKED:
                    return BlockedBloom.construct(keys, bitsPerKey);
                default:
                    throw new IllegalStateException("Impossible state reached");
            }
        }
    }

    static class CountingBloomFilterBuilder extends ConfigurableBitsPerKey<RemovableFilter, Filters.CountingBloom>
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
            switch (flags) {
                case COUNTING:
                    return CountingBloom.construct(keys, bitsPerKey);
                case SUCCINCT | COUNTING:
                    return SuccinctCountingBloom.construct(keys, bitsPerKey);
                case SUCCINCT | COUNTING | BLOCKED:
                    return SuccinctCountingBlockedBloom.construct(keys, bitsPerKey);
                case SUCCINCT | COUNTING | RANKED:
                    return SuccinctCountingBloomRanked.construct(keys, bitsPerKey);
                case SUCCINCT | COUNTING | BLOCKED | RANKED:
                    return SuccinctCountingBlockedBloomRanked.construct(keys, bitsPerKey);
                default:
                    throw new IllegalStateException("Impossible state reached");
            }
        }
    }

    @FunctionalInterface
    interface ConstructFromArray<T extends Filter> {
        T construct(long[] keys, int bitsPerKey);
    }

    static class GenericBuilder<T extends Filter, U extends Filters.BitsPerKeyChoice<T, U>> extends ConfigurableBitsPerKey<T, U> {
        private final ConstructFromArray<T> constructFromArray;

        GenericBuilder(ConstructFromArray<T> constructFromArray) {
            this.constructFromArray = constructFromArray;
        }

        @Override
        public T build(long[] keys) {
            return constructFromArray.construct(keys, bitsPerKey);
        }
    }

}
