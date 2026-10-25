package com.ikalagaming.random;

import lombok.NonNull;

import java.nio.charset.StandardCharsets;

/**
 * A random generator whose output is fixed by its seed, on every machine and every Java version,
 * for results that have to be rebuilt elsewhere, like a design rolled on the server and again on
 * each client from the same seed.
 *
 * <p>Every method is implemented here rather than taken from the JDK, whose bounded and floating
 * point methods may change between versions. The generator is SplitMix64. Changing what any method
 * returns for a seed breaks saved and networked rolls, so the tests pin the outputs.
 *
 * <p>Use {@link #derive(String)} to give each independent choice its own stream, so adding a choice
 * doesn't change the results of the others.
 *
 * <p>Not thread safe.
 *
 * @author Ches Burks
 */
public final class SeededRandom {
    /** The SplitMix64 increment, the golden ratio as a 64 bit fraction. */
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    /** The FNV-1a 64 bit offset basis. */
    private static final long FNV_OFFSET = 0xCBF29CE484222325L;

    /** The FNV-1a 64 bit prime. */
    private static final long FNV_PRIME = 0x100000001B3L;

    /** The seed this generator started from, which {@link #derive(String)} works from. */
    private final long seed;

    /** The generator's state, which advances by {@link #GOLDEN_GAMMA} each step. */
    private long state;

    /**
     * Create a generator.
     *
     * @param seed The seed, which decides every value it returns.
     */
    public SeededRandom(long seed) {
        this.seed = seed;
        state = seed;
    }

    /**
     * The SplitMix64 output function, which scrambles a state into a value.
     *
     * @param value The value to scramble.
     * @return The scrambled value.
     */
    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Hash a key into 64 bits with FNV-1a over its UTF-8 bytes, which unlike {@link
     * String#hashCode()} uses the full width.
     *
     * @param key The key.
     * @return The hash.
     */
    private static long hash(@NonNull String key) {
        long hash = FNV_OFFSET;
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ (b & 0xFF)) * FNV_PRIME;
        }
        return hash;
    }

    /**
     * Create an independent generator for a named choice. It depends only on this generator's seed
     * and the key, not on how many values were taken from this one, so choices keyed by name keep
     * their results when other choices are added or removed.
     *
     * @param key The name of the choice.
     * @return A new generator for that choice.
     */
    public SeededRandom derive(@NonNull String key) {
        return new SeededRandom(mix(seed ^ mix(hash(key))));
    }

    /**
     * The seed this generator started from.
     *
     * @return The seed.
     */
    public long getSeed() {
        return seed;
    }

    /**
     * Pick true with the given chance.
     *
     * @param chance The chance of true, from 0 to 1. At or below 0 is never, at or above 1 always.
     * @return Whether it was picked.
     */
    public boolean nextBoolean(double chance) {
        return nextDouble() < chance;
    }

    /**
     * A value uniformly in [0, 1), with 53 bits of precision.
     *
     * @return The value.
     */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /**
     * A value uniformly in [min, max).
     *
     * @param min The smallest value.
     * @param max The value it stays below. If it is not above min, min is returned.
     * @return The value.
     */
    public double nextDouble(double min, double max) {
        if (max <= min) {
            return min;
        }
        double value = min + nextDouble() * (max - min);
        // Rounding can land on max itself
        return value < max ? value : Math.nextDown(max);
    }

    /**
     * A value uniformly in [0, 1), with 24 bits of precision.
     *
     * @return The value.
     */
    public float nextFloat() {
        return (nextLong() >>> 40) * 0x1.0p-24f;
    }

    /**
     * A value uniformly in [0, bound).
     *
     * @param bound The value it stays below, which must be positive.
     * @return The value.
     * @throws IllegalArgumentException If the bound isn't positive.
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("The bound must be positive, not " + bound);
        }
        int mask = bound - 1;
        int value = (int) (nextLong() >>> 33);
        if ((bound & mask) == 0) {
            // A power of two takes the high bits
            return (int) ((bound * (long) value) >> 31);
        }
        // Reject the values from the last, partial copy of the range, so every result is as likely
        for (int raw = value; raw - (value = raw % bound) + mask < 0; ) {
            raw = (int) (nextLong() >>> 33);
        }
        return value;
    }

    /**
     * A value uniformly in [min, max).
     *
     * @param min The smallest value.
     * @param max The value it stays below, which must be above min.
     * @return The value.
     * @throws IllegalArgumentException If max isn't above min.
     */
    public int nextInt(int min, int max) {
        if (max <= min) {
            throw new IllegalArgumentException(
                    "The maximum " + max + " must be above the minimum " + min);
        }
        long range = (long) max - min;
        if (range <= Integer.MAX_VALUE) {
            return min + nextInt((int) range);
        }
        // More than half of all ints are in range, so retrying is quick
        int value;
        do {
            value = (int) nextLong();
        } while (value < min || value >= max);
        return value;
    }

    /**
     * The next 64 random bits.
     *
     * @return The value.
     */
    public long nextLong() {
        state += GOLDEN_GAMMA;
        return mix(state);
    }

    /**
     * Pick an index with a chance proportional to its weight, so a weight of 2 is twice as likely
     * as a weight of 1. Weights that are negative, NaN or infinite count as 0.
     *
     * @param weights The weight of each index.
     * @return The index picked, or -1 if no weight is above 0.
     */
    public int selectWeighted(@NonNull double[] weights) {
        double total = 0;
        for (double weight : weights) {
            total += usable(weight);
        }
        if (total <= 0) {
            return -1;
        }
        double target = nextDouble() * total;
        int last = -1;
        for (int i = 0; i < weights.length; ++i) {
            double weight = usable(weights[i]);
            if (weight <= 0) {
                continue;
            }
            last = i;
            target -= weight;
            if (target < 0) {
                return i;
            }
        }
        // Rounding can leave a sliver past the last weight
        return last;
    }

    /**
     * A weight as {@link #selectWeighted(double[])} counts it.
     *
     * @param weight The weight.
     * @return The weight, or 0 if it is negative, NaN or infinite.
     */
    private static double usable(double weight) {
        return weight > 0 && Double.isFinite(weight) ? weight : 0;
    }
}
