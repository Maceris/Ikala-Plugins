package com.ikalagaming.factory.gui;

import static com.ikalagaming.factory.gui.DefaultWindows.*;

import com.ikalagaming.factory.FactoryClientPlugin;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.gui.WindowManager;
import com.ikalagaming.graphics.frontend.gui.component.Checkbox;
import com.ikalagaming.graphics.frontend.gui.component.MainToolbar;
import com.ikalagaming.graphics.frontend.gui.windows.GraphicsDebug;
import com.ikalagaming.graphics.frontend.gui.windows.GuiDemo;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.events.Shutdown;
import com.ikalagaming.util.SafeResourceLoader;

import imgui.ImColor;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import lombok.NonNull;

/** A menu bar at the top of the screen for debugging. */
public class DebugToolbar extends MainToolbar {
    private final WindowManager windowManager;
    private final Checkbox biomeDebug;
    private final Checkbox debug;
    private final Checkbox demoWindow;
    private final Checkbox graphicsWindow;

    public DebugToolbar(@NonNull WindowManager windowManager) {
        this.windowManager = windowManager;
        var textBiomeDebug =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_BIOME_DEBUG", FactoryClientPlugin.getResourceBundle());
        biomeDebug = new Checkbox(textBiomeDebug, windowManager.isVisible(BIOME_DEBUG.getName()));

        var textDebug =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_DEBUG", FactoryClientPlugin.getResourceBundle());
        debug = new Checkbox(textDebug, windowManager.isVisible(DEBUG.getName()));

        var textDemo =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_DEMO", FactoryClientPlugin.getResourceBundle());
        demoWindow = new Checkbox(textDemo, windowManager.isVisible(GuiDemo.WINDOW_NAME));

        var textGraphics =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_GRAPHICS_DEBUG", FactoryClientPlugin.getResourceBundle());
        graphicsWindow =
                new Checkbox(textGraphics, windowManager.isVisible(GraphicsDebug.WINDOW_NAME));
    }

    @Override
    public void draw(final int width, final int height) {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("Windows")) {
                biomeDebug.draw(width, height);
                debug.draw(width, height);
                demoWindow.draw(width, height);
                graphicsWindow.draw(width, height);
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
            windowManager.setVisible(BIOME_DEBUG.getName(), biomeDebug.getState());
            return true;
        }
        if (debug.checkResult()) {
            windowManager.setVisible(DEBUG.getName(), debug.getState());
            return true;
        }
        if (demoWindow.checkResult()) {
            windowManager.setVisible(GuiDemo.WINDOW_NAME, demoWindow.getState());
            return true;
        }
        if (graphicsWindow.checkResult()) {
            windowManager.setVisible(GraphicsDebug.WINDOW_NAME, graphicsWindow.getState());
            return true;
        }
        return false;
    }

    @Override
    public void updateValues(@NonNull Scene scene, @NonNull Window window) {
        // Not required
    }
}
