package com.ikalagaming.graphics.ui.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Tests for {@link FileListing}. */
class FileListingTest {
    /** A folder to list. */
    @TempDir Path folder;

    /**
     * Fill the folder with two subfolders and a few files.
     *
     * @throws IOException If they can't be made.
     */
    private void fill() throws IOException {
        Files.createDirectories(folder.resolve("zeta"));
        Files.createDirectories(folder.resolve("Alpha"));
        Files.writeString(folder.resolve("b.GLB"), "");
        Files.writeString(folder.resolve("a.json"), "");
        Files.writeString(folder.resolve("c.glb"), "");
    }

    /**
     * The names listed.
     *
     * @param listing The listing.
     * @return The names.
     */
    private static List<String> names(FileListing listing) {
        return listing.getEntries().stream().map(FileListing.Entry::name).toList();
    }

    /**
     * Folders come first, then files, each by name ignoring case, filtered by extension.
     *
     * @throws IOException If the files can't be made.
     */
    @Test
    void testListsFoldersThenMatchingFiles() throws IOException {
        fill();
        FileListing all = new FileListing(folder, false, List.of());
        assertEquals(List.of("Alpha", "zeta", "a.json", "b.GLB", "c.glb"), names(all));

        FileListing models = new FileListing(folder, false, List.of(".glb"));
        assertEquals(List.of("Alpha", "zeta", "b.GLB", "c.glb"), names(models));

        FileListing folders = new FileListing(folder, true, List.of());
        assertEquals(List.of("Alpha", "zeta"), names(folders));
    }

    /**
     * Going into a folder and back up, and a folder that doesn't exist.
     *
     * @throws IOException If the files can't be made.
     */
    @Test
    void testNavigate() throws IOException {
        fill();
        FileListing listing = new FileListing(folder, false, List.of());
        assertTrue(listing.navigate(folder.resolve("zeta")));
        assertEquals(folder.resolve("zeta").toAbsolutePath().normalize(), listing.getDirectory());
        assertTrue(listing.getEntries().isEmpty());

        listing.up();
        assertEquals(folder.toAbsolutePath().normalize(), listing.getDirectory());

        assertFalse(listing.navigate(folder.resolve("missing")));
        assertNotNull(listing.getError());
        // The folder shown stays
        assertEquals(folder.toAbsolutePath().normalize(), listing.getDirectory());
    }

    /** A start folder that doesn't exist falls back to the working folder, noting why. */
    @Test
    void testMissingStart() {
        FileListing listing = new FileListing(folder.resolve("missing"), true, List.of());
        assertEquals(Path.of("").toAbsolutePath().normalize(), listing.getDirectory());
        assertNotNull(listing.getError());
    }

    /** Typed names resolve against the folder shown, and empty text is nothing. */
    @Test
    void testResolve() {
        FileListing listing = new FileListing(folder, false, List.of());
        assertEquals(
                folder.toAbsolutePath().normalize().resolve("x.json"),
                listing.resolve("  x.json "));
        assertNull(listing.resolve("   "));
        Path absolute = folder.resolve("zeta").toAbsolutePath();
        assertEquals(absolute, listing.resolve(absolute.toString()));
    }

    /** Saved names get the first extension unless they end in one of them. */
    @Test
    void testWithExtension() {
        FileListing listing = new FileListing(folder, false, List.of("json", "yml"));
        assertEquals("design.json", listing.withExtension("design"));
        assertEquals("design.YML", listing.withExtension("design.YML"));
        assertEquals("a.b.json", listing.withExtension("a.b"));
        FileListing any = new FileListing(folder, false, List.of());
        assertEquals("design", any.withExtension("design"));
    }
}
