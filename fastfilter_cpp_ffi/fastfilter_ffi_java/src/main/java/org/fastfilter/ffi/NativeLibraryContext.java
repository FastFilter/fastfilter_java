package org.fastfilter.ffi;

import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;

public sealed interface NativeLibraryContext
	permits DefaultNativeContext
{

	static NativeLibraryContext context(SymbolLookup symbolLookup)
	{
		return DefaultNativeContext.of(symbolLookup);
	}

	/**
	 * Get the native linker for this context.
	 * This is used to resolve symbols and call native functions.
	 *
	 * @return the native linker
	 */
	Linker linker();

	/**
	 * Get the symbol lookup for this context.
	 * This is used to find native symbols in libraries.
	 *
	 * @return the symbol lookup
	 */
	SymbolLookup lookup();
}
