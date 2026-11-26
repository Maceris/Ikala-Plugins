package com.ikalagaming.graphics;

import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.NonNull;

/**
 * Makes preview views for a plugin: small scenes drawn into their own textures, like a material on
 * a sphere. They close when the plugin unloads.
 *
 * <pre>
 * PreviewView preview = context.previews().create(256, 256);
 * preview.getScene().getCamera().setPosition(0, 0, 3);
 * preview.place(sphere, new Vector3d(), new Quaternionf(), 1);
 * // each frame, in the GUI
 * TextureHandle texture = preview.getTexture();
 * if (texture != null) {
 *     IkGui.image(texture, 256, 256);
 * }
 * </pre>
 */
public final class Previews {
    /** The context these previews belong to. */
    private final GraphicsContext context;

    /**
     * Create the previews API for a context.
     *
     * @param context The owning context.
     */
    Previews(@NonNull GraphicsContext context) {
        this.context = context;
    }

    /**
     * Make a preview, drawn from the next frame that draws no world.
     *
     * @param width The width to draw it at, in pixels.
     * @param height The height to draw it at, in pixels.
     * @return The preview.
     * @throws IllegalStateException If the plugin was unloaded, the renderer isn't running, or
     *     every preview view is in use.
     */
    public PreviewView create(int width, int height) {
        context.checkOpen();
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null) {
            throw new IllegalStateException("The renderer is not running");
        }
        return renderer.getPreviews()
                .create(context.getOwnerKey(), width, height, renderer.getState());
    }
}
