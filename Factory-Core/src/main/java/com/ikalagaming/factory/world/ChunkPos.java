package com.ikalagaming.factory.world;

/**
 * Where a chunk is, in chunks: the chunk at (1, 0, 0) starts {@value World#CHUNK_SIZE} blocks along
 * x from the one at the origin.
 *
 * @param x The chunk's x coordinate.
 * @param y The chunk's y coordinate.
 * @param z The chunk's z coordinate.
 */
public record ChunkPos(int x, int y, int z) {

    /**
     * The chunk a block is in.
     *
     * @param blockX The block's x coordinate.
     * @param blockY The block's y coordinate.
     * @param blockZ The block's z coordinate.
     * @return The chunk.
     */
    public static ChunkPos containing(long blockX, long blockY, long blockZ) {
        return new ChunkPos(
                (int) (blockX >> World.CHUNK_SHIFT),
                (int) (blockY >> World.CHUNK_SHIFT),
                (int) (blockZ >> World.CHUNK_SHIFT));
    }

    /**
     * The x coordinate of the chunk's first block. A long, since chunk coordinates near the int
     * limit start beyond it.
     *
     * @return The block coordinate.
     */
    public long blockX() {
        return (long) x << World.CHUNK_SHIFT;
    }

    /**
     * The y coordinate of the chunk's first block.
     *
     * @return The block coordinate.
     */
    public long blockY() {
        return (long) y << World.CHUNK_SHIFT;
    }

    /**
     * The z coordinate of the chunk's first block.
     *
     * @return The block coordinate.
     */
    public long blockZ() {
        return (long) z << World.CHUNK_SHIFT;
    }
}
