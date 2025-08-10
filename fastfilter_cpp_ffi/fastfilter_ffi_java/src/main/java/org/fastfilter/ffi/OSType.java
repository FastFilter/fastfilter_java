package org.fastfilter.ffi;

// OS Detection
public enum OSType
{
	WINDOWS("windows", "win", "dll", "msvcrt"),
	LINUX("linux", "linux", "so", "c"),
	MACOS("macos", "darwin", "dylib", "c"),
	FREEBSD("freebsd", "freebsd", "so", "c"),
	OPENBSD("openbsd", "openbsd", "so", "c"),
	SOLARIS("solaris", "sunos", "so", "c"),
	AIX("aix", "aix", "so", "c"),
	UNKNOWN("unknown", "unknown", "", "");

	private final String name;
	private final String platformName;
	private final String libExtension;
	private final String cLibName;

	OSType(String name, String platformName, String libExtension, String cLibName)
	{
		this.name = name;
		this.platformName = platformName;
		this.libExtension = libExtension;
		this.cLibName = cLibName;
	}

	public String getName()
	{
		return name;
	}

	public String getPlatformName()
	{
		return platformName;
	}

	public String getLibExtension()
	{
		return libExtension;
	}

	public String getCLibName()
	{
		return cLibName;
	}

	public String getLibPrefix()
	{
		return this == WINDOWS ? "" : "lib";
	}
}
