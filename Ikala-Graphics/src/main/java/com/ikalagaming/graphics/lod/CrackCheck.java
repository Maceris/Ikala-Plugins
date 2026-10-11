package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks a cut for cracks. With the input's open border locked at every level, a cut without
 * cracks has exactly the input's open edges (edges with one triangle) and no others: a crack is an
 * edge where two clusters drawn at different levels don't meet, so it shows up as an open edge the
 * input doesn't have. An edge used by more triangles than in the input means parts drawn twice.
 */
public final class CrackCheck {

    /**
     * What a check found.
     *
     * @param openEdges Open edges in the cut that the input doesn't have: cracks.
     * @param closedBorderEdges Input open edges the cut covers twice or not at all.
     * @param drawnClusters How many clusters the cut draws.
     */
    public record Result(
            @NonNull List<Grouping.Edge> openEdges,
            @NonNull List<Grouping.Edge> closedBorderEdges,
            int drawnClusters) {

        /**
         * Whether the cut is crack free.
         *
         * @return True if nothing was found.
         */
        public boolean isClean() {
            return openEdges.isEmpty() && closedBorderEdges.isEmpty();
        }
    }

    /** The input's open edges. */
    private final Map<Grouping.Edge, Integer> leafUses;

    /** The DAG. */
    private final LodDag dag;

    /**
     * Prepare to check cuts of a DAG.
     *
     * @param dag The DAG.
     */
    public CrackCheck(@NonNull LodDag dag) {
        this.dag = dag;
        List<Integer> leaves = new ArrayList<>();
        for (LodDag.Cluster cluster : dag.clusters()) {
            if (cluster.level() == 0) {
                leaves.add(cluster.id());
            }
        }
        leafUses = Grouping.edgeUses(dag, leaves);
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
        List<Grouping.Edge> open = new ArrayList<>();
        for (Map.Entry<Grouping.Edge, Integer> edge : uses.entrySet()) {
            if (edge.getValue() == 1 && leafUses.getOrDefault(edge.getKey(), 0) != 1) {
                open.add(edge.getKey());
            }
        }
        List<Grouping.Edge> border = new ArrayList<>();
        for (Map.Entry<Grouping.Edge, Integer> edge : leafUses.entrySet()) {
            if (edge.getValue() == 1 && uses.getOrDefault(edge.getKey(), 0) != 1) {
                border.add(edge.getKey());
            }
        }
        return new Result(open, border, clusters.size());
    }
}
