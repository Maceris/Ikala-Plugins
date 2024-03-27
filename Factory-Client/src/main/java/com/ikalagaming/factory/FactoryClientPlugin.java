package com.ikalagaming.factory;

import com.ikalagaming.event.Listener;
import com.ikalagaming.factory.gui.BiomeDebug;
import com.ikalagaming.factory.gui.DebugToolbar;
import com.ikalagaming.factory.gui.GuiManager;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Handles the client-side parts of Lotomation.
 *
 * @author Ches Burks
 */
@Slf4j
public class FactoryClientPlugin extends Plugin {
    /** The name of the plugin in Java for convenience, should match the name in plugin.yml. */
    public static final String PLUGIN_NAME = "Factory-Client";

    /**
     * The resource bundle for the Graphics plugin.
     *
     * @return The bundle.
     * @param resourceBundle The new bundle to use.
     */
    @Getter @Setter private static ResourceBundle resourceBundle;

    private Set<Listener> listeners;

    private GuiManager guiManager;

    @Override
    public Set<Listener> getListeners() {
        if (null == listeners) {
            listeners = new HashSet<>();
        }
        return listeners;
    }

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public boolean onDisable() {
        GraphicsManager.setGUI(null);
        return true;
    }

    @Override
    public boolean onEnable() {
        guiManager = new GuiManager();
        GraphicsManager.setGUI(guiManager);
        guiManager.addComponent("Debug Toolbar", new DebugToolbar());
        guiManager.addComponent("Biome Debug", new BiomeDebug());
        guiManager.enable("Debug Toolbar");
        guiManager.enable("Biome Debug");
        return true;
    }

    @Override
    public boolean onLoad() {
        try {
            setResourceBundle(
                    ResourceBundle.getBundle(
                            "com.ikalagaming.factory.strings", Localization.getLocale()));
        } catch (MissingResourceException missingResource) {
            // don't localize this since it would fail anyways
            log.warn("Locale not found for Factory-Core in onLoad()");
        }
        return true;
    }

    @Override
    public boolean onUnload() {
        setResourceBundle(null);
        return true;
    }
}
