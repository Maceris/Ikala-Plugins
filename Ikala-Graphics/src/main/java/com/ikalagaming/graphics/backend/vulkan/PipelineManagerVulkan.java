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

import imgui.ImFontAtlas;
import imgui.ImGui;
import imgui.type.ImInt;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.*;

@Slf4j
public class PipelineManagerVulkan {

    /** The size of a 4x4 model matrix ({@value}). */
    public static final int MODEL_MATRIX_SIZE = 4 * 4;

    /** Fallback pipeline that does nothing. */
    public static final Pipeline ERROR_PIPELINE =
            new PipelineVulkan(new RenderStage[0], RenderConfig.ERROR_MASK);

    /** The texture we store font atlas on. */
    @Deprecated private Texture imguiFont;

    /** The texture we store the font atlas on. */
    private Texture fontAtlas;

    /** The mesh to render. */
    private ImGuiMesh imGuiMesh;

    /** The GUI mesh to render. */
    private GuiMesh guiMesh;

    /** A mesh for rendering onto. */
    private QuadMesh quadMesh;

    /** The map from config value to the associated renderer. */
    private final Map<Integer, Pipeline> renderers;

    /** Model used for rendering the skybox. */
    private SkyboxModel skybox;

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
        createShaderData(window, state);
        createGuiFont();
        skybox = new SkyboxModel();
        quadMesh = QuadMesh.getInstance(state);
        imGuiMesh = ImGuiMesh.create();
        guiMesh = GuiMesh.create();

        stageModelMatrixUpdate = new ModelMatrixUpdate();
        stageModelMatrixUpdate.initialize(state);
        stageSceneRender =
                new SceneRender((ShaderVulkan) shaders.getShader(RenderStage.Type.SCENE));
        stageSceneRender.initialize(state);
        stageGuiRender =
                new GuiRender(
                        (ShaderVulkan) shaders.getShader(RenderStage.Type.GUI_LEGACY),
                        (ShaderVulkan) shaders.getShader(RenderStage.Type.GUI),
                        imGuiMesh,
                        guiMesh,
                        fontAtlas);
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

    private TextureInfo createDepthTexture(
            @NonNull VulkanState state, @NonNull VkExtent3D imageExtent) {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            VkImageCreateInfo imageCreateInfo =
                    VkImageCreateInfo.calloc(stack)
                            .sType$Default()
                            .imageType(VK_IMAGE_TYPE_2D)
                            .format(VK_FORMAT_D32_SFLOAT)
                            .extent(imageExtent)
                            .mipLevels(1)
                            .arrayLayers(1)
                            .samples(VK_SAMPLE_COUNT_1_BIT)
                            .tiling(VK_IMAGE_TILING_OPTIMAL)
                            .usage(
                                    VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
                                            | VK_IMAGE_USAGE_SAMPLED_BIT)
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
                            .format(VK_FORMAT_D32_SFLOAT)
                            .subresourceRange(
                                    VkImageSubresourceRange.calloc(stack)
                                            .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                                            .levelCount(1)
                                            .layerCount(1));
            checkError(vkCreateImageView(state.device.logical, viewCreateInfo, null, longOutput));
            final long imageView = longOutput.get(0);

            VkSamplerCreateInfo samplerCreateInfo = VkSamplerCreateInfo.calloc(stack);
            samplerCreateInfo
                    .sType$Default()
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER)
                    .anisotropyEnable(false)
                    .compareEnable(false)
                    .compareOp(VK_COMPARE_OP_NEVER)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                    .mipLodBias(0.0f)
                    .minLod(0.0f)
                    .maxLod(0.0f);

            checkError(vkCreateSampler(state.device.logical, samplerCreateInfo, null, longOutput));
            final long imageSampler = longOutput.get(0);

            return new TextureInfo()
                    .texture(image)
                    .textureAllocation(imageAllocation)
                    .view(imageView)
                    .sampler(imageSampler);
        }
    }

    private void createShaderData(@NonNull Window window, @NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // TODO(ches) make the gBuffer like 2560 × 1440, just use viewport+scissor if smaller
            // than that
            VkExtent3D imageExtent = VkExtent3D.calloc(stack);
            imageExtent.set(window.getWidth(), window.getHeight(), 1);

            final int NORMAL_USAGE =
                    VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;

            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                state.perFrameData[i] = new PerFrameData();

                final long DEFERRED_UNTIL_LATER = 0;
                state.perFrameData[i].animationData =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, NORMAL_USAGE);
                state.perFrameData[i].animationOffsets =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, NORMAL_USAGE);
                state.perFrameData[i].animationModelData =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, NORMAL_USAGE);
                state.perFrameData[i].animationBoneWeight =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, NORMAL_USAGE);
                state.perFrameData[i].animationTarget =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, NORMAL_USAGE);
                state.perFrameData[i].guiUniforms =
                        SharedBuffer.allocate(
                                ShaderBindings.GUI.UNIFORMS_BUFFER_SIZE,
                                state,
                                NORMAL_USAGE
                                        | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT
                                        | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
                state.perFrameData[i].guiCommands =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, NORMAL_USAGE);
                state.perFrameData[i].guiPoints =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, NORMAL_USAGE);
                state.perFrameData[i].guiPointDetails =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, NORMAL_USAGE);
                state.perFrameData[i].lightUniforms =
                        SharedBuffer.allocate(
                                ShaderBindings.Light.UNIFORMS_BUFFER_SIZE,
                                state,
                                NORMAL_USAGE | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
                state.perFrameData[i].lightPointLights =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, NORMAL_USAGE);
                state.perFrameData[i].lightSpotLights =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, NORMAL_USAGE);
                state.perFrameData[i].sceneUniforms =
                        SharedBuffer.allocate(
                                ShaderBindings.Scene.UNIFORMS_BUFFER_SIZE,
                                state,
                                NORMAL_USAGE | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
                state.perFrameData[i].sceneModelMatrices =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, NORMAL_USAGE);
                state.perFrameData[i].sceneMaterialOverrides =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, NORMAL_USAGE);
                state.perFrameData[i].shadowUniforms =
                        SharedBuffer.allocate(
                                ShaderBindings.Shadow.UNIFORMS_BUFFER_SIZE,
                                state,
                                NORMAL_USAGE | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
                state.perFrameData[i].shadowModelMatrices =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, NORMAL_USAGE);
                state.perFrameData[i].skyboxUniforms =
                        SharedBuffer.allocate(
                                ShaderBindings.Skybox.UNIFORMS_BUFFER_SIZE,
                                state,
                                NORMAL_USAGE | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
                state.perFrameData[i].materials =
                        SharedBuffer.allocate(DEFERRED_UNTIL_LATER, state, NORMAL_USAGE);
                state.perFrameData[i].textures =
                        SharedBuffer.allocate(
                                state.device.physical.bindlessTextureDescriptorBufferSize,
                                state,
                                NORMAL_USAGE);
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
        data.cascadeShadows = new TextureInfo[CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT];
        for (int shadow = 0; shadow < CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT; shadow++) {
            data.cascadeShadowSplits[shadow] = new CascadeShadowSplit();
            data.cascadeShadows[shadow] = createDepthTexture(state, imageExtent);
        }
        data.gBuffer = generateGBuffer(state, imageExtent);
        data.preFilterTexture =
                createTexture(
                        state,
                        imageExtent,
                        VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);
        data.finalTexture =
                createTexture(
                        state,
                        imageExtent,
                        VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
                                | VK_IMAGE_USAGE_SAMPLED_BIT
                                | VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
    }

    private TextureInfo createTexture(
            @NonNull VulkanState state, @NonNull VkExtent3D imageExtent, int imageUsage) {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            VkImageCreateInfo imageCreateInfo =
                    VkImageCreateInfo.calloc(stack)
                            .sType$Default()
                            .imageType(VK_IMAGE_TYPE_2D)
                            .format(VK_FORMAT_R8G8B8A8_SRGB)
                            .extent(imageExtent)
                            .mipLevels(1)
                            .arrayLayers(1)
                            .samples(VK_SAMPLE_COUNT_1_BIT)
                            .tiling(VK_IMAGE_TILING_OPTIMAL)
                            .usage(imageUsage)
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
                            .format(VK_FORMAT_R8G8B8A8_SRGB)
                            .subresourceRange(
                                    VkImageSubresourceRange.calloc(stack)
                                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                            .levelCount(1)
                                            .layerCount(1));
            checkError(vkCreateImageView(state.device.logical, viewCreateInfo, null, longOutput));
            final long imageView = longOutput.get(0);

            VkSamplerCreateInfo samplerCreateInfo = VkSamplerCreateInfo.calloc(stack);
            samplerCreateInfo
                    .sType$Default()
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .anisotropyEnable(false)
                    .compareEnable(false)
                    .compareOp(VK_COMPARE_OP_ALWAYS)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                    .mipLodBias(0.0f)
                    .minLod(0.0f)
                    .maxLod(0.0f);

            checkError(vkCreateSampler(state.device.logical, samplerCreateInfo, null, longOutput));
            final long imageSampler = longOutput.get(0);

            return new TextureInfo()
                    .texture(image)
                    .textureAllocation(imageAllocation)
                    .view(imageView)
                    .sampler(imageSampler);
        }
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
        GraphicsManager.getDeletionQueue().add(imguiFont);
        imguiFont = null;
        GraphicsManager.getDeletionQueue().add(fontAtlas);
        fontAtlas = null;
        imGuiMesh.cleanup();
        guiMesh.cleanup();
        skybox.cleanup();
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
        if (data.cascadeShadows != null) {
            for (int shadow = 0; shadow < CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT; shadow++) {
                vmaDestroyImage(
                        state.vmaAllocator,
                        data.cascadeShadows[shadow].texture,
                        data.cascadeShadows[shadow].textureAllocation);
            }
            data.cascadeShadows = null;
        }

        if (data.gBuffer != null) {
            for (TextureInfo info : data.gBuffer.textures()) {
                vmaDestroyImage(state.vmaAllocator, info.texture, info.textureAllocation);
            }
            vmaDestroyImage(
                    state.vmaAllocator,
                    data.gBuffer.depth().texture,
                    data.gBuffer.depth().textureAllocation);
            data.gBuffer = null;
        }

        if (data.preFilterTexture != null) {
            vmaDestroyImage(
                    state.vmaAllocator,
                    data.preFilterTexture.texture,
                    data.preFilterTexture.textureAllocation);
            data.preFilterTexture = null;
        }
        if (data.finalTexture != null) {
            vmaDestroyImage(
                    state.vmaAllocator,
                    data.finalTexture.texture,
                    data.finalTexture.textureAllocation);
            data.finalTexture = null;
        }
    }

    private void cleanupPerFrameData(@NonNull VulkanState state, @NonNull PerFrameData data) {
        SharedBuffer.free(data.animationData, state);
        data.animationData = null;
        SharedBuffer.free(data.animationOffsets, state);
        data.animationOffsets = null;
        SharedBuffer.free(data.animationModelData, state);
        data.animationModelData = null;
        SharedBuffer.free(data.animationBoneWeight, state);
        data.animationBoneWeight = null;
        SharedBuffer.free(data.animationTarget, state);
        data.animationTarget = null;
        SharedBuffer.free(data.guiUniforms, state);
        data.guiUniforms = null;
        SharedBuffer.free(data.guiCommands, state);
        data.guiCommands = null;
        SharedBuffer.free(data.guiPoints, state);
        data.guiPoints = null;
        SharedBuffer.free(data.guiPointDetails, state);
        data.guiPointDetails = null;
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
        SharedBuffer.free(data.shadowUniforms, state);
        data.shadowUniforms = null;
        SharedBuffer.free(data.shadowModelMatrices, state);
        data.shadowModelMatrices = null;
        SharedBuffer.free(data.skyboxUniforms, state);
        data.skyboxUniforms = null;
        SharedBuffer.free(data.materials, state);
        data.materials = null;
        SharedBuffer.free(data.textures, state);
        data.textures = null;

        data.cascadeShadowSplits = null;

        cleanupIntermediaryTextures(state, data);
    }

    private void createGuiFont() {
        ImFontAtlas fontAtlas = ImGui.getIO().getFonts();
        ImInt width = new ImInt();
        ImInt height = new ImInt();
        ByteBuffer buf = fontAtlas.getTexDataAsRGBA32(width, height);
        imguiFont =
                GraphicsManager.getRenderInstance()
                        .getTextureLoader()
                        .load(buf, Format.R8G8B8A8_UINT, width.get(), height.get());
        fontAtlas.setTexID((int) imguiFont.id());

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
                                Format.R8G8B8A8_UINT,
                                FontAtlas.FONT_ATLAS_IMAGE_WIDTH,
                                FontAtlas.FONT_ATLAS_IMAGE_HEIGHT);
    }

    private GBuffer generateGBuffer(@NonNull VulkanState state, @NonNull VkExtent3D imageExtent) {
        TextureInfo[] textures = new TextureInfo[5];
        for (int i = 0; i < textures.length; i++) {
            textures[i] =
                    createTexture(
                            state,
                            imageExtent,
                            VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);
        }
        TextureInfo depth = createDepthTexture(state, imageExtent);

        return new GBuffer(textures, depth, imageExtent.width(), imageExtent.height());
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
        // TODO(ches) resize
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // TODO(ches) make the gBuffer like 2560 × 1440, just use viewport+scissor if smaller
            // than that
            VkExtent3D imageExtent = VkExtent3D.calloc(stack);
            imageExtent.set(width, height, 1);

            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                cleanupIntermediaryTextures(state, state.perFrameData[i]);
                createIntermediaryTextures(state, state.perFrameData[i], imageExtent);
            }
        }
    }
}
