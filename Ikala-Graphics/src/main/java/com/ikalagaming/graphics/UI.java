package com.ikalagaming.graphics;

import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.UiManager;

import lombok.NonNull;

import java.util.function.Supplier;

/**
 * Retained UI owned by one plugin. Surfaces shown through it are removed when the plugin unloads.
 *
 * <p>Build a surface's tree on any thread, then {@link #show(Surface) show} it. Once shown, change
 * its nodes only on the render thread: inside node callbacks like button clicks, or through {@link
 * #post(Runnable)}.
 *
 * @see com.ikalagaming.graphics.ui
 */
public final class UI {
    /** The context that owns everything shown through this. */
    private final GraphicsContext context;

    /** Finds the UI manager, looked up when needed so contexts can exist before graphics starts. */
    private final Supplier<UiManager> manager;

    /**
     * Create the UI API for a context.
     *
     * @param context The owning context.
     * @param manager Finds the UI manager.
     */
    UI(@NonNull GraphicsContext context, @NonNull Supplier<UiManager> manager) {
        this.context = context;
        this.manager = manager;
    }

    /**
     * Create a surface to fill in and show. It isn't drawn until it is shown.
     *
     * @param id The surface ID, unique among all surfaces.
     * @return A new surface with an empty column as its content.
     */
    public Surface surface(@NonNull String id) {
        context.checkOpen();
        return new Surface(id);
    }

    /**
     * Show a surface from the next frame on, replacing any shown surface with the same ID.
     *
     * @param surface The surface.
     */
    public void show(@NonNull Surface surface) {
        context.checkOpen();
        manager.get().post(() -> manager.get().add(context, surface));
    }

    /**
     * Show or hide a surface this plugin showed, without removing it.
     *
     * @param id The surface ID.
     * @param visible Whether to draw it.
     */
    public void setVisible(@NonNull String id, boolean visible) {
        context.checkOpen();
        manager.get().post(() -> manager.get().setVisible(context, id, visible));
    }

    /**
     * Remove a surface this plugin showed.
     *
     * @param id The surface ID.
     */
    public void remove(@NonNull String id) {
        context.checkOpen();
        manager.get().post(() -> manager.get().remove(context, id));
    }

    /**
     * Run a change to shown UI on the render thread, at the start of the next frame.
     *
     * @param change The change.
     */
    public void post(@NonNull Runnable change) {
        context.checkOpen();
        manager.get().post(change);
    }
}
