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

    /** Where its particles' behavior word is, see {@link #packBehavior}. */
    public static final int EMITTER_BEHAVIOR = EMITTER_COUNT + 2 * Integer.BYTES;

    /** The size of a particle's light, four half floats, see {@code particle_light.comp}. */
    public static final int RADIANCE_SIZE = 4 * Short.BYTES;

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

    /** A particle's behavior word: its anisotropy, as a signed byte in the low 8 bits. */
    public static final int BEHAVIOR_ANISOTROPY_MASK = 0xFF;

    /** Where in it whether lights light the particle is, one bit. */
    public static final int BEHAVIOR_LIT_SHIFT = 8;

    /** Where in it what the particle does when it hits something is, two bits. */
    public static final int BEHAVIOR_COLLISION_SHIFT = 9;

    /** The collision bits' mask, once shifted down. */
    public static final int BEHAVIOR_COLLISION_MASK = 3;

    /** Where in it how much of its speed into a surface it keeps is, an unsigned byte. */
    public static final int BEHAVIOR_BOUNCE_SHIFT = 16;

    /** Where in it how much of its speed along a surface it loses is, an unsigned byte. */
    public static final int BEHAVIOR_FRICTION_SHIFT = 24;

    /** The largest value a signed byte holds, for the anisotropy. */
    public static final int SIGNED_BYTE_MAX = 127;

    /** The largest value an unsigned byte holds, for the bounce and friction. */
    public static final int UNSIGNED_BYTE_MAX = 255;

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
     * Pack how a particle behaves into its behavior word: which way it scatters light, whether
     * lights light it, and what it does when it hits something, see {@code particles.glsl}.
     *
     * @param anisotropy Its Henyey-Greenstein g, -1 to 1.
     * @param lit Whether lights light it.
     * @param collision What it does when it hits something, an ordinal of {@code
     *     ParticleEffect.Collision}.
     * @param bounce How much of its speed into a surface it keeps, 0 to 1.
     * @param friction How much of its speed along a surface it loses, 0 to 1.
     * @return The word.
     */
    public static int packBehavior(
            float anisotropy, boolean lit, int collision, float bounce, float friction) {
        final int g = Math.round(Math.clamp(anisotropy, -1, 1) * SIGNED_BYTE_MAX);
        return (g & BEHAVIOR_ANISOTROPY_MASK)
                | (lit ? 1 << BEHAVIOR_LIT_SHIFT : 0)
                | (collision & BEHAVIOR_COLLISION_MASK) << BEHAVIOR_COLLISION_SHIFT
                | Math.round(Math.clamp(bounce, 0, 1) * UNSIGNED_BYTE_MAX) << BEHAVIOR_BOUNCE_SHIFT
                | Math.round(Math.clamp(friction, 0, 1) * UNSIGNED_BYTE_MAX)
                        << BEHAVIOR_FRICTION_SHIFT;
    }

    /**
     * The anisotropy in a behavior word.
     *
     * @param behavior The word.
     * @return The Henyey-Greenstein g, to within a byte's step.
     */
    public static float anisotropyOf(int behavior) {
        return (byte) (behavior & BEHAVIOR_ANISOTROPY_MASK) / (float) SIGNED_BYTE_MAX;
    }

    /**
     * Turn a particle back from a solid voxel it stepped into: back to where it was, its speed into
     * the face it crossed reversed and scaled by the bounce, and its speed along the face scaled
     * down by the friction. The face is the last voxel boundary the step crossed, the one into the
     * solid voxel.
     *
     * @param previous Where it was, relative to the voxel grid's corner.
     * @param next Where it stepped to, in a solid voxel, relative to the same corner.
     * @param velocity Its velocity, updated in place.
     * @param voxel The voxels' size, in meters.
     * @param bounce How much of its speed into the face it keeps, 0 to 1.
     * @param friction How much of its speed along the face it loses, 0 to 1.
     * @param normal Receives the face's normal, pointing back out toward where it was.
     * @return Where it is now: where it was.
     */
    public static Vector3f collide(
            @NonNull Vector3fc previous,
            @NonNull Vector3fc next,
            @NonNull Vector3f velocity,
            float voxel,
            float bounce,
            float friction,
            @NonNull Vector3f normal) {
        int axis = -1;
        float latest = -1;
        for (int i = 0; i < 3; ++i) {
            final float from = previous.get(i);
            final float to = next.get(i);
            final float fromCell = (float) Math.floor(from / voxel);
            final float toCell = (float) Math.floor(to / voxel);
            if (fromCell == toCell) {
                continue;
            }
            // The boundary of the solid voxel facing where it came from
            final float boundary = (to > from ? toCell : toCell + 1) * voxel;
            final float crossing = (boundary - from) / (to - from);
            if (crossing > latest) {
                latest = crossing;
                axis = i;
            }
        }
        normal.zero();
        if (axis < 0) {
            // Stepped nowhere new, so already inside: stop it
            velocity.zero();
            return new Vector3f(previous);
        }
        normal.setComponent(axis, next.get(axis) > previous.get(axis) ? -1 : 1);
        final float into = velocity.dot(normal);
        final Vector3f along = new Vector3f(velocity).fma(-into, normal).mul(1 - friction);
        velocity.set(along).fma(into < 0 ? -bounce * into : into, normal);
        return new Vector3f(previous);
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
