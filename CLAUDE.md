1. I want you to upgrade this project to the latest JDK 24 version
2. Update all test to use the latest JUnit 5 version
3. Ensure all dependencies are compatible with JDK 24
4. Update all dependencies to their latest versions
5. Create comprehensive documentation of the API and data structures in Markdown format, create memory and docs in ./docs/CLAUDE.md
6. Convert all test run via main to jmh benchmarks
7. Ensure the project is fully functional and passes all tests after the upgrade
8. In JHM tests use KeyGenerationStrategy instead of RandomGenerator.createRandomUniqueListFast(list, size);
9. now Create google bazel build files and integrate https://github.com/FastFilter/fastfilter_cpp build into this project for performance comparisions
10. Generate JDK FFI interfaces for the fastfilter C++ library library
11. Integrate the fastfilter C++ library into the Java project using JDK FFI
12. Ensure the C++ library is callable from Java and can be used in the benchmarks
13. Document the integration process and provide examples in the documentation
14. Ensure the project is compatible with both Java and C++ codebases
15. Create bazel build files for the Java project and the C++ library