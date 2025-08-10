# FastFilter C FFI Wrapper

This directory contains C wrapper functions that provide a Foreign Function Interface (FFI) for the FastFilter C++ library.

## Files

- `fastfilter_ffi_wrapper.h` - Header file with function declarations
- `fastfilter_ffi_wrapper.c` - Implementation of FFI wrapper functions

## Purpose

These C wrapper functions provide a simplified C-compatible interface to the FastFilter C++ library, enabling:

1. **Java Integration**: Use with JDK 24+ Foreign Function Interface (FFI)
2. **C Compatibility**: Provide C-style functions for C++ filter implementations
3. **Memory Management**: Safe allocation and deallocation of filter objects
4. **Cross-Language Support**: Enable bindings for other languages

## Supported Filters

### XOR8 Filter
- `xor8_allocate_wrapper()` - Allocate filter for given size
- `xor8_populate_wrapper()` - Populate with keys
- `xor8_contain_wrapper()` - Check key membership
- `xor8_free_wrapper()` - Free filter resources
- `xor8_size_in_bytes_wrapper()` - Get memory usage

### Binary Fuse8 Filter
- `binary_fuse8_allocate_wrapper()` - Allocate filter for given size
- `binary_fuse8_populate_wrapper()` - Populate with keys
- `binary_fuse8_contain_wrapper()` - Check key membership
- `binary_fuse8_free_wrapper()` - Free filter resources
- `binary_fuse8_size_in_bytes_wrapper()` - Get memory usage

## Usage

These functions are not intended to be called directly but through:
1. The Java FFI bindings in `fastfilter_ffi_java`
2. The compiled native libraries in `native_libs`
3. The Bazel build system that compiles them into shared libraries

## Building

These C files are compiled by Bazel as part of the fastfilter_cpp external dependency:
- Linux: `libfastfilter_cpp_ffi.so`
- macOS: `libfastfilter_cpp_ffi.dylib`
- Windows: `fastfilter_cpp_ffi.dll`

The compiled shared libraries are then packaged into platform-specific JAR files for distribution.