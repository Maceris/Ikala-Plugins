package com.ikalagaming.factory.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.gen.data.Diagnostic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for the Lab's data loading and hot reload.
 *
 * @author Ches Burks
 */
class TestLabSession {

    /** How far apart file times are set, so a change always shows. */
    private static final long LATER_MILLIS = 5000;

    /**
     * Write a small world: a flat surface at y = 0.
     *
     * @param folder The data folder.
     * @return The terrain file, for editing.
     * @throws IOException If the files can't be written.
     */
    private static Path writeWorld(Path folder) throws IOException {
        final Path worldgen = folder.resolve("mods/t/worldgen");
        Files.createDirectories(worldgen.resolve("density"));
        Files.createDirectories(worldgen.resolve("biome"));
        Files.createDirectories(worldgen.resolve("world_type"));
        final Path terrain = worldgen.resolve("density/terrain.kvt");
        Files.writeString(terrain, "{type:\"neg\", arg:{type:\"axis\", axis:\"y\"}}");
        Files.writeString(worldgen.resolve("biome/plain.kvt"), "{}");
        Files.writeString(
                worldgen.resolve("world_type/flat.kvt"),
                "{density:\"t:terrain\", default_block:\"t:stone\", parameters:[T;],"
                        + " biomes:[T;\"t:plain\"], fallback_biome:\"t:plain\"}");
        return terrain;
    }

    /**
     * Edit a file, moving its time on so the change is noticed.
     *
     * @param file The file.
     * @param text The new contents.
     * @throws IOException If it can't be written.
     */
    private static void edit(Path file, String text) throws IOException {
        final FileTime before = Files.getLastModifiedTime(file);
        Files.writeString(file, text);
        Files.setLastModifiedTime(file, FileTime.fromMillis(before.toMillis() + LATER_MILLIS));
    }

    /**
     * Edits reload, and a broken edit keeps the last good world, marked stale.
     *
     * @param folder A temporary data folder.
     * @throws IOException If the files can't be written.
     */
    @Test
    void testHotReload(@TempDir Path folder) throws IOException {
        final Path terrain = writeWorld(folder);
        List<LabSession.State> told = new ArrayList<>();
        LabSession session = new LabSession(told::add);
        final LabSession.State first =
                session.load(new LabSession.Settings(List.of(folder), "t:flat", 1));
        assertNotNull(first.debug(), first.diagnostics().toString());
        assertFalse(first.stale());
        assertFalse(session.reloadIfChanged(), "Nothing changed yet");

        // A good edit loads the new world
        edit(
                terrain,
                "{type:\"add\", args:[N;{type:\"neg\", arg:{type:\"axis\", axis:\"y\"}},"
                        + " {type:\"constant\", value:5.0}]}");
        assertTrue(session.reloadIfChanged());
        final LabSession.State second = session.getState();
        assertFalse(second.stale());
        assertEquals(5, second.debug().resolve("t:terrain", null).value(0, 0, 0, null));
        assertTrue(second.version() > first.version());
        assertTrue(second.dataHash() != first.dataHash());

        // A broken edit keeps showing the last good world, and says what's wrong
        edit(terrain, "{type:\"nope\"}");
        assertTrue(session.reloadIfChanged());
        final LabSession.State third = session.getState();
        assertTrue(third.stale());
        assertSame(second.debug(), third.debug());
        assertTrue(
                third.diagnostics().stream()
                        .anyMatch(
                                d ->
                                        d.severity() == Diagnostic.Severity.ERROR
                                                && "WORLDGEN_UNKNOWN_TYPE".equals(d.code())));
        assertEquals(List.of(first, second, third), told);
    }

    /**
     * A broken world with nothing good to fall back on shows nothing.
     *
     * @param folder A temporary data folder.
     * @throws IOException If the files can't be written.
     */
    @Test
    void testNothingToKeep(@TempDir Path folder) throws IOException {
        writeWorld(folder);
        LabSession session = new LabSession(state -> {});
        final LabSession.State state =
                session.load(new LabSession.Settings(List.of(folder), "t:missing", 1));
        assertNull(state.debug());
        assertFalse(state.stale());
        assertFalse(state.diagnostics().isEmpty());
    }
}
