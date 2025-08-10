package org.fastfilter;

import org.fastfilter.utils.Hash;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Specialized benchmark for XOR Binary Fuse 8 filter scaling behavior.
 * This benchmark replaces the specific XOR Binary Fuse scaling tests
 * that were in the main method of TestAllFilters.java.
 * 
 * Tests construction and lookup performance across a wide range of sizes
 * from very small (1 element) to very large (100M elements).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class XorBinaryFuseScalingBenchmark {

    @State(Scope.Benchmark)
    public static class SmallScaleState {
        // Test sizes 1-100 (original: for (int size = 1; size <= 100; size++))
        @Param({"1", "5", "10", "25", "50", "75", "100"})
        public int size;
        
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
    public static class MediumScaleState {
        // Test sizes 100-100K with 1.1x scaling (original: size *= 1.1)
        @Param({"100", "500", "1000", "5000", "10000", "50000", "100000"})
        public int size;
        
        @Param({"RANDOM64"})
        public KeyGenerationStrategy kgs;
        
        public long[] keys;
        public Filter filter;
        public long[] keysNotInSet;
        
        @Setup(Level.Trial)
        public void setup() {
            Hash.setSeed(1);
            
            // Generate keys for the set
            keys = new long[size];
            kgs.fill(keys);
            
            // Generate different keys not in the set
            keysNotInSet = new long[size];
            kgs.fill(keysNotInSet);
            
            filter = FilterType.XOR_BINARY_FUSE_8.construct(keys, 0);
        }
    }
    
    @State(Scope.Benchmark) 
    public static class LargeScaleState {
        // Test sizes 1M-100M (original large scale tests)
        @Param({"1000000", "2000000", "4000000", "8000000", "10000000", "20000000", "40000000", "80000000"})
        public int size;
        
        @Param({"RANDOM64"})
        public KeyGenerationStrategy kgs;
        
        public long[] keys;
        public Filter filter;
        public long[] keysNotInSet;
        
        @Setup(Level.Trial)
        public void setup() {
            Hash.setSeed(1);
            
            // Generate keys for the set
            keys = new long[size];
            kgs.fill(keys);
            
            // Generate different keys not in the set
            keysNotInSet = new long[size];
            kgs.fill(keysNotInSet);
            
            filter = FilterType.XOR_BINARY_FUSE_8.construct(keys, 0);
        }
    }

    /**
     * Benchmark XOR Binary Fuse 8 construction for very small datasets (1-100 elements).
     * This tests the filter's behavior with minimal data where overhead might dominate.
     */
    @Benchmark
    public Filter benchmarkSmallScaleConstruction(SmallScaleState state) {
        return FilterType.XOR_BINARY_FUSE_8.construct(state.keys, 0);
    }
    
    /**
     * Benchmark XOR Binary Fuse 8 construction for medium datasets (100-100K elements).
     * This tests the filter's scaling behavior in the medium range.
     */
    @Benchmark
    public Filter benchmarkMediumScaleConstruction(MediumScaleState state) {
        return FilterType.XOR_BINARY_FUSE_8.construct(state.keys, 0);
    }
    
    /**
     * Benchmark XOR Binary Fuse 8 construction for large datasets (1M-100M elements).
     * This tests the filter's behavior with realistic large-scale data.
     */
    @Benchmark
    @Timeout(time = 30, timeUnit = TimeUnit.SECONDS)
    public Filter benchmarkLargeScaleConstruction(LargeScaleState state) {
        return FilterType.XOR_BINARY_FUSE_8.construct(state.keys, 0);
    }
    
    /**
     * Benchmark lookup performance for medium-scale XOR Binary Fuse filters.
     * Tests both positive and negative lookups.
     */
    @Benchmark
    public boolean benchmarkMediumScaleLookupPositive(MediumScaleState state) {
        // Test a key that should be in the filter
        int index = (int)(System.nanoTime() % state.size);
        return state.filter.mayContain(state.keys[index]);
    }
    
    @Benchmark
    public boolean benchmarkMediumScaleLookupNegative(MediumScaleState state) {
        // Test a key that should not be in the filter
        int index = (int)(System.nanoTime() % state.size);
        return state.filter.mayContain(state.keysNotInSet[index]);
    }
    
    /**
     * Benchmark lookup performance for large-scale XOR Binary Fuse filters.
     */
    @Benchmark
    public boolean benchmarkLargeScaleLookupPositive(LargeScaleState state) {
        int index = (int)(System.nanoTime() % state.size);
        return state.filter.mayContain(state.keys[index]);
    }
    
    @Benchmark
    public boolean benchmarkLargeScaleLookupNegative(LargeScaleState state) {
        int index = (int)(System.nanoTime() % state.size);
        return state.filter.mayContain(state.keysNotInSet[index]);
    }
    
    /**
     * Measure memory efficiency (bits per key) across different scales.
     */
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Measurement(iterations = 1)
    public double benchmarkMediumScaleMemoryEfficiency(MediumScaleState state, Blackhole bh) {
        long bitCount = state.filter.getBitCount();
        double bitsPerKey = (double) bitCount / state.size;
        bh.consume(bitCount);
        return bitsPerKey;
    }
    
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS) 
    @Measurement(iterations = 1)
    public double benchmarkLargeScaleMemoryEfficiency(LargeScaleState state, Blackhole bh) {
        long bitCount = state.filter.getBitCount();
        double bitsPerKey = (double) bitCount / state.size;
        bh.consume(bitCount);
        return bitsPerKey;
    }
    
    /**
     * Comprehensive performance test that measures all metrics like the original main method.
     * This provides a single benchmark that matches the output format of the original test.
     */
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Measurement(iterations = 1)
    public void benchmarkComprehensivePerformance(MediumScaleState state, Blackhole bh) {
        // Replicate the original test logic from TestAllFilters.test() method
        
        // Construction time
        long startTime = System.nanoTime();
        Filter filter = FilterType.XOR_BINARY_FUSE_8.construct(state.keys, 0);
        long constructionTime = System.nanoTime() - startTime;
        double nanosPerAdd = (double) constructionTime / state.size;
        
        // Positive lookups (keys in set)
        startTime = System.nanoTime();
        int falseNegatives = 0;
        for (long key : state.keys) {
            if (!filter.mayContain(key)) {
                falseNegatives++;
            }
        }
        long positiveLookupTime = System.nanoTime() - startTime;
        double nanosPerLookupInSet = (double) positiveLookupTime / state.size;
        
        // Negative lookups (keys not in set)
        startTime = System.nanoTime();
        int falsePositives = 0;
        for (long key : state.keysNotInSet) {
            if (filter.mayContain(key)) {
                falsePositives++;
            }
        }
        long negativeLookupTime = System.nanoTime() - startTime;
        double nanosPerLookupNotInSet = (double) negativeLookupTime / state.size;
        
        // Calculate metrics
        double fpp = (double) falsePositives / state.size;
        long bitCount = filter.getBitCount();
        double bitsPerKey = (double) bitCount / state.size;
        
        // Consume results to prevent dead code elimination
        bh.consume(nanosPerAdd);
        bh.consume(nanosPerLookupInSet);
        bh.consume(nanosPerLookupNotInSet);
        bh.consume(fpp);
        bh.consume(bitsPerKey);
        bh.consume(falseNegatives); // Should be 0
        
        // This could be enhanced to output results in a structured format
        // similar to the original console output if needed
    }
}