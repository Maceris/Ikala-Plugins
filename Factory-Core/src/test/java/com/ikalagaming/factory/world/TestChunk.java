package com.ikalagaming.factory.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for cubic chunks.
 *
 * @author Ches Burks
 */
class TestChunk {

    private static final Block STONE = new Block("test:stone", null);

    private static final Block AIR = new Block("test:air", null);

    private static final Block DIRT = new Block("test:dirt", null);

    /** Blocks read back as they were set, at every corner. */
    @Test
    void testBlocksRoundTrip() {
        Chunk chunk = new Chunk(AIR, "test:plains");
        final int last = World.CHUNK_SIZE - 1;
        chunk.setBlock(0, 0, 0, STONE);
        chunk.setBlock(last, 0, 0, DIRT);
        chunk.setBlock(0, last, 0, STONE);
        chunk.setBlock(last, last, last, DIRT);
        assertEquals(STONE, chunk.getBlock(0, 0, 0));
        assertEquals(DIRT, chunk.getBlock(last, 0, 0));
        assertEquals(STONE, chunk.getBlock(0, last, 0));
        assertEquals(DIRT, chunk.getBlock(last, last, last));
        assertEquals(AIR, chunk.getBlock(1, 1, 1));
    }

    /** A filled chunk is uniform until something differs. */
    @Test
    void testUniform() {
        Chunk chunk = new Chunk(STONE, "test:plains");
        assertTrue(chunk.isUniform());
        chunk.setBlock(3, 4, 5, AIR);
        assertFalse(chunk.isUniform());
        chunk.setBlock(3, 4, 5, STONE);
        assertTrue(chunk.isUniform());
    }

    /** Biomes are per 4^3 cell. */
    @Test
    void testBiomeCells() {
        Chunk chunk = new Chunk(STONE, "test:plains");
        chunk.setBiomeCell(1, 2, 3, "test:desert");
        assertEquals("test:desert", chunk.getBiome(4, 8, 12));
        assertEquals("test:desert", chunk.getBiome(7, 11, 15));
        assertEquals("test:plains", chunk.getBiome(3, 8, 12));
    }

    /** The hash is about content, not the order the palette was filled in. */
    @Test
    void testHashIgnoresPaletteOrder() {
        Chunk first = new Chunk(AIR, "test:plains");
        first.setBlock(1, 1, 1, STONE);
        first.setBlock(2, 2, 2, DIRT);
        Chunk second = new Chunk(DIRT, "test:plains");
        second.setBlock(1, 1, 1, STONE);
        for (int y = 0; y < World.CHUNK_SIZE; ++y) {
            for (int z = 0; z < World.CHUNK_SIZE; ++z) {
                for (int x = 0; x < World.CHUNK_SIZE; ++x) {
                    if (!(x == 1 && y == 1 && z == 1) && !(x == 2 && y == 2 && z == 2)) {
                        second.setBlock(x, y, z, AIR);
                    }
                }
            }
        }
        assertEquals(first.contentHash(), second.contentHash());
    }

    /** Any change to a block or a biome changes the hash. */
    @Test
    void testHashSeesChanges() {
        Chunk chunk = new Chunk(STONE, "test:plains");
        final long original = chunk.contentHash();
        chunk.setBlock(15, 15, 15, AIR);
        final long changedBlock = chunk.contentHash();
        assertNotEquals(original, changedBlock);
        chunk.setBiomeCell(0, 0, 0, "test:desert");
        assertNotEquals(changedBlock, chunk.contentHash());
    }

    /** Chunk positions convert to and from block coordinates, past the int range too. */
    @Test
    void testChunkPositions() {
        assertEquals(new ChunkPos(-1, 0, 1), ChunkPos.containing(-1, 15, 16));
        ChunkPos far = new ChunkPos(Integer.MAX_VALUE, Integer.MIN_VALUE, 0);
        assertEquals((long) Integer.MAX_VALUE * World.CHUNK_SIZE, far.blockX());
        assertEquals((long) Integer.MIN_VALUE * World.CHUNK_SIZE, far.blockY());
        assertEquals(far, ChunkPos.containing(far.blockX(), far.blockY(), far.blockZ()));
    }
}
