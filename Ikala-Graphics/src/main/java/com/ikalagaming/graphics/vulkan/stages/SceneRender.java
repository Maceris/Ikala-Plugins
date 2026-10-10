package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.MeshKind;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.bake.BakedVertex;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.*;
import com.ikalagaming.graphics.vulkan.RenderStage;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

/**
 * Handles rendering of scene geometry to the g-buffer, in two halves around the depth pyramid. This
 * stage is the early half: it clears the g-buffer, draws what the early culling pass listed, and
 * leaves the depth readable for the pyramid. {@link #getLate()} is the late half: it draws what the
 * late culling pass found, and the animated models, on top, then leaves the g-buffer in the read
 * only layout for the light stage.
 */
@Slf4j
public class SceneRender implements RenderStage {

    /** The constant_id of {@code ALPHA_TEST} in {@code scene.frag}. */
    private static final int ALPHA_TEST_CONSTANT_ID = 0;

    /** The size of a material in the materials buffer, in bytes. */
    public static final int MATERIAL_SIZE = ShaderBindings.Scene.Material.SIZEOF;

    /** The storage buffer bindings in the descriptor set, in the order we update them. */
    private static final int[] STORAGE_BINDINGS = {
        ShaderBindings.Scene.MODEL_MATRICES_BINDING,
        ShaderBindings.Scene.MATERIALS_BINDING,
        ShaderBindings.Scene.VISIBLE_BINDING
    };

    /**
     * Write every material in the cache into this frame's materials buffer, which the light stage
     * also reads. The cache only tracks one dirty flag, but each frame has its own buffer, so we
     * write them all every frame. There aren't many materials.
     *
     * @param scene The scene.
     * @param state The Vulkan state.
     * @param frameData The data for the current frame.
     */
    static void updateMaterials(
            @NonNull Scene scene, @NonNull VulkanState state, @NonNull PerFrameData frameData) {
        final MaterialCache cache = scene.getMaterialCache();
        final int materialCount = cache.getMaterialCount();
        frameData.materials.ensureCapacity((long) materialCount * MATERIAL_SIZE, state);
        if (materialCount <= 0) {
            return;
        }

        ByteBuffer materialData =
                MemoryUtil.memByteBuffer(
                        frameData.materials.allocationInfo.pMappedData(),
                        materialCount * MATERIAL_SIZE);

        for (int i = 0; i < materialCount; ++i) {
            Material material = cache.getMaterial(i);

            Vector4f baseColor = material.getBaseColor();
            materialData.putFloat(baseColor.x);
            materialData.putFloat(baseColor.y);
            materialData.putFloat(baseColor.z);
            materialData.putFloat(baseColor.w);

            materialData.putFloat(material.getAnisotropic());
            materialData.putFloat(material.getClearcoat());
            materialData.putFloat(material.getClearcoatGloss());
            materialData.putFloat(material.getMetallic());

            materialData.putFloat(material.getRoughness());
            materialData.putFloat(material.getSheen());
            materialData.putFloat(material.getSheenTint());
            materialData.putFloat(material.getSpecular());

            materialData.putFloat(material.getSpecularTint());
            materialData.putFloat(material.getSubsurface());
            materialData.putInt(state.textureRegistry.slotOrDefault(material.getNormalMap()));
            materialData.putInt(state.textureRegistry.slotOrDefault(material.getTexture()));
        }
    }

    /** The shader to use for rendering. */
    @NonNull @Setter private ShaderVulkan shader;

    /** The shader for baked sections, in the baked vertex format. */
    @NonNull private final ShaderVulkan bakedShader;

    /** VkPipeline for baked opaque sections, with no alpha test so depth is tested early. */
    private long bakedPipeline;

    /** VkPipeline for baked sections with cut out or see-through textures. */
    private long bakedAlphaPipeline;

    /** VkPipeline for drawing baked sections as wireframes. */
    private long bakedWireframe;

    /**
     * The late half of the scene pass, which shares this stage's pipelines and descriptor sets, so
     * it is only initialized and cleaned up through this stage. -- GETTER -- The late half.
     *
     * @return The stage that draws the late half.
     */
    @Getter private final RenderStage late = this::renderLate;

    /** VkDescriptorSetLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorSetLayout;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /** VkPipeline pointer for drawing wireframes, will be VK_NULL_HANDLE if not set up. */
    private long pipelineWireframe;

    /** VkDescriptorPool pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorPool;

    /** The VkDescriptorSet for each frame in flight, VK_NULL_HANDLE if not set up. */
    private final long[] descriptorSets;

    /**
     * The VkBuffer each storage binding of each frame's descriptor set points at, so we know when
     * to rewrite them.
     */
    private final long[][] writtenBuffers;

    /**
     * Set up the scene render stage.
     *
     * @param shader The shader to use for rendering.
     * @param bakedShader The shader for baked sections.
     */
    public SceneRender(
            final @NonNull ShaderVulkan shader, final @NonNull ShaderVulkan bakedShader) {
        this.shader = shader;
        this.bakedShader = bakedShader;
        this.bakedPipeline = VK_NULL_HANDLE;
        this.bakedAlphaPipeline = VK_NULL_HANDLE;
        this.bakedWireframe = VK_NULL_HANDLE;
        this.descriptorSetLayout = VK_NULL_HANDLE;
        this.pipelineLayout = VK_NULL_HANDLE;
        this.pipeline = VK_NULL_HANDLE;
        this.pipelineWireframe = VK_NULL_HANDLE;
        this.descriptorPool = VK_NULL_HANDLE;
        this.descriptorSets = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];
        this.writtenBuffers =
                new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT][STORAGE_BINDINGS.length];
    }

    @Override
    public void initialize(@NonNull VulkanState vulkanState) {
        log.debug("Initializing scene render");
        createPipelineLayout(vulkanState);
        this.pipeline = createPipeline(vulkanState, shader, false, false, true);
        this.pipelineWireframe = createPipeline(vulkanState, shader, false, true, true);
        this.bakedPipeline = createPipeline(vulkanState, bakedShader, true, false, false);
        this.bakedAlphaPipeline = createPipeline(vulkanState, bakedShader, true, false, true);
        this.bakedWireframe = createPipeline(vulkanState, bakedShader, true, true, true);
    }

    @Override
    public void cleanup(@NonNull VulkanState vulkanState) {
        // Freed along with the pool
        Arrays.fill(descriptorSets, VK_NULL_HANDLE);
        vkDestroyDescriptorPool(vulkanState.device.logical, descriptorPool, null);
        descriptorPool = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, pipelineWireframe, null);
        pipelineWireframe = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, bakedPipeline, null);
        bakedPipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, bakedAlphaPipeline, null);
        bakedAlphaPipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, bakedWireframe, null);
        bakedWireframe = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(vulkanState.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(vulkanState.device.logical, descriptorSetLayout, null);
        descriptorSetLayout = VK_NULL_HANDLE;
    }

    @Override
    public void render(
            Scene scene,
            @NonNull Window window,
            @NonNull VulkanState vulkanState,
            int renderConfig) {
        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];
        final PerFrameData frameData = vulkanState.perFrameData[vulkanState.frameIndex];
        final GBuffer gBuffer = frameData.gBuffer;

        updateMaterials(scene, vulkanState, frameData);
        updateUniforms(scene, frameData);
        updateBindings(vulkanState, frameData);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            transitionForRendering(commandBuffer, gBuffer, stack);
            // Cleared to zero like OpenGL, the material index clears to the default material
            drawList(
                    commandBuffer,
                    window,
                    vulkanState,
                    renderConfig,
                    VK_ATTACHMENT_LOAD_OP_CLEAR,
                    InstanceDrawUpdate.LIST_SCENE_EARLY,
                    false,
                    stack);
            transitionForPyramid(commandBuffer, gBuffer, stack);
        }
    }

    /**
     * Draw the late half of the scene pass on top of the early half, then leave the g-buffer ready
     * for the light stage.
     *
     * @param scene The scene.
     * @param window The window.
     * @param vulkanState The Vulkan state.
     * @param renderConfig The render configuration.
     */
    private void renderLate(
            Scene scene,
            @NonNull Window window,
            @NonNull VulkanState vulkanState,
            int renderConfig) {
        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];
        final GBuffer gBuffer = vulkanState.perFrameData[vulkanState.frameIndex].gBuffer;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            transitionForLate(commandBuffer, gBuffer, stack);
            drawList(
                    commandBuffer,
                    window,
                    vulkanState,
                    renderConfig,
                    VK_ATTACHMENT_LOAD_OP_LOAD,
                    InstanceDrawUpdate.LIST_SCENE_LATE,
                    true,
                    stack);
            transitionForReading(commandBuffer, gBuffer, stack);
        }
    }

    /**
     * Draw one of the scene's visible lists into the g-buffer, in its own rendering scope.
     *
     * @param commandBuffer The command buffer to record into.
     * @param window The window, to find the size to render at.
     * @param state The Vulkan state.
     * @param renderConfig The render configuration, to pick wireframe or not.
     * @param loadOp The VkAttachmentLoadOp: clear for the early half, load for the late one.
     * @param list Which visible list to draw.
     * @param listedModels Whether to also draw the animated models.
     * @param stack The stack to allocate on.
     */
    private void drawList(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull Window window,
            @NonNull VulkanState state,
            int renderConfig,
            int loadOp,
            int list,
            boolean listedModels,
            @NonNull MemoryStack stack) {
        final PerFrameData frameData = state.perFrameData[state.frameIndex];
        final GBuffer gBuffer = frameData.gBuffer;
        // The g-buffer can be bigger than the window, but not smaller
        final int width = Math.min(window.getWidth(), gBuffer.width());
        final int height = Math.min(window.getHeight(), gBuffer.height());

        VkRenderingAttachmentInfo.Buffer colorAttachments =
                VkRenderingAttachmentInfo.calloc(GBuffer.TEXTURE_COUNT, stack);
        for (int i = 0; i < GBuffer.TEXTURE_COUNT; i++) {
            colorAttachments
                    .get(i)
                    .sType$Default()
                    .imageView(gBuffer.textures()[i].view)
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(loadOp)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
        }
        VkRenderingAttachmentInfo depthAttachment =
                VkRenderingAttachmentInfo.calloc(stack)
                        .sType$Default()
                        .imageView(gBuffer.depth().view)
                        .imageLayout(VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL)
                        .loadOp(loadOp)
                        .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
        depthAttachment.clearValue().depthStencil().depth(1.0f);

        VkRenderingInfo renderingInfo =
                VkRenderingInfo.calloc(stack)
                        .sType$Default()
                        .renderArea(area -> area.extent().set(width, height))
                        .layerCount(1)
                        .pColorAttachments(colorAttachments)
                        .pDepthAttachment(depthAttachment);
        vkCmdBeginRendering(commandBuffer, renderingInfo);

        final boolean anyListed = listedModels && !frameData.modelDrawInfo.isEmpty();
        final boolean anyCulled = frameData.meshSlotCount + frameData.bakedSlotCount > 0;
        if ((anyCulled || anyListed) && width > 0 && height > 0) {
            drawModels(commandBuffer, state, renderConfig, width, height, list, anyListed, stack);
        }

        vkCmdEndRendering(commandBuffer);
    }

    /**
     * Record one visible list's draws: one indirect draw for everything culling handled, then the
     * animated models if asked.
     *
     * @param commandBuffer The command buffer to record into.
     * @param state The Vulkan state.
     * @param renderConfig The render configuration, to pick wireframe or not.
     * @param width The width to render in pixels.
     * @param height The height to render in pixels.
     * @param list Which visible list to draw.
     * @param listedModels Whether to also draw the animated models.
     * @param stack The stack to allocate on.
     */
    private void drawModels(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            int renderConfig,
            int width,
            int height,
            int list,
            boolean listedModels,
            @NonNull MemoryStack stack) {
        final PerFrameData frameData = state.perFrameData[state.frameIndex];
        final boolean wireframe = RenderConfig.sceneIsWireframe(renderConfig);

        // A negative height flips y so the projection matrices work the same as in OpenGL
        VkViewport.Buffer viewports = VkViewport.calloc(1, stack);
        viewports.get(0).x(0).y(height).width(width).height(-height).minDepth(0).maxDepth(1);
        vkCmdSetViewport(commandBuffer, 0, viewports);
        VkRect2D.Buffer scissors = VkRect2D.calloc(1, stack);
        scissors.get(0).extent().set(width, height);
        vkCmdSetScissor(commandBuffer, 0, scissors);

        vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipelineLayout,
                0,
                stack.longs(
                        descriptorSets[state.frameIndex],
                        state.bindlessTextures.getDescriptorSet()),
                null);

        LongBuffer vertexBuffers = stack.callocLong(1);
        LongBuffer vertexOffsets = stack.callocLong(1);

        // Everything culling handled: the list's commands for each kind, one per mesh slot, from
        // that kind's buffers with its pipeline. Standard last, which the listed models need.
        for (int k = MeshKind.ALL.length - 1; k >= 0; --k) {
            final MeshKind kind = MeshKind.ALL[k];
            final int slots =
                    InstanceDrawUpdate.slotsOf(
                            kind, frameData.meshSlotCount, frameData.bakedSlotCount);
            if (slots == 0 && kind != MeshKind.STANDARD) {
                continue;
            }
            vkCmdBindPipeline(
                    commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineFor(kind, wireframe));
            // Every mesh's indices are in the shared index buffer, found by each command's first
            // index
            final GeometryArena arena = state.arenaFor(kind);
            vkCmdBindIndexBuffer(commandBuffer, arena.getIndices().buffer, 0, VK_INDEX_TYPE_UINT32);
            vertexBuffers.put(0, arena.getVertices().buffer);
            vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, vertexOffsets);
            if (slots == 0) {
                continue;
            }
            vkCmdDrawIndexedIndirect(
                    commandBuffer,
                    frameData.sceneDrawCommands.buffer,
                    (long)
                                    InstanceDrawUpdate.commandIndex(
                                            kind,
                                            list,
                                            0,
                                            frameData.meshSlotCount,
                                            frameData.bakedSlotCount)
                            * InstanceDrawUpdate.DRAW_COMMAND_SIZE,
                    slots,
                    InstanceDrawUpdate.DRAW_COMMAND_SIZE);
        }
        if (listedModels) {
            drawListedModels(commandBuffer, state, frameData, vertexBuffers, vertexOffsets);
        }
    }

    /**
     * The pipeline a kind of mesh is drawn with.
     *
     * @param kind The kind.
     * @param wireframe Whether to draw wireframes.
     * @return The VkPipeline.
     */
    private long pipelineFor(@NonNull MeshKind kind, boolean wireframe) {
        if (!kind.isBaked()) {
            return wireframe ? pipelineWireframe : pipeline;
        }
        if (wireframe) {
            return bakedWireframe;
        }
        // See-through sections are drawn cut out until there is a blended pass
        return kind == MeshKind.BAKED_OPAQUE ? bakedPipeline : bakedAlphaPipeline;
    }

    /**
     * Draw the animated models from their CPU-written commands, which culling doesn't handle. The
     * shared vertex buffer must already be bound, and is bound again afterward.
     *
     * @param commandBuffer The command buffer, inside the rendering.
     * @param state The Vulkan state.
     * @param frameData This frame's data.
     * @param vertexBuffers A buffer to put the vertex buffer handle in.
     * @param vertexOffsets The vertex buffer offsets, zero.
     */
    static void drawListedModels(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            @NonNull PerFrameData frameData,
            @NonNull LongBuffer vertexBuffers,
            @NonNull LongBuffer vertexOffsets) {
        boolean rebound = false;
        for (var entry : frameData.modelDrawInfo.entrySet()) {
            final Model model = entry.getKey();
            if (!model.isAnimated()) {
                // Only listed for the normal and tangent lines
                continue;
            }
            final PerFrameData.ModelDrawInfo info = entry.getValue();
            int meshIndex = 0;
            for (MeshData mesh : model.getMeshDataList()) {
                final long vertexSource = SceneRender.vertexSource(state, model, mesh);
                if (vertexSource == VK_NULL_HANDLE) {
                    // The animation stage has not run for it yet
                    meshIndex += 1;
                    continue;
                }
                vertexBuffers.put(0, vertexSource);
                vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, vertexOffsets);
                rebound = true;
                final long commandOffset =
                        (long) (info.firstCommand() + meshIndex * info.commandCount())
                                * InstanceDrawUpdate.DRAW_COMMAND_SIZE;
                vkCmdDrawIndexedIndirect(
                        commandBuffer,
                        frameData.sceneDrawCommands.buffer,
                        commandOffset,
                        info.commandCount(),
                        InstanceDrawUpdate.DRAW_COMMAND_SIZE);
                meshIndex += 1;
            }
        }
        if (rebound) {
            vertexBuffers.put(0, state.geometry.getVertices().buffer);
            vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, vertexOffsets);
        }
    }

    /**
     * Find the buffer to read a mesh's vertices from. Most meshes draw from the shared vertex
     * buffer, at the offset in their draw commands. Animated models draw from the animation output,
     * which has a copy of the vertices for each pose.
     *
     * @param state The Vulkan state.
     * @param model The model the mesh belongs to.
     * @param mesh The mesh.
     * @return The VkBuffer, or VK_NULL_HANDLE if an animated model has no output yet.
     */
    static long vertexSource(
            @NonNull VulkanState state, @NonNull Model model, @NonNull MeshData mesh) {
        if (!model.isAnimated()) {
            return state.geometry.getVertices().buffer;
        }
        final SharedBuffer target = mesh.getAnimationTargetBuffer();
        return target == null ? VK_NULL_HANDLE : target.buffer;
    }

    /**
     * Move the g-buffer to the attachment layouts, discarding the old contents since we clear it.
     *
     * @param commandBuffer The command buffer to record into.
     * @param gBuffer The g-buffer.
     * @param stack The stack to allocate on.
     */
    private static void transitionForRendering(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull GBuffer gBuffer,
            @NonNull MemoryStack stack) {
        VkImageMemoryBarrier2.Buffer barriers =
                VkImageMemoryBarrier2.calloc(GBuffer.TEXTURE_COUNT + 1, stack);
        // Last read by the light stage, a frame or more ago
        for (int i = 0; i < GBuffer.TEXTURE_COUNT; i++) {
            imageBarrier(
                    barriers.get(i),
                    gBuffer.textures()[i].texture,
                    VK_IMAGE_ASPECT_COLOR_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_NONE,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        }
        imageBarrier(
                barriers.get(GBuffer.TEXTURE_COUNT),
                gBuffer.depth().texture,
                VK_IMAGE_ASPECT_DEPTH_BIT,
                VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                VK_ACCESS_2_NONE,
                VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                        | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                VK_IMAGE_LAYOUT_UNDEFINED,
                VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL);
        VkDependencyInfo dependencyInfo =
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers);
        vkCmdPipelineBarrier2(commandBuffer, dependencyInfo);
    }

    /**
     * Move the depth to the read only layout after the early half, so the depth pyramid can be
     * built from it. The bindless descriptor it is read through expects that layout.
     *
     * @param commandBuffer The command buffer to record into.
     * @param gBuffer The g-buffer.
     * @param stack The stack to allocate on.
     */
    private static void transitionForPyramid(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull GBuffer gBuffer,
            @NonNull MemoryStack stack) {
        VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(1, stack);
        imageBarrier(
                barriers.get(0),
                gBuffer.depth().texture,
                VK_IMAGE_ASPECT_DEPTH_BIT,
                VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
                VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL);
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers));
    }

    /**
     * Get the g-buffer ready for the late half to draw on top of the early half: the depth goes
     * back to being an attachment once the pyramid is built, and the colors are kept.
     *
     * @param commandBuffer The command buffer to record into.
     * @param gBuffer The g-buffer.
     * @param stack The stack to allocate on.
     */
    private static void transitionForLate(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull GBuffer gBuffer,
            @NonNull MemoryStack stack) {
        VkImageMemoryBarrier2.Buffer barriers =
                VkImageMemoryBarrier2.calloc(GBuffer.TEXTURE_COUNT + 1, stack);
        for (int i = 0; i < GBuffer.TEXTURE_COUNT; i++) {
            imageBarrier(
                    barriers.get(i),
                    gBuffer.textures()[i].texture,
                    VK_IMAGE_ASPECT_COLOR_BIT,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        }
        imageBarrier(
                barriers.get(GBuffer.TEXTURE_COUNT),
                gBuffer.depth().texture,
                VK_IMAGE_ASPECT_DEPTH_BIT,
                VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_NONE,
                VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                        | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL,
                VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL);
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers));
    }

    /**
     * Move the g-buffer to the read only layout so the light stage can sample it.
     *
     * @param commandBuffer The command buffer to record into.
     * @param gBuffer The g-buffer.
     * @param stack The stack to allocate on.
     */
    private static void transitionForReading(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull GBuffer gBuffer,
            @NonNull MemoryStack stack) {
        VkImageMemoryBarrier2.Buffer barriers =
                VkImageMemoryBarrier2.calloc(GBuffer.TEXTURE_COUNT + 1, stack);
        for (int i = 0; i < GBuffer.TEXTURE_COUNT; i++) {
            imageBarrier(
                    barriers.get(i),
                    gBuffer.textures()[i].texture,
                    VK_IMAGE_ASPECT_COLOR_BIT,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL);
        }
        imageBarrier(
                barriers.get(GBuffer.TEXTURE_COUNT),
                gBuffer.depth().texture,
                VK_IMAGE_ASPECT_DEPTH_BIT,
                VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
                VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL);
        VkDependencyInfo dependencyInfo =
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers);
        vkCmdPipelineBarrier2(commandBuffer, dependencyInfo);
    }

    /**
     * Fill out an image barrier for a single mip level and layer.
     *
     * @param barrier The barrier to fill out.
     * @param image The VkImage.
     * @param aspect The VkImageAspectFlags.
     * @param srcStage The source VkPipelineStageFlags2.
     * @param srcAccess The source VkAccessFlags2.
     * @param dstStage The destination VkPipelineStageFlags2.
     * @param dstAccess The destination VkAccessFlags2.
     * @param oldLayout The layout to transition from.
     * @param newLayout The layout to transition to.
     */
    static void imageBarrier(
            @NonNull VkImageMemoryBarrier2 barrier,
            long image,
            int aspect,
            long srcStage,
            long srcAccess,
            long dstStage,
            long dstAccess,
            int oldLayout,
            int newLayout) {
        barrier.sType$Default()
                .srcStageMask(srcStage)
                .srcAccessMask(srcAccess)
                .dstStageMask(dstStage)
                .dstAccessMask(dstAccess)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image)
                .subresourceRange(range -> range.aspectMask(aspect).levelCount(1).layerCount(1));
    }

    /**
     * Write the camera matrices for the current frame.
     *
     * @param scene The scene.
     * @param frameData The data for the current frame.
     */
    private static void updateUniforms(@NonNull Scene scene, @NonNull PerFrameData frameData) {
        ByteBuffer uniformData =
                MemoryUtil.memByteBuffer(
                        frameData.sceneUniforms.allocationInfo.pMappedData(),
                        ShaderBindings.Scene.UNIFORMS_BUFFER_SIZE);
        scene.getProjection()
                .getProjectionMatrix()
                .get(ShaderBindings.Scene.PROJECTION_MATRIX_OFFSET, uniformData);
        scene.getCamera().getViewMatrix().get(ShaderBindings.Scene.VIEW_MATRIX_OFFSET, uniformData);
    }

    /**
     * Point the descriptors at any storage buffers that were reallocated. This frame's set is not
     * in use by the GPU, so it can be updated directly.
     *
     * @param state The Vulkan state.
     * @param frameData The data for the current frame.
     */
    private void updateBindings(@NonNull VulkanState state, @NonNull PerFrameData frameData) {
        final SharedBuffer[] buffers = {
            frameData.sceneModelMatrices, frameData.materials, frameData.visibleInstances
        };
        writeStorageBindings(
                state,
                descriptorSets[state.frameIndex],
                buffers,
                STORAGE_BINDINGS,
                writtenBuffers[state.frameIndex]);
    }

    /**
     * Write storage buffer descriptors for any of the buffers that changed since they were last
     * written into this set. We track the handles per set rather than using {@link
     * SharedBuffer#updated}, since buffers like the materials are bound by more than one stage.
     *
     * @param state The Vulkan state.
     * @param descriptorSet The VkDescriptorSet to update, which must not be in use by the GPU.
     * @param buffers The buffers.
     * @param bindings The binding of each buffer.
     * @param written The VkBuffer each binding currently points at, updated as we write.
     */
    static void writeStorageBindings(
            @NonNull VulkanState state,
            long descriptorSet,
            @NonNull SharedBuffer[] buffers,
            int @NonNull [] bindings,
            long @NonNull [] written) {
        int updateCount = 0;
        for (int i = 0; i < buffers.length; ++i) {
            if (buffers[i].buffer != written[i] && buffers[i].buffer != VK_NULL_HANDLE) {
                updateCount += 1;
            }
        }
        if (updateCount == 0) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(updateCount, stack);
            int index = 0;
            for (int i = 0; i < buffers.length; ++i) {
                SharedBuffer buffer = buffers[i];
                if (buffer.buffer == written[i] || buffer.buffer == VK_NULL_HANDLE) {
                    continue;
                }
                VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
                bufferInfo.get(0).buffer(buffer.buffer).offset(0).range(VK_WHOLE_SIZE);
                writes.get(index)
                        .sType$Default()
                        .dstSet(descriptorSet)
                        .dstBinding(bindings[i])
                        .descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .pBufferInfo(bufferInfo);
                index += 1;
                written[i] = buffer.buffer;
            }
            vkUpdateDescriptorSets(state.device.logical, writes, null);
        }
    }

    private void createPipelineLayout(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkDescriptorSetLayoutBinding.Buffer bindings =
                    VkDescriptorSetLayoutBinding.calloc(4, stack);
            bindings.get(0)
                    .binding(ShaderBindings.Scene.UNIFORMS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            bindings.get(1)
                    .binding(ShaderBindings.Scene.MODEL_MATRICES_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            bindings.get(2)
                    .binding(ShaderBindings.Scene.MATERIALS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            bindings.get(3)
                    .binding(ShaderBindings.Scene.VISIBLE_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);

            // Each frame has its own set, only updated once the GPU is done with that frame
            VkDescriptorSetLayoutCreateInfo descriptorSetLayoutCreateInfo =
                    VkDescriptorSetLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pBindings(bindings);
            checkError(
                    vkCreateDescriptorSetLayout(
                            state.device.logical, descriptorSetLayoutCreateInfo, null, longOutput));
            descriptorSetLayout = longOutput.get(0);

            VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pSetLayouts(
                                    stack.longs(
                                            descriptorSetLayout,
                                            state.bindlessTextures.getDescriptorSetLayout()));
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical, pipelineLayoutCreateInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);

            createDescriptorSets(state, stack);
        }
    }

    /**
     * Create the descriptor pool and a set per frame, and point the sets at the uniform buffers,
     * which never change.
     *
     * @param state The Vulkan state.
     * @param stack The stack to allocate on.
     */
    private void createDescriptorSets(@NonNull VulkanState state, @NonNull MemoryStack stack) {
        LongBuffer longOutput = stack.callocLong(1);

        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2, stack);
        poolSizes
                .get(0)
                .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(GraphicsManager.MAX_FRAMES_IN_FLIGHT);
        poolSizes
                .get(1)
                .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(STORAGE_BINDINGS.length * GraphicsManager.MAX_FRAMES_IN_FLIGHT);
        VkDescriptorPoolCreateInfo descriptorPoolCreateInfo =
                VkDescriptorPoolCreateInfo.calloc(stack)
                        .sType$Default()
                        .maxSets(GraphicsManager.MAX_FRAMES_IN_FLIGHT)
                        .pPoolSizes(poolSizes);
        checkError(
                vkCreateDescriptorPool(
                        state.device.logical, descriptorPoolCreateInfo, null, longOutput));
        descriptorPool = longOutput.get(0);

        LongBuffer setLayouts = stack.callocLong(GraphicsManager.MAX_FRAMES_IN_FLIGHT);
        for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
            setLayouts.put(i, descriptorSetLayout);
        }
        VkDescriptorSetAllocateInfo descriptorSetAlloc =
                VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType$Default()
                        .descriptorPool(descriptorPool)
                        .pSetLayouts(setLayouts);
        checkError(
                vkAllocateDescriptorSets(state.device.logical, descriptorSetAlloc, descriptorSets));

        VkWriteDescriptorSet.Buffer writes =
                VkWriteDescriptorSet.calloc(GraphicsManager.MAX_FRAMES_IN_FLIGHT, stack);
        for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
            bufferInfo
                    .get(0)
                    .buffer(state.perFrameData[i].sceneUniforms.buffer)
                    .offset(0)
                    .range(VK_WHOLE_SIZE);
            writes.get(i)
                    .sType$Default()
                    .dstSet(descriptorSets[i])
                    .dstBinding(ShaderBindings.Scene.UNIFORMS_BINDING)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .pBufferInfo(bufferInfo);
        }
        vkUpdateDescriptorSets(state.device.logical, writes, null);
    }

    /**
     * Describe the vertex format a pipeline reads.
     *
     * @param baked Whether it is the baked format rather than the full one.
     * @param stack The stack to allocate on.
     * @return The attributes, read from binding 0.
     */
    private static VkVertexInputAttributeDescription.Buffer vertexAttributes(
            boolean baked, @NonNull MemoryStack stack) {
        if (baked) {
            // See BakedVertex: steps and user data, octahedral normal and tangent, half UV,
            // material and flags
            final int[] formats = {
                VK_FORMAT_R16G16B16A16_SINT,
                VK_FORMAT_R16G16_SNORM,
                VK_FORMAT_R16G16_SNORM,
                VK_FORMAT_R16G16_SFLOAT,
                VK_FORMAT_R16G16_UINT
            };
            final int[] offsets = {
                BakedVertex.POSITION_OFFSET,
                BakedVertex.NORMAL_OFFSET,
                BakedVertex.TANGENT_OFFSET,
                BakedVertex.UV_OFFSET,
                BakedVertex.MATERIAL_OFFSET
            };
            VkVertexInputAttributeDescription.Buffer attributes =
                    VkVertexInputAttributeDescription.calloc(formats.length, stack);
            for (int i = 0; i < formats.length; i++) {
                attributes.get(i).binding(0).location(i).format(formats[i]).offset(offsets[i]);
            }
            return attributes;
        }
        // Position, normal, tangent, bitangent, texture coordinates
        final int[] componentCounts = {3, 3, 3, 3, 2};
        VkVertexInputAttributeDescription.Buffer attributes =
                VkVertexInputAttributeDescription.calloc(componentCounts.length, stack);
        int offset = 0;
        for (int i = 0; i < componentCounts.length; i++) {
            attributes
                    .get(i)
                    .binding(0)
                    .location(i)
                    .format(
                            componentCounts[i] == 3
                                    ? VK_FORMAT_R32G32B32_SFLOAT
                                    : VK_FORMAT_R32G32_SFLOAT)
                    .offset(offset);
            offset += componentCounts[i] * Float.BYTES;
        }
        return attributes;
    }

    /**
     * Copy a shader's stages, turning the fragment shader's alpha test off if asked, through its
     * {@code ALPHA_TEST} specialization constant.
     *
     * @param shader The shader.
     * @param alphaTest Whether to drop pixels the texture's alpha cuts out.
     * @param stack The stack to allocate on.
     * @return The stages for the pipeline.
     */
    private static VkPipelineShaderStageCreateInfo.Buffer stages(
            @NonNull ShaderVulkan shader, boolean alphaTest, @NonNull MemoryStack stack) {
        final int count = shader.shaderModules.length;
        VkPipelineShaderStageCreateInfo.Buffer stages =
                VkPipelineShaderStageCreateInfo.calloc(count, stack);
        for (int i = 0; i < count; ++i) {
            stages.get(i).set(shader.shaderStages.get(i));
        }
        if (alphaTest) {
            return stages;
        }
        VkSpecializationMapEntry.Buffer entries = VkSpecializationMapEntry.calloc(1, stack);
        entries.get(0).constantID(ALPHA_TEST_CONSTANT_ID).offset(0).size(Integer.BYTES);
        VkSpecializationInfo specialization =
                VkSpecializationInfo.calloc(stack)
                        .pMapEntries(entries)
                        .pData(stack.bytes(new byte[Integer.BYTES]));
        for (int i = 0; i < count; ++i) {
            if (stages.get(i).stage() == VK_SHADER_STAGE_FRAGMENT_BIT) {
                stages.get(i).pSpecializationInfo(specialization);
            }
        }
        return stages;
    }

    /**
     * Create one of the scene pipelines.
     *
     * @param state The Vulkan state.
     * @param shader The shader.
     * @param baked Whether it reads the baked vertex format.
     * @param wireframe Whether to draw wireframes.
     * @param alphaTest Whether to drop pixels the texture's alpha cuts out.
     * @return The VkPipeline.
     */
    private long createPipeline(
            @NonNull VulkanState state,
            @NonNull ShaderVulkan shader,
            boolean baked,
            boolean wireframe,
            boolean alphaTest) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkVertexInputAttributeDescription.Buffer vertexAttributes =
                    vertexAttributes(baked, stack);
            VkVertexInputBindingDescription.Buffer vertexBindings =
                    VkVertexInputBindingDescription.calloc(1, stack);
            vertexBindings
                    .get(0)
                    .binding(0)
                    .stride(baked ? BakedVertex.SIZE : MeshData.VERTEX_SIZE_IN_BYTES)
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            VkPipelineVertexInputStateCreateInfo vertexInputStateCreateInfo =
                    VkPipelineVertexInputStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pVertexBindingDescriptions(vertexBindings)
                            .pVertexAttributeDescriptions(vertexAttributes);

            VkPipelineInputAssemblyStateCreateInfo inputAssemblyState =
                    VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

            VkPipelineViewportStateCreateInfo viewportState =
                    VkPipelineViewportStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .viewportCount(1)
                            .scissorCount(1);

            VkPipelineDynamicStateCreateInfo dynamicState =
                    VkPipelineDynamicStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pDynamicStates(
                                    stack.ints(
                                            VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            // OpenGL's default depth function
            VkPipelineDepthStencilStateCreateInfo depthStencilState =
                    VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .depthTestEnable(true)
                            .depthWriteEnable(true)
                            .depthCompareOp(VK_COMPARE_OP_LESS);

            VkPipelineRenderingCreateInfo renderingCreateInfo =
                    VkPipelineRenderingCreateInfo.calloc(stack)
                            .sType$Default()
                            .pColorAttachmentFormats(
                                    stack.ints(
                                            PipelineManagerVulkan.GBUFFER_COLOR_FORMAT,
                                            PipelineManagerVulkan.GBUFFER_COLOR_FORMAT,
                                            PipelineManagerVulkan.GBUFFER_COLOR_FORMAT,
                                            PipelineManagerVulkan.GBUFFER_MATERIAL_FORMAT))
                            .depthAttachmentFormat(PipelineManagerVulkan.DEPTH_FORMAT);

            // No blending, like OpenGL during the scene pass
            VkPipelineColorBlendAttachmentState.Buffer blendAttachments =
                    VkPipelineColorBlendAttachmentState.calloc(GBuffer.TEXTURE_COUNT, stack);
            for (int i = 0; i < GBuffer.TEXTURE_COUNT; i++) {
                blendAttachments
                        .get(i)
                        .colorWriteMask(
                                VK_COLOR_COMPONENT_R_BIT
                                        | VK_COLOR_COMPONENT_G_BIT
                                        | VK_COLOR_COMPONENT_B_BIT
                                        | VK_COLOR_COMPONENT_A_BIT);
            }
            VkPipelineColorBlendStateCreateInfo colorBlendState =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pAttachments(blendAttachments);

            // Back face culling with counter-clockwise front faces like OpenGL. The flipped
            // viewport keeps the winding the same as OpenGL.
            VkPipelineRasterizationStateCreateInfo rasterizationState =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .polygonMode(wireframe ? VK_POLYGON_MODE_LINE : VK_POLYGON_MODE_FILL)
                            .cullMode(VK_CULL_MODE_BACK_BIT)
                            .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                            .lineWidth(1.0f);
            VkPipelineMultisampleStateCreateInfo multisampleState =
                    VkPipelineMultisampleStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            VkGraphicsPipelineCreateInfo.Buffer pipelineCreateInfos =
                    VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineCreateInfos
                    .get(0)
                    .sType$Default()
                    .pNext(renderingCreateInfo)
                    .stageCount(shader.shaderModules.length)
                    .pStages(stages(shader, alphaTest, stack))
                    .pVertexInputState(vertexInputStateCreateInfo)
                    .pInputAssemblyState(inputAssemblyState)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizationState)
                    .pMultisampleState(multisampleState)
                    .pDepthStencilState(depthStencilState)
                    .pColorBlendState(colorBlendState)
                    .pDynamicState(dynamicState)
                    .layout(pipelineLayout)
                    .renderPass(VK_NULL_HANDLE);

            checkError(
                    vkCreateGraphicsPipelines(
                            state.device.logical,
                            VK_NULL_HANDLE,
                            pipelineCreateInfos,
                            null,
                            longOutput));

            return longOutput.get(0);
        }
    }
}
