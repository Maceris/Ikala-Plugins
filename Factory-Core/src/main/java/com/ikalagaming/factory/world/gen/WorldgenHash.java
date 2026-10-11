package com.ikalagaming.factory.world.gen;

import lombok.NonNull;

import java.nio.charset.StandardCharsets;

/**
 * The hashing world generation derives every seed from. Generation never keeps a running random
 * number generator: each value is a hash of the world seed and where and what it is for, so the
 * result can't depend on the order chunks are generated in or which thread runs them.
 *
 * <p>Strings are hashed with 64-bit FNV-1a over their UTF-8 bytes, never {@link String#hashCode()},
 * and values are mixed with the SplitMix64 finalizer.
 */
public final class WorldgenHash {

    /** The FNV-1a 64-bit offset basis. */
    public static final long FNV_OFFSET = 0xcbf2_9ce4_8422_2325L;

    /** The FNV-1a 64-bit prime. */
    public static final long FNV_PRIME = 0x0000_0100_0000_01b3L;

    /** The golden ratio increment SplitMix64 steps by. */
    private static final long GOLDEN_GAMMA = 0x9e37_79b9_7f4a_7c15L;

    /** How many bits a byte has, for walking through a long. */
    private static final int BYTE_BITS = 8;

    /** The mask of one byte. */
    private static final long BYTE_MASK = 0xff;

    /**
     * Hash a string, like a data ID or a salt.
     *
     * @param text The string.
     * @return Its 64-bit FNV-1a hash.
     */
    public static long fnv1a64(@NonNull String text) {
        long hash = FNV_OFFSET;
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & BYTE_MASK;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    /**
     * Add a long's bytes, lowest first, to a running FNV-1a hash.
     *
     * @param hash The hash so far, starting from {@link #FNV_OFFSET}.
     * @param value The value to add.
     * @return The new hash.
     */
    public static long fnvAdd(long hash, long value) {
        long result = hash;
        for (int shift = 0; shift < Long.SIZE; shift += BYTE_BITS) {
            result ^= (value >>> shift) & BYTE_MASK;
            result *= FNV_PRIME;
        }
        return result;
    }

    /**
     * Scramble a value so nearby inputs give unrelated outputs: the SplitMix64 finalizer.
     *
     * @param value The value.
     * @return The mixed value.
     */
    public static long mix64(long value) {
        // The shifts and multipliers are SplitMix64's published constants (Steele, Lea and Flood)
        long z = value + GOLDEN_GAMMA;
        z = (z ^ (z >>> 30)) * 0xbf58_476d_1ce4_e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d0_49bb_1331_11ebL;
        return z ^ (z >>> 31);
    }

    /**
     * Combine two values into one hash, in an order-dependent way.
     *
     * @param first The first value.
     * @param second The second value.
     * @return The combined hash.
     */
    public static long combine(long first, long second) {
        return mix64(first ^ mix64(second));
    }

    /**
     * The seed for something in the world, like a noise field or an octave of one.
     *
     * @param worldSeed The world's seed.
     * @param salt What the seed is for, usually a data ID.
     * @return The derived seed.
     */
    public static long seed(long worldSeed, @NonNull String salt) {
        return mix64(worldSeed ^ fnv1a64(salt));
    }

    private WorldgenHash() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
