package com.ikalagaming.graphics;

import com.ikalagaming.graphics.exceptions.TextureException;
import com.ikalagaming.graphics.texture.DdsFile;
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
     * Start loading a color texture from an image file, in the sRGB color space, which suits base
     * color and emissive textures and most images. The image is read on the calling thread.
     *
     * @param texturePath The full path to the image.
     * @return The handle for the new texture, which becomes resident once uploaded.
     * @throws TextureException If the image can't be read, or the texture can't be created.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     * @see #load(String, ColorSpace)
     */
    public TextureHandle load(@NonNull String texturePath) {
        return load(texturePath, ColorSpace.SRGB);
    }

    /**
     * Start loading a texture from an image file: a DDS file with the mip levels it holds, or any
     * image stb reads, as 8 bit RGBA with mip levels generated. The image is read on the calling
     * thread. Normal maps and other data load as {@link ColorSpace#LINEAR}.
     *
     * @param texturePath The full path to the image.
     * @param colorSpace The color space its values are in.
     * @return The handle for the new texture, which becomes resident once uploaded.
     * @throws TextureException If the image can't be read, or the texture can't be created.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     */
    public TextureHandle load(@NonNull String texturePath, @NonNull ColorSpace colorSpace) {
        context.checkOpen();
        return renderer().getTextureLoader().load(context.getOwnerKey(), texturePath, colorSpace);
    }

    /**
     * Start loading a texture from a DDS file already read, with the mip levels it holds.
     *
     * @param dds The file.
     * @param colorSpace The color space its values are in, for formats that can be either; the
     *     file's own say, if it has one, is {@link DdsFile#colorSpace()}.
     * @return The handle for the new texture, which becomes resident once uploaded.
     * @throws TextureException If the format isn't supported, or the texture can't be created.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     */
    public TextureHandle load(@NonNull DdsFile dds, @NonNull ColorSpace colorSpace) {
        context.checkOpen();
        return renderer().getTextureLoader().load(context.getOwnerKey(), dds, colorSpace);
    }

    /**
     * Read a texture in another color space from now on, such as a texture loaded as a color that
     * turns out to be a normal map. Shaders see the change from the start of a later frame, once
     * the texture is uploaded. Formats with only one version, like float or two channel ones, are
     * always linear, and are left as they are.
     *
     * @param texture The texture, which any plugin may switch.
     * @param colorSpace The color space to read it in.
     */
    public void setColorSpace(@Nullable TextureHandle texture, @NonNull ColorSpace colorSpace) {
        if (texture == null || !ColorSpace.canSwitch(texture.format())) {
            return;
        }
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer != null) {
            renderer.getState().textureUploads.changeColorSpace(texture, colorSpace);
        }
    }

    /**
     * The color space a texture is read in now. A switch asked for shows here once it has happened,
     * at the start of a frame.
     *
     * @param texture The texture.
     * @return The color space, or null if the handle is null or stale.
     */
    public @Nullable ColorSpace colorSpace(@Nullable TextureHandle texture) {
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null || renderer.getState().textureRegistry == null) {
            return null;
        }
        return renderer.getState().textureRegistry.colorSpaceOf(texture);
    }

    /**
     * Start loading a texture from raw pixel data. The data is copied before this returns, so the
     * buffer can be reused or freed right away.
     *
     * @param buffer The tightly packed pixel data, from its position to its limit. If null, the
     *     texture is cleared to transparent black where the format allows.
     * @param format The format of the pixel data. Colors encoded with the sRGB curve, as most
     *     images are, use an sRGB format such as {@link Format#R8G8B8A8_SRGB}; see {@link
     *     ColorSpace}.
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
