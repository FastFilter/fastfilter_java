package org.fastfilter.ffi;


import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record LibCInfo(
		LibCType type,
		String version,
		List<Path> libraryPaths,
		Set<String> features,
		int confidenceLevel) {


	public LibCInfo{
		Objects.requireNonNull(type, "LibC type cannot be null");
		Objects.requireNonNull(version, "LibC version cannot be null");
		libraryPaths = List.copyOf(Objects.requireNonNull(libraryPaths, "Library paths cannot be null"));
		features = Set.copyOf(Objects.requireNonNull(features, "Features cannot be null"));
		if (confidenceLevel < 0 || confidenceLevel > 100) {
			throw new IllegalArgumentException("Confidence level must be between 0 and 100");
		}
	}

	public LibCType getType() { return type; }
	public List<Path> getLibraryPaths() { return libraryPaths; }
	public Set<String> getFeatures() { return features; }
	public int getConfidenceLevel() { return confidenceLevel; }

	public boolean isMusl() { return type == LibCType.MUSL; }
	public boolean isGlibc() { return type == LibCType.GLIBC; }
	public boolean isAndroid() { return type == LibCType.BIONIC; }
	public boolean isBSD() { return type == LibCType.BSD_LIBC; }
	public boolean isMacOS() { return type == LibCType.DARWIN_LIBC; }
	public boolean isWindows() { return type == LibCType.MSVCRT; }
	public boolean isEmbedded() {
		return type == LibCType.UCLIBC || type == LibCType.DIETLIBC || type == LibCType.NEWLIB;
	}

}
