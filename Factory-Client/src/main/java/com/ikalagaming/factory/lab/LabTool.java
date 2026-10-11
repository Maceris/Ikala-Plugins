package com.ikalagaming.factory.lab;

import com.ikalagaming.factory.FactoryClientPlugin;
import com.ikalagaming.factory.registry.DefinitionLoader;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.plugins.config.ConfigManager;
import com.ikalagaming.plugins.config.PluginConfig;

import lombok.NonNull;

import java.nio.file.Path;
import java.util.List;

/**
 * Opens the Worldgen Lab on demand and closes it with the plugin. The Lab is only built the first
 * time it is opened, so its threads don't run until someone uses it. Render thread only.
 */
public final class LabTool implements AutoCloseable {

    /** The Lab's label, in tool lists and menus. */
    public static final String LABEL = "Worldgen Lab";

    /** The config key for the data folders. */
    private static final String DATA_FOLDERS_KEY = "lab.data-folders";

    /** The config key for the world type. */
    private static final String WORLD_TYPE_KEY = "lab.world-type";

    /** The config key for the seed. */
    private static final String SEED_KEY = "lab.seed";

    /** The world type used when the config doesn't name one. */
    private static final String DEFAULT_WORLD_TYPE = "lotomation:overworld";

    /** The plugin's graphics context. */
    private final GraphicsContext graphics;

    /** The Lab, once opened. */
    private WorldgenLab lab;

    /**
     * Set up the tool; nothing starts until it is opened.
     *
     * @param graphics The plugin's graphics context.
     */
    public LabTool(@NonNull GraphicsContext graphics) {
        this.graphics = graphics;
    }

    /**
     * What the Lab loads first, from Factory-Client's config.
     *
     * @return The settings.
     */
    static LabSession.Settings configuredSettings() {
        final PluginConfig config = ConfigManager.loadConfig(FactoryClientPlugin.PLUGIN_NAME);
        List<Path> folders = config.getStringList(DATA_FOLDERS_KEY).stream().map(Path::of).toList();
        if (folders.isEmpty()) {
            folders = List.of(DefinitionLoader.dataFolder());
        }
        final String worldType = config.getOrDefault(WORLD_TYPE_KEY, DEFAULT_WORLD_TYPE);
        final Number seed = config.getOrDefault(SEED_KEY, 0L);
        return new LabSession.Settings(folders, worldType, seed.longValue());
    }

    /** Open the Lab, building it the first time. */
    public void open() {
        if (lab == null) {
            lab =
                    new WorldgenLab(
                            graphics,
                            configuredSettings(),
                            PluginFolder.getResource(
                                            FactoryClientPlugin.PLUGIN_NAME,
                                            PluginFolder.ResourceType.DATA,
                                            "")
                                    .toPath());
            lab.open();
            return;
        }
        graphics.ui().setVisible(WorldgenLab.SURFACE_ID, true);
    }

    /**
     * Whether the Lab is showing.
     *
     * @return True if it was opened and is visible.
     */
    public boolean isOpen() {
        return lab != null && lab.isVisible();
    }

    /** Hide the Lab, keeping its state. */
    public void hide() {
        if (lab != null) {
            graphics.ui().setVisible(WorldgenLab.SURFACE_ID, false);
        }
    }

    /** Stop the Lab's threads and release what it holds. */
    @Override
    public void close() {
        if (lab != null) {
            lab.close();
            lab = null;
        }
    }
}
