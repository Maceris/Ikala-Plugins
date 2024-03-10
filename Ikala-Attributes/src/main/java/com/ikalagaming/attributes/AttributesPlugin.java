package com.ikalagaming.attributes;

import com.ikalagaming.plugins.Plugin;

/**
 * An example plugin to demonstrate how plugins are set up.
 *
 * @author Ches Burks
 */
public class AttributesPlugin extends Plugin {
    /** The name of the plugin in Java for convenience, should match the name in plugin.yml. */
    public static final String PLUGIN_NAME = "Ikala-Attributes";

    @Override
    public String getName() {
        return AttributesPlugin.PLUGIN_NAME;
    }
}
