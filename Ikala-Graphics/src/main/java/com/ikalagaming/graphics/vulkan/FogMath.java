package com.ikalagaming.graphics.vulkan;

import lombok.NonNull;

/**
 * The math of volumetric fog and smoke: participating media in a volume of the view's froxels, the
 * transparency volume's tiles and slices (see {@link VoxelOitMath}), lit per froxel and integrated
 * front to back, after Wronski ("Volumetric Fog", SIGGRAPH 2014) and Hillaire ("Physically Based
 * and Unified Volumetric Rendering in Frostbite", SIGGRAPH 2015). The fog shaders have the same
 * math; keep them in step. Pure Java, so it is tested without a GPU.
 *
 * <p>A medium has an extinction coefficient (per meter) and an albedo, its share of extinction that
 * scatters rather than absorbs. Light scattered toward the camera at a point is the scattering
 * coefficient times the light arriving, times the phase function for the angle between them.
 * Integrating a slice, the light scattered within it reaches its near end dimmed by the medium in
 * front of it within the slice: Hillaire's energy-conserving form, which stays right however long
 * the slice is, unlike adding scattering times length.
 */
public final class FogMath {

    /** Color channels. */
    public static final int CHANNELS = 3;

    /** Below this extinction, per meter, a slice's scattering is integrated as if clear. */
    public static final double CLEAR = 1e-6;

    /**
     * Scattering orders the sun's light is followed through in {@link #multipleScattering}: the
     * first, single scattering, and the octaves after it.
     */
    public static final int SCATTER_OCTAVES = 4;

    /**
     * How much less each octave of multiple scattering is dimmed by the medium than the one before.
     */
    public static final double SCATTER_ATTENUATION = 0.5;

    /** How much less each octave of multiple scattering adds than the one before. */
    public static final double SCATTER_CONTRIBUTION = 0.5;

    /** How much less forward each octave of multiple scattering scatters than the one before. */
    public static final double SCATTER_ECCENTRICITY = 0.5;

    /** Static helpers only. */
    private FogMath() {}

    /**
     * How far in front of the camera a slice boundary is: the inverse of {@link
     * VoxelOitMath#sliceCoordinate}.
     *
     * @param slice The slice coordinate, 0 at {@link VoxelOitMath#FIRST_DEPTH}, {@link
     *     VoxelOitMath#SLICES} at the view distance.
     * @param logScale {@link VoxelOitMath#logScale} of the view distance.
     * @return The depth, in meters.
     */
    public static double sliceDepth(double slice, double logScale) {
        return VoxelOitMath.FIRST_DEPTH * Math.exp(slice / logScale);
    }

    /**
     * The Henyey-Greenstein phase function: how much of the light scattered at a point goes off at
     * an angle from where it was going, per steradian, integrating to 1 over the sphere.
     *
     * @param cosTheta The cosine of the angle between the light's direction and the direction it
     *     scatters to.
     * @param g The anisotropy, from -1 (all back) through 0 (even) to 1 (all forward).
     * @return The phase, per steradian.
     */
    public static double henyeyGreenstein(double cosTheta, double g) {
        final double gg = g * g;
        final double denominator = 1 + gg - 2 * g * cosTheta;
        return (1 - gg) / (4 * Math.PI * denominator * Math.sqrt(denominator));
    }

    /**
     * The sun's light scattered toward the camera at a point in a medium, per unit of scattering
     * coefficient and of sunlight, counting light scattered more than once. Single scattering alone
     * makes dense smoke dark, since the medium in front of a point toward the sun stops nearly all
     * of the sun's light; light that scattered on the way gets deeper. This follows Wrenninge's
     * octaves ("Oz: The Great and Volumetric", SIGGRAPH 2013), as Frostbite's clouds do: each
     * octave after the first is dimmed less by the medium, adds less, and scatters less forward.
     * Higher orders need light to have scattered again around the point, so each is weighted by the
     * albedo and by how much light the medium around the point stops, its thickness, which keeps
     * thin fog at single scattering while the sunlit skin of thick smoke still gets the light
     * scattered back out from inside it.
     *
     * @param opticalDepth The medium's optical depth from the point toward the sun.
     * @param thickness The optical thickness of the medium around the point, across the volume it
     *     is in.
     * @param cosTheta The cosine between the sun's light's direction and the way toward the camera.
     * @param g The medium's Henyey-Greenstein anisotropy.
     * @param albedo The medium's albedo, its scattering over its extinction.
     * @return The light scattered toward the camera, per steradian.
     */
    public static double multipleScattering(
            double opticalDepth, double thickness, double cosTheta, double g, double albedo) {
        final double scattered = albedo * (1 - Math.exp(-thickness));
        double attenuation = 1;
        double contribution = 1;
        double eccentricity = 1;
        double total = 0;
        for (int octave = 0; octave < SCATTER_OCTAVES; ++octave) {
            total +=
                    contribution
                            * henyeyGreenstein(cosTheta, g * eccentricity)
                            * Math.exp(-attenuation * opticalDepth);
            attenuation *= SCATTER_ATTENUATION;
            contribution *= SCATTER_CONTRIBUTION * scattered;
            eccentricity *= SCATTER_ECCENTRICITY;
        }
        return total;
    }

    /**
     * Exponential height fog's extinction at a height: its density at and below a base height,
     * falling off by e every 1 / falloff meters above it.
     *
     * @param height The height, in the world, in meters.
     * @param density The extinction at the base height, per meter.
     * @param falloff How fast it thins with height, per meter; 0 for even fog.
     * @param baseHeight The base height, in meters.
     * @return The extinction, per meter.
     */
    public static double heightFog(
            double height, double density, double falloff, double baseHeight) {
        return density * Math.exp(-falloff * Math.max(0, height - baseHeight));
    }

    /**
     * How far a point is outside a box, negative inside.
     *
     * @param x The point's x, from the box's middle in its own frame.
     * @param y The point's y.
     * @param z The point's z.
     * @param halfX The box's half size along x.
     * @param halfY Along y.
     * @param halfZ Along z.
     * @return The signed distance, in meters.
     */
    public static double boxDistance(
            double x, double y, double z, double halfX, double halfY, double halfZ) {
        final double qx = Math.abs(x) - halfX;
        final double qy = Math.abs(y) - halfY;
        final double qz = Math.abs(z) - halfZ;
        final double ox = Math.max(qx, 0);
        final double oy = Math.max(qy, 0);
        final double oz = Math.max(qz, 0);
        return Math.sqrt(ox * ox + oy * oy + oz * oz) + Math.min(Math.max(qx, Math.max(qy, qz)), 0);
    }

    /**
     * How far a point is outside a sphere, negative inside.
     *
     * @param x The point's x, from the sphere's middle.
     * @param y The point's y.
     * @param z The point's z.
     * @param radius The sphere's radius.
     * @return The signed distance, in meters.
     */
    public static double sphereDistance(double x, double y, double z, double radius) {
        return Math.sqrt(x * x + y * y + z * z) - radius;
    }

    /**
     * How far a point is outside an upright cylinder, negative inside.
     *
     * @param x The point's x, from the cylinder's middle in its own frame.
     * @param y The point's y, along its axis.
     * @param z The point's z.
     * @param radius Its radius.
     * @param halfHeight Its half height.
     * @return The signed distance, in meters.
     */
    public static double cylinderDistance(
            double x, double y, double z, double radius, double halfHeight) {
        final double dr = Math.sqrt(x * x + z * z) - radius;
        final double dy = Math.abs(y) - halfHeight;
        final double or = Math.max(dr, 0);
        final double oy = Math.max(dy, 0);
        return Math.sqrt(or * or + oy * oy) + Math.min(Math.max(dr, dy), 0);
    }

    /**
     * How much of a shape's density a point gets, by how far inside it is: none outside, rising
     * smoothly to all of it once the edge's width inside.
     *
     * @param signedDistance How far the point is outside the shape, negative inside.
     * @param edge How wide the soft edge is, in meters; 0 for a hard one.
     * @return The share, 0 to 1.
     */
    public static double edgeWeight(double signedDistance, double edge) {
        if (edge <= 0) {
            return signedDistance <= 0 ? 1 : 0;
        }
        final double t = Math.clamp(-signedDistance / edge, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /**
     * Integrate slices front to back: how much light gets through to each slice's far end, and how
     * much light the media scatters toward the camera up to it, energy-conserving within each
     * slice.
     *
     * @param scattered Each slice's light scattered toward the camera per meter, in each channel:
     *     the scattering coefficient times the light arriving times the phase, plus emission,
     *     {@link #CHANNELS} values a slice.
     * @param extinction Each slice's extinction, per meter.
     * @param lengths Each slice's length along the ray, in meters.
     * @param gathered Receives the light scattered toward the camera up to each slice's far end,
     *     {@link #CHANNELS} values a slice.
     * @param transmittance Receives how much light gets through to each slice's far end.
     */
    public static void integrate(
            double @NonNull [] scattered,
            double @NonNull [] extinction,
            double @NonNull [] lengths,
            double @NonNull [] gathered,
            double @NonNull [] transmittance) {
        double through = 1;
        final double[] sum = new double[CHANNELS];
        for (int slice = 0; slice < extinction.length; ++slice) {
            final double sigma = extinction[slice];
            final double length = lengths[slice];
            final double slab = Math.exp(-sigma * length);
            for (int c = 0; c < CHANNELS; ++c) {
                final double light = scattered[slice * CHANNELS + c];
                // The slice's own scattering reaching its near end: integral of light * e^(-sigma
                // s)
                final double within = sigma < CLEAR ? light * length : light * (1 - slab) / sigma;
                sum[c] += through * within;
                gathered[slice * CHANNELS + c] = sum[c];
            }
            through *= slab;
            transmittance[slice] = through;
        }
    }

    /**
     * A surface's color seen through the media in front of it: its own light dimmed by them, plus
     * what they scatter toward the camera in front of it.
     *
     * @param color The surface's light, in each channel.
     * @param gathered The light scattered toward the camera in front of it, in each channel.
     * @param transmittance How much light gets through to it.
     * @param out Receives the fogged light, in each channel.
     */
    public static void apply(
            double @NonNull [] color,
            double @NonNull [] gathered,
            double transmittance,
            double @NonNull [] out) {
        for (int c = 0; c < CHANNELS; ++c) {
            out[c] = color[c] * transmittance + gathered[c];
        }
    }
}
