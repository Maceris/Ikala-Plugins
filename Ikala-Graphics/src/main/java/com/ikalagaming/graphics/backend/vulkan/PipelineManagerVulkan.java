package com.ikalagaming.graphics.backend.vulkan;

import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.ShaderMap;
import com.ikalagaming.graphics.backend.vulkan.stages.*;
import com.ikalagaming.graphics.frontend.*;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.data.FontAtlas;
import com.ikalagaming.graphics.graph.CascadeShadowSplit;

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

    /** Format of the g-buffer depth and the shadow maps. Supported everywhere for both uses. */
    public static final int DEPTH_FORMAT = VK_FORMAT_D32_SFLOAT;

    /**
     * Format of the images we render the lit scene, filters, and GUI into. Not sRGB, since the
     * OpenGL backend doesn't convert to sRGB either and we want the same output.
     */
    public static final int SCREEN_FORMAT = VK_FORMAT_R8G8B8A8_UNORM;

    /** Fallback pipeline that does nothing. */
    public static final Pipeline ERROR_PIPELINE =
            new PipelineVulkan(new RenderStage[0], RenderConfig.ERROR_MASK);

    /** The texture we store the font atlas on. */
    private Texture fontAtlas;

    /** A mesh for rendering onto. */
    private QuadMesh quadMesh;

    /** The map from config value to the associated renderer. */
    private final Map<Integer, Pipeline> renderers;

    /** Model used for rendering the skybox. */
    private SkyboxModel skybox;

    /**
     * The cascade shadow maps. These are shared between frames like in OpenGL, barriers keep a
     * frame from writing them while an earlier one reads them.
     */
    private TextureInfoVulkan[] shadowMaps;

    private final AnimationRender stageAnimationRender;
    private final FilterRender stageFilterRender;
    private final GuiRender stageGuiRender;
    private final LightRender stageLightRender;
    private final ModelMatrixUpdate stageModelMatrixUpdate;
    private final SceneRender stageSceneRender;
    private final ShadowRender stageShadowRender;
    private final SkyboxRender stageSkyboxRender;
    private final SwapchainPresent stageSwapchainPresent;

    private final LongBuffer longOutput = MemoryUtil.memAllocLong(1);
    private final PointerBuffer pointerOutput = MemoryUtil.memAllocPointer(1);

    public PipelineManagerVulkan(
            @NonNull Window window, @NonNull ShaderMap shaders, @NonNull VulkanState state) {

        renderers = new HashMap<>();
        createShadowMaps(state);
        createShaderData(window, state);
        createGuiFont();
        skybox = new SkyboxModel(state);
        quadMesh = QuadMesh.getInstance(state);

        stageModelMatrixUpdate = new ModelMatrixUpdate();
        stageModelMatrixUpdate.initialize(state);
        stageSceneRender =
                new SceneRender((ShaderVulkan) shaders.getShader(RenderStage.Type.SCENE));
        stageSceneRender.initialize(state);
        stageGuiRender =
                new GuiRender((ShaderVulkan) shaders.getShader(RenderStage.Type.GUI), fontAtlas);
        stageGuiRender.initialize(state);
        stageSkyboxRender =
                new SkyboxRender((ShaderVulkan) shaders.getShader(RenderStage.Type.SKYBOX), skybox);
        stageSkyboxRender.initialize(state);
        stageShadowRender =
                new ShadowRender((ShaderVulkan) shaders.getShader(RenderStage.Type.SHADOW));
        stageShadowRender.initialize(state);
        stageLightRender =
                new LightRender((ShaderVulkan) shaders.getShader(RenderStage.Type.LIGHT), quadMesh);
        stageLightRender.initialize(state);
        stageAnimationRender =
                new AnimationRender((ShaderVulkan) shaders.getShader(RenderStage.Type.ANIMATION));
        stageAnimationRender.initialize(state);
        stageFilterRender =
                new FilterRender(
                        (ShaderVulkan) shaders.getShader(RenderStage.Type.FILTER), quadMesh);
        stageFilterRender.initialize(state);
        stageSwapchainPresent = new SwapchainPresent();
        stageSwapchainPresent.initialize(state);
    }

    private Pipeline buildPipeline(final int configuration) {
        List<RenderStage> stages = new ArrayList<>();
        if (RenderConfig.hasError(configuration)) {
            log.error("Error in pipeline config");
            return ERROR_PIPELINE;
        }

        if (RenderConfig.hasSceneStage(configuration)) {
            stages.add(stageModelMatrixUpdate);
        }
        if (RenderConfig.hasAnimationStage(configuration)) {
            stages.add(stageAnimationRender);
        }
        if (RenderConfig.hasShadowStage(configuration)) {
            stages.add(stageShadowRender);
        }
        if (RenderConfig.hasSceneStage(configuration)) {
            stages.add(stageSceneRender);
            stages.add(stageLightRender);
        }
        if (RenderConfig.hasSkyboxStage(configuration)) {
            stages.add(stageSkyboxRender);
        }
        if (RenderConfig.hasFilterStage(configuration)) {
            stages.add(stageFilterRender);
        }
        if (RenderConfig.hasGuiStage(configuration)) {
            stages.add(stageGuiRender);
        }
        stages.add(stageSwapchainPresent);

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
     * Create the cascade shadow maps, cleared to the far plane and ready to be sampled, and give
     * them bindless slots. They are a fixed size, so they don't change when the window resizes.
     *
     * @param state The Vulkan state.
     */
    private void createShadowMaps(@NonNull VulkanState state) {
        shadowMaps = new TextureInfoVulkan[CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT];
        for (int i = 0; i < shadowMaps.length; i++) {
            shadowMaps[i] =
                    createRenderTarget(
                            state,
                            CascadeShadowSplit.SHADOW_MAP_WIDTH,
                            CascadeShadowSplit.SHADOW_MAP_HEIGHT,
                            DEPTH_FORMAT,
                            VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
                                    | VK_IMAGE_USAGE_SAMPLED_BIT
                                    | VK_IMAGE_USAGE_TRANSFER_DST_BIT,
                            VK_IMAGE_ASPECT_DEPTH_BIT,
                            VK_FILTER_LINEAR,
                            VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER);
            state.bindlessTextures.register(state, shadowMaps[i]);
        }

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
    }

    private void createShaderData(@NonNull Window window, @NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // TODO(ches) make the gBuffer like 2560 × 1440, just use viewport+scissor if smaller
            // than that
            VkExtent3D imageExtent = VkExtent3D.calloc(stack);
            imageExtent.set(window.getWidth(), window.getHeight(), 1);
            state.realSize.set(window.getWidth(), window.getHeight(), 1);

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
                state.perFrameData[i].lightPointLights =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                state.perFrameData[i].lightSpotLights =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                state.perFrameData[i].sceneUniforms =
                        SharedBuffer.allocate(
                                ShaderBindings.Scene.UNIFORMS_BUFFER_SIZE, state, UNIFORM);
                state.perFrameData[i].sceneModelMatrices =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                state.perFrameData[i].sceneMaterialOverrides =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, STORAGE);
                state.perFrameData[i].sceneDrawCommands =
                        SharedBuffer.allocate(
                                DEFERRED_UNTIL_LATER, state, VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT);
                state.perFrameData[i].modelDrawInfo = new HashMap<>();
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
        for (int shadow = 0; shadow < CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT; shadow++) {
            data.cascadeShadowSplits[shadow] = new CascadeShadowSplit();
        }
        data.gBuffer = generateGBuffer(state, imageExtent);

        // These are sampled through the bindless array by the light stage
        for (TextureInfoVulkan texture : data.gBuffer.textures()) {
            state.bindlessTextures.register(state, texture);
        }
        state.bindlessTextures.register(state, data.gBuffer.depth());

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
        stageGuiRender.cleanup(state);
        stageLightRender.cleanup(state);
        stageModelMatrixUpdate.cleanup(state);
        stageSceneRender.cleanup(state);
        stageShadowRender.cleanup(state);
        stageSkyboxRender.cleanup(state);
        stageSwapchainPresent.cleanup(state);
        cleanupShadowMaps(state);
        GraphicsManager.getDeletionQueue().add(fontAtlas);
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

        if (data.gBuffer != null) {
            for (TextureInfoVulkan info : data.gBuffer.textures()) {
                state.bindlessTextures.release(state, info);
                info.destroy(state);
            }
            state.bindlessTextures.release(state, data.gBuffer.depth());
            data.gBuffer.depth().destroy(state);
            data.gBuffer = null;
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
        SharedBuffer.free(data.lightPointLights, state);
        data.lightPointLights = null;
        SharedBuffer.free(data.lightSpotLights, state);
        data.lightSpotLights = null;
        SharedBuffer.free(data.sceneUniforms, state);
        data.sceneUniforms = null;
        SharedBuffer.free(data.sceneModelMatrices, state);
        data.sceneModelMatrices = null;
        SharedBuffer.free(data.sceneMaterialOverrides, state);
        data.sceneMaterialOverrides = null;
        SharedBuffer.free(data.sceneDrawCommands, state);
        data.sceneDrawCommands = null;
        data.modelDrawInfo = null;
        SharedBuffer.free(data.skyboxUniforms, state);
        data.skyboxUniforms = null;
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
                        .load(
                                null,
                                Format.R8G8B8A8_UNORM,
                                FontAtlas.FONT_ATLAS_IMAGE_WIDTH,
                                FontAtlas.FONT_ATLAS_IMAGE_HEIGHT);
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

    public Pipeline getPipeline(final int configuration) {
        return renderers.computeIfAbsent(configuration, this::buildPipeline);
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
                                    Window.getLargestMonitorWidth()));
            final int newHeight =
                    Math.max(
                            height,
                            Math.min(
                                    Math.max(state.realSize.height(), height) * 2,
                                    Window.getLargestMonitorHeight()));

            VkExtent3D imageExtent = VkExtent3D.calloc(stack);
            imageExtent.set(newWidth, newHeight, 1);

            state.realSize.set(newWidth, newHeight, 1);

            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                cleanupIntermediaryTextures(state, state.perFrameData[i]);
                createIntermediaryTextures(state, state.perFrameData[i], imageExtent);
            }
        }
    }
}
