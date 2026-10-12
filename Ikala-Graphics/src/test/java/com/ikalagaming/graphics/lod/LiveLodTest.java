package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.benchmark.SyntheticTerrain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Tests for keeping the level of detail of a changing world, built batch by batch. */
@Tag("slow")
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

        /** The time its changes are made at, in nanoseconds. */
        long now;

        /** A world whose cells are built as soon as they change. */
        Kept() {
            this(0);
        }

        /**
         * A world whose cells wait for their sections to settle.
         *
         * @param settleNanos How long they wait.
         */
        Kept(long settleNanos) {
            live.setSettleNanos(settleNanos);
            live.setClock(() -> now);
        }

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

    /**
     * Loading cells wait until their sections settle, even the observer's, and parents wait for
     * all; afterwards an edit to the observer's cell is built at once and one elsewhere waits.
     */
    @Test
    void testCellsWaitToSettle() {
        final List<LodMesh> sections = DagTest.terrain(4);
        final Kept kept = new Kept(LiveLod.SETTLE_NANOS);
        final LodMesh own = sections.get(0);
        final double[] corner = own.getOrigin();
        kept.live.setNear(corner[0] + 1, corner[1] + 1, corner[2] + 1);
        putAll(kept, sections, 1);
        assertTrue(kept.settle().isEmpty(), "Nothing is built while sections arrive");
        kept.now += LiveLod.SETTLE_NANOS - 1;
        assertTrue(kept.settle().isEmpty(), "Nothing has settled yet");
        kept.now += 1;
        final List<LiveLod.Batch> loaded = kept.settle();
        assertFalse(loaded.isEmpty());
        assertTrue(loaded.get(loaded.size() - 1).stale().isEmpty());
        for (LodMesh mesh : sections) {
            assertEquals(1L, kept.covered.get(position(mesh)));
        }
        // An edit in the observer's cell is built at once, with its chain
        kept.live.put(position(own), own, 2);
        final List<LiveLod.Batch> near = kept.settle();
        assertEquals(1, near.size());
        assertEquals(kept.live.cellOf(position(own)), near.get(0).added().get(0).key());
        assertEquals(2L, kept.covered.get(position(own)));
        // One elsewhere waits for its section to settle
        final LodMesh edited = sections.get(sections.size() - 1);
        kept.live.put(position(edited), edited, 2);
        assertTrue(kept.settle().isEmpty());
        kept.now += LiveLod.SETTLE_NANOS;
        assertEquals(1, kept.settle().size());
        assertEquals(2L, kept.covered.get(position(edited)));
    }

    /** A cell waits for a bake on the way however long it takes, and not for one abandoned. */
    @Test
    void testCellsWaitForExpectedBakes() {
        final List<LodMesh> sections = DagTest.terrain(2);
        final Kept kept = new Kept(LiveLod.SETTLE_NANOS);
        final LodMesh late = sections.get(0);
        final LodMesh failed = sections.get(sections.size() - 1);
        kept.live.expect(position(late), 1);
        kept.live.expect(position(failed), 1);
        for (LodMesh mesh : sections) {
            if (mesh != late && mesh != failed) {
                kept.live.put(position(mesh), mesh, 1);
            }
        }
        kept.now += 10 * LiveLod.SETTLE_NANOS;
        final List<LiveLod.Batch> early = kept.settle();
        for (LiveLod.Batch batch : early) {
            for (LiveLod.Piece piece : batch.added()) {
                assertTrue(piece.key().level() == 0, "No parent before every cell is built");
                assertFalse(piece.key().equals(kept.live.cellOf(position(late))));
                assertFalse(piece.key().equals(kept.live.cellOf(position(failed))));
            }
        }
        kept.live.abandon(position(failed), 1);
        kept.live.put(position(late), late, 1);
        kept.now += LiveLod.SETTLE_NANOS;
        kept.settle();
        assertEquals(1L, kept.covered.get(position(late)));
        assertTrue(kept.cells.containsKey(kept.live.cellOf(position(late))));
        assertTrue(kept.cells.keySet().stream().anyMatch(key -> key.level() > 0));
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
