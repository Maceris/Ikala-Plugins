package com.ikalagaming.factory.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.gen.WorldgenFixtures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for loading definitions from a data folder.
 *
 * @author Ches Burks
 */
class TestDefinitionLoader {

    /**
     * Blocks, their items and their tags load in order from a folder.
     *
     * @param folder A temporary data folder.
     * @throws IOException If the files can't be written.
     */
    @Test
    void testLoadAll(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve(DefinitionLoader.TAGS_FILE), "solid:\n  rock:\nliquid:\n");
        Files.writeString(folder.resolve(DefinitionLoader.MATERIALS_FILE), "{}\n");
        Files.writeString(
                folder.resolve(DefinitionLoader.BLOCKS_FILE),
                "mod_name,identifier,material,tags,register_item\n"
                        + "test,stone,,\"solid, rock\",TRUE\n"
                        + "test,water,,liquid,FALSE\n");
        Files.writeString(
                folder.resolve(DefinitionLoader.ITEMS_FILE),
                "mod_name,identifier,material,tags\ntest,gear,,\n");
        Registries registries = new Registries();
        DefinitionLoader.loadAll(folder, registries);

        assertTrue(registries.getTagRegistry().findTag("rock").isPresent());
        assertEquals(
                List.of("solid", "rock"),
                registries.getBlockRegistry().find("test:stone").orElseThrow().tags());
        assertEquals(
                List.of("liquid"),
                registries.getBlockRegistry().find("test:water").orElseThrow().tags());
        // Stone registers its item, water doesn't, and gear is an item of its own
        assertTrue(registries.getItemRegistry().containsKey("test:stone"));
        assertTrue(!registries.getItemRegistry().containsKey("test:water"));
        assertTrue(registries.getItemRegistry().containsKey("test:gear"));
    }

    /** The world generation fixture's blocks come from its own definitions. */
    @Test
    void testBlockIds() {
        assertEquals(WorldgenFixtures.BLOCKS, DefinitionLoader.blockIds(WorldgenFixtures.folder()));
    }

    /**
     * A folder with no blocks defines none, without errors.
     *
     * @param folder A temporary, empty data folder.
     */
    @Test
    void testNoDefinitions(@TempDir Path folder) {
        assertEquals(java.util.Set.of(), DefinitionLoader.blockIds(folder));
    }
}
