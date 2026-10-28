package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.benchmark.SyntheticTerrain;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A store fed a window of sections that moves along, as a world streamed around a camera is. */
class LodStoreStreamingTest {

    /** The section size, in units. */
    private static final int SECTION = SyntheticTerrain.SECTION;

    /** Options for a test build, on two threads. */
    private static final DagBuilder.Options OPTIONS =
            new DagBuilder.Options(0.5, false, 2, false, 12);

    /** Clusters a cell stops at. */
    private static final int STOP_AT = 4;

    /** How many columns of sections the window holds along x. */
    private static final int WINDOW = 3;

    /** Columns of terrain along x and z. */
    private static final int COLUMNS = 8;

    @Test
    void parentsAreBuiltWhileTheWindowMovesAndEveryBuiltCellIsThere() {
        final Map<LodStore.SectionPos, LodMesh> world = new HashMap<>();
        for (LodMesh mesh : DagTest.terrain(COLUMNS)) {
            final double[] origin = mesh.getOrigin();
            world.put(
                    new LodStore.SectionPos(
                            (int) Math.floorDiv((long) origin[0], SECTION),
                            (int) Math.floorDiv((long) origin[1], SECTION),
                            (int) Math.floorDiv((long) origin[2], SECTION)),
                    mesh);
        }
        final LodStore store = new LodStore(new double[3], SECTION, 1, STOP_AT, OPTIONS);
        final Set<LodStore.SectionPos> in = new HashSet<>();
        boolean parentsWhileMoving = false;
        for (int start = 0; start + WINDOW <= COLUMNS; ++start) {
            final int from = start;
            for (Map.Entry<LodStore.SectionPos, LodMesh> entry : world.entrySet()) {
                final int x = entry.getKey().x();
                final boolean inside = x >= from && x < from + WINDOW;
                if (inside && in.add(entry.getKey())) {
                    store.put(entry.getKey(), entry.getValue());
                } else if (!inside && in.remove(entry.getKey())) {
                    store.remove(entry.getKey());
                }
            }
            // The cells ahead are always still on the way, as when streaming
            final List<LodStore.CellKey> waiting =
                    List.of(store.cellOf(new LodStore.SectionPos(from + WINDOW, 0, 0)));
            final double[] near = {(from + 1.5) * SECTION, 0, COLUMNS * SECTION / 2.0};
            for (int round = 0; round < COLUMNS * COLUMNS; ++round) {
                final LodStore.Rebuilt rebuilt =
                        store.rebuild(2, near, key -> true, key -> true, waiting);
                for (LodStore.CellKey key : rebuilt.built()) {
                    assertTrue(store.contains(key), "Built and there: " + key);
                    // As the live world takes it, which failed on a cell taken out again
                    store.piece(key);
                    parentsWhileMoving |= key.level() > 0;
                }
                for (LodStore.CellKey key : rebuilt.removed()) {
                    assertFalse(store.contains(key), "Taken out and gone: " + key);
                }
                if (!store.hasWork(key -> true, key -> true, waiting)) {
                    break;
                }
            }
            assertTrue(store.dirtyCells().isEmpty(), "Every cell in the window built");
        }
        assertTrue(parentsWhileMoving, "Parents were built with sections still on the way");
        final int root =
                store.cellKeys().stream().mapToInt(LodStore.CellKey::level).max().orElse(-1);
        assertEquals(
                1,
                store.cellKeys().stream().filter(key -> key.level() == root).count(),
                "One root");
    }
}
