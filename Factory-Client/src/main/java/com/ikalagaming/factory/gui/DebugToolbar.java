package com.ikalagaming.factory.gui;

import static com.ikalagaming.factory.gui.DefaultWindows.*;

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
    private final Checkbox debug;
    private final Checkbox demoWindow;

    public DebugToolbar(@NonNull GuiManager guiManager) {
        this.guiManager = guiManager;
        var textBiomeDebug =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_BIOME_DEBUG", FactoryClientPlugin.getResourceBundle());
        biomeDebug = new Checkbox(textBiomeDebug, guiManager.isVisible(BIOME_DEBUG.getName()));

        var textDebug =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_DEBUG", FactoryClientPlugin.getResourceBundle());
        debug = new Checkbox(textDebug, guiManager.isVisible(DEBUG.getName()));

        var textDemo =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_DEMO", FactoryClientPlugin.getResourceBundle());
        demoWindow = new Checkbox(textDemo, guiManager.isVisible(IMGUI_DEMO.getName()));
    }

    @Override
    public void draw(final int width, final int height) {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("Windows")) {
                biomeDebug.draw(width, height);
                debug.draw(width, height);
                demoWindow.draw(width, height);
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
            guiManager.setVisible(BIOME_DEBUG.getName(), biomeDebug.getState());
            return true;
        }
        if (debug.checkResult()) {
            guiManager.setVisible(DEBUG.getName(), debug.getState());
            return true;
        }
        if (demoWindow.checkResult()) {
            guiManager.setVisible(IMGUI_DEMO.getName(), demoWindow.getState());
            return true;
        }
        return false;
    }
}
