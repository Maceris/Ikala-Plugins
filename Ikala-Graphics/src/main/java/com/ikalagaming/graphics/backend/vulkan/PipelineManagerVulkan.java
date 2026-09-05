package com.ikalagaming.graphics.backend.vulkan;

import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
import static org.lwjgl.vulkan.VK12.vkGetBufferDeviceAddress;

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
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.*;

@Slf4j
public class PipelineManagerVulkan {

    /** The size of a 4x4 model matrix ({@value}). */
    public static final int MODEL_MATRIX_SIZE = 4 * 4;

    /** Fallback pipeline that does nothing. */
    private static final Pipeline ERROR_PIPELINE =
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
        createLightBuffers();
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
        VulkanState.WindowInfo windowInfo = state.windows.get(window);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSurfaceCapabilitiesKHR surfaceCapabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            checkError(
                    vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                            state.device.physical.physicalDevice,
                            windowInfo.surfaceHandle,
                            surfaceCapabilities));
            VkExtent3D imageExtent = VkExtent3D.calloc(stack);

            if (surfaceCapabilities.currentExtent().width() == 0xFFFF_FFFF) {
                imageExtent.set(window.getWidth(), window.getHeight(), 1);
            } else {
                imageExtent.set(
                        surfaceCapabilities.currentExtent().width(),
                        surfaceCapabilities.currentExtent().height(),
                        1);
            }

            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                state.perFrameData[i] = new PerFrameData();

                final long DYNAMIC = 0;
                state.perFrameData[i].animationData = createSharedBuffer(DYNAMIC, state);
                state.perFrameData[i].animationOffsets = createSharedBuffer(DYNAMIC, state);
                state.perFrameData[i].animationModelData = createSharedBuffer(DYNAMIC, state);
                state.perFrameData[i].animationBoneWeight = createSharedBuffer(DYNAMIC, state);
                state.perFrameData[i].animationTarget = createSharedBuffer(DYNAMIC, state);
                state.perFrameData[i].guiUniforms =
                        createSharedBuffer(ShaderBindings.GUI.UNIFORMS_BUFFER_SIZE, state);
                state.perFrameData[i].guiCommands = createSharedBuffer(DYNAMIC, state);
                state.perFrameData[i].guiPoints = createSharedBuffer(DYNAMIC, state);
                state.perFrameData[i].guiPointDetails = createSharedBuffer(DYNAMIC, state);
                state.perFrameData[i].lightUniforms =
                        createSharedBuffer(ShaderBindings.Light.UNIFORMS_BUFFER_SIZE, state);
                state.perFrameData[i].lightPointLights = createSharedBuffer(DYNAMIC, state);
                state.perFrameData[i].lightSpotLights = createSharedBuffer(DYNAMIC, state);
                state.perFrameData[i].lightMaterials = createSharedBuffer(DYNAMIC, state);
                state.perFrameData[i].sceneUniforms =
                        createSharedBuffer(ShaderBindings.Scene.UNIFORMS_BUFFER_SIZE, state);
                state.perFrameData[i].sceneModelMatrices = createSharedBuffer(DYNAMIC, state);
                state.perFrameData[i].sceneMaterials = createSharedBuffer(DYNAMIC, state);
                state.perFrameData[i].sceneMaterialOverrides = createSharedBuffer(DYNAMIC, state);
                state.perFrameData[i].shadowUniforms =
                        createSharedBuffer(ShaderBindings.Shadow.UNIFORMS_BUFFER_SIZE, state);
                state.perFrameData[i].shadowModelMatrices = createSharedBuffer(DYNAMIC, state);
                state.perFrameData[i].skyboxUniforms =
                        createSharedBuffer(ShaderBindings.Skybox.UNIFORMS_BUFFER_SIZE, state);
                state.perFrameData[i].cascadeShadowSplits =
                        new CascadeShadowSplit[CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT];
                state.perFrameData[i].cascadeShadows =
                        new TextureInfo[CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT];
                for (int shadow = 0;
                        shadow < CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT;
                        shadow++) {
                    state.perFrameData[i].cascadeShadowSplits[shadow] = new CascadeShadowSplit();
                    state.perFrameData[i].cascadeShadows[shadow] =
                            createDepthTexture(state, imageExtent);
                }
                state.perFrameData[i].gBuffer = generateGBuffer(state, imageExtent);
                state.perFrameData[i].sceneTexture = createTexture(state, imageExtent);
            }
        }
    }

    /**
     * Create a shared buffer with the given size.
     *
     * @param bufferSize The size of the buffer in bytes.
     * @return The new buffer object.
     */
    private SharedBuffer createSharedBuffer(long bufferSize, @NonNull VulkanState state) {
        if (bufferSize <= 0) {
            return new SharedBuffer();
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferCreateInfo =
                    VkBufferCreateInfo.calloc(stack)
                            .sType$Default()
                            .size(bufferSize)
                            .usage(VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT);
            VmaAllocationCreateInfo bufferAllocationCreateInfo =
                    VmaAllocationCreateInfo.calloc(stack)
                            .flags(
                                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT
                                            | VMA_ALLOCATION_CREATE_HOST_ACCESS_ALLOW_TRANSFER_INSTEAD_BIT
                                            | VMA_ALLOCATION_CREATE_MAPPED_BIT)
                            .usage(VMA_MEMORY_USAGE_AUTO);
            VkBufferDeviceAddressInfo bufferDeviceAddressInfo =
                    VkBufferDeviceAddressInfo.calloc(stack).sType$Default();

            SharedBuffer result = new SharedBuffer();

            checkError(
                    vmaCreateBuffer(
                            state.vmaAllocator,
                            bufferCreateInfo,
                            bufferAllocationCreateInfo,
                            longOutput,
                            pointerOutput,
                            result.allocationInfo));
            result.buffer = longOutput.get(0);
            result.allocation = pointerOutput.get(0);

            bufferDeviceAddressInfo.buffer(result.buffer);
            result.deviceAddress =
                    vkGetBufferDeviceAddress(state.device.logical, bufferDeviceAddressInfo);
            return result;
        }
    }

    private TextureInfo createTexture(@NonNull VulkanState state, @NonNull VkExtent3D imageExtent) {
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
                            .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
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
        stageAnimationRender.cleanup(state);
        stageFilterRender.cleanup(state);
        stageGuiRender.cleanup(state);
        stageLightRender.cleanup(state);
        stageModelMatrixUpdate.cleanup(state);
        stageSceneRender.cleanup(state);
        stageShadowRender.cleanup(state);
        stageSkyboxRender.cleanup(state);
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

    /** Initialize the lighting SSBOs and fill them with zeroes. */
    private void createLightBuffers() {
        int pointLightBuffer = 0;
        // TODO(ches) create buffer

        /*
         * Position (vec3 + ignored), color (vec3), intensity (1), Attenuation
         * (3 + ignored), in that order.
         */
        final int POINT_LIGHT_SIZE = 4 + 3 + 1 + 4;
        FloatBuffer pointLightFloatBuffer =
                MemoryUtil.memAllocFloat(PipelineVulkan.MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE);

        pointLightFloatBuffer
                .put(new float[PipelineVulkan.MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE])
                .flip();

        // TODO(ches) buffer data

        MemoryUtil.memFree(pointLightFloatBuffer);

        int spotLightBuffer = 0;
        // TODO(ches) create buffer

        /*
         * Position (vec3 + ignored), color (vec3), intensity (1), Attenuation
         * (3 + ignored), cone direction (vec3), cutoff (1) in that order.
         */
        final int SPOT_LIGHT_SIZE = 4 + 3 + 1 + 4 + 3 + 1;
        FloatBuffer spotLightFloatBuffer =
                MemoryUtil.memAllocFloat(PipelineVulkan.MAX_LIGHTS_SUPPORTED * SPOT_LIGHT_SIZE);

        spotLightFloatBuffer
                .put(new float[PipelineVulkan.MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE])
                .flip();

        // TODO(ches) buffer data

        MemoryUtil.memFree(spotLightFloatBuffer);
    }

    private Framebuffer createShadowBuffers() {
        int depthMapFBO = 0;
        // TODO(ches) create buffer

        int[] shadowTextures = new int[CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT];

        // TODO(ches) create textures

        for (int i = 0; i < CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT; ++i) {
            // TODO(ches) create all the textures
        }

        long[] textureIds = Arrays.stream(shadowTextures).mapToLong(i -> (long) i).toArray();
        return new Framebuffer(
                depthMapFBO,
                CascadeShadowSplit.SHADOW_MAP_WIDTH,
                CascadeShadowSplit.SHADOW_MAP_HEIGHT,
                textureIds);
    }

    private GBuffer generateGBuffer(@NonNull VulkanState state, @NonNull VkExtent3D imageExtent) {
        TextureInfo[] textures = new TextureInfo[5];
        for (int i = 0; i < textures.length; i++) {
            textures[i] = createTexture(state, imageExtent);
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
    public void resize(final int width, final int height) {
        // TODO(ches) resize
    }
}
