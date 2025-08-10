package org.fastfilter;

import org.fastfilter.utils.Hash;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Comprehensive benchmark suite for all filter types covering construction,
 * lookup performance for both present and absent keys, and removal operations.
 * 
 * This benchmark replaces the main method performance tests in TestAllFilters.java
 * with proper JMH-based benchmarks that provide statistical significance and
 * better measurement accuracy.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ComprehensiveFilterBenchmark {

    @State(Scope.Benchmark)
    public static class FilterConstructionState {
        @Param({"1000", "10000", "100000", "1000000"})
        public int size;
        
        @Param({"BLOOM", "BLOCKED_BLOOM", "COUNTING_BLOOM", "SUCCINCT_COUNTING_BLOOM", 
                "XOR_8", "XOR_16", "XOR_BINARY_FUSE_8", "CUCKOO_8", "CUCKOO_PLUS_8"})
        public FilterType filterType;
        
        @Param({"RANDOM64"})
        public KeyGenerationStrategy kgs;
        
        public long[] keys;
        
        @Setup(Level.Trial)
        public void setup() {
            Hash.setSeed(1);
            keys = new long[size];
            kgs.fill(keys);
        }
    }
    
    @State(Scope.Benchmark)
    public static class FilterLookupState {
        @Param({"1000", "10000", "100000", "1000000"})
        public int size;
        
        @Param({"BLOOM", "BLOCKED_BLOOM", "XOR_BINARY_FUSE_8", "CUCKOO_PLUS_8"})
        public FilterType filterType;
        
        public Filter filter;
        @Param({"RANDOM64"})
        public KeyGenerationStrategy kgs;
        
        public long[] keysInSet;
        public long[] keysNotInSet;
        private int keyIndex = 0;
        
        @Setup(Level.Trial)
        public void setup() {
            Hash.setSeed(1);
            
            // Generate keys for the set
            keysInSet = new long[size];
            kgs.fill(keysInSet);
            
            // Generate different keys not in the set
            keysNotInSet = new long[size];
            kgs.fill(keysNotInSet);
            
            filter = filterType.construct(keysInSet, 10);
        }
        
        @Setup(Level.Invocation)
        public void resetIndex() {
            keyIndex = 0;
        }
        
        public long nextKeyInSet() {
            return keysInSet[keyIndex++ % size];
        }
        
        public long nextKeyNotInSet() {
            return keysNotInSet[keyIndex++ % size];
        }
    }
    
    @State(Scope.Benchmark)
    public static class FilterRemovalState {
        @Param({"1000", "10000", "100000"})
        public int size;
        
        // Only test filters that support removal
        @Param({"COUNTING_BLOOM", "SUCCINCT_COUNTING_BLOOM", "CUCKOO_8", "CUCKOO_PLUS_8"})
        public FilterType filterType;


        
        @Param({"RANDOM64"})
        public KeyGenerationStrategy kgs;
        
        public long[] keys;
        public Filter filter;
        private int keyIndex = 0;
        
        @Setup(Level.Invocation)
        public void setup() {
            Hash.setSeed(1);
            keys = new long[size];
            kgs.fill(keys);
            
            filter = filterType.construct(keys, 10);
            keyIndex = 0;
        }
        
        public long nextKey() {
            return keys[keyIndex++];
        }
        
        public boolean hasMoreKeys() {
            return keyIndex < size;
        }
    }

    /**
     * Benchmark filter construction time across different filter types and sizes.
     */
    @Benchmark
    public Filter benchmarkConstruction(FilterConstructionState state) {
        return state.filterType.construct(state.keys, 10);
    }
    
    /**
     * Benchmark lookup performance for keys that ARE in the filter.
     * This should always return true (no false negatives allowed).
     */
    @Benchmark
    public boolean benchmarkLookupKeysInSet(FilterLookupState state) {
        return state.filter.mayContain(state.nextKeyInSet());
    }
    
    /**
     * Benchmark lookup performance for keys that are NOT in the filter.
     * This may return true (false positives) but should mostly return false.
     */
    @Benchmark
    public boolean benchmarkLookupKeysNotInSet(FilterLookupState state) {
        return state.filter.mayContain(state.nextKeyNotInSet());
    }
    
    /**
     * Benchmark removal operation performance for filters that support it.
     * Only tests filters that implement supportsRemove() == true.
     */
    @Benchmark
    public void benchmarkRemoval(FilterRemovalState state, Blackhole bh) {
        if (state.hasMoreKeys() && state.filter.supportsRemove()) {
            long key = state.nextKey();
            state.filter.remove(key);
            // Verify removal by checking the key is no longer found
            bh.consume(state.filter.mayContain(key));
        }
    }
    
    /**
     * Benchmark false positive rate calculation by testing a large number
     * of keys not in the set and measuring how many return true.
     */
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Measurement(iterations = 1)
    public double benchmarkFalsePositiveRate(FilterLookupState state, Blackhole bh) {
        int falsePositives = 0;
        int totalTests = Math.min(state.size, 10000); // Limit for reasonable benchmark time
        
        for (int i = 0; i < totalTests; i++) {
            if (state.filter.mayContain(state.keysNotInSet[i])) {
                falsePositives++;
            }
        }
        
        double fpp = (double) falsePositives / totalTests;
        bh.consume(fpp);
        return fpp;
    }
    
    /**
     * Benchmark memory efficiency by measuring bits per key for different filter types.
     */
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime) 
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Measurement(iterations = 1)
    public double benchmarkMemoryEfficiency(FilterConstructionState state, Blackhole bh) {
        Filter filter = state.filterType.construct(state.keys, 10);
        long bitCount = filter.getBitCount();
        double bitsPerKey = (double) bitCount / state.size;
        bh.consume(bitCount);
        return bitsPerKey;
    }
}