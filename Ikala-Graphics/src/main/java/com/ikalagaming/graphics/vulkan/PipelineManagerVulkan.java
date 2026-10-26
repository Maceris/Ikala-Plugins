package com.ikalagaming.graphics.vulkan;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.*;
import com.ikalagaming.graphics.GlfwPlatform;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.graph.CascadeShadowSplit;
import com.ikalagaming.graphics.graph.SkyOcclusion;
import com.ikalagaming.graphics.graph.SunShadowCache;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.FontAtlas;
import com.ikalagaming.graphics.vulkan.stages.*;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.*;

@Slf4j
public class PipelineManagerVulkan {

    /** The size of a 4x4 model matrix ({@value}). */
    public static final int MODEL_MATRIX_SIZE = 4 * 4;

    /**
     * Format of the base color, normal, and tangent textures in the g-buffer. Matches the OpenGL
     * backend.
     */
    public static final int GBUFFER_COLOR_FORMAT = VK_FORMAT_R32G32B32A32_SFLOAT;

    /** Format of the material index texture in the g-buffer. */
    public static final int GBUFFER_MATERIAL_FORMAT = VK_FORMAT_R32_UINT;

    /** Format of the emission and occlusion texture in the g-buffer. Emission can pass 1. */
    public static final int GBUFFER_EMISSION_FORMAT = VK_FORMAT_R16G16B16A16_SFLOAT;

    /** Format of the g-buffer depth and the shadow maps. Supported everywhere for both uses. */
    public static final int DEPTH_FORMAT = VK_FORMAT_D32_SFLOAT;

    /**
     * Format of the images we render the lit scene, filters, and GUI into. Not sRGB, since the
     * OpenGL backend doesn't convert to sRGB either and we want the same output.
     */
    public static final int SCREEN_FORMAT = VK_FORMAT_R8G8B8A8_UNORM;

    /**
     * The format of the scene color: linear light, which can go past 1 before tone mapping, at half
     * float precision.
     */
    public static final int SCENE_COLOR_FORMAT = VK_FORMAT_R16G16B16A16_SFLOAT;

    /**
     * The format the transparent layers' weighted colors add up in. Full floats, since lit colors
     * past 1 times weights up to a few thousand would overflow half floats in a few layers.
     */
    public static final int OIT_ACCUM_FORMAT = VK_FORMAT_R32G32B32A32_SFLOAT;

    /**
     * The format the transparent layers' extinction adds up in: red all of it, green only the
     * translucent surfaces', which have no history of their own for TAA to follow, unlike volume
     * bricks, which stand in for blocks that don't move.
     */
    public static final int OIT_EXTINCTION_FORMAT = VK_FORMAT_R16G16_SFLOAT;

    /** The texture we store the font atlas on. */
    private TextureHandle fontAtlas;

    /** A mesh for rendering onto. */
    private QuadMesh quadMesh;

    /** The map from config value to the associated renderer. */
    private final Map<RenderConfig, PipelineVulkan> renderers;

    /** Model used for rendering the skybox. */
    private SkyboxModel skybox;

    /**
     * The cascade shadow maps. These are shared between frames like in OpenGL, barriers keep a
     * frame from writing them while an earlier one reads them.
     */
    private TextureInfoVulkan[] shadowMaps;

    /**
     * The cascades' maps of what moves, for the cached cascades, null for the others. Shared
     * between frames like the shadow maps.
     */
    private TextureInfoVulkan[] dynamicShadowMaps;

    /** The copy cached cascades' maps are shifted along from. */
    private TextureInfoVulkan scrollCopy;

    /** The sky occlusion map, shared between frames like the shadow maps. */
    private TextureInfoVulkan skyMap;

    /** The point light shadow atlases, of what doesn't move and of what moves. */
    private TextureInfoVulkan pointAtlas;

    /** The point light shadow atlas of what moves. */
    private TextureInfoVulkan pointDynamicAtlas;

    private final AnimationRender stageAnimationRender;
    private final FilterRender stageFilterRender;

    private final DebugRender stageDebugRender;
    private final GuiRender stageGuiRender;
    private final LightRender stageLightRender;

    /** Sorts the lights into clusters for the light stage. */
    private final LightCull stageLightCull;

    /** Works out the light the sky gives when it changes, for the light stage. */
    private final EnvironmentUpdate stageEnvironmentUpdate;

    /** Turns the scene color into the image shown. */
    private final ToneMap stageToneMap;

    /** Blends each frame into the history of the ones before. */
    private final Taa stageTaa;

    /** Draws translucent geometry into the transparency targets. */
    private final TranslucentRender stageTranslucentRender;

    /** Composites the transparency targets over the scene color. */
    private final OitResolve stageOitResolve;

    /** Adds translucent surfaces' extinction into the transparency volume. */
    private final OitSplat stageOitSplat;

    /** Moves and spawns the particles, which the transparency passes draw. */
    private final ParticleUpdate stageParticleUpdate;

    /** Integrates the transparency volume into the weights the transparent stage reads. */
    private final OitIntegrate stageOitIntegrate;

    /** Keeps the volume bricks up to date and lists the ones to draw. */
    private final VolumeUpload stageVolumeUpload;

    /** Bakes level of detail clusters' appearance into their tiles. */
    private final LodBake stageLodBake;

    /** Writes the depth of the volume bricks' solid faces. */
    private final VolumeDepth stageVolumeDepth;

    /** Adds the volume bricks' extinction into the transparency volume. */
    private final VolumeSplat stageVolumeSplat;

    /** Ray marches the volume bricks into the transparency targets. */
    private final VolumeColor stageVolumeColor;

    private final InstanceDrawUpdate stageInstanceDrawUpdate;
    private final InstanceTransform stageInstanceTransform;

    /** Keeps the occupancy volume up to date. */
    private final OccupancyVoxelize stageOccupancyVoxelize;

    private final InstanceCull stageInstanceCull;
    private final InstanceCull stageInstanceCullLate;

    /** Picks and culls the level of detail cut before anything draws. */
    private final LodCull stageLodCull;

    /** Culls the level of detail cut against the depth pyramid. */
    private final LodCull stageLodCullLate;

    private final DepthPyramidBuild stageDepthPyramidBuild;
    private final SceneRender stageSceneRender;
    private final ShadowRender stageShadowRender;
    private final SkyboxRender stageSkyboxRender;
    private final SwapchainPresent stageSwapchainPresent;

    private final LongBuffer longOutput = MemoryUtil.memAllocLong(1);
    private final PointerBuffer pointerOutput = MemoryUtil.memAllocPointer(1);

    public PipelineManagerVulkan(
            int width, int height, @NonNull ShaderMap shaders, @NonNull VulkanState state) {

        log.trace("Initializing pipeline manager");

        renderers = new HashMap<>();
        createShadowMaps(state);
        log.trace("Created shadow maps");
        createShaderData(width, height, state);
        log.trace("Created shader data");
        createGuiFont();
        log.trace("Created GUI font");
        skybox = new SkyboxModel(state);
        quadMesh = QuadMesh.getInstance(state);

        stageInstanceDrawUpdate = new InstanceDrawUpdate();
        stageInstanceDrawUpdate.initialize(state);
        stageOccupancyVoxelize =
                new OccupancyVoxelize(
                        shaders.getShader(RenderStage.Type.OCCUPANCY_CLEAR),
                        shaders.getShader(RenderStage.Type.OCCUPANCY_VOXELIZE),
                        shaders.getShader(RenderStage.Type.OCCUPANCY_BRICKS));
        stageOccupancyVoxelize.initialize(state);
        stageInstanceTransform =
                new InstanceTransform(shaders.getShader(RenderStage.Type.INSTANCES));
        stageInstanceTransform.initialize(state);
        stageInstanceCull =
                new InstanceCull(
                        shaders.getShader(RenderStage.Type.CULL), ShaderBindings.Cull.PHASE_EARLY);
        stageInstanceCull.initialize(state);
        stageInstanceCullLate =
                new InstanceCull(
                        shaders.getShader(RenderStage.Type.CULL), ShaderBindings.Cull.PHASE_LATE);
        stageInstanceCullLate.initialize(state);
        stageLodCull =
                new LodCull(
                        shaders.getShader(RenderStage.Type.LOD_CULL),
                        ShaderBindings.Cull.PHASE_EARLY);
        stageLodCull.initialize(state);
        stageLodCullLate =
                new LodCull(
                        shaders.getShader(RenderStage.Type.LOD_CULL),
                        ShaderBindings.Cull.PHASE_LATE);
        stageLodCullLate.initialize(state);
        stageDepthPyramidBuild =
                new DepthPyramidBuild(shaders.getShader(RenderStage.Type.DEPTH_PYRAMID));
        stageDepthPyramidBuild.initialize(state);
        stageSceneRender =
                new SceneRender(
                        shaders.getShader(RenderStage.Type.SCENE),
                        shaders.getShader(RenderStage.Type.SCENE_BAKED),
                        shaders.getShader(RenderStage.Type.SCENE_LOD),
                        shaders.getShader(RenderStage.Type.SCENE_VOLUME),
                        shaders.getShader(RenderStage.Type.SCENE_SKINNED));
        stageSceneRender.initialize(state);
        stageGuiRender = new GuiRender(shaders.getShader(RenderStage.Type.GUI), fontAtlas);
        stageGuiRender.initialize(state);
        stageSkyboxRender = new SkyboxRender(shaders.getShader(RenderStage.Type.SKYBOX), skybox);
        stageSkyboxRender.initialize(state);
        stageShadowRender =
                new ShadowRender(
                        shaders.getShader(RenderStage.Type.SHADOW_BAKED),
                        shaders.getShader(RenderStage.Type.SHADOW_LOD),
                        shaders.getShader(RenderStage.Type.SHADOW_LOD_CUTOUT),
                        shaders.getShader(RenderStage.Type.SHADOW_VOLUME),
                        shaders.getShader(RenderStage.Type.SHADOW_BRICK),
                        shaders.getShader(RenderStage.Type.SHADOW_CUTOUT),
                        shaders.getShader(RenderStage.Type.SHADOW_BAKED_CUTOUT),
                        shaders.getShader(RenderStage.Type.POINT_SHADOW),
                        shaders.getShader(RenderStage.Type.POINT_SHADOW_BAKED),
                        shaders.getShader(RenderStage.Type.SHADOW_SCROLL),
                        shaders.getShader(RenderStage.Type.SHADOW_SKINNED));
        stageShadowRender.initialize(state);
        stageLightCull = new LightCull(shaders.getShader(RenderStage.Type.LIGHT_CULL));
        stageLightCull.initialize(state);
        stageEnvironmentUpdate =
                new EnvironmentUpdate(
                        shaders.getShader(RenderStage.Type.ENVIRONMENT),
                        shaders.getShader(RenderStage.Type.ATMOSPHERE));
        stageEnvironmentUpdate.initialize(state);
        state.tableScatter =
                new TableScatter(state, shaders.getShader(RenderStage.Type.TABLE_SCATTER));
        stageLightRender =
                new LightRender(
                        shaders.getShader(RenderStage.Type.LIGHT),
                        quadMesh,
                        new FogRender(
                                shaders.getShader(RenderStage.Type.FOG_INJECT),
                                shaders.getShader(RenderStage.Type.FOG_INTEGRATE)));
        stageLightRender.initialize(state);
        stageToneMap =
                new ToneMap(
                        shaders.getShader(RenderStage.Type.TONEMAP),
                        shaders.getShader(RenderStage.Type.AUTO_EXPOSURE),
                        quadMesh);
        stageToneMap.initialize(state);
        stageTaa = new Taa(shaders.getShader(RenderStage.Type.TAA), quadMesh);
        stageTaa.initialize(state);
        stageTranslucentRender =
                new TranslucentRender(
                        shaders.getShader(RenderStage.Type.TRANSLUCENT),
                        shaders.getShader(RenderStage.Type.TRANSLUCENT_BAKED),
                        stageSceneRender,
                        stageLightRender,
                        new ParticleDraw(shaders.getShader(RenderStage.Type.PARTICLE), true));
        stageTranslucentRender.initialize(state);
        stageOitSplat =
                new OitSplat(
                        shaders.getShader(RenderStage.Type.OIT_SPLAT),
                        shaders.getShader(RenderStage.Type.OIT_SPLAT_BAKED),
                        stageSceneRender,
                        stageLightRender,
                        new ParticleDraw(
                                shaders.getShader(RenderStage.Type.PARTICLE_SPLAT), false));
        stageOitSplat.initialize(state);
        stageParticleUpdate =
                new ParticleUpdate(
                        shaders.getShader(RenderStage.Type.PARTICLE_CONTROL),
                        shaders.getShader(RenderStage.Type.PARTICLE_SIMULATE),
                        shaders.getShader(RenderStage.Type.PARTICLE_EMIT));
        stageParticleUpdate.initialize(state);
        stageOitIntegrate = new OitIntegrate(shaders.getShader(RenderStage.Type.OIT_INTEGRATE));
        stageOitIntegrate.initialize(state);
        stageVolumeUpload = new VolumeUpload(shaders.getShader(RenderStage.Type.VOLUME_UPLOAD));
        stageVolumeUpload.initialize(state);
        stageLodBake = new LodBake(shaders.getShader(RenderStage.Type.LOD_BAKE));
        stageLodBake.initialize(state);
        stageVolumeDepth =
                new VolumeDepth(
                        shaders.getShader(RenderStage.Type.VOLUME_DEPTH),
                        stageSceneRender,
                        stageLightRender);
        stageVolumeDepth.initialize(state);
        stageVolumeSplat =
                new VolumeSplat(
                        shaders.getShader(RenderStage.Type.VOLUME_SPLAT),
                        stageSceneRender,
                        stageLightRender);
        stageVolumeSplat.initialize(state);
        stageVolumeColor =
                new VolumeColor(
                        shaders.getShader(RenderStage.Type.VOLUME_COLOR),
                        stageSceneRender,
                        stageLightRender);
        stageVolumeColor.initialize(state);
        stageOitResolve = new OitResolve(shaders.getShader(RenderStage.Type.OIT_RESOLVE), quadMesh);
        stageOitResolve.initialize(state);
        stageAnimationRender = new AnimationRender(shaders.getShader(RenderStage.Type.ANIMATION));
        stageAnimationRender.initialize(state);
        Map<FilterView, ShaderVulkan> filterShaders = new EnumMap<>(FilterView.class);
        for (FilterView view : FilterView.values()) {
            filterShaders.put(view, shaders.getShader(view.getShaderType()));
        }
        stageFilterRender = new FilterRender(filterShaders, quadMesh);
        stageFilterRender.initialize(state);
        stageDebugRender =
                new DebugRender(
                        shaders.getShader(RenderStage.Type.DEBUG),
                        shaders.getShader(RenderStage.Type.DEBUG_NORMALS));
        stageDebugRender.initialize(state);
        stageSwapchainPresent = new SwapchainPresent();
        stageSwapchainPresent.initialize(state);
    }

    /**
     * Whether a pipeline draws anything into the final image, which is copied into the scene
     * window. Without that, the scene window only shows the GUI.
     *
     * @param configuration The pipeline configuration.
     * @return True if the final image is drawn and copied.
     */
    public static boolean drawsFinalImage(@NonNull RenderConfig configuration) {
        return configuration.world();
    }

    /**
     * The GUI renderer, which draws in every window after the pipeline runs.
     *
     * @return The GUI renderer.
     */
    public GuiRender getGuiRender() {
        return stageGuiRender;
    }

    /**
     * Put together the stages a configuration runs. The world is everything from culling to tone
     * mapping: animation, shadows, the scene, lighting, the sky and transparency.
     *
     * @param configuration What to draw.
     * @return The pipeline.
     */
    private PipelineVulkan buildPipeline(@NonNull RenderConfig configuration) {
        List<RenderStage> stages = new ArrayList<>();
        final boolean world = configuration.world();
        if (world) {
            // Bakes first, so what they write is drawn this frame
            // Particles first, when waiting on what came before costs nearly nothing: last
            // frame's draws are done by then, where later this frame's would hold them up
            stages.add(stageParticleUpdate);
            stages.add(stageLodBake);
            stages.add(stageInstanceDrawUpdate);
            stages.add(stageOccupancyVoxelize);
            stages.add(stageInstanceTransform);
            stages.add(stageInstanceCull);
            stages.add(stageLodCull);
            // Animated models' poses, then the shadows they and everything else cast
            stages.add(stageAnimationRender);
            stages.add(stageShadowRender);
            // Draw what was visible last frame, test the rest against what that drew, draw those
            stages.add(stageSceneRender);
            stages.add(stageDepthPyramidBuild);
            stages.add(stageInstanceCullLate);
            stages.add(stageLodCullLate);
            stages.add(stageSceneRender.getLate());
            stages.add(stageLightCull);
            stages.add(stageEnvironmentUpdate);
            stages.add(stageLightRender);
            // The sky the world is lit by, behind it
            stages.add(stageSkyboxRender);
            // Translucent geometry, over the sky too, in any order: what is in front of each
            // surface first, then the surfaces weighted by it
            // Distant cut out and translucent blocks as volumes
            stages.add(stageVolumeUpload);
            stages.add(stageVolumeDepth);
            stages.add(stageOitSplat);
            stages.add(stageVolumeSplat);
            stages.add(stageOitIntegrate);
            stages.add(stageTranslucentRender);
            stages.add(stageVolumeColor);
            stages.add(stageOitResolve);
            // Blends this frame into the ones before, in linear light
            stages.add(stageTaa);
            // From linear light to the image shown, before filters, debug lines and the GUI
            stages.add(stageToneMap);
        }
        if (configuration.runsFilters()) {
            stages.add(stageFilterRender);
        }
        if (configuration.drawsDebug()) {
            // After filters so they don't distort the lines, depth tested against the scene
            stages.add(stageDebugRender);
        }
        if (drawsFinalImage(configuration)) {
            // The GUI is drawn over it in the scene window afterward, see GuiRender
            stages.add(stageSwapchainPresent);
        }

        return new PipelineVulkan(stages.toArray(new RenderStage[0]), configuration);
    }

    /**
     * Create a 2D image to render into, with a view and its own sampler.
     *
     * @param state The Vulkan state.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param format The VkFormat.
     * @param usage The VkImageUsageFlags.
     * @param aspect The VkImageAspectFlags for the view.
     * @param filter The VkFilter for the sampler.
     * @param addressMode The VkSamplerAddressMode for the sampler.
     * @return The texture, in the undefined layout.
     */
    private TextureInfoVulkan createRenderTarget(
            @NonNull VulkanState state,
            int width,
            int height,
            int format,
            int usage,
            int aspect,
            int filter,
            int addressMode) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageCreateInfo =
                    VkImageCreateInfo.calloc(stack)
                            .sType$Default()
                            .imageType(VK_IMAGE_TYPE_2D)
                            .format(format)
                            .extent(e -> e.set(width, height, 1))
                            .mipLevels(1)
                            .arrayLayers(1)
                            .samples(VK_SAMPLE_COUNT_1_BIT)
                            .tiling(VK_IMAGE_TILING_OPTIMAL)
                            .usage(usage)
                            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);

            VmaAllocationCreateInfo imageAlloc =
                    VmaAllocationCreateInfo.calloc(stack)
                            .flags(VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT)
                            .usage(VMA_MEMORY_USAGE_AUTO);

            checkError(
                    vmaCreateImage(
                            state.vmaAllocator,
                            imageCreateInfo,
                            imageAlloc,
                            longOutput,
                            pointerOutput,
                            null));
            final long image = longOutput.get(0);
            final long imageAllocation = pointerOutput.get(0);

            VkImageViewCreateInfo viewCreateInfo =
                    VkImageViewCreateInfo.calloc(stack)
                            .sType$Default()
                            .image(image)
                            .viewType(VK_IMAGE_VIEW_TYPE_2D)
                            .format(format)
                            .subresourceRange(
                                    range -> range.aspectMask(aspect).levelCount(1).layerCount(1));
            checkError(vkCreateImageView(state.device.logical, viewCreateInfo, null, longOutput));
            final long imageView = longOutput.get(0);

            // The default border color is transparent black, like OpenGL
            VkSamplerCreateInfo samplerCreateInfo =
                    VkSamplerCreateInfo.calloc(stack)
                            .sType$Default()
                            .magFilter(filter)
                            .minFilter(filter)
                            .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                            .addressModeU(addressMode)
                            .addressModeV(addressMode)
                            .addressModeW(addressMode)
                            .anisotropyEnable(false)
                            .compareEnable(false)
                            .minLod(0.0f)
                            .maxLod(0.0f);
            checkError(vkCreateSampler(state.device.logical, samplerCreateInfo, null, longOutput));
            final long imageSampler = longOutput.get(0);

            TextureInfoVulkan result =
                    new TextureInfoVulkan()
                            .texture(image)
                            .textureAllocation(imageAllocation)
                            .view(imageView)
                            .sampler(imageSampler);
            result.ownsSampler = true;
            return result;
        }
    }

    /**
     * Create the cascade shadow maps, and the maps of what moves for the cached cascades, cleared
     * to the far plane and ready to be sampled, and give them bindless slots. They are a fixed
     * size, so they don't change when the window resizes.
     *
     * @param state The Vulkan state.
     */
    private void createShadowMaps(@NonNull VulkanState state) {
        shadowMaps = new TextureInfoVulkan[CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT];
        dynamicShadowMaps = new TextureInfoVulkan[CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT];
        final List<TextureInfoVulkan> all = new ArrayList<>();
        for (int i = 0; i < shadowMaps.length; i++) {
            final boolean cached = SunShadowCache.CACHED[i];
            shadowMaps[i] =
                    createShadowMap(
                            state,
                            cached
                                    ? SunShadowCache.STATIC_MAP_SIZE
                                    : CascadeShadowSplit.SHADOW_MAP_SIZE);
            all.add(shadowMaps[i]);
            if (cached) {
                dynamicShadowMaps[i] = createShadowMap(state, SunShadowCache.DYNAMIC_MAP_SIZE);
                all.add(dynamicShadowMaps[i]);
            }
        }
        // Only ever copied into whole before it is read, so it isn't cleared
        scrollCopy = createShadowMap(state, SunShadowCache.STATIC_MAP_SIZE);
        skyMap = createShadowMap(state, SkyOcclusion.MAP_SIZE);
        all.add(skyMap);
        pointAtlas = createShadowMap(state, PointShadows.ATLAS_SIZE);
        all.add(pointAtlas);
        pointDynamicAtlas = createShadowMap(state, PointShadows.ATLAS_SIZE);
        all.add(pointDynamicAtlas);
        state.pointShadows.invalidateAll();
        state.sunShadows.invalidateAll();
        state.skyOcclusion.invalidateAll();
        clearShadowMaps(state, all.toArray(new TextureInfoVulkan[0]));
    }

    /**
     * Create one shadow map and give it a bindless slot.
     *
     * @param state The Vulkan state.
     * @param size Its width and height, in texels.
     * @return The map, in the undefined layout.
     */
    private TextureInfoVulkan createShadowMap(@NonNull VulkanState state, int size) {
        final TextureInfoVulkan map =
                createRenderTarget(
                        state,
                        size,
                        size,
                        DEPTH_FORMAT,
                        VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
                                | VK_IMAGE_USAGE_SAMPLED_BIT
                                | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                                | VK_IMAGE_USAGE_TRANSFER_DST_BIT,
                        VK_IMAGE_ASPECT_DEPTH_BIT,
                        VK_FILTER_LINEAR,
                        VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER);
        state.bindlessTextures.register(state, map);
        return map;
    }

    /**
     * Clear shadow maps to the far plane and leave them ready to be sampled.
     *
     * @param state The Vulkan state.
     * @param shadowMaps The maps, in the undefined layout.
     */
    private static void clearShadowMaps(
            @NonNull VulkanState state, @NonNull TextureInfoVulkan[] shadowMaps) {

        // The light stage samples these even if the shadow stage never draws to them
        state.immediateCommands.submit(
                state,
                commandBuffer -> {
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        VkImageMemoryBarrier2.Buffer barriers =
                                VkImageMemoryBarrier2.calloc(shadowMaps.length, stack);
                        for (int i = 0; i < shadowMaps.length; i++) {
                            barriers.get(i)
                                    .sType$Default()
                                    .srcStageMask(VK_PIPELINE_STAGE_2_NONE)
                                    .srcAccessMask(VK_ACCESS_2_NONE)
                                    .dstStageMask(VK_PIPELINE_STAGE_2_CLEAR_BIT)
                                    .dstAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                                    .image(shadowMaps[i].texture)
                                    .subresourceRange(
                                            range ->
                                                    range.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                                                            .levelCount(1)
                                                            .layerCount(1));
                        }
                        VkDependencyInfo dependencyInfo =
                                VkDependencyInfo.calloc(stack)
                                        .sType$Default()
                                        .pImageMemoryBarriers(barriers);
                        vkCmdPipelineBarrier2(commandBuffer, dependencyInfo);

                        VkClearDepthStencilValue clearValue =
                                VkClearDepthStencilValue.calloc(stack).depth(1.0f);
                        VkImageSubresourceRange.Buffer ranges =
                                VkImageSubresourceRange.calloc(1, stack);
                        ranges.get(0)
                                .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                                .levelCount(1)
                                .layerCount(1);
                        for (TextureInfoVulkan shadowMap : shadowMaps) {
                            vkCmdClearDepthStencilImage(
                                    commandBuffer,
                                    shadowMap.texture,
                                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                                    clearValue,
                                    ranges);
                        }

                        for (int i = 0; i < shadowMaps.length; i++) {
                            barriers.get(i)
                                    .srcStageMask(VK_PIPELINE_STAGE_2_CLEAR_BIT)
                                    .srcAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                                    .dstStageMask(VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT)
                                    .dstAccessMask(VK_ACCESS_2_SHADER_SAMPLED_READ_BIT)
                                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                                    .newLayout(VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL);
                        }
                        vkCmdPipelineBarrier2(commandBuffer, dependencyInfo);
                    }
                });
    }

    /**
     * Destroy the shadow maps and release their bindless slots.
     *
     * @param state The Vulkan state.
     */
    private void cleanupShadowMaps(@NonNull VulkanState state) {
        for (TextureInfoVulkan shadowMap : shadowMaps) {
            state.bindlessTextures.release(state, shadowMap);
            shadowMap.destroy(state);
        }
        shadowMaps = null;
        for (TextureInfoVulkan shadowMap : dynamicShadowMaps) {
            if (shadowMap != null) {
                state.bindlessTextures.release(state, shadowMap);
                shadowMap.destroy(state);
            }
        }
        dynamicShadowMaps = null;
        state.bindlessTextures.release(state, scrollCopy);
        scrollCopy.destroy(state);
        scrollCopy = null;
        state.bindlessTextures.release(state, skyMap);
        skyMap.destroy(state);
        skyMap = null;
        for (TextureInfoVulkan atlas : new TextureInfoVulkan[] {pointAtlas, pointDynamicAtlas}) {
            state.bindlessTextures.release(state, atlas);
            atlas.destroy(state);
        }
        pointAtlas = null;
        pointDynamicAtlas = null;
    }

    private void createShaderData(int width, int height, @NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // TODO(ches) make the gBuffer like 2560 × 1440, just use viewport+scissor if smaller
            // than that
            VkExtent3D imageExtent = VkExtent3D.calloc(stack);
            imageExtent.set(width, height, 1);
            state.realSize.set(width, height, 1);
            state.depthPyramid = new DepthPyramid(state);
            state.depthPyramid.resize(state, width, height);
            state.oitVolume = new OitVolume(state);
            state.oitVolume.resize(state, width, height);
            state.fogVolume = new FogVolume(state);
            state.fogVolume.resize(state, width, height);
            state.particlePool =
                    new ParticlePool(
                            state, Math.max(1, GraphicsManager.getSettings().maxParticles));
            state.occupancy = new OccupancyVolume(state);
            state.environment = new Environment(state);
            state.volumeBricks = new VolumeBricks(state);

            // SharedBuffer adds device address and transfer destination usage to all of these
            final int STORAGE = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
            final int UNIFORM = VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;

            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                state.perFrameData[i] = new PerFrameData();

                final long DEFERRED_UNTIL_LATER = 0;
                state.perFrameData[i].animationOffsets =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                state.perFrameData[i].guiUniforms =
                        SharedBuffer.allocate(
                                ShaderBindings.GUI.UNIFORMS_BUFFER_SIZE, state, UNIFORM);
                state.perFrameData[i].guiCommands =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                state.perFrameData[i].guiPoints =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                state.perFrameData[i].guiPointDetails =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                state.perFrameData[i].guiVertices =
                        SharedBuffer.allocate(
                                DEFERRED_UNTIL_LATER, state, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
                state.perFrameData[i].guiTextureIndices =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                state.perFrameData[i].guiFontStaging =
                        SharedBuffer.allocate(
                                DEFERRED_UNTIL_LATER, state, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
                state.perFrameData[i].lightUniforms =
                        SharedBuffer.allocate(
                                ShaderBindings.Light.UNIFORMS_BUFFER_SIZE, state, UNIFORM);
                state.perFrameData[i].lights =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                state.perFrameData[i].pointShadowMaps =
                        SharedBuffer.allocate(
                                (long) PointShadows.MAX_MAPS * PointShadows.MAP_ENTRY_SIZE,
                                state,
                                STORAGE);
                state.perFrameData[i].materialTransparency =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                state.perFrameData[i].lightClusters =
                        SharedBuffer.allocateDeviceLocal(
                                ShaderBindings.LightCull.CLUSTERS_BUFFER_SIZE, state, STORAGE);
                state.perFrameData[i].lightClusterStats =
                        SharedBuffer.allocate(
                                (long) ShaderBindings.LightCull.STATS_COUNT * Integer.BYTES,
                                state,
                                STORAGE);
                state.perFrameData[i].sceneUniforms =
                        SharedBuffer.allocate(
                                ShaderBindings.Scene.UNIFORMS_BUFFER_SIZE, state, UNIFORM);
                state.perFrameData[i].sceneModelMatrices =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                state.perFrameData[i].cullFrusta =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                state.perFrameData[i].cullCounters =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                state.perFrameData[i].cullView =
                        SharedBuffer.allocate(ShaderBindings.Cull.VIEW_SIZE, state, STORAGE);
                state.perFrameData[i].visibleInstances =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                // Filled in by the culling pass as well as read as draws
                state.perFrameData[i].sceneDrawCommands =
                        SharedBuffer.allocate(
                                DEFERRED_UNTIL_LATER,
                                state,
                                VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | STORAGE);
                state.perFrameData[i].lodDrawCommands =
                        SharedBuffer.allocate(
                                DEFERRED_UNTIL_LATER,
                                state,
                                VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | STORAGE);
                state.perFrameData[i].lodCounts =
                        SharedBuffer.allocate(
                                DEFERRED_UNTIL_LATER,
                                state,
                                VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | STORAGE);
                state.perFrameData[i].lodWorlds =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                state.perFrameData[i].modelDrawInfo = new LinkedHashMap<>();
                state.perFrameData[i].debugVertices =
                        SharedBuffer.allocate(
                                DEFERRED_UNTIL_LATER, state, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
                state.perFrameData[i].skyboxUniforms =
                        SharedBuffer.allocate(
                                ShaderBindings.Skybox.UNIFORMS_BUFFER_SIZE, state, UNIFORM);
                state.perFrameData[i].filterUniforms =
                        SharedBuffer.allocate(
                                ShaderBindings.Filter.UNIFORMS_BUFFER_SIZE, state, UNIFORM);
                state.perFrameData[i].materials =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                state.perFrameData[i].cascadeShadowSplits =
                        new CascadeShadowSplit[CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT];

                createIntermediaryTextures(state, state.perFrameData[i], imageExtent);
            }
        }
    }

    private void createIntermediaryTextures(
            @NonNull VulkanState state,
            @NonNull PerFrameData data,
            @NonNull VkExtent3D imageExtent) {
        data.cascadeShadows = shadowMaps;
        data.cascadeDynamicShadows = dynamicShadowMaps;
        data.cascadeScrollCopy = scrollCopy;
        data.skyMap = skyMap;
        data.pointAtlas = pointAtlas;
        data.pointDynamicAtlas = pointDynamicAtlas;
        data.skyView = new CascadeShadowSplit();
        for (int shadow = 0; shadow < CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT; shadow++) {
            data.cascadeShadowSplits[shadow] = new CascadeShadowSplit();
        }
        data.gBuffer = generateGBuffer(state, imageExtent);

        // These are sampled through the bindless array by the light stage
        for (TextureInfoVulkan texture : data.gBuffer.textures()) {
            state.bindlessTextures.register(state, texture);
        }
        state.bindlessTextures.register(state, data.gBuffer.depth());

        data.sceneColor =
                createRenderTarget(
                        state,
                        imageExtent.width(),
                        imageExtent.height(),
                        SCENE_COLOR_FORMAT,
                        VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_FILTER_NEAREST,
                        VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        // Read through the bindless array by the tone map stage
        state.bindlessTextures.register(state, data.sceneColor);
        // Read through the bindless array, filtered, by the next frame's anti-aliasing as history,
        // and by the tone map stage
        data.taaColor =
                createRenderTarget(
                        state,
                        imageExtent.width(),
                        imageExtent.height(),
                        SCENE_COLOR_FORMAT,
                        VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_FILTER_LINEAR,
                        VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        state.bindlessTextures.register(state, data.taaColor);
        data.oitAccum =
                createRenderTarget(
                        state,
                        imageExtent.width(),
                        imageExtent.height(),
                        OIT_ACCUM_FORMAT,
                        VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_FILTER_NEAREST,
                        VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        data.oitExtinction =
                createRenderTarget(
                        state,
                        imageExtent.width(),
                        imageExtent.height(),
                        OIT_EXTINCTION_FORMAT,
                        VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_FILTER_NEAREST,
                        VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        // Read through the bindless array by the transparency resolve
        state.bindlessTextures.register(state, data.oitAccum);
        state.bindlessTextures.register(state, data.oitExtinction);

        // Transfer destination so the filter can clear it if nothing rendered to it
        data.preFilterTexture =
                createRenderTarget(
                        state,
                        imageExtent.width(),
                        imageExtent.height(),
                        SCREEN_FORMAT,
                        VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
                                | VK_IMAGE_USAGE_SAMPLED_BIT
                                | VK_IMAGE_USAGE_TRANSFER_DST_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_FILTER_LINEAR,
                        VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        data.finalTexture =
                createRenderTarget(
                        state,
                        imageExtent.width(),
                        imageExtent.height(),
                        SCREEN_FORMAT,
                        VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
                                | VK_IMAGE_USAGE_SAMPLED_BIT
                                | VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_FILTER_LINEAR,
                        VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
    }

    /** Clean up all the rendering resources. */
    public void cleanup(@NonNull VulkanState state) {
        for (PerFrameData frameData : state.perFrameData) {
            cleanupPerFrameData(state, frameData);
        }
        stageAnimationRender.cleanup(state);
        stageFilterRender.cleanup(state);
        stageDebugRender.cleanup(state);
        stageGuiRender.cleanup(state);
        stageLightRender.cleanup(state);
        stageToneMap.cleanup(state);
        stageTaa.cleanup(state);
        stageOitResolve.cleanup(state);
        stageTranslucentRender.cleanup(state);
        stageOitSplat.cleanup(state);
        stageParticleUpdate.cleanup(state);
        stageOitIntegrate.cleanup(state);
        stageVolumeUpload.cleanup(state);
        stageLodBake.cleanup(state);
        stageVolumeDepth.cleanup(state);
        stageVolumeSplat.cleanup(state);
        stageVolumeColor.cleanup(state);
        stageLightCull.cleanup(state);
        stageEnvironmentUpdate.cleanup(state);
        state.tableScatter.cleanup(state);
        state.tableScatter = null;
        stageInstanceDrawUpdate.cleanup(state);
        stageOccupancyVoxelize.cleanup(state);
        stageInstanceTransform.cleanup(state);
        stageInstanceCull.cleanup(state);
        stageInstanceCullLate.cleanup(state);
        stageLodCull.cleanup(state);
        stageLodCullLate.cleanup(state);
        stageDepthPyramidBuild.cleanup(state);
        stageSceneRender.cleanup(state);
        stageShadowRender.cleanup(state);
        stageSkyboxRender.cleanup(state);
        stageSwapchainPresent.cleanup(state);
        cleanupShadowMaps(state);
        state.depthPyramid.cleanup(state);
        state.depthPyramid = null;
        state.oitVolume.cleanup(state);
        state.oitVolume = null;
        state.fogVolume.cleanup(state);
        state.fogVolume = null;
        state.particlePool.cleanup(state);
        state.particlePool = null;
        state.occupancy.cleanup(state);
        state.occupancy = null;
        state.environment.cleanup(state);
        state.environment = null;
        state.volumeBricks.cleanup(state);
        state.volumeBricks = null;
        TextureInfoVulkan fontAtlasInfo = state.textureRegistry.remove(fontAtlas);
        if (fontAtlasInfo != null) {
            GraphicsManager.getDeletionQueue().add(fontAtlasInfo);
        }
        if (IkGui.getIO().fonts.texture == fontAtlas) {
            IkGui.getIO().fonts.texture = null;
        }
        fontAtlas = null;
        skybox.cleanup(state);
        skybox = null;
        quadMesh.cleanup(state);
        quadMesh = null;
    }

    /**
     * Clean up any textures that are tied to the screen size.
     *
     * @param state The state.
     * @param data The frame data we are cleaning up.
     */
    private void cleanupIntermediaryTextures(
            @NonNull VulkanState state, @NonNull PerFrameData data) {
        // Shared between frames, cleaned up separately
        data.cascadeShadows = null;
        data.cascadeDynamicShadows = null;
        data.skyMap = null;
        data.pointAtlas = null;
        data.pointDynamicAtlas = null;

        if (data.gBuffer != null) {
            for (TextureInfoVulkan info : data.gBuffer.textures()) {
                state.bindlessTextures.release(state, info);
                info.destroy(state);
            }
            state.bindlessTextures.release(state, data.gBuffer.depth());
            data.gBuffer.depth().destroy(state);
            data.gBuffer = null;
        }

        if (data.oitAccum != null) {
            state.bindlessTextures.release(state, data.oitAccum);
            data.oitAccum.destroy(state);
            data.oitAccum = null;
        }
        if (data.oitExtinction != null) {
            state.bindlessTextures.release(state, data.oitExtinction);
            data.oitExtinction.destroy(state);
            data.oitExtinction = null;
        }
        if (data.sceneColor != null) {
            state.bindlessTextures.release(state, data.sceneColor);
            data.sceneColor.destroy(state);
            data.sceneColor = null;
        }
        if (data.taaColor != null) {
            state.bindlessTextures.release(state, data.taaColor);
            data.taaColor.destroy(state);
            data.taaColor = null;
        }
        if (data.preFilterTexture != null) {
            data.preFilterTexture.destroy(state);
            data.preFilterTexture = null;
        }
        if (data.finalTexture != null) {
            data.finalTexture.destroy(state);
            data.finalTexture = null;
        }
    }

    private void cleanupPerFrameData(@NonNull VulkanState state, @NonNull PerFrameData data) {
        SharedBuffer.free(data.animationOffsets, state);
        data.animationOffsets = null;
        SharedBuffer.free(data.guiUniforms, state);
        data.guiUniforms = null;
        SharedBuffer.free(data.guiCommands, state);
        data.guiCommands = null;
        SharedBuffer.free(data.guiPoints, state);
        data.guiPoints = null;
        SharedBuffer.free(data.guiPointDetails, state);
        data.guiPointDetails = null;
        SharedBuffer.free(data.guiVertices, state);
        data.guiVertices = null;
        SharedBuffer.free(data.guiTextureIndices, state);
        data.guiTextureIndices = null;
        SharedBuffer.free(data.guiFontStaging, state);
        data.guiFontStaging = null;
        SharedBuffer.free(data.lightUniforms, state);
        data.lightUniforms = null;
        SharedBuffer.free(data.lights, state);
        data.lights = null;
        SharedBuffer.free(data.pointShadowMaps, state);
        data.pointShadowMaps = null;
        SharedBuffer.free(data.materialTransparency, state);
        data.materialTransparency = null;
        SharedBuffer.free(data.lightClusters, state);
        data.lightClusters = null;
        SharedBuffer.free(data.lightClusterStats, state);
        data.lightClusterStats = null;
        SharedBuffer.free(data.sceneUniforms, state);
        data.sceneUniforms = null;
        SharedBuffer.free(data.sceneModelMatrices, state);
        data.sceneModelMatrices = null;
        SharedBuffer.free(data.cullFrusta, state);
        data.cullFrusta = null;
        SharedBuffer.free(data.cullCounters, state);
        data.cullCounters = null;
        SharedBuffer.free(data.cullView, state);
        data.cullView = null;
        SharedBuffer.free(data.visibleInstances, state);
        data.visibleInstances = null;
        SharedBuffer.free(data.sceneDrawCommands, state);
        data.sceneDrawCommands = null;
        SharedBuffer.free(data.lodDrawCommands, state);
        data.lodDrawCommands = null;
        SharedBuffer.free(data.lodCounts, state);
        data.lodCounts = null;
        SharedBuffer.free(data.lodWorlds, state);
        data.lodWorlds = null;
        data.lodWorldCount = 0;
        data.modelDrawInfo = null;
        SharedBuffer.free(data.skyboxUniforms, state);
        data.skyboxUniforms = null;
        SharedBuffer.free(data.debugVertices, state);
        data.debugVertices = null;
        SharedBuffer.free(data.filterUniforms, state);
        data.filterUniforms = null;
        SharedBuffer.free(data.materials, state);
        data.materials = null;

        data.cascadeShadowSplits = null;

        cleanupIntermediaryTextures(state, data);
    }

    private void createGuiFont() {
        FontAtlas fontAtlas1 = IkGui.getIO().fonts;
        final String notoSans = "fonts/NotoSans.ttf";
        if (!fontAtlas1.loadFont(notoSans)) {
            log.error("Issue setting up GUI font");
        }
        IkGui.setFont(notoSans, 12);
        IkGui.setFontFallbacks(notoSans);
        fontAtlas1.addDefaultCharacters(notoSans, IkGui.getFontSize());

        this.fontAtlas =
                GraphicsManager.getRenderInstance()
                        .getTextureLoader()
                        .loadNow(
                                GraphicsPlugin.PLUGIN_NAME,
                                null,
                                Format.R8G8B8A8_UNORM,
                                FontAtlas.FONT_ATLAS_IMAGE_WIDTH,
                                FontAtlas.FONT_ATLAS_IMAGE_HEIGHT);
        // So debug tools like the demo and metrics windows can show the atlas
        fontAtlas1.texture = this.fontAtlas;
    }

    private GBuffer generateGBuffer(@NonNull VulkanState state, @NonNull VkExtent3D imageExtent) {
        final int width = imageExtent.width();
        final int height = imageExtent.height();
        final int colorUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;

        // Read 1:1 by the light stage, so nearest filtering like in OpenGL
        TextureInfoVulkan[] textures = new TextureInfoVulkan[GBuffer.TEXTURE_COUNT];
        for (int i = 0; i < GBuffer.MATERIAL; i++) {
            textures[i] =
                    createRenderTarget(
                            state,
                            width,
                            height,
                            GBUFFER_COLOR_FORMAT,
                            colorUsage,
                            VK_IMAGE_ASPECT_COLOR_BIT,
                            VK_FILTER_NEAREST,
                            VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        }
        textures[GBuffer.MATERIAL] =
                createRenderTarget(
                        state,
                        width,
                        height,
                        GBUFFER_MATERIAL_FORMAT,
                        colorUsage,
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_FILTER_NEAREST,
                        VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        textures[GBuffer.EMISSION] =
                createRenderTarget(
                        state,
                        width,
                        height,
                        GBUFFER_EMISSION_FORMAT,
                        colorUsage,
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_FILTER_NEAREST,
                        VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        TextureInfoVulkan depth =
                createRenderTarget(
                        state,
                        width,
                        height,
                        DEPTH_FORMAT,
                        VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                        VK_IMAGE_ASPECT_DEPTH_BIT,
                        VK_FILTER_NEAREST,
                        VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);

        return new GBuffer(textures, depth, width, height);
    }

    public PipelineVulkan getPipeline(@NonNull RenderConfig configuration) {
        return renderers.computeIfAbsent(configuration, this::buildPipeline);
    }

    /**
     * Fetch what the filter stage currently shows.
     *
     * @return The current filter view.
     */
    public FilterView getFilterView() {
        return stageFilterRender.getView();
    }

    /**
     * Change what the filter stage shows, starting next frame.
     *
     * @param view The view to show.
     */
    public void setFilterView(@NonNull FilterView view) {
        stageFilterRender.setView(view);
    }

    /**
     * Update the buffers and GUI when we resize the screen.
     *
     * @param width The new screen width in pixels.
     * @param height The new screen height in pixels.
     */
    public void resize(@NonNull VulkanState state, final int width, final int height) {
        if (width <= state.realSize.width() && height <= state.realSize.height()) {
            // We are getting smaller, I don't care
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            /*
             * Keep doubling the image until we get to the full screen size, but always at least fit the window. The
             * monitor size is in screen coordinates, which can be smaller than the window in pixels (high DPI on
             * macOS), and windows can span several monitors.
             */
            final int newWidth =
                    Math.max(
                            width,
                            Math.min(
                                    Math.max(state.realSize.width(), width) * 2,
                                    GlfwPlatform.getLargestMonitorWidth()));
            final int newHeight =
                    Math.max(
                            height,
                            Math.min(
                                    Math.max(state.realSize.height(), height) * 2,
                                    GlfwPlatform.getLargestMonitorHeight()));

            VkExtent3D imageExtent = VkExtent3D.calloc(stack);
            imageExtent.set(newWidth, newHeight, 1);

            state.realSize.set(newWidth, newHeight, 1);

            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                cleanupIntermediaryTextures(state, state.perFrameData[i]);
                createIntermediaryTextures(state, state.perFrameData[i], imageExtent);
            }
            state.depthPyramid.resize(state, newWidth, newHeight);
            state.oitVolume.resize(state, newWidth, newHeight);
            state.fogVolume.resize(state, newWidth, newHeight);
        }
    }
}
