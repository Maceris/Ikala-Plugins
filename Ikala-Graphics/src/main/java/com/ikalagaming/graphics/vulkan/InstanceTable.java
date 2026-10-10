package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.Model;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Quaternionfc;
import org.joml.Vector3dc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nullable;

/**
 * The GPU's copy of every instance in the scene, kept in device-local memory across frames, in
 * three tables:
 *
 * <ul>
 *   <li>The instance table, one entry per {@link InstanceRegistry} slot.
 *   <li>The model mesh list: for each model with instances, its meshes, as mesh table slots with
 *       their generations and the meshes' own materials.
 *   <li>The override list: for each instance, the material override of each mesh, 0 for none.
 * </ul>
 *
 * <p>Only the entries that changed are copied in, at the start of a frame, so a scene that isn't
 * changing costs nothing to keep up to date. The same snapshot also gives each model's instance
 * count, which the draw stage uses to set aside room for each mesh's visible instances.
 *
 * <p>World positions are doubles on the CPU. Each entry stores one as two floats, a high part and
 * the low part left over, and the camera is split the same way, so shaders work out camera-relative
 * positions as {@code (high - cameraHigh) + (low - cameraLow)}: exact near the camera wherever it
 * is, without doubles on the GPU. See {@link #high(double)} and {@link #low(double)}.
 *
 * <p>Instance entry layout (std430), {@link #ENTRY_SIZE} bytes:
 *
 * <pre>
 * vec4 positionHigh;  // xyz: high part of the world position, w: uniform scale
 * vec4 positionLow;   // xyz: low part of the world position, w: unused
 * vec4 rotation;      // quaternion x, y, z, w
 * uvec4 info;         // x: flags (FLAG_ALIVE, visibility mask in bits 8-15),
 *                     // y: first model mesh, z: mesh count, w: first override
 * </pre>
 *
 * <p>Model mesh entry, {@link #MODEL_MESH_SIZE} bytes: {@code uvec4(meshSlot, meshGeneration,
 * material, 0)}, with {@link #NO_MESH} as the slot when the mesh isn't registered.
 */
public class InstanceTable {

    /** The size of one instance entry, in bytes. */
    public static final int ENTRY_SIZE = 4 * 4 * Float.BYTES;

    /** Where the high part of the position and the scale start in an entry. */
    public static final int POSITION_HIGH_OFFSET = 0;

    /** Where the low part of the position starts in an entry. */
    public static final int POSITION_LOW_OFFSET = 4 * Float.BYTES;

    /** Where the rotation starts in an entry. */
    public static final int ROTATION_OFFSET = 2 * 4 * Float.BYTES;

    /** Where the flags start in an entry. */
    public static final int INFO_OFFSET = 3 * 4 * Float.BYTES;

    /** Set in the flags when the slot holds an instance. */
    public static final int FLAG_ALIVE = 1;

    /** Where the visibility mask starts in the flags, for ray tracing to filter instances by. */
    public static final int VISIBILITY_MASK_SHIFT = 8;

    /** Every visibility bit, which is what instances get until something needs fewer. */
    public static final int VISIBLE_TO_ALL = 0xFF;

    /** The size of one model mesh entry, in bytes. */
    public static final int MODEL_MESH_SIZE = 4 * Integer.BYTES;

    /** The mesh slot of a model mesh entry whose mesh isn't registered. */
    public static final int NO_MESH = -1;

    /** The size of one material override entry, in bytes. */
    public static final int OVERRIDE_SIZE = Integer.BYTES;

    /** How many slots the instance table starts out holding. */
    public static final int INITIAL_SLOTS = 1024;

    /**
     * A model with instances this frame, and how many.
     *
     * @param model The model.
     * @param instanceCount How many instances it has.
     */
    public record ModelCount(@NonNull Model model, int instanceCount) {}

    /**
     * What the tables held after this frame's upload.
     *
     * @param models Every model with instances, and how many each has.
     * @param slotCapacity How many instance slots there are.
     */
    public record Snapshot(@NonNull List<ModelCount> models, int slotCapacity) {}

    /**
     * Which instances are where. -- GETTER -- Which instances are where.
     *
     * @return The instance registry.
     */
    @Getter private final InstanceRegistry registry = new InstanceRegistry();

    /**
     * The instance table. -- GETTER -- The instance table.
     *
     * @return The instance table.
     */
    @Getter private final DeviceTable instances;

    /**
     * The model mesh list. -- GETTER -- The model mesh list.
     *
     * @return The model mesh list.
     */
    @Getter private final DeviceTable modelMeshes;

    /**
     * The material override list. -- GETTER -- The material override list.
     *
     * @return The material override list.
     */
    @Getter private final DeviceTable overrides;

    /**
     * What the tables held after the last upload. Render thread only. -- GETTER -- What the tables
     * held after the last upload.
     *
     * @return The snapshot.
     */
    @Getter private Snapshot snapshot = new Snapshot(List.of(), 0);

    /**
     * How many instances each pass drew, as last read back from the GPU, scene first. -- GETTER --
     * How many instances each pass drew.
     *
     * @return The counts, or an empty array before any are read back. Don't change it.
     */
    @Getter private volatile int[] drawnCounts = new int[0];

    /** Removed instances whose slots can be reused once frames in flight are done with them. */
    private final Queue<Integer> retiring = new ConcurrentLinkedQueue<>();

    /**
     * Create the tables.
     *
     * @param state The Vulkan state.
     */
    public InstanceTable(@NonNull VulkanState state) {
        instances = new DeviceTable(state, "instance", ENTRY_SIZE, INITIAL_SLOTS);
        modelMeshes = new DeviceTable(state, "model mesh", MODEL_MESH_SIZE, 256);
        overrides = new DeviceTable(state, "material override", OVERRIDE_SIZE, 1024);
    }

    /**
     * The high part of a coordinate: the nearest float.
     *
     * @param value The coordinate.
     * @return The high part.
     */
    public static float high(double value) {
        return (float) value;
    }

    /**
     * The low part of a coordinate: what the high part leaves over, as a float.
     *
     * @param value The coordinate.
     * @return The low part.
     */
    public static float low(double value) {
        return (float) (value - (float) value);
    }

    /**
     * Write one instance entry.
     *
     * @param out Where to write it, at its position, which is left unchanged.
     * @param alive Whether the slot holds an instance.
     * @param position The world position.
     * @param rotation The rotation.
     * @param scale The uniform scale.
     * @param meshFirst Where its model's meshes start in the model mesh list.
     * @param meshCount How many meshes its model has.
     * @param overrideFirst Where its material overrides start in the override list.
     */
    public static void writeEntry(
            @NonNull ByteBuffer out,
            boolean alive,
            @NonNull Vector3dc position,
            @NonNull Quaternionfc rotation,
            float scale,
            int meshFirst,
            int meshCount,
            int overrideFirst) {
        final int base = out.position();
        out.putFloat(base + POSITION_HIGH_OFFSET, high(position.x()));
        out.putFloat(base + POSITION_HIGH_OFFSET + Float.BYTES, high(position.y()));
        out.putFloat(base + POSITION_HIGH_OFFSET + 2 * Float.BYTES, high(position.z()));
        out.putFloat(base + POSITION_HIGH_OFFSET + 3 * Float.BYTES, scale);
        out.putFloat(base + POSITION_LOW_OFFSET, low(position.x()));
        out.putFloat(base + POSITION_LOW_OFFSET + Float.BYTES, low(position.y()));
        out.putFloat(base + POSITION_LOW_OFFSET + 2 * Float.BYTES, low(position.z()));
        out.putFloat(base + POSITION_LOW_OFFSET + 3 * Float.BYTES, 0);
        out.putFloat(base + ROTATION_OFFSET, rotation.x());
        out.putFloat(base + ROTATION_OFFSET + Float.BYTES, rotation.y());
        out.putFloat(base + ROTATION_OFFSET + 2 * Float.BYTES, rotation.z());
        out.putFloat(base + ROTATION_OFFSET + 3 * Float.BYTES, rotation.w());
        final int flags = alive ? FLAG_ALIVE | VISIBLE_TO_ALL << VISIBILITY_MASK_SHIFT : 0;
        out.putInt(base + INFO_OFFSET, flags);
        out.putInt(base + INFO_OFFSET + Integer.BYTES, alive ? meshFirst : 0);
        out.putInt(base + INFO_OFFSET + 2 * Integer.BYTES, alive ? meshCount : 0);
        out.putInt(base + INFO_OFFSET + 3 * Integer.BYTES, alive ? overrideFirst : 0);
    }

    /**
     * Write one model mesh entry.
     *
     * @param out Where to write it, at its position, which is left unchanged.
     * @param mesh The mesh, or null if it isn't registered.
     * @param materialIndex The mesh's own material index.
     */
    public static void writeModelMesh(
            @NonNull ByteBuffer out, @Nullable MeshHandle mesh, int materialIndex) {
        final int base = out.position();
        out.putInt(base, mesh == null ? NO_MESH : mesh.slot());
        out.putInt(base + Integer.BYTES, mesh == null ? 0 : mesh.generation());
        out.putInt(base + 2 * Integer.BYTES, materialIndex);
        out.putInt(base + 3 * Integer.BYTES, 0);
    }

    /**
     * Note that an instance was removed, so its slot is reused once no frame in flight can read it.
     * Safe from any thread.
     *
     * @param slot The slot {@link InstanceRegistry} returned, or -1 for nothing.
     */
    public void retire(int slot) {
        if (slot >= 0) {
            retiring.add(slot);
        }
    }

    /**
     * Note how many instances each pass drew, as read back from the GPU.
     *
     * @param counts The counts, scene first.
     */
    public void setDrawnCounts(int @NonNull [] counts) {
        drawnCounts = counts.clone();
    }

    /**
     * Copy in everything that changed, growing the tables if needed, and take this frame's
     * snapshot. Render thread only, at the start of a frame, before anything reads the tables.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The frame's command buffer, recording.
     * @param materials Looks up material indices for the GPU.
     */
    public void record(
            @NonNull VulkanState state,
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull MaterialCache materials) {
        Integer slot;
        while ((slot = retiring.poll()) != null) {
            final int free = slot;
            state.deferFree(() -> registry.free(free));
        }

        final DeviceTable.Entries instanceEntries = new DeviceTable.Entries(ENTRY_SIZE);
        final DeviceTable.Entries meshEntries = new DeviceTable.Entries(MODEL_MESH_SIZE);
        final DeviceTable.Entries overrideEntries = new DeviceTable.Entries(OVERRIDE_SIZE);
        final List<ModelCount> models = new ArrayList<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final InstanceRegistry.Capacities capacities =
                    registry.takeChanges(
                            new InstanceRegistry.Changes() {
                                @Override
                                public void instance(
                                        int changed,
                                        boolean alive,
                                        @NonNull Vector3dc position,
                                        @NonNull Quaternionfc rotation,
                                        float scale,
                                        int meshFirst,
                                        int meshCount,
                                        int overrideFirst) {
                                    writeEntry(
                                            instanceEntries.next(changed),
                                            alive,
                                            position,
                                            rotation,
                                            scale,
                                            meshFirst,
                                            meshCount,
                                            overrideFirst);
                                }

                                @Override
                                public void modelMesh(
                                        int index,
                                        @Nullable MeshHandle mesh,
                                        @Nullable Material material) {
                                    writeModelMesh(
                                            meshEntries.next(index),
                                            mesh,
                                            materials.getMaterialIndex(material));
                                }

                                @Override
                                public void override(int index, @Nullable Material material) {
                                    ByteBuffer out = overrideEntries.next(index);
                                    out.putInt(
                                            out.position(), materials.getMaterialIndex(material));
                                }

                                @Override
                                public void model(@NonNull Model model, int instanceCount) {
                                    models.add(new ModelCount(model, instanceCount));
                                }
                            });
            snapshot = new Snapshot(List.copyOf(models), capacities.slots());

            final boolean work =
                    instances.hasWork(capacities.slots(), instanceEntries)
                            || modelMeshes.hasWork(capacities.modelMeshes(), meshEntries)
                            || overrides.hasWork(capacities.overrides(), overrideEntries);
            if (!work) {
                return;
            }
            DeviceTable.barrierBefore(commandBuffer, stack);
            instances.record(state, commandBuffer, capacities.slots(), instanceEntries, stack);
            modelMeshes.record(state, commandBuffer, capacities.modelMeshes(), meshEntries, stack);
            overrides.record(state, commandBuffer, capacities.overrides(), overrideEntries, stack);
            DeviceTable.barrierAfter(commandBuffer, stack);
        } finally {
            instanceEntries.free();
            meshEntries.free();
            overrideEntries.free();
        }
    }

    /**
     * Free the tables, when shutting down. The GPU must be idle.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        retiring.clear();
        instances.cleanup(state);
        modelMeshes.cleanup(state);
        overrides.cleanup(state);
    }
}
