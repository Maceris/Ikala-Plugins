package com.ikalagaming.factory.world;

import com.ikalagaming.factory.registry.BlockRegistry;
import com.ikalagaming.factory.sim.BlockView;

import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Movement's view of a set of chunks: a block is solid if its collision is not {@link
 * Collision#NONE}, and every block of a chunk outside the set is solid. Air is never solid, and a
 * block the registry doesn't know is.
 */
public final class ChunkBlockView implements BlockView {

    /** The mask for a block's place within its chunk. */
    private static final int IN_CHUNK = World.CHUNK_SIZE - 1;

    /** The chunks, or null for one that's missing. */
    private final Function<ChunkPos, Chunk> chunks;

    /** Whether each block, by name, is solid. */
    private final Map<String, Boolean> solidByName;

    /**
     * A view.
     *
     * @param chunks Finds a chunk, or gives null when it is missing.
     * @param solidByName Whether each block, by name, is solid, from {@link
     *     #solidByName(BlockRegistry)}.
     */
    public ChunkBlockView(
            @NonNull Function<ChunkPos, Chunk> chunks, @NonNull Map<String, Boolean> solidByName) {
        this.chunks = chunks;
        this.solidByName = solidByName;
    }

    /**
     * Whether each defined block is solid, by name, with air.
     *
     * @param blocks The block definitions.
     * @return A table to share between views.
     */
    public static Map<String, Boolean> solidByName(@NonNull BlockRegistry blocks) {
        final Map<String, Boolean> table = new HashMap<>();
        for (String name : blocks.getNames()) {
            blocks.find(name)
                    .ifPresent(
                            definition ->
                                    table.put(name, definition.collision() != Collision.NONE));
        }
        table.put(World.AIR_NAME, false);
        return Map.copyOf(table);
    }

    @Override
    public boolean solid(int x, int y, int z) {
        final Chunk chunk = chunks.apply(ChunkPos.containing(x, y, z));
        if (chunk == null) {
            return true;
        }
        final Block block = chunk.getBlock(x & IN_CHUNK, y & IN_CHUNK, z & IN_CHUNK);
        return solidByName.getOrDefault(block.getName(), true);
    }
}
