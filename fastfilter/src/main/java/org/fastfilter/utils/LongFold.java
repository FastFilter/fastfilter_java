package org.fastfilter.utils;

public final class LongFold
{
	/**
	 * Fold a signed number into an unsigned number.
	 *
	 * @param x a signed number
	 * @return an unsigned number
	 */
	public static long fold(long x) {
		return x > 0 ? x * 2 - 1 : -x * 2;
	}

	/**
	 * Unfold an unsigned number into a signed number.
	 *
	 * @param x an unsigned number
	 * @return a signed number
	 */
	public static long unfold(long x) {
		return ((x & 1) == 1) ? (x + 1) / 2 : -(x / 2);
	}
}
