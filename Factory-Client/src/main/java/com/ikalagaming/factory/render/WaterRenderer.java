package com.ikalagaming.factory.render;

import com.ikalagaming.factory.client.ClientWorld;
import com.ikalagaming.factory.render.looks.WaterLooks;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.WaterHandle;
import com.ikalagaming.graphics.scene.WaterTile;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Draws the client's water with Graphics' water: as the chunks a square of the world holds change,
 * it builds that square's tiles again on its own thread, a moment later so a burst of chunks
 * rebuilds it once. Water blocks themselves get no mesh; see {@link WorldRenderer}. See the Water
 * design doc.
 */
@Slf4j
public final class WaterRenderer implements ClientWorld.Listener, AutoCloseable {

    /** How long a changed square waits before it is built, so streaming chunks batch up. */
    static final long REBUILD_DELAY_MILLIS = 250;

    /** How long closing waits for a build to finish. */
    private static final long CLOSE_WAIT_SECONDS = 5;

    /** The plugin's graphics. */
    private final GraphicsContext graphics;

    /** Whether a block, by name, is water. */
    private final Predicate<String> isWater;

    /** What water holds in each biome. */
    private final WaterLooks looks;

    /** The chunks, by position. */
    private final Map<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();

    /** Each square's tiles in the scene. */
    private final Map<WaterTiles.Square, List<WaterHandle>> tiles = new ConcurrentHashMap<>();

    /** Squares waiting to be built. */
    private final Set<WaterTiles.Square> queued = ConcurrentHashMap.newKeySet();

    /** Builds squares. */
    private final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor(
                    task -> {
                        final Thread thread = new Thread(task, "World water");
                        thread.setDaemon(true);
                        return thread;
                    });

    /**
     * A water renderer.
     *
     * @param graphics The plugin's graphics.
     * @param isWater Whether a block, by name, is water.
     * @param looks What water holds in each biome.
     */
    public WaterRenderer(
            @NonNull GraphicsContext graphics,
            @NonNull Predicate<String> isWater,
            @NonNull WaterLooks looks) {
        this.graphics = graphics;
        this.isWater = isWater;
        this.looks = looks;
    }

    @Override
    public void chunkAdded(@NonNull ChunkPos pos, @NonNull Chunk chunk) {
        chunks.put(pos, chunk);
        queue(WaterTiles.Square.of(pos));
    }

    @Override
    public void chunkRemoved(@NonNull ChunkPos pos) {
        chunks.remove(pos);
        queue(WaterTiles.Square.of(pos));
    }

    /**
     * How many water tiles are in the scene.
     *
     * @return The count.
     */
    public int tileCount() {
        return tiles.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Build a square again soon.
     *
     * @param square The square.
     */
    private void queue(@NonNull WaterTiles.Square square) {
        if (queued.add(square)) {
            worker.schedule(
                    () -> {
                        queued.remove(square);
                        try {
                            rebuild(square);
                        } catch (RuntimeException e) {
                            log.warn("The water of square {} couldn't be built", square, e);
                        }
                    },
                    REBUILD_DELAY_MILLIS,
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Build a square's tiles and swap them in for its old ones.
     *
     * @param square The square.
     */
    private void rebuild(@NonNull WaterTiles.Square square) {
        if (graphics.isClosed()) {
            return;
        }
        final List<WaterTile> built =
                WaterTiles.build(
                        square, chunks::get, chunkHeights(square), isWater, looks::bodyFor);
        final List<WaterHandle> handles = new ArrayList<>(built.size());
        for (WaterTile tile : built) {
            final WaterHandle handle = graphics.water().add(tile);
            if (handle != null) {
                handles.add(handle);
            }
        }
        // After the new ones are in, so the water never blinks out
        final List<WaterHandle> old =
                handles.isEmpty() ? tiles.remove(square) : tiles.put(square, handles);
        if (old != null) {
            old.forEach(graphics.water()::remove);
        }
    }

    /**
     * The chunk heights loaded in a square's columns.
     *
     * @param square The square.
     * @return The heights, lowest first.
     */
    private int[] chunkHeights(@NonNull WaterTiles.Square square) {
        final TreeSet<Integer> heights = new TreeSet<>();
        for (ChunkPos pos : chunks.keySet()) {
            if (WaterTiles.Square.of(pos).equals(square)) {
                heights.add(pos.y());
            }
        }
        return heights.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public void close() {
        worker.shutdownNow();
        try {
            if (!worker.awaitTermination(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("The water thread didn't stop in {} s", CLOSE_WAIT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!graphics.isClosed()) {
            tiles.values().forEach(list -> list.forEach(graphics.water()::remove));
        }
        tiles.clear();
        chunks.clear();
    }
}
