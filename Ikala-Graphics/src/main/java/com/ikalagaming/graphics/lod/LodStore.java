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
     */
    private record Cell(@NonNull LodDag dag, @NonNull List<Input> inputs, double leafEdge) {

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
        final List<CellKey> built = new ArrayList<>();
        final SortedSet<CellKey> changed = new TreeSet<>();
        // Level 0 from sections
        for (CellKey key : dirty) {
            final List<LodMesh> inside = sectionsIn(key);
            if (inside.isEmpty()) {
                if (cells.remove(key) != null) {
                    changed.add(key);
                }
                continue;
            }
            final double[][] box = box(key);
            final LodDag dag =
                    DagBuilder.buildCell(inside, origin, box[0], box[1], stopAt, options);
            cells.put(key, new Cell(dag, List.of(), dag.getLeafEdge()));
            changed.add(key);
            built.add(key);
        }
        dirty.clear();
        // Parents, level by level, until one cell covers everything
        for (int level = 0; ; ++level) {
            final List<CellKey> atLevel = keysAt(level);
            if (atLevel.size() <= 1) {
                // This is the root, or there is nothing: no cell above it is needed any more
                final int root = atLevel.isEmpty() ? -1 : level;
                cells.keySet().removeIf(key -> key.level() > root);
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
        return built;
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
        return new Cell(dag, List.copyOf(inputs), leafEdge);
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
     * @return Their meshes, in position order.
     */
    private List<LodMesh> sectionsIn(@NonNull CellKey key) {
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
