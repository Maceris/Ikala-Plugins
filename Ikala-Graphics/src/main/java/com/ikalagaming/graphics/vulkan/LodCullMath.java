package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.lod.ClusterInfo;
import com.ikalagaming.graphics.lod.LodCut;
import com.ikalagaming.graphics.lod.LodDag;

import lombok.NonNull;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

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
 * <p>Streaming changes it too: a cluster is drawn only while its unit is shown (see {@link
 * com.ikalagaming.graphics.lod.LodResidency}), and while the unit of its children, the clusters its
 * producer group consumed, isn't shown, it is drawn whatever its own error, standing in for them.
 * Shown units are closed upward, so a cluster's parent is always there.
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
 * uvec4 unit        // its streaming unit's slot, the slot of its children's unit ({@link #NONE}
 *                   // for a leaf), its level counted from the finest (its cell's level plus its
 *                   // level within the cell), for the debug coloring, its flags
 *                   // ({@link #FLAG_CUT_OUT}, {@link #FLAG_VOLUME})
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
 * uvec4 info          // the debug coloring's ordinal, 1 if it is drawn, float bits of the threshold in pixels and of
 *                     // pixels per unit at distance 1
 * vec4 rotation       // an instance's turn, a quaternion x, y, z, w
 * uvec4 model         // an instance's model: 1 + its first cluster entry (0 for a world, which draws
 *                     // its own entries), its cluster count, the instance's first pair, its remaps
 * uvec4 remapFrom     // the material indices an instance draws with others, up to MAX_REMAPS
 * uvec4 remapTo       // what it draws them with
 * </pre>
 *
 * <p>An instance of a model (see {@link LodRenderer}) has a world slot of its own: its renderOffset
 * is where the model's origin lands, minus the camera, with the instance's scale in w, and its eye
 * is the observer moved into the model's frame, from the model's origin. Errors and distances scale
 * together, so the cut is the model's own from there. Each instance owns a pair per model cluster,
 * {@value #PAIR_ENTRY_SIZE} bytes in the pair table: its world slot + 1 (0 for none) and whether
 * that cluster of it was visible last frame. Its draws carry {@link #INSTANCED} with its slot and
 * the cluster's index in the model as their instance, see {@link #drawId}.
 */
public final class LodCullMath {

    /** The size of a cluster table entry, in bytes. */
    public static final int CLUSTER_ENTRY_SIZE = 6 * 4 * Integer.BYTES;

    /**
     * A cluster flag: some of its triangles' materials have holes cut out by their textures, so it
     * is drawn into the cut out lists, with an alpha test.
     */
    public static final int FLAG_CUT_OUT = 1;

    /**
     * A cluster flag: it is a coverage volume, its mesh the boxes of its bricks, so it is drawn
     * into the volume lists, walked by {@code scene_volume.frag}.
     */
    public static final int FLAG_VOLUME = 2;

    /**
     * Where a coverage volume cluster's flags keep how many of its mesh's indices are its bricks'
     * boxes, the rest its shell's, which it is drawn as once small on screen: 0 for no shell.
     */
    public static final int SHELL_SHIFT = 8;

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

    /** Where a cluster's unit slot, its children's unit slot and its level start. */
    public static final int CLUSTER_UNIT_OFFSET = 5 * 4 * Float.BYTES;

    /** The mesh slot of a cluster without triangles. */
    public static final int NO_MESH = -1;

    /** A cell or section slot that isn't there. */
    public static final int NONE = -1;

    /** Set in a cell's flags while it is stale. */
    public static final int FLAG_STALE = 1;

    /** Bits in a word of the hidden section set. */
    public static final int BITS_PER_WORD = Integer.SIZE;

    /** The size of a world table entry, in bytes. */
    public static final int WORLD_ENTRY_SIZE = 9 * 4 * Integer.BYTES;

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

    /** Where an instance's rotation starts. */
    public static final int WORLD_ROTATION_OFFSET = 5 * 4 * Float.BYTES;

    /** Where an instance's model, first pair and remap count start. */
    public static final int WORLD_MODEL_OFFSET = 6 * 4 * Float.BYTES;

    /** Where the material indices an instance replaces start. */
    public static final int WORLD_REMAP_FROM_OFFSET = 7 * 4 * Float.BYTES;

    /** Where their replacements start. */
    public static final int WORLD_REMAP_TO_OFFSET = 8 * 4 * Float.BYTES;

    /** The most materials an instance can replace. */
    public static final int MAX_REMAPS = 4;

    /** The size of a pair table entry, in bytes. */
    public static final int PAIR_ENTRY_SIZE = 2 * Integer.BYTES;

    /** Set in a draw's instance when it is a cluster of an instance. */
    public static final int INSTANCED = 1 << 31;

    /** Where an instanced draw's world slot starts in its instance. */
    public static final int INSTANCE_SHIFT = 16;

    /** The most world slots an instance can have, so its slot fits beside the cluster's index. */
    public static final int MAX_INSTANCE_SLOTS = 1 << (Integer.SIZE - 1 - INSTANCE_SHIFT);

    /** The most clusters a model can have, so a cluster's index fits in an instanced draw. */
    public static final int MAX_MODEL_CLUSTERS = 1 << INSTANCE_SHIFT;

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
     * @param unit Its streaming unit's slot, or {@link #NONE} if everything is shown.
     * @param childUnit The slot of the unit its producer group consumed, or {@link #NONE}.
     * @param level Its level counted from the finest: its cell's level plus its level within the
     *     cell.
     */
    public record Place(
            int world, int cell, int parentCell, int section, int unit, int childUnit, int level) {

        /** No world, cell, section or units: how the cut of a lone DAG is tested. */
        public static final Place LONE = new Place(0, NONE, NONE, NONE, NONE, NONE, 0);
    }

    /**
     * Write a cluster's table entry.
     *
     * @param out Where it goes; written with absolute puts from its position.
     * @param cluster What the cluster is.
     * @param parentError Its parent group's error, infinity for a root.
     * @param parentBound Its parent group's sphere.
     * @param meshSlot Its mesh's slot, or {@link #NO_MESH}.
     * @param generation The slot's generation.
     * @param place Where it sits.
     * @param flags Its flags, such as {@link #FLAG_CUT_OUT}.
     */
    public static void putCluster(
            @NonNull ByteBuffer out,
            @NonNull ClusterInfo cluster,
            double parentError,
            double @NonNull [] parentBound,
            int meshSlot,
            int generation,
            @NonNull Place place,
            int flags) {
        final int base = out.position();
        putSphere(out, base + CLUSTER_TIGHT_OFFSET, cluster.tight());
        putSphere(out, base + CLUSTER_BOUND_OFFSET, cluster.bound());
        putSphere(out, base + CLUSTER_PARENT_BOUND_OFFSET, parentBound);
        // A leaf has nothing finer under it, so whatever its error, it is fine enough to draw;
        // coarse leaves' errors count only in the groups above them
        out.putFloat(
                base + CLUSTER_INFO_OFFSET, cluster.level() == 0 ? 0 : (float) cluster.error());
        out.putFloat(base + CLUSTER_INFO_OFFSET + Integer.BYTES, (float) parentError);
        out.putInt(base + CLUSTER_INFO_OFFSET + 2 * Integer.BYTES, meshSlot);
        out.putInt(base + CLUSTER_INFO_OFFSET + 3 * Integer.BYTES, generation);
        out.putInt(base + CLUSTER_PLACE_OFFSET, place.world() + 1);
        out.putInt(base + CLUSTER_PLACE_OFFSET + Integer.BYTES, place.cell());
        out.putInt(base + CLUSTER_PLACE_OFFSET + 2 * Integer.BYTES, place.parentCell());
        out.putInt(base + CLUSTER_PLACE_OFFSET + 3 * Integer.BYTES, place.section());
        out.putInt(base + CLUSTER_UNIT_OFFSET, place.unit());
        out.putInt(base + CLUSTER_UNIT_OFFSET + Integer.BYTES, place.childUnit());
        out.putInt(base + CLUSTER_UNIT_OFFSET + 2 * Integer.BYTES, place.level());
        out.putInt(base + CLUSTER_UNIT_OFFSET + 3 * Integer.BYTES, flags);
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
                ClusterInfo.of(dag.clusters().get(cluster)),
                dag.parentError(cluster),
                dag.parentBound(cluster),
                meshSlot,
                generation,
                Place.LONE,
                0);
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
     * @param shownUnits The shown unit set, one bit per unit slot; units with no slot are shown.
     * @return True if it is in the cut.
     */
    public static boolean selected(
            @NonNull ByteBuffer entries,
            int at,
            float @NonNull [] eye,
            float pixelsPerUnit,
            float thresholdPixels,
            int @NonNull [] cellFlags,
            int @NonNull [] hidden,
            int @NonNull [] shownUnits) {
        final int unit = entries.getInt(at + CLUSTER_UNIT_OFFSET);
        if (unit != NONE && !isHidden(shownUnits, unit)) {
            return false;
        }
        final int childUnit = entries.getInt(at + CLUSTER_UNIT_OFFSET + Integer.BYTES);
        // Standing in for children that aren't there
        final boolean forced = childUnit != NONE && !isHidden(shownUnits, childUnit);
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
        return (own <= thresholdPixels || forced) && parent > thresholdPixels;
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
     * Whether a slot's bit is set in a bit set: a hidden section, or a shown unit.
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
        return select(
                entries,
                n,
                dag.getOrigin(),
                view,
                thresholdPixels,
                new int[0],
                new int[0],
                new int[0]);
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
     * @param shownUnits The shown unit set, one bit per unit slot.
     * @return Whether each entry is drawn.
     */
    public static boolean[] select(
            @NonNull ByteBuffer entries,
            int count,
            double @NonNull [] origin,
            LodCut.@NonNull View view,
            double thresholdPixels,
            int @NonNull [] cellFlags,
            int @NonNull [] hidden,
            int @NonNull [] shownUnits) {
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
                            hidden,
                            shownUnits);
        }
        return drawn;
    }

    /**
     * The instance an instanced draw carries.
     *
     * @param slot The instance's world slot, under {@link #MAX_INSTANCE_SLOTS}.
     * @param cluster The cluster's index in the model, under {@link #MAX_MODEL_CLUSTERS}.
     * @return The draw's instance.
     */
    public static int drawId(int slot, int cluster) {
        return INSTANCED | slot << INSTANCE_SHIFT | cluster;
    }

    /**
     * Where the observer is in a model's frame, as an instance of it sees it.
     *
     * @param eye The observer, in the world.
     * @param position Where the instance puts the model's frame, in the world.
     * @param rotation The instance's turn.
     * @param scale The instance's scale.
     * @param origin The model's origin, in its frame.
     * @return The observer from the model's origin, in model units.
     */
    public static double[] modelEye(
            double @NonNull [] eye,
            double @NonNull [] position,
            @NonNull Quaterniondc rotation,
            double scale,
            double @NonNull [] origin) {
        final Vector3d at =
                new Vector3d(eye[0] - position[0], eye[1] - position[1], eye[2] - position[2]);
        rotation.transformInverse(at).div(scale);
        return new double[] {at.x - origin[0], at.y - origin[1], at.z - origin[2]};
    }

    /**
     * A model's sphere where an instance puts it.
     *
     * @param sphere The sphere, x, y, z from the model's origin, radius.
     * @param position Where the instance puts the model's frame, in the world.
     * @param rotation The instance's turn.
     * @param scale The instance's scale.
     * @param origin The model's origin, in its frame.
     * @return The sphere in the world.
     */
    public static double[] instanceSphere(
            double @NonNull [] sphere,
            double @NonNull [] position,
            @NonNull Quaterniondc rotation,
            double scale,
            double @NonNull [] origin) {
        final Vector3d at =
                new Vector3d(origin[0] + sphere[0], origin[1] + sphere[1], origin[2] + sphere[2]);
        rotation.transform(at.mul(scale));
        return new double[] {
            at.x + position[0], at.y + position[1], at.z + position[2], sphere[AXES] * scale
        };
    }

    /**
     * The cut the GPU picks for an instance of a whole DAG, from its eye in the model's frame.
     *
     * @param dag The DAG.
     * @param view The viewpoint, in the world.
     * @param thresholdPixels The most error a drawn cluster may have, in pixels.
     * @param position Where the instance puts the model's frame, in the world.
     * @param rotation The instance's turn.
     * @param scale The instance's scale.
     * @return Whether each cluster is drawn.
     */
    public static boolean[] selectInstance(
            @NonNull LodDag dag,
            LodCut.@NonNull View view,
            double thresholdPixels,
            double @NonNull [] position,
            @NonNull Quaterniondc rotation,
            double scale) {
        final double[] origin = dag.getOrigin();
        final double[] eye =
                modelEye(
                        new double[] {view.x(), view.y(), view.z()},
                        position,
                        rotation,
                        scale,
                        origin);
        return select(
                dag,
                new LodCut.View(
                        eye[0] + origin[0],
                        eye[1] + origin[1],
                        eye[2] + origin[2],
                        view.pixelsPerUnit()),
                thresholdPixels);
    }

    /** Static helpers only. */
    private LodCullMath() {}
}
