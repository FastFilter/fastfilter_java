package org.fastfilter;

import org.fastfilter.bloom.BlockedBloom;
import org.fastfilter.bloom.Bloom;
import org.fastfilter.bloom.CountingBloom;
import org.fastfilter.bloom.SuccinctCountingBloom;
import org.fastfilter.cuckoo.Cuckoo16;
import org.fastfilter.cuckoo.Cuckoo8;
import org.fastfilter.cuckoo.CuckooPlus16;
import org.fastfilter.cuckoo.CuckooPlus8;
import org.fastfilter.gcs.GolombCompressedSet;
import org.fastfilter.mphf.MPHFilter;
import org.fastfilter.xor.Xor16;
import org.fastfilter.xor.Xor8;
import org.fastfilter.xor.XorSimple;
import org.fastfilter.xor.XorSimple2;
import org.fastfilter.xorplus.XorPlus8;

/**
 * The list of supported approximate membership implementations.
 */
public enum FilterType {
    SUCCINCT_COUNTING_BLOOM {
        @Override
        public Filter construct(long[] keys, int setting) {
            return SuccinctCountingBloom.construct(keys, setting);
        }
    },
    COUNTING_BLOOM {
        @Override
        public Filter construct(long[] keys, int setting) {
            return CountingBloom.construct(keys, setting);
        }
    },
    BLOOM {
        @Override
        public Filter construct(long[] keys, int setting) {
            return Bloom.construct(keys, setting);
        }
    },
    BLOCKED_BLOOM {
        @Override
        public Filter construct(long[] keys, int setting) {
            return BlockedBloom.construct(keys, setting);
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
            return Xor8.construct(keys);
        }
    },
    XOR_16 {
        @Override
        public Filter construct(long[] keys, int setting) {
            return Xor16.construct(keys);
        }
    },
    XOR_PLUS_8 {
        @Override
        public Filter construct(long[] keys, int setting) {
            return XorPlus8.construct(keys);
        }
    },
    CUCKOO_8 {
        @Override
        public Filter construct(long[] keys, int setting) {
            return Cuckoo8.construct(keys);
        }
    },
    CUCKOO_16 {
        @Override
        public Filter construct(long[] keys, int setting) {
            return Cuckoo16.construct(keys);
        }
    },
    CUCKOO_PLUS_8 {
        @Override
        public Filter construct(long[] keys, int setting) {
            return CuckooPlus8.construct(keys);
        }
    },
    CUCKOO_PLUS_16 {
        @Override
        public Filter construct(long[] keys, int setting) {
            return CuckooPlus16.construct(keys);
        }
    },
    GCS {
        @Override
        public Filter construct(long[] keys, int setting) {
            return GolombCompressedSet.construct(keys, setting);
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