package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.vulkan.VK10.*;

import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.lod.ClusterMeshes;
import com.ikalagaming.graphics.lod.LodVertex;
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
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps level of detail worlds on the GPU and draws the clusters each one has chosen. A world's
 * clusters live in their own geometry arena in the {@link LodVertex} format, one mesh each, and
 * each frame every drawn, uploaded cluster becomes one indirect draw command. The commands are
 * written by the CPU from the cut each world's owner sets; the culling pass will write them
 * instead.
 *
 * <p>Adding, choosing and removing are safe from any thread. Uploads and the frame's commands
 * happen on the render thread.
 */
public class LodRenderer {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Bytes in a draw command. */
    private static final int COMMAND_SIZE = InstanceDrawUpdate.DRAW_COMMAND_SIZE;

    /** Bytes of push constants for where the camera is: an ivec4 and a vec4. */
    public static final int CAMERA_CONSTANTS_SIZE = 2 * 4 * Integer.BYTES;

    /** Where the camera's grid step is in the camera push constants. */
    private static final int CAMERA_GRID_OFFSET = 0;

    /** Where the rest of the camera's position is in the camera push constants. */
    private static final int CAMERA_FRACTION_OFFSET = 4 * Integer.BYTES;

    /**
     * The clusters' geometry. -- GETTER -- The arena.
     *
     * @return The arena.
     */
    @Getter private final GeometryArena arena;

    /** Guards the worlds. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The worlds by id, in the order they were added. */
    private final Map<Long, World> worlds = new LinkedHashMap<>();

    /** The next world's id. */
    private long nextId = 1;

    /**
     * One world's draws for a frame.
     *
     * @param firstCommand Its first command in the frame's LOD commands.
     * @param count How many commands it has.
     * @param camera Its camera push constants.
     */
    public record Batch(int firstCommand, int count, @NonNull ByteBuffer camera) {}

    /** One world's clusters. */
    private static final class World {
        /** The plugin that owns it. */
        final String owner;

        /** Where its positions are measured from, in the world. */
        final double[] origin;

        /** Each cluster's mesh, or null if it has no triangles. */
        final MeshHandle[] meshes;

        /** Each cluster's index count. */
        final int[] indexCounts;

        /** Which clusters are drawn. */
        boolean[] drawn = new boolean[0];

        /**
         * Keep a world.
         *
         * @param owner The owner.
         * @param origin The origin.
         * @param meshes The meshes.
         * @param indexCounts Their index counts.
         */
        World(String owner, double[] origin, MeshHandle[] meshes, int[] indexCounts) {
            this.owner = owner;
            this.origin = origin;
            this.meshes = meshes;
            this.indexCounts = indexCounts;
        }
    }

    /**
     * Set up the renderer.
     *
     * @param state The Vulkan state.
     */
    public LodRenderer(@NonNull VulkanState state) {
        arena = new GeometryArena(state, LodVertex.SIZE);
    }

    /**
     * Add a world, uploading every cluster. Safe from any thread.
     *
     * @param state The Vulkan state.
     * @param owner The key of the plugin that owns it.
     * @param origin Where its positions are measured from, in the world.
     * @param clusters Each cluster's mesh, by cluster id.
     * @return The world's id.
     */
    public long add(
            @NonNull VulkanState state,
            @NonNull String owner,
            double @NonNull [] origin,
            @NonNull List<ClusterMeshes.Mesh> clusters) {
        final MeshHandle[] meshes = new MeshHandle[clusters.size()];
        final int[] indexCounts = new int[clusters.size()];
        for (int i = 0; i < meshes.length; ++i) {
            final ClusterMeshes.Mesh mesh = clusters.get(i);
            if (mesh.triangleCount() == 0) {
                continue;
            }
            meshes[i] = register(state, owner, mesh);
            indexCounts[i] = mesh.indices().length;
        }
        lock.lock();
        try {
            final long id = nextId++;
            worlds.put(id, new World(owner, origin.clone(), meshes, indexCounts));
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
     * Choose which of a world's clusters are drawn.
     *
     * @param id The world.
     * @param drawn Whether each cluster is drawn, by id; copied.
     */
    public void setDrawn(long id, boolean @NonNull [] drawn) {
        lock.lock();
        try {
            final World world = worlds.get(id);
            if (world != null) {
                world.drawn = drawn.clone();
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
            if (world == null) {
                return false;
            }
            for (MeshHandle mesh : world.meshes) {
                if (mesh != null && !arena.getRegistry().isResident(mesh)) {
                    return false;
                }
            }
            return true;
        } finally {
            lock.unlock();
        }
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
     * Upload waiting clusters and free released ones. Render thread, at the start of a frame.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The frame's command buffer.
     */
    public void record(@NonNull VulkanState state, @NonNull VkCommandBuffer commandBuffer) {
        arena.record(state, commandBuffer);
    }

    /**
     * Write this frame's draw commands: one per drawn, uploaded cluster, grouped by world. Render
     * thread, before anything draws.
     *
     * @param state The Vulkan state.
     * @param frameData The frame's data.
     * @param camera The camera's position, in the world.
     */
    public void prepare(
            @NonNull VulkanState state,
            @NonNull PerFrameData frameData,
            @NonNull Vector3dc camera) {
        final List<Batch> batches = new ArrayList<>();
        lock.lock();
        try {
            int total = 0;
            for (World world : worlds.values()) {
                total += count(world.drawn);
            }
            frameData.lodDrawCommands.ensureCapacity((long) total * COMMAND_SIZE, state);
            final ByteBuffer commands =
                    MemoryUtil.memByteBuffer(
                            frameData.lodDrawCommands.allocationInfo.pMappedData(),
                            Math.max(1, total) * COMMAND_SIZE);
            int written = 0;
            for (World world : worlds.values()) {
                final int first = written;
                for (int c = 0; c < world.drawn.length && c < world.meshes.length; ++c) {
                    if (!world.drawn[c] || world.meshes[c] == null) {
                        continue;
                    }
                    final MeshRegistry.Location at = arena.getRegistry().locate(world.meshes[c]);
                    if (at == null) {
                        continue;
                    }
                    final int base = written * COMMAND_SIZE;
                    commands.putInt(base, world.indexCounts[c]);
                    commands.putInt(base + Integer.BYTES, 1);
                    commands.putInt(base + 2 * Integer.BYTES, at.firstIndex());
                    commands.putInt(base + 3 * Integer.BYTES, at.vertexOffset());
                    commands.putInt(base + 4 * Integer.BYTES, 0);
                    ++written;
                }
                if (written > first) {
                    batches.add(new Batch(first, written - first, cameraConstants(world, camera)));
                }
            }
        } finally {
            lock.unlock();
        }
        frameData.lodBatches = Collections.unmodifiableList(batches);
    }

    /**
     * How many entries are set.
     *
     * @param drawn The flags.
     * @return The count.
     */
    private static int count(boolean @NonNull [] drawn) {
        int count = 0;
        for (boolean d : drawn) {
            count += d ? 1 : 0;
        }
        return count;
    }

    /**
     * Where the camera is relative to a world: its grid step, rounded down, and the rest.
     *
     * @param world The world.
     * @param camera The camera's position, in the world.
     * @return The push constants, {@link #CAMERA_CONSTANTS_SIZE} bytes.
     */
    private static ByteBuffer cameraConstants(@NonNull World world, @NonNull Vector3dc camera) {
        final ByteBuffer out =
                ByteBuffer.allocate(CAMERA_CONSTANTS_SIZE).order(ByteOrder.nativeOrder());
        final double[] at = {camera.x(), camera.y(), camera.z()};
        for (int axis = 0; axis < AXES; ++axis) {
            final double relative = at[axis] - world.origin[axis];
            final double step = Math.floor(relative * LodVertex.STEPS_PER_UNIT);
            out.putInt(CAMERA_GRID_OFFSET + axis * Integer.BYTES, (int) step);
            out.putFloat(
                    CAMERA_FRACTION_OFFSET + axis * Float.BYTES,
                    (float) (relative - step / LodVertex.STEPS_PER_UNIT));
        }
        return out;
    }

    /**
     * Whether a frame has anything to draw.
     *
     * @param frameData The frame's data.
     * @return True if there are draws.
     */
    public static boolean hasDraws(@NonNull PerFrameData frameData) {
        return frameData.lodBatches != null && !frameData.lodBatches.isEmpty();
    }

    /**
     * Draw the frame's clusters with a bound pipeline, pushing each world's camera constants.
     *
     * @param commandBuffer The command buffer, inside the rendering, with the pipeline bound.
     * @param frameData The frame's data.
     * @param pipelineLayout The pipeline's layout.
     * @param cameraOffset Where the camera constants go in its vertex push constants.
     */
    public void draw(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull PerFrameData frameData,
            long pipelineLayout,
            int cameraOffset) {
        if (!hasDraws(frameData)) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindIndexBuffer(commandBuffer, arena.getIndices().buffer, 0, VK_INDEX_TYPE_UINT32);
            final LongBuffer vertexBuffers = stack.longs(arena.getVertices().buffer);
            vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, stack.callocLong(1));
            final ByteBuffer constants = stack.malloc(CAMERA_CONSTANTS_SIZE);
            for (Batch batch : frameData.lodBatches) {
                constants.put(0, batch.camera(), 0, CAMERA_CONSTANTS_SIZE);
                vkCmdPushConstants(
                        commandBuffer,
                        pipelineLayout,
                        VK_SHADER_STAGE_VERTEX_BIT,
                        cameraOffset,
                        constants);
                vkCmdDrawIndexedIndirect(
                        commandBuffer,
                        frameData.lodDrawCommands.buffer,
                        (long) batch.firstCommand() * COMMAND_SIZE,
                        batch.count(),
                        COMMAND_SIZE);
            }
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
        } finally {
            lock.unlock();
        }
        arena.cleanup(state);
    }
}
