package com.ikalagaming.random;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

	/**
	 * Used for selecting items from a list up to a certain weight.
	 *
	 * @author Ches Burks
	 * @see RandomGen#selectUpToWeight(int[], int)
	 */
	@AllArgsConstructor
	@Getter
	private static class WeightEntry {
		private int index;
		private int weight;
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
	 * and also they all add up to 1, or at least extremely close. Also makes
	 * the values positive.
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
			if (values[i] < 0) {
				values[i] = -values[i];
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
	 * @see #selectFromWeightedList(List, int)
	 */
	public int[] selectFromWeightedList(@NonNull final double[] weights,
		final int count) {
		if (count <= 0 || weights.length == 0) {
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
	 * @see #selectFromWeightedList(double[], int)
	 */
	public List<Integer> selectFromWeightedList(
		@NonNull final List<Double> weights, final int count) {
		if (count <= 0 || weights.isEmpty()) {
			return new ArrayList<>();
		}
		double[] normalizedWeights = new double[weights.size()];

		for (int i = 0; i < weights.size(); ++i) {
			normalizedWeights[i] = weights.get(i);
		}
		this.normalize(normalizedWeights);

		double[] cdf = this.generateCDF(normalizedWeights);
		Interval[] intervals = this.calculateIntervals(cdf);

		List<Integer> selections = new ArrayList<>(count);

		for (int i = 0; i < count; ++i) {
			selections.add(this.selectFromWeightedList(intervals));
		}
		return selections;
	}

	/**
	 * Given a list of weights, selects items at random up to the maximum
	 * provided weight. Does not guarantee it is exactly the max weight, but
	 * tries to get close and does not go over.
	 *
	 * @param weights The weights of each index. The absolute value will be used
	 *            for negative weights.
	 * @param maxWeight The max weight we can have total. Must be positive if
	 *            you want any selections.
	 * @return The list of choices, where each choice is the index in the
	 *         supplied weights list. May be empty depending on inputs.
	 * @see #selectUpToWeight(List, int)
	 */
	public int[] selectUpToWeight(@NonNull final int[] weights,
		final int maxWeight) {
		if (maxWeight <= 0 || weights.length == 0) {
			return new int[0];
		}

		int remainingWeight = maxWeight;

		List<WeightEntry> validChoices = new ArrayList<>();
		List<Integer> selections = new ArrayList<>();

		for (int i = 0; i < weights.length; ++i) {
			validChoices.add(new WeightEntry(i, weights[i]));
		}
		while (remainingWeight > 0) {
			/*
			 * Java complains that the weight is not effectively final, even
			 * though the lambda does not modify it. Annoying workaround.
			 */
			final int weightBecauseLambdas = remainingWeight;

			// get rid of any values that are too expensive to select
			validChoices
				.removeIf(entry -> entry.getWeight() > weightBecauseLambdas);
			// if we can't make any more valid choices, then bail
			int size = validChoices.size();
			if (size == 0) {
				break;
			}
			WeightEntry selection =
				validChoices.get(RandomGen.random.nextInt(size));
			remainingWeight -= Math.abs(selection.getWeight());
			selections.add(selection.getIndex());
		}

		int[] values = new int[selections.size()];

		for (int i = 0; i < selections.size(); ++i) {
			values[i] = selections.get(i);
		}
		return values;
	}

	/**
	 * Given a list of weights, selects items at random up to the maximum
	 * provided weight. Does not guarantee it is exactly the max weight, but
	 * tries to get close and does not go over.
	 *
	 * @param weights The weights of each index. The absolute value will be used
	 *            for negative weights.
	 * @param maxWeight The max weight we can have total. Must be positive if
	 *            you want any selections.
	 * @return The list of choices, where each choice is the index in the
	 *         supplied weights list. May be empty depending on inputs.
	 * @see #selectUpToWeight(int[], int)
	 */
	public List<Integer> selectUpToWeight(@NonNull final List<Integer> weights,
		final int maxWeight) {
		if (maxWeight <= 0 || weights.isEmpty()) {
			return new ArrayList<>();
		}

		int remainingWeight = maxWeight;

		List<WeightEntry> validChoices = new ArrayList<>();
		List<Integer> selections = new ArrayList<>();

		for (int i = 0; i < weights.size(); ++i) {
			validChoices.add(new WeightEntry(i, weights.get(i)));
		}
		while (remainingWeight > 0) {
			/*
			 * Java complains that the weight is not effectively final, even
			 * though the lambda does not modify it. Annoying workaround.
			 */
			final int weightBecauseLambdas = remainingWeight;

			// get rid of any values that are too expensive to select
			validChoices
				.removeIf(entry -> entry.getWeight() > weightBecauseLambdas);
			// if we can't make any more valid choices, then bail
			int size = validChoices.size();
			if (size == 0) {
				break;
			}
			WeightEntry selection =
				validChoices.get(RandomGen.random.nextInt(size));
			remainingWeight -= Math.abs(selection.getWeight());
			selections.add(selection.getIndex());
		}

		return selections;
	}

}
