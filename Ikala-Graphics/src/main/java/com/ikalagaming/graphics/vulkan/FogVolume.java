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
 * The fog volume, see {@link FogMath} and {@code fog.glsl}: froxels of {@link #TILE_SIZE} pixel
 * tiles across the screen by the transparency volume's slices in depth. Each frame the fog's media
 * and the light they scatter toward the camera are sampled at a point jittered within each froxel
 * and blended with the frames before, reprojected, into one of two media images, in turns; then
 * integrated front to back into another image, which every surface reads the fog in front of it
 * from.
 *
 * <p>The images are shared by every frame in flight, like the {@link OitVolume}: they stay in the
 * general layout, and barriers keep a frame from writing them while an earlier one still reads
 * them. It is sized for the g-buffer, and each frame uses as many tiles as cover what it draws.
 */
@Slf4j
public class FogVolume {

    /** Pixels along each side of a froxel's tile. */
    public static final int TILE_SIZE = 16;

    /**
     * The images' format, half floats: light in rgb, or extinction or transmittance by color in
     * rgb; alpha unused.
     */
    public static final int FORMAT = VK_FORMAT_R16G16B16A16_SFLOAT;

    /** The binding the injection writes this frame's media through. */
    public static final int INJECT_BINDING = 0;

    /** The binding the injection reads the frames before's media through. */
    public static final int HISTORY_BINDING = 1;

    /** The binding the integration reads the media through. */
    public static final int MEDIA_BINDING = 0;

    /** The binding the integration writes the integrated fog through. */
    public static final int INTEGRATED_BINDING = 1;

    /** The binding surfaces read the integrated fog through. */
    public static final int READ_BINDING = 0;

    /** The binding the injection writes this frame's extinction by color through. */
    public static final int INJECT_EXTINCTION_BINDING = 2;

    /** The binding the injection reads the frames before's extinction through. */
    public static final int HISTORY_EXTINCTION_BINDING = 3;

    /** The binding the integration reads the extinction through. */
    public static final int MEDIA_EXTINCTION_BINDING = 2;

    /** The binding the integration writes the transmittance by color through. */
    public static final int TRANSMITTANCE_BINDING = 3;

    /** The binding surfaces read the transmittance through. */
    public static final int READ_TRANSMITTANCE_BINDING = 1;

    /** How many media images take turns. */
    private static final int MEDIA_IMAGES = 2;

    /**
     * The layout of the set the injection writes the media through. -- GETTER -- The layout.
     *
     * @return The VkDescriptorSetLayout.
     */
    @Getter private long injectSetLayout;

    /**
     * The layout of the set the integration reads the media and writes the fog through. -- GETTER
     * -- The layout.
     *
     * @return The VkDescriptorSetLayout.
     */
    @Getter private long integrateSetLayout;

    /**
     * The layout of the set surfaces read the integrated fog through. -- GETTER -- The layout.
     *
     * @return The VkDescriptorSetLayout.
     */
    @Getter private long readSetLayout;

    /** Blends between froxels. */
    private long sampler;

    /** The media images, which take turns, and their allocations and views. */
    private final long[] media = new long[MEDIA_IMAGES];

    private final long[] mediaAllocations = new long[MEDIA_IMAGES];
    private final long[] mediaViews = new long[MEDIA_IMAGES];

    /** The media's extinction by color, alongside each media image, and allocations and views. */
    private final long[] extinction = new long[MEDIA_IMAGES];

    private final long[] extinctionAllocations = new long[MEDIA_IMAGES];
    private final long[] extinctionViews = new long[MEDIA_IMAGES];

    /** The fog integrated front to back, per froxel, and its allocation and view. */
    private long integrated;

    private long integratedAllocation;
    private long integratedView;

    /** How much of each color gets through, front to back, per froxel, and allocation and view. */
    private long transmittance;

    private long transmittanceAllocation;
    private long transmittanceView;

    /** The pool the sets come from. */
    private long descriptorPool;

    /** For each media image, the set the injection writes it through, reading the other. */
    private final long[] injectSets = new long[MEDIA_IMAGES];

    /** For each media image, the set the integration reads it through. */
    private final long[] integrateSets = new long[MEDIA_IMAGES];

    /**
     * The set surfaces read the integrated fog through. -- GETTER -- The set.
     *
     * @return The VkDescriptorSet.
     */
    @Getter private long readSet;

    /**
     * How many tiles across the images hold. -- GETTER -- The tiles across.
     *
     * @return The width in tiles.
     */
    @Getter private int tilesX;

    /**
     * How many tiles down the images hold. -- GETTER -- The tiles down.
     *
     * @return The height in tiles.
     */
    @Getter private int tilesY;

    /**
     * What volumetric particles leave in each froxel, {@link ParticleMath#FROXEL_CHANNELS} values
     * per froxel, see {@code particle_froxel.comp}; cleared before each frame's splat.
     */
    @Getter private SharedBuffer particleFroxels;

    /** Which media image this frame writes. */
    private int current;

    /**
     * Whether the other media image holds the frames before's fog, to blend with. -- GETTER --
     * Whether there is history.
     *
     * @return True if the last frame filled the volume, since it was made.
     */
    @Getter private boolean historyValid;

    /**
     * Set up the descriptor layouts and sampler. Call {@link #resize} before using it.
     *
     * @param state The Vulkan state.
     */
    public FogVolume(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            injectSetLayout =
                    createSetLayout(
                            state,
                            new int[] {
                                INJECT_BINDING,
                                HISTORY_BINDING,
                                INJECT_EXTINCTION_BINDING,
                                HISTORY_EXTINCTION_BINDING
                            },
                            new int[] {
                                VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
                                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                                VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
                                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
                            },
                            VK_SHADER_STAGE_COMPUTE_BIT,
                            stack);
            integrateSetLayout =
                    createSetLayout(
                            state,
                            new int[] {
                                MEDIA_BINDING,
                                INTEGRATED_BINDING,
                                MEDIA_EXTINCTION_BINDING,
                                TRANSMITTANCE_BINDING
                            },
                            new int[] {
                                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                                VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
                                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                                VK_DESCRIPTOR_TYPE_STORAGE_IMAGE
                            },
                            VK_SHADER_STAGE_COMPUTE_BIT,
                            stack);
            readSetLayout =
                    createSetLayout(
                            state,
                            new int[] {READ_BINDING, READ_TRANSMITTANCE_BINDING},
                            new int[] {
                                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
                            },
                            VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_COMPUTE_BIT,
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
     * Create a set layout.
     *
     * @param state The Vulkan state.
     * @param bindings The bindings.
     * @param types Each binding's VkDescriptorType.
     * @param stages The VkShaderStageFlags that use them.
     * @param stack The stack to allocate on.
     * @return The VkDescriptorSetLayout.
     */
    private static long createSetLayout(
            @NonNull VulkanState state,
            int @NonNull [] bindings,
            int @NonNull [] types,
            int stages,
            @NonNull MemoryStack stack) {
        LongBuffer longOutput = stack.callocLong(1);
        VkDescriptorSetLayoutBinding.Buffer layoutBindings =
                VkDescriptorSetLayoutBinding.calloc(bindings.length, stack);
        for (int i = 0; i < bindings.length; ++i) {
            layoutBindings
                    .get(i)
                    .binding(bindings[i])
                    .descriptorType(types[i])
                    .descriptorCount(1)
                    .stageFlags(stages);
        }
        checkError(
                vkCreateDescriptorSetLayout(
                        state.device.logical,
                        VkDescriptorSetLayoutCreateInfo.calloc(stack)
                                .sType$Default()
                                .pBindings(layoutBindings),
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
        tilesX = tiles(width);
        tilesY = tiles(height);
        historyValid = false;
        log.debug("Creating a {} x {} x {} fog volume", tilesX, tilesY, VoxelOitMath.SLICES);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (int i = 0; i < MEDIA_IMAGES; ++i) {
                final long[] made = createImage(state, stack);
                media[i] = made[0];
                mediaAllocations[i] = made[1];
                mediaViews[i] = made[2];
                final long[] extinctionMade = createImage(state, stack);
                extinction[i] = extinctionMade[0];
                extinctionAllocations[i] = extinctionMade[1];
                extinctionViews[i] = extinctionMade[2];
            }
            final long[] made = createImage(state, stack);
            integrated = made[0];
            integratedAllocation = made[1];
            integratedView = made[2];
            final long[] transmittanceMade = createImage(state, stack);
            transmittance = transmittanceMade[0];
            transmittanceAllocation = transmittanceMade[1];
            transmittanceView = transmittanceMade[2];
            createDescriptorSets(state, stack);
        }
        particleFroxels =
                SharedBuffer.allocateDeviceLocal(
                        (long) tilesX
                                * tilesY
                                * VoxelOitMath.SLICES
                                * ParticleMath.FROXEL_CHANNELS
                                * Integer.BYTES,
                        state,
                        VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        // The images move to the general layout once, where they stay
        state.immediateCommands.submit(
                state,
                commandBuffer -> {
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        final long[] images = {
                            media[0],
                            media[1],
                            extinction[0],
                            extinction[1],
                            integrated,
                            transmittance
                        };
                        VkImageMemoryBarrier2.Buffer layouts =
                                VkImageMemoryBarrier2.calloc(images.length, stack);
                        for (int i = 0; i < images.length; ++i) {
                            layouts.get(i)
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
                                    .image(images[i])
                                    .subresourceRange(
                                            range ->
                                                    range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                            .levelCount(1)
                                                            .layerCount(1));
                        }
                        vkCmdPipelineBarrier2(
                                commandBuffer,
                                VkDependencyInfo.calloc(stack)
                                        .sType$Default()
                                        .pImageMemoryBarriers(layouts));
                    }
                });
    }

    /**
     * How many tiles cover some pixels.
     *
     * @param pixels The pixels.
     * @return The tiles, at least 1.
     */
    public static int tiles(int pixels) {
        return Math.max(1, (pixels + TILE_SIZE - 1) / TILE_SIZE);
    }

    /**
     * The set the injection writes this frame's media through, reading the frames before's.
     *
     * @return The VkDescriptorSet.
     */
    public long getInjectSet() {
        return injectSets[current];
    }

    /**
     * The set the integration reads this frame's media through.
     *
     * @return The VkDescriptorSet.
     */
    public long getIntegrateSet() {
        return integrateSets[current];
    }

    /**
     * Note that this frame filled the volume: the next frame writes the other media image and
     * blends with this one.
     */
    public void advance() {
        current = (current + 1) % MEDIA_IMAGES;
        historyValid = true;
    }

    /**
     * Forget the frames before, as when the fog was off for a while: the next frame starts over.
     */
    public void forgetHistory() {
        historyValid = false;
    }

    /**
     * Create one of the volume's images and its view.
     *
     * @param state The Vulkan state.
     * @param stack The stack to allocate on.
     * @return The VkImage, its VmaAllocation and its VkImageView.
     */
    private long[] createImage(@NonNull VulkanState state, @NonNull MemoryStack stack) {
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
                        state.vmaAllocator, imageInfo, allocInfo, longOutput, pointerOutput, null));
        final long image = longOutput.get(0);
        final long allocation = pointerOutput.get(0);
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
        return new long[] {image, allocation, longOutput.get(0)};
    }

    /**
     * Create the sets, pointing at the current images.
     *
     * @param state The Vulkan state.
     * @param stack The stack to allocate on.
     */
    private void createDescriptorSets(@NonNull VulkanState state, @NonNull MemoryStack stack) {
        LongBuffer longOutput = stack.callocLong(1);
        final int sets = 2 * MEDIA_IMAGES + 1;
        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2, stack);
        // Each set reads or writes the light and the extinction alike: twice as many images
        poolSizes
                .get(0)
                .type(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                .descriptorCount(2 * 2 * MEDIA_IMAGES);
        poolSizes
                .get(1)
                .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(2 * (2 * MEDIA_IMAGES + 1));
        checkError(
                vkCreateDescriptorPool(
                        state.device.logical,
                        VkDescriptorPoolCreateInfo.calloc(stack)
                                .sType$Default()
                                .maxSets(sets)
                                .pPoolSizes(poolSizes),
                        null,
                        longOutput));
        descriptorPool = longOutput.get(0);

        LongBuffer allocated = stack.mallocLong(sets);
        checkError(
                vkAllocateDescriptorSets(
                        state.device.logical,
                        VkDescriptorSetAllocateInfo.calloc(stack)
                                .sType$Default()
                                .descriptorPool(descriptorPool)
                                .pSetLayouts(
                                        stack.longs(
                                                injectSetLayout,
                                                injectSetLayout,
                                                integrateSetLayout,
                                                integrateSetLayout,
                                                readSetLayout)),
                        allocated));
        VkWriteDescriptorSet.Buffer writes =
                VkWriteDescriptorSet.calloc(2 * (4 * MEDIA_IMAGES + 1), stack);
        int write = 0;
        for (int i = 0; i < MEDIA_IMAGES; ++i) {
            final int other = (i + 1) % MEDIA_IMAGES;
            injectSets[i] = allocated.get(i);
            integrateSets[i] = allocated.get(MEDIA_IMAGES + i);
            image(writes.get(write++), injectSets[i], INJECT_BINDING, mediaViews[i], false, stack);
            image(
                    writes.get(write++),
                    injectSets[i],
                    HISTORY_BINDING,
                    mediaViews[other],
                    true,
                    stack);
            image(writes.get(write++), integrateSets[i], MEDIA_BINDING, mediaViews[i], true, stack);
            image(
                    writes.get(write++),
                    integrateSets[i],
                    INTEGRATED_BINDING,
                    integratedView,
                    false,
                    stack);
            image(
                    writes.get(write++),
                    injectSets[i],
                    INJECT_EXTINCTION_BINDING,
                    extinctionViews[i],
                    false,
                    stack);
            image(
                    writes.get(write++),
                    injectSets[i],
                    HISTORY_EXTINCTION_BINDING,
                    extinctionViews[other],
                    true,
                    stack);
            image(
                    writes.get(write++),
                    integrateSets[i],
                    MEDIA_EXTINCTION_BINDING,
                    extinctionViews[i],
                    true,
                    stack);
            image(
                    writes.get(write++),
                    integrateSets[i],
                    TRANSMITTANCE_BINDING,
                    transmittanceView,
                    false,
                    stack);
        }
        readSet = allocated.get(2 * MEDIA_IMAGES);
        image(writes.get(write++), readSet, READ_BINDING, integratedView, true, stack);
        image(
                writes.get(write),
                readSet,
                READ_TRANSMITTANCE_BINDING,
                transmittanceView,
                true,
                stack);
        vkUpdateDescriptorSets(state.device.logical, writes, null);
    }

    /**
     * Fill in a write of one image to a set.
     *
     * @param write The write.
     * @param set The set.
     * @param binding The binding.
     * @param view The image's view.
     * @param sampled Whether it is read through the sampler, rather than as a storage image.
     * @param stack The stack to allocate on.
     */
    private void image(
            @NonNull VkWriteDescriptorSet write,
            long set,
            int binding,
            long view,
            boolean sampled,
            @NonNull MemoryStack stack) {
        VkDescriptorImageInfo.Buffer info = VkDescriptorImageInfo.calloc(1, stack);
        info.get(0).imageView(view).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
        if (sampled) {
            info.get(0).sampler(sampler);
        }
        write.sType$Default()
                .dstSet(set)
                .dstBinding(binding)
                .descriptorType(
                        sampled
                                ? VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
                                : VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                .descriptorCount(1)
                .pImageInfo(info);
    }

    /**
     * Destroy the images and everything that points at them.
     *
     * @param state The Vulkan state.
     */
    private void destroy(@NonNull VulkanState state) {
        if (integrated == VK_NULL_HANDLE) {
            return;
        }
        vkDestroyDescriptorPool(state.device.logical, descriptorPool, null);
        descriptorPool = VK_NULL_HANDLE;
        readSet = VK_NULL_HANDLE;
        for (int i = 0; i < MEDIA_IMAGES; ++i) {
            injectSets[i] = VK_NULL_HANDLE;
            integrateSets[i] = VK_NULL_HANDLE;
            vkDestroyImageView(state.device.logical, mediaViews[i], null);
            vmaDestroyImage(state.vmaAllocator, media[i], mediaAllocations[i]);
            media[i] = VK_NULL_HANDLE;
            vkDestroyImageView(state.device.logical, extinctionViews[i], null);
            vmaDestroyImage(state.vmaAllocator, extinction[i], extinctionAllocations[i]);
            extinction[i] = VK_NULL_HANDLE;
        }
        vkDestroyImageView(state.device.logical, integratedView, null);
        vmaDestroyImage(state.vmaAllocator, integrated, integratedAllocation);
        integrated = VK_NULL_HANDLE;
        vkDestroyImageView(state.device.logical, transmittanceView, null);
        vmaDestroyImage(state.vmaAllocator, transmittance, transmittanceAllocation);
        transmittance = VK_NULL_HANDLE;
        if (particleFroxels != null) {
            SharedBuffer.free(particleFroxels, state);
            particleFroxels = null;
        }
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
        vkDestroyDescriptorSetLayout(state.device.logical, injectSetLayout, null);
        vkDestroyDescriptorSetLayout(state.device.logical, integrateSetLayout, null);
        vkDestroyDescriptorSetLayout(state.device.logical, readSetLayout, null);
        injectSetLayout = VK_NULL_HANDLE;
        integrateSetLayout = VK_NULL_HANDLE;
        readSetLayout = VK_NULL_HANDLE;
    }
}
