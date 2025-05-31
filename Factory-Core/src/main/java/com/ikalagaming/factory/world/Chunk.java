package com.ikalagaming.factory.world;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * A region of blocks in the world.
 *
 * <p>Chunks have a {@value World#CHUNK_WIDTH}x{@value World#CHUNK_WIDTH} square footprint but are
 * {@value World#WORLD_HEIGHT_TOTAL} blocks tall.
 *
 * @author Ches Burks
 */
@Slf4j
public class Chunk {
    /** The number of sub-chunks that we store in each chunk. */
    private static final int SUBCHUNK_COUNT = World.WORLD_HEIGHT_TOTAL / Subchunk.SUBCHUNK_HEIGHT;

    private byte[][] biomes = new byte[World.CHUNK_WIDTH][World.CHUNK_WIDTH];

    private Subchunk[] subchunks = new Subchunk[SUBCHUNK_COUNT];

    public void setBiome(int x, int z, byte biome) {
        biomes[x][z] = biome;
    }

    public void setBlock(int x, int y, int z, @NonNull Block block) {
        final int subchunkIndex = y / Subchunk.SUBCHUNK_HEIGHT;
        if (subchunkIndex < 0 || subchunkIndex >= SUBCHUNK_COUNT) {
            log.warn(
                    SafeResourceLoader.getString(
                            "INVALID_BLOCK_COORDINATES", FactoryPlugin.getResourceBundle()));
            return;
        }
        subchunks[subchunkIndex].setBlock(
                x % World.CHUNK_WIDTH, y % Subchunk.SUBCHUNK_HEIGHT, z % World.CHUNK_WIDTH, block);
    }
}
