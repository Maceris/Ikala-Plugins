package com.ikalagaming.factory.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.graphics.asset.AssetFile;
import com.ikalagaming.graphics.asset.AssetFormatException;
import com.ikalagaming.graphics.asset.AssetMetadata;
import com.ikalagaming.graphics.asset.AssetWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for section corpus files.
 *
 * @author Ches Burks
 */
class TestSectionCorpus {

    /** Where the water starts in the test chunk. */
    private static final int WATER_HEIGHT = 6;

    /** Where the air starts in the test chunk. */
    private static final int AIR_HEIGHT = 9;

    /** A seed that needs every bit of a long. */
    private static final long SEED = -7_123_456_789_012_345_678L;

    /** A data hash with the top bit set. */
    private static final long DATA_HASH = 0xfedc_ba98_7654_3210L;

    /** What made the test corpus. */
    private static final SectionCorpus.Source SOURCE =
            new SectionCorpus.Source("t:flat", SEED, "BLOCK_RULES", DATA_HASH);

    /**
     * A chunk with stone, then water, then air, a block named outside ASCII, and two biomes.
     *
     * @return The chunk.
     */
    private static Chunk layered() {
        final Block stone = new Block("t:stone", null);
        final Block water = new Block("t:water", null);
        final Block odd = new Block("t:ébène", null);
        Chunk chunk = new Chunk(World.AIR, "t:plain");
        for (int y = 0; y < AIR_HEIGHT; ++y) {
            for (int z = 0; z < World.CHUNK_SIZE; ++z) {
                for (int x = 0; x < World.CHUNK_SIZE; ++x) {
                    chunk.setBlock(x, y, z, y < WATER_HEIGHT ? stone : water);
                }
            }
        }
        chunk.setBlock(3, 2, 1, odd);
        chunk.setBiomeCell(1, 0, 2, "t:lake");
        return chunk;
    }

    /**
     * Chunks read back hash the same as those written, with the same source.
     *
     * @param folder A temporary folder.
     */
    @Test
    void testRoundTrip(@TempDir Path folder) {
        Map<ChunkPos, Chunk> chunks = new LinkedHashMap<>();
        chunks.put(new ChunkPos(-2, 0, 5), layered());
        chunks.put(new ChunkPos(-2, 1, 5), new Chunk(World.AIR, "t:plain"));
        final Path file = folder.resolve("corpus.ika");
        SectionCorpus.write(file, SOURCE, chunks);

        final SectionCorpus.Corpus corpus = SectionCorpus.read(file);
        assertEquals(SOURCE, corpus.source());
        assertEquals(List.copyOf(chunks.keySet()), List.copyOf(corpus.chunks().keySet()));
        chunks.forEach(
                (pos, chunk) ->
                        assertEquals(
                                chunk.contentHash(),
                                corpus.chunks().get(pos).contentHash(),
                                pos.toString()));
    }

    /**
     * The asset container reader accepts a corpus with nothing to warn about.
     *
     * @param folder A temporary folder.
     */
    @Test
    void testValidates(@TempDir Path folder) {
        final Path file = folder.resolve("corpus.ika");
        SectionCorpus.write(file, SOURCE, Map.of(new ChunkPos(0, 0, 0), layered()));
        final AssetFile asset = AssetFile.open(file, SectionCorpus.known());
        assertTrue(asset.warnings().isEmpty(), asset.warnings().toString());
        assertEquals(SectionCorpus.EXPORTER, asset.metadata().exporter());
    }

    /**
     * A chunk section of a newer version is rejected.
     *
     * @param folder A temporary folder.
     */
    @Test
    void testRejectsNewerVersion(@TempDir Path folder) {
        final Path file = folder.resolve("corpus.ika");
        new AssetWriter()
                .metadata(
                        AssetMetadata.of(SectionCorpus.EXPORTER, "2")
                                .with(SectionCorpus.WORLD_TYPE, SOURCE.worldType())
                                .with(SectionCorpus.SEED, Long.toString(SEED))
                                .with(SectionCorpus.STAGE, SOURCE.stage())
                                .with(SectionCorpus.DATA_HASH, Long.toHexString(DATA_HASH)))
                .section(
                        SectionCorpus.FSEC,
                        SectionCorpus.VERSION + 1,
                        true,
                        SectionCorpus.encode(new ChunkPos(0, 0, 0), layered()))
                .write(file);
        assertThrows(AssetFormatException.class, () -> SectionCorpus.read(file));
    }
}
