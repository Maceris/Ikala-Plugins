package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.gui.data.*;
import com.ikalagaming.graphics.gui.enums.*;
import com.ikalagaming.graphics.gui.flags.*;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.Hash;
import com.ikalagaming.graphics.gui.util.KeyChord;
import com.ikalagaming.graphics.gui.util.MathUtil;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.util.IllegalFormatException;
import java.util.Locale;

/**
 * Drags and sliders, and the data type helpers they share.
 *
 * <p>Dear ImGui uses templates to implement the behaviors once per data type. Here we have two
 * versions: floating point types are edited with doubles, and integer types with longs, so values
 * and limits are always exact. Like Dear ImGui does for 64-bit types, doubles are still used for
 * things that don't need to be exact, like the position of a value along a slider.
 *
 * <p>Limits are passed as boxed numbers ({@link Number}), where null means there is no limit. Drags
 * without a limit use the limit of the data type, and sliders need both limits.
 */
@Slf4j
class IkGuiImplSliders {

    /** Multiplier for the mouse drag threshold, to make drags react faster to mouse movement. */
    private static final float DRAG_MOUSE_THRESHOLD_FACTOR = 0.50f;

    /** Default colors for the R/G/B/A component markers. */
    static final int[] DEFAULT_RGBA_COLOR_MARKERS = {
        Color.rgba(240, 20, 20, 255),
        Color.rgba(20, 240, 20, 255),
        Color.rgba(20, 20, 240, 255),
        Color.rgba(140, 140, 140, 255)
    };

    /** Padding between the slider frame and the grab, in pixels. */
    private static final float GRAB_PADDING = 2.0f;

    /** The default format for sliderAngle(). */
    private static final String SLIDER_ANGLE_DEFAULT_FORMAT = "%.0f deg";

    static Context context;

    // ---------------------------------------------------------------------------------------
    // Data type helpers
    // ---------------------------------------------------------------------------------------

    /**
     * Read a value from a data container.
     *
     * @param dataType The type of data.
     * @param data An array of the data type, or one of the IkByte/IkShort/IkInt/IkLong/IkFloat/
     *     IkDouble boxes.
     * @param index The index into the data.
     * @return The value, as a double.
     */
    static double readScalar(@NonNull SliderDataType dataType, @NonNull Object data, int index) {
        final Object array = unbox(data);
        return switch (dataType) {
            case BYTE -> ((byte[]) array)[index];
            case SHORT -> ((short[]) array)[index];
            case INT -> ((int[]) array)[index];
            case LONG -> ((long[]) array)[index];
            case FLOAT -> ((float[]) array)[index];
            case DOUBLE -> ((double[]) array)[index];
        };
    }

    /**
     * Write a value into a data container, converting it to the data type.
     *
     * @param dataType The type of data.
     * @param data An array of the data type, or one of the Ik* boxes.
     * @param index The index into the data.
     * @param value The value to write.
     */
    static void writeScalar(
            @NonNull SliderDataType dataType, @NonNull Object data, int index, double value) {
        final Object array = unbox(data);
        value = toType(dataType, value);
        switch (dataType) {
            case BYTE -> ((byte[]) array)[index] = (byte) value;
            case SHORT -> ((short[]) array)[index] = (short) value;
            case INT -> ((int[]) array)[index] = (int) value;
            case LONG -> ((long[]) array)[index] = (long) value;
            case FLOAT -> ((float[]) array)[index] = (float) value;
            case DOUBLE -> ((double[]) array)[index] = value;
        }
    }

    /**
     * Fetch the backing array of an Ik* box, or return arrays as is.
     *
     * @param data The data.
     * @return The array.
     */
    private static Object unbox(@NonNull Object data) {
        return switch (data) {
            case IkByte box -> box.getData();
            case IkShort box -> box.getData();
            case IkInt box -> box.getData();
            case IkLong box -> box.getData();
            case IkFloat box -> box.getData();
            case IkDouble box -> box.getData();
            default -> data;
        };
    }

    /**
     * Check that a data container matches the data type and has enough components.
     *
     * @param dataType The data type.
     * @param data The data.
     * @param components The number of components needed.
     * @return True if the data is usable.
     */
    static boolean validateData(
            @NonNull SliderDataType dataType, @NonNull Object data, int components) {
        final Object array = unbox(data);
        final int length =
                switch (dataType) {
                    case BYTE -> array instanceof byte[] values ? values.length : -1;
                    case SHORT -> array instanceof short[] values ? values.length : -1;
                    case INT -> array instanceof int[] values ? values.length : -1;
                    case LONG -> array instanceof long[] values ? values.length : -1;
                    case FLOAT -> array instanceof float[] values ? values.length : -1;
                    case DOUBLE -> array instanceof double[] values ? values.length : -1;
                };
        if (length < 0) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Data of type {} does not match the data type {}",
                    data.getClass().getSimpleName(),
                    dataType);
            return false;
        }
        if (length < components) {
            IkGuiImplDebugTools.reportError(
                    log, "Data has {} values but {} components were requested", length, components);
            return false;
        }
        return true;
    }

    /**
     * Convert a value to what the data type can actually store, truncating integers and clamping to
     * the range of the type. Floats are rounded to float precision.
     *
     * @param dataType The data type.
     * @param value The value.
     * @return The value as the data type would store it.
     */
    static double toType(@NonNull SliderDataType dataType, double value) {
        if (Double.isNaN(value)) {
            return dataType.isFloatingPoint() ? value : 0;
        }
        return switch (dataType) {
            case BYTE, SHORT, INT ->
                    MathUtil.clamp(
                            (long) value,
                            (long) dataType.getMinValue(),
                            (long) dataType.getMaxValue());
            case LONG -> (double) (long) value;
            case FLOAT -> (double) (float) value;
            case DOUBLE -> value;
        };
    }

    /**
     * The smallest value an integer data type can hold.
     *
     * @param dataType The data type.
     * @return The minimum value, exactly.
     */
    static long typeMinLong(@NonNull SliderDataType dataType) {
        return switch (dataType) {
            case BYTE -> Byte.MIN_VALUE;
            case SHORT -> Short.MIN_VALUE;
            case INT -> Integer.MIN_VALUE;
            case LONG, FLOAT, DOUBLE -> Long.MIN_VALUE;
        };
    }

    /**
     * The largest value an integer data type can hold.
     *
     * @param dataType The data type.
     * @return The maximum value, exactly.
     */
    static long typeMaxLong(@NonNull SliderDataType dataType) {
        return switch (dataType) {
            case BYTE -> Byte.MAX_VALUE;
            case SHORT -> Short.MAX_VALUE;
            case INT -> Integer.MAX_VALUE;
            case LONG, FLOAT, DOUBLE -> Long.MAX_VALUE;
        };
    }

    /**
     * Read a value from a data container as a long, which is exact for integer types. Floating
     * point values are truncated.
     *
     * @param dataType The type of data.
     * @param data An array of the data type, or one of the Ik* boxes.
     * @param index The index into the data.
     * @return The value.
     */
    static long readLong(@NonNull SliderDataType dataType, @NonNull Object data, int index) {
        final Object array = unbox(data);
        return switch (dataType) {
            case BYTE -> ((byte[]) array)[index];
            case SHORT -> ((short[]) array)[index];
            case INT -> ((int[]) array)[index];
            case LONG -> ((long[]) array)[index];
            case FLOAT -> (long) ((float[]) array)[index];
            case DOUBLE -> (long) ((double[]) array)[index];
        };
    }

    /**
     * Write a long into a data container, clamping it to the range of the data type.
     *
     * @param dataType The type of data.
     * @param data An array of the data type, or one of the Ik* boxes.
     * @param index The index into the data.
     * @param value The value to write.
     */
    static void writeLong(
            @NonNull SliderDataType dataType, @NonNull Object data, int index, long value) {
        if (dataType.isFloatingPoint()) {
            writeScalar(dataType, data, index, value);
            return;
        }
        final Object array = unbox(data);
        final long clamped = MathUtil.clamp(value, typeMinLong(dataType), typeMaxLong(dataType));
        switch (dataType) {
            case BYTE -> ((byte[]) array)[index] = (byte) clamped;
            case SHORT -> ((short[]) array)[index] = (short) clamped;
            case INT -> ((int[]) array)[index] = (int) clamped;
            default -> ((long[]) array)[index] = clamped;
        }
    }

    /**
     * Read a value exactly, as a Long for integer types or a Double for floating point types.
     *
     * @param dataType The type of data.
     * @param data An array of the data type, or one of the Ik* boxes.
     * @param index The index into the data.
     * @return The value.
     */
    static Number readNumber(@NonNull SliderDataType dataType, @NonNull Object data, int index) {
        if (dataType.isFloatingPoint()) {
            return readScalar(dataType, data, index);
        }
        return readLong(dataType, data, index);
    }

    /**
     * Write a number into a data container, converting it to the data type.
     *
     * @param dataType The type of data.
     * @param data An array of the data type, or one of the Ik* boxes.
     * @param index The index into the data.
     * @param value The value to write.
     */
    static void writeNumber(
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int index,
            @NonNull Number value) {
        if (dataType.isFloatingPoint()) {
            writeScalar(dataType, data, index, value.doubleValue());
        } else {
            writeLong(dataType, data, index, toLong(value));
        }
    }

    /**
     * Convert a number to a long, truncating floating point values.
     *
     * @param value The number.
     * @return The value as a long.
     */
    static long toLong(@NonNull Number value) {
        if (value instanceof Double || value instanceof Float) {
            return (long) value.doubleValue();
        }
        return value.longValue();
    }

    /**
     * Compare two numbers as the data type would.
     *
     * @param dataType The data type.
     * @param a The first number.
     * @param b The second number.
     * @return Negative if a is less than b, 0 if they are equal, positive if a is greater.
     */
    static int compareNumbers(
            @NonNull SliderDataType dataType, @NonNull Number a, @NonNull Number b) {
        if (dataType.isFloatingPoint()) {
            return Double.compare(a.doubleValue(), b.doubleValue());
        }
        return Long.compare(toLong(a), toLong(b));
    }

    /**
     * The smaller of two numbers, as the data type would compare them.
     *
     * @param dataType The data type.
     * @param a The first number.
     * @param b The second number.
     * @return The smaller number.
     */
    private static Number minNumber(
            @NonNull SliderDataType dataType, @NonNull Number a, @NonNull Number b) {
        return compareNumbers(dataType, a, b) <= 0 ? a : b;
    }

    /**
     * The larger of two numbers, as the data type would compare them.
     *
     * @param dataType The data type.
     * @param a The first number.
     * @param b The second number.
     * @return The larger number.
     */
    private static Number maxNumber(
            @NonNull SliderDataType dataType, @NonNull Number a, @NonNull Number b) {
        return compareNumbers(dataType, a, b) >= 0 ? a : b;
    }

    /**
     * The smallest value the data type can hold, as a number.
     *
     * @param dataType The data type.
     * @return The minimum.
     */
    private static Number typeMinNumber(@NonNull SliderDataType dataType) {
        if (dataType.isFloatingPoint()) {
            return dataType.getMinValue();
        }
        return typeMinLong(dataType);
    }

    /**
     * The largest value the data type can hold, as a number.
     *
     * @param dataType The data type.
     * @return The maximum.
     */
    private static Number typeMaxNumber(@NonNull SliderDataType dataType) {
        if (dataType.isFloatingPoint()) {
            return dataType.getMaxValue();
        }
        return typeMaxLong(dataType);
    }

    /**
     * Zero as a number of the data type.
     *
     * @param dataType The data type.
     * @return Zero.
     */
    static Number zeroNumber(@NonNull SliderDataType dataType) {
        if (dataType.isFloatingPoint()) {
            return 0.0;
        }
        return 0L;
    }

    /**
     * The limits of a drag or slider. For integer types the long limits are exact, and the double
     * limits are only used where precision doesn't matter, like the position along a slider. For
     * floating point types only the double limits are used.
     */
    static final class Limits {
        /** If these are the limits of an integer type. */
        final boolean integer;

        /** The minimum, exact for integer types. */
        final long minLong;

        /** The maximum, exact for integer types. */
        final long maxLong;

        /** The minimum, exact for floating point types. */
        final double min;

        /** The maximum, exact for floating point types. */
        final double max;

        /**
         * Limits for an integer type.
         *
         * @param minLong The minimum.
         * @param maxLong The maximum.
         */
        Limits(long minLong, long maxLong) {
            integer = true;
            this.minLong = minLong;
            this.maxLong = maxLong;
            min = minLong;
            max = maxLong;
        }

        /**
         * Limits for a floating point type.
         *
         * @param min The minimum.
         * @param max The maximum.
         */
        Limits(double min, double max) {
            integer = false;
            minLong = (long) min;
            maxLong = (long) max;
            this.min = min;
            this.max = max;
        }

        /**
         * The size of the range (max - min), which is negative for reversed ranges. For integer
         * types this is calculated from the exact limits, so close limits don't round to the same
         * double.
         *
         * @return The size of the range.
         */
        double range() {
            return integer ? difference(maxLong, minLong) : max - min;
        }
    }

    /**
     * Resolve the limits of a drag or slider. Missing limits use the limits of the data type, like
     * Dear ImGui does for drags.
     *
     * @param dataType The data type.
     * @param min The minimum, or null for none.
     * @param max The maximum, or null for none.
     * @return The limits.
     */
    static Limits resolveLimits(@NonNull SliderDataType dataType, Number min, Number max) {
        if (dataType.isFloatingPoint()) {
            return new Limits(
                    min != null ? min.doubleValue() : dataType.getMinValue(),
                    max != null ? max.doubleValue() : dataType.getMaxValue());
        }
        return new Limits(
                min != null ? limitToLong(dataType, min) : typeMinLong(dataType),
                max != null ? limitToLong(dataType, max) : typeMaxLong(dataType));
    }

    /**
     * Convert a limit to a long for an integer type, clamped to the range of the type.
     *
     * @param dataType The integer data type.
     * @param limit The limit.
     * @return The limit as a long.
     */
    private static long limitToLong(@NonNull SliderDataType dataType, @NonNull Number limit) {
        final long result = toLong(limit);
        if ((limit instanceof Double || limit instanceof Float) && limit.doubleValue() != result) {
            IkGuiImplDebugTools.reportError(
                    log, "The limit {} is not a whole number, for data type {}", limit, dataType);
        }
        return MathUtil.clamp(result, typeMinLong(dataType), typeMaxLong(dataType));
    }

    /**
     * Check if limits clamp the value. For legacy reasons, a min and max that are both zero don't
     * clamp unless CLAMP_ZERO_RANGE is set.
     *
     * @param dataType The data type.
     * @param limits The limits.
     * @param sliderFlags The slider flags.
     * @return True if the value is clamped.
     */
    private static boolean isBounded(
            @NonNull SliderDataType dataType, @NonNull Limits limits, int sliderFlags) {
        final boolean clampZeroRange = (sliderFlags & SliderFlags.CLAMP_ZERO_RANGE) != 0;
        if (dataType.isFloatingPoint()) {
            return limits.min < limits.max
                    || (limits.min == limits.max && (limits.min != 0.0 || clampZeroRange));
        }
        return limits.minLong < limits.maxLong
                || (limits.minLong == limits.maxLong && (limits.minLong != 0 || clampZeroRange));
    }

    /**
     * Whether Ctrl+Click text input should be clamped, which only happens with CLAMP_ON_INPUT.
     *
     * @param sliderFlags The slider flags.
     * @param dataType The data type.
     * @param min The minimum, may be null.
     * @param max The maximum, may be null.
     * @return True if text input should be clamped to the limits.
     */
    static boolean tempInputIsClampEnabled(
            int sliderFlags, @NonNull SliderDataType dataType, Number min, Number max) {
        if ((sliderFlags & SliderFlags.CLAMP_ON_INPUT) == 0 || (min == null && max == null)) {
            return false;
        }
        if (min == null || max == null) {
            return true;
        }
        final int rangeDirection = compareNumbers(dataType, min, max);
        if (rangeDirection < 0) {
            return true;
        }
        if (rangeDirection == 0) {
            return compareNumbers(dataType, min, zeroNumber(dataType)) != 0
                    || (sliderFlags & SliderFlags.CLAMP_ZERO_RANGE) != 0;
        }
        return false;
    }

    /**
     * Clamp a value to optional limits, swapping them if they are reversed.
     *
     * @param dataType The data type.
     * @param data The data.
     * @param index The index of the value in the data.
     * @param clampMin The minimum, or null for none.
     * @param clampMax The maximum, or null for none.
     */
    static void clampScalar(
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int index,
            Number clampMin,
            Number clampMax) {
        if (dataType.isFloatingPoint()) {
            double min = clampMin != null ? clampMin.doubleValue() : dataType.getMinValue();
            double max = clampMax != null ? clampMax.doubleValue() : dataType.getMaxValue();
            if (min > max) {
                final double temp = min;
                min = max;
                max = temp;
            }
            writeScalar(
                    dataType,
                    data,
                    index,
                    MathUtil.clamp(readScalar(dataType, data, index), min, max));
            return;
        }
        long min = clampMin != null ? toLong(clampMin) : typeMinLong(dataType);
        long max = clampMax != null ? toLong(clampMax) : typeMaxLong(dataType);
        if (min > max) {
            final long temp = min;
            min = max;
            max = temp;
        }
        writeLong(dataType, data, index, MathUtil.clamp(readLong(dataType, data, index), min, max));
    }

    /**
     * Add or subtract a step from a value, saturating at the range of the type.
     *
     * @param dataType The data type.
     * @param data The data.
     * @param index The index of the value in the data.
     * @param add True to add, false to subtract.
     * @param step The amount to change by.
     */
    static void applyStep(
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int index,
            boolean add,
            @NonNull Number step) {
        if (dataType.isFloatingPoint()) {
            final double value = readScalar(dataType, data, index);
            final double result = add ? value + step.doubleValue() : value - step.doubleValue();
            writeScalar(
                    dataType,
                    data,
                    index,
                    MathUtil.clamp(result, dataType.getMinValue(), dataType.getMaxValue()));
            return;
        }
        final long value = readLong(dataType, data, index);
        final long amount = toLong(step);
        long result;
        try {
            result = add ? Math.addExact(value, amount) : Math.subtractExact(value, amount);
        } catch (ArithmeticException e) {
            result = (add == (amount > 0)) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        writeLong(dataType, data, index, result);
    }

    /**
     * Format a value using a printf style format string. C style length modifiers (like %lld) are
     * stripped, and integers are formatted as integers even with float formats and vice versa.
     *
     * @param dataType The data type.
     * @param value The value, which is formatted exactly if it's a Long.
     * @param format The format string.
     * @return The formatted value.
     */
    static String formatScalar(
            @NonNull SliderDataType dataType, @NonNull Number value, @NonNull String format) {
        final String sanitized = sanitizeFormatForJava(format);
        final int start = findFormatStart(sanitized);
        if (start >= sanitized.length()) {
            // No value is displayed, but "%%" may still need to be converted
            return sanitized.replace("%%", "%");
        }
        final int end = findFormatEnd(sanitized, start);
        final char conversion = Character.toLowerCase(sanitized.charAt(end - 1));
        final boolean integerConversion =
                conversion == 'd' || conversion == 'x' || conversion == 'o';
        try {
            if (integerConversion) {
                long integer = toLong(value);
                // Hexadecimal and octal show the bits of the actual type, not of a long
                if (conversion != 'd') {
                    integer =
                            switch (dataType) {
                                case BYTE -> integer & 0xFFL;
                                case SHORT -> integer & 0xFFFFL;
                                case INT -> integer & 0xFFFF_FFFFL;
                                default -> integer;
                            };
                }
                return String.format(Locale.ROOT, sanitized, integer);
            }
            return String.format(Locale.ROOT, sanitized, value.doubleValue());
        } catch (IllegalFormatException e) {
            log.warn("Invalid format {} for value {}", format, value);
            return dataType.isFloatingPoint()
                    ? String.format(Locale.ROOT, dataType.getDefaultFormat(), value.doubleValue())
                    : Long.toString(toLong(value));
        }
    }

    /**
     * Strip C style length modifiers and flags Java doesn't support from a format string.
     *
     * @param format The format.
     * @return The sanitized format.
     */
    private static String sanitizeFormatForJava(@NonNull String format) {
        final int start = findFormatStart(format);
        if (start >= format.length()) {
            return format;
        }
        final int end = findFormatEnd(format, start);
        final StringBuilder result = new StringBuilder(format.length());
        result.append(format, 0, start);
        for (int i = start; i < end; ++i) {
            final char c = format.charAt(i);
            // MSVC style "I64"/"I32" length modifiers
            if (c == 'I') {
                if (format.startsWith("64", i + 1) || format.startsWith("32", i + 1)) {
                    i += 2;
                }
                continue;
            }
            // Length modifiers, and custom flags from stb_sprintf
            if ("hlLjztw'$_".indexOf(c) >= 0) {
                continue;
            }
            // Java has no unsigned conversion, and 'i' is not valid either
            if (i == end - 1 && (c == 'u' || c == 'i')) {
                result.append('d');
                continue;
            }
            result.append(c);
        }
        result.append(format, end, format.length());
        return result.toString();
    }

    /**
     * Find where the value specifier starts in a format string.
     *
     * @param format The format string.
     * @return The index of the '%', or the length of the string if there is no specifier.
     */
    static int findFormatStart(@NonNull String format) {
        int i = 0;
        while (i < format.length()) {
            final char c = format.charAt(i);
            if (c == '%') {
                if (i + 1 < format.length() && format.charAt(i + 1) == '%') {
                    i += 2;
                    continue;
                }
                return i;
            }
            ++i;
        }
        return format.length();
    }

    /**
     * Find where the value specifier ends in a format string.
     *
     * @param format The format string.
     * @param start The start of the specifier.
     * @return The index after the conversion character.
     */
    static int findFormatEnd(@NonNull String format, int start) {
        // printf type modifiers: I/L/h/j/l/t/w/z, other letters are the conversion
        for (int i = start + 1; i < format.length(); ++i) {
            final char c = format.charAt(i);
            if (Character.isLetter(c) && "ILhjltwz".indexOf(c) < 0) {
                return i + 1;
            }
        }
        return format.length();
    }

    /**
     * Parse the display precision from a format string, e.g. 3 for "%.3f".
     *
     * @param format The format string.
     * @param defaultPrecision The precision to use if it isn't specified.
     * @return The precision, or -1 for maximum precision (scientific notation).
     */
    static int parseFormatPrecision(@NonNull String format, int defaultPrecision) {
        int i = findFormatStart(format);
        if (i >= format.length()) {
            return defaultPrecision;
        }
        ++i;
        while (i < format.length() && Character.isDigit(format.charAt(i))) {
            ++i;
        }
        int precision = Integer.MAX_VALUE;
        if (i < format.length() && format.charAt(i) == '.') {
            ++i;
            final int digitsStart = i;
            while (i < format.length() && Character.isDigit(format.charAt(i))) {
                ++i;
            }
            precision = i > digitsStart ? Integer.parseInt(format.substring(digitsStart, i)) : 0;
            if (precision > 99) {
                precision = defaultPrecision;
            }
        }
        if (i < format.length()) {
            final char c = format.charAt(i);
            if (c == 'e' || c == 'E') {
                // Maximum precision with scientific notation
                precision = -1;
            }
            if ((c == 'g' || c == 'G') && precision == Integer.MAX_VALUE) {
                precision = -1;
            }
        }
        return precision == Integer.MAX_VALUE ? defaultPrecision : precision;
    }

    /**
     * The smallest step a value can change by at a given decimal precision.
     *
     * @param decimalPrecision The number of decimal places.
     * @return The minimum step.
     */
    static float getMinimumStepAtDecimalPrecision(int decimalPrecision) {
        if (decimalPrecision < 0) {
            return Float.MIN_NORMAL;
        }
        return (float) Math.pow(10.0, -decimalPrecision);
    }

    /**
     * Round a floating point value to the precision that its format string displays.
     *
     * @param format The format string.
     * @param dataType The data type, which must be floating point.
     * @param value The value.
     * @return The rounded value.
     */
    static double roundScalarWithFormat(
            @NonNull String format, @NonNull SliderDataType dataType, double value) {
        final int start = findFormatStart(format);
        if (start >= format.length()) {
            // Don't apply if the value is not visible in the format string
            return value;
        }
        final int end = findFormatEnd(format, start);
        final String specifier = sanitizeFormatForJava(format.substring(start, end));
        try {
            final String formatted = String.format(Locale.ROOT, specifier, value).trim();
            return toType(dataType, Double.parseDouble(formatted));
        } catch (IllegalFormatException | NumberFormatException e) {
            return value;
        }
    }

    /**
     * Fetch the format to use for a data type, using the default if none is provided.
     *
     * @param dataType The data type.
     * @param format The provided format, may be null.
     * @return The format to use.
     */
    private static String formatOrDefault(@NonNull SliderDataType dataType, String format) {
        return format != null ? format : dataType.getDefaultFormat();
    }

    // ---------------------------------------------------------------------------------------
    // Logarithmic and linear scaling
    // ---------------------------------------------------------------------------------------

    /**
     * Convert a value in the output space of a slider into a parametric position on the slider
     * itself, the logical opposite of scaleValueFromRatio().
     *
     * @param dataType The data type.
     * @param value The value.
     * @param min The minimum of the range.
     * @param max The maximum of the range.
     * @param logarithmicZeroEpsilon Greater than zero for logarithmic sliders, how close to zero we
     *     can get.
     * @param zeroDeadzoneHalfSize Half the size of the dead zone around zero, in parametric space.
     * @return The ratio from 0 to 1.
     */
    static float scaleRatioFromValue(
            @NonNull SliderDataType dataType,
            double value,
            double min,
            double max,
            float logarithmicZeroEpsilon,
            float zeroDeadzoneHalfSize) {
        if (min == max) {
            return 0.0f;
        }

        final double clamped =
                min < max ? MathUtil.clamp(value, min, max) : MathUtil.clamp(value, max, min);
        if (logarithmicZeroEpsilon > 0.0f) {
            final boolean flipped = max < min;
            if (flipped) {
                // Handle the case where the range is backwards
                final double temp = min;
                min = max;
                max = temp;
            }

            // Fudge min/max to avoid getting close to log(0)
            double minFudged =
                    Math.abs(min) < logarithmicZeroEpsilon
                            ? (min < 0.0 ? -logarithmicZeroEpsilon : logarithmicZeroEpsilon)
                            : min;
            double maxFudged =
                    Math.abs(max) < logarithmicZeroEpsilon
                            ? (max < 0.0 ? -logarithmicZeroEpsilon : logarithmicZeroEpsilon)
                            : max;

            // Awkward special cases - we need ranges of the form (-100 .. 0) to convert to
            // (-100 .. -epsilon), not (-100 .. epsilon)
            if (min == 0.0 && max < 0.0) {
                minFudged = -logarithmicZeroEpsilon;
            } else if (max == 0.0 && min < 0.0) {
                maxFudged = -logarithmicZeroEpsilon;
            }

            final float result;
            if (clamped <= minFudged) {
                // Workaround for values that are in range but below our fudge
                result = 0.0f;
            } else if (clamped >= maxFudged) {
                // Workaround for values that are in range but above our fudge
                result = 1.0f;
            } else if (min * max < 0.0) {
                // The range crosses zero, so split into two portions
                final float zeroPointCenter = (float) (-min / (max - min));
                final float zeroPointSnapLeft = zeroPointCenter - zeroDeadzoneHalfSize;
                final float zeroPointSnapRight = zeroPointCenter + zeroDeadzoneHalfSize;
                if (value == 0.0) {
                    // Special case for exactly zero
                    result = zeroPointCenter;
                } else if (value < 0.0) {
                    result =
                            (1.0f
                                            - (float)
                                                    (Math.log(-clamped / logarithmicZeroEpsilon)
                                                            / Math.log(
                                                                    -minFudged
                                                                            / logarithmicZeroEpsilon)))
                                    * zeroPointSnapLeft;
                } else {
                    result =
                            zeroPointSnapRight
                                    + ((float)
                                                    (Math.log(clamped / logarithmicZeroEpsilon)
                                                            / Math.log(
                                                                    maxFudged
                                                                            / logarithmicZeroEpsilon))
                                            * (1.0f - zeroPointSnapRight));
                }
            } else if (min < 0.0 || max < 0.0) {
                // Entirely negative slider
                result =
                        1.0f
                                - (float)
                                        (Math.log(-clamped / -maxFudged)
                                                / Math.log(-minFudged / -maxFudged));
            } else {
                result = (float) (Math.log(clamped / minFudged) / Math.log(maxFudged / minFudged));
            }

            return flipped ? (1.0f - result) : result;
        }

        // Linear slider
        return (float) ((clamped - min) / (max - min));
    }

    /**
     * Convert a parametric position on a slider into a value in the output space, the logical
     * opposite of scaleRatioFromValue().
     *
     * @param dataType The data type.
     * @param t The ratio from 0 to 1.
     * @param min The minimum of the range.
     * @param max The maximum of the range.
     * @param logarithmicZeroEpsilon Greater than zero for logarithmic sliders, how close to zero we
     *     can get.
     * @param zeroDeadzoneHalfSize Half the size of the dead zone around zero, in parametric space.
     * @return The value, as the data type would store it.
     */
    static double scaleValueFromRatio(
            @NonNull SliderDataType dataType,
            float t,
            double min,
            double max,
            float logarithmicZeroEpsilon,
            float zeroDeadzoneHalfSize) {
        // We special case the extents because otherwise our logarithmic fudging can lead to
        // "mathematically correct" but non-intuitive behaviors like a fully-left slider not
        // actually reaching the minimum value
        if (t <= 0.0f || min == max) {
            return min;
        }
        if (t >= 1.0f) {
            return max;
        }

        double result;
        if (logarithmicZeroEpsilon > 0.0f) {
            // Fudge min/max to avoid getting silly results close to zero
            double minFudged =
                    Math.abs(min) < logarithmicZeroEpsilon
                            ? (min < 0.0 ? -logarithmicZeroEpsilon : logarithmicZeroEpsilon)
                            : min;
            double maxFudged =
                    Math.abs(max) < logarithmicZeroEpsilon
                            ? (max < 0.0 ? -logarithmicZeroEpsilon : logarithmicZeroEpsilon)
                            : max;

            // Check if the range is "backwards"
            final boolean flipped = max < min;
            if (flipped) {
                final double temp = minFudged;
                minFudged = maxFudged;
                maxFudged = temp;
            }

            // Awkward special case - we need ranges of the form (-100 .. 0) to convert to
            // (-100 .. -epsilon), not (-100 .. epsilon)
            if (max == 0.0 && min < 0.0) {
                maxFudged = -logarithmicZeroEpsilon;
            }

            // t, but flipped if necessary to account for us flipping the range
            final float tWithFlip = flipped ? (1.0f - t) : t;

            if (min * max < 0.0) {
                // The range crosses zero, so we have to do this in two parts
                final float zeroPointCenter = (float) (-Math.min(min, max) / Math.abs(max - min));
                final float zeroPointSnapLeft = zeroPointCenter - zeroDeadzoneHalfSize;
                final float zeroPointSnapRight = zeroPointCenter + zeroDeadzoneHalfSize;
                if (tWithFlip >= zeroPointSnapLeft && tWithFlip <= zeroPointSnapRight) {
                    // Special case to make getting exactly zero possible (the epsilon prevents it
                    // otherwise)
                    result = 0.0;
                } else if (tWithFlip < zeroPointCenter) {
                    result =
                            -(logarithmicZeroEpsilon
                                    * Math.pow(
                                            -minFudged / logarithmicZeroEpsilon,
                                            1.0 - (tWithFlip / zeroPointSnapLeft)));
                } else {
                    result =
                            logarithmicZeroEpsilon
                                    * Math.pow(
                                            maxFudged / logarithmicZeroEpsilon,
                                            (tWithFlip - zeroPointSnapRight)
                                                    / (1.0 - zeroPointSnapRight));
                }
            } else if (min < 0.0 || max < 0.0) {
                // Entirely negative slider
                result = -(-maxFudged * Math.pow(-minFudged / -maxFudged, 1.0 - tWithFlip));
            } else {
                result = minFudged * Math.pow(maxFudged / minFudged, tWithFlip);
            }
            return toType(dataType, result);
        }

        // Linear slider
        return toType(dataType, min + (max - min) * t);
    }

    /**
     * The difference between two longs as a double, exact unless it doesn't fit in a long.
     *
     * @param a The first value.
     * @param b The value to subtract.
     * @return a - b.
     */
    private static double difference(long a, long b) {
        final long result = a - b;
        if (((a ^ b) & (a ^ result)) < 0) {
            // Overflow, which only happens for huge ranges where doubles are precise enough
            return (double) a - (double) b;
        }
        return result;
    }

    /**
     * The integer version of scaleRatioFromValue(), which clamps exactly. The ratio itself is
     * calculated with doubles, like Dear ImGui does for 64-bit types.
     *
     * @param value The value.
     * @param min The minimum of the range.
     * @param max The maximum of the range.
     * @param logarithmicZeroEpsilon Greater than zero for logarithmic sliders, how close to zero we
     *     can get.
     * @param zeroDeadzoneHalfSize Half the size of the dead zone around zero, in parametric space.
     * @return The ratio from 0 to 1.
     */
    static float scaleRatioFromValueInteger(
            long value,
            long min,
            long max,
            float logarithmicZeroEpsilon,
            float zeroDeadzoneHalfSize) {
        if (min == max) {
            return 0.0f;
        }
        final long clamped =
                min < max ? MathUtil.clamp(value, min, max) : MathUtil.clamp(value, max, min);
        if (logarithmicZeroEpsilon > 0.0f) {
            return scaleRatioFromValue(
                    SliderDataType.DOUBLE,
                    clamped,
                    min,
                    max,
                    logarithmicZeroEpsilon,
                    zeroDeadzoneHalfSize);
        }
        return (float) (difference(clamped, min) / difference(max, min));
    }

    /**
     * The integer version of scaleValueFromRatio(). The ends of the slider are exactly the limits,
     * and the offset along the slider is added to the exact minimum.
     *
     * @param t The ratio from 0 to 1.
     * @param min The minimum of the range.
     * @param max The maximum of the range.
     * @param logarithmicZeroEpsilon Greater than zero for logarithmic sliders, how close to zero we
     *     can get.
     * @param zeroDeadzoneHalfSize Half the size of the dead zone around zero, in parametric space.
     * @return The value.
     */
    static long scaleValueFromRatioInteger(
            float t, long min, long max, float logarithmicZeroEpsilon, float zeroDeadzoneHalfSize) {
        if (t <= 0.0f || min == max) {
            return min;
        }
        if (t >= 1.0f) {
            return max;
        }
        if (logarithmicZeroEpsilon > 0.0f) {
            final double result =
                    scaleValueFromRatio(
                            SliderDataType.DOUBLE,
                            t,
                            min,
                            max,
                            logarithmicZeroEpsilon,
                            zeroDeadzoneHalfSize);
            // Stay inside the range, in case converting the limits to doubles rounded them
            return MathUtil.clamp((long) result, Math.min(min, max), Math.max(min, max));
        }
        // We want the clicking position to match the grab box, so we round above. Aiming within a
        // huge range is imprecise anyway, but we add the offset to the exact minimum.
        final double offset = difference(max, min) * t;
        return min + (long) (offset + (min > max ? -0.5 : 0.5));
    }

    // ---------------------------------------------------------------------------------------
    // Shared widget pieces
    // ---------------------------------------------------------------------------------------

    /**
     * Whether the modifier for slow tweaking is held (Ctrl, or L1 on gamepads).
     *
     * @return True if tweaking should be slow.
     */
    private static boolean isNavTweakSlowDown() {
        return context.navInputSource == GuiInputSource.GAMEPAD
                ? context.io.getKeyDown(Key.GAMEPAD_L1)
                : context.io.keyCtrl;
    }

    /**
     * Whether the modifier for fast tweaking is held (Shift, or R1 on gamepads).
     *
     * @return True if tweaking should be fast.
     */
    private static boolean isNavTweakFastDown() {
        return context.navInputSource == GuiInputSource.GAMEPAD
                ? context.io.getKeyDown(Key.GAMEPAD_R1)
                : context.io.keyShift;
    }

    /**
     * The speed factor for tweaking drags with keyboard/gamepad navigation.
     *
     * @param sliderFlags Slider flags.
     * @return The factor.
     */
    private static float navTweakFactor(int sliderFlags) {
        if ((sliderFlags & SliderFlags.NO_SPEED_TWEAKS) != 0) {
            return 1.0f;
        }
        if (isNavTweakSlowDown()) {
            return 1.0f / 10.0f;
        }
        return isNavTweakFastDown() ? 10.0f : 1.0f;
    }

    /**
     * Check for the user canceling an interaction, which reverts the value.
     *
     * @param id The ID of the item.
     * @return True if the interaction was canceled.
     */
    private static boolean shortcutsForCancel(int id) {
        final boolean cancelWithKeyboard =
                IkGuiImplKeys.shortcut(KeyChord.of(Key.ESCAPE), InputFlags.NONE, id);
        final boolean cancelWithGamepad =
                (context.io.configFlags & ConfigFlags.NAV_ENABLE_GAMEPAD) != 0
                        && (context.io.backendFlags & BackendFlags.HAS_GAMEPAD) != 0
                        && IkGuiImplKeys.shortcut(
                                KeyChord.of(IkGuiImplNav.navGamepadCancelKey()),
                                InputFlags.NONE,
                                id);
        final boolean cancelWithMouse = IkGuiImplUtils.isMouseReleased(MouseButton.RIGHT, id);
        if (cancelWithMouse) {
            IkGuiImplKeys.setKeyOwner(Key.MOUSE_RIGHT, id, InputFlags.NONE);
        }
        return cancelWithKeyboard || cancelWithGamepad || cancelWithMouse;
    }

    /**
     * Activate a drag or slider when it is clicked. Ctrl+Click (or a double click, if allowed)
     * turns the item into a text input instead.
     *
     * @param window The current window.
     * @param id The ID of the item.
     * @param hovered Whether the item is hovered.
     * @param dataType The data type.
     * @param data The data.
     * @param index The index of the value in the data.
     * @param tempInputAllowed Whether the item can be edited as text.
     * @param doubleClickToInput Whether double clicking edits the item as text.
     * @param vertical Whether the item is vertical, so it uses up/down for tweaking.
     * @return True if the item is being edited as text.
     */
    private static boolean activateOnClick(
            @NonNull Window window,
            int id,
            boolean hovered,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int index,
            boolean tempInputAllowed,
            boolean doubleClickToInput,
            boolean vertical) {
        if (tempInputAllowed && IkGuiImplInputText.tempInputIsActive(id)) {
            return true;
        }
        // Tabbing, navigation activation, or Ctrl+Click turns the item into a text input
        final boolean clicked =
                hovered && IkGuiImplUtils.isMouseClicked(MouseButton.LEFT, InputFlags.NONE, id);
        final boolean doubleClicked =
                doubleClickToInput
                        && hovered
                        && context.io.mouseClickedCount[MouseButton.LEFT.index] == 2
                        && IkGuiImplKeys.testKeyOwner(Key.MOUSE_LEFT, id);
        final boolean navActivated = context.navActivateID == id;
        if (!clicked && !doubleClicked && !navActivated) {
            // (Optional) simple click (without moving) turns a drag into a text input
            if (context.io.configDragClickToInputText
                    && tempInputAllowed
                    && doubleClickToInput
                    && context.activeID == id
                    && hovered
                    && context.io.getMouseReleased(MouseButton.LEFT)
                    && !IkGuiInternal.isMouseDragPastThreshold(
                            MouseButton.LEFT,
                            context.io.mouseDragThreshold * DRAG_MOUSE_THRESHOLD_FACTOR)) {
                context.navActivateID = id;
                context.navActivateFlags = ActivateFlags.PREFER_INPUT;
                return true;
            }
            return false;
        }
        if (clicked || doubleClicked) {
            IkGuiImplKeys.setKeyOwner(Key.MOUSE_LEFT, id, InputFlags.NONE);
        }
        final boolean tempInputActive =
                tempInputAllowed
                        && ((clicked && context.io.keyCtrl)
                                || doubleClicked
                                || (navActivated
                                        && (context.navActivateFlags & ActivateFlags.PREFER_INPUT)
                                                != 0));

        // Store the initial value so the change can be canceled
        context.activeIDValueOnActivation = readNumber(dataType, data, index);
        if (!tempInputActive) {
            IkGuiInternal.setActiveID(id, window);
            IkGuiImplNav.setFocusID(id, window);
            IkGuiInternal.focusWindow(window, WindowFocusRequestFlags.NONE);
            context.activeIDUsingNavDirMask |=
                    vertical
                            ? (1 << Direction.UP.ordinal()) | (1 << Direction.DOWN.ordinal())
                            : (1 << Direction.LEFT.ordinal()) | (1 << Direction.RIGHT.ordinal());
        }
        return tempInputActive;
    }

    /**
     * Render the frame of a drag or slider.
     *
     * @param frameBB The frame bounding box.
     * @param id The ID of the item.
     * @param hovered Whether the item is hovered.
     * @param colorMarker The color marker to draw, 0 for none.
     */
    private static void renderScalarFrame(
            @NonNull RectFloat frameBB, int id, boolean hovered, int colorMarker) {
        final StyleVariables style = context.style.variable;
        final ColorType frameColor;
        if (context.activeID == id) {
            frameColor = ColorType.FRAME_BACKGROUND_ACTIVE;
        } else if (hovered) {
            frameColor = ColorType.FRAME_BACKGROUND_HOVERED;
        } else {
            frameColor = ColorType.FRAME_BACKGROUND;
        }
        IkGuiImplNav.renderNavCursor(frameBB, id, NavRenderCursorFlags.NONE, -1.0f);
        IkGuiInternal.renderFrame(
                frameBB.getLeft(),
                frameBB.getTop(),
                frameBB.getRight(),
                frameBB.getBottom(),
                IkGuiImplUtils.getColorWithGlobalAlpha(frameColor),
                false,
                style.frameRounding);
        if (colorMarker != 0 && style.colorMarkerSize > 0.0f) {
            renderColorComponentMarker(
                    frameBB,
                    IkGuiImplUtils.applyGlobalAlpha(colorMarker, false),
                    style.frameRounding);
        }
        IkGuiInternal.renderFrameBorder(
                frameBB.getLeft(),
                frameBB.getTop(),
                frameBB.getRight(),
                frameBB.getBottom(),
                style.frameRounding);
    }

    /**
     * Draw a colored marker on the left edge of a frame.
     *
     * @param bb The frame bounding box.
     * @param color The color.
     * @param rounding The frame rounding.
     */
    private static void renderColorComponentMarker(
            @NonNull RectFloat bb, int color, float rounding) {
        if (bb.getLeft() + 1 >= bb.getRight()) {
            return;
        }
        final float maxX =
                Math.min(bb.getLeft() + context.style.variable.colorMarkerSize, bb.getRight());
        context.windowCurrent.drawList.addRectFilled(
                bb.getLeft(),
                bb.getTop(),
                maxX,
                bb.getBottom(),
                color,
                Math.min(rounding, maxX - bb.getLeft()),
                DrawFlags.ROUND_CORNERS_LEFT);
    }

    /**
     * Render the value text and the label of a drag or slider.
     *
     * @param frameBB The frame bounding box.
     * @param label The label.
     * @param labelSize The size of the displayed label.
     * @param valueText The formatted value.
     */
    private static void renderValueAndLabel(
            @NonNull RectFloat frameBB,
            @NonNull String label,
            @NonNull Vector2f labelSize,
            @NonNull String valueText) {
        final StyleVariables style = context.style.variable;
        // Several values are being edited at once, so show the mixed value label instead
        final boolean isMixed = (context.lastItemData.itemFlags & ItemFlags.MIXED_VALUE) != 0;
        final String displayed =
                isMixed && context.mixedValueLabel != null ? context.mixedValueLabel : valueText;
        if (context.logEnabled) {
            IkGuiImplLogging.logSetNextTextDecoration("{", "}");
        }
        if (isMixed) {
            IkGuiImplUtils.pushStyleColor(
                    ColorType.TEXT, IkGuiImplUtils.getColor(ColorType.TEXT_DISABLED));
        }
        IkGuiInternal.renderTextClipped(
                frameBB.getLeft(),
                frameBB.getTop(),
                frameBB.getRight(),
                frameBB.getBottom(),
                displayed,
                null,
                0.5f,
                0.5f,
                null);
        if (isMixed) {
            IkGuiImplUtils.popStyleColor();
        }
        if (labelSize.x > 0.0f) {
            IkGuiInternal.renderText(
                    frameBB.getRight() + style.itemInnerSpacing.x,
                    frameBB.getTop() + style.framePadding.y,
                    Hash.getDisplayedText(label),
                    false);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Drags
    // ---------------------------------------------------------------------------------------

    /**
     * The behavior of a drag while it is active (held by the mouse).
     *
     * @param dataType The data type.
     * @param data The data.
     * @param index The index of the value in the data.
     * @param speed The speed, in value units per pixel.
     * @param limits The limits.
     * @param format The display format.
     * @param sliderFlags Slider flags.
     * @return True if the value changed.
     */
    private static boolean dragBehaviorImpl(
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int index,
            float speed,
            @NonNull Limits limits,
            @NonNull String format,
            int sliderFlags) {
        final boolean vertical = (sliderFlags & SliderFlags.INTERNAL_VERTICAL) != 0;
        final boolean isBounded = isBounded(dataType, limits, sliderFlags);
        final boolean isWrapped = isBounded && (sliderFlags & SliderFlags.WRAP_AROUND) != 0;
        final boolean isLogarithmic = (sliderFlags & SliderFlags.LOGARITHMIC) != 0;
        final boolean isFloatingPoint = dataType.isFloatingPoint();
        // The size of the range doesn't need to be exact
        final double range = limits.range();

        // Default tweak speed
        if (speed == 0.0f && isBounded && (range < Float.MAX_VALUE)) {
            speed = (float) (range * context.dragSpeedDefaultRatio);
        }

        // Inputs accumulate into the drag accumulator, which is flushed into the current value as
        // soon as it makes a difference with our precision settings
        float adjustDelta = 0.0f;
        final IkIO io = context.io;
        if (context.activeIDSource == GuiInputSource.MOUSE
                && IkGuiImplUtils.isMousePosValid(io.mousePosition.x, io.mousePosition.y)
                && IkGuiInternal.isMouseDragPastThreshold(
                        MouseButton.LEFT, io.mouseDragThreshold * DRAG_MOUSE_THRESHOLD_FACTOR)) {
            adjustDelta = vertical ? io.mouseDelta.y : io.mouseDelta.x;
            if (io.keyAlt && (sliderFlags & SliderFlags.NO_SPEED_TWEAKS) == 0) {
                adjustDelta *= 1.0f / 100.0f;
            }
            if (io.keyShift && (sliderFlags & SliderFlags.NO_SPEED_TWEAKS) == 0) {
                adjustDelta *= 10.0f;
            }
        } else if (context.activeIDSource == GuiInputSource.KEYBOARD
                || context.activeIDSource == GuiInputSource.GAMEPAD) {
            final int decimalPrecision = isFloatingPoint ? parseFormatPrecision(format, 3) : 0;
            final float tweakFactor = navTweakFactor(sliderFlags);
            adjustDelta =
                    IkGuiImplNav.getNavTweakPressedAmount(vertical ? Axis.Y : Axis.X) * tweakFactor;
            speed = Math.max(speed, getMinimumStepAtDecimalPrecision(decimalPrecision));
        }
        adjustDelta *= speed;

        // For vertical drags we currently assume that up means a higher value
        if (vertical) {
            adjustDelta = -adjustDelta;
        }

        // For logarithmic use our range is effectively 0..1 so scale the delta into that range
        if (isLogarithmic && (range < Float.MAX_VALUE) && range > 0.000_001) {
            adjustDelta /= (float) range;
        }

        // Clear the current value on activation. Avoid altering values and clamping when we are
        // already past the limits and heading in the same direction, so e.g. if the range is
        // 0..255, the current value is 300 and we are pushing to the right, keep the 300.
        final boolean atOrAboveMax;
        final boolean atOrBelowMin;
        if (isFloatingPoint) {
            final double value = readScalar(dataType, data, index);
            atOrAboveMax = value >= limits.max;
            atOrBelowMin = value <= limits.min;
        } else {
            final long value = readLong(dataType, data, index);
            atOrAboveMax = value >= limits.maxLong;
            atOrBelowMin = value <= limits.minLong;
        }
        final boolean isJustActivated = context.activeIDIsJustActivated;
        final boolean isAlreadyPastLimitsAndPushingOutward =
                isBounded
                        && !isWrapped
                        && ((atOrAboveMax && adjustDelta > 0.0f)
                                || (atOrBelowMin && adjustDelta < 0.0f));
        if (isJustActivated || isAlreadyPastLimitsAndPushingOutward) {
            context.dragCurrentAccumulatedDelta = 0.0f;
            context.dragCurrentAccumulatedDeltaDirty = false;
        } else if (adjustDelta != 0.0f) {
            context.dragCurrentAccumulatedDelta += adjustDelta;
            context.dragCurrentAccumulatedDeltaDirty = true;
        }

        if (!context.dragCurrentAccumulatedDeltaDirty) {
            return false;
        }

        // When using logarithmic sliders, we need to clamp to avoid hitting zero, but our choice
        // of clamp value greatly affects precision. We use the format precision to estimate a good
        // lower bound.
        float logarithmicZeroEpsilon = 0.0f;
        if (isLogarithmic) {
            final int decimalPrecision = isFloatingPoint ? parseFormatPrecision(format, 3) : 1;
            logarithmicZeroEpsilon = (float) Math.pow(0.1, decimalPrecision);
        }
        context.dragCurrentAccumulatedDeltaDirty = false;
        if (isFloatingPoint) {
            return dragApplyFloat(
                    dataType,
                    data,
                    index,
                    limits,
                    format,
                    sliderFlags,
                    isBounded,
                    isWrapped,
                    logarithmicZeroEpsilon);
        }
        return dragApplyInteger(
                dataType,
                data,
                index,
                limits,
                adjustDelta,
                isBounded,
                isWrapped,
                logarithmicZeroEpsilon);
    }

    /**
     * Apply the accumulated drag delta to a floating point value.
     *
     * @param dataType The floating point data type.
     * @param data The data.
     * @param index The index of the value in the data.
     * @param limits The limits.
     * @param format The display format.
     * @param sliderFlags Slider flags.
     * @param isBounded Whether the value is clamped (or wrapped) to the limits.
     * @param isWrapped Whether the value wraps around at the limits.
     * @param logarithmicZeroEpsilon Greater than zero for logarithmic drags.
     * @return True if the value changed.
     */
    private static boolean dragApplyFloat(
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int index,
            @NonNull Limits limits,
            @NonNull String format,
            int sliderFlags,
            boolean isBounded,
            boolean isWrapped,
            float logarithmicZeroEpsilon) {
        final double value = readScalar(dataType, data, index);
        final double min = limits.min;
        final double max = limits.max;
        // Drag widgets have no dead zone (as it doesn't make sense)
        final float zeroDeadzoneHalfSize = 0.0f;

        double current = value;
        float oldParametric = 0.0f;
        if (logarithmicZeroEpsilon > 0.0f) {
            // Convert to parametric space, apply the delta, convert back
            oldParametric =
                    scaleRatioFromValue(
                            dataType,
                            current,
                            min,
                            max,
                            logarithmicZeroEpsilon,
                            zeroDeadzoneHalfSize);
            current =
                    scaleValueFromRatio(
                            dataType,
                            oldParametric + context.dragCurrentAccumulatedDelta,
                            min,
                            max,
                            logarithmicZeroEpsilon,
                            zeroDeadzoneHalfSize);
        } else {
            current += context.dragCurrentAccumulatedDelta;
        }

        // Round to the user desired precision based on the format string
        if ((sliderFlags & SliderFlags.NO_ROUND_TO_FORMAT) == 0) {
            current = roundScalarWithFormat(format, dataType, current);
        }
        current = toType(dataType, current);

        // Preserve the remainder after rounding has been applied. This also allows slow tweaking
        // of values.
        if (logarithmicZeroEpsilon > 0.0f) {
            final float newParametric =
                    scaleRatioFromValue(
                            dataType,
                            current,
                            min,
                            max,
                            logarithmicZeroEpsilon,
                            zeroDeadzoneHalfSize);
            context.dragCurrentAccumulatedDelta -= newParametric - oldParametric;
        } else {
            context.dragCurrentAccumulatedDelta -= (float) (current - value);
        }

        // Lose the zero sign
        if (current == 0.0) {
            current = 0.0;
        }

        if (value != current && isBounded) {
            if (isWrapped) {
                // Wrap values
                if (current < min) {
                    current += max - min;
                }
                if (current > max) {
                    current -= max - min;
                }
            } else {
                current = MathUtil.clamp(current, min, max);
            }
        }

        // Apply the result
        current = toType(dataType, current);
        if (value == current) {
            return false;
        }
        writeScalar(dataType, data, index, current);
        return true;
    }

    /**
     * Apply the accumulated drag delta to an integer value, with exact long math.
     *
     * @param dataType The integer data type.
     * @param data The data.
     * @param index The index of the value in the data.
     * @param limits The limits.
     * @param adjustDelta The change this frame, to detect overflow.
     * @param isBounded Whether the value is clamped (or wrapped) to the limits.
     * @param isWrapped Whether the value wraps around at the limits.
     * @param logarithmicZeroEpsilon Greater than zero for logarithmic drags.
     * @return True if the value changed.
     */
    private static boolean dragApplyInteger(
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int index,
            @NonNull Limits limits,
            float adjustDelta,
            boolean isBounded,
            boolean isWrapped,
            float logarithmicZeroEpsilon) {
        final long value = readLong(dataType, data, index);
        final long min = limits.minLong;
        final long max = limits.maxLong;
        // Drag widgets have no dead zone (as it doesn't make sense)
        final float zeroDeadzoneHalfSize = 0.0f;

        long current = value;
        if (logarithmicZeroEpsilon > 0.0f) {
            // Convert to parametric space, apply the delta, convert back
            final float oldParametric =
                    scaleRatioFromValueInteger(
                            current, min, max, logarithmicZeroEpsilon, zeroDeadzoneHalfSize);
            current =
                    scaleValueFromRatioInteger(
                            oldParametric + context.dragCurrentAccumulatedDelta,
                            min,
                            max,
                            logarithmicZeroEpsilon,
                            zeroDeadzoneHalfSize);
            // Preserve the remainder, which also allows slow tweaking of values
            final float newParametric =
                    scaleRatioFromValueInteger(
                            current, min, max, logarithmicZeroEpsilon, zeroDeadzoneHalfSize);
            context.dragCurrentAccumulatedDelta -= newParametric - oldParametric;
        } else {
            // This may overflow, which is handled when clamping
            final long step = (long) context.dragCurrentAccumulatedDelta;
            current += step;
            context.dragCurrentAccumulatedDelta -= step;
        }

        if (value != current && isBounded) {
            if (isWrapped) {
                // Wrap values
                if (current < min) {
                    current += max - min + 1;
                }
                if (current > max) {
                    current -= max - min + 1;
                }
            } else {
                // Clamp values, handling overflow
                if (current < min || (current > value && adjustDelta < 0.0f)) {
                    current = min;
                }
                if (current > max || (current < value && adjustDelta > 0.0f)) {
                    current = max;
                }
            }
        }

        // Apply the result
        if (value == current) {
            return false;
        }
        writeLong(dataType, data, index, current);
        return true;
    }

    /**
     * Revert a value to what it was when the item was activated.
     *
     * @param dataType The data type.
     * @param data The data.
     * @param index The index of the value in the data.
     * @return True if the value changed.
     */
    private static boolean revertToValueOnActivation(
            @NonNull SliderDataType dataType, @NonNull Object data, int index) {
        final Number before = readNumber(dataType, data, index);
        if (context.activeIDValueOnActivation != null) {
            writeNumber(dataType, data, index, context.activeIDValueOnActivation);
        }
        IkGuiInternal.clearActiveID();
        return !before.equals(readNumber(dataType, data, index));
    }

    /**
     * Process the behavior of a drag.
     *
     * @param id The ID of the item.
     * @param dataType The data type.
     * @param data The data.
     * @param index The index of the value in the data.
     * @param speed The speed, in value units per pixel.
     * @param limits The limits.
     * @param format The display format.
     * @param sliderFlags Slider flags.
     * @return True if the value changed.
     */
    static boolean dragBehavior(
            int id,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int index,
            float speed,
            @NonNull Limits limits,
            @NonNull String format,
            int sliderFlags) {
        if (context.activeID == id) {
            if (context.activeIDSource == GuiInputSource.MOUSE
                    && !context.io.mouseDown[MouseButton.LEFT.index]) {
                IkGuiInternal.clearActiveID();
            } else if ((context.activeIDSource == GuiInputSource.KEYBOARD
                            || context.activeIDSource == GuiInputSource.GAMEPAD)
                    && context.navActivatePressedID == id
                    && !context.activeIDIsJustActivated) {
                IkGuiInternal.clearActiveID();
            } else if (shortcutsForCancel(id)) {
                // Canceling reverts to the initial value
                return revertToValueOnActivation(dataType, data, index);
            }
        }
        if (context.activeID != id) {
            return false;
        }
        if ((context.lastItemData.itemFlags & ItemFlags.READ_ONLY) != 0
                || (sliderFlags & SliderFlags.INTERNAL_READ_ONLY) != 0) {
            return false;
        }

        return dragBehaviorImpl(dataType, data, index, speed, limits, format, sliderFlags);
    }

    /**
     * A drag widget for a single value of any type.
     *
     * @param label The label, which is also used for the ID.
     * @param dataType The data type.
     * @param data An array of the data type, or one of the Ik* boxes.
     * @param speed The speed, in value units per pixel.
     * @param min The minimum value, or null for the minimum of the data type. If min and max are
     *     both 0, the value is not clamped unless CLAMP_ZERO_RANGE is set.
     * @param max The maximum value, or null for the maximum of the data type.
     * @param format The display format, null for the default for the data type.
     * @param sliderFlags Slider flags.
     * @return True if the value changed.
     */
    public static boolean dragScalar(
            @NonNull String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            float speed,
            Number min,
            Number max,
            String format,
            int sliderFlags) {
        return dragScalarIndex(label, dataType, data, 0, speed, min, max, format, sliderFlags);
    }

    /**
     * A drag widget for one value out of a data array.
     *
     * @param label The label, which is also used for the ID.
     * @param dataType The data type.
     * @param data An array of the data type, or one of the Ik* boxes.
     * @param index The index of the value in the data.
     * @param speed The speed, in value units per pixel.
     * @param min The minimum value, or null for the minimum of the data type.
     * @param max The maximum value, or null for the maximum of the data type.
     * @param format The display format, null for the default for the data type.
     * @param sliderFlags Slider flags.
     * @return True if the value changed.
     */
    static boolean dragScalarIndex(
            @NonNull String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int index,
            float speed,
            Number min,
            Number max,
            String format,
            int sliderFlags) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        if (!validateData(dataType, data, index + 1)) {
            return false;
        }

        final StyleVariables style = context.style.variable;
        final int id = window.getID(label);
        final float width = IkGuiImplUtils.calculateItemWidth();
        final int colorMarker =
                (context.nextItemData.fieldFlags & NextItemFlags.HAS_COLOR_MARKER) != 0
                        ? context.nextItemData.colorMarker
                        : 0;

        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, label, true, -1.0f);
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat frameBB =
                new RectFloat(x, y, x + width, y + labelSize.y + style.framePadding.y * 2.0f);
        final RectFloat totalBB =
                new RectFloat(
                        x,
                        y,
                        frameBB.getRight()
                                + (labelSize.x > 0.0f
                                        ? style.itemInnerSpacing.x + labelSize.x
                                        : 0.0f),
                        frameBB.getBottom());

        IkGuiInternal.itemSize(totalBB, style.framePadding.y);
        final boolean tempInputAllowed = (sliderFlags & SliderFlags.NO_INPUT) == 0;
        if (!IkGuiInternal.itemAdd(
                totalBB,
                id,
                frameBB,
                tempInputAllowed ? ItemFlags.INTERNAL_INPUTABLE : ItemFlags.NONE)) {
            return false;
        }

        format = formatOrDefault(dataType, format);

        final boolean hovered =
                IkGuiInternal.itemHoverable(frameBB, id, context.lastItemData.itemFlags);
        final boolean tempInputActive =
                activateOnClick(
                        window,
                        id,
                        hovered,
                        dataType,
                        data,
                        index,
                        (sliderFlags & SliderFlags.NO_INPUT) == 0,
                        true,
                        false);
        if (tempInputActive) {
            // Only clamp Ctrl+Click input when CLAMP_ON_INPUT is set
            final boolean clampEnabled = tempInputIsClampEnabled(sliderFlags, dataType, min, max);
            return IkGuiImplInputText.tempInputScalar(
                    frameBB,
                    id,
                    label,
                    dataType,
                    data,
                    index,
                    format,
                    clampEnabled ? min : null,
                    clampEnabled ? max : null);
        }

        renderScalarFrame(frameBB, id, hovered, colorMarker);

        // Drag behavior
        final Limits limits = resolveLimits(dataType, min, max);
        final boolean valueChanged =
                dragBehavior(id, dataType, data, index, speed, limits, format, sliderFlags);
        if (valueChanged) {
            IkGuiInternal.markItemEdited(id);
        }

        // Display the value using the user-provided display format so the user can add
        // prefix/suffix/decorations to the value
        final String valueText = formatScalar(dataType, readNumber(dataType, data, index), format);
        renderValueAndLabel(frameBB, label, labelSize, valueText);

        IkGuiInternal.testEngineItemInfo(
                id,
                label,
                context.lastItemData.statusFlags
                        | (tempInputAllowed ? ItemStatusFlags.INPUTABLE : 0));
        return valueChanged;
    }

    /**
     * Multiple drags on one line, for editing multiple components.
     *
     * @param label The label, which is also used for the ID.
     * @param dataType The data type.
     * @param data An array of the data type.
     * @param components The number of components.
     * @param speed The speed, in value units per pixel.
     * @param min The minimum value, or null for the minimum of the data type.
     * @param max The maximum value, or null for the maximum of the data type.
     * @param format The display format, null for the default for the data type.
     * @param sliderFlags Slider flags.
     * @return True if any value changed.
     */
    public static boolean dragScalarN(
            @NonNull String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            float speed,
            Number min,
            Number max,
            String format,
            int sliderFlags) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        if (!validateData(dataType, data, components)) {
            return false;
        }

        boolean valueChanged = false;
        IkGuiImplLayout.beginGroup();
        IkGuiImplUtils.pushID(label);
        IkGuiImplLayout.pushMultiItemsWidths(components, IkGuiImplUtils.calculateItemWidth());
        for (int i = 0; i < components; ++i) {
            IkGuiImplUtils.pushID(i);
            if (i > 0) {
                IkGuiImplLayout.sameLine(0, context.style.variable.itemInnerSpacing.x);
            }
            if ((sliderFlags & SliderFlags.COLOR_MARKERS) != 0) {
                setNextItemColorMarker(DEFAULT_RGBA_COLOR_MARKERS[i % 4]);
            }
            valueChanged |=
                    dragScalarIndex("", dataType, data, i, speed, min, max, format, sliderFlags);
            IkGuiImplUtils.popID();
            IkGuiImplLayout.popItemWidth();
        }
        IkGuiImplUtils.popID();

        renderGroupLabel(label);
        IkGuiImplLayout.endGroup();
        return valueChanged;
    }

    /**
     * Render the label after a group of components, if it has visible text.
     *
     * @param label The label.
     */
    private static void renderGroupLabel(@NonNull String label) {
        final String displayed = Hash.getDisplayedText(label);
        if (!displayed.isEmpty()) {
            IkGuiImplLayout.sameLine(0, context.style.variable.itemInnerSpacing.x);
            IkGuiImplText.textEx(displayed);
        }
    }

    /**
     * Set the color marker for the next drag or slider.
     *
     * @param color The color, in RGBA format.
     */
    static void setNextItemColorMarker(int color) {
        context.nextItemData.fieldFlags |= NextItemFlags.HAS_COLOR_MARKER;
        context.nextItemData.colorMarker = color;
    }

    /**
     * Two drags for editing a range, where the min can't go above the max and vice versa.
     *
     * @param label The label, which is also used for the ID.
     * @param dataType The data type.
     * @param currentMin The current minimum of the range.
     * @param currentMax The current maximum of the range.
     * @param speed The speed, in value units per pixel.
     * @param min The minimum value.
     * @param max The maximum value.
     * @param format The display format.
     * @param formatMax The display format for the max, null to use the same format.
     * @param sliderFlags Slider flags.
     * @return True if either value changed.
     */
    static boolean dragRange2(
            @NonNull String label,
            @NonNull SliderDataType dataType,
            @NonNull Object currentMin,
            @NonNull Object currentMax,
            float speed,
            @NonNull Number min,
            @NonNull Number max,
            String format,
            String formatMax,
            int sliderFlags) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        if (!validateData(dataType, currentMin, 1) || !validateData(dataType, currentMax, 1)) {
            return false;
        }

        IkGuiImplUtils.pushID(label);
        IkGuiImplLayout.beginGroup();
        IkGuiImplLayout.pushMultiItemsWidths(2, IkGuiImplUtils.calculateItemWidth());

        // Without a range, each value is only limited by the other one
        final boolean noRange = compareNumbers(dataType, min, max) >= 0;
        final Number currentMaxValue = readNumber(dataType, currentMax, 0);
        final Number minMin = noRange ? typeMinNumber(dataType) : min;
        final Number minMax = noRange ? currentMaxValue : minNumber(dataType, max, currentMaxValue);
        final int minFlags =
                sliderFlags
                        | (compareNumbers(dataType, minMin, minMax) == 0
                                ? SliderFlags.INTERNAL_READ_ONLY
                                : 0);
        boolean valueChanged =
                dragScalar("##min", dataType, currentMin, speed, minMin, minMax, format, minFlags);
        IkGuiImplLayout.popItemWidth();
        IkGuiImplLayout.sameLine(0, context.style.variable.itemInnerSpacing.x);

        final Number currentMinValue = readNumber(dataType, currentMin, 0);
        final Number maxMin = noRange ? currentMinValue : maxNumber(dataType, min, currentMinValue);
        final Number maxMax = noRange ? typeMaxNumber(dataType) : max;
        final int maxFlags =
                sliderFlags
                        | (compareNumbers(dataType, maxMin, maxMax) == 0
                                ? SliderFlags.INTERNAL_READ_ONLY
                                : 0);
        valueChanged |=
                dragScalar(
                        "##max",
                        dataType,
                        currentMax,
                        speed,
                        maxMin,
                        maxMax,
                        formatMax != null ? formatMax : format,
                        maxFlags);
        IkGuiImplLayout.popItemWidth();
        IkGuiImplLayout.sameLine(0, context.style.variable.itemInnerSpacing.x);

        IkGuiImplText.textEx(Hash.getDisplayedText(label));
        IkGuiImplLayout.endGroup();
        IkGuiImplUtils.popID();
        return valueChanged;
    }

    // ---------------------------------------------------------------------------------------
    // Sliders
    // ---------------------------------------------------------------------------------------

    /**
     * The position of the current value along a slider.
     *
     * @param dataType The data type.
     * @param data The data.
     * @param index The index of the value in the data.
     * @param limits The limits.
     * @param logarithmicZeroEpsilon Greater than zero for logarithmic sliders.
     * @param zeroDeadzoneHalfSize Half the size of the dead zone around zero.
     * @return The ratio from 0 to 1.
     */
    private static float ratioOfValue(
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int index,
            @NonNull Limits limits,
            float logarithmicZeroEpsilon,
            float zeroDeadzoneHalfSize) {
        if (dataType.isFloatingPoint()) {
            return scaleRatioFromValue(
                    dataType,
                    readScalar(dataType, data, index),
                    limits.min,
                    limits.max,
                    logarithmicZeroEpsilon,
                    zeroDeadzoneHalfSize);
        }
        return scaleRatioFromValueInteger(
                readLong(dataType, data, index),
                limits.minLong,
                limits.maxLong,
                logarithmicZeroEpsilon,
                zeroDeadzoneHalfSize);
    }

    /**
     * Convert a position along a slider to a value, round it the way it would be stored, and
     * convert it back to a position. Used to find how far the slider actually moved.
     *
     * @param dataType The data type.
     * @param t The ratio from 0 to 1.
     * @param limits The limits.
     * @param format The display format.
     * @param sliderFlags Slider flags.
     * @param logarithmicZeroEpsilon Greater than zero for logarithmic sliders.
     * @param zeroDeadzoneHalfSize Half the size of the dead zone around zero.
     * @return The ratio of the rounded value.
     */
    private static float ratioAfterRounding(
            @NonNull SliderDataType dataType,
            float t,
            @NonNull Limits limits,
            @NonNull String format,
            int sliderFlags,
            float logarithmicZeroEpsilon,
            float zeroDeadzoneHalfSize) {
        if (dataType.isFloatingPoint()) {
            double value =
                    scaleValueFromRatio(
                            dataType,
                            t,
                            limits.min,
                            limits.max,
                            logarithmicZeroEpsilon,
                            zeroDeadzoneHalfSize);
            if ((sliderFlags & SliderFlags.NO_ROUND_TO_FORMAT) == 0) {
                value = roundScalarWithFormat(format, dataType, value);
            }
            return scaleRatioFromValue(
                    dataType,
                    value,
                    limits.min,
                    limits.max,
                    logarithmicZeroEpsilon,
                    zeroDeadzoneHalfSize);
        }
        final long value =
                scaleValueFromRatioInteger(
                        t,
                        limits.minLong,
                        limits.maxLong,
                        logarithmicZeroEpsilon,
                        zeroDeadzoneHalfSize);
        return scaleRatioFromValueInteger(
                value,
                limits.minLong,
                limits.maxLong,
                logarithmicZeroEpsilon,
                zeroDeadzoneHalfSize);
    }

    /**
     * Set the value from a position along a slider, rounding floating point values to the format.
     *
     * @param dataType The data type.
     * @param data The data.
     * @param index The index of the value in the data.
     * @param t The ratio from 0 to 1.
     * @param limits The limits.
     * @param format The display format.
     * @param sliderFlags Slider flags.
     * @param logarithmicZeroEpsilon Greater than zero for logarithmic sliders.
     * @param zeroDeadzoneHalfSize Half the size of the dead zone around zero.
     * @return True if the value changed.
     */
    private static boolean setValueFromRatio(
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int index,
            float t,
            @NonNull Limits limits,
            @NonNull String format,
            int sliderFlags,
            float logarithmicZeroEpsilon,
            float zeroDeadzoneHalfSize) {
        if (dataType.isFloatingPoint()) {
            final double value = readScalar(dataType, data, index);
            double newValue =
                    scaleValueFromRatio(
                            dataType,
                            t,
                            limits.min,
                            limits.max,
                            logarithmicZeroEpsilon,
                            zeroDeadzoneHalfSize);
            // Round to the user desired precision based on the format string
            if ((sliderFlags & SliderFlags.NO_ROUND_TO_FORMAT) == 0) {
                newValue = roundScalarWithFormat(format, dataType, newValue);
            }
            newValue = toType(dataType, newValue);
            if (value == newValue) {
                return false;
            }
            writeScalar(dataType, data, index, newValue);
            return true;
        }
        final long value = readLong(dataType, data, index);
        final long newValue =
                scaleValueFromRatioInteger(
                        t,
                        limits.minLong,
                        limits.maxLong,
                        logarithmicZeroEpsilon,
                        zeroDeadzoneHalfSize);
        if (value == newValue) {
            return false;
        }
        writeLong(dataType, data, index, newValue);
        return true;
    }

    /**
     * Check that slider limits are usable. Like Dear ImGui, sliders have a maximum usable range of
     * half the range of the type for 64-bit and floating point types, so the size of the range
     * doesn't overflow. Smaller integer types are calculated with longs, so they can use the full
     * range.
     *
     * @param dataType The data type.
     * @param min The minimum, may be null.
     * @param max The maximum, may be null.
     * @return The limits, or null if they are missing.
     */
    private static Limits sliderLimits(@NonNull SliderDataType dataType, Number min, Number max) {
        if (min == null || max == null) {
            IkGuiImplDebugTools.reportError(log, "Sliders need both a min and a max");
            return null;
        }
        final Limits limits = resolveLimits(dataType, min, max);
        final boolean inRange =
                switch (dataType) {
                    case BYTE, SHORT, INT -> true;
                    case LONG ->
                            Math.min(limits.minLong, limits.maxLong) >= Long.MIN_VALUE / 2
                                    && Math.max(limits.minLong, limits.maxLong)
                                            <= Long.MAX_VALUE / 2;
                    case FLOAT, DOUBLE ->
                            Math.min(limits.min, limits.max) >= dataType.getMinValue() / 2
                                    && Math.max(limits.min, limits.max)
                                            <= dataType.getMaxValue() / 2;
                };
        if (!inRange) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Slider limits {}..{} are outside half the range of {}, use a drag instead",
                    min,
                    max,
                    dataType);
        }
        return limits;
    }

    /**
     * Process the behavior of a slider, and calculate where the grab should be drawn.
     *
     * @param bb The bounding box of the slider frame.
     * @param id The ID of the item.
     * @param dataType The data type.
     * @param data The data.
     * @param index The index of the value in the data.
     * @param limits The limits.
     * @param format The display format.
     * @param sliderFlags Slider flags.
     * @param outGrabBB Where to store the grab bounding box.
     * @return True if the value changed.
     */
    static boolean sliderBehavior(
            @NonNull RectFloat bb,
            int id,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int index,
            @NonNull Limits limits,
            @NonNull String format,
            int sliderFlags,
            @NonNull RectFloat outGrabBB) {
        if ((sliderFlags & SliderFlags.WRAP_AROUND) != 0) {
            log.warn("WRAP_AROUND is only supported by drags");
        }
        if (context.activeID == id) {
            if (context.activeIDSource == GuiInputSource.MOUSE
                    && !context.io.mouseDown[MouseButton.LEFT.index]) {
                IkGuiInternal.clearActiveID();
            }
            if (context.activeIDSource == GuiInputSource.KEYBOARD
                    || context.activeIDSource == GuiInputSource.GAMEPAD) {
                if (context.activeIDIsJustActivated) {
                    // Reset any stored navigation delta upon activation
                    context.sliderCurrentAccumulatedDelta = 0.0f;
                    context.sliderCurrentAccumulatedDeltaDirty = false;
                }
                if (context.navActivatePressedID == id && !context.activeIDIsJustActivated) {
                    IkGuiInternal.clearActiveID();
                }
            }
            if (context.activeID == id && shortcutsForCancel(id)) {
                // Canceling reverts to the initial value
                return revertToValueOnActivation(dataType, data, index);
            }
        }

        final StyleVariables style = context.style.variable;
        final boolean vertical = (sliderFlags & SliderFlags.INTERNAL_VERTICAL) != 0;
        final boolean isLogarithmic = (sliderFlags & SliderFlags.LOGARITHMIC) != 0;
        final boolean isFloatingPoint = dataType.isFloatingPoint();
        // We don't need high precision for what we do with this
        final float rangeF = (float) Math.abs(limits.range());

        // Calculate bounds
        final float bbMin = vertical ? bb.getTop() : bb.getLeft();
        final float bbMax = vertical ? bb.getBottom() : bb.getRight();
        final float sliderSize = (bbMax - bbMin) - GRAB_PADDING * 2.0f;
        float grabSize = style.grabMinSize;
        if (!isFloatingPoint && rangeF >= 0.0f) {
            // For integer sliders, if possible have the grab size represent 1 unit
            grabSize = Math.max(sliderSize / (rangeF + 1), style.grabMinSize);
        }
        grabSize = Math.min(grabSize, sliderSize);
        final float sliderUsableSize = sliderSize - grabSize;
        final float sliderUsablePosMin = bbMin + GRAB_PADDING + grabSize * 0.5f;
        final float sliderUsablePosMax = bbMax - GRAB_PADDING - grabSize * 0.5f;

        float logarithmicZeroEpsilon = 0.0f;
        float zeroDeadzoneHalfSize = 0.0f;
        if (isLogarithmic) {
            // When using logarithmic sliders, we need to clamp to avoid hitting zero, but our
            // choice of clamp value greatly affects precision. We use the format precision to
            // estimate a good lower bound.
            final int decimalPrecision = isFloatingPoint ? parseFormatPrecision(format, 3) : 1;
            logarithmicZeroEpsilon = (float) Math.pow(0.1, decimalPrecision);
            zeroDeadzoneHalfSize =
                    (style.logSliderDeadzone * 0.5f) / Math.max(sliderUsableSize, 1.0f);
        }

        // Process interacting with the slider
        boolean valueChanged = false;
        if (context.activeID == id) {
            boolean setNewValue = false;
            float clickedT = 0.0f;
            if (context.activeIDSource == GuiInputSource.MOUSE) {
                final float mousePosition =
                        vertical ? context.io.mousePosition.y : context.io.mousePosition.x;
                if (context.activeIDIsJustActivated) {
                    float grabT =
                            ratioOfValue(
                                    dataType,
                                    data,
                                    index,
                                    limits,
                                    logarithmicZeroEpsilon,
                                    zeroDeadzoneHalfSize);
                    if (vertical) {
                        grabT = 1.0f - grabT;
                    }
                    final float grabPosition =
                            sliderUsablePosMin + (sliderUsablePosMax - sliderUsablePosMin) * grabT;
                    // No harm being extra generous here
                    final boolean clickedAroundGrab =
                            mousePosition >= grabPosition - grabSize * 0.5f - 1.0f
                                    && mousePosition <= grabPosition + grabSize * 0.5f + 1.0f;
                    context.sliderGrabClickOffset =
                            (clickedAroundGrab && isFloatingPoint)
                                    ? mousePosition - grabPosition
                                    : 0.0f;
                }
                if (sliderUsableSize > 0.0f) {
                    clickedT =
                            MathUtil.clamp(
                                    (mousePosition
                                                    - context.sliderGrabClickOffset
                                                    - sliderUsablePosMin)
                                            / sliderUsableSize,
                                    0.0f,
                                    1.0f);
                }
                if (vertical) {
                    clickedT = 1.0f - clickedT;
                }
                setNewValue = true;
            } else if (context.activeIDSource == GuiInputSource.KEYBOARD
                    || context.activeIDSource == GuiInputSource.GAMEPAD) {
                float inputDelta =
                        vertical
                                ? -IkGuiImplNav.getNavTweakPressedAmount(Axis.Y)
                                : IkGuiImplNav.getNavTweakPressedAmount(Axis.X);
                if (inputDelta != 0.0f) {
                    final boolean tweakSlow = isNavTweakSlowDown();
                    final boolean tweakFast = isNavTweakFastDown();
                    final int decimalPrecision =
                            isFloatingPoint ? parseFormatPrecision(format, 3) : 0;
                    if (decimalPrecision > 0) {
                        // Tweak speeds in % of the slider bounds
                        inputDelta /= 100.0f;
                        if (tweakSlow) {
                            inputDelta /= 10.0f;
                        }
                    } else if ((rangeF >= -100.0f && rangeF <= 100.0f && rangeF != 0.0f)
                            || tweakSlow) {
                        // Tweak speeds in integer steps
                        inputDelta = (inputDelta < 0.0f ? -1.0f : 1.0f) / rangeF;
                    } else {
                        inputDelta /= 100.0f;
                    }
                    if (tweakFast) {
                        inputDelta *= 10.0f;
                    }
                    context.sliderCurrentAccumulatedDelta += inputDelta;
                    context.sliderCurrentAccumulatedDeltaDirty = true;
                }

                final float delta = context.sliderCurrentAccumulatedDelta;
                if (context.sliderCurrentAccumulatedDeltaDirty) {
                    clickedT =
                            ratioOfValue(
                                    dataType,
                                    data,
                                    index,
                                    limits,
                                    logarithmicZeroEpsilon,
                                    zeroDeadzoneHalfSize);

                    if ((clickedT >= 1.0f && delta > 0.0f) || (clickedT <= 0.0f && delta < 0.0f)) {
                        // Avoid applying the saturation when already past the limits, and don't
                        // continue to accumulate when pushing against them
                        context.sliderCurrentAccumulatedDelta = 0.0f;
                    } else {
                        setNewValue = true;
                        final float oldClickedT = clickedT;
                        clickedT = MathUtil.clamp(clickedT + delta, 0.0f, 1.0f);

                        // Calculate how far we actually moved the slider, and subtract this from
                        // the accumulator
                        final float newClickedT =
                                ratioAfterRounding(
                                        dataType,
                                        clickedT,
                                        limits,
                                        format,
                                        sliderFlags,
                                        logarithmicZeroEpsilon,
                                        zeroDeadzoneHalfSize);
                        if (delta > 0) {
                            context.sliderCurrentAccumulatedDelta -=
                                    Math.min(newClickedT - oldClickedT, delta);
                        } else {
                            context.sliderCurrentAccumulatedDelta -=
                                    Math.max(newClickedT - oldClickedT, delta);
                        }
                    }
                    context.sliderCurrentAccumulatedDeltaDirty = false;
                }
            }

            final boolean readOnly =
                    (context.lastItemData.itemFlags & ItemFlags.READ_ONLY) != 0
                            || (sliderFlags & SliderFlags.INTERNAL_READ_ONLY) != 0;
            if (setNewValue && !readOnly) {
                valueChanged =
                        setValueFromRatio(
                                dataType,
                                data,
                                index,
                                clickedT,
                                limits,
                                format,
                                sliderFlags,
                                logarithmicZeroEpsilon,
                                zeroDeadzoneHalfSize);
            }
        }

        if (sliderSize < 1.0f) {
            outGrabBB.set(bb.getLeft(), bb.getTop(), bb.getLeft(), bb.getTop());
        } else {
            // Output the grab position so it can be displayed by the caller
            float grabT =
                    ratioOfValue(
                            dataType,
                            data,
                            index,
                            limits,
                            logarithmicZeroEpsilon,
                            zeroDeadzoneHalfSize);
            if (vertical) {
                grabT = 1.0f - grabT;
            }
            final float grabPosition =
                    sliderUsablePosMin + (sliderUsablePosMax - sliderUsablePosMin) * grabT;
            if (!vertical) {
                outGrabBB.set(
                        grabPosition - grabSize * 0.5f,
                        bb.getTop() + GRAB_PADDING,
                        grabPosition + grabSize * 0.5f,
                        bb.getBottom() - GRAB_PADDING);
            } else {
                outGrabBB.set(
                        bb.getLeft() + GRAB_PADDING,
                        grabPosition - grabSize * 0.5f,
                        bb.getRight() - GRAB_PADDING,
                        grabPosition + grabSize * 0.5f);
            }
        }

        return valueChanged;
    }

    /**
     * Draw the grab of a slider.
     *
     * @param window The window.
     * @param id The ID of the slider.
     * @param grabBB The grab bounding box.
     */
    private static void renderGrab(@NonNull Window window, int id, @NonNull RectFloat grabBB) {
        window.drawList.addRectFilled(
                grabBB.getLeft(),
                grabBB.getTop(),
                grabBB.getRight(),
                grabBB.getBottom(),
                IkGuiImplUtils.getColorWithGlobalAlpha(
                        context.activeID == id
                                ? ColorType.SLIDER_GRAB_ACTIVE
                                : ColorType.SLIDER_GRAB),
                context.style.variable.grabRounding);
    }

    /**
     * A slider for a single value of any type.
     *
     * @param label The label, which is also used for the ID.
     * @param dataType The data type.
     * @param data An array of the data type, or one of the Ik* boxes.
     * @param min The minimum value, which is required.
     * @param max The maximum value, which is required.
     * @param format The display format, null for the default for the data type.
     * @param sliderFlags Slider flags.
     * @return True if the value changed.
     */
    public static boolean sliderScalar(
            @NonNull String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            Number min,
            Number max,
            String format,
            int sliderFlags) {
        return sliderScalarIndex(label, dataType, data, 0, min, max, format, sliderFlags);
    }

    /**
     * A slider for one value out of a data array.
     *
     * @param label The label, which is also used for the ID.
     * @param dataType The data type.
     * @param data An array of the data type, or one of the Ik* boxes.
     * @param index The index of the value in the data.
     * @param min The minimum value, which is required.
     * @param max The maximum value, which is required.
     * @param format The display format, null for the default for the data type.
     * @param sliderFlags Slider flags.
     * @return True if the value changed.
     */
    static boolean sliderScalarIndex(
            @NonNull String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int index,
            Number min,
            Number max,
            String format,
            int sliderFlags) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        if (!validateData(dataType, data, index + 1)) {
            return false;
        }
        final Limits limits = sliderLimits(dataType, min, max);
        if (limits == null) {
            return false;
        }

        final StyleVariables style = context.style.variable;
        final int id = window.getID(label);
        final float width = IkGuiImplUtils.calculateItemWidth();
        final int colorMarker =
                (context.nextItemData.fieldFlags & NextItemFlags.HAS_COLOR_MARKER) != 0
                        ? context.nextItemData.colorMarker
                        : 0;

        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, label, true, -1.0f);
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat frameBB =
                new RectFloat(x, y, x + width, y + labelSize.y + style.framePadding.y * 2.0f);
        final RectFloat totalBB =
                new RectFloat(
                        x,
                        y,
                        frameBB.getRight()
                                + (labelSize.x > 0.0f
                                        ? style.itemInnerSpacing.x + labelSize.x
                                        : 0.0f),
                        frameBB.getBottom());

        IkGuiInternal.itemSize(totalBB, style.framePadding.y);
        final boolean tempInputAllowed = (sliderFlags & SliderFlags.NO_INPUT) == 0;
        if (!IkGuiInternal.itemAdd(
                totalBB,
                id,
                frameBB,
                tempInputAllowed ? ItemFlags.INTERNAL_INPUTABLE : ItemFlags.NONE)) {
            return false;
        }

        format = formatOrDefault(dataType, format);

        final boolean hovered =
                IkGuiInternal.itemHoverable(frameBB, id, context.lastItemData.itemFlags);
        final boolean tempInputActive =
                activateOnClick(
                        window,
                        id,
                        hovered,
                        dataType,
                        data,
                        index,
                        (sliderFlags & SliderFlags.NO_INPUT) == 0,
                        false,
                        false);
        if (tempInputActive) {
            // Only clamp Ctrl+Click input when CLAMP_ON_INPUT is set
            final boolean clampEnabled = (sliderFlags & SliderFlags.CLAMP_ON_INPUT) != 0;
            return IkGuiImplInputText.tempInputScalar(
                    frameBB,
                    id,
                    label,
                    dataType,
                    data,
                    index,
                    format,
                    clampEnabled ? min : null,
                    clampEnabled ? max : null);
        }

        renderScalarFrame(frameBB, id, hovered, colorMarker);

        // Slider behavior
        final RectFloat grabBB = new RectFloat(0, 0, 0, 0);
        final boolean valueChanged =
                sliderBehavior(
                        frameBB, id, dataType, data, index, limits, format, sliderFlags, grabBB);
        if (valueChanged) {
            IkGuiInternal.markItemEdited(id);
        }

        // Render the grab
        if (grabBB.getRight() > grabBB.getLeft()) {
            renderGrab(window, id, grabBB);
        }

        // Display the value using the user-provided display format so the user can add
        // prefix/suffix/decorations to the value
        final String valueText = formatScalar(dataType, readNumber(dataType, data, index), format);
        renderValueAndLabel(frameBB, label, labelSize, valueText);

        IkGuiInternal.testEngineItemInfo(
                id,
                label,
                context.lastItemData.statusFlags
                        | (tempInputAllowed ? ItemStatusFlags.INPUTABLE : 0));
        return valueChanged;
    }

    /**
     * Multiple sliders on one line, for editing multiple components.
     *
     * @param label The label, which is also used for the ID.
     * @param dataType The data type.
     * @param data An array of the data type.
     * @param components The number of components.
     * @param min The minimum value, which is required.
     * @param max The maximum value, which is required.
     * @param format The display format, null for the default for the data type.
     * @param sliderFlags Slider flags.
     * @return True if any value changed.
     */
    public static boolean sliderScalarN(
            @NonNull String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            Number min,
            Number max,
            String format,
            int sliderFlags) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        if (!validateData(dataType, data, components)) {
            return false;
        }

        boolean valueChanged = false;
        IkGuiImplLayout.beginGroup();
        IkGuiImplUtils.pushID(label);
        IkGuiImplLayout.pushMultiItemsWidths(components, IkGuiImplUtils.calculateItemWidth());
        for (int i = 0; i < components; ++i) {
            IkGuiImplUtils.pushID(i);
            if (i > 0) {
                IkGuiImplLayout.sameLine(0, context.style.variable.itemInnerSpacing.x);
            }
            if ((sliderFlags & SliderFlags.COLOR_MARKERS) != 0) {
                setNextItemColorMarker(DEFAULT_RGBA_COLOR_MARKERS[i % 4]);
            }
            valueChanged |= sliderScalarIndex("", dataType, data, i, min, max, format, sliderFlags);
            IkGuiImplUtils.popID();
            IkGuiImplLayout.popItemWidth();
        }
        IkGuiImplUtils.popID();

        renderGroupLabel(label);
        IkGuiImplLayout.endGroup();
        return valueChanged;
    }

    /**
     * Two sliders for editing a range, where the min can't go above the max and vice versa. This is
     * not part of Dear ImGui, but mirrors dragFloatRange2()/dragIntRange2().
     *
     * @param label The label, which is also used for the ID.
     * @param dataType The data type.
     * @param currentMin The current minimum of the range.
     * @param currentMax The current maximum of the range.
     * @param min The minimum value.
     * @param max The maximum value.
     * @param format The display format.
     * @param formatMax The display format for the max, null to use the same format.
     * @param sliderFlags Slider flags.
     * @return True if either value changed.
     */
    static boolean sliderRange2(
            @NonNull String label,
            @NonNull SliderDataType dataType,
            @NonNull Object currentMin,
            @NonNull Object currentMax,
            @NonNull Number min,
            @NonNull Number max,
            String format,
            String formatMax,
            int sliderFlags) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        if (!validateData(dataType, currentMin, 1) || !validateData(dataType, currentMax, 1)) {
            return false;
        }

        IkGuiImplUtils.pushID(label);
        IkGuiImplLayout.beginGroup();
        IkGuiImplLayout.pushMultiItemsWidths(2, IkGuiImplUtils.calculateItemWidth());

        // The min slider covers min..currentMax, the max slider covers currentMin..max
        final Number minMax = minNumber(dataType, max, readNumber(dataType, currentMax, 0));
        final int minFlags =
                sliderFlags
                        | (compareNumbers(dataType, min, minMax) == 0
                                ? SliderFlags.INTERNAL_READ_ONLY
                                : 0);
        boolean valueChanged =
                sliderScalar("##min", dataType, currentMin, min, minMax, format, minFlags);
        IkGuiImplLayout.popItemWidth();
        IkGuiImplLayout.sameLine(0, context.style.variable.itemInnerSpacing.x);

        final Number maxMin = maxNumber(dataType, min, readNumber(dataType, currentMin, 0));
        final int maxFlags =
                sliderFlags
                        | (compareNumbers(dataType, maxMin, max) == 0
                                ? SliderFlags.INTERNAL_READ_ONLY
                                : 0);
        valueChanged |=
                sliderScalar(
                        "##max",
                        dataType,
                        currentMax,
                        maxMin,
                        max,
                        formatMax != null ? formatMax : format,
                        maxFlags);
        IkGuiImplLayout.popItemWidth();
        IkGuiImplLayout.sameLine(0, context.style.variable.itemInnerSpacing.x);

        IkGuiImplText.textEx(Hash.getDisplayedText(label));
        IkGuiImplLayout.endGroup();
        IkGuiImplUtils.popID();
        return valueChanged;
    }

    /**
     * A slider for an angle, stored in radians but displayed in degrees.
     *
     * @param label The label, which is also used for the ID.
     * @param valueRadians The angle in radians, in a float array.
     * @param minDegrees The minimum angle, in degrees.
     * @param maxDegrees The maximum angle, in degrees.
     * @param format The display format, null for the default ("%.0f deg").
     * @param sliderFlags Slider flags.
     * @return True if the value changed.
     */
    public static boolean sliderAngle(
            @NonNull String label,
            float @NonNull [] valueRadians,
            float minDegrees,
            float maxDegrees,
            String format,
            int sliderFlags) {
        if (format == null) {
            format = SLIDER_ANGLE_DEFAULT_FORMAT;
        }
        final float[] degrees = {(float) Math.toDegrees(valueRadians[0])};
        final boolean valueChanged =
                sliderScalar(
                        label,
                        SliderDataType.FLOAT,
                        degrees,
                        minDegrees,
                        maxDegrees,
                        format,
                        sliderFlags);
        if (valueChanged) {
            valueRadians[0] = (float) Math.toRadians(degrees[0]);
        }
        return valueChanged;
    }

    /**
     * A vertical slider for a single value of any type.
     *
     * @param label The label, which is also used for the ID.
     * @param width The width of the slider.
     * @param height The height of the slider.
     * @param dataType The data type.
     * @param data An array of the data type, or one of the Ik* boxes.
     * @param min The minimum value.
     * @param max The maximum value.
     * @param format The display format, null for the default for the data type.
     * @param sliderFlags Slider flags.
     * @return True if the value changed.
     */
    public static boolean vSliderScalar(
            @NonNull String label,
            float width,
            float height,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            Number min,
            Number max,
            String format,
            int sliderFlags) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        if (!validateData(dataType, data, 1)) {
            return false;
        }
        final Limits limits = sliderLimits(dataType, min, max);
        if (limits == null) {
            return false;
        }

        final StyleVariables style = context.style.variable;
        final int id = window.getID(label);

        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, label, true, -1.0f);
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat frameBB = new RectFloat(x, y, x + width, y + height);
        final RectFloat bb =
                new RectFloat(
                        x,
                        y,
                        frameBB.getRight()
                                + (labelSize.x > 0.0f
                                        ? style.itemInnerSpacing.x + labelSize.x
                                        : 0.0f),
                        frameBB.getBottom());

        IkGuiInternal.itemSize(bb, style.framePadding.y);
        if (!IkGuiInternal.itemAdd(frameBB, id)) {
            return false;
        }

        format = formatOrDefault(dataType, format);

        final boolean hovered =
                IkGuiInternal.itemHoverable(frameBB, id, context.lastItemData.itemFlags);
        activateOnClick(window, id, hovered, dataType, data, 0, false, false, true);

        // Draw the frame
        IkGuiImplNav.renderNavCursor(frameBB, id, NavRenderCursorFlags.NONE, -1.0f);
        final ColorType frameColor;
        if (context.activeID == id) {
            frameColor = ColorType.FRAME_BACKGROUND_ACTIVE;
        } else if (hovered) {
            frameColor = ColorType.FRAME_BACKGROUND_HOVERED;
        } else {
            frameColor = ColorType.FRAME_BACKGROUND;
        }
        IkGuiInternal.renderFrame(
                frameBB.getLeft(),
                frameBB.getTop(),
                frameBB.getRight(),
                frameBB.getBottom(),
                IkGuiImplUtils.getColorWithGlobalAlpha(frameColor),
                true,
                style.frameRounding);

        // Slider behavior
        final RectFloat grabBB = new RectFloat(0, 0, 0, 0);
        final boolean valueChanged =
                sliderBehavior(
                        frameBB,
                        id,
                        dataType,
                        data,
                        0,
                        limits,
                        format,
                        sliderFlags | SliderFlags.INTERNAL_VERTICAL,
                        grabBB);
        if (valueChanged) {
            IkGuiInternal.markItemEdited(id);
        }

        // Render the grab
        if (grabBB.getBottom() > grabBB.getTop()) {
            renderGrab(window, id, grabBB);
        }

        // Display the value. For the vertical slider we allow centered text to overlap the frame
        // padding.
        final boolean isMixed = (context.lastItemData.itemFlags & ItemFlags.MIXED_VALUE) != 0;
        final String valueText =
                isMixed && context.mixedValueLabel != null
                        ? context.mixedValueLabel
                        : formatScalar(dataType, readNumber(dataType, data, 0), format);
        if (isMixed) {
            IkGuiImplUtils.pushStyleColor(
                    ColorType.TEXT, IkGuiImplUtils.getColor(ColorType.TEXT_DISABLED));
        }
        IkGuiInternal.renderTextClipped(
                frameBB.getLeft(),
                frameBB.getTop() + style.framePadding.y,
                frameBB.getRight(),
                frameBB.getBottom(),
                valueText,
                null,
                0.5f,
                0.0f,
                null);
        if (isMixed) {
            IkGuiImplUtils.popStyleColor();
        }
        if (labelSize.x > 0.0f) {
            IkGuiInternal.renderText(
                    frameBB.getRight() + style.itemInnerSpacing.x,
                    frameBB.getTop() + style.framePadding.y,
                    Hash.getDisplayedText(label),
                    false);
        }

        IkGuiInternal.testEngineItemInfo(id, label, context.lastItemData.statusFlags);
        return valueChanged;
    }

    // ---------------------------------------------------------------------------------------
    // Typed helpers
    // ---------------------------------------------------------------------------------------

    /**
     * A drag widget for a single float.
     *
     * @return True if the value changed.
     */
    public static boolean dragFloat(
            String label,
            float[] value,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return dragScalar(label, SliderDataType.FLOAT, value, speed, min, max, format, sliderFlags);
    }

    /**
     * A drag widget for 2 floats.
     *
     * @return True if the value changed.
     */
    public static boolean dragFloat2(
            String label,
            float[] values,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return dragScalarN(
                label, SliderDataType.FLOAT, values, 2, speed, min, max, format, sliderFlags);
    }

    /**
     * A drag widget for 3 floats.
     *
     * @return True if the value changed.
     */
    public static boolean dragFloat3(
            String label,
            float[] values,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return dragScalarN(
                label, SliderDataType.FLOAT, values, 3, speed, min, max, format, sliderFlags);
    }

    /**
     * A drag widget for 4 floats.
     *
     * @return True if the value changed.
     */
    public static boolean dragFloat4(
            String label,
            float[] values,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return dragScalarN(
                label, SliderDataType.FLOAT, values, 4, speed, min, max, format, sliderFlags);
    }

    /**
     * A drag widget for a range of two floats.
     *
     * @return True if the value changed.
     */
    public static boolean dragFloatRange2(
            String label,
            float[] currentMin,
            float[] currentMax,
            float speed,
            float min,
            float max,
            String format,
            String formatMax,
            int sliderFlags) {
        return dragRange2(
                label,
                SliderDataType.FLOAT,
                currentMin,
                currentMax,
                speed,
                min,
                max,
                format,
                formatMax,
                sliderFlags);
    }

    /**
     * A drag widget for a single int.
     *
     * @return True if the value changed.
     */
    public static boolean dragInt(
            String label,
            int[] value,
            float speed,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return dragScalar(label, SliderDataType.INT, value, speed, min, max, format, sliderFlags);
    }

    /**
     * A drag widget for 2 ints.
     *
     * @return True if the value changed.
     */
    public static boolean dragInt2(
            String label,
            int[] values,
            float speed,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return dragScalarN(
                label, SliderDataType.INT, values, 2, speed, min, max, format, sliderFlags);
    }

    /**
     * A drag widget for 3 ints.
     *
     * @return True if the value changed.
     */
    public static boolean dragInt3(
            String label,
            int[] values,
            float speed,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return dragScalarN(
                label, SliderDataType.INT, values, 3, speed, min, max, format, sliderFlags);
    }

    /**
     * A drag widget for 4 ints.
     *
     * @return True if the value changed.
     */
    public static boolean dragInt4(
            String label,
            int[] values,
            float speed,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return dragScalarN(
                label, SliderDataType.INT, values, 4, speed, min, max, format, sliderFlags);
    }

    /**
     * A drag widget for a range of two ints.
     *
     * @return True if the value changed.
     */
    public static boolean dragIntRange2(
            String label,
            int[] currentMin,
            int[] currentMax,
            float speed,
            int min,
            int max,
            String format,
            String formatMax,
            int sliderFlags) {
        return dragRange2(
                label,
                SliderDataType.INT,
                currentMin,
                currentMax,
                speed,
                min,
                max,
                format,
                formatMax,
                sliderFlags);
    }

    /**
     * A slider for a single float.
     *
     * @return True if the value changed.
     */
    public static boolean sliderFloat(
            String label, float[] value, float min, float max, String format, int sliderFlags) {
        return sliderScalar(label, SliderDataType.FLOAT, value, min, max, format, sliderFlags);
    }

    /**
     * A slider for 2 floats.
     *
     * @return True if the value changed.
     */
    public static boolean sliderFloat2(
            String label, float[] values, float min, float max, String format, int sliderFlags) {
        return sliderScalarN(label, SliderDataType.FLOAT, values, 2, min, max, format, sliderFlags);
    }

    /**
     * A slider for 3 floats.
     *
     * @return True if the value changed.
     */
    public static boolean sliderFloat3(
            String label, float[] values, float min, float max, String format, int sliderFlags) {
        return sliderScalarN(label, SliderDataType.FLOAT, values, 3, min, max, format, sliderFlags);
    }

    /**
     * A slider for 4 floats.
     *
     * @return True if the value changed.
     */
    public static boolean sliderFloat4(
            String label, float[] values, float min, float max, String format, int sliderFlags) {
        return sliderScalarN(label, SliderDataType.FLOAT, values, 4, min, max, format, sliderFlags);
    }

    /**
     * A slider for a range of two floats.
     *
     * @return True if the value changed.
     */
    public static boolean sliderFloatRange2(
            String label,
            float[] currentMin,
            float[] currentMax,
            float min,
            float max,
            String format,
            String formatMax,
            int sliderFlags) {
        return sliderRange2(
                label,
                SliderDataType.FLOAT,
                currentMin,
                currentMax,
                min,
                max,
                format,
                formatMax,
                sliderFlags);
    }

    /**
     * A slider for a single int.
     *
     * @return True if the value changed.
     */
    public static boolean sliderInt(
            String label, int[] value, int min, int max, String format, int sliderFlags) {
        return sliderScalar(label, SliderDataType.INT, value, min, max, format, sliderFlags);
    }

    /**
     * A slider for 2 ints.
     *
     * @return True if the value changed.
     */
    public static boolean sliderInt2(
            String label, int[] values, int min, int max, String format, int sliderFlags) {
        return sliderScalarN(label, SliderDataType.INT, values, 2, min, max, format, sliderFlags);
    }

    /**
     * A slider for 3 ints.
     *
     * @return True if the value changed.
     */
    public static boolean sliderInt3(
            String label, int[] values, int min, int max, String format, int sliderFlags) {
        return sliderScalarN(label, SliderDataType.INT, values, 3, min, max, format, sliderFlags);
    }

    /**
     * A slider for 4 ints.
     *
     * @return True if the value changed.
     */
    public static boolean sliderInt4(
            String label, int[] values, int min, int max, String format, int sliderFlags) {
        return sliderScalarN(label, SliderDataType.INT, values, 4, min, max, format, sliderFlags);
    }

    /**
     * A slider for a range of two ints.
     *
     * @return True if the value changed.
     */
    public static boolean sliderIntRange2(
            String label,
            int[] currentMin,
            int[] currentMax,
            int min,
            int max,
            String format,
            String formatMax,
            int sliderFlags) {
        return sliderRange2(
                label,
                SliderDataType.INT,
                currentMin,
                currentMax,
                min,
                max,
                format,
                formatMax,
                sliderFlags);
    }

    /**
     * A vertical slider for a single float.
     *
     * @return True if the value changed.
     */
    public static boolean vSliderFloat(
            String label,
            float width,
            float height,
            float[] value,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return vSliderScalar(
                label, width, height, SliderDataType.FLOAT, value, min, max, format, sliderFlags);
    }

    /**
     * A vertical slider for a single int.
     *
     * @return True if the value changed.
     */
    public static boolean vSliderInt(
            String label,
            float width,
            float height,
            int[] value,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return vSliderScalar(
                label, width, height, SliderDataType.INT, value, min, max, format, sliderFlags);
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplSliders() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
