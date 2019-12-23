package org.fastfilter;

import org.fastfilter.bloom.BlockedBloom;
import org.fastfilter.bloom.Bloom;
import org.fastfilter.bloom.count.*;
import org.fastfilter.cuckoo.Cuckoo16;
import org.fastfilter.cuckoo.Cuckoo8;
import org.fastfilter.cuckoo.CuckooPlus16;
import org.fastfilter.cuckoo.CuckooPlus8;
import org.fastfilter.gcs.GolombCompressedSet;
import org.fastfilter.gcs.GolombCompressedSet2;
import org.fastfilter.mphf.MPHFilter;
import org.fastfilter.xor.Xor16;
import org.fastfilter.xor.Xor8;
import org.fastfilter.xor.XorSimple;
import org.fastfilter.xor.XorSimple2;
import org.fastfilter.xorplus.XorPlus8;

import static org.fastfilter.FilterBuilders.SEED;
import static org.fastfilter.FilterBuilders.defaultSeedingStrategy;

/**
 * The list of supported approximate membership implementations.
 */
public enum FilterType {
    BLOOM {
        @Override
        public Filter construct(long[] keys, int setting) {
            return Bloom.construct(keys, setting, SEED);
        }
    },
    COUNTING_BLOOM {
        @Override
        public Filter construct(long[] keys, int setting) {
            return CountingBloom.construct(keys, setting, SEED);
        }
    },
    SUCCINCT_COUNTING_BLOOM {
        @Override
        public Filter construct(long[] keys, int setting) {
            return SuccinctCountingBloom.construct(keys, setting, SEED);
        }
    },
    SUCCINCT_COUNTING_BLOOM_RANKED {
        @Override
        public Filter construct(long[] keys, int setting) {
            return SuccinctCountingBloomRanked.construct(keys, setting, SEED);
        }
    },
    BLOCKED_BLOOM {
        @Override
        public Filter construct(long[] keys, int setting) {
            return BlockedBloom.construct(keys, setting, SEED);
        }
    },
    SUCCINCT_COUNTING_BLOCKED_BLOOM {
        @Override
        public Filter construct(long[] keys, int setting) {
            return SuccinctCountingBlockedBloom.construct(keys, setting, SEED);
        }
    },
    SUCCINCT_COUNTING_BLOCKED_BLOOM_RANKED {
        @Override
        public Filter construct(long[] keys, int setting) {
            return SuccinctCountingBlockedBloomRanked.construct(keys, setting, SEED);
        }
    },
    XOR_SIMPLE {
        @Override
        public Filter construct(long[] keys, int setting) {
            return XorSimple.construct(keys);
        }
    },
    XOR_SIMPLE_2 {
        @Override
        public Filter construct(long[] keys, int setting) {
            return XorSimple2.construct(keys);
        }
    },
    XOR_8 {
        @Override
        public Filter construct(long[] keys, int setting) {
            return Xor8.construct(keys, defaultSeedingStrategy());
        }
    },
    XOR_16 {
        @Override
        public Filter construct(long[] keys, int setting) {
            return Xor16.construct(keys, defaultSeedingStrategy());
        }
    },
    XOR_PLUS_8 {
        @Override
        public Filter construct(long[] keys, int setting) {
            return XorPlus8.construct(keys, defaultSeedingStrategy());
        }
    },
    CUCKOO_8 {
        @Override
        public Filter construct(long[] keys, int setting) {
            return Cuckoo8.construct(keys, SEED);
        }
    },
    CUCKOO_16 {
        @Override
        public Filter construct(long[] keys, int setting) {
            return Cuckoo16.construct(keys, SEED);
        }
    },
    CUCKOO_PLUS_8 {
        @Override
        public Filter construct(long[] keys, int setting) {
            return CuckooPlus8.construct(keys, SEED);
        }
    },
    CUCKOO_PLUS_16 {
        @Override
        public Filter construct(long[] keys, int setting) {
            return CuckooPlus16.construct(keys, SEED);
        }
    },
    GCS {
        @Override
        public Filter construct(long[] keys, int setting) {
            return GolombCompressedSet.construct(keys, setting, SEED);
        }
    },
    GCS2 {
        @Override
        public Filter construct(long[] keys, int setting) {
            return GolombCompressedSet2.construct(keys, setting, SEED);
        }
    },
    MPHF {
        @Override
        public Filter construct(long[] keys, int setting) {
            return MPHFilter.construct(keys, setting);
        }
    };

    /**
     * Construct the filter with the given keys and the setting.
     *
     * @param keys the keys
     * @param setting the setting (roughly bits per fingerprint)
     * @return the constructed filter
     */
    public abstract Filter construct(long[] keys, int setting);

}