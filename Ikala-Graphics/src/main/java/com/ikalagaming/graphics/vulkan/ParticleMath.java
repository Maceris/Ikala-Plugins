package com.ikalagaming.graphics.vulkan;

import lombok.NonNull;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The math and layouts of GPU particles: how emitters spawn over time, how a particle moves under
 * acceleration and drag, how directions are picked in a cone, and where the particle pool keeps its
 * positions so floats stay precise. The particle shaders ({@code particles.glsl}) have the same
 * math; keep them in step. Pure Java, so it is tested without a GPU.
 *
 * <p>Positions on the GPU are floats relative to an anchor, a point on a {@link #ANCHOR_GRID} grid
 * near the camera, rather than to the camera itself: particles keep their positions as the camera
 * moves, and only when the anchor steps are they all moved by the step.
 */
public final class ParticleMath {

    /** Threads in a workgroup of the particle compute shaders. */
    public static final int WORKGROUP_SIZE = 64;

    /** How far apart the anchors particle positions are kept relative to are, in meters. */
    public static final double ANCHOR_GRID = 1024;

    /** The size of one particle in the pool, in bytes, see {@code particles.glsl}. */
    public static final int PARTICLE_SIZE = 80;

    /** The size of one emitter's record for a frame, in bytes: eight vec4s. */
    public static final int EMITTER_SIZE = 8 * 4 * Float.BYTES;

    /** Where an emitter's position, relative to the anchor, and first spawn index are. */
    public static final int EMITTER_POSITION = 0;

    /** Where its direction and the cosine of its spread are. */
    public static final int EMITTER_DIRECTION = 16;

    /** Where its acceleration and drag are. */
    public static final int EMITTER_ACCELERATION = 32;

    /** Where its lifetime and speed ranges are. */
    public static final int EMITTER_LIFETIME = 48;

    /** Where its starting color is. */
    public static final int EMITTER_COLOR_START = 64;

    /** Where its ending color is. */
    public static final int EMITTER_COLOR_END = 80;

    /** Where its sizes, emission and spawn radius are. */
    public static final int EMITTER_SIZES = 96;

    /** Where its spawn count and random seed are. */
    public static final int EMITTER_COUNT = 112;

    /** Where the control buffer keeps the two alive lists' counts. */
    public static final int CONTROL_ALIVE = 0;

    /** Where it keeps how many particles are free, an int. */
    public static final int CONTROL_DEAD = 8;

    /** Where it keeps how many spawns found the pool full this frame. */
    public static final int CONTROL_DROPPED = 12;

    /** Where it keeps the simulation's indirect dispatch: x, y and z groups. */
    public static final int CONTROL_DISPATCH = 16;

    /** Where it keeps the draw's indirect arguments: vertices, instances, first of each. */
    public static final int CONTROL_DRAW = 32;

    /** The size of the draw's indirect arguments, a VkDrawIndirectCommand. */
    public static final int CONTROL_DRAW_STRIDE = 4 * Integer.BYTES;

    /** The size of the control buffer. */
    public static final int CONTROL_SIZE = 48;

    /** Vertices in a particle's billboard, two triangles. */
    public static final int BILLBOARD_VERTICES = 6;

    /** Below this drag, per second, motion is integrated as if there were none. */
    public static final double NO_DRAG = 1e-6;

    /** Static helpers only. */
    private ParticleMath() {}

    /**
     * How many particles an emitter spawns this frame, keeping the fraction for the next.
     *
     * @param rate Particles a second.
     * @param seconds How long the frame was.
     * @param carry The fraction of a particle left from the frames before, updated in place at
     *     index 0.
     * @return How many to spawn now.
     */
    public static int spawns(double rate, double seconds, double @NonNull [] carry) {
        if (rate <= 0 || seconds <= 0) {
            return 0;
        }
        final double total = carry[0] + rate * seconds;
        final double whole = Math.floor(total);
        carry[0] = total - whole;
        return (int) Math.min(whole, Integer.MAX_VALUE);
    }

    /**
     * The anchor a camera position keeps particles relative to: the corner of its {@link
     * #ANCHOR_GRID} cell.
     *
     * @param camera The camera's position, in the world.
     * @param out Receives the anchor.
     * @return The anchor.
     */
    public static Vector3d anchorFor(@NonNull Vector3d camera, @NonNull Vector3d out) {
        return out.set(
                Math.floor(camera.x / ANCHOR_GRID) * ANCHOR_GRID,
                Math.floor(camera.y / ANCHOR_GRID) * ANCHOR_GRID,
                Math.floor(camera.z / ANCHOR_GRID) * ANCHOR_GRID);
    }

    /**
     * Move a particle for a time under constant acceleration and linear drag, in closed form, so a
     * long frame moves it as far as many short ones: v(t) = a/k + (v - a/k) e^(-kt), and its
     * position the integral of that.
     *
     * @param position Its position, updated in place.
     * @param velocity Its velocity, updated in place.
     * @param acceleration Its acceleration, such as gravity, in meters a second squared.
     * @param drag How fast its velocity relative to the air decays, per second.
     * @param seconds How long it moves.
     */
    public static void advance(
            @NonNull Vector3f position,
            @NonNull Vector3f velocity,
            @NonNull Vector3fc acceleration,
            float drag,
            float seconds) {
        if (drag < NO_DRAG) {
            position.fma(seconds, velocity).fma(0.5f * seconds * seconds, acceleration);
            velocity.fma(seconds, acceleration);
            return;
        }
        final float decay = (float) Math.exp(-drag * seconds);
        // The velocity it settles at, and how far from it it starts
        final Vector3f terminal = new Vector3f(acceleration).div(drag);
        final Vector3f excess = new Vector3f(velocity).sub(terminal);
        position.fma(seconds, terminal).fma((1 - decay) / drag, excess);
        velocity.set(terminal).fma(decay, excess);
    }

    /**
     * A direction within a cone around an axis, spread evenly over the cone's cap of the unit
     * sphere.
     *
     * @param axis The cone's axis, unit length.
     * @param cosSpread The cosine of the cone's half angle: 1 for only the axis, -1 for any way.
     * @param u A number from 0 to 1, picking how far from the axis.
     * @param v A number from 0 to 1, picking which way around it.
     * @param out Receives the direction, unit length.
     * @return The direction.
     */
    public static Vector3f coneDirection(
            @NonNull Vector3fc axis, float cosSpread, float u, float v, @NonNull Vector3f out) {
        final float cosTheta = 1 - u * (1 - cosSpread);
        final float sinTheta = (float) Math.sqrt(Math.max(0, 1 - cosTheta * cosTheta));
        final float phi = (float) (2 * Math.PI * v);
        // A frame around the axis, from whichever world axis is least along it
        final Vector3f helper =
                Math.abs(axis.y()) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        final Vector3f tangent = helper.cross(axis).normalize();
        final Vector3f bitangent = new Vector3f(axis).cross(tangent);
        return out.set(axis)
                .mul(cosTheta)
                .fma(sinTheta * (float) Math.cos(phi), tangent)
                .fma(sinTheta * (float) Math.sin(phi), bitangent);
    }

    /**
     * How far through its life a particle is.
     *
     * @param age How long it has lived, in seconds.
     * @param lifetime How long it lives.
     * @return From 0 when spawned to 1 when it dies.
     */
    public static float lifeFraction(float age, float lifetime) {
        return lifetime <= 0 ? 1 : Math.clamp(age / lifetime, 0, 1);
    }

    /**
     * How opaque a particle's round billboard is at a point on it: full in the middle, fading
     * smoothly to nothing at the rim, (1 - r^2)^2.
     *
     * @param x Across the billboard, from -1 to 1.
     * @param y Up it, from -1 to 1.
     * @return The share of the particle's opacity there, 0 to 1.
     */
    public static float roundShape(float x, float y) {
        final float r2 = x * x + y * y;
        if (r2 >= 1) {
            return 0;
        }
        final float falloff = 1 - r2;
        return falloff * falloff;
    }

    /**
     * How much of a particle shows in front of the opaque scene, fading it out as it nears a
     * surface, so it doesn't end in a hard line where it crosses one.
     *
     * @param particleDepth The particle's distance in front of the camera, in meters.
     * @param sceneDepth The opaque scene's, at the same pixel.
     * @param fadeDistance How far in front of the scene it starts fading, in meters.
     * @return From 0 at the surface to 1 the fade distance or more in front of it.
     */
    public static float softFade(float particleDepth, float sceneDepth, float fadeDistance) {
        if (fadeDistance <= 0) {
            return sceneDepth > particleDepth ? 1 : 0;
        }
        return Math.clamp((sceneDepth - particleDepth) / fadeDistance, 0, 1);
    }
}
