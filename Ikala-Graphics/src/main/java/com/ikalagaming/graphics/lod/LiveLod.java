package com.ikalagaming.graphics.lod;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * The level of detail of a changing world, kept up to date in the background. Sections are put in
 * and taken out from any thread, each change carrying its section's bake generation; a builder
 * thread owns a {@link LodStore}, rebuilds the cells that changed, nearest the observer first, and
 * hands each rebuild to a {@link Sink} as a {@link Batch}: the cells built, with their cluster
 * meshes, the cells taken out, and which generation of each section the new cells show. An edit's
 * cell and every cell above it come in one batch; while many sections are loading, batches of the
 * nearest cells come first and their parents once no cell is left to build.
 *
 * <p>Cells above changed cells that aren't rebuilt yet are reported stale with every batch, so
 * whoever draws the cells can keep the cut from standing on them.
 *
 * <p>A level 0 cell is built only once none of its sections has a bake on the way, as {@link
 * #expect} says, and they have stopped changing for a while, except that an edit to the observer's
 * own cell once it is built doesn't wait for the while. Parents are built once no level 0 cell is
 * left to build and no bake is on the way. While an area loads, its cells are built with all their
 * sections rather than with whichever came first, so they match what a later load of the same area
 * builds and are read from the cache then instead of built again.
 */
@Slf4j
public final class LiveLod implements AutoCloseable {

    /** The most level 0 cells one batch builds, so near cells don't wait behind far ones. */
    public static final int CELLS_PER_BATCH = 8;

    /** How long a level 0 cell's sections must stay unchanged before it is built, by default. */
    public static final long SETTLE_NANOS = TimeUnit.MILLISECONDS.toNanos(500);

    /** How long the builder waits for changes before checking whether it was closed. */
    private static final long POLL_MILLIS = 100;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** What takes the batches, such as the renderer. */
    @FunctionalInterface
    public interface Sink {
        /**
         * Take a batch. Called on the builder thread, in order.
         *
         * @param batch The batch.
         */
        void publish(@NonNull Batch batch);
    }

    /**
     * One built cell, ready to draw.
     *
     * @param key The cell.
     * @param inputs How many of the cell's first clusters are its children's, which aren't part of
     *     it; its piece's cluster {@code i} is the cell's cluster {@code inputs + i}.
     * @param clusters Its own clusters, with what each gives way to.
     * @param meshes Each cluster's mesh, in the same order.
     */
    public record Piece(
            LodStore.@NonNull CellKey key,
            int inputs,
            @NonNull List<LodStore.PieceCluster> clusters,
            @NonNull List<ClusterMeshes.Mesh> meshes) {}

    /**
     * What one rebuild changed.
     *
     * @param added The cells built, replacing any with the same key.
     * @param removed The cells taken out.
     * @param tops For cells that weren't rebuilt, their top clusters with what consumes them now,
     *     after their parents were rebuilt; no meshes.
     * @param stale Every cell that is stale after this batch, see {@link LodStore.Rebuilt}.
     * @param covered The generation of each section the cells built or taken out now show.
     * @param buildNanos How long the rebuild took.
     * @param oldestChangeNanos When the oldest change it covers was made, by {@link
     *     System#nanoTime()}.
     */
    public record Batch(
            @NonNull List<Piece> added,
            @NonNull List<LodStore.CellKey> removed,
            @NonNull List<Piece> tops,
            @NonNull SortedSet<LodStore.CellKey> stale,
            @NonNull Map<LodStore.SectionPos, Long> covered,
            long buildNanos,
            long oldestChangeNanos) {}

    /**
     * A change to one section.
     *
     * @param position The section.
     * @param mesh Its opaque leaves, or null if it has none or was taken out.
     * @param generation Its bake generation.
     * @param at When the change was made.
     */
    private record Change(
            LodStore.@NonNull SectionPos position, LodMesh mesh, long generation, long at) {}

    /** Where the world's positions are measured from. */
    private final double[] origin;

    /** The section size, in world units. */
    private final double sectionSize;

    /** The cells. Builder thread only. */
    private final LodStore store;

    /** Where cells are read from and saved, or null. */
    private final LodCache cache;

    /** Where batches go. */
    private final Sink sink;

    /** Changes waiting for the builder. */
    private final LinkedBlockingQueue<Change> changes = new LinkedBlockingQueue<>();

    /** The newest generation of each section the store holds. Builder thread only. */
    private final Map<LodStore.SectionPos, Long> latest = new HashMap<>();

    /** When each section's oldest change not yet covered was made. Builder thread only. */
    private final Map<LodStore.SectionPos, Long> changedAt = new HashMap<>();

    /** The sections in each level 0 cell. Builder thread only. */
    private final Map<LodStore.CellKey, Set<LodStore.SectionPos>> byCell = new HashMap<>();

    /** Taken-out sections waiting for their cell's rebuild, with their generations. */
    private final Map<LodStore.SectionPos, Long> removedAt = new HashMap<>();

    /** Changes that left the store as it was, covered by the next batch. Builder thread only. */
    private final Map<LodStore.SectionPos, Long> coveredAsIs = new HashMap<>();

    /** The newest bake asked for of each section that hasn't been put in yet. */
    private final Map<LodStore.SectionPos, Long> expected = new ConcurrentHashMap<>();

    /** When each dirty level 0 cell's sections last changed. Builder thread only. */
    private final Map<LodStore.CellKey, Long> lastChange = new HashMap<>();

    /** How long a level 0 cell's sections must stay unchanged before it is built. */
    private volatile long settleNanos = SETTLE_NANOS;

    /** The time, as {@link System#nanoTime()}; tests replace it. */
    private LongSupplier clock = System::nanoTime;

    /** Where to build nearest to, in the world. */
    private volatile double[] near = new double[AXES];

    /** The builder thread, or null if batches are built with {@link #step()}. */
    private Thread builder;

    /** Whether it was closed. */
    private volatile boolean closed;

    /**
     * Set up a live world. Nothing is built until {@link #start()} or {@link #step()}.
     *
     * @param origin Where positions are measured from, in the world: a section corner.
     * @param sectionSize The section size, in world units.
     * @param options How to build cells.
     * @param sink Where batches go.
     */
    public LiveLod(
            double @NonNull [] origin,
            double sectionSize,
            DagBuilder.@NonNull Options options,
            @NonNull Sink sink) {
        this(origin, sectionSize, LodStore.DEFAULT_CELL_SECTIONS, options, sink);
    }

    /**
     * Set up a live world with cells of a given size. Nothing is built until {@link #start()} or
     * {@link #step()}.
     *
     * @param origin Where positions are measured from, in the world: a section corner.
     * @param sectionSize The section size, in world units.
     * @param cellSections Sections along each side of a level 0 cell.
     * @param options How to build cells.
     * @param sink Where batches go.
     */
    public LiveLod(
            double @NonNull [] origin,
            double sectionSize,
            int cellSections,
            DagBuilder.@NonNull Options options,
            @NonNull Sink sink) {
        this(origin, sectionSize, cellSections, options, sink, null);
    }

    /**
     * Set up a live world whose cells are read from and saved in a cache. Nothing is built until
     * {@link #start()} or {@link #step()}.
     *
     * @param origin Where positions are measured from, in the world: a section corner.
     * @param sectionSize The section size, in world units.
     * @param cellSections Sections along each side of a level 0 cell.
     * @param options How to build cells.
     * @param sink Where batches go.
     * @param cache Where cells are read from and saved, or null for none.
     */
    public LiveLod(
            double @NonNull [] origin,
            double sectionSize,
            int cellSections,
            DagBuilder.@NonNull Options options,
            @NonNull Sink sink,
            LodCache cache) {
        this.origin = origin.clone();
        this.sectionSize = sectionSize;
        this.cache = cache;
        store =
                new LodStore(
                        origin,
                        sectionSize,
                        cellSections,
                        LodStore.DEFAULT_STOP_AT,
                        options,
                        cache);
        this.sink = sink;
    }

    /**
     * What its cache has done.
     *
     * @return The numbers, or null without a cache.
     */
    public LodCache.Stats cacheStats() {
        return cache == null ? null : cache.stats();
    }

    /**
     * Set how long a level 0 cell's sections must stay unchanged before it is built. Safe from any
     * thread.
     *
     * @param nanos The time, in nanoseconds; 0 builds every changed cell at once.
     */
    public void setSettleNanos(long nanos) {
        settleNanos = nanos;
    }

    /**
     * Replace the clock changes are timed with, for tests. Before any change is put in.
     *
     * @param clock The time, in nanoseconds.
     */
    void setClock(@NonNull LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Where positions are measured from.
     *
     * @return The origin, x, y, z. Don't modify it.
     */
    public double[] getOrigin() {
        return origin;
    }

    /**
     * The level 0 cell a section is in. Safe from any thread.
     *
     * @param position The section.
     * @return The cell.
     */
    public LodStore.CellKey cellOf(LodStore.@NonNull SectionPos position) {
        return store.cellOf(position);
    }

    /**
     * Put in or replace a section's leaves. Safe from any thread.
     *
     * @param position The section.
     * @param mesh Its opaque leaves, whose origin is its corner, or null if it has none.
     * @param generation Its bake generation, which grows with each bake.
     */
    public void put(LodStore.@NonNull SectionPos position, LodMesh mesh, long generation) {
        changes.add(new Change(position, mesh, generation, clock.getAsLong()));
    }

    /**
     * Say a section's bake is on the way, so its cell waits for it rather than being built without
     * it and again with it. Safe from any thread.
     *
     * @param position The section.
     * @param generation The bake's generation.
     */
    public void expect(LodStore.@NonNull SectionPos position, long generation) {
        expected.merge(position, generation, Math::max);
    }

    /**
     * Say a bake that was expected won't come, as when it failed, so its cell doesn't wait for it.
     * Safe from any thread.
     *
     * @param position The section.
     * @param generation The bake's generation.
     */
    public void abandon(LodStore.@NonNull SectionPos position, long generation) {
        arrived(position, generation);
    }

    /**
     * Stop waiting for a section's bakes up to a generation.
     *
     * @param position The section.
     * @param generation The generation.
     */
    private void arrived(LodStore.@NonNull SectionPos position, long generation) {
        expected.computeIfPresent(
                position, (key, waiting) -> waiting <= generation ? null : waiting);
    }

    /**
     * Take a section out. Safe from any thread.
     *
     * @param position The section.
     * @param generation A generation past its last bake.
     */
    public void remove(LodStore.@NonNull SectionPos position, long generation) {
        put(position, null, generation);
    }

    /**
     * Say where the observer is, so nearer cells are built first. Safe from any thread.
     *
     * @param x The observer's x, in the world.
     * @param y Its y.
     * @param z Its z.
     */
    public void setNear(double x, double y, double z) {
        near = new double[] {x, y, z};
    }

    /** Start the builder thread. */
    public void start() {
        builder = new Thread(this::run, "LOD builder");
        builder.setDaemon(true);
        builder.start();
    }

    /** The builder thread's loop. */
    private void run() {
        while (!closed) {
            try {
                final Change first = changes.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (first != null) {
                    apply(first);
                }
                while (!closed && step()) {
                    // Keep building while there is work, taking new changes between batches
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                // Closing interrupts a build in progress, which is no failure
                if (closed) {
                    return;
                }
                log.error("Building level of detail failed", e);
            }
        }
    }

    /**
     * Take every waiting change and build one batch, if there is anything to build. The builder
     * thread does this; tests call it directly instead of starting it.
     *
     * @return True if a batch was published.
     */
    public boolean step() {
        Change change;
        while ((change = changes.poll()) != null) {
            apply(change);
        }
        final Predicate<LodStore.CellKey> ready = readyCells();
        // Parents wait for every section on the way, so they are built over all of them at once
        final boolean parentsReady = expected.isEmpty();
        if (!store.hasWork(ready, parentsReady) && coveredAsIs.isEmpty()) {
            return false;
        }
        final long start = System.nanoTime();
        final LodStore.Rebuilt rebuilt = store.rebuild(CELLS_PER_BATCH, near, ready, parentsReady);
        rebuilt.built().forEach(lastChange::remove);
        rebuilt.removed().forEach(lastChange::remove);
        final Map<LodStore.SectionPos, Long> covered = new HashMap<>(coveredAsIs);
        coveredAsIs.clear();
        final List<Piece> added = new ArrayList<>();
        for (LodStore.CellKey key : rebuilt.built()) {
            added.add(piece(key));
            cover(key, covered);
        }
        for (LodStore.CellKey key : rebuilt.removed()) {
            cover(key, covered);
        }
        final List<Piece> tops = new ArrayList<>();
        final boolean parentsBuilt = rebuilt.built().stream().anyMatch(key -> key.level() > 0);
        if (parentsBuilt || rebuilt.removed().stream().anyMatch(key -> key.level() > 0)) {
            final Set<LodStore.CellKey> built = new HashSet<>(rebuilt.built());
            for (LodStore.CellKey key : store.cellKeys()) {
                if (!built.contains(key)) {
                    tops.add(
                            new Piece(
                                    key, store.inputCount(key), store.topParents(key), List.of()));
                }
            }
        }
        long oldest = Long.MAX_VALUE;
        for (LodStore.SectionPos position : covered.keySet()) {
            final Long at = changedAt.remove(position);
            if (at != null) {
                oldest = Math.min(oldest, at);
            }
        }
        sink.publish(
                new Batch(
                        added,
                        rebuilt.removed(),
                        tops,
                        rebuilt.stale(),
                        covered,
                        System.nanoTime() - start,
                        oldest == Long.MAX_VALUE ? start : oldest));
        return true;
    }

    /**
     * Which dirty level 0 cells may be built now: those with no bake on the way whose sections have
     * settled, or that are the observer's own cell and were built before, so an edit there isn't
     * held back.
     *
     * @return The test.
     */
    private Predicate<LodStore.CellKey> readyCells() {
        final Set<LodStore.CellKey> waiting = new HashSet<>();
        for (LodStore.SectionPos position : expected.keySet()) {
            waiting.add(store.cellOf(position));
        }
        final long settle = settleNanos;
        if (settle <= 0) {
            return key -> !waiting.contains(key);
        }
        final long now = clock.getAsLong();
        final double[] point = near;
        final LodStore.CellKey own =
                store.cellOf(
                        new LodStore.SectionPos(
                                (int) Math.floor((point[0] - origin[0]) / sectionSize),
                                (int) Math.floor((point[1] - origin[1]) / sectionSize),
                                (int) Math.floor((point[2] - origin[2]) / sectionSize)));
        return key -> {
            if (waiting.contains(key)) {
                return false;
            }
            final Long changed = lastChange.get(key);
            return changed == null
                    || now - changed >= settle
                    || (key.equals(own) && store.contains(key));
        };
    }

    /**
     * Put one change into the store. Builder thread.
     *
     * @param change The change.
     */
    private void apply(@NonNull Change change) {
        final LodStore.SectionPos position = change.position();
        arrived(position, change.generation());
        final Long known = latest.get(position);
        if (known != null && known > change.generation()) {
            return;
        }
        changedAt.putIfAbsent(position, change.at());
        final LodStore.CellKey cell = store.cellOf(position);
        if (change.mesh() != null && change.mesh().getTriangleCount() > 0) {
            store.put(position, change.mesh());
            latest.put(position, change.generation());
            removedAt.remove(position);
            byCell.computeIfAbsent(cell, k -> new HashSet<>()).add(position);
            lastChange.merge(cell, change.at(), Math::max);
            return;
        }
        latest.remove(position);
        if (store.remove(position) || removedAt.containsKey(position)) {
            // Its cell is dirty: covered when that is rebuilt
            byCell.computeIfAbsent(cell, k -> new HashSet<>()).add(position);
            removedAt.put(position, change.generation());
            lastChange.merge(cell, change.at(), Math::max);
        } else {
            // It was never in the store, so the store already shows it
            coveredAsIs.put(position, change.generation());
        }
    }

    /**
     * Note the generation of every section of a level 0 cell built or taken out.
     *
     * @param key The cell.
     * @param covered Receives the generations.
     */
    private void cover(LodStore.@NonNull CellKey key, Map<LodStore.SectionPos, Long> covered) {
        if (key.level() != 0) {
            return;
        }
        final Set<LodStore.SectionPos> positions = byCell.get(key);
        if (positions == null) {
            return;
        }
        for (LodStore.SectionPos position : List.copyOf(positions)) {
            final Long generation = latest.get(position);
            if (generation != null) {
                covered.put(position, generation);
                continue;
            }
            final Long removed = removedAt.remove(position);
            if (removed != null) {
                covered.put(position, removed);
            }
            positions.remove(position);
        }
        if (positions.isEmpty()) {
            byCell.remove(key);
        }
    }

    /**
     * Turn a built cell into a piece, with a mesh per cluster.
     *
     * @param key The cell.
     * @return The piece.
     */
    private Piece piece(LodStore.@NonNull CellKey key) {
        final List<LodStore.PieceCluster> clusters = store.piece(key);
        final List<ClusterMeshes.Mesh> meshes = new ArrayList<>(clusters.size());
        for (LodStore.PieceCluster cluster : clusters) {
            meshes.add(ClusterMeshes.of(cluster.cluster()));
        }
        return new Piece(key, store.inputCount(key), clusters, meshes);
    }

    /**
     * Flatten the cells into one DAG, for tools and tests. Builder thread, or with it stopped.
     *
     * @return The DAG.
     */
    public LodDag snapshot() {
        return store.snapshot();
    }

    /** Stop the builder thread. Batches already published stay with their sink. */
    @Override
    public void close() {
        closed = true;
        if (builder != null) {
            builder.interrupt();
        }
    }
}
