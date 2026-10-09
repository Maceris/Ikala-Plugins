package com.ikalagaming.graphics;

import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.spec.NodeFactory;
import com.ikalagaming.graphics.ui.spec.SpecBindings;
import com.ikalagaming.graphics.ui.spec.SpecException;
import com.ikalagaming.graphics.ui.spec.SpecInstance;
import com.ikalagaming.graphics.ui.spec.SpecLoader;
import com.ikalagaming.graphics.ui.spec.UiSpec;
import com.ikalagaming.graphics.ui.style.Theme;
import com.ikalagaming.graphics.ui.style.ThemeException;
import com.ikalagaming.graphics.ui.style.ThemeLoader;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;

import lombok.NonNull;

import java.io.File;
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
     * Replace the theme for all retained UI, until this plugin unloads or chooses another. Meant
     * for the plugin that owns the game's look, like a game client.
     *
     * @param theme The theme, or null to go back to the default.
     */
    public void useTheme(Theme theme) {
        context.checkOpen();
        manager.get().post(() -> manager.get().useTheme(context, theme));
    }

    /**
     * Add this plugin's own tokens and classes on top of the theme, until it unloads. Prefix the
     * names with the plugin, like {@code converter.error}, so they don't clash with others.
     *
     * @param extension A theme holding the tokens and classes.
     */
    public void addStyles(@NonNull Theme extension) {
        context.checkOpen();
        manager.get().post(() -> manager.get().addStyles(context, extension));
    }

    /**
     * Load a theme from this plugin's data folder. Its styles may use the default theme's tokens.
     *
     * @param path The path within the plugin's data folder.
     * @return The theme.
     * @throws ThemeException If the theme is broken.
     * @throws java.io.UncheckedIOException If it can't be read.
     */
    public Theme loadTheme(@NonNull String path) {
        context.checkOpen();
        File file = PluginFolder.getResource(context.getOwner(), ResourceType.DATA, path);
        return ThemeLoader.load(file.toPath(), manager.get().getDefaultTheme());
    }

    /**
     * Open a UI spec: build it now and show it from the next frame. Its handlers and observables
     * come from the bindings. It is closed when this plugin unloads.
     *
     * @param spec The spec.
     * @param bindings The handlers, observables, lists and resource bundle the spec names.
     * @return The open spec, which can find nodes and be closed.
     * @throws SpecException If the spec can't be built with these bindings, such as a missing
     *     handler; nothing is shown then.
     */
    public SpecInstance open(@NonNull UiSpec spec, @NonNull SpecBindings bindings) {
        context.checkOpen();
        return SpecInstance.open(manager.get(), context, spec, bindings);
    }

    /**
     * Load a UI spec from this plugin's data folder, so it can be hot reloaded in the editor.
     *
     * @param path The path within the plugin's data folder.
     * @return The spec.
     * @throws SpecException If the spec is broken.
     * @throws java.io.UncheckedIOException If it can't be read.
     */
    public UiSpec loadSpec(@NonNull String path) {
        context.checkOpen();
        File file = PluginFolder.getResource(context.getOwner(), ResourceType.DATA, path);
        return SpecLoader.load(file.toPath());
    }

    /**
     * Add a node type that UI specs can use, owned by this plugin. When it unloads, the type is
     * removed and every open spec using it is closed, from any plugin.
     *
     * @param name The type name; prefix it with the plugin, like {@code converter.slot}.
     * @param factory Makes nodes of the type.
     * @throws IllegalArgumentException If the name is already taken.
     */
    public void registerNodeType(@NonNull String name, @NonNull NodeFactory factory) {
        context.checkOpen();
        manager.get().getNodeTypes().register(context, name, factory);
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
