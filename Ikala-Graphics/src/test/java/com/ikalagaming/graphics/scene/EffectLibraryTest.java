package com.ikalagaming.graphics.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.EffectHandle;
import com.ikalagaming.graphics.EmitterHandle;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

/** Loading effects from files, reloading them into their emitters, and unloading them. */
class EffectLibraryTest {

    /** The owner key effects are loaded under. */
    private static final String OWNER = "plugin#1";

    /** Another plugin's key. */
    private static final String OTHER = "other#2";

    /** A folder for the test's files. */
    @TempDir Path folder;

    /** How many times a file has been written, to move its time forward each time. */
    private int writes;

    /**
     * Write an effect file, its time a second later than the last write's, so a reload sees it
     * changed whatever the file system's clock resolution.
     *
     * @param file The file.
     * @param yaml What it holds.
     * @throws IOException If it can't be written.
     */
    private void write(Path file, String yaml) throws IOException {
        Files.writeString(file, yaml);
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000L + 1000L * ++writes));
    }

    /**
     * The rate an emitter spawns at, from what it collects over a second.
     *
     * @param registry The emitters.
     * @return The first spawn's effect's rate.
     */
    private static float collectedRate(EmitterRegistry registry) {
        final List<EmitterRegistry.Spawn> spawns = new ArrayList<>();
        registry.collect(1, spawns);
        return spawns.getFirst().effect().getRate();
    }

    /** A changed file reaches the emitters spawned from it. */
    @Test
    void testReloadReachesEmitters() throws IOException {
        final Path file = folder.resolve("puff.effect.yml");
        write(file, "rate: 10\n");
        final EffectLibrary library = new EffectLibrary();
        final EffectHandle handle = library.load(OWNER, file);
        final EmitterRegistry registry = new EmitterRegistry();
        final EffectSource source = library.sourceFor(OWNER, handle);
        assertNotNull(source);
        registry.add(OWNER, source, new Vector3d());
        assertEquals(10, collectedRate(registry));

        assertEquals(0, library.reloadChanged());
        write(file, "rate: 40\n");
        assertEquals(1, library.reloadChanged());
        assertEquals(40, collectedRate(registry));
        assertEquals(40, library.effectOf(handle).getRate());
    }

    /** An edit that doesn't load keeps the last good effect, and is tried once until it changes. */
    @Test
    void testBrokenEditKeepsTheLastGoodEffect() throws IOException {
        final Path file = folder.resolve("puff.effect.yml");
        write(file, "rate: 10\n");
        final EffectLibrary library = new EffectLibrary();
        final EffectHandle handle = library.load(OWNER, file);
        write(file, "rate: lots\n");
        assertEquals(0, library.reloadChanged());
        assertEquals(10, library.effectOf(handle).getRate());
        assertTrue(library.errorOf(handle).contains("rate: expected a number"));
        // Not changed since, so not read again
        assertEquals(0, library.reloadChanged());
        write(file, "rate: 20\n");
        assertEquals(1, library.reloadChanged());
        assertEquals(20, library.effectOf(handle).getRate());
        assertNull(library.errorOf(handle));
    }

    /** Only the owner spawns from or unloads an effect. */
    @Test
    void testOwnership() throws IOException {
        final Path file = folder.resolve("puff.effect.yml");
        write(file, "rate: 10\n");
        final EffectLibrary library = new EffectLibrary();
        final EffectHandle handle = library.load(OWNER, file);
        assertNull(library.sourceFor(OTHER, handle));
        assertFalse(library.unload(OTHER, handle));
        assertEquals(OWNER, library.ownerOf(handle));
        assertTrue(library.unload(OWNER, handle));
        assertNull(library.effectOf(handle));
        assertNull(library.sourceFor(OWNER, handle));
    }

    /**
     * Unloading a plugin forgets its effects and stops reloading them, while emitters spawned from
     * them keep the last version, and a new effect in the freed slot doesn't answer old handles.
     */
    @Test
    void testUnloadingAPlugin() throws IOException {
        final Path file = folder.resolve("puff.effect.yml");
        write(file, "rate: 10\n");
        final EffectLibrary library = new EffectLibrary();
        final EffectHandle handle = library.load(OWNER, file);
        final EmitterRegistry registry = new EmitterRegistry();
        final EmitterHandle emitter =
                registry.add(OWNER, library.sourceFor(OWNER, handle), new Vector3d());
        assertEquals(1, library.removeAllOwnedBy(OWNER));
        assertNull(library.effectOf(handle));
        write(file, "rate: 99\n");
        assertEquals(0, library.reloadChanged());
        assertEquals(10, collectedRate(registry));

        final EffectHandle next = library.load(OTHER, file);
        assertEquals(handle.slot(), next.slot());
        assertNull(library.effectOf(handle));
        assertEquals(99, library.effectOf(next).getRate());

        assertEquals(1, registry.removeAllOwnedBy(OWNER));
        assertFalse(registry.isValid(emitter));
    }

    /** Files are only checked with hot reload on, and then no more than twice a second. */
    @Test
    void testHotReloadChecksWhenDue() throws IOException {
        final Path file = folder.resolve("puff.effect.yml");
        write(file, "rate: 10\n");
        final EffectLibrary library = new EffectLibrary();
        final EffectHandle handle = library.load(OWNER, file);
        write(file, "rate: 30\n");
        final long start = 1_000_000_000L;
        assertEquals(0, library.checkIfDue(start));
        assertEquals(10, library.effectOf(handle).getRate());
        library.setHotReload(true);
        assertEquals(1, library.checkIfDue(start));
        write(file, "rate: 50\n");
        assertEquals(0, library.checkIfDue(start + EffectLibrary.CHECK_INTERVAL_NANOS / 2));
        assertEquals(1, library.checkIfDue(start + EffectLibrary.CHECK_INTERVAL_NANOS));
        assertEquals(50, library.effectOf(handle).getRate());
    }
}
