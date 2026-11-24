package com.ikalagaming.factory.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Tests for finding content in plugins' data folders. */
class TestContentFolders {

    /** A folder's namespace is its plugin folder's name in lower case, if that is valid. */
    @Test
    void testNamespace() {
        assertEquals(
                Optional.of("lotomation"),
                ContentFolders.namespace(Path.of("plugins", "Lotomation", "data")));
        assertEquals(
                Optional.of("ores-2"),
                ContentFolders.namespace(Path.of("plugins", "Ores-2", "data")));
        assertTrue(ContentFolders.namespace(Path.of("plugins", "2Fast", "data")).isEmpty());
        assertTrue(ContentFolders.namespace(Path.of("plugins", "Dotted.Name", "data")).isEmpty());
    }

    /** Only plugins that build on the base are content, each after what it depends on. */
    @Test
    void testOrder() {
        final Map<String, List<String>> dependencies =
                Map.of(
                        "Factory-Core", List.of(),
                        "Ikala-Graphics", List.of(),
                        "Factory-Client", List.of("Factory-Core", "Ikala-Graphics"),
                        "Lotomation", List.of("Factory-Core"),
                        "Addon", List.of("Zebra"),
                        "Zebra", List.of("Lotomation"));
        assertEquals(
                List.of("Lotomation", "Zebra", "Addon", "Factory-Client"),
                ContentFolders.order(dependencies, "Factory-Core"));
    }

    /**
     * Files get IDs from their paths, nested folders included, and the last folder with an ID wins,
     * whether its own or as an override.
     *
     * @param base A temporary folder.
     * @throws IOException If the files can't be written.
     */
    @Test
    void testFilesAndFind(@TempDir Path base) throws IOException {
        final Path game = base.resolve("Game/data");
        final Path pack = base.resolve("Pack/data");
        final Path own = game.resolve("textures/block/stone.png");
        final Path replaced = pack.resolve("overrides/game/textures/block/stone.png");
        final Path wrong = game.resolve("textures/block/notes.txt");
        for (Path file : List.of(own, replaced, wrong)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "x");
        }
        final ContentFolders.Listing listing =
                ContentFolders.files(List.of(game, pack), "textures", ".png");
        assertEquals(Map.of("game:block/stone", replaced), listing.files());
        assertEquals(1, listing.skipped().size());
        assertEquals(
                Optional.of(replaced),
                ContentFolders.find(List.of(game, pack), "textures", "game:block/stone", ".png"));
        assertEquals(
                Optional.of(own),
                ContentFolders.find(List.of(game), "textures", "game:block/stone", ".png"));
        assertTrue(
                ContentFolders.find(List.of(game), "textures", "game:../secret", ".png").isEmpty());
    }
}
