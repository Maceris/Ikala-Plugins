package com.ikalagaming.factory.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.Collision;
import com.ikalagaming.factory.world.gen.WorldgenFixtures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Tests for loading definitions from content folders.
 *
 * @author Ches Burks
 */
class TestDefinitionLoader {

    /**
     * Write a definition file.
     *
     * @param folder The content folder.
     * @param kind The kind's folder.
     * @param name The file's name, without its extension.
     * @param text Its contents.
     * @throws IOException If it can't be written.
     */
    private static void write(Path folder, String kind, String name, String text)
            throws IOException {
        final Path file = folder.resolve(kind).resolve(name + DefinitionLoader.EXTENSION);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    /**
     * Blocks, their items and their tags load in order from a folder, one file each.
     *
     * @param base A temporary folder, holding the {@code Test} plugin's folder.
     * @throws IOException If the files can't be written.
     */
    @Test
    void testLoadAll(@TempDir Path base) throws IOException {
        final Path folder = base.resolve("Test/data");
        // A child written before its parent's name sorts, so parents must still come first
        write(folder, DefinitionLoader.TAGS_FOLDER, "aaa_rock", "{parent:\"solid\"}");
        write(folder, DefinitionLoader.TAGS_FOLDER, "solid", "/* Solid things */\n{}");
        write(folder, DefinitionLoader.TAGS_FOLDER, "liquid", "{}");
        write(folder, DefinitionLoader.MATERIALS_FOLDER, "metal", "{tags:[T;\"solid\"]}");
        write(folder, DefinitionLoader.MATERIALS_FOLDER, "bronze", "{parent:\"metal\"}");
        write(
                folder,
                DefinitionLoader.BLOCKS_FOLDER,
                "stone",
                "{tags:[T;\"solid\", \"aaa_rock\"]}");
        write(
                folder,
                DefinitionLoader.BLOCKS_FOLDER,
                "water",
                "{tags:[T;\"liquid\"], item:false, collision:\"none\"}");
        write(folder, DefinitionLoader.BLOCKS_FOLDER, "odd", "{item:false, collision:\"wobbly\"}");
        write(folder, DefinitionLoader.ITEMS_FOLDER, "gear", "{material:\"bronze\"}");
        Registries registries = new Registries();
        DefinitionLoader.loadAll(List.of(folder), registries);

        assertTrue(registries.getTagRegistry().findTag("aaa_rock").isPresent());
        assertTrue(registries.getMaterialRegistry().findMaterial("bronze").isPresent());
        assertEquals(
                List.of("solid", "aaa_rock"),
                registries.getBlockRegistry().find("test:stone").orElseThrow().tags());
        assertEquals(
                Collision.FULL,
                registries.getBlockRegistry().find("test:stone").orElseThrow().collision());
        assertEquals(
                Collision.NONE,
                registries.getBlockRegistry().find("test:water").orElseThrow().collision());
        // A collision this doesn't know is full, so nothing unknown can be walked through
        assertEquals(
                Collision.FULL,
                registries.getBlockRegistry().find("test:odd").orElseThrow().collision());
        // Stone registers its item by default, water doesn't, and gear is an item of its own
        assertTrue(registries.getItemRegistry().containsKey("test:stone"));
        assertFalse(registries.getItemRegistry().containsKey("test:water"));
        assertEquals(
                "bronze", registries.getItemRegistry().find("test:gear").orElseThrow().material());
    }

    /**
     * A later plugin replaces an earlier one's block through its overrides, and its own blocks are
     * in its own namespace.
     *
     * @param base A temporary folder, holding the plugins' folders.
     * @throws IOException If the files can't be written.
     */
    @Test
    void testOverrides(@TempDir Path base) throws IOException {
        final Path game = base.resolve("Game/data");
        final Path mod = base.resolve("Mod2/data");
        write(game, DefinitionLoader.BLOCKS_FOLDER, "stone", "{}");
        write(mod, DefinitionLoader.BLOCKS_FOLDER, "ore", "{}");
        write(
                mod,
                ContentFolders.OVERRIDES_FOLDER + "/game/" + DefinitionLoader.BLOCKS_FOLDER,
                "stone",
                "{item:false}");
        Registries registries = new Registries();
        DefinitionLoader.loadAll(List.of(game, mod), registries);

        assertEquals(
                Set.of("game:stone", "mod2:ore"),
                Set.copyOf(registries.getBlockRegistry().getNames()));
        assertFalse(registries.getItemRegistry().containsKey("game:stone"));
    }

    /** The world generation fixture's blocks come from its own definitions. */
    @Test
    void testBlockIds() {
        assertEquals(
                WorldgenFixtures.BLOCKS,
                DefinitionLoader.blockIds(List.of(WorldgenFixtures.folder())));
    }

    /**
     * A folder with no blocks defines none, without errors.
     *
     * @param base A temporary folder.
     */
    @Test
    void testNoDefinitions(@TempDir Path base) {
        assertEquals(Set.of(), DefinitionLoader.blockIds(List.of(base.resolve("Empty/data"))));
    }
}
