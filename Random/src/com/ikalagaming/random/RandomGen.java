package com.ikalagaming.random;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Random generation functions.
 *
 * @author Ches Burks
 *
 */
public class RandomGen {

	@AllArgsConstructor
	@Getter
	private static class Interval {
		private double start;
		private double end;
	}

	private static SplittableRandom random;

	static {
		try {
			RandomGen.random = new SplittableRandom(
				SecureRandom.getInstanceStrong().nextLong());
		}
		catch (NoSuchAlgorithmException e) {
			RandomGen.random = new SplittableRandom();
		}
	}

	/**
	 * Takes a cumulative distribution function and turns it into intervals
	 * representing the sections of the line from 0 to 1 that is split at the
	 * values at each index.
	 *
	 * @param cdf The cumulative distribution function values.
	 * @return The list of intervals that represent the CDF.
	 */
	private Interval[] calculateIntervals(double[] cdf) {
		if (cdf == null || cdf.length == 0) {
			return new Interval[0];
		}
		Interval[] intervals = new Interval[cdf.length];
		intervals[0] = new Interval(0, cdf[0]);
		for (int i = 1; i < cdf.length; ++i) {
			intervals[i] = new Interval(cdf[i - 1], cdf[i]);
		}
		return intervals;
	}

	/**
	 * Generate a cumulative distribution function from normalized weights. The
	 * length of the returned array will be the same as the input, but each
	 * element will be the probability a randomly chosen value will be less than
	 * or equal to that index.
	 *
	 * @param normalizedWeights A list of weights, in [0, 1) that add up to 1.
	 * @return The CDF for those weights.
	 */
	private double[] generateCDF(double[] normalizedWeights) {
		if (normalizedWeights == null || normalizedWeights.length == 0) {
			return new double[0];
		}
		double[] cdf = new double[normalizedWeights.length];
		double total = 0;
		for (int i = 0; i < normalizedWeights.length; ++i) {
			total += normalizedWeights[i];
			cdf[i] = total;
		}
		return cdf;
	}

	/**
	 * Normalize an array of doubles, so that they are all in the range [0, 1],
	 * and also they all add up to 1, or at least extremely close.
	 *
	 * @param values The values to normalize, this is modified.
	 */
	void normalize(double[] values) {
		if (values == null || values.length == 0) {
			return;
		}
		double total = 0;
		for (int i = 0; i < values.length; ++i) {
			double value = values[i];
			if (value <= 0 || Double.isNaN(value) || Double.isInfinite(value)) {
				values[i] = 0;
			}
			total += values[i];
		}

		if (total <= 0) {
			total = Double.MIN_NORMAL;
		}

		for (int i = 0; i < values.length; ++i) {
			values[i] = values[i] / total;
		}
	}

	/**
	 * Select weighted random values, given the list of relative weight for each
	 * index. For example, an item with weight 2 would be twice as likely to be
	 * selected as one with weight 1. <br>
	 * No real constraints on the values, as it will be normalized, although
	 * note that weird values like NaN or Infinity count as 0.
	 *
	 * @param weights The weights of each index.
	 * @param count The number of selections to make. If <= 0, an empty list
	 *            will be returned.
	 * @return The list of selections, in the order they were made.
	 */
	public int[] selectFromWeightedList(final double[] weights,
		final int count) {
		if (count <= 0) {
			return new int[0];
		}
		double[] normalizedWeights = new double[weights.length];

		System.arraycopy(weights, 0, normalizedWeights, 0, weights.length);
		this.normalize(normalizedWeights);

		double[] cdf = this.generateCDF(normalizedWeights);
		Interval[] intervals = this.calculateIntervals(cdf);

		int[] selections = new int[count];
		for (int i = 0; i < count; ++i) {
			selections[i] = this.selectFromWeightedList(intervals);
		}
		return selections;
	}

	/**
	 * Select a random item out of a list of intervals. Selects a number from 0
	 * to 1, finds the interval that contains that number using a binary search,
	 * and returns its index.
	 *
	 * @param intervals The intervals that the number line is split into.
	 * @return The selection index.
	 */
	private int selectFromWeightedList(Interval[] intervals) {
		double selection = RandomGen.random.nextDouble();

		return Arrays.binarySearch(intervals,
			new Interval(selection, selection), (b, a) -> {
				int startCompare = Double.compare(a.getStart(), b.getStart());
				int endCompare = Double.compare(a.getEnd(), b.getEnd());

				if (startCompare > 0 && endCompare >= 0) {
					return -1;
				}
				if (startCompare <= 0 && endCompare < 0) {
					return 1;
				}
				return 0;
			});
	}

}
