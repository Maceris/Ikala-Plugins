package com.ikalagaming.graphics.vulkan;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

/**
 * The voxel-based transparency volume, see {@link VoxelOitMath}: a buffer the translucent surfaces
 * add their extinction into, and the image it is integrated into, which the transparent stage reads
 * its weights from.
 *
 * <p>One of each is shared by every frame in flight, like the {@link DepthPyramid}. The image
 * always stays in the general layout, and barriers keep a frame from writing either while an
 * earlier one still reads it. The buffer starts at zero and integrating clears what it reads, so it
 * is back at zero at the start of every frame.
 *
 * <p>It is sized for the g-buffer, and each frame uses as many tiles as cover what it draws.
 */
@Slf4j
public class OitVolume {

    /**
     * The format of the integrated transmittance. One float, since half floats aren't guaranteed as
     * storage images.
     */
    public static final int FORMAT = VK_FORMAT_R32_SFLOAT;

    /**
     * The layout of the set the integration writes the image through. -- GETTER -- The layout of
     * the integration set.
     *
     * @return The VkDescriptorSetLayout.
     */
    @Getter private long integrateSetLayout;

    /**
     * The layout of the set fragment shaders read the image through. -- GETTER -- The layout of the
     * read set.
     *
     * @return The VkDescriptorSetLayout.
     */
    @Getter private long readSetLayout;

    /** Blends between tiles and slices. */
    private long sampler;

    /** The VkImage, VK_NULL_HANDLE until it is sized. */
    private long image;

    /** The VmaAllocation of the image. */
    private long imageAllocation;

    /** The view of the whole image. */
    private long view;

    /**
     * The extinction each tile's slices add up, in fixed point. -- GETTER -- The extinction buffer.
     *
     * @return The buffer, null until it is sized.
     */
    @Getter private SharedBuffer extinction;

    /** The pool the sets come from. */
    private long descriptorPool;

    /**
     * The set the integration writes the image through. -- GETTER -- The integration set.
     *
     * @return The VkDescriptorSet.
     */
    @Getter private long integrateSet;

    /**
     * The set fragment shaders read the image through. -- GETTER -- The read set.
     *
     * @return The VkDescriptorSet.
     */
    @Getter private long readSet;

    /**
     * How many tiles across the image holds. -- GETTER -- The tiles across.
     *
     * @return The width in tiles.
     */
    @Getter private int tilesX;

    /**
     * How many tiles down the image holds. -- GETTER -- The tiles down.
     *
     * @return The height in tiles.
     */
    @Getter private int tilesY;

    /**
     * Set up the descriptor layouts and sampler. Call {@link #resize} before using it.
     *
     * @param state The Vulkan state.
     */
    public OitVolume(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            integrateSetLayout =
                    createSetLayout(
                            state,
                            VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
                            VK_SHADER_STAGE_COMPUTE_BIT,
                            stack);
            readSetLayout =
                    createSetLayout(
                            state,
                            VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                            VK_SHADER_STAGE_FRAGMENT_BIT,
                            stack);

            LongBuffer longOutput = stack.callocLong(1);
            VkSamplerCreateInfo samplerInfo =
                    VkSamplerCreateInfo.calloc(stack)
                            .sType$Default()
                            .magFilter(VK_FILTER_LINEAR)
                            .minFilter(VK_FILTER_LINEAR)
                            .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                            .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                            .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                            .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                            .minLod(0)
                            .maxLod(0);
            checkError(vkCreateSampler(state.device.logical, samplerInfo, null, longOutput));
            sampler = longOutput.get(0);
        }
    }

    /**
     * Create a set layout with one binding.
     *
     * @param state The Vulkan state.
     * @param type The VkDescriptorType of the binding.
     * @param stages The VkShaderStageFlags that use it.
     * @param stack The stack to allocate on.
     * @return The VkDescriptorSetLayout.
     */
    private static long createSetLayout(
            @NonNull VulkanState state, int type, int stages, @NonNull MemoryStack stack) {
        LongBuffer longOutput = stack.callocLong(1);
        VkDescriptorSetLayoutBinding.Buffer bindings =
                VkDescriptorSetLayoutBinding.calloc(1, stack);
        bindings.get(0)
                .binding(ShaderBindings.OitVolume.TRANSMITTANCE_BINDING)
                .descriptorType(type)
                .descriptorCount(1)
                .stageFlags(stages);
        checkError(
                vkCreateDescriptorSetLayout(
                        state.device.logical,
                        VkDescriptorSetLayoutCreateInfo.calloc(stack)
                                .sType$Default()
                                .pBindings(bindings),
                        null,
                        longOutput));
        return longOutput.get(0);
    }

    /**
     * Make the volume big enough for a g-buffer, throwing away what it held. The GPU must be idle.
     *
     * @param state The Vulkan state.
     * @param width The g-buffer's width.
     * @param height The g-buffer's height.
     */
    public void resize(@NonNull VulkanState state, int width, int height) {
        destroy(state);
        tilesX = VoxelOitMath.tiles(width);
        tilesY = VoxelOitMath.tiles(height);
        final long cells = (long) tilesX * tilesY * VoxelOitMath.SLICES;
        log.debug(
                "Creating a {} × {} × {} transparency volume", tilesX, tilesY, VoxelOitMath.SLICES);

        extinction =
                SharedBuffer.allocateDeviceLocal(
                        cells * Integer.BYTES, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            PointerBuffer pointerOutput = stack.callocPointer(1);
            VkImageCreateInfo imageInfo =
                    VkImageCreateInfo.calloc(stack)
                            .sType$Default()
                            .imageType(VK_IMAGE_TYPE_3D)
                            .format(FORMAT)
                            .extent(e -> e.set(tilesX, tilesY, VoxelOitMath.SLICES))
                            .mipLevels(1)
                            .arrayLayers(1)
                            .samples(VK_SAMPLE_COUNT_1_BIT)
                            .tiling(VK_IMAGE_TILING_OPTIMAL)
                            .usage(VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            VmaAllocationCreateInfo allocInfo =
                    VmaAllocationCreateInfo.calloc(stack)
                            .flags(VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT)
                            .usage(VMA_MEMORY_USAGE_AUTO);
            checkError(
                    vmaCreateImage(
                            state.vmaAllocator,
                            imageInfo,
                            allocInfo,
                            longOutput,
                            pointerOutput,
                            null));
            image = longOutput.get(0);
            imageAllocation = pointerOutput.get(0);

            VkImageViewCreateInfo viewInfo =
                    VkImageViewCreateInfo.calloc(stack)
                            .sType$Default()
                            .image(image)
                            .viewType(VK_IMAGE_VIEW_TYPE_3D)
                            .format(FORMAT)
                            .subresourceRange(
                                    range ->
                                            range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                    .levelCount(1)
                                                    .layerCount(1));
            checkError(vkCreateImageView(state.device.logical, viewInfo, null, longOutput));
            view = longOutput.get(0);

            createDescriptorSets(state, stack);
        }

        // The buffer starts empty, and the image moves to the general layout once, where it stays
        state.immediateCommands.submit(
                state,
                commandBuffer -> {
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        vkCmdFillBuffer(commandBuffer, extinction.buffer, 0, VK_WHOLE_SIZE, 0);
                        VkMemoryBarrier2.Buffer fill = VkMemoryBarrier2.calloc(1, stack);
                        fill.get(0)
                                .sType$Default()
                                .srcStageMask(VK_PIPELINE_STAGE_2_CLEAR_BIT)
                                .srcAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                                .dstStageMask(
                                        VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                                                | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                                .dstAccessMask(
                                        VK_ACCESS_2_SHADER_STORAGE_READ_BIT
                                                | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT);
                        VkImageMemoryBarrier2.Buffer layout =
                                VkImageMemoryBarrier2.calloc(1, stack);
                        layout.get(0)
                                .sType$Default()
                                .srcStageMask(VK_PIPELINE_STAGE_2_NONE)
                                .srcAccessMask(VK_ACCESS_2_NONE)
                                .dstStageMask(
                                        VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT
                                                | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT)
                                .dstAccessMask(
                                        VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT
                                                | VK_ACCESS_2_SHADER_SAMPLED_READ_BIT)
                                .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                                .newLayout(VK_IMAGE_LAYOUT_GENERAL)
                                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                                .image(image)
                                .subresourceRange(
                                        range ->
                                                range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                        .levelCount(1)
                                                        .layerCount(1));
                        vkCmdPipelineBarrier2(
                                commandBuffer,
                                VkDependencyInfo.calloc(stack)
                                        .sType$Default()
                                        .pMemoryBarriers(fill)
                                        .pImageMemoryBarriers(layout));
                    }
                });
    }

    /**
     * Create the integration and read sets, pointing at the current image.
     *
     * @param state The Vulkan state.
     * @param stack The stack to allocate on.
     */
    private void createDescriptorSets(@NonNull VulkanState state, @NonNull MemoryStack stack) {
        LongBuffer longOutput = stack.callocLong(1);
        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2, stack);
        poolSizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(1);
        poolSizes.get(1).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1);
        checkError(
                vkCreateDescriptorPool(
                        state.device.logical,
                        VkDescriptorPoolCreateInfo.calloc(stack)
                                .sType$Default()
                                .maxSets(2)
                                .pPoolSizes(poolSizes),
                        null,
                        longOutput));
        descriptorPool = longOutput.get(0);

        LongBuffer sets = stack.mallocLong(2);
        checkError(
                vkAllocateDescriptorSets(
                        state.device.logical,
                        VkDescriptorSetAllocateInfo.calloc(stack)
                                .sType$Default()
                                .descriptorPool(descriptorPool)
                                .pSetLayouts(stack.longs(integrateSetLayout, readSetLayout)),
                        sets));
        integrateSet = sets.get(0);
        readSet = sets.get(1);

        VkDescriptorImageInfo.Buffer storage = VkDescriptorImageInfo.calloc(1, stack);
        storage.get(0).imageView(view).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
        VkDescriptorImageInfo.Buffer sampled = VkDescriptorImageInfo.calloc(1, stack);
        sampled.get(0).sampler(sampler).imageView(view).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);
        writes.get(0)
                .sType$Default()
                .dstSet(integrateSet)
                .dstBinding(ShaderBindings.OitVolume.TRANSMITTANCE_BINDING)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                .descriptorCount(1)
                .pImageInfo(storage);
        writes.get(1)
                .sType$Default()
                .dstSet(readSet)
                .dstBinding(ShaderBindings.OitVolume.TRANSMITTANCE_BINDING)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(sampled);
        vkUpdateDescriptorSets(state.device.logical, writes, null);
    }

    /**
     * Destroy the image, the buffer and everything that points at them.
     *
     * @param state The Vulkan state.
     */
    private void destroy(@NonNull VulkanState state) {
        if (image == VK_NULL_HANDLE) {
            return;
        }
        vkDestroyDescriptorPool(state.device.logical, descriptorPool, null);
        descriptorPool = VK_NULL_HANDLE;
        integrateSet = VK_NULL_HANDLE;
        readSet = VK_NULL_HANDLE;
        vkDestroyImageView(state.device.logical, view, null);
        view = VK_NULL_HANDLE;
        vmaDestroyImage(state.vmaAllocator, image, imageAllocation);
        image = VK_NULL_HANDLE;
        imageAllocation = VK_NULL_HANDLE;
        SharedBuffer.free(extinction, state);
        extinction = null;
    }

    /**
     * Destroy everything, when shutting down. The GPU must be idle.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        destroy(state);
        vkDestroySampler(state.device.logical, sampler, null);
        sampler = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(state.device.logical, integrateSetLayout, null);
        integrateSetLayout = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(state.device.logical, readSetLayout, null);
        readSetLayout = VK_NULL_HANDLE;
    }
}
