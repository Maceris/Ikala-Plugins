package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.MeshKind;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkMemoryBarrier2;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The shared vertex and index buffers that every scene mesh lives in, so a pass binds them once and
 * each draw picks its mesh by offset. The buffers are in device-local memory: meshes are copied in
 * through the staging ring at the start of a frame, like textures, and grow by doubling, copying
 * the old contents across on the GPU.
 *
 * <p>Registering and releasing meshes is safe from any thread. Uploads, growth and freeing happen
 * on the render thread in {@link #record(VulkanState, VkCommandBuffer)}.
 *
 * <p>The buffers can be read as storage through their device addresses, so compute passes and
 * shaders can fetch vertices by mesh, and later the ray tracing builders can read them as they are.
 * Indices are 32-bit and positions come first in each vertex for that reason.
 */
@Slf4j
public class GeometryArena {

    /**
     * Roughly how many bytes of mesh data to upload per frame, so a burst of loads is spread out
     * instead of causing one long frame. At least one mesh is uploaded every frame regardless.
     */
    public static final long BYTES_PER_FRAME = 32L * 1024 * 1024;

    /** How many vertices the buffer starts out holding. */
    public static final int INITIAL_VERTICES = 256 * 1024;

    /** How many indices the buffer starts out holding. */
    public static final int INITIAL_INDICES = 3 * INITIAL_VERTICES;

    /** Usage of the vertex buffer: drawn from, read by compute, and copied when it grows. */
    private static final int VERTEX_USAGE =
            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
                    | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
                    | VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

    /** Usage of the index buffer: drawn from, read by compute, and copied when it grows. */
    private static final int INDEX_USAGE =
            VK_BUFFER_USAGE_INDEX_BUFFER_BIT
                    | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
                    | VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

    /**
     * A mesh waiting to be copied in.
     *
     * @param handle The mesh, which is pending until the copy is recorded.
     * @param vertices The staged vertex data.
     * @param indices The staged index data.
     */
    private record Upload(
            @NonNull MeshHandle handle,
            @NonNull StagingRing.Staging vertices,
            @NonNull StagingRing.Staging indices) {}

    /**
     * The size of one vertex, in bytes. -- GETTER -- The size of one vertex.
     *
     * @return The vertex size in bytes.
     */
    @Getter private final int vertexStride;

    /**
     * Which meshes are where. -- GETTER -- Which meshes are where.
     *
     * @return The mesh registry.
     */
    @Getter private final MeshRegistry registry;

    /**
     * The vertex buffer. Replaced when it grows, so look it up each frame. -- GETTER -- The vertex
     * buffer.
     *
     * @return The current vertex buffer.
     */
    @Getter private SharedBuffer vertices;

    /**
     * The index buffer. Replaced when it grows, so look it up each frame. -- GETTER -- The index
     * buffer.
     *
     * @return The current index buffer.
     */
    @Getter private SharedBuffer indices;

    /** The size of one mesh table entry, in bytes: offsets and counts, then the bounds. */
    public static final int MESH_ENTRY_SIZE = 3 * 4 * Integer.BYTES;

    /**
     * Where a mesh table entry's uvec4 of vertex offset, first index, index count and generation
     * starts.
     */
    public static final int MESH_INFO_OFFSET = 0;

    /** Where a mesh table entry's minimum bounds corner starts, a vec4. */
    public static final int MESH_MIN_OFFSET = 4 * Integer.BYTES;

    /** Where a mesh table entry's maximum bounds corner starts, a vec4. */
    public static final int MESH_MAX_OFFSET = 2 * 4 * Integer.BYTES;

    /**
     * Where each mesh is and how big it is, for the culling pass, one entry per mesh slot. --
     * GETTER -- The GPU mesh table.
     *
     * @return The mesh table.
     */
    @Getter private final DeviceTable meshTable;

    /** Meshes waiting to be copied in, in the order they were registered. */
    private final Queue<Upload> uploads = new ConcurrentLinkedQueue<>();

    /** Removed meshes whose space can be reused once frames in flight are done with them. */
    private final Queue<MeshRegistry.Retired> retiring = new ConcurrentLinkedQueue<>();

    /**
     * Create the buffers.
     *
     * @param state The Vulkan state.
     * @param vertexStride The size of one vertex, in bytes.
     */
    public GeometryArena(@NonNull VulkanState state, int vertexStride) {
        this.vertexStride = vertexStride;
        registry = new MeshRegistry(INITIAL_VERTICES, INITIAL_INDICES);
        meshTable = new DeviceTable(state, "mesh", MESH_ENTRY_SIZE, 256);
        vertices =
                SharedBuffer.allocateDeviceLocal(
                        (long) INITIAL_VERTICES * vertexStride, state, VERTEX_USAGE);
        indices =
                SharedBuffer.allocateDeviceLocal(
                        (long) INITIAL_INDICES * Integer.BYTES, state, INDEX_USAGE);
    }

    /**
     * Add a mesh. Its data is checked and copied to staging memory before this returns, so the
     * buffers can be reused right away. It is drawn once the render thread has copied it in. Safe
     * from any thread.
     *
     * @param state The Vulkan state.
     * @param owner The key of the plugin that owns the mesh.
     * @param vertexData The vertices, tightly packed, from position to limit.
     * @param indexData The 32-bit indices, from position to limit.
     * @param aabbMin The minimum corner of the mesh's bounding box.
     * @param aabbMax The maximum corner of the mesh's bounding box.
     * @return The handle, pending until uploaded.
     * @throws IllegalArgumentException If the data isn't whole vertices and triangles, or an index
     *     points past the last vertex.
     */
    public MeshHandle register(
            @NonNull VulkanState state,
            @NonNull String owner,
            @NonNull ByteBuffer vertexData,
            @NonNull ByteBuffer indexData,
            @NonNull Vector3fc aabbMin,
            @NonNull Vector3fc aabbMax) {
        return register(state, owner, MeshKind.STANDARD, vertexData, indexData, aabbMin, aabbMax);
    }

    /**
     * Add a mesh of a given kind, which must be in this arena's vertex format. See {@link
     * #register(VulkanState, String, ByteBuffer, ByteBuffer, Vector3fc, Vector3fc)}.
     *
     * @param state The Vulkan state.
     * @param owner The key of the plugin that owns the mesh.
     * @param kind The kind of mesh, which its handle carries.
     * @param vertexData The vertices, tightly packed, from position to limit.
     * @param indexData The 32-bit indices, from position to limit.
     * @param aabbMin The minimum corner of the mesh's bounding box.
     * @param aabbMax The maximum corner of the mesh's bounding box.
     * @return The handle, pending until uploaded.
     * @throws IllegalArgumentException If the data isn't whole vertices and triangles, or an index
     *     points past the last vertex.
     */
    public MeshHandle register(
            @NonNull VulkanState state,
            @NonNull String owner,
            @NonNull MeshKind kind,
            @NonNull ByteBuffer vertexData,
            @NonNull ByteBuffer indexData,
            @NonNull Vector3fc aabbMin,
            @NonNull Vector3fc aabbMax) {
        if (vertexData.remaining() % vertexStride != 0) {
            throw new IllegalArgumentException(
                    vertexData.remaining() + " bytes isn't a whole number of vertices");
        }
        if (indexData.remaining() % (3 * Integer.BYTES) != 0) {
            throw new IllegalArgumentException(
                    indexData.remaining() + " bytes isn't a whole number of triangles");
        }
        final int vertexCount = vertexData.remaining() / vertexStride;
        final int indexCount = indexData.remaining() / Integer.BYTES;
        checkIndices(indexData, vertexCount);

        final MeshRegistry.Reservation reservation =
                registry.add(owner, kind, vertexCount, indexCount, aabbMin, aabbMax);
        uploads.add(
                new Upload(
                        reservation.handle(),
                        state.stagingRing.stage(state, vertexData),
                        state.stagingRing.stage(state, indexData)));
        return reservation.handle();
    }

    /**
     * Make sure no index points past the last vertex, since a bad index read on the GPU can lose
     * the device. Mesh data from plugins is untrusted.
     *
     * @param indexData The 32-bit indices, from position to limit, which is left unchanged.
     * @param vertexCount The number of vertices.
     * @throws IllegalArgumentException If an index is out of range.
     */
    static void checkIndices(@NonNull ByteBuffer indexData, int vertexCount) {
        final long address = MemoryUtil.memAddress(indexData);
        final int count = indexData.remaining() / Integer.BYTES;
        for (int i = 0; i < count; ++i) {
            final int index = MemoryUtil.memGetInt(address + (long) i * Integer.BYTES);
            // Compared unsigned, since the GPU reads them unsigned
            if (Integer.compareUnsigned(index, vertexCount) >= 0) {
                throw new IllegalArgumentException(
                        "Index "
                                + Integer.toUnsignedString(index)
                                + " at "
                                + i
                                + " is past the last of "
                                + vertexCount
                                + " vertices");
            }
        }
    }

    /**
     * Remove a mesh. The handle goes stale at once; its space is reused once no frame in flight can
     * draw it. Nothing happens if the handle is null or stale. Safe from any thread.
     *
     * @param handle The mesh.
     */
    public void release(MeshHandle handle) {
        MeshRegistry.Retired retired = registry.remove(handle);
        if (retired != null) {
            retiring.add(retired);
        }
    }

    /**
     * Remove every mesh a plugin owns. Safe from any thread.
     *
     * @param owner The plugin's key.
     * @return How many meshes were removed.
     */
    public int releaseAllOwnedBy(@NonNull String owner) {
        var removed = registry.removeAllOwnedBy(owner);
        retiring.addAll(removed);
        return removed.size();
    }

    /**
     * The device address of a mesh's first vertex, for shaders that read vertices as storage.
     *
     * @param location Where the mesh is.
     * @return The address.
     */
    public long vertexAddress(@NonNull MeshRegistry.Location location) {
        return vertices.deviceAddress + (long) location.vertexOffset() * vertexStride;
    }

    /**
     * Grow the buffers if needed, record queued uploads up to the per-frame budget, and hand
     * removed meshes' space back once this frame is done. Render thread only, at the start of a
     * frame, before anything draws.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The frame's command buffer, recording.
     */
    public void record(@NonNull VulkanState state, @NonNull VkCommandBuffer commandBuffer) {
        MeshRegistry.Retired retired;
        while ((retired = retiring.poll()) != null) {
            // Frames before this one may still draw it, and none after
            final MeshRegistry.Retired free = retired;
            state.deferFree(() -> registry.free(free));
        }
        if (!uploads.isEmpty()) {
            recordUploads(state, commandBuffer);
        }
        // After the uploads, which make meshes resident
        recordMeshTable(state, commandBuffer);
    }

    /**
     * Write a mesh table entry, in the layout the culling shader reads.
     *
     * @param out Where to write it, at its position, which is left unchanged.
     * @param resident Whether the slot holds a mesh that can be drawn.
     * @param generation The slot's generation.
     * @param vertexOffset Where its vertices start.
     * @param firstIndex Where its indices start.
     * @param indexCount How many indices it has.
     * @param aabbMin The minimum corner of its bounding box.
     * @param aabbMax The maximum corner of its bounding box.
     */
    public static void writeMeshEntry(
            @NonNull ByteBuffer out,
            boolean resident,
            int generation,
            int vertexOffset,
            int firstIndex,
            int indexCount,
            @NonNull Vector3fc aabbMin,
            @NonNull Vector3fc aabbMax) {
        final int base = out.position();
        out.putInt(base + MESH_INFO_OFFSET, resident ? vertexOffset : 0);
        out.putInt(base + MESH_INFO_OFFSET + Integer.BYTES, resident ? firstIndex : 0);
        out.putInt(base + MESH_INFO_OFFSET + 2 * Integer.BYTES, resident ? indexCount : 0);
        out.putInt(base + MESH_INFO_OFFSET + 3 * Integer.BYTES, generation);
        out.putFloat(base + MESH_MIN_OFFSET, aabbMin.x());
        out.putFloat(base + MESH_MIN_OFFSET + Float.BYTES, aabbMin.y());
        out.putFloat(base + MESH_MIN_OFFSET + 2 * Float.BYTES, aabbMin.z());
        out.putFloat(base + MESH_MAX_OFFSET, aabbMax.x());
        out.putFloat(base + MESH_MAX_OFFSET + Float.BYTES, aabbMax.y());
        out.putFloat(base + MESH_MAX_OFFSET + 2 * Float.BYTES, aabbMax.z());
    }

    /**
     * Copy the mesh table entries that changed into the GPU's copy.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The frame's command buffer.
     */
    private void recordMeshTable(
            @NonNull VulkanState state, @NonNull VkCommandBuffer commandBuffer) {
        DeviceTable.Entries entries = new DeviceTable.Entries(MESH_ENTRY_SIZE);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final int capacity =
                    registry.takeChanged(
                            (slot,
                                    resident,
                                    generation,
                                    vertexOffset,
                                    firstIndex,
                                    indexCount,
                                    min,
                                    max) ->
                                    writeMeshEntry(
                                            entries.next(slot),
                                            resident,
                                            generation,
                                            vertexOffset,
                                            firstIndex,
                                            indexCount,
                                            min,
                                            max));
            if (!meshTable.hasWork(capacity, entries)) {
                return;
            }
            DeviceTable.barrierBefore(commandBuffer, stack);
            meshTable.record(state, commandBuffer, capacity, entries, stack);
            DeviceTable.barrierAfter(commandBuffer, stack);
        } finally {
            entries.free();
        }
    }

    /**
     * Grow the buffers if needed and record queued uploads up to the per-frame budget.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The frame's command buffer.
     */
    private void recordUploads(@NonNull VulkanState state, @NonNull VkCommandBuffer commandBuffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Earlier frames' copies and growth finish before these copies read or write
            memoryBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COPY_BIT,
                    VK_ACCESS_2_TRANSFER_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_COPY_BIT,
                    VK_ACCESS_2_TRANSFER_READ_BIT | VK_ACCESS_2_TRANSFER_WRITE_BIT,
                    stack);
            vertices = grown(state, commandBuffer, vertices, vertexBytes(), stack);
            indices = grown(state, commandBuffer, indices, indexBytes(), stack);

            long bytes = 0;
            Upload upload;
            VkBufferCopy.Buffer region = VkBufferCopy.calloc(1, stack);
            while (bytes < BYTES_PER_FRAME && (upload = uploads.poll()) != null) {
                final StagingRing.Staging vertexStaging = upload.vertices();
                final StagingRing.Staging indexStaging = upload.indices();
                bytes += vertexStaging.size() + indexStaging.size();
                // Read by this frame, so they can go once it finishes
                state.deferFree(() -> state.stagingRing.free(state, vertexStaging));
                state.deferFree(() -> state.stagingRing.free(state, indexStaging));
                final MeshRegistry.Location location = registry.locatePending(upload.handle());
                if (location == null) {
                    // Released while waiting
                    continue;
                }
                copy(
                        commandBuffer,
                        vertexStaging,
                        vertices,
                        (long) location.vertexOffset() * vertexStride,
                        region);
                copy(
                        commandBuffer,
                        indexStaging,
                        indices,
                        (long) location.firstIndex() * Integer.BYTES,
                        region);
                registry.markResident(upload.handle());
            }

            // Drawn, and read as storage by compute and debug passes
            memoryBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_2_COPY_BIT,
                    VK_ACCESS_2_TRANSFER_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_VERTEX_ATTRIBUTE_INPUT_BIT
                            | VK_PIPELINE_STAGE_2_INDEX_INPUT_BIT
                            | VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_VERTEX_ATTRIBUTE_READ_BIT
                            | VK_ACCESS_2_INDEX_READ_BIT
                            | VK_ACCESS_2_SHADER_STORAGE_READ_BIT,
                    stack);
        }
    }

    /**
     * How many bytes the vertex buffer must hold.
     *
     * @return The size in bytes.
     */
    private long vertexBytes() {
        return (long) registry.getVertexCapacity() * vertexStride;
    }

    /**
     * How many bytes the index buffer must hold.
     *
     * @return The size in bytes.
     */
    private long indexBytes() {
        return (long) registry.getIndexCapacity() * Integer.BYTES;
    }

    /**
     * Replace a buffer with a bigger one if it is too small, copying its contents across on the
     * GPU. The old buffer is freed once this frame is done.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The frame's command buffer.
     * @param buffer The buffer.
     * @param needed How many bytes it must hold.
     * @param stack The stack to allocate on.
     * @return The buffer to use from now on.
     */
    private static SharedBuffer grown(
            @NonNull VulkanState state,
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull SharedBuffer buffer,
            long needed,
            @NonNull MemoryStack stack) {
        final long oldSize = buffer.allocationInfo.size();
        if (needed <= oldSize) {
            return buffer;
        }
        log.debug("Growing a geometry buffer from {} to {} bytes", oldSize, needed);
        SharedBuffer bigger = SharedBuffer.allocateDeviceLocal(needed, state, buffer.usage);
        VkBufferCopy.Buffer region = VkBufferCopy.calloc(1, stack);
        region.get(0).srcOffset(0).dstOffset(0).size(oldSize);
        vkCmdCopyBuffer(commandBuffer, buffer.buffer, bigger.buffer, region);
        state.deferFree(() -> SharedBuffer.free(buffer, state));
        return bigger;
    }

    /**
     * Record a copy from staging into a buffer.
     *
     * @param commandBuffer The frame's command buffer.
     * @param staging The staged data.
     * @param target The buffer to copy into.
     * @param offset Where in the buffer, in bytes.
     * @param region A copy region to fill in.
     */
    private static void copy(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull StagingRing.Staging staging,
            @NonNull SharedBuffer target,
            long offset,
            @NonNull VkBufferCopy.Buffer region) {
        if (staging.size() == 0) {
            return;
        }
        region.get(0).srcOffset(staging.offset()).dstOffset(offset).size(staging.size());
        vkCmdCopyBuffer(commandBuffer, staging.buffer(), target.buffer, region);
    }

    /**
     * Record a global memory barrier.
     *
     * @param commandBuffer The command buffer.
     * @param srcStage The source VkPipelineStageFlags2.
     * @param srcAccess The source VkAccessFlags2.
     * @param dstStage The destination VkPipelineStageFlags2.
     * @param dstAccess The destination VkAccessFlags2.
     * @param stack The stack to allocate on.
     */
    private static void memoryBarrier(
            @NonNull VkCommandBuffer commandBuffer,
            long srcStage,
            long srcAccess,
            long dstStage,
            long dstAccess,
            @NonNull MemoryStack stack) {
        VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack);
        barrier.get(0)
                .sType$Default()
                .srcStageMask(srcStage)
                .srcAccessMask(srcAccess)
                .dstStageMask(dstStage)
                .dstAccessMask(dstAccess);
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier));
    }

    /**
     * Free everything, when shutting down. The GPU must be idle. Meshes still registered are
     * dropped.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        Upload upload;
        while ((upload = uploads.poll()) != null) {
            state.stagingRing.free(state, upload.vertices());
            state.stagingRing.free(state, upload.indices());
        }
        registry.removeAll();
        retiring.clear();
        SharedBuffer.free(vertices, state);
        SharedBuffer.free(indices, state);
        meshTable.cleanup(state);
    }
}
