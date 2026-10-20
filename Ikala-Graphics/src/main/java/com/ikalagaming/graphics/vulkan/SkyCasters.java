package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.MeshKind;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;

import lombok.NonNull;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.BitSet;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Which placed instances the sky occlusion map holds, and where, so it is drawn again where one is
 * placed, removed, changed, or stops or starts moving. The map is kept from frame to frame, so
 * instances that stay put are drawn into it like what doesn't move; those that keep moving are left
 * out, see {@link InstanceRegistry#SETTLE_ROUNDS}.
 *
 * <p>Only standard meshes of models that aren't animated count: baked meshes are sections, which
 * report their own changes, and animated models move.
 *
 * <p>An instance placed before its meshes are uploaded waits until they are, since the map can't
 * draw them before, then has its box drawn again.
 *
 * <p>Render thread only.
 */
final class SkyCasters {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Values in a box, min x, y, z then max x, y, z. */
    private static final int BOX_VALUES = 2 * AXES;

    /** Corners of a box. */
    private static final int BOX_CORNERS = 8;

    /** Each slot's box in the world as the map holds it, or null if the map holds nothing. */
    private double[][] boxes = new double[0][];

    /** Each waiting slot's model. */
    private Model[] models = new Model[0];

    /** Slots in the map whose meshes weren't all uploaded yet. */
    private final BitSet waiting = new BitSet();

    /**
     * Note that an instance table entry changed.
     *
     * @param slot The slot.
     * @param alive Whether the slot holds an instance.
     * @param position Its world position.
     * @param rotation Its rotation.
     * @param scale Its uniform scale.
     * @param model Its model, or null if the slot holds none.
     * @param moving Whether it is moving, which leaves it out of the map.
     * @param resident Whether a mesh is uploaded, so the map can draw it.
     * @param out Receives the boxes in the world to draw again.
     */
    void changed(
            int slot,
            boolean alive,
            @NonNull Vector3dc position,
            @NonNull Quaternionfc rotation,
            float scale,
            @Nullable Model model,
            boolean moving,
            @NonNull Predicate<MeshHandle> resident,
            ShadowInvalidation.@NonNull Visitor out) {
        if (slot >= boxes.length) {
            final int length = Math.max(slot + 1, boxes.length * 2);
            boxes = Arrays.copyOf(boxes, length);
            models = Arrays.copyOf(models, length);
        }
        final double[] old = boxes[slot];
        if (old != null) {
            if (!waiting.get(slot)) {
                out.changed(min(old), max(old));
            }
            boxes[slot] = null;
            models[slot] = null;
            waiting.clear(slot);
        }
        if (!alive || moving || model == null || model.isAnimated()) {
            return;
        }
        final double[] box = boxOf(model, position, rotation, scale);
        if (box == null) {
            return;
        }
        boxes[slot] = box;
        if (uploaded(model, resident)) {
            out.changed(min(box), max(box));
        } else {
            models[slot] = model;
            waiting.set(slot);
        }
    }

    /**
     * Draw again where instances waited for their meshes, now uploaded.
     *
     * @param resident Whether a mesh is uploaded.
     * @param out Receives the boxes in the world to draw again.
     */
    void settle(@NonNull Predicate<MeshHandle> resident, ShadowInvalidation.@NonNull Visitor out) {
        for (int slot = waiting.nextSetBit(0); slot >= 0; slot = waiting.nextSetBit(slot + 1)) {
            if (uploaded(models[slot], resident)) {
                out.changed(min(boxes[slot]), max(boxes[slot]));
                models[slot] = null;
                waiting.clear(slot);
            }
        }
    }

    /**
     * How many instances wait for their meshes.
     *
     * @return The count.
     */
    int waitingCount() {
        return waiting.cardinality();
    }

    /**
     * Whether every standard mesh of a model is uploaded.
     *
     * @param model The model.
     * @param resident Whether a mesh is uploaded.
     * @return True if they all are.
     */
    private static boolean uploaded(@NonNull Model model, @NonNull Predicate<MeshHandle> resident) {
        for (MeshData data : model.getMeshDataList()) {
            final MeshHandle mesh = data.getMesh();
            if (mesh != null && mesh.kind() == MeshKind.STANDARD && !resident.test(mesh)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The box in the world around an instance's standard meshes.
     *
     * @param model The model.
     * @param position The world position.
     * @param rotation The rotation.
     * @param scale The uniform scale.
     * @return The box, or null if it has no standard meshes.
     */
    @Nullable static double[] boxOf(
            @NonNull Model model,
            @NonNull Vector3dc position,
            @NonNull Quaternionfc rotation,
            float scale) {
        final Vector3f low = new Vector3f(Float.POSITIVE_INFINITY);
        final Vector3f high = new Vector3f(Float.NEGATIVE_INFINITY);
        boolean any = false;
        for (MeshData data : model.getMeshDataList()) {
            final MeshHandle mesh = data.getMesh();
            if (mesh == null || mesh.kind() != MeshKind.STANDARD) {
                continue;
            }
            low.min(mesh.aabbMin());
            high.max(mesh.aabbMax());
            any = true;
        }
        if (!any) {
            return null;
        }
        final double[] box = {
            Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            Double.NEGATIVE_INFINITY
        };
        final Vector3f corner = new Vector3f();
        final Vector3d world = new Vector3d();
        for (int i = 0; i < BOX_CORNERS; ++i) {
            corner.set(
                    (i & 1) == 0 ? low.x : high.x,
                    (i & 2) == 0 ? low.y : high.y,
                    (i & 4) == 0 ? low.z : high.z);
            rotation.transform(corner.mul(scale));
            world.set(corner).add(position);
            for (int axis = 0; axis < AXES; ++axis) {
                box[axis] = Math.min(box[axis], world.get(axis));
                box[AXES + axis] = Math.max(box[AXES + axis], world.get(axis));
            }
        }
        return box;
    }

    /**
     * A box's minimum corner.
     *
     * @param box The box.
     * @return Its minimum corner.
     */
    private static double[] min(double @NonNull [] box) {
        return Arrays.copyOfRange(box, 0, AXES);
    }

    /**
     * A box's maximum corner.
     *
     * @param box The box.
     * @return Its maximum corner.
     */
    private static double[] max(double @NonNull [] box) {
        return Arrays.copyOfRange(box, AXES, BOX_VALUES);
    }
}
