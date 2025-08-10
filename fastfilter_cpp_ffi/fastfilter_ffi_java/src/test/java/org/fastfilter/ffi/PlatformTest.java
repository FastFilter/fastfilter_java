package org.fastfilter.ffi;


import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

public class PlatformTest
{

	public static void main(String[] args) throws Throwable {
		// Platform detection
		PlatformInfo platform = PlatformInfo.getInstance();
		System.out.println("Platform: " + platform.getDetailedPlatformString());
		System.out.println("OS: " + platform.getOS().getName());
		System.out.println("Arch: " + platform.getArch().getName() +
			                   " (" + platform.getArch().bits() + "-bit)");
		System.out.println("Kernel: " + platform.getKernelVersion());
		System.out.println("CPU Features: " + platform.getCPUFeatures());
		System.out.println("Has AVX2: " + platform.hasAVX2());

		// FFI Example - Call native malloc/free
		FFIHelper ffi = new FFIHelper();

		try (Arena arena = Arena.ofConfined()) {
			// Create downcall handle for malloc
			MethodHandle malloc = ffi.downcallHandle(
				platform.getOS().getCLibName(),
				"malloc",
				FFIHelper.Descriptors.POINTER_SIZE_T
			);

			// Create downcall handle for free
			MethodHandle free = ffi.downcallHandle(
				platform.getOS().getCLibName(),
				"free",
				FFIHelper.Descriptors.VOID_POINTER
			);

			// Allocate 1024 bytes
			MemorySegment ptr = (MemorySegment) malloc.invoke(1024L);
			System.out.println("Allocated memory at: " + ptr);

			// Use the memory
			ptr.set(ValueLayout.JAVA_INT, 0, 42);
			int value = ptr.get(ValueLayout.JAVA_INT, 0);
			System.out.println("Value at allocated memory: " + value);

			// Free the memory
			free.invoke(ptr);
			System.out.println("Memory freed");
		}

		// Load custom library example
		NativeLibraryLoaderAdvanced loader = new NativeLibraryLoaderAdvanced();
		try {
			// This would load a custom library from resources or system
			// SymbolLookup myLib = loader.loadLibrary("mylib", "1.0.0");
			System.out.println("Loaded libraries: " + loader.getLoadedLibraries());
		} finally {
			loader.cleanup();
		}
	}
}
