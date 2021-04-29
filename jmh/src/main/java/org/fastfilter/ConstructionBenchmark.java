package org.fastfilter;


import org.fastfilter.Filter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.SECONDS)
public class ConstructionBenchmark {

    @Benchmark
    public Filter construct(ConstructionState state) {
        return state.getConstructor().construct(state.getKeys(), 64);
    }
}
