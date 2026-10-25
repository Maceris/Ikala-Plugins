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
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Volume bricks, which stand in for sections' cut out and translucent blocks past {@link
 * com.ikalagaming.graphics.GraphicsSettings#volumeDistance}: one brick of {@link VolumeMath#BRICK}
 * cubed voxels per section, each holding its surfaces' extinction and average color (see {@link
 * VolumeMath}), in slots of one 3D atlas image.
 *
 * <p>Bricks are voxelized on the bake workers with their sections' bakes (see {@link
 * VolumeVoxelizer}), and put here as their bakes go in; {@code VolumeUpload} copies the queued ones
 * into their slots a budget at a time. Each frame it also lists the bricks to draw: those of
 * sections that don't draw their own blocks now, which the transparency stages ray march, and the
 * bricks to cast shadows: those of sections whose own cut out and translucent meshes are off the
 * GPU, which the shadow stage walks along the sun.
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

    /** Each frame in flight's list of bricks casting shadows, written by the CPU. */
    private final SharedBuffer[] shadowLists =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** How many bricks each frame's shadow list has. */
    private final int[] shadowCounts = new int[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Each frame in flight's nearest solid face at each pixel, see {@code VolumeDepth}. */
    private final SharedBuffer[] hitBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /**
     * How far the camera moves before the draw order is sorted again, in meters: nearest first only
     * matters roughly, for the depth test to drop most of what is behind.
     */
    private static final double RESORT_DISTANCE = 4;

    /**
     * Some sections, such as those drawn as volumes: which they are now, picked from others, and a
     * version that changes whenever they do. Far out they can be many more than fit the atlas, so
     * the bricks pick theirs from their own slots rather than look up every one.
     */
    public interface Members {
        /** No sections. */
        Members NONE =
                new Members() {
                    @Override
                    public long version() {
                        return 0;
                    }

                    @Override
                    public List<LodStore.SectionPos> among(
                            @NonNull Collection<LodStore.SectionPos> candidates) {
                        return List.of();
                    }

                    @Override
                    public long changesSince(
                            long version,
                            @NonNull BiConsumer<LodStore.SectionPos, Boolean> visitor) {
                        return 0;
                    }
                };

        /**
         * A number that changes whenever the sections do.
         *
         * @return The version.
         */
        long version();

        /**
         * Which of some sections are among these.
         *
         * @param candidates The sections.
         * @return Those that are, in the candidates' order.
         */
        List<LodStore.SectionPos> among(@NonNull Collection<LodStore.SectionPos> candidates);

        /**
         * The sections that entered or left these since a version, oldest first.
         *
         * @param version The version last seen.
         * @param visitor What to do with each section and whether it entered.
         * @return The version now, or -1 if the changes since aren't all known, so the sections
         *     must be looked at whole.
         */
        long changesSince(long version, @NonNull BiConsumer<LodStore.SectionPos, Boolean> visitor);
    }

    /**
     * What one set of sections is known to hold of the slotted ones, kept up with the set's changes
     * and the slots' instead of looked up for every slot each time either changes.
     */
    private static final class Tracked {
        /** Whether each slot's section is in the set. */
        final boolean[] in = new boolean[VolumeMath.SLOTS];

        /** Slots given to another section since, whose section must be looked up. */
        final BitSet moved = new BitSet(VolumeMath.SLOTS);

        /** The set's version last taken, or -1 to look at it whole. */
        long version = -1;
    }

    /** What each set of sections drawn from is known to hold, by the set. */
    private final Map<Members, Tracked> tracked = new IdentityHashMap<>();

    /** Each slot's section, or null for a free one. */
    private final LodStore.SectionPos[] slotPositions = new LodStore.SectionPos[VolumeMath.SLOTS];

    /** The sections the draw order was sorted from. */
    private Members sortedFrom;

    /** Their version when it was. */
    private long sortedVersion;

    /**
     * One brick to draw.
     *
     * @param position Its section.
     * @param slot Its slot in the atlas.
     * @param distance How far its middle is from the camera, squared.
     */
    private record Draw(LodStore.@NonNull SectionPos position, int slot, double distance) {}

    /** The bricks to draw, nearest first. */
    private List<Draw> sorted = List.of();

    /** Whether a slot was taken, filled or given back since the draws were sorted. */
    private boolean slotsChanged = true;

    /**
     * Counts every slot taken, filled or given back, so lists made from the slots know to change.
     */
    private long slotVersion;

    /** The sections the shadow list was made from. */
    private Members shadowsFrom;

    /** Their version when it was. */
    private long shadowsVersion;

    /** The {@link #slotVersion} the shadow list was made at. */
    private long shadowsAt = -1;

    /** The bricks casting shadows, with no order. */
    private List<Draw> shadows = List.of();

    /** Where the camera was when the draw order was sorted, or null. */
    private Vector3d sortedAt;

    /** Whether the atlas was full, so it is only said once. */
    private boolean warnedFull;

    /** Sections that wanted a brick while the atlas was full, and still have none. */
    private final Set<LodStore.SectionPos> withoutSlot = new HashSet<>();

    /**
     * Every section's brick, slotted or not, kept on the CPU so slots can move to the sections
     * nearest the camera without voxelizing again.
     */
    private final Map<LodStore.SectionPos, VolumeVoxelizer.Brick> held = new HashMap<>();

    /** Where the camera was when slots were last handed to the nearest sections, or null. */
    private Vector3d balancedAt;

    /** Whether a section without a slot came since slots were last handed out. */
    private boolean waitingChanged;

    /** Frames since slots were last handed out. */
    private int framesSinceBalance;

    /**
     * How far the camera moves before slots are handed to the nearest sections again, in meters.
     */
    private static final double REBALANCE_DISTANCE = 8;

    /** How many frames new sections without a slot wait, at most, to be weighed for one. */
    private static final int REBALANCE_FRAMES = 30;

    /**
     * How much nearer, in meters, a section without a slot must be than one with a slot to take it,
     * so two at about the same distance don't swap back and forth.
     */
    private static final double SWAP_MARGIN = 16;

    /** The most slots that move to nearer sections in one go; the rest move next time. */
    private static final int MAX_SWAPS = 256;

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
            shadowLists[i] =
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
        held.put(position, brick);
        if (!slots.containsKey(position)) {
            if (free.isEmpty()) {
                if (!warnedFull) {
                    log.warn(
                            "The volume atlas is full, at {} bricks; the sections nearest the"
                                    + " camera keep theirs, and the rest go without their cut out"
                                    + " and translucent blocks",
                            VolumeMath.SLOTS);
                    warnedFull = true;
                }
                withoutSlot.add(position);
                waitingChanged = true;
                return;
            }
            take(position);
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
        held.remove(position);
        release(position);
    }

    /**
     * While the atlas is full, hand its slots to the sections nearest the camera: free slots to the
     * nearest without one, then each farthest slotted section's to a nearer one without, by more
     * than a margin. Weighed again once the camera moves, now and then while new sections wait, and
     * whenever a slot is free. Render thread, before the frame's uploads are taken.
     *
     * @param camera The camera's position, in the world.
     */
    public void rebalance(@NonNull Vector3dc camera) {
        ++framesSinceBalance;
        if (withoutSlot.isEmpty()) {
            return;
        }
        final boolean moved =
                balancedAt == null || balancedAt.distance(camera) > REBALANCE_DISTANCE;
        final boolean due = waitingChanged && framesSinceBalance >= REBALANCE_FRAMES;
        if (free.isEmpty() && !moved && !due) {
            return;
        }
        balancedAt = new Vector3d(camera);
        waitingChanged = false;
        framesSinceBalance = 0;
        // No more than MAX_SWAPS move, so only that many of the nearest waiting and the farthest
        // slotted are needed, out of what can be tens of thousands
        final List<LodStore.SectionPos> waiting = extremes(withoutSlot, camera, MAX_SWAPS, false);
        int next = 0;
        int moves = 0;
        while (next < waiting.size() && !free.isEmpty() && moves < MAX_SWAPS) {
            assign(waiting.get(next++));
            ++moves;
        }
        if (next >= waiting.size() || moves >= MAX_SWAPS) {
            return;
        }
        final List<LodStore.SectionPos> slotted = extremes(slots.keySet(), camera, MAX_SWAPS, true);
        for (int far = 0; far < slotted.size() && next < waiting.size() && moves < MAX_SWAPS; ) {
            final LodStore.SectionPos near = waiting.get(next);
            final LodStore.SectionPos out = slotted.get(far);
            if (Math.sqrt(distanceSquared(near, camera)) + SWAP_MARGIN
                    >= Math.sqrt(distanceSquared(out, camera))) {
                break;
            }
            // The far one keeps its brick on the CPU, and waits for a slot again
            uploads.remove(out);
            release(out);
            withoutSlot.add(out);
            assign(near);
            ++next;
            ++far;
            ++moves;
        }
    }

    /**
     * The sections nearest the camera, or farthest from it, each distance worked out once.
     *
     * @param sections The sections.
     * @param camera The camera's position.
     * @param most How many at most.
     * @param farthest Whether the farthest rather than the nearest.
     * @return They, nearest first, or farthest first.
     */
    private static List<LodStore.SectionPos> extremes(
            @NonNull Collection<LodStore.SectionPos> sections,
            @NonNull Vector3dc camera,
            int most,
            boolean farthest) {
        // Keyed so that the heap's head is the one to drop first: the farthest of the nearest kept
        final double sign = farthest ? -1 : 1;
        final PriorityQueue<Draw> kept =
                new PriorityQueue<>(
                        most + 1, Comparator.comparingDouble(Draw::distance).reversed());
        for (LodStore.SectionPos position : sections) {
            final double key = sign * distanceSquared(position, camera);
            if (kept.size() < most) {
                kept.add(new Draw(position, 0, key));
            } else if (key < kept.peek().distance()) {
                kept.poll();
                kept.add(new Draw(position, 0, key));
            }
        }
        final List<Draw> ordered = new ArrayList<>(kept);
        ordered.sort(Comparator.comparingDouble(Draw::distance));
        final List<LodStore.SectionPos> out = new ArrayList<>(ordered.size());
        for (Draw draw : ordered) {
            out.add(draw.position());
        }
        return out;
    }

    /**
     * Give a section without a slot a free one, and queue its brick to be copied in.
     *
     * @param position The section.
     */
    private void assign(LodStore.@NonNull SectionPos position) {
        final VolumeVoxelizer.Brick brick = held.get(position);
        withoutSlot.remove(position);
        if (brick == null) {
            return;
        }
        take(position);
        uploads.put(position, brick);
    }

    /**
     * How far a section's brick's middle is from the camera, squared.
     *
     * @param position The section.
     * @param camera The camera's position.
     * @return The squared distance, in square meters.
     */
    private static double distanceSquared(
            LodStore.@NonNull SectionPos position, @NonNull Vector3dc camera) {
        final double size = VolumeMath.BRICK * VolumeMath.VOXEL_SIZE;
        final double dx = position.x() * size + size / 2 - camera.x();
        final double dy = position.y() * size + size / 2 - camera.y();
        final double dz = position.z() * size + size / 2 - camera.z();
        return dx * dx + dy * dy + dz * dz;
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
            slotsChanged = true;
            ++slotVersion;
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
            slotsChanged = true;
            ++slotVersion;
            slotPositions[slot] = null;
            for (Tracked set : tracked.values()) {
                set.moved.set(slot);
            }
            free.add(slot);
        }
    }

    /**
     * Write this frame's draw list: every voxelized brick of the given sections, its corner
     * relative to the camera, nearest first. The order is kept while the sections stay the same and
     * the camera stays within {@link #RESORT_DISTANCE} of where it was sorted for.
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
            @NonNull Members sections,
            @NonNull Vector3dc camera) {
        final SharedBuffer buffer = drawLists[frame];
        buffer.ensureCapacity((long) Math.max(1, slots.size()) * DRAW_SIZE, state);
        final ByteBuffer out =
                MemoryUtil.memByteBuffer(
                        buffer.allocationInfo.pMappedData(), Math.max(1, slots.size()) * DRAW_SIZE);
        final double size = VolumeMath.BRICK * VolumeMath.VOXEL_SIZE;
        // Nearest first, so the depth pass writes the near faces before the far boxes behind them,
        // which the depth test then drops before their shaders run
        final double half = size / 2;
        // Only filled bricks are sorted, each distance worked out once: the sections drawn as
        // volumes change most frames while the camera moves, and are many more than fit the atlas
        final long version = sections.version();
        if (sections != sortedFrom
                || version != sortedVersion
                || slotsChanged
                || sortedAt == null
                || sortedAt.distance(camera) > RESORT_DISTANCE) {
            final List<Draw> draws = new ArrayList<>();
            for (Draw draw : filledIn(sections)) {
                final LodStore.SectionPos position = draw.position();
                final double dx = position.x() * size + half - camera.x();
                final double dy = position.y() * size + half - camera.y();
                final double dz = position.z() * size + half - camera.z();
                draws.add(new Draw(position, draw.slot(), dx * dx + dy * dy + dz * dz));
            }
            draws.sort(Comparator.comparingDouble(Draw::distance));
            sorted = draws;
            sortedFrom = sections;
            sortedVersion = version;
            sortedAt = new Vector3d(camera);
            slotsChanged = false;
        }
        int count = 0;
        for (Draw draw : sorted) {
            final LodStore.SectionPos position = draw.position();
            final int base = count * DRAW_SIZE;
            out.putFloat(base, (float) (position.x() * size - camera.x()));
            out.putFloat(base + Float.BYTES, (float) (position.y() * size - camera.y()));
            out.putFloat(base + 2 * Float.BYTES, (float) (position.z() * size - camera.z()));
            out.putInt(base + 3 * Float.BYTES, draw.slot());
            ++count;
        }
        drawCounts[frame] = count;
        return count;
    }

    /**
     * Write this frame's shadow list: the filled brick of each of the given sections, its corner
     * relative to the camera, in the draw list's layout. Each is walked along the sun into every
     * shadow map drawn this frame, which clip what is outside them. Which bricks those are is kept
     * while the sections and the slots stay the same.
     *
     * @param frame The frame in flight.
     * @param state The Vulkan state.
     * @param sections The sections whose bricks stand in for their meshes.
     * @param camera The camera's position, in the world.
     * @return How many bricks cast shadows.
     */
    public int writeShadowDraws(
            int frame,
            @NonNull VulkanState state,
            @NonNull Members sections,
            @NonNull Vector3dc camera) {
        final SharedBuffer buffer = shadowLists[frame];
        buffer.ensureCapacity((long) Math.max(1, slots.size()) * DRAW_SIZE, state);
        final ByteBuffer out =
                MemoryUtil.memByteBuffer(
                        buffer.allocationInfo.pMappedData(), Math.max(1, slots.size()) * DRAW_SIZE);
        final double size = VolumeMath.BRICK * VolumeMath.VOXEL_SIZE;
        final long version = sections.version();
        if (sections != shadowsFrom || version != shadowsVersion || shadowsAt != slotVersion) {
            shadows = filledIn(sections);
            shadowsFrom = sections;
            shadowsVersion = version;
            shadowsAt = slotVersion;
        }
        int count = 0;
        for (Draw draw : shadows) {
            final LodStore.SectionPos position = draw.position();
            final int base = count * DRAW_SIZE;
            out.putFloat(base, (float) (position.x() * size - camera.x()));
            out.putFloat(base + Float.BYTES, (float) (position.y() * size - camera.y()));
            out.putFloat(base + 2 * Float.BYTES, (float) (position.z() * size - camera.z()));
            out.putInt(base + 3 * Float.BYTES, draw.slot());
            ++count;
        }
        shadowCounts[frame] = count;
        return count;
    }

    /**
     * Which of some sections have a filled slot, picked from the filled slots: far out, many more
     * sections stand in as bricks than fit the atlas.
     *
     * @param sections The sections.
     * @return Each one with a filled slot and its slot, no distance, in no order.
     */
    private List<Draw> filledIn(@NonNull Members sections) {
        final Tracked set = tracked.computeIfAbsent(sections, key -> new Tracked());
        final long version =
                set.version < 0
                        ? -1
                        : sections.changesSince(
                                set.version,
                                (position, entered) -> {
                                    final Integer slot = slots.get(position);
                                    if (slot != null) {
                                        set.in[slot] = entered;
                                    }
                                });
        if (version < 0) {
            // Looked at whole: every slotted section
            Arrays.fill(set.in, false);
            set.moved.clear();
            final long now = sections.version();
            for (LodStore.SectionPos position : sections.among(slots.keySet())) {
                set.in[slots.get(position)] = true;
            }
            set.version = now;
        } else {
            set.version = version;
            if (!set.moved.isEmpty()) {
                // Only the slots given to another section since
                final List<LodStore.SectionPos> movedTo = new ArrayList<>();
                for (int slot = set.moved.nextSetBit(0);
                        slot >= 0;
                        slot = set.moved.nextSetBit(slot + 1)) {
                    set.in[slot] = false;
                    if (slotPositions[slot] != null) {
                        movedTo.add(slotPositions[slot]);
                    }
                }
                set.moved.clear();
                for (LodStore.SectionPos position : sections.among(movedTo)) {
                    set.in[slots.get(position)] = true;
                }
            }
        }
        final List<Draw> out = new ArrayList<>();
        for (int slot = 0; slot < VolumeMath.SLOTS; ++slot) {
            if (set.in[slot] && filled[slot] && slotPositions[slot] != null) {
                out.add(new Draw(slotPositions[slot], slot, 0));
            }
        }
        return out;
    }

    /**
     * Give a section a free slot, noting which section the slot is for now.
     *
     * @param position The section.
     */
    private void take(LodStore.@NonNull SectionPos position) {
        final int slot = free.poll();
        slots.put(position, slot);
        slotPositions[slot] = position;
        slotsChanged = true;
        ++slotVersion;
        for (Tracked set : tracked.values()) {
            set.moved.set(slot);
        }
    }

    /**
     * A frame's shadow list's device address.
     *
     * @param frame The frame in flight.
     * @return The address.
     */
    public long shadowList(int frame) {
        return shadowLists[frame].deviceAddress;
    }

    /**
     * How many bricks cast shadows in a frame.
     *
     * @param frame The frame in flight.
     * @return The count.
     */
    public int shadowCount(int frame) {
        return shadowCounts[frame];
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
            SharedBuffer.free(shadowLists[i], state);
            shadowLists[i] = null;
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
