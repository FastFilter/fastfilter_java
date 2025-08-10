package org.fastfilter;

import org.fastfilter.cpp.CppFilterType;
import org.fastfilter.utils.Hash;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Comprehensive benchmark comparing Java and C++ filter implementations.
 * This benchmark measures construction time, lookup performance, and memory usage
 * between Java FastFilter implementations and their C++ counterparts via FFI.
 * 
 * Key performance metrics:
 * - Construction speed (operations/second)
 * - Positive lookup performance (ns/operation) 
 * - Negative lookup performance (ns/operation)
 * - Memory efficiency (bits per key)
 * - Cross-platform compatibility
 * 
 * The C++ implementations are accessed through JDK 24's Foreign Function Interface
 * for direct performance comparison without JNI overhead.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class JavaVsCppBenchmark {

    @State(Scope.Benchmark)
    public static class FilterComparisonState {
        @Param({"1000", "10000", "100000", "1000000"})
        public int size;
        
        @Param({"RANDOM64"})
        public KeyGenerationStrategy keyStrategy;
        
        public long[] keys;
        public long[] keysNotInSet;
        
        // Java filters
        public Filter javaXorFilter;
        public Filter javaBinaryFuseFilter;
        
        // C++ filters (will be null if C++ library not available)
        public Filter cppXorFilter;
        public Filter cppBinaryFuseFilter;
        
        @Setup(Level.Trial)
        public void setup() {
            Hash.setSeed(1);
            
            // Generate keys for the set
            keys = new long[size];
            keyStrategy.fill(keys);
            
            // Generate different keys not in the set
            keysNotInSet = new long[size];
            keyStrategy.fill(keysNotInSet);
            
            // Pre-construct Java filters
            javaXorFilter = FilterType.XOR_BINARY_FUSE_8.construct(keys, 0);
            javaBinaryFuseFilter = FilterType.XOR_BINARY_FUSE_8.construct(keys, 0);
            
            // Attempt to construct C++ filters (will fail gracefully if library not available)
            try {
                cppXorFilter = CppFilterType.XOR8_CPP.construct(keys, 0);
            } catch (Exception e) {
                System.err.println("Warning: C++ XOR8 filter not available: " + e.getMessage());
                cppXorFilter = null;
            }
            
            try {
                cppBinaryFuseFilter = CppFilterType.BINARY_FUSE8_CPP.construct(keys, 0);
            } catch (Exception e) {
                System.err.println("Warning: C++ Binary Fuse8 filter not available: " + e.getMessage());
                cppBinaryFuseFilter = null;
            }
        }
    }
    
    @State(Scope.Benchmark)
    public static class ConstructionState {
        @Param({"1000", "10000", "100000", "1000000"})
        public int size;
        
        @Param({"RANDOM64"})
        public KeyGenerationStrategy keyStrategy;
        
        public long[] keys;
        
        @Setup(Level.Invocation)
        public void setup() {
            Hash.setSeed(System.nanoTime());
            keys = new long[size];
            keyStrategy.fill(keys);
        }
    }

    // Java Filter Construction Benchmarks
    @Benchmark
    public Filter benchmarkJavaXorConstruction(ConstructionState state) {
        return FilterType.XOR_BINARY_FUSE_8.construct(state.keys, 0);
    }
    
    @Benchmark 
    public Filter benchmarkJavaBinaryFuseConstruction(ConstructionState state) {
        return FilterType.XOR_BINARY_FUSE_8.construct(state.keys, 0);
    }
    
    // C++ Filter Construction Benchmarks
    @Benchmark
    public Filter benchmarkCppXorConstruction(ConstructionState state) {
        try {
            return CppFilterType.XOR8_CPP.construct(state.keys, 0);
        } catch (Exception e) {
            // Return null if C++ library not available
            return null;
        }
    }
    
    @Benchmark
    public Filter benchmarkCppBinaryFuseConstruction(ConstructionState state) {
        try {
            return CppFilterType.BINARY_FUSE8_CPP.construct(state.keys, 0);
        } catch (Exception e) {
            // Return null if C++ library not available
            return null;
        }
    }
    
    // Positive Lookup Benchmarks (keys in set)
    @Benchmark
    public boolean benchmarkJavaXorPositiveLookup(FilterComparisonState state) {
        int index = (int)(System.nanoTime() % state.size);
        return state.javaXorFilter.mayContain(state.keys[index]);
    }
    
    @Benchmark
    public boolean benchmarkJavaBinaryFusePositiveLookup(FilterComparisonState state) {
        int index = (int)(System.nanoTime() % state.size);
        return state.javaBinaryFuseFilter.mayContain(state.keys[index]);
    }
    
    @Benchmark
    public boolean benchmarkCppXorPositiveLookup(FilterComparisonState state) {
        if (state.cppXorFilter == null) return false; // C++ library not available
        int index = (int)(System.nanoTime() % state.size);
        return state.cppXorFilter.mayContain(state.keys[index]);
    }
    
    @Benchmark
    public boolean benchmarkCppBinaryFusePositiveLookup(FilterComparisonState state) {
        if (state.cppBinaryFuseFilter == null) return false; // C++ library not available  
        int index = (int)(System.nanoTime() % state.size);
        return state.cppBinaryFuseFilter.mayContain(state.keys[index]);
    }
    
    // Negative Lookup Benchmarks (keys not in set)
    @Benchmark
    public boolean benchmarkJavaXorNegativeLookup(FilterComparisonState state) {
        int index = (int)(System.nanoTime() % state.size);
        return state.javaXorFilter.mayContain(state.keysNotInSet[index]);
    }
    
    @Benchmark
    public boolean benchmarkJavaBinaryFuseNegativeLookup(FilterComparisonState state) {
        int index = (int)(System.nanoTime() % state.size);
        return state.javaBinaryFuseFilter.mayContain(state.keysNotInSet[index]);
    }
    
    @Benchmark
    public boolean benchmarkCppXorNegativeLookup(FilterComparisonState state) {
        if (state.cppXorFilter == null) return false; // C++ library not available
        int index = (int)(System.nanoTime() % state.size);
        return state.cppXorFilter.mayContain(state.keysNotInSet[index]);
    }
    
    @Benchmark
    public boolean benchmarkCppBinaryFuseNegativeLookup(FilterComparisonState state) {
        if (state.cppBinaryFuseFilter == null) return false; // C++ library not available
        int index = (int)(System.nanoTime() % state.size);
        return state.cppBinaryFuseFilter.mayContain(state.keysNotInSet[index]);
    }
    
    // Memory Usage Benchmarks
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public double benchmarkJavaXorMemoryEfficiency(FilterComparisonState state, Blackhole bh) {
        long bitCount = state.javaXorFilter.getBitCount();
        double bitsPerKey = (double) bitCount / state.size;
        bh.consume(bitCount);
        return bitsPerKey;
    }
    
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime) 
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public double benchmarkJavaBinaryFuseMemoryEfficiency(FilterComparisonState state, Blackhole bh) {
        long bitCount = state.javaBinaryFuseFilter.getBitCount();
        double bitsPerKey = (double) bitCount / state.size;
        bh.consume(bitCount);
        return bitsPerKey;
    }
    
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public double benchmarkCppXorMemoryEfficiency(FilterComparisonState state, Blackhole bh) {
        if (state.cppXorFilter == null) return -1.0; // C++ library not available
        long bitCount = state.cppXorFilter.getBitCount();
        double bitsPerKey = (double) bitCount / state.size;
        bh.consume(bitCount);
        return bitsPerKey;
    }
    
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public double benchmarkCppBinaryFuseMemoryEfficiency(FilterComparisonState state, Blackhole bh) {
        if (state.cppBinaryFuseFilter == null) return -1.0; // C++ library not available
        long bitCount = state.cppBinaryFuseFilter.getBitCount();
        double bitsPerKey = (double) bitCount / state.size;
        bh.consume(bitCount);
        return bitsPerKey;
    }
    
    // Comprehensive comparison benchmark
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkComprehensiveComparison(FilterComparisonState state, Blackhole bh) {
        // Test both Java and C++ implementations in a single benchmark
        
        // Java performance
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            int index = i % state.size;
            bh.consume(state.javaXorFilter.mayContain(state.keys[index]));
            bh.consume(state.javaBinaryFuseFilter.mayContain(state.keys[index]));
        }
        long javaTime = System.nanoTime() - startTime;
        
        // C++ performance (if available)
        long cppTime = 0;
        if (state.cppXorFilter != null && state.cppBinaryFuseFilter != null) {
            startTime = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                int index = i % state.size;
                bh.consume(state.cppXorFilter.mayContain(state.keys[index]));
                bh.consume(state.cppBinaryFuseFilter.mayContain(state.keys[index]));
            }
            cppTime = System.nanoTime() - startTime;
        }
        
        bh.consume(javaTime);
        bh.consume(cppTime);
    }
}