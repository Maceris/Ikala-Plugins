package com.ikalagaming.converter.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class AssetInspectorTest {

    @TempDir Path folder;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectory(folder.resolve("b-folder"));
        Files.createDirectory(folder.resolve("A-folder"));
        Files.writeString(folder.resolve("notes.txt"), "not a container");
        AssetSamples.write(folder);
    }

    private static String names(AssetInspector inspector) {
        return String.join(
                ",",
                inspector.getEntries().stream().map(AssetInspector.DirectoryEntry::name).toList());
    }

    @Test
    void foldersComeFirstThenContainers() {
        AssetInspector inspector = new AssetInspector(folder);

        assertEquals(
                "A-folder,b-folder,bad-checksum.ika,truncated.ika,unknown-optional.ika,"
                        + "unknown-required.ika,valid.ika",
                names(inspector));
    }

    @Test
    void allFilesCanBeListed() {
        AssetInspector inspector = new AssetInspector(folder);
        inspector.setShowAllFiles(true);

        assertTrue(names(inspector).contains("notes.txt"));
    }

    @Test
    void navigatingUpAndIntoFolders() {
        AssetInspector inspector = new AssetInspector(folder.resolve("A-folder"));
        assertTrue(inspector.getEntries().isEmpty());

        inspector.up();
        assertEquals(folder.toAbsolutePath().normalize(), inspector.getDirectory());

        inspector.openPath(folder.resolve("b-folder").toString());
        assertEquals("b-folder", inspector.getDirectory().getFileName().toString());
    }

    @Test
    void openingAContainerInspectsIt() {
        AssetInspector inspector = new AssetInspector(folder);
        inspector.open(folder.resolve(AssetSamples.BAD_CHECKSUM));

        assertNotNull(inspector.getInspection());
        assertNull(inspector.getError());
        assertEquals(-1, inspector.getSelected());

        inspector.select(2);
        assertEquals(2, inspector.selectedEntry().index());
        inspector.select(99);
        assertNull(inspector.selectedEntry(), "Out of range selects nothing");
    }

    @Test
    void missingFilesReportAnErrorInsteadOfThrowing() {
        AssetInspector inspector = new AssetInspector(folder);
        inspector.open(folder.resolve(AssetSamples.VALID));
        inspector.open(folder.resolve("missing.ika"));

        assertNotNull(inspector.getError());
        assertNotNull(inspector.getInspection(), "The last good file stays open");

        inspector.navigate(folder.resolve("missing-folder"));
        assertNotNull(inspector.getError());
        assertEquals(folder.toAbsolutePath().normalize(), inspector.getDirectory());
    }
}
