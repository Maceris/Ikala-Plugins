package com.ikalagaming.converter.content;

import com.ikalagaming.plugins.PluginManager;

/**
 * Whether the game's content can be read: Factory-Core, which knows its formats and folders, is a
 * soft dependency, so nothing that names its classes is touched unless it is loaded. Ask here
 * before using {@link BlockContent}.
 */
public final class GameContent {
    /** The plugin that reads game content. */
    public static final String CORE_PLUGIN = "Factory-Core";

    /** Not made. */
    private GameContent() {}

    /**
     * Whether Factory-Core is loaded and running, so block looks can be read.
     *
     * @return True if it is.
     */
    public static boolean available() {
        return PluginManager.getExistingInstance()
                .map(manager -> manager.isEnabled(CORE_PLUGIN))
                .orElse(false);
    }
}
