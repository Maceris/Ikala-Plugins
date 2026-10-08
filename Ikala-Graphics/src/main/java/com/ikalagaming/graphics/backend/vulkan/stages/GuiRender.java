package com.ikalagaming.graphics.backend.vulkan.stages;

import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.State;
import com.ikalagaming.graphics.backend.vulkan.*;
import com.ikalagaming.graphics.frontend.RenderConfig;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.frontend.TextureInfo;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.WindowManager;
import com.ikalagaming.graphics.frontend.gui.data.DrawData;
import com.ikalagaming.graphics.frontend.gui.data.FontAtlas;
import com.ikalagaming.graphics.frontend.gui.data.IkIO;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * Renders IkGui on top of the final image. Every draw list is packed into the same per-frame
 * buffers, and push constants tell the shader where the current draw list starts.
 */
@Slf4j
public class GuiRender implements RenderStage {

    /** The number of descriptors in the set for each frame. */
    private static final int DESCRIPTOR_COUNT = 5;

    /** Where a draw list starts in each of the packed buffers. */
    private record DrawListOffsets(long vertexBytes, int commands, int points, int details) {}

    /** The shader to use for rendering. */
    @NonNull @Setter private ShaderVulkan shader;

    /** The font atlas texture. */
    private final Texture fontAtlas;

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
     * Set up the GUI render stage.
     *
     * @param shader The shader to render the GUI with.
     * @param fontAtlas The font atlas texture.
     */
    public GuiRender(final @NonNull ShaderVulkan shader, final @NonNull Texture fontAtlas) {
        this.shader = shader;
        this.fontAtlas = fontAtlas;
        this.descriptorSetLayout = VK_NULL_HANDLE;
        this.pipelineLayout = VK_NULL_HANDLE;
        this.pipeline = VK_NULL_HANDLE;
        this.descriptorPool = VK_NULL_HANDLE;

        this.descriptorSets = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];
    }

    @Override
    public void initialize(@NonNull State state) {
        VulkanState vulkanState = (VulkanState) state;
        log.debug("Initializing gui render");
        createPipelineLayout(vulkanState);
        createPipeline(vulkanState);
    }

    @Override
    public void cleanup(@NonNull State state) {
        VulkanState vulkanState = (VulkanState) state;
        // Freed along with the pool
        Arrays.fill(descriptorSets, VK_NULL_HANDLE);
        vkDestroyDescriptorPool(vulkanState.device.logical, descriptorPool, null);
        descriptorPool = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(vulkanState.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(vulkanState.device.logical, descriptorSetLayout, null);
        descriptorSetLayout = VK_NULL_HANDLE;
    }

    @Override
    public void render(Scene scene, @NonNull Window window, State state, int renderConfig) {
        final IkIO io = IkGui.getIO();

        final int width = (int) io.displaySize.x;
        final int height = (int) io.displaySize.y;

        WindowManager windowManager = GraphicsManager.getWindowManager();
        if (windowManager == null) {
            return;
        }

        windowManager.drawGui(width, height);

        VulkanState vulkanState = (VulkanState) state;
        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];
        final PerFrameData frameData = vulkanState.perFrameData[vulkanState.frameIndex];

        // Transfers have to happen outside of rendering
        uploadFontGlyphs(commandBuffer, vulkanState, frameData);

        final DrawData drawData = IkGui.getDrawData();
        DrawListOffsets[] offsets = new DrawListOffsets[0];
        if (drawData != null) {
            updateUniforms(width, height, drawData, frameData);
            offsets = uploadDrawLists(drawData, vulkanState, frameData);
        } else {
            ensureBuffersExist(vulkanState, frameData);
        }
        updateBindings(vulkanState, frameData);

        // Earlier stages leave the final image as a color attachment, otherwise we start it here
        final boolean firstToRender =
                !RenderConfig.hasSceneStage(renderConfig)
                        && !RenderConfig.hasSkyboxStage(renderConfig)
                        && !RenderConfig.hasFilterStage(renderConfig);

        // The image can be bigger than the window, but not smaller
        final int renderWidth = Math.min(width, vulkanState.realSize.width());
        final int renderHeight = Math.min(height, vulkanState.realSize.height());

        try (MemoryStack stack = MemoryStack.stackPush()) {
            transitionFinalImage(commandBuffer, frameData, firstToRender, stack);

            VkRenderingAttachmentInfo.Buffer colorAttachments =
                    VkRenderingAttachmentInfo.calloc(1, stack);
            colorAttachments
                    .get(0)
                    .sType$Default()
                    .imageView(frameData.finalTexture.view)
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(
                            firstToRender
                                    ? VK_ATTACHMENT_LOAD_OP_CLEAR
                                    : VK_ATTACHMENT_LOAD_OP_LOAD)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            // The clear value is already zeroed, which matches the OpenGL default clear color

            VkRenderingInfo renderingInfo =
                    VkRenderingInfo.calloc(stack)
                            .sType$Default()
                            .renderArea(area -> area.extent().set(renderWidth, renderHeight))
                            .layerCount(1)
                            .pColorAttachments(colorAttachments);
            vkCmdBeginRendering(commandBuffer, renderingInfo);

            if (offsets.length > 0 && renderWidth > 0 && renderHeight > 0) {
                drawLists(commandBuffer, vulkanState, drawData, offsets, renderWidth, renderHeight);
            }

            vkCmdEndRendering(commandBuffer);
        }
    }

    /**
     * Move the final image to be a color attachment we can render to. If we are the first to render
     * to it this frame, the old contents are discarded since we clear it anyway.
     *
     * @param commandBuffer The command buffer to record into.
     * @param frameData The data for the current frame.
     * @param firstToRender Whether no earlier stage rendered to the final image this frame.
     * @param stack The stack to allocate on.
     */
    private void transitionFinalImage(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull PerFrameData frameData,
            boolean firstToRender,
            @NonNull MemoryStack stack) {
        VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(1, stack);
        barriers.get(0)
                .sType$Default()
                .dstStageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstAccessMask(
                        VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT
                                | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT)
                .newLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(frameData.finalTexture.texture)
                .subresourceRange(
                        range ->
                                range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                        .levelCount(1)
                                        .layerCount(1));
        if (firstToRender) {
            // Last used as the source of the blit to the swapchain
            barriers.get(0)
                    .srcStageMask(VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT)
                    .srcAccessMask(VK_ACCESS_2_NONE)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED);
        } else {
            // An earlier stage rendered to it this frame
            barriers.get(0)
                    .srcStageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        }
        VkDependencyInfo dependencyInfo =
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers);
        vkCmdPipelineBarrier2(commandBuffer, dependencyInfo);
    }

    /**
     * Copy any glyphs that were added to the font atlas since last frame into the atlas texture.
     *
     * @param commandBuffer The command buffer to record into.
     * @param state The Vulkan state.
     * @param frameData The data for the current frame.
     */
    private void uploadFontGlyphs(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            @NonNull PerFrameData frameData) {
        List<FontAtlas.StagedBitmap> letters = IkGui.getIO().fonts.stagedBitmaps;
        if (letters.isEmpty()) {
            return;
        }

        // Offsets into the staging buffer must be a multiple of the 4 byte texel size
        long totalSize = 0;
        for (FontAtlas.StagedBitmap letter : letters) {
            totalSize += SharedBuffer.alignWithoutPadding(letter.data().remaining(), 4);
        }
        frameData.guiFontStaging.ensureCapacity(totalSize, state);

        final var atlas = (TextureInfoVulkan) fontAtlas.info();
        final long staging = frameData.guiFontStaging.allocationInfo.pMappedData();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferImageCopy.Buffer regions = VkBufferImageCopy.calloc(letters.size(), stack);
            long offset = 0;
            for (int i = 0; i < letters.size(); ++i) {
                FontAtlas.StagedBitmap letter = letters.get(i);
                final int size = letter.data().remaining();
                MemoryUtil.memCopy(MemoryUtil.memAddress(letter.data()), staging + offset, size);
                regions.get(i)
                        .bufferOffset(offset)
                        .imageSubresource(
                                layers ->
                                        layers.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1))
                        .imageOffset(o -> o.set(letter.x(), letter.y(), 0))
                        .imageExtent(e -> e.set(letter.width(), letter.height(), 1));
                offset += SharedBuffer.alignWithoutPadding(size, 4);
            }

            VkImageMemoryBarrier2.Buffer barrier = VkImageMemoryBarrier2.calloc(1, stack);
            // Wait for earlier frames to finish sampling the atlas before writing to it
            barrier.get(0)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT)
                    .srcAccessMask(VK_ACCESS_2_SHADER_SAMPLED_READ_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_COPY_BIT)
                    .dstAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(atlas.texture)
                    .subresourceRange(
                            range ->
                                    range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                            .levelCount(1)
                                            .layerCount(1));
            VkDependencyInfo dependencyInfo =
                    VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barrier);
            vkCmdPipelineBarrier2(commandBuffer, dependencyInfo);

            vkCmdCopyBufferToImage(
                    commandBuffer,
                    frameData.guiFontStaging.buffer,
                    atlas.texture,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    regions);

            barrier.get(0)
                    .srcStageMask(VK_PIPELINE_STAGE_2_COPY_BIT)
                    .srcAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT)
                    .dstAccessMask(VK_ACCESS_2_SHADER_SAMPLED_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL);
            vkCmdPipelineBarrier2(commandBuffer, dependencyInfo);
        }

        letters.clear();
    }

    /**
     * Write the uniforms for the current frame. The buffer is host coherent and this frame's buffer
     * isn't in use by the GPU, so we can write it directly.
     *
     * @param width The width of the display in pixels.
     * @param height The height of the display in pixels.
     * @param drawData The draw data for the frame.
     * @param frameData The data for the current frame.
     */
    private void updateUniforms(
            int width, int height, @NonNull DrawData drawData, @NonNull PerFrameData frameData) {
        ByteBuffer uniformData =
                MemoryUtil.memByteBuffer(
                        frameData.guiUniforms.allocationInfo.pMappedData(),
                        ShaderBindings.GUI.UNIFORMS_BUFFER_SIZE);
        // Vulkan NDC has y pointing down like our pixel coordinates, unlike OpenGL
        uniformData.putFloat(ShaderBindings.GUI.UNIFORM_BUFFER_SCALE_OFFSET, 2.0f / width);
        uniformData.putFloat(
                ShaderBindings.GUI.UNIFORM_BUFFER_SCALE_OFFSET + Float.BYTES, 2.0f / height);
        uniformData.putInt(
                ShaderBindings.GUI.UNIFORM_BUFFER_FONT_TEXTURE_OFFSET,
                ((TextureInfoVulkan) fontAtlas.info()).bindlessIndex);
        uniformData.putFloat(
                ShaderBindings.GUI.UNIFORM_BUFFER_DISPLAY_POSITION_OFFSET,
                drawData.displayPosition.x);
        uniformData.putFloat(
                ShaderBindings.GUI.UNIFORM_BUFFER_DISPLAY_POSITION_OFFSET + Float.BYTES,
                drawData.displayPosition.y);
    }

    /**
     * Make sure every GUI buffer exists, so the descriptors are always valid.
     *
     * @param state The Vulkan state.
     * @param frameData The data for the current frame.
     */
    private void ensureBuffersExist(@NonNull VulkanState state, @NonNull PerFrameData frameData) {
        frameData.guiVertices.ensureCapacity(0, state);
        frameData.guiCommands.ensureCapacity(0, state);
        frameData.guiPoints.ensureCapacity(0, state);
        frameData.guiPointDetails.ensureCapacity(0, state);
        frameData.guiTextureIndices.ensureCapacity(0, state);
    }

    /**
     * Copy every draw list into the packed buffers, along with the texture slot mapping.
     *
     * @param drawData The draw data for the frame.
     * @param state The Vulkan state.
     * @param frameData The data for the current frame.
     * @return Where each draw list starts in the buffers.
     */
    private DrawListOffsets[] uploadDrawLists(
            @NonNull DrawData drawData,
            @NonNull VulkanState state,
            @NonNull PerFrameData frameData) {
        final int drawListCount = drawData.getDrawListCount();

        long vertexBytes = 0;
        long commandBytes = 0;
        long pointBytes = 0;
        long detailBytes = 0;
        for (int i = 0; i < drawListCount; ++i) {
            vertexBytes += remaining(drawData.getDrawListVertexBuffer(i));
            commandBytes += remaining(drawData.getDrawListCommandBuffer(i));
            pointBytes += remaining(drawData.getDrawListPointBuffer(i));
            detailBytes += remaining(drawData.getDrawListPointDetailBuffer(i));
        }
        // Always map at least one texture, the font atlas is the fallback like in OpenGL
        final int textureCount = Math.max(1, drawData.textures.size());

        frameData.guiVertices.ensureCapacity(vertexBytes, state);
        frameData.guiCommands.ensureCapacity(commandBytes, state);
        frameData.guiPoints.ensureCapacity(pointBytes, state);
        frameData.guiPointDetails.ensureCapacity(detailBytes, state);
        frameData.guiTextureIndices.ensureCapacity((long) textureCount * Integer.BYTES, state);

        DrawListOffsets[] offsets = new DrawListOffsets[drawListCount];
        long vertexOffset = 0;
        long commandOffset = 0;
        long pointOffset = 0;
        long detailOffset = 0;
        for (int i = 0; i < drawListCount; ++i) {
            offsets[i] =
                    new DrawListOffsets(
                            vertexOffset,
                            (int) (commandOffset / DrawData.SIZE_OF_DRAW_COMMAND),
                            (int) (pointOffset / DrawData.SIZE_OF_POINT),
                            (int) (detailOffset / DrawData.SIZE_OF_POINT_DETAIL));
            vertexOffset +=
                    copy(drawData.getDrawListVertexBuffer(i), frameData.guiVertices, vertexOffset);
            commandOffset +=
                    copy(
                            drawData.getDrawListCommandBuffer(i),
                            frameData.guiCommands,
                            commandOffset);
            pointOffset +=
                    copy(drawData.getDrawListPointBuffer(i), frameData.guiPoints, pointOffset);
            detailOffset +=
                    copy(
                            drawData.getDrawListPointDetailBuffer(i),
                            frameData.guiPointDetails,
                            detailOffset);
        }

        final int fallback = ((TextureInfoVulkan) fontAtlas.info()).bindlessIndex;
        ByteBuffer textureIndices =
                MemoryUtil.memByteBuffer(
                        frameData.guiTextureIndices.allocationInfo.pMappedData(),
                        textureCount * Integer.BYTES);
        textureIndices.putInt(0, fallback);
        for (int i = 0; i < drawData.textures.size(); ++i) {
            textureIndices.putInt(
                    i * Integer.BYTES, getBindlessIndex(drawData.textures.get(i), fallback));
        }

        return offsets;
    }

    /**
     * The number of bytes left in a buffer, treating null as empty.
     *
     * @param buffer The buffer, which may be null.
     * @return The number of bytes remaining.
     */
    private static int remaining(ByteBuffer buffer) {
        return buffer == null ? 0 : buffer.remaining();
    }

    /**
     * Copy data into a mapped buffer.
     *
     * @param source The data to copy, which may be null.
     * @param destination The buffer to copy into, which must be large enough.
     * @param offset The offset in bytes into the destination.
     * @return The number of bytes copied.
     */
    private static int copy(ByteBuffer source, @NonNull SharedBuffer destination, long offset) {
        final int size = remaining(source);
        if (size > 0) {
            MemoryUtil.memCopy(
                    MemoryUtil.memAddress(source),
                    destination.allocationInfo.pMappedData() + offset,
                    size);
        }
        return size;
    }

    /**
     * Look up the bindless slot for a texture used by the GUI.
     *
     * @param texture The texture.
     * @param fallback The slot to use if the texture can't be used.
     * @return The bindless slot.
     */
    private static int getBindlessIndex(@NonNull TextureInfo texture, int fallback) {
        if (!(texture instanceof TextureInfoVulkan info)) {
            log.warn("Can't render non-Vulkan texture {} in the GUI", texture);
            return fallback;
        }
        if (info.bindlessIndex == TextureInfoVulkan.NO_BINDLESS_INDEX) {
            log.warn("Can't render texture {} in the GUI, it has no bindless slot", texture);
            return fallback;
        }
        return info.bindlessIndex;
    }

    /**
     * Record the draws for each draw list.
     *
     * @param commandBuffer The command buffer to record into.
     * @param state The Vulkan state.
     * @param drawData The draw data for the frame.
     * @param offsets Where each draw list starts in the buffers.
     * @param width The width to render in pixels.
     * @param height The height to render in pixels.
     */
    private void drawLists(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            @NonNull DrawData drawData,
            @NonNull DrawListOffsets[] offsets,
            int width,
            int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);

            VkViewport.Buffer viewports = VkViewport.calloc(1, stack);
            viewports.get(0).width(width).height(height).minDepth(0.0f).maxDepth(1.0f);
            vkCmdSetViewport(commandBuffer, 0, viewports);
            VkRect2D.Buffer scissors = VkRect2D.calloc(1, stack);
            scissors.get(0).extent().set(width, height);
            vkCmdSetScissor(commandBuffer, 0, scissors);

            // Sets 0 and 1 are consecutive, so bind both at once
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipelineLayout,
                    0,
                    stack.longs(
                            descriptorSets[state.frameIndex],
                            state.bindlessTextures.getDescriptorSet()),
                    null);

            LongBuffer vertexBuffers =
                    stack.longs(state.perFrameData[state.frameIndex].guiVertices.buffer);
            LongBuffer vertexOffsets = stack.callocLong(1);
            ByteBuffer pushConstants = stack.calloc(ShaderBindings.GUI.PUSH_CONSTANTS_SIZE);

            for (int i = 0; i < offsets.length; ++i) {
                final int vertexCount = drawData.getDrawListVertexCount(i);
                if (vertexCount <= 0) {
                    continue;
                }
                pushConstants.putInt(
                        ShaderBindings.GUI.PUSH_CONSTANT_COMMAND_OFFSET, offsets[i].commands());
                pushConstants.putInt(
                        ShaderBindings.GUI.PUSH_CONSTANT_POINT_OFFSET, offsets[i].points());
                pushConstants.putInt(
                        ShaderBindings.GUI.PUSH_CONSTANT_DETAIL_OFFSET, offsets[i].details());
                vkCmdPushConstants(
                        commandBuffer,
                        pipelineLayout,
                        VK_SHADER_STAGE_FRAGMENT_BIT,
                        0,
                        pushConstants);

                // The vertex shader works out the command from the vertex index, so start at 0
                vertexOffsets.put(0, offsets[i].vertexBytes());
                vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, vertexOffsets);
                vkCmdDraw(commandBuffer, vertexCount, 1, 0, 0);
            }
        }
    }

    private void createPipelineLayout(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0)
                    .size(ShaderBindings.GUI.PUSH_CONSTANTS_SIZE);

            VkDescriptorSetLayoutBinding.Buffer descriptorSetLayoutBindings =
                    VkDescriptorSetLayoutBinding.calloc(DESCRIPTOR_COUNT, stack);
            descriptorSetLayoutBindings
                    .get(0)
                    .binding(ShaderBindings.GUI.UNIFORMS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            final int[] storageBindings = {
                ShaderBindings.GUI.COMMANDS_BINDING,
                ShaderBindings.GUI.POINTS_BINDING,
                ShaderBindings.GUI.POINT_DETAILS_BINDING,
                ShaderBindings.GUI.TEXTURE_INDICES_BINDING
            };
            for (int i = 0; i < storageBindings.length; ++i) {
                descriptorSetLayoutBindings
                        .get(i + 1)
                        .binding(storageBindings[i])
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            }

            // Each frame has its own set, only updated once the GPU is done with that frame
            VkDescriptorSetLayoutCreateInfo descriptorSetLayoutCreateInfo =
                    VkDescriptorSetLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pBindings(descriptorSetLayoutBindings);

            checkError(
                    vkCreateDescriptorSetLayout(
                            state.device.logical, descriptorSetLayoutCreateInfo, null, longOutput));
            descriptorSetLayout = longOutput.get(0);

            LongBuffer setLayouts =
                    stack.longs(
                            descriptorSetLayout, state.bindlessTextures.getDescriptorSetLayout());

            VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack);
            pipelineLayoutCreateInfo
                    .sType$Default()
                    .pSetLayouts(setLayouts)
                    .pPushConstantRanges(pushConstantRanges);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical, pipelineLayoutCreateInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2, stack);
            poolSizes
                    .get(0)
                    .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(GraphicsManager.MAX_FRAMES_IN_FLIGHT);
            poolSizes
                    .get(1)
                    .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(storageBindings.length * GraphicsManager.MAX_FRAMES_IN_FLIGHT);

            VkDescriptorPoolCreateInfo descriptorPoolCreateInfo =
                    VkDescriptorPoolCreateInfo.calloc(stack)
                            .sType$Default()
                            .maxSets(GraphicsManager.MAX_FRAMES_IN_FLIGHT)
                            .pPoolSizes(poolSizes);

            checkError(
                    vkCreateDescriptorPool(
                            state.device.logical, descriptorPoolCreateInfo, null, longOutput));
            descriptorPool = longOutput.get(0);

            LongBuffer descriptorSetLayouts =
                    stack.callocLong(GraphicsManager.MAX_FRAMES_IN_FLIGHT);
            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                descriptorSetLayouts.put(i, descriptorSetLayout);
            }

            VkDescriptorSetAllocateInfo descriptorSetAlloc =
                    VkDescriptorSetAllocateInfo.calloc(stack)
                            .sType$Default()
                            .descriptorPool(descriptorPool)
                            .pSetLayouts(descriptorSetLayouts);
            checkError(
                    vkAllocateDescriptorSets(
                            state.device.logical, descriptorSetAlloc, descriptorSets));

            // The uniform buffers never change, so write them once
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
                        .dstSet(descriptorSets[i])
                        .dstBinding(ShaderBindings.GUI.UNIFORMS_BINDING)
                        .pBufferInfo(uniformBufferInfo)
                        .descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            }
            vkUpdateDescriptorSets(state.device.logical, writeDescriptorSets, null);
        }
    }

    private void createPipeline(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkVertexInputAttributeDescription.Buffer vertexAttributes =
                    VkVertexInputAttributeDescription.calloc(1, stack);
            // Positions
            vertexAttributes
                    .get(0)
                    .binding(0)
                    .location(0)
                    .format(VK_FORMAT_R32G32_SFLOAT)
                    .offset(0);

            VkVertexInputBindingDescription.Buffer vertexBindings =
                    VkVertexInputBindingDescription.calloc(1, stack);
            vertexBindings
                    .get(0)
                    .binding(0)
                    .stride(DrawData.SIZE_OF_VERTEX)
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

            // Not sRGB, matching the OpenGL backend
            VkPipelineRenderingCreateInfo renderingCreateInfo =
                    VkPipelineRenderingCreateInfo.calloc(stack)
                            .sType$Default()
                            .colorAttachmentCount(1)
                            .pColorAttachmentFormats(
                                    stack.ints(PipelineManagerVulkan.SCREEN_FORMAT));

            // Same blending as the OpenGL backend
            VkPipelineColorBlendAttachmentState.Buffer blendAttachments =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack);
            blendAttachments
                    .get(0)
                    .blendEnable(true)
                    .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                    .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                    .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .alphaBlendOp(VK_BLEND_OP_ADD)
                    .colorWriteMask(
                            VK_COLOR_COMPONENT_R_BIT
                                    | VK_COLOR_COMPONENT_G_BIT
                                    | VK_COLOR_COMPONENT_B_BIT
                                    | VK_COLOR_COMPONENT_A_BIT);
            VkPipelineColorBlendStateCreateInfo colorBlendState =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pAttachments(blendAttachments);
            // No culling or depth testing, like OpenGL
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
                    .stageCount(shader.shaderModules.length)
                    .pStages(shader.shaderStages)
                    .pVertexInputState(vertexInputStateCreateInfo)
                    .pInputAssemblyState(inputAssemblyState)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizationState)
                    .pMultisampleState(multisampleState)
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

    /**
     * Point the descriptors at any storage buffers that were reallocated. This frame's set is not
     * in use by the GPU, so it can be updated directly.
     *
     * @param state The Vulkan state.
     * @param frameData The data for the current frame.
     */
    private void updateBindings(@NonNull VulkanState state, @NonNull PerFrameData frameData) {
        final SharedBuffer[] buffers = {
            frameData.guiCommands,
            frameData.guiPoints,
            frameData.guiPointDetails,
            frameData.guiTextureIndices
        };
        final int[] bindings = {
            ShaderBindings.GUI.COMMANDS_BINDING,
            ShaderBindings.GUI.POINTS_BINDING,
            ShaderBindings.GUI.POINT_DETAILS_BINDING,
            ShaderBindings.GUI.TEXTURE_INDICES_BINDING
        };

        int updateCount = 0;
        for (SharedBuffer buffer : buffers) {
            if (buffer.updated && buffer.buffer != VK_NULL_HANDLE) {
                updateCount += 1;
            }
        }
        if (updateCount == 0) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer writeDescriptorSets =
                    VkWriteDescriptorSet.calloc(updateCount, stack);

            int index = 0;
            for (int i = 0; i < buffers.length; ++i) {
                SharedBuffer buffer = buffers[i];
                if (!buffer.updated || buffer.buffer == VK_NULL_HANDLE) {
                    continue;
                }
                VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
                bufferInfo.get(0).buffer(buffer.buffer).offset(0).range(VK_WHOLE_SIZE);
                writeDescriptorSets
                        .get(index)
                        .sType$Default()
                        .dstSet(descriptorSets[state.frameIndex])
                        .dstBinding(bindings[i])
                        .pBufferInfo(bufferInfo)
                        .descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
                index += 1;
                buffer.updated = false;
            }
            vkUpdateDescriptorSets(state.device.logical, writeDescriptorSets, null);
        }
    }
}
