package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for the graph partitioner. */
class GraphPartitionerTest {

    /**
     * A square grid graph, each node joined to its four neighbors, positioned on the grid.
     *
     * @param size Nodes along each side.
     * @return The graph.
     */
    private static Graph grid(int size) {
        Graph.Builder builder = new Graph.Builder(size * size);
        for (int x = 0; x < size; ++x) {
            for (int y = 0; y < size; ++y) {
                final int v = x * size + y;
                builder.position(v, x, y, 0);
                if (x + 1 < size) {
                    builder.edge(v, v + size, 1);
                }
                if (y + 1 < size) {
                    builder.edge(v, v + 1, 1);
                }
            }
        }
        return builder.build();
    }

    /**
     * The weight of edges between different parts.
     *
     * @param graph The graph.
     * @param part The part of each node.
     * @return The cut weight.
     */
    private static long cut(Graph graph, int[] part) {
        long cut = 0;
        for (int v = 0; v < graph.nodeCount(); ++v) {
            for (int e = graph.offsets()[v]; e < graph.offsets()[v + 1]; ++e) {
                if (part[v] < part[graph.targets()[e]]) {
                    cut += graph.edgeWeights()[e];
                }
            }
        }
        return cut;
    }

    /**
     * The heaviest part.
     *
     * @param graph The graph.
     * @param part The part of each node.
     * @return Its weight.
     */
    private static int heaviestPart(Graph graph, int[] part) {
        final int[] weights = new int[GraphPartitioner.partCount(part)];
        for (int v = 0; v < part.length; ++v) {
            weights[part[v]] += graph.nodeWeights()[v];
        }
        int max = 0;
        for (int w : weights) {
            max = Math.max(max, w);
        }
        return max;
    }

    /** Two cliques joined by one light edge split along that edge. */
    @Test
    void testCliquesSplitAtTheBridge() {
        Graph.Builder builder = new Graph.Builder(16);
        for (int c = 0; c < 2; ++c) {
            for (int i = 0; i < 8; ++i) {
                builder.position(c * 8 + i, c * 10 + i % 3, i / 3, 0);
                for (int j = i + 1; j < 8; ++j) {
                    builder.edge(c * 8 + i, c * 8 + j, 10);
                }
            }
        }
        builder.edge(3, 12, 1);
        final Graph graph = builder.build();
        final int[] part = GraphPartitioner.partition(graph, 8);
        assertEquals(2, GraphPartitioner.partCount(part));
        assertEquals(1, cut(graph, part));
    }

    /** Every part stays under the cap. */
    @Test
    void testPartsStayUnderTheCap() {
        final Graph graph = grid(23);
        for (int cap : new int[] {7, 32, 128}) {
            final int[] part = GraphPartitioner.partition(graph, cap);
            assertTrue(heaviestPart(graph, part) <= cap, "Cap " + cap);
        }
    }

    /** The same graph always splits the same way. */
    @Test
    void testDeterministic() {
        final Graph graph = grid(20);
        assertArrayEquals(
                GraphPartitioner.partition(graph, 50), GraphPartitioner.partition(graph, 50));
    }

    /** Halving a grid cuts close to the best straight line. */
    @Test
    void testGridCutIsNearOptimal() {
        final Graph graph = grid(16);
        final int[] part = GraphPartitioner.partition(graph, 128);
        assertEquals(2, GraphPartitioner.partCount(part));
        assertTrue(cut(graph, part) <= 20, "Cut " + cut(graph, part) + ", best is 16");
    }

    /** Nodes with no edges at all are split by position, still under the cap. */
    @Test
    void testDisconnectedNodesSplitByPosition() {
        Graph.Builder builder = new Graph.Builder(100);
        for (int v = 0; v < 100; ++v) {
            builder.position(v, v % 10, v / 10, 0);
        }
        final Graph graph = builder.build();
        final int[] part = GraphPartitioner.partition(graph, 10);
        assertTrue(heaviestPart(graph, part) <= 10);
        assertTrue(GraphPartitioner.partCount(part) <= 14, "Parts " + GraphPartitioner.partCount(part));
    }
}
