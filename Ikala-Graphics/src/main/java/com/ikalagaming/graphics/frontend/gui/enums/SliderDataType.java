package com.ikalagaming.graphics.frontend.gui.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The type of data that a scalar widget (drag, slider, input) edits. Java has no unsigned types, so
 * only signed integers are supported.
 */
@Getter
@AllArgsConstructor
public enum SliderDataType {
    /** A byte, stored in a byte[] or IkByte. */
    BYTE("%d", Byte.MIN_VALUE, Byte.MAX_VALUE, false),
    /** A short, stored in a short[] or IkShort. */
    SHORT("%d", Short.MIN_VALUE, Short.MAX_VALUE, false),
    /** An int, stored in an int[] or IkInt. */
    INT("%d", Integer.MIN_VALUE, Integer.MAX_VALUE, false),
    /** A long, stored in a long[] or IkLong. */
    LONG("%d", Long.MIN_VALUE, Long.MAX_VALUE, false),
    /** A float, stored in a float[] or IkFloat. */
    FLOAT("%.3f", -Float.MAX_VALUE, Float.MAX_VALUE, true),
    /** A double, stored in a double[] or IkDouble. */
    DOUBLE("%f", -Double.MAX_VALUE, Double.MAX_VALUE, true),
    ;

    /** The format used to display values when no format is specified. */
    private final String defaultFormat;

    /** The smallest value the type can hold. */
    private final double minValue;

    /** The largest value the type can hold. */
    private final double maxValue;

    /** Whether the type is a floating point type. */
    private final boolean floatingPoint;
}
