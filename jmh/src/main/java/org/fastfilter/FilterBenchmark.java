package org.fastfilter;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import org.fastfilter.Filter;
import org.fastfilter.xor.Xor8;
import org.fastfilter.xor.Xor16;
import org.fastfilter.xor.XorBinaryFuse8;
import org.fastfilter.xor.XorBinaryFuse16;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class FilterBenchmark {

    @Param({"XOR_8", "XOR_16", "XOR_BINARY_FUSE_8", "XOR_BINARY_FUSE_16"})
    public String filterType;

    private Filter filter;
    private long[] testKeys;
    private final int NUM_KEYS = 1_000_000;

    @Setup
    public void setup() {
        // Create 1,000,000 keys (even numbers)
        testKeys = new long[NUM_KEYS];
        for (int i = 0; i < testKeys.length; i++) {
            testKeys[i] = (long) i * 2L; // even numbers
        }

        try {
            switch (filterType) {
                case "XOR_8":
                    filter = Xor8.construct(testKeys);
                    break;
                case "XOR_16":
                    filter = Xor16.construct(testKeys);
                    break;
                case "XOR_BINARY_FUSE_8":
                    filter = XorBinaryFuse8.construct(testKeys);
                    break;
                case "XOR_BINARY_FUSE_16":
                    filter = XorBinaryFuse16.construct(testKeys);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown filter type: " + filterType);
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @TearDown
    public void tearDown() {
        filter = null;
        testKeys = null;
    }

    @Benchmark
    @OperationsPerInvocation(NUM_KEYS)
    public void benchmarkContainsExisting(Blackhole blackhole) throws Throwable {
        for (long key : testKeys) {
            if (!filter.mayContain(key)) {
                throw new RuntimeException("Key should exist: " + key);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(NUM_KEYS)
    public void benchmarkContainsNonExisting(Blackhole blackhole) throws Throwable {
        int fp = 0;
        for (int i = 0; i < testKeys.length; i++) {
            long key = (long) i * 2L + 1L; // odd numbers
            if (filter.mayContain(key)) {
                fp++;
            }
        }
        if (fp > 10000) {
            throw new RuntimeException("Too many false positives: " + fp);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(NUM_KEYS)
    public void benchmarkContainsExistingThroughput(Blackhole blackhole) throws Throwable {
        for (long key : testKeys) {
            if (!filter.mayContain(key)) {
                throw new RuntimeException("Key should exist: " + key);
            }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(NUM_KEYS)
    public void benchmarkContainsNonExistingThroughput(Blackhole blackhole) throws Throwable {
        int fp = 0;
        for (int i = 0; i < testKeys.length; i++) {
            long key = (long) i * 2L + 1L; // odd numbers
            if (filter.mayContain(key)) {
                fp++;
            }
        }
        if (fp > 10000) {
            throw new RuntimeException("Too many false positives: " + fp);
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(FilterBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
