package com.ikalagaming.graphics.vulkan;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.Arrays;

/**
 * The light the sky gives, for image-based lighting: the sky captured into a cube map, its GGX
 * prefiltered levels for specular reflections by roughness, and its first 9 spherical harmonics
 * coefficients for diffuse light. The sky is at infinity, so all of it is global, worked out again
 * only when the sky changes, see {@code EnvironmentUpdate}.
 *
 * <p>Everything is the sky's own radiance in linear light. The light stage scales it so its average
 * matches the ambient light's color times its intensity: a sky of one color lights as the ambient
 * light alone did, and a textured one sends more light from where it is brighter.
 *
 * <p>Two cube images, each {@link #SIZE} texels a face: the source, the sky as captured with a mip
 * chain to filter from, and the environment, whose level {@code m} is the source convolved with the
 * GGX lobe of roughness {@code m / (ENVIRONMENT_LEVELS - 1)}. Both stay in the general layout,
 * written through storage views of each level and read through cube views, in one set the compute
 * passes and the lighting share.
 *
 * <p>The sky is the skybox, its texture or its color, or a physical sky (see {@link
 * com.ikalagaming.graphics.scene.PhysicalSky}), whose three tables live here too: transmittance
 * through the atmosphere, its multiple scattering, both filled once, and the sky's light toward
 * every direction from the camera, filled every frame the physical sky is on, see {@code
 * atmosphere.comp}. Render thread only.
 */
public class Environment {

    /** The width and height of each face of both cubes, in texels. */
    public static final int SIZE = 128;

    /** The source cube's levels, down to one texel. Mirrors environment.comp. */
    public static final int SOURCE_LEVELS = 8;

    /**
     * The environment cube's levels, from mirror to fully rough. Mirrors environment.comp and
     * {@code ENVIRONMENT_LEVELS} in lighting.glsl.
     */
    public static final int ENVIRONMENT_LEVELS = 6;

    /**
     * The source level the harmonics are taken from, 16 texels a face. Mirrors environment.comp.
     */
    public static final int HARMONICS_LEVEL = 3;

    /** How many spherical harmonics coefficients there are, up to the second band. */
    public static final int HARMONICS_COUNT = 9;

    /** The format of both cubes. */
    public static final int FORMAT = VK_FORMAT_R16G16B16A16_SFLOAT;

    /** Faces of a cube. */
    public static final int FACES = 6;

    /** The binding of the source's levels, as storage images. */
    public static final int SOURCE_LEVELS_BINDING = 0;

    /** The binding of the environment's levels, as storage images. */
    public static final int ENVIRONMENT_LEVELS_BINDING = 1;

    /** The binding of the source cube, sampled. */
    public static final int SOURCE_CUBE_BINDING = 2;

    /** The binding of the environment cube, sampled. */
    public static final int ENVIRONMENT_CUBE_BINDING = 3;

    /** The binding of the harmonics coefficients, a vec4 each. */
    public static final int HARMONICS_BINDING = 4;

    /** The first binding of the physical sky's tables as storage images, one each. */
    public static final int TABLES_STORAGE_BINDING = 5;

    /** The first binding of the physical sky's tables, sampled, one each. */
    public static final int TABLES_SAMPLED_BINDING = 8;

    /** The transmittance table, among the tables. */
    public static final int TRANSMITTANCE = 0;

    /** The multiple scattering table, among the tables. */
    public static final int MULTIPLE_SCATTERING = 1;

    /** The sky view table, among the tables. */
    public static final int SKY_VIEW = 2;

    /** Each table's width, by table. Mirrors atmosphere.glsl. */
    public static final int[] TABLE_WIDTHS = {256, 32, 192};

    /** Each table's height, by table. Mirrors atmosphere.glsl. */
    public static final int[] TABLE_HEIGHTS = {64, 32, 108};

    /** The sky texture slot that stands for the physical sky. */
    public static final int PHYSICAL_SKY = -2;

    /** How many tables there are. */
    private static final int TABLES = 3;

    /** The size of the harmonics buffer. */
    private static final int HARMONICS_SIZE = HARMONICS_COUNT * 4 * Float.BYTES;

    /** Bindings in the set. */
    private static final int BINDINGS = 5 + 2 * TABLES;

    /** The source cube. */
    private final long sourceImage;

    /** Its allocation. */
    private final long sourceAllocation;

    /** The environment cube. */
    private final long environmentImage;

    /** Its allocation. */
    private final long environmentAllocation;

    /** A cube view of each, all levels: source, then environment. */
    private final long[] cubeViews = new long[2];

    /** A view of each level of the source, as an array of its faces. */
    private final long[] sourceLevelViews = new long[SOURCE_LEVELS];

    /** A view of each level of the environment, as an array of its faces. */
    private final long[] environmentLevelViews = new long[ENVIRONMENT_LEVELS];

    /** The harmonics coefficients. */
    private final SharedBuffer harmonics;

    /** The physical sky's tables. */
    private final long[] tableImages = new long[TABLES];

    /** Their allocations. */
    private final long[] tableAllocations = new long[TABLES];

    /** Their views. */
    private final long[] tableViews = new long[TABLES];

    /**
     * Whether the transmittance and multiple scattering tables are filled, which only has to happen
     * once. -- GETTER -- Whether they are filled. -- SETTER -- Note them filled.
     *
     * @param tablesFilled Whether they are filled.
     * @return Whether they are filled.
     */
    @Getter @Setter private boolean tablesFilled;

    /** Filters between texels and levels. */
    private final long sampler;

    /**
     * The set's layout. -- GETTER -- The set's layout.
     *
     * @return The VkDescriptorSetLayout.
     */
    @Getter private final long setLayout;

    /** Where the set comes from. */
    private final long descriptorPool;

    /**
     * The set, for the compute passes and the lighting. -- GETTER -- The set.
     *
     * @return The VkDescriptorSet.
     */
    @Getter private final long set;

    /** The skybox texture's bindless slot the cubes hold, or -1 for its color. */
    private int capturedTexture = Integer.MIN_VALUE;

    /** The skybox color the cubes hold, when it has no texture. */
    private final Vector4f capturedColor = new Vector4f(Float.NaN);

    /** The physical sky's sun and altitude steps the cubes hold, when they hold it. */
    private final int[] capturedSteps = new int[3];

    /**
     * Times the sky was captured and filtered. -- GETTER -- Times the sky was captured.
     *
     * @return The count.
     */
    @Getter private long updates;

    /**
     * Make the cubes, empty, and their set.
     *
     * @param state The Vulkan state.
     */
    public Environment(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            sourceImage = createCube(state, SOURCE_LEVELS, longOutput, stack);
            sourceAllocation = longOutput.get(0);
            environmentImage = createCube(state, ENVIRONMENT_LEVELS, longOutput, stack);
            environmentAllocation = longOutput.get(0);
            cubeViews[0] =
                    createView(
                            state, sourceImage, VK_IMAGE_VIEW_TYPE_CUBE, 0, SOURCE_LEVELS, stack);
            cubeViews[1] =
                    createView(
                            state,
                            environmentImage,
                            VK_IMAGE_VIEW_TYPE_CUBE,
                            0,
                            ENVIRONMENT_LEVELS,
                            stack);
            for (int level = 0; level < SOURCE_LEVELS; ++level) {
                sourceLevelViews[level] =
                        createView(
                                state, sourceImage, VK_IMAGE_VIEW_TYPE_2D_ARRAY, level, 1, stack);
            }
            for (int level = 0; level < ENVIRONMENT_LEVELS; ++level) {
                environmentLevelViews[level] =
                        createView(
                                state,
                                environmentImage,
                                VK_IMAGE_VIEW_TYPE_2D_ARRAY,
                                level,
                                1,
                                stack);
            }
            for (int table = 0; table < TABLES; ++table) {
                tableImages[table] =
                        createTable(
                                state,
                                TABLE_WIDTHS[table],
                                TABLE_HEIGHTS[table],
                                longOutput,
                                stack);
                tableAllocations[table] = longOutput.get(0);
                tableViews[table] = createTableView(state, tableImages[table], stack);
            }
            harmonics =
                    SharedBuffer.allocateDeviceLocal(
                            HARMONICS_SIZE,
                            state,
                            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);

            VkSamplerCreateInfo samplerInfo =
                    VkSamplerCreateInfo.calloc(stack)
                            .sType$Default()
                            .magFilter(VK_FILTER_LINEAR)
                            .minFilter(VK_FILTER_LINEAR)
                            .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                            .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                            .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                            .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                            .minLod(0)
                            .maxLod(SOURCE_LEVELS);
            checkError(vkCreateSampler(state.device.logical, samplerInfo, null, longOutput));
            sampler = longOutput.get(0);

            final int stages = VK_SHADER_STAGE_COMPUTE_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
            VkDescriptorSetLayoutBinding.Buffer bindings =
                    VkDescriptorSetLayoutBinding.calloc(BINDINGS, stack);
            bindings.get(0)
                    .binding(SOURCE_LEVELS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(SOURCE_LEVELS)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            bindings.get(1)
                    .binding(ENVIRONMENT_LEVELS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(ENVIRONMENT_LEVELS)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            bindings.get(2)
                    .binding(SOURCE_CUBE_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(stages);
            bindings.get(3)
                    .binding(ENVIRONMENT_CUBE_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(stages);
            bindings.get(4)
                    .binding(HARMONICS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(stages);
            for (int table = 0; table < TABLES; ++table) {
                bindings.get(5 + table)
                        .binding(TABLES_STORAGE_BINDING + table)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
                bindings.get(5 + TABLES + table)
                        .binding(TABLES_SAMPLED_BINDING + table)
                        .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(1)
                        .stageFlags(stages);
            }
            checkError(
                    vkCreateDescriptorSetLayout(
                            state.device.logical,
                            VkDescriptorSetLayoutCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pBindings(bindings),
                            null,
                            longOutput));
            setLayout = longOutput.get(0);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(3, stack);
            poolSizes
                    .get(0)
                    .type(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(SOURCE_LEVELS + ENVIRONMENT_LEVELS + TABLES);
            poolSizes
                    .get(1)
                    .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(2 + TABLES);
            poolSizes.get(2).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1);
            checkError(
                    vkCreateDescriptorPool(
                            state.device.logical,
                            VkDescriptorPoolCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .maxSets(1)
                                    .pPoolSizes(poolSizes),
                            null,
                            longOutput));
            descriptorPool = longOutput.get(0);
            LongBuffer sets = stack.mallocLong(1);
            checkError(
                    vkAllocateDescriptorSets(
                            state.device.logical,
                            VkDescriptorSetAllocateInfo.calloc(stack)
                                    .sType$Default()
                                    .descriptorPool(descriptorPool)
                                    .pSetLayouts(stack.longs(setLayout)),
                            sets));
            set = sets.get(0);
            writeSet(state, stack);
        }

        // Black until the first capture, in the general layout, where they stay
        state.immediateCommands.submit(
                state,
                commandBuffer -> {
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        clearWhole(commandBuffer, sourceImage, SOURCE_LEVELS, FACES, stack);
                        clearWhole(
                                commandBuffer, environmentImage, ENVIRONMENT_LEVELS, FACES, stack);
                        for (long table : tableImages) {
                            clearWhole(commandBuffer, table, 1, 1, stack);
                        }
                        vkCmdFillBuffer(commandBuffer, harmonics.buffer, 0, HARMONICS_SIZE, 0);
                    }
                });
    }

    /**
     * Whether the sky changed since it was last captured, and if so, note it as captured.
     *
     * @param skyTexture The skybox texture's bindless slot, -1 for its color, or {@link
     *     #PHYSICAL_SKY}.
     * @param skyColor The skybox's color, used without a texture.
     * @param steps For the physical sky, the steps its sun's direction and the camera's altitude
     *     are in, three of them, so the cubes follow it a step at a time; for a skybox texture, its
     *     layout, then zeros.
     * @return True if the cubes should be worked out again now.
     */
    public boolean takeChange(int skyTexture, @NonNull Vector4fc skyColor, int @NonNull [] steps) {
        final boolean changed =
                skyTexture != capturedTexture
                        || skyTexture == -1 && !capturedColor.equals(skyColor)
                        || !Arrays.equals(steps, capturedSteps);
        if (changed) {
            capturedTexture = skyTexture;
            capturedColor.set(skyColor);
            System.arraycopy(steps, 0, capturedSteps, 0, capturedSteps.length);
            updates += 1;
        }
        return changed;
    }

    /** Forget what the cubes hold, so they are worked out again next frame. */
    public void invalidate() {
        capturedTexture = Integer.MIN_VALUE;
    }

    /**
     * Write the set's descriptors.
     *
     * @param state The Vulkan state.
     * @param stack The stack to allocate from.
     */
    private void writeSet(@NonNull VulkanState state, @NonNull MemoryStack stack) {
        VkDescriptorImageInfo.Buffer sourceLevels =
                VkDescriptorImageInfo.calloc(SOURCE_LEVELS, stack);
        for (int level = 0; level < SOURCE_LEVELS; ++level) {
            sourceLevels
                    .get(level)
                    .imageView(sourceLevelViews[level])
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL);
        }
        VkDescriptorImageInfo.Buffer environmentLevels =
                VkDescriptorImageInfo.calloc(ENVIRONMENT_LEVELS, stack);
        for (int level = 0; level < ENVIRONMENT_LEVELS; ++level) {
            environmentLevels
                    .get(level)
                    .imageView(environmentLevelViews[level])
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL);
        }
        VkDescriptorImageInfo.Buffer sourceCube = VkDescriptorImageInfo.calloc(1, stack);
        sourceCube
                .get(0)
                .sampler(sampler)
                .imageView(cubeViews[0])
                .imageLayout(VK_IMAGE_LAYOUT_GENERAL);
        VkDescriptorImageInfo.Buffer environmentCube = VkDescriptorImageInfo.calloc(1, stack);
        environmentCube
                .get(0)
                .sampler(sampler)
                .imageView(cubeViews[1])
                .imageLayout(VK_IMAGE_LAYOUT_GENERAL);
        VkDescriptorBufferInfo.Buffer harmonicsInfo = VkDescriptorBufferInfo.calloc(1, stack);
        harmonicsInfo.get(0).buffer(harmonics.buffer).offset(0).range(HARMONICS_SIZE);

        VkDescriptorImageInfo.Buffer tableStorage = VkDescriptorImageInfo.calloc(TABLES, stack);
        VkDescriptorImageInfo.Buffer tableSampled = VkDescriptorImageInfo.calloc(TABLES, stack);
        for (int table = 0; table < TABLES; ++table) {
            tableStorage
                    .get(table)
                    .imageView(tableViews[table])
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL);
            tableSampled
                    .get(table)
                    .sampler(sampler)
                    .imageView(tableViews[table])
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL);
        }

        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(BINDINGS, stack);
        for (int table = 0; table < TABLES; ++table) {
            writes.get(5 + table)
                    .sType$Default()
                    .dstSet(set)
                    .dstBinding(TABLES_STORAGE_BINDING + table)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1)
                    .pImageInfo(VkDescriptorImageInfo.create(tableStorage.address(table), 1));
            writes.get(5 + TABLES + table)
                    .sType$Default()
                    .dstSet(set)
                    .dstBinding(TABLES_SAMPLED_BINDING + table)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(VkDescriptorImageInfo.create(tableSampled.address(table), 1));
        }
        writes.get(0)
                .sType$Default()
                .dstSet(set)
                .dstBinding(SOURCE_LEVELS_BINDING)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                .descriptorCount(SOURCE_LEVELS)
                .pImageInfo(sourceLevels);
        writes.get(1)
                .sType$Default()
                .dstSet(set)
                .dstBinding(ENVIRONMENT_LEVELS_BINDING)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                .descriptorCount(ENVIRONMENT_LEVELS)
                .pImageInfo(environmentLevels);
        writes.get(2)
                .sType$Default()
                .dstSet(set)
                .dstBinding(SOURCE_CUBE_BINDING)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(sourceCube);
        writes.get(3)
                .sType$Default()
                .dstSet(set)
                .dstBinding(ENVIRONMENT_CUBE_BINDING)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(environmentCube);
        writes.get(4)
                .sType$Default()
                .dstSet(set)
                .dstBinding(HARMONICS_BINDING)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1)
                .pBufferInfo(harmonicsInfo);
        vkUpdateDescriptorSets(state.device.logical, writes, null);
    }

    /**
     * Create a cube image that can be sampled and written as storage.
     *
     * @param state The Vulkan state.
     * @param levels How many mip levels it has.
     * @param allocation Where to put its allocation.
     * @param stack The stack to allocate from.
     * @return The VkImage.
     */
    private static long createCube(
            @NonNull VulkanState state,
            int levels,
            @NonNull LongBuffer allocation,
            @NonNull MemoryStack stack) {
        LongBuffer longOutput = stack.callocLong(1);
        PointerBuffer pointerOutput = stack.callocPointer(1);
        VkImageCreateInfo imageInfo =
                VkImageCreateInfo.calloc(stack)
                        .sType$Default()
                        .flags(VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT)
                        .imageType(VK_IMAGE_TYPE_2D)
                        .format(FORMAT)
                        .extent(e -> e.set(SIZE, SIZE, 1))
                        .mipLevels(levels)
                        .arrayLayers(FACES)
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .tiling(VK_IMAGE_TILING_OPTIMAL)
                        .usage(
                                VK_IMAGE_USAGE_STORAGE_BIT
                                        | VK_IMAGE_USAGE_SAMPLED_BIT
                                        | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
        VmaAllocationCreateInfo allocInfo =
                VmaAllocationCreateInfo.calloc(stack).usage(VMA_MEMORY_USAGE_AUTO);
        checkError(
                vmaCreateImage(
                        state.vmaAllocator, imageInfo, allocInfo, longOutput, pointerOutput, null));
        allocation.put(0, pointerOutput.get(0));
        return longOutput.get(0);
    }

    /**
     * Create one of the physical sky's tables, a 2D image that can be sampled and written as
     * storage.
     *
     * @param state The Vulkan state.
     * @param width Its width.
     * @param height Its height.
     * @param allocation Where to put its allocation.
     * @param stack The stack to allocate from.
     * @return The VkImage.
     */
    private static long createTable(
            @NonNull VulkanState state,
            int width,
            int height,
            @NonNull LongBuffer allocation,
            @NonNull MemoryStack stack) {
        LongBuffer longOutput = stack.callocLong(1);
        PointerBuffer pointerOutput = stack.callocPointer(1);
        VkImageCreateInfo imageInfo =
                VkImageCreateInfo.calloc(stack)
                        .sType$Default()
                        .imageType(VK_IMAGE_TYPE_2D)
                        .format(FORMAT)
                        .extent(e -> e.set(width, height, 1))
                        .mipLevels(1)
                        .arrayLayers(1)
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .tiling(VK_IMAGE_TILING_OPTIMAL)
                        .usage(
                                VK_IMAGE_USAGE_STORAGE_BIT
                                        | VK_IMAGE_USAGE_SAMPLED_BIT
                                        | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
        VmaAllocationCreateInfo allocInfo =
                VmaAllocationCreateInfo.calloc(stack).usage(VMA_MEMORY_USAGE_AUTO);
        checkError(
                vmaCreateImage(
                        state.vmaAllocator, imageInfo, allocInfo, longOutput, pointerOutput, null));
        allocation.put(0, pointerOutput.get(0));
        return longOutput.get(0);
    }

    /**
     * Create a view of one of the tables.
     *
     * @param state The Vulkan state.
     * @param image The VkImage.
     * @param stack The stack to allocate from.
     * @return The VkImageView.
     */
    private static long createTableView(
            @NonNull VulkanState state, long image, @NonNull MemoryStack stack) {
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
                                                .levelCount(1)
                                                .layerCount(1));
        checkError(vkCreateImageView(state.device.logical, viewInfo, null, longOutput));
        return longOutput.get(0);
    }

    /**
     * Create a view of a cube's faces.
     *
     * @param state The Vulkan state.
     * @param image The VkImage.
     * @param type The VkImageViewType: a cube, or an array of the faces.
     * @param baseLevel The first mip level it shows.
     * @param levels How many mip levels it shows.
     * @param stack The stack to allocate from.
     * @return The VkImageView.
     */
    private static long createView(
            @NonNull VulkanState state,
            long image,
            int type,
            int baseLevel,
            int levels,
            @NonNull MemoryStack stack) {
        LongBuffer longOutput = stack.callocLong(1);
        VkImageViewCreateInfo viewInfo =
                VkImageViewCreateInfo.calloc(stack)
                        .sType$Default()
                        .image(image)
                        .viewType(type)
                        .format(FORMAT)
                        .subresourceRange(
                                range ->
                                        range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                .baseMipLevel(baseLevel)
                                                .levelCount(levels)
                                                .layerCount(FACES));
        checkError(vkCreateImageView(state.device.logical, viewInfo, null, longOutput));
        return longOutput.get(0);
    }

    /**
     * Move a new image to the general layout and clear it to black.
     *
     * @param commandBuffer The command buffer to record into.
     * @param image The VkImage.
     * @param levels How many mip levels it has.
     * @param layers How many layers it has.
     * @param stack The stack to allocate from.
     */
    private static void clearWhole(
            @NonNull VkCommandBuffer commandBuffer,
            long image,
            int levels,
            int layers,
            @NonNull MemoryStack stack) {
        VkImageMemoryBarrier2.Buffer barrier = VkImageMemoryBarrier2.calloc(1, stack);
        barrier.get(0)
                .sType$Default()
                .srcStageMask(VK_PIPELINE_STAGE_2_NONE)
                .srcAccessMask(VK_ACCESS_2_NONE)
                .dstStageMask(VK_PIPELINE_STAGE_2_CLEAR_BIT)
                .dstAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .newLayout(VK_IMAGE_LAYOUT_GENERAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image)
                .subresourceRange(
                        range ->
                                range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                        .levelCount(levels)
                                        .layerCount(layers));
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barrier));
        VkImageSubresourceRange.Buffer range = VkImageSubresourceRange.calloc(1, stack);
        range.get(0).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(levels).layerCount(layers);
        vkCmdClearColorImage(
                commandBuffer,
                image,
                VK_IMAGE_LAYOUT_GENERAL,
                VkClearColorValue.calloc(stack),
                range);
        barrier.get(0)
                .srcStageMask(VK_PIPELINE_STAGE_2_CLEAR_BIT)
                .srcAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                .dstStageMask(
                        VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT
                                | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT)
                .dstAccessMask(
                        VK_ACCESS_2_SHADER_STORAGE_READ_BIT
                                | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT
                                | VK_ACCESS_2_SHADER_SAMPLED_READ_BIT)
                .oldLayout(VK_IMAGE_LAYOUT_GENERAL)
                .newLayout(VK_IMAGE_LAYOUT_GENERAL);
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barrier));
    }

    /**
     * Destroy the cubes, the buffer and the set. The GPU must be idle.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        vkDestroyDescriptorPool(state.device.logical, descriptorPool, null);
        vkDestroyDescriptorSetLayout(state.device.logical, setLayout, null);
        for (long view : cubeViews) {
            vkDestroyImageView(state.device.logical, view, null);
        }
        for (long view : sourceLevelViews) {
            vkDestroyImageView(state.device.logical, view, null);
        }
        for (long view : environmentLevelViews) {
            vkDestroyImageView(state.device.logical, view, null);
        }
        vmaDestroyImage(state.vmaAllocator, sourceImage, sourceAllocation);
        vmaDestroyImage(state.vmaAllocator, environmentImage, environmentAllocation);
        for (int table = 0; table < TABLES; ++table) {
            vkDestroyImageView(state.device.logical, tableViews[table], null);
            vmaDestroyImage(state.vmaAllocator, tableImages[table], tableAllocations[table]);
        }
        vkDestroySampler(state.device.logical, sampler, null);
        SharedBuffer.free(harmonics, state);
    }
}
