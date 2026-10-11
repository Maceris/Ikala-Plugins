package com.ikalagaming.factory.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.gen.debug.WorldgenDebug;
import com.ikalagaming.factory.world.gen.debug.WorldgenTool;
import com.ikalagaming.factory.world.gen.density.Box;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for the headless debug API and the command line tools.
 *
 * @author Ches Burks
 */
class TestWorldgenDebug {

    private static WorldgenDebug debug() {
        WorldgenDebug.LoadResult result =
                WorldgenDebug.load(
                        WorldgenFixtures.WORLD,
                        List.of(WorldgenFixtures.folder()),
                        WorldgenFixtures.SEED,
                        WorldgenFixtures.BLOCKS);
        assertEquals(List.of(), result.diagnostics());
        return result.debug().orElseThrow();
    }

    /** A trace's root is the target's value, and each child is evaluated where it really is. */
    @Test
    void testTraceMatchesValues() {
        WorldgenDebug debug = debug();
        final double x = 12.5;
        final double y = 3;
        final double z = -7.25;
        final WorldgenDebug.Trace trace = debug.trace("test:strata", x, y, z);
        assertEquals(debug.resolve("test:strata", null).value(x, y, z, null), trace.value());
        assertEquals("density/test:strata", trace.origin());
        assertTrue(trace.bounds().contains(trace.value()));
        checkTrace(trace);
    }

    /**
     * Every traced node's value is inside its bounds, so trace points are where nodes are
     * evaluated.
     *
     * @param trace The trace.
     */
    private static void checkTrace(WorldgenDebug.Trace trace) {
        if (!Double.isNaN(trace.value())) {
            assertTrue(
                    trace.bounds().min() - 1e-9 <= trace.value()
                            && trace.value() <= trace.bounds().max() + 1e-9,
                    trace.type() + " " + trace.value() + " " + trace.bounds());
        }
        trace.children().forEach(TestWorldgenDebug::checkTrace);
    }

    /** Targets inside files resolve by field path. */
    @Test
    void testPathTargets() {
        WorldgenDebug debug = debug();
        assertEquals("spline", debug.resolve("test:ground#args[0]", null).type());
        assertEquals("blur", debug.resolve("test:enclosure#density", null).type());
        assertEquals("noise", debug.resolve("test:hills", null).type());
        assertThrows(IllegalArgumentException.class, () -> debug.resolve("test:nothing", null));
        assertThrows(
                IllegalArgumentException.class, () -> debug.resolve("test:ground#args[9]", null));
    }

    /** The bounds tree says the sky is certainly air. */
    @Test
    void testBoundsTree() {
        final WorldgenDebug.Bounds sky =
                debug().bounds("test:terrain", new Box(0, 200, 0, 16, 216, 16));
        assertTrue(sky.bounds().max() <= 0, sky.bounds().toString());
        assertFalse(sky.children().isEmpty());
    }

    /** The biome report agrees with what generation chose. */
    @Test
    void testBiomeAtMatchesGeneration() {
        WorldgenDebug debug = debug();
        final Chunk chunk = debug.generate(new ChunkPos(2, 0, -3), Stage.BIOMES);
        final WorldgenDebug.BiomeReport report = debug.biomeAt(2 * 16 + 5, 6, -3 * 16 + 9);
        assertEquals(chunk.getBiome(5, 6, 9), report.chosen());
        assertEquals(3, report.parameters().size());
        assertEquals(3, report.ranked().size());
    }

    /**
     * The command line tools run: check, render, hash.
     *
     * @param folder A temporary folder for the image.
     */
    @Test
    void testTool(@TempDir Path folder) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        final String data = WorldgenFixtures.folder().toString();
        assertEquals(0, WorldgenTool.run(List.of("check", data), out));
        final Path image = folder.resolve("slice.png");
        assertEquals(
                0,
                WorldgenTool.run(
                        List.of(
                                "render",
                                "--data",
                                data,
                                "--world",
                                WorldgenFixtures.WORLD,
                                "--target",
                                "test:terrain",
                                "--plane",
                                "xy",
                                "--size",
                                "32,16",
                                "--out",
                                image.toString()),
                        out));
        assertTrue(Files.isRegularFile(image));
        assertEquals(
                0,
                WorldgenTool.run(
                        List.of(
                                "hash",
                                "--data",
                                data,
                                "--world",
                                WorldgenFixtures.WORLD,
                                "--chunks",
                                "0,0,0;1,-1,2"),
                        out));
        assertEquals(1, WorldgenTool.run(List.of("nonsense"), out));
        final String printed = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("0 errors, 0 warnings"), printed);
        assertTrue(printed.lines().anyMatch(l -> l.startsWith("1 -1 2 ")), printed);
    }
}
