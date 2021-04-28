package com.github.fastfilter;

import org.openjdk.jmh.annotations.*;

import com.github.fastfilter.Filter;

import java.util.HashSet;
import java.util.Set;

@State(Scope.Benchmark)
public class MembershipState extends ConstructionState {

    @Param({"10", "15", "20", "25"})
    int logDistinctLookups;

    @Param({"0.1", "0.5", "0.9"})
    float trueMatchProbability;

    private long[] keysToFind;
    int workDone;
    Filter filter;

    public Filter getFilter() {
        return filter;
    }

    public long nextKey() {
        return keysToFind[(workDone++) & (keysToFind.length - 1)];
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
        Set<Long> present = new HashSet<>();
        for (long key : keys) {
            present.add(key);
        }
        for (int i = memberCount; i < keysToFind.length; ++i) {
            do {
                long key = kgs.nextKey();
                if (!present.contains(key)) {
                    present.add(key);
                    keysToFind[i] = key;
                    break;
                }
            } while (true);
        }

    }
}
