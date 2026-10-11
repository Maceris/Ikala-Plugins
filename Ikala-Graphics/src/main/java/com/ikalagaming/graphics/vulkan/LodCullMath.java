package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.lod.LodCut;
import com.ikalagaming.graphics.lod.LodDag;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The level of detail cut the GPU picks, written out in Java so it can be tested and so the shader
 * has a reference, with the layouts of the tables it reads. {@code lod_cull.comp} does this; keep
 * them in step.
 *
 * <p>The rule is {@link LodCut}'s: a cluster is drawn when its own error, projected from the
 * nearest point of its saturated sphere, is at or under the threshold, and its parent group's is
 * over it. The GPU works in floats rather than doubles, so a cluster within rounding of the
 * threshold may go the other way; the saturated errors and spheres keep every leaf-to-root path
 * switching once all the same, since a parent's float error and sphere are never under a child's.
 *
 * <p>A cluster table entry, {@value #CLUSTER_ENTRY_SIZE} bytes:
 *
 * <pre>
 * vec4 tight        // x, y, z from the world's origin, radius: around its triangles, for culling
 * vec4 bound        // its own error's saturated sphere
 * vec4 parentBound  // its parent group's sphere, its own for a root
 * uvec4 info        // float bits of its error and its parent's (infinity for a root),
 *                   // its mesh slot ({@link #NO_MESH} if it has no triangles), the slot's generation
 * </pre>
 *
 * <p>A world table entry, {@value #WORLD_ENTRY_SIZE} bytes, rewritten each frame:
 *
 * <pre>
 * vec4 renderOffset   // the world's origin minus the camera, so table positions plus it are in
 *                     // render space
 * vec4 eye            // the observer from the world's origin, which errors are projected from
 * ivec4 cameraGrid    // the camera's grid step from the origin, rounded down, see LodVertex
 * vec4 cameraFraction // the rest of the camera's position
 * uvec4 info          // its first cluster in the table, its cluster count, float bits of the
 *                     // threshold in pixels and of pixels per unit at distance 1
 * </pre>
 */
public final class LodCullMath {

    /** The size of a cluster table entry, in bytes. */
    public static final int CLUSTER_ENTRY_SIZE = 4 * 4 * Integer.BYTES;

    /** Where a cluster's tight sphere starts. */
    public static final int CLUSTER_TIGHT_OFFSET = 0;

    /** Where a cluster's own error sphere starts. */
    public static final int CLUSTER_BOUND_OFFSET = 4 * Float.BYTES;

    /** Where a cluster's parent error sphere starts. */
    public static final int CLUSTER_PARENT_BOUND_OFFSET = 2 * 4 * Float.BYTES;

    /** Where a cluster's error, parent error, mesh slot and generation start. */
    public static final int CLUSTER_INFO_OFFSET = 3 * 4 * Float.BYTES;

    /** The mesh slot of a cluster without triangles. */
    public static final int NO_MESH = -1;

    /** The size of a world table entry, in bytes. */
    public static final int WORLD_ENTRY_SIZE = 5 * 4 * Integer.BYTES;

    /** Where a world's offset into render space starts. */
    public static final int WORLD_RENDER_OFFSET_OFFSET = 0;

    /** Where the observer, from the world's origin, starts. */
    public static final int WORLD_EYE_OFFSET = 4 * Float.BYTES;

    /** Where the camera's grid step starts. */
    public static final int WORLD_CAMERA_GRID_OFFSET = 2 * 4 * Float.BYTES;

    /** Where the rest of the camera's position starts. */
    public static final int WORLD_CAMERA_FRACTION_OFFSET = 3 * 4 * Float.BYTES;

    /** Where the world's first cluster, cluster count, threshold and scale start. */
    public static final int WORLD_INFO_OFFSET = 4 * 4 * Float.BYTES;

    /** The nearest a sphere is treated as being, so errors stay finite, as in {@link LodCut}. */
    public static final float MIN_DISTANCE = 1e-6f;

    /** Values in a sphere. */
    private static final int SPHERE_VALUES = 4;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /**
     * Write a cluster's table entry.
     *
     * @param out Where it goes; written with absolute puts from its position.
     * @param dag The DAG.
     * @param cluster The cluster's id.
     * @param meshSlot Its mesh's slot, or {@link #NO_MESH}.
     * @param generation The slot's generation.
     */
    public static void putCluster(
            @NonNull ByteBuffer out,
            @NonNull LodDag dag,
            int cluster,
            int meshSlot,
            int generation) {
        final int base = out.position();
        final LodDag.Cluster c = dag.clusters().get(cluster);
        putSphere(out, base + CLUSTER_TIGHT_OFFSET, c.tight());
        putSphere(out, base + CLUSTER_BOUND_OFFSET, c.bound());
        putSphere(out, base + CLUSTER_PARENT_BOUND_OFFSET, dag.parentBound(cluster));
        out.putFloat(base + CLUSTER_INFO_OFFSET, (float) c.error());
        out.putFloat(base + CLUSTER_INFO_OFFSET + Integer.BYTES, (float) dag.parentError(cluster));
        out.putInt(base + CLUSTER_INFO_OFFSET + 2 * Integer.BYTES, meshSlot);
        out.putInt(base + CLUSTER_INFO_OFFSET + 3 * Integer.BYTES, generation);
    }

    /**
     * Write a sphere as floats.
     *
     * @param out Where it goes.
     * @param at The byte offset.
     * @param sphere x, y, z, radius.
     */
    private static void putSphere(@NonNull ByteBuffer out, int at, double @NonNull [] sphere) {
        for (int i = 0; i < SPHERE_VALUES; ++i) {
            out.putFloat(at + i * Float.BYTES, (float) sphere[i]);
        }
    }

    /**
     * Read a sphere back as floats.
     *
     * @param in The entries.
     * @param at The byte offset.
     * @return x, y, z, radius.
     */
    public static float[] sphere(@NonNull ByteBuffer in, int at) {
        final float[] sphere = new float[SPHERE_VALUES];
        for (int i = 0; i < SPHERE_VALUES; ++i) {
            sphere[i] = in.getFloat(at + i * Float.BYTES);
        }
        return sphere;
    }

    /**
     * An error projected to the screen from the nearest point of a sphere, in floats, as the shader
     * does it.
     *
     * @param error The error, in world units; infinity stays infinity.
     * @param sphere The sphere, x, y, z, radius, from the world's origin.
     * @param eye The eye, from the world's origin.
     * @param pixelsPerUnit Pixels per world unit at distance 1.
     * @return The error in pixels.
     */
    public static float projected(
            float error, float @NonNull [] sphere, float @NonNull [] eye, float pixelsPerUnit) {
        if (Float.isInfinite(error)) {
            return error;
        }
        if (error == 0) {
            return 0;
        }
        final float dx = sphere[0] - eye[0];
        final float dy = sphere[1] - eye[1];
        final float dz = sphere[2] - eye[2];
        final float distance =
                Math.max(
                        MIN_DISTANCE,
                        (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - sphere[AXES]);
        return error * pixelsPerUnit / distance;
    }

    /**
     * Whether the GPU draws a cluster.
     *
     * @param entries The cluster table.
     * @param at The byte offset of the cluster's entry.
     * @param eye The eye, from the world's origin.
     * @param pixelsPerUnit Pixels per world unit at distance 1.
     * @param thresholdPixels The most error a drawn cluster may have, in pixels.
     * @return True if it is in the cut.
     */
    public static boolean selected(
            @NonNull ByteBuffer entries,
            int at,
            float @NonNull [] eye,
            float pixelsPerUnit,
            float thresholdPixels) {
        final float own =
                projected(
                        entries.getFloat(at + CLUSTER_INFO_OFFSET),
                        sphere(entries, at + CLUSTER_BOUND_OFFSET),
                        eye,
                        pixelsPerUnit);
        final float parent =
                projected(
                        entries.getFloat(at + CLUSTER_INFO_OFFSET + Integer.BYTES),
                        sphere(entries, at + CLUSTER_PARENT_BOUND_OFFSET),
                        eye,
                        pixelsPerUnit);
        return own <= thresholdPixels && parent > thresholdPixels;
    }

    /**
     * The cut the GPU picks for a whole DAG, from entries packed as the renderer packs them.
     *
     * @param dag The DAG.
     * @param view The viewpoint.
     * @param thresholdPixels The most error a drawn cluster may have, in pixels.
     * @return Whether each cluster is drawn.
     */
    public static boolean[] select(
            @NonNull LodDag dag, LodCut.@NonNull View view, double thresholdPixels) {
        final int n = dag.clusters().size();
        final ByteBuffer entries =
                ByteBuffer.allocate(Math.max(1, n) * CLUSTER_ENTRY_SIZE)
                        .order(ByteOrder.nativeOrder());
        for (int i = 0; i < n; ++i) {
            entries.position(i * CLUSTER_ENTRY_SIZE);
            putCluster(entries, dag, i, 0, 0);
        }
        final float[] eye = {
            (float) (view.x() - dag.getOrigin()[0]),
            (float) (view.y() - dag.getOrigin()[1]),
            (float) (view.z() - dag.getOrigin()[2])
        };
        final boolean[] drawn = new boolean[n];
        for (int i = 0; i < n; ++i) {
            drawn[i] =
                    selected(
                            entries,
                            i * CLUSTER_ENTRY_SIZE,
                            eye,
                            (float) view.pixelsPerUnit(),
                            (float) thresholdPixels);
        }
        return drawn;
    }

    /** Static helpers only. */
    private LodCullMath() {}
}
