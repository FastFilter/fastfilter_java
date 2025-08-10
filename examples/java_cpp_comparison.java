import org.fastfilter.Filter;
import org.fastfilter.FilterType;
import org.fastfilter.cpp.CppFilterType;
import org.fastfilter.utils.Hash;

/**
 * Example demonstrating performance comparison between Java and C++ FastFilter implementations.
 * 
 * This example shows:
 * - How to use both Java and C++ filter implementations
 * - Performance comparison between implementations
 * - Proper resource management for C++ filters
 * - Error handling for library availability
 * 
 * To run this example:
 * 1. Build the project: bazel build //fastfilter:fastfilter_with_cpp
 * 2. Run with FFI enabled: java --enable-native-access=ALL-UNNAMED --enable-preview JavaCppComparison
 */
public class JavaCppComparison {
    
    public static void main(String[] args) {
        // Generate test data
        int size = 1000000;
        long[] keys = generateRandomKeys(size);
        
        System.out.println("FastFilter Java vs C++ Performance Comparison");
        System.out.println("Dataset size: " + size + " keys");
        System.out.println("=" .repeat(60));
        
        // Test Java XOR Binary Fuse filter
        System.out.println("\n1. Java XOR Binary Fuse 8 Filter:");
        testJavaFilter(keys);
        
        // Test C++ XOR8 filter
        System.out.println("\n2. C++ XOR8 Filter:");
        testCppXor8Filter(keys);
        
        // Test C++ Binary Fuse8 filter
        System.out.println("\n3. C++ Binary Fuse8 Filter:");
        testCppBinaryFuse8Filter(keys);
        
        // Direct performance comparison
        System.out.println("\n4. Direct Performance Comparison:");
        performanceComparison(keys);
        
        System.out.println("\nComparison complete!");
    }
    
    private static long[] generateRandomKeys(int size) {
        System.out.println("Generating " + size + " random keys...");
        Hash.setSeed(42L); // For reproducible results
        
        long[] keys = new long[size];
        for (int i = 0; i < size; i++) {
            keys[i] = Hash.hash64(i, 0);
        }
        return keys;
    }
    
    private static void testJavaFilter(long[] keys) {
        try {
            // Construction
            long startTime = System.nanoTime();
            Filter filter = FilterType.XOR_BINARY_FUSE_8.construct(keys, 0);
            long constructionTime = System.nanoTime() - startTime;
            
            // Memory usage
            long bits = filter.getBitCount();
            double bitsPerKey = (double) bits / keys.length;
            
            // Lookup performance test
            startTime = System.nanoTime();
            int found = 0;
            for (int i = 0; i < 10000; i++) {
                if (filter.mayContain(keys[i % keys.length])) {
                    found++;
                }
            }
            long lookupTime = System.nanoTime() - startTime;
            
            System.out.println("  Construction time: " + (constructionTime / 1_000_000) + " ms");
            System.out.println("  Memory usage: " + bits + " bits (" + String.format("%.2f", bitsPerKey) + " bits/key)");
            System.out.println("  Lookup time (10k ops): " + (lookupTime / 1_000_000) + " ms");
            System.out.println("  Found keys: " + found + "/10000");
            
        } catch (Exception e) {
            System.err.println("  Error with Java filter: " + e.getMessage());
        }
    }
    
    private static void testCppXor8Filter(long[] keys) {
        try {
            // Construction
            long startTime = System.nanoTime();
            Filter filter = CppFilterType.XOR8_CPP.construct(keys, 0);
            long constructionTime = System.nanoTime() - startTime;
            
            // Memory usage
            long bits = filter.getBitCount();
            double bitsPerKey = (double) bits / keys.length;
            
            // Lookup performance test
            startTime = System.nanoTime();
            int found = 0;
            for (int i = 0; i < 10000; i++) {
                if (filter.mayContain(keys[i % keys.length])) {
                    found++;
                }
            }
            long lookupTime = System.nanoTime() - startTime;
            
            System.out.println("  Construction time: " + (constructionTime / 1_000_000) + " ms");
            System.out.println("  Memory usage: " + bits + " bits (" + String.format("%.2f", bitsPerKey) + " bits/key)");
            System.out.println("  Lookup time (10k ops): " + (lookupTime / 1_000_000) + " ms");
            System.out.println("  Found keys: " + found + "/10000");
            
            // Cleanup C++ resources
            if (filter instanceof org.fastfilter.cpp.Xor8Filter) {
                ((org.fastfilter.cpp.Xor8Filter) filter).free();
            }
            
        } catch (Exception e) {
            System.err.println("  C++ XOR8 filter not available: " + e.getMessage());
            System.err.println("  (This is normal if C++ library is not built)");
        }
    }
    
    private static void testCppBinaryFuse8Filter(long[] keys) {
        try {
            // Construction
            long startTime = System.nanoTime();
            Filter filter = CppFilterType.BINARY_FUSE8_CPP.construct(keys, 0);
            long constructionTime = System.nanoTime() - startTime;
            
            // Memory usage
            long bits = filter.getBitCount();
            double bitsPerKey = (double) bits / keys.length;
            
            // Lookup performance test
            startTime = System.nanoTime();
            int found = 0;
            for (int i = 0; i < 10000; i++) {
                if (filter.mayContain(keys[i % keys.length])) {
                    found++;
                }
            }
            long lookupTime = System.nanoTime() - startTime;
            
            System.out.println("  Construction time: " + (constructionTime / 1_000_000) + " ms");
            System.out.println("  Memory usage: " + bits + " bits (" + String.format("%.2f", bitsPerKey) + " bits/key)");
            System.out.println("  Lookup time (10k ops): " + (lookupTime / 1_000_000) + " ms");
            System.out.println("  Found keys: " + found + "/10000");
            
            // Cleanup C++ resources
            if (filter instanceof org.fastfilter.cpp.BinaryFuse8Filter) {
                ((org.fastfilter.cpp.BinaryFuse8Filter) filter).free();
            }
            
        } catch (Exception e) {
            System.err.println("  C++ Binary Fuse8 filter not available: " + e.getMessage());
            System.err.println("  (This is normal if C++ library is not built)");
        }
    }
    
    private static void performanceComparison(long[] keys) {
        System.out.println("  Running detailed performance comparison...");
        
        try {
            // Java filter
            long javaStart = System.nanoTime();
            Filter javaFilter = FilterType.XOR_BINARY_FUSE_8.construct(keys, 0);
            long javaConstruction = System.nanoTime() - javaStart;
            
            // C++ filter
            Filter cppFilter = null;
            long cppConstruction = 0;
            try {
                long cppStart = System.nanoTime();
                cppFilter = CppFilterType.XOR8_CPP.construct(keys, 0);
                cppConstruction = System.nanoTime() - cppStart;
            } catch (Exception e) {
                System.err.println("  C++ filter unavailable, skipping comparison");
                return;
            }
            
            // Lookup comparison
            javaStart = System.nanoTime();
            for (int i = 0; i < 100000; i++) {
                javaFilter.mayContain(keys[i % keys.length]);
            }
            long javaLookup = System.nanoTime() - javaStart;
            
            long cppStart = System.nanoTime();
            for (int i = 0; i < 100000; i++) {
                cppFilter.mayContain(keys[i % keys.length]);
            }
            long cppLookup = System.nanoTime() - cppStart;
            
            // Results
            System.out.println("\n  Performance Results:");
            System.out.println("  Construction - Java: " + (javaConstruction / 1_000_000) + " ms, C++: " + (cppConstruction / 1_000_000) + " ms");
            System.out.println("  Construction speedup: " + String.format("%.2fx", (double) javaConstruction / cppConstruction));
            System.out.println("  Lookup (100k) - Java: " + (javaLookup / 1_000_000) + " ms, C++: " + (cppLookup / 1_000_000) + " ms");
            System.out.println("  Lookup speedup: " + String.format("%.2fx", (double) javaLookup / cppLookup));
            
            // Memory comparison
            long javaBits = javaFilter.getBitCount();
            long cppBits = cppFilter.getBitCount();
            System.out.println("  Memory - Java: " + javaBits + " bits, C++: " + cppBits + " bits");
            
            // Cleanup
            if (cppFilter instanceof org.fastfilter.cpp.Xor8Filter) {
                ((org.fastfilter.cpp.Xor8Filter) cppFilter).free();
            }
            
        } catch (Exception e) {
            System.err.println("  Error during comparison: " + e.getMessage());
        }
    }
}