package com.ikalagaming.factory.world;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

public class Subchunk {
    /** The height of a sub-chunk in blocks. */
    public static final int SUBCHUNK_HEIGHT = 16;

    private static final int DATA_SIZE = SUBCHUNK_HEIGHT * World.CHUNK_WIDTH * World.CHUNK_WIDTH;

    private List<Block> palette;

    private int[] data = new int[DATA_SIZE];

    public Subchunk() {
        palette = new ArrayList<>();
    }

    /**
     * Set a block at the specified coordinates within the subchunk.
     *
     * @param x The x coordinate in the range [0, {@value World#CHUNK_WIDTH}).
     * @param y The y coordinate in the range [0, {@value SUBCHUNK_HEIGHT}).
     * @param z The z coordinate in the range [0, {@value World#CHUNK_WIDTH}).
     * @param block The block to set.
     */
    public void setBlock(int x, int y, int z, @NonNull Block block) {
        // TODO(ches) update the palette if required, set the block
    }
}
