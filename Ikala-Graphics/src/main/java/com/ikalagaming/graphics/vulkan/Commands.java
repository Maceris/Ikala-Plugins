package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memPutAddress;
import static org.lwjgl.system.MemoryUtil.memPutLong;
import static org.lwjgl.vulkan.VK10.nvkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.nvkCmdBindVertexBuffers;
import static org.lwjgl.vulkan.VK10.nvkCmdSetScissor;
import static org.lwjgl.vulkan.VK10.nvkCmdSetViewport;
import static org.lwjgl.vulkan.VK13.nvkCmdPipelineBarrier2;

import lombok.NonNull;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkMemoryBarrier2;
import org.lwjgl.vulkan.VkOffset2D;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;

/**
 * Common commands recorded without making Java objects: the structs are written straight onto the
 * thread's {@link MemoryStack} through LWJGL's address accessors, where {@code VkX.calloc(stack)}
 * or {@code stack.longs(...)} would make a wrapper object for each. Passes record these many times
 * a frame, so the wrappers added up.
 */
public final class Commands {
    /** Bytes in a handle. */
    private static final int HANDLE_BYTES = Long.BYTES;

    /** Not made. */
    private Commands() {}

    /**
     * Record a global memory barrier.
     *
     * @param commandBuffer The command buffer.
     * @param srcStage The stages to wait for.
     * @param srcAccess The writes to make available.
     * @param dstStage The stages that wait.
     * @param dstAccess The accesses to make them visible to.
     */
    public static void memoryBarrier(
            @NonNull VkCommandBuffer commandBuffer,
            long srcStage,
            long srcAccess,
            long dstStage,
            long dstAccess) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long barrier =
                    stack.ncalloc(VkMemoryBarrier2.ALIGNOF, 1, VkMemoryBarrier2.SIZEOF);
            VkMemoryBarrier2.nsType(barrier, VK13.VK_STRUCTURE_TYPE_MEMORY_BARRIER_2);
            VkMemoryBarrier2.nsrcStageMask(barrier, srcStage);
            VkMemoryBarrier2.nsrcAccessMask(barrier, srcAccess);
            VkMemoryBarrier2.ndstStageMask(barrier, dstStage);
            VkMemoryBarrier2.ndstAccessMask(barrier, dstAccess);
            final long dependency =
                    stack.ncalloc(VkDependencyInfo.ALIGNOF, 1, VkDependencyInfo.SIZEOF);
            VkDependencyInfo.nsType(dependency, VK13.VK_STRUCTURE_TYPE_DEPENDENCY_INFO);
            VkDependencyInfo.nmemoryBarrierCount(dependency, 1);
            memPutAddress(dependency + VkDependencyInfo.PMEMORYBARRIERS, barrier);
            nvkCmdPipelineBarrier2(commandBuffer, dependency);
        }
    }

    /**
     * Record a barrier on the first mip level and layer of an image, with no queue transfer.
     *
     * @param commandBuffer The command buffer.
     * @param image The image.
     * @param aspect Its aspects.
     * @param srcStage The stages to wait for.
     * @param srcAccess The writes to make available.
     * @param dstStage The stages that wait.
     * @param dstAccess The accesses to make them visible to.
     * @param oldLayout The layout it is in.
     * @param newLayout The layout to move it to.
     */
    public static void imageBarrier(
            @NonNull VkCommandBuffer commandBuffer,
            long image,
            int aspect,
            long srcStage,
            long srcAccess,
            long dstStage,
            long dstAccess,
            int oldLayout,
            int newLayout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long barrier =
                    stack.ncalloc(VkImageMemoryBarrier2.ALIGNOF, 1, VkImageMemoryBarrier2.SIZEOF);
            VkImageMemoryBarrier2.nsType(barrier, VK13.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER_2);
            VkImageMemoryBarrier2.nsrcStageMask(barrier, srcStage);
            VkImageMemoryBarrier2.nsrcAccessMask(barrier, srcAccess);
            VkImageMemoryBarrier2.ndstStageMask(barrier, dstStage);
            VkImageMemoryBarrier2.ndstAccessMask(barrier, dstAccess);
            VkImageMemoryBarrier2.noldLayout(barrier, oldLayout);
            VkImageMemoryBarrier2.nnewLayout(barrier, newLayout);
            VkImageMemoryBarrier2.nsrcQueueFamilyIndex(barrier, VK10.VK_QUEUE_FAMILY_IGNORED);
            VkImageMemoryBarrier2.ndstQueueFamilyIndex(barrier, VK10.VK_QUEUE_FAMILY_IGNORED);
            VkImageMemoryBarrier2.nimage(barrier, image);
            wholeRange(barrier + VkImageMemoryBarrier2.SUBRESOURCERANGE, aspect);
            final long dependency =
                    stack.ncalloc(VkDependencyInfo.ALIGNOF, 1, VkDependencyInfo.SIZEOF);
            VkDependencyInfo.nsType(dependency, VK13.VK_STRUCTURE_TYPE_DEPENDENCY_INFO);
            VkDependencyInfo.nimageMemoryBarrierCount(dependency, 1);
            memPutAddress(dependency + VkDependencyInfo.PIMAGEMEMORYBARRIERS, barrier);
            nvkCmdPipelineBarrier2(commandBuffer, dependency);
        }
    }

    /**
     * Write a subresource range of the first mip level and layer, by address.
     *
     * @param range The range's address.
     * @param aspect The aspects.
     */
    public static void wholeRange(long range, int aspect) {
        VkImageSubresourceRange.naspectMask(range, aspect);
        VkImageSubresourceRange.nbaseMipLevel(range, 0);
        VkImageSubresourceRange.nlevelCount(range, 1);
        VkImageSubresourceRange.nbaseArrayLayer(range, 0);
        VkImageSubresourceRange.nlayerCount(range, 1);
    }

    /**
     * Bind one descriptor set, with no dynamic offsets.
     *
     * @param commandBuffer The command buffer.
     * @param bindPoint The pipeline bind point.
     * @param layout The pipeline layout.
     * @param firstSet The set number.
     * @param set The set.
     */
    public static void bindDescriptorSets(
            @NonNull VkCommandBuffer commandBuffer,
            int bindPoint,
            long layout,
            int firstSet,
            long set) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long sets = stack.nmalloc(HANDLE_BYTES, HANDLE_BYTES);
            memPutLong(sets, set);
            nvkCmdBindDescriptorSets(commandBuffer, bindPoint, layout, firstSet, 1, sets, 0, NULL);
        }
    }

    /**
     * Bind consecutive descriptor sets from an array, with no dynamic offsets.
     *
     * @param commandBuffer The command buffer.
     * @param bindPoint The pipeline bind point.
     * @param layout The pipeline layout.
     * @param firstSet The first set's number.
     * @param sets The sets, in order.
     */
    public static void bindDescriptorSets(
            @NonNull VkCommandBuffer commandBuffer,
            int bindPoint,
            long layout,
            int firstSet,
            long @NonNull [] sets) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long values = stack.nmalloc(HANDLE_BYTES, sets.length * HANDLE_BYTES);
            for (int i = 0; i < sets.length; ++i) {
                memPutLong(values + (long) i * HANDLE_BYTES, sets[i]);
            }
            nvkCmdBindDescriptorSets(
                    commandBuffer, bindPoint, layout, firstSet, sets.length, values, 0, NULL);
        }
    }

    /**
     * Bind two consecutive descriptor sets, with no dynamic offsets.
     *
     * @param commandBuffer The command buffer.
     * @param bindPoint The pipeline bind point.
     * @param layout The pipeline layout.
     * @param firstSet The first set's number.
     * @param first The first set.
     * @param second The set after it.
     */
    public static void bindDescriptorSets(
            @NonNull VkCommandBuffer commandBuffer,
            int bindPoint,
            long layout,
            int firstSet,
            long first,
            long second) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long sets = stack.nmalloc(HANDLE_BYTES, 2 * HANDLE_BYTES);
            memPutLong(sets, first);
            memPutLong(sets + HANDLE_BYTES, second);
            nvkCmdBindDescriptorSets(commandBuffer, bindPoint, layout, firstSet, 2, sets, 0, NULL);
        }
    }

    /**
     * Bind three consecutive descriptor sets, with no dynamic offsets.
     *
     * @param commandBuffer The command buffer.
     * @param bindPoint The pipeline bind point.
     * @param layout The pipeline layout.
     * @param firstSet The first set's number.
     * @param first The first set.
     * @param second The set after it.
     * @param third The set after that.
     */
    public static void bindDescriptorSets(
            @NonNull VkCommandBuffer commandBuffer,
            int bindPoint,
            long layout,
            int firstSet,
            long first,
            long second,
            long third) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long sets = stack.nmalloc(HANDLE_BYTES, 3 * HANDLE_BYTES);
            memPutLong(sets, first);
            memPutLong(sets + HANDLE_BYTES, second);
            memPutLong(sets + 2 * HANDLE_BYTES, third);
            nvkCmdBindDescriptorSets(commandBuffer, bindPoint, layout, firstSet, 3, sets, 0, NULL);
        }
    }

    /**
     * Set viewport 0, with depths from 0 to 1.
     *
     * @param commandBuffer The command buffer.
     * @param x Its left, in pixels.
     * @param y Its top, in pixels; the bottom for a flipped viewport.
     * @param width Its width, in pixels.
     * @param height Its height, in pixels; negative to flip it.
     */
    public static void viewport(
            @NonNull VkCommandBuffer commandBuffer, float x, float y, float width, float height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long viewport = stack.nmalloc(VkViewport.ALIGNOF, VkViewport.SIZEOF);
            VkViewport.nx(viewport, x);
            VkViewport.ny(viewport, y);
            VkViewport.nwidth(viewport, width);
            VkViewport.nheight(viewport, height);
            VkViewport.nminDepth(viewport, 0);
            VkViewport.nmaxDepth(viewport, 1);
            nvkCmdSetViewport(commandBuffer, 0, 1, viewport);
        }
    }

    /**
     * Set scissor 0.
     *
     * @param commandBuffer The command buffer.
     * @param x Its left, in pixels.
     * @param y Its top, in pixels.
     * @param width Its width, in pixels.
     * @param height Its height, in pixels.
     */
    public static void scissor(
            @NonNull VkCommandBuffer commandBuffer, int x, int y, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long rect = stack.nmalloc(VkRect2D.ALIGNOF, VkRect2D.SIZEOF);
            VkOffset2D.nx(rect + VkRect2D.OFFSET, x);
            VkOffset2D.ny(rect + VkRect2D.OFFSET, y);
            VkExtent2D.nwidth(rect + VkRect2D.EXTENT, width);
            VkExtent2D.nheight(rect + VkRect2D.EXTENT, height);
            nvkCmdSetScissor(commandBuffer, 0, 1, rect);
        }
    }

    /**
     * Bind one vertex buffer.
     *
     * @param commandBuffer The command buffer.
     * @param binding The binding.
     * @param buffer The buffer.
     * @param offset Where its vertices start, in bytes.
     */
    public static void bindVertexBuffer(
            @NonNull VkCommandBuffer commandBuffer, int binding, long buffer, long offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long values = stack.nmalloc(HANDLE_BYTES, 2 * HANDLE_BYTES);
            memPutLong(values, buffer);
            memPutLong(values + HANDLE_BYTES, offset);
            nvkCmdBindVertexBuffers(commandBuffer, binding, 1, values, values + HANDLE_BYTES);
        }
    }
}
