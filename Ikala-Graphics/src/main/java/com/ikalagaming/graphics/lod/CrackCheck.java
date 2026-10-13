package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks a cut for cracks. Clusters drawn at different levels meet along group borders, which were
 * locked while each group was simplified, so there they share exactly the same edges. A crack is an
 * edge where they don't meet: an edge the cut uses once although the level that drew it uses it
 * twice. An edge the cut uses once that its own level also uses once is that level's rim, the open
 * edge of the input as that level simplified it, which nothing is drawn beyond. This holds whether
 * or not the rim was locked. An edge used more often in the cut than in its own level means parts
 * are drawn twice.
 *
 * <p>Each level's edges are recorded when the check is made, so it compares cuts against the DAG as
 * it was built. A level is every cluster that stands for its part of the input there: those on the
 * level, and those below it whose consumer makes a higher level, as happens where region cells stop
 * at different levels and their parent takes over. In a DAG built in one piece, that is just the
 * level's own clusters.
 *
 * <p>Where parts meet at T-junctions, an edge used once can still be closed: another triangle
 * touches it all along, so the surface goes on past it. Faces that end against the middle of
 * another meet that way, as a slab's side does against a wall, and so do meshes merged on their own
 * that meet without sharing corners. An edge the cut uses once is no crack where another drawn
 * triangle touches it all along, and its level's rim is what nothing in its level touches. Without
 * T-junctions this is the same as counting uses.
 */
public final class CrackCheck {

    /**
     * What a check found.
     *
     * @param openEdges Edges the cut uses once that the level drawing them uses twice: cracks.
     * @param overdrawnEdges Edges the cut uses more often than the level drawing them does.
     * @param drawnClusters How many clusters the cut draws.
     */
    public record Result(
            @NonNull List<Grouping.Edge> openEdges,
            @NonNull List<Grouping.Edge> overdrawnEdges,
            int drawnClusters) {

        /**
         * Whether the cut is crack free.
         *
         * @return True if nothing was found.
         */
        public boolean isClean() {
            return openEdges.isEmpty() && overdrawnEdges.isEmpty();
        }
    }

    /**
     * What a check of a whole world, column by column, found.
     *
     * @param openEdges Cracks: edges the cut uses once that the level drawing them uses twice.
     * @param overdrawnEdges Edges the cut uses more often than the level drawing them does.
     * @param drawnClusters How many clusters the cut draws.
     * @param checkedEdges How many distinct edges the cut draws that were checked.
     * @param longEdges Edges longer than a column, which weren't checked.
     * @param excusedEdges Open edges on the box of a section drawn some other way, not counted.
     * @param examples The middles of up to {@link #EXAMPLES} edges found, x, y, z.
     */
    public record Tiled(
            long openEdges,
            long overdrawnEdges,
            int drawnClusters,
            long checkedEdges,
            long longEdges,
            long excusedEdges,
            @NonNull List<double[]> examples) {

        /**
         * Whether the cut is crack free.
         *
         * @return True if nothing was found.
         */
        public boolean isClean() {
            return openEdges == 0 && overdrawnEdges == 0;
        }
    }

    /**
     * How far past a column, as a part of its width, edges are counted that may meet the column's
     * edges along a line.
     */
    private static final double NEAR_COLUMN = 0.25;

    /** How many findings a tiled check keeps as examples. */
    public static final int EXAMPLES = 16;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Values in a sphere. */
    private static final int SPHERE_VALUES = 4;

    /** How far outside a box an edge's ends may be and still be on it, in world units. */
    private static final double BOX_SLACK = 1e-3;

    /** The low 32 bits of a long. */
    private static final long LOW_BITS = 0xFFFF_FFFFL;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = Clusters.TRIANGLE_VALUES;

    /** How many triangles use each edge in the whole of each level, by level. */
    private final List<Map<Grouping.Edge, Integer>> levelUses = new ArrayList<>();

    /** The triangles of the whole of each level, by level. */
    private final List<Surface> levelSurfaces = new ArrayList<>();

    /**
     * Triangles to find what touches an edge in, numbered by their clusters' keys and their index
     * in each.
     */
    private static final class Surface {
        /** The triangles. */
        private final TriangleBvh tree;

        /** Where each cluster's triangles start, by its key. */
        private final Map<Integer, Integer> starts = new HashMap<>();

        /**
         * Gather some clusters' triangles.
         *
         * @param clusters The clusters to pick from.
         * @param keys Which ones, by index in {@code clusters}, which also becomes their key.
         */
        Surface(@NonNull List<LodDag.Cluster> clusters, @NonNull Collection<Integer> keys) {
            int total = 0;
            for (int key : keys) {
                total += clusters.get(key).corners().length;
            }
            final float[] all = new float[total];
            int at = 0;
            for (int key : keys) {
                final float[] corners = clusters.get(key).corners();
                starts.put(key, at / TRIANGLE_VALUES);
                System.arraycopy(corners, 0, all, at, corners.length);
                at += corners.length;
            }
            tree = new TriangleBvh(all);
        }

        /**
         * Whether a triangle other than the edge's own touches it all along.
         *
         * @param from The edge's start, x, y, z.
         * @param to Its end.
         * @param key The key of the cluster the edge's triangle is in.
         * @param triangle The triangle's index in it.
         * @param scratch Room for the queries.
         * @return True if it does.
         */
        boolean touches(
                double @NonNull [] from,
                double @NonNull [] to,
                int key,
                int triangle,
                TriangleBvh.@NonNull Scratch scratch) {
            final int skip = starts.get(key) + triangle;
            final double touch = TJunctions.TOUCH * TJunctions.TOUCH;
            final int samples = TJunctions.samples(from, to);
            final double[] point = new double[AXES];
            for (int k = 0; k < samples; ++k) {
                TJunctions.sample(from, to, k, samples, point);
                if (tree.nearest(point, touch, touch, skip, scratch) >= touch) {
                    return false;
                }
            }
            return true;
        }
    }

    /** The DAG. */
    private final LodDag dag;

    /**
     * Prepare to check cuts of a DAG.
     *
     * @param dag The DAG.
     */
    public CrackCheck(@NonNull LodDag dag) {
        this.dag = dag;
        List<List<Integer>> byLevel = new ArrayList<>();
        for (int level = 0; level < dag.getLevels(); ++level) {
            byLevel.add(new ArrayList<>());
        }
        for (LodDag.Cluster cluster : dag.clusters()) {
            for (int level = cluster.level(); level <= highestStanding(cluster); ++level) {
                byLevel.get(level).add(cluster.id());
            }
        }
        for (List<Integer> clusters : byLevel) {
            levelUses.add(Grouping.edgeUses(dag, clusters));
            levelSurfaces.add(new Surface(dag.clusters(), clusters));
        }
    }

    /**
     * A corner's position.
     *
     * @param corners Corner positions.
     * @param at Where the corner starts.
     * @return x, y, z.
     */
    private static double[] point(float @NonNull [] corners, int at) {
        return new double[] {corners[at], corners[at + 1], corners[at + 2]};
    }

    /**
     * The highest level a cluster stands for its part of the input at: below the level its consumer
     * makes, or the top for a root.
     *
     * @param cluster The cluster.
     * @return The level.
     */
    private int highestStanding(LodDag.@NonNull Cluster cluster) {
        final int group = dag.consumer(cluster.id());
        if (group < 0) {
            return dag.getLevels() - 1;
        }
        return dag.groups().get(group).level();
    }

    /**
     * Check the cut of a whole world in vertical columns, so the edges of only one column are held
     * at a time. Each edge is checked in the column its midpoint is in, against every cluster near
     * enough to use it; edges longer than a column are counted and left out. The clusters are any
     * set sharing one origin, such as every cell of a live world, each with the highest level it
     * stands for its part of the input at.
     *
     * @param clusters The clusters.
     * @param highest The highest level each stands at: below the level its consumer makes, or the
     *     top for a root.
     * @param drawn Whether each is drawn.
     * @param column The width of a column, in world units.
     * @param excused Boxes, min x, y, z then max x, y, z, of sections drawn some other way: open
     *     edges with both ends on one are where that drawing takes over, not cracks.
     * @return What was found.
     */
    public static Tiled checkTiled(
            @NonNull List<LodDag.Cluster> clusters,
            int @NonNull [] highest,
            boolean @NonNull [] drawn,
            double column,
            @NonNull Collection<double[]> excused) {
        // Each column's clusters, by the box around their spheres widened by a column
        final Map<Long, List<Integer>> columns = new HashMap<>();
        int drawnCount = 0;
        for (int i = 0; i < clusters.size(); ++i) {
            drawnCount += drawn[i] ? 1 : 0;
            final double[] sphere = clusters.get(i).tight();
            final double reach = sphere[SPHERE_VALUES - 1] + column;
            final int x0 = (int) Math.floor((sphere[0] - reach) / column);
            final int x1 = (int) Math.floor((sphere[0] + reach) / column);
            final int z0 = (int) Math.floor((sphere[2] - reach) / column);
            final int z1 = (int) Math.floor((sphere[2] + reach) / column);
            for (int x = x0; x <= x1; ++x) {
                for (int z = z0; z <= z1; ++z) {
                    columns.computeIfAbsent(columnKey(x, z), k -> new ArrayList<>()).add(i);
                }
            }
        }
        long open = 0;
        long overdrawn = 0;
        long checked = 0;
        long excusedCount = 0;
        final List<double[]> examples = new ArrayList<>();
        final long[] longEdges = new long[1];
        for (Map.Entry<Long, List<Integer>> entry : columns.entrySet()) {
            final long key = entry.getKey();
            final List<Integer> members = entry.getValue();
            // Uses of the edges near the column in each level, and in the cut, with the first
            // triangle using each
            final Map<Integer, Map<Grouping.Edge, Use>> levelUses = new HashMap<>();
            final Map<Grouping.Edge, Use> cutUses = new HashMap<>();
            for (int i : members) {
                final LodDag.Cluster cluster = clusters.get(i);
                final int top = highest[i];
                final boolean inCut = drawn[i];
                final int owner = i;
                forEachEdge(
                        cluster,
                        key,
                        column,
                        null,
                        false,
                        (edge, corners, from, to, triangle) -> {
                            for (int level = cluster.level(); level <= top; ++level) {
                                levelUses
                                        .computeIfAbsent(level, l -> new HashMap<>())
                                        .computeIfAbsent(edge, e -> new Use())
                                        .add(corners, from, to, owner, triangle);
                            }
                            if (inCut) {
                                cutUses.computeIfAbsent(edge, e -> new Use())
                                        .add(corners, from, to, owner, triangle);
                            }
                        });
            }
            // The most any level drawing an edge uses it, and a level that uses it once
            final Map<Grouping.Edge, Integer> allowed = new HashMap<>();
            final Map<Grouping.Edge, Integer> onceIn = new HashMap<>();
            for (int i : members) {
                if (!drawn[i]) {
                    continue;
                }
                final LodDag.Cluster cluster = clusters.get(i);
                final int top = highest[i];
                forEachEdge(
                        cluster,
                        key,
                        column,
                        null,
                        true,
                        (edge, corners, from, to, triangle) -> {
                            for (int level = cluster.level(); level <= top; ++level) {
                                final Use use = levelUses.get(level).get(edge);
                                final int count = use == null ? 0 : use.count;
                                allowed.merge(edge, count, Math::max);
                                if (count == 1) {
                                    onceIn.put(edge, level);
                                }
                            }
                        });
            }
            // What touches an edge, in the cut and in each level, made when first needed: first
            // the edges along its line, which settle most, then the triangles
            final TriangleBvh.Scratch scratch = new TriangleBvh.Scratch();
            final TJunctions[] cutLines = new TJunctions[1];
            final Surface[] cutSurface = new Surface[1];
            final Map<Integer, TJunctions> levelLines = new HashMap<>();
            final Map<Integer, Surface> levelSurfaces = new HashMap<>();
            for (Map.Entry<Grouping.Edge, Use> edgeUse : cutUses.entrySet()) {
                if (!inColumn(edgeUse.getKey(), key, column)) {
                    continue;
                }
                if (tooLong(edgeUse.getKey(), column)) {
                    ++longEdges[0];
                    continue;
                }
                ++checked;
                final Use use = edgeUse.getValue();
                final int most = allowed.getOrDefault(edgeUse.getKey(), 0);
                if (use.count == 1) {
                    if (most == 1) {
                        // Its level's rim, unless something there touches it
                        final int level = onceIn.get(edgeUse.getKey());
                        final Use own = levelUses.get(level).get(edgeUse.getKey());
                        final boolean along =
                                levelLines
                                        .computeIfAbsent(level, l -> lines(levelUses.get(l)))
                                        .covered(own.from(), own.to());
                        final Surface surface =
                                along
                                        ? null
                                        : levelSurfaces.computeIfAbsent(
                                                level,
                                                l -> {
                                                    final List<Integer> standing =
                                                            new ArrayList<>();
                                                    for (int i : members) {
                                                        if (clusters.get(i).level() <= l
                                                                && l <= highest[i]) {
                                                            standing.add(i);
                                                        }
                                                    }
                                                    return new Surface(clusters, standing);
                                                });
                        if (!along
                                && !surface.touches(
                                        own.from(), own.to(), own.owner, own.triangle, scratch)) {
                            continue;
                        }
                    }
                    if (cutLines[0] == null) {
                        cutLines[0] = lines(cutUses);
                    }
                    if (cutLines[0].covered(use.from(), use.to())) {
                        continue;
                    }
                    if (cutSurface[0] == null) {
                        final List<Integer> inCut = new ArrayList<>();
                        for (int i : members) {
                            if (drawn[i]) {
                                inCut.add(i);
                            }
                        }
                        cutSurface[0] = new Surface(clusters, inCut);
                    }
                    if (cutSurface[0].touches(
                            use.from(), use.to(), use.owner, use.triangle, scratch)) {
                        continue;
                    }
                    if (onAny(edgeUse.getKey(), excused)) {
                        ++excusedCount;
                        continue;
                    }
                    ++open;
                    if (examples.size() < EXAMPLES) {
                        examples.add(middle(edgeUse.getKey()));
                    }
                } else if (use.count > Math.max(most, 1)) {
                    ++overdrawn;
                    if (examples.size() < EXAMPLES) {
                        examples.add(middle(edgeUse.getKey()));
                    }
                }
            }
        }
        return new Tiled(
                open, overdrawn, drawnCount, checked, longEdges[0], excusedCount, examples);
    }

    /**
     * A column's key.
     *
     * @param x Its x, in columns.
     * @param z Its z, in columns.
     * @return The key.
     */
    private static long columnKey(int x, int z) {
        return ((long) x << Integer.SIZE) | (z & LOW_BITS);
    }

    /** Something done with each edge in a column. */
    @FunctionalInterface
    private interface EdgeVisitor {
        /**
         * Visit an edge.
         *
         * @param edge The edge.
         * @param corners The corners of the cluster using it.
         * @param from Where the corner the triangle runs it from starts in them.
         * @param to Where the corner it runs it to starts.
         * @param triangle The triangle's index in its cluster.
         */
        void visit(
                Grouping.@NonNull Edge edge,
                float @NonNull [] corners,
                int from,
                int to,
                int triangle);
    }

    /** How often an edge is used, and the first triangle using it. */
    private static final class Use {
        /** How many triangles use it. */
        int count;

        /** The corners of the first one's cluster. */
        float[] corners;

        /** Where the corner the first one runs it from starts in them. */
        int start;

        /** Where the corner it runs it to starts. */
        int end;

        /** The key of the first one's cluster. */
        int owner;

        /** The first one's index in its cluster. */
        int triangle;

        /**
         * Count one more use.
         *
         * @param of The corners of its triangle's cluster.
         * @param from Where the corner it runs it from starts in them.
         * @param to Where the corner it runs it to starts.
         * @param cluster The key of its triangle's cluster.
         * @param index Its triangle's index there.
         */
        void add(float @NonNull [] of, int from, int to, int cluster, int index) {
            if (count++ == 0) {
                corners = of;
                start = from;
                end = to;
                owner = cluster;
                triangle = index;
            }
        }

        /**
         * Where the first triangle runs the edge from.
         *
         * @return x, y, z.
         */
        double[] from() {
            return point(corners, start);
        }

        /**
         * Where the first triangle runs the edge to.
         *
         * @return x, y, z.
         */
        double[] to() {
            return point(corners, end);
        }
    }

    /**
     * File edges by line: each the way its first triangle runs it, and the other way too if more
     * than one triangle uses it.
     *
     * @param uses The edges' uses.
     * @return The edges.
     */
    private static TJunctions lines(@NonNull Map<Grouping.Edge, Use> uses) {
        final TJunctions out = new TJunctions();
        for (Use use : uses.values()) {
            final double[] from = use.from();
            final double[] to = use.to();
            out.add(from, to, 0, -1, -1);
            if (use.count > 1) {
                out.add(to, from, 0, -1, -1);
            }
        }
        return out;
    }

    /**
     * Whether an edge's midpoint is in a column.
     *
     * @param edge The edge.
     * @param key The column.
     * @param column The column width.
     * @return True if it is.
     */
    private static boolean inColumn(Grouping.@NonNull Edge edge, long key, double column) {
        final double[] m = middle(edge);
        return columnKey((int) Math.floor(m[0] / column), (int) Math.floor(m[2] / column)) == key;
    }

    /**
     * Whether an edge is longer than a column.
     *
     * @param edge The edge.
     * @param column The column width.
     * @return True if it is.
     */
    private static boolean tooLong(Grouping.@NonNull Edge edge, double column) {
        final double[] a = position(edge.a());
        final double[] b = position(edge.b());
        double d = 0;
        for (int axis = 0; axis < AXES; ++axis) {
            d += (a[axis] - b[axis]) * (a[axis] - b[axis]);
        }
        return d > column * column;
    }

    /**
     * Visit the edges of a cluster whose midpoint is in a column, once per triangle using it: only
     * those to check, or also those near enough to meet them along a line.
     *
     * @param cluster The cluster.
     * @param key The column.
     * @param column The column width.
     * @param longEdges Counts edges in the column too long to check, or null not to count.
     * @param columnOnly Whether to visit only the column's edges, and none too long to check,
     *     rather than every edge whose midpoint is within {@link #NEAR_COLUMN} of it.
     * @param visitor What to do with each.
     */
    private static void forEachEdge(
            LodDag.@NonNull Cluster cluster,
            long key,
            double column,
            long[] longEdges,
            boolean columnOnly,
            @NonNull EdgeVisitor visitor) {
        final float[] corners = cluster.corners();
        final double near = columnOnly ? 0 : NEAR_COLUMN * column;
        final double minX = (key >> Integer.SIZE) * column - near;
        final double minZ = (int) key * column - near;
        final double maxX = minX + column + 2 * near;
        final double maxZ = minZ + column + 2 * near;
        for (int t = 0; t < corners.length; t += TRIANGLE_VALUES) {
            for (int corner = 0; corner < AXES; ++corner) {
                final int p = t + corner * AXES;
                final int q = t + ((corner + 1) % AXES) * AXES;
                final double mx = ((double) corners[p] + corners[q]) / 2;
                final double mz = ((double) corners[p + 2] + corners[q + 2]) / 2;
                if (mx < minX || mx >= maxX || mz < minZ || mz >= maxZ) {
                    continue;
                }
                final double dx = corners[p] - corners[q];
                final double dy = corners[p + 1] - corners[q + 1];
                final double dz = corners[p + 2] - corners[q + 2];
                if (columnOnly && dx * dx + dy * dy + dz * dz > column * column) {
                    if (longEdges != null) {
                        ++longEdges[0];
                    }
                    continue;
                }
                visitor.visit(
                        Grouping.Edge.of(
                                Grouping.Corner.of(corners, p), Grouping.Corner.of(corners, q)),
                        corners,
                        p,
                        q,
                        t / TRIANGLE_VALUES);
            }
        }
    }

    /**
     * Whether both ends of an edge are on one of some boxes.
     *
     * @param edge The edge.
     * @param boxes The boxes, min x, y, z then max x, y, z.
     * @return True if one holds it.
     */
    private static boolean onAny(Grouping.@NonNull Edge edge, @NonNull Collection<double[]> boxes) {
        for (double[] box : boxes) {
            if (inside(edge.a(), box) && inside(edge.b(), box)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The middle of an edge.
     *
     * @param edge The edge.
     * @return x, y, z.
     */
    private static double[] middle(Grouping.@NonNull Edge edge) {
        final double[] a = position(edge.a());
        final double[] b = position(edge.b());
        return new double[] {(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2};
    }

    /**
     * Where a corner is.
     *
     * @param corner The corner.
     * @return x, y, z.
     */
    private static double[] position(Grouping.@NonNull Corner corner) {
        return new double[] {
            Float.intBitsToFloat(corner.x()),
            Float.intBitsToFloat(corner.y()),
            Float.intBitsToFloat(corner.z())
        };
    }

    /**
     * Whether a corner is in a box, give or take a little.
     *
     * @param corner The corner.
     * @param box The box, min x, y, z then max x, y, z.
     * @return True if it is.
     */
    private static boolean inside(Grouping.@NonNull Corner corner, double @NonNull [] box) {
        final double[] at = position(corner);
        for (int axis = 0; axis < AXES; ++axis) {
            if (at[axis] < box[axis] - BOX_SLACK || at[axis] > box[axis + AXES] + BOX_SLACK) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check a cut.
     *
     * @param drawn Whether each cluster is drawn.
     * @return What was found.
     */
    public Result check(boolean @NonNull [] drawn) {
        List<Integer> clusters = new ArrayList<>();
        for (int i = 0; i < drawn.length; ++i) {
            if (drawn[i]) {
                clusters.add(i);
            }
        }
        final Map<Grouping.Edge, Integer> uses = Grouping.edgeUses(dag, clusters);
        // The most any level drawing an edge uses it, a level that uses it once, and the triangle
        // using each edge used once
        final Map<Grouping.Edge, Integer> allowed = new HashMap<>();
        final Map<Grouping.Edge, Integer> onceIn = new HashMap<>();
        final Map<Grouping.Edge, int[]> owners = new HashMap<>();
        for (int id : clusters) {
            final LodDag.Cluster cluster = dag.clusters().get(id);
            final float[] corners = cluster.corners();
            for (int t = 0; t < cluster.triangleCount(); ++t) {
                for (int corner = 0; corner < AXES; ++corner) {
                    final int p = t * TRIANGLE_VALUES + corner * AXES;
                    final int q = t * TRIANGLE_VALUES + ((corner + 1) % AXES) * AXES;
                    final Grouping.Edge edge =
                            Grouping.Edge.of(
                                    Grouping.Corner.of(corners, p), Grouping.Corner.of(corners, q));
                    if (uses.get(edge) == 1) {
                        owners.put(edge, new int[] {id, t, p, q});
                    }
                    for (int level = cluster.level(); level <= highestStanding(cluster); ++level) {
                        final int count = levelUses.get(level).getOrDefault(edge, 0);
                        allowed.merge(edge, count, Math::max);
                        if (count == 1) {
                            onceIn.put(edge, level);
                        }
                    }
                }
            }
        }
        final Surface cut = new Surface(dag.clusters(), clusters);
        final TriangleBvh.Scratch scratch = new TriangleBvh.Scratch();
        List<Grouping.Edge> open = new ArrayList<>();
        List<Grouping.Edge> overdrawn = new ArrayList<>();
        for (Map.Entry<Grouping.Edge, Integer> entry : uses.entrySet()) {
            final int most = allowed.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() == 1) {
                final int[] owner = owners.get(entry.getKey());
                final float[] corners = dag.clusters().get(owner[0]).corners();
                final double[] from = point(corners, owner[2]);
                final double[] to = point(corners, owner[3]);
                // Its level's rim, unless something there touches it
                if (most == 1
                        && !levelSurfaces
                                .get(onceIn.get(entry.getKey()))
                                .touches(from, to, owner[0], owner[1], scratch)) {
                    continue;
                }
                if (!cut.touches(from, to, owner[0], owner[1], scratch)) {
                    open.add(entry.getKey());
                }
            } else if (entry.getValue() > Math.max(most, 1)) {
                overdrawn.add(entry.getKey());
            }
        }
        return new Result(open, overdrawn, clusters.size());
    }
}
