package com.ikalagaming.factory.world;

import static org.junit.jupiter.api.Assertions.*;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.factory.FactoryPlugin;
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

    @Test
    void testRegistryCreation() {
        var world = new World();

        assertNotNull(world.getTagRegistry());
        assertNotNull(world.getMaterialRegistry());
        assertNotNull(world.getItemRegistry());
        assertNotNull(world.getBlockRegistry());
    }

    /** Test the creation of the world object. */
    @Test
    void testWorldCreation() {
        assertDoesNotThrow(World::new);
    }
}
