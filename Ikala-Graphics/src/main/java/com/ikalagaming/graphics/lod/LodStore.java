package com.ikalagaming.graphics.lod;

import lombok.Getter;
import lombok.NonNull;

import java.util.*;

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
 * <p>Not thread safe: one thread changes and rebuilds it.
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

    /** How many child cells a parent spans along each axis. */
    private static final int BRANCHING = 2;

    /**
     * Added to level 0 cell indices so they are positive and parents halve them evenly. Its bits
     * alternate, so the planes between level k cells lie about 2^k / 3 cells from the origin, a
     * different place at each level: a fixed grid would have a plane through the origin at every
     * level, and an area straddling it would never come under one root.
     */
    static final int BIAS = 0x55555;

    /**
     * A section's place in the world, in sections.
     *
     * @param x Along x.
     * @param y Along y.
     * @param z Along z.
     */
    public record SectionPos(int x, int y, int z) implements Comparable<SectionPos> {
        @Override
        public int compareTo(@NonNull SectionPos o) {
            return Arrays.compare(new int[] {x, y, z}, new int[] {o.x, o.y, o.z});
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
    private record Cell(
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
    private record Input(@NonNull CellKey child, int cluster) {}

    /** Where positions are measured from, in the world. */
    private final double[] origin;

    /** Sections along each side of a level 0 cell. */
    @Getter private final int cellSections;

    /** The section size, in world units. */
    private final double sectionSize;

    /** Stop building a cell once this many clusters or fewer are left. */
    private final int stopAt;

    /** How to build. */
    private final DagBuilder.Options options;

    /** The sections, as leaf meshes. */
    private final SortedMap<SectionPos, LodMesh> sections = new TreeMap<>();

    /** The built cells. */
    private final SortedMap<CellKey, Cell> cells = new TreeMap<>();

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
     * @param section The section a leaf came from, or null above level 0.
     */
    public record PieceCluster(
            int local,
            LodDag.@NonNull Cluster cluster,
            double parentError,
            double @NonNull [] parentBound,
            CellKey parentCell,
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
        this.origin = origin.clone();
        this.sectionSize = sectionSize;
        this.cellSections = cellSections;
        this.stopAt = stopAt;
        this.options =
                new DagBuilder.Options(
                        options.virtualEdgeFactor(),
                        options.lockOpenBorder(),
                        options.threads(),
                        false,
                        options.visibilityReach());
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
        final List<CellKey> built = new ArrayList<>();
        final List<CellKey> removed = new ArrayList<>();
        final List<CellKey> order = new ArrayList<>(dirty);
        order.sort(Comparator.comparingDouble(key -> distanceSquared(key, near)));
        for (CellKey key : order.subList(0, Math.min(maxCells, order.size()))) {
            dirty.remove(key);
            final List<SectionPos> positions = new ArrayList<>();
            final List<LodMesh> inside = sectionsIn(key, positions);
            if (inside.isEmpty()) {
                if (cells.remove(key) != null) {
                    unparented.add(key);
                    removed.add(key);
                }
                continue;
            }
            final double[][] box = box(key);
            final LodDag dag =
                    DagBuilder.buildCell(inside, origin, box[0], box[1], stopAt, options);
            cells.put(key, new Cell(dag, List.of(), dag.getLeafEdge(), List.copyOf(positions)));
            unparented.add(key);
            built.add(key);
        }
        if (dirty.isEmpty() && !unparented.isEmpty()) {
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
                    removed.add(key);
                    changed.add(key);
                }
            }
            for (CellKey parent : parents) {
                if (cells.containsKey(parent) && !nextChanged.contains(parent)) {
                    continue;
                }
                cells.put(parent, buildParent(parent));
                changed.add(parent);
                built.add(parent);
            }
        }
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
                return new PieceCluster(local, cluster, g.error(), g.bound(), at, section);
            }
            final CellKey up = at.parent();
            final Cell upCell = cells.get(up);
            final int upLocal =
                    upCell == null ? -1 : upCell.inputs().indexOf(new Input(at, atLocal));
            if (upLocal < 0) {
                return new PieceCluster(
                        local, cluster, Double.POSITIVE_INFINITY, cluster.bound(), null, section);
            }
            at = up;
            atCell = upCell;
            atLocal = upLocal;
        }
    }

    /**
     * Build a parent cell from its children's tops.
     *
     * @param key The parent.
     * @return The cell.
     */
    private Cell buildParent(@NonNull CellKey key) {
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
        final double leafEdge = edges / children;
        final double[][] box = box(key);
        final LodDag dag =
                DagBuilder.buildParent(tops, leafEdge, origin, box[0], box[1], stopAt, options);
        return new Cell(dag, List.copyOf(inputs), leafEdge, List.of());
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
