package com.ikalagaming.random;

import lombok.NonNull;

import java.awt.image.BufferedImage;
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

	/**
	 * An interval along a number line.
	 *
	 * @author Ches Burks
	 * @param start The start of the interval.
	 * @param end The end of the interval.
	 *
	 */
	private record Interval(double start, double end) {}

	/**
	 * Used for selecting items from a list up to a certain weight.
	 *
	 * @author Ches Burks
	 * @param index The index that the weight is associated with.
	 * @param weight The weight of the index.
	 * @see RandomGen#selectUpToWeight(int[], int)
	 */
	private record WeightEntry(int index, int weight) {}

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
	 * If there is a tile set to 1 at the position, used for generating height
	 * maps.
	 *
	 * @param map The map.
	 * @param x The x position.
	 * @param y The x position.
	 * @return If x and y are a a tile with the value 1.
	 */
	private static boolean hasTile(int[][] map, int x, int y) {
		if (map == null || y < 0 || y >= map.length || map[y] == null) {
			return false;
		}
		int[] row = map[y];
		if (x < 0 || x >= row.length) {
			return false;
		}

		return row[x] == 1;
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
	 * WIP code that takes a tile map and combines it with noise to generate a
	 * terrain height map that blends in to the surrounding tiles.
	 *
	 * @return The generated height map image.
	 */
	public BufferedImage generateHeightMap() {
		final int MAP_WIDTH = 20;
		final int MAP_HEIGHT = 20;
		final int TILE_SIZE = 32;
		final long seed = random.nextLong();

		BufferedImage tiles = new BufferedImage(MAP_WIDTH * TILE_SIZE,
			MAP_HEIGHT * TILE_SIZE, BufferedImage.TYPE_INT_RGB);

		BufferedImage combined = new BufferedImage(MAP_WIDTH * TILE_SIZE,
			MAP_HEIGHT * TILE_SIZE, BufferedImage.TYPE_INT_RGB);

		BufferedImage noise = this.generateSimplexNoise(seed, 0, 0,
			MAP_WIDTH * TILE_SIZE, MAP_HEIGHT * TILE_SIZE, 10, 0.25);

		// create map
		int[][] map = new int[MAP_HEIGHT][];
		int x;
		int y;
		for (y = 0; y < MAP_HEIGHT; ++y) {
			map[y] = new int[MAP_WIDTH];
			for (x = 0; x < MAP_WIDTH; ++x) {
				if (RandomGen.random.nextInt(10) <= 5) {
					map[y][x] = 1;
				}
			}
		}

		int[][] pointsOfInterest = new int[MAP_HEIGHT * 2 + 2][];
		for (y = 0; y < pointsOfInterest.length; ++y) {
			pointsOfInterest[y] = new int[MAP_WIDTH * 2 + 2];
		}

		int xCoord;
		int yCoord;
		for (y = 0; y < (MAP_HEIGHT + 1) * TILE_SIZE; y += TILE_SIZE / 2) {
			for (x = 0; x < (MAP_WIDTH + 1) * TILE_SIZE; x += TILE_SIZE / 2) {
				xCoord = x == 0 ? -1 : (x - 1) / TILE_SIZE;
				yCoord = y == 0 ? -1 : (y - 1) / TILE_SIZE;
				if (x % TILE_SIZE == 0 && y % TILE_SIZE == 0) {
					if (RandomGen.hasTile(map, xCoord, yCoord)
						&& RandomGen.hasTile(map, xCoord, yCoord + 1)
						&& RandomGen.hasTile(map, xCoord + 1, yCoord)
						&& RandomGen.hasTile(map, xCoord + 1, yCoord + 1)) {
						pointsOfInterest[y / (TILE_SIZE / 2)][x
							/ (TILE_SIZE / 2)] = 255;
					}
					else {
						pointsOfInterest[y / (TILE_SIZE / 2)][x
							/ (TILE_SIZE / 2)] = 0;
					}
				}
				else if (x % TILE_SIZE == 0) {
					if (RandomGen.hasTile(map, xCoord, yCoord)
						&& RandomGen.hasTile(map, xCoord + 1, yCoord)) {
						pointsOfInterest[y / (TILE_SIZE / 2)][x
							/ (TILE_SIZE / 2)] = 255;
					}
					else {
						pointsOfInterest[y / (TILE_SIZE / 2)][x
							/ (TILE_SIZE / 2)] = 0;
					}
				}
				else if (y % TILE_SIZE == 0) {
					if (RandomGen.hasTile(map, xCoord, yCoord)
						&& RandomGen.hasTile(map, xCoord, yCoord + 1)) {
						pointsOfInterest[y / (TILE_SIZE / 2)][x
							/ (TILE_SIZE / 2)] = 255;
					}
					else {
						pointsOfInterest[y / (TILE_SIZE / 2)][x
							/ (TILE_SIZE / 2)] = 0;
					}
				}
				else {
					if (RandomGen.hasTile(map, xCoord, yCoord)) {
						pointsOfInterest[y / (TILE_SIZE / 2)][x
							/ (TILE_SIZE / 2)] = 255;
					}
					else {
						pointsOfInterest[y / (TILE_SIZE / 2)][x
							/ (TILE_SIZE / 2)] = 0;
					}
				}
			}
		}

		// point 1 we are interpolating between
		int p1x;
		int p1y;
		// point 2 we are interpolating between
		int p2x;
		int p2y;
		// point 3 we are interpolating between
		int p3x;
		int p3y;

		// actual pixel x and y we are at, within the tile
		int px;
		int py;
		// the values we are interpolating between
		int v1;
		int v2;
		int v3;
		int totalValue;
		// Relative weights of the three nodes
		double w1;
		double w2;
		double w3;
		int poiX;
		int poiY;
		for (y = 0; y < MAP_HEIGHT; ++y) {
			for (x = 0; x < MAP_WIDTH; ++x) {
				if (!RandomGen.hasTile(map, x, y)) {
					continue;
				}
				p3x = TILE_SIZE / 2;
				p3y = TILE_SIZE / 2;
				poiX = (x + 1) * 2 - 1;
				poiY = (y + 1) * 2 - 1;
				v3 = pointsOfInterest[poiY][poiX];
				for (py = 0; py < TILE_SIZE; ++py) {
					for (px = 0; px < TILE_SIZE; ++px) {
						if (px < TILE_SIZE / 2 && py < TILE_SIZE / 2) {
							// Top left quadrant
							p1x = 0;
							p1y = 0;
							v1 = pointsOfInterest[poiY - 1][poiX - 1];

							if (py < px) {
								// upper triangle
								p2x = TILE_SIZE / 2;
								p2y = 0;
								v2 = pointsOfInterest[poiY - 1][poiX];
							}
							else {
								// lower triangle
								p2x = 0;
								p2y = TILE_SIZE / 2;
								v2 = pointsOfInterest[poiY][poiX - 1];
							}
						}
						else if (px >= TILE_SIZE / 2 && py < TILE_SIZE / 2) {
							// Top right quadrant
							p1x = TILE_SIZE;
							p1y = 0;
							v1 = pointsOfInterest[poiY - 1][poiX + 1];
							if (TILE_SIZE - py < px) {
								// lower triangle
								p2x = TILE_SIZE;
								p2y = TILE_SIZE / 2;
								v2 = pointsOfInterest[poiY][poiX + 1];
							}
							else {
								// upper triangle
								p2x = TILE_SIZE / 2;
								p2y = 0;
								v2 = pointsOfInterest[poiY - 1][poiX];
							}
						}
						else if (px < TILE_SIZE / 2 && py >= TILE_SIZE / 2) {
							// Bottom left quadrant
							p1x = 0;
							p1y = TILE_SIZE;
							v1 = pointsOfInterest[poiY + 1][poiX - 1];
							if (TILE_SIZE - py < px) {
								// lower triangle
								p2x = TILE_SIZE / 2;
								p2y = TILE_SIZE;
								v2 = pointsOfInterest[poiY + 1][poiX];
							}
							else {
								// upper triangle
								p2x = 0;
								p2y = TILE_SIZE / 2;
								v2 = pointsOfInterest[poiY][poiX - 1];
							}
						}
						else if (px >= TILE_SIZE / 2 && py >= TILE_SIZE / 2) {
							// Bottom right quadrant
							p1x = TILE_SIZE;
							p1y = TILE_SIZE;
							v1 = pointsOfInterest[poiY + 1][poiX + 1];
							if (py < px) {
								// upper triangle
								p2x = TILE_SIZE;
								p2y = TILE_SIZE / 2;
								v2 = pointsOfInterest[poiY][poiX + 1];
							}
							else {
								// lower triangle
								p2x = TILE_SIZE / 2;
								p2y = TILE_SIZE;
								v2 = pointsOfInterest[poiY + 1][poiX];
							}
						}
						else {
							continue;
						}

						w1 = ((double) (p2y - p3y) * (px - p3x)
							+ (p3x - p2x) * (py - p3y))
							/ (((p2y - p3y) * (p1x - p3x))
								+ ((p3x - p2x) * (p1y - p3y)));
						w2 = ((double) ((p3y - p1y) * (px - p3x))
							+ ((p1x - p3x) * (py - p3y)))
							/ (((p2y - p3y) * (p1x - p3x))
								+ ((p3x - p2x) * (p1y - p3y)));
						w1 /= 2;
						w2 /= 2;
						w3 = 1 - w1 - w2;

						totalValue = (int) (w1 * v1 + w2 * v2 + w3 * v3);
						tiles.setRGB(x * TILE_SIZE + px, y * TILE_SIZE + py,
							totalValue << 16 | totalValue << 8 | totalValue);
					}
				}
			}
		}

		int pixel;
		float amplitude;
		for (y = 0; y < MAP_HEIGHT * TILE_SIZE; ++y) {
			for (x = 0; x < MAP_WIDTH * TILE_SIZE; ++x) {
				amplitude = (tiles.getRGB(x, y) & 0xFF) / 255f;
				pixel = (int) ((noise.getRGB(x, y) & 0xFF) * amplitude);
				combined.setRGB(x, y, (pixel << 16) + (pixel << 8) + pixel);
			}
		}

		return combined;
	}

	/**
	 * Create an image containing simplex noise. Please keep the sizes
	 * reasonable.
	 * 
	 * @param seed The seed to use for the noise generation.
	 * @param startX The starting X coordinate. Values increase from here.
	 * @param startY The starting Y coordinate. Values increase from here.
	 * @param width The width of the image.
	 * @param height The height of the image.
	 * @return The noise, or an empty image if you give weird height and width
	 *         values.
	 */
	public BufferedImage generateSimplexNoise(long seed, int startX, int startY,
		int width, int height) {
		final int frequency = 5;
		final double noiseAmplitude = 1;
		return this.generateSimplexNoise(seed, startX, startY, width, height,
			frequency, noiseAmplitude);
	}

	/**
	 * Generate simplex noise with specific parameters.
	 * 
	 * @param seed The seed to use for the noise generation.
	 * @param startX The starting X coordinate. Values increase from here.
	 * @param startY The starting Y coordinate. Values increase from here.
	 * @param width The width of the image.
	 * @param height The height of the image.
	 * @param frequency The base frequency for the noise. A reasonable example
	 *            is 5, should be > 1.
	 * @param noiseAmplitude The maximum amplitude for the noise. Should be
	 *            between 0 and 1 inclusive.
	 * @return The noise, or an empty image if you give weird values.
	 */
	public BufferedImage generateSimplexNoise(long seed, int startX, int startY,
		int width, int height, int frequency, double noiseAmplitude) {
		BufferedImage result =
			new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		if (width <= 0 || height <= 0 || frequency <= 0 || noiseAmplitude <= 0
			|| noiseAmplitude > 1) {
			return result;
		}

		OpenSimplexNoise noise = new OpenSimplexNoise(seed);

		int[] rawData = new int[width * height];

		int x;
		int y;
		double nx;
		double ny;
		double pixelValue;
		int pixelTemp;
		for (y = startY; y < startY + height; ++y) {
			for (x = startX; x < startX + width; ++x) {
				nx = ((double) x - startX) / width - 0.5;
				ny = ((double) y - startY) / height - 0.5;
				// output is from -1 to 1
				pixelValue = noiseAmplitude * noise
					.eval(frequency * nx + startX, frequency * ny + startY);
				pixelValue += noiseAmplitude * 0.5 * noise.eval(
					frequency * 2 * nx + startX, frequency * 2 * ny + startY);
				pixelValue += noiseAmplitude * 0.25 * noise.eval(
					frequency * 4 * nx + startX, frequency * 4 * ny + startY);
				pixelValue /= noiseAmplitude + noiseAmplitude * 0.5
					+ noiseAmplitude * 0.25;
				// we map it from 0 to 1
				pixelValue = (pixelValue + 1d) / 2d;
				// Map to [0,255)
				pixelTemp = (int) (pixelValue * 255);
				// fill out R, G, and B to pixelTemps least significant byte
				rawData[width * y + x] =
					pixelTemp << 16 | pixelTemp << 8 | pixelTemp;
			}
		}
		result.setRGB(0, 0, width, height, rawData, 0, width);
		return result;
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
	 * Select a value at random from the specified enum class.
	 *
	 * @param <T> The enum we are selecting from.
	 * @param enumClass The enum class we want to select values from.
	 * @return The randomly selected value out of the specified enum.
	 */
	public <T extends Enum<T>> T selectEnumValue(Class<T> enumClass) {
		final T[] enumValues = enumClass.getEnumConstants();
		return enumValues[RandomGen.random.nextInt(enumValues.length)];
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
				int startCompare = Double.compare(a.start(), b.start());
				int endCompare = Double.compare(a.end(), b.end());

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
				.removeIf(entry -> entry.weight() > weightBecauseLambdas);
			// if we can't make any more valid choices, then bail
			int size = validChoices.size();
			if (size == 0) {
				break;
			}
			WeightEntry selection =
				validChoices.get(RandomGen.random.nextInt(size));
			remainingWeight -= Math.abs(selection.weight());
			selections.add(selection.index());
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
				.removeIf(entry -> entry.weight() > weightBecauseLambdas);
			// if we can't make any more valid choices, then bail
			int size = validChoices.size();
			if (size == 0) {
				break;
			}
			WeightEntry selection =
				validChoices.get(RandomGen.random.nextInt(size));
			remainingWeight -= Math.abs(selection.weight());
			selections.add(selection.index());
		}

		return selections;
	}

}
