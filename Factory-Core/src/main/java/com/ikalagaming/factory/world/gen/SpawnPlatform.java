package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;

import lombok.NonNull;

/**
 * The space made for players when no spawn suits them: a {@value #SIZE}x{@value #SIZE} floor under
 * the spawn, with {@value #SIZE}x{@value #SIZE}x{@value #HEADROOM} of air above it.
 *
 * <p>Until changed chunks are saved, the platform is stamped into the chunks it touches each time
 * they are generated, so it is always there.
 */
public final class SpawnPlatform {

    /** Blocks along each side of the platform. */
    public static final int SIZE = 3;

    /** Blocks of air cleared above the platform. */
    public static final int HEADROOM = 3;

    /**
     * Put the platform's blocks into a chunk, where they overlap it.
     *
     * @param chunk The chunk, changed in place.
     * @param pos Where the chunk is.
     * @param spawn The spawn the platform is for; its y is the block above the floor.
     * @param floor The block the floor is made of.
     */
    public static void stamp(
            @NonNull Chunk chunk,
            @NonNull ChunkPos pos,
            @NonNull SpawnFinder.Spawn spawn,
            @NonNull Block floor) {
        final int half = SIZE / 2;
        final int floorY = spawn.y() - 1;
        for (int y = floorY; y <= floorY + HEADROOM; ++y) {
            final Block block = y == floorY ? floor : World.AIR;
            for (int x = spawn.x() - half; x <= spawn.x() + half; ++x) {
                for (int z = spawn.z() - half; z <= spawn.z() + half; ++z) {
                    if (ChunkPos.containing(x, y, z).equals(pos)) {
                        final int mask = World.CHUNK_SIZE - 1;
                        chunk.setBlock(x & mask, y & mask, z & mask, block);
                    }
                }
            }
        }
    }

    /**
     * Whether the platform reaches into a chunk.
     *
     * @param pos The chunk.
     * @param spawn The spawn the platform is for.
     * @return True if stamping would change the chunk.
     */
    public static boolean touches(@NonNull ChunkPos pos, @NonNull SpawnFinder.Spawn spawn) {
        final int half = SIZE / 2;
        final long minX = spawn.x() - half;
        final long maxX = spawn.x() + half;
        final long minY = spawn.y() - 1L;
        final long maxY = spawn.y() - 1L + HEADROOM;
        final long minZ = spawn.z() - half;
        final long maxZ = spawn.z() + half;
        return maxX >= pos.blockX()
                && minX < pos.blockX() + World.CHUNK_SIZE
                && maxY >= pos.blockY()
                && minY < pos.blockY() + World.CHUNK_SIZE
                && maxZ >= pos.blockZ()
                && minZ < pos.blockZ() + World.CHUNK_SIZE;
    }

    /** Private constructor so this class is not instantiated. */
    private SpawnPlatform() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
