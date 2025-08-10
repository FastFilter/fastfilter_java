package org.fastfilter.ffi;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

public class FFIHelper {

	private final NativeLibraryLoaderAdvanced loader;
	private final Linker linker;
	private final SymbolLookup stdlibLookup;

	public FFIHelper() {
		this.loader = new NativeLibraryLoaderAdvanced();
		this.linker = Linker.nativeLinker();
		this.stdlibLookup = linker.defaultLookup();
	}

	/**
	 * Create a downcall handle for a native function
	 */
	public MethodHandle downcallHandle(String libraryName,
	                                   String functionName,
	                                   FunctionDescriptor descriptor) {
		try {
			SymbolLookup lookup = loader.loadLibrary(libraryName);
			Optional<MemorySegment> symbol = lookup.find(functionName);

			if (symbol.isEmpty()) {
				// Try stdlib lookup
				symbol = stdlibLookup.find(functionName);
			}

			return symbol.map(seg -> linker.downcallHandle(seg, descriptor))
			             .orElseThrow(() -> new IllegalArgumentException(
				             "Function not found: " + functionName));
		} catch (Exception e) {
			throw new RuntimeException("Failed to create downcall handle", e);
		}
	}

	/**
	 * Create an upcall stub for callbacks
	 */
	public MemorySegment upcallStub(
		MethodHandle target,
		FunctionDescriptor descriptor,
		Arena arena) {
		try {
			return linker.upcallStub(target, descriptor, arena);
		} catch (Exception e) {
			throw new RuntimeException("Failed to create upcall stub", e);
		}
	}

	/**
	 * Platform-specific C type layouts
	 */
	public static class CLayouts {
		public static final ValueLayout.OfBoolean C_BOOL = ValueLayout.JAVA_BOOLEAN;
		public static final ValueLayout.OfByte C_CHAR = ValueLayout.JAVA_BYTE;
		public static final ValueLayout.OfShort C_SHORT = ValueLayout.JAVA_SHORT;
		public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;
		public static final ValueLayout.OfLong C_LONG_LONG = ValueLayout.JAVA_LONG;
		public static final ValueLayout.OfFloat C_FLOAT = ValueLayout.JAVA_FLOAT;
		public static final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE;
		public static final AddressLayout C_POINTER = ValueLayout.ADDRESS;

		// Platform-specific long type
		public static final ValueLayout C_LONG = getPlatformLong();
		public static final ValueLayout C_SIZE_T = getPlatformSizeT();

		private static ValueLayout getPlatformLong() {
			PlatformInfo platform = PlatformInfo.getInstance();
			if (platform.getOS() == OSType.WINDOWS &&
				    platform.getArch().bits() == 64) {
				return ValueLayout.JAVA_INT; // Windows 64-bit uses 32-bit long
			}
			return platform.getArch().bits() == 64 ?
			       ValueLayout.JAVA_LONG : ValueLayout.JAVA_INT;
		}

		private static ValueLayout getPlatformSizeT() {
			return PlatformInfo.getInstance().getArch().bits() == 64 ?
			       ValueLayout.JAVA_LONG : ValueLayout.JAVA_INT;
		}
	}

	/**
	 * Common function descriptors
	 */
	public static class Descriptors {
		public static final FunctionDescriptor VOID_VOID =
			FunctionDescriptor.ofVoid();

		public static final FunctionDescriptor INT_VOID =
			FunctionDescriptor.of(CLayouts.C_INT);

		public static final FunctionDescriptor VOID_INT =
			FunctionDescriptor.ofVoid(CLayouts.C_INT);

		public static final FunctionDescriptor INT_INT_INT =
			FunctionDescriptor.of(CLayouts.C_INT, CLayouts.C_INT, CLayouts.C_INT);

		public static final FunctionDescriptor POINTER_VOID =
			FunctionDescriptor.of(CLayouts.C_POINTER);

		public static final FunctionDescriptor VOID_POINTER =
			FunctionDescriptor.ofVoid(CLayouts.C_POINTER);

		public static final FunctionDescriptor POINTER_SIZE_T =
			FunctionDescriptor.of(CLayouts.C_POINTER, CLayouts.C_SIZE_T);
	}
}

