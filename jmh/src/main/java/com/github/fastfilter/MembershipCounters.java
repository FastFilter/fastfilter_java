package com.github.fastfilter;

import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@AuxCounters(AuxCounters.Type.EVENTS)
public class MembershipCounters {
    public int found;
    public int notFound;

    public int total() {
        return notFound + found;
    }

    @Setup(Level.Iteration)
    public void reset() {
        found = 0;
        notFound = 0;
    }
}
