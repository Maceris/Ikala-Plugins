package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.benchmark.SyntheticTerrain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Tests for keeping the level of detail of a changing world, built batch by batch. */
class LiveLodTest {

    /** The section size, in units. */
    private static final int SECTION = SyntheticTerrain.SECTION;

    /** Options for a test build, on two threads. */
    private static final DagBuilder.Options OPTIONS =
            new DagBuilder.Options(0.5, false, 2, false, 0);

    /** A live world whose batches are kept, built by stepping. */
    private static final class Kept {
        /** The batches, in order. */
        final List<LiveLod.Batch> batches = new ArrayList<>();

        /** The world. */
        final LiveLod live = new LiveLod(new double[3], SECTION, 1, OPTIONS, batches::add);

        /** The cells the batches leave, with their piece sizes. */
        final Map<LodStore.CellKey, Integer> cells = new TreeMap<>();

        /** The newest generation each section is shown at. */
        final Map<LodStore.SectionPos, Long> covered = new TreeMap<>();

        /**
         * Step until there is nothing left to build, applying each batch.
         *
         * @return The batches built.
         */
        List<LiveLod.Batch> settle() {
            final int from = batches.size();
            while (live.step()) {
                // Each step publishes one batch
            }
            final List<LiveLod.Batch> out = batches.subList(from, batches.size());
            for (LiveLod.Batch batch : out) {
                batch.removed().forEach(cells::remove);
                for (LiveLod.Piece piece : batch.added()) {
                    assertEquals(piece.clusters().size(), piece.meshes().size());
                    cells.put(piece.key(), piece.clusters().size());
                }
                covered.putAll(batch.covered());
            }
            return out;
        }
    }

    /**
     * Where a section mesh is.
     *
     * @param mesh The mesh.
     * @return Its position, in sections.
     */
    private static LodStore.SectionPos position(LodMesh mesh) {
        final double[] origin = mesh.getOrigin();
        return new LodStore.SectionPos(
                Math.floorDiv((int) origin[0], SECTION),
                Math.floorDiv((int) origin[1], SECTION),
                Math.floorDiv((int) origin[2], SECTION));
    }

    /**
     * Put sections in at a generation.
     *
     * @param kept The world.
     * @param sections The sections.
     * @param generation Their generation.
     */
    private static void putAll(Kept kept, List<LodMesh> sections, long generation) {
        for (LodMesh mesh : sections) {
            kept.live.put(position(mesh), mesh, generation);
        }
    }

    /** Loading builds near cells in batches, then their parents, ending with every cell. */
    @Test
    void testLoadEndsWithAllCells() {
        final List<LodMesh> sections = DagTest.terrain(4);
        final Kept kept = new Kept();
        putAll(kept, sections, 1);
        final List<LiveLod.Batch> batches = kept.settle();
        assertTrue(batches.size() > 1, batches.size() + " batches");
        final LiveLod.Batch last = batches.get(batches.size() - 1);
        assertTrue(last.stale().isEmpty());
        assertTrue(last.added().stream().anyMatch(piece -> piece.key().level() > 0));
        int clusters = 0;
        for (int size : kept.cells.values()) {
            clusters += size;
        }
        assertEquals(kept.live.snapshot().clusters().size(), clusters);
        for (LodMesh mesh : sections) {
            assertEquals(1L, kept.covered.get(position(mesh)));
        }
    }

    /** An edit comes back as one batch with its cell and every cell above it. */
    @Test
    void testEditIsOneChain() {
        final List<LodMesh> sections = DagTest.terrain(4);
        final Kept kept = new Kept();
        putAll(kept, sections, 1);
        kept.settle();
        final LodMesh edited = sections.get(sections.size() / 2);
        kept.live.put(position(edited), edited, 2);
        final List<LiveLod.Batch> batches = kept.settle();
        assertEquals(1, batches.size());
        final LiveLod.Batch batch = batches.get(0);
        assertTrue(batch.stale().isEmpty());
        final List<LiveLod.Piece> added = batch.added();
        for (int i = 0; i < added.size(); ++i) {
            assertEquals(i, added.get(i).key().level(), "One cell per level, bottom up");
        }
        assertEquals(2L, batch.covered().get(position(edited)));
        assertFalse(batch.tops().isEmpty(), "Untouched cells' tops get their new parents");
    }

    /** Many edits to a section before a build come back as one, at the newest generation. */
    @Test
    void testRapidEditsCoalesce() {
        final List<LodMesh> sections = DagTest.terrain(2);
        final Kept kept = new Kept();
        putAll(kept, sections, 1);
        kept.settle();
        final LodMesh edited = sections.get(0);
        for (long generation = 2; generation <= 5; ++generation) {
            kept.live.put(position(edited), edited, generation);
        }
        // An older change arriving late is ignored
        kept.live.put(position(edited), edited, 3);
        final List<LiveLod.Batch> batches = kept.settle();
        assertEquals(1, batches.size());
        assertEquals(5L, batches.get(0).covered().get(position(edited)));
    }

    /** Taking sections out is covered once their cells are rebuilt, or at once if never in. */
    @Test
    void testRemovalsAreCovered() {
        final List<LodMesh> sections = DagTest.terrain(2);
        final Kept kept = new Kept();
        putAll(kept, sections, 1);
        kept.settle();
        final LodStore.SectionPos gone = position(sections.get(0));
        kept.live.remove(gone, 2);
        final LodStore.SectionPos never = new LodStore.SectionPos(100, 100, 100);
        kept.live.remove(never, 7);
        kept.settle();
        assertEquals(2L, kept.covered.get(gone));
        assertEquals(7L, kept.covered.get(never));
    }
}
