package com.ikalagaming.factory.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.kvt.TreeStringSerialization;
import com.ikalagaming.factory.render.looks.WaterLooks;
import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.graphics.scene.WaterBody;
import com.ikalagaming.graphics.scene.WaterTile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tests for turning water blocks into water tiles, and water looks. */
class TestWaterTiles {

    private static final Block STONE = new Block("t:stone", null);
    private static final Block WATER = new Block("t:water", null);
    private static final WaterBody SEA = WaterBody.OCEAN;
    private static final WaterBody LAKE = WaterBody.GREEN;

    /**
     * A small world: stone below y = -4, sea water from -4 up to y = -1 (surface at 0) over the
     * square, except a stone island in one corner; and a pond in the plains biome above it: one
     * water block at (21, 4, 21) on stone.
     *
     * @return The chunks.
     */
    private static Map<ChunkPos, Chunk> world() {
        final Map<ChunkPos, Chunk> chunks = new HashMap<>();
        for (int cx = 0; cx < WaterTiles.CHUNKS; ++cx) {
            for (int cz = 0; cz < WaterTiles.CHUNKS; ++cz) {
                final Chunk below = new Chunk(WATER, "t:ocean");
                for (int x = 0; x < World.CHUNK_SIZE; ++x) {
                    for (int z = 0; z < World.CHUNK_SIZE; ++z) {
                        for (int y = 0; y < 12; ++y) {
                            below.setBlock(x, y, z, STONE);
                        }
                        // The island: the first chunk's 4 x 4 corner rises above the sea
                        if (cx == 0 && cz == 0 && x < 4 && z < 4) {
                            for (int y = 12; y < World.CHUNK_SIZE; ++y) {
                                below.setBlock(x, y, z, STONE);
                            }
                        }
                    }
                }
                chunks.put(new ChunkPos(cx, -1, cz), below);
                final Chunk above = new Chunk(World.AIR, "t:plains");
                if (cx == 1 && cz == 1) {
                    // A floating basin is enough for a second surface
                    above.setBlock(5, 3, 5, STONE);
                    above.setBlock(5, 4, 5, WATER);
                }
                chunks.put(new ChunkPos(cx, 0, cz), above);
                chunks.put(new ChunkPos(cx, 1, cz), new Chunk(World.AIR, "t:plains"));
            }
        }
        return chunks;
    }

    /** The sea and the pond become two tiles, with their surfaces, bottoms, masks and bodies. */
    @Test
    void testSeaAndPond() {
        final Map<ChunkPos, Chunk> chunks = world();
        final List<WaterTile> tiles =
                WaterTiles.build(
                        new WaterTiles.Square(0, 0),
                        chunks::get,
                        new int[] {-1, 0, 1},
                        "t:water"::equals,
                        biome -> "t:ocean".equals(biome) ? SEA : LAKE);
        assertEquals(2, tiles.size());

        final WaterTile sea = tiles.get(0);
        assertEquals(0, sea.origin().y());
        assertEquals(SEA, sea.body());
        assertFalse(sea.isWater(0, 0), "the island");
        assertFalse(sea.isWater(3, 3), "the island");
        assertTrue(sea.isWater(4, 0));
        assertTrue(sea.isWater(63, 63));
        // The floor is the top of the stone at y = -5, 4 m under the surface
        assertEquals(-4, sea.bottomAt(10, 10));
        assertEquals(0, sea.heightAt(10, 10));
        // The basin floats with air under it, so the sea under it still has its surface
        assertTrue(sea.isWater(21, 21));

        final WaterTile pond = tiles.get(1);
        assertEquals(5, pond.origin().y());
        assertEquals(LAKE, pond.body());
        assertTrue(pond.isWater(21, 21));
        assertFalse(pond.isWater(20, 21));
        assertEquals(-1, pond.bottomAt(21, 21));
    }

    /** A square with no water, or no chunks, has no tiles; water under a roof isn't a surface. */
    @Test
    void testNoSurface() {
        final Map<ChunkPos, Chunk> chunks = new HashMap<>();
        final Chunk cave = new Chunk(STONE, "t:cave");
        cave.setBlock(3, 3, 3, WATER);
        chunks.put(new ChunkPos(0, 0, 0), cave);
        assertTrue(
                WaterTiles.build(
                                new WaterTiles.Square(0, 0),
                                chunks::get,
                                new int[] {0},
                                "t:water"::equals,
                                biome -> SEA)
                        .isEmpty());
        assertTrue(
                WaterTiles.build(
                                new WaterTiles.Square(5, 5),
                                chunks::get,
                                new int[] {},
                                "t:water"::equals,
                                biome -> SEA)
                        .isEmpty());
    }

    /** Squares hold 4 x 4 chunks, counting down for negative chunks. */
    @Test
    void testSquares() {
        assertEquals(new WaterTiles.Square(0, 0), WaterTiles.Square.of(new ChunkPos(3, 9, 0)));
        assertEquals(new WaterTiles.Square(-1, 1), WaterTiles.Square.of(new ChunkPos(-1, 0, 4)));
    }

    /**
     * Water looks read real units and the fetch, fall back to the default look, then to a built-in
     * body.
     *
     * @param folder A scratch data folder.
     */
    @Test
    void testLooks(@TempDir Path folder) throws Exception {
        final Path looks = folder.resolve("assets/lotomation/looks/water");
        Files.createDirectories(looks);
        Files.writeString(
                looks.resolve("river.kvt"),
                "/* A muddy river */ {chlorophyll:1.0F, cdom:0.5F, sediment:40.0F, fetch:200.0F}");
        final WaterLooks water = new WaterLooks(List.of(folder));
        final WaterBody river = water.bodyFor("lotomation:river");
        assertEquals(40, river.sediment());
        assertEquals(200, river.fetch());
        assertEquals(WaterLooks.WIND_SPEED, river.windSpeed());
        assertEquals(water.bodyFor("lotomation:unknown"), water.bodyFor("lotomation:other"));
        Files.writeString(looks.resolve("default.kvt"), "{chlorophyll:2.0F}");
        final WaterLooks withDefault = new WaterLooks(List.of(folder));
        assertEquals(2, withDefault.bodyFor("lotomation:plains").chlorophyll());
        assertEquals(Float.POSITIVE_INFINITY, withDefault.bodyFor("lotomation:plains").fetch());
        assertTrue(TreeStringSerialization.fromString("{a:1}").isPresent());
    }
}
