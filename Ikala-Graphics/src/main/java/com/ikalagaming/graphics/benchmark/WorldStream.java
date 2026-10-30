package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.SectionHandle;
import com.ikalagaming.graphics.lod.LodStore;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Keeps a gate world composed around the camera as it moves, the way a game streams its world: a
 * worker composes the level of detail cells, {@link #CELL_SECTIONS} sections a side, whose middles
 * come within the view distance, nearest first, and removes those whose middles fall a cell past
 * it, and does the same for the lakes' water tiles. A whole cell comes and goes at once, so the
 * level of detail builds it once rather than again for each slice of it the edge passes. The world
 * is generated as it is reached, so the camera can go anywhere.
 *
 * <p>The render thread tells it where the camera is each frame through {@link #follow} and checks
 * how long sections take to be drawn through {@link #poll}; the rest runs on its worker.
 */
@Slf4j
final class WorldStream {

    /** The sections along each side of a cell, the level of detail's. */
    public static final int CELL_SECTIONS = LodStore.DEFAULT_CELL_SECTIONS;

    /**
     * How many sections past the view distance a cell's middle may go before it is removed, so one
     * at the edge isn't removed and composed again as the camera moves back and forth: a cell.
     */
    public static final int REMOVE_MARGIN = CELL_SECTIONS;

    /**
     * The most sections waiting for their first bake before the worker waits, so a fast camera
     * queues the nearest cells, not a long trail of ones it has passed.
     */
    public static final int MAX_PENDING = 512;

    /** How long the worker sleeps when it has nothing to do, in milliseconds. */
    private static final long IDLE_MILLIS = 20;

    /** How long {@link #stop} waits for the worker, in milliseconds. */
    private static final long STOP_MILLIS = 2000;

    /** Nanoseconds in a millisecond. */
    private static final double NANOS_PER_MILLI = 1e6;

    /** What the stream changes in the run. */
    interface Target {
        /**
         * Whether a section was composed already, such as with the world first laid out.
         *
         * @param position The section.
         * @return True if it was.
         */
        boolean isComposed(LodStore.@NonNull SectionPos position);

        /**
         * Compose a section. On the worker.
         *
         * @param position The section.
         * @param placements What it holds, which may be nothing.
         * @return The section's handle, or null if it holds nothing.
         */
        SectionHandle compose(
                LodStore.@NonNull SectionPos position,
                SyntheticTerrain.@NonNull Placements placements);

        /**
         * Remove a section, if one was composed there. On the worker.
         *
         * @param position The section.
         * @return True if one was.
         */
        boolean remove(LodStore.@NonNull SectionPos position);

        /**
         * Add the lake water tile with its corner here, if there is lake in it. On the worker.
         *
         * @param startX The tile's corner along x, in blocks.
         * @param startZ Along z.
         */
        void addLakeTile(int startX, int startZ);

        /**
         * Remove the lake water tile with its corner here, if one was added. On the worker.
         *
         * @param startX The tile's corner along x, in blocks.
         * @param startZ Along z.
         */
        void removeLakeTile(int startX, int startZ);
    }

    /**
     * Totals since the stream started.
     *
     * @param composed Sections with something in them that were composed.
     * @param empty Sections laid out that held nothing.
     * @param removed Sections removed.
     * @param backlog Cells in the view distance not laid out yet, when the worker last looked.
     * @param pending Sections composed and not drawn yet.
     * @param latencies Milliseconds from composing each section to its first bake being drawn,
     *     sorted.
     */
    record Stats(
            long composed,
            long empty,
            long removed,
            int backlog,
            int pending,
            double @NonNull [] latencies) {}

    /** The world. */
    private final SyntheticTerrain terrain;

    /** What the stream changes. */
    private final Target target;

    /** The view distance, in sections. */
    private final int radius;

    /** The size of a lake water tile, in blocks. */
    private final int tileSize;

    /** Cells laid out, by cell position. Worker. */
    private final Set<LodStore.SectionPos> known = new HashSet<>();

    /** Lake water tiles looked at, by corner, with lake in them or not. Worker. */
    private final Set<Long> knownTiles = new HashSet<>();

    /** The cells left to lay out from the last look, nearest first. Worker. */
    private final Queue<LodStore.SectionPos> queue = new ArrayDeque<>();

    /**
     * Cells counted as laid out that the world laid out at the start holds only part of, see {@link
     * #edgeCells}: laid out once they come within the radius. Worker thread only, after
     * construction.
     */
    private final Set<LodStore.SectionPos> partial = new HashSet<>();

    /** The section the camera was in at the last look. Worker. */
    private LodStore.SectionPos lookedFrom;

    /** The section the camera is in, from the render thread. */
    private volatile LodStore.SectionPos center;

    /** The camera's position along x and z, from the render thread. */
    private volatile double cameraX;

    /** Along z. */
    private volatile double cameraZ;

    /** Whether the worker keeps going. */
    private volatile boolean running = true;

    /** The worker. */
    private final Thread worker;

    /** When each composed section not drawn yet was composed, in nanoseconds. */
    private final Map<SectionHandle, Long> composedAt = new ConcurrentHashMap<>();

    /** Sections composed by the worker that {@link #poll} hasn't seen yet. */
    private final Queue<SectionHandle> justComposed = new ConcurrentLinkedQueue<>();

    /** Latencies to be drawn, in milliseconds. Render thread. */
    private final List<Double> latencies = new ArrayList<>();

    /** How many latencies were taken at the last {@link #takeWindow}. Render thread. */
    private int windowStart;

    /** Totals. */
    private final AtomicLong composed = new AtomicLong();

    /** Sections that held nothing. */
    private final AtomicLong empty = new AtomicLong();

    /** Sections removed. */
    private final AtomicLong removed = new AtomicLong();

    /** The backlog at the last look, in cells. */
    private final AtomicInteger backlog = new AtomicInteger();

    /**
     * Start streaming around where the world was first laid out: every cell with a section within
     * the radius of the starting section counts as laid out, and so do the lake tiles over the
     * starting square.
     *
     * @param terrain The world.
     * @param target What the stream changes.
     * @param radius The view distance, in sections.
     * @param start The section the world was laid out around.
     * @param tileSize The size of a lake water tile, in blocks.
     * @param startTiles The corners of the lake tiles looked at when the world was laid out, as
     *     {@link #tileKey} gives them.
     */
    WorldStream(
            @NonNull SyntheticTerrain terrain,
            @NonNull Target target,
            int radius,
            LodStore.@NonNull SectionPos start,
            int tileSize,
            @NonNull Set<Long> startTiles) {
        this.terrain = terrain;
        this.target = target;
        this.radius = radius;
        this.tileSize = tileSize;
        for (LodStore.SectionPos position : sphere(start, radius)) {
            known.add(cellOf(position));
        }
        partial.addAll(edgeCells(start, radius));
        knownTiles.addAll(startTiles);
        center = start;
        worker = new Thread(this::run, "Gate benchmark streamer");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * The cells on the edge of the world laid out at the start, which it holds only part of: the
     * stream lays them out first, composing what is missing. Only counted as laid out, their
     * sections past the edge were never composed, and showed as holes a radius from the start.
     *
     * @param start The section the world was laid out around.
     * @param radius The radius, in sections.
     * @return The cells, nearest the start first.
     */
    static List<LodStore.SectionPos> edgeCells(LodStore.@NonNull SectionPos start, int radius) {
        final Map<LodStore.SectionPos, Integer> counts = new HashMap<>();
        for (LodStore.SectionPos position : sphere(start, radius)) {
            counts.merge(cellOf(position), 1, Integer::sum);
        }
        final int whole = CELL_SECTIONS * CELL_SECTIONS * CELL_SECTIONS;
        final List<LodStore.SectionPos> cells = new ArrayList<>();
        for (Map.Entry<LodStore.SectionPos, Integer> entry : counts.entrySet()) {
            if (entry.getValue() < whole) {
                cells.add(entry.getKey());
            }
        }
        cells.sort(Comparator.comparingLong(cell -> doubledDistanceSquared(cell, start)));
        return cells;
    }

    /**
     * Every section within a radius of another, as a sphere.
     *
     * @param center The middle section.
     * @param radius The radius, in sections.
     * @return The sections, in no order.
     */
    static List<LodStore.SectionPos> sphere(LodStore.@NonNull SectionPos center, int radius) {
        final List<LodStore.SectionPos> inside = new ArrayList<>();
        for (int x = -radius; x <= radius; ++x) {
            for (int y = -radius; y <= radius; ++y) {
                for (int z = -radius; z <= radius; ++z) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        inside.add(
                                new LodStore.SectionPos(
                                        center.x() + x, center.y() + y, center.z() + z));
                    }
                }
            }
        }
        return inside;
    }

    /**
     * The cell a section is in.
     *
     * @param section The section.
     * @return The cell's position, in cells.
     */
    static LodStore.SectionPos cellOf(LodStore.@NonNull SectionPos section) {
        return new LodStore.SectionPos(
                Math.floorDiv(section.x(), CELL_SECTIONS),
                Math.floorDiv(section.y(), CELL_SECTIONS),
                Math.floorDiv(section.z(), CELL_SECTIONS));
    }

    /**
     * Twice the distance from a cell's middle to a section's, squared, in sections: twice, so both
     * middles are whole numbers.
     *
     * @param cell The cell, in cells.
     * @param section The section.
     * @return The doubled distance, squared.
     */
    static long doubledDistanceSquared(
            LodStore.@NonNull SectionPos cell, LodStore.@NonNull SectionPos section) {
        final long x = 2L * cell.x() * CELL_SECTIONS + CELL_SECTIONS - (2L * section.x() + 1);
        final long y = 2L * cell.y() * CELL_SECTIONS + CELL_SECTIONS - (2L * section.y() + 1);
        final long z = 2L * cell.z() * CELL_SECTIONS + CELL_SECTIONS - (2L * section.z() + 1);
        return x * x + y * y + z * z;
    }

    /**
     * Whether a cell's middle is within a distance of a section's.
     *
     * @param cell The cell, in cells.
     * @param section The section.
     * @param distance The distance, in sections.
     * @return True if it is.
     */
    static boolean within(
            LodStore.@NonNull SectionPos cell, LodStore.@NonNull SectionPos section, int distance) {
        return doubledDistanceSquared(cell, section) <= 4L * distance * distance;
    }

    /**
     * The cells whose middles are within a radius of a section that aren't laid out yet, nearest
     * first.
     *
     * @param known The cells laid out.
     * @param center The section in the middle.
     * @param radius The radius, in sections.
     * @return The cells to lay out.
     */
    static List<LodStore.SectionPos> missing(
            @NonNull Set<LodStore.SectionPos> known,
            LodStore.@NonNull SectionPos center,
            int radius) {
        final LodStore.SectionPos middle = cellOf(center);
        final int reach = radius / CELL_SECTIONS + 1;
        final List<LodStore.SectionPos> missing = new ArrayList<>();
        for (int x = -reach; x <= reach; ++x) {
            for (int y = -reach; y <= reach; ++y) {
                for (int z = -reach; z <= reach; ++z) {
                    final LodStore.SectionPos cell =
                            new LodStore.SectionPos(middle.x() + x, middle.y() + y, middle.z() + z);
                    if (within(cell, center, radius) && !known.contains(cell)) {
                        missing.add(cell);
                    }
                }
            }
        }
        missing.sort(Comparator.comparingLong(cell -> doubledDistanceSquared(cell, center)));
        return missing;
    }

    /**
     * The cells laid out whose middles are farther from a section than a radius plus {@link
     * #REMOVE_MARGIN}.
     *
     * @param known The cells laid out.
     * @param center The section in the middle.
     * @param radius The view distance, in sections.
     * @return The cells to remove.
     */
    static List<LodStore.SectionPos> beyond(
            @NonNull Set<LodStore.SectionPos> known,
            LodStore.@NonNull SectionPos center,
            int radius) {
        final List<LodStore.SectionPos> far = new ArrayList<>();
        for (LodStore.SectionPos cell : known) {
            if (!within(cell, center, radius + REMOVE_MARGIN)) {
                far.add(cell);
            }
        }
        return far;
    }

    /**
     * The sections in a cell.
     *
     * @param cell The cell, in cells.
     * @return Its sections.
     */
    static List<LodStore.SectionPos> sectionsOf(LodStore.@NonNull SectionPos cell) {
        final List<LodStore.SectionPos> sections = new ArrayList<>();
        for (int x = 0; x < CELL_SECTIONS; ++x) {
            for (int y = 0; y < CELL_SECTIONS; ++y) {
                for (int z = 0; z < CELL_SECTIONS; ++z) {
                    sections.add(
                            new LodStore.SectionPos(
                                    cell.x() * CELL_SECTIONS + x,
                                    cell.y() * CELL_SECTIONS + y,
                                    cell.z() * CELL_SECTIONS + z));
                }
            }
        }
        return sections;
    }

    /**
     * A percentile of sorted values, the nearest one at or above it.
     *
     * @param sorted The values, sorted.
     * @param fraction The percentile, from 0 to 1.
     * @return The value, or 0 if there are none.
     */
    static double percentile(double @NonNull [] sorted, double fraction) {
        if (sorted.length == 0) {
            return 0;
        }
        final int index = (int) Math.ceil(fraction * sorted.length) - 1;
        return sorted[Math.clamp(index, 0, sorted.length - 1)];
    }

    /**
     * A lake tile's key, from its corner.
     *
     * @param startX The corner along x, in blocks.
     * @param startZ Along z.
     * @return The key.
     */
    static long tileKey(int startX, int startZ) {
        return ((long) startX << Integer.SIZE) | (startZ & 0xFFFFFFFFL);
    }

    /**
     * Whether a tile reaches within a distance of a point, measured to its nearest point.
     *
     * @param startX The tile's corner along x, in blocks.
     * @param startZ Along z.
     * @param size The tile's size, in blocks.
     * @param x The point along x.
     * @param z Along z.
     * @param reach The distance, in blocks.
     * @return True if some of the tile is within reach.
     */
    static boolean tileWithin(int startX, int startZ, int size, double x, double z, double reach) {
        final double dx = Math.max(0, Math.max(startX - x, x - (startX + size)));
        final double dz = Math.max(0, Math.max(startZ - z, z - (startZ + size)));
        return dx * dx + dz * dz <= reach * reach;
    }

    /**
     * Tell the stream where the camera is. Render thread, every frame.
     *
     * @param x The camera's position along x.
     * @param y Along y.
     * @param z Along z.
     */
    void follow(double x, double y, double z) {
        cameraX = x;
        cameraZ = z;
        final int size = SyntheticTerrain.SECTION;
        final LodStore.SectionPos now =
                new LodStore.SectionPos(
                        Math.floorDiv((int) Math.floor(x), size),
                        Math.floorDiv((int) Math.floor(y), size),
                        Math.floorDiv((int) Math.floor(z), size));
        if (!now.equals(center)) {
            center = now;
        }
    }

    /**
     * Note which composed sections are drawn now, for how long they took. Render thread.
     *
     * @param isResident Whether a section's first bake is drawn.
     * @param isValid Whether a section still exists.
     */
    void poll(
            @NonNull Predicate<SectionHandle> isResident,
            @NonNull Predicate<SectionHandle> isValid) {
        final long now = System.nanoTime();
        SectionHandle handle;
        while ((handle = justComposed.poll()) != null) {
            // Seen here so the map only holds what the worker made
            composedAt.putIfAbsent(handle, now);
        }
        final Iterator<Map.Entry<SectionHandle, Long>> waiting = composedAt.entrySet().iterator();
        while (waiting.hasNext()) {
            final Map.Entry<SectionHandle, Long> entry = waiting.next();
            if (isResident.test(entry.getKey())) {
                latencies.add((now - entry.getValue()) / NANOS_PER_MILLI);
                waiting.remove();
            } else if (!isValid.test(entry.getKey())) {
                waiting.remove();
            }
        }
    }

    /**
     * The totals, with the latencies since the last call. Render thread.
     *
     * @return The totals, latencies sorted.
     */
    Stats takeWindow() {
        final double[] window =
                latencies.subList(windowStart, latencies.size()).stream()
                        .mapToDouble(Double::doubleValue)
                        .sorted()
                        .toArray();
        windowStart = latencies.size();
        return stats(window);
    }

    /**
     * The totals, with every latency so far. Render thread.
     *
     * @return The totals, latencies sorted.
     */
    Stats totals() {
        return stats(latencies.stream().mapToDouble(Double::doubleValue).sorted().toArray());
    }

    /**
     * The totals with some latencies.
     *
     * @param sorted The latencies, sorted.
     * @return The totals.
     */
    private Stats stats(double @NonNull [] sorted) {
        return new Stats(
                composed.get(),
                empty.get(),
                removed.get(),
                backlog.get(),
                composedAt.size() + justComposed.size(),
                sorted);
    }

    /** Stop the worker, waiting a moment for it to finish the cell it is composing. */
    void stop() {
        running = false;
        worker.interrupt();
        try {
            worker.join(STOP_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The worker's loop: look again when the camera moves, then lay out a cell at a time. */
    private void run() {
        try {
            while (running) {
                final LodStore.SectionPos now = center;
                if (!now.equals(lookedFrom)) {
                    look(now);
                }
                if (queue.isEmpty() || composedAt.size() + justComposed.size() >= MAX_PENDING) {
                    Thread.sleep(IDLE_MILLIS);
                    continue;
                }
                layOutCell(queue.poll());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.error("Gate benchmark streaming failed", e);
        }
    }

    /**
     * Remove what is now too far, list what is missing nearest first, and do the same for lake
     * tiles.
     *
     * @param now The section the camera is in.
     */
    private void look(LodStore.@NonNull SectionPos now) {
        lookedFrom = now;
        for (LodStore.SectionPos far : beyond(known, now, radius)) {
            known.remove(far);
            partial.remove(far);
            for (LodStore.SectionPos section : sectionsOf(far)) {
                if (target.remove(section)) {
                    removed.incrementAndGet();
                }
            }
        }
        final List<LodStore.SectionPos> next = missing(known, now, radius);
        for (LodStore.SectionPos cell : partial) {
            if (within(cell, now, radius)) {
                next.add(cell);
            }
        }
        next.sort(Comparator.comparingLong(cell -> doubledDistanceSquared(cell, now)));
        queue.clear();
        queue.addAll(next);
        backlog.set(queue.size());
        lookAtTiles();
    }

    /** Add the lake tiles that come within the view distance and remove those past it. */
    private void lookAtTiles() {
        final double x = cameraX;
        final double z = cameraZ;
        final double reach = (double) radius * SyntheticTerrain.SECTION;
        final double keep = (double) (radius + REMOVE_MARGIN) * SyntheticTerrain.SECTION;
        final Iterator<Long> tiles = knownTiles.iterator();
        while (tiles.hasNext()) {
            final long key = tiles.next();
            final int startX = (int) (key >> Integer.SIZE);
            final int startZ = (int) key;
            if (!tileWithin(startX, startZ, tileSize, x, z, keep)) {
                tiles.remove();
                target.removeLakeTile(startX, startZ);
            }
        }
        final int first = Math.floorDiv((int) Math.floor(-reach), tileSize);
        final int last = Math.floorDiv((int) Math.ceil(reach), tileSize);
        final int baseX = Math.floorDiv((int) Math.floor(x), tileSize);
        final int baseZ = Math.floorDiv((int) Math.floor(z), tileSize);
        for (int tileZ = baseZ + first; tileZ <= baseZ + last; ++tileZ) {
            for (int tileX = baseX + first; tileX <= baseX + last; ++tileX) {
                final int startX = tileX * tileSize;
                final int startZ = tileZ * tileSize;
                if (tileWithin(startX, startZ, tileSize, x, z, reach)
                        && knownTiles.add(tileKey(startX, startZ))) {
                    target.addLakeTile(startX, startZ);
                }
            }
        }
    }

    /**
     * Lay out a cell's sections on every core and compose those not composed already.
     *
     * @param cell The cell, in cells.
     */
    private void layOutCell(LodStore.@NonNull SectionPos cell) {
        known.add(cell);
        partial.remove(cell);
        final List<LodStore.SectionPos> sections = new ArrayList<>();
        for (LodStore.SectionPos section : sectionsOf(cell)) {
            if (!target.isComposed(section)) {
                sections.add(section);
            }
        }
        final SyntheticTerrain.Placements[] placed =
                new SyntheticTerrain.Placements[sections.size()];
        IntStream.range(0, sections.size())
                .parallel()
                .forEach(
                        i -> {
                            final LodStore.SectionPos at = sections.get(i);
                            placed[i] = terrain.placements(at.x(), at.y(), at.z());
                        });
        for (int i = 0; i < sections.size(); ++i) {
            final SectionHandle handle = target.compose(sections.get(i), placed[i]);
            if (handle == null) {
                empty.incrementAndGet();
            } else {
                composed.incrementAndGet();
                justComposed.add(handle);
            }
        }
        backlog.set(queue.size());
    }
}
