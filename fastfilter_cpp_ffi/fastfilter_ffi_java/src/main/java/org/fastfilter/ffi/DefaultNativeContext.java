package org.fastfilter.ffi;

import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.util.Objects;

record DefaultNativeContext(Linker linker,
                            SymbolLookup lookup)
	implements NativeLibraryContext
{
	DefaultNativeContext{
		Objects.requireNonNull(linker, "linker");
		Objects.requireNonNull(lookup, "lookup");
	}

	static DefaultNativeContext of(Linker linker,
	                               SymbolLookup lookup) {
		return new DefaultNativeContext(linker, lookup);
	}

	static DefaultNativeContext of(SymbolLookup lookup) {
		return of(Linker.nativeLinker(), lookup);
	}
}
