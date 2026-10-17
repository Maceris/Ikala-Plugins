package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.DrawData;
import com.ikalagaming.graphics.gui.data.FontAtlas;
import com.ikalagaming.graphics.vulkan.*;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Renders IkGui straight onto the windows' swapchain images, after the scene is copied into its
 * window. Every draw list is packed into the same per-frame buffers once a frame, and push
 * constants tell the shader where the current draw list starts. Each window then gets a pass, which
 * also readies its image to present even with nothing drawn.
 */
@Slf4j
public class GuiRender {

    /** The number of descriptors in the set for each frame. */
    private static final int DESCRIPTOR_COUNT = 5;

    /**
     * Both shaders read the push constants: the vertex shader places, the fragment shader draws.
     */
    private static final int PUSH_CONSTANT_STAGES =
            VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;

    /** The shader to use for rendering. */
    @NonNull @Setter private ShaderVulkan shader;

    /** The font atlas texture. */
    private final TextureHandle fontAtlas;

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

    /** Where the draw data uploaded by {@link #prepare} went in the packed buffers. */
    private GuiLayout prepared = GuiLayout.EMPTY;

    /**
     * Set up the GUI render stage.
     *
     * @param shader The shader to render the GUI with.
     * @param fontAtlas The font atlas texture.
     */
    public GuiRender(final @NonNull ShaderVulkan shader, final @NonNull TextureHandle fontAtlas) {
        this.shader = shader;
        this.fontAtlas = fontAtlas;
        this.descriptorSetLayout = VK_NULL_HANDLE;
        this.pipelineLayout = VK_NULL_HANDLE;
        this.pipeline = VK_NULL_HANDLE;
        this.descriptorPool = VK_NULL_HANDLE;

        this.descriptorSets = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];
    }

    /**
     * Create the pipeline and descriptors.
     *
     * @param vulkanState The Vulkan state.
     */
    public void initialize(@NonNull VulkanState vulkanState) {
        log.debug("Initializing gui render");
        createPipelineLayout(vulkanState);
        createPipeline(vulkanState);
    }

    /**
     * Destroy the pipeline and descriptors.
     *
     * @param vulkanState The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState vulkanState) {
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

    /**
     * Upload the glyphs added to the font atlas and the draw lists of every window that shows GUI
     * this frame, all packed into the same buffers. Call once a frame, before any window's pass and
     * outside of rendering, since it records transfers.
     *
     * @param commandBuffer The frame's command buffer.
     * @param vulkanState The Vulkan state.
     * @param drawData The draw data of each window that shows GUI, which can be empty.
     */
    public void prepare(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState vulkanState,
            @NonNull Collection<DrawData> drawData) {
        final PerFrameData frameData = vulkanState.perFrameData[vulkanState.frameIndex];

        uploadFontGlyphs(commandBuffer, vulkanState, frameData);
        updateUniforms(frameData);

        prepared = GuiLayout.of(drawData);
        if (prepared.drawData().isEmpty()) {
            ensureBuffersExist(vulkanState, frameData);
        } else {
            uploadDrawLists(prepared, vulkanState, frameData);
        }
        updateBindings(vulkanState, frameData);
    }

    /**
     * Record a window's pass: draw the prepared GUI over its swapchain image if asked, then leave
     * the image ready to present.
     *
     * @param commandBuffer The frame's command buffer.
     * @param vulkanState The Vulkan state.
     * @param presentation The window, with its swapchain image acquired.
     * @param sceneCopied Whether the scene was copied into the image, which is kept. Otherwise it
     *     starts out cleared.
     * @param drawData The window's draw data, which must have been prepared this frame, or null to
     *     draw no GUI in it.
     */
    public void recordWindow(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState vulkanState,
            @NonNull Presentation presentation,
            boolean sceneCopied,
            DrawData drawData) {
        final long image = presentation.swapchainImages[presentation.currentSwapchainIndex];
        final long view = presentation.swapchainImageViews[presentation.currentSwapchainIndex];
        final int imageWidth = presentation.swapchainWidth;
        final int imageHeight = presentation.swapchainHeight;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            transitionToDraw(commandBuffer, image, sceneCopied, stack);

            VkRenderingAttachmentInfo.Buffer colorAttachments =
                    VkRenderingAttachmentInfo.calloc(1, stack);
            colorAttachments
                    .get(0)
                    .sType$Default()
                    .imageView(view)
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(sceneCopied ? VK_ATTACHMENT_LOAD_OP_LOAD : VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            // The clear value is already zeroed, which matches the OpenGL default clear color

            VkRenderingInfo renderingInfo =
                    VkRenderingInfo.calloc(stack)
                            .sType$Default()
                            .renderArea(area -> area.extent().set(imageWidth, imageHeight))
                            .layerCount(1)
                            .pColorAttachments(colorAttachments);
            vkCmdBeginRendering(commandBuffer, renderingInfo);

            final GuiLayout.DrawListOffsets[] offsets =
                    drawData == null ? null : prepared.offsetsOf(drawData);
            if (offsets != null && offsets.length > 0) {
                drawLists(commandBuffer, vulkanState, drawData, offsets, imageWidth, imageHeight);
            } else if (drawData != null) {
                log.warn("GUI draw data for {} wasn't prepared this frame", presentation);
            }

            vkCmdEndRendering(commandBuffer);

            transitionToPresent(commandBuffer, image, stack);
        }
    }

    /**
     * Move a swapchain image to be a color attachment we can draw to. A copied scene is kept,
     * otherwise the old contents are discarded since the pass clears them.
     *
     * @param commandBuffer The command buffer to record into.
     * @param image The swapchain image.
     * @param sceneCopied Whether the scene was just copied into the image.
     * @param stack The stack to allocate on.
     */
    private static void transitionToDraw(
            @NonNull VkCommandBuffer commandBuffer,
            long image,
            boolean sceneCopied,
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
                .image(image)
                .subresourceRange(
                        range ->
                                range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                        .levelCount(1)
                                        .layerCount(1));
        if (sceneCopied) {
            // Written by the blit from the scene
            barriers.get(0)
                    .srcStageMask(VK_PIPELINE_STAGE_2_BLIT_BIT)
                    .srcAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        } else {
            // Chained to the acquire semaphore, which the submit waits on at this stage
            barriers.get(0)
                    .srcStageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(VK_ACCESS_2_NONE)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED);
        }
        VkDependencyInfo dependencyInfo =
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers);
        vkCmdPipelineBarrier2(commandBuffer, dependencyInfo);
    }

    /**
     * Ready a swapchain image we drew to for presenting.
     *
     * @param commandBuffer The command buffer to record into.
     * @param image The swapchain image.
     * @param stack The stack to allocate on.
     */
    private static void transitionToPresent(
            @NonNull VkCommandBuffer commandBuffer, long image, @NonNull MemoryStack stack) {
        VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(1, stack);
        // Presenting waits on the render complete semaphore, so nothing needs to wait here
        barriers.get(0)
                .sType$Default()
                .srcStageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                .srcAccessMask(VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_2_NONE)
                .dstAccessMask(VK_ACCESS_2_NONE)
                .oldLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image)
                .subresourceRange(
                        range ->
                                range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                        .levelCount(1)
                                        .layerCount(1));
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

        final TextureInfoVulkan atlas = state.textureRegistry.resolve(fontAtlas);
        if (atlas == null) {
            log.error("The font atlas texture was released, can't upload glyphs");
            return;
        }
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
     * @param frameData The data for the current frame.
     */
    private void updateUniforms(@NonNull PerFrameData frameData) {
        ByteBuffer uniformData =
                MemoryUtil.memByteBuffer(
                        frameData.guiUniforms.allocationInfo.pMappedData(),
                        ShaderBindings.GUI.UNIFORMS_BUFFER_SIZE);
        uniformData.putInt(ShaderBindings.GUI.UNIFORM_BUFFER_FONT_TEXTURE_OFFSET, fontAtlas.slot());
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
     * Copy every draw list into the packed buffers where the layout puts it, along with the texture
     * slot mapping.
     *
     * @param layout Where each draw list goes, with at least one draw data in it.
     * @param state The Vulkan state.
     * @param frameData The data for the current frame.
     */
    private void uploadDrawLists(
            @NonNull GuiLayout layout,
            @NonNull VulkanState state,
            @NonNull PerFrameData frameData) {
        // Every viewport's draw data shares one texture list
        final List<TextureHandle> textures = layout.drawData().getFirst().textures;
        // Always map at least one texture, the font atlas is the fallback like in OpenGL
        final int textureCount = Math.max(1, textures.size());

        frameData.guiVertices.ensureCapacity(layout.vertexBytes, state);
        frameData.guiCommands.ensureCapacity(layout.commandBytes, state);
        frameData.guiPoints.ensureCapacity(layout.pointBytes, state);
        frameData.guiPointDetails.ensureCapacity(layout.detailBytes, state);
        frameData.guiTextureIndices.ensureCapacity((long) textureCount * Integer.BYTES, state);

        for (DrawData drawData : layout.drawData()) {
            final GuiLayout.DrawListOffsets[] offsets = layout.offsetsOf(drawData);
            for (int i = 0; i < offsets.length; ++i) {
                final GuiLayout.DrawListOffsets start = offsets[i];
                copy(
                        drawData.getDrawListVertexBuffer(i),
                        frameData.guiVertices,
                        start.vertexBytes());
                copy(
                        drawData.getDrawListCommandBuffer(i),
                        frameData.guiCommands,
                        (long) start.commands() * DrawData.SIZE_OF_DRAW_COMMAND);
                copy(
                        drawData.getDrawListPointBuffer(i),
                        frameData.guiPoints,
                        (long) start.points() * DrawData.SIZE_OF_POINT);
                copy(
                        drawData.getDrawListPointDetailBuffer(i),
                        frameData.guiPointDetails,
                        (long) start.details() * DrawData.SIZE_OF_POINT_DETAIL);
            }
        }

        final int fallback = fontAtlas.slot();
        ByteBuffer textureIndices =
                MemoryUtil.memByteBuffer(
                        frameData.guiTextureIndices.allocationInfo.pMappedData(),
                        textureCount * Integer.BYTES);
        textureIndices.putInt(0, fallback);
        for (int i = 0; i < textures.size(); ++i) {
            textureIndices.putInt(i * Integer.BYTES, getBindlessIndex(state, textures.get(i)));
        }
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
        final int size = GuiLayout.remaining(source);
        if (size > 0) {
            MemoryUtil.memCopy(
                    MemoryUtil.memAddress(source),
                    destination.allocationInfo.pMappedData() + offset,
                    size);
        }
        return size;
    }

    /**
     * Set in a GUI texture index for a texture read in the sRGB color space, which shaders sample
     * in linear light, so the GUI encodes it back to show it as stored. Must line up with
     * ikgui.frag.
     */
    static final int SRGB_TEXTURE_FLAG = 1 << 30;

    /**
     * Look up the bindless slot for a texture used by the GUI, flagged if it is read as sRGB.
     *
     * @param state The Vulkan state.
     * @param texture The texture.
     * @return The bindless slot, or the default white texture while the texture is still loading or
     *     after it was released, with {@link #SRGB_TEXTURE_FLAG} set for sRGB textures.
     */
    private static int getBindlessIndex(
            @NonNull VulkanState state, @NonNull TextureHandle texture) {
        if (!state.textureRegistry.isValid(texture)) {
            log.warn("Can't render texture {} in the GUI, it was released", texture);
        }
        final int slot = state.textureRegistry.slotOrDefault(texture);
        if (slot != ShaderBindings.BindlessTextures.DEFAULT_TEXTURE_INDEX
                && state.textureRegistry.colorSpaceOf(texture) == ColorSpace.SRGB) {
            return slot | SRGB_TEXTURE_FLAG;
        }
        return slot;
    }

    /**
     * Record the draws for each of a window's draw lists, placed by its draw data: drawn at its
     * display size, at one framebuffer pixel per display unit times its framebuffer scale.
     *
     * @param commandBuffer The command buffer to record into.
     * @param state The Vulkan state.
     * @param drawData The window's draw data.
     * @param offsets Where each of its draw lists starts in the buffers.
     * @param imageWidth The width of the image drawn into, in pixels.
     * @param imageHeight The height of the image drawn into, in pixels.
     */
    private void drawLists(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            @NonNull DrawData drawData,
            GuiLayout.DrawListOffsets @NonNull [] offsets,
            int imageWidth,
            int imageHeight) {
        final float displayWidth = drawData.displaySize.x;
        final float displayHeight = drawData.displaySize.y;
        final float scaleX = drawData.framebufferScale.x > 0 ? drawData.framebufferScale.x : 1;
        final float scaleY = drawData.framebufferScale.y > 0 ? drawData.framebufferScale.y : 1;
        if (displayWidth <= 0 || displayHeight <= 0) {
            // Minimized
            return;
        }
        // The swapchain can briefly disagree with the display size while resizing, which draws
        // the GUI at its usual size and cuts off what doesn't fit
        final float viewportWidth = displayWidth * scaleX;
        final float viewportHeight = displayHeight * scaleY;
        final int scissorWidth = Math.min((int) viewportWidth, imageWidth);
        final int scissorHeight = Math.min((int) viewportHeight, imageHeight);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);

            VkViewport.Buffer viewports = VkViewport.calloc(1, stack);
            viewports
                    .get(0)
                    .width(viewportWidth)
                    .height(viewportHeight)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(commandBuffer, 0, viewports);
            VkRect2D.Buffer scissors = VkRect2D.calloc(1, stack);
            scissors.get(0).extent().set(scissorWidth, scissorHeight);
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
            // Vulkan NDC has y pointing down like our display coordinates, unlike OpenGL
            pushConstants.putFloat(
                    ShaderBindings.GUI.PUSH_CONSTANT_SCALE_OFFSET, 2.0f / displayWidth);
            pushConstants.putFloat(
                    ShaderBindings.GUI.PUSH_CONSTANT_SCALE_OFFSET + Float.BYTES,
                    2.0f / displayHeight);
            pushConstants.putFloat(
                    ShaderBindings.GUI.PUSH_CONSTANT_DISPLAY_POSITION_OFFSET,
                    drawData.displayPosition.x);
            pushConstants.putFloat(
                    ShaderBindings.GUI.PUSH_CONSTANT_DISPLAY_POSITION_OFFSET + Float.BYTES,
                    drawData.displayPosition.y);
            pushConstants.putFloat(
                    ShaderBindings.GUI.PUSH_CONSTANT_FRAMEBUFFER_SCALE_OFFSET, scaleX);
            pushConstants.putFloat(
                    ShaderBindings.GUI.PUSH_CONSTANT_FRAMEBUFFER_SCALE_OFFSET + Float.BYTES,
                    scaleY);

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
                        commandBuffer, pipelineLayout, PUSH_CONSTANT_STAGES, 0, pushConstants);

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
                    .stageFlags(PUSH_CONSTANT_STAGES)
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

            // Drawn straight onto the swapchain images, which aren't sRGB, matching the OpenGL
            // backend
            VkPipelineRenderingCreateInfo renderingCreateInfo =
                    VkPipelineRenderingCreateInfo.calloc(stack)
                            .sType$Default()
                            .colorAttachmentCount(1)
                            .pColorAttachmentFormats(stack.ints(Presentation.SWAPCHAIN_FORMAT));

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
