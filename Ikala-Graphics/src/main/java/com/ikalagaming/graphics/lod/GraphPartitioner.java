package com.ikalagaming.graphics.lod;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;

import java.util.Arrays;

/**
 * Splits a graph into parts no heavier than a cap, cutting as little edge weight as it can, the way
 * METIS does, which is what Nanite uses for both clusters and groups (Karis, Stubbe and Wihlidal,
 * <i>Nanite: A Deep Dive</i>, 2021):
 *
 * <ol>
 *   <li>Recursive bisection: a graph over the cap is split in two, with weights in proportion to how
 *       many parts each side should end up with, and each side is split again until it fits.
 *   <li>Each bisection is multilevel: the graph is coarsened by matching each node with the
 *       neighbor it shares the heaviest edge with, the small graph is split by growing a region
 *       from a seed, and the split is refined with Fiduccia–Mattheyses moves on the way back to the
 *       full graph.
 *   <li>Where that can't split a graph, such as one heavy node or disconnected bits, it is split at
 *       the weighted median along its longest axis, so every part is under the cap in the end.
 * </ol>
 *
 * <p>Everything is deterministic: the same graph always gives the same parts.
 */
final class GraphPartitioner {

    /** Stop coarsening once a graph has this few nodes. */
    private static final int COARSE_ENOUGH = 32;

    /** Stop coarsening when a step removes less than this share of the nodes. */
    private static final double MIN_COARSEN = 0.9;

    /** How far a side may go over its target weight, as a share of the total. */
    private static final double SLACK = 0.03;

    /** How many seeds to try when growing the first split. */
    private static final int SEEDS = 4;

    /** How many refinement passes at each level. */
    private static final int PASSES = 4;

    /** Coordinates in a position. */
    private static final int AXES = Graph.AXES;

    /**
     * Split a graph.
     *
     * @param graph The graph.
     * @param cap The most weight any part may have; at least the heaviest node.
     * @return The part of each node, numbered from 0 in no particular order.
     */
    static int[] partition(@NonNull Graph graph, int cap) {
        final int[] part = new int[graph.nodeCount()];
        final int[] nodes = new int[graph.nodeCount()];
        for (int v = 0; v < nodes.length; ++v) {
            nodes[v] = v;
        }
        final int[] next = {0};
        split(graph, nodes, Math.max(1, cap), part, next);
        return part;
    }

    /**
     * How many parts a partition has.
     *
     * @param part The part of each node.
     * @return The number of parts.
     */
    static int partCount(int @NonNull [] part) {
        int max = -1;
        for (int p : part) {
            max = Math.max(max, p);
        }
        return max + 1;
    }

    /**
     * Split a set of nodes until each part fits, numbering the parts.
     *
     * @param graph The whole graph.
     * @param nodes The nodes to split.
     * @param cap The most weight a part may have.
     * @param part The part of each node, written.
     * @param next The next part number.
     */
    private static void split(
            @NonNull Graph graph, int @NonNull [] nodes, int cap, int @NonNull [] part, int @NonNull [] next) {
        int weight = 0;
        for (int v : nodes) {
            weight += graph.nodeWeights()[v];
        }
        if (weight <= cap || nodes.length <= 1) {
            final int id = next[0]++;
            for (int v : nodes) {
                part[v] = id;
            }
            return;
        }
        final int parts = (weight + cap - 1) / cap;
        final int leftParts = parts / 2;
        final double leftTarget = (double) weight * leftParts / parts;
        final Graph sub = induced(graph, nodes);
        boolean[] left = bisect(sub, leftTarget);
        int leftCount = 0;
        for (boolean side : left) {
            leftCount += side ? 1 : 0;
        }
        if (leftCount == 0 || leftCount == nodes.length) {
            left = splitByPosition(sub, leftTarget);
            leftCount = 0;
            for (boolean side : left) {
                leftCount += side ? 1 : 0;
            }
        }
        final int[] a = new int[leftCount];
        final int[] b = new int[nodes.length - leftCount];
        int ai = 0;
        int bi = 0;
        for (int i = 0; i < nodes.length; ++i) {
            if (left[i]) {
                a[ai++] = nodes[i];
            } else {
                b[bi++] = nodes[i];
            }
        }
        split(graph, a, cap, part, next);
        split(graph, b, cap, part, next);
    }

    /**
     * The graph made of some nodes and the edges between them, renumbered from 0 in their order.
     *
     * @param graph The whole graph.
     * @param nodes The nodes to keep.
     * @return The smaller graph.
     */
    static Graph induced(@NonNull Graph graph, int @NonNull [] nodes) {
        final int[] local = new int[graph.nodeCount()];
        Arrays.fill(local, -1);
        for (int i = 0; i < nodes.length; ++i) {
            local[nodes[i]] = i;
        }
        final int[] offsets = new int[nodes.length + 1];
        IntArrayList targets = new IntArrayList();
        IntArrayList weights = new IntArrayList();
        final int[] nodeWeights = new int[nodes.length];
        final double[] positions = new double[nodes.length * AXES];
        for (int i = 0; i < nodes.length; ++i) {
            final int v = nodes[i];
            offsets[i] = targets.size();
            for (int e = graph.offsets()[v]; e < graph.offsets()[v + 1]; ++e) {
                final int u = local[graph.targets()[e]];
                if (u >= 0) {
                    targets.addInt(u);
                    weights.addInt(graph.edgeWeights()[e]);
                }
            }
            nodeWeights[i] = graph.nodeWeights()[v];
            System.arraycopy(graph.positions(), v * AXES, positions, i * AXES, AXES);
        }
        offsets[nodes.length] = targets.size();
        return new Graph(
                nodes.length,
                offsets,
                targets.toIntArray(),
                weights.toIntArray(),
                nodeWeights,
                positions);
    }

    /**
     * Split a graph in two, multilevel.
     *
     * @param graph The graph.
     * @param leftTarget The weight the left side should have.
     * @return Whether each node is on the left.
     */
    static boolean[] bisect(@NonNull Graph graph, double leftTarget) {
        // Coarsen
        Graph[] levels = new Graph[1];
        int[][] maps = new int[0][];
        levels[0] = graph;
        while (levels[levels.length - 1].nodeCount() > COARSE_ENOUGH) {
            final Graph fine = levels[levels.length - 1];
            final int[] map = new int[fine.nodeCount()];
            final Graph coarse = coarsen(fine, map);
            if (coarse.nodeCount() > MIN_COARSEN * fine.nodeCount()) {
                break;
            }
            levels = Arrays.copyOf(levels, levels.length + 1);
            levels[levels.length - 1] = coarse;
            maps = Arrays.copyOf(maps, maps.length + 1);
            maps[maps.length - 1] = map;
        }
        final int total = graph.totalWeight();
        final double tolerance = Math.max(heaviest(graph), SLACK * total);
        // Split the coarsest, then project back up, refining at each level
        boolean[] side = grow(levels[levels.length - 1], leftTarget, tolerance);
        refine(levels[levels.length - 1], side, leftTarget, tolerance);
        for (int level = levels.length - 2; level >= 0; --level) {
            final int[] map = maps[level];
            final boolean[] finer = new boolean[levels[level].nodeCount()];
            for (int v = 0; v < finer.length; ++v) {
                finer[v] = side[map[v]];
            }
            side = finer;
            refine(levels[level], side, leftTarget, tolerance);
        }
        return side;
    }

    /**
     * Coarsen a graph by heavy-edge matching: each unmatched node, in order, is merged with the
     * unmatched neighbor it shares the heaviest edge with.
     *
     * @param graph The graph.
     * @param map The coarse node of each node, written.
     * @return The coarse graph.
     */
    private static Graph coarsen(@NonNull Graph graph, int @NonNull [] map) {
        final int n = graph.nodeCount();
        Arrays.fill(map, -1);
        int coarseCount = 0;
        for (int v = 0; v < n; ++v) {
            if (map[v] >= 0) {
                continue;
            }
            int best = -1;
            int bestWeight = 0;
            for (int e = graph.offsets()[v]; e < graph.offsets()[v + 1]; ++e) {
                final int u = graph.targets()[e];
                if (map[u] < 0 && graph.edgeWeights()[e] > bestWeight) {
                    best = u;
                    bestWeight = graph.edgeWeights()[e];
                }
            }
            map[v] = coarseCount;
            if (best >= 0) {
                map[best] = coarseCount;
            }
            ++coarseCount;
        }
        Graph.Builder builder = new Graph.Builder(coarseCount);
        final int[] weights = new int[coarseCount];
        final double[] positions = new double[coarseCount * AXES];
        for (int v = 0; v < n; ++v) {
            final int c = map[v];
            final int w = graph.nodeWeights()[v];
            weights[c] += w;
            for (int axis = 0; axis < AXES; ++axis) {
                positions[c * AXES + axis] += graph.positions()[v * AXES + axis] * w;
            }
            for (int e = graph.offsets()[v]; e < graph.offsets()[v + 1]; ++e) {
                final int u = graph.targets()[e];
                // Each edge is stored both ways; add it once
                if (v < u) {
                    builder.edge(c, map[u], graph.edgeWeights()[e]);
                }
            }
        }
        for (int c = 0; c < coarseCount; ++c) {
            builder.nodeWeight(c, weights[c]);
            final double w = Math.max(1, weights[c]);
            builder.position(
                    c, positions[c * AXES] / w, positions[c * AXES + 1] / w, positions[c * AXES + 2] / w);
        }
        return builder.build();
    }

    /**
     * Grow the left side from a seed, breadth first, always taking the frontier node most tied to
     * it, until it reaches its weight; tried from a few seeds, keeping the smallest cut.
     *
     * @param graph The graph.
     * @param leftTarget The left side's weight.
     * @param tolerance How far off the target is acceptable.
     * @return Whether each node is on the left.
     */
    private static boolean[] grow(@NonNull Graph graph, double leftTarget, double tolerance) {
        final int n = graph.nodeCount();
        boolean[] best = null;
        long bestCut = Long.MAX_VALUE;
        int seed = farthestFrom(graph, 0);
        for (int attempt = 0; attempt < Math.min(SEEDS, n); ++attempt) {
            final boolean[] left = new boolean[n];
            final long[] tie = new long[n];
            double weight = 0;
            int current = seed;
            while (current >= 0 && weight + graph.nodeWeights()[current] <= leftTarget + tolerance) {
                left[current] = true;
                weight += graph.nodeWeights()[current];
                for (int e = graph.offsets()[current]; e < graph.offsets()[current + 1]; ++e) {
                    tie[graph.targets()[e]] += graph.edgeWeights()[e];
                }
                if (weight >= leftTarget) {
                    break;
                }
                current = mostTied(graph, left, tie, seed);
            }
            final long cut = cut(graph, left);
            if (balanced(graph, left, leftTarget, tolerance) && cut < bestCut) {
                bestCut = cut;
                best = left;
            }
            seed = farthestFrom(graph, seed);
        }
        if (best == null) {
            return splitByPosition(graph, leftTarget);
        }
        return best;
    }

    /**
     * The next node to add: the outside node most tied to the left side, or if none is tied (the
     * left side reached the end of its component), the outside node nearest the seed.
     *
     * @param graph The graph.
     * @param left Which nodes are on the left.
     * @param tie How much edge weight each node has to the left side.
     * @param seed The seed.
     * @return The node, or -1 if every node is on the left.
     */
    private static int mostTied(
            @NonNull Graph graph, boolean @NonNull [] left, long @NonNull [] tie, int seed) {
        int best = -1;
        long bestTie = 0;
        double nearest = Double.MAX_VALUE;
        int nearestNode = -1;
        for (int v = 0; v < graph.nodeCount(); ++v) {
            if (left[v]) {
                continue;
            }
            if (tie[v] > bestTie) {
                bestTie = tie[v];
                best = v;
            }
            final double d = distanceSquared(graph, v, seed);
            if (d < nearest) {
                nearest = d;
                nearestNode = v;
            }
        }
        return best >= 0 ? best : nearestNode;
    }

    /**
     * The node farthest from another by position, as a seed at the edge of the graph.
     *
     * @param graph The graph.
     * @param from The node.
     * @return The farthest node.
     */
    private static int farthestFrom(@NonNull Graph graph, int from) {
        int best = from;
        double bestDistance = -1;
        for (int v = 0; v < graph.nodeCount(); ++v) {
            final double d = distanceSquared(graph, v, from);
            if (d > bestDistance) {
                bestDistance = d;
                best = v;
            }
        }
        return best;
    }

    /**
     * Improve a split with Fiduccia–Mattheyses passes: move the node whose move cuts the most
     * weight, keeping balance, lock it, and after the pass keep the best point reached.
     *
     * @param graph The graph.
     * @param left Whether each node is on the left; changed in place.
     * @param leftTarget The left side's weight.
     * @param tolerance How far off the target is acceptable.
     */
    private static void refine(
            @NonNull Graph graph, boolean @NonNull [] left, double leftTarget, double tolerance) {
        final int n = graph.nodeCount();
        double leftWeight = 0;
        for (int v = 0; v < n; ++v) {
            if (left[v]) {
                leftWeight += graph.nodeWeights()[v];
            }
        }
        final long[] gain = new long[n];
        final boolean[] moved = new boolean[n];
        final int[] order = new int[n];
        for (int pass = 0; pass < PASSES; ++pass) {
            for (int v = 0; v < n; ++v) {
                gain[v] = gainOf(graph, left, v);
            }
            Arrays.fill(moved, false);
            final GainHeap heap = new GainHeap(n);
            for (int v = 0; v < n; ++v) {
                if (isBoundary(graph, left, v)) {
                    heap.push(v, gain[v]);
                }
            }
            long running = 0;
            long bestRunning = 0;
            int bestMoves = 0;
            int moves = 0;
            double weight = leftWeight;
            double bestImbalance = Math.abs(weight - leftTarget);
            while (!heap.isEmpty()) {
                final int v = heap.pop(gain);
                if (v < 0) {
                    break;
                }
                if (moved[v]) {
                    continue;
                }
                final double after =
                        left[v] ? weight - graph.nodeWeights()[v] : weight + graph.nodeWeights()[v];
                if (Math.abs(after - leftTarget) > tolerance
                        && Math.abs(after - leftTarget) >= Math.abs(weight - leftTarget)) {
                    continue;
                }
                moved[v] = true;
                left[v] = !left[v];
                weight = after;
                running += gain[v];
                order[moves++] = v;
                for (int e = graph.offsets()[v]; e < graph.offsets()[v + 1]; ++e) {
                    final int u = graph.targets()[e];
                    if (!moved[u]) {
                        gain[u] = gainOf(graph, left, u);
                        heap.push(u, gain[u]);
                    }
                }
                final double imbalance = Math.abs(weight - leftTarget);
                final boolean fits = imbalance <= tolerance;
                if (fits
                        && (running > bestRunning
                                || (running == bestRunning && imbalance < bestImbalance))) {
                    bestRunning = running;
                    bestMoves = moves;
                    bestImbalance = imbalance;
                }
            }
            // Undo the moves after the best point
            for (int i = moves - 1; i >= bestMoves; --i) {
                final int v = order[i];
                left[v] = !left[v];
            }
            leftWeight = 0;
            for (int v = 0; v < n; ++v) {
                if (left[v]) {
                    leftWeight += graph.nodeWeights()[v];
                }
            }
            if (bestMoves == 0) {
                break;
            }
        }
    }

    /**
     * How much cut weight moving a node to the other side would save.
     *
     * @param graph The graph.
     * @param left Which nodes are on the left.
     * @param v The node.
     * @return Edge weight to the other side minus edge weight to its own side.
     */
    private static long gainOf(@NonNull Graph graph, boolean @NonNull [] left, int v) {
        long gain = 0;
        for (int e = graph.offsets()[v]; e < graph.offsets()[v + 1]; ++e) {
            gain += left[graph.targets()[e]] == left[v] ? -graph.edgeWeights()[e] : graph.edgeWeights()[e];
        }
        return gain;
    }

    /**
     * Whether a node has a neighbor on the other side.
     *
     * @param graph The graph.
     * @param left Which nodes are on the left.
     * @param v The node.
     * @return True if it is on the boundary.
     */
    private static boolean isBoundary(@NonNull Graph graph, boolean @NonNull [] left, int v) {
        for (int e = graph.offsets()[v]; e < graph.offsets()[v + 1]; ++e) {
            if (left[graph.targets()[e]] != left[v]) {
                return true;
            }
        }
        return false;
    }

    /**
     * The weight of the edges between the sides.
     *
     * @param graph The graph.
     * @param left Which nodes are on the left.
     * @return The cut weight.
     */
    static long cut(@NonNull Graph graph, boolean @NonNull [] left) {
        long cut = 0;
        for (int v = 0; v < graph.nodeCount(); ++v) {
            for (int e = graph.offsets()[v]; e < graph.offsets()[v + 1]; ++e) {
                if (left[v] && !left[graph.targets()[e]]) {
                    cut += graph.edgeWeights()[e];
                }
            }
        }
        return cut;
    }

    /**
     * Whether a split is within tolerance of its target and has nodes on both sides.
     *
     * @param graph The graph.
     * @param left Which nodes are on the left.
     * @param leftTarget The left side's weight.
     * @param tolerance How far off is acceptable.
     * @return True if balanced.
     */
    private static boolean balanced(
            @NonNull Graph graph, boolean @NonNull [] left, double leftTarget, double tolerance) {
        double weight = 0;
        int count = 0;
        for (int v = 0; v < graph.nodeCount(); ++v) {
            if (left[v]) {
                weight += graph.nodeWeights()[v];
                ++count;
            }
        }
        return count > 0 && count < graph.nodeCount() && Math.abs(weight - leftTarget) <= tolerance;
    }

    /**
     * Split at the weighted median along the longest axis of the nodes' positions.
     *
     * @param graph The graph.
     * @param leftTarget The left side's weight.
     * @return Whether each node is on the left.
     */
    static boolean[] splitByPosition(@NonNull Graph graph, double leftTarget) {
        final int n = graph.nodeCount();
        int axis = 0;
        double longest = -1;
        for (int a = 0; a < AXES; ++a) {
            double lo = Double.MAX_VALUE;
            double hi = -Double.MAX_VALUE;
            for (int v = 0; v < n; ++v) {
                lo = Math.min(lo, graph.positions()[v * AXES + a]);
                hi = Math.max(hi, graph.positions()[v * AXES + a]);
            }
            if (hi - lo > longest) {
                longest = hi - lo;
                axis = a;
            }
        }
        final int sortAxis = axis;
        Integer[] order = new Integer[n];
        for (int v = 0; v < n; ++v) {
            order[v] = v;
        }
        Arrays.sort(
                order,
                (x, y) -> {
                    final int c =
                            Double.compare(
                                    graph.positions()[x * AXES + sortAxis],
                                    graph.positions()[y * AXES + sortAxis]);
                    return c != 0 ? c : Integer.compare(x, y);
                });
        final boolean[] left = new boolean[n];
        double weight = 0;
        for (int i = 0; i < n - 1; ++i) {
            final int v = order[i];
            if (i > 0 && weight + graph.nodeWeights()[v] / 2.0 > leftTarget) {
                break;
            }
            left[v] = true;
            weight += graph.nodeWeights()[v];
        }
        return left;
    }

    /**
     * The heaviest node's weight.
     *
     * @param graph The graph.
     * @return The weight.
     */
    private static int heaviest(@NonNull Graph graph) {
        int max = 0;
        for (int w : graph.nodeWeights()) {
            max = Math.max(max, w);
        }
        return max;
    }

    /**
     * The squared distance between two nodes' positions.
     *
     * @param graph The graph.
     * @param a One node.
     * @param b The other.
     * @return The squared distance.
     */
    private static double distanceSquared(@NonNull Graph graph, int a, int b) {
        double d = 0;
        for (int axis = 0; axis < AXES; ++axis) {
            final double delta = graph.positions()[a * AXES + axis] - graph.positions()[b * AXES + axis];
            d += delta * delta;
        }
        return d;
    }

    /**
     * A max-heap of nodes by gain, with stale entries skipped when popped: an entry is current only
     * if its gain still matches the node's.
     */
    private static final class GainHeap {
        /** The nodes. */
        private int[] nodes;

        /** The gains they were pushed with. */
        private long[] gains;

        /** How many entries. */
        private int size;

        /**
         * Make a heap.
         *
         * @param capacity Room to start with.
         */
        GainHeap(int capacity) {
            nodes = new int[Math.max(1, capacity)];
            gains = new long[nodes.length];
        }

        /**
         * Whether it is empty.
         *
         * @return True if so.
         */
        boolean isEmpty() {
            return size == 0;
        }

        /**
         * Add a node.
         *
         * @param node The node.
         * @param gain Its gain.
         */
        void push(int node, long gain) {
            if (size == nodes.length) {
                nodes = Arrays.copyOf(nodes, size * 2);
                gains = Arrays.copyOf(gains, size * 2);
            }
            int at = size++;
            while (at > 0) {
                final int parent = (at - 1) >>> 1;
                if (better(parent, gain, node)) {
                    break;
                }
                nodes[at] = nodes[parent];
                gains[at] = gains[parent];
                at = parent;
            }
            nodes[at] = node;
            gains[at] = gain;
        }

        /**
         * Remove the node with the most gain whose entry is current.
         *
         * @param current Each node's current gain.
         * @return The node, or -1 if none is current.
         */
        int pop(long @NonNull [] current) {
            while (size > 0) {
                final int node = nodes[0];
                final long gain = gains[0];
                --size;
                if (size > 0) {
                    siftDown(nodes[size], gains[size]);
                }
                if (current[node] == gain) {
                    return node;
                }
            }
            return -1;
        }

        /**
         * Put an entry at the top and move it down to where it belongs.
         *
         * @param node The node.
         * @param gain Its gain.
         */
        private void siftDown(int node, long gain) {
            int at = 0;
            while (true) {
                int child = at * 2 + 1;
                if (child >= size) {
                    break;
                }
                if (child + 1 < size && better(child + 1, gains[child], nodes[child])) {
                    ++child;
                }
                if (!better(child, gain, node)) {
                    break;
                }
                nodes[at] = nodes[child];
                gains[at] = gains[child];
                at = child;
            }
            nodes[at] = node;
            gains[at] = gain;
        }

        /**
         * Whether the entry at an index beats a gain and node, ties going to the lower node.
         *
         * @param index The entry.
         * @param gain The other gain.
         * @param node The other node.
         * @return True if the entry comes first.
         */
        private boolean better(int index, long gain, int node) {
            return beats(gains[index], nodes[index], gain, node);
        }

        /**
         * Whether one gain and node come before another, ties going to the lower node.
         *
         * @param gainA The first gain.
         * @param nodeA The first node.
         * @param gainB The second gain.
         * @param nodeB The second node.
         * @return True if the first comes first.
         */
        private static boolean beats(long gainA, int nodeA, long gainB, int nodeB) {
            return gainA > gainB || (gainA == gainB && nodeA < nodeB);
        }
    }
}
