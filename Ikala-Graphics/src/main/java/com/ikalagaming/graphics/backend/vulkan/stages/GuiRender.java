package com.ikalagaming.graphics.backend.vulkan.stages;

import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK12.*;
import static org.lwjgl.vulkan.VK12.VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.State;
import com.ikalagaming.graphics.backend.vulkan.*;
import com.ikalagaming.graphics.frontend.RenderConfig;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.WindowManager;
import com.ikalagaming.graphics.frontend.gui.data.DrawData;
import com.ikalagaming.graphics.frontend.gui.data.FontAtlas;
import com.ikalagaming.graphics.scene.Scene;

import imgui.*;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

@Slf4j
public class GuiRender implements RenderStage {

    /** VkDescriptorSet's for a frame, will be VK_NULL_HANDLE if not set up. */
    private static class Descriptors {
        /** The total number of descriptors to create, not how many actually get bound per frame. */
        public static final int COUNT = 5;

        public long uniforms = VK_NULL_HANDLE;
        public long commands = VK_NULL_HANDLE;
        public long points = VK_NULL_HANDLE;
        public long pointDetails = VK_NULL_HANDLE;
        public long textures = VK_NULL_HANDLE;

        /** Clear values so we don't refer to junk descriptor handles. */
        public void reset() {
            uniforms = VK_NULL_HANDLE;
            commands = VK_NULL_HANDLE;
            points = VK_NULL_HANDLE;
            pointDetails = VK_NULL_HANDLE;
            textures = VK_NULL_HANDLE;
        }
    }

    /** The scale of the GUI, kept here to prevent reallocation. */
    private final Vector2f scale;

    /** The GUI Mesh to use. */
    @Deprecated private final ImGuiMesh imGuiMesh;

    private final GuiMesh guiMesh;

    /** The shader to use for rendering ImGui. */
    @Deprecated @NonNull @Setter private ShaderVulkan imGuiShader;

    /** The shader to use for rendering. */
    @NonNull @Setter private ShaderVulkan shader;

    /** The font atlas texture. */
    private final Texture fontAtlas;

    /** VkDescriptorSetLayout pointer, will be VK_NULL_HANDLE if not set up. */
    @Deprecated private long descriptorSetLayoutLegacy;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayoutLegacy;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLegacy;

    /** VkDescriptorSetLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorSetLayout;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /** VkDescriptorPool pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorPool;

    /** All the descriptors, one per frame in flight. */
    private Descriptors[] descriptors;

    /**
     * Set up the GUI render stage.
     *
     * @param imGuiMesh The mesh information ImGui uses.
     */
    public GuiRender(
            final @NonNull ShaderVulkan imGuiShader,
            final @NonNull ShaderVulkan shader,
            final @NonNull ImGuiMesh imGuiMesh,
            final @NonNull GuiMesh guiMesh,
            final @NonNull Texture fontAtlas) {
        scale = new Vector2f();
        this.imGuiShader = imGuiShader;
        this.shader = shader;
        this.imGuiMesh = imGuiMesh;
        this.guiMesh = guiMesh;
        this.fontAtlas = fontAtlas;
        this.descriptorSetLayoutLegacy = VK_NULL_HANDLE;
        this.pipelineLayoutLegacy = VK_NULL_HANDLE;
        this.pipelineLegacy = VK_NULL_HANDLE;
        this.descriptorSetLayout = VK_NULL_HANDLE;
        this.pipelineLayout = VK_NULL_HANDLE;
        this.pipeline = VK_NULL_HANDLE;
        this.descriptorPool = VK_NULL_HANDLE;

        this.descriptors = new Descriptors[GraphicsManager.MAX_FRAMES_IN_FLIGHT];
        for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
            this.descriptors[i] = new Descriptors();
        }
    }

    @Override
    public void initialize(@NonNull State state) {
        VulkanState vulkanState = (VulkanState) state;
        log.debug("Initializing legacy gui render");
        createPipelineLayoutLegacy(vulkanState);
        createPipelineLegacy(vulkanState);
        log.debug("Initializing gui render");
        createPipelineLayout(vulkanState);
        createPipeline(vulkanState);
    }

    @Override
    public void cleanup(@NonNull State state) {
        VulkanState vulkanState = (VulkanState) state;
        for (Descriptors descriptorSet : this.descriptors) {
            descriptorSet.reset();
        }
        vkDestroyDescriptorPool(vulkanState.device.logical, descriptorPool, null);
        descriptorPool = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(vulkanState.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(vulkanState.device.logical, descriptorSetLayout, null);
        descriptorSetLayout = VK_NULL_HANDLE;

        vkDestroyPipeline(vulkanState.device.logical, pipelineLegacy, null);
        pipelineLegacy = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(vulkanState.device.logical, pipelineLayoutLegacy, null);
        pipelineLayoutLegacy = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(vulkanState.device.logical, descriptorSetLayoutLegacy, null);
        descriptorSetLayoutLegacy = VK_NULL_HANDLE;
    }

    @Override
    public void render(Scene scene, @NonNull Window window, State state, int renderConfig) {
        ImGuiIO io = ImGui.getIO();

        final int width = (int) io.getDisplaySizeX();
        final int height = (int) io.getDisplaySizeY();

        WindowManager windowManager = GraphicsManager.getWindowManager();
        if (windowManager == null) {
            return;
        }

        windowManager.drawGui(width, height);

        VulkanState vulkanState = (VulkanState) state;
        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];
        final TextureInfo targetImage =
                vulkanState.perFrameData[vulkanState.frameIndex].finalTexture;

        updateBindings(vulkanState);

        if (!RenderConfig.hasSceneStage(renderConfig)
                && !RenderConfig.hasSkyboxStage(renderConfig)) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkImageMemoryBarrier2.Buffer outputBarriers =
                        VkImageMemoryBarrier2.calloc(1, stack);
                outputBarriers
                        .get(0)
                        .sType$Default()
                        .srcStageMask(VK_PIPELINE_STAGE_2_TRANSFER_BIT)
                        .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .dstStageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                        .dstAccessMask(
                                VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                                        | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                        .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                        .newLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                        .image(targetImage.texture)
                        .subresourceRange(
                                VkImageSubresourceRange.calloc(stack)
                                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                        .levelCount(1)
                                        .layerCount(1));

                VkDependencyInfo barrierDependencyInfo =
                        VkDependencyInfo.calloc(stack)
                                .sType$Default()
                                .pImageMemoryBarriers(outputBarriers);
                vkCmdPipelineBarrier2(commandBuffer, barrierDependencyInfo);
            }
        }

        renderImGui(width, height, vulkanState, renderConfig);
        renderIkGui(width, height, vulkanState, renderConfig);
    }

    private void renderImGui(int width, int height, VulkanState vulkanState, int renderConfig) {
        // TODO(ches) render
        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];
        final PerFrameData frameData = vulkanState.perFrameData[vulkanState.frameIndex];
        final TextureInfo targetImage = frameData.finalTexture;
        try (MemoryStack stack = MemoryStack.stackPush()) {

            scale.x = 2.0f / width;
            scale.y = -2.0f / height;
            // TODO(ches) set scale

            int offset = ShaderBindings.GUI.UNIFORM_BUFFER_SCALE_OFFSET;
            ByteBuffer uniformData = stack.calloc(ShaderBindings.GUI.UNIFORMS_BUFFER_SIZE);
            uniformData.putFloat(offset, scale.x);
            offset += Float.BYTES;
            uniformData.putFloat(offset, scale.y);
            offset = ShaderBindings.GUI.UNIFORM_BUFFER_FONT_TEXTURE_OFFSET;
            // TODO(ches) figure out the texture offset
            uniformData.putFloat(offset, 0);

            vkCmdUpdateBuffer(commandBuffer, frameData.guiUniforms.buffer, 0, uniformData);

            ImDrawData drawData = ImGui.getDrawData();
            ImVec2 bufferScale = drawData.getFramebufferScale();
            ImVec2 displaySize = drawData.getDisplaySize();

            int framebufferHeight = (int) (displaySize.y * bufferScale.y);

            int commandListCount = drawData.getCmdListsCount();
            for (int i = 0; i < commandListCount; ++i) {
                // TODO(ches) opengl buffered stuff here

                int commandCount = drawData.getCmdListCmdBufferSize(i);
                for (int j = 0; j < commandCount; j++) {
                    final int elementCount = drawData.getCmdListCmdBufferElemCount(i, j);
                    final int indexBufferOffset = drawData.getCmdListCmdBufferIdxOffset(i, j);
                    final int indices = indexBufferOffset * ImDrawData.sizeOfImDrawIdx();

                    long id = drawData.getCmdListCmdBufferTextureId(i, j);

                    ImVec4 clipRect = drawData.getCmdListCmdBufferClipRect(i, j);
                    // TODO(ches) opengl bound and rendered stuff here
                }
            }
        }
    }

    private void renderIkGui(int width, int height, VulkanState vulkanState, int renderConfig) {
        // TODO(ches) render
        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];
        final TextureInfo targetImage =
                vulkanState.perFrameData[vulkanState.frameIndex].finalTexture;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (!RenderConfig.hasSceneStage(renderConfig)
                    && !RenderConfig.hasSkyboxStage(renderConfig)) {
                VkImageMemoryBarrier2.Buffer outputBarriers =
                        VkImageMemoryBarrier2.calloc(1, stack);
                outputBarriers
                        .get(0)
                        .sType$Default()
                        .srcStageMask(VK_PIPELINE_STAGE_2_TRANSFER_BIT)
                        .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .dstStageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                        .dstAccessMask(
                                VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                                        | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                        .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                        .newLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                        .image(targetImage.texture)
                        .subresourceRange(
                                VkImageSubresourceRange.calloc(stack)
                                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                        .levelCount(1)
                                        .layerCount(1));

                VkDependencyInfo barrierDependencyInfo =
                        VkDependencyInfo.calloc(stack)
                                .sType$Default()
                                .pImageMemoryBarriers(outputBarriers);
                vkCmdPipelineBarrier2(commandBuffer, barrierDependencyInfo);
            }
        }

        // TODO(ches) opengl buffered stuff here

        scale.x = 2.0f / width;
        scale.y = -2.0f / height;
        // TODO(ches) set scale

        if (!IkGui.getIO().fonts.stagedBitmaps.isEmpty()) {
            for (FontAtlas.StagedBitmap letter : IkGui.getIO().fonts.stagedBitmaps) {
                // TODO(ches) opengl rendered sub-images here

            }
            IkGui.getIO().fonts.stagedBitmaps.clear();
        }

        DrawData drawData = IkGui.getContext().drawData;
        int drawListCount = drawData.getDrawListCount();
        for (int i = 0; i < drawListCount; ++i) {
            int vertexCount = drawData.getDrawListVertexCount(i);
            // TODO(ches) opengl buffered and rendered stuff here

        }
    }

    private void createPipelineLayout(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                    .size(Long.BYTES);

            IntBuffer descriptorVariableFlags =
                    stack.ints(
                            /* Uniforms */
                            0,
                            /* Commands */
                            VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT,
                            /* Points */
                            VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT,
                            /* Point details */
                            VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT,
                            /* Textures */
                            VK_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT_BIT
                                    | VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT
                                    | VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT);

            VkDescriptorSetLayoutBindingFlagsCreateInfo descriptorSetBindingFlags =
                    VkDescriptorSetLayoutBindingFlagsCreateInfo.calloc(stack);
            descriptorSetBindingFlags
                    .sType$Default()
                    .bindingCount(5)
                    .pBindingFlags(descriptorVariableFlags);

            VkDescriptorSetLayoutBinding.Buffer descriptorSetLayoutBindings =
                    VkDescriptorSetLayoutBinding.calloc(5, stack);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.GUI.UNIFORMS_BINDING)
                    .binding(ShaderBindings.GUI.UNIFORMS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.GUI.COMMANDS_BINDING)
                    .binding(ShaderBindings.GUI.COMMANDS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.GUI.POINTS_BINDING)
                    .binding(ShaderBindings.GUI.POINTS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.GUI.POINT_DETAILS_BINDING)
                    .binding(ShaderBindings.GUI.POINT_DETAILS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.GUI.TEXTURES_BINDING)
                    .binding(ShaderBindings.GUI.TEXTURES_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(state.device.physical.maxBindlessImages)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            VkDescriptorSetLayoutCreateInfo descriptorSetLayoutCreateInfo =
                    VkDescriptorSetLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pNext(descriptorSetBindingFlags)
                            .pBindings(descriptorSetLayoutBindings)
                            .flags(VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT);

            checkError(
                    vkCreateDescriptorSetLayout(
                            state.device.logical, descriptorSetLayoutCreateInfo, null, longOutput));
            descriptorSetLayout = longOutput.get(0);

            LongBuffer descriptorSetLayoutAddress = stack.longs(descriptorSetLayout);

            VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack);
            pipelineLayoutCreateInfo
                    .sType$Default()
                    .setLayoutCount(1)
                    .pSetLayouts(descriptorSetLayoutAddress)
                    .pPushConstantRanges(pushConstantRanges);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical, pipelineLayoutCreateInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(3, stack);
            poolSizes
                    .get(0)
                    .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(GraphicsManager.MAX_FRAMES_IN_FLIGHT);
            poolSizes
                    .get(1)
                    .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(3 * GraphicsManager.MAX_FRAMES_IN_FLIGHT);
            poolSizes
                    .get(2)
                    .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(GraphicsManager.MAX_FRAMES_IN_FLIGHT);

            VkDescriptorPoolCreateInfo descriptorPoolCreateInfo =
                    VkDescriptorPoolCreateInfo.calloc(stack)
                            .sType$Default()
                            .maxSets(GraphicsManager.MAX_FRAMES_IN_FLIGHT * Descriptors.COUNT)
                            .flags(VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT)
                            .pPoolSizes(poolSizes);

            checkError(
                    vkCreateDescriptorPool(
                            state.device.logical, descriptorPoolCreateInfo, null, longOutput));
            descriptorPool = longOutput.get(0);

            final int UPDATE_COUNT = Descriptors.COUNT - 1;

            LongBuffer descriptorSetLayoutAddresses =
                    stack.callocLong(GraphicsManager.MAX_FRAMES_IN_FLIGHT * UPDATE_COUNT);
            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT * UPDATE_COUNT; i++) {
                descriptorSetLayoutAddresses.put(i, descriptorSetLayoutLegacy);
            }

            VkDescriptorSetAllocateInfo descriptorSetAlloc =
                    VkDescriptorSetAllocateInfo.calloc(stack)
                            .sType$Default()
                            .pNext(VK_NULL_HANDLE)
                            .descriptorPool(descriptorPool)
                            .pSetLayouts(descriptorSetLayoutAddresses);
            LongBuffer setAddresses =
                    stack.callocLong(GraphicsManager.MAX_FRAMES_IN_FLIGHT * UPDATE_COUNT);
            checkError(
                    vkAllocateDescriptorSets(
                            state.device.logical, descriptorSetAlloc, setAddresses));
            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                descriptors[i].uniforms =
                        setAddresses.get(i * UPDATE_COUNT + ShaderBindings.GUI.UNIFORMS_BINDING);
                descriptors[i].commands =
                        setAddresses.get(i * UPDATE_COUNT + ShaderBindings.GUI.COMMANDS_BINDING);
                descriptors[i].points =
                        setAddresses.get(i * UPDATE_COUNT + ShaderBindings.GUI.POINTS_BINDING);
                descriptors[i].pointDetails =
                        setAddresses.get(
                                i * UPDATE_COUNT + ShaderBindings.GUI.POINT_DETAILS_BINDING);
            }

            VkWriteDescriptorSet.Buffer writeDescriptorSets =
                    VkWriteDescriptorSet.calloc(GraphicsManager.MAX_FRAMES_IN_FLIGHT, stack);
            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                VkDescriptorBufferInfo.Buffer uniformBufferInfo =
                        VkDescriptorBufferInfo.calloc(1, stack);
                uniformBufferInfo
                        .get(0)
                        .buffer(state.perFrameData[i].guiUniforms.buffer)
                        .offset(0)
                        .range(VK_WHOLE_SIZE);
                writeDescriptorSets
                        .get(i)
                        .sType$Default()
                        .dstSet(descriptors[i].uniforms)
                        .dstBinding(ShaderBindings.GUI.UNIFORMS_BINDING)
                        .pBufferInfo(uniformBufferInfo)
                        .descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            }
            vkUpdateDescriptorSets(state.device.logical, writeDescriptorSets, null);
        }
    }

    private void createPipelineLayoutLegacy(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                    .size(Long.BYTES);

            IntBuffer descriptorVariableFlags =
                    stack.ints(
                            /* Uniforms */
                            0,
                            /* Textures */
                            VK_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT_BIT
                                    | VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT
                                    | VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT);

            VkDescriptorSetLayoutBindingFlagsCreateInfo descriptorSetBindingFlags =
                    VkDescriptorSetLayoutBindingFlagsCreateInfo.calloc(stack);
            descriptorSetBindingFlags
                    .sType$Default()
                    .bindingCount(2)
                    .pBindingFlags(descriptorVariableFlags);

            VkDescriptorSetLayoutBinding.Buffer descriptorSetLayoutBindings =
                    VkDescriptorSetLayoutBinding.calloc(2, stack);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.GUI.UNIFORMS_BINDING)
                    .binding(ShaderBindings.GUI.UNIFORMS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.GUI.TEXTURES_BINDING_LEGACY)
                    .binding(ShaderBindings.GUI.TEXTURES_BINDING_LEGACY)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(state.device.physical.maxBindlessImages)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            VkDescriptorSetLayoutCreateInfo descriptorSetLayoutCreateInfo =
                    VkDescriptorSetLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pNext(descriptorSetBindingFlags)
                            .pBindings(descriptorSetLayoutBindings)
                            .flags(VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT);

            checkError(
                    vkCreateDescriptorSetLayout(
                            state.device.logical, descriptorSetLayoutCreateInfo, null, longOutput));
            descriptorSetLayoutLegacy = longOutput.get(0);

            LongBuffer descriptorSetLayoutAddress = stack.longs(descriptorSetLayoutLegacy);

            VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack);
            pipelineLayoutCreateInfo
                    .sType$Default()
                    .setLayoutCount(1)
                    .pSetLayouts(descriptorSetLayoutAddress)
                    .pPushConstantRanges(pushConstantRanges);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical, pipelineLayoutCreateInfo, null, longOutput));
            pipelineLayoutLegacy = longOutput.get(0);
        }
    }

    private void createPipeline(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkVertexInputAttributeDescription.Buffer vertexAttributes =
                    VkVertexInputAttributeDescription.calloc(1, stack);

            int offset = 0;
            // Positions
            vertexAttributes
                    .get(0)
                    .binding(0)
                    .location(0)
                    .format(VK_FORMAT_R32G32_SFLOAT)
                    .offset(offset);
            offset += 2 * Float.BYTES;

            VkVertexInputBindingDescription.Buffer vertexBindings =
                    VkVertexInputBindingDescription.calloc(1, stack);
            vertexBindings.get(0).binding(0).stride(offset).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

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

            IntBuffer dynamicStates =
                    stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);
            VkPipelineDynamicStateCreateInfo dynamicState =
                    VkPipelineDynamicStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pDynamicStates(dynamicStates);

            VkPipelineDepthStencilStateCreateInfo depthStencilState =
                    VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .depthTestEnable(true)
                            .depthWriteEnable(true)
                            .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);

            IntBuffer imageFormat = stack.ints(VK_FORMAT_R8G8B8A8_SRGB);

            VkPipelineRenderingCreateInfo renderingCreateInfo =
                    VkPipelineRenderingCreateInfo.calloc(stack)
                            .sType$Default()
                            .colorAttachmentCount(1)
                            .pColorAttachmentFormats(imageFormat)
                            .depthAttachmentFormat(state.device.physical.depthFormat);

            VkPipelineColorBlendAttachmentState.Buffer blendAttachments =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack);
            blendAttachments
                    .get(0)
                    .colorWriteMask(
                            VK_COLOR_COMPONENT_R_BIT
                                    | VK_COLOR_COMPONENT_G_BIT
                                    | VK_COLOR_COMPONENT_B_BIT
                                    | VK_COLOR_COMPONENT_A_BIT);
            VkPipelineColorBlendStateCreateInfo colorBlendState =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .attachmentCount(1)
                            .pAttachments(blendAttachments);
            VkPipelineRasterizationStateCreateInfo rasterizationState =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType$Default()
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

            pipeline = longOutput.get(0);
        }
    }

    private void createPipelineLegacy(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkVertexInputAttributeDescription.Buffer vertexAttributes =
                    VkVertexInputAttributeDescription.calloc(3, stack);

            int offset = 0;
            // Positions
            vertexAttributes
                    .get(0)
                    .binding(0)
                    .location(0)
                    .format(VK_FORMAT_R32G32_SFLOAT)
                    .offset(offset);
            offset += 2 * Float.BYTES;
            // Texture Coordinates
            vertexAttributes
                    .get(1)
                    .binding(0)
                    .location(1)
                    .format(VK_FORMAT_R32G32_SFLOAT)
                    .offset(offset);
            offset += 2 * Float.BYTES;
            // Color
            vertexAttributes
                    .get(2)
                    .binding(0)
                    .location(2)
                    .format(VK_FORMAT_R32G32B32A32_SFLOAT)
                    .offset(offset);
            offset += 4 * Float.BYTES;

            VkVertexInputBindingDescription.Buffer vertexBindings =
                    VkVertexInputBindingDescription.calloc(1, stack);
            vertexBindings.get(0).binding(0).stride(offset).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

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

            IntBuffer dynamicStates =
                    stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);
            VkPipelineDynamicStateCreateInfo dynamicState =
                    VkPipelineDynamicStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pDynamicStates(dynamicStates);

            VkPipelineDepthStencilStateCreateInfo depthStencilState =
                    VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .depthTestEnable(true)
                            .depthWriteEnable(true)
                            .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);

            IntBuffer imageFormat = stack.ints(VK_FORMAT_R8G8B8A8_SRGB);

            VkPipelineRenderingCreateInfo renderingCreateInfo =
                    VkPipelineRenderingCreateInfo.calloc(stack)
                            .sType$Default()
                            .colorAttachmentCount(1)
                            .pColorAttachmentFormats(imageFormat)
                            .depthAttachmentFormat(state.device.physical.depthFormat);

            VkPipelineColorBlendAttachmentState.Buffer blendAttachments =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack);
            blendAttachments
                    .get(0)
                    .colorWriteMask(
                            VK_COLOR_COMPONENT_R_BIT
                                    | VK_COLOR_COMPONENT_G_BIT
                                    | VK_COLOR_COMPONENT_B_BIT
                                    | VK_COLOR_COMPONENT_A_BIT);
            VkPipelineColorBlendStateCreateInfo colorBlendState =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .attachmentCount(1)
                            .pAttachments(blendAttachments);
            VkPipelineRasterizationStateCreateInfo rasterizationState =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType$Default()
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
                    .stageCount(imGuiShader.shaderModules.length)
                    .pStages(imGuiShader.shaderStages)
                    .pVertexInputState(vertexInputStateCreateInfo)
                    .pInputAssemblyState(inputAssemblyState)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizationState)
                    .pMultisampleState(multisampleState)
                    .pDepthStencilState(depthStencilState)
                    .pColorBlendState(colorBlendState)
                    .pDynamicState(dynamicState)
                    .layout(pipelineLayoutLegacy)
                    .renderPass(VK_NULL_HANDLE);

            checkError(
                    vkCreateGraphicsPipelines(
                            state.device.logical,
                            VK_NULL_HANDLE,
                            pipelineCreateInfos,
                            null,
                            longOutput));

            pipelineLegacy = longOutput.get(0);
        }
    }

    private void updateBindings(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PerFrameData frameData = state.perFrameData[state.frameIndex];

            int updateCount = 0;
            if (frameData.guiCommands.updated && frameData.guiCommands.buffer != VK_NULL_HANDLE) {
                updateCount += 1;
            }
            if (frameData.guiPoints.updated && frameData.guiPoints.buffer != VK_NULL_HANDLE) {
                updateCount += 1;
            }
            if (frameData.guiPointDetails.updated
                    && frameData.guiPointDetails.buffer != VK_NULL_HANDLE) {
                updateCount += 1;
            }
            if (updateCount == 0) {
                return;
            }
            final Descriptors currentDescriptors = descriptors[state.frameIndex];

            VkWriteDescriptorSet.Buffer writeDescriptorSets =
                    VkWriteDescriptorSet.calloc(updateCount, stack);

            int index = 0;
            if (frameData.guiCommands.updated && frameData.guiCommands.buffer != VK_NULL_HANDLE) {
                VkDescriptorBufferInfo.Buffer commandsBufferInfo =
                        VkDescriptorBufferInfo.calloc(1, stack);
                commandsBufferInfo
                        .get(0)
                        .buffer(frameData.guiCommands.buffer)
                        .offset(0)
                        .range(VK_WHOLE_SIZE);
                writeDescriptorSets
                        .get(index)
                        .sType$Default()
                        .dstSet(currentDescriptors.commands)
                        .dstBinding(ShaderBindings.GUI.COMMANDS_BINDING)
                        .pBufferInfo(commandsBufferInfo)
                        .descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
                index += 1;
                frameData.guiCommands.updated = false;
            }
            if (frameData.guiPoints.updated && frameData.guiPoints.buffer != VK_NULL_HANDLE) {
                VkDescriptorBufferInfo.Buffer pointsBufferInfo =
                        VkDescriptorBufferInfo.calloc(1, stack);
                pointsBufferInfo
                        .get(0)
                        .buffer(frameData.guiPoints.buffer)
                        .offset(0)
                        .range(VK_WHOLE_SIZE);
                writeDescriptorSets
                        .get(index)
                        .sType$Default()
                        .dstSet(currentDescriptors.points)
                        .dstBinding(ShaderBindings.GUI.POINTS_BINDING)
                        .pBufferInfo(pointsBufferInfo)
                        .descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
                index += 1;
                frameData.guiPoints.updated = false;
            }
            if (frameData.guiPointDetails.updated
                    && frameData.guiPointDetails.buffer != VK_NULL_HANDLE) {
                VkDescriptorBufferInfo.Buffer pointDetailsBuffer =
                        VkDescriptorBufferInfo.calloc(1, stack);
                pointDetailsBuffer
                        .get(0)
                        .buffer(frameData.guiPointDetails.buffer)
                        .offset(0)
                        .range(VK_WHOLE_SIZE);
                writeDescriptorSets
                        .get(index)
                        .sType$Default()
                        .dstSet(currentDescriptors.pointDetails)
                        .dstBinding(ShaderBindings.GUI.POINT_DETAILS_BINDING)
                        .pBufferInfo(pointDetailsBuffer)
                        .descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
                frameData.guiPointDetails.updated = false;
            }
            vkUpdateDescriptorSets(state.device.logical, writeDescriptorSets, null);
        }
    }
}
