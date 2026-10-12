package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ikalagaming.graphics.asset.AssetFile;
import com.ikalagaming.graphics.benchmark.SyntheticTerrain;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Tests for storing region cells in files. */
class LodCellFileTest {

    /** Section size. */
    static final int SECTION = SyntheticTerrain.SECTION;

    /** Options for a test build, on two threads, without visibility to keep it quick. */
    static final DagBuilder.Options OPTIONS = new DagBuilder.Options(0.5, false, 2, false, 0);

    /**
     * A store of sections in one-section cells, not yet built.
     *
     * @param sections The sections.
     * @param cache The cache, or null.
     * @return The store.
     */
    static LodStore store(List<LodMesh> sections, LodCache cache) {
        final LodStore store = new LodStore(new double[3], SECTION, 1, 4, OPTIONS, cache);
        for (LodMesh mesh : sections) {
            store.put(position(mesh), mesh);
        }
        return store;
    }

    /**
     * Where a section mesh is.
     *
     * @param mesh The mesh.
     * @return Its position, in sections.
     */
    static LodStore.SectionPos position(LodMesh mesh) {
        final double[] origin = mesh.getOrigin();
        return new LodStore.SectionPos(
                Math.floorDiv((int) origin[0], SECTION),
                Math.floorDiv((int) origin[1], SECTION),
                Math.floorDiv((int) origin[2], SECTION));
    }

    /**
     * Check two DAGs are the same in every field a file keeps.
     *
     * @param expected One DAG.
     * @param actual The other.
     */
    static void assertSameDag(LodDag expected, LodDag actual) {
        assertArrayEquals(expected.getOrigin(), actual.getOrigin());
        assertEquals(expected.getLeafEdge(), actual.getLeafEdge());
        assertEquals(expected.getStopReason(), actual.getStopReason());
        assertEquals(expected.clusters().size(), actual.clusters().size());
        for (int i = 0; i < expected.clusters().size(); ++i) {
            final LodDag.Cluster a = expected.clusters().get(i);
            final LodDag.Cluster b = actual.clusters().get(i);
            assertEquals(a.id(), b.id());
            assertEquals(a.level(), b.level());
            assertArrayEquals(a.corners(), b.corners());
            assertArrayEquals(a.materials(), b.materials());
            assertArrayEquals(a.visibility(), b.visibility());
            assertEquals(a.error(), b.error());
            assertArrayEquals(a.bound(), b.bound());
            assertArrayEquals(a.tight(), b.tight());
            assertEquals(a.producer(), b.producer());
            assertEquals(a.section(), b.section());
            assertEquals(expected.consumer(i), actual.consumer(i));
        }
        assertEquals(expected.groups().size(), actual.groups().size());
        for (int i = 0; i < expected.groups().size(); ++i) {
            final LodDag.Group a = expected.groups().get(i);
            final LodDag.Group b = actual.groups().get(i);
            assertEquals(a.id(), b.id());
            assertEquals(a.level(), b.level());
            assertArrayEquals(a.children(), b.children());
            assertArrayEquals(a.outputs(), b.outputs());
            assertEquals(a.error(), b.error());
            assertArrayEquals(a.bound(), b.bound());
            assertEquals(a.simplifyError(), b.simplifyError());
            assertEquals(a.quadricError(), b.quadricError());
            assertEquals(a.trianglesIn(), b.trianglesIn());
            assertEquals(a.trianglesOut(), b.trianglesOut());
            assertEquals(a.borderEdges(), b.borderEdges());
            assertEquals(a.edges(), b.edges());
            assertEquals(a.virtualEdges(), b.virtualEdges());
            assertEquals(a.rejectedFlips(), b.rejectedFlips());
            assertEquals(a.rejectedLinks(), b.rejectedLinks());
            assertEquals(a.buildNanos(), b.buildNanos());
        }
    }

    /** Every cell, leaves and parents, reads back as it was written. */
    @Test
    void testRoundTrip() {
        final LodStore store = store(DagTest.terrain(2), null);
        store.rebuild();
        int parents = 0;
        for (LodStore.CellKey key : store.cellKeys()) {
            final LodStore.Cell cell = store.built(key);
            final AssetFile file =
                    AssetFile.read(
                            LodCellFile.encode("k", LodStore.BUILDER_VERSION, key, cell).toBuffer(),
                            LodCellFile.known());
            final LodCellFile.Stored back = LodCellFile.decode(file, "k", LodStore.BUILDER_VERSION);
            assertEquals(key, back.key());
            assertSameDag(cell.dag(), back.cell().dag());
            assertEquals(cell.inputs(), back.cell().inputs());
            assertEquals(cell.sections(), back.cell().sections());
            assertEquals(cell.leafEdge(), back.cell().leafEdge());
            parents += key.level() > 0 ? 1 : 0;
        }
        assertEquals(true, parents > 0, "Parents were checked too");
    }

    /** A file read for the wrong key or builder version is refused. */
    @Test
    void testWrongKeyOrVersionRefused() {
        final LodStore store = store(DagTest.terrain(1), null);
        store.rebuild();
        final LodStore.CellKey key = store.cellKeys().iterator().next();
        final AssetFile file =
                AssetFile.read(
                        LodCellFile.encode("k", LodStore.BUILDER_VERSION, key, store.built(key))
                                .toBuffer(),
                        LodCellFile.known());
        assertThrows(
                IllegalArgumentException.class,
                () -> LodCellFile.decode(file, "other", LodStore.BUILDER_VERSION));
        assertThrows(
                IllegalArgumentException.class,
                () -> LodCellFile.decode(file, "k", LodStore.BUILDER_VERSION + 1));
    }
}
