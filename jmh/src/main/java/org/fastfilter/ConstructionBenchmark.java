package org.fastfilter;


import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class ConstructionBenchmark {

    @Benchmark
    public Filter construct(FilterConstructionState state) {
        return state.getConstructor().construct(state.getKeys(), 64);
    }
}
