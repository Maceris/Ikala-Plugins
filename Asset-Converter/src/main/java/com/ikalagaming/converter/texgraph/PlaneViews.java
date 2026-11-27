package com.ikalagaming.converter.texgraph;

import lombok.NonNull;

import java.util.Locale;

/**
 * Ways to look at a node's output: as colored pixels for a swatch, the range of its values, and its
 * average.
 */
public final class PlaneViews {
    /** Values above 1 show striped in this color. */
    private static final float[] OVER = {1, 0.15f, 0.15f, 1};

    /** Values below 0 show striped in this color. */
    private static final float[] UNDER = {0.2f, 0.4f, 1, 1};

    /** How wide the stripes over clipped values are, in pixels. */
    private static final int STRIPE = 2;

    /** Rec. 709 weights for the luminance of linear red, green and blue. */
    private static final float[] LUMA = {0.2126f, 0.7152f, 0.0722f};

    /** Not made. */
    private PlaneViews() {}

    /**
     * The plane as linear RGBA pixels: one channel as gray, coordinates as red and green, vectors
     * from -1..1 to 0..1, colors as they are.
     *
     * @param plane The plane.
     * @param type Its pin type.
     * @param marks Whether to stripe pixels whose values are outside 0..1, red above and blue
     *     below, so clipping shows.
     * @return Red, green, blue and alpha for each pixel, rows from the top.
     */
    public static float[] rgba(@NonNull Plane plane, @NonNull String type, boolean marks) {
        final int width = plane.getWidth();
        final int height = plane.getHeight();
        final int channels = plane.getChannels();
        final float[] values = plane.values();
        final float[] out = new float[width * height * 4];
        final boolean vector = TexturePins.VECTOR.equals(type);
        final boolean coordinates = TexturePins.COORDINATES.equals(type);
        for (int i = 0; i < width * height; ++i) {
            final int from = i * channels;
            final int to = i * 4;
            int clipped = 0;
            for (int c = 0; c < 3; ++c) {
                float value = values[from + Math.min(c, channels - 1)];
                if (channels == 2 && c == 2) {
                    value = 0;
                }
                if (vector) {
                    value = value * 0.5f + 0.5f;
                } else if (coordinates) {
                    value -= (float) Math.floor(value);
                }
                if (value > 1) {
                    clipped = 1;
                } else if (value < 0 && clipped == 0) {
                    clipped = -1;
                }
                out[to + c] = value;
            }
            out[to + 3] = channels == 4 ? values[from + 3] : 1;
            if (marks && clipped != 0) {
                final int x = i % width;
                final int y = i / width;
                if ((x + y) / STRIPE % 2 == 0) {
                    System.arraycopy(clipped > 0 ? OVER : UNDER, 0, out, to, 4);
                }
            }
        }
        return out;
    }

    /**
     * Each channel's lowest and highest value, as text.
     *
     * @param plane The plane.
     * @return Like "0.02 to 0.98", or one range per channel for several.
     */
    public static String range(@NonNull Plane plane) {
        final int channels = plane.getChannels();
        final float[] low = new float[channels];
        final float[] high = new float[channels];
        java.util.Arrays.fill(low, Float.POSITIVE_INFINITY);
        java.util.Arrays.fill(high, Float.NEGATIVE_INFINITY);
        final float[] values = plane.values();
        for (int i = 0; i < values.length; ++i) {
            final int c = i % channels;
            low[c] = Math.min(low[c], values[i]);
            high[c] = Math.max(high[c], values[i]);
        }
        if (channels == 1) {
            return number(low[0]) + " to " + number(high[0]);
        }
        final String names = "rgba";
        final StringBuilder out = new StringBuilder();
        for (int c = 0; c < channels; ++c) {
            if (c > 0) {
                out.append(", ");
            }
            out.append(names.charAt(c))
                    .append(' ')
                    .append(number(low[c]))
                    .append(" to ")
                    .append(number(high[c]));
        }
        return out.toString();
    }

    /**
     * A plane's average, as a color: one channel's average is gray.
     *
     * @param plane The plane.
     * @return The average red, green, blue and alpha, linear.
     */
    public static float[] average(@NonNull Plane plane) {
        final int channels = plane.getChannels();
        final double[] sums = new double[4];
        final float[] values = plane.values();
        for (int i = 0; i < values.length; ++i) {
            sums[i % channels] += values[i];
        }
        final int pixels = plane.getWidth() * plane.getHeight();
        final float[] out = new float[4];
        for (int c = 0; c < 3; ++c) {
            out[c] = (float) (sums[Math.min(c, channels - 1)] / pixels);
        }
        if (channels == 2) {
            out[2] = 0;
        }
        out[3] = channels == 4 ? (float) (sums[3] / pixels) : 1;
        return out;
    }

    /**
     * The luminance of a linear color, as an overall albedo.
     *
     * @param rgb Linear red, green and blue.
     * @return The luminance.
     */
    public static float luminance(float @NonNull [] rgb) {
        return LUMA[0] * rgb[0] + LUMA[1] * rgb[1] + LUMA[2] * rgb[2];
    }

    /**
     * A value with three places, as ranges show it.
     *
     * @param value The value.
     * @return The text.
     */
    public static String number(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
