package com.ikalagaming.graphics;

import com.ikalagaming.event.Listener;
import com.ikalagaming.launcher.Launcher;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.plugins.config.ConfigManager;
import com.ikalagaming.plugins.config.PluginConfig;
import com.ikalagaming.scripting.ScriptManager;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashSet;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * The plugin for handling graphics.
 *
 * @author Ches Burks
 */
@Slf4j
public class GraphicsPlugin extends Plugin {

    /** The name of the plugin. */
    public static final String PLUGIN_NAME = "Ikala-Graphics";

    /** The global scripts use the retained UI through. */
    public static final String SCRIPT_UI_GLOBAL = "ui";

    /** The config value naming the level of detail cache's folder. */
    private static final String LOD_CACHE_FOLDER = "lod-cache-folder";

    /** The config value of the level of detail cache's size cap, in mebibytes. */
    private static final String LOD_CACHE_MAX_MB = "lod-cache-max-mb";

    /** Bytes in a mebibyte. */
    private static final long BYTES_PER_MIB = 1024L * 1024L;

    /**
     * The resource bundle for the Graphics plugin.
     *
     * @return The bundle.
     * @param resourceBundle The new bundle to use.
     */
    @Getter @Setter private static ResourceBundle resourceBundle;

    private Set<Listener> listeners;

    @Override
    public Set<Listener> getListeners() {
        if (listeners == null) {
            listeners = Collections.synchronizedSet(new HashSet<>());
            listeners.add(new GraphicsPluginListener());
        }

        return listeners;
    }

    @Override
    public String getName() {
        return GraphicsPlugin.PLUGIN_NAME;
    }

    @Override
    public boolean onDisable() {
        /*
         * The tick stage removes itself once graphics have shut down. Removing it here could stop the main thread
         * from ever ticking again to do the cleanup.
         */
        ScriptManager.unregisterGlobal(GraphicsPlugin.SCRIPT_UI_GLOBAL);
        GraphicsManager.requestShutdown();
        return true;
    }

    @Override
    public boolean onEnable() {
        GraphicsSettings settings = GraphicsManager.getSettings();
        settings.antiAliasing = true;
        settings.targetFPS = GraphicsManager.TARGET_FPS;
        readLodCache(settings);

        if (!GraphicsManager.createWindow()) {
            return false;
        }
        Launcher.addMainThreadStage(GraphicsManager::tick);
        // Each plugin's scripts get that plugin's UI, so what they open is owned by the plugin
        ScriptManager.unregisterGlobal(GraphicsPlugin.SCRIPT_UI_GLOBAL);
        ScriptManager.registerGlobal(
                GraphicsPlugin.SCRIPT_UI_GLOBAL,
                GraphicsPlugin.PLUGIN_NAME,
                owner -> owner == null ? null : GraphicsManager.forPlugin(owner).ui().scripts());
        return true;
    }

    /**
     * Read where and how big the level of detail cache is from the config, keeping the defaults for
     * values it doesn't have.
     *
     * @param settings The settings to fill in.
     */
    private static void readLodCache(@NonNull GraphicsSettings settings) {
        final PluginConfig config = ConfigManager.loadConfig(GraphicsPlugin.PLUGIN_NAME);
        if (config.isPresent(LOD_CACHE_FOLDER)) {
            settings.lodCacheDirectory = config.getString(LOD_CACHE_FOLDER);
        }
        if (config.isPresent(LOD_CACHE_MAX_MB)) {
            settings.lodCacheMaxBytes = config.getLong(LOD_CACHE_MAX_MB) * BYTES_PER_MIB;
        }
    }

    @Override
    public boolean onLoad() {
        try {
            GraphicsPlugin.setResourceBundle(
                    ResourceBundle.getBundle(
                            "com.ikalagaming.graphics.strings", Localization.getLocale()));
        } catch (MissingResourceException missingResource) {
            // don't localize this since it would fail anyway
            log.warn("Locale not found for Ikala-Graphics in onLoad()");
        }
        return true;
    }

    @Override
    public boolean onUnload() {
        GraphicsPlugin.setResourceBundle(null);
        return true;
    }
}
