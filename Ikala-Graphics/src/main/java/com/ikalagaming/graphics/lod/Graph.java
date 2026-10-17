package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.Arrays;

/**
 * An undirected graph with node and edge weights, in compressed sparse row form, plus a position
 * per node for splitting by position when the graph alone doesn't say how. Every edge is stored in
 * both directions.
 *
 * @param nodeCount How many nodes there are.
 * @param offsets Where each node's edges start in {@link #targets}, with the end after the last.
 * @param targets The node at the other end of each edge.
 * @param edgeWeights Each edge's weight.
 * @param nodeWeights Each node's weight.
 * @param positions Each node's position, x, y, z.
 */
record Graph(
        int nodeCount,
        int @NonNull [] offsets,
        int @NonNull [] targets,
        int @NonNull [] edgeWeights,
        int @NonNull [] nodeWeights,
        double @NonNull [] positions) {

    /** Coordinates in a position. */
    static final int AXES = 3;

    /**
     * The total node weight.
     *
     * @return The sum of the node weights.
     */
    int totalWeight() {
        int total = 0;
        for (int w : nodeWeights) {
            total += w;
        }
        return total;
    }

    /** Builds a graph from edges given once each, merging repeats by adding their weights. */
    static final class Builder {
        /** How many nodes. */
        private final int nodes;

        /** Edge ends, two per edge. */
        private int[] ends = new int[64];

        /** Edge weights. */
        private int[] weights = new int[32];

        /** How many edges. */
        private int count;

        /** Node weights, 1 unless set. */
        private final int[] nodeWeights;

        /** Node positions. */
        private final double[] positions;

        /**
         * Start a graph.
         *
         * @param nodes How many nodes.
         */
        Builder(int nodes) {
            this(nodes, 0);
        }

        /**
         * Start a graph with room for some edges.
         *
         * @param nodes How many nodes.
         * @param edges How many edges it is expected to get.
         */
        Builder(int nodes, int edges) {
            this.nodes = nodes;
            if (edges > weights.length) {
                weights = new int[edges];
                ends = new int[edges * 2];
            }
            nodeWeights = new int[nodes];
            Arrays.fill(nodeWeights, 1);
            positions = new double[nodes * AXES];
        }

        /**
         * Set a node's weight.
         *
         * @param node The node.
         * @param weight Its weight.
         * @return This builder.
         */
        Builder nodeWeight(int node, int weight) {
            nodeWeights[node] = weight;
            return this;
        }

        /**
         * Set a node's position.
         *
         * @param node The node.
         * @param x Its x.
         * @param y Its y.
         * @param z Its z.
         * @return This builder.
         */
        Builder position(int node, double x, double y, double z) {
            positions[node * AXES] = x;
            positions[node * AXES + 1] = y;
            positions[node * AXES + 2] = z;
            return this;
        }

        /**
         * Add an edge. Self loops are ignored; repeats add up.
         *
         * @param a One end.
         * @param b The other end.
         * @param weight Its weight.
         * @return This builder.
         */
        Builder edge(int a, int b, int weight) {
            if (a == b) {
                return this;
            }
            if (count == weights.length) {
                weights = Arrays.copyOf(weights, count * 2);
                ends = Arrays.copyOf(ends, count * 4);
            }
            ends[count * 2] = a;
            ends[count * 2 + 1] = b;
            weights[count] = weight;
            ++count;
            return this;
        }

        /**
         * Finish the graph.
         *
         * @return The graph.
         */
        Graph build() {
            // Sort each node's edges by target so repeats are next to each other, then merge them
            final int[] degree = new int[nodes];
            for (int e = 0; e < count; ++e) {
                ++degree[ends[e * 2]];
                ++degree[ends[e * 2 + 1]];
            }
            final int[] start = new int[nodes + 1];
            for (int v = 0; v < nodes; ++v) {
                start[v + 1] = start[v] + degree[v];
            }
            final long[] packed = new long[start[nodes]];
            final int[] fill = Arrays.copyOf(start, nodes);
            for (int e = 0; e < count; ++e) {
                final int a = ends[e * 2];
                final int b = ends[e * 2 + 1];
                packed[fill[a]++] = ((long) b << Integer.SIZE) | weights[e];
                packed[fill[b]++] = ((long) a << Integer.SIZE) | weights[e];
            }
            final int[] offsets = new int[nodes + 1];
            int[] targets = new int[packed.length];
            int[] edgeWeights = new int[packed.length];
            int out = 0;
            for (int v = 0; v < nodes; ++v) {
                Arrays.sort(packed, start[v], start[v + 1]);
                offsets[v] = out;
                for (int i = start[v]; i < start[v + 1]; ++i) {
                    final int target = (int) (packed[i] >>> Integer.SIZE);
                    final int weight = (int) packed[i];
                    if (out > offsets[v] && targets[out - 1] == target) {
                        edgeWeights[out - 1] += weight;
                    } else {
                        targets[out] = target;
                        edgeWeights[out] = weight;
                        ++out;
                    }
                }
            }
            offsets[nodes] = out;
            return new Graph(
                    nodes,
                    offsets,
                    Arrays.copyOf(targets, out),
                    Arrays.copyOf(edgeWeights, out),
                    nodeWeights,
                    positions);
        }
    }
}
