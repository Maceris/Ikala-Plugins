package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.MeshKind;
import com.ikalagaming.graphics.Sections;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.bake.BakedVertex;
import com.ikalagaming.graphics.graph.CascadeShadowSplit;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.SkyOcclusion;
import com.ikalagaming.graphics.graph.SunShadowCache;
import com.ikalagaming.graphics.lod.LodVertex;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.*;
import com.ikalagaming.graphics.vulkan.RenderStage;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the scene depth from the directional light into each cascade shadow map. The near cascade
 * is drawn whole every frame. The cached ones, see {@link SunShadowCache}, keep what doesn't move
 * in their maps, drawn again whole or in part only when the cache says, and draw what moves into
 * their maps of what moves every frame there is any. The shadow maps are left in the read only
 * layout for the light stage.
 */
@Slf4j
public class ShadowRender implements RenderStage {

    /**
     * How far every surface is pushed from the light in the shadow maps, in units of the smallest
     * depth step at its depth. Tiny with a 32-bit float depth buffer; the slope term does the work.
     */
    private static final float DEPTH_BIAS_CONSTANT = 1;

    /**
     * How far surfaces are pushed from the light per unit of their depth slope across a texel, so a
     * surface nearly edge-on to the sun, which spans a wide depth range within one texel, doesn't
     * shadow itself. The light stage also offsets along the normal, see lights.frag.
     */
    private static final float DEPTH_BIAS_SLOPE = 1.5f;

    /** The shader to use for rendering. */
    @NonNull @Setter private ShaderVulkan shader;

    /** The shader for baked sections, in the baked vertex format. */
    @NonNull private final ShaderVulkan bakedShader;

    /** VkPipeline for baked sections, will be VK_NULL_HANDLE if not set up. */
    private long bakedPipeline;

    /** The shader for level of detail clusters, in their vertex format. */
    @NonNull private final ShaderVulkan lodShader;

    /** VkPipeline for level of detail clusters, will be VK_NULL_HANDLE if not set up. */
    private long lodPipeline;

    /** The shader for standard meshes in a point light shadow face. */
    @NonNull private final ShaderVulkan pointShader;

    /** VkPipeline for standard meshes in a point light shadow face. */
    private long pointPipeline;

    /** The shader for baked sections in a point light shadow face. */
    @NonNull private final ShaderVulkan pointBakedShader;

    /** VkPipeline for baked sections in a point light shadow face. */
    private long pointBakedPipeline;

    /** Where in the push constants a point shadow face's placement goes, after the matrix. */
    private static final int POINT_PLACE_OFFSET = 16 * Float.BYTES;

    /** VkDescriptorSetLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorSetLayout;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /** VkDescriptorPool pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorPool;

    /** The VkDescriptorSet for each frame in flight, VK_NULL_HANDLE if not set up. */
    private final long[] descriptorSets;

    /**
     * The VkBuffer the model matrix binding of each frame's descriptor set points at, so we know
     * when to rewrite them.
     */
    private final long[][] writtenBuffers;

    /**
     * Set up the shadow render stage.
     *
     * @param shader The shader to use for rendering.
     * @param bakedShader The shader for baked sections.
     * @param lodShader The shader for level of detail clusters.
     * @param pointShader The shader for standard meshes in point light shadow faces.
     * @param pointBakedShader The shader for baked sections in point light shadow faces.
     */
    public ShadowRender(
            final @NonNull ShaderVulkan shader,
            final @NonNull ShaderVulkan bakedShader,
            final @NonNull ShaderVulkan lodShader,
            final @NonNull ShaderVulkan pointShader,
            final @NonNull ShaderVulkan pointBakedShader) {
        this.shader = shader;
        this.bakedShader = bakedShader;
        this.lodShader = lodShader;
        this.pointShader = pointShader;
        this.pointBakedShader = pointBakedShader;
        this.bakedPipeline = VK_NULL_HANDLE;
        this.lodPipeline = VK_NULL_HANDLE;
        this.descriptorSetLayout = VK_NULL_HANDLE;
        this.pipelineLayout = VK_NULL_HANDLE;
        this.pipeline = VK_NULL_HANDLE;
        this.descriptorPool = VK_NULL_HANDLE;
        this.descriptorSets = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];
        this.writtenBuffers = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT][1];
    }

    @Override
    public void initialize(@NonNull VulkanState vulkanState) {
        log.debug("Initializing shadow render");
        createPipelineLayout(vulkanState);
        pipeline = createPipeline(vulkanState, shader, false, false);
        bakedPipeline = createPipeline(vulkanState, bakedShader, true, false);
        lodPipeline = createPipeline(vulkanState, lodShader, false, true);
        pointPipeline = createPipeline(vulkanState, pointShader, false, false);
        pointBakedPipeline = createPipeline(vulkanState, pointBakedShader, true, false);
    }

    @Override
    public void cleanup(@NonNull VulkanState vulkanState) {
        // Freed along with the pool
        Arrays.fill(descriptorSets, VK_NULL_HANDLE);
        vkDestroyDescriptorPool(vulkanState.device.logical, descriptorPool, null);
        descriptorPool = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, bakedPipeline, null);
        bakedPipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, lodPipeline, null);
        lodPipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, pointPipeline, null);
        pointPipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, pointBakedPipeline, null);
        pointBakedPipeline = VK_NULL_HANDLE;
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

        // Worked out by the instance draw update stage, since culling needs them first
        CascadeShadowSplit[] cascadeShadowSplits = frameData.cascadeShadowSplits;

        SceneRender.writeStorageBindings(
                vulkanState,
                descriptorSets[vulkanState.frameIndex],
                new SharedBuffer[] {frameData.sceneModelMatrices},
                new int[] {ShaderBindings.Shadow.MODEL_MATRICES_BINDING},
                writtenBuffers[vulkanState.frameIndex]);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pushConstants = stack.calloc(ShaderBindings.Shadow.PUSH_CONSTANTS_SIZE);
            pushConstants.putLong(
                    ShaderBindings.Shadow.PUSH_CONSTANT_VISIBLE_OFFSET,
                    frameData.visibleInstances.deviceAddress);
            for (int i = 0; i < CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT; ++i) {
                final CascadeShadowSplit cascade = cascadeShadowSplits[i];
                cascade.getProjViewMatrix()
                        .get(
                                ShaderBindings.Shadow.PUSH_CONSTANT_PROJECTION_VIEW_MATRIX_OFFSET,
                                pushConstants);
                final int list = InstanceDrawUpdate.LIST_FIRST_CASCADE + i;
                final TextureInfoVulkan map = frameData.cascadeShadows[i];
                if (!cascade.isCached()) {
                    // Everything, drawn whole every frame; what was there is thrown away
                    beginWriting(commandBuffer, map, VK_IMAGE_LAYOUT_UNDEFINED, stack);
                    renderMap(
                            commandBuffer,
                            vulkanState,
                            map,
                            cascade.getMapSize(),
                            null,
                            list,
                            pushConstants,
                            true,
                            true);
                    endWriting(commandBuffer, map, stack);
                    continue;
                }
                if (cascade.getRedraw() != SunShadowCache.Redraw.NONE) {
                    // What doesn't move, kept from frame to frame, so it is drawn over
                    beginWriting(commandBuffer, map, VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL, stack);
                    renderMap(
                            commandBuffer,
                            vulkanState,
                            map,
                            cascade.getMapSize(),
                            cascade.getRedraw() == SunShadowCache.Redraw.PART
                                    ? cascade.getRedrawRect()
                                    : null,
                            list,
                            pushConstants,
                            true,
                            false);
                    endWriting(commandBuffer, map, stack);
                }
                final TextureInfoVulkan dynamicMap = frameData.cascadeDynamicShadows[i];
                if (frameData.dynamicShadowCasters && dynamicMap != null) {
                    beginWriting(commandBuffer, dynamicMap, VK_IMAGE_LAYOUT_UNDEFINED, stack);
                    renderMap(
                            commandBuffer,
                            vulkanState,
                            dynamicMap,
                            SunShadowCache.DYNAMIC_MAP_SIZE,
                            null,
                            list,
                            pushConstants,
                            false,
                            true);
                    endWriting(commandBuffer, dynamicMap, stack);
                }
            }

            // The sky occlusion map, what doesn't move from straight above, when it is due
            final CascadeShadowSplit sky = frameData.skyView;
            if (frameData.skyMap != null && sky.getRedraw() != SunShadowCache.Redraw.NONE) {
                sky.getProjViewMatrix()
                        .get(
                                ShaderBindings.Shadow.PUSH_CONSTANT_PROJECTION_VIEW_MATRIX_OFFSET,
                                pushConstants);
                beginWriting(
                        commandBuffer, frameData.skyMap, VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL, stack);
                renderMap(
                        commandBuffer,
                        vulkanState,
                        frameData.skyMap,
                        SkyOcclusion.MAP_SIZE,
                        sky.getRedraw() == SunShadowCache.Redraw.PART ? sky.getRedrawRect() : null,
                        InstanceDrawUpdate.LIST_SKY,
                        pushConstants,
                        true,
                        false);
                endWriting(commandBuffer, frameData.skyMap, stack);
            }

            renderPointShadows(scene, vulkanState, commandBuffer, frameData, stack);
        }
    }

    /**
     * Draw the point light shadow faces that are due into the atlas of what doesn't move, and every
     * face of maps with something moving in reach into the atlas of what moves.
     *
     * @param scene The scene.
     * @param state The Vulkan state.
     * @param commandBuffer The command buffer to record into.
     * @param frameData This frame's data.
     * @param stack The stack to allocate on.
     */
    private void renderPointShadows(
            @NonNull Scene scene,
            @NonNull VulkanState state,
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull PerFrameData frameData,
            @NonNull MemoryStack stack) {
        final PointShadows points = state.pointShadows;
        if (frameData.pointAtlas == null) {
            return;
        }
        final Vector3dc camera = scene.getCamera().getPosition();
        final List<PointShadows.Face> faces = points.facesToDraw();
        if (!faces.isEmpty()) {
            beginWriting(
                    commandBuffer, frameData.pointAtlas, VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL, stack);
            renderFaces(
                    commandBuffer, state, frameData, frameData.pointAtlas, faces, camera, false);
            endWriting(commandBuffer, frameData.pointAtlas, stack);
            faces.forEach(points::drawn);
        }
        final List<PointShadows.Face> moving = new ArrayList<>();
        for (int map : points.maps()) {
            if (!points.dynamics(map).isEmpty()) {
                for (int face = 0; face < PointShadows.FACES; ++face) {
                    moving.add(new PointShadows.Face(map, face));
                }
            }
        }
        if (!moving.isEmpty()) {
            beginWriting(
                    commandBuffer, frameData.pointDynamicAtlas, VK_IMAGE_LAYOUT_UNDEFINED, stack);
            renderFaces(
                    commandBuffer,
                    state,
                    frameData,
                    frameData.pointDynamicAtlas,
                    moving,
                    camera,
                    true);
            endWriting(commandBuffer, frameData.pointDynamicAtlas, stack);
        }
    }

    /**
     * Draw point light shadow faces into an atlas: what doesn't move, clearing each face's tile
     * first and keeping the rest, or what moves, clearing the whole atlas.
     *
     * @param commandBuffer The command buffer to record into.
     * @param state The Vulkan state.
     * @param frameData This frame's data.
     * @param atlas The atlas, as a depth attachment.
     * @param faces The faces to draw.
     * @param camera The camera's world position, the render space origin.
     * @param dynamics True to draw what moves, false for what doesn't.
     */
    private void renderFaces(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            @NonNull PerFrameData frameData,
            @NonNull TextureInfoVulkan atlas,
            @NonNull List<PointShadows.Face> faces,
            @NonNull Vector3dc camera,
            boolean dynamics) {
        final PointShadows points = state.pointShadows;
        final int size = PointShadows.ATLAS_SIZE;
        final int tile = PointShadows.TILE_SIZE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkRenderingAttachmentInfo depthAttachment =
                    VkRenderingAttachmentInfo.calloc(stack)
                            .sType$Default()
                            .imageView(atlas.view)
                            .imageLayout(VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL)
                            .loadOp(
                                    dynamics
                                            ? VK_ATTACHMENT_LOAD_OP_CLEAR
                                            : VK_ATTACHMENT_LOAD_OP_LOAD)
                            .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            depthAttachment.clearValue().depthStencil().depth(1.0f);
            VkRenderingInfo renderingInfo =
                    VkRenderingInfo.calloc(stack)
                            .sType$Default()
                            .renderArea(area -> area.extent().set(size, size))
                            .layerCount(1)
                            .pDepthAttachment(depthAttachment);
            vkCmdBeginRendering(commandBuffer, renderingInfo);
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipelineLayout,
                    0,
                    stack.longs(descriptorSets[state.frameIndex]),
                    null);
            final GeometryArena arena = dynamics ? state.geometry : state.bakedGeometry;
            vkCmdBindPipeline(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_GRAPHICS,
                    dynamics ? pointPipeline : pointBakedPipeline);
            vkCmdBindIndexBuffer(commandBuffer, arena.getIndices().buffer, 0, VK_INDEX_TYPE_UINT32);
            vkCmdBindVertexBuffers(
                    commandBuffer, 0, stack.longs(arena.getVertices().buffer), stack.longs(0));
            final MeshRegistry registry = arena.getRegistry();
            final ByteBuffer pushConstants =
                    stack.calloc(ShaderBindings.Shadow.PUSH_CONSTANTS_SIZE);
            final Matrix4f matrix = new Matrix4f();
            final float[] planes = new float[FrustumPlanes.FLOATS];
            final Vector3f center = new Vector3f();
            final Vector3f extents = new Vector3f();
            final float half = Sections.SECTION_SIZE / 2f;
            final Map<Integer, List<SectionManager.Caster>> casters = new HashMap<>();
            VkViewport.Buffer viewports = VkViewport.calloc(1, stack);
            VkRect2D.Buffer scissors = VkRect2D.calloc(1, stack);
            VkClearAttachment.Buffer clear = VkClearAttachment.calloc(1, stack);
            clear.get(0).aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
            clear.get(0).clearValue().depthStencil().depth(1.0f);
            VkClearRect.Buffer clearRect = VkClearRect.calloc(1, stack);
            clearRect.get(0).layerCount(1);
            for (PointShadows.Face face : faces) {
                final int[] origin = PointShadows.tileOrigin(face.map(), face.face());
                viewports
                        .get(0)
                        .x(origin[0])
                        .y(origin[1])
                        .width(tile)
                        .height(tile)
                        .minDepth(0)
                        .maxDepth(1);
                vkCmdSetViewport(commandBuffer, 0, viewports);
                scissors.get(0).offset().set(origin[0], origin[1]);
                scissors.get(0).extent().set(tile, tile);
                vkCmdSetScissor(commandBuffer, 0, scissors);
                if (!dynamics) {
                    clearRect.get(0).rect().offset().set(origin[0], origin[1]);
                    clearRect.get(0).rect().extent().set(tile, tile);
                    vkCmdClearAttachments(commandBuffer, clear, clearRect);
                }
                points.faceMatrix(face.map(), face.face(), camera, matrix);
                matrix.get(0, pushConstants);
                if (dynamics) {
                    for (PointShadows.Dynamic dynamic : points.dynamics(face.map())) {
                        pushConstants.putInt(POINT_PLACE_OFFSET, dynamic.slot());
                        vkCmdPushConstants(
                                commandBuffer,
                                pipelineLayout,
                                VK_SHADER_STAGE_VERTEX_BIT,
                                0,
                                pushConstants);
                        for (MeshHandle mesh : dynamic.meshes()) {
                            final MeshRegistry.Location at = registry.locate(mesh);
                            if (at != null) {
                                vkCmdDrawIndexed(
                                        commandBuffer,
                                        mesh.indexCount(),
                                        1,
                                        at.firstIndex(),
                                        at.vertexOffset(),
                                        0);
                            }
                        }
                    }
                    continue;
                }
                FrustumPlanes.extract(matrix, planes);
                final List<SectionManager.Caster> nearby =
                        casters.computeIfAbsent(
                                face.map(),
                                m -> state.sections.casters(points.center(m), points.range(m)));
                for (SectionManager.Caster caster : nearby) {
                    final Vector3dc corner = caster.origin();
                    final float x = (float) (corner.x() - camera.x());
                    final float y = (float) (corner.y() - camera.y());
                    final float z = (float) (corner.z() - camera.z());
                    center.set(x + half, y + half, z + half);
                    extents.set(half);
                    if (!FrustumPlanes.boxInside(planes, center, extents)) {
                        continue;
                    }
                    final MeshRegistry.Location at = registry.locate(caster.mesh());
                    if (at == null) {
                        continue;
                    }
                    pushConstants
                            .putFloat(POINT_PLACE_OFFSET, x)
                            .putFloat(POINT_PLACE_OFFSET + Float.BYTES, y)
                            .putFloat(POINT_PLACE_OFFSET + 2 * Float.BYTES, z)
                            .putFloat(
                                    POINT_PLACE_OFFSET + 3 * Float.BYTES,
                                    1f / BakedVertex.STEPS_PER_UNIT);
                    vkCmdPushConstants(
                            commandBuffer,
                            pipelineLayout,
                            VK_SHADER_STAGE_VERTEX_BIT,
                            0,
                            pushConstants);
                    vkCmdDrawIndexed(
                            commandBuffer,
                            caster.mesh().indexCount(),
                            1,
                            at.firstIndex(),
                            at.vertexOffset(),
                            0);
                }
            }
            vkCmdEndRendering(commandBuffer);
        }
    }

    /**
     * Make a shadow map ready to draw into, once the light stage of an earlier frame is done
     * reading it.
     *
     * @param commandBuffer The command buffer to record into.
     * @param map The map.
     * @param oldLayout Its layout: undefined to throw away what it holds, read only to keep it.
     * @param stack The stack to allocate on.
     */
    private static void beginWriting(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull TextureInfoVulkan map,
            int oldLayout,
            @NonNull MemoryStack stack) {
        transitionShadowMaps(
                commandBuffer,
                new TextureInfoVulkan[] {map},
                VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                VK_ACCESS_2_NONE,
                VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                        | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                oldLayout,
                VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
                stack);
    }

    /**
     * Leave a shadow map drawn into ready for the light stage to read.
     *
     * @param commandBuffer The command buffer to record into.
     * @param map The map.
     * @param stack The stack to allocate on.
     */
    private static void endWriting(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull TextureInfoVulkan map,
            @NonNull MemoryStack stack) {
        transitionShadowMaps(
                commandBuffer,
                new TextureInfoVulkan[] {map},
                VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
                VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL,
                stack);
    }

    /**
     * Draw the scene depth into a shadow map, whole or in part.
     *
     * @param commandBuffer The command buffer to record into.
     * @param state The Vulkan state.
     * @param shadowMap The shadow map to render into, as a depth attachment.
     * @param size The map's width and height, in texels.
     * @param rect The texels to draw again, min x, min y, max x, max y, or null for the whole map,
     *     which is cleared first. A part is cleared and drawn, and the rest kept.
     * @param list Which visible list the cascade is, to find its draw commands.
     * @param pushConstants The push constants, with the cascade's matrix already filled out.
     * @param statics Whether to draw what doesn't move: baked sections and level of detail.
     * @param dynamics Whether to draw what moves: standard meshes and listed models.
     */
    private void renderMap(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            @NonNull TextureInfoVulkan shadowMap,
            int size,
            int[] rect,
            int list,
            @NonNull ByteBuffer pushConstants,
            boolean statics,
            boolean dynamics) {
        final PerFrameData frameData = state.perFrameData[state.frameIndex];
        final boolean whole = rect == null;
        final int x0 = whole ? 0 : rect[0];
        final int y0 = whole ? 0 : rect[1];
        final int width = whole ? size : rect[2] - rect[0];
        final int height = whole ? size : rect[3] - rect[1];

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkRenderingAttachmentInfo depthAttachment =
                    VkRenderingAttachmentInfo.calloc(stack)
                            .sType$Default()
                            .imageView(shadowMap.view)
                            .imageLayout(VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL)
                            .loadOp(
                                    whole
                                            ? VK_ATTACHMENT_LOAD_OP_CLEAR
                                            : VK_ATTACHMENT_LOAD_OP_LOAD)
                            .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            depthAttachment.clearValue().depthStencil().depth(1.0f);
            VkRenderingInfo renderingInfo =
                    VkRenderingInfo.calloc(stack)
                            .sType$Default()
                            .renderArea(
                                    area -> {
                                        area.offset().set(x0, y0);
                                        area.extent().set(width, height);
                                    })
                            .layerCount(1)
                            .pDepthAttachment(depthAttachment);
            vkCmdBeginRendering(commandBuffer, renderingInfo);

            if (!whole) {
                // Clear just the part drawn again
                VkClearAttachment.Buffer clear = VkClearAttachment.calloc(1, stack);
                clear.get(0).aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                clear.get(0).clearValue().depthStencil().depth(1.0f);
                VkClearRect.Buffer clearRect = VkClearRect.calloc(1, stack);
                clearRect.get(0).layerCount(1);
                clearRect.get(0).rect().offset().set(x0, y0);
                clearRect.get(0).rect().extent().set(width, height);
                vkCmdClearAttachments(commandBuffer, clear, clearRect);
            }

            if (frameData.meshSlotCount + frameData.bakedSlotCount > 0
                    || !frameData.modelDrawInfo.isEmpty()
                    || LodRenderer.hasDraws(frameData)) {
                // Not flipped, so the light stage can use the shadow map coordinates as is
                VkViewport.Buffer viewports = VkViewport.calloc(1, stack);
                viewports.get(0).width(size).height(size).minDepth(0).maxDepth(1);
                vkCmdSetViewport(commandBuffer, 0, viewports);
                VkRect2D.Buffer scissors = VkRect2D.calloc(1, stack);
                scissors.get(0).offset().set(x0, y0);
                scissors.get(0).extent().set(width, height);
                vkCmdSetScissor(commandBuffer, 0, scissors);

                vkCmdBindDescriptorSets(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipelineLayout,
                        0,
                        stack.longs(descriptorSets[state.frameIndex]),
                        null);

                vkCmdPushConstants(
                        commandBuffer,
                        pipelineLayout,
                        VK_SHADER_STAGE_VERTEX_BIT,
                        0,
                        pushConstants);
                LongBuffer vertexBuffers = stack.callocLong(1);
                LongBuffer vertexOffsets = stack.callocLong(1);

                // Level of detail clusters, the cut the scene sees, in this cascade's frustum
                if (statics && LodRenderer.hasDraws(frameData)) {
                    vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, lodPipeline);
                    state.lods.draw(
                            commandBuffer,
                            frameData,
                            pipelineLayout,
                            ShaderBindings.Shadow.PUSH_CONSTANT_LOD_WORLDS_OFFSET,
                            list);
                }

                // Everything culling handled for this cascade, each kind from its own buffers,
                // one command per mesh slot. Standard last, which the listed models need.
                for (int k = MeshKind.ALL.length - 1; k >= 0; --k) {
                    final MeshKind kind = MeshKind.ALL[k];
                    if (kind.isBaked() ? !statics : !dynamics) {
                        continue;
                    }
                    final int slots =
                            InstanceDrawUpdate.slotsOf(
                                    kind, frameData.meshSlotCount, frameData.bakedSlotCount);
                    if (slots == 0 && kind != MeshKind.STANDARD) {
                        continue;
                    }
                    vkCmdBindPipeline(
                            commandBuffer,
                            VK_PIPELINE_BIND_POINT_GRAPHICS,
                            kind.isBaked() ? bakedPipeline : pipeline);
                    final GeometryArena arena = state.arenaFor(kind);
                    vkCmdBindIndexBuffer(
                            commandBuffer, arena.getIndices().buffer, 0, VK_INDEX_TYPE_UINT32);
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
                if (dynamics) {
                    SceneRender.drawListedModels(
                            commandBuffer, state, frameData, vertexBuffers, vertexOffsets);
                }
            }

            vkCmdEndRendering(commandBuffer);
        }
    }

    /**
     * Record a barrier for all the shadow maps.
     *
     * @param commandBuffer The command buffer to record into.
     * @param shadowMaps The shadow maps.
     * @param srcStage The source VkPipelineStageFlags2.
     * @param srcAccess The source VkAccessFlags2.
     * @param dstStage The destination VkPipelineStageFlags2.
     * @param dstAccess The destination VkAccessFlags2.
     * @param oldLayout The layout to transition from.
     * @param newLayout The layout to transition to.
     * @param stack The stack to allocate on.
     */
    private static void transitionShadowMaps(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull TextureInfoVulkan[] shadowMaps,
            long srcStage,
            long srcAccess,
            long dstStage,
            long dstAccess,
            int oldLayout,
            int newLayout,
            @NonNull MemoryStack stack) {
        VkImageMemoryBarrier2.Buffer barriers =
                VkImageMemoryBarrier2.calloc(shadowMaps.length, stack);
        for (int i = 0; i < shadowMaps.length; i++) {
            SceneRender.imageBarrier(
                    barriers.get(i),
                    shadowMaps[i].texture,
                    VK_IMAGE_ASPECT_DEPTH_BIT,
                    srcStage,
                    srcAccess,
                    dstStage,
                    dstAccess,
                    oldLayout,
                    newLayout);
        }
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers));
    }

    private void createPipelineLayout(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                    .offset(0)
                    .size(ShaderBindings.Shadow.PUSH_CONSTANTS_SIZE);

            VkDescriptorSetLayoutBinding.Buffer bindings =
                    VkDescriptorSetLayoutBinding.calloc(1, stack);
            bindings.get(0)
                    .binding(ShaderBindings.Shadow.MODEL_MATRICES_BINDING)
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
                            .pSetLayouts(stack.longs(descriptorSetLayout))
                            .pPushConstantRanges(pushConstantRanges);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical, pipelineLayoutCreateInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes
                    .get(0)
                    .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(GraphicsManager.MAX_FRAMES_IN_FLIGHT);
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
                    vkAllocateDescriptorSets(
                            state.device.logical, descriptorSetAlloc, descriptorSets));
        }
    }

    /**
     * Create a shadow pipeline.
     *
     * @param state The Vulkan state.
     * @param shader The shader.
     * @param baked Whether it reads the baked vertex format.
     * @param lod Whether it reads the level of detail vertex format.
     * @return The VkPipeline.
     */
    private long createPipeline(
            @NonNull VulkanState state, @NonNull ShaderVulkan shader, boolean baked, boolean lod) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            // Only the position is needed, the rest of the vertex is skipped over
            VkVertexInputAttributeDescription.Buffer vertexAttributes =
                    VkVertexInputAttributeDescription.calloc(1, stack);
            vertexAttributes
                    .get(0)
                    .binding(0)
                    .location(0)
                    .format(
                            lod
                                    ? VK_FORMAT_R32G32B32_SINT
                                    : (baked
                                            ? VK_FORMAT_R16G16B16A16_SINT
                                            : VK_FORMAT_R32G32B32_SFLOAT))
                    .offset(
                            lod
                                    ? LodVertex.POSITION_OFFSET
                                    : (baked ? BakedVertex.POSITION_OFFSET : 0));

            VkVertexInputBindingDescription.Buffer vertexBindings =
                    VkVertexInputBindingDescription.calloc(1, stack);
            vertexBindings
                    .get(0)
                    .binding(0)
                    .stride(
                            lod
                                    ? LodVertex.SIZE
                                    : (baked ? BakedVertex.SIZE : MeshData.VERTEX_SIZE_IN_BYTES))
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
                            .depthAttachmentFormat(PipelineManagerVulkan.DEPTH_FORMAT);

            VkPipelineColorBlendStateCreateInfo colorBlendState =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack).sType$Default();

            // Back face culling like OpenGL. The viewport isn't flipped here, which flips the
            // winding, so counter-clockwise faces in OpenGL are clockwise here. Depth is pushed
            // away from the light, more on slopes, so lit surfaces don't shadow themselves.
            VkPipelineRasterizationStateCreateInfo rasterizationState =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .polygonMode(VK_POLYGON_MODE_FILL)
                            .cullMode(VK_CULL_MODE_BACK_BIT)
                            .frontFace(VK_FRONT_FACE_CLOCKWISE)
                            .depthBiasEnable(true)
                            .depthBiasConstantFactor(DEPTH_BIAS_CONSTANT)
                            .depthBiasSlopeFactor(DEPTH_BIAS_SLOPE)
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
                    .pStages(shader.shaderStages)
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
