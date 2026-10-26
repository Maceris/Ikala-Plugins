package com.ikalagaming.graphics.scene;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

import java.util.List;

/**
 * A few particle effects to look at and test with: the gate benchmark places them, and the asset
 * workbench's particle viewer starts from them.
 */
public final class ParticleSamples {

    /**
     * A named sample.
     *
     * @param name What it is called, for lists.
     * @param effect The effect.
     */
    public record Sample(String name, ParticleEffect effect) {}

    /** Gravity, in meters a second squared. */
    private static final Vector3fc GRAVITY = new Vector3f(0, -9.81f, 0);

    /** Blue-white water drops arcing up and falling back. */
    public static final ParticleEffect FOUNTAIN =
            ParticleEffect.builder()
                    .rate(400)
                    .lifetime(1.2f, 1.8f)
                    .speed(6, 8)
                    .spread(0.25f)
                    .acceleration(GRAVITY)
                    .drag(0.3f)
                    .size(0.12f, 0.06f)
                    .colors(new Vector4f(0.6f, 0.75f, 1, 0.9f), new Vector4f(0.6f, 0.75f, 1, 0.3f))
                    .collision(ParticleEffect.Collision.BOUNCE)
                    .bounce(0.2f)
                    .friction(0.5f)
                    .build();

    /** Short-lived glowing orange sparks, flung every way and falling. */
    public static final ParticleEffect SPARKS =
            ParticleEffect.builder()
                    .rate(300)
                    .lifetime(0.4f, 0.9f)
                    .speed(3, 6)
                    .spread((float) Math.PI)
                    .acceleration(GRAVITY)
                    .size(0.06f, 0.02f)
                    .colors(new Vector4f(1, 0.6f, 0.2f, 1), new Vector4f(1, 0.2f, 0.05f, 0.6f))
                    .emissive(6)
                    .lit(false)
                    .collision(ParticleEffect.Collision.BOUNCE)
                    .bounce(0.4f)
                    .friction(0.3f)
                    .build();

    /** Big gray puffs rising slowly, growing and fading. */
    public static final ParticleEffect SMOKE =
            ParticleEffect.builder()
                    .rate(25)
                    .lifetime(3, 5)
                    .speed(0.8f, 1.5f)
                    .spread(0.3f)
                    .spawnRadius(0.4f)
                    .drag(0.4f)
                    .size(0.8f, 3)
                    .colors(new Vector4f(0.5f, 0.5f, 0.5f, 0.6f), new Vector4f(0.6f, 0.6f, 0.6f, 0))
                    .build();

    /** Thick smoke drawn in the fog volume: big soft balls, lit and shaded like the fog. */
    public static final ParticleEffect THICK_SMOKE =
            ParticleEffect.builder()
                    .rate(12)
                    .lifetime(5, 7)
                    .speed(0.6f, 1)
                    .spread(0.25f)
                    .spawnRadius(0.5f)
                    .drag(0.3f)
                    .size(1.5f, 4)
                    .colors(
                            new Vector4f(0.7f, 0.7f, 0.7f, 0.9f),
                            new Vector4f(0.75f, 0.75f, 0.75f, 0))
                    .anisotropy(0.3f)
                    .volumetric(true)
                    .build();

    /** Flames: glowing orange puffs rising fast and shrinking, going dark red as they fade. */
    public static final ParticleEffect FIRE =
            ParticleEffect.builder()
                    .rate(120)
                    .lifetime(0.5f, 1)
                    .speed(1.5f, 2.5f)
                    .spread(0.2f)
                    .spawnRadius(0.25f)
                    .acceleration(new Vector3f(0, 2, 0))
                    .drag(1)
                    .size(0.5f, 0.15f)
                    .colors(new Vector4f(1, 0.55f, 0.15f, 0.7f), new Vector4f(0.6f, 0.1f, 0.02f, 0))
                    .emissive(3)
                    .lit(false)
                    .build();

    /** Every sample, in the order lists show them. */
    public static final List<Sample> ALL =
            List.of(
                    new Sample("Fountain", FOUNTAIN),
                    new Sample("Sparks", SPARKS),
                    new Sample("Smoke", SMOKE),
                    new Sample("Thick smoke", THICK_SMOKE),
                    new Sample("Fire", FIRE));

    /** Static samples only. */
    private ParticleSamples() {}
}
