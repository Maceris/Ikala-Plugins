package com.ikalagaming.graphics.vulkan;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import javax.imageio.ImageIO;

/**
 * Saves the finished scene to PNG files, for checks and captures that should not depend on the
 * window being in front. A frame is copied right after the scene goes to its window, before the GUI
 * is drawn over it, so panels never show. Requests wait for the next frame that draws the scene,
 * one per frame, and their files are written on another thread once the GPU is done.
 */
@Slf4j
public class FrameCapture {

    /**
     * A frame someone asked for.
     *
     * @param path Where the PNG goes.
     * @param done Completed with the path once written.
     */
    private record Request(Path path, CompletableFuture<Path> done) {}

    /**
     * A frame being copied.
     *
     * @param request Who asked for it.
     * @param width Its width in pixels.
     * @param height Its height in pixels.
     */
    private record InFlight(Request request, int width, int height, boolean bgra) {}

    /** Guards {@link #waiting}, which other threads add to. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Requests no frame has taken yet, oldest first. */
    private final Queue<Request> waiting = new ArrayDeque<>();

    /** The copy each frame index has in flight, or null. */
    private final InFlight[] inFlight = new InFlight[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Each frame index's readback buffer, or VK_NULL_HANDLE. */
    private final long[] buffers = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** The VMA allocation behind each buffer. */
    private final long[] allocations = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Where each buffer is mapped. */
    private final long[] mapped = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Each buffer's size in bytes. */
    private final long[] sizes = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /**
     * Turn RGBA pixels, top row first, into an image without alpha. The scene's colors are already
     * in display space, so they are kept as they are.
     *
     * @param rgba Four bytes per pixel.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @return The image.
     * @throws IllegalArgumentException If there are too few bytes.
     */
    public static BufferedImage toImage(byte @NonNull [] rgba, int width, int height) {
        return toImage(rgba, width, height, false);
    }

    /**
     * Turn pixels, top row first, into an image without alpha.
     *
     * @param pixels Four bytes per pixel.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param bgra Whether each pixel's bytes are blue, green, red, alpha, as swapchain images' are,
     *     rather than red first.
     * @return The image.
     * @throws IllegalArgumentException If there are too few bytes.
     */
    public static BufferedImage toImage(
            byte @NonNull [] pixels, int width, int height, boolean bgra) {
        final byte[] rgba = pixels;
        if (rgba.length < (long) width * height * 4) {
            throw new IllegalArgumentException(
                    "Need " + (long) width * height * 4 + " bytes, got " + rgba.length);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[width];
        for (int y = 0; y < height; ++y) {
            int at = y * width * 4;
            for (int x = 0; x < width; ++x, at += 4) {
                final int red = rgba[bgra ? at + 2 : at] & 0xFF;
                final int blue = rgba[bgra ? at : at + 2] & 0xFF;
                row[x] = red << 16 | (rgba[at + 1] & 0xFF) << 8 | blue;
            }
            image.setRGB(0, y, width, 1, row, 0, width);
        }
        return image;
    }

    /**
     * Ask for the next frame that draws the scene to be saved.
     *
     * @param path Where the PNG goes; folders are made as needed.
     * @return Completed with the path once the file is written.
     */
    public CompletableFuture<Path> request(@NonNull Path path) {
        CompletableFuture<Path> done = new CompletableFuture<>();
        lock.lock();
        try {
            waiting.add(new Request(path, done));
        } finally {
            lock.unlock();
        }
        return done;
    }

    /**
     * Whether any request waits for a frame.
     *
     * @return True if one does.
     */
    public boolean isWaiting() {
        lock.lock();
        try {
            return !waiting.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Take the oldest request, if any.
     *
     * @return The request, or null.
     */
    private Request take() {
        lock.lock();
        try {
            return waiting.poll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Copy the finished scene for the oldest request, if there is one. The image must already be a
     * transfer source, as it is once it has been copied to the window.
     *
     * @param commandBuffer The frame's command buffer.
     * @param state The Vulkan state.
     * @param image The scene's final image, in the transfer source layout.
     * @param width How much of it was rendered across.
     * @param height How much of it was rendered down.
     */
    public void record(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            long image,
            int width,
            int height) {
        record(commandBuffer, state, image, width, height, false);
    }

    /**
     * Copy an image for the next waiting request, if there is one and this frame index is free. The
     * image must be a transfer source.
     *
     * @param commandBuffer The frame's command buffer.
     * @param state The Vulkan state.
     * @param image The image, four bytes per pixel.
     * @param width The width to copy, in pixels.
     * @param height The height to copy, in pixels.
     * @param bgra Whether its bytes are blue first, as swapchain images' are.
     */
    public void record(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            long image,
            int width,
            int height,
            boolean bgra) {
        final int index = state.frameIndex;
        if (inFlight[index] != null || width <= 0 || height <= 0) {
            return;
        }
        final Request request = take();
        if (request == null) {
            return;
        }
        ensureBuffer(state, index, (long) width * height * 4);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferImageCopy.Buffer region =
                    VkBufferImageCopy.calloc(1, stack)
                            .bufferOffset(0)
                            // Tightly packed rows
                            .bufferRowLength(0)
                            .bufferImageHeight(0)
                            .imageSubresource(
                                    s -> s.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1))
                            .imageOffset(o -> o.set(0, 0, 0))
                            .imageExtent(e -> e.set(width, height, 1));
            vkCmdCopyImageToBuffer(
                    commandBuffer,
                    image,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    buffers[index],
                    region);

            // The host reads it once the frame's fence has signaled
            VkBufferMemoryBarrier2.Buffer barrier =
                    VkBufferMemoryBarrier2.calloc(1, stack)
                            .sType$Default()
                            .srcStageMask(VK_PIPELINE_STAGE_2_TRANSFER_BIT)
                            .srcAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                            .dstStageMask(VK_PIPELINE_STAGE_2_HOST_BIT)
                            .dstAccessMask(VK_ACCESS_2_HOST_READ_BIT)
                            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .buffer(buffers[index])
                            .offset(0)
                            .size(VK_WHOLE_SIZE);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pBufferMemoryBarriers(barrier));
        }
        inFlight[index] = new InFlight(request, width, height, bgra);
    }

    /**
     * Hand the copy a frame index made to a worker thread to write, once its fence has been waited
     * on.
     *
     * @param index The frame index whose fence has signaled.
     */
    public void collect(int index) {
        final InFlight done = inFlight[index];
        if (done == null) {
            return;
        }
        inFlight[index] = null;
        final byte[] rgba = new byte[done.width() * done.height() * 4];
        MemoryUtil.memByteBuffer(mapped[index], rgba.length).get(rgba);
        CompletableFuture.runAsync(() -> write(done, rgba));
    }

    /**
     * Write a copied frame and complete its request.
     *
     * @param done The copy.
     * @param rgba Its pixels.
     */
    private static void write(InFlight done, byte[] rgba) {
        final Path path = done.request().path();
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!ImageIO.write(
                    toImage(rgba, done.width(), done.height(), done.bgra()),
                    "png",
                    path.toFile())) {
                throw new IOException("No PNG writer");
            }
            log.debug("Saved a {}x{} frame to {}", done.width(), done.height(), path);
            done.request().done().complete(path);
        } catch (IOException e) {
            done.request().done().completeExceptionally(new UncheckedIOException(e));
        } catch (RuntimeException e) {
            done.request().done().completeExceptionally(e);
        }
    }

    /**
     * Make sure a frame index has a readback buffer big enough, replacing a smaller one. The frame
     * index's fence has been waited on, so the GPU is done with the old one.
     *
     * @param state The Vulkan state.
     * @param index The frame index.
     * @param size The bytes needed.
     */
    private void ensureBuffer(VulkanState state, int index, long size) {
        if (sizes[index] >= size) {
            return;
        }
        destroyBuffer(state, index);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo =
                    VkBufferCreateInfo.calloc(stack)
                            .sType$Default()
                            .size(size)
                            .usage(VK_BUFFER_USAGE_TRANSFER_DST_BIT)
                            .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            // Read back on the CPU, so cached memory rather than write-combined
            VmaAllocationCreateInfo allocationInfo =
                    VmaAllocationCreateInfo.calloc(stack)
                            .flags(
                                    VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT
                                            | VMA_ALLOCATION_CREATE_MAPPED_BIT)
                            .usage(VMA_MEMORY_USAGE_AUTO)
                            .requiredFlags(
                                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                                            | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            LongBuffer buffer = stack.callocLong(1);
            PointerBuffer allocation = stack.callocPointer(1);
            VmaAllocationInfo info = VmaAllocationInfo.calloc(stack);
            checkError(
                    vmaCreateBuffer(
                            state.vmaAllocator,
                            bufferInfo,
                            allocationInfo,
                            buffer,
                            allocation,
                            info));
            buffers[index] = buffer.get(0);
            allocations[index] = allocation.get(0);
            mapped[index] = info.pMappedData();
            sizes[index] = size;
        }
    }

    /**
     * Free a frame index's readback buffer, if it has one.
     *
     * @param state The Vulkan state.
     * @param index The frame index.
     */
    private void destroyBuffer(VulkanState state, int index) {
        if (buffers[index] == VK_NULL_HANDLE) {
            return;
        }
        vmaDestroyBuffer(state.vmaAllocator, buffers[index], allocations[index]);
        buffers[index] = VK_NULL_HANDLE;
        allocations[index] = VK_NULL_HANDLE;
        mapped[index] = 0;
        sizes[index] = 0;
    }

    /**
     * Free the buffers and fail every request not yet written. The device must be idle.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        for (int i = 0; i < inFlight.length; ++i) {
            if (inFlight[i] != null) {
                inFlight[i].request().done().cancel(false);
                inFlight[i] = null;
            }
            destroyBuffer(state, i);
        }
        Request left;
        while ((left = take()) != null) {
            left.done().cancel(false);
        }
    }
}
