package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.vulkan.VK10.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.SkinnedMeshHandle;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.scene.Observer;
import com.ikalagaming.graphics.vulkan.stages.InstanceDrawUpdate;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Vector3dc;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Draws skinned meshes, like characters, posed by palettes of bone matrices their owners send each
 * frame. The meshes live in their own geometry buffers in {@link SkinnedVertex} format, skinned in
 * the vertex shader, see {@code skinning.glsl}. Nothing here knows what is being drawn: a skinned
 * instance is a set of meshes, a palette, bounds and a mask of hidden regions.
 *
 * <p>Instances aren't culled on the GPU like standard ones. There are few of them, so each frame
 * {@link #prepare} culls them against the observer on the CPU and writes their draw commands, and
 * the scene pass draws them with one indirect draw, as do the shadow maps redrawn every frame.
 *
 * @author Ches Burks
 */
public final class SkinnedRenderer {
    /** Usage of the data and draw record buffers, which shaders read by device address. */
    private static final int STORAGE_USAGE = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;

    /** Usage of the draw command buffer. */
    private static final int COMMAND_USAGE = VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;

    /** The geometry buffers skinned meshes live in. */
    @Getter private final GeometryArena arena;

    /** The skinned instances. */
    @Getter private final SkinnedRegistry registry = new SkinnedRegistry();

    /** Packs each frame. Render thread. */
    private final SkinnedPacker packer = new SkinnedPacker();

    /** Each frame's instances and palettes. */
    private final SharedBuffer[] data;

    /** Each frame's draw records. */
    private final SharedBuffer[] draws;

    /** Each frame's draw commands. */
    private final SharedBuffer[] commands;

    /** What each frame packed. */
    private final SkinnedPacker.Counts[] counts;

    /** Scratch for the observer's frustum planes. */
    private final float[] planes = new float[FrustumPlanes.FLOATS];

    /** Scratch for the observer's projection × view. */
    private final Matrix4f projectionView = new Matrix4f();

    /**
     * Create the geometry buffers and per-frame buffers.
     *
     * @param state The Vulkan state.
     */
    public SkinnedRenderer(@NonNull VulkanState state) {
        arena = new GeometryArena(state, SkinnedVertex.SIZE);
        final int frames = GraphicsManager.MAX_FRAMES_IN_FLIGHT;
        data = new SharedBuffer[frames];
        draws = new SharedBuffer[frames];
        commands = new SharedBuffer[frames];
        counts = new SkinnedPacker.Counts[frames];
        for (int i = 0; i < frames; ++i) {
            data[i] = SharedBuffer.allocate(SharedBuffer.MIN_CAPACITY, state, STORAGE_USAGE);
            draws[i] = SharedBuffer.allocate(SharedBuffer.MIN_CAPACITY, state, STORAGE_USAGE);
            commands[i] = SharedBuffer.allocate(SharedBuffer.MIN_CAPACITY, state, COMMAND_USAGE);
            counts[i] = SkinnedPacker.Counts.NONE;
        }
    }

    /**
     * Add a skinned mesh, checked and copied to staging memory before this returns. Safe from any
     * thread.
     *
     * @param state The Vulkan state.
     * @param owner The key of the plugin that owns it.
     * @param vertices The scene vertices, {@link SkinnedVertex#SCENE_FLOATS} floats each.
     * @param joints Four bone indices per vertex.
     * @param weights Four weights per vertex.
     * @param regions One mask region per vertex, or null for none.
     * @param indices The triangle indices.
     * @param aabbMin The minimum corner of the mesh's rest bounds.
     * @param aabbMax The maximum corner of the mesh's rest bounds.
     * @return The handle, pending until uploaded.
     * @throws IllegalArgumentException If the data is malformed.
     */
    public SkinnedMeshHandle register(
            @NonNull VulkanState state,
            @NonNull String owner,
            float @NonNull [] vertices,
            int @NonNull [] joints,
            float @NonNull [] weights,
            int[] regions,
            int @NonNull [] indices,
            @NonNull Vector3fc aabbMin,
            @NonNull Vector3fc aabbMax) {
        final int vertexCount = vertices.length / SkinnedVertex.SCENE_FLOATS;
        ByteBuffer vertexData = MemoryUtil.memAlloc(vertexCount * SkinnedVertex.SIZE);
        ByteBuffer indexData = MemoryUtil.memAlloc(indices.length * Integer.BYTES);
        try {
            final int jointCount =
                    SkinnedVertex.pack(vertices, joints, weights, regions, vertexData);
            vertexData.flip();
            indexData.asIntBuffer().put(indices);
            MeshHandle mesh = arena.register(state, owner, vertexData, indexData, aabbMin, aabbMax);
            return new SkinnedMeshHandle(mesh, jointCount);
        } finally {
            MemoryUtil.memFree(indexData);
            MemoryUtil.memFree(vertexData);
        }
    }

    /**
     * Record the uploads of meshes registered since last frame, at the start of the frame.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The frame's command buffer.
     */
    public void record(@NonNull VulkanState state, @NonNull VkCommandBuffer commandBuffer) {
        arena.record(state, commandBuffer);
    }

    /**
     * Pack this frame's instances: cull them against the observer and write their data and draw
     * commands. Call before the shadow and scene passes.
     *
     * @param state The Vulkan state.
     * @param camera The camera's world position, the origin of render space.
     * @param observer The point of view to cull against.
     * @param materials The scene's materials, for their indices.
     */
    public void prepare(
            @NonNull VulkanState state,
            @NonNull Vector3dc camera,
            @NonNull Observer observer,
            @NonNull MaterialCache materials) {
        final int frame = state.frameIndex;
        observer.getProjectionMatrix().mul(observer.getViewMatrix(), projectionView);
        FrustumPlanes.extract(projectionView, observer.getPosition(), camera, planes);
        final MeshRegistry meshes = arena.getRegistry();
        counts[frame] =
                packer.pack(
                        registry,
                        part -> meshes.locate(part.mesh().mesh()),
                        materials::getMaterialIndex,
                        state.textureRegistry::slotOrDefault,
                        camera,
                        planes,
                        new SkinnedPacker.Buffers() {
                            @Override
                            public ByteBuffer data(long bytes) {
                                return mapped(data[frame], bytes, state);
                            }

                            @Override
                            public ByteBuffer draws(long bytes) {
                                return mapped(draws[frame], bytes, state);
                            }

                            @Override
                            public ByteBuffer commands(long bytes) {
                                return mapped(commands[frame], bytes, state);
                            }
                        });
    }

    /**
     * Grow a per-frame buffer if needed and map it.
     *
     * @param buffer The buffer.
     * @param bytes How many bytes are needed.
     * @param state The Vulkan state.
     * @return The mapped bytes.
     */
    private static ByteBuffer mapped(SharedBuffer buffer, long bytes, VulkanState state) {
        buffer.ensureCapacity(bytes, state);
        return MemoryUtil.memByteBuffer(buffer.allocationInfo.pMappedData(), (int) bytes);
    }

    /**
     * Whether this frame draws any skinned instance into the scene.
     *
     * @param frameIndex The frame in flight.
     * @return True if there are scene draws.
     */
    public boolean hasSceneDraws(int frameIndex) {
        return counts[frameIndex].sceneCommands() > 0;
    }

    /**
     * Whether this frame draws any skinned instance into the shadow maps.
     *
     * @param frameIndex The frame in flight.
     * @return True if there are shadow draws.
     */
    public boolean hasShadowDraws(int frameIndex) {
        return counts[frameIndex].shadowCommands() > 0;
    }

    /**
     * Draw this frame's skinned instances into the scene. The skinned scene pipeline must be bound.
     *
     * @param commandBuffer The command buffer, inside the rendering.
     * @param frameIndex The frame in flight.
     * @param pipelineLayout The pipeline layout, for the push constants.
     * @param pushOffset Where the shader's two buffer addresses go in the push constants.
     */
    public void drawScene(
            @NonNull VkCommandBuffer commandBuffer,
            int frameIndex,
            long pipelineLayout,
            int pushOffset) {
        draw(
                commandBuffer,
                frameIndex,
                pipelineLayout,
                pushOffset,
                0,
                counts[frameIndex].sceneCommands());
    }

    /**
     * Draw this frame's shadow casting skinned instances into a shadow map. The skinned shadow
     * pipeline must be bound.
     *
     * @param commandBuffer The command buffer, inside the rendering.
     * @param frameIndex The frame in flight.
     * @param pipelineLayout The pipeline layout, for the push constants.
     * @param pushOffset Where the shader's two buffer addresses go in the push constants.
     */
    public void drawShadows(
            @NonNull VkCommandBuffer commandBuffer,
            int frameIndex,
            long pipelineLayout,
            int pushOffset) {
        final SkinnedPacker.Counts frame = counts[frameIndex];
        draw(
                commandBuffer,
                frameIndex,
                pipelineLayout,
                pushOffset,
                frame.shadowFirst(),
                frame.shadowCommands());
    }

    /**
     * Bind the skinned geometry and the frame's buffers, and draw a range of commands.
     *
     * @param commandBuffer The command buffer, inside the rendering.
     * @param frameIndex The frame in flight.
     * @param pipelineLayout The pipeline layout.
     * @param pushOffset Where the two buffer addresses go in the push constants.
     * @param first The first command.
     * @param count How many commands.
     */
    private void draw(
            VkCommandBuffer commandBuffer,
            int frameIndex,
            long pipelineLayout,
            int pushOffset,
            int first,
            int count) {
        if (count == 0) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer addresses = stack.malloc(2 * Long.BYTES);
            addresses.putLong(0, data[frameIndex].deviceAddress);
            addresses.putLong(Long.BYTES, draws[frameIndex].deviceAddress);
            vkCmdPushConstants(
                    commandBuffer,
                    pipelineLayout,
                    VK_SHADER_STAGE_VERTEX_BIT,
                    pushOffset,
                    addresses);
            LongBuffer vertexBuffers = stack.longs(arena.getVertices().buffer);
            vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, stack.longs(0));
            vkCmdBindIndexBuffer(commandBuffer, arena.getIndices().buffer, 0, VK_INDEX_TYPE_UINT32);
            vkCmdDrawIndexedIndirect(
                    commandBuffer,
                    commands[frameIndex].buffer,
                    (long) first * InstanceDrawUpdate.DRAW_COMMAND_SIZE,
                    count,
                    InstanceDrawUpdate.DRAW_COMMAND_SIZE);
        }
    }

    /**
     * Remove every skinned instance and mesh a plugin owns. Safe from any thread.
     *
     * @param owner The plugin's key.
     * @return How many instances and meshes were removed.
     */
    public int removeAllOwnedBy(@NonNull String owner) {
        return registry.removeAllOwnedBy(owner) + arena.releaseAllOwnedBy(owner);
    }

    /**
     * Free every buffer. The GPU must be idle.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        for (int i = 0; i < data.length; ++i) {
            SharedBuffer.free(data[i], state);
            SharedBuffer.free(draws[i], state);
            SharedBuffer.free(commands[i], state);
        }
        arena.cleanup(state);
    }
}
