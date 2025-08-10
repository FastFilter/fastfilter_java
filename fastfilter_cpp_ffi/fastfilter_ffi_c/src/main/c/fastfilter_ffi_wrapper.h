#ifndef FASTFILTER_FFI_WRAPPER_H
#define FASTFILTER_FFI_WRAPPER_H

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

//
// XOR8 Filter FFI functions
//

/**
 * Allocate and initialize a new XOR8 filter for the given number of keys.
 * 
 * @param size Expected number of keys to be inserted
 * @return Pointer to allocated filter, or NULL on failure
 */
void* xor8_allocate_wrapper(uint64_t size);

/**
 * Populate the XOR8 filter with the given keys.
 * 
 * @param filter_ptr Pointer to allocated filter
 * @param keys Array of 64-bit keys
 * @param length Number of keys in the array
 * @return true if successful, false on failure
 */
bool xor8_populate_wrapper(void* filter_ptr, uint64_t* keys, uint64_t length);

/**
 * Check if a key might be contained in the XOR8 filter.
 * 
 * @param filter_ptr Pointer to populated filter
 * @param key 64-bit key to check
 * @return true if key might be present, false if definitely not present
 */
bool xor8_contain_wrapper(void* filter_ptr, uint64_t key);

/**
 * Free the resources used by an XOR8 filter.
 * 
 * @param filter_ptr Pointer to filter to be freed
 */
void xor8_free_wrapper(void* filter_ptr);

/**
 * Get the size in bytes of an XOR8 filter.
 * 
 * @param filter_ptr Pointer to filter
 * @return Size in bytes, or 0 if filter is NULL
 */
uint64_t xor8_size_in_bytes_wrapper(void* filter_ptr);

//
// Binary Fuse8 Filter FFI functions
//

/**
 * Allocate and initialize a new Binary Fuse8 filter for the given number of keys.
 * 
 * @param size Expected number of keys to be inserted
 * @return Pointer to allocated filter, or NULL on failure
 */
void* binary_fuse8_allocate_wrapper(uint64_t size);

/**
 * Populate the Binary Fuse8 filter with the given keys.
 * 
 * @param filter_ptr Pointer to allocated filter
 * @param keys Array of 64-bit keys
 * @param length Number of keys in the array
 * @return true if successful, false on failure
 */
bool binary_fuse8_populate_wrapper(void* filter_ptr, uint64_t* keys, uint64_t length);

/**
 * Check if a key might be contained in the Binary Fuse8 filter.
 * 
 * @param filter_ptr Pointer to populated filter
 * @param key 64-bit key to check
 * @return true if key might be present, false if definitely not present
 */
bool binary_fuse8_contain_wrapper(void* filter_ptr, uint64_t key);

/**
 * Free the resources used by a Binary Fuse8 filter.
 * 
 * @param filter_ptr Pointer to filter to be freed
 */
void binary_fuse8_free_wrapper(void* filter_ptr);

/**
 * Get the size in bytes of a Binary Fuse8 filter.
 * 
 * @param filter_ptr Pointer to filter
 * @return Size in bytes, or 0 if filter is NULL
 */
uint64_t binary_fuse8_size_in_bytes_wrapper(void* filter_ptr);

#ifdef __cplusplus
}
#endif

#endif /* FASTFILTER_FFI_WRAPPER_H */