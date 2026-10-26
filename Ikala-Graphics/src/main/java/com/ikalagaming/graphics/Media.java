package com.ikalagaming.graphics;

import com.ikalagaming.graphics.scene.MediumRegistry;
import com.ikalagaming.graphics.scene.MediumVolume;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Places medium volumes, like smoke, fog banks and steam, in the scene for one plugin; they are
 * drawn in the fog volume with the scene's fog. Every volume placed here is owned by the plugin's
 * {@link GraphicsContext}, and is removed automatically when the plugin unloads if it wasn't
 * removed before then. A plugin can only change or remove its own volumes; anything else is ignored
 * with a warning. The scene's own fog is set through {@link com.ikalagaming.graphics.scene.Fog}.
 *
 * <p>Everything here is safe from any thread, and changes show from the next frame.
 *
 * <pre>
 * MediumHandle smoke = graphics.media().add(
 *         MediumVolume.of(Shape.CYLINDER, chimneyTop, new Vector3f(1.5f, 6, 0)));
 * graphics.media().update(smoke, volume.withCenter(newTop));
 * graphics.media().remove(smoke);
 * </pre>
 *
 * @see GraphicsContext#media()
 */
@Slf4j
public final class Media {

    /** The context these volumes belong to. */
    private final GraphicsContext context;

    /**
     * Create the media API for a context.
     *
     * @param context The owning context.
     */
    Media(@NonNull GraphicsContext context) {
        this.context = context;
    }

    /**
     * Place a medium volume.
     *
     * @param volume The volume.
     * @return Its handle, or null if the scene already holds {@link MediumRegistry#MAX_VOLUMES}.
     * @throws IllegalStateException If graphics isn't running.
     */
    public @Nullable MediumHandle add(@NonNull MediumVolume volume) {
        final MediumHandle handle = registry().add(owner(), volume);
        if (handle == null) {
            log.warn(
                    "{} placed a medium volume past the scene's {}, which isn't drawn",
                    owner(),
                    MediumRegistry.MAX_VOLUMES);
        }
        return handle;
    }

    /**
     * Change a medium volume this plugin owns: move it, reshape it or change its medium.
     *
     * @param handle The volume's handle.
     * @param volume What it is now.
     */
    public void update(@Nullable MediumHandle handle, @NonNull MediumVolume volume) {
        final MediumRegistry registry = registryOrNull();
        if (registry != null && !registry.update(owner(), handle, volume)) {
            ignored(registry, handle, "change");
        }
    }

    /**
     * Remove a medium volume this plugin owns. The handle, and any copies of it, become stale.
     * Nothing happens if the handle is null or already stale.
     *
     * @param handle The volume's handle.
     */
    public void remove(@Nullable MediumHandle handle) {
        final MediumRegistry registry = registryOrNull();
        if (registry != null && !registry.remove(owner(), handle)) {
            ignored(registry, handle, "remove");
        }
    }

    /**
     * Whether a handle still refers to a medium volume.
     *
     * @param handle The handle to check.
     * @return False if the handle is null or stale.
     */
    public boolean isValid(@Nullable MediumHandle handle) {
        final MediumRegistry registry = registryOrNull();
        return registry != null && registry.isValid(handle);
    }

    /**
     * The key this plugin's volumes are owned under.
     *
     * @return The owner key.
     */
    private String owner() {
        return context.getOwnerKey();
    }

    /**
     * Warn when a change to another plugin's volume was ignored. Stale handles are ignored quietly.
     *
     * @param registry The registry.
     * @param handle The handle.
     * @param action What was attempted, for the message.
     */
    private void ignored(
            @NonNull MediumRegistry registry,
            @Nullable MediumHandle handle,
            @NonNull String action) {
        final String owner = registry.ownerOf(handle);
        if (owner != null && !owner.equals(owner())) {
            log.warn(
                    "{} tried to {} a medium volume owned by {}, ignoring it",
                    owner(),
                    action,
                    owner);
        }
    }

    /**
     * The scene's medium registry, if graphics is running.
     *
     * @return The registry, or null.
     */
    private static MediumRegistry registryOrNull() {
        final Scene scene = GraphicsManager.getScene();
        return scene == null ? null : scene.getMediumRegistry();
    }

    /**
     * The scene's medium registry.
     *
     * @return The registry.
     * @throws IllegalStateException If graphics isn't running.
     */
    private static MediumRegistry registry() {
        final MediumRegistry registry = registryOrNull();
        if (registry == null) {
            throw new IllegalStateException("Graphics is not running");
        }
        return registry;
    }
}
