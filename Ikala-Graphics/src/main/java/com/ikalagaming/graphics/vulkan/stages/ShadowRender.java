package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.MeshKind;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.Sections;
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
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3d;
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
 *
 * <p>Sections whose cut out and translucent meshes are off the GPU, far away, cast their shadows
 * from their volume bricks instead, walked along the map's direction, with what doesn't move.
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

    /** Draws see-through meshes into the cascades' translucent maps, for shadows through glass. */
    private final TranslucentShadowPass translucent;

    /**
     * Whether each cascade's translucent maps hold what doesn't move as its map does, so a cached
     * cascade that isn't drawn again keeps them too.
     */
    private final boolean[] translucentKept =
            new boolean[CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT];

    /** The shader for baked sections, in the baked vertex format. */
    @NonNull private final ShaderVulkan bakedShader;

    /** VkPipeline for baked sections, will be VK_NULL_HANDLE if not set up. */
    private long bakedPipeline;

    /** The shader for level of detail clusters, in their vertex format. */
    @NonNull private final ShaderVulkan lodShader;

    /** VkPipeline for level of detail clusters, will be VK_NULL_HANDLE if not set up. */
    private long lodPipeline;

    /** The shader for level of detail clusters with cut out materials, alpha tested. */
    @NonNull private final ShaderVulkan lodCutoutShader;

    /** The pipeline for level of detail clusters with cut out materials. */
    private long lodCutoutPipeline;

    /** The shader for coverage volumes' bricks. */
    @NonNull private final ShaderVulkan volumeShader;

    /** The pipeline for coverage volumes' bricks. */
    private long volumePipeline;

    /** The shader for sections' volume bricks, standing in for their meshes off the GPU. */
    @NonNull private final ShaderVulkan brickShader;

    /** The pipeline for sections' volume bricks. */
    private long brickPipeline;

    /** Vertices of a volume brick's box: 12 triangles. */
    private static final int BRICK_VERTICES = 36;

    /** The descriptor set the volume brick atlas is bound as. */
    private static final int BRICK_SET = 2;

    /**
     * The shader for standard meshes in the cascades, which alpha tests their textures as the scene
     * does, so cut out meshes cast the shadows of what is left.
     */
    @NonNull private final ShaderVulkan cutoutShader;

    /** VkPipeline for standard meshes in the cascades, will be VK_NULL_HANDLE if not set up. */
    private long cutoutPipeline;

    /** The shader for baked sections' cut out meshes in the cascades, which alpha tests them. */
    @NonNull private final ShaderVulkan bakedCutoutShader;

    /**
     * VkPipeline for baked sections' cut out meshes in the cascades, will be VK_NULL_HANDLE if not
     * set up.
     */
    private long bakedCutoutPipeline;

    /** The shader for standard meshes in a point light shadow face. */
    @NonNull private final ShaderVulkan pointShader;

    /** VkPipeline for standard meshes in a point light shadow face. */
    private long pointPipeline;

    /** The shader for baked sections in a point light shadow face. */
    @NonNull private final ShaderVulkan pointBakedShader;

    /** VkPipeline for baked sections in a point light shadow face. */
    private long pointBakedPipeline;

    /** The shader that shifts a cached cascade's map along. */
    @NonNull private final ShaderVulkan scrollShader;

    /** VkPipeline that shifts a cached cascade's map along. */
    private long scrollPipeline;

    /** Vertices of the triangle over a map that shifts it along. */
    private static final int SCROLL_VERTICES = 3;

    /**
     * Where a standard vertex's texture coordinates are: after its position, normal, tangent and
     * bitangent, see MeshData.
     */
    private static final int STANDARD_UV_OFFSET = 4 * 3 * Float.BYTES;

    /** Where in the push constants a point shadow face's placement goes, after the matrix. */
    private static final int POINT_PLACE_OFFSET = 16 * Float.BYTES;

    /** VkDescriptorSetLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorSetLayout;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

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
     * @param bakedShader The shader for baked sections.
     * @param lodShader The shader for level of detail clusters.
     * @param lodCutoutShader The shader for level of detail clusters with cut out materials.
     * @param volumeShader The shader for coverage volumes' bricks.
     * @param brickShader The shader for sections' volume bricks.
     * @param cutoutShader The shader for standard meshes, alpha tested.
     * @param bakedCutoutShader The shader for baked sections' cut out meshes, alpha tested.
     * @param pointShader The shader for standard meshes in point light shadow faces.
     * @param pointBakedShader The shader for baked sections in point light shadow faces.
     * @param scrollShader The shader that shifts a cached cascade's map along.
     * @param skinnedShader The shader for skinned instances.
     * @param translucentShader The shader for standard see-through meshes' shadows.
     * @param translucentBakedShader The shader for baked see-through meshes' shadows.
     * @param translucentBrickShader The shader for volume bricks' see-through voxels' shadows.
     */
    public ShadowRender(
            final @NonNull ShaderVulkan bakedShader,
            final @NonNull ShaderVulkan lodShader,
            final @NonNull ShaderVulkan lodCutoutShader,
            final @NonNull ShaderVulkan volumeShader,
            final @NonNull ShaderVulkan brickShader,
            final @NonNull ShaderVulkan cutoutShader,
            final @NonNull ShaderVulkan bakedCutoutShader,
            final @NonNull ShaderVulkan pointShader,
            final @NonNull ShaderVulkan pointBakedShader,
            final @NonNull ShaderVulkan scrollShader,
            final @NonNull ShaderVulkan skinnedShader,
            final @NonNull ShaderVulkan translucentShader,
            final @NonNull ShaderVulkan translucentBakedShader,
            final @NonNull ShaderVulkan translucentBrickShader) {
        this.translucent =
                new TranslucentShadowPass(
                        translucentShader, translucentBakedShader, translucentBrickShader);
        this.skinnedShader = skinnedShader;
        this.skinnedPipeline = VK_NULL_HANDLE;
        this.bakedShader = bakedShader;
        this.lodShader = lodShader;
        this.lodCutoutShader = lodCutoutShader;
        this.volumeShader = volumeShader;
        this.brickShader = brickShader;
        this.brickPipeline = VK_NULL_HANDLE;
        this.cutoutShader = cutoutShader;
        this.bakedCutoutShader = bakedCutoutShader;
        this.pointShader = pointShader;
        this.pointBakedShader = pointBakedShader;
        this.scrollShader = scrollShader;
        this.scrollPipeline = VK_NULL_HANDLE;
        this.bakedPipeline = VK_NULL_HANDLE;
        this.lodPipeline = VK_NULL_HANDLE;
        this.lodCutoutPipeline = VK_NULL_HANDLE;
        this.volumePipeline = VK_NULL_HANDLE;
        this.descriptorSetLayout = VK_NULL_HANDLE;
        this.pipelineLayout = VK_NULL_HANDLE;
        this.descriptorPool = VK_NULL_HANDLE;
        this.descriptorSets = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];
        this.writtenBuffers = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT][2];
    }

    @Override
    public void initialize(@NonNull VulkanState vulkanState) {
        log.debug("Initializing shadow render");
        createPipelineLayout(vulkanState);
        bakedPipeline = createPipeline(vulkanState, bakedShader, true, false, false);
        lodPipeline = createPipeline(vulkanState, lodShader, false, true, false);
        lodCutoutPipeline = createPipeline(vulkanState, lodCutoutShader, false, true, true);
        volumePipeline = createVolumePipeline(vulkanState);
        brickPipeline =
                createPipeline(vulkanState, brickShader, false, false, false, false, true, false);
        cutoutPipeline = createPipeline(vulkanState, cutoutShader, false, false, true);
        bakedCutoutPipeline = createPipeline(vulkanState, bakedCutoutShader, true, false, true);
        pointPipeline = createPipeline(vulkanState, pointShader, false, false, false);
        pointBakedPipeline = createPipeline(vulkanState, pointBakedShader, true, false, false);
        scrollPipeline = createScrollPipeline(vulkanState);
        skinnedPipeline = createSkinnedPipeline(vulkanState);
        translucent.initialize(vulkanState, pipelineLayout);
    }

    @Override
    public void cleanup(@NonNull VulkanState vulkanState) {
        // Freed along with the pool
        Arrays.fill(descriptorSets, VK_NULL_HANDLE);
        vkDestroyDescriptorPool(vulkanState.device.logical, descriptorPool, null);
        descriptorPool = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, bakedPipeline, null);
        bakedPipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, lodPipeline, null);
        lodPipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, lodCutoutPipeline, null);
        lodCutoutPipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, volumePipeline, null);
        volumePipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, brickPipeline, null);
        brickPipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, cutoutPipeline, null);
        cutoutPipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, bakedCutoutPipeline, null);
        bakedCutoutPipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, pointPipeline, null);
        pointPipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, pointBakedPipeline, null);
        pointBakedPipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, scrollPipeline, null);
        scrollPipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, skinnedPipeline, null);
        skinnedPipeline = VK_NULL_HANDLE;
        translucent.cleanup(vulkanState);
        vkDestroyPipelineLayout(vulkanState.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(vulkanState.device.logical, descriptorSetLayout, null);
        descriptorSetLayout = VK_NULL_HANDLE;
    }

    @Override
    public void render(
            Scene scene,
            @NonNull SceneTarget sceneTarget,
            @NonNull VulkanState vulkanState,
            RenderConfig renderConfig) {
        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];
        final PerFrameData frameData = vulkanState.perFrameData[vulkanState.frameIndex];

        // Worked out by the instance draw update stage, since culling needs them first
        CascadeShadowSplit[] cascadeShadowSplits = frameData.cascadeShadowSplits;

        // The scene stage writes them too, but after this one, and cut out meshes need them now
        SceneRender.updateMaterials(scene, vulkanState, frameData);
        SceneRender.writeStorageBindings(
                vulkanState,
                descriptorSets[vulkanState.frameIndex],
                new SharedBuffer[] {frameData.sceneModelMatrices, frameData.materials},
                new int[] {
                    ShaderBindings.Shadow.MODEL_MATRICES_BINDING,
                    ShaderBindings.Shadow.MATERIALS_BINDING
                },
                writtenBuffers[vulkanState.frameIndex]);

        // The volume bricks standing in for meshes off the GPU, cast with what doesn't move
        if (vulkanState.volumeBricks != null) {
            vulkanState.volumeBricks.writeShadowDraws(
                    vulkanState.frameIndex,
                    vulkanState,
                    vulkanState.sections == null
                                    || scene == null
                                    || !GraphicsManager.getSettings().volumeShadows
                            ? VolumeBricks.Members.NONE
                            : vulkanState.sections.getStandInSections(),
                    scene == null ? new Vector3d() : scene.getCamera().getPosition());
        }

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
                // See-through meshes, drawn whole when the cascade isn't cached, when it is drawn
                // again, or when anything moves, as what moves isn't kept apart for them
                if (!frameData.translucentShadowCasters) {
                    translucentKept[i] = false;
                } else if (!cascade.isCached()
                        || cascade.getRedraw() != SunShadowCache.Redraw.NONE
                        || frameData.dynamicShadowCasters
                        || !translucentKept[i]) {
                    translucent.render(
                            commandBuffer,
                            vulkanState,
                            i,
                            pipelineLayout,
                            descriptorSets[vulkanState.frameIndex],
                            pushConstants,
                            stack);
                    translucentKept[i] = true;
                }
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
                            true,
                            true);
                    endWriting(commandBuffer, map, stack);
                    continue;
                }
                if (cascade.getRedraw() == SunShadowCache.Redraw.SCROLL) {
                    // Moved across the light: what it holds shifts along, then the strip it
                    // uncovered is drawn below
                    scrollMap(commandBuffer, vulkanState, map, cascade.getScroll(), stack);
                }
                if (cascade.getRedraw() != SunShadowCache.Redraw.NONE) {
                    // What doesn't move, kept from frame to frame, so it is drawn over
                    beginWriting(commandBuffer, map, VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL, stack);
                    renderMap(
                            commandBuffer,
                            vulkanState,
                            map,
                            cascade.getMapSize(),
                            cascade.getRedraw() == SunShadowCache.Redraw.ALL
                                    ? null
                                    : cascade.getRedrawRect(),
                            list,
                            pushConstants,
                            true,
                            false,
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
                            true,
                            true);
                    endWriting(commandBuffer, dynamicMap, stack);
                }
            }

            // The sky occlusion map, what doesn't move from straight above, when it is due:
            // instances that stay put too, which culling leaves out while they move
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
     * Shift a cached cascade's map along by whole texels: copy it, then draw each texel back from
     * where its contents were in the copy, the far plane where that is outside the map. Leaves the
     * map ready to be read, or drawn into again.
     *
     * @param commandBuffer The command buffer to record into.
     * @param state The Vulkan state.
     * @param map The map, ready to be read.
     * @param shift The shift in texels, x then y: a texel's contents come from where it is less
     *     this.
     * @param stack The stack to allocate on.
     */
    private void scrollMap(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            @NonNull TextureInfoVulkan map,
            int @NonNull [] shift,
            @NonNull MemoryStack stack) {
        final TextureInfoVulkan copy = state.perFrameData[state.frameIndex].cascadeScrollCopy;
        final int size = SunShadowCache.STATIC_MAP_SIZE;
        // Once earlier frames are done reading the map, and the last shift is done with the copy
        transitionShadowMaps(
                commandBuffer,
                new TextureInfoVulkan[] {map},
                VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                VK_ACCESS_2_NONE,
                VK_PIPELINE_STAGE_2_COPY_BIT,
                VK_ACCESS_2_TRANSFER_READ_BIT,
                VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL,
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                stack);
        transitionShadowMaps(
                commandBuffer,
                new TextureInfoVulkan[] {copy},
                VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                VK_ACCESS_2_NONE,
                VK_PIPELINE_STAGE_2_COPY_BIT,
                VK_ACCESS_2_TRANSFER_WRITE_BIT,
                VK_IMAGE_LAYOUT_UNDEFINED,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                stack);
        VkImageCopy.Buffer region = VkImageCopy.calloc(1, stack);
        region.get(0)
                .srcSubresource(
                        layers -> layers.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).layerCount(1));
        region.get(0)
                .dstSubresource(
                        layers -> layers.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).layerCount(1));
        region.get(0).extent().set(size, size, 1);
        vkCmdCopyImage(
                commandBuffer,
                map.texture,
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                copy.texture,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                region);
        transitionShadowMaps(
                commandBuffer,
                new TextureInfoVulkan[] {copy},
                VK_PIPELINE_STAGE_2_COPY_BIT,
                VK_ACCESS_2_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL,
                stack);
        transitionShadowMaps(
                commandBuffer,
                new TextureInfoVulkan[] {map},
                VK_PIPELINE_STAGE_2_COPY_BIT,
                VK_ACCESS_2_NONE,
                VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
                stack);

        // Every texel is written, so what the map held is no longer needed
        VkRenderingAttachmentInfo depthAttachment =
                VkRenderingAttachmentInfo.calloc(stack)
                        .sType$Default()
                        .imageView(map.view)
                        .imageLayout(VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL)
                        .loadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
        VkRenderingInfo renderingInfo =
                VkRenderingInfo.calloc(stack)
                        .sType$Default()
                        .renderArea(area -> area.extent().set(size, size))
                        .layerCount(1)
                        .pDepthAttachment(depthAttachment);
        vkCmdBeginRendering(commandBuffer, renderingInfo);
        VkViewport.Buffer viewports = VkViewport.calloc(1, stack);
        viewports.get(0).width(size).height(size).minDepth(0).maxDepth(1);
        vkCmdSetViewport(commandBuffer, 0, viewports);
        VkRect2D.Buffer scissors = VkRect2D.calloc(1, stack);
        scissors.get(0).extent().set(size, size);
        vkCmdSetScissor(commandBuffer, 0, scissors);
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, scrollPipeline);
        vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipelineLayout,
                0,
                stack.longs(
                        descriptorSets[state.frameIndex],
                        state.bindlessTextures.getDescriptorSet()),
                null);
        final ByteBuffer constants = stack.malloc(ShaderBindings.Shadow.VOLUME_PUSH_CONSTANTS_SIZE);
        constants.putInt(0, copy.bindlessIndex);
        constants.putInt(
                ShaderBindings.Shadow.PUSH_CONSTANT_SCROLL_SHIFT_OFFSET
                        - ShaderBindings.Shadow.PUSH_CONSTANT_SCROLL_SOURCE_OFFSET,
                shift[0]);
        constants.putInt(
                ShaderBindings.Shadow.PUSH_CONSTANT_SCROLL_SHIFT_OFFSET
                        - ShaderBindings.Shadow.PUSH_CONSTANT_SCROLL_SOURCE_OFFSET
                        + Integer.BYTES,
                shift[1]);
        vkCmdPushConstants(
                commandBuffer,
                pipelineLayout,
                VK_SHADER_STAGE_FRAGMENT_BIT,
                ShaderBindings.Shadow.PUSH_CONSTANT_SCROLL_SOURCE_OFFSET,
                constants);
        vkCmdDraw(commandBuffer, SCROLL_VERTICES, 1, 0, 0);
        vkCmdEndRendering(commandBuffer);
        endWriting(commandBuffer, map, stack);
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
     * @param standards Whether to draw standard meshes, which culling put in the list.
     * @param listed Whether to draw the listed models, animated ones, which always move.
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
            boolean standards,
            boolean listed) {
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

            final int bricks =
                    statics && state.volumeBricks != null
                            ? state.volumeBricks.shadowCount(state.frameIndex)
                            : 0;
            final boolean skinned = listed && state.skins.hasShadowDraws(state.frameIndex);
            if (frameData.meshSlotCount + frameData.bakedSlotCount > 0
                    || !frameData.modelDrawInfo.isEmpty()
                    || LodRenderer.hasDraws(frameData)
                    || bricks > 0
                    || skinned) {
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
                        stack.longs(
                                descriptorSets[state.frameIndex],
                                state.bindlessTextures.getDescriptorSet()),
                        null);

                vkCmdPushConstants(
                        commandBuffer,
                        pipelineLayout,
                        VK_SHADER_STAGE_VERTEX_BIT,
                        0,
                        pushConstants);
                LongBuffer vertexBuffers = stack.callocLong(1);
                LongBuffer vertexOffsets = stack.callocLong(1);

                // Level of detail clusters, the cut the scene sees, in this cascade's frustum: the
                // opaque ones, then those with cut out materials, alpha tested
                if (statics && LodRenderer.hasDraws(frameData)) {
                    vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, lodPipeline);
                    state.lods.draw(
                            commandBuffer,
                            frameData,
                            pipelineLayout,
                            ShaderBindings.Shadow.PUSH_CONSTANT_LOD_WORLDS_OFFSET,
                            list);
                    vkCmdBindPipeline(
                            commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, lodCutoutPipeline);
                    state.lods.draw(
                            commandBuffer,
                            frameData,
                            pipelineLayout,
                            ShaderBindings.Shadow.PUSH_CONSTANT_LOD_WORLDS_OFFSET,
                            LodRenderer.CUT_OUT_LISTS + list);
                    // Coverage volumes, walked along the map's direction
                    vkCmdBindPipeline(
                            commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, volumePipeline);
                    final ByteBuffer volumeConstants =
                            stack.calloc(ShaderBindings.Shadow.VOLUME_PUSH_CONSTANTS_SIZE);
                    volumeConstants.putLong(0, state.lods.getBricks().getBuffer().deviceAddress);
                    volumeConstants.putInt(Long.BYTES, (int) state.frameNumber);
                    vkCmdPushConstants(
                            commandBuffer,
                            pipelineLayout,
                            VK_SHADER_STAGE_FRAGMENT_BIT,
                            ShaderBindings.Shadow.PUSH_CONSTANT_BRICKS_OFFSET,
                            volumeConstants);
                    state.lods.draw(
                            commandBuffer,
                            frameData,
                            pipelineLayout,
                            ShaderBindings.Shadow.PUSH_CONSTANT_LOD_WORLDS_OFFSET,
                            LodRenderer.VOLUME_LISTS + list);
                }

                // Volume bricks standing in for sections' meshes off the GPU, walked along the
                // map's direction; their list's address goes where level of detail's worlds were
                if (bricks > 0) {
                    vkCmdBindPipeline(
                            commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, brickPipeline);
                    vkCmdBindDescriptorSets(
                            commandBuffer,
                            VK_PIPELINE_BIND_POINT_GRAPHICS,
                            pipelineLayout,
                            BRICK_SET,
                            stack.longs(state.volumeBricks.getSet()),
                            null);
                    final ByteBuffer brickConstants = stack.calloc(Long.BYTES);
                    brickConstants.putLong(0, state.volumeBricks.shadowList(state.frameIndex));
                    vkCmdPushConstants(
                            commandBuffer,
                            pipelineLayout,
                            VK_SHADER_STAGE_VERTEX_BIT,
                            ShaderBindings.Shadow.PUSH_CONSTANT_LOD_WORLDS_OFFSET,
                            brickConstants);
                    vkCmdDraw(commandBuffer, BRICK_VERTICES, bricks, 0, 0);
                }

                // Everything culling handled for this cascade, each kind from its own buffers,
                // one command per mesh slot. Standard last, which the listed models need.
                for (int k = MeshKind.ALL.length - 1; k >= 0; --k) {
                    final MeshKind kind = MeshKind.ALL[k];
                    if (kind.isBaked() ? !statics : !standards) {
                        continue;
                    }
                    final int slots =
                            InstanceDrawUpdate.slotsOf(
                                    kind, frameData.meshSlotCount, frameData.bakedSlotCount);
                    if (slots == 0 && kind != MeshKind.STANDARD) {
                        continue;
                    }
                    vkCmdBindPipeline(
                            commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineFor(kind));
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
                if (listed) {
                    SceneRender.drawListedModels(
                            commandBuffer, state, frameData, vertexBuffers, vertexOffsets);
                }
                // Skinned instances with what moves, drawn every frame; their buffers' addresses
                // go where level of detail's tables do, after the cascade's matrix
                if (skinned) {
                    vkCmdBindPipeline(
                            commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, skinnedPipeline);
                    state.skins.drawShadows(
                            commandBuffer,
                            state.frameIndex,
                            pipelineLayout,
                            ShaderBindings.Shadow.PUSH_CONSTANT_LOD_WORLDS_OFFSET);
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

    /**
     * The cascades' pipeline for a kind of mesh: alpha tested for standard meshes, as the scene's
     * is, and for baked cut out meshes, so their holes reach their shadows; depth only otherwise.
     *
     * @param kind The kind.
     * @return The VkPipeline.
     */
    private long pipelineFor(@NonNull MeshKind kind) {
        return switch (kind) {
            case STANDARD -> cutoutPipeline;
            case BAKED_CUTOUT -> bakedCutoutPipeline;
            default -> bakedPipeline;
        };
    }

    /**
     * Create the pipeline for coverage volumes' bricks: their boxes' corners as {@link
     * SceneRender#volumeVertexAttributes} reads them, their faces toward the light drawn, each
     * fragment walking the box along the map's direction, and no depth bias, since the walk writes
     * the depth.
     *
     * @param state The Vulkan state.
     * @return The VkPipeline.
     */
    /**
     * Create the pipeline that shifts a cached cascade's map along: one triangle over the map from
     * the vertex index, writing every texel's depth, whatever was there.
     *
     * @param state The Vulkan state.
     * @return The VkPipeline.
     */
    private long createScrollPipeline(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPipelineVertexInputStateCreateInfo vertexInputState =
                    VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default();
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
            VkPipelineDepthStencilStateCreateInfo depthStencilState =
                    VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .depthTestEnable(true)
                            .depthWriteEnable(true)
                            .depthCompareOp(VK_COMPARE_OP_ALWAYS);
            VkPipelineRenderingCreateInfo renderingCreateInfo =
                    VkPipelineRenderingCreateInfo.calloc(stack)
                            .sType$Default()
                            .depthAttachmentFormat(PipelineManagerVulkan.DEPTH_FORMAT);
            VkPipelineColorBlendStateCreateInfo colorBlendState =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack).sType$Default();
            VkPipelineRasterizationStateCreateInfo rasterizationState =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .polygonMode(VK_POLYGON_MODE_FILL)
                            .cullMode(VK_CULL_MODE_NONE)
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
                    .stageCount(scrollShader.shaderModules.length)
                    .pStages(scrollShader.shaderStages)
                    .pVertexInputState(vertexInputState)
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

    private long createVolumePipeline(@NonNull VulkanState state) {
        return createPipeline(state, volumeShader, false, true, false, true, false, false);
    }

    private void createPipelineLayout(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            // The vertex shaders' constants, then what coverage volumes' fragments read
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(2, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                    .offset(0)
                    .size(ShaderBindings.Shadow.PUSH_CONSTANTS_SIZE);
            pushConstantRanges
                    .get(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(ShaderBindings.Shadow.PUSH_CONSTANT_BRICKS_OFFSET)
                    .size(ShaderBindings.Shadow.VOLUME_PUSH_CONSTANTS_SIZE);

            VkDescriptorSetLayoutBinding.Buffer bindings =
                    VkDescriptorSetLayoutBinding.calloc(2, stack);
            bindings.get(0)
                    .binding(ShaderBindings.Shadow.MODEL_MATRICES_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            bindings.get(1)
                    .binding(ShaderBindings.Shadow.MATERIALS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

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
                                            state.bindlessTextures.getDescriptorSetLayout(),
                                            state.volumeBricks.getSetLayout()))
                            .pPushConstantRanges(pushConstantRanges);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical, pipelineLayoutCreateInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes
                    .get(0)
                    .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(2 * GraphicsManager.MAX_FRAMES_IN_FLIGHT);
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

    /** The shader for skinned meshes. */
    @NonNull private final ShaderVulkan skinnedShader;

    /** VkPipeline for skinned meshes, VK_NULL_HANDLE if not set up. */
    private long skinnedPipeline;

    /**
     * Create the skinned mesh pipeline: the standard one, reading SkinnedVertex's stride and its
     * skinning attributes too.
     *
     * @param state The Vulkan state.
     * @return The VkPipeline.
     */
    private long createSkinnedPipeline(@NonNull VulkanState state) {
        return createPipeline(state, skinnedShader, false, false, false, false, false, true);
    }

    /**
     * Create a shadow pipeline.
     *
     * @param state The Vulkan state.
     * @param shader The shader.
     * @param baked Whether it reads the baked vertex format.
     * @param lod Whether it reads the level of detail vertex format.
     * @param cutout Whether it alpha tests, reading texture coordinates and, for baked meshes, the
     *     material too.
     * @return The VkPipeline.
     */
    private long createPipeline(
            @NonNull VulkanState state,
            @NonNull ShaderVulkan shader,
            boolean baked,
            boolean lod,
            boolean cutout) {
        return createPipeline(state, shader, baked, lod, cutout, false, false, false);
    }

    /**
     * Create one of the shadow pipelines.
     *
     * @param state The Vulkan state.
     * @param shader The shader.
     * @param baked Whether it reads baked vertices.
     * @param lod Whether it reads level of detail vertices.
     * @param cutout Whether it alpha tests.
     * @param volume Whether it walks coverage volumes' bricks.
     * @param brick Whether it walks sections' volume bricks, whose boxes come from the vertex index
     *     with no vertex buffer, their faces toward the light drawn, and no depth bias, since the
     *     walk writes the depth.
     * @param skinned Whether it reads skinned vertices: the position, then the bones, weights and
     *     region the shader skins with.
     * @return The VkPipeline.
     */
    private long createPipeline(
            @NonNull VulkanState state,
            @NonNull ShaderVulkan shader,
            boolean baked,
            boolean lod,
            boolean cutout,
            boolean volume,
            boolean brick,
            boolean skinned) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            // Only the position is needed, the rest of the vertex is skipped over, except the
            // texture coordinates and material for alpha testing
            // Level of detail's cut outs also take the texture coordinates' bits whole, where a
            // coverage volume shell's corner keeps its straight in coverage, and the normal's,
            // where it keeps its grazing coverage, see Attributes.shellNormal
            final int attributeCount = cutout ? (lod ? 5 : baked ? 3 : 2) : skinned ? 4 : 1;
            VkVertexInputAttributeDescription.Buffer vertexAttributes =
                    volume
                            ? SceneRender.volumeVertexAttributes(stack)
                            : VkVertexInputAttributeDescription.calloc(attributeCount, stack);
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
            if (cutout) {
                vertexAttributes
                        .get(1)
                        .binding(0)
                        .location(1)
                        .format(baked || lod ? VK_FORMAT_R16G16_SFLOAT : VK_FORMAT_R32G32_SFLOAT)
                        .offset(
                                lod
                                        ? LodVertex.UV_OFFSET
                                        : (baked ? BakedVertex.UV_OFFSET : STANDARD_UV_OFFSET));
                if (baked || lod) {
                    vertexAttributes
                            .get(2)
                            .binding(0)
                            .location(2)
                            .format(VK_FORMAT_R16G16_UINT)
                            .offset(lod ? LodVertex.MATERIAL_OFFSET : BakedVertex.MATERIAL_OFFSET);
                }
                if (lod) {
                    vertexAttributes
                            .get(3)
                            .binding(0)
                            .location(3)
                            .format(VK_FORMAT_R32_UINT)
                            .offset(LodVertex.UV_OFFSET);
                    vertexAttributes
                            .get(4)
                            .binding(0)
                            .location(4)
                            .format(VK_FORMAT_R32_UINT)
                            .offset(LodVertex.NORMAL_OFFSET);
                }
            }

            if (skinned) {
                // See skinning.glsl
                final int[] formats = {
                    VK_FORMAT_R16G16B16A16_UINT, VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_R32_UINT
                };
                final int[] offsets = {
                    SkinnedVertex.JOINTS_OFFSET,
                    SkinnedVertex.WEIGHTS_OFFSET,
                    SkinnedVertex.REGION_OFFSET
                };
                for (int i = 0; i < formats.length; ++i) {
                    vertexAttributes
                            .get(1 + i)
                            .binding(0)
                            .location(SceneRender.SKINNED_FIRST_LOCATION + i)
                            .format(formats[i])
                            .offset(offsets[i]);
                }
            }

            VkVertexInputBindingDescription.Buffer vertexBindings =
                    VkVertexInputBindingDescription.calloc(1, stack);
            vertexBindings
                    .get(0)
                    .binding(0)
                    .stride(
                            skinned
                                    ? SkinnedVertex.SIZE
                                    : lod
                                            ? LodVertex.SIZE
                                            : (baked
                                                    ? BakedVertex.SIZE
                                                    : MeshData.VERTEX_SIZE_IN_BYTES))
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            VkPipelineVertexInputStateCreateInfo vertexInputStateCreateInfo =
                    VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default();
            if (!brick) {
                vertexInputStateCreateInfo
                        .pVertexBindingDescriptions(vertexBindings)
                        .pVertexAttributeDescriptions(vertexAttributes);
            }

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
                            // Volumes' boxes too, by their faces toward the light, so the
                            // depth test drops those behind what is drawn before their walks run
                            .cullMode(VK_CULL_MODE_BACK_BIT)
                            .frontFace(VK_FRONT_FACE_CLOCKWISE)
                            .depthBiasEnable(!volume && !brick)
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
