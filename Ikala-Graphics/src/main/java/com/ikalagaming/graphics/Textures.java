package com.ikalagaming.graphics;

import com.ikalagaming.graphics.exceptions.TextureException;
import com.ikalagaming.graphics.vulkan.TextureInfoVulkan;
import com.ikalagaming.graphics.vulkan.TextureRegistry;
import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import javax.annotation.Nullable;

/**
 * Loads and releases textures for one plugin. Every texture loaded here is owned by the plugin's
 * {@link GraphicsContext}, and is released automatically when the plugin unloads if it wasn't
 * released before then. A plugin can only release its own textures.
 *
 * <p>Loading must happen on the render thread. Releasing and checking validity can happen on any
 * thread.
 *
 * @see GraphicsContext#textures()
 */
@Slf4j
public final class Textures {

    /** The context these textures belong to. */
    private final GraphicsContext context;

    /**
     * Create the textures API for a context.
     *
     * @param context The owning context.
     */
    Textures(@NonNull GraphicsContext context) {
        this.context = context;
    }

    /**
     * Load a texture from an image file.
     *
     * @param texturePath The full path to the image.
     * @return The handle for the new texture.
     * @throws TextureException If the image can't be read, or the texture can't be created.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     */
    public TextureHandle load(@NonNull String texturePath) {
        context.checkOpen();
        return renderer().getTextureLoader().load(context.getOwnerKey(), texturePath);
    }

    /**
     * Load a texture from raw pixel data.
     *
     * @param buffer The tightly packed pixel data. If null, we allocate texture memory but don't
     *     fill it with anything meaningful.
     * @param format The format of the pixel data.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @return The handle for the new texture.
     * @throws TextureException If the format isn't supported, or the texture can't be created.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     */
    public TextureHandle load(
            @Nullable ByteBuffer buffer, @NonNull Format format, int width, int height) {
        context.checkOpen();
        return renderer()
                .getTextureLoader()
                .load(context.getOwnerKey(), buffer, format, width, height);
    }

    /**
     * Release a texture this plugin owns. The handle, and any copies of it, become stale. Nothing
     * happens if the handle is null or already stale. Releasing another plugin's texture is not
     * allowed, and is ignored with a warning.
     *
     * @param texture The texture to release.
     */
    public void release(@Nullable TextureHandle texture) {
        if (texture == null) {
            return;
        }
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null) {
            return;
        }
        TextureRegistry registry = renderer.getState().textureRegistry;
        if (registry == null) {
            return;
        }
        final String owner = registry.ownerOf(texture);
        if (owner == null) {
            return;
        }
        if (!owner.equals(context.getOwnerKey())) {
            log.warn(
                    "{} tried to release a texture owned by {}, ignoring it",
                    context.getOwnerKey(),
                    owner);
            return;
        }
        TextureInfoVulkan info = registry.remove(texture);
        if (info != null) {
            GraphicsManager.getDeletionQueue().add(info);
        }
    }

    /**
     * Whether a handle still refers to a live texture.
     *
     * @param texture The handle to check.
     * @return False if the handle is null or stale.
     */
    public boolean isValid(@Nullable TextureHandle texture) {
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null || renderer.getState().textureRegistry == null) {
            return false;
        }
        return renderer.getState().textureRegistry.isValid(texture);
    }

    /**
     * Fetch the running renderer.
     *
     * @return The renderer.
     * @throws IllegalStateException If the renderer isn't running.
     */
    private static VulkanInstance renderer() {
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null) {
            throw new IllegalStateException("The renderer is not running");
        }
        return renderer;
    }
}
