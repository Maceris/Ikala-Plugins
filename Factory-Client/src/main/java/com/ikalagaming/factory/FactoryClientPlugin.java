package com.ikalagaming.factory;

import static com.ikalagaming.factory.gui.DefaultWindows.*;

import com.ikalagaming.event.Listener;
import com.ikalagaming.factory.gui.*;
import com.ikalagaming.factory.gui.window.*;
import com.ikalagaming.factory.saves.UserDataUtil;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.frontend.gui.WindowManager;
import com.ikalagaming.graphics.frontend.gui.windows.GraphicsDebug;
import com.ikalagaming.graphics.frontend.gui.windows.GuiDemo;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

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

    /**
     * The resource bundle for the Graphics plugin.
     *
     * @return The bundle.
     * @param resourceBundle The new bundle to use.
     */
    @Getter @Setter private static ResourceBundle resourceBundle;

    private Set<Listener> listeners;

    @Getter private WindowManager guiManager;

    @Override
    public Set<Listener> getListeners() {
        if (null == listeners) {
            listeners = Collections.synchronizedSet(new HashSet<>());
        }
        return listeners;
    }

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public boolean onEnable() {
        UserDataUtil.createUserDataFolder();
        guiManager = GraphicsManager.getWindowManager();
        guiManager.addWindow(BIOME_DEBUG.getName(), new BiomeDebug());
        guiManager.addWindow(MAIN_MENU.getName(), new MainMenu(guiManager));
        guiManager.addWindow(SINGLE_PLAYER.getName(), new SinglePlayer(guiManager));
        guiManager.addWindow(DEBUG.getName(), new Debug());
        guiManager.addWindow(GuiDemo.WINDOW_NAME, new GuiDemo());
        guiManager.addWindow(GraphicsDebug.WINDOW_NAME, new GraphicsDebug());
        Stream.of(MAIN_MENU).map(DefaultWindows::getName).forEach(guiManager::show);

        guiManager.setToolbar(new DebugToolbar(guiManager));
        return true;
    }

    @Override
    public boolean onLoad() {
        try {
            setResourceBundle(
                    ResourceBundle.getBundle(
                            "com.ikalagaming.factory.strings", Localization.getLocale()));
        } catch (MissingResourceException missingResource) {
            // don't localize this since it would fail anyway
            log.warn("Locale not found for Factory-Client in onLoad()");
        }
        return true;
    }

    @Override
    public boolean onUnload() {
        setResourceBundle(null);
        return true;
    }
}
