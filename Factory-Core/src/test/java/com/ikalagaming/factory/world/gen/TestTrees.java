package com.ikalagaming.factory.world.gen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.factory.world.gen.data.WorldgenCompiler;
import com.ikalagaming.factory.world.gen.data.WorldgenData;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tests for trees: growing a species, and placing them across chunks. */
class TestTrees {

    /** The log block. */
    private static final String LOG = "test:log";

    /** The leaves block. */
    private static final String LEAVES = "test:leaves";

    /** The spacing the test oaks keep. */
    private static final double SPACING = 4;

    /** How far around the origin to generate, in chunks. */
    private static final int AREA = 2;

    /**
     * The fixture plus a mod placing oaks on grass in plains.
     *
     * @return A new generator, with empty caches.
     */
    private static WorldGenerator generator() {
        final Path trees;
        try {
            trees = Path.of(TestTrees.class.getResource("/worldgen/trees/Trees/data").toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        final Set<String> blocks = new HashSet<>(WorldgenFixtures.BLOCKS);
        blocks.add(LOG);
        blocks.add(LEAVES);
        final WorldgenCompiler compiler =
                new WorldgenCompiler(
                        WorldgenData.load(List.of(WorldgenFixtures.folder(), trees)),
                        WorldgenFixtures.SEED,
                        blocks);
        final WorldType type =
                compiler.worldType(WorldgenFixtures.WORLD, null)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                compiler.getDiagnostics().toString()));
        assertFalse(compiler.hasErrors(), () -> compiler.getDiagnostics().toString());
        return new WorldGenerator(type, WorldgenFixtures.SEED);
    }

    /**
     * The chunks around the origin, in a fixed order.
     *
     * @return The positions.
     */
    private static List<ChunkPos> area() {
        final List<ChunkPos> out = new ArrayList<>();
        for (int cy = -1; cy <= 1; ++cy) {
            for (int cz = -AREA; cz <= AREA; ++cz) {
                for (int cx = -AREA; cx <= AREA; ++cx) {
                    out.add(new ChunkPos(cx, cy, cz));
                }
            }
        }
        return out;
    }

    /** A tree grows the same from the same seed, stands on its foot, and stays in its bounds. */
    @Test
    void testGrow() {
        final TreeSpecies species =
                new TreeSpecies(
                        "t:oak",
                        TreeSpecies.Shape.BROADLEAF,
                        new Block(LOG, null),
                        new Block(LEAVES, null),
                        new double[] {6, 14},
                        new double[] {0.3, 0.5},
                        new double[] {2, 6},
                        new double[] {1.5, 2.5});
        for (long seed = 0; seed < 20; ++seed) {
            final TreeSpecies.Grown a = species.grow(seed);
            final TreeSpecies.Grown b = species.grow(seed);
            assertArrayEquals(a.xs(), b.xs());
            assertArrayEquals(a.ys(), b.ys());
            assertArrayEquals(a.zs(), b.zs());
            assertArrayEquals(a.blocks(), b.blocks());
            boolean foot = false;
            int leaves = 0;
            for (int i = 0; i < a.size(); ++i) {
                assertTrue(Math.abs(a.xs()[i]) <= species.maxRadius());
                assertTrue(Math.abs(a.zs()[i]) <= species.maxRadius());
                assertTrue(a.ys()[i] >= 0 && a.ys()[i] <= species.maxHeight());
                if (a.xs()[i] == 0 && a.ys()[i] == 0 && a.zs()[i] == 0) {
                    assertEquals(LOG, a.blocks()[i].getName());
                    foot = true;
                }
                if (LEAVES.equals(a.blocks()[i].getName())) {
                    ++leaves;
                }
            }
            assertTrue(foot, "seed " + seed + " has no log at its foot");
            assertTrue(leaves > 20, "seed " + seed + " has " + leaves + " leaves");
        }
    }

    /**
     * Chunks come out the same whichever order they are generated in, so trees that cross chunk
     * borders agree; trees only fill what was air, and there are some.
     */
    @Test
    void testOrderAndAir() {
        final List<ChunkPos> area = area();
        final WorldGenerator forward = generator();
        final WorldGenerator backward = generator();
        final List<Long> forwardHashes = new ArrayList<>();
        for (ChunkPos pos : area) {
            forwardHashes.add(forward.generate(pos).contentHash());
        }
        for (int i = area.size() - 1; i >= 0; --i) {
            assertEquals(
                    forwardHashes.get(i),
                    backward.generate(area.get(i)).contentHash(),
                    "chunk " + area.get(i));
        }
        int logs = 0;
        int leaves = 0;
        for (ChunkPos pos : area) {
            final Chunk plain = forward.generate(pos, Stage.BLOCK_RULES);
            final Chunk featured = forward.generate(pos);
            for (int y = 0; y < World.CHUNK_SIZE; ++y) {
                for (int z = 0; z < World.CHUNK_SIZE; ++z) {
                    for (int x = 0; x < World.CHUNK_SIZE; ++x) {
                        final String before = plain.getBlock(x, y, z).getName();
                        final String after = featured.getBlock(x, y, z).getName();
                        if (before.equals(after)) {
                            continue;
                        }
                        assertEquals(World.AIR_NAME, before, "replaced at " + pos);
                        if (LOG.equals(after)) {
                            ++logs;
                        } else if (LEAVES.equals(after)) {
                            ++leaves;
                        }
                    }
                }
            }
        }
        assertTrue(logs > 0 && leaves > 0, logs + " logs, " + leaves + " leaves");
    }

    /** Trees keep at least their spacing from each other, and each has a log at its foot. */
    @Test
    void testSpacing() {
        final WorldGenerator generator = generator();
        final PlacedFeature placed = generator.getWorldType().features().get(0);
        final int[] size = placed.cells().size();
        final List<LargeFeatures.Candidate> trees = new ArrayList<>();
        final int cells = AREA * World.CHUNK_SIZE / size[0] + 1;
        for (long cy = -1; cy <= 1; ++cy) {
            for (long cz = -cells; cz <= cells; ++cz) {
                for (long cx = -cells; cx <= cells; ++cx) {
                    trees.addAll(generator.getLarge().instances(0, cx, cy, cz));
                }
            }
        }
        assertTrue(trees.size() > 1, "only " + trees.size() + " trees to compare");
        final int mask = World.CHUNK_SIZE - 1;
        for (LargeFeatures.Candidate tree : trees) {
            final Chunk chunk =
                    generator.generate(ChunkPos.containing(tree.x(), tree.y(), tree.z()));
            assertEquals(
                    LOG,
                    chunk.getBlock(
                                    (int) tree.x() & mask,
                                    (int) tree.y() & mask,
                                    (int) tree.z() & mask)
                            .getName(),
                    "no log at the foot of " + tree);
            for (LargeFeatures.Candidate other : trees) {
                if (other == tree) {
                    continue;
                }
                final double dx = other.x() - tree.x();
                final double dy = other.y() - tree.y();
                final double dz = other.z() - tree.z();
                assertTrue(
                        Math.sqrt(dx * dx + dy * dy + dz * dz) >= SPACING,
                        tree + " and " + other + " are too close");
            }
        }
    }
}
