package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.PreviewView;
import com.ikalagaming.graphics.TextureHandle;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageSubresourceLayers;
import org.lwjgl.vulkan.VkOffset3D;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps the preview views and draws them, one a frame. The renderer draws a preview through the
 * preview pipeline into the shared targets, then this copies the final image into the preview's own
 * texture, which the GUI samples.
 *
 * <p>Previews are created and closed from any thread; their images are made, drawn and freed on the
 * render thread.
 */
@Slf4j
public class PreviewRenderer {
    /** The owner of previews' textures in the texture registry. */
    private static final String TEXTURE_OWNER = "graphics/previews";

    /** A preview's image and what it was made for. Render thread only. */
    private static final class Target {
        /** The image the GUI samples. */
        TextureInfoVulkan image;

        /** Its handle in the texture registry. */
        TextureHandle handle;

        /** Its size. */
        int width;

        /** Its size. */
        int height;

        /** Whether it has been drawn, so it holds a layout other than undefined. */
        boolean drawn;

        /** Saves the image to files. */
        final FrameCapture capture = new FrameCapture();

        /** When it was last drawn, in frames. */
        long lastDrawn = -1;
    }

    /** Guards {@link #views} and {@link #usedViews}. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The open previews, in the order they were made. */
    private final List<PreviewView> views = new ArrayList<>();

    /** The view IDs in use; 0 is the world's. */
    private final BitSet usedViews = new BitSet();

    /** Each preview's image. Render thread only. */
    private final Map<PreviewView, Target> targets = new HashMap<>();

    /**
     * Make a preview. Safe from any thread.
     *
     * @param owner The key of the plugin that owns it.
     * @param width The width to draw it at, in pixels.
     * @param height The height to draw it at, in pixels.
     * @param state The Vulkan state, for the instance table.
     * @return The preview.
     * @throws IllegalStateException If every view ID is taken.
     */
    public PreviewView create(
            @NonNull String owner, int width, int height, @NonNull VulkanState state) {
        lock.lock();
        try {
            final int id = usedViews.nextClearBit(1);
            if (id > InstanceRegistry.MAX_VIEW) {
                throw new IllegalStateException("Every preview view is in use");
            }
            usedViews.set(id);
            final PreviewView view =
                    new PreviewView(
                            owner,
                            id,
                            width,
                            height,
                            GraphicsManager.getScene().getMaterialCache(),
                            () -> state.instances);
            views.add(view);
            return view;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Close every preview a plugin owns. Safe from any thread.
     *
     * @param owner The plugin's key.
     * @return How many were closed.
     */
    public int removeAllOwnedBy(@NonNull String owner) {
        final List<PreviewView> owned = new ArrayList<>();
        lock.lock();
        try {
            for (PreviewView view : views) {
                if (view.getOwner().equals(owner) && !view.isClosed()) {
                    owned.add(view);
                }
            }
        } finally {
            lock.unlock();
        }
        owned.forEach(PreviewView::close);
        return owned.size();
    }

    /**
     * How many previews are open.
     *
     * @return The count.
     */
    public int getCount() {
        lock.lock();
        try {
            return views.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Free closed previews, then pick the one to draw this frame: the one that has waited longest
     * among those that want frames. Render thread.
     *
     * @param state The Vulkan state.
     * @return The preview, or null if none wants a frame.
     */
    public PreviewView next(@NonNull VulkanState state) {
        final List<PreviewView> open;
        lock.lock();
        try {
            for (int i = views.size() - 1; i >= 0; --i) {
                final PreviewView view = views.get(i);
                if (view.isClosed()) {
                    views.remove(i);
                    usedViews.clear(view.getViewID());
                    free(state, view, targets.remove(view));
                }
            }
            open = List.copyOf(views);
        } finally {
            lock.unlock();
        }
        PreviewView best = null;
        long bestDrawn = Long.MAX_VALUE;
        for (PreviewView view : open) {
            if (view.getFramesWanted() <= 0) {
                continue;
            }
            final Target target = targets.get(view);
            final long drawn = target == null ? -1 : target.lastDrawn;
            if (drawn < bestDrawn) {
                best = view;
                bestDrawn = drawn;
            }
        }
        return best;
    }

    /**
     * Copy the final image of a preview's frame into its texture, and save it if asked once it has
     * settled. The final image is in the transfer source layout, as the preview pipeline leaves it.
     * Render thread, while recording the frame.
     *
     * @param commandBuffer The frame's command buffer.
     * @param state The Vulkan state.
     * @param pipelines Makes the image.
     * @param view The preview drawn this frame.
     * @param width The width drawn, which may be less than the preview's if the targets are
     *     smaller.
     * @param height The height drawn.
     */
    public void copy(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            @NonNull PipelineManagerVulkan pipelines,
            @NonNull PreviewView view,
            int width,
            int height) {
        final Target target = targetFor(state, pipelines, view);
        final long source = state.perFrameData[state.frameIndex].finalTexture.texture;
        final long image = target.image.texture;
        // Last sampled by the GUI, maybe in a frame still running
        Commands.imageBarrier(
                commandBuffer,
                image,
                VK_IMAGE_ASPECT_COLOR_BIT,
                VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                VK_ACCESS_2_NONE,
                VK_PIPELINE_STAGE_2_BLIT_BIT,
                VK_ACCESS_2_TRANSFER_WRITE_BIT,
                target.drawn ? VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final VkImageSubresourceLayers layers =
                    VkImageSubresourceLayers.calloc(stack)
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .layerCount(1)
                            .mipLevel(0);
            final VkOffset3D.Buffer from = VkOffset3D.calloc(2, stack);
            from.get(0).set(0, 0, 0);
            from.get(1).set(width, height, 1);
            final VkOffset3D.Buffer to = VkOffset3D.calloc(2, stack);
            to.get(0).set(0, 0, 0);
            to.get(1).set(target.width, target.height, 1);
            final VkImageBlit.Buffer region =
                    VkImageBlit.calloc(1, stack)
                            .srcOffsets(from)
                            .dstOffsets(to)
                            .srcSubresource(layers)
                            .dstSubresource(layers);
            vkCmdBlitImage(
                    commandBuffer,
                    source,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    image,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    region,
                    VK_FILTER_NEAREST);
        }
        Commands.imageBarrier(
                commandBuffer,
                image,
                VK_IMAGE_ASPECT_COLOR_BIT,
                VK_PIPELINE_STAGE_2_BLIT_BIT,
                VK_ACCESS_2_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL);
        target.drawn = true;
        target.lastDrawn = state.frameNumber;
        view.setTexture(target.handle);
        // The last frame of a settle is the one saved, read from the final image still in place
        if (view.frameDrawn() == 0) {
            for (PreviewView.Capture capture : view.takeCaptures()) {
                target.capture
                        .request(capture.path())
                        .whenComplete(
                                (path, error) -> {
                                    if (error == null) {
                                        capture.done().complete(path);
                                    } else {
                                        capture.done().completeExceptionally(error);
                                    }
                                });
            }
            target.capture.record(commandBuffer, state, source, width, height);
        }
    }

    /**
     * Read back finished saves. Render thread, once the frame index's fence is done.
     *
     * @param index The frame index.
     */
    public void collect(int index) {
        for (Target target : targets.values()) {
            target.capture.collect(index);
        }
    }

    /**
     * Free every preview's image, when the renderer shuts down. Render thread, with the device
     * idle.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        for (Map.Entry<PreviewView, Target> entry : targets.entrySet()) {
            destroy(state, entry.getValue());
            entry.getKey().setTexture(null);
        }
        targets.clear();
    }

    /**
     * A preview's image at its current size, made or remade if needed.
     *
     * @param state The Vulkan state.
     * @param pipelines Makes the image.
     * @param view The preview.
     * @return The target.
     */
    private Target targetFor(
            @NonNull VulkanState state,
            @NonNull PipelineManagerVulkan pipelines,
            @NonNull PreviewView view) {
        Target target = targets.get(view);
        final int width = view.getWidth();
        final int height = view.getHeight();
        if (target != null && target.width == width && target.height == height) {
            return target;
        }
        final long lastDrawn = target == null ? -1 : target.lastDrawn;
        free(state, view, target);
        target = new Target();
        target.width = width;
        target.height = height;
        target.lastDrawn = lastDrawn;
        target.image =
                pipelines.createRenderTarget(
                        state,
                        width,
                        height,
                        VK_FORMAT_R8G8B8A8_UNORM,
                        VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_FILTER_LINEAR,
                        VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        target.image.format = Format.R8G8B8A8_UNORM;
        target.image.layout = VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL;
        state.bindlessTextures.reserve(target.image);
        target.handle =
                state.textureRegistry.add(
                        TEXTURE_OWNER, target.image, width, height, Format.R8G8B8A8_UNORM);
        state.bindlessTextures.update(state, target.image);
        state.textureRegistry.markResident(target.handle);
        targets.put(view, target);
        return target;
    }

    /**
     * Free a preview's image once no frame in flight uses it.
     *
     * @param state The Vulkan state.
     * @param view The preview.
     * @param target Its image, or null if it has none.
     */
    private static void free(@NonNull VulkanState state, PreviewView view, Target target) {
        view.setTexture(null);
        if (target == null) {
            return;
        }
        state.deferFree(() -> destroy(state, target));
    }

    /**
     * Free an image now.
     *
     * @param state The Vulkan state.
     * @param target The image.
     */
    private static void destroy(@NonNull VulkanState state, @NonNull Target target) {
        final TextureInfoVulkan info = state.textureRegistry.remove(target.handle);
        if (info != null) {
            state.bindlessTextures.release(state, info);
            info.destroy(state);
        }
        target.capture.cleanup(state);
    }
}
