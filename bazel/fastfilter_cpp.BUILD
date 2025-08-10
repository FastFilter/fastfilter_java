# BUILD file for external fastfilter_cpp repository
# This file is applied to the downloaded fastfilter_cpp repository

package(default_visibility = ["//visibility:public"])

load("@rules_cc//cc:defs.bzl", "cc_library", "cc_binary")

# Core C++ FastFilter library with essential filter implementations
cc_library(
    name = "fastfilter_cpp_core",
    hdrs = [
        "src/hashutil.h",
        "src/bloom/bloom.h",
        "src/bloom/counting_bloom.h",
        "src/bloom/simd-block.h",
        "src/bloom/simd-block-fixed-fpp.h",
        "src/xorfilter/xorfilter.h",
        "src/xorfilter/xorfilter_singleheader.h",
        "src/xorfilter/xorfilter_plus.h",
        "src/xorfilter/xor_binary_fuse_filter.h",
        "src/xorfilter/binaryfusefilter_singleheader.h",
        "src/xorfilter/3wise_xor_binary_fuse_filter_lowmem.h",
        "src/xorfilter/3wise_xor_binary_fuse_filter_naive.h",
        "src/xorfilter/4wise_xor_binary_fuse_filter_lowmem.h",
        "src/xorfilter/4wise_xor_binary_fuse_filter_naive.h",
        "src/cuckoo/cuckoofilter.h",
        "src/cuckoo/cuckoofilter_stable.h",
        "src/cuckoo/cuckoo_fuse.h",
        "src/cuckoo/bitsutil.h",
        "src/cuckoo/debug.h", 
        "src/cuckoo/packedtable.h",
        "src/cuckoo/permencoding.h",
        "src/cuckoo/singletable.h",
        "src/cuckoo/printutil.h",
        "src/morton/morton_filter.h",
        "src/morton/compressed_cuckoo_filter.h",
        "src/morton/compressed_cuckoo_config.h",
        "src/morton/morton_sample_configs.h",
        "src/morton/morton_util.h",
        "src/morton/bf.h",
        "src/morton/block.h",
        "src/morton/fixed_point.h",
        "src/morton/hash_util.h",
        "src/morton/test_util.h",
        "src/morton/util.h",
        "src/morton/vector_types.h",
        "src/gcs/gcs.h",
        "src/ribbon/ribbon_impl.h",
        "src/ribbon/ribbon_alg.h",
        "src/ribbon/ribbon_bloom_impl.h",
        "src/ribbon/ribbon_coding_lean.h",
        "src/ribbon/ribbon_fastrange.h",
        "src/ribbon/ribbon_lang.h",
        "src/ribbon/ribbon_math.h",
        "src/ribbon/ribbon_math128.h",
        "src/ribbon/ribbon_port.h",
        "benchmarks/filterapi.h",
        "benchmarks/random.h",
        "benchmarks/timing.h",
    ],
    srcs = [],
    includes = [
        "src",
        "src/bloom",
        "src/xorfilter", 
        "src/cuckoo",
        "src/morton",
        "src/gcs",
        "src/ribbon",
        "benchmarks",
    ],
    copts = [
        "-std=c++11",
        "-O3",
        "-march=native",
        "-Wall",
    ] + select({
        "@platforms//cpu:x86_64": ["-mavx2"],
        "//conditions:default": [],
    }),
    defines = select({
        "@platforms//cpu:x86_64": ["__AVX2__"],
        "//conditions:default": [],
    }),
)

# SIMD and advanced filter implementations (Linux/AVX2 only)
cc_library(
    name = "fastfilter_cpp_simd",
    hdrs = [
        "src/bloom/simd-block.h",
        "src/bloom/simd-block-fixed-fpp.h",
        "src/gqf/gqf_cpp.h",
        "src/vqf/vqf_cpp.h",
        "src/morton/morton_filter.h",
        "src/morton/compressed_cuckoo_filter.h",
        "src/morton/morton_sample_configs.h",
        "src/ribbon/ribbon_impl.h",
    ],
    srcs = [
        "src/gqf/gqf.c",
        "src/gqf/gqf_hashutil.c", 
        "src/vqf/vqf_filter.c",
    ],
    deps = [":fastfilter_cpp_core"],
    includes = [
        "src/gqf",
        "src/vqf",
        "src/morton",
        "src/ribbon",
    ],
    copts = [
        "-std=c++11",
        "-O3",
        "-march=native",
        "-mavx2",
        "-Wall",
    ],
    defines = ["__AVX2__"],
    target_compatible_with = select({
        "@platforms//cpu:x86_64": [],
        "//conditions:default": ["@platforms//:incompatible"],
    }),
)

# Main benchmark executable
cc_binary(
    name = "bulk_insert_and_query",
    srcs = ["benchmarks/bulk-insert-and-query.cc"],
    deps = [
        ":fastfilter_cpp_core",
    ] + select({
        "@platforms//cpu:x86_64": [":fastfilter_cpp_simd"],
        "//conditions:default": [],
    }),
    includes = ["benchmarks"],
    copts = [
        "-std=c++11",
        "-O3", 
        "-march=native",
        "-Wall",
    ] + select({
        "@platforms//cpu:x86_64": ["-mavx2"],
        "//conditions:default": [],
    }),
    defines = select({
        "@platforms//cpu:x86_64": ["__AVX2__"],
        "//conditions:default": [],
    }),
)

# Stream benchmark executable
cc_binary(
    name = "stream",
    srcs = ["benchmarks/stream.cc"],
    deps = [
        ":fastfilter_cpp_core",
    ] + select({
        "@platforms//cpu:x86_64": [":fastfilter_cpp_simd"],
        "//conditions:default": [],
    }),
    includes = ["benchmarks"],
    copts = [
        "-std=c++11",
        "-O3",
        "-march=native", 
        "-Wall",
    ] + select({
        "@platforms//cpu:x86_64": ["-mavx2"],
        "//conditions:default": [],
    }),
    defines = select({
        "@platforms//cpu:x86_64": ["__AVX2__"],
        "//conditions:default": [],
    }),
)

# Construction failure test executable
cc_binary(
    name = "construction_failure",
    srcs = ["benchmarks/construction-failure.cc"],
    deps = [
        ":fastfilter_cpp_core",
    ] + select({
        "@platforms//cpu:x86_64": [":fastfilter_cpp_simd"],
        "//conditions:default": [],
    }),
    includes = ["benchmarks"],
    copts = [
        "-std=c++11",
        "-O3",
        "-march=native",
        "-Wall", 
    ] + select({
        "@platforms//cpu:x86_64": ["-mavx2"],
        "//conditions:default": [],
    }),
    defines = select({
        "@platforms//cpu:x86_64": ["__AVX2__"],
        "//conditions:default": [],
    }),
)

# FFI wrapper sources - generate inline for external fastfilter_cpp repository
genrule(
    name = "generate_ffi_wrapper",
    outs = ["ffi_wrapper.c"],
    cmd = """
cat > $@ << 'EOF'
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>

// Include the single-header C implementations
#include "src/xorfilter/xorfilter_singleheader.h"
#include "src/xorfilter/binaryfusefilter_singleheader.h"

// XOR8 Filter FFI wrappers
void* xor8_allocate_wrapper(uint64_t size) {
    xor8_t* filter = (xor8_t*)malloc(sizeof(xor8_t));
    if (filter == NULL) return NULL;
    
    if (!xor8_allocate(size, filter)) {
        free(filter);
        return NULL;
    }
    return filter;
}

bool xor8_populate_wrapper(void* filter_ptr, uint64_t* keys, uint64_t length) {
    if (filter_ptr == NULL || keys == NULL) return false;
    xor8_t* filter = (xor8_t*)filter_ptr;
    return xor8_buffered_populate(keys, length, filter);
}

bool xor8_contain_wrapper(void* filter_ptr, uint64_t key) {
    if (filter_ptr == NULL) return false;
    xor8_t* filter = (xor8_t*)filter_ptr;
    return xor8_contain(key, filter);
}

void xor8_free_wrapper(void* filter_ptr) {
    if (filter_ptr == NULL) return;
    xor8_t* filter = (xor8_t*)filter_ptr;
    xor8_free(filter);
    free(filter);
}

uint64_t xor8_size_in_bytes_wrapper(void* filter_ptr) {
    if (filter_ptr == NULL) return 0;
    xor8_t* filter = (xor8_t*)filter_ptr;
    return xor8_size_in_bytes(filter);
}

// Binary Fuse8 Filter FFI wrappers
void* binary_fuse8_allocate_wrapper(uint64_t size) {
    binary_fuse8_t* filter = (binary_fuse8_t*)malloc(sizeof(binary_fuse8_t));
    if (filter == NULL) return NULL;
    
    if (!binary_fuse8_allocate(size, filter)) {
        free(filter);
        return NULL;
    }
    return filter;
}

bool binary_fuse8_populate_wrapper(void* filter_ptr, uint64_t* keys, uint64_t length) {
    if (filter_ptr == NULL || keys == NULL) return false;
    binary_fuse8_t* filter = (binary_fuse8_t*)filter_ptr;
    return binary_fuse8_populate(keys, length, filter);
}

bool binary_fuse8_contain_wrapper(void* filter_ptr, uint64_t key) {
    if (filter_ptr == NULL) return false;
    binary_fuse8_t* filter = (binary_fuse8_t*)filter_ptr;
    return binary_fuse8_contain(key, filter);
}

void binary_fuse8_free_wrapper(void* filter_ptr) {
    if (filter_ptr == NULL) return;
    binary_fuse8_t* filter = (binary_fuse8_t*)filter_ptr;
    binary_fuse8_free(filter);
    free(filter);
}

uint64_t binary_fuse8_size_in_bytes_wrapper(void* filter_ptr) {
    if (filter_ptr == NULL) return 0;
    binary_fuse8_t* filter = (binary_fuse8_t*)filter_ptr;
    return binary_fuse8_size_in_bytes(filter);
}
EOF
""",
)

# Platform-specific FFI libraries using cc_binary to create shared libraries directly
cc_binary(
    name = "fastfilter_cpp_ffi_linux_x86_64.so",
    srcs = [
        ":generate_ffi_wrapper",
        "src/xorfilter/xorfilter_singleheader.h", 
        "src/xorfilter/binaryfusefilter_singleheader.h",
    ],
    includes = [
        "src",
        "src/xorfilter",
    ],
    copts = [
        "-std=c11",
        "-O3",
        "-fPIC",
        "-mavx2",
        "-march=native",
    ],
    defines = ["__AVX2__"],
    linkshared = True,
    target_compatible_with = [
        "@platforms//os:linux",
        "@platforms//cpu:x86_64",
    ],
)

cc_binary(
    name = "fastfilter_cpp_ffi_macos_x86_64.dylib",
    srcs = [
        ":generate_ffi_wrapper",
        "src/xorfilter/xorfilter_singleheader.h", 
        "src/xorfilter/binaryfusefilter_singleheader.h",
    ],
    includes = [
        "src",
        "src/xorfilter",
    ],
    copts = [
        "-std=c11",
        "-O3",
        "-fPIC",
        "-mavx2",
        "-march=native",
    ],
    defines = ["__AVX2__"],
    linkshared = True,
    target_compatible_with = [
        "@platforms//os:macos",
        "@platforms//cpu:x86_64",
    ],
)

cc_binary(
    name = "fastfilter_cpp_ffi_macos_arm64.dylib",
    srcs = [
        ":generate_ffi_wrapper",
        "src/xorfilter/xorfilter_singleheader.h", 
        "src/xorfilter/binaryfusefilter_singleheader.h",
    ],
    includes = [
        "src",
        "src/xorfilter",
    ],
    copts = [
        "-std=c11",
        "-O3",
        "-fPIC",
    ],
    linkshared = True,
    target_compatible_with = [
        "@platforms//os:macos", 
        "@platforms//cpu:arm64",
    ],
)

cc_binary(
    name = "fastfilter_cpp_ffi_linux_arm64.so",
    srcs = [
        ":generate_ffi_wrapper",
        "src/xorfilter/xorfilter_singleheader.h", 
        "src/xorfilter/binaryfusefilter_singleheader.h",
    ],
    includes = [
        "src",
        "src/xorfilter",
    ],
    copts = [
        "-std=c11",
        "-O3",
        "-fPIC",
    ],
    linkshared = True,
    target_compatible_with = [
        "@platforms//os:linux",
        "@platforms//cpu:arm64",
    ],
)

cc_binary(
    name = "fastfilter_cpp_ffi_windows_x86_64.dll",
    srcs = [
        ":generate_ffi_wrapper",
        "src/xorfilter/xorfilter_singleheader.h", 
        "src/xorfilter/binaryfusefilter_singleheader.h",
    ],
    includes = [
        "src",
        "src/xorfilter",
    ],
    copts = [
        "-std=c11",
        "-O3",
        "-fPIC",
        "-mavx2",
        "-march=native",
    ],
    defines = ["__AVX2__"],
    linkshared = True,
    target_compatible_with = [
        "@platforms//os:windows",
        "@platforms//cpu:x86_64", 
    ],
)

# Convenience aliases for the shared libraries
alias(
    name = "fastfilter_cpp_ffi_linux_x86_64",
    actual = ":fastfilter_cpp_ffi_linux_x86_64.so",
)

alias(
    name = "fastfilter_cpp_ffi_linux_arm64",
    actual = ":fastfilter_cpp_ffi_linux_arm64.so",
)

alias(
    name = "fastfilter_cpp_ffi_macos_x86_64",
    actual = ":fastfilter_cpp_ffi_macos_x86_64.dylib",
)

alias(
    name = "fastfilter_cpp_ffi_macos_arm64",
    actual = ":fastfilter_cpp_ffi_macos_arm64.dylib",
)

alias(
    name = "fastfilter_cpp_ffi_windows_x86_64",
    actual = ":fastfilter_cpp_ffi_windows_x86_64.dll",
)

# Platform-specific default aliases (no nested selects)
alias(
    name = "fastfilter_cpp_ffi_linux_default",
    actual = select({
        "@platforms//cpu:x86_64": ":fastfilter_cpp_ffi_linux_x86_64",
        "@platforms//cpu:arm64": ":fastfilter_cpp_ffi_linux_arm64",
        "//conditions:default": ":fastfilter_cpp_ffi_linux_x86_64",
    }),
)

alias(
    name = "fastfilter_cpp_ffi_macos_default",
    actual = select({
        "@platforms//cpu:x86_64": ":fastfilter_cpp_ffi_macos_x86_64",
        "@platforms//cpu:arm64": ":fastfilter_cpp_ffi_macos_arm64",
        "//conditions:default": ":fastfilter_cpp_ffi_macos_arm64",
    }),
)

# Default FFI library (backwards compatibility)
alias(
    name = "fastfilter_cpp_ffi",
    actual = select({
        "@platforms//os:linux": ":fastfilter_cpp_ffi_linux_default",
        "@platforms//os:macos": ":fastfilter_cpp_ffi_macos_default",
        "@platforms//os:windows": ":fastfilter_cpp_ffi_windows_x86_64",
        "//conditions:default": ":fastfilter_cpp_ffi_macos_arm64",
    }),
)