#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>

// Include the single-header C implementations
// Note: These includes assume the fastfilter_cpp headers are available
// In practice, these would be provided by the Bazel build system
#include "src/xorfilter/xorfilter_singleheader.h"
#include "src/xorfilter/binaryfusefilter_singleheader.h"

//
// XOR8 Filter FFI wrappers
//

/**
 * Allocate and initialize a new XOR8 filter for the given number of keys.
 * 
 * @param size Expected number of keys to be inserted
 * @return Pointer to allocated filter, or NULL on failure
 */
void* xor8_allocate_wrapper(uint64_t size) {
    xor8_t* filter = (xor8_t*)malloc(sizeof(xor8_t));
    if (filter == NULL) {
        return NULL;
    }
    
    if (!xor8_allocate(size, filter)) {
        free(filter);
        return NULL;
    }
    return filter;
}

/**
 * Populate the XOR8 filter with the given keys.
 * 
 * @param filter_ptr Pointer to allocated filter
 * @param keys Array of 64-bit keys
 * @param length Number of keys in the array
 * @return true if successful, false on failure
 */
bool xor8_populate_wrapper(void* filter_ptr, uint64_t* keys, uint64_t length) {
    if (filter_ptr == NULL || keys == NULL) {
        return false;
    }
    xor8_t* filter = (xor8_t*)filter_ptr;
    return xor8_buffered_populate(keys, length, filter);
}

/**
 * Check if a key might be contained in the XOR8 filter.
 * 
 * @param filter_ptr Pointer to populated filter
 * @param key 64-bit key to check
 * @return true if key might be present, false if definitely not present
 */
bool xor8_contain_wrapper(void* filter_ptr, uint64_t key) {
    if (filter_ptr == NULL) {
        return false;
    }
    xor8_t* filter = (xor8_t*)filter_ptr;
    return xor8_contain(key, filter);
}

/**
 * Free the resources used by an XOR8 filter.
 * 
 * @param filter_ptr Pointer to filter to be freed
 */
void xor8_free_wrapper(void* filter_ptr) {
    if (filter_ptr == NULL) {
        return;
    }
    xor8_t* filter = (xor8_t*)filter_ptr;
    xor8_free(filter);
    free(filter);
}

/**
 * Get the size in bytes of an XOR8 filter.
 * 
 * @param filter_ptr Pointer to filter
 * @return Size in bytes, or 0 if filter is NULL
 */
uint64_t xor8_size_in_bytes_wrapper(void* filter_ptr) {
    if (filter_ptr == NULL) {
        return 0;
    }
    xor8_t* filter = (xor8_t*)filter_ptr;
    return xor8_size_in_bytes(filter);
}

//
// Binary Fuse8 Filter FFI wrappers
//

/**
 * Allocate and initialize a new Binary Fuse8 filter for the given number of keys.
 * 
 * @param size Expected number of keys to be inserted
 * @return Pointer to allocated filter, or NULL on failure
 */
void* binary_fuse8_allocate_wrapper(uint64_t size) {
    binary_fuse8_t* filter = (binary_fuse8_t*)malloc(sizeof(binary_fuse8_t));
    if (filter == NULL) {
        return NULL;
    }
    
    if (!binary_fuse8_allocate(size, filter)) {
        free(filter);
        return NULL;
    }
    return filter;
}

/**
 * Populate the Binary Fuse8 filter with the given keys.
 * 
 * @param filter_ptr Pointer to allocated filter
 * @param keys Array of 64-bit keys
 * @param length Number of keys in the array
 * @return true if successful, false on failure
 */
bool binary_fuse8_populate_wrapper(void* filter_ptr, uint64_t* keys, uint64_t length) {
    if (filter_ptr == NULL || keys == NULL) {
        return false;
    }
    binary_fuse8_t* filter = (binary_fuse8_t*)filter_ptr;
    return binary_fuse8_populate(keys, length, filter);
}

/**
 * Check if a key might be contained in the Binary Fuse8 filter.
 * 
 * @param filter_ptr Pointer to populated filter
 * @param key 64-bit key to check
 * @return true if key might be present, false if definitely not present
 */
bool binary_fuse8_contain_wrapper(void* filter_ptr, uint64_t key) {
    if (filter_ptr == NULL) {
        return false;
    }
    binary_fuse8_t* filter = (binary_fuse8_t*)filter_ptr;
    return binary_fuse8_contain(key, filter);
}

/**
 * Free the resources used by a Binary Fuse8 filter.
 * 
 * @param filter_ptr Pointer to filter to be freed
 */
void binary_fuse8_free_wrapper(void* filter_ptr) {
    if (filter_ptr == NULL) {
        return;
    }
    binary_fuse8_t* filter = (binary_fuse8_t*)filter_ptr;
    binary_fuse8_free(filter);
    free(filter);
}

/**
 * Get the size in bytes of a Binary Fuse8 filter.
 * 
 * @param filter_ptr Pointer to filter
 * @return Size in bytes, or 0 if filter is NULL
 */
uint64_t binary_fuse8_size_in_bytes_wrapper(void* filter_ptr) {
    if (filter_ptr == NULL) {
        return 0;
    }
    binary_fuse8_t* filter = (binary_fuse8_t*)filter_ptr;
    return binary_fuse8_size_in_bytes(filter);
}