package com.ikalagaming.random;

import lombok.Getter;
import lombok.NonNull;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random generation functions.
 *
 * @author Ches Burks
 */
public class RandomGen {

    /**
     * Used for selecting items from a list up to a certain weight.
     *
     * @author Ches Burks
     * @param index The index that the weight is associated with.
     * @param weight The weight of the index.
     * @see RandomGen#selectUpToWeight(int[], int)
     */
    private record WeightEntry(int index, long weight) {}

    /** A secure source of strong random data, for use in things like cryptography. */
    @Getter private static final SecureRandom secureRandom;

    /**
     * A fast source of random data, for use in non-security related things. This is not safe to
     * share between threads, so the selection methods here use {@link ThreadLocalRandom} instead.
     */
    @Getter private static final SplittableRandom fastRandom;

    static {
        try {
            secureRandom = SecureRandom.getInstanceStrong();
            fastRandom = new SplittableRandom(secureRandom.nextLong());
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    /**
     * Generate a cumulative distribution function from normalized weights. The length of the
     * returned array will be the same as the input, but each element will be the probability a
     * randomly chosen value will be less than or equal to that index.
     *
     * @param normalizedWeights A list of weights, in [0, 1) that add up to 1.
     * @return The CDF for those weights.
     */
    private static double[] generateCDF(double[] normalizedWeights) {
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
     * Returns a pseudo-randomly chosen long value for use as a seed.
     *
     * @return A pseudo-randomly chosen long value.
     */
    public static long generateSeed() {
        return RandomGen.secureRandom.nextLong();
    }

    /**
     * Normalize an array of doubles, so that they are all in the range [0, 1], and also they all
     * add up to 1, or at least extremely close. Also makes the values positive.
     *
     * @param values The values to normalize, this is modified.
     */
    static void normalize(double[] values) {
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
    public static <T extends Enum<T>> T selectEnumValue(Class<T> enumClass) {
        final T[] enumValues = enumClass.getEnumConstants();
        return enumValues[ThreadLocalRandom.current().nextInt(enumValues.length)];
    }

    /**
     * Select weighted random values, given the list of relative weight for each index. For example,
     * an item with weight 2 would be twice as likely to be selected as one with weight 1. <br>
     * No real constraints on the values, as it will be normalized, although note that weird values
     * like NaN or Infinity count as 0.
     *
     * @param weights The weights of each index.
     * @param count The number of selections to make. If &lt;= 0, an empty list will be returned.
     * @return The list of selections, in the order they were made.
     * @see #selectFromWeightedList(List, int)
     */
    public static int[] selectFromWeightedList(@NonNull final double[] weights, final int count) {
        if (count <= 0 || weights.length == 0) {
            return new int[0];
        }
        double[] normalizedWeights = new double[weights.length];

        System.arraycopy(weights, 0, normalizedWeights, 0, weights.length);
        RandomGen.normalize(normalizedWeights);

        double[] cdf = RandomGen.generateCDF(normalizedWeights);

        int[] selections = new int[count];
        for (int i = 0; i < count; ++i) {
            selections[i] = RandomGen.selectFromCDF(cdf);
        }
        return selections;
    }

    /**
     * Select a random index using a cumulative distribution function. Picks a number in [0, 1) and
     * returns the first index whose cumulative value is above it, so indices with no weight are
     * never chosen. If every weight is zero, all indices are equally likely.
     *
     * @param cdf The cumulative distribution function, which is not empty.
     * @return The selected index.
     */
    private static int selectFromCDF(double[] cdf) {
        final int last = cdf.length - 1;
        if (cdf[last] <= 0) {
            return ThreadLocalRandom.current().nextInt(cdf.length);
        }
        // Scale by the total so rounding in the normalized weights can't leave a gap at the top
        final double selection = ThreadLocalRandom.current().nextDouble() * cdf[last];
        int low = 0;
        int high = last;
        while (low < high) {
            final int middle = (low + high) >>> 1;
            if (cdf[middle] > selection) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    /**
     * Select weighted random values, given the list of relative weight for each index. For example,
     * an item with weight 2 would be twice as likely to be selected as one with weight 1. <br>
     * No real constraints on the values, as it will be normalized, although note that weird values
     * like NaN or Infinity count as 0.
     *
     * @param weights The weights of each index.
     * @param count The number of selections to make. If &lt;= 0, an empty list will be returned.
     * @return The list of selections, in the order they were made.
     * @see #selectFromWeightedList(double[], int)
     */
    public static List<Integer> selectFromWeightedList(
            @NonNull final List<Double> weights, final int count) {
        if (count <= 0 || weights.isEmpty()) {
            return new ArrayList<>();
        }
        double[] normalizedWeights = new double[weights.size()];

        for (int i = 0; i < weights.size(); ++i) {
            normalizedWeights[i] = weights.get(i);
        }
        RandomGen.normalize(normalizedWeights);

        double[] cdf = RandomGen.generateCDF(normalizedWeights);

        List<Integer> selections = new ArrayList<>(count);

        for (int i = 0; i < count; ++i) {
            selections.add(RandomGen.selectFromCDF(cdf));
        }
        return selections;
    }

    /**
     * Given a list of weights, selects items at random up to the maximum provided weight. Does not
     * guarantee it is exactly the max weight, but tries to get close and does not go over.
     *
     * @param weights The weights of each index. The absolute value will be used for negative
     *     weights.
     * @param maxWeight The max weight we can have total. Must be positive if you want any
     *     selections.
     * @return The list of choices, where each choice is the index in the supplied weights list. May
     *     be empty depending on inputs.
     * @see #selectUpToWeight(List, int)
     */
    public static int[] selectUpToWeight(@NonNull final int[] weights, final int maxWeight) {
        if (maxWeight <= 0 || weights.length == 0) {
            return new int[0];
        }
        List<WeightEntry> choices = new ArrayList<>(weights.length);
        for (int i = 0; i < weights.length; ++i) {
            choices.add(new WeightEntry(i, Math.abs((long) weights[i])));
        }
        return selectEntriesUpToWeight(choices, maxWeight).stream()
                .mapToInt(Integer::intValue)
                .toArray();
    }

    /**
     * Given a list of weights, selects items at random up to the maximum provided weight. Does not
     * guarantee it is exactly the max weight, but tries to get close and does not go over.
     *
     * @param weights The weights of each index. The absolute value will be used for negative
     *     weights.
     * @param maxWeight The max weight we can have total. Must be positive if you want any
     *     selections.
     * @return The list of choices, where each choice is the index in the supplied weights list. May
     *     be empty depending on inputs.
     * @see #selectUpToWeight(int[], int)
     */
    public static List<Integer> selectUpToWeight(
            @NonNull final List<Integer> weights, final int maxWeight) {
        if (maxWeight <= 0 || weights.isEmpty()) {
            return new ArrayList<>();
        }

        List<WeightEntry> choices = new ArrayList<>(weights.size());
        for (int i = 0; i < weights.size(); ++i) {
            choices.add(new WeightEntry(i, Math.abs((long) weights.get(i))));
        }
        return selectEntriesUpToWeight(choices, maxWeight);
    }

    /**
     * Select entries at random until no more fit in the remaining weight.
     *
     * @param choices The entries, with weights that are not negative. Modified.
     * @param maxWeight The total weight allowed, which is positive.
     * @return The indices of the selected entries, in the order they were selected.
     */
    private static List<Integer> selectEntriesUpToWeight(
            List<WeightEntry> choices, final int maxWeight) {
        // Something with no weight could be selected forever without using any up
        choices.removeIf(entry -> entry.weight() == 0);
        long remainingWeight = maxWeight;
        List<Integer> selections = new ArrayList<>();
        while (remainingWeight > 0) {
            final long remaining = remainingWeight;
            // get rid of any values that are too expensive to select
            choices.removeIf(entry -> entry.weight() > remaining);
            // if we can't make any more valid choices, then bail
            int size = choices.size();
            if (size == 0) {
                break;
            }
            WeightEntry selection = choices.get(ThreadLocalRandom.current().nextInt(size));
            remainingWeight -= selection.weight();
            selections.add(selection.index());
        }
        return selections;
    }
}
