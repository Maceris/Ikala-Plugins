package com.ikalagaming.graphics.scene;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import javax.annotation.Nullable;

/**
 * What an emitter spawns and how its particles move and look: a rate and an optional burst, how
 * long they live, how fast and which way they start, what pushes them, and their size and color
 * from birth to death. Emitters are placed through {@link com.ikalagaming.graphics.Particles}.
 *
 * <p>Built with {@link #builder()}; every value has a default, a soft white puff. Immutable: don't
 * change the vectors handed in after building.
 *
 * <pre>
 * ParticleEffect sparks = ParticleEffect.builder()
 *         .rate(200)
 *         .lifetime(0.4f, 0.9f)
 *         .speed(4, 7)
 *         .spread(0.6f)
 *         .acceleration(new Vector3f(0, -9.81f, 0))
 *         .size(0.05f, 0.02f)
 *         .colors(new Vector4f(4, 2, 0.6f, 1), new Vector4f(1, 0.2f, 0, 0))
 *         .build();
 * </pre>
 */
@Value
@Builder(toBuilder = true)
public class ParticleEffect {

    /**
     * What a particle does when it hits the world: the static world near the camera, its occupancy
     * volume's finest voxels, see {@link com.ikalagaming.graphics.vulkan.ParticleMath#collide}.
     */
    public enum Collision {
        /** Passes through. */
        NONE,
        /** Bounces off, keeping {@link #bounce} of its speed into the surface. */
        BOUNCE,
        /** Stops where it hits and stays. */
        STICK,
        /** Dies at once. */
        DIE
    }

    /** How many particles a second an emitter spawns. */
    @Builder.Default float rate = 20;

    /** How many it spawns at once when placed, on top of its rate. */
    @Builder.Default int burst = 0;

    /** The shortest a particle lives, in seconds. */
    @Builder.Default float lifetimeMin = 1;

    /** The longest, picked evenly between. */
    @Builder.Default float lifetimeMax = 2;

    /** The slowest a particle starts, in meters a second. */
    @Builder.Default float speedMin = 0.5f;

    /** The fastest, picked evenly between. */
    @Builder.Default float speedMax = 1;

    /** The way particles start out, unit length, in the world. */
    @Builder.Default @NonNull Vector3fc direction = new Vector3f(0, 1, 0);

    /** How far from the direction they may start, the cone's half angle in radians: 0 to pi. */
    @Builder.Default float spread = 0.3f;

    /** How far from the emitter they appear, a ball's radius in meters. */
    @Builder.Default float spawnRadius = 0;

    /** What pulls them, like gravity, in meters a second squared, in the world. */
    @Builder.Default @NonNull Vector3fc acceleration = new Vector3f();

    /** How fast the air slows them, per second: 0 for none. */
    @Builder.Default float drag = 0;

    /** How big a particle is when spawned, its billboard's width in meters. */
    @Builder.Default float sizeStart = 0.25f;

    /** How big it is when it dies, changing evenly over its life. */
    @Builder.Default float sizeEnd = 0.5f;

    /**
     * Its color and opacity when spawned, in linear light; the color can go past 1 for particles
     * that glow.
     */
    @Builder.Default @NonNull Vector4fc colorStart = new Vector4f(1, 1, 1, 0.8f);

    /** Its color and opacity when it dies, changing evenly over its life. */
    @Builder.Default @NonNull Vector4fc colorEnd = new Vector4f(1, 1, 1, 0);

    /** How much light it gives off, times its color: 0 for none. */
    @Builder.Default float emissive = 0;

    /**
     * Whether the sun, the sky and the lights light it, its color then being how much of their
     * light it scatters; unlit particles show their color as it is, like sparks and flames.
     */
    @Builder.Default boolean lit = true;

    /**
     * Which way a lit particle scatters light, its Henyey-Greenstein g: 0 the same every way,
     * toward 1 mostly onward, so it glows with the sun behind it, toward -1 mostly back.
     */
    @Builder.Default float anisotropy = 0.2f;

    /** What a particle does when it hits the world. */
    @Builder.Default @NonNull Collision collision = Collision.NONE;

    /** How much of its speed into a surface a bouncing particle keeps, 0 to 1. */
    @Builder.Default float bounce = 0.3f;

    /** How much of its speed along a surface a bouncing particle loses when it hits, 0 to 1. */
    @Builder.Default float friction = 0.2f;

    /**
     * Whether particles are drawn as soft balls of smoke in the fog volume rather than billboards:
     * lit and shaded like the fog, at the fog's coarse resolution, for broad thick smoke. Their
     * opacity is how much light a line through a particle's middle loses.
     */
    @Builder.Default boolean volumetric = false;

    /**
     * The box particles spawn in, its size in meters along each axis of the world, centered on the
     * emitter; all zero to spawn in the {@link #spawnRadius} ball instead.
     */
    @Builder.Default @NonNull Vector3fc spawnBox = new Vector3f();

    /**
     * Whether the emitter goes with the camera, its position an offset from it, for weather such as
     * rain and snow. Particles stay in its {@link #spawnBox}, wrapping around to the far side as
     * the camera moves, and none show under cover: below the highest thing over them, as the sky
     * occlusion map holds it. Coming down onto that highest thing counts as hitting the world.
     */
    @Builder.Default boolean aroundCamera = false;

    /**
     * How far a billboard stretches along its motion across the view, in seconds: its length grows
     * by its speed times this, and its opacity shrinks to match, for streaks like rain; 0 for none.
     */
    @Builder.Default float stretch = 0;

    /**
     * What spawns where a particle hits the world, like splashes where rain lands, {@link
     * #splashCount} particles a hit; null for nothing. A splash's own splash is left out.
     */
    @Builder.Default @Nullable ParticleEffect splash = null;

    /** How many of the {@link #splash} effect's particles spawn at each hit. */
    @Builder.Default int splashCount = 0;

    /**
     * Whether its emitters need a link, a slot of their own the GPU looks up while their particles
     * live: for a spawn box, following the camera, or splashes.
     *
     * @return Whether it needs one.
     */
    public boolean needsLink() {
        return aroundCamera || hasSpawnBox() || (splash != null && splashCount > 0);
    }

    /**
     * Whether it spawns in a box rather than a ball.
     *
     * @return Whether its spawn box has a size.
     */
    public boolean hasSpawnBox() {
        return spawnBox.x() > 0 || spawnBox.y() > 0 || spawnBox.z() > 0;
    }

    /** Builder methods for ranges and pairs, on top of the generated ones, filled in by Lombok. */
    public static class ParticleEffectBuilder {

        /**
         * How long particles live, picked evenly between.
         *
         * @param min The shortest, in seconds.
         * @param max The longest.
         * @return This builder.
         */
        public ParticleEffectBuilder lifetime(float min, float max) {
            return lifetimeMin(min).lifetimeMax(max);
        }

        /**
         * How fast particles start, picked evenly between.
         *
         * @param min The slowest, in meters a second.
         * @param max The fastest.
         * @return This builder.
         */
        public ParticleEffectBuilder speed(float min, float max) {
            return speedMin(min).speedMax(max);
        }

        /**
         * How big particles are, from birth to death.
         *
         * @param start When spawned, in meters.
         * @param end When they die.
         * @return This builder.
         */
        public ParticleEffectBuilder size(float start, float end) {
            return sizeStart(start).sizeEnd(end);
        }

        /**
         * Particles' colors and opacities, from birth to death.
         *
         * @param start When spawned.
         * @param end When they die.
         * @return This builder.
         */
        public ParticleEffectBuilder colors(@NonNull Vector4fc start, @NonNull Vector4fc end) {
            return colorStart(start).colorEnd(end);
        }
    }
}
