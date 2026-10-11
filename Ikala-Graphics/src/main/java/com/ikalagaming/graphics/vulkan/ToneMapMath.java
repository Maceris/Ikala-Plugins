package com.ikalagaming.graphics.vulkan;

import lombok.NonNull;
import org.joml.Vector3f;

/**
 * The color math between linear light and the image shown, as {@code tonemap.frag} and the shaders
 * that decode sRGB colors do it; keep them in step with this.
 */
public final class ToneMapMath {

    /** Where Khronos PBR Neutral starts compressing highlights. */
    public static final float START_COMPRESSION = 0.8f - 0.04f;

    /** How much PBR Neutral desaturates highlights toward white. */
    public static final float DESATURATION = 0.15f;

    /**
     * Decode one sRGB channel to linear light, with the exact piecewise curve.
     *
     * @param value The sRGB value, from 0 to 1.
     * @return The linear value.
     */
    public static float decode(float value) {
        final float c = Math.clamp(value, 0.0f, 1.0f);
        return c <= 0.040_45f ? c / 12.92f : (float) Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /**
     * Encode one channel of linear light to sRGB, the inverse of {@link #decode(float)}.
     *
     * @param value The linear value, from 0 to 1.
     * @return The sRGB value.
     */
    public static float encode(float value) {
        final float c = Math.clamp(value, 0.0f, 1.0f);
        return c <= 0.003_130_8f ? c * 12.92f : (float) (1.055 * Math.pow(c, 1 / 2.4) - 0.055);
    }

    /**
     * Khronos PBR Neutral tone mapping: colors keep their hue and stay as they are up to {@link
     * #START_COMPRESSION}, and brighter ones roll off smoothly toward white.
     *
     * @param color The linear color, changed in place.
     * @return The color, for chaining.
     */
    public static Vector3f pbrNeutral(@NonNull Vector3f color) {
        final float x = Math.min(color.x, Math.min(color.y, color.z));
        final float offset = x < 0.08f ? x - 6.25f * x * x : 0.04f;
        color.sub(offset, offset, offset);

        final float peak = Math.max(color.x, Math.max(color.y, color.z));
        if (peak < START_COMPRESSION) {
            return color;
        }
        final float d = 1 - START_COMPRESSION;
        final float newPeak = 1 - d * d / (peak + d - START_COMPRESSION);
        color.mul(newPeak / peak);

        final float g = 1 - 1 / (DESATURATION * (peak - newPeak) + 1);
        return color.lerp(new Vector3f(newPeak), g);
    }

    private ToneMapMath() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
