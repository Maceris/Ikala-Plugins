package com.ikalagaming.graphics;

import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.AnimationState;
import com.ikalagaming.graphics.vulkan.InstanceRegistry;
import com.ikalagaming.graphics.vulkan.InstanceTable;
import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import javax.annotation.Nullable;

/**
 * Places models in the scene for one plugin. Every instance placed here is owned by the plugin's
 * {@link GraphicsContext}, and is removed automatically when the plugin unloads if it wasn't
 * removed before then. A plugin can only change or remove its own instances; anything else is
 * ignored with a warning.
 *
 * <p>Everything here is safe from any thread, and changes show from the next frame. The renderer
 * keeps every instance on the GPU across frames and only uploads the ones that changed, so a scene
 * that holds still costs almost nothing to keep up to date.
 *
 * <pre>
 * InstanceHandle ball = graphics.instances().place(ballModel, position, rotation, 0.003f);
 * graphics.instances().move(ball, 1, 0, 0);
 * graphics.instances().setMaterial(ball, 0, shinyRed);
 * graphics.instances().remove(ball);
 * </pre>
 *
 * @see GraphicsContext#instances()
 */
@Slf4j
public final class Instances {

    /** The context these instances belong to. */
    private final GraphicsContext context;

    /**
     * Create the instances API for a context.
     *
     * @param context The owning context.
     */
    Instances(@NonNull GraphicsContext context) {
        this.context = context;
    }

    /**
     * Place a model in the scene. The model must be in the scene, with its meshes registered.
     *
     * @param model The model.
     * @param position The world position.
     * @param rotation The rotation.
     * @param scale The uniform scale.
     * @return The handle.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     */
    public InstanceHandle place(
            @NonNull Model model,
            @NonNull Vector3dc position,
            @NonNull Quaternionfc rotation,
            float scale) {
        context.checkOpen();
        return table().getRegistry().place(context.getOwnerKey(), model, position, rotation, scale);
    }

    /**
     * Place a model in the scene with no rotation and its own size.
     *
     * @param model The model.
     * @param x The world x position.
     * @param y The world y position.
     * @param z The world z position.
     * @return The handle.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     */
    public InstanceHandle place(@NonNull Model model, double x, double y, double z) {
        return place(model, new Vector3d(x, y, z), new Quaternionf(), 1);
    }

    /**
     * Move an instance.
     *
     * @param instance The instance.
     * @param x The new world x position.
     * @param y The new world y position.
     * @param z The new world z position.
     */
    public void move(@Nullable InstanceHandle instance, double x, double y, double z) {
        transform(instance, new Vector3d(x, y, z), null, Float.NaN);
    }

    /**
     * Rotate an instance.
     *
     * @param instance The instance.
     * @param rotation The new rotation.
     */
    public void rotate(@Nullable InstanceHandle instance, @NonNull Quaternionfc rotation) {
        transform(instance, null, rotation, Float.NaN);
    }

    /**
     * Resize an instance.
     *
     * @param instance The instance.
     * @param scale The new uniform scale.
     */
    public void scale(@Nullable InstanceHandle instance, float scale) {
        transform(instance, null, null, scale);
    }

    /**
     * Change several parts of an instance's transform at once.
     *
     * @param instance The instance.
     * @param position The new world position, or null to keep it.
     * @param rotation The new rotation, or null to keep it.
     * @param scale The new uniform scale, or NaN to keep it.
     */
    public void transform(
            @Nullable InstanceHandle instance,
            @Nullable Vector3dc position,
            @Nullable Quaternionfc rotation,
            float scale) {
        InstanceRegistry registry = registry();
        if (registry != null && !registry.transform(owner(), instance, position, rotation, scale)) {
            ignored(registry, instance, "move");
        }
    }

    /**
     * Change the material one of an instance's meshes is drawn with. The material must be in the
     * scene's material cache, or it is ignored when drawing.
     *
     * @param instance The instance.
     * @param meshIndex The mesh, by its index in the model.
     * @param material The material, or null to use the mesh's own.
     * @throws IndexOutOfBoundsException If the model has no such mesh.
     */
    public void setMaterial(
            @Nullable InstanceHandle instance, int meshIndex, @Nullable Material material) {
        InstanceRegistry registry = registry();
        if (registry != null && !registry.setMaterial(owner(), instance, meshIndex, material)) {
            ignored(registry, instance, "change the material of");
        }
    }

    /**
     * Change an instance's animation.
     *
     * @param instance The instance.
     * @param animation The animation state, or null to stop animating.
     */
    public void setAnimation(
            @Nullable InstanceHandle instance, @Nullable AnimationState animation) {
        InstanceRegistry registry = registry();
        if (registry != null && !registry.setAnimation(owner(), instance, animation)) {
            ignored(registry, instance, "animate");
        }
    }

    /**
     * Read an instance's position.
     *
     * @param instance The instance.
     * @param dest Where to store the world position.
     * @return False if the handle is null or stale, leaving dest unchanged.
     */
    public boolean getPosition(@Nullable InstanceHandle instance, @NonNull Vector3d dest) {
        InstanceRegistry registry = registry();
        return registry != null && registry.getPosition(instance, dest);
    }

    /**
     * Read an instance's rotation.
     *
     * @param instance The instance.
     * @param dest Where to store the rotation.
     * @return False if the handle is null or stale, leaving dest unchanged.
     */
    public boolean getRotation(@Nullable InstanceHandle instance, @NonNull Quaternionf dest) {
        InstanceRegistry registry = registry();
        return registry != null && registry.getRotation(instance, dest);
    }

    /**
     * Remove an instance this plugin owns. The handle, and any copies of it, become stale, and it
     * stops being drawn. Nothing happens if the handle is null or already stale.
     *
     * @param instance The instance.
     */
    public void remove(@Nullable InstanceHandle instance) {
        InstanceTable table = tableOrNull();
        if (table == null) {
            return;
        }
        int slot = table.getRegistry().remove(owner(), instance);
        if (slot >= 0) {
            table.retire(slot);
        } else {
            ignored(table.getRegistry(), instance, "remove");
        }
    }

    /**
     * Whether a handle still refers to a placed instance.
     *
     * @param instance The handle to check.
     * @return False if the handle is null or stale.
     */
    public boolean isValid(@Nullable InstanceHandle instance) {
        InstanceRegistry registry = registry();
        return registry != null && registry.isValid(instance);
    }

    /**
     * The key this plugin's instances are owned under.
     *
     * @return The owner key.
     */
    private String owner() {
        return context.getOwnerKey();
    }

    /**
     * Warn when a change to another plugin's instance was ignored. Stale handles are ignored
     * quietly, since a handle can go stale while its owner is still using it.
     *
     * @param registry The registry.
     * @param instance The handle.
     * @param action What was attempted, for the message.
     */
    private void ignored(
            @NonNull InstanceRegistry registry,
            @Nullable InstanceHandle instance,
            @NonNull String action) {
        String owner = registry.ownerOf(instance);
        if (owner != null && !owner.equals(owner())) {
            log.warn("{} tried to {} an instance owned by {}, ignoring it", owner(), action, owner);
        }
    }

    /**
     * The instance registry, if the renderer is running.
     *
     * @return The registry, or null.
     */
    private static InstanceRegistry registry() {
        InstanceTable table = tableOrNull();
        return table == null ? null : table.getRegistry();
    }

    /**
     * The instance table, if the renderer is running.
     *
     * @return The table, or null.
     */
    private static InstanceTable tableOrNull() {
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        return renderer == null ? null : renderer.getState().instances;
    }

    /**
     * The instance table.
     *
     * @return The table.
     * @throws IllegalStateException If the renderer isn't running.
     */
    private static InstanceTable table() {
        InstanceTable table = tableOrNull();
        if (table == null) {
            throw new IllegalStateException("The renderer is not running");
        }
        return table;
    }
}
