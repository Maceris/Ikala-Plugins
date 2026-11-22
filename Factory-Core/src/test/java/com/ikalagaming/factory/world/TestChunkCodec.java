package com.ikalagaming.factory.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.TreeBinarySerialization;
import com.ikalagaming.factory.networking.request.clientbound.ChunkData;
import com.ikalagaming.factory.networking.serialization.TreeRequestSerialization;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** Tests for turning chunks into nodes and back. */
class TestChunkCodec {

    private static final Block STONE = new Block("t:stone", null);
    private static final Block DIRT = new Block("t:dirt", null);

    /** A chunk with several blocks, one with state, and two biomes. */
    private static Chunk mixed() {
        Chunk chunk = new Chunk(World.AIR, "t:plains");
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                for (int y = 0; y < 4; ++y) {
                    chunk.setBlock(x, y, z, (x + z) % 3 == 0 ? DIRT : STONE);
                }
            }
        }
        Node state = new Node();
        state.add("facing", "north");
        chunk.setBlock(5, 9, 7, new Block("t:stairs", state));
        chunk.setBiomeCell(3, 3, 3, "t:desert");
        return chunk;
    }

    private static void assertSameChunk(Chunk expected, Chunk actual) {
        for (int y = 0; y < 16; ++y) {
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 16; ++x) {
                    assertEquals(expected.getBlock(x, y, z), actual.getBlock(x, y, z));
                    assertEquals(expected.getBiome(x, y, z), actual.getBiome(x, y, z));
                }
            }
        }
    }

    @Test
    void testRoundTrip() {
        Chunk chunk = mixed();
        assertSameChunk(chunk, ChunkCodec.decode(ChunkCodec.encode(chunk)));
    }

    @Test
    void testRoundTripThroughBinary() {
        Chunk chunk = mixed();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(TreeBinarySerialization.write(ChunkCodec.encode(chunk), out));
        Node read =
                TreeBinarySerialization.read(new ByteArrayInputStream(out.toByteArray()))
                        .orElseThrow();
        assertSameChunk(chunk, ChunkCodec.decode(read));
    }

    @Test
    void testUniformChunkHasNoArrays() {
        Node node = ChunkCodec.encode(new Chunk(STONE, "t:plains"));
        assertFalse(node.hasChild(ChunkCodec.KEY_BLOCKS));
        Node biomes = node.get(ChunkCodec.KEY_BIOMES);
        assertFalse(biomes.hasChild(ChunkCodec.KEY_CELLS));
        assertTrue(ChunkCodec.decode(node).isUniform());
    }

    @Test
    void testOnlyUsedBlocksInPalette() {
        Chunk chunk = new Chunk(STONE, "t:plains");
        chunk.setBlock(0, 0, 0, DIRT);
        chunk.setBlock(0, 0, 0, STONE);
        List<?> palette = ChunkCodec.encode(chunk).get(ChunkCodec.KEY_PALETTE);
        assertEquals(1, palette.size());
    }

    @Test
    void testPacking() {
        assertEquals(1, ChunkCodec.bitsFor(2));
        assertEquals(2, ChunkCodec.bitsFor(3));
        assertEquals(4, ChunkCodec.bitsFor(16));
        assertEquals(5, ChunkCodec.bitsFor(17));
        int[] values = new int[100];
        for (int i = 0; i < values.length; ++i) {
            values[i] = i % 17;
        }
        List<Long> packed = ChunkCodec.pack(values, 17);
        // 5 bits each, 12 to a long, none straddling two longs
        assertEquals(9, packed.size());
        long[] raw = packed.stream().mapToLong(Long::longValue).toArray();
        assertEquals(
                List.of(0, 1, 16, 0),
                List.of(
                        ChunkCodec.unpack(raw, 17, 100)[0],
                        ChunkCodec.unpack(raw, 17, 100)[1],
                        ChunkCodec.unpack(raw, 17, 100)[16],
                        ChunkCodec.unpack(raw, 17, 100)[17]));
    }

    @Test
    void testBadNodes() {
        assertThrows(IllegalArgumentException.class, () -> ChunkCodec.decode(new Node()));
        Node node = ChunkCodec.encode(mixed());
        node.add(ChunkCodec.KEY_VERSION, ChunkCodec.FORMAT_VERSION + 1);
        assertThrows(IllegalArgumentException.class, () -> ChunkCodec.decode(node));
    }

    @Test
    void testChunkDataSerializes() {
        Chunk chunk = mixed();
        ChunkData request = new ChunkData(1, -2, 3, ChunkCodec.encode(chunk));
        Node tree = TreeRequestSerialization.fromObject(request).orElseThrow();
        ChunkData back = TreeRequestSerialization.toObject(tree, ChunkData.class).orElseThrow();
        assertEquals(1, back.getX());
        assertEquals(-2, back.getY());
        assertEquals(3, back.getZ());
        assertSameChunk(chunk, ChunkCodec.decode(back.getChunk()));
    }
}
