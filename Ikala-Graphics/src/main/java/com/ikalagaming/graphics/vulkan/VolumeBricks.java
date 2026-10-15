package com.ikalagaming.graphics.vulkan;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.graph.VolumeMath;
import com.ikalagaming.graphics.graph.VolumeVoxelizer;
import com.ikalagaming.graphics.lod.LodStore;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Volume bricks, which stand in for sections' cut out and translucent blocks past {@link
 * com.ikalagaming.graphics.GraphicsSettings#volumeDistance}: one brick of {@link VolumeMath#BRICK}
 * cubed voxels per section, each holding its surfaces' extinction and average color (see {@link
 * VolumeMath}), in slots of one 3D atlas image.
 *
 * <p>Bricks are voxelized on the bake workers with their sections' bakes (see {@link
 * VolumeVoxelizer}), and put here as their bakes go in; {@code VolumeUpload} copies the queued ones
 * into their slots a budget at a time. Each frame it also lists the bricks to draw: those of
 * sections that don't draw their own blocks now, which the transparency stages ray march.
 *
 * <p>The atlas stays in the general layout, which compute writes it in, as uploads do. Render
 * thread only.
 */
@Slf4j
public class VolumeBricks {

    /** The atlas's format: color, and the square root of the extinction's share in alpha. */
    public static final int FORMAT = VK_FORMAT_R8G8B8A8_UNORM;

    /** The binding compute shaders write the atlas through, as uploads do. */
    public static final int STORAGE_BINDING = 0;

    /** The binding fragment shaders sample the atlas through. */
    public static final int SAMPLED_BINDING = 1;

    /**
     * The binding of each slot's cell masks: which of its 4 cubed cells of 4 cubed voxels hold
     * something solid, then anything at all, 64 bits each, so walks skip the empty ones.
     */
    public static final int MASKS_BINDING = 2;

    /** The size of one slot's masks: two 64-bit masks. */
    public static final int MASKS_SIZE = 4 * Integer.BYTES;

    /**
     * The binding of each voxel's material, two 16-bit indices a word, 0 for none or the index plus
     * 1, set for solid voxels, so their faces show their material's texture.
     */
    public static final int MATERIALS_BINDING = 3;

    /** The size of one slot's materials: two bytes a voxel. */
    public static final int MATERIALS_SIZE =
            VolumeMath.BRICK * VolumeMath.BRICK * VolumeMath.BRICK * Short.BYTES;

    /** The size of one brick's texels. */
    public static final int TEXELS_SIZE =
            VolumeMath.BRICK * VolumeMath.BRICK * VolumeMath.BRICK * VolumeMath.BYTES_PER_VOXEL;

    /**
     * The size of one slot's surface bits, which the occupancy volume reads for sections whose own
     * meshes are off the GPU, see {@link VolumeVoxelizer.Brick#surfaces}.
     */
    public static final int SURFACES_SIZE = VolumeVoxelizer.SURFACE_INTS * Integer.BYTES;

    /**
     * The size of one brick's block in an upload buffer: its texels, then its masks, its materials
     * and its surface bits.
     */
    public static final int STAGED_SIZE = TEXELS_SIZE + MASKS_SIZE + MATERIALS_SIZE + SURFACES_SIZE;

    /**
     * The size of a draw in the draw list: the brick's minimum corner in render space, its slot.
     */
    public static final int DRAW_SIZE = 4 * Float.BYTES;

    /**
     * A brick to copy into its slot.
     *
     * @param position Its section.
     * @param slot The slot.
     * @param brick The brick.
     */
    public record Upload(
            LodStore.@NonNull SectionPos position,
            int slot,
            VolumeVoxelizer.@NonNull Brick brick) {}

    /** The layout of the atlas's set. -- GETTER -- The set layout. */
    @Getter private long setLayout;

    /** The atlas's set. -- GETTER -- The set. */
    @Getter private long set;

    /** The pool the set comes from. */
    private long descriptorPool;

    /** The atlas image. */
    private long image;

    /** Its allocation. */
    private long imageAllocation;

    /** The view of the whole atlas. */
    private long view;

    /** Filters within a brick, clamped at the atlas's edge. */
    private long sampler;

    /** Each slot's cell masks. -- GETTER -- The masks buffer. */
    @Getter private SharedBuffer masks;

    /** Each voxel's material. -- GETTER -- The materials buffer. */
    @Getter private SharedBuffer materials;

    /** Each slot's surface bits, read by address. -- GETTER -- The surfaces buffer. */
    @Getter private SharedBuffer surfaces;

    /** Each section's slot. */
    private final Map<LodStore.SectionPos, Integer> slots = new HashMap<>();

    /** Slots no section has. */
    private final ArrayDeque<Integer> free = new ArrayDeque<>();

    /** Whether each slot has had a brick copied in, so it can be drawn. */
    private final boolean[] filled = new boolean[VolumeMath.SLOTS];

    /** Bricks waiting to be copied into their sections' slots, oldest first. */
    private final Map<LodStore.SectionPos, VolumeVoxelizer.Brick> uploads = new LinkedHashMap<>();

    /** How the blocks in an upload buffer are aligned, for the shader's reads. */
    private static final long BLOCK_ALIGNMENT = 16;

    /** Each frame in flight's upload buffer, written by the CPU, or null before its first use. */
    private final SharedBuffer[] uploadBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Each frame in flight's draw list, written by the CPU. */
    private final SharedBuffer[] drawLists = new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** How many draws each frame's list has. */
    private final int[] drawCounts = new int[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Each frame in flight's nearest solid face at each pixel, see {@code VolumeDepth}. */
    private final SharedBuffer[] hitBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /**
     * How far the camera moves before the draw order is sorted again, in meters: nearest first only
     * matters roughly, for the depth test to drop most of what is behind.
     */
    private static final double RESORT_DISTANCE = 4;

    /** The sections the draw order was sorted from, the same list while they don't change. */
    private List<LodStore.SectionPos> sortedFrom;

    /** The draw order, nearest first. */
    private List<LodStore.SectionPos> sorted = List.of();

    /** Where the camera was when the draw order was sorted, or null. */
    private Vector3d sortedAt;

    /** Whether the atlas was full, so it is only said once. */
    private boolean warnedFull;

    /** Sections that wanted a brick while the atlas was full, and still have none. */
    private final Set<LodStore.SectionPos> withoutSlot = new HashSet<>();

    /**
     * Set up the atlas, cleared to empty in the general layout.
     *
     * @param state The Vulkan state.
     */
    public VolumeBricks(@NonNull VulkanState state) {
        for (int slot = 0; slot < VolumeMath.SLOTS; ++slot) {
            free.add(slot);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            PointerBuffer pointerOutput = stack.callocPointer(1);
            VkImageCreateInfo imageInfo =
                    VkImageCreateInfo.calloc(stack)
                            .sType$Default()
                            .imageType(VK_IMAGE_TYPE_3D)
                            .format(FORMAT)
                            .extent(
                                    e ->
                                            e.set(
                                                    VolumeMath.SLOTS_X * VolumeMath.BRICK,
                                                    VolumeMath.SLOTS_Y * VolumeMath.BRICK,
                                                    VolumeMath.SLOTS_Z * VolumeMath.BRICK))
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

            VkDescriptorSetLayoutBinding.Buffer bindings =
                    VkDescriptorSetLayoutBinding.calloc(4, stack);
            bindings.get(0)
                    .binding(STORAGE_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            bindings.get(1)
                    .binding(SAMPLED_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            bindings.get(2)
                    .binding(MASKS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            bindings.get(3)
                    .binding(MATERIALS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
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
            poolSizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(1);
            poolSizes.get(1).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1);
            poolSizes.get(2).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(2);
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
            VkDescriptorImageInfo.Buffer storage = VkDescriptorImageInfo.calloc(1, stack);
            storage.get(0).imageView(view).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
            VkDescriptorImageInfo.Buffer sampled = VkDescriptorImageInfo.calloc(1, stack);
            sampled.get(0).sampler(sampler).imageView(view).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
            masks =
                    SharedBuffer.allocateDeviceLocal(
                            (long) VolumeMath.SLOTS * MASKS_SIZE,
                            state,
                            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            VkDescriptorBufferInfo.Buffer maskInfo = VkDescriptorBufferInfo.calloc(1, stack);
            maskInfo.get(0).buffer(masks.buffer).offset(0).range(VK_WHOLE_SIZE);
            materials =
                    SharedBuffer.allocateDeviceLocal(
                            (long) VolumeMath.SLOTS * MATERIALS_SIZE,
                            state,
                            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            surfaces =
                    SharedBuffer.allocateDeviceLocal(
                            (long) VolumeMath.SLOTS * SURFACES_SIZE,
                            state,
                            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            VkDescriptorBufferInfo.Buffer materialInfo = VkDescriptorBufferInfo.calloc(1, stack);
            materialInfo.get(0).buffer(materials.buffer).offset(0).range(VK_WHOLE_SIZE);
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(4, stack);
            writes.get(0)
                    .sType$Default()
                    .dstSet(set)
                    .dstBinding(STORAGE_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1)
                    .pImageInfo(storage);
            writes.get(1)
                    .sType$Default()
                    .dstSet(set)
                    .dstBinding(SAMPLED_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(sampled);
            writes.get(2)
                    .sType$Default()
                    .dstSet(set)
                    .dstBinding(MASKS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(maskInfo);
            writes.get(3)
                    .sType$Default()
                    .dstSet(set)
                    .dstBinding(MATERIALS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(materialInfo);
            vkUpdateDescriptorSets(state.device.logical, writes, null);
        }
        for (int i = 0; i < drawLists.length; ++i) {
            drawLists[i] =
                    SharedBuffer.allocate(
                            (long) DRAW_SIZE * 1024, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        }
        state.immediateCommands.submit(
                state,
                commandBuffer -> {
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        clear(commandBuffer, stack);
                    }
                });
    }

    /**
     * Move the new atlas to the general layout and clear it.
     *
     * @param commandBuffer The command buffer.
     * @param stack The stack to allocate on.
     */
    private void clear(@NonNull VkCommandBuffer commandBuffer, @NonNull MemoryStack stack) {
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
                                        .levelCount(1)
                                        .layerCount(1));
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barrier));
        VkImageSubresourceRange.Buffer range = VkImageSubresourceRange.calloc(1, stack);
        range.get(0).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);
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
                        VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT | VK_ACCESS_2_SHADER_SAMPLED_READ_BIT)
                .oldLayout(VK_IMAGE_LAYOUT_GENERAL)
                .newLayout(VK_IMAGE_LAYOUT_GENERAL);
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barrier));
    }

    /**
     * Give a section a new brick, queued to be copied into its slot. A section gets a slot the
     * first time, if the atlas has one free.
     *
     * @param position The section.
     * @param brick Its brick, or null if it has nothing to stand in for, which gives its slot back.
     */
    public void put(LodStore.@NonNull SectionPos position, VolumeVoxelizer.Brick brick) {
        if (brick == null) {
            remove(position);
            return;
        }
        if (!slots.containsKey(position)) {
            if (free.isEmpty()) {
                if (!warnedFull) {
                    log.warn(
                            "The volume atlas is full, at {} bricks; sections past the volume"
                                    + " distance go without their cut out and translucent blocks",
                            VolumeMath.SLOTS);
                    warnedFull = true;
                }
                withoutSlot.add(position);
                return;
            }
            slots.put(position, free.poll());
            withoutSlot.remove(position);
        }
        uploads.put(position, brick);
    }

    /**
     * Take a section's brick out, giving its slot back.
     *
     * @param position The section.
     */
    public void remove(LodStore.@NonNull SectionPos position) {
        uploads.remove(position);
        release(position);
    }

    /**
     * Take the next bricks to copy into their slots, up to a budget, and count them as drawable:
     * the caller records the copies before this frame's draws.
     *
     * @param maxBricks The most bricks.
     * @return The bricks and their slots.
     */
    public List<Upload> takeUploads(int maxBricks) {
        final List<Upload> out = new ArrayList<>();
        final Iterator<Map.Entry<LodStore.SectionPos, VolumeVoxelizer.Brick>> it =
                uploads.entrySet().iterator();
        while (it.hasNext() && out.size() < maxBricks) {
            final Map.Entry<LodStore.SectionPos, VolumeVoxelizer.Brick> entry = it.next();
            it.remove();
            final int slot = slots.get(entry.getKey());
            filled[slot] = true;
            out.add(new Upload(entry.getKey(), slot, entry.getValue()));
        }
        return out;
    }

    /**
     * Write bricks into a frame's upload buffer, for {@code VolumeUpload} to copy into their slots:
     * each brick's slot, then from {@link #blocksOffset} each brick's block of {@link #STAGED_SIZE}
     * bytes, its texels first. The buffer is only rewritten once that frame is done with it.
     *
     * @param frame The frame in flight.
     * @param state The Vulkan state.
     * @param batch The bricks.
     * @return The buffer.
     */
    public SharedBuffer writeUploads(
            int frame, @NonNull VulkanState state, @NonNull List<Upload> batch) {
        final long bytes = blocksOffset(batch.size()) + (long) batch.size() * STAGED_SIZE;
        SharedBuffer buffer = uploadBuffers[frame];
        if (buffer == null) {
            buffer =
                    SharedBuffer.allocate(
                            bytes,
                            state,
                            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
            uploadBuffers[frame] = buffer;
        }
        buffer.ensureCapacity(bytes, state);
        final ByteBuffer data =
                MemoryUtil.memByteBuffer(buffer.allocationInfo.pMappedData(), (int) bytes);
        final int blocks = (int) blocksOffset(batch.size());
        for (int i = 0; i < batch.size(); ++i) {
            data.putInt(i * Integer.BYTES, batch.get(i).slot());
            final VolumeVoxelizer.Brick brick = batch.get(i).brick();
            final int base = blocks + i * STAGED_SIZE;
            data.put(base, brick.texels());
            for (int mask = 0; mask < brick.cellMasks().length; ++mask) {
                data.putInt(base + TEXELS_SIZE + mask * Integer.BYTES, brick.cellMasks()[mask]);
            }
            final short[] voxelMaterials = brick.materials();
            for (int voxel = 0; voxel < voxelMaterials.length; ++voxel) {
                data.putShort(
                        base + TEXELS_SIZE + MASKS_SIZE + voxel * Short.BYTES,
                        voxelMaterials[voxel]);
            }
            final int[] voxelSurfaces = brick.surfaces();
            for (int word = 0; word < voxelSurfaces.length; ++word) {
                data.putInt(
                        base + TEXELS_SIZE + MASKS_SIZE + MATERIALS_SIZE + word * Integer.BYTES,
                        voxelSurfaces[word]);
            }
        }
        return buffer;
    }

    /**
     * Where the bricks' blocks start in an upload buffer: after their slots, aligned to 16 bytes.
     *
     * @param count How many bricks it holds.
     * @return The offset, in bytes.
     */
    public static long blocksOffset(int count) {
        final long slotBytes = (long) count * Integer.BYTES;
        return (slotBytes + BLOCK_ALIGNMENT - 1) / BLOCK_ALIGNMENT * BLOCK_ALIGNMENT;
    }

    /**
     * Record copying the bricks' masks, materials and surface bits from a frame's upload buffer
     * into their slots. The texels go by compute, which writes the atlas in the layout it stays in.
     *
     * @param commandBuffer The command buffer.
     * @param buffer The upload buffer, as {@link #writeUploads} wrote it.
     * @param batch The bricks.
     * @param stack The stack to allocate on.
     */
    public void recordCopies(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull SharedBuffer buffer,
            @NonNull List<Upload> batch,
            @NonNull MemoryStack stack) {
        final VkBufferCopy.Buffer maskRegions = VkBufferCopy.calloc(batch.size(), stack);
        final VkBufferCopy.Buffer materialRegions = VkBufferCopy.calloc(batch.size(), stack);
        final VkBufferCopy.Buffer surfaceRegions = VkBufferCopy.calloc(batch.size(), stack);
        final long blocks = blocksOffset(batch.size());
        for (int i = 0; i < batch.size(); ++i) {
            final int slot = batch.get(i).slot();
            final long base = blocks + (long) i * STAGED_SIZE;
            maskRegions
                    .get(i)
                    .srcOffset(base + TEXELS_SIZE)
                    .dstOffset((long) slot * MASKS_SIZE)
                    .size(MASKS_SIZE);
            materialRegions
                    .get(i)
                    .srcOffset(base + TEXELS_SIZE + MASKS_SIZE)
                    .dstOffset((long) slot * MATERIALS_SIZE)
                    .size(MATERIALS_SIZE);
            surfaceRegions
                    .get(i)
                    .srcOffset(base + TEXELS_SIZE + MASKS_SIZE + MATERIALS_SIZE)
                    .dstOffset((long) slot * SURFACES_SIZE)
                    .size(SURFACES_SIZE);
        }
        vkCmdCopyBuffer(commandBuffer, buffer.buffer, masks.buffer, maskRegions);
        vkCmdCopyBuffer(commandBuffer, buffer.buffer, materials.buffer, materialRegions);
        vkCmdCopyBuffer(commandBuffer, buffer.buffer, surfaces.buffer, surfaceRegions);
    }

    /**
     * Give a section's slot back, if it has one.
     *
     * @param position The section.
     */
    private void release(LodStore.@NonNull SectionPos position) {
        withoutSlot.remove(position);
        final Integer slot = slots.remove(position);
        if (slot != null) {
            filled[slot] = false;
            free.add(slot);
        }
    }

    /**
     * Write this frame's draw list: every voxelized brick of the given sections, its corner
     * relative to the camera, nearest first. The order is kept while the sections are the same list
     * and the camera stays within {@link #RESORT_DISTANCE} of where it was sorted for.
     *
     * @param frame The frame in flight.
     * @param state The Vulkan state.
     * @param sections The sections drawn as volumes.
     * @param camera The camera's position, in the world.
     * @return How many bricks are drawn.
     */
    public int writeDraws(
            int frame,
            @NonNull VulkanState state,
            @NonNull List<LodStore.SectionPos> sections,
            @NonNull Vector3dc camera) {
        final SharedBuffer buffer = drawLists[frame];
        buffer.ensureCapacity((long) Math.max(1, sections.size()) * DRAW_SIZE, state);
        final ByteBuffer out =
                MemoryUtil.memByteBuffer(
                        buffer.allocationInfo.pMappedData(),
                        Math.max(1, sections.size()) * DRAW_SIZE);
        final double size = VolumeMath.BRICK * VolumeMath.VOXEL_SIZE;
        // Nearest first, so the depth pass writes the near faces before the far boxes behind them,
        // which the depth test then drops before their shaders run
        final double half = size / 2;
        if (sections != sortedFrom
                || sortedAt == null
                || sortedAt.distance(camera) > RESORT_DISTANCE) {
            final List<LodStore.SectionPos> ordered = new ArrayList<>(sections);
            ordered.sort(
                    Comparator.comparingDouble(
                            position -> {
                                final double dx = position.x() * size + half - camera.x();
                                final double dy = position.y() * size + half - camera.y();
                                final double dz = position.z() * size + half - camera.z();
                                return dx * dx + dy * dy + dz * dz;
                            }));
            sorted = ordered;
            sortedFrom = sections;
            sortedAt = new Vector3d(camera);
        }
        final List<LodStore.SectionPos> ordered = sorted;
        int count = 0;
        for (LodStore.SectionPos position : ordered) {
            final Integer slot = slots.get(position);
            if (slot == null || !filled[slot]) {
                continue;
            }
            final int base = count * DRAW_SIZE;
            out.putFloat(base, (float) (position.x() * size - camera.x()));
            out.putFloat(base + Float.BYTES, (float) (position.y() * size - camera.y()));
            out.putFloat(base + 2 * Float.BYTES, (float) (position.z() * size - camera.z()));
            out.putInt(base + 3 * Float.BYTES, slot);
            ++count;
        }
        drawCounts[frame] = count;
        return count;
    }

    /**
     * A frame's draw list's device address.
     *
     * @param frame The frame in flight.
     * @return The address.
     */
    public long drawList(int frame) {
        return drawLists[frame].deviceAddress;
    }

    /**
     * A frame's buffer of each pixel's nearest solid face, big enough for some pixels. Only the GPU
     * fills it; a frame's buffer is only replaced once that frame is done with it.
     *
     * @param frame The frame in flight.
     * @param state The Vulkan state.
     * @param pixels How many pixels it holds.
     * @return The buffer.
     */
    public SharedBuffer hits(int frame, @NonNull VulkanState state, long pixels) {
        final long bytes = pixels * Integer.BYTES;
        SharedBuffer buffer = hitBuffers[frame];
        if (buffer == null || buffer.allocationInfo.size() < bytes) {
            if (buffer != null) {
                SharedBuffer.free(buffer, state);
            }
            buffer =
                    SharedBuffer.allocateDeviceLocal(
                            bytes,
                            state,
                            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
            hitBuffers[frame] = buffer;
        }
        return buffer;
    }

    /**
     * How many bricks a frame draws.
     *
     * @param frame The frame in flight.
     * @return The count.
     */
    public int drawCount(int frame) {
        return drawCounts[frame];
    }

    /**
     * Whether a section's brick shows its blocks as they are now: copied in, and no newer one
     * waiting.
     *
     * @param position The section.
     * @return True if it is up to date.
     */
    public boolean isCurrent(LodStore.@NonNull SectionPos position) {
        return currentSlot(position) >= 0;
    }

    /**
     * The slot of a section's brick, if it shows its blocks as they are now.
     *
     * @param position The section.
     * @return The slot, or -1 if it has none or a newer brick is waiting.
     */
    public int currentSlot(LodStore.@NonNull SectionPos position) {
        final Integer slot = slots.get(position);
        return slot != null && filled[slot] && !uploads.containsKey(position) ? slot : -1;
    }

    /**
     * How many sections have a brick.
     *
     * @return The count.
     */
    public int brickCount() {
        return slots.size();
    }

    /**
     * How many sections wanted a brick while the atlas was full, and still have none.
     *
     * @return The count.
     */
    public int withoutSlotCount() {
        return withoutSlot.size();
    }

    /**
     * How many bricks wait to be copied in.
     *
     * @return The count.
     */
    public int queuedCount() {
        return uploads.size();
    }

    /**
     * Destroy the atlas and everything with it.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        for (int i = 0; i < drawLists.length; ++i) {
            SharedBuffer.free(drawLists[i], state);
            drawLists[i] = null;
            if (uploadBuffers[i] != null) {
                SharedBuffer.free(uploadBuffers[i], state);
                uploadBuffers[i] = null;
            }
            if (hitBuffers[i] != null) {
                SharedBuffer.free(hitBuffers[i], state);
                hitBuffers[i] = null;
            }
        }
        SharedBuffer.free(masks, state);
        masks = null;
        SharedBuffer.free(materials, state);
        materials = null;
        SharedBuffer.free(surfaces, state);
        surfaces = null;
        vkDestroyDescriptorPool(state.device.logical, descriptorPool, null);
        vkDestroyDescriptorSetLayout(state.device.logical, setLayout, null);
        vkDestroySampler(state.device.logical, sampler, null);
        vkDestroyImageView(state.device.logical, view, null);
        vmaDestroyImage(state.vmaAllocator, image, imageAllocation);
    }
}
