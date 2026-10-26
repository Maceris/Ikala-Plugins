package com.ikalagaming.graphics.scene;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * The scene's fog: a participating medium filling the world, thickest at and below a base height
 * and thinning above it, lit by the sun, the sky and the lights, and shadowed by the scene. It is
 * drawn as volumetric fog through every surface, opaque or not, and the sky (see {@code FogMath}).
 * The physical sky's air and haze can be added in, so distant land fades into the sky.
 */
@Getter
@Setter
public class Fog {
    /** How dense fog is by default, per meter: about a third of the light gets through 500 m. */
    public static final float DEFAULT_DENSITY = 0.002f;

    /**
     * Whether the fog is drawn.
     *
     * @param active Whether to draw it.
     * @return Whether it is drawn.
     */
    private boolean active;

    /**
     * The fog's color: how much of each channel of the light it stops it scatters rather than
     * absorbs, 0 to 1. White fog scatters all of it.
     *
     * @return The color, which can be changed in place.
     */
    @NonNull private final Vector3f color;

    /**
     * How much light the fog stops per meter at and below its base height, its extinction.
     *
     * @param density The extinction, per meter, 0 or more.
     * @return The extinction, per meter.
     */
    private float density;

    /**
     * How fast the fog thins above its base height, per meter: it falls off by e every 1 / falloff
     * meters. 0 for fog as dense at every height.
     *
     * @param heightFalloff The falloff, per meter, 0 or more.
     * @return The falloff, per meter.
     */
    private float heightFalloff;

    /**
     * The height at and below which the fog is densest, in meters.
     *
     * @param baseHeight The base height, in the world.
     * @return The base height.
     */
    private float baseHeight;

    /**
     * Which way the fog scatters light: from -1, back toward the light, through 0, every way alike,
     * to 1, onward from the light, which lights fog toward the sun.
     *
     * @param anisotropy The Henyey-Greenstein anisotropy, -0.9 to 0.9.
     * @return The anisotropy.
     */
    private float anisotropy;

    /**
     * Whether the physical sky's air and haze are added in, so far land fades into the sky's color
     * whatever the fog. Only with a physical sky.
     *
     * @param haze Whether to add them.
     * @return Whether they are added.
     */
    private boolean haze;

    /** Set up fog, off, at its default density, even at every height. */
    public Fog() {
        active = false;
        color = new Vector3f(1, 1, 1);
        density = DEFAULT_DENSITY;
        heightFalloff = 0;
        baseHeight = 0;
        anisotropy = 0;
        haze = false;
    }
}
