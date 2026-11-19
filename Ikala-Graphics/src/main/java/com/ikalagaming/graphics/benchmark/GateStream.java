package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.LodHandle;
import com.ikalagaming.graphics.SectionHandle;
import com.ikalagaming.graphics.lod.LodStore;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.WaterTile;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3dc;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nullable;

/**
 * Keeps a {@link GateBenchmark} run's world composed around the camera as it moves, with the {@code
 * .stream} option, through a {@link WorldStream}: sections and lake tiles are added ahead of the
 * camera and taken away behind it, and how well it keeps up is logged. Render thread, but for what
 * the stream's worker calls on its target.
 */
@Slf4j
final class GateStream {

    /** How many frames apart the stream checks which sections are drawn. */
    private static final int STREAM_POLL_FRAMES = 4;

    /** How often the stream is logged while measuring, in seconds. */
    private static final double STREAM_LOG_SECONDS = 2;

    /** How often the stream is logged once the run is done and kept, in seconds. */
    private static final double STREAM_LOG_KEPT_SECONDS = 10;

    /** The median, as a fraction. */
    private static final double MEDIAN = 0.5;

    /** The 95th percentile, as a fraction. */
    private static final double PERCENTILE_95 = 0.95;

    /** Bytes in a mebibyte. */
    private static final long BYTES_PER_MIB = 1024L * 1024L;

    /** The context everything is made under. */
    private final GraphicsContext graphics;

    /** The run's sections, which the stream composes and removes. */
    private final GateWorld world;

    /** The run's water, whose lake tiles the stream adds and takes away. */
    private final GateWater water;

    /** The view distance, in sections. */
    private final int radius;

    /** The stream, or null until started and once stopped. */
    @Nullable private WorldStream stream;

    /** Changes the stream's worker makes to the run on the render thread, such as lake water. */
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    /** Frames since the stream last checked which sections are drawn. */
    private int pollFrames;

    /** When the stream was last logged, in nanoseconds, or 0. */
    private long loggedAt;

    /** The largest backlog the stream logged. */
    private int mostBacklog;

    /** What the stream changes in the run, on its worker. */
    private final class Target implements WorldStream.Target {

        /** The world being streamed. */
        private final SyntheticTerrain terrain;

        /**
         * Make a target.
         *
         * @param terrain The world being streamed.
         */
        Target(@NonNull SyntheticTerrain terrain) {
            this.terrain = terrain;
        }

        @Override
        public boolean isComposed(LodStore.@NonNull SectionPos position) {
            return world.isComposed(position);
        }

        @Override
        public SectionHandle compose(
                LodStore.@NonNull SectionPos position,
                SyntheticTerrain.@NonNull Placements placements) {
            return world.compose(position, placements);
        }

        @Override
        public boolean remove(LodStore.@NonNull SectionPos position) {
            return world.remove(position);
        }

        @Override
        public void addLakeTile(int startX, int startZ) {
            final WaterTile tile = GateWater.lakeWaterTile(terrain, startX, startZ);
            if (tile != null) {
                tasks.add(() -> water.placeLakeTile(tile));
            }
        }

        @Override
        public void removeLakeTile(int startX, int startZ) {
            tasks.add(() -> water.removeLakeTile(startX, startZ));
        }
    }

    /**
     * Set up a run's stream, not started.
     *
     * @param graphics The context everything is made under.
     * @param world The run's sections.
     * @param water The run's water.
     * @param radius The view distance, in sections.
     */
    GateStream(
            @NonNull GraphicsContext graphics,
            @NonNull GateWorld world,
            @NonNull GateWater water,
            int radius) {
        this.graphics = graphics;
        this.world = world;
        this.water = water;
        this.radius = radius;
    }

    /**
     * Start streaming the world around the camera, once the world first laid out is drawn, unless
     * it is streaming already.
     *
     * @param terrain The world.
     */
    void start(@NonNull SyntheticTerrain terrain) {
        if (stream != null) {
            return;
        }
        final LodStore.SectionPos start =
                new LodStore.SectionPos(
                        0,
                        Math.floorDiv(
                                (int) Math.floor(terrain.cameraY()), SyntheticTerrain.SECTION),
                        0);
        stream =
                new WorldStream(
                        terrain,
                        new Target(terrain),
                        radius,
                        start,
                        GateWater.OCEAN_CELLS,
                        water.lakeTileKeys());
        loggedAt = System.nanoTime();
        log.info("Gate benchmark streaming the world around the camera at radius {}", radius);
    }

    /**
     * Keep the stream following the camera, apply its changes, and log how it keeps up. Every
     * frame.
     *
     * @param scene The scene.
     * @param measuring Whether the run is measuring, when it is logged more often.
     */
    void step(@NonNull Scene scene, boolean measuring) {
        final WorldStream streaming = stream;
        if (streaming == null) {
            return;
        }
        Runnable task;
        while ((task = tasks.poll()) != null) {
            task.run();
        }
        final Vector3dc camera = scene.getCamera().getPosition();
        streaming.follow(camera.x(), camera.y(), camera.z());
        if (++pollFrames >= STREAM_POLL_FRAMES) {
            pollFrames = 0;
            streaming.poll(graphics.sections()::isResident, graphics.sections()::isValid);
        }
        final double every = measuring ? STREAM_LOG_SECONDS : STREAM_LOG_KEPT_SECONDS;
        final long now = System.nanoTime();
        if ((now - loggedAt) / 1e9 < every) {
            return;
        }
        loggedAt = now;
        final WorldStream.Stats stats = streaming.takeWindow();
        mostBacklog = Math.max(mostBacklog, stats.backlog());
        final LodHandle lodWorld = world.lodWorld();
        final var live = lodWorld == null ? null : graphics.lods().liveStats(lodWorld);
        final var sections = GraphicsManager.getRenderInstance().getState().sections;
        final Runtime runtime = Runtime.getRuntime();
        log.info(
                "Gate benchmark streaming at x {}: {} composed, {} empty, {} removed; backlog {}"
                        + " cells, {} sections waiting to be drawn; drawn after {} ms (median), {}"
                        + " (95th), {} (most) for {} sections; LOD {} stale cells, {} batches"
                        + " waiting, {} sections behind, {} taken out still shown; heap {} MiB",
                String.format("%.0f", camera.x()),
                stats.composed(),
                stats.empty(),
                stats.removed(),
                stats.backlog(),
                stats.pending(),
                String.format("%.0f", WorldStream.percentile(stats.latencies(), MEDIAN)),
                String.format("%.0f", WorldStream.percentile(stats.latencies(), PERCENTILE_95)),
                String.format("%.0f", WorldStream.percentile(stats.latencies(), 1)),
                stats.latencies().length,
                live == null ? 0 : live.staleCells(),
                live == null ? 0 : live.pendingBatches(),
                sections.getLodBehind(),
                sections.getLodRemovedWaiting(),
                (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MIB);
    }

    /** Log what the stream did over the whole run, if it streamed. */
    void logTotals() {
        final WorldStream streaming = stream;
        if (streaming == null) {
            return;
        }
        final WorldStream.Stats stats = streaming.totals();
        log.info(
                "Gate benchmark streaming totals: {} sections composed, {} empty, {} removed; most"
                        + " backlog {} cells; drawn after {} ms (median), {} (95th), {} (most) for {}"
                        + " sections",
                stats.composed(),
                stats.empty(),
                stats.removed(),
                mostBacklog,
                String.format("%.0f", WorldStream.percentile(stats.latencies(), MEDIAN)),
                String.format("%.0f", WorldStream.percentile(stats.latencies(), PERCENTILE_95)),
                String.format("%.0f", WorldStream.percentile(stats.latencies(), 1)),
                stats.latencies().length);
    }

    /**
     * Stop streaming and drop the changes waiting for the render thread. Before the sections are
     * released, so the worker adds no more.
     */
    void stop() {
        if (stream != null) {
            stream.stop();
            stream = null;
        }
        tasks.clear();
    }
}
