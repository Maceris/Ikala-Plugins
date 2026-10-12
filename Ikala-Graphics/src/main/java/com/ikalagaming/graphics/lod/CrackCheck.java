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
        }
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
            // Uses of the column's edges in each level, and in the cut
            final Map<Integer, Map<Grouping.Edge, Integer>> levelUses = new HashMap<>();
            final Map<Grouping.Edge, Integer> cutUses = new HashMap<>();
            for (int i : members) {
                final LodDag.Cluster cluster = clusters.get(i);
                final int top = highest[i];
                final boolean inCut = drawn[i];
                forEachEdge(
                        cluster,
                        key,
                        column,
                        longEdges,
                        edge -> {
                            for (int level = cluster.level(); level <= top; ++level) {
                                levelUses
                                        .computeIfAbsent(level, l -> new HashMap<>())
                                        .merge(edge, 1, Integer::sum);
                            }
                            if (inCut) {
                                cutUses.merge(edge, 1, Integer::sum);
                            }
                        });
            }
            // The most any level drawing an edge uses it
            final Map<Grouping.Edge, Integer> allowed = new HashMap<>();
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
                        edge -> {
                            for (int level = cluster.level(); level <= top; ++level) {
                                allowed.merge(
                                        edge,
                                        levelUses.get(level).getOrDefault(edge, 0),
                                        Math::max);
                            }
                        });
            }
            for (Map.Entry<Grouping.Edge, Integer> use : cutUses.entrySet()) {
                ++checked;
                final int most = allowed.getOrDefault(use.getKey(), 0);
                if (use.getValue() == 1 && most != 1) {
                    if (onAny(use.getKey(), excused)) {
                        ++excusedCount;
                        continue;
                    }
                    ++open;
                    if (examples.size() < EXAMPLES) {
                        examples.add(middle(use.getKey()));
                    }
                } else if (use.getValue() > Math.max(most, 1)) {
                    ++overdrawn;
                    if (examples.size() < EXAMPLES) {
                        examples.add(middle(use.getKey()));
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
         */
        void visit(Grouping.@NonNull Edge edge);
    }

    /**
     * Visit every edge of a cluster whose midpoint is in a column, once per triangle using it.
     *
     * @param cluster The cluster.
     * @param key The column.
     * @param column The column width.
     * @param longEdges Counts edges in the column too long to check, or null not to count.
     * @param visitor What to do with each.
     */
    private static void forEachEdge(
            LodDag.@NonNull Cluster cluster,
            long key,
            double column,
            long[] longEdges,
            @NonNull EdgeVisitor visitor) {
        final float[] corners = cluster.corners();
        for (int t = 0; t < corners.length; t += TRIANGLE_VALUES) {
            for (int corner = 0; corner < AXES; ++corner) {
                final int p = t + corner * AXES;
                final int q = t + ((corner + 1) % AXES) * AXES;
                final double mx = ((double) corners[p] + corners[q]) / 2;
                final double mz = ((double) corners[p + 2] + corners[q + 2]) / 2;
                if (columnKey((int) Math.floor(mx / column), (int) Math.floor(mz / column))
                        != key) {
                    continue;
                }
                final double dx = corners[p] - corners[q];
                final double dy = corners[p + 1] - corners[q + 1];
                final double dz = corners[p + 2] - corners[q + 2];
                if (dx * dx + dy * dy + dz * dz > column * column) {
                    if (longEdges != null) {
                        ++longEdges[0];
                    }
                    continue;
                }
                visitor.visit(
                        Grouping.Edge.of(
                                Grouping.Corner.of(corners, p), Grouping.Corner.of(corners, q)));
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
        // The most any level drawing an edge uses it
        final Map<Grouping.Edge, Integer> allowed = new HashMap<>();
        for (int id : clusters) {
            final LodDag.Cluster cluster = dag.clusters().get(id);
            final float[] corners = cluster.corners();
            for (int t = 0; t < cluster.triangleCount(); ++t) {
                for (int corner = 0; corner < AXES; ++corner) {
                    final Grouping.Edge edge =
                            Grouping.Edge.of(
                                    Grouping.Corner.of(
                                            corners, t * TRIANGLE_VALUES + corner * AXES),
                                    Grouping.Corner.of(
                                            corners,
                                            t * TRIANGLE_VALUES + ((corner + 1) % AXES) * AXES));
                    for (int level = cluster.level(); level <= highestStanding(cluster); ++level) {
                        allowed.merge(edge, levelUses.get(level).getOrDefault(edge, 0), Math::max);
                    }
                }
            }
        }
        List<Grouping.Edge> open = new ArrayList<>();
        List<Grouping.Edge> overdrawn = new ArrayList<>();
        for (Map.Entry<Grouping.Edge, Integer> entry : uses.entrySet()) {
            final int most = allowed.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() == 1 && most != 1) {
                open.add(entry.getKey());
            } else if (entry.getValue() > Math.max(most, 1)) {
                overdrawn.add(entry.getKey());
            }
        }
        return new Result(open, overdrawn, clusters.size());
    }
}
