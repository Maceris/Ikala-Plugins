package com.ikalagaming.converter.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Tests for browsing folders in the workbench, and telling file kinds apart. */
class FileBrowserTest {

    /** The folder browsed. */
    @TempDir Path folder;

    @Test
    void testKinds() {
        assertEquals(AssetKind.IMAGE, AssetKind.of(Path.of("a", "Brick.PNG")));
        assertEquals(AssetKind.IMAGE, AssetKind.of(Path.of("sky.hdr")));
        assertEquals(AssetKind.UNKNOWN, AssetKind.of(Path.of("notes.txt")));
        assertEquals(AssetKind.UNKNOWN, AssetKind.of(Path.of("README")));
    }

    @Test
    void testListsFoldersThenOpenableFiles() throws IOException {
        Files.createDirectory(folder.resolve("zeta"));
        Files.createFile(folder.resolve("b.png"));
        Files.createFile(folder.resolve("A.tga"));
        Files.createFile(folder.resolve("notes.txt"));

        FileBrowser browser = new FileBrowser(folder);
        List<String> names = browser.getEntries().stream().map(FileBrowser.Entry::name).toList();
        assertEquals(List.of("zeta", "A.tga", "b.png"), names);

        browser.setShowAllFiles(true);
        names = browser.getEntries().stream().map(FileBrowser.Entry::name).toList();
        assertEquals(List.of("zeta", "A.tga", "b.png", "notes.txt"), names);
    }

    @Test
    void testUpAndMissingFolder() {
        FileBrowser browser = new FileBrowser(folder);
        browser.navigate(folder.resolve("missing"));
        assertNotNull(browser.getError());
        assertEquals(folder.toAbsolutePath().normalize(), browser.getDirectory());

        browser.up();
        assertNull(browser.getError());
        assertEquals(folder.toAbsolutePath().normalize().getParent(), browser.getDirectory());
    }
}
