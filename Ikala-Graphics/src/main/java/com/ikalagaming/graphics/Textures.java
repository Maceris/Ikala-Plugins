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
 * <p>Everything here is safe from any thread. Loading doesn't wait for the GPU: the image is
 * created and the data copied to staging memory right away, and the render thread uploads it at the
 * start of a later frame, in the order textures were loaded, spread over frames when many are
 * loaded at once. Until then the handle is valid but not {@linkplain #isResident(TextureHandle)
 * resident}, and anything drawn with it uses the default white texture.
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
     * Start loading a texture from an image file. The image is decoded on the calling thread.
     *
     * @param texturePath The full path to the image.
     * @return The handle for the new texture, which becomes resident once uploaded.
     * @throws TextureException If the image can't be read, or the texture can't be created.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     */
    public TextureHandle load(@NonNull String texturePath) {
        context.checkOpen();
        return renderer().getTextureLoader().load(context.getOwnerKey(), texturePath);
    }

    /**
     * Start loading a texture from raw pixel data. The data is copied before this returns, so the
     * buffer can be reused or freed right away.
     *
     * @param buffer The tightly packed pixel data, from its position to its limit. If null, the
     *     texture is cleared to transparent black where the format allows.
     * @param format The format of the pixel data.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @return The handle for the new texture, which becomes resident once uploaded.
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
     * Whether a texture has been uploaded, so drawing with it shows its contents instead of the
     * default white texture.
     *
     * @param texture The handle to check.
     * @return False if the handle is null, stale, or still loading.
     */
    public boolean isResident(@Nullable TextureHandle texture) {
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null || renderer.getState().textureRegistry == null) {
            return false;
        }
        return renderer.getState().textureRegistry.isResident(texture);
    }

    /**
     * Whether a handle still refers to a live texture, which may still be loading.
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
