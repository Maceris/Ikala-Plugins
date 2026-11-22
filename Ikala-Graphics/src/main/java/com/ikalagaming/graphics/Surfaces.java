package com.ikalagaming.graphics;

import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.vulkan.DeformationField;
import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.NonNull;

/**
 * Presses prints into the soft surfaces near the camera, see {@link SurfaceLayer} and {@link
 * SurfaceStamp}: feet, bodies, wheels. Prints show only within about 32 m of the camera; past that
 * a surface is drawn undisturbed, and prints that fall out of that window are lost. They fill back
 * in at their surface's refill rate. Stamps aren't saved: the game sends them each frame its things
 * touch a surface. Safe from any thread.
 *
 * @see GraphicsContext#surfaces()
 */
public final class Surfaces {

    /** The context these surfaces belong to. */
    private final GraphicsContext context;

    /**
     * Create the surfaces API for a context.
     *
     * @param context The owning context.
     */
    Surfaces(@NonNull GraphicsContext context) {
        this.context = context;
    }

    /**
     * Press a shape into the surfaces under it this frame.
     *
     * @param stamp The shape.
     */
    public void stamp(@NonNull SurfaceStamp stamp) {
        context.checkOpen();
        final DeformationField field = field();
        if (field != null) {
            field.stamp(stamp);
        }
    }

    /**
     * Set how fast a material's surfaces fill back in once pressed, unless a column says its own:
     * faster while it snows, say. None fill in until given a rate.
     *
     * @param material The material.
     * @param metersPerSecond How fast, in meters a second.
     */
    public void setRefillRate(@NonNull Material material, float metersPerSecond) {
        context.checkOpen();
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null || renderer.getState().sections == null) {
            throw new IllegalStateException("The renderer is not running");
        }
        renderer.getState()
                .sections
                .surfaces
                .setRefillRate(
                        GraphicsManager.getScene().getMaterialCache().getMaterialIndex(material),
                        metersPerSecond);
    }

    /**
     * The running renderer's deformation field.
     *
     * @return The field, or null if the renderer isn't running.
     */
    private static DeformationField field() {
        final VulkanInstance renderer = GraphicsManager.getRenderInstance();
        return renderer == null ? null : renderer.getState().deformation;
    }
}
