package com.ikalagaming.characters.data;

import lombok.NonNull;

/**
 * The colors a design can hold, as 16-bit codes: 6 bits of hue, 4 of saturation and 6 of value
 * (brightness), so 64 hues, 16 saturations and 64 brightnesses. Steps in HSV look about even, so
 * dark and pale colors keep their detail, and a design's colors fit in two bytes each. Editors snap
 * picked colors to these codes, and network encoding sends the codes.
 *
 * <p>Codes are laid out as {@code hhhhhh ssss vvvvvv}, high bits first. Grays have no hue, so with
 * saturation 0 the hue bits make no difference; {@link #encode} writes them as 0.
 *
 * @author Ches Burks
 */
public final class ColorCode {
    /** Hue steps. */
    public static final int HUES = 64;

    /** Saturation steps. */
    public static final int SATURATIONS = 16;

    /** Value (brightness) steps. */
    public static final int VALUES = 64;

    /** Bits for the saturation. */
    private static final int SATURATION_BITS = 4;

    /** Bits for the value. */
    private static final int VALUE_BITS = 6;

    /** The most a channel holds, as a float. */
    private static final float CHANNEL_MAX = 255;

    /**
     * Make a code from its steps.
     *
     * @param hue The hue step, 0 to 63, around from red.
     * @param saturation The saturation step, 0 (gray) to 15.
     * @param value The value step, 0 (black) to 63.
     * @return The code.
     * @throws IllegalArgumentException If a step is out of range.
     */
    public static int of(int hue, int saturation, int value) {
        if (hue < 0
                || hue >= HUES
                || saturation < 0
                || saturation >= SATURATIONS
                || value < 0
                || value >= VALUES) {
            throw new IllegalArgumentException(
                    "Color steps go 0-63 for hue, 0-15 for saturation, 0-63 for value");
        }
        int keptHue = saturation == 0 ? 0 : hue;
        return (keptHue << (SATURATION_BITS + VALUE_BITS)) | (saturation << VALUE_BITS) | value;
    }

    /**
     * A code's hue step.
     *
     * @param code The code.
     * @return The hue step, 0 to 63.
     */
    public static int hue(int code) {
        return (code >> (SATURATION_BITS + VALUE_BITS)) & (HUES - 1);
    }

    /**
     * A code's saturation step.
     *
     * @param code The code.
     * @return The saturation step, 0 to 15.
     */
    public static int saturation(int code) {
        return (code >> VALUE_BITS) & (SATURATIONS - 1);
    }

    /**
     * A code's value step.
     *
     * @param code The code.
     * @return The value step, 0 to 63.
     */
    public static int value(int code) {
        return code & (VALUES - 1);
    }

    /**
     * The nearest code to an sRGB color.
     *
     * @param red Red, 0 to 1.
     * @param green Green, 0 to 1.
     * @param blue Blue, 0 to 1.
     * @return The code.
     */
    public static int encode(float red, float green, float blue) {
        float r = Math.clamp(red, 0, 1);
        float g = Math.clamp(green, 0, 1);
        float b = Math.clamp(blue, 0, 1);
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float chroma = max - min;
        float hue = 0;
        if (chroma > 0) {
            if (max == r) {
                hue = ((g - b) / chroma) / 6;
            } else if (max == g) {
                hue = ((b - r) / chroma + 2) / 6;
            } else {
                hue = ((r - g) / chroma + 4) / 6;
            }
            hue -= (float) Math.floor(hue);
        }
        float saturation = max > 0 ? chroma / max : 0;
        int hueStep = Math.round(hue * HUES) % HUES;
        int saturationStep = Math.round(saturation * (SATURATIONS - 1));
        int valueStep = Math.round(max * (VALUES - 1));
        return of(hueStep, saturationStep, valueStep);
    }

    /**
     * The nearest code to a color.
     *
     * @param color The color.
     * @return The code.
     */
    public static int encode(@NonNull Color color) {
        return encode(
                color.red() / CHANNEL_MAX, color.green() / CHANNEL_MAX, color.blue() / CHANNEL_MAX);
    }

    /**
     * The sRGB color a code stands for, as floats.
     *
     * @param code The code.
     * @param out Where to put red, green and blue, 0 to 1.
     * @return The output.
     */
    public static float[] decode(int code, float @NonNull [] out) {
        float hue = hue(code) / (float) HUES * 6;
        float saturation = saturation(code) / (float) (SATURATIONS - 1);
        float value = value(code) / (float) (VALUES - 1);
        float chroma = value * saturation;
        float x = chroma * (1 - Math.abs(hue % 2 - 1));
        float m = value - chroma;
        float r;
        float g;
        float b;
        switch ((int) hue) {
            case 0 -> {
                r = chroma;
                g = x;
                b = 0;
            }
            case 1 -> {
                r = x;
                g = chroma;
                b = 0;
            }
            case 2 -> {
                r = 0;
                g = chroma;
                b = x;
            }
            case 3 -> {
                r = 0;
                g = x;
                b = chroma;
            }
            case 4 -> {
                r = x;
                g = 0;
                b = chroma;
            }
            default -> {
                r = chroma;
                g = 0;
                b = x;
            }
        }
        out[0] = r + m;
        out[1] = g + m;
        out[2] = b + m;
        return out;
    }

    /**
     * The color a code stands for.
     *
     * @param code The code.
     * @return The color.
     */
    public static Color toColor(int code) {
        float[] rgb = decode(code, new float[3]);
        return new Color(
                Math.round(rgb[0] * CHANNEL_MAX),
                Math.round(rgb[1] * CHANNEL_MAX),
                Math.round(rgb[2] * CHANNEL_MAX));
    }

    /** Static methods only. */
    private ColorCode() {}
}
