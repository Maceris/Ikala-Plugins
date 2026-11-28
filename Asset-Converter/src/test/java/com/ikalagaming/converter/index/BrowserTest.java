package com.ikalagaming.converter.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** The browser's thumbnails, search and collections. */
class BrowserTest {
    private static AssetIndex index() {
        final List<AssetType> types = new java.util.ArrayList<>(GameTypes.all());
        types.addAll(EditorTypes.all());
        return new AssetIndex(types);
    }

    private static void png(Path file, int size, int argb) throws Exception {
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; ++y) {
            for (int x = 0; x < size; ++x) {
                image.setRGB(x, y, x == 0 ? 0xFFFF0000 : argb);
            }
        }
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }

    @Test
    void aThumbnailIsMadeOnceAndAgainOnlyWhenItsFileChanges(@TempDir Path temp) throws Exception {
        final Path data = temp.resolve("plugins/Base/data");
        final Path rock = data.resolve("textures/block/rock.png");
        png(rock, 16, 0xFF808080);
        final AssetIndex index = index();
        final List<AssetIndex.Root> roots = List.of(AssetIndex.Root.of(data));
        AssetIndex.Snapshot snapshot = index.scan(roots, null);
        final AssetIndex.Entry entry = snapshot.entry(rock);
        final Path cache = temp.resolve("cache/thumbnails");
        final Path made;
        try (Thumbnails thumbnails = new Thumbnails(cache, Thumbnails.DEFAULT_CAP)) {
            assertNull(thumbnails.get(entry, snapshot), "Not made yet");
            made = thumbnails.make(entry, snapshot).get(10, TimeUnit.SECONDS);
        }
        final BufferedImage image = ImageIO.read(made.toFile());
        assertEquals(96, image.getWidth(), "16 px tiled 3 times at twice the size");
        assertEquals(0xFFFF0000, image.getRGB(32, 5), "The second tile's first column, sharp");
        assertEquals(0xFFFF0000, image.getRGB(33, 5));
        assertEquals(0xFF808080, image.getRGB(34, 5));
        try (Thumbnails again = new Thumbnails(cache, Thumbnails.DEFAULT_CAP)) {
            assertEquals(made, again.get(entry, snapshot), "Found by another cache instance");
        }

        png(rock, 16, 0xFF404040);
        Files.setLastModifiedTime(rock, FileTime.fromMillis(System.currentTimeMillis() + 5000));
        snapshot = index.scan(roots, snapshot);
        assertNotEquals(
                Thumbnails.key(entry, snapshot),
                Thumbnails.key(snapshot.entry(rock), snapshot),
                "A changed file has a new key");
    }

    @Test
    void theCacheDropsTheLeastRecentlyUsedPastItsSize(@TempDir Path temp) throws Exception {
        final Path data = temp.resolve("plugins/Base/data");
        for (int i = 0; i < 4; ++i) {
            png(data.resolve("textures/block/t" + i + ".png"), 16, 0xFF000000 + i);
        }
        final AssetIndex.Snapshot snapshot = index().scan(List.of(AssetIndex.Root.of(data)), null);
        final Path cache = temp.resolve("thumbnails");
        try (Thumbnails thumbnails = new Thumbnails(cache, 600)) {
            for (int i = 0; i < 4; ++i) {
                thumbnails
                        .make(
                                snapshot.entry(data.resolve("textures/block/t" + i + ".png")),
                                snapshot)
                        .get(10, TimeUnit.SECONDS);
            }
        }
        long total = 0;
        try (var files = Files.list(cache)) {
            for (Path file : files.toList()) {
                total += Files.size(file);
            }
        }
        assertTrue(total <= 600, "Kept under its size: " + total);
    }

    @Test
    void fuzzySearchFindsLettersInOrderBestFirst() {
        assertTrue(AssetSearch.score("grs", "grass.png") > 0);
        assertEquals(-1, AssetSearch.score("gsr", "grass.png"), "In order");
        assertTrue(
                AssetSearch.score("gra", "grass.png") > AssetSearch.score("gra", "big_red_ash.png"),
                "A run at the start beats scattered letters");
        assertTrue(
                AssetSearch.score("ol", "oak_log.ika") > AssetSearch.score("ol", "coal.ika"),
                "Word starts count");
        assertEquals(0, AssetSearch.score("", "anything"));
    }

    @Test
    void searchesFilterAndCollectionsSurviveBeingReadAgain(@TempDir Path temp) throws Exception {
        final Path data = temp.resolve("plugins/Base/data");
        png(data.resolve("textures/block/grass.png"), 16, 0xFF00FF00);
        png(data.resolve("textures/block/gravel.png"), 16, 0xFF888888);
        Files.createDirectories(data.resolve("render_materials/block"));
        Files.writeString(data.resolve("render_materials/block/grass.ika"), "ika");
        final AssetIndex.Snapshot snapshot = index().scan(List.of(AssetIndex.Root.of(data)), null);
        final List<AssetIndex.Entry> textures =
                AssetSearch.find(
                        snapshot,
                        new AssetSearch.Query("gras", EditorTypes.TEXTURE, "base", null),
                        null);
        assertEquals(1, textures.size(), textures.toString());
        assertEquals("base:block/grass", textures.getFirst().id());
        assertEquals(
                2,
                AssetSearch.find(snapshot, new AssetSearch.Query("grass", null, null, null), null)
                        .size(),
                "Any type");

        final Path file = temp.resolve("state/asset-collections.yml");
        final AssetCollections collections = new AssetCollections(file);
        collections.saveSearch(
                "Green", new AssetSearch.Query("gras", EditorTypes.TEXTURE, null, data));
        assertTrue(collections.add("Picked", data.resolve("textures/block/gravel.png")));
        assertFalse(collections.add("Green", data.resolve("textures/block/gravel.png")));

        final AssetCollections read = new AssetCollections(file);
        assertEquals(2, read.all().size());
        final AssetCollections.Collection green = read.get("Green");
        assertNotNull(green);
        assertEquals(EditorTypes.TEXTURE, green.search().type());
        assertEquals(data, green.search().folder());
        assertEquals(
                Set.of(data.resolve("textures/block/gravel.png").toAbsolutePath().normalize()),
                read.get("Picked").files());
        assertEquals(
                List.of(data.resolve("textures/block/gravel.png").toAbsolutePath().normalize()),
                AssetSearch.find(snapshot, AssetSearch.Query.ALL, read.get("Picked").files())
                        .stream()
                        .map(AssetIndex.Entry::file)
                        .toList());
    }
}
