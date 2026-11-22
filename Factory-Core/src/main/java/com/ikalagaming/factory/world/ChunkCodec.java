package com.ikalagaming.factory.world;

import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.NodeType;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a chunk into a KVT node and back, in the save format's chunk layout (Save File Format,
 * Chunk Contents): a name palette, palette indices packed into longs, and biome cells packed the
 * same way. The same node is what the server sends clients.
 *
 * <p>Palette indices use {@code max(1, ceil(log2(palette size)))} bits each, packed from the least
 * significant bit of each long, never straddling two longs. A chunk of one block has no {@code
 * blocks} array, and one of one biome no {@code cells}. Block index order is {@code x + 16 * (z +
 * 16 * y)}, biome cell order {@code x + 4 * (z + 4 * y)}.
 */
public final class ChunkCodec {

    /** The chunk format version this writes. */
    public static final int FORMAT_VERSION = 1;

    /** Keys in a chunk node. */
    public static final String KEY_VERSION = "version";

    public static final String KEY_PALETTE = "palette";
    public static final String KEY_BLOCKS = "blocks";
    public static final String KEY_BIOMES = "biomes";
    public static final String KEY_CELLS = "cells";
    public static final String KEY_NAME = "name";
    public static final String KEY_STATE = "state";

    /**
     * Encode a chunk. Only blocks and biomes the chunk holds are in its palettes.
     *
     * @param chunk The chunk.
     * @return A new node, which shares nothing with the chunk.
     */
    public static Node encode(@NonNull Chunk chunk) {
        final int size = World.CHUNK_SIZE;
        final List<Block> palette = new ArrayList<>();
        final Map<Block, Integer> paletteIndex = new HashMap<>();
        final int[] indices = new int[World.CHUNK_VOLUME];
        for (int y = 0; y < size; ++y) {
            for (int z = 0; z < size; ++z) {
                for (int x = 0; x < size; ++x) {
                    final Block block = chunk.getBlock(x, y, z);
                    indices[Chunk.index(x, y, z)] =
                            paletteIndex.computeIfAbsent(
                                    block,
                                    key -> {
                                        palette.add(key);
                                        return palette.size() - 1;
                                    });
                }
            }
        }

        final List<String> biomePalette = new ArrayList<>();
        final Map<String, Integer> biomeIndex = new HashMap<>();
        final int[] cells = new int[Chunk.BIOME_CELL_COUNT];
        final int cellSize = Chunk.BIOME_CELL_SIZE;
        for (int y = 0; y < Chunk.BIOME_CELLS; ++y) {
            for (int z = 0; z < Chunk.BIOME_CELLS; ++z) {
                for (int x = 0; x < Chunk.BIOME_CELLS; ++x) {
                    final String biome = chunk.getBiome(x * cellSize, y * cellSize, z * cellSize);
                    cells[Chunk.biomeCellIndex(x, y, z)] =
                            biomeIndex.computeIfAbsent(
                                    biome,
                                    key -> {
                                        biomePalette.add(key);
                                        return biomePalette.size() - 1;
                                    });
                }
            }
        }

        final Node node = new Node();
        node.add(KEY_VERSION, FORMAT_VERSION);
        final List<Node> paletteNodes = new ArrayList<>(palette.size());
        for (Block block : palette) {
            final Node entry = new Node();
            entry.add(KEY_NAME, block.getName());
            if (block.getData() instanceof Node state) {
                entry.add(KEY_STATE, NodeType.NODE, state);
            }
            paletteNodes.add(entry);
        }
        node.add(KEY_PALETTE, NodeType.NODE_ARRAY, paletteNodes);
        if (palette.size() > 1) {
            node.add(KEY_BLOCKS, NodeType.LONG_ARRAY, pack(indices, palette.size()));
        }
        final Node biomes = new Node();
        biomes.add(KEY_PALETTE, NodeType.STRING_ARRAY, biomePalette);
        if (biomePalette.size() > 1) {
            biomes.add(KEY_CELLS, NodeType.LONG_ARRAY, pack(cells, biomePalette.size()));
        }
        node.add(KEY_BIOMES, NodeType.NODE, biomes);
        return node;
    }

    /**
     * Decode a chunk. Blocks whose names aren't known are kept by name, so they come back as they
     * were.
     *
     * @param node A node from {@link #encode(Chunk)} or a save.
     * @return A new chunk.
     * @throws IllegalArgumentException If the node isn't a chunk this can read.
     */
    public static Chunk decode(@NonNull Node node) {
        final Object version = node.hasChild(KEY_VERSION) ? node.get(KEY_VERSION) : null;
        if (!(version instanceof Number number) || number.intValue() > FORMAT_VERSION) {
            throw new IllegalArgumentException("Not a chunk this can read: version " + version);
        }
        final List<Block> palette = new ArrayList<>();
        if (node.get(KEY_PALETTE) instanceof List<?> entries) {
            for (Object entry : entries) {
                if (!(entry instanceof Node blockNode)
                        || !(blockNode.get(KEY_NAME) instanceof String name)) {
                    throw new IllegalArgumentException("A palette entry has no name");
                }
                final KVT state = blockNode.hasChild(KEY_STATE) ? blockNode.get(KEY_STATE) : null;
                palette.add(new Block(name, state));
            }
        }
        if (palette.isEmpty()) {
            throw new IllegalArgumentException("The chunk's palette is empty");
        }
        final Node biomes = node.hasChild(KEY_BIOMES) ? node.get(KEY_BIOMES) : null;
        final List<String> biomePalette = new ArrayList<>();
        if (biomes != null && biomes.get(KEY_PALETTE) instanceof List<?> names) {
            names.forEach(name -> biomePalette.add(String.valueOf(name)));
        }
        if (biomePalette.isEmpty()) {
            throw new IllegalArgumentException("The chunk's biome palette is empty");
        }

        final Chunk chunk = new Chunk(palette.getFirst(), biomePalette.getFirst());
        if (palette.size() > 1) {
            final int[] indices =
                    unpack(longs(node.get(KEY_BLOCKS)), palette.size(), World.CHUNK_VOLUME);
            final int size = World.CHUNK_SIZE;
            for (int y = 0; y < size; ++y) {
                for (int z = 0; z < size; ++z) {
                    for (int x = 0; x < size; ++x) {
                        final int index = indices[Chunk.index(x, y, z)];
                        if (index >= palette.size()) {
                            throw new IllegalArgumentException("A block index is past the palette");
                        }
                        chunk.setBlock(x, y, z, palette.get(index));
                    }
                }
            }
        }
        if (biomePalette.size() > 1) {
            final int[] cells =
                    unpack(
                            longs(biomes.get(KEY_CELLS)),
                            biomePalette.size(),
                            Chunk.BIOME_CELL_COUNT);
            for (int y = 0; y < Chunk.BIOME_CELLS; ++y) {
                for (int z = 0; z < Chunk.BIOME_CELLS; ++z) {
                    for (int x = 0; x < Chunk.BIOME_CELLS; ++x) {
                        final int index = cells[Chunk.biomeCellIndex(x, y, z)];
                        if (index >= biomePalette.size()) {
                            throw new IllegalArgumentException("A biome index is past the palette");
                        }
                        chunk.setBiomeCell(x, y, z, biomePalette.get(index));
                    }
                }
            }
        }
        return chunk;
    }

    /**
     * How many bits each index needs.
     *
     * @param paletteSize How many entries the palette has.
     * @return The bits, at least 1.
     */
    static int bitsFor(int paletteSize) {
        return Math.max(1, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
    }

    /**
     * Pack indices into longs.
     *
     * @param indices The indices.
     * @param paletteSize How many entries the palette has, which sets the bits per index.
     * @return The packed longs.
     */
    static List<Long> pack(int @NonNull [] indices, int paletteSize) {
        final int bits = bitsFor(paletteSize);
        final int perLong = Long.SIZE / bits;
        final long[] packed = new long[(indices.length + perLong - 1) / perLong];
        for (int i = 0; i < indices.length; ++i) {
            packed[i / perLong] |= (long) indices[i] << ((i % perLong) * bits);
        }
        final List<Long> result = new ArrayList<>(packed.length);
        for (long value : packed) {
            result.add(value);
        }
        return result;
    }

    /**
     * Unpack indices from longs.
     *
     * @param packed The packed longs.
     * @param paletteSize How many entries the palette has, which sets the bits per index.
     * @param count How many indices there are.
     * @return The indices.
     * @throws IllegalArgumentException If there are too few longs.
     */
    static int[] unpack(long @NonNull [] packed, int paletteSize, int count) {
        final int bits = bitsFor(paletteSize);
        final int perLong = Long.SIZE / bits;
        if (packed.length < (count + perLong - 1) / perLong) {
            throw new IllegalArgumentException("Too few packed values");
        }
        final long mask = (1L << bits) - 1;
        final int[] indices = new int[count];
        for (int i = 0; i < count; ++i) {
            indices[i] = (int) ((packed[i / perLong] >>> ((i % perLong) * bits)) & mask);
        }
        return indices;
    }

    /**
     * A long array value.
     *
     * @param value The value from a node.
     * @return The longs.
     * @throws IllegalArgumentException If it isn't a list of numbers.
     */
    private static long[] longs(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected packed indices");
        }
        final long[] result = new long[list.size()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = ((Number) list.get(i)).longValue();
        }
        return result;
    }

    /** Private constructor so this class is not instantiated. */
    private ChunkCodec() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
