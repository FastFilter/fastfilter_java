package org.fastfilter.ffi;

import java.util.Set;

// Architecture Detection
public enum CPUArch
{
	X86("x86", CPUBitness.BIT_32, "i386", "i486", "i586", "i686"),
	X86_64("x86_64", CPUBitness.BIT_64, "amd64", "x86_64"),
	ARM32("arm", CPUBitness.BIT_32, "arm", "armv7", "armv7l"),
	ARM64("aarch64", CPUBitness.BIT_64, "aarch64", "arm64"),
	PPC64("ppc64", CPUBitness.BIT_64, "ppc64", "ppc64le"),
	RISCV64("riscv64", CPUBitness.BIT_64, "riscv64"),
	S390X("s390x", CPUBitness.BIT_64, "s390x"),
	MIPS64("mips64", CPUBitness.BIT_64, "mips64", "mips64el"),
	WASM32("wasm32", CPUBitness.BIT_32, "wasm32"),
	UNKNOWN("unknown", CPUBitness.UNKNOWN);

	private final String name;
	private final CPUBitness bitness;
	private final Set<String> aliases;

	CPUArch(String name, int bitness, String... aliases)
	{
		this.name = name;
		this.bitness = CPUBitness.fromBits(bitness);
		this.aliases = Set.of(aliases);
	}

	CPUArch(String name, CPUBitness bitness, String... aliases)
	{
		this.name = name;
		this.bitness = bitness;
		this.aliases = Set.of(aliases);
	}

	public String getName()
	{
		return name;
	}

	public CPUBitness bitness()
	{
		return bitness;
	}

	public int bits()
	{
		return bitness.bits();
	}

	public boolean matches(String arch)
	{
		if( arch == null || arch.isEmpty() ) {
			return false;
		}
		for( String alias : aliases ) {
			if( alias.equalsIgnoreCase(arch) ) {
				return true;
			}
		}
		return name.equalsIgnoreCase(arch);
	}
}
