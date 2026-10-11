package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.vkCmdDrawIndexedIndirectCount;

import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.lod.ClusterMeshes;
import com.ikalagaming.graphics.lod.LodDag;
import com.ikalagaming.graphics.lod.LodVertex;
import com.ikalagaming.graphics.scene.Observer;
import com.ikalagaming.graphics.vulkan.stages.InstanceDrawUpdate;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps level of detail worlds on the GPU and draws the cut the culling pass picks for them. A
 * world's clusters live in their own geometry arena in the {@link LodVertex} format, one mesh each,
 * with an entry each in the cluster table that {@code lod_cull.comp} reads (see {@link
 * LodCullMath}). Each frame the culling pass picks every world's cut, culls it against each view
 * and writes compacted draw commands and their counts, one list per view, which the scene and
 * shadow stages draw with one call each.
 *
 * <p>Adding, setting up and removing are safe from any thread. Uploads and the frame's tables
 * happen on the render thread.
 */
public class LodRenderer {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Bytes in a draw command. */
    private static final int COMMAND_SIZE = InstanceDrawUpdate.DRAW_COMMAND_SIZE;

    /**
     * The draw lists, which are the same as the instance culling's up to the translucent one: the
     * early scene, the late scene, then one per shadow cascade.
     */
    public static final int LIST_COUNT = InstanceDrawUpdate.LIST_TRANSLUCENT;

    /** The counter of clusters in the cut, after one count per list. */
    public static final int COUNTER_SELECTED = LIST_COUNT;

    /** The counter of triangles in the cut. */
    public static final int COUNTER_TRIANGLES = COUNTER_SELECTED + 1;

    /** The counter of clusters in the cut outside the scene's frustum. */
    public static final int COUNTER_OUTSIDE = COUNTER_TRIANGLES + 1;

    /** The counter of clusters in the cut the occlusion test hid. */
    public static final int COUNTER_OCCLUDED = COUNTER_OUTSIDE + 1;

    /** How many counters there are. */
    public static final int COUNTER_COUNT = COUNTER_OCCLUDED + 1;

    /** How many clusters the tables start with room for. */
    private static final int INITIAL_CLUSTERS = 1024;

    /** Bytes in a history entry. */
    private static final int HISTORY_ENTRY_SIZE = Integer.BYTES;

    /**
     * The clusters' geometry. -- GETTER -- The arena.
     *
     * @return The arena.
     */
    @Getter private final GeometryArena arena;

    /**
     * Each cluster's entry, see {@link LodCullMath}. -- GETTER -- The cluster table.
     *
     * @return The cluster table.
     */
    @Getter private final DeviceTable clusters;

    /**
     * Whether each cluster was visible last frame, written by the culling pass, indexed like the
     * cluster table. -- GETTER -- The visibility history.
     *
     * @return The history.
     */
    @Getter private final DeviceTable history;

    /** Where each world's clusters are in the tables. Render thread. */
    private final RangeAllocator ranges = new RangeAllocator(INITIAL_CLUSTERS, Integer.MAX_VALUE);

    /** Guards the worlds and the removed ones. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The worlds by id, in the order they were added. */
    private final Map<Long, World> worlds = new LinkedHashMap<>();

    /** Removed worlds whose ranges are freed on the render thread. */
    private final List<World> removedRanges = new ArrayList<>();

    /** The next world's id. */
    private long nextId = 1;

    /**
     * What the culling pass did, the last time its counts were read. -- GETTER -- Recent counts.
     *
     * @return The counts.
     */
    @Getter private volatile Stats stats = new Stats(0, 0, 0, 0, 0, 0);

    /**
     * What the culling pass did in a frame, over every world.
     *
     * @param selectedClusters Clusters in the cut.
     * @param selectedTriangles Triangles in the cut.
     * @param outside Clusters in the cut outside the scene's frustum.
     * @param occluded Clusters in the cut the occlusion test hid.
     * @param early Clusters the scene drew before the depth pyramid.
     * @param late Clusters the scene drew after it.
     */
    public record Stats(
            int selectedClusters,
            int selectedTriangles,
            int outside,
            int occluded,
            int early,
            int late) {}

    /** One world's clusters. */
    private static final class World {
        /** The plugin that owns it. */
        final String owner;

        /** Where its positions are measured from, in the world. */
        final double[] origin;

        /** Each cluster's mesh, or null if it has no triangles. */
        final MeshHandle[] meshes;

        /** Its cluster table entries until they are placed, then null. */
        ByteBuffer entries;

        /** Where its clusters start in the tables, or -1 until they are placed. */
        int first = -1;

        /** Whether every mesh has been uploaded, once it has. */
        boolean resident;

        /** Whether it is drawn. */
        boolean visible = true;

        /** The most error a drawn cluster may have, in pixels. */
        double thresholdPixels;

        /**
         * Keep a world.
         *
         * @param owner The owner.
         * @param origin The origin.
         * @param meshes The meshes.
         * @param entries Its cluster table entries.
         * @param thresholdPixels Its error threshold.
         */
        World(
                String owner,
                double[] origin,
                MeshHandle[] meshes,
                ByteBuffer entries,
                double thresholdPixels) {
            this.owner = owner;
            this.origin = origin;
            this.meshes = meshes;
            this.entries = entries;
            this.thresholdPixels = thresholdPixels;
        }
    }

    /**
     * Set up the renderer.
     *
     * @param state The Vulkan state.
     */
    public LodRenderer(@NonNull VulkanState state) {
        arena = new GeometryArena(state, LodVertex.SIZE);
        clusters =
                new DeviceTable(
                        state, "lod cluster", LodCullMath.CLUSTER_ENTRY_SIZE, INITIAL_CLUSTERS);
        history = new DeviceTable(state, "lod history", HISTORY_ENTRY_SIZE, INITIAL_CLUSTERS);
    }

    /**
     * Add a world, uploading every cluster. Safe from any thread.
     *
     * @param state The Vulkan state.
     * @param owner The key of the plugin that owns it.
     * @param dag The world.
     * @param meshes Each cluster's mesh, by cluster id.
     * @param thresholdPixels The most error a drawn cluster may have, in pixels.
     * @return The world's id.
     */
    public long add(
            @NonNull VulkanState state,
            @NonNull String owner,
            @NonNull LodDag dag,
            @NonNull List<ClusterMeshes.Mesh> meshes,
            double thresholdPixels) {
        final int count = meshes.size();
        final MeshHandle[] handles = new MeshHandle[count];
        final ByteBuffer entries =
                ByteBuffer.allocate(count * LodCullMath.CLUSTER_ENTRY_SIZE)
                        .order(ByteOrder.nativeOrder());
        for (int i = 0; i < count; ++i) {
            final ClusterMeshes.Mesh mesh = meshes.get(i);
            if (mesh.triangleCount() > 0) {
                handles[i] = register(state, owner, mesh);
            }
            entries.position(i * LodCullMath.CLUSTER_ENTRY_SIZE);
            LodCullMath.putCluster(
                    entries,
                    dag,
                    i,
                    handles[i] == null ? LodCullMath.NO_MESH : handles[i].slot(),
                    handles[i] == null ? 0 : handles[i].generation());
        }
        entries.position(0);
        lock.lock();
        try {
            final long id = nextId++;
            worlds.put(
                    id,
                    new World(owner, dag.getOrigin().clone(), handles, entries, thresholdPixels));
            return id;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Register one cluster's mesh in the arena.
     *
     * @param state The Vulkan state.
     * @param owner The owner.
     * @param mesh The mesh.
     * @return Its handle.
     */
    private MeshHandle register(
            @NonNull VulkanState state, @NonNull String owner, ClusterMeshes.@NonNull Mesh mesh) {
        final ByteBuffer vertices = MemoryUtil.memAlloc(mesh.vertexCount() * LodVertex.SIZE);
        final ByteBuffer indices = MemoryUtil.memAlloc(mesh.indices().length * Integer.BYTES);
        try {
            // The vertex bytes are already little endian, as the GPU wants
            vertices.put(0, mesh.vertices(), 0, mesh.vertexCount() * LodVertex.SIZE);
            indices.asIntBuffer().put(mesh.indices());
            return arena.register(
                    state,
                    owner,
                    vertices,
                    indices,
                    new Vector3f(mesh.min()[0], mesh.min()[1], mesh.min()[2]),
                    new Vector3f(mesh.max()[0], mesh.max()[1], mesh.max()[2]));
        } finally {
            MemoryUtil.memFree(indices);
            MemoryUtil.memFree(vertices);
        }
    }

    /**
     * Set the most error a world's drawn clusters may have.
     *
     * @param id The world.
     * @param thresholdPixels The error, in pixels.
     */
    public void setThreshold(long id, double thresholdPixels) {
        lock.lock();
        try {
            final World world = worlds.get(id);
            if (world != null) {
                world.thresholdPixels = thresholdPixels;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Show or hide a world.
     *
     * @param id The world.
     * @param visible Whether it is drawn.
     */
    public void setVisible(long id, boolean visible) {
        lock.lock();
        try {
            final World world = worlds.get(id);
            if (world != null) {
                world.visible = visible;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether every cluster of a world has been uploaded.
     *
     * @param id The world.
     * @return False if any is still waiting, or there is no such world.
     */
    public boolean isResident(long id) {
        lock.lock();
        try {
            final World world = worlds.get(id);
            return world != null && isResidentLocked(world);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether every cluster of a world has been uploaded, noting it once it has. Hold the lock.
     *
     * @param world The world.
     * @return True if all have.
     */
    private boolean isResidentLocked(@NonNull World world) {
        if (world.resident) {
            return true;
        }
        for (MeshHandle mesh : world.meshes) {
            if (mesh != null && !arena.getRegistry().isResident(mesh)) {
                return false;
            }
        }
        world.resident = true;
        return true;
    }

    /**
     * Remove a world, releasing its clusters.
     *
     * @param id The world.
     * @param owner The plugin asking, which must own it.
     * @return True if it was removed.
     */
    public boolean remove(long id, @NonNull String owner) {
        final World world;
        lock.lock();
        try {
            world = worlds.get(id);
            if (world == null || !world.owner.equals(owner)) {
                return false;
            }
            worlds.remove(id);
            removedRanges.add(world);
        } finally {
            lock.unlock();
        }
        release(world);
        return true;
    }

    /**
     * Remove every world a plugin owns.
     *
     * @param owner The plugin.
     * @return How many were removed.
     */
    public int removeAllOwnedBy(@NonNull String owner) {
        final List<World> removed = new ArrayList<>();
        lock.lock();
        try {
            final Iterator<World> it = worlds.values().iterator();
            while (it.hasNext()) {
                final World world = it.next();
                if (world.owner.equals(owner)) {
                    removed.add(world);
                    it.remove();
                }
            }
            removedRanges.addAll(removed);
        } finally {
            lock.unlock();
        }
        removed.forEach(this::release);
        return removed.size();
    }

    /**
     * Release a world's meshes.
     *
     * @param world The world.
     */
    private void release(@NonNull World world) {
        for (MeshHandle mesh : world.meshes) {
            if (mesh != null) {
                arena.release(mesh);
            }
        }
    }

    /**
     * Upload waiting clusters, free released ones, and place new worlds in the cluster table.
     * Render thread, at the start of a frame.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The frame's command buffer.
     */
    public void record(@NonNull VulkanState state, @NonNull VkCommandBuffer commandBuffer) {
        arena.record(state, commandBuffer);
        final DeviceTable.Entries clusterEntries =
                new DeviceTable.Entries(LodCullMath.CLUSTER_ENTRY_SIZE);
        final DeviceTable.Entries historyEntries = new DeviceTable.Entries(HISTORY_ENTRY_SIZE);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            lock.lock();
            try {
                // A removed range may be reused at once: frames in flight that read it come
                // before this frame's copies on the queue
                for (World world : removedRanges) {
                    if (world.first >= 0) {
                        ranges.free(world.first, world.meshes.length);
                    }
                }
                removedRanges.clear();
                for (World world : worlds.values()) {
                    if (world.entries != null) {
                        place(world, clusterEntries, historyEntries);
                    }
                }
            } finally {
                lock.unlock();
            }
            final int capacity = ranges.getCapacity();
            if (!clusters.hasWork(capacity, clusterEntries)
                    && !history.hasWork(capacity, historyEntries)) {
                return;
            }
            DeviceTable.barrierBefore(commandBuffer, stack);
            clusters.record(state, commandBuffer, capacity, clusterEntries, stack);
            history.record(state, commandBuffer, capacity, historyEntries, stack);
            DeviceTable.barrierAfter(commandBuffer, stack);
        } finally {
            historyEntries.free();
            clusterEntries.free();
        }
    }

    /**
     * Give a new world its range of the tables and pack its entries, with no history. Hold the
     * lock.
     *
     * @param world The world.
     * @param clusterEntries The cluster table's changed entries.
     * @param historyEntries The history's changed entries, which start out cleared.
     */
    private void place(
            @NonNull World world,
            DeviceTable.@NonNull Entries clusterEntries,
            DeviceTable.@NonNull Entries historyEntries) {
        final int count = world.meshes.length;
        world.first = ranges.allocate(count);
        if (world.first < 0) {
            throw new IllegalStateException("No room for " + count + " more LOD clusters");
        }
        final ByteBuffer packed = world.entries;
        for (int i = 0; i < count; ++i) {
            final ByteBuffer out = clusterEntries.next(world.first + i);
            out.put(
                    out.position(),
                    packed,
                    i * LodCullMath.CLUSTER_ENTRY_SIZE,
                    LodCullMath.CLUSTER_ENTRY_SIZE);
            historyEntries.next(world.first + i);
        }
        world.entries = null;
    }

    /**
     * Write this frame's world table and clear its draw counts, after reading the counts the GPU
     * left there the last time this frame's data was used. Render thread, before anything culls or
     * draws.
     *
     * @param state The Vulkan state.
     * @param frameData The frame's data, which the GPU is done with.
     * @param camera The camera's position, in the world.
     * @param observer Where the cut is seen from, which may be frozen away from the camera.
     * @param heightPixels The height the scene is drawn at.
     */
    public void prepare(
            @NonNull VulkanState state,
            @NonNull PerFrameData frameData,
            @NonNull Vector3dc camera,
            @NonNull Observer observer,
            int heightPixels) {
        frameData.lodCounts.ensureCapacity((long) COUNTER_COUNT * Integer.BYTES, state);
        final IntBuffer counts =
                MemoryUtil.memIntBuffer(
                        frameData.lodCounts.allocationInfo.pMappedData(), COUNTER_COUNT);
        if (frameData.lodWorldCount > 0) {
            stats =
                    new Stats(
                            counts.get(COUNTER_SELECTED),
                            counts.get(COUNTER_TRIANGLES),
                            counts.get(COUNTER_OUTSIDE),
                            counts.get(COUNTER_OCCLUDED),
                            counts.get(InstanceDrawUpdate.LIST_SCENE_EARLY),
                            counts.get(InstanceDrawUpdate.LIST_SCENE_LATE));
        }
        for (int i = 0; i < COUNTER_COUNT; ++i) {
            counts.put(i, 0);
        }

        // m11 is 1 / tan(fov / 2), negative with Vulkan's y flip
        final float pixelsPerUnit =
                Math.abs(observer.getProjectionMatrix().m11()) * heightPixels / 2;
        final Vector3dc eye = observer.getPosition();
        final List<World> culled = new ArrayList<>();
        final List<Float> thresholds = new ArrayList<>();
        lock.lock();
        try {
            for (World world : worlds.values()) {
                if (world.visible && world.first >= 0 && isResidentLocked(world)) {
                    culled.add(world);
                    thresholds.add((float) world.thresholdPixels);
                }
            }
        } finally {
            lock.unlock();
        }
        int capacity = 0;
        for (World world : culled) {
            capacity = Math.max(capacity, world.first + world.meshes.length);
        }
        frameData.lodWorldCount = culled.size();
        frameData.lodFirstClusters = new int[culled.size()];
        frameData.lodClusterCounts = new int[culled.size()];
        frameData.lodCapacity = capacity;
        frameData.lodWorlds.ensureCapacity(
                (long) Math.max(1, culled.size()) * LodCullMath.WORLD_ENTRY_SIZE, state);
        frameData.lodDrawCommands.ensureCapacity(
                (long) Math.max(1, capacity) * LIST_COUNT * COMMAND_SIZE, state);
        final ByteBuffer table =
                MemoryUtil.memByteBuffer(
                        frameData.lodWorlds.allocationInfo.pMappedData(),
                        Math.max(1, culled.size()) * LodCullMath.WORLD_ENTRY_SIZE);
        for (int w = 0; w < culled.size(); ++w) {
            final World world = culled.get(w);
            frameData.lodFirstClusters[w] = world.first;
            frameData.lodClusterCounts[w] = world.meshes.length;
            putWorld(
                    table,
                    w * LodCullMath.WORLD_ENTRY_SIZE,
                    world,
                    camera,
                    eye,
                    thresholds.get(w),
                    pixelsPerUnit);
        }
    }

    /**
     * Write a world's entry in the world table.
     *
     * @param out The table.
     * @param base Where the entry starts.
     * @param world The world.
     * @param camera The camera's position, in the world.
     * @param eye The observer's position, in the world.
     * @param thresholdPixels The world's error threshold.
     * @param pixelsPerUnit Pixels per world unit at distance 1.
     */
    private static void putWorld(
            @NonNull ByteBuffer out,
            int base,
            @NonNull World world,
            @NonNull Vector3dc camera,
            @NonNull Vector3dc eye,
            float thresholdPixels,
            float pixelsPerUnit) {
        final double[] at = {camera.x(), camera.y(), camera.z()};
        final double[] from = {eye.x(), eye.y(), eye.z()};
        for (int axis = 0; axis < AXES; ++axis) {
            final double relative = at[axis] - world.origin[axis];
            final double step = Math.floor(relative * LodVertex.STEPS_PER_UNIT);
            out.putFloat(
                    base + LodCullMath.WORLD_RENDER_OFFSET_OFFSET + axis * Float.BYTES,
                    (float) -relative);
            out.putFloat(
                    base + LodCullMath.WORLD_EYE_OFFSET + axis * Float.BYTES,
                    (float) (from[axis] - world.origin[axis]));
            out.putInt(
                    base + LodCullMath.WORLD_CAMERA_GRID_OFFSET + axis * Integer.BYTES, (int) step);
            out.putFloat(
                    base + LodCullMath.WORLD_CAMERA_FRACTION_OFFSET + axis * Float.BYTES,
                    (float) (relative - step / LodVertex.STEPS_PER_UNIT));
        }
        out.putInt(base + LodCullMath.WORLD_INFO_OFFSET, world.first);
        out.putInt(base + LodCullMath.WORLD_INFO_OFFSET + Integer.BYTES, world.meshes.length);
        out.putFloat(base + LodCullMath.WORLD_INFO_OFFSET + 2 * Integer.BYTES, thresholdPixels);
        out.putFloat(base + LodCullMath.WORLD_INFO_OFFSET + 3 * Integer.BYTES, pixelsPerUnit);
    }

    /**
     * Whether a frame has any worlds to cull and draw.
     *
     * @param frameData The frame's data.
     * @return True if there are.
     */
    public static boolean hasDraws(@NonNull PerFrameData frameData) {
        return frameData.lodWorldCount > 0;
    }

    /**
     * Draw one list of the frame's clusters with a bound pipeline: as many commands as the culling
     * pass counted, in one call.
     *
     * @param commandBuffer The command buffer, inside the rendering, with the pipeline bound.
     * @param frameData The frame's data.
     * @param pipelineLayout The pipeline's layout.
     * @param worldsOffset Where the world table's address goes in its vertex push constants.
     * @param list Which list to draw.
     */
    public void draw(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull PerFrameData frameData,
            long pipelineLayout,
            int worldsOffset,
            int list) {
        if (!hasDraws(frameData) || list < 0 || list >= LIST_COUNT) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindIndexBuffer(commandBuffer, arena.getIndices().buffer, 0, VK_INDEX_TYPE_UINT32);
            vkCmdBindVertexBuffers(
                    commandBuffer, 0, stack.longs(arena.getVertices().buffer), stack.callocLong(1));
            final ByteBuffer constants = stack.malloc(Long.BYTES);
            constants.putLong(0, frameData.lodWorlds.deviceAddress);
            vkCmdPushConstants(
                    commandBuffer,
                    pipelineLayout,
                    VK_SHADER_STAGE_VERTEX_BIT,
                    worldsOffset,
                    constants);
            vkCmdDrawIndexedIndirectCount(
                    commandBuffer,
                    frameData.lodDrawCommands.buffer,
                    (long) list * frameData.lodCapacity * COMMAND_SIZE,
                    frameData.lodCounts.buffer,
                    (long) list * Integer.BYTES,
                    frameData.lodCapacity,
                    COMMAND_SIZE);
        }
    }

    /**
     * Free everything. Render thread, with the device idle.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        lock.lock();
        try {
            worlds.clear();
            removedRanges.clear();
        } finally {
            lock.unlock();
        }
        history.cleanup(state);
        clusters.cleanup(state);
        arena.cleanup(state);
    }
}
