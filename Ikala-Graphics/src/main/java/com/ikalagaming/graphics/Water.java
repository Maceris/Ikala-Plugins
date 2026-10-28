package com.ikalagaming.graphics;

import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.WaterEffects;
import com.ikalagaming.graphics.scene.WaterRegistry;
import com.ikalagaming.graphics.scene.WaterTile;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import javax.annotation.Nullable;

/**
 * Places water in the scene for one plugin, as height-field tiles: where water is, how high its
 * surface stands and what it holds, which sets its color and clarity. Graphics shades it: light
 * reflected from the sky and the sun, and the bottom and the light scattered in the water seen
 * through the surface. Game water of blocks with levels and slopes becomes heights; graphics knows
 * nothing of blocks. Every tile placed here is owned by the plugin's {@link GraphicsContext}, and
 * is removed automatically when the plugin unloads if it wasn't removed before then. A plugin can
 * only change or remove its own tiles; anything else is ignored with a warning.
 *
 * <p>Everything here is safe from any thread, and changes show from the next frame.
 *
 * <pre>
 * WaterHandle pond = graphics.water().add(
 *         WaterTile.flat(corner, 1, 16, 16, 0.9f, WaterBody.ofChlorophyll(3)));
 * graphics.water().update(pond, tile.withBody(WaterBody.MUDDY));
 * graphics.water().remove(pond);
 * </pre>
 *
 * @see GraphicsContext#water()
 */
@Slf4j
public final class Water {

    /** The context these tiles belong to. */
    private final GraphicsContext context;

    /**
     * Create the water API for a context.
     *
     * @param context The owning context.
     */
    Water(@NonNull GraphicsContext context) {
        this.context = context;
    }

    /**
     * Place a water tile.
     *
     * @param tile The tile.
     * @return Its handle, or null if the scene already holds {@link WaterRegistry#MAX_TILES}.
     * @throws IllegalStateException If graphics isn't running.
     */
    public @Nullable WaterHandle add(@NonNull WaterTile tile) {
        final WaterHandle handle = registry().add(owner(), tile);
        if (handle == null) {
            log.warn(
                    "{} placed a water tile past the scene's {}, which isn't drawn",
                    owner(),
                    WaterRegistry.MAX_TILES);
        }
        return handle;
    }

    /**
     * Change a water tile this plugin owns: its heights, where water is, or what it holds.
     *
     * @param handle The tile's handle.
     * @param tile What it is now.
     */
    public void update(@Nullable WaterHandle handle, @NonNull WaterTile tile) {
        final WaterRegistry registry = registryOrNull();
        if (registry != null && !registry.update(owner(), handle, tile)) {
            ignored(registry, handle, "change");
        }
    }

    /**
     * Remove a water tile this plugin owns. The handle, and any copies of it, become stale. Nothing
     * happens if the handle is null or already stale.
     *
     * @param handle The tile's handle.
     */
    public void remove(@Nullable WaterHandle handle) {
        final WaterRegistry registry = registryOrNull();
        if (registry != null && !registry.remove(owner(), handle)) {
            ignored(registry, handle, "remove");
        }
    }

    /**
     * Set the water rippling, as a stone dropped in or a swimmer does: ripples leave the disturbed
     * patch as a ring, reflect off the shores and die away. Only water near the camera ripples.
     * Something moving through water can call this every frame along its way.
     *
     * @param position Where, in the world.
     * @param radius How far around it, in meters.
     * @param strength How high the ripples start, in meters: a few centimeters for a footstep, tens
     *     for a boulder falling in.
     */
    public void disturb(@NonNull Vector3dc position, float radius, float strength) {
        final WaterRegistry registry = registryOrNull();
        if (registry != null && radius > 0 && strength > 0) {
            registry.disturb(
                    new WaterRegistry.Disturbance(new Vector3d(position), radius, strength));
        }
    }

    /**
     * Set the wind's waves over a patch of water by hand for the next frame, as the water surface
     * wavelets paper's amplitude painting does: in the patch the waves go only the way it points,
     * and they carry on past it, so painting along a path every frame sends waves along it. Only
     * water near the camera, under a wind, takes paint.
     *
     * @param position Where, in the world.
     * @param radius How far around it, in meters; it fades out toward the edge.
     * @param direction The way the waves go, in radians from +x toward +z.
     * @param strength How tall they are, as a share of the wind's waves in its own direction: 1 for
     *     as tall, 0 to calm the patch.
     */
    public void paintWaves(
            @NonNull Vector3dc position, float radius, float direction, float strength) {
        final WaterRegistry registry = registryOrNull();
        if (registry != null && radius > 0 && strength >= 0) {
            registry.paint(
                    new WaterRegistry.WavePaint(
                            new Vector3d(position), radius, direction, strength));
        }
    }

    /**
     * Set the particles the water throws up, for the whole scene: spray off breaking crests, mist
     * and droplets where falling water lands or the water is disturbed, and droplets where rain and
     * the like land in it. They stay until this plugin sets others or unloads, when the water's
     * own, {@link WaterEffects#DEFAULT}, come back.
     *
     * @param effects The effects, {@link WaterEffects#NONE} for none, or null for the water's own.
     */
    public void setEffects(@Nullable WaterEffects effects) {
        final WaterRegistry registry = registryOrNull();
        if (registry != null) {
            registry.setEffects(owner(), effects);
        }
    }

    /**
     * Whether a handle still refers to a water tile.
     *
     * @param handle The handle to check.
     * @return False if the handle is null or stale.
     */
    public boolean isValid(@Nullable WaterHandle handle) {
        final WaterRegistry registry = registryOrNull();
        return registry != null && registry.isValid(handle);
    }

    /**
     * The key this plugin's tiles are owned under.
     *
     * @return The owner key.
     */
    private String owner() {
        return context.getOwnerKey();
    }

    /**
     * Warn when a change to another plugin's tile was ignored. Stale handles are ignored quietly.
     *
     * @param registry The registry.
     * @param handle The handle.
     * @param action What was attempted, for the message.
     */
    private void ignored(
            @NonNull WaterRegistry registry, @Nullable WaterHandle handle, @NonNull String action) {
        final String owner = registry.ownerOf(handle);
        if (owner != null && !owner.equals(owner())) {
            log.warn(
                    "{} tried to {} a water tile owned by {}, ignoring it", owner(), action, owner);
        }
    }

    /**
     * The scene's water registry, if graphics is running.
     *
     * @return The registry, or null.
     */
    private static WaterRegistry registryOrNull() {
        final Scene scene = GraphicsManager.getScene();
        return scene == null ? null : scene.getWaterRegistry();
    }

    /**
     * The scene's water registry.
     *
     * @return The registry.
     * @throws IllegalStateException If graphics isn't running.
     */
    private static WaterRegistry registry() {
        final WaterRegistry registry = registryOrNull();
        if (registry == null) {
            throw new IllegalStateException("Graphics is not running");
        }
        return registry;
    }
}
