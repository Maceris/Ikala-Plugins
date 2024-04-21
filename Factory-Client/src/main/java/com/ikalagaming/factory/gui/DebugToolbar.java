package com.ikalagaming.factory.gui;

import com.ikalagaming.factory.FactoryClientPlugin;
import com.ikalagaming.factory.gui.component.Checkbox;
import com.ikalagaming.factory.gui.component.MainToolbar;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.events.Shutdown;
import com.ikalagaming.util.SafeResourceLoader;

import imgui.ImColor;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import lombok.NonNull;

/** A menu bar at the top of the screen for debugging. */
public class DebugToolbar extends MainToolbar {
    private final GuiManager guiManager;
    private final Checkbox biomeDebug;

    public DebugToolbar(@NonNull GuiManager guiManager) {
        this.guiManager = guiManager;
        var textBiomeDebug =
                SafeResourceLoader.getString(
                        "TEXT_BIOME_DEBUG", FactoryClientPlugin.getResourceBundle());
        biomeDebug = new Checkbox(textBiomeDebug, false);
    }

    @Override
    public void draw(final int width, final int height) {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("Windows")) {
                biomeDebug.draw(width, height);
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
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        if (biomeDebug.checkResult()) {
            guiManager.setVisible(DefaultComponents.BIOME_DEBUG.getName(), biomeDebug.getState());
            return true;
        }
        return false;
    }
}
