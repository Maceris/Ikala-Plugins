package com.ikalagaming.characters;

import com.ikalagaming.characters.registry.ContentSource;
import com.ikalagaming.event.EventManager;
import com.ikalagaming.plugins.PluginInfo;
import com.ikalagaming.plugins.PluginManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Path;

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

    /** Content folder entries split into a namespace and folder, and bad ones are rejected. */
    @Test
    void testParseSource() {
        ContentSource source = CharacterPlugin.parseSource("goblinmod=D:/mods/goblin/characters");
        Assertions.assertEquals("goblinmod", source.namespace());
        Assertions.assertEquals(Path.of("D:/mods/goblin/characters"), source.root());
        Assertions.assertNull(CharacterPlugin.parseSource("no-folder"));
        Assertions.assertNull(CharacterPlugin.parseSource("=folder"));
        Assertions.assertNull(CharacterPlugin.parseSource("namespace="));
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
