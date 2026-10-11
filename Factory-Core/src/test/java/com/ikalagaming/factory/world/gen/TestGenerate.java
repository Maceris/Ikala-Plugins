package com.ikalagaming.factory.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;

import org.junit.jupiter.api.Test;

/**
 * Tests for generating chunks from the fixture world type.
 *
 * @author Ches Burks
 */
class TestGenerate {

    private static final String WATER = "test:water";

    private static final int LAST = World.CHUNK_SIZE - 1;

    /**
     * Whether a block is open space: air or fluid.
     *
     * @param block The block.
     * @return True if not solid.
     */
    private static boolean open(Block block) {
        return World.AIR.equals(block) || WATER.equals(block.getName());
    }

    /** Each stage only adds to the stage before it. */
    @Test
    void testStagesOnlyAdd() {
        WorldGenerator generator = WorldgenFixtures.generator(WorldgenFixtures.SEED);
        boolean sawWater = false;
        for (ChunkPos pos : new ChunkPos[] {new ChunkPos(0, -1, 0), new ChunkPos(3, 0, -2)}) {
            final Chunk biomes = generator.generate(pos, Stage.BIOMES);
            final Chunk terrain = generator.generate(pos, Stage.TERRAIN);
            final Chunk fluids = generator.generate(pos, Stage.FLUIDS);
            final Chunk rules = generator.generate(pos, Stage.BLOCK_RULES);
            for (int y = 0; y <= LAST; ++y) {
                for (int z = 0; z <= LAST; ++z) {
                    for (int x = 0; x <= LAST; ++x) {
                        assertEquals(World.AIR, biomes.getBlock(x, y, z));
                        assertEquals(biomes.getBiome(x, y, z), rules.getBiome(x, y, z));
                        final boolean solid = !World.AIR.equals(terrain.getBlock(x, y, z));
                        assertEquals(solid, !open(fluids.getBlock(x, y, z)));
                        assertEquals(solid, !open(rules.getBlock(x, y, z)));
                        if (solid) {
                            assertEquals(
                                    generator.getWorldType().defaultBlock(),
                                    fluids.getBlock(x, y, z));
                        } else {
                            assertEquals(fluids.getBlock(x, y, z), rules.getBlock(x, y, z));
                            sawWater |= WATER.equals(fluids.getBlock(x, y, z).getName());
                        }
                    }
                }
            }
        }
        assertTrue(sawWater, "The fixture's chunks below 0 should hold some water");
    }

    /** The sky is certainly air, so it isn't sampled. */
    @Test
    void testSkySkipsSampling() {
        WorldGenerator generator = WorldgenFixtures.generator(WorldgenFixtures.SEED);
        final Chunk sky = generator.generate(new ChunkPos(0, 10, 0));
        assertTrue(sky.isUniform());
        assertEquals(World.AIR, sky.getBlock(0, 0, 0));
        assertEquals(1, generator.getStats().skippedAir.get());
        assertEquals(0, generator.getStats().latticeSamples.get());
    }

    /** Grass only grows with open air right above it. */
    @Test
    void testGrassHasAirAbove() {
        WorldGenerator generator = WorldgenFixtures.generator(WorldgenFixtures.SEED);
        int grass = 0;
        for (int cx = -2; cx <= 2; ++cx) {
            for (int cz = -2; cz <= 2; ++cz) {
                for (int cy = -1; cy <= 1; ++cy) {
                    final Chunk chunk = generator.generate(new ChunkPos(cx, cy, cz));
                    final Chunk above = generator.generate(new ChunkPos(cx, cy + 1, cz));
                    for (int y = 0; y <= LAST; ++y) {
                        for (int z = 0; z <= LAST; ++z) {
                            for (int x = 0; x <= LAST; ++x) {
                                if (!"test:grass".equals(chunk.getBlock(x, y, z).getName())) {
                                    continue;
                                }
                                ++grass;
                                final Block up =
                                        y < LAST
                                                ? chunk.getBlock(x, y + 1, z)
                                                : above.getBlock(x, 0, z);
                                assertEquals(World.AIR, up, "Grass at " + cx + "," + cy + "," + cz);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(grass > 0, "The fixture's surface should have grass");
    }

    /** The floating island's underside follows the ceiling rule: moss with air below. */
    @Test
    void testIslandUnderside() {
        int moss = 0;
        for (long seed = 1; seed <= 5; ++seed) {
            WorldGenerator generator = WorldgenFixtures.generator(seed);
            for (int cx = -1; cx <= 0; ++cx) {
                for (int cz = -1; cz <= 0; ++cz) {
                    final Chunk chunk = generator.generate(new ChunkPos(cx, 2, cz));
                    final Chunk below = generator.generate(new ChunkPos(cx, 1, cz));
                    for (int y = 0; y <= LAST; ++y) {
                        for (int z = 0; z <= LAST; ++z) {
                            for (int x = 0; x <= LAST; ++x) {
                                if (!"test:moss".equals(chunk.getBlock(x, y, z).getName())) {
                                    continue;
                                }
                                ++moss;
                                final Block down =
                                        y > 0
                                                ? chunk.getBlock(x, y - 1, z)
                                                : below.getBlock(x, LAST, z);
                                assertTrue(open(down), "Moss needs open space below");
                            }
                        }
                    }
                }
            }
        }
        assertTrue(moss > 0, "Some seed should put moss under the island");
    }

    /** Nothing exists outside the world border. */
    @Test
    void testBorder() {
        WorldGenerator generator = WorldgenFixtures.generator(WorldgenFixtures.SEED);
        final long borderX = (long) generator.getWorldType().border().maxX();
        final ChunkPos straddling = ChunkPos.containing(borderX, 0, 0);
        final ChunkPos outside = new ChunkPos(straddling.x() + 1, -1, 0);
        // Deep enough to be solid inside the border
        final ChunkPos deep = new ChunkPos(straddling.x(), -20, 0);
        final Chunk chunk = generator.generate(deep);
        int solidInside = 0;
        for (int y = 0; y <= LAST; ++y) {
            for (int z = 0; z <= LAST; ++z) {
                for (int x = 0; x <= LAST; ++x) {
                    final boolean inside = deep.blockX() + x <= borderX;
                    if (!inside) {
                        assertEquals(World.AIR, chunk.getBlock(x, y, z));
                    } else if (!open(chunk.getBlock(x, y, z))) {
                        ++solidInside;
                    }
                }
            }
        }
        assertTrue(solidInside > 0, "Inside the border is rock");
        final Chunk beyond = generator.generate(outside);
        assertTrue(beyond.isUniform());
        assertEquals(World.AIR, beyond.getBlock(0, 0, 0));
        assertEquals(1, generator.getStats().outsideBorder.get());
    }

    /** The same chunk always comes out the same, and different chunks differ. */
    @Test
    void testRepeatable() {
        final ChunkPos pos = new ChunkPos(5, 0, -7);
        final long first =
                WorldgenFixtures.generator(WorldgenFixtures.SEED).generate(pos).contentHash();
        final long second =
                WorldgenFixtures.generator(WorldgenFixtures.SEED).generate(pos).contentHash();
        assertEquals(first, second);
        assertNotEquals(
                first,
                WorldgenFixtures.generator(WorldgenFixtures.SEED + 1).generate(pos).contentHash());
    }

    /** Biome ranking is stable and puts containing biomes first. */
    @Test
    void testBiomeRanking() {
        WorldGenerator generator = WorldgenFixtures.generator(WorldgenFixtures.SEED);
        final BiomeSelector selector = generator.getBiomes();
        final double[] values = {0.5, 0.0, 0.0};
        final var first = selector.rank(values, 1, 2, 3);
        assertEquals(first, selector.rank(values, 1, 2, 3));
        assertEquals("test:desert", first.get(0).biome().id());
        assertTrue(first.get(0).contains());
        // Nothing contains a temperature this hot and wet, so the fallback wins
        assertEquals("test:plains", selector.choose(new double[] {0.9, 0.9, 0.0}, 0, 0, 0).id());
    }

    /** Stages that aren't built yet are refused. */
    @Test
    void testUnbuiltStages() {
        WorldGenerator generator = WorldgenFixtures.generator(WorldgenFixtures.SEED);
        assertThrows(
                IllegalArgumentException.class,
                () -> generator.generate(new ChunkPos(0, 0, 0), Stage.FEATURES));
    }
}
