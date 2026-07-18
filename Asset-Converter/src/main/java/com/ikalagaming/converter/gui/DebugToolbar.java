package com.ikalagaming.converter.gui;

import static com.ikalagaming.converter.gui.DefaultWindows.*;

import com.ikalagaming.converter.ConverterPlugin;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.gui.WindowManager;
import com.ikalagaming.graphics.frontend.gui.component.Checkbox;
import com.ikalagaming.graphics.frontend.gui.component.MainToolbar;
import com.ikalagaming.graphics.frontend.gui.windows.GraphicsDebug;
import com.ikalagaming.graphics.frontend.gui.windows.IkGuiDemo;
import com.ikalagaming.graphics.frontend.gui.windows.ImGuiDemo;
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
    private final Checkbox debug;
    private final Checkbox ikDemoWindow;
    private final Checkbox imDemoWindow;
    private final Checkbox graphicsWindow;

    public DebugToolbar(@NonNull WindowManager windowManager) {
        this.windowManager = windowManager;

        var textDebug =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_DEBUG", ConverterPlugin.getResourceBundle());
        debug = new Checkbox(textDebug, windowManager.isVisible(DEBUG.getName()));

        var textIkDemo =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_DEMO", ConverterPlugin.getResourceBundle());
        ikDemoWindow = new Checkbox(textIkDemo, windowManager.isVisible(IkGuiDemo.WINDOW_NAME));

        var textImDemo =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_DEMO", ConverterPlugin.getResourceBundle());
        imDemoWindow = new Checkbox(textImDemo, windowManager.isVisible(ImGuiDemo.WINDOW_NAME));

        var textGraphics =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_GRAPHICS_DEBUG", ConverterPlugin.getResourceBundle());
        graphicsWindow =
                new Checkbox(textGraphics, windowManager.isVisible(GraphicsDebug.WINDOW_NAME));
    }

    @Override
    public void draw(final int width, final int height) {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("Windows")) {
                debug.draw(width, height);
                ikDemoWindow.draw(width, height);
                imDemoWindow.draw(width, height);
                graphicsWindow.draw(width, height);
                ImGui.endMenu();
            }
            ImGui.pushStyleColor(ImGuiCol.Text, ImColor.rgba(1f, 0.1f, 0.1f, 1.0f));
            if (ImGui.menuItem("Quit Editor")) {
                new Shutdown().fire();
            }
            ImGui.popStyleColor();

            ImGui.endMainMenuBar();
        }
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        if (debug.checkResult()) {
            windowManager.setVisible(DEBUG.getName(), debug.getState());
            return true;
        }
        if (ikDemoWindow.checkResult()) {
            windowManager.setVisible(IkGuiDemo.WINDOW_NAME, ikDemoWindow.getState());
            return true;
        }
        if (imDemoWindow.checkResult()) {
            windowManager.setVisible(ImGuiDemo.WINDOW_NAME, imDemoWindow.getState());
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
