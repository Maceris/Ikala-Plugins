package com.ikalagaming.factory.gui;

import com.ikalagaming.factory.gui.component.Checkbox;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.events.Shutdown;

import imgui.ImColor;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import lombok.NonNull;

/** A menu bar at the top of the screen for debugging. */
public class DebugToolbar extends GuiWindow {
    private final GuiManager guiManager;
    private final Checkbox biomeDebug;

    public DebugToolbar(@NonNull GuiManager guiManager) {
        this.guiManager = guiManager;
        biomeDebug = new Checkbox("Biome Debug", false);
    }

    @Override
    public void drawGui() {
        final var sizes = guiManager.getCurrentSizes();
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("Windows")) {
                biomeDebug.draw(sizes);
                ImGui.endMenu();
            }
            ImGui.pushStyleColor(ImGuiCol.Text, ImColor.rgba(1f, 0.1f, 0.1f, 1.0f));
            if (ImGui.menuItem("Quit Game")) {
                new Shutdown().fire();
            }
            ImGui.popStyleColor();

            ImGui.endMainMenuBar();
        }
    }

    @Override
    public void handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        if (biomeDebug.checkResult()) {
            guiManager.setEnabled(DefaultComponents.BIOME_DEBUG.getName(), biomeDebug.getState());
        }
    }
}
