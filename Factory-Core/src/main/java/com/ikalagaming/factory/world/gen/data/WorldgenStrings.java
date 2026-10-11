package com.ikalagaming.factory.world.gen.data;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;

import java.util.ResourceBundle;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Localized messages for world generation. The headless tools and tests run without the plugin
 * loaded, so when the plugin's bundle isn't set this loads the same bundle itself.
 */
public final class WorldgenStrings {

    /** The bundle the plugin loads its strings from. */
    private static final String BUNDLE_NAME = "com.ikalagaming.factory.strings";

    /** Guards {@link #fallback}. */
    private static final ReentrantLock LOCK = new ReentrantLock();

    /** The bundle used while the plugin isn't loaded. */
    private static ResourceBundle fallback;

    /**
     * A message, with its {} placeholders filled in.
     *
     * @param key The message key.
     * @param args The values for the placeholders.
     * @return The message.
     */
    public static String format(@NonNull String key, Object... args) {
        return SafeResourceLoader.getStringFormatted(key, bundle(), args);
    }

    /**
     * The bundle to read messages from.
     *
     * @return The plugin's bundle if it is loaded, otherwise the same bundle loaded directly.
     */
    private static ResourceBundle bundle() {
        final ResourceBundle plugin = FactoryPlugin.getResourceBundle();
        if (plugin != null) {
            return plugin;
        }
        LOCK.lock();
        try {
            if (fallback == null) {
                fallback = ResourceBundle.getBundle(BUNDLE_NAME, Localization.getLocale());
            }
            return fallback;
        } finally {
            LOCK.unlock();
        }
    }

    private WorldgenStrings() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
