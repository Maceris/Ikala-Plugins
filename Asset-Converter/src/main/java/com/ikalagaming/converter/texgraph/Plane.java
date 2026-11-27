package com.ikalagaming.converter.texgraph;

import lombok.Getter;
import lombok.NonNull;

import java.util.Arrays;

/**
 * A grid of pixels with a few float channels each: what flows along a texture graph's links. A
 * scalar has one channel, coordinates two, a vector three and a color four (linear RGBA). Reads
 * wrap at the edges, so every plane tiles. Planes are not changed once a node has output them, so
 * the evaluator's cache can share them.
 */
public final class Plane {
    /**
     * Pixels across. -- GETTER -- Pixels across.
     *
     * @return The width.
     */
    @Getter private final int width;

    /**
     * Pixels down. -- GETTER -- Pixels down.
     *
     * @return The height.
     */
    @Getter private final int height;

    /**
     * Floats per pixel. -- GETTER -- Floats per pixel.
     *
     * @return The channel count.
     */
    @Getter private final int channels;

    /** The values, rows from the top, each pixel's channels together. */
    private final float[] values;

    /**
     * Make a plane of zeros.
     *
     * @param width Pixels across, at least 1.
     * @param height Pixels down, at least 1.
     * @param channels Floats per pixel, 1 to 4.
     * @throws IllegalArgumentException If a size is out of range.
     */
    public Plane(int width, int height, int channels) {
        if (width < 1 || height < 1 || channels < 1 || channels > 4) {
            throw new IllegalArgumentException(
                    "A plane is at least 1x1 with 1 to 4 channels, not "
                            + width
                            + "x"
                            + height
                            + "x"
                            + channels);
        }
        this.width = width;
        this.height = height;
        this.channels = channels;
        values = new float[width * height * channels];
    }

    /**
     * Make a plane filled with one value per channel.
     *
     * @param width Pixels across.
     * @param height Pixels down.
     * @param fill The value of each channel; its length is the channel count.
     * @return The plane.
     */
    public static Plane filled(int width, int height, float @NonNull ... fill) {
        final Plane plane = new Plane(width, height, fill.length);
        for (int i = 0; i < plane.values.length; ++i) {
            plane.values[i] = fill[i % fill.length];
        }
        return plane;
    }

    /**
     * A channel of a pixel, wrapping at the edges.
     *
     * @param x The column, which may be off the plane.
     * @param y The row, which may be off the plane.
     * @param channel The channel.
     * @return The value.
     */
    public float get(int x, int y, int channel) {
        return values[index(Math.floorMod(x, width), Math.floorMod(y, height)) + channel];
    }

    /**
     * Set a channel of a pixel. Only for the node making the plane.
     *
     * @param x The column, on the plane.
     * @param y The row, on the plane.
     * @param channel The channel.
     * @param value The value.
     */
    public void set(int x, int y, int channel, float value) {
        values[index(x, y) + channel] = value;
    }

    /**
     * Where a pixel's channels start.
     *
     * @param x The column.
     * @param y The row.
     * @return The index of its first channel.
     */
    private int index(int x, int y) {
        return (y * width + x) * channels;
    }

    /**
     * The values, for reading in bulk. Not to be changed.
     *
     * @return The array, rows from the top, each pixel's channels together.
     */
    float[] values() {
        return values;
    }

    /**
     * A copy with every value passed through a function of the value and its channel.
     *
     * @param function Gives the new value.
     * @return The new plane.
     */
    Plane map(@NonNull ChannelFunction function) {
        final Plane out = new Plane(width, height, channels);
        for (int i = 0; i < values.length; ++i) {
            out.values[i] = function.apply(values[i], i % channels);
        }
        return out;
    }

    /** A function of one channel's value. */
    @FunctionalInterface
    interface ChannelFunction {
        /**
         * The new value.
         *
         * @param value The old value.
         * @param channel Its channel.
         * @return The new value.
         */
        float apply(float value, int channel);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Plane plane
                && plane.width == width
                && plane.height == height
                && plane.channels == channels
                && Arrays.equals(plane.values, values);
    }

    @Override
    public int hashCode() {
        return ((width * 31 + height) * 31 + channels) * 31 + Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "Plane[" + width + "x" + height + "x" + channels + "]";
    }
}
