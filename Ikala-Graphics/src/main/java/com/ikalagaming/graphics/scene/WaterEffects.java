package com.ikalagaming.graphics.scene;

import javax.annotation.Nullable;

/**
 * The particles the water throws up, see {@link com.ikalagaming.graphics.Water#setEffects}: each
 * effect is spawned where the water says, its {@link ParticleEffect#getBurst() burst} particles at
 * a time, its rate unused. Null leaves that one out. Their particles never splash again.
 *
 * @param spray Thrown off breaking crests, as the wind tears the tops off whitecaps.
 * @param mist Rising where falling water lands.
 * @param splash Droplets thrown up where falling water lands, and where the water is disturbed.
 * @param drop Droplets thrown up where something falling fast lands in the water, like a raindrop.
 */
public record WaterEffects(
        @Nullable ParticleEffect spray,
        @Nullable ParticleEffect mist,
        @Nullable ParticleEffect splash,
        @Nullable ParticleEffect drop) {

    /** The water's own effects, which it starts with. */
    public static final WaterEffects DEFAULT =
            new WaterEffects(
                    ParticleSamples.WATER_SPRAY,
                    ParticleSamples.WATER_MIST,
                    ParticleSamples.WATER_SPLASH,
                    ParticleSamples.WATER_DROP);

    /** No particles from the water at all. */
    public static final WaterEffects NONE = new WaterEffects(null, null, null, null);
}
