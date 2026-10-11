package com.ikalagaming.graphics.vulkan;

import lombok.NonNull;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * How a surface reflects ambient light, light arriving equally from every direction, as {@code
 * lighting.glsl} does it; keep them in step with this.
 *
 * <p>The reflection splits like the direct lights' does. Specular uses the split-sum approximation,
 * with Karis's analytic fit to the environment BRDF ("Physically Based Shading on Mobile", 2014)
 * giving a scale and bias on the color at normal incidence. Multiple scattering is compensated
 * after Fdez-Agüera ("A Multiple-Scattering Microfacet Model for Real-Time Image-based Lighting",
 * 2019), so rough surfaces don't lose the light that bounces more than once between their
 * microfacets. Whatever the specular doesn't reflect is left for the diffuse, which metals don't
 * have.
 *
 * <p>With ambient light the same from every direction, the prefiltered radiance and the irradiance
 * are both just the ambient light.
 */
public final class AmbientMath {

    /**
     * How strong a fully specular dielectric's reflection is head on, per unit of the material's
     * specular value: 0.5 gives 4%, a typical dielectric. Matches the Disney BRDF in {@code
     * lighting.glsl}.
     */
    public static final float DIELECTRIC_SPECULAR_SCALE = 0.08f;

    /** The smallest N·V used, so surfaces seen edge on don't divide by nothing. */
    public static final float MIN_N_DOT_V = 1e-4f;

    /**
     * The average of Schlick's Fresnel over the hemisphere is {@code F0 + (1 - F0) / 21}; this is
     * the 21.
     */
    public static final float FRESNEL_AVERAGE_DIVISOR = 21;

    /**
     * Karis's fit to the split-sum environment BRDF: the scale and bias that turn the color at
     * normal incidence into how much light the surface reflects specularly. The bias is kept at or
     * above zero; the fit dips a fraction of a percent below it on rough surfaces seen head on.
     *
     * @param roughness The material's roughness, from 0 to 1, before squaring.
     * @param nDotV The cosine between the normal and the direction to the viewer.
     * @param out Receives the scale in x and the bias in y.
     * @return The out vector.
     */
    public static Vector2f environmentBrdf(float roughness, float nDotV, @NonNull Vector2f out) {
        final float rX = roughness * -1 + 1;
        final float rY = roughness * -0.0275f + 0.0425f;
        final float rZ = roughness * -0.572f + 1.04f;
        final float rW = roughness * 0.022f + -0.04f;
        final float a004 =
                Math.min(rX * rX, (float) Math.pow(2, -9.28 * Math.max(nDotV, MIN_N_DOT_V))) * rX
                        + rY;
        return out.set(-1.04f * a004 + rZ, Math.max(1.04f * a004 + rW, 0));
    }

    /**
     * The color a surface reflects specularly head on, as the Disney BRDF works it out: a
     * dielectric reflects a little, tinted toward its base color by its specular tint, and a metal
     * reflects its base color.
     *
     * @param baseLinear The base color, in linear light.
     * @param metallic How metallic it is, from 0 to 1.
     * @param specular The material's specular value.
     * @param specularTint How much a dielectric's reflection takes the base color's hue.
     * @param out Receives the color.
     * @return The out vector.
     */
    public static Vector3f specularColor(
            @NonNull Vector3fc baseLinear,
            float metallic,
            float specular,
            float specularTint,
            @NonNull Vector3f out) {
        final float luminance =
                0.3f * baseLinear.x() + 0.6f * baseLinear.y() + 0.1f * baseLinear.z();
        Vector3f tint = new Vector3f(1);
        if (luminance > 0) {
            baseLinear.div(luminance, tint);
        }
        Vector3f dielectric =
                new Vector3f(1).lerp(tint, specularTint).mul(specular * DIELECTRIC_SPECULAR_SCALE);
        return dielectric.lerp(baseLinear, metallic, out);
    }

    /**
     * How much of the ambient light a surface reflects toward the viewer, diffuse and specular
     * together, per channel.
     *
     * @param baseLinear The base color, in linear light.
     * @param metallic How metallic it is, from 0 to 1.
     * @param roughness The material's roughness, from 0 to 1, before squaring.
     * @param specular The material's specular value.
     * @param specularTint How much a dielectric's reflection takes the base color's hue.
     * @param nDotV The cosine between the normal and the direction to the viewer.
     * @param diffuseOut Receives the diffuse part.
     * @param specularOut Receives the specular part.
     */
    public static void reflectance(
            @NonNull Vector3fc baseLinear,
            float metallic,
            float roughness,
            float specular,
            float specularTint,
            float nDotV,
            @NonNull Vector3f diffuseOut,
            @NonNull Vector3f specularOut) {
        Vector3f f0 = specularColor(baseLinear, metallic, specular, specularTint, new Vector3f());
        Vector2f scaleBias = environmentBrdf(roughness, nDotV, new Vector2f());
        // Single scattering: how much one bounce reflects, for white and for this color. The fit
        // can give a color more than white at grazing angles on smooth surfaces, where its scale
        // goes negative, so it is kept to white's.
        final float singleWhite = scaleBias.x + scaleBias.y;
        Vector3f singleScatter =
                new Vector3f(f0)
                        .mul(scaleBias.x)
                        .add(new Vector3f(scaleBias.y))
                        .min(new Vector3f(singleWhite));
        final float missed = 1 - singleWhite;
        // What the missed light does over more bounces, by the average Fresnel
        Vector3f average = new Vector3f(1).sub(f0).div(FRESNEL_AVERAGE_DIVISOR).add(f0);
        Vector3f multiScatter = new Vector3f(singleScatter).mul(average);
        Vector3f denominator = new Vector3f(average).mul(-missed).add(new Vector3f(1));
        multiScatter.div(denominator).mul(missed);
        singleScatter.add(multiScatter, specularOut);
        // The diffuse gets what the specular doesn't reflect
        new Vector3f(1).sub(specularOut).mul(baseLinear).mul(1 - metallic, diffuseOut);
    }

    private AmbientMath() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
