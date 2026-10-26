package com.ikalagaming.graphics.vulkan;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.graph.CoarseBricks;
import com.ikalagaming.graphics.graph.OccupancyClipmap;
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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
 * <p>Far out, where a voxel is too small on screen to tell apart, coarse bricks stand in for
 * several sections' bricks: a brick of level L covers 2^L sections along each side with voxels 2^L
 * meters wide, downsampled from theirs (see {@link CoarseBricks}), so the atlas covers far more
 * ground. Each cell of 2 cubed sections picks its level by its distance from the camera, the cells
 * of a level 2 brick all together, and keeps its old bricks drawn until the new ones are in.
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

    /** How many values a slot's corner has in {@link #slotCorners}. */
    private static final int CORNER_VALUES = PackedPositions.CORNER_VALUES;

    /**
     * Each slot's section's place, x, y and z, in sections, set as it is taken: the draw lists are
     * written from these every frame, for thousands of bricks.
     */
    private final int[] slotCorners = new int[VolumeMath.SLOTS * CORNER_VALUES];

    /** The sections the draw order was sorted from. */
    private Members sortedFrom;

    /** Their version when it was. */
    private long sortedVersion;

    /**
     * The bricks to draw, nearest first: each one's squared distance as float bits in the high
     * half, which sort as the distances do since they are never negative, and its slot in the low
     * half.
     */
    private final long[] sorted = new long[VolumeMath.SLOTS];

    /** How many of {@link #sorted} are bricks to draw. */
    private int sortedCount;

    /** The slots of the bricks this frame's lists are made from, a scratch list. */
    private final int[] filledSlots = new int[VolumeMath.SLOTS];

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

    /** The slots of the bricks casting shadows, in slot order. */
    private final int[] shadows = new int[VolumeMath.SLOTS];

    /** How many of {@link #shadows} are bricks casting shadows. */
    private int shadowCount;

    /** Where the camera was when the draw order was sorted, or null. */
    private Vector3d sortedAt;

    /** Whether the atlas was full, so it is only said once. */
    private boolean warnedFull;

    /** Sections that wanted a brick while the atlas was full, and still have none. */
    private final PackedPositions withoutSlot = new PackedPositions();

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
     * Where a draw's level sits in its slot word, above the slot: the shaders scale the brick's box
     * and walk by 2^level.
     */
    public static final int LEVEL_SHIFT = 28;

    /** How far the camera moves before cells pick their levels again, in meters. */
    private static final double RING_DISTANCE = 8;

    /**
     * How far past where a level starts a cell must be to start drawing at it, and how far inside
     * to stop, in meters, so cells at the edge don't flip back and forth.
     */
    private static final double RING_MARGIN = 16;

    /**
     * How far past the volume distance and the occupancy volume coarse bricks start at least, in
     * meters: nearer, sections' own bricks feed the occupancy volume and hand over to their blocks.
     */
    private static final double COARSE_CLEARANCE = 32;

    /** The most frames a cell keeps its old bricks drawn while its new ones come in. */
    private static final int HANDOVER_FRAMES = 120;

    /** Threads building coarse bricks. */
    private static final int COARSE_THREADS = 2;

    /** Each slot's brick's level: 0 for a section's own, more for a coarse brick. */
    private final int[] slotLevels = new int[VolumeMath.SLOTS];

    /** How many slots hold bricks of each level. */
    private final int[] levelCounts = new int[CoarseBricks.MAX_LEVEL + 1];

    /**
     * How many times each brick is wanted drawn: a section's own by its cell, a level 1 brick by
     * its cell, a level 2 brick by each of its cells drawn at level 2, and each of them once more
     * while a hand over keeps it.
     */
    private final Map<LodStore.SectionPos, Integer> wanted = new HashMap<>();

    /** Coarse bricks built for wanted coarse keys. */
    private final Map<LodStore.SectionPos, VolumeVoxelizer.Brick> coarse = new HashMap<>();

    /** The build of each coarse key being built now, whose result is the one taken. */
    private final Map<LodStore.SectionPos, Integer> building = new HashMap<>();

    /**
     * Coarse keys to build, as their sections' bricks change: while a world loads, a level 2
     * brick's 64 sections come in one by one, so builds start a few a frame, one at a time for each
     * key.
     */
    private final Set<LodStore.SectionPos> dirty = new LinkedHashSet<>();

    /** The most coarse builds started in a frame. */
    private static final int BUILDS_PER_FRAME = 64;

    /** Builds handed out, for numbering them. */
    private int builds;

    /**
     * A coarse brick built on a worker.
     *
     * @param key Its key.
     * @param build Which build it was.
     * @param brick The brick, or null if its sections have nothing.
     */
    private record Built(
            LodStore.@NonNull SectionPos key, int build, VolumeVoxelizer.Brick brick) {}

    /** Coarse bricks built, waiting for the render thread. */
    private final Queue<Built> built = new ConcurrentLinkedQueue<>();

    /** The threads building coarse bricks, or null before the first build. */
    private ExecutorService coarseWorkers;

    /** A cell of 2 cubed sections, of those holding a brick, and the level it draws at. */
    private static final class Cell {
        /** Its key: its corner, in sections, at level 1. */
        final LodStore.SectionPos key;

        /** How many of its sections hold a brick. */
        int members;

        /** The level it draws at: 0 for its sections' own bricks, 1 its own, 2 its parent's. */
        int level;

        Cell(LodStore.@NonNull SectionPos key) {
            this.key = key;
        }
    }

    /** A cell of 4 cubed sections, of the cells inside it, which draw at level 2 together. */
    private static final class Parent {
        /** Its key: its corner, in sections, at level 2. */
        final LodStore.SectionPos key;

        /** Its cells, x fastest, null where none holds a brick. */
        final Cell[] cells = new Cell[CHILDREN];

        /** How many of its cells there are. */
        int count;

        /** How many of its cells draw at level 1 or coarser. */
        int coarseCells;

        /** Whether it draws whole at level 2. */
        boolean whole;

        /** Its place in {@link #parentList}. */
        int index;

        Parent(LodStore.@NonNull SectionPos key) {
            this.key = key;
        }
    }

    /** Cells along a side of a parent, and cells in it. */
    private static final int CHILDREN_SPAN = 2;

    private static final int CHILDREN = CHILDREN_SPAN * CHILDREN_SPAN * CHILDREN_SPAN;

    /** The cells, by key. */
    private final Map<LodStore.SectionPos, Cell> cells = new HashMap<>();

    /** The parents, by key, and in a list to go through. */
    private final Map<LodStore.SectionPos, Parent> parents = new HashMap<>();

    private final List<Parent> parentList = new ArrayList<>();

    /** Where each level starts drawing, in meters, as of the last time the rings were weighed. */
    private double[] ringDistances = {0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};

    /** Where the camera was when the rings were last weighed, or null. */
    private Vector3d ringsAt;

    /**
     * A cell's change of level: the bricks it drew from, released once the bricks it draws now are
     * in, or after {@link #HANDOVER_FRAMES}.
     *
     * @param from The bricks it drew from.
     * @param to The bricks it draws now.
     * @param deadline The frame they are released at, at the latest.
     */
    private record Handover(
            @NonNull List<LodStore.SectionPos> from,
            @NonNull List<LodStore.SectionPos> to,
            long deadline) {}

    /** Hand overs not done yet. */
    private final List<Handover> handovers = new ArrayList<>();

    /** Frames counted by {@link #rebalance}. */
    private long frameCount;

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
        final boolean added = held.put(position, brick) == null;
        final Cell cell = added ? join(position) : cells.get(keyAt(position, 1));
        if (wanted.containsKey(position)) {
            // Drawn now, or kept by a hand over: the new brick replaces the old
            if (slots.containsKey(position)) {
                uploads.put(position, brick);
            } else if (!withoutSlot.contains(position)) {
                giveSlot(position, brick);
            }
        } else if (cell.level == 0) {
            want(position);
        }
        rebuildCoarse(position);
    }

    /**
     * Take a section's brick out, giving its slot back.
     *
     * @param position The section.
     */
    public void remove(LodStore.@NonNull SectionPos position) {
        uploads.remove(position);
        release(position);
        if (held.remove(position) == null) {
            return;
        }
        final Cell cell = cells.get(keyAt(position, 1));
        if (cell.level == 0) {
            unwant(position);
        }
        leave(cell);
        rebuildCoarse(position);
    }

    /**
     * Give a key its slot, or a free one, and queue its brick to be copied in; when the atlas is
     * full it waits for one.
     *
     * @param key The key.
     * @param brick Its brick.
     */
    private void giveSlot(LodStore.@NonNull SectionPos key, VolumeVoxelizer.@NonNull Brick brick) {
        if (!slots.containsKey(key)) {
            if (free.isEmpty()) {
                if (!warnedFull) {
                    log.warn(
                            "The volume atlas is full, at {} bricks; the sections nearest the"
                                    + " camera keep theirs, and the rest go without their cut out"
                                    + " and translucent blocks",
                            VolumeMath.SLOTS);
                    warnedFull = true;
                }
                withoutSlot.add(key);
                waitingChanged = true;
                return;
            }
            take(key);
            withoutSlot.remove(key);
        }
        uploads.put(key, brick);
    }

    /**
     * The key of the brick of a level covering a section: the corner, in sections, of the cube of
     * 2^level sections along each side it is in.
     *
     * @param position The section.
     * @param level The level.
     * @return The key.
     */
    static LodStore.SectionPos keyAt(LodStore.@NonNull SectionPos position, int level) {
        final int span = CoarseBricks.span(level);
        return new LodStore.SectionPos(
                Math.floorDiv(position.x(), span) * span,
                Math.floorDiv(position.y(), span) * span,
                Math.floorDiv(position.z(), span) * span,
                level);
    }

    /**
     * Note a section that now holds a brick in its cell, making the cell, and its parent, if it is
     * the first; a new cell picks its level from the last rings weighed.
     *
     * @param position The section.
     * @return Its cell.
     */
    private Cell join(LodStore.@NonNull SectionPos position) {
        final LodStore.SectionPos key = keyAt(position, 1);
        Cell cell = cells.get(key);
        if (cell == null) {
            cell = new Cell(key);
            cells.put(key, cell);
            final LodStore.SectionPos parentKey = keyAt(key, 2);
            Parent parent = parents.get(parentKey);
            if (parent == null) {
                parent = new Parent(parentKey);
                parent.index = parentList.size();
                parents.put(parentKey, parent);
                parentList.add(parent);
                if (ringsAt != null) {
                    parent.whole =
                            CoarseBricks.reaches(
                                    boxDistance(parentKey, ringsAt),
                                    ringDistances[2],
                                    false,
                                    RING_MARGIN);
                }
            }
            parent.cells[childIndex(key)] = cell;
            ++parent.count;
            ++cell.members;
            final int level = ringsAt == null ? 0 : levelOf(cell, parent, ringsAt);
            if (level > 0) {
                cell.level = level;
                ++parent.coarseCells;
                want(represented(cell, level).getFirst());
            }
            return cell;
        }
        ++cell.members;
        return cell;
    }

    /**
     * Note that a section of a cell no longer holds a brick, taking the cell, and its parent, out
     * once they have none.
     *
     * @param cell The cell.
     */
    private void leave(@NonNull Cell cell) {
        if (--cell.members > 0) {
            return;
        }
        if (cell.level > 0) {
            unwant(represented(cell, cell.level).getFirst());
        }
        cells.remove(cell.key);
        final Parent parent = parents.get(keyAt(cell.key, 2));
        parent.cells[childIndex(cell.key)] = null;
        if (cell.level > 0) {
            --parent.coarseCells;
        }
        if (--parent.count == 0) {
            parents.remove(parent.key);
            final Parent last = parentList.removeLast();
            if (last != parent) {
                parentList.set(parent.index, last);
                last.index = parent.index;
            }
        }
    }

    /**
     * Where a cell sits in its parent, x fastest.
     *
     * @param key The cell's key.
     * @return Its index.
     */
    private static int childIndex(LodStore.@NonNull SectionPos key) {
        final int span = CoarseBricks.span(1);
        return Math.floorMod(key.x() / span, CHILDREN_SPAN)
                + CHILDREN_SPAN
                        * (Math.floorMod(key.y() / span, CHILDREN_SPAN)
                                + CHILDREN_SPAN * Math.floorMod(key.z() / span, CHILDREN_SPAN));
    }

    /**
     * The bricks a cell draws from at a level: its sections' own, its own, or its parent's.
     *
     * @param cell The cell.
     * @param level The level.
     * @return The keys.
     */
    private List<LodStore.SectionPos> represented(@NonNull Cell cell, int level) {
        if (level > 0) {
            return List.of(keyAt(cell.key, level));
        }
        final List<LodStore.SectionPos> out = new ArrayList<>();
        final int span = CoarseBricks.span(1);
        for (int z = 0; z < span; ++z) {
            for (int y = 0; y < span; ++y) {
                for (int x = 0; x < span; ++x) {
                    final LodStore.SectionPos position =
                            new LodStore.SectionPos(
                                    cell.key.x() + x, cell.key.y() + y, cell.key.z() + z);
                    if (held.containsKey(position)) {
                        out.add(position);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Want a brick drawn once more; the first time, give it a slot, or build it if it is coarse.
     *
     * @param key The brick's key.
     */
    private void want(LodStore.@NonNull SectionPos key) {
        if (wanted.merge(key, 1, Integer::sum) > 1) {
            return;
        }
        if (key.level() == 0) {
            final VolumeVoxelizer.Brick brick = held.get(key);
            if (brick != null) {
                giveSlot(key, brick);
            }
        } else {
            build(key);
        }
    }

    /**
     * Want a brick drawn once less; once it isn't wanted, give its slot back.
     *
     * @param key The brick's key.
     */
    private void unwant(LodStore.@NonNull SectionPos key) {
        final Integer count = wanted.get(key);
        if (count == null) {
            return;
        }
        if (count > 1) {
            wanted.put(key, count - 1);
            return;
        }
        wanted.remove(key);
        uploads.remove(key);
        release(key);
        if (key.level() > 0) {
            coarse.remove(key);
            building.remove(key);
            dirty.remove(key);
        }
    }

    /**
     * Build again the coarse bricks over a section whose brick changed, those that are wanted.
     *
     * @param position The section.
     */
    private void rebuildCoarse(LodStore.@NonNull SectionPos position) {
        for (int level = 1; level <= CoarseBricks.MAX_LEVEL; ++level) {
            final LodStore.SectionPos key = keyAt(position, level);
            if (wanted.containsKey(key)) {
                build(key);
            }
        }
    }

    /**
     * Have a coarse brick built from its sections' bricks as they are now, soon.
     *
     * @param key The coarse brick's key.
     */
    private void build(LodStore.@NonNull SectionPos key) {
        dirty.add(key);
    }

    /**
     * Start building the coarse bricks marked, a budget a frame, each only once its last build is
     * back, so a key's builds don't pile up.
     */
    private void startBuilds() {
        final Iterator<LodStore.SectionPos> it = dirty.iterator();
        int started = 0;
        while (it.hasNext() && started < BUILDS_PER_FRAME) {
            final LodStore.SectionPos key = it.next();
            if (!wanted.containsKey(key)) {
                it.remove();
                continue;
            }
            if (building.containsKey(key)) {
                continue;
            }
            it.remove();
            startBuild(key);
            ++started;
        }
    }

    /**
     * Build a coarse brick on the workers from its sections' bricks as they are now.
     *
     * @param key The coarse brick's key.
     */
    private void startBuild(LodStore.@NonNull SectionPos key) {
        final int span = CoarseBricks.span(key.level());
        final VolumeVoxelizer.Brick[] children = new VolumeVoxelizer.Brick[span * span * span];
        for (int z = 0; z < span; ++z) {
            for (int y = 0; y < span; ++y) {
                for (int x = 0; x < span; ++x) {
                    children[x + span * (y + span * z)] =
                            held.get(
                                    new LodStore.SectionPos(key.x() + x, key.y() + y, key.z() + z));
                }
            }
        }
        final int build = ++builds;
        building.put(key, build);
        coarseWorkers()
                .execute(
                        () ->
                                built.add(
                                        new Built(
                                                key,
                                                build,
                                                CoarseBricks.downsample(key.level(), children))));
    }

    /**
     * The threads building coarse bricks, started the first time.
     *
     * @return The threads.
     */
    private ExecutorService coarseWorkers() {
        if (coarseWorkers == null) {
            final AtomicInteger count = new AtomicInteger();
            coarseWorkers =
                    Executors.newFixedThreadPool(
                            COARSE_THREADS,
                            runnable -> {
                                final Thread thread =
                                        new Thread(
                                                runnable,
                                                "Coarse bricks " + count.incrementAndGet());
                                thread.setDaemon(true);
                                thread.setPriority(Thread.MIN_PRIORITY);
                                return thread;
                            });
        }
        return coarseWorkers;
    }

    /** Take in the coarse bricks built since, those still wanted from their latest builds. */
    private void takeBuilt() {
        Built done;
        while ((done = built.poll()) != null) {
            final Integer latest = building.get(done.key());
            if (latest == null || latest != done.build()) {
                continue;
            }
            building.remove(done.key());
            if (!wanted.containsKey(done.key())) {
                continue;
            }
            if (done.brick() == null) {
                // Its sections have nothing to draw now
                coarse.remove(done.key());
                uploads.remove(done.key());
                release(done.key());
            } else {
                coarse.put(done.key(), done.brick());
                giveSlot(done.key(), done.brick());
            }
        }
    }

    /**
     * Weigh again which level each cell draws at, once the camera moved far enough or the rings
     * changed: a parent draws whole at level 2 past where it starts, otherwise each cell of it at
     * level 1 past where that starts, or as its sections' own bricks.
     *
     * @param camera The camera's position, in the world.
     * @param pixelsPerMeter Pixels a meter covers at a distance of one meter.
     */
    private void weighRings(@NonNull Vector3dc camera, double pixelsPerMeter) {
        final double volumeDistance = GraphicsManager.getSettings().volumeDistance;
        final double[] distances;
        if (volumeDistance <= 0
                || pixelsPerMeter <= 0
                || !GraphicsManager.getSettings().coarseBricks) {
            distances = new double[] {0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        } else {
            final double occupancyReach =
                    OccupancyClipmap.SIZE
                            * OccupancyClipmap.VOXEL_SIZES[OccupancyClipmap.LEVELS - 1]
                            / 2.0;
            distances =
                    CoarseBricks.distances(
                            pixelsPerMeter,
                            GraphicsManager.getSettings().lodThresholdPixels,
                            Math.max(VolumeMath.fadeEnd(volumeDistance), occupancyReach)
                                    + COARSE_CLEARANCE);
        }
        final boolean changed = !Arrays.equals(distances, ringDistances);
        if (!changed && ringsAt != null && ringsAt.distance(camera) <= RING_DISTANCE) {
            return;
        }
        ringDistances = distances;
        ringsAt = new Vector3d(camera);
        for (Parent parent : parentList) {
            final double away = boxDistance(parent.key, camera);
            parent.whole = CoarseBricks.reaches(away, distances[2], parent.whole, RING_MARGIN);
            // Its cells only change if it is whole, was, or one of them is or could be coarse
            final double diagonal =
                    Math.sqrt(3) * CoarseBricks.span(2) * VolumeMath.BRICK * VolumeMath.VOXEL_SIZE;
            if (!parent.whole
                    && parent.coarseCells == 0
                    && away + diagonal < distances[1] - RING_MARGIN) {
                continue;
            }
            for (Cell cell : parent.cells) {
                if (cell != null) {
                    final int level = levelOf(cell, parent, camera);
                    if (level != cell.level) {
                        changeLevel(cell, parent, level);
                    }
                }
            }
        }
    }

    /**
     * The level a cell draws at now.
     *
     * @param cell The cell.
     * @param parent Its parent, whose {@link Parent#whole} is up to date.
     * @param camera The camera's position.
     * @return The level.
     */
    private int levelOf(@NonNull Cell cell, @NonNull Parent parent, @NonNull Vector3dc camera) {
        if (parent.whole) {
            return 2;
        }
        return CoarseBricks.reaches(
                        boxDistance(cell.key, camera),
                        ringDistances[1],
                        cell.level >= 1,
                        RING_MARGIN)
                ? 1
                : 0;
    }

    /**
     * Have a cell draw at another level: want its new bricks now, and release the old ones once the
     * new are in.
     *
     * @param cell The cell.
     * @param parent Its parent.
     * @param level The new level.
     */
    private void changeLevel(@NonNull Cell cell, @NonNull Parent parent, int level) {
        final List<LodStore.SectionPos> from = represented(cell, cell.level);
        final List<LodStore.SectionPos> to = represented(cell, level);
        for (LodStore.SectionPos key : to) {
            want(key);
        }
        parent.coarseCells += (level > 0 ? 1 : 0) - (cell.level > 0 ? 1 : 0);
        cell.level = level;
        handovers.add(new Handover(from, to, frameCount + HANDOVER_FRAMES));
    }

    /** Release the old bricks of hand overs whose new bricks are in, or that waited long enough. */
    private void finishHandovers() {
        final Iterator<Handover> it = handovers.iterator();
        while (it.hasNext()) {
            final Handover handover = it.next();
            boolean ready = frameCount >= handover.deadline();
            if (!ready) {
                ready = true;
                for (LodStore.SectionPos key : handover.to()) {
                    ready &= settled(key);
                }
            }
            if (ready) {
                for (LodStore.SectionPos key : handover.from()) {
                    unwant(key);
                }
                it.remove();
            }
        }
    }

    /**
     * Whether a wanted brick is as drawn as it will get for now: copied in, or with nothing to
     * draw, or waiting for a slot in a full atlas.
     *
     * @param key The brick's key.
     * @return True if so.
     */
    private boolean settled(LodStore.@NonNull SectionPos key) {
        if (!wanted.containsKey(key) || withoutSlot.contains(key)) {
            return true;
        }
        if (key.level() > 0 && (building.containsKey(key) || dirty.contains(key))) {
            return false;
        }
        if (key.level() == 0 ? !held.containsKey(key) : !coarse.containsKey(key)) {
            return true;
        }
        return isIn(key);
    }

    /**
     * How far a brick's box is from a point, 0 inside it.
     *
     * @param key The brick's key, whose level gives its size.
     * @param point The point, in the world.
     * @return The distance, in meters.
     */
    private static double boxDistance(LodStore.@NonNull SectionPos key, @NonNull Vector3dc point) {
        final double section = VolumeMath.BRICK * VolumeMath.VOXEL_SIZE;
        final double size = section * CoarseBricks.span(key.level());
        final double[] min = {key.x() * section, key.y() * section, key.z() * section};
        final double[] at = {point.x(), point.y(), point.z()};
        double sum = 0;
        for (int axis = 0; axis < at.length; ++axis) {
            final double d =
                    Math.max(0, Math.max(min[axis] - at[axis], at[axis] - (min[axis] + size)));
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    /**
     * A wanted brick's brick on the CPU: a section's own, or a coarse one built.
     *
     * @param key The brick's key.
     * @return The brick, or null if there is none yet.
     */
    private VolumeVoxelizer.Brick brickOf(LodStore.@NonNull SectionPos key) {
        return key.level() == 0 ? held.get(key) : coarse.get(key);
    }

    /**
     * While the atlas is full, hand its slots to the sections nearest the camera: free slots to the
     * nearest without one, then each farthest slotted section's to a nearer one without, by more
     * than a margin. Weighed again once the camera moves, now and then while new sections wait, and
     * whenever a slot is free. First, the cells pick their levels, and coarse bricks built come in.
     * Render thread, before the frame's uploads are taken.
     *
     * @param camera The camera's position, in the world.
     * @param pixelsPerMeter Pixels a meter covers at a distance of one meter, for the levels.
     */
    public void rebalance(@NonNull Vector3dc camera, double pixelsPerMeter) {
        ++frameCount;
        takeBuilt();
        weighRings(camera, pixelsPerMeter);
        startBuilds();
        finishHandovers();
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
        final List<LodStore.SectionPos> waiting =
                extremes(
                        withoutSlot.positions(),
                        withoutSlot.corners(),
                        withoutSlot.size(),
                        camera,
                        MAX_SWAPS,
                        false);
        int next = 0;
        int moves = 0;
        while (next < waiting.size() && !free.isEmpty() && moves < MAX_SWAPS) {
            assign(waiting.get(next++));
            ++moves;
        }
        if (next >= waiting.size() || moves >= MAX_SWAPS) {
            return;
        }
        final List<LodStore.SectionPos> slotted =
                extremes(slotPositions, slotCorners, VolumeMath.SLOTS, camera, MAX_SWAPS, true);
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
    static List<LodStore.SectionPos> extremes(
            @NonNull Collection<LodStore.SectionPos> sections,
            @NonNull Vector3dc camera,
            int most,
            boolean farthest) {
        final LodStore.SectionPos[] positions = sections.toArray(new LodStore.SectionPos[0]);
        final int[] corners = new int[positions.length * CORNER_VALUES];
        for (int i = 0; i < positions.length; ++i) {
            corners[i * CORNER_VALUES] = positions[i].x();
            corners[i * CORNER_VALUES + 1] = positions[i].y();
            corners[i * CORNER_VALUES + 2] = positions[i].z();
        }
        return extremes(positions, corners, positions.length, camera, most, farthest);
    }

    /**
     * The sections nearest the camera, or farthest from it, from flat arrays, which go through tens
     * of thousands far faster than a set does.
     *
     * @param positions The sections, null where there is none, which is skipped.
     * @param corners Each one's corner, x, y and z in sections, in the same order.
     * @param count How many of the arrays to look at.
     * @param camera The camera's position.
     * @param most How many at most.
     * @param farthest Whether the farthest rather than the nearest.
     * @return They, nearest first, or farthest first.
     */
    static List<LodStore.SectionPos> extremes(
            LodStore.SectionPos @NonNull [] positions,
            int @NonNull [] corners,
            int count,
            @NonNull Vector3dc camera,
            int most,
            boolean farthest) {
        final double size = VolumeMath.BRICK * VolumeMath.VOXEL_SIZE;
        final double half = size / 2;
        // Each kept one's key, smaller first wanted: its squared distance as float bits, which sort
        // as the distances do since they are never negative, turned around for the farthest. The
        // keys are a heap with the largest, the one to drop first, at its head, and each kept
        // section's index is at its key's index, moved along with it
        final int[] keys = new int[Math.max(0, most)];
        final int[] kept = new int[keys.length];
        int heapSize = 0;
        for (int i = 0; i < count; ++i) {
            if (positions[i] == null) {
                continue;
            }
            final int corner = i * CORNER_VALUES;
            final double dx = corners[corner] * size + half - camera.x();
            final double dy = corners[corner + 1] * size + half - camera.y();
            final double dz = corners[corner + 2] * size + half - camera.z();
            final int bits = Float.floatToRawIntBits((float) (dx * dx + dy * dy + dz * dz));
            final int key = farthest ? Integer.MAX_VALUE - bits : bits;
            if (heapSize < keys.length) {
                // Up from the end
                int at = heapSize++;
                while (at > 0 && keys[(at - 1) / 2] < key) {
                    keys[at] = keys[(at - 1) / 2];
                    kept[at] = kept[(at - 1) / 2];
                    at = (at - 1) / 2;
                }
                keys[at] = key;
                kept[at] = i;
            } else if (heapSize > 0 && key < keys[0]) {
                // In place of the head, then down
                int at = 0;
                while (true) {
                    final int left = 2 * at + 1;
                    if (left >= heapSize) {
                        break;
                    }
                    final int larger =
                            left + 1 < heapSize && keys[left + 1] > keys[left] ? left + 1 : left;
                    if (keys[larger] <= key) {
                        break;
                    }
                    keys[at] = keys[larger];
                    kept[at] = kept[larger];
                    at = larger;
                }
                keys[at] = key;
                kept[at] = i;
            }
        }
        // Wanted first: each key with its place in the heap in the low half
        final long[] order = new long[heapSize];
        for (int i = 0; i < heapSize; ++i) {
            order[i] = ((long) keys[i] << Integer.SIZE) | i;
        }
        Arrays.sort(order);
        final List<LodStore.SectionPos> out = new ArrayList<>(heapSize);
        for (long entry : order) {
            out.add(positions[kept[(int) entry]]);
        }
        return out;
    }

    /**
     * Give a section without a slot a free one, and queue its brick to be copied in.
     *
     * @param position The section.
     */
    private void assign(LodStore.@NonNull SectionPos position) {
        final VolumeVoxelizer.Brick brick = brickOf(position);
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
        final double section = VolumeMath.BRICK * VolumeMath.VOXEL_SIZE;
        final double half = section * CoarseBricks.span(position.level()) / 2;
        final double dx = position.x() * section + half - camera.x();
        final double dy = position.y() * section + half - camera.y();
        final double dz = position.z() * section + half - camera.z();
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
            // Bulk copies, in the buffer's byte order: a rebalance uploads hundreds of bricks at
            // once, and a call per value took most of a frame
            data.slice(base + TEXELS_SIZE, MASKS_SIZE)
                    .order(data.order())
                    .asIntBuffer()
                    .put(brick.cellMasks());
            data.slice(base + TEXELS_SIZE + MASKS_SIZE, MATERIALS_SIZE)
                    .order(data.order())
                    .asShortBuffer()
                    .put(brick.materials());
            data.slice(base + TEXELS_SIZE + MASKS_SIZE + MATERIALS_SIZE, SURFACES_SIZE)
                    .order(data.order())
                    .asIntBuffer()
                    .put(brick.surfaces());
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
            --levelCounts[slotLevels[slot]];
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
        final long out = buffer.allocationInfo.pMappedData();
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
            final int filledCount = filledIn(sections, filledSlots);
            for (int i = 0; i < filledCount; ++i) {
                final int slot = filledSlots[i];
                final int corner = slot * CORNER_VALUES;
                final double dx = slotCorners[corner] * size + half - camera.x();
                final double dy = slotCorners[corner + 1] * size + half - camera.y();
                final double dz = slotCorners[corner + 2] * size + half - camera.z();
                final float distance = (float) (dx * dx + dy * dy + dz * dz);
                sorted[i] = ((long) Float.floatToRawIntBits(distance) << Integer.SIZE) | slot;
            }
            // Primitive keys: no boxing or comparator, which cost the most here while walking
            Arrays.sort(sorted, 0, filledCount);
            sortedCount = filledCount;
            sortedFrom = sections;
            sortedVersion = version;
            sortedAt = new Vector3d(camera);
            slotsChanged = false;
        }
        for (int i = 0; i < sortedCount; ++i) {
            writeDraw(out, i, (int) sorted[i], size, camera);
        }
        drawCounts[frame] = sortedCount;
        return sortedCount;
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
        final long out = buffer.allocationInfo.pMappedData();
        final double size = VolumeMath.BRICK * VolumeMath.VOXEL_SIZE;
        final long version = sections.version();
        if (sections != shadowsFrom || version != shadowsVersion || shadowsAt != slotVersion) {
            shadowCount = filledIn(sections, shadows);
            shadowsFrom = sections;
            shadowsVersion = version;
            shadowsAt = slotVersion;
        }
        for (int i = 0; i < shadowCount; ++i) {
            writeDraw(out, i, shadows[i], size, camera);
        }
        shadowCounts[frame] = shadowCount;
        return shadowCount;
    }

    /**
     * Write one brick of a draw list: its corner relative to the camera, and its slot with its
     * level above {@link #LEVEL_SHIFT}.
     *
     * @param out The list's address, with room for the brick.
     * @param index Where in the list.
     * @param slot The brick's slot, which holds a section.
     * @param size A brick's size, in meters.
     * @param camera The camera's position, in the world.
     */
    private void writeDraw(long out, int index, int slot, double size, @NonNull Vector3dc camera) {
        final int corner = slot * CORNER_VALUES;
        final long base = out + (long) index * DRAW_SIZE;
        MemoryUtil.memPutFloat(base, (float) (slotCorners[corner] * size - camera.x()));
        MemoryUtil.memPutFloat(
                base + Float.BYTES, (float) (slotCorners[corner + 1] * size - camera.y()));
        MemoryUtil.memPutFloat(
                base + 2 * Float.BYTES, (float) (slotCorners[corner + 2] * size - camera.z()));
        MemoryUtil.memPutInt(base + 3 * Float.BYTES, slot | slotLevels[slot] << LEVEL_SHIFT);
    }

    /**
     * Which of some sections have a filled slot, picked from the filled slots: far out, many more
     * sections stand in as bricks than fit the atlas.
     *
     * @param sections The sections.
     * @param into Receives the filled slots of those sections, in slot order.
     * @return How many slots it received.
     */
    private int filledIn(@NonNull Members sections, int @NonNull [] into) {
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
        int count = 0;
        for (int slot = 0; slot < VolumeMath.SLOTS; ++slot) {
            // Coarse bricks stand in far past where every section is drawn as a volume
            if ((set.in[slot] || slotLevels[slot] > 0)
                    && filled[slot]
                    && slotPositions[slot] != null) {
                into[count++] = slot;
            }
        }
        return count;
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
        slotCorners[slot * CORNER_VALUES] = position.x();
        slotCorners[slot * CORNER_VALUES + 1] = position.y();
        slotCorners[slot * CORNER_VALUES + 2] = position.z();
        slotLevels[slot] = position.level();
        ++levelCounts[position.level()];
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
     * Whether a section's brick, or a coarse brick standing in for it, shows its blocks as they are
     * now: copied in, and no newer one waiting or being built.
     *
     * @param position The section.
     * @return True if it is up to date.
     */
    public boolean isCurrent(LodStore.@NonNull SectionPos position) {
        if (currentSlot(position) >= 0) {
            return true;
        }
        // Or a coarse brick standing in for it, built from its brick as it is now
        for (int level = 1; level <= CoarseBricks.MAX_LEVEL; ++level) {
            final LodStore.SectionPos key = keyAt(position, level);
            if (wanted.containsKey(key)
                    && !building.containsKey(key)
                    && !dirty.contains(key)
                    && isIn(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a brick is copied into its slot, with no newer one waiting.
     *
     * @param key The brick's key.
     * @return True if so.
     */
    private boolean isIn(LodStore.@NonNull SectionPos key) {
        final Integer slot = slots.get(key);
        return slot != null && filled[slot] && !uploads.containsKey(key);
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
     * How many slots hold bricks of a level.
     *
     * @param level The level, 0 for sections' own bricks.
     * @return The count.
     */
    public int brickCount(int level) {
        return levelCounts[level];
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
        if (coarseWorkers != null) {
            coarseWorkers.shutdownNow();
            coarseWorkers = null;
        }
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
