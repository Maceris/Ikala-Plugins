package com.ikalagaming.converter;

import static com.ikalagaming.converter.gui.DefaultWindows.*;

import com.ikalagaming.converter.gui.DebugToolbar;
import com.ikalagaming.converter.gui.DefaultWindows;
import com.ikalagaming.converter.gui.window.Debug;
import com.ikalagaming.converter.gui.window.GraphicsDebug;
import com.ikalagaming.converter.gui.window.ImGuiDemo;
import com.ikalagaming.converter.gui.window.MainMenu;
import com.ikalagaming.event.Listener;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.frontend.gui.WindowManager;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Stream;

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

    @Override
    public boolean onEnable() {
        guiManager = GraphicsManager.getWindowManager();
        guiManager.addWindow(MAIN_MENU.getName(), new MainMenu(guiManager));
        guiManager.addWindow(DEBUG.getName(), new Debug());
        guiManager.addWindow(IMGUI_DEMO.getName(), new ImGuiDemo());
        guiManager.addWindow(GRAPHICS_DEBUG.getName(), new GraphicsDebug());
        Stream.of(MAIN_MENU).map(DefaultWindows::getName).forEach(guiManager::show);

        guiManager.setToolbar(new DebugToolbar(guiManager));
        return true;
    }
}
