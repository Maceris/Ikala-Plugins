package com.ikalagaming.graphics.lod;

import lombok.Getter;
import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * The level of detail of a changing world, kept as an octree of region cells so that loading,
 * unloading or editing a section rebuilds only a little of it. A cell at level 0 covers a cube of
 * sections and builds its own DAG from them ({@link DagBuilder#buildCell}); a cell at level k
 * covers two by two by two cells of level k - 1 and builds on from their top clusters ({@link
 * DagBuilder#buildParent}), up to one root cell over everything. Each cell locks its own boundary,
 * so cells meet exactly, and its parent unlocks the planes between its children.
 *
 * <p>A changed section marks its level 0 cell dirty; {@link #rebuild()} builds every dirty cell and
 * then every parent above one, bottom up. {@link #snapshot()} flattens the cells into one {@link
 * LodDag}, so cuts, crack checks and the prototype's views work on it unchanged.
 *
 * <p>With a {@link LodCache}, every cell has a key: a hash of everything it is built from, which
 * for a level 0 cell is its sections' leaf meshes and for a parent its children's keys, along with
 * the builder version and the options. A cell whose key is in the cache is read instead of built,
 * and every cell built is saved, so an area seen before loads without building.
 *
 * <p>Not thread safe: one thread changes and rebuilds it. A rebuild builds several cells at once on
 * workers of its own, each cell from inputs gathered beforehand, so the store's own state is only
 * touched by the thread calling it.
 */
public final class LodStore {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /**
     * Sections along each side of a level 0 cell by default: 64 m cells built in about a third less
     * time than one DAG over the same terrain, with about as few triangles at the top.
     */
    public static final int DEFAULT_CELL_SECTIONS = 4;

    /** Clusters a cell stops at by default, for its parent to take further. */
    public static final int DEFAULT_STOP_AT = 4;

    /**
     * The version of what building makes, part of every cache key. Bump it whenever a change to the
     * builder, the simplifier or anything else changes the cells built from the same input, so
     * cached cells from before are rebuilt.
     */
    public static final int BUILDER_VERSION = 7;

    /** How many child cells a parent spans along each axis. */
    private static final int BRANCHING = 2;

    /**
     * The options' threads for each cell built at once: a cell's build spends much of its time on
     * serial work and on its slowest groups, keeping well under one core busy, so more cells than
     * cores' worth keep the cores busy.
     */
    private static final int THREADS_PER_CELL_AT_ONCE = 2;

    /**
     * The fewest threads one cell's groups are built on, when as many cells as can be are built at
     * once; fewer cells at once, as for the last parents, each get more.
     */
    private static final int GROUP_THREADS = 4;

    /**
     * Added to level 0 cell indices so they are positive and parents halve them evenly. Its bits
     * alternate, so the planes between level k cells lie about 2^k / 3 cells from the origin, a
     * different place at each level: a fixed grid would have a plane through the origin at every
     * level, and an area straddling it would never come under one root.
     */
    static final int BIAS = 0x55555;

    /** Murmur3's constants for mixing a block into a hash. */
    private static final int MIX_C1 = 0xCC9E_2D51;

    /** Murmur3's second block constant. */
    private static final int MIX_C2 = 0x1B87_3593;

    /** How far Murmur3 turns a block. */
    private static final int MIX_ROTATE = 15;

    /** How far Murmur3 turns the hash after a block. */
    private static final int MIX_ROTATE_HASH = 13;

    /** Murmur3's per-block multiplier. */
    private static final int MIX_M = 5;

    /** Murmur3's per-block addend. */
    private static final int MIX_N = 0xE654_6B64;

    /** Murmur3's finalizer shifts and multipliers. */
    private static final int FINAL_SHIFT_1 = 16;

    /** Murmur3's finalizer's first multiplier. */
    private static final int FINAL_M1 = 0x85EB_CA6B;

    /** Murmur3's finalizer's middle shift. */
    private static final int FINAL_SHIFT_2 = 13;

    /** Murmur3's finalizer's second multiplier. */
    private static final int FINAL_M2 = 0xC2B2_AE35;

    /**
     * Mix one int into a Murmur3 hash.
     *
     * @param h The hash so far.
     * @param k The int.
     * @return The hash with it.
     */
    private static int mixIn(int h, int k) {
        final int mixed = h ^ Integer.rotateLeft(k * MIX_C1, MIX_ROTATE) * MIX_C2;
        return Integer.rotateLeft(mixed, MIX_ROTATE_HASH) * MIX_M + MIX_N;
    }

    /**
     * A well mixed hash of four ints, Murmur3's, for keys whose fields overlap in range: records'
     * own hashes, 31 times one plus the next, collide all over such keys and turn hash maps'
     * buckets into slow trees, as a cell's key and a group index in the hundreds did.
     *
     * @param a The first.
     * @param b The second.
     * @param c The third.
     * @param d The fourth.
     * @return The hash.
     */
    public static int hashOf(int a, int b, int c, int d) {
        int h = mixIn(mixIn(mixIn(mixIn(0, a), b), c), d);
        h ^= h >>> FINAL_SHIFT_1;
        h *= FINAL_M1;
        h ^= h >>> FINAL_SHIFT_2;
        h *= FINAL_M2;
        h ^= h >>> FINAL_SHIFT_1;
        return h;
    }

    /**
     * A section's place in the world, in sections, and its level: 0 for full detail, or a coarse
     * section of a level above, which covers 2^level sections along each side from its corner and
     * stands in for them at a distance. A coarse section and the sections it stands for can be in
     * the store at once, while one hands over to the other.
     *
     * @param x Along x, the corner's.
     * @param y Along y.
     * @param z Along z.
     * @param level 0 for full detail, higher for coarser.
     */
    public record SectionPos(int x, int y, int z, int level) implements Comparable<SectionPos> {

        @Override
        public int hashCode() {
            return hashOf(x, y, z, level);
        }

        /**
         * A full detail section's place.
         *
         * @param x Along x.
         * @param y Along y.
         * @param z Along z.
         */
        public SectionPos(int x, int y, int z) {
            this(x, y, z, 0);
        }

        @Override
        public int compareTo(@NonNull SectionPos o) {
            return Arrays.compare(new int[] {x, y, z, level}, new int[] {o.x, o.y, o.z, o.level});
        }
    }

    /**
     * A cell: its level and its place among the cells of that level.
     *
     * @param level 0 for cells built from sections.
     * @param x Along x, in cells of its level, offset by {@link #BIAS} at level 0.
     * @param y Along y.
     * @param z Along z.
     */
    public record CellKey(int level, int x, int y, int z) implements Comparable<CellKey> {

        /**
         * The cell above this one.
         *
         * @return The parent's key.
         */
        public CellKey parent() {
            return new CellKey(
                    level + 1,
                    Math.floorDiv(x, BRANCHING),
                    Math.floorDiv(y, BRANCHING),
                    Math.floorDiv(z, BRANCHING));
        }

        @Override
        public int compareTo(@NonNull CellKey o) {
            return Arrays.compare(new int[] {level, x, y, z}, new int[] {o.level, o.x, o.y, o.z});
        }
    }

    /**
     * One built cell.
     *
     * @param dag Its DAG. A parent's first clusters are its children's tops.
     * @param inputs Which child cluster each of a parent's first clusters is, empty at level 0.
     * @param leafEdge The average leaf edge length under it.
     * @param sections The section each leaf's section index stands for, empty above level 0.
     */
    record Cell(
            @NonNull LodDag dag,
            @NonNull List<Input> inputs,
            double leafEdge,
            @NonNull List<SectionPos> sections) {

        /**
         * Its top clusters: those nothing in it was made from.
         *
         * @return Their ids in its DAG.
         */
        int[] tops() {
            final List<Integer> out = new ArrayList<>();
            for (int id = 0; id < dag.clusters().size(); ++id) {
                if (dag.consumer(id) < 0) {
                    out.add(id);
                }
            }
            return out.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    /**
     * Which child cluster one of a parent's first clusters is.
     *
     * @param child The child cell.
     * @param cluster The cluster's id in the child's DAG.
     */
    record Input(@NonNull CellKey child, int cluster) {}

    /** Where positions are measured from, in the world. */
    private final double[] origin;

    /** Sections along each side of a level 0 cell. */
    @Getter private final int cellSections;

    /** The section size, in world units. */
    private final double sectionSize;

    /** Stop building a cell once this many clusters or fewer are left. */
    private final int stopAt;

    /** How to build, on all the threads the store was given. */
    private final DagBuilder.Options options;

    /** The sections, as leaf meshes. */
    private final SortedMap<SectionPos, LodMesh> sections = new TreeMap<>();

    /** The built cells. */
    private final SortedMap<CellKey, Cell> cells = new TreeMap<>();

    /** Where built cells are read from and saved, or null. */
    private final LodCache cache;

    /** Each built cell's cache key. */
    private final Map<CellKey, String> keys = new HashMap<>();

    /**
     * How many cells were built rather than read from the cache. -- GETTER -- Cells built.
     *
     * @return The count.
     */
    private final AtomicInteger builtCount = new AtomicInteger();

    /**
     * How many cells were read from the cache. -- GETTER -- Cells read.
     *
     * @return The count.
     */
    private final AtomicInteger loadedCount = new AtomicInteger();

    /**
     * How many cells are built at once. -- GETTER -- Cells built at once.
     *
     * @return The count.
     */
    @Getter private final int cellsAtOnce;

    /** Level 0 cells whose sections changed since they were built. */
    private final SortedSet<CellKey> dirty = new TreeSet<>();

    /** Level 0 cells rebuilt or removed since their parents were last built. */
    private final SortedSet<CellKey> unparented = new TreeSet<>();

    /**
     * What one rebuild did.
     *
     * @param built The cells built, bottom up.
     * @param removed The cells taken out.
     * @param stale Cells left in place that stand on children that changed since they were built,
     *     because their level 0 cells aren't all rebuilt yet: every ancestor of a changed level 0
     *     cell that is still there.
     */
    public record Rebuilt(
            @NonNull List<CellKey> built,
            @NonNull List<CellKey> removed,
            @NonNull SortedSet<CellKey> stale) {}

    /**
     * One of a cell's own clusters, as the GPU takes it: what it gives way to, and which section a
     * leaf came from.
     *
     * @param local Its id in the cell's DAG.
     * @param cluster The cluster.
     * @param parentError The error of the group that consumed it, infinity if none has.
     * @param parentBound That group's sphere, or the cluster's own if none has.
     * @param parentCell The cell holding that group, or null if none has.
     * @param parentGroup That group's id in its cell's DAG, or -1 if none has.
     * @param section The section a leaf came from, or null above level 0.
     */
    public record PieceCluster(
            int local,
            LodDag.@NonNull Cluster cluster,
            double parentError,
            double @NonNull [] parentBound,
            CellKey parentCell,
            int parentGroup,
            SectionPos section) {}

    /**
     * Start an empty store.
     *
     * @param origin Where positions are measured from, in the world: a section corner, so that
     *     section corners are exact.
     * @param sectionSize The section size, in world units.
     * @param cellSections Sections along each side of a level 0 cell.
     * @param stopAt Stop building a cell once this many clusters or fewer are left.
     * @param options How to build each cell; the lineage is never kept.
     */
    public LodStore(
            double @NonNull [] origin,
            double sectionSize,
            int cellSections,
            int stopAt,
            DagBuilder.@NonNull Options options) {
        this(origin, sectionSize, cellSections, stopAt, options, null);
    }

    /**
     * Start an empty store that reads and saves its cells in a cache.
     *
     * @param origin Where positions are measured from, in the world: a section corner, so that
     *     section corners are exact.
     * @param sectionSize The section size, in world units.
     * @param cellSections Sections along each side of a level 0 cell.
     * @param stopAt Stop building a cell once this many clusters or fewer are left.
     * @param options How to build each cell; the lineage is never kept.
     * @param cache Where cells are read from and saved, or null for none.
     */
    public LodStore(
            double @NonNull [] origin,
            double sectionSize,
            int cellSections,
            int stopAt,
            DagBuilder.@NonNull Options options,
            LodCache cache) {
        this.cache = cache;
        this.origin = origin.clone();
        this.sectionSize = sectionSize;
        this.cellSections = cellSections;
        this.stopAt = stopAt;
        // The options' threads go to cells built at once, each with a few for its groups; together
        // more threads than that, since most of them wait most of the time
        cellsAtOnce = Math.max(1, options.threads() / THREADS_PER_CELL_AT_ONCE);
        this.options =
                new DagBuilder.Options(
                        options.virtualEdgeFactor(),
                        options.lockOpenBorder(),
                        options.threads(),
                        false,
                        options.visibilityReach(),
                        options.mergePlanar(),
                        options.averages());
    }

    /**
     * Add or replace a section's leaf mesh.
     *
     * @param position The section.
     * @param leaves Its mesh, whose origin is the section's corner.
     */
    public void put(@NonNull SectionPos position, @NonNull LodMesh leaves) {
        sections.put(position, leaves);
        dirty.add(cellOf(position));
    }

    /**
     * Take a section out.
     *
     * @param position The section.
     * @return True if it was there.
     */
    public boolean remove(@NonNull SectionPos position) {
        if (sections.remove(position) == null) {
            return false;
        }
        dirty.add(cellOf(position));
        return true;
    }

    /**
     * Whether anything changed since the last rebuild.
     *
     * @return True if a rebuild has work to do.
     */
    public boolean isDirty() {
        return !dirty.isEmpty();
    }

    /**
     * Whether a rebuild has anything it may do now: a dirty level 0 cell that is ready, or parents
     * to build once no cell is left dirty.
     *
     * @param ready Which cells may be built now.
     * @param parentsReady Whether parents may be built.
     * @return True if a rebuild would build or take out a cell.
     */
    public boolean hasWork(@NonNull Predicate<CellKey> ready, boolean parentsReady) {
        if (dirty.isEmpty()) {
            return parentsReady && !unparented.isEmpty();
        }
        for (CellKey key : dirty) {
            if (ready.test(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * How many cells are built.
     *
     * @return The count, over all levels.
     */
    public int cellCount() {
        return cells.size();
    }

    /**
     * The level 0 cell a section is in.
     *
     * @param position The section.
     * @return The cell.
     */
    public CellKey cellOf(@NonNull SectionPos position) {
        return new CellKey(
                0,
                Math.floorDiv(position.x(), cellSections) + BIAS,
                Math.floorDiv(position.y(), cellSections) + BIAS,
                Math.floorDiv(position.z(), cellSections) + BIAS);
    }

    /**
     * Build every dirty cell, then every parent above a rebuilt or vanished cell, up to the root.
     *
     * @return The keys of the cells built, bottom up.
     */
    public List<CellKey> rebuild() {
        return rebuild(Integer.MAX_VALUE, new double[AXES]).built();
    }

    /**
     * Build up to some of the dirty level 0 cells, nearest a point first, and once none is left
     * dirty, every parent above a cell rebuilt or removed since parents were last built, up to the
     * root. Until then, the parents of changed cells are left as they were and reported stale.
     *
     * @param maxCells The most level 0 cells to build.
     * @param near The point to build nearest to, in the world.
     * @return What was built and taken out, and which cells are stale.
     */
    public Rebuilt rebuild(int maxCells, double @NonNull [] near) {
        return rebuild(maxCells, near, key -> true, true);
    }

    /**
     * Build up to some of the dirty level 0 cells that are ready, nearest a point first, and once
     * none is left dirty and parents may be built, every parent above a cell rebuilt or removed
     * since parents were last built, up to the root. Cells that aren't ready stay dirty, and their
     * parents wait for them.
     *
     * @param maxCells The most level 0 cells to build.
     * @param near The point to build nearest to, in the world.
     * @param ready Which dirty level 0 cells may be built now.
     * @param parentsReady Whether parents may be built, such as when no section is still to come.
     * @return What was built and taken out, and which cells are stale.
     */
    public Rebuilt rebuild(
            int maxCells,
            double @NonNull [] near,
            @NonNull Predicate<CellKey> ready,
            boolean parentsReady) {
        final List<CellKey> built = new ArrayList<>();
        final List<CellKey> removed = new ArrayList<>();
        final List<CellKey> order = new ArrayList<>();
        for (CellKey key : dirty) {
            if (ready.test(key)) {
                order.add(key);
            }
        }
        order.sort(Comparator.comparingDouble(key -> distanceSquared(key, near)));
        // What each cell is built from, gathered here, since the workers don't touch the store
        final List<LeafJob> jobs = new ArrayList<>();
        for (CellKey key : order.subList(0, Math.min(maxCells, order.size()))) {
            dirty.remove(key);
            final List<SectionPos> positions = new ArrayList<>();
            final List<LodMesh> inside = sectionsIn(key, positions);
            if (inside.isEmpty()) {
                if (cells.remove(key) != null) {
                    keys.remove(key);
                    unparented.add(key);
                    removed.add(key);
                }
                continue;
            }
            final String cacheKey = cache == null ? null : leafKey(key, inside, positions);
            jobs.add(new LeafJob(key, inside, List.copyOf(positions), cacheKey, box(key)));
        }
        final List<Cell> leaves = buildAll(jobs, this::buildLeaf);
        for (int i = 0; i < jobs.size(); ++i) {
            final LeafJob job = jobs.get(i);
            cells.put(job.key(), leaves.get(i));
            if (job.cacheKey() != null) {
                keys.put(job.key(), job.cacheKey());
            }
            unparented.add(job.key());
            built.add(job.key());
        }
        if (dirty.isEmpty() && parentsReady && !unparented.isEmpty()) {
            buildParents(built, removed);
            unparented.clear();
        }
        final SortedSet<CellKey> stale = new TreeSet<>();
        for (CellKey key : unparented) {
            for (CellKey up = key.parent(); cells.containsKey(up); up = up.parent()) {
                stale.add(up);
            }
        }
        return new Rebuilt(built, removed, stale);
    }

    /**
     * How far a point is from a level 0 cell's center, squared.
     *
     * @param key The cell.
     * @param point The point, in the world.
     * @return The distance squared.
     */
    private double distanceSquared(@NonNull CellKey key, double @NonNull [] point) {
        final double[][] box = box(key);
        double sum = 0;
        for (int axis = 0; axis < AXES; ++axis) {
            final double d = (box[0][axis] + box[1][axis]) / 2 + origin[axis] - point[axis];
            sum += d * d;
        }
        return sum;
    }

    /**
     * Build every parent above a level 0 cell changed since parents were last built, level by
     * level, until one cell covers everything.
     *
     * @param built Receives the cells built.
     * @param removed Receives the cells taken out.
     */
    private void buildParents(@NonNull List<CellKey> built, @NonNull List<CellKey> removed) {
        final SortedSet<CellKey> changed = new TreeSet<>(unparented);
        for (int level = 0; ; ++level) {
            final List<CellKey> atLevel = keysAt(level);
            if (atLevel.size() <= 1) {
                // This is the root, or there is nothing: no cell above it is needed any more
                final int root = atLevel.isEmpty() ? -1 : level;
                final Iterator<CellKey> it = cells.keySet().iterator();
                while (it.hasNext()) {
                    final CellKey key = it.next();
                    if (key.level() > root) {
                        it.remove();
                        keys.remove(key);
                        removed.add(key);
                    }
                }
                break;
            }
            final SortedSet<CellKey> parents = new TreeSet<>();
            for (CellKey key : atLevel) {
                parents.add(key.parent());
            }
            final SortedSet<CellKey> nextChanged = new TreeSet<>();
            for (CellKey key : changed) {
                if (key.level() == level) {
                    nextChanged.add(key.parent());
                }
            }
            // Parents no child needs any more, whose own parents then change
            for (CellKey key : keysAt(level + 1)) {
                if (!parents.contains(key)) {
                    cells.remove(key);
                    keys.remove(key);
                    removed.add(key);
                    changed.add(key);
                }
            }
            final List<ParentJob> jobs = new ArrayList<>();
            for (CellKey parent : parents) {
                if (cells.containsKey(parent) && !nextChanged.contains(parent)) {
                    continue;
                }
                jobs.add(parentJob(parent));
            }
            // The parents of a level stand on the level below, done, and not on each other
            final List<Cell> builtParents = buildAll(jobs, this::buildParent);
            for (int i = 0; i < jobs.size(); ++i) {
                final CellKey parent = jobs.get(i).key();
                cells.put(parent, builtParents.get(i));
                changed.add(parent);
                built.add(parent);
            }
        }
    }

    /**
     * A built cell.
     *
     * @param key The cell.
     * @return It, or null if it isn't built.
     */
    Cell built(@NonNull CellKey key) {
        return cells.get(key);
    }

    /**
     * A built cell's cache key.
     *
     * @param key The cell.
     * @return Its key, or null without a cache.
     */
    String cacheKey(@NonNull CellKey key) {
        return keys.get(key);
    }

    /**
     * Whether a cell is built.
     *
     * @param key The cell.
     * @return True if it is.
     */
    public boolean contains(@NonNull CellKey key) {
        return cells.containsKey(key);
    }

    /**
     * Roughly how many bytes it holds: its sections' meshes and its cells' DAGs, for memory
     * reports. Whoever owns the store calls it.
     *
     * @return The bytes.
     */
    public long heldBytes() {
        long bytes = 0;
        for (LodMesh mesh : sections.values()) {
            bytes += mesh.heldBytes();
        }
        for (Cell cell : cells.values()) {
            bytes += cell.dag().heldBytes();
        }
        return bytes;
    }

    /**
     * The built cells' keys.
     *
     * @return The keys, in order. Don't modify them.
     */
    public Set<CellKey> cellKeys() {
        return Collections.unmodifiableSet(cells.keySet());
    }

    /**
     * A cell's own clusters, as the GPU takes them: everything but its first clusters, which are
     * its children's, each with the group that consumed it, in this cell or above it. A cluster
     * that is a top of a cell and of its parent too is consumed further up, if anywhere.
     *
     * @param key The cell, which must be built.
     * @return Its clusters, in id order.
     */
    public List<PieceCluster> piece(@NonNull CellKey key) {
        final Cell cell = cells.get(key);
        final List<PieceCluster> out = new ArrayList<>();
        final LodDag dag = cell.dag();
        for (int local = cell.inputs().size(); local < dag.clusters().size(); ++local) {
            out.add(pieceCluster(key, cell, local));
        }
        return out;
    }

    /**
     * How many of a cell's first clusters are its children's, which its piece leaves out.
     *
     * @param key The cell, which must be built.
     * @return The count.
     */
    public int inputCount(@NonNull CellKey key) {
        return cells.get(key).inputs().size();
    }

    /**
     * A cell's own clusters that nothing in it consumed, with what consumes them now. After a
     * parent is rebuilt, these are what change in a child that wasn't.
     *
     * @param key The cell, which must be built.
     * @return Its top clusters.
     */
    public List<PieceCluster> topParents(@NonNull CellKey key) {
        final Cell cell = cells.get(key);
        final List<PieceCluster> out = new ArrayList<>();
        for (int local : cell.tops()) {
            if (local >= cell.inputs().size()) {
                out.add(pieceCluster(key, cell, local));
            }
        }
        return out;
    }

    /**
     * One of a cell's own clusters with the group that consumed it.
     *
     * @param key The cell.
     * @param cell The cell's data.
     * @param local The cluster's id in it.
     * @return The cluster.
     */
    private PieceCluster pieceCluster(@NonNull CellKey key, @NonNull Cell cell, int local) {
        final LodDag.Cluster cluster = cell.dag().clusters().get(local);
        final SectionPos section =
                key.level() == 0 && cluster.section() >= 0
                        ? cell.sections().get(cluster.section())
                        : null;
        // Up the cells until one consumes it
        CellKey at = key;
        Cell atCell = cell;
        int atLocal = local;
        while (true) {
            final int group = atCell.dag().consumer(atLocal);
            if (group >= 0) {
                final LodDag.Group g = atCell.dag().groups().get(group);
                return new PieceCluster(local, cluster, g.error(), g.bound(), at, group, section);
            }
            final CellKey up = at.parent();
            final Cell upCell = cells.get(up);
            final int upLocal =
                    upCell == null ? -1 : upCell.inputs().indexOf(new Input(at, atLocal));
            if (upLocal < 0) {
                return new PieceCluster(
                        local,
                        cluster,
                        Double.POSITIVE_INFINITY,
                        cluster.bound(),
                        null,
                        -1,
                        section);
            }
            at = up;
            atCell = upCell;
            atLocal = upLocal;
        }
    }

    /**
     * What a level 0 cell is built from.
     *
     * @param key The cell.
     * @param inside Its sections' meshes, in position order.
     * @param positions Their positions.
     * @param cacheKey Its cache key, or null without a cache.
     * @param box Its box, relative to the origin.
     */
    private record LeafJob(
            @NonNull CellKey key,
            @NonNull List<LodMesh> inside,
            @NonNull List<SectionPos> positions,
            String cacheKey,
            double @NonNull [] @NonNull [] box) {}

    /**
     * What a parent cell is built from.
     *
     * @param key The parent.
     * @param tops Its children's tops.
     * @param inputs Where each top came from.
     * @param leafEdge Its children's average leaf edge.
     * @param cacheKey Its cache key, or null without a cache.
     * @param box Its box, relative to the origin.
     */
    private record ParentJob(
            @NonNull CellKey key,
            @NonNull List<LodDag.Cluster> tops,
            @NonNull List<Input> inputs,
            double leafEdge,
            String cacheKey,
            double @NonNull [] @NonNull [] box) {}

    /**
     * Build cells, several at once on workers of their own when there is more than one, the results
     * in the same order. Interrupting the calling thread stops them.
     *
     * @param jobs What each cell is built from.
     * @param build Builds one cell with the options for each, and must not touch the store's state.
     * @param <J> The kind of job.
     * @return The cells.
     * @throws CancellationException If the calling thread was interrupted.
     * @throws IllegalStateException If a cell failed to build.
     */
    private <J> List<Cell> buildAll(
            @NonNull List<J> jobs, @NonNull BiFunction<J, DagBuilder.Options, Cell> build) {
        final List<Cell> out = new ArrayList<>(jobs.size());
        if (jobs.size() <= 1 || cellsAtOnce <= 1) {
            for (J job : jobs) {
                out.add(build.apply(job, options));
            }
            return out;
        }
        // The threads shared out between the cells built at once, so a few last cells get more
        final int atOnce = Math.min(cellsAtOnce, jobs.size());
        final DagBuilder.Options each =
                options.withThreads(
                        Math.min(
                                options.threads(),
                                Math.max(GROUP_THREADS, options.threads() / atOnce)));
        final ExecutorService pool =
                Executors.newFixedThreadPool(atOnce, DagBuilder.daemonThreads("LOD cell"));
        try {
            final List<Future<Cell>> futures = new ArrayList<>(jobs.size());
            for (J job : jobs) {
                futures.add(pool.submit(() -> build.apply(job, each)));
            }
            for (Future<Cell> future : futures) {
                out.add(future.get());
            }
            return out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Building level of detail cells was interrupted");
        } catch (ExecutionException e) {
            throw new IllegalStateException("A level of detail cell failed to build", e.getCause());
        } finally {
            // Interrupts the cells still building if one failed or this thread was interrupted
            pool.shutdownNow();
        }
    }

    /**
     * Build a level 0 cell, or read it from the cache. On a worker.
     *
     * @param job What it is built from.
     * @param each How to build it, on its share of the threads.
     * @return The cell.
     */
    private Cell buildLeaf(@NonNull LeafJob job, DagBuilder.@NonNull Options each) {
        final Cell loaded = cached(job.key(), job.cacheKey());
        if (loaded != null) {
            return loaded;
        }
        final LodDag dag =
                DagBuilder.buildCell(
                        job.inside(), origin, job.box()[0], job.box()[1], stopAt, each);
        final Cell cell = new Cell(dag, List.of(), dag.getLeafEdge(), job.positions());
        builtCount.incrementAndGet();
        save(job.key(), job.cacheKey(), cell);
        return cell;
    }

    /**
     * Gather what a parent cell is built from: its children's tops, which must all be built.
     *
     * @param key The parent.
     * @return What it is built from.
     */
    private ParentJob parentJob(@NonNull CellKey key) {
        final String cacheKey = cache == null ? null : parentKey(key);
        if (cacheKey != null) {
            keys.put(key, cacheKey);
        }
        final List<LodDag.Cluster> tops = new ArrayList<>();
        final List<Input> inputs = new ArrayList<>();
        double edges = 0;
        int children = 0;
        for (Map.Entry<CellKey, Cell> entry : cells.entrySet()) {
            final CellKey child = entry.getKey();
            if (child.level() != key.level() - 1 || !child.parent().equals(key)) {
                continue;
            }
            final Cell cell = entry.getValue();
            for (int id : cell.tops()) {
                tops.add(cell.dag().clusters().get(id));
                inputs.add(new Input(child, id));
            }
            edges += cell.leafEdge();
            ++children;
        }
        return new ParentJob(key, tops, inputs, edges / children, cacheKey, box(key));
    }

    /**
     * Build a parent cell from its children's tops, or read it from the cache. On a worker.
     *
     * @param job What it is built from.
     * @param each How to build it, on its share of the threads.
     * @return The cell.
     */
    private Cell buildParent(@NonNull ParentJob job, DagBuilder.@NonNull Options each) {
        final Cell loaded = cached(job.key(), job.cacheKey());
        if (loaded != null) {
            return loaded;
        }
        final LodDag dag =
                DagBuilder.buildParent(
                        job.tops(),
                        job.leafEdge(),
                        origin,
                        job.box()[0],
                        job.box()[1],
                        stopAt,
                        each);
        final Cell cell = new Cell(dag, List.copyOf(job.inputs()), job.leafEdge(), List.of());
        builtCount.incrementAndGet();
        save(job.key(), job.cacheKey(), cell);
        return cell;
    }

    /**
     * How many cells were built rather than read from the cache.
     *
     * @return The count.
     */
    public int getBuiltCount() {
        return builtCount.get();
    }

    /**
     * How many cells were read from the cache.
     *
     * @return The count.
     */
    public int getLoadedCount() {
        return loadedCount.get();
    }

    /**
     * Read a cell from the cache, if it is there and is the cell asked for.
     *
     * @param key The cell.
     * @param cacheKey Its cache key, or null without a cache.
     * @return The cell, or null.
     */
    private Cell cached(@NonNull CellKey key, String cacheKey) {
        if (cacheKey == null) {
            return null;
        }
        final LodCellFile.Stored stored = cache.load(cacheKey, BUILDER_VERSION);
        if (stored == null || !stored.key().equals(key)) {
            return null;
        }
        loadedCount.incrementAndGet();
        return stored.cell();
    }

    /**
     * Save a cell built, if there is a cache.
     *
     * @param key The cell.
     * @param cacheKey Its cache key, or null without a cache.
     * @param cell The cell.
     */
    private void save(@NonNull CellKey key, String cacheKey, @NonNull Cell cell) {
        if (cacheKey != null) {
            cache.save(cacheKey, BUILDER_VERSION, key, cell);
        }
    }

    /**
     * A level 0 cell's cache key: the builder version, the options, the cell, and every one of its
     * sections' leaf meshes, whole.
     *
     * @param key The cell.
     * @param inside Its sections' meshes, in position order.
     * @param positions Their positions.
     * @return The key.
     */
    private String leafKey(
            @NonNull CellKey key,
            @NonNull List<LodMesh> inside,
            @NonNull List<SectionPos> positions) {
        final KeyHash hash = startKey(key);
        hash.putInt(inside.size());
        for (int i = 0; i < inside.size(); ++i) {
            final SectionPos position = positions.get(i);
            hash.putInt(position.x()).putInt(position.y()).putInt(position.z());
            hash.putInt(position.level());
            final LodMesh mesh = inside.get(i);
            hash.putDouble(mesh.getLeafError());
            for (double value : mesh.getOrigin()) {
                hash.putDouble(value);
            }
            hash.putInt(mesh.getVertexCount());
            for (int v = 0; v < mesh.getVertexCount() * AXES; ++v) {
                hash.putFloat(mesh.positions[v]);
            }
            for (int v = 0; v < mesh.getVertexCount(); ++v) {
                hash.putInt(mesh.locked[v] ? 1 : 0);
            }
            hash.putInt(mesh.getTriangleCount());
            for (int t = 0; t < mesh.getTriangleCount() * LodMesh.CORNERS; ++t) {
                hash.putInt(mesh.triangles[t]);
            }
            for (int t = 0; t < mesh.getTriangleCount(); ++t) {
                hash.putInt(mesh.materials[t]);
                hash.putFloat(mesh.visibility[t]);
            }
            final int[] attributes = mesh.attributes;
            hash.putInt(attributes == null ? 0 : attributes.length);
            if (attributes != null) {
                for (int value : attributes) {
                    hash.putInt(value);
                }
            }
        }
        // What the leaves' materials average to, as far-out groups blend it: a changed texture
        // changes them
        final MaterialAverages averages = options.averages();
        hash.putInt(averages == null ? 0 : 1);
        if (averages != null) {
            final TreeSet<Integer> materials = new TreeSet<>();
            for (LodMesh mesh : inside) {
                for (int t = 0; t < mesh.getTriangleCount(); ++t) {
                    materials.add(mesh.materials[t]);
                }
            }
            for (int material : materials) {
                hash.putInt(material);
                final float[] average = averages.average(material);
                hash.putInt(average == null ? 0 : average.length);
                if (average != null) {
                    for (float value : average) {
                        hash.putFloat(value);
                    }
                }
            }
        }
        return hash.hex();
    }

    /**
     * A parent cell's cache key: the builder version, the options, the cell, and its children's
     * keys, in order.
     *
     * @param key The parent.
     * @return The key, or null if a child has none.
     */
    private String parentKey(@NonNull CellKey key) {
        final KeyHash hash = startKey(key);
        for (CellKey child : cells.keySet()) {
            if (child.level() != key.level() - 1 || !child.parent().equals(key)) {
                continue;
            }
            final String childKey = keys.get(child);
            if (childKey == null) {
                return null;
            }
            hash.putInt(child.level()).putInt(child.x()).putInt(child.y()).putInt(child.z());
            hash.putString(childKey);
        }
        return hash.hex();
    }

    /**
     * Start a cache key with what every cell's key holds.
     *
     * @param key The cell.
     * @return The hash so far.
     */
    private KeyHash startKey(@NonNull CellKey key) {
        final KeyHash hash = new KeyHash();
        hash.putInt(BUILDER_VERSION);
        hash.putDouble(options.virtualEdgeFactor());
        hash.putInt(options.lockOpenBorder() ? 1 : 0);
        hash.putDouble(options.visibilityReach());
        hash.putInt(options.mergePlanar() ? 1 : 0);
        hash.putDouble(sectionSize).putInt(cellSections).putInt(stopAt);
        for (double value : origin) {
            hash.putDouble(value);
        }
        hash.putInt(key.level()).putInt(key.x()).putInt(key.y()).putInt(key.z());
        return hash;
    }

    /** A SHA-256 hash fed values in little endian, through a buffer. */
    private static final class KeyHash {
        /** Bytes buffered before they go to the digest. */
        private static final int BUFFER = 8192;

        /** The digest. */
        private final MessageDigest digest;

        /** Values waiting for the digest. */
        private final ByteBuffer buffer =
                ByteBuffer.allocate(BUFFER).order(ByteOrder.LITTLE_ENDIAN);

        /** Start a hash. */
        KeyHash() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 isn't available", e);
            }
        }

        /**
         * Make room for some bytes.
         *
         * @param bytes How many.
         */
        private void room(int bytes) {
            if (buffer.remaining() < bytes) {
                digest.update(buffer.array(), 0, buffer.position());
                buffer.clear();
            }
        }

        /**
         * Add an int.
         *
         * @param value The value.
         * @return This hash.
         */
        KeyHash putInt(int value) {
            room(Integer.BYTES);
            buffer.putInt(value);
            return this;
        }

        /**
         * Add a float, by its bits.
         *
         * @param value The value.
         * @return This hash.
         */
        KeyHash putFloat(float value) {
            room(Float.BYTES);
            buffer.putInt(Float.floatToIntBits(value));
            return this;
        }

        /**
         * Add a double, by its bits.
         *
         * @param value The value.
         * @return This hash.
         */
        KeyHash putDouble(double value) {
            room(Double.BYTES);
            buffer.putLong(Double.doubleToLongBits(value));
            return this;
        }

        /**
         * Add a string, with its length.
         *
         * @param value The value.
         * @return This hash.
         */
        KeyHash putString(@NonNull String value) {
            final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            putInt(bytes.length);
            for (byte b : bytes) {
                room(1);
                buffer.put(b);
            }
            return this;
        }

        /**
         * Finish the hash.
         *
         * @return It, in lowercase hex.
         */
        String hex() {
            digest.update(buffer.array(), 0, buffer.position());
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    /**
     * The cells of one level.
     *
     * @param level The level.
     * @return Their keys, in order.
     */
    private List<CellKey> keysAt(int level) {
        final List<CellKey> out = new ArrayList<>();
        for (CellKey key : cells.keySet()) {
            if (key.level() == level) {
                out.add(key);
            }
        }
        return out;
    }

    /**
     * The sections inside a level 0 cell.
     *
     * @param key The cell.
     * @param positions Receives their positions, in the same order.
     * @return Their meshes, in position order.
     */
    private List<LodMesh> sectionsIn(@NonNull CellKey key, @NonNull List<SectionPos> positions) {
        final SectionPos from =
                new SectionPos(
                        (key.x() - BIAS) * cellSections,
                        (key.y() - BIAS) * cellSections,
                        (key.z() - BIAS) * cellSections);
        final List<LodMesh> out = new ArrayList<>();
        for (Map.Entry<SectionPos, LodMesh> entry : sections.tailMap(from).entrySet()) {
            final SectionPos position = entry.getKey();
            if (position.x() >= from.x() + cellSections) {
                break;
            }
            if (cellOf(position).equals(key)) {
                out.add(entry.getValue());
                positions.add(position);
            }
        }
        return out;
    }

    /**
     * A cell's corners, relative to the origin.
     *
     * @param key The cell.
     * @return The minimum corner, then the maximum.
     */
    private double[][] box(@NonNull CellKey key) {
        final double cell = sectionSize * cellSections;
        final long span = 1L << key.level();
        final int[] at = {key.x(), key.y(), key.z()};
        final double[] min = new double[AXES];
        final double[] max = new double[AXES];
        for (int axis = 0; axis < AXES; ++axis) {
            // The level 0 cells it covers, without the bias
            final long first = at[axis] * span - BIAS;
            min[axis] = first * cell;
            max[axis] = (first + span) * cell;
        }
        return new double[][] {min, max};
    }

    /**
     * Flatten every cell into one DAG: a parent's first clusters become the child clusters they
     * stand for, so each child top is consumed by the parent group that took it.
     *
     * @return The DAG, empty if there are no cells.
     */
    public LodDag snapshot() {
        final LodDag out = new LodDag(origin.clone());
        final Map<CellKey, int[]> globalIds = new HashMap<>();
        double edges = 0;
        for (Map.Entry<CellKey, Cell> entry : cells.entrySet()) {
            final Cell cell = entry.getValue();
            final LodDag dag = cell.dag();
            final int[] ids = new int[dag.clusters().size()];
            final int groupBase = out.groups().size();
            for (int local = 0; local < ids.length; ++local) {
                if (local < cell.inputs().size()) {
                    final Input input = cell.inputs().get(local);
                    ids[local] = globalIds.get(input.child())[input.cluster()];
                    continue;
                }
                final LodDag.Cluster c = dag.clusters().get(local);
                ids[local] = out.clusters().size();
                out.add(
                        new LodDag.Cluster(
                                ids[local],
                                c.level(),
                                c.corners(),
                                c.materials(),
                                c.visibility(),
                                c.attributes(),
                                c.error(),
                                c.bound(),
                                c.tight(),
                                c.producer() < 0 ? -1 : groupBase + c.producer(),
                                c.section()));
            }
            for (LodDag.Group g : dag.groups()) {
                out.add(
                        new LodDag.Group(
                                groupBase + g.id(),
                                g.level(),
                                remap(g.children(), ids),
                                remap(g.outputs(), ids),
                                g.error(),
                                g.bound(),
                                g.simplifyError(),
                                g.quadricError(),
                                g.trianglesIn(),
                                g.trianglesOut(),
                                g.borderEdges(),
                                g.edges(),
                                g.virtualEdges(),
                                g.rejectedFlips(),
                                g.rejectedLinks(),
                                g.buildNanos()));
            }
            globalIds.put(entry.getKey(), ids);
            edges += cell.leafEdge();
        }
        out.setLeafEdge(cells.isEmpty() ? 1 : edges / cells.size());
        out.setStopReason(cells.size() + " cells");
        return out;
    }

    /**
     * Map local ids to global ones.
     *
     * @param local The local ids.
     * @param ids Each local id's global one.
     * @return The global ids.
     */
    private static int[] remap(int @NonNull [] local, int @NonNull [] ids) {
        final int[] out = new int[local.length];
        for (int i = 0; i < local.length; ++i) {
            out[i] = ids[local[i]];
        }
        return out;
    }
}
