package com.ikalagaming.characters;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.plugins.PluginInfo;
import com.ikalagaming.plugins.PluginManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

/**
 * Tests for the {@link CharacterPlugin characters plugin}.
 *
 * @author Ches Burks
 */
class TestCharacterPlugin {

    /**
     * Set up before all the tests.
     *
     * @throws Exception If something goes wrong.
     */
    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        EventManager.getInstance();
        PluginManager.getInstance();
    }

    /**
     * Tear down after all the tests.
     *
     * @throws Exception If something goes wrong.
     */
    @AfterAll
    static void tearDownAfterClass() throws Exception {
        PluginManager.destroyInstance();
        EventManager.destroyInstance();
    }

    /** Test that the plugin provides its listener. */
    @Test
    void testGetListeners() {
        CharacterPlugin plugin = new CharacterPlugin();
        Assertions.assertFalse(plugin.getListeners().isEmpty());
    }

    /**
     * Test that the plugin.yml is valid and matches the plugin class, since a mismatch only shows
     * up when the plugin fails to load.
     *
     * @throws Exception If the plugin.yml can't be read or is invalid.
     */
    @Test
    void testPluginInfo() throws Exception {
        try (InputStream stream =
                CharacterPlugin.class.getClassLoader().getResourceAsStream("plugin.yml")) {
            Assertions.assertNotNull(stream);
            PluginInfo info = new PluginInfo(stream);
            Assertions.assertEquals(CharacterPlugin.PLUGIN_NAME, info.getName());
            Assertions.assertEquals(CharacterPlugin.class.getName(), info.getMainClass());
        }
    }
}
