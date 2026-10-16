package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.TextureHandle;

import lombok.NonNull;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nullable;

/**
 * Texture uploads waiting for the render thread. Any thread can queue an upload; the render thread
 * records them at the start of each frame, before anything that could sample the textures, and then
 * marks them resident.
 */
public class TextureUploads {

    /**
     * Roughly how many bytes of staged data to upload per frame, so that a burst of loads is spread
     * out instead of causing one long frame. At least one upload happens every frame regardless.
     */
    public static final long BYTES_PER_FRAME = 32L * 1024 * 1024;

    /**
     * A texture waiting to be uploaded.
     *
     * @param handle The texture's handle, which is pending until the upload is recorded.
     * @param info The texture, with its image and view already created.
     * @param staging The pixel data for mip level 0, or every level when the levels are given; null
     *     to clear the texture instead.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param mipLevels The number of mip levels, generated from level 0 unless they are given.
     * @param clear Whether the format can be cleared, for a texture with no data.
     * @param levelOffsets Where each mip level starts in the staged data, for data that holds every
     *     level already, like a DDS file; null to generate the levels from level 0.
     */
    public record Request(
            @NonNull TextureHandle handle,
            @NonNull TextureInfoVulkan info,
            @Nullable StagingRing.Staging staging,
            int width,
            int height,
            int mipLevels,
            boolean clear,
            @Nullable int[] levelOffsets) {}

    /**
     * A texture to read in another color space, once it is resident.
     *
     * @param handle The texture.
     * @param colorSpace The color space to read it in.
     */
    private record ColorSpaceChange(
            @NonNull TextureHandle handle, @NonNull ColorSpace colorSpace) {}

    /** Color space changes in the order they were asked for. */
    private final Queue<ColorSpaceChange> colorSpaceChanges = new ConcurrentLinkedQueue<>();

    /** Uploads in the order they were requested. */
    private final Queue<Request> requests = new ConcurrentLinkedQueue<>();

    /**
     * Queue an upload. Safe from any thread.
     *
     * @param request The upload.
     */
    public void add(@NonNull Request request) {
        requests.add(request);
    }

    /**
     * Queue reading a texture in another color space. It happens at the start of a frame, once the
     * texture is uploaded. Safe from any thread.
     *
     * @param handle The texture.
     * @param colorSpace The color space to read it in.
     */
    public void changeColorSpace(@NonNull TextureHandle handle, @NonNull ColorSpace colorSpace) {
        colorSpaceChanges.add(new ColorSpaceChange(handle, colorSpace));
    }

    /**
     * Record queued uploads into the frame's command buffer, up to the per-frame budget, and mark
     * the textures resident. Uploads for textures that were released while waiting are skipped.
     * Render thread only.
     *
     * @param state The Vulkan state.
     * @param commandBuffer The frame's command buffer, recording, before any draws.
     * @param loader Records the copies.
     */
    public void record(
            @NonNull VulkanState state,
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull TextureLoaderVulkan loader) {
        long bytes = 0;
        Request request;
        while (bytes < BYTES_PER_FRAME && (request = requests.poll()) != null) {
            final StagingRing.Staging staging = request.staging();
            if (staging != null) {
                bytes += staging.size();
                // Read by this frame, so it can go once this frame finishes
                state.deferFree(() -> state.stagingRing.free(state, staging));
            }
            if (state.textureRegistry.resolve(request.handle()) == null) {
                // Released while pending, the deletion queue destroys the image
                continue;
            }
            loader.recordUpload(commandBuffer, request);
            state.bindlessTextures.update(state, request.info());
            state.textureRegistry.markResident(request.handle());
        }
        applyColorSpaceChanges(state, loader);
    }

    /**
     * Read textures in the color spaces asked for. Textures still waiting to upload keep waiting;
     * released ones are dropped. Render thread only.
     *
     * @param state The Vulkan state.
     * @param loader Makes the views.
     */
    private void applyColorSpaceChanges(
            @NonNull VulkanState state, @NonNull TextureLoaderVulkan loader) {
        // Only the ones queued so far, since the ones still waiting go back on the queue
        int count = colorSpaceChanges.size();
        ColorSpaceChange change;
        while (count-- > 0 && (change = colorSpaceChanges.poll()) != null) {
            final TextureInfoVulkan info = state.textureRegistry.resolve(change.handle());
            if (info == null) {
                continue;
            }
            if (!state.textureRegistry.isResident(change.handle())) {
                colorSpaceChanges.add(change);
                continue;
            }
            loader.applyColorSpace(info, change.colorSpace());
        }
    }

    /**
     * Drop every queued upload and free its staging data, when shutting down. The GPU must be idle.
     * The textures themselves are destroyed through the {@link TextureRegistry}.
     *
     * @param state The Vulkan state.
     */
    public void clear(@NonNull VulkanState state) {
        colorSpaceChanges.clear();
        Request request;
        while ((request = requests.poll()) != null) {
            if (request.staging() != null) {
                state.stagingRing.free(state, request.staging());
            }
        }
    }
}
