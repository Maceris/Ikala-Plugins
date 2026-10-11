package com.ikalagaming.factory.world.gen.debug;

import com.ikalagaming.factory.world.gen.WorldgenHash;

import lombok.NonNull;

import java.awt.Color;

/** The colors debug images use for values and IDs, as packed RGB. */
public final class Colormaps {

    /** The color of air in block views: pale sky blue. */
    public static final int AIR = 0xd8ecff;

    /** The color of the zero contour in diverging views. */
    public static final int CONTOUR = 0x000000;

    /** The brightest a channel gets. */
    private static final int CHANNEL_MAX = 255;

    /** Bits to shift for the red channel of a packed color. */
    private static final int RED_SHIFT = 16;

    /** Bits to shift for the green channel of a packed color. */
    private static final int GREEN_SHIFT = 8;

    /** The mask of a packed RGB color, without alpha. */
    private static final int RGB_MASK = 0xffffff;

    /** How much red, green and blue fade from white toward the solid color (brown). */
    private static final double[] SOLID_FADE = {0.45, 0.65, 0.85};

    /** How much red, green and blue fade from white toward the air color (blue). */
    private static final double[] AIR_FADE = {0.8, 0.55, 0.1};

    /** The sequential map's green at its start. */
    private static final double SEQUENTIAL_GREEN_START = 0.15;

    /** How much the sequential map's green rises across it. */
    private static final double SEQUENTIAL_GREEN_RISE = 0.75;

    /** The sequential map's blue at its start, which bulges in the middle and fades at the end. */
    private static final double SEQUENTIAL_BLUE_START = 0.4;

    /** How much the sequential map's blue bulges and fades. */
    private static final double SEQUENTIAL_BLUE_SWING = 0.3;

    /** Hash bits for a hue: 16 of them, spread over the color wheel. */
    private static final int HUE_BITS = 16;

    /** Hash bits for each of saturation and brightness. */
    private static final int LEVEL_BITS = 8;

    /** The lowest saturation for IDs, so colors stay distinct from grey. */
    private static final float SATURATION_MIN = 0.45f;

    /** How far saturation varies above its minimum. */
    private static final float SATURATION_SPAN = 0.4f;

    /** The lowest brightness for IDs, so colors stay readable. */
    private static final float BRIGHTNESS_MIN = 0.6f;

    /** How far brightness varies above its minimum. */
    private static final float BRIGHTNESS_SPAN = 0.35f;

    /**
     * Pack a color.
     *
     * @param red Red, 0 to 1.
     * @param green Green, 0 to 1.
     * @param blue Blue, 0 to 1.
     * @return The packed RGB color.
     */
    public static int rgb(double red, double green, double blue) {
        final int r = (int) Math.round(Math.clamp(red, 0, 1) * CHANNEL_MAX);
        final int g = (int) Math.round(Math.clamp(green, 0, 1) * CHANNEL_MAX);
        final int b = (int) Math.round(Math.clamp(blue, 0, 1) * CHANNEL_MAX);
        return r << RED_SHIFT | g << GREEN_SHIFT | b;
    }

    /**
     * A diverging color for density: air (negative) blue, solid (positive) brown, white at zero.
     *
     * @param v The value, -1 to 1.
     * @return The color.
     */
    public static int diverging(double v) {
        final double t = Math.clamp(Math.abs(v), 0, 1);
        final double[] fade = v > 0 ? SOLID_FADE : AIR_FADE;
        return rgb(1 - fade[0] * t, 1 - fade[1] * t, 1 - fade[2] * t);
    }

    /**
     * A sequential color for parameters, from dark blue through teal to yellow.
     *
     * @param t How far along, 0 to 1.
     * @return The color.
     */
    public static int sequential(double t) {
        final double c = Math.clamp(t, 0, 1);
        return rgb(
                c * c,
                SEQUENTIAL_GREEN_START + SEQUENTIAL_GREEN_RISE * c,
                SEQUENTIAL_BLUE_START
                        + SEQUENTIAL_BLUE_SWING * (StrictMath.sin(c * StrictMath.PI) - c));
    }

    /**
     * A categorical color for an ID, hashed from it, so it stays the same between runs.
     *
     * @param id The ID.
     * @return The color.
     */
    public static int categorical(@NonNull String id) {
        final long hash = WorldgenHash.mix64(WorldgenHash.fnv1a64(id));
        final long hueMask = (1L << HUE_BITS) - 1;
        final long levelMask = (1L << LEVEL_BITS) - 1;
        final float hue = (hash & hueMask) / (float) (1L << HUE_BITS);
        final float saturation =
                SATURATION_MIN
                        + ((hash >>> HUE_BITS) & levelMask) / (float) levelMask * SATURATION_SPAN;
        final float brightness =
                BRIGHTNESS_MIN
                        + ((hash >>> (HUE_BITS + LEVEL_BITS)) & levelMask)
                                / (float) levelMask
                                * BRIGHTNESS_SPAN;
        return Color.HSBtoRGB(hue, saturation, brightness) & RGB_MASK;
    }

    private Colormaps() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
