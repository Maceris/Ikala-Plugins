package com.ikalagaming.factory.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.SplittableRandom;

/** Tests for creating and listing saves. */
class TestSaves {

    private static final String OVERWORLD = "lotomation:overworld";

    @TempDir Path saves;

    private static Clock at(long millis) {
        return Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
    }

    private SaveSummary create(String name, String seed, long millis) throws IOException {
        return Saves.create(
                saves,
                new NewGame(name, OVERWORLD, seed),
                "0.0.1-alpha",
                new SplittableRandom(7),
                at(millis));
    }

    @Test
    void testBlankNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> create("  ", "", 1));
    }

    @Test
    void testCreateWritesSaveAndWorld() throws IOException {
        SaveSummary created = create("My World", "42", 1000);

        assertEquals("My World", created.folder());
        assertTrue(Files.isRegularFile(saves.resolve("My World").resolve(Saves.SAVE_FILE)));
        assertTrue(
                Files.isRegularFile(
                        saves.resolve("My World")
                                .resolve(Saves.WORLDS_FOLDER)
                                .resolve("overworld")
                                .resolve(Saves.WORLD_FILE)));

        SaveSummary read = Saves.read(saves.resolve("My World")).orElseThrow();
        assertEquals(created, read);
        assertEquals(42L, read.seed());
        assertEquals(OVERWORLD, read.worldType());
        assertEquals(List.of("overworld"), read.worlds());
        assertTrue(read.devBuild());
        assertEquals(1000L, read.created());
    }

    @Test
    void testDevBuild() {
        assertTrue(Saves.isDevBuild("0.0.1-alpha"));
        assertFalse(Saves.isDevBuild("1.2.0"));
        assertFalse(Saves.isDevBuild("1.2.0+build-7"));
    }

    @Test
    void testFolderNames() {
        assertEquals("My World", Saves.safeFolderName("  My World  "));
        assertEquals("Ch_s World", Saves.safeFolderName("Chés World"));
        assertEquals("a_b", Saves.safeFolderName("a/b"));
        assertEquals("_con", Saves.safeFolderName("con"));
        assertEquals("_nul.txt", Saves.safeFolderName("nul.txt"));
        assertEquals("World", Saves.safeFolderName("..."));
        assertEquals("World", Saves.safeFolderName("???"));
        assertEquals("hidden", Saves.safeFolderName(".hidden"));
        assertEquals(Saves.MAX_FOLDER_LENGTH, Saves.safeFolderName("x".repeat(200)).length());
    }

    @Test
    void testListSkipsUnfinishedAndSortsByLastPlayed() throws IOException {
        create("Old", "", 1000);
        create("New", "", 2000);
        // Never finished being made: no save.kvt
        Files.createDirectories(saves.resolve("Broken"));

        List<SaveSummary> listed = Saves.list(saves);

        assertEquals(List.of("New", "Old"), listed.stream().map(SaveSummary::name).toList());

        assertTrue(Saves.markPlayed(saves.resolve("Old"), at(3000)));
        assertEquals(
                List.of("Old", "New"), Saves.list(saves).stream().map(SaveSummary::name).toList());
    }

    @Test
    void testMissingFolderListsNothing() {
        assertTrue(Saves.list(saves.resolve("missing")).isEmpty());
    }

    @Test
    void testSameNameGetsNewFolder() throws IOException {
        create("Twin", "", 1);
        SaveSummary second = create("Twin", "", 2);
        SaveSummary third = create("Twin", "", 3);

        assertEquals("Twin 2", second.folder());
        assertEquals("Twin 3", third.folder());
        assertEquals("Twin", second.name());
    }

    @Test
    void testSeeds() {
        SplittableRandom random = new SplittableRandom(1);
        assertEquals(-5L, Saves.parseSeed(" -5 ", random));
        // Text always gives the same seed
        assertEquals(Saves.parseSeed("hello", random), Saves.parseSeed("hello", random));
        assertNotEquals(Saves.parseSeed("hello", random), Saves.parseSeed("world", random));
        // Blank is random
        assertNotEquals(Saves.parseSeed("", random), Saves.parseSeed("", random));
    }

    @Test
    void testWorldFolder() {
        assertEquals("overworld", Saves.worldFolderFor(OVERWORLD));
        assertEquals("caves_deep", Saves.worldFolderFor("mod:caves/deep"));
    }
}
