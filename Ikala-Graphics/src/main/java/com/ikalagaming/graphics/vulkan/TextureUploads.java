package com.ikalagaming.graphics.vulkan;

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
     * @param staging The pixel data for mip level 0, or null to clear the texture instead.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param mipLevels The number of mip levels to generate.
     * @param clear Whether the format can be cleared, for a texture with no data.
     */
    public record Request(
            @NonNull TextureHandle handle,
            @NonNull TextureInfoVulkan info,
            @Nullable StagingRing.Staging staging,
            int width,
            int height,
            int mipLevels,
            boolean clear) {}

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
    }

    /**
     * Drop every queued upload and free its staging data, when shutting down. The GPU must be idle.
     * The textures themselves are destroyed through the {@link TextureRegistry}.
     *
     * @param state The Vulkan state.
     */
    public void clear(@NonNull VulkanState state) {
        Request request;
        while ((request = requests.poll()) != null) {
            if (request.staging() != null) {
                state.stagingRing.free(state, request.staging());
            }
        }
    }
}
