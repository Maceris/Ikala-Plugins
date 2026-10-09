package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.gui.component.Component;
import com.ikalagaming.graphics.gui.component.GuiWindow;
import com.ikalagaming.graphics.gui.component.MainToolbar;
import com.ikalagaming.graphics.gui.windows.IkGuiDemo;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.ui.UiManager;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks and engages all the things we want to render. Windows and the toolbar are owned by the
 * graphics context of the plugin that added them, and are removed when that plugin unloads.
 *
 * <p>Windows are added and removed from plugin threads while the render thread draws them, so
 * drawing picks up each change within a frame.
 */
@Slf4j
public class WindowManager {

    /**
     * Something registered by a plugin.
     *
     * @param owner The context of the plugin that registered it.
     * @param value The registered thing.
     * @param <T> The type of thing.
     */
    private record Owned<T>(@NonNull GraphicsContext owner, @NonNull T value) {}

    /** A table for looking up specific window by name. */
    private final Map<String, Owned<GuiWindow>> windows;

    /** The toolbar, if any. */
    private final AtomicReference<Owned<MainToolbar>> toolbar;

    /** Retained UI surfaces, drawn after the windows, or null for none. */
    private volatile UiManager uiManager;

    public WindowManager() {
        windows = new ConcurrentHashMap<>();
        toolbar = new AtomicReference<>();
    }

    /**
     * Add and register a named gui window, owned by a plugin. It is removed when the plugin
     * unloads.
     *
     * @param owner The graphics context of the plugin adding the window.
     * @param name The unique name of the window.
     * @param component The component.
     * @throws IllegalStateException If the context was closed because its plugin unloaded.
     */
    public void addWindow(
            @NonNull GraphicsContext owner, @NonNull String name, @NonNull GuiWindow component) {
        checkOpen(owner);
        Owned<GuiWindow> previous = windows.put(name, new Owned<>(owner, component));
        if (previous == null) {
            return;
        }
        if (previous.owner().getOwner().equals(owner.getOwner())) {
            // A reloaded plugin, re-adding its window before the old instance's unload is handled
            log.debug("Window {} replaced by {}", name, owner.getOwnerKey());
        } else {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Window with name {} already exists, owned by {}",
                    name,
                    previous.owner().getOwner());
        }
    }

    /**
     * Set the retained UI surfaces to draw after the windows.
     *
     * @param manager The UI manager, or null for none.
     */
    public void setUiManager(UiManager manager) {
        uiManager = manager;
    }

    /**
     * Set the main toolbar, owned by a plugin. It is removed when the plugin unloads.
     *
     * @param owner The graphics context of the plugin setting the toolbar.
     * @param newToolbar The toolbar.
     * @throws IllegalStateException If the context was closed because its plugin unloaded.
     */
    public void setToolbar(@NonNull GraphicsContext owner, @NonNull MainToolbar newToolbar) {
        checkOpen(owner);
        toolbar.set(new Owned<>(owner, newToolbar));
    }

    /**
     * The current main toolbar.
     *
     * @return The toolbar, or null if there isn't one.
     */
    public MainToolbar getToolbar() {
        return value(toolbar.get());
    }

    /**
     * Remove the main toolbar, if a specific plugin owns it.
     *
     * @param owner The graphics context of the plugin removing the toolbar. A toolbar set by any
     *     other context is left alone.
     */
    public void removeToolbar(@NonNull GraphicsContext owner) {
        toolbar.updateAndGet(
                current -> current != null && current.owner() == owner ? null : current);
    }

    /**
     * Remove every window and the toolbar a context owns. Called when its plugin unloads, from any
     * thread.
     *
     * @param owner The context whose windows should go.
     * @return How many windows were removed, not counting the toolbar.
     */
    public int removeAllOwnedBy(@NonNull GraphicsContext owner) {
        int[] removed = {0};
        windows.values()
                .removeIf(
                        entry -> {
                            if (entry.owner() == owner) {
                                ++removed[0];
                                return true;
                            }
                            return false;
                        });
        removeToolbar(owner);
        return removed[0];
    }

    /**
     * Make sure a context can still own things.
     *
     * @param owner The context.
     * @throws IllegalStateException If it was closed.
     */
    private static void checkOpen(GraphicsContext owner) {
        if (owner.isClosed()) {
            throw new IllegalStateException(
                    "The graphics context for "
                            + owner.getOwner()
                            + " was closed when it unloaded");
        }
    }

    /**
     * Unwrap something that may not be registered.
     *
     * @param owned The registration, or null.
     * @return The value, or null.
     * @param <T> The type of value.
     */
    private static <T> T value(Owned<T> owned) {
        return owned == null ? null : owned.value();
    }

    /**
     * Disable a component by name.
     *
     * @param name The name of the component to hide.
     * @see #setVisible(String, boolean)
     */
    public void hide(@NonNull String name) {
        setVisible(name, false);
    }

    /**
     * Used to draw the GUI.
     *
     * @param width The width of the window, in pixels.
     * @param height The width of the height, in pixels.
     */
    public void drawGui(final int width, final int height) {
        IkGui.newFrame();

        windows.values().stream()
                .map(Owned::value)
                .filter(Component::isVisible)
                .forEach(window -> window.draw(width, height));

        final UiManager currentUi = uiManager;
        if (currentUi != null) {
            currentUi.draw();
        }

        MainToolbar currentToolbar = getToolbar();
        if (currentToolbar != null) {
            currentToolbar.draw(width, height);
        }

        // Defer this until right at the end
        if (isVisible(IkGuiDemo.WINDOW_NAME)) {
            IkGui.showDemoWindow();
        }
        IkGui.render();
    }

    /**
     * Make a component visible based on the name.
     *
     * @param name The name od the component to show.
     * @see #setVisible(String, boolean)
     */
    public void show(@NonNull String name) {
        setVisible(name, true);
    }

    /**
     * Process GUI inputs, which might happen at a different frequency than rendering.
     *
     * @param scene The scene we are rendering.
     * @param window The window we are using.
     */
    public void handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        MainToolbar currentToolbar = getToolbar();
        if (currentToolbar != null) {
            currentToolbar.handleGuiInput(scene, window);
        }

        windows.values().stream()
                .map(Owned::value)
                .filter(Component::isVisible)
                .forEach(component -> component.handleGuiInput(scene, window));
    }

    /**
     * Update any internal values as required.
     *
     * @param scene The scene we are rendering.
     * @param window The window we are using.
     */
    public void updateValues(@NonNull Scene scene, @NonNull Window window) {
        windows.values().stream()
                .map(Owned::value)
                .filter(Component::isVisible)
                .forEach(component -> component.updateValues(scene, window));
    }

    /**
     * Returns whether a component is enabled. Components that are not found are considered
     * disabled.
     *
     * @param name The name of the component to check.
     * @return Whether the specified component is enabled.
     */
    public boolean isVisible(@NonNull String name) {
        return Optional.ofNullable(value(windows.get(name)))
                .map(Component::isVisible)
                .orElse(false);
    }

    /**
     * Remove and unregister a component by name.
     *
     * @param name The name of the component to remove.
     */
    public void removeWindow(@NonNull String name) {
        this.windows.remove(name);
    }

    /**
     * Show/hide a component by name.
     *
     * @param name The name of the component to show/hide.
     * @param visible True if the component should show, false if it should be hidden.
     * @see #setVisible(String, boolean)
     */
    public void setVisible(@NonNull String name, boolean visible) {
        Optional.ofNullable(value(windows.get(name)))
                .ifPresent(component -> component.setVisible(visible));
    }
}
