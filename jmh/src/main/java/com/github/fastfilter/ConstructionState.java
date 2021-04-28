package com.github.fastfilter;

import org.openjdk.jmh.annotations.*;

import com.github.fastfilter.FilterType;

@State(Scope.Benchmark)
public class ConstructionState {

    @Param({"1048576", "2097152"})
    int size;

    @Param({"RANDOM32", "RANDOM64"})
    KeyGenerationStrategy kgs;

    @Param({"BLOOM",
            "COUNTING_BLOOM",
            "SUCCINCT_COUNTING_BLOOM",
            "SUCCINCT_COUNTING_BLOOM_RANKED",
            "BLOCKED_BLOOM",
            "SUCCINCT_COUNTING_BLOCKED_BLOOM",
            "SUCCINCT_COUNTING_BLOCKED_BLOOM_RANKED",
            "XOR_SIMPLE",
            "XOR_SIMPLE_2",
            "XOR_8",
            "XOR_16",
            "XOR_PLUS_8",
            "CUCKOO_8",
            "CUCKOO_16",
            "CUCKOO_PLUS_8",
            "CUCKOO_PLUS_16",
            "GCS"})
    FilterType type;

    long[] keys;

    public long[] getKeys() {
        return keys;
    }

    public FilterType getConstructor() {
        return type;
    }

    @Setup(Level.Trial)
    public void init() {
        this.keys = new long[size];
        kgs.fill(keys);
    }
}
