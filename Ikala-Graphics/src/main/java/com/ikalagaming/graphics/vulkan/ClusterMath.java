package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.scene.Projection;

import lombok.NonNull;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * The grid of clusters lights are sorted into, and the tests that decide which clusters a light
 * touches. {@code light_cull.comp} and {@code lights.frag} work these out the same way; keep them
 * in step with this.
 *
 * <p>The view is split into {@link #X} by {@link #Y} screen tiles, and each tile into {@link #Z}
 * slices by depth. The first slice runs from the camera to {@link #FIRST_SLICE_DEPTH}; the rest are
 * spaced exponentially from there out to the view distance, so each is about as deep as it is wide,
 * and the last also holds everything beyond. Depths are distances in front of the camera, so the
 * negated view space z.
 */
public final class ClusterMath {

    /** How many tiles across the screen. */
    public static final int X = 16;

    /** How many tiles down the screen, for a 16:9 screen's tiles to be square. */
    public static final int Y = 9;

    /** How many slices by depth. */
    public static final int Z = 24;

    /** How many clusters there are. */
    public static final int COUNT = X * Y * Z;

    /**
     * The most lights one cluster lists. A cluster that touches more keeps the first ones, by their
     * order in the light buffer, and the overflow is counted for the debug window.
     */
    public static final int MAX_LIGHTS_PER_CLUSTER = 256;

    /**
     * Where the first slice ends, in metres. Exponential slices starting at the near plane would
     * spend half of them on the first metre, where little is lit, so the first slice takes all of
     * it.
     */
    public static final float FIRST_SLICE_DEPTH = 1;

    /**
     * The scale from the log of a depth to a slice, for a view distance: the slices after the first
     * evenly divide the log of the depth range they cover.
     *
     * @param viewDistance How far the view reaches, in metres.
     * @return The scale, {@code (Z - 1) / ln(viewDistance / FIRST_SLICE_DEPTH)}.
     */
    public static float logScale(double viewDistance) {
        // Leave the exponential slices at least a doubling of depth to divide up
        final double far = Math.max(viewDistance, 2 * FIRST_SLICE_DEPTH);
        return (float) ((Z - 1) / Math.log(far / FIRST_SLICE_DEPTH));
    }

    /**
     * Which slice a depth is in.
     *
     * @param depth The distance in front of the camera, in metres.
     * @param logScale From {@link #logScale(double)}.
     * @return The slice, from 0 to {@code Z - 1}.
     */
    public static int slice(float depth, float logScale) {
        if (depth < FIRST_SLICE_DEPTH) {
            return 0;
        }
        final int slice = 1 + (int) Math.floor(Math.log(depth / FIRST_SLICE_DEPTH) * logScale);
        return Math.min(slice, Z - 1);
    }

    /**
     * Where a slice starts.
     *
     * @param slice The slice.
     * @param logScale From {@link #logScale(double)}.
     * @return The depth of its near edge, in metres.
     */
    public static float sliceStart(int slice, float logScale) {
        if (slice == 0) {
            return 0;
        }
        return (float) (FIRST_SLICE_DEPTH * Math.exp((slice - 1) / logScale));
    }

    /**
     * Where a slice ends. The last one reaches the far plane, holding everything past the view
     * distance.
     *
     * @param slice The slice.
     * @param logScale From {@link #logScale(double)}.
     * @return The depth of its far edge, in metres.
     */
    public static float sliceEnd(int slice, float logScale) {
        return slice == Z - 1 ? Projection.Z_FAR : sliceStart(slice + 1, logScale);
    }

    /**
     * The index of a cluster in the cluster buffers.
     *
     * @param x The tile across, from the left.
     * @param y The tile down, from the top.
     * @param z The slice.
     * @return The index.
     */
    public static int index(int x, int y, int z) {
        return x + X * (y + Y * z);
    }

    /**
     * The view space box around a cluster.
     *
     * @param x The tile across, from the left.
     * @param y The tile down, from the top.
     * @param z The slice.
     * @param inverseProjection The inverse of the projection matrix.
     * @param logScale From {@link #logScale(double)}.
     * @param min Where to store the box's lowest corner.
     * @param max Where to store the box's highest corner.
     */
    public static void bounds(
            int x,
            int y,
            int z,
            @NonNull Matrix4fc inverseProjection,
            float logScale,
            @NonNull Vector3f min,
            @NonNull Vector3f max) {
        final float near = sliceStart(z, logScale);
        final float far = sliceEnd(z, logScale);
        min.set(Float.POSITIVE_INFINITY);
        max.set(Float.NEGATIVE_INFINITY);
        final Vector3f direction = new Vector3f();
        final Vector3f corner = new Vector3f();
        for (int cornerY = y; cornerY <= y + 1; ++cornerY) {
            for (int cornerX = x; cornerX <= x + 1; ++cornerX) {
                ray(cornerX / (float) X, cornerY / (float) Y, inverseProjection, direction);
                direction.mul(near, corner);
                min.min(corner);
                max.max(corner);
                direction.mul(far, corner);
                min.min(corner);
                max.max(corner);
            }
        }
    }

    /**
     * The view space ray through a point on the screen, scaled to reach one metre in front of the
     * camera.
     *
     * @param u How far across the screen, from 0 at the left to 1 at the right.
     * @param v How far down the screen, from 0 at the top to 1 at the bottom.
     * @param inverseProjection The inverse of the projection matrix.
     * @param dest Where to store the ray.
     */
    public static void ray(
            float u, float v, @NonNull Matrix4fc inverseProjection, @NonNull Vector3f dest) {
        // The same flip as lights.frag: the scene is drawn y up, the screen counts y down
        final Vector4f point = new Vector4f(u * 2 - 1, 1 - v * 2, 0.5f, 1).mul(inverseProjection);
        dest.set(point.x, point.y, point.z).div(point.w);
        dest.div(-dest.z);
    }

    /**
     * Whether a sphere touches a box.
     *
     * @param center The sphere's center.
     * @param radius The sphere's radius.
     * @param min The box's lowest corner.
     * @param max The box's highest corner.
     * @return True if any of the sphere is in the box.
     */
    public static boolean sphereTouchesBox(
            @NonNull Vector3fc center,
            float radius,
            @NonNull Vector3fc min,
            @NonNull Vector3fc max) {
        final float dx = Math.max(Math.max(min.x() - center.x(), 0), center.x() - max.x());
        final float dy = Math.max(Math.max(min.y() - center.y(), 0), center.y() - max.y());
        final float dz = Math.max(Math.max(min.z() - center.z(), 0), center.z() - max.z());
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /**
     * Whether a spotlight's cone touches a box, tested against the sphere around the box: within
     * reach of the light, inside the cone's angle, and not behind the light.
     *
     * @param position The light's position.
     * @param direction Which way it points, normalized.
     * @param range How far it reaches.
     * @param cosOuter The cosine of the angle from its axis where it ends.
     * @param min The box's lowest corner.
     * @param max The box's highest corner.
     * @return False if the cone certainly misses the box.
     */
    public static boolean coneTouchesBox(
            @NonNull Vector3fc position,
            @NonNull Vector3fc direction,
            float range,
            float cosOuter,
            @NonNull Vector3fc min,
            @NonNull Vector3fc max) {
        if (!sphereTouchesBox(position, range, min, max)) {
            return false;
        }
        final Vector3f center = new Vector3f(min).add(max).mul(0.5f);
        final float radius = new Vector3f(max).sub(min).length() * 0.5f;
        final Vector3f toCenter = center.sub(position);
        final float along = toCenter.dot(direction);
        final float sinOuter = (float) Math.sqrt(Math.max(0, 1 - cosOuter * cosOuter));
        // The distance from the sphere's center to the cone's surface, outward positive
        final float across =
                (float) Math.sqrt(Math.max(0, toCenter.lengthSquared() - along * along));
        final float distance = cosOuter * across - along * sinOuter;
        // A cone wider than a half sphere also reaches behind the light
        final boolean behind = cosOuter >= 0 && along < -radius;
        return distance <= radius && !behind;
    }

    private ClusterMath() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
