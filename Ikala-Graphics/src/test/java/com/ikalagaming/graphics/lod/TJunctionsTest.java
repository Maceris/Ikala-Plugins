package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.util.IntArrayList;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/** Tests for finding and closing T-junctions between open edges. */
class TJunctionsTest {

    /**
     * A point.
     *
     * @param x The x.
     * @param y The y.
     * @param z The z.
     * @return x, y, z.
     */
    private static double[] p(double x, double y, double z) {
        return new double[] {x, y, z};
    }

    /** Runs of edges going one way are covered by runs going the other way that span them. */
    @Test
    void testCoverage() {
        final TJunctions index = new TJunctions();
        index.add(p(0, 1, 0), p(3, 1, 0), 0, -1, -1);
        index.add(p(3, 1, 0), p(5, 1, 0), 0, -1, -1);
        index.add(p(5, 1, 0), p(0, 1, 0), 1, -1, -1);
        assertTrue(index.covered(p(0, 1, 0), p(3, 1, 0)));
        assertTrue(index.covered(p(5, 1, 0), p(0, 1, 0)));
        // Only partly covered
        final TJunctions partial = new TJunctions();
        partial.add(p(0, 1, 0), p(4, 1, 0), 0, -1, -1);
        partial.add(p(5, 1, 0), p(0, 1, 0), 1, -1, -1);
        assertFalse(partial.covered(p(5, 1, 0), p(0, 1, 0)));
        // Running the same way doesn't cover, nor does a line beside it
        final TJunctions same = new TJunctions();
        same.add(p(0, 1, 0), p(5, 1, 0), 0, -1, -1);
        same.add(p(5, 1.01, 0), p(0, 1.01, 0), 1, -1, -1);
        assertFalse(same.covered(p(0, 1, 0), p(2, 1, 0)));
    }

    /** Edges sharing a stretch of line with another's are found; touching at an end isn't. */
    @Test
    void testOverlapsOther() {
        final TJunctions index = new TJunctions();
        index.add(p(0, 0, 0), p(0, 0, 4), 0, -1, -1);
        index.add(p(0, 0, 4), p(0, 0, 1), 1, -1, -1);
        index.add(p(0, 0, 4), p(0, 0, 6), 2, -1, -1);
        assertTrue(index.overlapsOther(p(0, 0, 0), p(0, 0, 4), 0));
        assertTrue(index.overlapsOther(p(0, 0, 4), p(0, 0, 1), 1));
        assertFalse(index.overlapsOther(p(0, 0, 4), p(0, 0, 6), 2));
        assertTrue(index.overlapsOther(p(0, 0, 0), p(0, 0, 4), 1));
    }

    /**
     * A strip of 4 unit quads meeting one long quad along a line: welding splits the long quad's
     * edge at the 3 corners inside it, and leaves nothing open along the line.
     */
    @Test
    void testWeld() {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        final int[] top = new int[5];
        final int[] middle = new int[5];
        for (int x = 0; x <= 4; ++x) {
            top[x] = builder.vertex(x, 0, 2);
            middle[x] = builder.vertex(x, 0, 1);
        }
        // The strip, z 1 to 2, cut at every x
        for (int x = 0; x < 4; ++x) {
            builder.triangle(middle[x], top[x + 1], middle[x + 1], 0);
            builder.triangle(middle[x], top[x], top[x + 1], 0);
        }
        // The long quad, z 0 to 1, using only the line's ends
        final int a = builder.vertex(0, 0, 0);
        final int b = builder.vertex(4, 0, 0);
        builder.triangle(a, middle[4], b, 1);
        builder.triangle(a, middle[0], middle[4], 1);
        final LodMesh mesh = builder.build();
        assertEquals(5, openOnLine(mesh, 1));
        final IntArrayList from = new IntArrayList();
        final LodMesh welded = TJunctions.weld(mesh, from);
        assertEquals(mesh.getTriangleCount() + 3, welded.getTriangleCount());
        assertEquals(welded.getTriangleCount(), from.size());
        assertEquals(0, openOnLine(welded, 1));
        assertEquals(mesh.area(), welded.area(), 1e-6);
        for (int t = 0; t < welded.getTriangleCount(); ++t) {
            assertEquals(mesh.material(from.getInt(t)), welded.material(t));
        }
        // Welding again finds nothing
        final LodMesh again = TJunctions.weld(welded, from);
        assertSame(welded, again);
        assertArrayEquals(
                java.util.stream.IntStream.range(0, welded.getTriangleCount()).toArray(),
                from.toIntArray());
    }

    /**
     * How many open edges lie along z = some value.
     *
     * @param mesh The mesh.
     * @param z The line's z.
     * @return The count.
     */
    private static int openOnLine(LodMesh mesh, double z) {
        final Map<Long, Integer> uses = new HashMap<>();
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            for (int c = 0; c < 3; ++c) {
                final int v = mesh.corner(t, c);
                final int w = mesh.corner(t, (c + 1) % 3);
                uses.merge(LongIntMap.pair(Math.min(v, w), Math.max(v, w)), 1, Integer::sum);
            }
        }
        int count = 0;
        for (Map.Entry<Long, Integer> use : uses.entrySet()) {
            final int v = (int) (use.getKey() >>> Integer.SIZE);
            final int w = (int) (long) use.getKey();
            if (use.getValue() == 1 && mesh.position(v, 2) == z && mesh.position(w, 2) == z) {
                ++count;
            }
        }
        return count;
    }
}
