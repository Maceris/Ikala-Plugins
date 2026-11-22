package com.ikalagaming.factory.render;

import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.Face;
import com.ikalagaming.graphics.bake.Faces;
import com.ikalagaming.graphics.bake.SectionBaker;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Turns a chunk into what Graphics' sections take: each block's mesh, its packed cell position, and
 * which of its faces neighboring blocks hide. Shared by the game and the Worldgen Lab.
 */
public final class ChunkSections {

    /**
     * One chunk's blocks, as the baker takes them.
     *
     * @param blocks Each block's mesh entry.
     * @param packed Each block's packed cell position in the chunk.
     * @param faceMasks Each block's faces hidden by its neighbors.
     */
    public record Placed(
            BlockMeshes.Entry @NonNull [] blocks,
            int @NonNull [] packed,
            byte @NonNull [] faceMasks) {}

    /**
     * Work out a chunk's blocks and which of their faces neighbors hide, looking into neighboring
     * chunks; faces next to chunks that aren't there show.
     *
     * @param chunks Finds a chunk by position, or null if it isn't there.
     * @param meshes The block meshes, by name.
     * @param pos The chunk's position.
     * @param chunk The chunk.
     * @return Its blocks, or null if it has none with a mesh.
     */
    public static Placed place(
            @NonNull Function<ChunkPos, Chunk> chunks,
            @NonNull Map<String, BlockMeshes.Entry> meshes,
            @NonNull ChunkPos pos,
            @NonNull Chunk chunk) {
        List<BlockMeshes.Entry> blocks = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        List<Byte> masks = new ArrayList<>();
        BakeSource[] neighbors = new BakeSource[Face.ALL.length];
        final int[] noRotations = new int[Face.ALL.length];
        for (int y = 0; y < World.CHUNK_SIZE; ++y) {
            for (int z = 0; z < World.CHUNK_SIZE; ++z) {
                for (int x = 0; x < World.CHUNK_SIZE; ++x) {
                    final BlockMeshes.Entry block = meshes.get(chunk.getBlock(x, y, z).getName());
                    if (block == null) {
                        continue;
                    }
                    for (Face face : Face.ALL) {
                        final int[] at = {x, y, z};
                        at[face.getAxis()] += face.getSign();
                        final BlockMeshes.Entry neighbor = neighbor(chunks, meshes, pos, chunk, at);
                        neighbors[face.ordinal()] = neighbor == null ? null : neighbor.source();
                    }
                    blocks.add(block);
                    positions.add(SectionBaker.pack(x, y, z));
                    masks.add((byte) Faces.hiddenFaces(block.source(), 0, neighbors, noRotations));
                }
            }
        }
        if (blocks.isEmpty()) {
            return null;
        }
        final int count = blocks.size();
        int[] packed = new int[count];
        byte[] faceMasks = new byte[count];
        for (int i = 0; i < count; ++i) {
            packed[i] = positions.get(i);
            faceMasks[i] = masks.get(i);
        }
        return new Placed(blocks.toArray(BlockMeshes.Entry[]::new), packed, faceMasks);
    }

    /**
     * The block next to a cell, which may be in a neighboring chunk.
     *
     * @param generated Finds a chunk by position, or null if it isn't there.
     * @param meshes The block meshes, by name.
     * @param pos The cell's chunk.
     * @param chunk That chunk.
     * @param at The neighbor's coordinates within the cell's chunk, possibly just outside it.
     * @return The neighbor's mesh, or null for air or a chunk that isn't there.
     */
    private static BlockMeshes.Entry neighbor(
            @NonNull Function<ChunkPos, Chunk> generated,
            @NonNull Map<String, BlockMeshes.Entry> meshes,
            @NonNull ChunkPos pos,
            @NonNull Chunk chunk,
            int @NonNull [] at) {
        Chunk holder = chunk;
        int[] offset = {0, 0, 0};
        for (int axis = 0; axis < at.length; ++axis) {
            if (at[axis] < 0) {
                offset[axis] = -1;
                at[axis] += World.CHUNK_SIZE;
            } else if (at[axis] >= World.CHUNK_SIZE) {
                offset[axis] = 1;
                at[axis] -= World.CHUNK_SIZE;
            }
        }
        if (offset[0] != 0 || offset[1] != 0 || offset[2] != 0) {
            holder =
                    generated.apply(
                            new ChunkPos(
                                    pos.x() + offset[0], pos.y() + offset[1], pos.z() + offset[2]));
            if (holder == null) {
                return null;
            }
        }
        return meshes.get(holder.getBlock(at[0], at[1], at[2]).getName());
    }

    /** Private constructor so this class is not instantiated. */
    private ChunkSections() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
