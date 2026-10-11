package com.ikalagaming.factory.world.gen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.factory.world.gen.data.Kind;
import com.ikalagaming.factory.world.gen.data.WorldgenData;
import com.ikalagaming.factory.world.gen.debug.Colormaps;
import com.ikalagaming.factory.world.gen.debug.SliceImages;
import com.ikalagaming.factory.world.gen.debug.WorldgenDebug;
import com.ikalagaming.factory.world.gen.density.DensityNode;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tests for the slice images both the CLI and the Lab draw, rule explanations and the data hash.
 *
 * @author Ches Burks
 */
class TestSliceImages {

    private static WorldgenDebug debug() {
        return WorldgenDebug.load(
                        WorldgenFixtures.WORLD,
                        List.of(WorldgenFixtures.folder()),
                        WorldgenFixtures.SEED,
                        WorldgenFixtures.BLOCKS)
                .debug()
                .orElseThrow();
    }

    /** The zero contour is drawn where the sign changes, and the sides take their colors. */
    @Test
    void testDivergingContour() {
        final SliceImages.Request request =
                new SliceImages.Request(
                        "t", null, WorldgenDebug.Plane.XZ, new double[3], 3, 1, 1, Stage.TERRAIN);
        final SliceImages.Samples samples = new SliceImages.Samples(new double[] {-2, 2, 2}, null);
        final SliceImages.Image image =
                SliceImages.colorize(request, samples, SliceImages.Colormap.DIVERGING, 2);
        assertEquals(Colormaps.CONTOUR, image.rgb()[0]);
        assertEquals(Colormaps.diverging(1), image.rgb()[1]);
        assertEquals(Colormaps.diverging(1), image.rgb()[2]);
        assertEquals(-2, image.range().min());
        assertEquals(2, image.range().max());
    }

    /** Drawing in bands of rows, as the Lab does on several threads, matches drawing at once. */
    @Test
    void testBandsMatchWhole() {
        WorldgenDebug debug = debug();
        for (String target : new String[] {"test:terrain", SliceImages.BLOCKS_TARGET}) {
            final SliceImages.Request request =
                    new SliceImages.Request(
                            target,
                            null,
                            WorldgenDebug.Plane.XY,
                            new double[] {-20, 40, 3},
                            40,
                            30,
                            2,
                            Stage.BLOCK_RULES);
            final SliceImages.Image whole =
                    SliceImages.render(debug, request, SliceImages.Colormap.DIVERGING, 30);
            final DensityNode node =
                    request.isCategorical() ? null : debug.resolve(request.target(), null);
            final SliceImages.Samples samples = SliceImages.Samples.allocate(request);
            Map<ChunkPos, com.ikalagaming.factory.world.Chunk> chunks = new ConcurrentHashMap<>();
            final int band = 7;
            for (int row = 0; row < request.height(); row += band) {
                SliceImages.sampleRows(
                        debug,
                        request,
                        node,
                        row,
                        Math.min(band, request.height() - row),
                        chunks,
                        samples);
            }
            final SliceImages.Image banded =
                    SliceImages.colorize(request, samples, SliceImages.Colormap.DIVERGING, 30);
            assertArrayEquals(whole.rgb(), banded.rgb(), target);
        }
    }

    /** Explaining a block names the rule generation used, and agrees with the generated block. */
    @Test
    void testExplainMatchesGeneration() {
        WorldGenerator generator = WorldgenFixtures.generator(WorldgenFixtures.SEED);
        int explained = 0;
        for (int cx = -1; cx <= 1; ++cx) {
            final ChunkPos pos = new ChunkPos(cx, 0, 0);
            final var chunk = generator.generate(pos);
            for (int x = 0; x < World.CHUNK_SIZE; x += 3) {
                for (int y = 0; y < World.CHUNK_SIZE; y += 3) {
                    final Block block = chunk.getBlock(x, y, 5);
                    final BlockRules.Match match =
                            generator.explain(pos.blockX() + x, pos.blockY() + y, pos.blockZ() + 5);
                    if (match == null) {
                        continue;
                    }
                    ++explained;
                    assertEquals(block, match.block());
                    assertTrue(match.rule().contains("rules["), match.rule());
                    assertTrue(match.toString().endsWith(block.getName()));
                }
            }
        }
        assertTrue(explained > 0, "Some sampled blocks come from rules");
        // Open space has no rule
        assertNull(generator.explain(0, 1000, 0));
    }

    /** The data hash changes with any file, and not with load order. */
    @Test
    void testContentHash() {
        final long base =
                WorldgenData.fromText(
                                Map.of(
                                        Kind.DENSITY,
                                        Map.of(
                                                "t:a",
                                                "{type:\"constant\", value:1.0}",
                                                "t:b",
                                                "2.0")))
                        .contentHash();
        final long same =
                WorldgenData.fromText(
                                Map.of(
                                        Kind.DENSITY,
                                        Map.of(
                                                "t:b",
                                                "2.0",
                                                "t:a",
                                                "{type:\"constant\", value:1.0}")))
                        .contentHash();
        final long changed =
                WorldgenData.fromText(
                                Map.of(
                                        Kind.DENSITY,
                                        Map.of(
                                                "t:a",
                                                "{type:\"constant\", value:1.5}",
                                                "t:b",
                                                "2.0")))
                        .contentHash();
        assertEquals(base, same);
        assertNotEquals(base, changed);
        assertNotNull(WorldgenData.load(List.of(WorldgenFixtures.folder())));
    }
}
