package com.ikalagaming.factory.world.gen.noise;

import java.util.List;

/**
 * A deterministic noise field, always in [-1, 1]. Cellular and subdivision noise will give several
 * channels; simplex has one, {@value #VALUE_CHANNEL}.
 */
public interface Noise {
    /** The channel every noise has. */
    String VALUE_CHANNEL = "value";

    /**
     * The noise at a point.
     *
     * @param channel Which channel, an index into {@link #channels()}.
     * @param x The x coordinate, in blocks.
     * @param y The y coordinate, in blocks. Ignored by 2D noise.
     * @param z The z coordinate, in blocks.
     * @return The value, from -1 to 1.
     */
    double sample(int channel, double x, double y, double z);

    /**
     * Whether the noise ignores y.
     *
     * @return True for 2D noise.
     */
    boolean is2D();

    /**
     * The channels it gives.
     *
     * @return The channel names.
     */
    List<String> channels();
}
