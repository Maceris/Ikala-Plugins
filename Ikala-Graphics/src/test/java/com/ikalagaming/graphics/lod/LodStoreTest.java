package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.benchmark.SyntheticTerrain;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Tests for building level of detail in region cells. */
class LodStoreTest {

    /** The section size, in units. */
    private static final int SECTION = SyntheticTerrain.SECTION;

    /** Options for a test build, on two threads, with visibility. */
    private static final DagBuilder.Options OPTIONS =
            new DagBuilder.Options(0.5, false, 2, false, 12);

    /** Clusters a cell stops at. */
    private static final int STOP_AT = 4;

    /**
     * A store of terrain sections.
     *
     * @param sections The sections.
     * @param cellSections Sections along each side of a level 0 cell.
     * @return The store, not yet built.
     */
    private static LodStore store(List<LodMesh> sections, int cellSections) {
        final LodStore store = new LodStore(new double[3], SECTION, cellSections, STOP_AT, OPTIONS);
        for (LodMesh mesh : sections) {
            store.put(position(mesh), mesh);
        }
        return store;
    }

    /**
     * Where a section mesh is, from its origin.
     *
     * @param mesh The mesh.
     * @return Its position, in sections.
     */
    private static LodStore.SectionPos position(LodMesh mesh) {
        final double[] origin = mesh.getOrigin();
        return new LodStore.SectionPos(
                (int) Math.floorDiv((long) origin[0], SECTION),
                (int) Math.floorDiv((long) origin[1], SECTION),
                (int) Math.floorDiv((long) origin[2], SECTION));
    }

    /** Cells meet without cracks in any cut, over several levels of cells. */
    @Test
    void testCellsMeetWithoutCracks() {
        final LodStore store = store(DagTest.terrain(4), 1);
        store.rebuild();
        assertTrue(store.cellCount() > 8, store.cellCount() + " cells");
        final LodDag dag = store.snapshot();
        DagTest.assertCrackFree(dag, 4);
        System.out.print(LodStats.report("cells", dag, List.of()));
    }

    /** A parent's error is at least each child's, and its sphere holds theirs, across cells. */
    @Test
    void testSaturatedAcrossCells() {
        final LodStore store = store(DagTest.terrain(4), 1);
        store.rebuild();
        final LodDag dag = store.snapshot();
        for (LodDag.Group group : dag.groups()) {
            for (int child : group.children()) {
                final LodDag.Cluster c = dag.clusters().get(child);
                assertTrue(group.error() >= c.error(), "Group " + group.id());
                final double[] g = group.bound();
                final double[] s = c.bound();
                final double d =
                        Math.sqrt(
                                (g[0] - s[0]) * (g[0] - s[0])
                                        + (g[1] - s[1]) * (g[1] - s[1])
                                        + (g[2] - s[2]) * (g[2] - s[2]));
                assertTrue(d + s[3] <= g[3] + 1e-6, "Group " + group.id() + " sphere");
            }
        }
    }

    /** Adding a section later and rebuilding gives what building with it from the start does. */
    @Test
    void testRebuildMatchesFresh() {
        final List<LodMesh> sections = DagTest.terrain(4);
        final LodDag fresh = withAll(store(sections, 1));
        final LodStore later = store(sections.subList(0, sections.size() - 1), 1);
        later.rebuild();
        final LodMesh last = sections.get(sections.size() - 1);
        later.put(position(last), last);
        final List<LodStore.CellKey> rebuilt = later.rebuild();
        // Only the section's cell and the cells above it are rebuilt
        for (int i = 1; i < rebuilt.size(); ++i) {
            assertEquals(rebuilt.get(i - 1).level() + 1, rebuilt.get(i).level(), "" + rebuilt);
        }
        final LodDag after = later.snapshot();
        assertEquals(fresh.clusters().size(), after.clusters().size());
        for (int i = 0; i < fresh.clusters().size(); ++i) {
            assertArrayEquals(
                    fresh.clusters().get(i).corners(), after.clusters().get(i).corners(), "" + i);
            assertEquals(fresh.clusters().get(i).error(), after.clusters().get(i).error(), 1e-9);
        }
    }

    /**
     * Build a store and flatten it.
     *
     * @param store The store.
     * @return Its snapshot.
     */
    private static LodDag withAll(LodStore store) {
        store.rebuild();
        return store.snapshot();
    }

    /** Taking every section out leaves no cells. */
    @Test
    void testRemovingSectionsFreesCells() {
        final List<LodMesh> sections = DagTest.terrain(2);
        final LodStore store = store(sections, 1);
        store.rebuild();
        assertTrue(store.cellCount() > 0);
        for (int i = 1; i < sections.size(); ++i) {
            assertTrue(store.remove(position(sections.get(i))));
        }
        store.rebuild();
        assertEquals(1, store.cellCount(), "One section's cell is its own root");
        assertTrue(store.remove(position(sections.get(0))));
        store.rebuild();
        assertEquals(0, store.cellCount());
        assertEquals(0, store.snapshot().clusters().size());
    }

    /** A limited rebuild takes the nearest cells first, and leaves parents until none is dirty. */
    @Test
    void testLimitedRebuildNearestFirst() {
        final LodStore store = store(DagTest.terrain(4), 1);
        final double[] near = {0, 0, 0};
        LodStore.Rebuilt first = store.rebuild(2, near);
        assertEquals(2, first.built().size());
        for (LodStore.CellKey key : first.built()) {
            assertEquals(0, key.level());
            assertTrue(key.x() - LodStore.BIAS <= 1 && key.z() - LodStore.BIAS <= 1, "" + key);
        }
        assertTrue(store.isDirty());
        LodStore.Rebuilt last = first;
        while (store.isDirty()) {
            last = store.rebuild(2, near);
        }
        assertTrue(last.built().stream().anyMatch(key -> key.level() > 0), "Parents come last");
        assertTrue(last.stale().isEmpty());
        final LodDag all = withAll(store(DagTest.terrain(4), 1));
        assertEquals(all.clusters().size(), store.snapshot().clusters().size());
    }

    /** Ancestors of a rebuilt cell whose parents wait are stale until they are rebuilt. */
    @Test
    void testStaleAncestors() {
        final List<LodMesh> sections = DagTest.terrain(4);
        final LodStore store = store(sections, 1);
        store.rebuild();
        final LodMesh a = sections.get(0);
        final LodMesh b = sections.get(sections.size() - 1);
        store.put(position(a), a);
        store.put(position(b), b);
        final LodStore.Rebuilt one = store.rebuild(1, a.getOrigin());
        assertEquals(1, one.built().size());
        assertFalse(one.stale().isEmpty());
        for (LodStore.CellKey key : one.stale()) {
            assertTrue(key.level() > 0);
            assertTrue(store.contains(key));
        }
        final LodStore.Rebuilt rest = store.rebuild(1, a.getOrigin());
        assertTrue(rest.stale().isEmpty());
        assertTrue(rest.built().containsAll(one.stale()), "Every stale cell is rebuilt");
    }

    /** Pieces, cell by cell, give every cluster the same parent as the flattened DAG. */
    @Test
    void testPiecesMatchSnapshot() {
        final LodStore store = store(DagTest.terrain(4), 1);
        store.rebuild();
        final LodDag dag = store.snapshot();
        int id = 0;
        int leaves = 0;
        for (LodStore.CellKey key : store.cellKeys()) {
            for (LodStore.PieceCluster piece : store.piece(key)) {
                final LodDag.Cluster cluster = dag.clusters().get(id);
                assertArrayEquals(cluster.corners(), piece.cluster().corners(), "" + id);
                assertEquals(dag.parentError(id), piece.parentError(), 0, "" + id);
                assertArrayEquals(dag.parentBound(id), piece.parentBound(), 0, "" + id);
                assertEquals(dag.consumer(id) < 0, piece.parentCell() == null, "" + id);
                if (cluster.level() == 0) {
                    assertNotNull(piece.section());
                    ++leaves;
                }
                ++id;
            }
        }
        assertEquals(dag.clusters().size(), id);
        assertTrue(leaves > 0);
    }
}
