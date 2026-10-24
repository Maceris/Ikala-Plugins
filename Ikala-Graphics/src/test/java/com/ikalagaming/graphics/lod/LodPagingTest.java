package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tests for keeping built cells' triangles only in their cache files. */
class LodPagingTest {

    /** A cap no test reaches. */
    private static final long NO_CAP = Long.MAX_VALUE;

    /**
     * Whether two clusters have the same triangles.
     *
     * @param expected One.
     * @param actual The other.
     * @param what What they are, for messages.
     */
    private static void assertSameTriangles(
            LodDag.Cluster expected, LodDag.Cluster actual, String what) {
        assertArrayEquals(expected.corners(), actual.corners(), what);
        assertArrayEquals(expected.materials(), actual.materials(), what);
        assertArrayEquals(expected.visibility(), actual.visibility(), what);
        assertArrayEquals(expected.attributes(), actual.attributes(), what);
    }

    /**
     * Every cell's own clusters, with their triangles.
     *
     * @param store The store.
     * @return Each cell's clusters, by key.
     */
    private static Map<LodStore.CellKey, List<LodDag.Cluster>> bodies(LodStore store) {
        final Map<LodStore.CellKey, List<LodDag.Cluster>> out = new HashMap<>();
        for (LodStore.CellKey key : store.cellKeys()) {
            final List<LodDag.Cluster> clusters = new ArrayList<>();
            for (LodStore.PieceCluster piece : store.piece(key)) {
                clusters.add(piece.body());
            }
            out.put(key, clusters);
        }
        return out;
    }

    /**
     * Paged cells keep their tops' triangles, read the rest back from their files as they were, and
     * flatten to the same DAG.
     */
    @Test
    void testPagedClustersReadBack(@TempDir Path folder) {
        final LodStore store =
                LodCellFileTest.store(DagTest.terrain(2), new LodCache(folder, NO_CAP));
        store.rebuild();
        final Map<LodStore.CellKey, List<LodDag.Cluster>> before = bodies(store);
        final LodDag flatBefore = store.snapshot();
        int paged = 0;
        for (LodStore.CellKey key : store.cellKeys()) {
            assertTrue(store.page(key), "" + key);
            assertFalse(store.page(key), "Paged once");
            for (LodStore.PieceCluster piece : store.piece(key)) {
                final LodDag.Cluster original =
                        before.get(key).get(piece.local() - store.inputCount(key));
                assertEquals(
                        ClusterInfo.of(original).triangleCount(), piece.info().triangleCount());
                assertSameTriangles(original, piece.body(), key + " " + piece.local());
                paged += piece.paged() ? 1 : 0;
            }
        }
        assertTrue(paged > 0, "Some clusters are only in the cache");
        final LodDag flatAfter = store.snapshot();
        assertEquals(flatBefore.clusters().size(), flatAfter.clusters().size());
        for (int i = 0; i < flatBefore.clusters().size(); ++i) {
            assertSameTriangles(
                    flatBefore.clusters().get(i), flatAfter.clusters().get(i), "cluster " + i);
        }
    }

    /** A paged store rebuilt after an edit has the same DAG as one built from the edit. */
    @Test
    void testRebuildAfterPaging(@TempDir Path paging, @TempDir Path fresh) {
        final List<LodMesh> sections = DagTest.terrain(2);
        final LodStore store = LodCellFileTest.store(sections, new LodCache(paging, NO_CAP));
        store.rebuild();
        for (LodStore.CellKey key : store.cellKeys()) {
            store.page(key);
        }
        // Take a triangle out of one section
        final LodMesh mesh = sections.get(0);
        final LodMesh.Builder builder = LodMesh.builder(mesh.getOrigin().clone());
        for (int v = 0; v < mesh.getVertexCount(); ++v) {
            builder.vertex(mesh.position(v, 0), mesh.position(v, 1), mesh.position(v, 2));
            if (mesh.isLocked(v)) {
                builder.lock(v);
            }
        }
        for (int t = 1; t < mesh.getTriangleCount(); ++t) {
            builder.triangle(
                    mesh.corner(t, 0),
                    mesh.corner(t, 1),
                    mesh.corner(t, 2),
                    mesh.material(t),
                    mesh.visibility(t));
        }
        final LodMesh edit = builder.build();
        store.put(LodCellFileTest.position(mesh), edit);
        store.rebuild();
        final List<LodMesh> edited = new ArrayList<>(sections);
        edited.set(0, edit);
        final LodStore expected = LodCellFileTest.store(edited, new LodCache(fresh, NO_CAP));
        expected.rebuild();
        final LodDag a = expected.snapshot();
        final LodDag b = store.snapshot();
        assertEquals(a.clusters().size(), b.clusters().size());
        for (int i = 0; i < a.clusters().size(); ++i) {
            assertSameTriangles(a.clusters().get(i), b.clusters().get(i), "cluster " + i);
        }
    }
}
