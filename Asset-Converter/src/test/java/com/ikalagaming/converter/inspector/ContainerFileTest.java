package com.ikalagaming.converter.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.asset.AssetValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/** Tests for reading containers to show in the workbench. */
class ContainerFileTest {

    @TempDir Path folder;

    @BeforeEach
    void setUp() throws IOException {
        AssetSamples.write(folder);
    }

    @Test
    void aValidContainerIsOk() throws IOException {
        final ContainerFile container = ContainerFile.read(folder.resolve(AssetSamples.VALID));
        assertTrue(container.inspection().isReadable());
        assertEquals("OK", container.status());
        assertEquals(Files.size(folder.resolve(AssetSamples.VALID)), container.size());
        assertTrue(container.summary().contains("format"), container.summary());
        assertTrue(container.path().isAbsolute());
    }

    @Test
    void aBadChecksumIsStillRead() throws IOException {
        final ContainerFile container =
                ContainerFile.read(folder.resolve(AssetSamples.BAD_CHECKSUM));
        assertFalse(container.inspection().entries().isEmpty());
        assertTrue(container.inspection().count(AssetValidator.Severity.ERROR) > 0);
        assertTrue(container.status().startsWith("Not readable"), container.status());
    }

    @Test
    void anUnknownOptionalSectionIsSkipped() throws IOException {
        final ContainerFile container =
                ContainerFile.read(folder.resolve(AssetSamples.UNKNOWN_OPTIONAL));
        assertTrue(container.inspection().isReadable());
        assertTrue(
                container.inspection().entries().stream()
                        .anyMatch(entry -> !entry.known() && !entry.isRequired()),
                "The optional section nothing knows is listed");
    }

    @Test
    void aMissingFileThrows() {
        assertThrows(
                NoSuchFileException.class, () -> ContainerFile.read(folder.resolve("missing.ika")));
    }

    @Test
    void pluralsReadWell() {
        assertEquals("1 error", ContainerFile.plural(1, "error"));
        assertEquals("0 warnings", ContainerFile.plural(0, "warning"));
    }
}
