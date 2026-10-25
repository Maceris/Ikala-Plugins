package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** How changed boxes are noted and taken, with swaps of levels of detail told apart. */
class ShadowInvalidationTest {

    /**
     * Spheres come out as the boxes around them, in the order they went in, and only those noted as
     * swaps say so.
     */
    @Test
    void testSwapsAreToldApart() {
        final ShadowInvalidation invalidation = new ShadowInvalidation();
        final double[] origin = {100, 0, -50};
        invalidation.addSphere(origin, new double[] {1, 2, 3, 4});
        invalidation.addSwap(origin, new double[] {5, 6, 7, 1});
        invalidation.add(new double[] {0, 0, 0}, new double[] {1, 1, 1});
        final List<double[]> boxes = new ArrayList<>();
        final List<Boolean> swaps = new ArrayList<>();
        invalidation.drain(
                (min, max, swap) -> {
                    boxes.add(new double[] {min[0], min[1], min[2], max[0], max[1], max[2]});
                    swaps.add(swap);
                });
        assertEquals(List.of(false, true, false), swaps);
        assertArrayEquals(new double[] {97, -2, -51, 105, 6, -43}, boxes.get(0), 1e-9);
        assertArrayEquals(new double[] {104, 5, -44, 106, 7, -42}, boxes.get(1), 1e-9);
        // Taken once
        invalidation.drain((min, max, swap) -> swaps.add(swap));
        assertEquals(3, swaps.size());
    }
}
