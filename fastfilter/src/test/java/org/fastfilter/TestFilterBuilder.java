package org.fastfilter;

import org.fastfilter.bloom.BlockedBloom;
import org.fastfilter.bloom.Bloom;
import org.fastfilter.bloom.count.*;
import org.fastfilter.bloom.count.CountingBloom;
import org.fastfilter.cuckoo.Cuckoo16;
import org.fastfilter.cuckoo.Cuckoo8;
import org.fastfilter.cuckoo.CuckooPlus16;
import org.fastfilter.cuckoo.CuckooPlus8;
import org.fastfilter.xor.Xor16;
import org.fastfilter.xor.Xor8;
import org.fastfilter.xor.XorSimple;
import org.fastfilter.xorplus.XorPlus8;
import org.junit.Test;

import java.util.stream.LongStream;

import static org.fastfilter.Filters.*;
import static org.junit.Assert.*;

public class TestFilterBuilder {

    @Test
    public void testBuildBloom() {
        long[] keys = new long[] { 1, 2, 3};
        MutableFilter filter = bloom().withBitsPerKey(8).build(keys);
        assertTrue(filter instanceof Bloom);
    }

    @Test
    public void testBuildBlockedBloom() {
        long[] keys = new long[] { 1, 2, 3};
        assertTrue(bloom().withBitsPerKey(8).blocked().build(keys) instanceof BlockedBloom);
    }

    @Test
    public void testBuildCountingBloom() {
        long[] keys = new long[] { 1, 2, 3};
        assertTrue(bloom().counting().build(keys) instanceof CountingBloom);
    }

    @Test
    public void testBuildCountingBlockedBloom() {
        long[] keys = new long[] { 1, 2, 3};
        assertTrue(bloom().counting().blocked().build(keys) instanceof SuccinctCountingBlockedBloom);
    }

    @Test
    public void testBuildSuccinctCountingBlockedBloom() {
        long[] keys = new long[] { 1, 2, 3};
        assertTrue(bloom().counting().succinct().blocked().build(keys) instanceof SuccinctCountingBlockedBloom);
    }

    @Test
    public void testBuildSuccinctCountingBloom() {
        long[] keys = new long[] { 1, 2, 3};
        assertTrue(bloom().counting().succinct().build(keys) instanceof SuccinctCountingBloom);
    }


    @Test
    public void testBuildSuccinctCountingBlockedRankedBloom() {
        long[] keys = new long[] { 1, 2, 3};
        assertTrue(bloom().counting().blocked().succinct().ranked().build(keys) instanceof SuccinctCountingBlockedBloomRanked);
    }

    @Test
    public void testBuildSuccinctCountingRankedBloom() {
        long[] keys = new long[] { 1, 2, 3};
        assertTrue(bloom().counting().succinct().ranked().build(keys) instanceof SuccinctCountingBloomRanked);
    }

    @Test
    public void testBuildCuckoo8() {
        long[] keys = LongStream.range(0, 64).toArray();
        assertTrue(cuckoo().withBitsPerKey(8).build(keys) instanceof Cuckoo8);
        assertTrue(cuckoo().withBitsPerKey(7).build(keys) instanceof Cuckoo8);
        assertTrue(cuckoo().withBitsPerKey(9).build(keys) instanceof Cuckoo8);
    }

    @Test
    public void testBuildCuckoo16() {
        long[] keys = LongStream.range(0, 64).toArray();
        assertTrue(cuckoo().withBitsPerKey(16).build(keys) instanceof Cuckoo16);
        assertTrue(cuckoo().withBitsPerKey(17).build(keys) instanceof Cuckoo16);
    }


    @Test
    public void testBuildCuckooPlus8() {
        long[] keys = LongStream.range(0, 64).toArray();
        assertTrue(cuckoo().plus().withBitsPerKey(8).build(keys) instanceof CuckooPlus8);
        assertTrue(cuckoo().plus().withBitsPerKey(7).build(keys) instanceof CuckooPlus8);
        assertTrue(cuckoo().plus().withBitsPerKey(9).build(keys) instanceof CuckooPlus8);
    }

    @Test
    public void testBuildCuckooPlus16() {
        long[] keys = LongStream.range(0, 64).toArray();
        assertTrue(cuckoo().plus().withBitsPerKey(16).build(keys) instanceof CuckooPlus16);
        assertTrue(cuckoo().plus().withBitsPerKey(17).build(keys) instanceof CuckooPlus16);
    }

    @Test
    public void testBuildXor8() {
        long[] keys = LongStream.range(0, 64).toArray();
        assertTrue(xor().withBitsPerKey(7).build(keys) instanceof Xor8);
        assertTrue(xor().withBitsPerKey(8).build(keys) instanceof Xor8);
        assertTrue(xor().withBitsPerKey(9).build(keys) instanceof Xor8);
    }

    @Test
    public void testBuildXor16() {
        long[] keys = LongStream.range(0, 64).toArray();
        assertTrue(xor().withBitsPerKey(16).build(keys) instanceof Xor16);
        assertTrue(xor().withBitsPerKey(17).build(keys) instanceof Xor16);
    }

    @Test
    public void testBuildXorSimple() {
        long[] keys = LongStream.range(0, 64).toArray();
        assertTrue(xor().withBitsPerKey(29).build(keys) instanceof XorSimple);
        assertTrue(xor().withBitsPerKey(32).build(keys) instanceof XorSimple);
    }

    @Test
    public void testBuildXorPlus() {
        long[] keys = LongStream.range(0, 64).toArray();
        assertTrue(xor().plus().withBitsPerKey(29).build(keys) instanceof XorPlus8);
    }
}