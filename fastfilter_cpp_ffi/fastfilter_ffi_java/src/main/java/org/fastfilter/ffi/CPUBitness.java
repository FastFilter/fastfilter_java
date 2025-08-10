package org.fastfilter.ffi;

import java.nio.ByteOrder;
import java.util.Arrays;

public enum CPUBitness
{
	BIT_32(32),
	BIT_64(64),
	BIT_128(128),
	UNKNOWN(0);

	private final int bits;

	CPUBitness(int value)
	{
		this.bits = value;
	}

	public ByteOrder byteOrder()
	{
		return ByteOrder.nativeOrder();
	}

	public int bits()
	{
		return bits;
	}

	public static CPUBitness fromBits(int value)
	{
		return Arrays.stream(values())
		             .filter(b -> b.bits == value)
		             .findFirst()
		             .orElse(UNKNOWN);
	}
}
