package com.ikalagaming.factory.render;

import com.ikalagaming.factory.client.ClientWorld;
import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.SectionHandle;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Draws the chunks the client has, as Graphics sections. Chunks are turned into sections on a
 * worker thread; when one arrives next to chunks already drawn, they are redone too, since faces
 * that showed toward the missing chunk may now be hidden.
 *
 * <p>The listener methods are called on the main thread; everything else happens on the worker.
 */
@Slf4j
public final class WorldRenderer implements ClientWorld.Listener, AutoCloseable {

    /** The six face neighbors of a chunk, as offsets. */
    private static final int[][] NEIGHBORS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    /** How long {@link #close()} waits for the worker to stop. */
    private static final long CLOSE_WAIT_SECONDS = 5;

    /** The plugin's graphics. */
    private final GraphicsContext graphics;

    /** Each block's mesh. Main thread only. */
    private final BlockMeshes blockMeshes;

    /** The meshes the worker reads, by block name; filled on the main thread first. */
    private final Map<String, BlockMeshes.Entry> meshes = new ConcurrentHashMap<>();

    /** The chunks being drawn, which the worker reads. */
    private final Map<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();

    /** Each chunk's section. Worker only. */
    private final Map<ChunkPos, SectionHandle> sections = new ConcurrentHashMap<>();

    /** Chunks waiting to be redone, so each is queued once. */
    private final Set<ChunkPos> queued = ConcurrentHashMap.newKeySet();

    /** Turns chunks into sections, one at a time, in the order they arrive. */
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    task -> {
                        Thread thread = new Thread(task, "World sections");
                        thread.setDaemon(true);
                        return thread;
                    });

    /**
     * A renderer.
     *
     * @param graphics The plugin's graphics.
     */
    public WorldRenderer(@NonNull GraphicsContext graphics) {
        this.graphics = graphics;
        blockMeshes = new BlockMeshes(graphics);
    }

    @Override
    public void chunkAdded(@NonNull ChunkPos pos, @NonNull Chunk chunk) {
        final List<String> names = new ArrayList<>();
        for (Block block : chunk.paletteBlocks()) {
            // Air has no mesh, so ChunkSections leaves it out
            if (!World.AIR_NAME.equals(block.getName()) && !meshes.containsKey(block.getName())) {
                names.add(block.getName());
            }
        }
        if (!names.isEmpty()) {
            meshes.putAll(blockMeshes.meshesFor(names, Set.of()));
        }
        chunks.put(pos, chunk);
        queue(pos);
        for (int[] offset : NEIGHBORS) {
            final ChunkPos neighbor =
                    new ChunkPos(pos.x() + offset[0], pos.y() + offset[1], pos.z() + offset[2]);
            if (chunks.containsKey(neighbor)) {
                queue(neighbor);
            }
        }
    }

    @Override
    public void chunkRemoved(@NonNull ChunkPos pos) {
        chunks.remove(pos);
        queue(pos);
    }

    /**
     * How many chunks have a section.
     *
     * @return The count.
     */
    public int sectionCount() {
        return sections.size();
    }

    /**
     * Redo a chunk's section on the worker, once however often it is asked for before then.
     *
     * @param pos The chunk.
     */
    private void queue(@NonNull ChunkPos pos) {
        if (queued.add(pos)) {
            worker.execute(
                    () -> {
                        queued.remove(pos);
                        try {
                            compose(pos);
                        } catch (RuntimeException e) {
                            log.warn("Chunk {} couldn't be drawn", pos, e);
                        }
                    });
        }
    }

    /**
     * Make a chunk's section from its blocks and its neighbors, replacing any it had, or remove its
     * section if the chunk is gone or has nothing to draw. Worker only.
     *
     * @param pos The chunk.
     */
    private void compose(@NonNull ChunkPos pos) {
        final Chunk chunk = chunks.get(pos);
        final ChunkSections.Placed placed =
                chunk == null ? null : ChunkSections.place(chunks::get, meshes, pos, chunk);
        SectionHandle old;
        if (placed == null) {
            old = sections.remove(pos);
        } else {
            final int count = placed.blocks().length;
            final MeshHandle[] handles = new MeshHandle[count];
            for (int i = 0; i < count; ++i) {
                handles[i] = placed.blocks()[i].mesh();
            }
            final SectionHandle made =
                    graphics.sections()
                            .compose(new Vector3d(pos.blockX(), pos.blockY(), pos.blockZ()))
                            .placements(
                                    handles,
                                    placed.packed(),
                                    new byte[count],
                                    placed.faceMasks(),
                                    new short[count])
                            .submit();
            old = sections.put(pos, made);
        }
        // After the new one is in, so the chunk never shows a gap
        if (old != null && !graphics.isClosed()) {
            graphics.sections().remove(old);
        }
    }

    @Override
    public void close() {
        worker.shutdownNow();
        try {
            if (!worker.awaitTermination(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("The world section thread didn't stop within {} s", CLOSE_WAIT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!graphics.isClosed()) {
            sections.values().forEach(graphics.sections()::remove);
        }
        sections.clear();
        chunks.clear();
        meshes.clear();
        blockMeshes.close();
    }
}
