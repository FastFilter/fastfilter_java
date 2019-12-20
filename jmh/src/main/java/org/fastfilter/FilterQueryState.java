package org.fastfilter;

import org.openjdk.jmh.annotations.*;

@State(Scope.Benchmark)
public class FilterQueryState extends FilterConstructionState {

    @AuxCounters
    static class Counters {
        private final long bitCount;
        int workDone;
        int found;
        int notFound;

        Counters(Filter filter) {
            this.bitCount = filter.getBitCount();
        }
    }

    @Param({"10", "15", "20", "25"})
    int logDistinctLookups;

    @Param({"0.1", "0.5", "0.9"})
    float trueMatchProbability;

    private long[] keysToFind;
    private Counters counters;

    public void found() {
        ++counters.found;
    }

    public void notFound() {
        ++counters.notFound;
    }

    Filter filter;

    public Filter getFilter() {
        return filter;
    }

    public long nextKey() {
        return keysToFind[(counters.workDone++) & (keysToFind.length - 1)];
    }

    @Override
    @Setup(Level.Trial)
    public void init() {
        super.init();
        filter = type.construct(keys, 64);
        keysToFind = new long[1 << logDistinctLookups];
        int memberCount = (int)(trueMatchProbability * keysToFind.length);
        if (memberCount > keys.length) {
            throw new AssertionError("Benchmark setup error: expect at least "
                    + memberCount + " keys  but have " + keys.length);
        }
        System.arraycopy(keys, 0, keysToFind, 0, memberCount);
    }
}
