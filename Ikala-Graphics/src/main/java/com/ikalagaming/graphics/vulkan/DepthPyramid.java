package com.ikalagaming.graphics.vulkan;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

/**
 * The depth pyramid the late culling pass tests the scene against: each level half the size of the
 * one before, every texel holding the farthest depth under it. See {@link DepthPyramidMath} for how
 * it is built and read.
 *
 * <p>One image is shared by every frame in flight. It always stays in the general layout, since
 * each level is written as a storage image while the level before is read, and the culling pass
 * reads them all. Barriers keep a frame from building it while an earlier one still reads it.
 *
 * <p>It is sized for the g-buffer, and the depth it is built from may be smaller, so it remembers
 * the size it was last built from, along with where from and with what projection, which is what
 * lets a frozen observer keep culling by the pyramid it saw.
 */
@Slf4j
public class DepthPyramid {

    /** The format of every level: one float, the farthest depth. */
    public static final int FORMAT = VK_FORMAT_R32_SFLOAT;

    /**
     * The layout of the sets that build each level: the level being written, and every level to
     * read the one before. -- GETTER -- The layout of the build sets.
     *
     * @return The VkDescriptorSetLayout.
     */
    @Getter private long buildSetLayout;

    /**
     * The layout of the set culling reads the pyramid through. -- GETTER -- The layout of the read
     * set.
     *
     * @return The VkDescriptorSetLayout.
     */
    @Getter private long readSetLayout;

    /** Reads texels exactly, from any level. */
    private long sampler;

    /** The VkImage, VK_NULL_HANDLE until it is sized. */
    private long image;

    /** The VmaAllocation of the image. */
    private long imageAllocation;

    /** A view of every level, for reading. */
    private long fullView;

    /** A view of each level, for writing. */
    private long[] levelViews = new long[0];

    /** The pool the sets come from. */
    private long descriptorPool;

    /** The set that builds each level. */
    private long[] buildSets = new long[0];

    /**
     * The set culling reads the pyramid through. -- GETTER -- The read set.
     *
     * @return The VkDescriptorSet.
     */
    @Getter private long readSet;

    /**
     * How many levels the image has, enough for the whole g-buffer. -- GETTER -- How many levels
     * the image has.
     *
     * @return The level count.
     */
    @Getter private int imageLevels;

    /**
     * Whether it holds a depth buffer. It doesn't after being resized, until it is built again. --
     * GETTER -- Whether it holds a depth buffer.
     *
     * @return True if it can be tested against.
     */
    @Getter private boolean valid;

    /**
     * The width of the depth buffer it was last built from. -- GETTER -- The depth width.
     *
     * @return The width in pixels.
     */
    @Getter private int depthWidth;

    /**
     * The height of the depth buffer it was last built from. -- GETTER -- The depth height.
     *
     * @return The height in pixels.
     */
    @Getter private int depthHeight;

    /** Where the depth it was last built from was drawn from, in the world. */
    private final Vector3d position = new Vector3d();

    /** The projection × view matrix the depth was drawn with, relative to its position. */
    private final Matrix4f projectionView = new Matrix4f();

    /**
     * Set up the descriptor layouts and sampler. Call {@link #resize} before using it.
     *
     * @param state The Vulkan state.
     */
    public DepthPyramid(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkDescriptorSetLayoutBinding.Buffer buildBindings =
                    VkDescriptorSetLayoutBinding.calloc(2, stack);
            buildBindings
                    .get(0)
                    .binding(ShaderBindings.DepthPyramid.DESTINATION_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            buildBindings
                    .get(1)
                    .binding(ShaderBindings.DepthPyramid.PYRAMID_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            checkError(
                    vkCreateDescriptorSetLayout(
                            state.device.logical,
                            VkDescriptorSetLayoutCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pBindings(buildBindings),
                            null,
                            longOutput));
            buildSetLayout = longOutput.get(0);

            VkDescriptorSetLayoutBinding.Buffer readBindings =
                    VkDescriptorSetLayoutBinding.calloc(1, stack);
            readBindings
                    .get(0)
                    .binding(ShaderBindings.Cull.PYRAMID_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            checkError(
                    vkCreateDescriptorSetLayout(
                            state.device.logical,
                            VkDescriptorSetLayoutCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pBindings(readBindings),
                            null,
                            longOutput));
            readSetLayout = longOutput.get(0);

            // Only ever read with texelFetch, which picks the texel and level itself
            VkSamplerCreateInfo samplerInfo =
                    VkSamplerCreateInfo.calloc(stack)
                            .sType$Default()
                            .magFilter(VK_FILTER_NEAREST)
                            .minFilter(VK_FILTER_NEAREST)
                            .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                            .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                            .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                            .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                            .minLod(0)
                            .maxLod(VK_LOD_CLAMP_NONE);
            checkError(vkCreateSampler(state.device.logical, samplerInfo, null, longOutput));
            sampler = longOutput.get(0);
        }
    }

    /**
     * Make the pyramid big enough for a g-buffer, throwing away what it held. The GPU must be idle.
     *
     * @param state The Vulkan state.
     * @param width The g-buffer's width.
     * @param height The g-buffer's height.
     */
    public void resize(@NonNull VulkanState state, int width, int height) {
        destroyImage(state);
        valid = false;
        imageLevels = DepthPyramidMath.levelCount(width, height);
        final int levelWidth = DepthPyramidMath.levelSize(width, 0);
        final int levelHeight = DepthPyramidMath.levelSize(height, 0);
        log.debug(
                "Creating a {} × {} depth pyramid with {} levels",
                levelWidth,
                levelHeight,
                imageLevels);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            PointerBuffer pointerOutput = stack.callocPointer(1);
            VkImageCreateInfo imageInfo =
                    VkImageCreateInfo.calloc(stack)
                            .sType$Default()
                            .imageType(VK_IMAGE_TYPE_2D)
                            .format(FORMAT)
                            .extent(e -> e.set(levelWidth, levelHeight, 1))
                            .mipLevels(imageLevels)
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

            fullView = createView(state, 0, imageLevels, stack);
            levelViews = new long[imageLevels];
            for (int level = 0; level < imageLevels; ++level) {
                levelViews[level] = createView(state, level, 1, stack);
            }

            createDescriptorSets(state, stack);
        }

        // Moved to the general layout once, where it stays
        state.immediateCommands.submit(
                state,
                commandBuffer -> {
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        VkImageMemoryBarrier2.Buffer barrier =
                                VkImageMemoryBarrier2.calloc(1, stack);
                        barrier.get(0)
                                .sType$Default()
                                .srcStageMask(VK_PIPELINE_STAGE_2_NONE)
                                .srcAccessMask(VK_ACCESS_2_NONE)
                                .dstStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
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
                                                        .levelCount(imageLevels)
                                                        .layerCount(1));
                        vkCmdPipelineBarrier2(
                                commandBuffer,
                                VkDependencyInfo.calloc(stack)
                                        .sType$Default()
                                        .pImageMemoryBarriers(barrier));
                    }
                });
    }

    /**
     * Create a view of some of the levels.
     *
     * @param state The Vulkan state.
     * @param firstLevel The first level.
     * @param levelCount How many levels.
     * @param stack The stack to allocate on.
     * @return The VkImageView.
     */
    private long createView(
            @NonNull VulkanState state,
            int firstLevel,
            int levelCount,
            @NonNull MemoryStack stack) {
        LongBuffer longOutput = stack.callocLong(1);
        VkImageViewCreateInfo viewInfo =
                VkImageViewCreateInfo.calloc(stack)
                        .sType$Default()
                        .image(image)
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(FORMAT)
                        .subresourceRange(
                                range ->
                                        range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                .baseMipLevel(firstLevel)
                                                .levelCount(levelCount)
                                                .layerCount(1));
        checkError(vkCreateImageView(state.device.logical, viewInfo, null, longOutput));
        return longOutput.get(0);
    }

    /**
     * Create the build sets, one per level, and the read set, all pointing at the current image.
     *
     * @param state The Vulkan state.
     * @param stack The stack to allocate on.
     */
    private void createDescriptorSets(@NonNull VulkanState state, @NonNull MemoryStack stack) {
        LongBuffer longOutput = stack.callocLong(1);
        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2, stack);
        poolSizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(imageLevels);
        poolSizes
                .get(1)
                .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(imageLevels + 1);
        checkError(
                vkCreateDescriptorPool(
                        state.device.logical,
                        VkDescriptorPoolCreateInfo.calloc(stack)
                                .sType$Default()
                                .maxSets(imageLevels + 1)
                                .pPoolSizes(poolSizes),
                        null,
                        longOutput));
        descriptorPool = longOutput.get(0);

        LongBuffer layouts = stack.mallocLong(imageLevels + 1);
        for (int level = 0; level < imageLevels; ++level) {
            layouts.put(level, buildSetLayout);
        }
        layouts.put(imageLevels, readSetLayout);
        LongBuffer sets = stack.mallocLong(imageLevels + 1);
        checkError(
                vkAllocateDescriptorSets(
                        state.device.logical,
                        VkDescriptorSetAllocateInfo.calloc(stack)
                                .sType$Default()
                                .descriptorPool(descriptorPool)
                                .pSetLayouts(layouts),
                        sets));
        buildSets = new long[imageLevels];
        sets.get(0, buildSets);
        readSet = sets.get(imageLevels);

        VkDescriptorImageInfo.Buffer everyLevel = VkDescriptorImageInfo.calloc(1, stack);
        everyLevel.get(0).sampler(sampler).imageView(fullView).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
        VkWriteDescriptorSet.Buffer writes =
                VkWriteDescriptorSet.calloc(2 * imageLevels + 1, stack);
        for (int level = 0; level < imageLevels; ++level) {
            VkDescriptorImageInfo.Buffer destination = VkDescriptorImageInfo.calloc(1, stack);
            destination.get(0).imageView(levelViews[level]).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
            writes.get(2 * level)
                    .sType$Default()
                    .dstSet(buildSets[level])
                    .dstBinding(ShaderBindings.DepthPyramid.DESTINATION_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1)
                    .pImageInfo(destination);
            writes.get(2 * level + 1)
                    .sType$Default()
                    .dstSet(buildSets[level])
                    .dstBinding(ShaderBindings.DepthPyramid.PYRAMID_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(everyLevel);
        }
        writes.get(2 * imageLevels)
                .sType$Default()
                .dstSet(readSet)
                .dstBinding(ShaderBindings.Cull.PYRAMID_BINDING)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(everyLevel);
        vkUpdateDescriptorSets(state.device.logical, writes, null);
    }

    /**
     * The set that builds a level.
     *
     * @param level The level.
     * @return The VkDescriptorSet.
     */
    public long getBuildSet(int level) {
        return buildSets[level];
    }

    /**
     * The VkImage, for barriers.
     *
     * @return The image.
     */
    public long getImage() {
        return image;
    }

    /**
     * Note that the pyramid is being built this frame, from a depth buffer drawn from somewhere.
     *
     * @param width The depth buffer's width, no bigger than the g-buffer.
     * @param height The depth buffer's height, no bigger than the g-buffer.
     * @param from Where the depth was drawn from, in the world.
     * @param drawnWith The projection × view matrix it was drawn with, relative to that position.
     */
    public void markBuilt(
            int width, int height, @NonNull Vector3dc from, @NonNull Matrix4fc drawnWith) {
        depthWidth = width;
        depthHeight = height;
        position.set(from);
        projectionView.set(drawnWith);
        valid = true;
    }

    /**
     * How many levels the last build filled, for the depth buffer it was built from.
     *
     * @return The level count.
     */
    public int getLevels() {
        return Math.min(imageLevels, DepthPyramidMath.levelCount(depthWidth, depthHeight));
    }

    /**
     * The matrix that projects render space positions onto the pyramid, which moves with the camera
     * when the pyramid was built from somewhere else, like a frozen observer.
     *
     * @param camera The camera's world position, the render space origin.
     * @param out Receives the matrix.
     * @return The out matrix.
     */
    public Matrix4f projectionFrom(@NonNull Vector3dc camera, @NonNull Matrix4f out) {
        return DepthPyramidMath.moveInto(projectionView, position, camera, out);
    }

    /**
     * Destroy the image and everything that points at it.
     *
     * @param state The Vulkan state.
     */
    private void destroyImage(@NonNull VulkanState state) {
        if (image == VK_NULL_HANDLE) {
            return;
        }
        vkDestroyDescriptorPool(state.device.logical, descriptorPool, null);
        descriptorPool = VK_NULL_HANDLE;
        buildSets = new long[0];
        readSet = VK_NULL_HANDLE;
        for (long view : levelViews) {
            vkDestroyImageView(state.device.logical, view, null);
        }
        levelViews = new long[0];
        vkDestroyImageView(state.device.logical, fullView, null);
        fullView = VK_NULL_HANDLE;
        vmaDestroyImage(state.vmaAllocator, image, imageAllocation);
        image = VK_NULL_HANDLE;
        imageAllocation = VK_NULL_HANDLE;
    }

    /**
     * Destroy everything, when shutting down. The GPU must be idle.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        destroyImage(state);
        valid = false;
        vkDestroySampler(state.device.logical, sampler, null);
        sampler = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(state.device.logical, buildSetLayout, null);
        buildSetLayout = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(state.device.logical, readSetLayout, null);
        readSetLayout = VK_NULL_HANDLE;
    }
}
