package com.ikalagaming.factory.gui;

import com.ikalagaming.graphics.GuiInstance;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;

import imgui.ImGui;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Tracks and engages all the things we want to render with ImGui. */
public class GuiManager implements GuiInstance {
    private final Map<String, GuiWindow> windows;

    /** The component sizes to use for UIs. */
    @Getter @Setter @NonNull private SizeConstants currentSizes = SizeConstants.MEDIUM;

    public GuiManager() {
        windows = new HashMap<>();
    }

    /**
     * Register a named gui component. This is expected for larger things like a menu or floating
     * window, rather than a button or list.
     *
     * @param name The unique name of the component.
     * @param component The component.
     */
    public void addWindow(@NonNull String name, @NonNull GuiWindow component) {
        this.windows.put(name, component);
    }

    /**
     * Remove a component by name.
     *
     * @param name The name od the component to remove.
     */
    public void removeWindow(@NonNull String name) {
        this.windows.remove(name);
    }

    /**
     * Enable a component by name.
     *
     * @param name The name od the component to show.
     * @see #setEnabled(String, boolean)
     */
    public void enable(@NonNull String name) {
        setEnabled(name, true);
    }

    /**
     * Disable a component by name.
     *
     * @param name The name od the component to hide.
     * @see #setEnabled(String, boolean)
     */
    public void disable(@NonNull String name) {
        setEnabled(name, false);
    }

    /**
     * Enable/disable a component by name.
     *
     * @param name The name of the component to show/hide.
     * @param enabled True if the component should show, false if it should be hidden.
     * @see #setEnabled(String, boolean)
     */
    public void setEnabled(@NonNull String name, boolean enabled) {
        Optional.ofNullable(this.windows.get(name))
                .ifPresent(component -> component.setVisible(enabled));
    }

    /**
     * Returns whether a component is enabled. Components that are not found are considered
     * disabled.
     *
     * @param name The name of the component to check.
     * @return Whether the specified component is enabled.
     */
    public boolean isEnabled(@NonNull String name) {
        return Optional.ofNullable(this.windows.get(name)).map(GuiWindow::isVisible).orElse(false);
    }

    @Override
    public void drawGui() {
        ImGui.newFrame();

        windows.values().stream().filter(GuiWindow::isVisible).forEach(GuiInstance::drawGui);

        ImGui.endFrame();
        ImGui.render();
    }

    @Override
    public void handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        windows.values().stream()
                .filter(GuiWindow::isVisible)
                .forEach(component -> component.handleGuiInput(scene, window));
    }
}
