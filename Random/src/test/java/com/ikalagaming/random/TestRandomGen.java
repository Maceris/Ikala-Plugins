package com.ikalagaming.random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

/**
 * Tests for the random number generation.
 *
 * @author Ches Burks
 *
 */
public class TestRandomGen {
	/**
	 * Used to test the selection of random enum values.
	 *
	 * @author Ches Burks
	 * @see TestRandomGen#testSelectEnumValue()
	 */
	private enum TestEnum {
		VALUE_1,
		VALUE_2,
		VALUE_3,
		VALUE_4,
		VALUE_5,
		VALUE_6,
		VALUE_7,
		VALUE_8,
		VALUE_9,
		VALUE_10;
	}

	/**
	 * Helper function for testing if a list is normalized.
	 *
	 * @param items The list of values.
	 * @return true if all the values add to very nearly 1, false otherwise.
	 */
	private boolean isNormalized(double[] items) {
		double total = 0;
		for (int i = 0; i < items.length; ++i) {
			total += items[i];
		}
		return Math.abs(total - 1d) < 0.0001d;
	}

	/**
	 * Test the normalization of double values.
	 */
	@Test
	public void testNormalization() {
		RandomGen gen = new RandomGen();

		double[] one = {2};
		double[] two = {4, 6};
		double[] three = {123, 3, 0};
		double[] many = new double[100];
		SplittableRandom rand = new SplittableRandom();
		for (int i = 0; i < many.length; ++i) {
			many[i] = rand.nextDouble();
		}
		double[] weird =
			{Double.NaN, Double.MIN_NORMAL, Double.NEGATIVE_INFINITY, 7d, 45};

		gen.normalize(one);
		gen.normalize(two);
		gen.normalize(three);
		gen.normalize(many);
		gen.normalize(weird);

		Assertions.assertTrue(this.isNormalized(one));
		Assertions.assertTrue(this.isNormalized(two));
		Assertions.assertTrue(this.isNormalized(three));
		Assertions.assertTrue(this.isNormalized(many));
		Assertions.assertTrue(this.isNormalized(weird));
	}

	/**
	 * Test the selection of a random enum value.
	 */
	@Test
	public void testSelectEnumValue() {
		RandomGen gen = new RandomGen();

		Map<TestEnum, Integer> selections = new HashMap<>();
		for (TestEnum value : TestEnum.values()) {
			selections.put(value, 0);
		}
		final int queries = 10000;
		for (int i = 0; i < queries; ++i) {
			TestEnum selection = gen.selectEnumValue(TestEnum.class);
			selections.put(selection, selections.get(selection) + 1);
		}

		for (TestEnum value : TestEnum.values()) {
			Assertions.assertTrue(selections
				.get(value) > (queries / TestEnum.values().length) / 2);
		}

	}

	/**
	 * Tests the {@link RandomGen#selectUpToWeight(int[], int)} method.
	 */
	@Test
	public void testSelectUpToWeight() {
		int[] weights = new int[100];
		SplittableRandom rand = new SplittableRandom();
		for (int i = 0; i < weights.length; ++i) {
			weights[i] = rand.nextInt();
		}

		final int requestedTotal = 5000;
		RandomGen generator = new RandomGen();
		int[] selections = generator.selectUpToWeight(weights, requestedTotal);

		int total = 0;
		for (int i = 0; i < selections.length; ++i) {
			total += selections[i];
		}
		Assertions.assertTrue(total < requestedTotal);
	}

	/**
	 * Tests the {@link RandomGen#selectUpToWeight(int[], int)} methods error
	 * scenarios.
	 */
	@Test
	public void testSelectUpToWeightErrors() {
		RandomGen generator = new RandomGen();

		int[] badArray = null;
		Assertions.assertThrows(NullPointerException.class,
			() -> generator.selectUpToWeight(badArray, 10));

		int[] selections = generator.selectUpToWeight(new int[0], 10);
		Assertions.assertNotNull(selections);
		Assertions.assertEquals(0, selections.length);

		int[] weights = {1};
		selections = generator.selectUpToWeight(weights, 0);
		Assertions.assertNotNull(selections);
		Assertions.assertEquals(0, selections.length);

		selections = generator.selectUpToWeight(weights, -1);
		Assertions.assertNotNull(selections);
		Assertions.assertEquals(0, selections.length);
	}

	/**
	 * Tests the {@link RandomGen#selectUpToWeight(List, int)} method.
	 */
	@Test
	public void testSelectUpToWeightList() {
		final int weightSize = 100;
		List<Integer> weights = new ArrayList<>(weightSize);

		SplittableRandom rand = new SplittableRandom();
		for (int i = 0; i < weightSize; ++i) {
			weights.add(rand.nextInt());
		}

		final int requestedTotal = 5000;
		RandomGen generator = new RandomGen();
		List<Integer> selections =
			generator.selectUpToWeight(weights, requestedTotal);

		int total =
			selections.stream().collect(Collectors.summingInt(item -> item));

		Assertions.assertTrue(total < requestedTotal);
	}

	/**
	 * Tests the {@link RandomGen#selectUpToWeight(List, int)} methods error
	 * scenarios.
	 */
	@Test
	public void testSelectUpToWeightListErrors() {
		RandomGen generator = new RandomGen();

		List<Integer> badList = null;
		Assertions.assertThrows(NullPointerException.class,
			() -> generator.selectUpToWeight(badList, 10));

		List<Integer> selections =
			generator.selectUpToWeight(new ArrayList<>(), 10);
		Assertions.assertNotNull(selections);
		Assertions.assertTrue(selections.isEmpty());

		List<Integer> weights = new ArrayList<>();
		weights.add(1);

		selections = generator.selectUpToWeight(weights, 0);
		Assertions.assertNotNull(selections);
		Assertions.assertTrue(selections.isEmpty());

		selections = generator.selectUpToWeight(weights, -1);
		Assertions.assertNotNull(selections);
		Assertions.assertTrue(selections.isEmpty());
	}

	/**
	 * Test the distribution of
	 * {@link RandomGen#selectFromWeightedList(double[], int)} matches what it
	 * should be.
	 */
	@Test
	public void testWeightedListSelectionDistribution() {
		double[] weights = new double[100];
		SplittableRandom rand = new SplittableRandom();
		for (int i = 0; i < weights.length; ++i) {
			weights[i] = rand.nextDouble() * 100;
		}

		RandomGen generator = new RandomGen();
		int[] selections = generator.selectFromWeightedList(weights, 1000000);

		double[] counts = new double[weights.length];
		for (int i = 0; i < selections.length; ++i) {
			int value = selections[i];
			Assertions.assertTrue(value < weights.length);
			Assertions.assertTrue(value >= 0);
			counts[value] = counts[value] + 1;
		}
		generator.normalize(weights);
		generator.normalize(counts);
		for (int i = 0; i < weights.length; ++i) {
			double error = Math.abs(weights[i] - counts[i]);
			Assertions.assertTrue(error < 0.001);
		}
	}

	/**
	 * Test the method {@link RandomGen#selectFromWeightedList(double[], int)}
	 * handles invalid inputs correctly.
	 */
	@Test
	public void testWeightedListSelectionErrors() {
		RandomGen generator = new RandomGen();

		double[] badArray = null;
		Assertions.assertThrows(NullPointerException.class,
			() -> generator.selectFromWeightedList(badArray, 10));

		int[] selections = generator.selectFromWeightedList(new double[0], 10);
		Assertions.assertNotNull(selections);
		Assertions.assertEquals(0, selections.length);

		double[] weights = {1};
		selections = generator.selectFromWeightedList(weights, 0);
		Assertions.assertNotNull(selections);
		Assertions.assertEquals(0, selections.length);

		selections = generator.selectFromWeightedList(weights, -1);
		Assertions.assertNotNull(selections);
		Assertions.assertEquals(0, selections.length);
	}

	/**
	 * Test the distribution of
	 * {@link RandomGen#selectFromWeightedList(List, int)} matches what it
	 * should be.
	 */
	@Test
	public void testWeightedListSelectionListDistribution() {
		final int weightSize = 100;
		List<Double> weights = new ArrayList<>(weightSize);
		SplittableRandom rand = new SplittableRandom();
		for (int i = 0; i < weightSize; ++i) {
			weights.add(rand.nextDouble() * 100);
		}

		RandomGen generator = new RandomGen();
		List<Integer> selections =
			generator.selectFromWeightedList(weights, 1000000);

		double[] counts = new double[weights.size()];
		for (int i = 0; i < selections.size(); ++i) {
			int value = selections.get(i);
			Assertions.assertTrue(value < weights.size());
			Assertions.assertTrue(value >= 0);
			counts[value] = counts[value] + 1;
		}

		double[] weightArray = new double[weights.size()];
		for (int i = 0; i < weights.size(); ++i) {
			weightArray[i] = weights.get(i);
		}

		generator.normalize(weightArray);
		generator.normalize(counts);
		for (int i = 0; i < weightArray.length; ++i) {
			double error = Math.abs(weightArray[i] - counts[i]);
			Assertions.assertTrue(error < 0.001);
		}
	}

	/**
	 * Test the method {@link RandomGen#selectFromWeightedList(List, int)}
	 * handles invalid inputs correctly.
	 */
	@Test
	public void testWeightedListSelectionListErrors() {
		RandomGen generator = new RandomGen();

		List<Double> badList = null;
		Assertions.assertThrows(NullPointerException.class,
			() -> generator.selectFromWeightedList(badList, 10));

		List<Integer> selections =
			generator.selectFromWeightedList(new ArrayList<>(), 10);
		Assertions.assertNotNull(selections);
		Assertions.assertTrue(selections.isEmpty());

		List<Double> weights = new ArrayList<>();
		weights.add(1D);

		selections = generator.selectFromWeightedList(weights, 0);
		Assertions.assertNotNull(selections);
		Assertions.assertTrue(selections.isEmpty());

		selections = generator.selectFromWeightedList(weights, -1);
		Assertions.assertNotNull(selections);
		Assertions.assertTrue(selections.isEmpty());
	}

}
