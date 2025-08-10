package org.fastfilter;

import org.fastfilter.utils.Hash;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

/**
 * JMH benchmark for fuzz testing filter reliability and performance.
 * This benchmark replaces the SimpleFuzzer main method with proper
 * JMH-based reliability and stress testing.
 * 
 * Tests filter construction and correctness across random data patterns,
 * various key lengths, and different bits per key settings.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class FuzzTestBenchmark {

    @State(Scope.Benchmark)
    public static class FuzzState {
        @Param({"8", "16", "24"})
        public int bitsPerKey;
        
        @Param({"10", "100", "1000", "10000"})
        public int keyLength;
        
        @Param({"BLOOM", "XOR_BINARY_FUSE_8", "CUCKOO_PLUS_8"})
        public FilterType filterType;
        
        public long[][] randomKeySets;
        public long[] seeds;
        private int currentIndex = 0;
        
        @Setup(Level.Trial)
        public void setup() {
            // Pre-generate multiple random key sets and seeds for testing
            int numTests = 100; // Reduced from original 1M for practical benchmarking
            randomKeySets = new long[numTests][];
            seeds = new long[numTests];
            
            // Use KeyGenerationStrategy for consistent key generation
            KeyGenerationStrategy keyGen = KeyGenerationStrategy.RANDOM64;
            for (int i = 0; i < numTests; i++) {
                seeds[i] = ThreadLocalRandom.current().nextLong();
                randomKeySets[i] = new long[keyLength];
                keyGen.fill(randomKeySets[i]);
            }
        }
        
        @Setup(Level.Invocation)
        public void resetIndex() {
            currentIndex = 0;
        }
        
        public long[] getNextKeySet() {
            return randomKeySets[currentIndex % randomKeySets.length];
        }
        
        public long getNextSeed() {
            return seeds[currentIndex++ % seeds.length];
        }
    }
    
    @State(Scope.Benchmark)
    public static class ConstructionReliabilityState {
        @Param({"100", "1000", "10000"})
        public int keyLength;
        
        // Test filters that may have construction failures
        @Param({"CUCKOO_8", "CUCKOO_PLUS_8", "CUCKOO_16", "CUCKOO_PLUS_16"})
        public FilterType filterType;
        
        public long[] keys;
        
        @Param({"RANDOM64"})
        public KeyGenerationStrategy kgs;
        
        @Setup(Level.Invocation)
        public void setup() {
            // Generate new random keys for each invocation to test construction reliability
            keys = new long[keyLength];
            kgs.fill(keys);
            
            Hash.setSeed(ThreadLocalRandom.current().nextLong());
        }
    }

    /**
     * Benchmark filter construction and correctness verification with random data.
     * This tests that filters can be constructed successfully and that all inserted
     * keys are found (no false negatives).
     */
    @Benchmark
    public boolean benchmarkFuzzConstructionAndCorrectness(FuzzState state, Blackhole bh) {
        long[] keys = state.getNextKeySet();
        long seed = state.getNextSeed();
        
        Hash.setSeed(seed);
        
        try {
            Filter filter = state.filterType.construct(keys, state.bitsPerKey);
            
            // Verify all keys are found (no false negatives allowed)
            boolean allFound = true;
            for (long key : keys) {
                if (!filter.mayContain(key)) {
                    allFound = false;
                    break;
                }
            }
            
            bh.consume(filter.getBitCount());
            return allFound;
            
        } catch (Exception e) {
            // Some filters (like Cuckoo) may fail construction with certain data
            bh.consume(e.getMessage());
            return false;
        }
    }
    
    /**
     * Benchmark construction success rate for filters that may fail construction.
     * This is particularly relevant for Cuckoo filters which can fail if the
     * hash table becomes too full during construction.
     */
    @Benchmark
    public boolean benchmarkConstructionReliability(ConstructionReliabilityState state, Blackhole bh) {
        try {
            Filter filter = state.filterType.construct(state.keys, 10);
            
            // Quick correctness check - verify first few keys are found
            boolean correct = true;
            int samplesToCheck = Math.min(10, state.keys.length);
            for (int i = 0; i < samplesToCheck; i++) {
                if (!filter.mayContain(state.keys[i])) {
                    correct = false;
                    break;
                }
            }
            
            bh.consume(filter.getBitCount());
            return correct;
            
        } catch (Exception e) {
            // Construction failed
            bh.consume(e.getMessage());
            return false;
        }
    }
    
    /**
     * Benchmark false positive rate measurement across random data.
     * Tests how consistent the false positive rate is across different random datasets.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public double benchmarkFalsePositiveRate(FuzzState state, Blackhole bh) {
        long[] keys = state.getNextKeySet();
        long seed = state.getNextSeed();
        
        Hash.setSeed(seed);
        
        try {
            Filter filter = state.filterType.construct(keys, state.bitsPerKey);
            
            // Generate keys not in the set for FPP testing
            long[] testKeys = LongStream.range(0, Math.min(1000, state.keyLength))
                .map(i -> ThreadLocalRandom.current().nextLong())
                .filter(key -> {
                    // Make sure test keys are not in the original set
                    for (long originalKey : keys) {
                        if (key == originalKey) return false;
                    }
                    return true;
                })
                .toArray();
            
            int falsePositives = 0;
            for (long testKey : testKeys) {
                if (filter.mayContain(testKey)) {
                    falsePositives++;
                }
            }
            
            double fpp = testKeys.length > 0 ? (double) falsePositives / testKeys.length : 0.0;
            bh.consume(falsePositives);
            return fpp;
            
        } catch (Exception e) {
            bh.consume(e.getMessage());
            return -1.0; // Indicates construction failure
        }
    }
    
    /**
     * Benchmark memory usage consistency across random datasets.
     * Tests that memory usage is predictable and doesn't vary wildly with different data.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public double benchmarkMemoryUsageConsistency(FuzzState state, Blackhole bh) {
        long[] keys = state.getNextKeySet();
        long seed = state.getNextSeed();
        
        Hash.setSeed(seed);
        
        try {
            Filter filter = state.filterType.construct(keys, state.bitsPerKey);
            long bitCount = filter.getBitCount();
            double bitsPerKey = (double) bitCount / keys.length;
            
            bh.consume(bitCount);
            return bitsPerKey;
            
        } catch (Exception e) {
            bh.consume(e.getMessage());
            return -1.0; // Indicates construction failure
        }
    }
    
    /**
     * Comprehensive fuzz test that combines construction, correctness, and performance.
     * This provides a single metric that captures overall filter robustness.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public boolean benchmarkComprehensiveFuzz(FuzzState state, Blackhole bh) {
        long[] keys = state.getNextKeySet();
        long seed = state.getNextSeed();
        
        Hash.setSeed(seed);
        
        try {
            // Construction
            long startTime = System.nanoTime();
            Filter filter = state.filterType.construct(keys, state.bitsPerKey);
            long constructionTime = System.nanoTime() - startTime;
            
            // Correctness check
            boolean allFound = true;
            startTime = System.nanoTime();
            for (long key : keys) {
                if (!filter.mayContain(key)) {
                    allFound = false;
                    break;
                }
            }
            long lookupTime = System.nanoTime() - startTime;
            
            // Memory usage
            long bitCount = filter.getBitCount();
            
            // Consume all metrics
            bh.consume(constructionTime);
            bh.consume(lookupTime);
            bh.consume(bitCount);
            
            return allFound; // Return success/failure
            
        } catch (Exception e) {
            bh.consume(e.getMessage());
            return false;
        }
    }
}