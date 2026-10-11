package com.ikalagaming.factory.lab;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.graphics.asset.AssetFile;
import com.ikalagaming.graphics.asset.AssetMetadata;
import com.ikalagaming.graphics.asset.AssetWriter;
import com.ikalagaming.graphics.asset.KnownSections;
import com.ikalagaming.graphics.asset.Section;
import com.ikalagaming.graphics.asset.SectionTag;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * A section corpus: generated chunks saved in an asset container, for testing what consumes
 * sections, like level of detail, against real worlds instead of synthetic ones.
 *
 * <p>Each chunk is one {@code FSEC} section, version {@value #VERSION}, little-endian:
 *
 * <ol>
 *   <li>the chunk's position, 3 ints;
 *   <li>the block palette: a count, then each block's name as an int length and UTF-8 bytes;
 *   <li>{@value World#CHUNK_VOLUME} shorts of block palette indices, y slowest, then z, then x;
 *   <li>the biome palette, in the same form;
 *   <li>{@value Chunk#BIOME_CELL_COUNT} shorts of biome palette indices, one per cell, in the same
 *       order.
 * </ol>
 *
 * <p>Palettes are in order of first appearance, so the same chunk always writes the same bytes. The
 * metadata says what made the chunks: world type, seed, stage and the data's content hash.
 * Rotations and block data come in a later version, when blocks have them.
 */
public final class SectionCorpus {

    /** The section tag of one chunk. */
    public static final int FSEC = SectionTag.of("FSEC");

    /** The layout version this writes and reads. */
    public static final int VERSION = 1;

    /** Who writes corpora. */
    public static final String EXPORTER = "Factory-Client Worldgen Lab";

    /** The metadata key for the world type. */
    public static final String WORLD_TYPE = "worldgen.world-type";

    /** The metadata key for the seed. */
    public static final String SEED = "worldgen.seed";

    /** The metadata key for the last generation stage. */
    public static final String STAGE = "worldgen.stage";

    /** The metadata key for the data's content hash, in hex. */
    public static final String DATA_HASH = "worldgen.data-hash";

    /** Bytes in an int. */
    private static final int INT_BYTES = Integer.BYTES;

    /** Bytes in a short. */
    private static final int SHORT_BYTES = Short.BYTES;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /**
     * What the corpus was made from.
     *
     * @param worldType The world type's ID.
     * @param seed The seed.
     * @param stage The last stage generated, by name.
     * @param dataHash The data's content hash.
     */
    public record Source(
            @NonNull String worldType, long seed, @NonNull String stage, long dataHash) {}

    /**
     * A corpus read back.
     *
     * @param source What made it.
     * @param chunks The chunks, in the order written.
     */
    public record Corpus(@NonNull Source source, @NonNull Map<ChunkPos, Chunk> chunks) {}

    /** The sections a corpus may hold, for reading and validating. */
    public static KnownSections known() {
        return KnownSections.graphics().with(FSEC, VERSION);
    }

    /**
     * Write chunks to a corpus file.
     *
     * @param file Where to write.
     * @param source What made them.
     * @param chunks The chunks, written in iteration order.
     */
    public static void write(
            @NonNull Path file, @NonNull Source source, @NonNull Map<ChunkPos, Chunk> chunks) {
        AssetWriter writer =
                new AssetWriter()
                        .metadata(
                                AssetMetadata.of(EXPORTER, Integer.toString(VERSION))
                                        .with(WORLD_TYPE, source.worldType())
                                        .with(SEED, Long.toString(source.seed()))
                                        .with(STAGE, source.stage())
                                        .with(DATA_HASH, Long.toHexString(source.dataHash())));
        chunks.forEach((pos, chunk) -> writer.section(FSEC, VERSION, true, encode(pos, chunk)));
        writer.write(file);
    }

    /**
     * Read a corpus file.
     *
     * @param file The file.
     * @return The corpus.
     * @throws com.ikalagaming.graphics.asset.AssetFormatException If the container is broken, or
     *     holds a chunk version this doesn't read.
     * @throws IllegalArgumentException If a chunk's contents or the metadata are invalid.
     */
    public static Corpus read(@NonNull Path file) {
        final AssetFile asset = AssetFile.open(file, known());
        final AssetMetadata metadata = asset.metadata();
        final Source source =
                new Source(
                        require(metadata, WORLD_TYPE),
                        Long.parseLong(require(metadata, SEED)),
                        require(metadata, STAGE),
                        Long.parseUnsignedLong(require(metadata, DATA_HASH), 16));
        Map<ChunkPos, Chunk> chunks = new LinkedHashMap<>();
        for (Section section : asset.sections(FSEC)) {
            if (section.version() != VERSION) {
                throw new IllegalArgumentException(
                        "Corpus chunk version " + section.version() + " isn't " + VERSION);
            }
            final ByteBuffer data = section.data();
            final ChunkPos pos = new ChunkPos(data.getInt(), data.getInt(), data.getInt());
            chunks.put(pos, decode(data));
        }
        return new Corpus(source, chunks);
    }

    /**
     * A metadata value that must be there.
     *
     * @param metadata The metadata.
     * @param key The key.
     * @return The value.
     * @throws IllegalArgumentException If it is missing.
     */
    private static String require(@NonNull AssetMetadata metadata, @NonNull String key) {
        final String value = metadata.properties().get(key);
        if (value == null) {
            throw new IllegalArgumentException("Corpus metadata is missing " + key);
        }
        return value;
    }

    /**
     * Lay out one chunk.
     *
     * @param pos Where it is.
     * @param chunk The chunk.
     * @return The section's payload.
     */
    static ByteBuffer encode(@NonNull ChunkPos pos, @NonNull Chunk chunk) {
        List<String> blocks = new ArrayList<>();
        Map<String, Short> blockIndex = new HashMap<>();
        short[] blockIndices = new short[World.CHUNK_VOLUME];
        List<String> biomes = new ArrayList<>();
        Map<String, Short> biomeIndex = new HashMap<>();
        short[] biomeIndices = new short[Chunk.BIOME_CELL_COUNT];
        for (int y = 0; y < World.CHUNK_SIZE; ++y) {
            for (int z = 0; z < World.CHUNK_SIZE; ++z) {
                for (int x = 0; x < World.CHUNK_SIZE; ++x) {
                    blockIndices[Chunk.index(x, y, z)] =
                            indexOf(chunk.getBlock(x, y, z).getName(), blocks, blockIndex);
                }
            }
        }
        for (int y = 0; y < Chunk.BIOME_CELLS; ++y) {
            for (int z = 0; z < Chunk.BIOME_CELLS; ++z) {
                for (int x = 0; x < Chunk.BIOME_CELLS; ++x) {
                    final String biome =
                            chunk.getBiome(
                                    x * Chunk.BIOME_CELL_SIZE,
                                    y * Chunk.BIOME_CELL_SIZE,
                                    z * Chunk.BIOME_CELL_SIZE);
                    biomeIndices[Chunk.biomeCellIndex(x, y, z)] =
                            indexOf(biome, biomes, biomeIndex);
                }
            }
        }
        final int size =
                AXES * INT_BYTES
                        + paletteBytes(blocks)
                        + blockIndices.length * SHORT_BYTES
                        + paletteBytes(biomes)
                        + biomeIndices.length * SHORT_BYTES;
        ByteBuffer data = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(pos.x()).putInt(pos.y()).putInt(pos.z());
        putPalette(data, blocks);
        for (short index : blockIndices) {
            data.putShort(index);
        }
        putPalette(data, biomes);
        for (short index : biomeIndices) {
            data.putShort(index);
        }
        return data.flip();
    }

    /**
     * Read one chunk's palettes and indices, after its position.
     *
     * @param data The payload, positioned after the position.
     * @return The chunk.
     */
    private static Chunk decode(@NonNull ByteBuffer data) {
        final List<String> blocks = getPalette(data);
        short[] blockIndices = new short[World.CHUNK_VOLUME];
        for (int i = 0; i < blockIndices.length; ++i) {
            blockIndices[i] = checked(data.getShort(), blocks);
        }
        final List<String> biomes = getPalette(data);
        short[] biomeIndices = new short[Chunk.BIOME_CELL_COUNT];
        for (int i = 0; i < biomeIndices.length; ++i) {
            biomeIndices[i] = checked(data.getShort(), biomes);
        }
        Block[] palette = new Block[blocks.size()];
        for (int i = 0; i < palette.length; ++i) {
            palette[i] = new Block(blocks.get(i), null);
        }
        Chunk chunk = new Chunk(palette[blockIndices[0]], biomes.get(biomeIndices[0]));
        for (int y = 0; y < World.CHUNK_SIZE; ++y) {
            for (int z = 0; z < World.CHUNK_SIZE; ++z) {
                for (int x = 0; x < World.CHUNK_SIZE; ++x) {
                    chunk.setBlock(x, y, z, palette[blockIndices[Chunk.index(x, y, z)]]);
                }
            }
        }
        for (int y = 0; y < Chunk.BIOME_CELLS; ++y) {
            for (int z = 0; z < Chunk.BIOME_CELLS; ++z) {
                for (int x = 0; x < Chunk.BIOME_CELLS; ++x) {
                    chunk.setBiomeCell(
                            x, y, z, biomes.get(biomeIndices[Chunk.biomeCellIndex(x, y, z)]));
                }
            }
        }
        return chunk;
    }

    /**
     * Check a palette index is in range.
     *
     * @param index The index.
     * @param palette The palette it indexes.
     * @return The index.
     * @throws IllegalArgumentException If it is out of range.
     */
    private static short checked(short index, @NonNull List<String> palette) {
        if (index < 0 || index >= palette.size()) {
            throw new IllegalArgumentException("Corpus palette index " + index + " is invalid");
        }
        return index;
    }

    /**
     * Find a name in a palette, adding it if it isn't there.
     *
     * @param name The name.
     * @param palette The palette, in order of first appearance.
     * @param index Where each name is in the palette.
     * @return Its index.
     */
    private static short indexOf(
            @NonNull String name,
            @NonNull List<String> palette,
            @NonNull Map<String, Short> index) {
        return index.computeIfAbsent(
                name,
                key -> {
                    palette.add(key);
                    return (short) (palette.size() - 1);
                });
    }

    /**
     * How many bytes a palette takes.
     *
     * @param palette The names.
     * @return The size.
     */
    private static int paletteBytes(@NonNull List<String> palette) {
        int size = INT_BYTES;
        for (String name : palette) {
            size += INT_BYTES + name.getBytes(StandardCharsets.UTF_8).length;
        }
        return size;
    }

    /**
     * Write a palette.
     *
     * @param data Where to write.
     * @param palette The names.
     */
    private static void putPalette(@NonNull ByteBuffer data, @NonNull List<String> palette) {
        data.putInt(palette.size());
        for (String name : palette) {
            final byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
            data.putInt(bytes.length).put(bytes);
        }
    }

    /**
     * Read a palette.
     *
     * @param data Where to read.
     * @return The names.
     */
    private static List<String> getPalette(@NonNull ByteBuffer data) {
        final int count = data.getInt();
        if (count <= 0 || count > data.remaining()) {
            throw new IllegalArgumentException("Corpus palette count " + count + " is invalid");
        }
        List<String> palette = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            final int length = data.getInt();
            if (length < 0 || length > data.remaining()) {
                throw new IllegalArgumentException("Corpus name length " + length + " is invalid");
            }
            byte[] bytes = new byte[length];
            data.get(bytes);
            palette.add(new String(bytes, StandardCharsets.UTF_8));
        }
        return palette;
    }

    /** Static helpers only. */
    private SectionCorpus() {}
}
