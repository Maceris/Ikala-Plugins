package com.ikalagaming.graphics.vulkan;

import lombok.NonNull;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Order independent transparency as {@code translucent.frag} and {@code oit_resolve.frag} do it;
 * keep them in step with this. Each layer adds its lit color times its opacity times a weight, its
 * opacity times the weight, and its extinction, {@code -log(1 - opacity)}. The resolve takes the
 * weighted average color of the layers and covers as much of the background as the summed
 * extinction hides, {@code 1 - exp(-sum)}. That much is exact in any order; only how the layers'
 * colors mix depends on the weights.
 */
public final class OitMath {

    /** The most opaque a layer can be, so its extinction stays finite. */
    public static final float MAX_OPACITY = 0.999f;

    /**
     * Weighted blended OIT's depth weight, McGuire and Bavoil 2013, equation 10: nearer layers
     * count for more in the average.
     *
     * @param alpha The layer's opacity.
     * @param depth How far in front of the camera it is, in meters.
     * @return The weight.
     */
    public static float weight(float alpha, float depth) {
        final double falloff = 10 / (1e-5 + Math.pow(depth / 5.0, 2) + Math.pow(depth / 200.0, 6));
        return (float) (alpha * Math.clamp(falloff, 1e-2, 3e3));
    }

    /**
     * Add one layer to the running sums.
     *
     * @param accum The weighted color and weight so far, changed in place. Starts at all zeros.
     * @param extinction The extinction so far.
     * @param color The layer's lit color.
     * @param alpha The layer's opacity.
     * @param depth How far in front of the camera it is, in meters.
     * @return The new extinction.
     */
    public static float add(
            @NonNull Vector4f accum,
            float extinction,
            @NonNull Vector3f color,
            float alpha,
            float depth) {
        final float a = Math.min(alpha, MAX_OPACITY);
        return addWeighted(accum, extinction, color, a, weight(a, depth));
    }

    /**
     * Add one layer to the running sums with a weight worked out elsewhere, like the voxel-based
     * weights of {@link VoxelOitMath}.
     *
     * @param accum The weighted color and weight so far, changed in place. Starts at all zeros.
     * @param extinction The extinction so far.
     * @param color The layer's lit color.
     * @param alpha The layer's opacity.
     * @param weight The layer's weight.
     * @return The new extinction.
     */
    public static float addWeighted(
            @NonNull Vector4f accum,
            float extinction,
            @NonNull Vector3f color,
            float alpha,
            float weight) {
        final float a = Math.min(alpha, MAX_OPACITY);
        accum.add(color.x * a * weight, color.y * a * weight, color.z * a * weight, a * weight);
        return extinction + extinction(a);
    }

    /**
     * How much a layer hides what is behind it, in a form that adds up across layers.
     *
     * @param alpha The layer's opacity, below 1.
     * @return {@code -log(1 - alpha)}.
     */
    public static float extinction(float alpha) {
        return (float) -Math.log(1 - alpha);
    }

    /**
     * Composite the layers over the background.
     *
     * @param accum The summed weighted color and weight.
     * @param extinction The summed extinction.
     * @param background What is behind every layer, changed in place to the result.
     * @return The result.
     */
    public static Vector3f resolve(
            @NonNull Vector4f accum, float extinction, @NonNull Vector3f background) {
        if (extinction <= 0) {
            return background;
        }
        final float transmittance = (float) Math.exp(-extinction);
        final float weight = Math.max(accum.w, 1e-5f);
        return background
                .mul(transmittance)
                .add(
                        accum.x / weight * (1 - transmittance),
                        accum.y / weight * (1 - transmittance),
                        accum.z / weight * (1 - transmittance));
    }

    private OitMath() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
