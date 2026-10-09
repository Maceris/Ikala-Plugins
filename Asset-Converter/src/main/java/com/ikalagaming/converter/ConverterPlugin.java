package com.ikalagaming.converter;

import static com.ikalagaming.converter.gui.DefaultWindows.*;

import com.ikalagaming.converter.gui.DebugToolbar;
import com.ikalagaming.converter.gui.window.Debug;
import com.ikalagaming.converter.gui.window.MainMenu;
import com.ikalagaming.converter.inspector.AssetInspectorWindow;
import com.ikalagaming.event.Listener;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.gui.WindowManager;
import com.ikalagaming.graphics.gui.windows.GraphicsDebug;
import com.ikalagaming.graphics.gui.windows.IkGuiDemo;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/** Used for asset conversion for the graphics plugin. */
@Slf4j
public class ConverterPlugin extends Plugin {
    /** The name of this plugin. */
    public static final String PLUGIN_NAME = "Asset-Converter";

    /**
     * The resource bundle for the Graphics plugin.
     *
     * @return The bundle.
     * @param resourceBundle The new bundle to use.
     */
    @Getter @Setter private static ResourceBundle resourceBundle;

    @Getter private WindowManager guiManager;

    private Set<Listener> listeners;

    @Override
    public Set<Listener> getListeners() {
        if (null == this.listeners) {
            this.listeners = Collections.synchronizedSet(new HashSet<>());
        }
        return this.listeners;
    }

    @Override
    public String getName() {
        return ConverterPlugin.PLUGIN_NAME;
    }

    @Override
    public boolean onLoad() {
        try {
            setResourceBundle(
                    ResourceBundle.getBundle(
                            "com.ikalagaming.converter.strings", Localization.getLocale()));
        } catch (MissingResourceException missingResource) {
            // don't localize this since it would fail anyway
            log.warn("Locale not found for Asset-Converter in onLoad()");
        }
        return true;
    }

    @Override
    public boolean onUnload() {
        setResourceBundle(null);
        return true;
    }

    /**
     * Show or hide the asset inspector.
     *
     * @param visible Whether it should be shown.
     */
    public void setInspectorVisible(boolean visible) {
        GraphicsManager.forPlugin(getName())
                .ui()
                .setVisible(AssetInspectorWindow.SURFACE_ID, visible);
    }

    @Override
    public boolean onEnable() {
        guiManager = GraphicsManager.getWindowManager();
        var graphics = GraphicsManager.forPlugin(getName());
        guiManager.addWindow(graphics, DEBUG.getName(), new Debug());
        guiManager.addWindow(graphics, IkGuiDemo.WINDOW_NAME, new IkGuiDemo());
        guiManager.addWindow(graphics, GraphicsDebug.WINDOW_NAME, new GraphicsDebug());
        UI ui = graphics.ui();
        ui.show(new MainMenu(ui, () -> setInspectorVisible(true)).build());
        // Shown once so later toggles only change its visibility, and keep its state
        ui.addStyles(AssetInspectorWindow.styles());
        ui.show(new AssetInspectorWindow(ui).build());
        ui.setVisible(AssetInspectorWindow.SURFACE_ID, false);

        guiManager.setToolbar(graphics, new DebugToolbar(guiManager, this::setInspectorVisible));
        return true;
    }
}
