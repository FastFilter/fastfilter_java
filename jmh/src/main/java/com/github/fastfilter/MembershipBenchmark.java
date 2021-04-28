package org.fastfilter;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class MembershipBenchmark {

    @Benchmark
    public void mayContain(MembershipState state, MembershipCounters counters) {
        if (state.getFilter().mayContain(state.nextKey())) {
            counters.found++;
        } else {
            counters.notFound++;
        }
    }
}
