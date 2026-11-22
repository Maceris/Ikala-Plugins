package com.ikalagaming.factory.server;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.gen.SpawnFinder;
import com.ikalagaming.factory.world.gen.SpawnPlatform;
import com.ikalagaming.factory.world.gen.WorldGenerator;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One open world on the server: its generator, its spawn, the chunks loaded so far and the pool
 * that generates them. Chunks nobody changed are never saved; they are generated again from the
 * seed when needed. See the Starting a Game design doc.
 */
@Slf4j
public final class ServerWorld implements AutoCloseable {

    /** How long {@link #close()} waits for generation threads to stop. */
    private static final long CLOSE_WAIT_SECONDS = 5;

    /**
     * The save the world is in.
     *
     * @return The save's folder.
     */
    @Getter private final Path saveFolder;

    /**
     * The world's folder name inside the save.
     *
     * @return The folder name.
     */
    @Getter private final String worldFolder;

    /**
     * Makes the world's chunks.
     *
     * @return The generator.
     */
    @Getter private final WorldGenerator generator;

    /**
     * Where new players start.
     *
     * @return The spawn.
     */
    @Getter private final SpawnFinder.Spawn spawn;

    /** Chunks loaded or being generated. */
    private final Map<ChunkPos, CompletableFuture<Chunk>> chunks = new ConcurrentHashMap<>();

    /** Generates chunks at low priority, so the client's frames stay smooth. */
    private final ExecutorService generation;

    /** Ticks run so far. */
    private final AtomicLong ticks = new AtomicLong();

    /**
     * Open a world.
     *
     * @param saveFolder The save the world is in.
     * @param worldFolder The world's folder name inside the save.
     * @param generator Makes the world's chunks.
     * @param spawn Where new players start.
     * @param threads How many threads generate chunks.
     */
    public ServerWorld(
            @NonNull Path saveFolder,
            @NonNull String worldFolder,
            @NonNull WorldGenerator generator,
            @NonNull SpawnFinder.Spawn spawn,
            int threads) {
        this.saveFolder = saveFolder;
        this.worldFolder = worldFolder;
        this.generator = generator;
        this.spawn = spawn;
        final AtomicInteger count = new AtomicInteger();
        generation =
                new ThreadPoolExecutor(
                        Math.max(1, threads),
                        Math.max(1, threads),
                        0,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(),
                        task -> {
                            Thread thread =
                                    new Thread(task, "World generation " + count.incrementAndGet());
                            thread.setDaemon(true);
                            thread.setPriority(Thread.MIN_PRIORITY);
                            return thread;
                        });
    }

    /**
     * How many threads generate chunks when not told: all but two of the processors, so the
     * client's render and main threads keep theirs.
     *
     * @return The thread count, at least 1.
     */
    public static int defaultThreads() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    }

    /**
     * A chunk, generating it if it isn't loaded.
     *
     * @param pos The chunk.
     * @return Completes with the chunk once it is ready. Generating it again after it was unloaded
     *     gives the same blocks.
     */
    public CompletableFuture<Chunk> chunk(@NonNull ChunkPos pos) {
        return chunks.computeIfAbsent(
                pos, key -> CompletableFuture.supplyAsync(() -> generate(key), generation));
    }

    /**
     * A chunk, only if it is already loaded.
     *
     * @param pos The chunk.
     * @return The chunk, or empty if it isn't loaded or is still being generated.
     */
    public Optional<Chunk> loadedChunk(@NonNull ChunkPos pos) {
        CompletableFuture<Chunk> chunk = chunks.get(pos);
        if (chunk == null || !chunk.isDone() || chunk.isCompletedExceptionally()) {
            return Optional.empty();
        }
        return Optional.of(chunk.join());
    }

    /**
     * Drop a chunk. Asking for it again generates it again.
     *
     * @param pos The chunk.
     * @return Whether it was loaded or being generated.
     */
    public boolean unload(@NonNull ChunkPos pos) {
        CompletableFuture<Chunk> chunk = chunks.remove(pos);
        if (chunk == null) {
            return false;
        }
        chunk.cancel(false);
        return true;
    }

    /**
     * How many chunks are loaded or being generated.
     *
     * @return The count.
     */
    public int loadedChunkCount() {
        return chunks.size();
    }

    /**
     * How many ticks have run.
     *
     * @return The tick count.
     */
    public long getTicks() {
        return ticks.get();
    }

    /** Run one tick. Called {@value Server#TICKS_PER_SECOND} times a second by the server. */
    void tick() {
        ticks.incrementAndGet();
    }

    /**
     * Generate a chunk, with the spawn platform stamped in if it reaches the chunk.
     *
     * @param pos The chunk.
     * @return The chunk.
     */
    private Chunk generate(@NonNull ChunkPos pos) {
        Chunk chunk = generator.generate(pos);
        if (spawn.platform() && SpawnPlatform.touches(pos, spawn)) {
            final Block floor = generator.getWorldType().defaultBlock();
            SpawnPlatform.stamp(chunk, pos, spawn, floor);
        }
        return chunk;
    }

    @Override
    public void close() {
        generation.shutdownNow();
        try {
            if (!generation.awaitTermination(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("World generation threads didn't stop within {} s", CLOSE_WAIT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        chunks.clear();
    }
}
