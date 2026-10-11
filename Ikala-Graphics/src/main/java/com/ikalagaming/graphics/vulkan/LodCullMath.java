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
 * <p>Two flags change the rule while a live world catches up with its sections. A cell marked stale
 * can't have its clusters above the leaves drawn, and a cluster whose parent is in a stale cell
 * treats its parent's error as infinite, so the cut goes down to fresh clusters there. A hidden
 * section's leaves aren't drawn at all, since the section draws itself.
 *
 * <p>A cluster table entry, {@value #CLUSTER_ENTRY_SIZE} bytes:
 *
 * <pre>
 * vec4 tight        // x, y, z from the world's origin, radius: around its triangles, for culling
 * vec4 bound        // its own error's saturated sphere
 * vec4 parentBound  // its parent group's sphere, its own for a root
 * uvec4 info        // float bits of its error and its parent's (infinity for a root),
 *                   // its mesh slot ({@link #NO_MESH} if it has no triangles), the slot's generation
 * uvec4 place       // its world's slot + 1 (0 for an empty entry), its cell's slot, the slot of the
 *                   // cell holding its parent ({@link #NONE} for a root), its section's slot for a
 *                   // leaf ({@link #NONE} above the leaves)
 * </pre>
 *
 * <p>A world table entry, {@value #WORLD_ENTRY_SIZE} bytes, one per world slot, rewritten each
 * frame:
 *
 * <pre>
 * vec4 renderOffset   // the world's origin minus the camera, so table positions plus it are in
 *                     // render space
 * vec4 eye            // the observer from the world's origin, which errors are projected from
 * ivec4 cameraGrid    // the camera's grid step from the origin, rounded down, see LodVertex
 * vec4 cameraFraction // the rest of the camera's position
 * uvec4 info          // unused, 1 if it is drawn, float bits of the threshold in pixels and of
 *                     // pixels per unit at distance 1
 * </pre>
 */
public final class LodCullMath {

    /** The size of a cluster table entry, in bytes. */
    public static final int CLUSTER_ENTRY_SIZE = 5 * 4 * Integer.BYTES;

    /** Where a cluster's tight sphere starts. */
    public static final int CLUSTER_TIGHT_OFFSET = 0;

    /** Where a cluster's own error sphere starts. */
    public static final int CLUSTER_BOUND_OFFSET = 4 * Float.BYTES;

    /** Where a cluster's parent error sphere starts. */
    public static final int CLUSTER_PARENT_BOUND_OFFSET = 2 * 4 * Float.BYTES;

    /** Where a cluster's error, parent error, mesh slot and generation start. */
    public static final int CLUSTER_INFO_OFFSET = 3 * 4 * Float.BYTES;

    /** Where a cluster's world, cell, parent cell and section slots start. */
    public static final int CLUSTER_PLACE_OFFSET = 4 * 4 * Float.BYTES;

    /** The mesh slot of a cluster without triangles. */
    public static final int NO_MESH = -1;

    /** A cell or section slot that isn't there. */
    public static final int NONE = -1;

    /** Set in a cell's flags while it is stale. */
    public static final int FLAG_STALE = 1;

    /** Bits in a word of the hidden section set. */
    public static final int BITS_PER_WORD = Integer.SIZE;

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

    /** Where whether the world is drawn, its threshold and scale start. */
    public static final int WORLD_INFO_OFFSET = 4 * 4 * Float.BYTES;

    /** The nearest a sphere is treated as being, so errors stay finite, as in {@link LodCut}. */
    public static final float MIN_DISTANCE = 1e-6f;

    /** Values in a sphere. */
    private static final int SPHERE_VALUES = 4;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /**
     * Where a cluster sits among worlds, cells and sections.
     *
     * @param world Its world's slot.
     * @param cell Its cell's slot.
     * @param parentCell The slot of the cell holding its parent, or {@link #NONE}.
     * @param section Its section's slot for a leaf, or {@link #NONE}.
     */
    public record Place(int world, int cell, int parentCell, int section) {

        /** No world, cell or section: how the cut of a lone DAG is tested. */
        public static final Place LONE = new Place(0, NONE, NONE, NONE);
    }

    /**
     * Write a cluster's table entry.
     *
     * @param out Where it goes; written with absolute puts from its position.
     * @param cluster The cluster.
     * @param parentError Its parent group's error, infinity for a root.
     * @param parentBound Its parent group's sphere.
     * @param meshSlot Its mesh's slot, or {@link #NO_MESH}.
     * @param generation The slot's generation.
     * @param place Where it sits.
     */
    public static void putCluster(
            @NonNull ByteBuffer out,
            LodDag.@NonNull Cluster cluster,
            double parentError,
            double @NonNull [] parentBound,
            int meshSlot,
            int generation,
            @NonNull Place place) {
        final int base = out.position();
        putSphere(out, base + CLUSTER_TIGHT_OFFSET, cluster.tight());
        putSphere(out, base + CLUSTER_BOUND_OFFSET, cluster.bound());
        putSphere(out, base + CLUSTER_PARENT_BOUND_OFFSET, parentBound);
        out.putFloat(base + CLUSTER_INFO_OFFSET, (float) cluster.error());
        out.putFloat(base + CLUSTER_INFO_OFFSET + Integer.BYTES, (float) parentError);
        out.putInt(base + CLUSTER_INFO_OFFSET + 2 * Integer.BYTES, meshSlot);
        out.putInt(base + CLUSTER_INFO_OFFSET + 3 * Integer.BYTES, generation);
        out.putInt(base + CLUSTER_PLACE_OFFSET, place.world() + 1);
        out.putInt(base + CLUSTER_PLACE_OFFSET + Integer.BYTES, place.cell());
        out.putInt(base + CLUSTER_PLACE_OFFSET + 2 * Integer.BYTES, place.parentCell());
        out.putInt(base + CLUSTER_PLACE_OFFSET + 3 * Integer.BYTES, place.section());
    }

    /**
     * Write the table entry of a cluster of a lone DAG.
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
        putCluster(
                out,
                dag.clusters().get(cluster),
                dag.parentError(cluster),
                dag.parentBound(cluster),
                meshSlot,
                generation,
                Place.LONE);
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
     * @param cellFlags Each cell slot's flags.
     * @param hidden The hidden section set, one bit per section slot.
     * @return True if it is in the cut.
     */
    public static boolean selected(
            @NonNull ByteBuffer entries,
            int at,
            float @NonNull [] eye,
            float pixelsPerUnit,
            float thresholdPixels,
            int @NonNull [] cellFlags,
            int @NonNull [] hidden) {
        final int cell = entries.getInt(at + CLUSTER_PLACE_OFFSET + Integer.BYTES);
        final int parentCell = entries.getInt(at + CLUSTER_PLACE_OFFSET + 2 * Integer.BYTES);
        final int section = entries.getInt(at + CLUSTER_PLACE_OFFSET + 3 * Integer.BYTES);
        final boolean leaf = section != NONE;
        if (leaf && isHidden(hidden, section)) {
            return false;
        }
        if (!leaf && isStale(cellFlags, cell)) {
            return false;
        }
        final float own =
                projected(
                        entries.getFloat(at + CLUSTER_INFO_OFFSET),
                        sphere(entries, at + CLUSTER_BOUND_OFFSET),
                        eye,
                        pixelsPerUnit);
        final float parent =
                isStale(cellFlags, parentCell)
                        ? Float.POSITIVE_INFINITY
                        : projected(
                                entries.getFloat(at + CLUSTER_INFO_OFFSET + Integer.BYTES),
                                sphere(entries, at + CLUSTER_PARENT_BOUND_OFFSET),
                                eye,
                                pixelsPerUnit);
        return own <= thresholdPixels && parent > thresholdPixels;
    }

    /**
     * Whether a cell slot is stale.
     *
     * @param cellFlags Each cell slot's flags.
     * @param cell The slot, or {@link #NONE}.
     * @return True if it is there and stale.
     */
    private static boolean isStale(int @NonNull [] cellFlags, int cell) {
        return cell != NONE && cell < cellFlags.length && (cellFlags[cell] & FLAG_STALE) != 0;
    }

    /**
     * Whether a section slot is in the hidden set.
     *
     * @param hidden The set, one bit per slot.
     * @param section The slot.
     * @return True if its bit is set.
     */
    public static boolean isHidden(int @NonNull [] hidden, int section) {
        final int word = section / BITS_PER_WORD;
        return word < hidden.length && (hidden[word] >>> (section % BITS_PER_WORD) & 1) != 0;
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
        return select(entries, n, dag.getOrigin(), view, thresholdPixels, new int[0], new int[0]);
    }

    /**
     * The cut the GPU picks from packed entries, with cell flags and hidden sections.
     *
     * @param entries The cluster table.
     * @param count How many entries it has.
     * @param origin The world's origin.
     * @param view The viewpoint.
     * @param thresholdPixels The most error a drawn cluster may have, in pixels.
     * @param cellFlags Each cell slot's flags.
     * @param hidden The hidden section set, one bit per section slot.
     * @return Whether each entry is drawn.
     */
    public static boolean[] select(
            @NonNull ByteBuffer entries,
            int count,
            double @NonNull [] origin,
            LodCut.@NonNull View view,
            double thresholdPixels,
            int @NonNull [] cellFlags,
            int @NonNull [] hidden) {
        final float[] eye = {
            (float) (view.x() - origin[0]),
            (float) (view.y() - origin[1]),
            (float) (view.z() - origin[2])
        };
        final boolean[] drawn = new boolean[count];
        for (int i = 0; i < count; ++i) {
            drawn[i] =
                    selected(
                            entries,
                            i * CLUSTER_ENTRY_SIZE,
                            eye,
                            (float) view.pixelsPerUnit(),
                            (float) thresholdPixels,
                            cellFlags,
                            hidden);
        }
        return drawn;
    }

    /** Static helpers only. */
    private LodCullMath() {}
}
