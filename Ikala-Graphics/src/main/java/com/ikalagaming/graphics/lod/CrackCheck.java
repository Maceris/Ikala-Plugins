package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.ArrayList;
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
 * it was built.
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

    /** Coordinates in a position. */
    private static final int AXES = 3;

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
            byLevel.get(cluster.level()).add(cluster.id());
        }
        for (List<Integer> clusters : byLevel) {
            levelUses.add(Grouping.edgeUses(dag, clusters));
        }
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
            final Map<Grouping.Edge, Integer> level = levelUses.get(cluster.level());
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
                    allowed.merge(edge, level.getOrDefault(edge, 0), Math::max);
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
