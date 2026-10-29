package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** How changed boxes are noted and taken, with what changed in each told apart. */
class ShadowInvalidationTest {

    /**
     * Spheres come out as the boxes around them, in the order they went in, each saying what
     * changed.
     */
    @Test
    void testChangesAreToldApart() {
        final ShadowInvalidation invalidation = new ShadowInvalidation();
        final double[] origin = {100, 0, -50};
        invalidation.addLevelOfDetail(origin, new double[] {1, 2, 3, 4});
        invalidation.addSwap(origin, new double[] {5, 6, 7, 1});
        invalidation.add(new double[] {0, 0, 0}, new double[] {1, 1, 1});
        final List<double[]> boxes = new ArrayList<>();
        final List<ShadowInvalidation.Change> changes = new ArrayList<>();
        invalidation.drain(
                (min, max, change) -> {
                    boxes.add(new double[] {min[0], min[1], min[2], max[0], max[1], max[2]});
                    changes.add(change);
                });
        assertEquals(
                List.of(
                        ShadowInvalidation.Change.LEVEL_OF_DETAIL,
                        ShadowInvalidation.Change.SWAP,
                        ShadowInvalidation.Change.SURFACE),
                changes);
        assertArrayEquals(new double[] {97, -2, -51, 105, 6, -43}, boxes.get(0), 1e-9);
        assertArrayEquals(new double[] {104, 5, -44, 106, 7, -42}, boxes.get(1), 1e-9);
        // Taken once
        invalidation.drain((min, max, change) -> changes.add(change));
        assertEquals(3, changes.size());
    }
}
