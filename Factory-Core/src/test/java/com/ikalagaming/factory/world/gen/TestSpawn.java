package com.ikalagaming.factory.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.factory.world.gen.data.Kind;
import com.ikalagaming.factory.world.gen.data.WorldgenCompiler;

import org.junit.jupiter.api.Test;

/** Tests for finding where new players start. */
class TestSpawn {

    /** Fluids are named water; everything else can be stood on. */
    private static final SpawnFinder.Kinds KINDS =
            new SpawnFinder.Kinds() {
                @Override
                public boolean isFluid(Block block) {
                    return block.getName().endsWith(":water");
                }

                @Override
                public boolean canStandOn(Block block) {
                    return true;
                }
            };

    /** Solid below y = 10: a flat world whose floor is at 9. */
    private static final String FLAT =
            "{type:\"add\", args:[N;{type:\"constant\", value:10.0},"
                    + "{type:\"mul\", args:[N;{type:\"constant\", value:-1.0},"
                    + "{type:\"axis\", axis:\"y\"}]}]}";

    /**
     * A world type with one biome, a density, and the given extra fields.
     *
     * @param density The density's text.
     * @param extra More fields, such as a spawn rule, or blank.
     * @return The world type.
     */
    private static WorldType world(String density, String extra) {
        WorldgenCompiler compiler =
                new WorldgenFixtures.Text()
                        .add(Kind.PARAMETER, "t:p", "{density:0.0}")
                        .add(Kind.BIOME, "t:plain", "{climate:{p:[D;-1.0,1.0]}}")
                        .add(
                                Kind.WORLD_TYPE,
                                "t:world",
                                "{default_block:\"t:stone\", parameters:[T;\"t:p\"],"
                                        + " biomes:[T;\"t:plain\"], fallback_biome:\"t:plain\","
                                        + " density:"
                                        + density
                                        + extra
                                        + "}")
                        .compileAll();
        return compiler.worldType("t:world", null)
                .orElseThrow(() -> new AssertionError(compiler.getDiagnostics().toString()));
    }

    @Test
    void testDefaultRule() {
        assertEquals(SpawnRule.DEFAULT, world(FLAT, "").spawn());
    }

    @Test
    void testRuleFromData() {
        SpawnRule rule =
                world(
                                FLAT,
                                ", spawn:{anchor:{type:\"floor\", min_air:2},"
                                        + " origin:[I;100,5,-40], radius:256, y_range:[I;-10,50]}")
                        .spawn();
        assertEquals(new SpawnRule(2, 100, 5, -40, 256, -10, 50), rule);
    }

    @Test
    void testFlatWorldSpawnsAtOrigin() {
        WorldType type = world(FLAT, ", spawn:{origin:[I;3,0,-7]}");
        SpawnFinder.Spawn spawn =
                SpawnFinder.find(new WorldGenerator(type, 1), type.spawn(), KINDS);
        assertEquals(new SpawnFinder.Spawn(3, 10, -7, false), spawn);
    }

    @Test
    void testEmptyWorldGetsPlatform() {
        WorldType type =
                world("{type:\"constant\", value:-1.0}", ", spawn:{origin:[I;0,64,0], radius:32}");
        WorldGenerator generator = new WorldGenerator(type, 1);
        SpawnFinder.Spawn spawn = SpawnFinder.find(generator, type.spawn(), KINDS);

        assertEquals(new SpawnFinder.Spawn(0, 64, 0, true), spawn);

        // The platform is a floor of the default block with air above it
        ChunkPos pos = ChunkPos.containing(0, 63, 0);
        assertTrue(SpawnPlatform.touches(pos, spawn));
        Chunk chunk = generator.generate(pos);
        SpawnPlatform.stamp(chunk, pos, spawn, type.defaultBlock());
        assertEquals(type.defaultBlock(), chunk.getBlock(1, 63 & 15, 1));
        assertEquals(World.AIR, chunk.getBlock(0, 64 & 15, 0));
        assertFalse(SpawnPlatform.touches(ChunkPos.containing(64, 64, 64), spawn));
    }

    @Test
    void testFixtureSpawnIsStandable() {
        WorldGenerator generator = WorldgenFixtures.generator(WorldgenFixtures.SEED);
        SpawnRule rule = generator.getWorldType().spawn();
        SpawnFinder.Spawn spawn = SpawnFinder.find(generator, rule, KINDS);

        if (!spawn.platform()) {
            Block floor = blockAt(generator, spawn.x(), spawn.y() - 1, spawn.z());
            assertNotEquals(World.AIR_NAME, floor.getName());
            assertFalse(KINDS.isFluid(floor));
            for (int y = spawn.y(); y < spawn.y() + rule.minAir(); ++y) {
                assertEquals(World.AIR_NAME, blockAt(generator, spawn.x(), y, spawn.z()).getName());
            }
        }
        // The same world always finds the same spawn
        assertEquals(spawn, SpawnFinder.find(generator, rule, KINDS));
    }

    @Test
    void testRingsCoverEachPointOnce() {
        assertEquals(1, SpawnFinder.ring(0).length);
        int[][] ring = SpawnFinder.ring(2);
        assertEquals(16, ring.length);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int[] point : ring) {
            assertEquals(2, Math.max(Math.abs(point[0]), Math.abs(point[1])));
            assertTrue(seen.add(point[0] + "," + point[1]));
        }
    }

    private static Block blockAt(WorldGenerator generator, int x, int y, int z) {
        Chunk chunk = generator.generate(ChunkPos.containing(x, y, z));
        return chunk.getBlock(x & 15, y & 15, z & 15);
    }
}
