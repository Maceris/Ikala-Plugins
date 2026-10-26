package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.lod.LodStore;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** The packed set of section positions the volume atlas scans for the nearest waiting ones. */
class PackedPositionsTest {

    /**
     * Through random adds and removes, it holds what a set would, each position once, with its own
     * corner beside it.
     */
    @Test
    void testMatchesASet() {
        final Random random = new Random(11);
        final PackedPositions packed = new PackedPositions();
        final Set<LodStore.SectionPos> expected = new HashSet<>();
        for (int step = 0; step < 20_000; ++step) {
            final LodStore.SectionPos position =
                    new LodStore.SectionPos(
                            random.nextInt(40) - 20, random.nextInt(4), random.nextInt(40) - 20);
            if (random.nextInt(3) == 0) {
                assertEquals(expected.remove(position), packed.remove(position));
            } else {
                assertEquals(expected.add(position), packed.add(position));
            }
        }
        assertEquals(expected.size(), packed.size());
        final Set<LodStore.SectionPos> seen = new HashSet<>();
        for (int i = 0; i < packed.size(); ++i) {
            final LodStore.SectionPos position = packed.positions()[i];
            assertTrue(expected.contains(position));
            assertTrue(packed.contains(position));
            assertTrue(seen.add(position), "Each position once");
            assertEquals(position.x(), packed.corners()[i * PackedPositions.CORNER_VALUES]);
            assertEquals(position.y(), packed.corners()[i * PackedPositions.CORNER_VALUES + 1]);
            assertEquals(position.z(), packed.corners()[i * PackedPositions.CORNER_VALUES + 2]);
        }
    }
}
