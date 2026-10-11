package com.ikalagaming.factory.world;

import com.ikalagaming.factory.world.gen.WorldgenHash;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A cube of blocks, {@value World#CHUNK_SIZE} on every side, and the biome of each {@value
 * #BIOME_CELL_SIZE}^3 cell in it.
 *
 * <p>Blocks are stored as indices into a palette of the distinct blocks, so a chunk of one block
 * holds a single palette entry. Biomes are stored the same way, as IDs. Indices are shorts, so
 * neither is capped at 256 entries.
 *
 * <p>Within a chunk, coordinates run from 0 to {@value World#CHUNK_SIZE} - 1, and the layout is y
 * slowest, then z, then x.
 *
 * @author Ches Burks
 */
public class Chunk {
    /** How many blocks along each side of a biome cell. */
    public static final int BIOME_CELL_SIZE = 4;

    /** How many biome cells along each side of a chunk. */
    public static final int BIOME_CELLS = World.CHUNK_SIZE / BIOME_CELL_SIZE;

    /** How many biome cells a chunk has. */
    public static final int BIOME_CELL_COUNT = BIOME_CELLS * BIOME_CELLS * BIOME_CELLS;

    /** The distinct blocks, indexed by {@link #blocks}. */
    private final List<Block> palette = new ArrayList<>();

    /** Where each block is in the palette, for setting blocks. Never iterated. */
    private final Map<Block, Short> paletteIndex = new HashMap<>();

    /** The palette index of each block. */
    private final short[] blocks = new short[World.CHUNK_VOLUME];

    /** The distinct biome IDs, indexed by {@link #biomes}. */
    private final List<String> biomePalette = new ArrayList<>();

    /** Where each biome is in the biome palette. Never iterated. */
    private final Map<String, Short> biomeIndex = new HashMap<>();

    /** The biome palette index of each cell. */
    private final short[] biomes = new short[BIOME_CELL_COUNT];

    /**
     * A chunk filled with one block, every cell one biome.
     *
     * @param fill The block everywhere.
     * @param biome The biome ID everywhere.
     */
    public Chunk(@NonNull Block fill, @NonNull String biome) {
        indexOf(fill);
        biomeIndexOf(biome);
    }

    /**
     * The index of a block within the chunk's block array.
     *
     * @param x The x coordinate within the chunk.
     * @param y The y coordinate within the chunk.
     * @param z The z coordinate within the chunk.
     * @return The index.
     */
    public static int index(int x, int y, int z) {
        return x + World.CHUNK_SIZE * (z + World.CHUNK_SIZE * y);
    }

    /**
     * The index of a biome cell.
     *
     * @param cellX The cell's x, from 0 to {@value #BIOME_CELLS} - 1.
     * @param cellY The cell's y.
     * @param cellZ The cell's z.
     * @return The index.
     */
    public static int biomeCellIndex(int cellX, int cellY, int cellZ) {
        return cellX + BIOME_CELLS * (cellZ + BIOME_CELLS * cellY);
    }

    /**
     * The block at a position.
     *
     * @param x The x coordinate within the chunk.
     * @param y The y coordinate within the chunk.
     * @param z The z coordinate within the chunk.
     * @return The block.
     */
    public Block getBlock(int x, int y, int z) {
        return palette.get(blocks[index(x, y, z)]);
    }

    /**
     * Set the block at a position.
     *
     * @param x The x coordinate within the chunk.
     * @param y The y coordinate within the chunk.
     * @param z The z coordinate within the chunk.
     * @param block The block.
     */
    public void setBlock(int x, int y, int z, @NonNull Block block) {
        blocks[index(x, y, z)] = indexOf(block);
    }

    /**
     * The biome of the cell a block is in.
     *
     * @param x The x coordinate within the chunk.
     * @param y The y coordinate within the chunk.
     * @param z The z coordinate within the chunk.
     * @return The biome ID.
     */
    public String getBiome(int x, int y, int z) {
        return biomePalette.get(
                biomes[
                        biomeCellIndex(
                                x / BIOME_CELL_SIZE, y / BIOME_CELL_SIZE, z / BIOME_CELL_SIZE)]);
    }

    /**
     * Set the biome of a cell.
     *
     * @param cellX The cell's x, from 0 to {@value #BIOME_CELLS} - 1.
     * @param cellY The cell's y.
     * @param cellZ The cell's z.
     * @param biome The biome ID.
     */
    public void setBiomeCell(int cellX, int cellY, int cellZ, @NonNull String biome) {
        biomes[biomeCellIndex(cellX, cellY, cellZ)] = biomeIndexOf(biome);
    }

    /**
     * Whether every block in the chunk is the same.
     *
     * @return True if the chunk is all one block.
     */
    public boolean isUniform() {
        final short first = blocks[0];
        for (short block : blocks) {
            if (block != first) {
                return false;
            }
        }
        return true;
    }

    /**
     * A hash of what is in the chunk: every block's name and every cell's biome, in layout order.
     * It doesn't depend on the order the palettes were filled in, so two chunks with the same
     * content always hash the same, and it is stable across runs, machines and JVMs.
     *
     * @return The 64-bit hash.
     */
    public long contentHash() {
        final long[] blockHashes = new long[palette.size()];
        for (int i = 0; i < blockHashes.length; ++i) {
            blockHashes[i] = WorldgenHash.fnv1a64(palette.get(i).getName());
        }
        final long[] biomeHashes = new long[biomePalette.size()];
        for (int i = 0; i < biomeHashes.length; ++i) {
            biomeHashes[i] = WorldgenHash.fnv1a64(biomePalette.get(i));
        }
        long hash = WorldgenHash.FNV_OFFSET;
        for (short block : blocks) {
            hash = WorldgenHash.fnvAdd(hash, blockHashes[block]);
        }
        for (short biome : biomes) {
            hash = WorldgenHash.fnvAdd(hash, biomeHashes[biome]);
        }
        return hash;
    }

    /**
     * Find a block in the palette, adding it if it isn't there.
     *
     * @param block The block.
     * @return Its palette index.
     */
    private short indexOf(@NonNull Block block) {
        return paletteIndex.computeIfAbsent(
                block,
                key -> {
                    palette.add(key);
                    return (short) (palette.size() - 1);
                });
    }

    /**
     * Find a biome in the biome palette, adding it if it isn't there.
     *
     * @param biome The biome ID.
     * @return Its palette index.
     */
    private short biomeIndexOf(@NonNull String biome) {
        return biomeIndex.computeIfAbsent(
                biome,
                key -> {
                    biomePalette.add(key);
                    return (short) (biomePalette.size() - 1);
                });
    }
}
