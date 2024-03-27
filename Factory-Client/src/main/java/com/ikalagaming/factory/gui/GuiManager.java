package com.ikalagaming.factory.gui;

import com.ikalagaming.graphics.GuiInstance;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;

import imgui.ImGui;
import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class GuiManager implements GuiInstance {
    private final Map<String, GuiComponent> components;

    public GuiManager() {
        components = new HashMap<>();
    }

    /**
     * Register a named gui component. This is expected for larger things like a menu or floating window, rather than
     * a button or list.
     *
     * @param name The unique name of the component.
     * @param component The component.
     */
    public void addComponent(@NonNull String name, @NonNull GuiComponent component) {
        this.components.put(name, component);
    }

    /**
     * Remove a component by name.
     *
     * @param name The name od the component to remove.
     */
    public void removeComponent(@NonNull String name) {
        this.components.remove(name);
    }

    public void enable(@NonNull String name) {
        setEnabled(name, true);
    }

    public void disable(@NonNull String name) {
        setEnabled(name, false);
    }

    public void setEnabled(@NonNull String name, boolean enabled) {
        Optional.ofNullable(this.components.get(name))
                .ifPresent(component -> component.setVisible(enabled));
    }

    @Override
    public void drawGui() {
        ImGui.newFrame();

        components.values().stream().filter(GuiComponent::isVisible).forEach(GuiInstance::drawGui);

        ImGui.endFrame();
        ImGui.render();
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        return components.values().stream()
                .filter(GuiComponent::isVisible)
                .map(component -> component.handleGuiInput(scene, window))
                .reduce(false, (a, b) -> a || b);
    }
}
