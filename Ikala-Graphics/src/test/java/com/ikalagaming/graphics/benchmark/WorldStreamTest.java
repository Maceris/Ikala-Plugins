package com.ikalagaming.graphics.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.lod.LodStore;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Which cells and lake tiles the world stream adds and removes as the camera moves. */
class WorldStreamTest {

    private static final LodStore.SectionPos ORIGIN = new LodStore.SectionPos(0, 0, 0);

    private static final int CELL = WorldStream.CELL_SECTIONS;

    @Test
    void theSphereHoldsWhatIsWithinTheRadius() {
        assertEquals(1, WorldStream.sphere(ORIGIN, 0).size());
        assertEquals(7, WorldStream.sphere(ORIGIN, 1).size());
    }

    @Test
    void sectionsBelongToTheCellOfTheirFloor() {
        assertEquals(ORIGIN, WorldStream.cellOf(new LodStore.SectionPos(CELL - 1, 0, 0)));
        assertEquals(
                new LodStore.SectionPos(-1, -1, 1),
                WorldStream.cellOf(new LodStore.SectionPos(-1, -CELL, CELL)));
        final List<LodStore.SectionPos> sections =
                WorldStream.sectionsOf(new LodStore.SectionPos(-1, 0, 2));
        assertEquals(CELL * CELL * CELL, sections.size());
        for (LodStore.SectionPos section : sections) {
            assertEquals(new LodStore.SectionPos(-1, 0, 2), WorldStream.cellOf(section));
        }
    }

    @Test
    void movingAddsTheCellsAheadNearestFirst() {
        final int radius = 16;
        final Set<LodStore.SectionPos> known =
                new HashSet<>(WorldStream.missing(Set.of(), ORIGIN, radius));
        assertTrue(WorldStream.missing(known, ORIGIN, radius).isEmpty(), "Nothing left");

        final LodStore.SectionPos moved = new LodStore.SectionPos(CELL, 0, 0);
        final List<LodStore.SectionPos> missing = WorldStream.missing(known, moved, radius);
        assertFalse(missing.isEmpty());
        long last = 0;
        for (LodStore.SectionPos cell : missing) {
            final long distance = WorldStream.doubledDistanceSquared(cell, moved);
            assertTrue(distance >= last, "Nearest first");
            assertTrue(WorldStream.within(cell, moved, radius));
            assertTrue(cell.x() > 0, "Only ahead of where the world was");
            last = distance;
        }
    }

    @Test
    void cellsAreRemovedOnlyPastTheMargin() {
        final int radius = 16;
        final Set<LodStore.SectionPos> known =
                new HashSet<>(WorldStream.missing(Set.of(), ORIGIN, radius));
        assertTrue(
                WorldStream.beyond(known, new LodStore.SectionPos(1, 0, 0), radius).isEmpty(),
                "A step inside the margin removes nothing");

        final LodStore.SectionPos far = new LodStore.SectionPos(3 * CELL, 0, 0);
        final List<LodStore.SectionPos> beyond = WorldStream.beyond(known, far, radius);
        assertFalse(beyond.isEmpty());
        for (LodStore.SectionPos cell : known) {
            assertEquals(
                    !WorldStream.within(cell, far, radius + WorldStream.REMOVE_MARGIN),
                    beyond.contains(cell),
                    "Removed exactly when its middle is past the radius and margin: " + cell);
        }
    }

    @Test
    void tilesCountByTheirNearestPoint() {
        assertTrue(WorldStream.tileWithin(0, 0, 64, 32, 32, 0), "The camera over it");
        assertTrue(WorldStream.tileWithin(0, 0, 64, 100, 32, 36));
        assertFalse(WorldStream.tileWithin(0, 0, 64, 100, 32, 35));
        assertFalse(WorldStream.tileWithin(0, 0, 64, 100, 100, 50), "Diagonally away");
    }

    @Test
    void tileKeysKeepNegativeCorners() {
        final long key = WorldStream.tileKey(-128, -64);
        assertEquals(-128, (int) (key >> Integer.SIZE));
        assertEquals(-64, (int) key);
    }

    @Test
    void percentilesTakeTheValueAtOrAbove() {
        final double[] sorted = {1, 2, 3, 4};
        assertEquals(2, WorldStream.percentile(sorted, 0.5));
        assertEquals(4, WorldStream.percentile(sorted, 0.95));
        assertEquals(4, WorldStream.percentile(sorted, 1));
        assertEquals(0, WorldStream.percentile(new double[0], 0.5));
    }
}
