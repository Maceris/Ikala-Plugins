package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for {@link DeviceTable}'s choice between copy regions and the scatter dispatch. */
class DeviceTableTest {

    @Test
    void consecutiveIndicesMakeOneRun() {
        assertEquals(1, DeviceTable.runsOf(new int[] {4, 5, 6, 7}, 4));
        assertEquals(1, DeviceTable.runsOf(new int[] {9}, 1));
    }

    @Test
    void eachGapStartsARun() {
        assertEquals(3, DeviceTable.runsOf(new int[] {0, 1, 5, 6, 9}, 5));
        // Only the counted entries matter
        assertEquals(2, DeviceTable.runsOf(new int[] {0, 1, 5, 6, 9}, 4));
    }

    @Test
    void scatteredEntriesGoPastTheCopyLimit() {
        // Every other slot, as releasing a camera jump's worth of meshes leaves them
        final int count = 1000;
        final int[] indices = new int[count];
        for (int i = 0; i < count; ++i) {
            indices[i] = 2 * i;
        }
        assertTrue(DeviceTable.runsOf(indices, count) > DeviceTable.SCATTER_RUNS);
    }
}
