package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.MouseInput;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.gui.component.Component;
import com.ikalagaming.graphics.frontend.gui.component.GuiWindow;
import com.ikalagaming.graphics.frontend.gui.component.MainToolbar;
import com.ikalagaming.graphics.scene.Scene;

import imgui.ImGui;
import imgui.ImGuiIO;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector2f;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Tracks and engages all the things we want to render. */
public class WindowManager {

    /** A table for looking up specific window by name. */
    private final Map<String, GuiWindow> windows;

    @Getter @Setter private MainToolbar toolbar;

    public WindowManager() {
        windows = new HashMap<>();
    }

    /**
     * Add and register a named gui window.
     *
     * @param name The unique name of the window.
     * @param component The component.
     */
    public void addWindow(@NonNull String name, @NonNull GuiWindow component) {
        this.windows.put(name, component);
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
        ImGui.newFrame();

        windows.values().stream()
                .filter(Component::isVisible)
                .forEach(window -> window.draw(width, height));

        if (toolbar != null) {
            toolbar.draw(width, height);
        }

        ImGui.endFrame();
        ImGui.render();
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
        ImGuiIO imGuiIO = ImGui.getIO();
        MouseInput mouseInput = window.getMouseInput();
        Vector2f mousePos = mouseInput.getCurrentPos();
        imGuiIO.setMousePos(mousePos.x, mousePos.y);
        imGuiIO.setMouseDown(0, mouseInput.isLeftButtonPressed());
        imGuiIO.setMouseDown(1, mouseInput.isRightButtonPressed());

        if (toolbar != null) {
            toolbar.handleGuiInput(scene, window);
        }

        windows.values().stream()
                .filter(Component::isVisible)
                .forEach(component -> component.handleGuiInput(scene, window));
    }

    /**
     * Returns whether a component is enabled. Components that are not found are considered
     * disabled.
     *
     * @param name The name of the component to check.
     * @return Whether the specified component is enabled.
     */
    public boolean isVisible(@NonNull String name) {
        return Optional.ofNullable(this.windows.get(name)).map(Component::isVisible).orElse(false);
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
        Optional.ofNullable(this.windows.get(name))
                .ifPresent(component -> component.setVisible(visible));
    }
}
