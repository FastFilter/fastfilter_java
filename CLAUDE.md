1. Review all changes in this repository
2. Test all github workflows via act
3. Fix any failures
4. Add missing documentation
5. Tests in fastfilter_cpp_ffi/fastfilter_ffi_java are failing, fix them
6. Make PlatformTest junit5 test and run it via QEMU and Docker to ensure it works on all platforms
7. Fix LibCTypeQemuTest to be able to run it on all platforms locally
8. Add a test for the C++ FFI to ensure it works on all platforms