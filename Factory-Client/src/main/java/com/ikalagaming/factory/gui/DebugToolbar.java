package com.ikalagaming.factory.gui;

import static com.ikalagaming.factory.gui.DefaultWindows.*;

import com.ikalagaming.factory.FactoryClientPlugin;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.WindowManager;
import com.ikalagaming.graphics.frontend.gui.component.Checkbox;
import com.ikalagaming.graphics.frontend.gui.component.MainToolbar;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.util.Color;
import com.ikalagaming.graphics.frontend.gui.windows.GraphicsDebug;
import com.ikalagaming.graphics.frontend.gui.windows.IkGuiDemo;
import com.ikalagaming.graphics.frontend.gui.windows.IkScriptDebugger;
import com.ikalagaming.graphics.frontend.gui.windows.ScriptMonitor;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.events.Shutdown;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;

/** A menu bar at the top of the screen for debugging. */
public class DebugToolbar extends MainToolbar {
    private final WindowManager windowManager;
    private final Checkbox biomeDebug;
    private final Checkbox debug;
    private final Checkbox demoIkGuiWindow;
    private final Checkbox graphicsWindow;
    private final Checkbox scriptDebugger;
    private final Checkbox scriptMonitor;

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

        var textIkGuiDemo =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_IKGUI_DEMO", FactoryClientPlugin.getResourceBundle());
        demoIkGuiWindow =
                new Checkbox(textIkGuiDemo, windowManager.isVisible(IkGuiDemo.WINDOW_NAME));

        var textGraphics =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_GRAPHICS_DEBUG", FactoryClientPlugin.getResourceBundle());
        graphicsWindow =
                new Checkbox(textGraphics, windowManager.isVisible(GraphicsDebug.WINDOW_NAME));

        var textScriptDebugger =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_IKSCRIPT_DEBUGGER", FactoryClientPlugin.getResourceBundle());
        scriptDebugger =
                new Checkbox(
                        textScriptDebugger, windowManager.isVisible(IkScriptDebugger.WINDOW_NAME));

        var textScriptMonitor =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_SCRIPT_MONITOR", FactoryClientPlugin.getResourceBundle());
        scriptMonitor =
                new Checkbox(textScriptMonitor, windowManager.isVisible(ScriptMonitor.WINDOW_NAME));
    }

    @Override
    public void draw(final int width, final int height) {
        if (IkGui.beginMainMenuBar()) {
            if (IkGui.beginMenu("Windows")) {
                biomeDebug.draw(width, height);
                debug.draw(width, height);
                demoIkGuiWindow.draw(width, height);
                graphicsWindow.draw(width, height);
                scriptDebugger.draw(width, height);
                scriptMonitor.draw(width, height);
                IkGui.endMenu();
            }
            IkGui.pushStyleColor(ColorType.TEXT, Color.rgba(1f, 0.1f, 0.1f, 1.0f));
            if (IkGui.menuItem("Quit Game")) {
                new Shutdown().fire();
            }
            IkGui.popStyleColor();

            IkGui.endMainMenuBar();
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
        if (demoIkGuiWindow.checkResult()) {
            windowManager.setVisible(IkGuiDemo.WINDOW_NAME, demoIkGuiWindow.getState());
            return true;
        }
        if (graphicsWindow.checkResult()) {
            windowManager.setVisible(GraphicsDebug.WINDOW_NAME, graphicsWindow.getState());
            return true;
        }
        if (scriptDebugger.checkResult()) {
            windowManager.setVisible(IkScriptDebugger.WINDOW_NAME, scriptDebugger.getState());
            return true;
        }
        if (scriptMonitor.checkResult()) {
            windowManager.setVisible(ScriptMonitor.WINDOW_NAME, scriptMonitor.getState());
            return true;
        }
        return false;
    }

    @Override
    public void updateValues(@NonNull Scene scene, @NonNull Window window) {
        // Not required
    }
}
