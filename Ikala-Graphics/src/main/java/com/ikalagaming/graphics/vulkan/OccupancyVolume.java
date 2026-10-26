package com.ikalagaming.graphics.vulkan;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.Sections;
import com.ikalagaming.graphics.graph.OccupancyClipmap;
import com.ikalagaming.graphics.graph.VolumeMath;
import com.ikalagaming.graphics.lod.LodStore;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3dc;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The occupancy volume: which voxels around the camera hold something that doesn't move, in three
 * clipmap levels of {@link OccupancyClipmap#SIZE}^3 voxels, 1, 2 and 4 meters wide, stacked along z
 * in one {@code R32_UINT} image. Bit {@link #OPAQUE_BIT} marks opaque surfaces, bit {@link
 * #CUTOUT_BIT} cut out ones such as leaves, and bit {@link #TRANSLUCENT_BIT} translucent ones.
 * Baked sections are voxelized into it on the GPU, see {@code OccupancyVoxelize}: their meshes, and
 * for a section whose cut out and translucent meshes are off the GPU, its volume brick's surface
 * bits in their place (see {@link VolumeBricks}).
 *
 * <p>Each frame the levels follow the camera ({@link OccupancyClipmap}): the voxels a level newly
 * covers are cleared, and the sections there are queued to be voxelized into it. A section that
 * changes is cleared from every level and queued again. Queued sections are voxelized under a
 * triangle budget a frame, the finest level first.
 *
 * <p>Each level also keeps its own {@code R8_UNORM} coverage image, written alongside the bits: 1
 * where something opaque is, less where only cut out surfaces are, which let some light through.
 * Read through a filtering sampler that wraps around, as the levels' addressing does, it gives how
 * full the volume is anywhere, blended smoothly between voxels, so a surface's ambient occlusion
 * doesn't flip between voxels with the least error in its position.
 *
 * <p>The images stay in the general layout, read and written through one set by the compute passes
 * and the light stage alike. Render thread only.
 */
@Slf4j
public class OccupancyVolume {

    /** The format: bits, written with atomic or. */
    public static final int FORMAT = VK_FORMAT_R32_UINT;

    /** The bit of voxels holding opaque or cut out surfaces. */
    public static final int OPAQUE_BIT = 1;

    /** The bit of voxels holding translucent surfaces. */
    public static final int TRANSLUCENT_BIT = 2;

    /** The bit of voxels holding cut out surfaces, such as leaves. */
    public static final int CUTOUT_BIT = 4;

    /** The most triangles voxelized in a frame, so the first fill is spread over frames. */
    public static final long MAX_TRIANGLES_PER_FRAME = 2_000_000;

    /**
     * The most boxes cleared from one level in a frame, each its own dispatch. Past this, as when a
     * world unloads and every section is removed at once, the whole level is cleared in one
     * dispatch and every section in it voxelized again: thousands of dispatches in one frame make
     * the GPU's work scheduler fail, and 128 sections already cover half of the finest level.
     */
    public static final int MAX_CLEARS_PER_LEVEL = 128;

    /** The format of each level's coverage image: how full each voxel is, filterable. */
    public static final int COVERAGE_FORMAT = VK_FORMAT_R8_UNORM;

    /** The image's binding in its set. */
    public static final int BINDING = 0;

    /** The binding of the levels' coverage images, as storage images, for the compute passes. */
    public static final int COVERAGE_BINDING = 1;

    /**
     * The binding of the levels' coverage images, through the filtering sampler, for fragment
     * shaders.
     */
    public static final int COVERAGE_SAMPLED_BINDING = 2;

    /**
     * One section mesh to voxelize into one level.
     *
     * @param level The level.
     * @param corner The section's corner, in the world, in whole meters.
     * @param mesh The mesh, in the baked geometry buffers.
     * @param bit The bit it sets.
     */
    public record Job(int level, long @NonNull [] corner, @NonNull MeshHandle mesh, int bit) {}

    /**
     * One section's volume brick to mark its surfaces from into one level.
     *
     * @param level The level.
     * @param corner The section's corner, in the world, in whole meters.
     * @param slot The brick's slot.
     */
    public record BrickJob(int level, long @NonNull [] corner, int slot) {}

    /**
     * A frame's work.
     *
     * @param meshes The meshes to voxelize.
     * @param bricks The bricks to mark surfaces from.
     */
    public record Jobs(@NonNull List<Job> meshes, @NonNull List<BrickJob> bricks) {}

    /**
     * What one brick job counts as against {@link #MAX_TRIANGLES_PER_FRAME}: a thread a voxel, as a
     * triangle is a thread.
     */
    private static final long BRICK_COST =
            (long) VolumeMath.BRICK * VolumeMath.BRICK * VolumeMath.BRICK;

    /**
     * The layout of the set the image is read and written through. -- GETTER -- The set layout.
     *
     * @return The VkDescriptorSetLayout.
     */
    @Getter private long setLayout;

    /**
     * The set the image is read and written through. -- GETTER -- The set.
     *
     * @return The VkDescriptorSet.
     */
    @Getter private long set;

    /** The pool the set comes from. */
    private long descriptorPool;

    /** The VkImage. */
    private long image;

    /** Its allocation. */
    private long imageAllocation;

    /** The view of the whole image. */
    private long view;

    /** Each level's coverage image. */
    private final long[] coverageImages = new long[OccupancyClipmap.LEVELS];

    /** Their allocations. */
    private final long[] coverageAllocations = new long[OccupancyClipmap.LEVELS];

    /** Their views. */
    private final long[] coverageViews = new long[OccupancyClipmap.LEVELS];

    /** The sampler the coverage images are read through: linear, wrapping around. */
    private long sampler;

    /** Where the levels are in the world. */
    @Getter private final OccupancyClipmap clipmap = new OccupancyClipmap();

    /** Boxes to clear this frame. */
    private final List<OccupancyClipmap.Box> clears = new ArrayList<>();

    /** How many boxes each level clears this frame. */
    private final int[] clearCounts = new int[OccupancyClipmap.LEVELS];

    /** Whether each level is cleared whole this frame, so its other boxes are left out. */
    private final boolean[] clearedWhole = new boolean[OccupancyClipmap.LEVELS];

    /** Sections to voxelize, by level, as section corners in sections, finest level first. */
    private final List<Set<List<Long>>> queued = new ArrayList<>();

    /**
     * Sections whose bricks came in after they were voxelized, to mark from them too, by level, as
     * section corners in sections. Nothing is cleared for them: their bits only add to the voxels.
     */
    private final List<Set<List<Long>>> queuedBricks = new ArrayList<>();

    /** Triangles voxelized since the volume was made. */
    @Getter private long trianglesVoxelized;

    /** Sections waiting to be voxelized, over every level. */
    @Getter private int queuedSections;

    /** When the queue last filled from empty, from {@link System#nanoTime()}, or 0 while empty. */
    private long busySince;

    /** Triangles voxelized since the queue last filled from empty. */
    private long busyTriangles;

    /** Frames since the queue last filled from empty. */
    private int busyFrames;

    /** Nanoseconds in a millisecond. */
    private static final double NANOS_PER_MILLI = 1e6;

    /**
     * Set up the volume, its image cleared to empty in the general layout.
     *
     * @param state The Vulkan state.
     */
    public OccupancyVolume(@NonNull VulkanState state) {
        for (int level = 0; level < OccupancyClipmap.LEVELS; ++level) {
            queued.add(new LinkedHashSet<>());
            queuedBricks.add(new LinkedHashSet<>());
        }
        final int size = OccupancyClipmap.SIZE;
        final int levels = OccupancyClipmap.LEVELS;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            image = createImage(state, FORMAT, size * levels, longOutput, stack);
            imageAllocation = longOutput.get(0);
            view = createView(state, image, FORMAT, stack);
            for (int level = 0; level < levels; ++level) {
                coverageImages[level] =
                        createImage(state, COVERAGE_FORMAT, size, longOutput, stack);
                coverageAllocations[level] = longOutput.get(0);
                coverageViews[level] =
                        createView(state, coverageImages[level], COVERAGE_FORMAT, stack);
            }
            VkSamplerCreateInfo samplerInfo =
                    VkSamplerCreateInfo.calloc(stack)
                            .sType$Default()
                            .magFilter(VK_FILTER_LINEAR)
                            .minFilter(VK_FILTER_LINEAR)
                            .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                            .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                            .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                            .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                            .minLod(0)
                            .maxLod(0);
            checkError(vkCreateSampler(state.device.logical, samplerInfo, null, longOutput));
            sampler = longOutput.get(0);

            VkDescriptorSetLayoutBinding.Buffer bindings =
                    VkDescriptorSetLayoutBinding.calloc(3, stack);
            bindings.get(0)
                    .binding(BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            bindings.get(1)
                    .binding(COVERAGE_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(levels)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            bindings.get(2)
                    .binding(COVERAGE_SAMPLED_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(levels)
                    // Compute too: the fog's injection includes the lighting that reads it
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_COMPUTE_BIT);
            checkError(
                    vkCreateDescriptorSetLayout(
                            state.device.logical,
                            VkDescriptorSetLayoutCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pBindings(bindings),
                            null,
                            longOutput));
            setLayout = longOutput.get(0);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2, stack);
            poolSizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(1 + levels);
            poolSizes
                    .get(1)
                    .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(levels);
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
            VkDescriptorImageInfo.Buffer coverageStorage =
                    VkDescriptorImageInfo.calloc(levels, stack);
            VkDescriptorImageInfo.Buffer coverageSampled =
                    VkDescriptorImageInfo.calloc(levels, stack);
            for (int level = 0; level < levels; ++level) {
                coverageStorage
                        .get(level)
                        .imageView(coverageViews[level])
                        .imageLayout(VK_IMAGE_LAYOUT_GENERAL);
                coverageSampled
                        .get(level)
                        .sampler(sampler)
                        .imageView(coverageViews[level])
                        .imageLayout(VK_IMAGE_LAYOUT_GENERAL);
            }
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(3, stack);
            writes.get(0)
                    .sType$Default()
                    .dstSet(set)
                    .dstBinding(BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1)
                    .pImageInfo(storage);
            writes.get(1)
                    .sType$Default()
                    .dstSet(set)
                    .dstBinding(COVERAGE_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(levels)
                    .pImageInfo(coverageStorage);
            writes.get(2)
                    .sType$Default()
                    .dstSet(set)
                    .dstBinding(COVERAGE_SAMPLED_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(levels)
                    .pImageInfo(coverageSampled);
            vkUpdateDescriptorSets(state.device.logical, writes, null);
        }

        // Empty, in the general layout, where they stay
        state.immediateCommands.submit(
                state,
                commandBuffer -> {
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        clearWhole(commandBuffer, image, stack);
                        for (long coverage : coverageImages) {
                            clearWhole(commandBuffer, coverage, stack);
                        }
                    }
                });
    }

    /**
     * Create a 3D storage image {@link OccupancyClipmap#SIZE} voxels wide and deep, which can also
     * be sampled and cleared.
     *
     * @param state The Vulkan state.
     * @param format Its VkFormat.
     * @param height How many voxels tall it is.
     * @param allocation Where to put its allocation.
     * @param stack The stack to allocate from.
     * @return The VkImage.
     */
    private static long createImage(
            @NonNull VulkanState state,
            int format,
            int height,
            @NonNull LongBuffer allocation,
            @NonNull MemoryStack stack) {
        final int size = OccupancyClipmap.SIZE;
        LongBuffer longOutput = stack.callocLong(1);
        PointerBuffer pointerOutput = stack.callocPointer(1);
        VkImageCreateInfo imageInfo =
                VkImageCreateInfo.calloc(stack)
                        .sType$Default()
                        .imageType(VK_IMAGE_TYPE_3D)
                        .format(format)
                        .extent(e -> e.set(size, size, height))
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
                        state.vmaAllocator, imageInfo, allocInfo, longOutput, pointerOutput, null));
        allocation.put(0, pointerOutput.get(0));
        return longOutput.get(0);
    }

    /**
     * Create a view of a whole 3D image.
     *
     * @param state The Vulkan state.
     * @param image The VkImage.
     * @param format Its VkFormat.
     * @param stack The stack to allocate from.
     * @return The VkImageView.
     */
    private static long createView(
            @NonNull VulkanState state, long image, int format, @NonNull MemoryStack stack) {
        LongBuffer longOutput = stack.callocLong(1);
        VkImageViewCreateInfo viewInfo =
                VkImageViewCreateInfo.calloc(stack)
                        .sType$Default()
                        .image(image)
                        .viewType(VK_IMAGE_VIEW_TYPE_3D)
                        .format(format)
                        .subresourceRange(
                                range ->
                                        range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                .levelCount(1)
                                                .layerCount(1));
        checkError(vkCreateImageView(state.device.logical, viewInfo, null, longOutput));
        return longOutput.get(0);
    }

    /**
     * Move a new image to the general layout and clear it to zero, ready for the compute passes and
     * fragment shaders.
     *
     * @param commandBuffer The command buffer to record into.
     * @param image The VkImage.
     * @param stack The stack to allocate from.
     */
    private static void clearWhole(
            @NonNull VkCommandBuffer commandBuffer, long image, @NonNull MemoryStack stack) {
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
     * Move the levels to the camera: clear what each newly covers and queue the sections there.
     *
     * @param camera The camera's world position.
     */
    public void follow(@NonNull Vector3dc camera) {
        for (OccupancyClipmap.Box box : clipmap.update(camera)) {
            clearAndQueue(box);
        }
    }

    /**
     * Note that something that doesn't move changed inside a box: clear it from every level and
     * queue the sections there again.
     *
     * @param min The box's minimum corner, in the world.
     * @param max The box's maximum corner.
     */
    public void invalidate(double @NonNull [] min, double @NonNull [] max) {
        for (OccupancyClipmap.Box box : clipmap.boxesFor(min, max)) {
            clearAndQueue(box);
        }
    }

    /** Forget everything, so every level is cleared and written again. */
    public void invalidateAll() {
        clipmap.invalidateAll();
        for (int level = 0; level < OccupancyClipmap.LEVELS; ++level) {
            queued.get(level).clear();
            queuedBricks.get(level).clear();
        }
    }

    /**
     * Note that a section's brick was copied into its slot, for a section whose brick stands in for
     * meshes off the GPU: mark its surfaces into every level it is in.
     *
     * @param position The section.
     */
    public void brickArrived(LodStore.@NonNull SectionPos position) {
        final double size = Sections.SECTION_SIZE;
        final double[] min = {position.x() * size, position.y() * size, position.z() * size};
        final double[] max = {min[0] + size, min[1] + size, min[2] + size};
        final List<Long> corner =
                List.of((long) position.x(), (long) position.y(), (long) position.z());
        for (OccupancyClipmap.Box box : clipmap.boxesFor(min, max)) {
            queuedBricks.get(box.level()).add(corner);
        }
    }

    /**
     * Clear a box, and queue every section it reaches to be voxelized into its level.
     *
     * @param box The box.
     */
    private void clearAndQueue(OccupancyClipmap.@NonNull Box box) {
        final int level = box.level();
        if (clearedWhole[level]) {
            return;
        }
        if (clearCounts[level] >= MAX_CLEARS_PER_LEVEL) {
            clearedWhole[level] = true;
            clears.removeIf(cleared -> cleared.level() == level);
            box = clipmap.whole(level);
        }
        ++clearCounts[level];
        clears.add(box);
        queueSections(box);
    }

    /**
     * Queue every section a box reaches to be voxelized into its level.
     *
     * @param box The box.
     */
    private void queueSections(OccupancyClipmap.@NonNull Box box) {
        final double[] world = box.world();
        final int section = Sections.SECTION_SIZE;
        final Set<List<Long>> sectionsQueued = queued.get(box.level());
        for (long x = Math.floorDiv((long) world[0], section); x * section < world[3]; ++x) {
            for (long y = Math.floorDiv((long) world[1], section); y * section < world[4]; ++y) {
                for (long z = Math.floorDiv((long) world[2], section);
                        z * section < world[5];
                        ++z) {
                    sectionsQueued.add(List.of(x, y, z));
                }
            }
        }
    }

    /**
     * Take the boxes to clear this frame.
     *
     * @return The boxes.
     */
    public List<OccupancyClipmap.Box> takeClears() {
        final List<OccupancyClipmap.Box> out = new ArrayList<>(clears);
        clears.clear();
        Arrays.fill(clearCounts, 0);
        Arrays.fill(clearedWhole, false);
        return out;
    }

    /**
     * Take this frame's sections to voxelize, finest level first, up to the triangle budget. Each
     * comes with its meshes as they are now, and its brick if that stands in for some of them;
     * sections with nothing there are dropped.
     *
     * @param sections The sections.
     * @param bricks The volume bricks, or null if there are none.
     * @return The jobs.
     */
    public Jobs takeJobs(@NonNull SectionManager sections, VolumeBricks bricks) {
        final List<Job> jobs = new ArrayList<>();
        final List<BrickJob> brickJobs = new ArrayList<>();
        long triangles = 0;
        final int size = Sections.SECTION_SIZE;
        for (int level = 0; level < OccupancyClipmap.LEVELS; ++level) {
            final Iterator<List<Long>> it = queued.get(level).iterator();
            while (it.hasNext() && triangles < MAX_TRIANGLES_PER_FRAME) {
                final List<Long> corner = it.next();
                it.remove();
                // Its brick is marked from here, if it stands in
                queuedBricks.get(level).remove(corner);
                final double[] min = {
                    (double) corner.get(0) * size,
                    (double) corner.get(1) * size,
                    (double) corner.get(2) * size
                };
                for (SectionManager.Caster caster : sections.meshesAt(min)) {
                    final MeshHandle mesh = caster.mesh();
                    final int bit =
                            switch (mesh.kind()) {
                                case BAKED_TRANSLUCENT -> TRANSLUCENT_BIT;
                                case BAKED_CUTOUT -> CUTOUT_BIT;
                                default -> OPAQUE_BIT;
                            };
                    jobs.add(
                            new Job(
                                    level,
                                    new long[] {
                                        Math.round(caster.origin().x()),
                                        Math.round(caster.origin().y()),
                                        Math.round(caster.origin().z())
                                    },
                                    mesh,
                                    bit));
                    triangles += mesh.indexCount() / 3;
                }
                triangles += addBrickJob(sections, bricks, level, corner, brickJobs);
            }
            final Iterator<List<Long>> late = queuedBricks.get(level).iterator();
            while (late.hasNext() && triangles < MAX_TRIANGLES_PER_FRAME) {
                final List<Long> corner = late.next();
                late.remove();
                triangles += addBrickJob(sections, bricks, level, corner, brickJobs);
            }
        }
        trianglesVoxelized += triangles;
        int waiting = 0;
        for (int level = 0; level < OccupancyClipmap.LEVELS; ++level) {
            waiting += queued.get(level).size() + queuedBricks.get(level).size();
        }
        queuedSections = waiting;
        if (!jobs.isEmpty() || !brickJobs.isEmpty()) {
            if (busySince == 0) {
                busySince = System.nanoTime();
                busyTriangles = 0;
                busyFrames = 0;
            }
            busyTriangles += triangles;
            busyFrames += 1;
        }
        if (waiting == 0 && busySince != 0) {
            log.debug(
                    "Occupancy volume caught up: {} triangles over {} frames in {} ms",
                    busyTriangles,
                    busyFrames,
                    String.format("%.1f", (System.nanoTime() - busySince) / NANOS_PER_MILLI));
            busySince = 0;
        }
        return new Jobs(jobs, brickJobs);
    }

    /**
     * Add a job marking a section's brick into a level, if the brick stands in for meshes off the
     * GPU and is up to date. One that isn't yet is marked once it arrives.
     *
     * @param sections The sections.
     * @param bricks The volume bricks, or null.
     * @param level The level.
     * @param corner The section's corner, in sections.
     * @param out Where the job goes.
     * @return What it counts as against the budget, 0 if there is no job.
     */
    private static long addBrickJob(
            @NonNull SectionManager sections,
            VolumeBricks bricks,
            int level,
            @NonNull List<Long> corner,
            @NonNull List<BrickJob> out) {
        if (bricks == null) {
            return 0;
        }
        final LodStore.SectionPos position =
                new LodStore.SectionPos(
                        corner.get(0).intValue(),
                        corner.get(1).intValue(),
                        corner.get(2).intValue());
        if (!sections.bricksStandIn(position)) {
            return 0;
        }
        final int slot = bricks.currentSlot(position);
        if (slot < 0) {
            return 0;
        }
        final int size = Sections.SECTION_SIZE;
        out.add(
                new BrickJob(
                        level,
                        new long[] {
                            corner.get(0) * size, corner.get(1) * size, corner.get(2) * size
                        },
                        slot));
        return BRICK_COST;
    }

    /**
     * Destroy the images and their set.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        vkDestroyDescriptorPool(state.device.logical, descriptorPool, null);
        vkDestroyDescriptorSetLayout(state.device.logical, setLayout, null);
        vkDestroyImageView(state.device.logical, view, null);
        vmaDestroyImage(state.vmaAllocator, image, imageAllocation);
        for (int level = 0; level < OccupancyClipmap.LEVELS; ++level) {
            vkDestroyImageView(state.device.logical, coverageViews[level], null);
            vmaDestroyImage(state.vmaAllocator, coverageImages[level], coverageAllocations[level]);
        }
        vkDestroySampler(state.device.logical, sampler, null);
    }
}
