package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.LodHandle;
import com.ikalagaming.graphics.lod.CrackCheck;
import com.ikalagaming.graphics.lod.LodCache;
import com.ikalagaming.graphics.lod.LodStore;
import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * A {@link GateBenchmark} run's level of detail bookkeeping, and the level of detail gate itself:
 * whether the level of detail has caught up with the sections, what the cache did while loading,
 * how much is on the GPU while measuring, then crack checks of the cut at a few places, edits near
 * the camera timed until the level of detail has them, and a last crack check over the edits. It
 * says what the run should do next rather than moving the run on itself. Render thread only.
 */
@Slf4j
final class LodGate {

    /** What the run does next, after a step of the gate. */
    enum Next {
        /** Keep waiting in the same phase. */
        WAIT,
        /** Check the cut for cracks. */
        CHECK,
        /** Make the edits, which have been started. */
        EDIT,
        /** The gate is done: finish the run. */
        FINISH
    }

    /**
     * Where the cut is checked for cracks: how far out, as a part of the view distance, and at what
     * angle around, in degrees. The cut depends on where the eye is, not where it looks.
     */
    private static final double[][] CHECK_SPOTS = {{0, 0}, {0.5, 0}, {0.5, 120}, {0.5, 240}};

    /** How many places the cut is checked at, at most. */
    static final int CHECK_SPOT_COUNT = CHECK_SPOTS.length;

    /** How high above the ground the camera is at each check, in blocks. */
    private static final double CHECK_HEIGHT = 6;

    /** The width of the columns the crack check works through, in blocks. */
    private static final double CHECK_COLUMN = 64;

    /** How long a view is held before its cut is checked, so streaming can catch up. */
    private static final long CHECK_HOLD_NANOS = 1_000_000_000L;

    /** How long the gate's edits may take to reach the level of detail, all told. */
    private static final long EDIT_TIMEOUT_NANOS = 60_000_000_000L;

    /** The percentile edit times are given at. */
    private static final double PERCENTILE = 0.95;

    /** Where in a dig's latency the frames until it was drawn are. */
    private static final int LATENCY_FRAMES = 2;

    /** Bytes in a mebibyte. */
    private static final double BYTES_PER_MIB = 1024.0 * 1024.0;

    /** A share as a percentage. */
    private static final double PERCENT = 100;

    /** The context everything is made under. */
    private final GraphicsContext graphics;

    /** The run's sections and level of detail world. */
    private final GateWorld world;

    /** The run's digs, which make the gate's edits. */
    private final GateDigs digs;

    /** The world being drawn, or null until it is laid out. */
    private final Supplier<SyntheticTerrain> terrain;

    /** The view distance, in sections. */
    private final int radius;

    /**
     * How many of the {@link #CHECK_SPOTS} the gate checks for cracks, the first so many: each
     * takes minutes at a large radius, and most runs only need some.
     */
    private int crackSpots = CHECK_SPOT_COUNT;

    /** Whether the gate checks the cut over its edits for cracks, after making them. */
    private boolean editCracks = true;

    /** The next crack check, by index in {@link #CHECK_SPOTS}, then one after the edits. */
    private int checkIndex;

    /** When the current view was taken up, from {@link System#nanoTime()}. */
    private long checkSince;

    /** Where the camera is for the current check. */
    @Nullable private Vector3d checkPosition;

    /** The crack check running on a worker, or null. */
    @Nullable private CompletableFuture<CrackCheck.Tiled> checking;

    /** The cut being checked. */
    @Nullable private com.ikalagaming.graphics.vulkan.LodRenderer.Cut checkCut;

    /** The checks so far. */
    private final List<GateReport.Check> checks = new ArrayList<>();

    /** When the gate's edits started, from {@link System#nanoTime()}. */
    private long editStart;

    /** Edits that didn't finish in time. */
    private int editsUnfinished;

    /** What the cache had done once the world was loaded, or null. */
    @Nullable private LodCache.Stats cacheAtLoad;

    /** Level of detail bytes on the GPU and kept, once loaded. */
    private long[] lodBytesAtLoad = new long[2];

    /** The last whole second of measuring whose residency was logged. */
    private int residencyLogged = -1;

    /**
     * Set up a run's level of detail gate.
     *
     * @param graphics The context everything is made under.
     * @param world The run's sections and level of detail world.
     * @param digs The run's digs.
     * @param terrain The world being drawn, or null until it is laid out.
     * @param radius The view distance, in sections.
     */
    LodGate(
            @NonNull GraphicsContext graphics,
            @NonNull GateWorld world,
            @NonNull GateDigs digs,
            @NonNull Supplier<SyntheticTerrain> terrain,
            int radius) {
        this.graphics = graphics;
        this.world = world;
        this.digs = digs;
        this.terrain = terrain;
        this.radius = radius;
    }

    /**
     * Check fewer places for cracks, for runs that only need some: the first so many.
     *
     * @param count How many, clamped to those there are.
     */
    void setCrackSpots(int count) {
        crackSpots = Math.clamp(count, 0, CHECK_SPOT_COUNT);
    }

    /**
     * Whether to check the cut over the edits for cracks; the edits are made and timed anyway.
     *
     * @param check Whether to.
     */
    void setEditCracks(boolean check) {
        editCracks = check;
    }

    /**
     * Whether the level of detail shows every section's bake, with nothing waiting to be built,
     * uploaded or loaded.
     *
     * @return True if it has caught up; false without a level of detail world.
     */
    boolean isSettled() {
        final LodHandle lodWorld = world.lodWorld();
        if (lodWorld == null) {
            return false;
        }
        final var stats = graphics.lods().liveStats(lodWorld);
        return renderer().getState().sections.getLodBehind() == 0
                && stats != null
                && stats.pendingBatches() == 0
                && stats.staleCells() == 0
                && renderer().getState().lods.isSettled(lodWorld.id());
    }

    /**
     * Keep and log what the cache did while loading and how much is on the GPU, once the world is
     * loaded.
     *
     * @param lod Whether the run draws with level of detail, without which there is no cache.
     */
    void recordLoad(boolean lod) {
        final var cache =
                lod ? renderer().getState().sections.lodCacheStats(graphics.getOwnerKey()) : null;
        cacheAtLoad = cache;
        final LodHandle lodWorld = world.lodWorld();
        final var live = lodWorld == null ? null : graphics.lods().liveStats(lodWorld);
        if (live != null) {
            lodBytesAtLoad = new long[] {live.loadedBytes(), live.totalBytes()};
        }
        if (cache != null) {
            log.info(
                    "Gate benchmark LOD cache: {} cells read, {} built, {} MiB read,"
                            + " {} MiB written, {} in the folder",
                    cache.hits(),
                    cache.misses(),
                    String.format("%.1f", cache.bytesRead() / BYTES_PER_MIB),
                    String.format("%.1f", cache.bytesWritten() / BYTES_PER_MIB),
                    cache.folderBytes() < 0
                            ? "nothing saved, so not counted"
                            : String.format("%.1f MiB", cache.folderBytes() / BYTES_PER_MIB));
        }
    }

    /**
     * Log how much level of detail is on the GPU, once a second while the camera turns.
     *
     * @param seconds How long measuring has gone on.
     */
    void logResidency(double seconds) {
        final LodHandle lodWorld = world.lodWorld();
        if (lodWorld == null || (int) seconds == residencyLogged) {
            return;
        }
        residencyLogged = (int) seconds;
        final var stats = graphics.lods().liveStats(lodWorld);
        if (stats != null) {
            log.info(
                    "Gate benchmark LOD at {} s: {} of {} units shown, {} MiB on the GPU of {} MiB"
                            + " ({} MiB wanted), {} loads, {} evictions; the cut draws {}"
                            + " triangles{}",
                    residencyLogged,
                    stats.shownUnits(),
                    stats.units(),
                    String.format("%.1f", stats.loadedBytes() / BYTES_PER_MIB),
                    String.format("%.1f", stats.totalBytes() / BYTES_PER_MIB),
                    String.format("%.1f", stats.wantedBytes() / BYTES_PER_MIB),
                    stats.loads(),
                    stats.evictions(),
                    graphics.lods().stats().selectedTriangles(),
                    stats.budgetScale() > 1
                            ? String.format(
                                    ", at %.1f times the threshold for the budget",
                                    stats.budgetScale())
                            : "");
        }
    }

    /**
     * Start the gate once measuring is done: crack checks first, or straight to the edits if no
     * place is checked.
     *
     * @param now The time, from {@link System#nanoTime()}.
     * @return {@link Next#CHECK} or {@link Next#EDIT}.
     */
    Next start(long now) {
        if (crackSpots == 0) {
            startEdits(now);
            return Next.EDIT;
        }
        checkSince = now;
        return Next.CHECK;
    }

    /**
     * Where the camera goes for the current crack check, above the ground.
     *
     * @return The place, or null for the check over the edits, which keeps the camera where it is.
     */
    @Nullable Vector3d checkPlace() {
        if (checkIndex >= crackSpots) {
            return null;
        }
        final double distance = CHECK_SPOTS[checkIndex][0] * radius * SyntheticTerrain.SECTION;
        final double angle = Math.toRadians(CHECK_SPOTS[checkIndex][1]);
        final int x = (int) Math.floor(distance * Math.cos(angle));
        final int z = (int) Math.floor(distance * Math.sin(angle));
        final SyntheticTerrain ground = terrain.get();
        final double top = ground == null ? 0 : ground.top(x, z) + 1;
        checkPosition = new Vector3d(x + 0.5, top + CHECK_HEIGHT, z + 0.5);
        return checkPosition;
    }

    /**
     * Once the view has been held long enough and the level of detail has caught up, work out the
     * cut the GPU picks and check it for cracks on a worker; when that is done, move on.
     *
     * @return {@link Next#WAIT} while checking, {@link Next#CHECK} for the next place, {@link
     *     Next#EDIT} once the places are checked and the edits started, or {@link Next#FINISH}
     *     after the check over the edits, or without a cut to check.
     */
    Next stepCheck() {
        final LodHandle lodWorld = world.lodWorld();
        if (lodWorld == null) {
            return Next.FINISH;
        }
        final long now = System.nanoTime();
        if (checking == null) {
            if (now - checkSince < CHECK_HOLD_NANOS || !isSettled()) {
                return Next.WAIT;
            }
            final var cut = renderer().getState().lods.cut(lodWorld.id());
            if (cut == null) {
                return Next.FINISH;
            }
            checkCut = cut;
            final List<double[]> boxes = sectionBoxes(cut.hidden());
            checking =
                    CompletableFuture.supplyAsync(
                            () ->
                                    CrackCheck.checkTiled(
                                            cut.clusters(),
                                            cut.highest(),
                                            cut.drawn(),
                                            CHECK_COLUMN,
                                            boxes,
                                            cut.volumes(),
                                            CrackCheck.TOLERANCE));
            return Next.WAIT;
        }
        if (!checking.isDone()) {
            return Next.WAIT;
        }
        final Vector3d at = checkPosition;
        final String where =
                checkIndex < crackSpots && at != null
                        ? String.format("from (%.0f, %.0f, %.0f)", at.x, at.y, at.z)
                        : "over the edits";
        final var cut = checkCut;
        try {
            final CrackCheck.Tiled cracks = checking.join();
            final GateReport.Check check =
                    new GateReport.Check(
                            where, cracks, cut.missing(), cut.triangles(), cut.hidden().size());
            checks.add(check);
            log.info("Gate benchmark crack check {}", GateReport.describe(check));
        } catch (CompletionException e) {
            log.error("Gate benchmark crack check {} failed", where, e);
        }
        checking = null;
        checkCut = null;
        checkIndex += 1;
        checkSince = now;
        if (checkIndex == crackSpots) {
            startEdits(now);
            return Next.EDIT;
        }
        return checkIndex > crackSpots ? Next.FINISH : Next.CHECK;
    }

    /**
     * Start making the gate's edits.
     *
     * @param now The time, from {@link System#nanoTime()}.
     */
    private void startEdits(long now) {
        checkIndex = crackSpots;
        digs.digPit(GateReport.GATE_DIGS, GateReport.GATE_DIG_RATE);
        editStart = now;
    }

    /**
     * The boxes of some sections.
     *
     * @param positions The sections.
     * @return Their boxes, min x, y, z then max x, y, z, from the world's origin.
     */
    private static List<double[]> sectionBoxes(@NonNull Set<LodStore.SectionPos> positions) {
        final int size = SyntheticTerrain.SECTION;
        final List<double[]> boxes = new ArrayList<>();
        for (LodStore.SectionPos position : positions) {
            final double x = (double) position.x() * size;
            final double y = (double) position.y() * size;
            final double z = (double) position.z() * size;
            boxes.add(new double[] {x, y, z, x + size, y + size, z + size});
        }
        return boxes;
    }

    /**
     * Make the gate's edits and wait for each to reach the level of detail, then check the cut over
     * them.
     *
     * @return {@link Next#WAIT} while edits are waiting, then {@link Next#CHECK} to check the cut
     *     over them, or {@link Next#FINISH} if it isn't checked.
     */
    Next stepEdits() {
        digs.update();
        final long now = System.nanoTime();
        final boolean done = digs.isDone();
        if (!done && now - editStart < EDIT_TIMEOUT_NANOS) {
            return Next.WAIT;
        }
        if (!done) {
            editsUnfinished = digs.abandon();
            log.warn("Gate benchmark: {} edits didn't finish in time", editsUnfinished);
        }
        if (!editCracks) {
            return Next.FINISH;
        }
        checkSince = now;
        return Next.CHECK;
    }

    /**
     * Say how the crack checks are going, for the run's status.
     *
     * @return A line of status.
     */
    String checkStatus() {
        return String.format(
                "Checking for cracks: %d of %d",
                Math.min(checkIndex, crackSpots) + 1, crackSpots + (editCracks ? 1 : 0));
    }

    /**
     * Gather what the gate found.
     *
     * @return The gate.
     */
    GateReport.Gate results() {
        final List<double[]> digLatencies = digs.getDigLatencies();
        double worstDrawn = 0;
        int worstDrawnFrames = 0;
        final double[] lodMillis = new double[digLatencies.size()];
        for (int i = 0; i < lodMillis.length; ++i) {
            final double[] latency = digLatencies.get(i);
            worstDrawn = Math.max(worstDrawn, latency[0]);
            lodMillis[i] = latency[1];
            worstDrawnFrames = Math.max(worstDrawnFrames, (int) latency[LATENCY_FRAMES]);
        }
        Arrays.sort(lodMillis);
        final double lodP95 =
                lodMillis.length == 0
                        ? 0
                        : lodMillis[
                                Math.clamp(
                                        (int) Math.ceil(PERCENTILE * lodMillis.length) - 1,
                                        0,
                                        lodMillis.length - 1)];
        final LodCache.Stats cache = cacheAtLoad;
        return new GateReport.Gate(
                lodBytesAtLoad[0],
                lodBytesAtLoad[1],
                cache == null ? 0 : cache.hits(),
                cache == null ? 0 : cache.misses(),
                List.copyOf(checks),
                digLatencies.size() + editsUnfinished,
                editsUnfinished,
                worstDrawn,
                worstDrawnFrames,
                lodP95);
    }

    /** Log the level of detail cut's clusters and triangles by level, if there is a world. */
    void logLevels() {
        if (world.lodWorld() == null) {
            return;
        }
        final var stats = graphics.lods().stats();
        final StringBuilder table = new StringBuilder();
        table.append(
                String.format(
                        "%-6s %10s %12s %8s %12s%n",
                        "Level", "Clusters", "Triangles", "Share", "In view"));
        for (int level = 0; level < stats.levelTriangles().length; ++level) {
            table.append(
                    String.format(
                            "%-6d %,10d %,12d %7.1f%% %,12d%n",
                            level,
                            stats.levelClusters()[level],
                            stats.levelTriangles()[level],
                            PERCENT
                                    * stats.levelTriangles()[level]
                                    / Math.max(1, stats.selectedTriangles()),
                            stats.levelInView()[level]));
        }
        log.info("Gate benchmark cut by level:\n{}", table);
    }

    /**
     * The running renderer.
     *
     * @return The renderer.
     */
    private static VulkanInstance renderer() {
        return GraphicsManager.getRenderInstance();
    }
}
