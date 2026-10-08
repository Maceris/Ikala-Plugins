package com.ikalagaming.graphics.backend.vulkan;

import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.exceptions.TextureException;
import com.ikalagaming.graphics.frontend.Format;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.frontend.TextureLoader;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

/**
 * Loads textures onto the GPU. Every texture is registered in the bindless texture array, so the
 * bindless and non-bindless variants behave the same.
 *
 * <p>Textures end up in {@link VK13#VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL}, ready to be sampled. When
 * data is provided, mipmaps are generated if the format supports it, matching the OpenGL backend.
 * Must be used from the render thread.
 */
@Slf4j
public class TextureLoaderVulkan implements TextureLoader {

    /** The Vulkan state. */
    private final VulkanState state;

    /** Sampler shared by every loaded texture. Linear filtering, clamped to the edge. */
    private long sampler;

    /** A 1x1 white texture that lives in the default bindless slot. */
    private Texture defaultTexture;

    /**
     * Whether a format can be cleared with vkCmdClearColorImage, which rules out compressed and
     * depth/stencil formats.
     *
     * @param format The format.
     * @return Whether we can clear an image of that format to a color.
     */
    private static boolean canClearColor(@NonNull Format format) {
        final String name = format.name();
        return !name.endsWith("_BLOCK")
                && !name.startsWith("D16_")
                && !name.startsWith("D24_")
                && !name.startsWith("D32_")
                && !name.startsWith("X8_D24_")
                && !"S8_UINT".equals(name);
    }

    /**
     * Create a subresource range covering the specified mip levels of a color image.
     *
     * @param stack The stack to allocate on.
     * @param baseMipLevel The first mip level.
     * @param levelCount How many mip levels.
     * @return The subresource range.
     */
    private static VkImageSubresourceRange colorRange(
            @NonNull MemoryStack stack, int baseMipLevel, int levelCount) {
        return VkImageSubresourceRange.calloc(stack)
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(baseMipLevel)
                .levelCount(levelCount)
                .layerCount(1);
    }

    /**
     * Fill out an image barrier that waits on and blocks transfers.
     *
     * @param barrier The barrier to fill out.
     * @param image The VkImage.
     * @param oldLayout The layout to transition from.
     * @param newLayout The layout to transition to.
     * @param range The subresources affected.
     */
    private static void transferBarrier(
            @NonNull VkImageMemoryBarrier2 barrier,
            long image,
            int oldLayout,
            int newLayout,
            @NonNull VkImageSubresourceRange range) {
        barrier.sType$Default()
                .srcStageMask(VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT)
                .srcAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT)
                .dstAccessMask(VK_ACCESS_2_TRANSFER_READ_BIT | VK_ACCESS_2_TRANSFER_WRITE_BIT)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image)
                .subresourceRange(range);
    }

    /**
     * Fill out an image barrier that makes transfer results visible to shaders, moving to the read
     * only layout.
     *
     * @param barrier The barrier to fill out.
     * @param image The VkImage.
     * @param oldLayout The layout to transition from.
     * @param range The subresources affected.
     */
    private static void readOnlyBarrier(
            @NonNull VkImageMemoryBarrier2 barrier,
            long image,
            int oldLayout,
            @NonNull VkImageSubresourceRange range) {
        barrier.sType$Default()
                .srcStageMask(VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT)
                .srcAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT)
                .dstAccessMask(VK_ACCESS_2_SHADER_SAMPLED_READ_BIT)
                .oldLayout(oldLayout)
                .newLayout(VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image)
                .subresourceRange(range);
    }

    /**
     * Record a pipeline barrier with the given image barriers.
     *
     * @param commandBuffer The command buffer to record into.
     * @param barriers The image barriers.
     * @param stack The stack to allocate on.
     */
    private static void recordBarriers(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VkImageMemoryBarrier2.Buffer barriers,
            @NonNull MemoryStack stack) {
        VkDependencyInfo dependencyInfo =
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers);
        vkCmdPipelineBarrier2(commandBuffer, dependencyInfo);
    }

    /**
     * Set up the shared sampler and the default texture. The bindless texture array and immediate
     * commands in the state must already exist.
     *
     * @param state The Vulkan state.
     */
    public TextureLoaderVulkan(@NonNull VulkanState state) {
        this.state = state;
        createSampler();

        ByteBuffer white = MemoryUtil.memAlloc(4);
        white.put(0, new byte[] {-1, -1, -1, -1});
        defaultTexture = load(white, Format.R8G8B8A8_UNORM, 1, 1);
        MemoryUtil.memFree(white);

        final int defaultIndex = ((TextureInfoVulkan) defaultTexture.info()).bindlessIndex;
        if (defaultIndex != ShaderBindings.BindlessTextures.DEFAULT_TEXTURE_INDEX) {
            log.error(
                    "Default texture ended up in bindless slot {} instead of {}",
                    defaultIndex,
                    ShaderBindings.BindlessTextures.DEFAULT_TEXTURE_INDEX);
        }
    }

    /**
     * Destroy the default texture and shared sampler. The GPU must be idle, and every texture using
     * the shared sampler must already be destroyed.
     */
    public void cleanup() {
        var defaultInfo = (TextureInfoVulkan) defaultTexture.info();
        defaultInfo.destroy(state);
        defaultTexture = null;
        vkDestroySampler(state.device.logical, sampler, null);
        sampler = VK_NULL_HANDLE;
    }

    /** Create the sampler shared by all the textures we load. */
    private void createSampler() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo samplerCreateInfo =
                    VkSamplerCreateInfo.calloc(stack)
                            .sType$Default()
                            .magFilter(VK_FILTER_LINEAR)
                            .minFilter(VK_FILTER_LINEAR)
                            .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                            .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                            .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                            .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                            .anisotropyEnable(false)
                            .compareEnable(false)
                            .minLod(0.0f)
                            // Works for any number of mip levels
                            .maxLod(VK_LOD_CLAMP_NONE);
            LongBuffer longOutput = stack.callocLong(1);
            checkError(vkCreateSampler(state.device.logical, samplerCreateInfo, null, longOutput));
            sampler = longOutput.get(0);
        }
    }

    @Override
    public Texture loadBindless(ByteBuffer buffer, @NonNull Format format, int width, int height) {
        return load(buffer, format, width, height);
    }

    @Override
    public Texture load(ByteBuffer buffer, @NonNull Format format, int width, int height) {
        final int vkFormat = FormatMapperVulkan.mapFormat(format);

        final int features;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties formatProperties = VkFormatProperties.calloc(stack);
            vkGetPhysicalDeviceFormatProperties(
                    state.device.physical.physicalDevice, vkFormat, formatProperties);
            features = formatProperties.optimalTilingFeatures();
        }
        final int requiredFeatures =
                VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT | VK_FORMAT_FEATURE_TRANSFER_DST_BIT;
        if ((features & requiredFeatures) != requiredFeatures) {
            final String error =
                    SafeResourceLoader.format(
                            "Texture format {} is not supported by this device", format.name());
            log.error(error);
            throw new TextureException(error);
        }

        final int mipFeatures =
                VK_FORMAT_FEATURE_BLIT_SRC_BIT
                        | VK_FORMAT_FEATURE_BLIT_DST_BIT
                        | VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT;
        final boolean generateMips = buffer != null && (features & mipFeatures) == mipFeatures;
        final int mipLevels =
                generateMips ? 32 - Integer.numberOfLeadingZeros(Math.max(width, height)) : 1;

        TextureInfoVulkan info = createImage(vkFormat, width, height, mipLevels, generateMips);

        if (buffer != null) {
            upload(info, buffer, width, height, mipLevels);
        } else {
            initializeEmpty(info, canClearColor(format));
        }

        state.bindlessTextures.register(state, info);
        return new Texture(width, height, info);
    }

    /**
     * Create the image and view, using the shared sampler.
     *
     * @param vkFormat The VkFormat.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param mipLevels The number of mip levels.
     * @param generateMips Whether we will blit to generate mips, which needs transfer source usage.
     * @return The texture info with the handles filled out.
     */
    private TextureInfoVulkan createImage(
            int vkFormat, int width, int height, int mipLevels, boolean generateMips) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            PointerBuffer pointerOutput = stack.callocPointer(1);

            int usage = VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
            if (generateMips) {
                usage |= VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
            }

            VkImageCreateInfo imageCreateInfo =
                    VkImageCreateInfo.calloc(stack)
                            .sType$Default()
                            .imageType(VK_IMAGE_TYPE_2D)
                            .format(vkFormat)
                            .extent(e -> e.set(width, height, 1))
                            .mipLevels(mipLevels)
                            .arrayLayers(1)
                            .samples(VK_SAMPLE_COUNT_1_BIT)
                            .tiling(VK_IMAGE_TILING_OPTIMAL)
                            .usage(usage)
                            .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            VmaAllocationCreateInfo allocationCreateInfo =
                    VmaAllocationCreateInfo.calloc(stack).usage(VMA_MEMORY_USAGE_AUTO);
            checkError(
                    vmaCreateImage(
                            state.vmaAllocator,
                            imageCreateInfo,
                            allocationCreateInfo,
                            longOutput,
                            pointerOutput,
                            null));
            final long image = longOutput.get(0);
            final long allocation = pointerOutput.get(0);

            VkImageViewCreateInfo viewCreateInfo =
                    VkImageViewCreateInfo.calloc(stack)
                            .sType$Default()
                            .image(image)
                            .viewType(VK_IMAGE_VIEW_TYPE_2D)
                            .format(vkFormat)
                            .subresourceRange(colorRange(stack, 0, mipLevels));
            checkError(vkCreateImageView(state.device.logical, viewCreateInfo, null, longOutput));

            return new TextureInfoVulkan()
                    .texture(image)
                    .textureAllocation(allocation)
                    .view(longOutput.get(0))
                    .sampler(sampler);
        }
    }

    /**
     * Copy the data into the first mip level, generate the rest of the mip levels, and move the
     * image to the read only layout. Waits for the GPU to finish.
     *
     * @param info The texture.
     * @param buffer The tightly packed pixel data for the first mip level.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param mipLevels The number of mip levels in the image.
     */
    private void upload(
            @NonNull TextureInfoVulkan info,
            @NonNull ByteBuffer buffer,
            int width,
            int height,
            int mipLevels) {
        final int size = buffer.remaining();
        SharedBuffer staging = SharedBuffer.allocate(size, state, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
        MemoryUtil.memCopy(
                MemoryUtil.memAddress(buffer), staging.allocationInfo.pMappedData(), size);

        state.immediateCommands.submit(
                state,
                commandBuffer -> {
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        VkImageMemoryBarrier2.Buffer barriers =
                                VkImageMemoryBarrier2.calloc(1, stack);
                        transferBarrier(
                                barriers.get(0),
                                info.texture,
                                VK_IMAGE_LAYOUT_UNDEFINED,
                                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                                colorRange(stack, 0, mipLevels));
                        recordBarriers(commandBuffer, barriers, stack);

                        VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
                        region.get(0)
                                .bufferOffset(0)
                                // Zero means tightly packed
                                .bufferRowLength(0)
                                .bufferImageHeight(0)
                                .imageSubresource(
                                        layers ->
                                                layers.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                        .mipLevel(0)
                                                        .layerCount(1))
                                .imageExtent(e -> e.set(width, height, 1));
                        vkCmdCopyBufferToImage(
                                commandBuffer,
                                staging.buffer,
                                info.texture,
                                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                                region);

                        generateMips(commandBuffer, info.texture, width, height, mipLevels);
                    }
                });

        // The submission has finished, so nothing is using the staging buffer
        SharedBuffer.free(staging, state);
    }

    /**
     * Fill out mip levels 1 and up by repeatedly blitting from the previous level, then move every
     * level to the read only layout. Expects every level to be in the transfer destination layout,
     * with level 0 filled out.
     *
     * @param commandBuffer The command buffer to record into.
     * @param image The VkImage.
     * @param width The width of level 0 in pixels.
     * @param height The height of level 0 in pixels.
     * @param mipLevels The number of mip levels.
     */
    private void generateMips(
            @NonNull VkCommandBuffer commandBuffer,
            long image,
            int width,
            int height,
            int mipLevels) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier2.Buffer barrier = VkImageMemoryBarrier2.calloc(1, stack);
            VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);

            int mipWidth = width;
            int mipHeight = height;
            for (int level = 1; level < mipLevels; ++level) {
                // The previous level is done being written, read from it
                transferBarrier(
                        barrier.get(0),
                        image,
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        colorRange(stack, level - 1, 1));
                recordBarriers(commandBuffer, barrier, stack);

                final int nextWidth = Math.max(1, mipWidth / 2);
                final int nextHeight = Math.max(1, mipHeight / 2);
                final int sourceLevel = level - 1;
                final int destinationLevel = level;
                final int sourceWidth = mipWidth;
                final int sourceHeight = mipHeight;

                blit.get(0)
                        .srcSubresource(
                                layers ->
                                        layers.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                .mipLevel(sourceLevel)
                                                .layerCount(1))
                        .srcOffsets(1, offset -> offset.set(sourceWidth, sourceHeight, 1))
                        .dstSubresource(
                                layers ->
                                        layers.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                .mipLevel(destinationLevel)
                                                .layerCount(1))
                        .dstOffsets(1, offset -> offset.set(nextWidth, nextHeight, 1));
                vkCmdBlitImage(
                        commandBuffer,
                        image,
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        image,
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        blit,
                        VK_FILTER_LINEAR);

                mipWidth = nextWidth;
                mipHeight = nextHeight;
            }

            // Every level but the last was a blit source, the last is still a destination
            VkImageMemoryBarrier2.Buffer finalBarriers =
                    VkImageMemoryBarrier2.calloc(mipLevels > 1 ? 2 : 1, stack);
            readOnlyBarrier(
                    finalBarriers.get(0),
                    image,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    colorRange(stack, mipLevels - 1, 1));
            if (mipLevels > 1) {
                readOnlyBarrier(
                        finalBarriers.get(1),
                        image,
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        colorRange(stack, 0, mipLevels - 1));
            }
            recordBarriers(commandBuffer, finalBarriers, stack);
        }
    }

    /**
     * Move a texture with no data to the read only layout, clearing it to transparent black if the
     * format allows so that sampling it doesn't show garbage. Waits for the GPU to finish.
     *
     * @param info The texture, which has a single mip level.
     * @param clear Whether to clear the image first.
     */
    private void initializeEmpty(@NonNull TextureInfoVulkan info, boolean clear) {
        state.immediateCommands.submit(
                state,
                commandBuffer -> {
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        VkImageMemoryBarrier2.Buffer barrier =
                                VkImageMemoryBarrier2.calloc(1, stack);
                        int layout = VK_IMAGE_LAYOUT_UNDEFINED;
                        if (clear) {
                            transferBarrier(
                                    barrier.get(0),
                                    info.texture,
                                    layout,
                                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                                    colorRange(stack, 0, 1));
                            recordBarriers(commandBuffer, barrier, stack);
                            layout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;

                            vkCmdClearColorImage(
                                    commandBuffer,
                                    info.texture,
                                    layout,
                                    VkClearColorValue.calloc(stack),
                                    colorRange(stack, 0, 1));
                        }
                        readOnlyBarrier(
                                barrier.get(0), info.texture, layout, colorRange(stack, 0, 1));
                        recordBarriers(commandBuffer, barrier, stack);
                    }
                });
    }

    @Override
    public Texture loadBindless(@NonNull String texturePath) {
        return load(texturePath);
    }

    @Override
    public Texture load(@NonNull String texturePath) {
        Texture result;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            ByteBuffer buffer = STBImage.stbi_load(texturePath, width, height, channels, 4);
            if (buffer == null) {
                final String error =
                        SafeResourceLoader.format(
                                "Image file {} not loaded: {}",
                                texturePath,
                                STBImage.stbi_failure_reason());
                log.info(error);
                throw new TextureException(error);
            }

            try {
                result = load(buffer, Format.R8G8B8A8_UNORM, width.get(0), height.get(0));
            } finally {
                STBImage.stbi_image_free(buffer);
            }
        }
        return result;
    }

    /**
     * Destroy a texture and release its bindless slot, once no frame in flight can be using it.
     *
     * @param texture The texture to delete.
     */
    public void delete(@NonNull Texture texture) {
        var info = (TextureInfoVulkan) texture.info();
        state.bindlessTextures.release(state, info);
        state.deferFree(() -> info.destroy(state));
    }
}
