package com.ikalagaming.converter.texgraph;

/**
 * Converts between sRGB values as image files store them and the linear values graphs compute with.
 * Decoding an 8-bit value and encoding it again gives the same byte back.
 */
public final class Srgb {
    /** The largest 8-bit value. */
    public static final int MAX_BYTE = 255;

    /** Linear values of each 8-bit sRGB value. */
    private static final float[] TO_LINEAR = new float[MAX_BYTE + 1];

    static {
        for (int i = 0; i <= MAX_BYTE; ++i) {
            TO_LINEAR[i] = (float) toLinear(i / (double) MAX_BYTE);
        }
    }

    /** Not made. */
    private Srgb() {}

    /**
     * The linear value of a stored 8-bit sRGB value.
     *
     * @param value The byte, 0 to 255.
     * @return The linear value, 0 to 1.
     */
    public static float decode(int value) {
        return TO_LINEAR[value];
    }

    /**
     * The 8-bit sRGB value storing a linear value, clamped to 0 to 1.
     *
     * @param linear The linear value.
     * @return The byte, 0 to 255.
     */
    public static int encode(float linear) {
        final double clamped = Math.clamp(linear, 0, 1);
        return (int) Math.round(fromLinear(clamped) * MAX_BYTE);
    }

    /**
     * sRGB's transfer function, encoded to linear.
     *
     * @param encoded The encoded value, 0 to 1.
     * @return The linear value.
     */
    static double toLinear(double encoded) {
        return encoded <= 0.040_45 ? encoded / 12.92 : Math.pow((encoded + 0.055) / 1.055, 2.4);
    }

    /**
     * sRGB's transfer function, linear to encoded.
     *
     * @param linear The linear value, 0 to 1.
     * @return The encoded value.
     */
    static double fromLinear(double linear) {
        return linear <= 0.003_130_8 ? linear * 12.92 : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
    }
}
