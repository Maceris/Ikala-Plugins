package com.ikalagaming.factory.world;

import static org.junit.jupiter.api.Assertions.*;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.world.registry.BlockRegistry;
import com.ikalagaming.factory.world.registry.ItemRegistry;
import com.ikalagaming.factory.world.registry.MaterialRegistry;
import com.ikalagaming.factory.world.registry.TagRegistry;
import com.ikalagaming.plugins.PluginManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for the world.
 *
 * @author Ches Burks
 */
class TestWorld {

    private static FactoryPlugin plugin;

    /** Set up before all the tests. */
    @BeforeAll
    static void setUpBeforeClass() {
        EventManager.getInstance();
        PluginManager.getInstance();
        TestWorld.plugin = new FactoryPlugin();
        TestWorld.plugin.onLoad();
        TestWorld.plugin.onEnable();
    }

    /** Tear down after all the tests. */
    @AfterAll
    static void tearDownAfterClass() {
        TestWorld.plugin.onDisable();
        TestWorld.plugin.onUnload();
        TestWorld.plugin = null;
        PluginManager.destroyInstance();
        EventManager.destroyInstance();
    }

    /** Test the default tag and material loading. */
    @Test
    void testDefaultLoading() {
        var world = new World();
        assertTrue(world.loadDefaultConfigurations());

        TagRegistry tagRegistry = world.getTagRegistry();

        assertNotNull(tagRegistry);
        assertNotNull(tagRegistry.getTags());
        assertFalse(tagRegistry.getTags().isEmpty());

        for (Tag tag : tagRegistry.getTags()) {
            assertNotNull(tag);
            assertNotNull(tag.name());
        }

        MaterialRegistry materialRegistry = world.getMaterialRegistry();

        assertNotNull(materialRegistry);
        assertNotNull(materialRegistry.getMaterials());
        assertFalse(materialRegistry.getMaterials().isEmpty());

        for (Material material : materialRegistry.getMaterials()) {
            assertNotNull(material);
            assertNotNull(material.name());
        }

        ItemRegistry itemRegistry = world.getItemRegistry();

        assertNotNull(itemRegistry);
        assertNotNull(itemRegistry.getNames());
        for (String name : itemRegistry.getNames()) {
            var definition = itemRegistry.find(name);
            assertNotNull(definition);
            assertTrue(definition.isPresent());
        }

        BlockRegistry blockRegistry = world.getBlockRegistry();
        assertNotNull(blockRegistry);
        assertNotNull(blockRegistry.getNames());

        for (String name : blockRegistry.getNames()) {
            var definition = blockRegistry.find(name);
            assertNotNull(definition);
            assertTrue(definition.isPresent());
        }
    }

    /** Test the creation of the world object. */
    @Test
    void testWorldCreation() {
        assertDoesNotThrow(World::new);
    }
}
