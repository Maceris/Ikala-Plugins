package com.ikalagaming.graphics.gui.flags;

/** Flags for drag*() and slider*() functions. */
public class SliderFlags {
    public static final int NONE = 0;

    /**
     * Make the widget logarithmic (linear otherwise). Consider using NO_ROUND_TO_FORMAT with this
     * if using a format string with a small number of digits.
     */
    public static final int LOGARITHMIC = 1 << 5;

    /**
     * Disable rounding the underlying value to match the precision of the display format string
     * (e.g. %.3f values are rounded to those 3 digits).
     */
    public static final int NO_ROUND_TO_FORMAT = 1 << 6;

    /** Disable ctrl+click or enter key allowing to input text directly into the widget. */
    public static final int NO_INPUT = 1 << 7;

    /**
     * Enable wrapping around from max to min and from min to max. Only supported by drag*()
     * functions for now.
     */
    public static final int WRAP_AROUND = 1 << 8;

    /**
     * Clamp the value to min/max bounds when input manually with ctrl+click. By default ctrl+click
     * allows going out of bounds.
     */
    public static final int CLAMP_ON_INPUT = 1 << 9;

    /**
     * Clamp even if min == max == 0. Otherwise drag*() functions don't clamp with those values.
     * When your clamping limits are dynamic you almost always want to use it.
     */
    public static final int CLAMP_ZERO_RANGE = 1 << 10;

    /** Disable keyboard modifiers altering the tweak speed. */
    public static final int NO_SPEED_TWEAKS = 1 << 11;

    /** dragScalarN(), sliderScalarN(): draw R/G/B/A color markers on each component. */
    public static final int COLOR_MARKERS = 1 << 12;

    /** Internal: the slider is oriented vertically. */
    public static final int INTERNAL_VERTICAL = 1 << 20;

    /** Internal: the value can't be changed. */
    public static final int INTERNAL_READ_ONLY = 1 << 21;

    // Combined flags
    public static final int ALWAYS_CLAMP = CLAMP_ON_INPUT | CLAMP_ZERO_RANGE;

    /** Private constructor so this is not instantiated. */
    private SliderFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
