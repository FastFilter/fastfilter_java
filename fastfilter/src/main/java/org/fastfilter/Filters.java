package org.fastfilter;

import java.util.function.LongSupplier;

public final class Filters {

    interface Buildable<T extends Filter> {
        /**
         * Build the filter from the supplied keys
         * @param keys the contents of the filter
         * @return a filter constructed from the keys
         */
        T build(long[] keys);
    }

    interface FilterConfiguration<T extends Filter, U extends FilterConfiguration<T, U>> extends Buildable<T> {
        /**
         * Selects the preferred number of bits per key
         * @param bitsPerKey the preferred number of bits to use per key in the filter
         * @return the builder with the number of bits per key set
         */
        U withBitsPerKey(int bitsPerKey);

        /**
         * Provide a sequence of seeds to use
         * @param strategy the seeds
         * @return the builder with the seeding strategy specified
         */
        U withSeedingStrategy(LongSupplier strategy);
    }

    interface Xor extends FilterConfiguration<Filter, Xor> {
        Xor plus();
    }

    interface Cuckoo extends FilterConfiguration<MutableFilter, Cuckoo> {
        Cuckoo plus();
    }

    interface Bloom extends FilterConfiguration<MutableFilter, Bloom> {
        CountingBloom counting();
        Bloom blocked();
    }

    interface CountingBloom extends FilterConfiguration<RemovableFilter, CountingBloom> {
        CountingBloom blocked();
        SuccinctBloom succinct();
    }

    interface SuccinctBloom extends FilterConfiguration<RemovableFilter, CountingBloom>, CountingBloom {
        SuccinctBloom ranked();
    }

    interface MPH extends FilterConfiguration<Filter, MPH> { }

    interface GCS extends FilterConfiguration<Filter, GCS> { }
}
