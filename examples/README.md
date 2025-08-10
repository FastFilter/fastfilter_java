# FastFilter Java Examples

This directory contains practical examples demonstrating how to use the FastFilter Java library effectively.

## Overview

The examples showcase:
- Basic filter usage patterns
- Performance comparison between Java and C++ implementations
- Best practices for different use cases
- Error handling and resource management
- Integration with real-world applications

## Prerequisites

- JDK 24+ (for C++ integration examples)
- Maven 3.6+ or Bazel (for building)
- FastFilter Java library

## Running Examples

### Option 1: Maven Build
```bash
# Build the main project first
mvn clean package

# Compile examples
javac -cp target/fastfilter-*.jar examples/*.java

# Run Java-only examples
java -cp "target/fastfilter-*.jar:examples" JavaCppComparison

# Run examples with C++ integration (requires JDK 24)
java --enable-native-access=ALL-UNNAMED --enable-preview -cp "target/fastfilter-*.jar:examples" JavaCppComparison
```

### Option 2: Bazel Build
```bash
# Build project with C++ integration
bazel build //fastfilter:fastfilter_with_cpp

# Run examples
bazel run //examples:java_cpp_comparison
```

## Available Examples

### 1. Java vs C++ Performance Comparison (`java_cpp_comparison.java`)

**Purpose**: Demonstrates performance differences between Java and C++ filter implementations.

**Features**:
- Generates large random datasets (1M keys)
- Tests construction and lookup performance
- Compares memory usage
- Proper resource management for C++ filters
- Graceful fallback when C++ libraries unavailable

**Sample Output**:
```
FastFilter Java vs C++ Performance Comparison
Dataset size: 1000000 keys
============================================================

1. Java XOR Binary Fuse 8 Filter:
  Construction time: 45 ms
  Memory usage: 9804856 bits (9.80 bits/key)
  Lookup time (10k ops): 8 ms
  Found keys: 10000/10000

2. C++ XOR8 Filter:
  Construction time: 15 ms
  Memory usage: 9843712 bits (9.84 bits/key)
  Lookup time (10k ops): 5 ms
  Found keys: 10000/10000

Performance Results:
  Construction speedup: 3.00x
  Lookup speedup: 1.60x
```

**Usage Patterns Demonstrated**:
- Basic filter creation and usage
- Performance timing techniques
- C++ resource cleanup with `filter.free()`
- Exception handling for library availability

## Common Usage Patterns

### Basic Filter Usage
```java
import org.fastfilter.Filter;
import org.fastfilter.FilterType;

// Create test data
long[] keys = {1L, 2L, 3L, 4L, 5L};

// Create filter
Filter filter = FilterType.XOR_BINARY_FUSE_8.construct(keys, 0);

// Test membership
boolean exists = filter.mayContain(3L); // true
boolean missing = filter.mayContain(99L); // false (or rare false positive)

// Check memory usage
long bits = filter.getBitCount();
double bitsPerKey = (double) bits / keys.length;
System.out.printf("Filter uses %.2f bits per key%n", bitsPerKey);
```

### C++ Filter Usage with Resource Management
```java
import org.fastfilter.cpp.CppFilterType;
import org.fastfilter.cpp.Xor8Filter;

try {
    // Create C++ filter
    Filter cppFilter = CppFilterType.XOR8_CPP.construct(keys, 0);
    
    // Use filter
    boolean result = cppFilter.mayContain(key);
    
    // Cleanup resources
    if (cppFilter instanceof Xor8Filter) {
        ((Xor8Filter) cppFilter).free();
    }
} catch (RuntimeException e) {
    // Fallback to Java implementation
    Filter javaFilter = FilterType.XOR_BINARY_FUSE_8.construct(keys, 0);
}
```

### Performance Benchmarking
```java
// Warm up JVM
for (int i = 0; i < 1000; i++) {
    FilterType.BLOOM.construct(smallDataset, 10);
}

// Measure performance
long startTime = System.nanoTime();
Filter filter = FilterType.XOR_BINARY_FUSE_8.construct(dataset, 0);
long constructionTime = System.nanoTime() - startTime;

System.out.println("Construction time: " + (constructionTime / 1_000_000) + " ms");
```

## Creating New Examples

To add a new example:

1. **Create the Java file** in this directory
2. **Follow naming convention**: `descriptive_name.java`
3. **Include comprehensive documentation**:
   - Purpose and use case
   - Prerequisites
   - Expected output
   - Key concepts demonstrated

4. **Example template**:
```java
/**
 * Example: [Brief Description]
 * 
 * This example demonstrates:
 * - [Key concept 1]
 * - [Key concept 2]
 * - [Key concept 3]
 * 
 * Prerequisites:
 * - [Requirement 1]
 * - [Requirement 2]
 * 
 * To run:
 * javac -cp ../target/fastfilter-*.jar YourExample.java
 * java -cp "../target/fastfilter-*.jar:." YourExample
 */
public class YourExample {
    public static void main(String[] args) {
        System.out.println("FastFilter Example: [Name]");
        // Implementation
    }
}
```

5. **Update this README** with the new example

## Best Practices Demonstrated

### Error Handling
- Always handle potential `RuntimeException` from C++ filters
- Provide fallback to Java implementations
- Validate input data before filter construction

### Performance Testing
- Include JVM warmup for accurate measurements
- Use appropriate dataset sizes for meaningful results
- Test both construction and lookup performance
- Consider memory pressure effects

### Resource Management
- Always call `free()` on C++ filters when done
- Use try-finally or try-with-resources patterns
- Handle cleanup in exception scenarios

### Memory Usage Analysis
- Calculate and report bits per key
- Compare memory usage across filter types
- Consider total memory including overhead

## Troubleshooting

### "C++ filter not available" Errors
- Ensure C++ libraries are built: `bazel build //fastfilter_cpp:all`
- Check library path configuration
- Verify JDK 24 with FFI support
- Run with `--enable-native-access=ALL-UNNAMED --enable-preview`

### Performance Variations
- Run multiple iterations for stable measurements
- Consider JVM warmup effects
- Check for background system activity
- Test with different dataset sizes and patterns

### Compilation Errors
- Ensure correct classpath includes FastFilter JAR
- Verify JDK version compatibility
- Check import statements

## Related Documentation

- [Main README](../README.md) - Library overview and basic usage
- [Build Instructions](../BUILD.md) - Comprehensive build guide
- [Integration Guide](../docs/INTEGRATION.md) - C++ integration details
- [JMH Benchmarks](../docs/JMH_BENCHMARKS.md) - Formal benchmarking guide

## Contributing Examples

We welcome additional examples! Please:

1. Focus on practical, real-world use cases
2. Include comprehensive documentation
3. Test examples on multiple platforms
4. Follow existing code style and patterns
5. Add appropriate error handling
6. Update this README with new examples

Examples that would be valuable:
- Database query optimization
- Web application caching
- Distributed systems membership testing
- Large-scale data processing pipelines
- Custom filter implementations