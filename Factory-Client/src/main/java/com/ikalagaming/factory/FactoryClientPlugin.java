package com.ikalagaming.factory;

import static com.ikalagaming.factory.gui.DefaultComponents.*;

import com.ikalagaming.event.Listener;
import com.ikalagaming.factory.gui.*;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.util.SafeResourceLoader;
import com.ikalagaming.util.SystemProperties;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.*;
import java.util.stream.Stream;

/**
 * Handles the client-side parts of Lotomation.
 *
 * @author Ches Burks
 */
@Slf4j
public class FactoryClientPlugin extends Plugin {
    /** The name of the plugin in Java for convenience, should match the name in plugin.yml. */
    public static final String PLUGIN_NAME = "Factory-Client";

    /** The path to the folder where all the game save data will be stored. */
    public static final String SAVE_FOLDER =
            SystemProperties.getHomeDir() + File.separator + ".lotomation";

    /**
     * The resource bundle for the Graphics plugin.
     *
     * @return The bundle.
     * @param resourceBundle The new bundle to use.
     */
    @Getter @Setter private static ResourceBundle resourceBundle;

    private Set<Listener> listeners;

    @Getter private GuiManager guiManager;

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
        createSaveFolder();
        guiManager = new GuiManager();
        GraphicsManager.setGUI(guiManager);
        guiManager.addComponent(DEBUG_TOOLBAR.getName(), new DebugToolbar());
        guiManager.addComponent(BIOME_DEBUG.getName(), new BiomeDebug());
        guiManager.addComponent(MAIN_MENU.getName(), new MainMenu(guiManager));
        Stream.of(DEBUG_TOOLBAR, BIOME_DEBUG, MAIN_MENU)
                .map(DefaultComponents::getName)
                .forEach(guiManager::enable);
        return true;
    }

    private void createSaveFolder() {
        File folder = new File(SAVE_FOLDER);
        if (!folder.exists() && (!folder.mkdir())) {
            log.error(
                    SafeResourceLoader.getStringFormatted(
                            "SAVE_FOLDER_CREATION_FAILED", resourceBundle, SAVE_FOLDER));
        }
    }

    @Override
    public boolean onLoad() {
        try {
            setResourceBundle(
                    ResourceBundle.getBundle(
                            "com.ikalagaming.factory.strings", Localization.getLocale()));
        } catch (MissingResourceException missingResource) {
            // don't localize this since it would fail anyway
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
