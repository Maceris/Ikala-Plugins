package com.ikalagaming.converter.gui;

import static com.ikalagaming.converter.gui.DefaultWindows.*;

import com.ikalagaming.converter.ConverterPlugin;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.WindowManager;
import com.ikalagaming.graphics.gui.component.Checkbox;
import com.ikalagaming.graphics.gui.component.MainToolbar;
import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.windows.GraphicsDebug;
import com.ikalagaming.graphics.gui.windows.IkGuiDemo;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.events.Shutdown;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;

import java.util.function.Consumer;

/** A menu bar at the top of the screen for debugging. */
public class DebugToolbar extends MainToolbar {
    private final WindowManager windowManager;
    private final Checkbox debug;
    private final Checkbox ikDemoWindow;
    private final Checkbox graphicsWindow;
    private final Checkbox assetInspector;

    /** Shows or hides the asset inspector. */
    private final Consumer<Boolean> inspectorVisible;

    public DebugToolbar(
            @NonNull WindowManager windowManager, @NonNull Consumer<Boolean> inspectorVisible) {
        this.windowManager = windowManager;
        this.inspectorVisible = inspectorVisible;

        var textDebug =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_DEBUG", ConverterPlugin.getResourceBundle());
        debug = new Checkbox(textDebug, windowManager.isVisible(DEBUG.getName()));

        var textIkDemo =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_DEMO", ConverterPlugin.getResourceBundle());
        ikDemoWindow = new Checkbox(textIkDemo, windowManager.isVisible(IkGuiDemo.WINDOW_NAME));

        var textGraphics =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_GRAPHICS_DEBUG", ConverterPlugin.getResourceBundle());
        graphicsWindow =
                new Checkbox(textGraphics, windowManager.isVisible(GraphicsDebug.WINDOW_NAME));

        var textInspector =
                SafeResourceLoader.getString(
                        "TOOLBAR_DEBUG_ASSET_INSPECTOR", ConverterPlugin.getResourceBundle());
        assetInspector = new Checkbox(textInspector, false);
    }

    @Override
    public void draw(final int width, final int height) {
        if (IkGui.beginMainMenuBar()) {
            if (IkGui.beginMenu("Windows")) {
                debug.draw(width, height);
                ikDemoWindow.draw(width, height);
                graphicsWindow.draw(width, height);
                assetInspector.draw(width, height);
                IkGui.endMenu();
            }
            IkGui.pushStyleColor(ColorType.TEXT, Color.rgba(1f, 0.1f, 0.1f, 1.0f));
            if (IkGui.menuItem("Quit Editor")) {
                new Shutdown().fire();
            }
            IkGui.popStyleColor();

            IkGui.endMainMenuBar();
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
        if (graphicsWindow.checkResult()) {
            windowManager.setVisible(GraphicsDebug.WINDOW_NAME, graphicsWindow.getState());
            return true;
        }
        if (assetInspector.checkResult()) {
            inspectorVisible.accept(assetInspector.getState());
            return true;
        }
        return false;
    }

    @Override
    public void updateValues(@NonNull Scene scene, @NonNull Window window) {
        // Not required
    }
}
