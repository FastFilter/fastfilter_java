package org.fastfilter;

public final class Filters {

    interface Buildable<T extends Filter> {
        /**
         * Build the filter from the supplied keys
         * @param keys the contents of the filter
         * @return a filter constructed from the keys
         */
        T build(long[] keys);
    }

    interface BitsPerKeyChoice<T extends Filter, U extends BitsPerKeyChoice<T, U>> extends Buildable<T> {
        /**
         * Selects the preferred number of bits per key
         * @param bitsPerKey the preferred number of bits to use per key in the filter
         * @return the builder with the number of bits per key set
         */
        U withBitsPerKey(int bitsPerKey);
    }

    interface Xor extends BitsPerKeyChoice<Filter, Xor> {
        Xor plus();
    }

    interface Cuckoo extends BitsPerKeyChoice<MutableFilter, Cuckoo> {
        Cuckoo plus();
    }

    interface Bloom extends BitsPerKeyChoice<MutableFilter, Bloom> {
        CountingBloom counting();
        Bloom blocked();
    }

    interface CountingBloom extends BitsPerKeyChoice<RemovableFilter, CountingBloom> {
        CountingBloom blocked();
        SuccinctBloom succinct();
    }

    interface SuccinctBloom extends BitsPerKeyChoice<RemovableFilter, CountingBloom>, CountingBloom {
        SuccinctBloom ranked();
    }

    interface MPH extends BitsPerKeyChoice<Filter, MPH> { }

    interface GCS extends BitsPerKeyChoice<Filter, GCS> { }
}
