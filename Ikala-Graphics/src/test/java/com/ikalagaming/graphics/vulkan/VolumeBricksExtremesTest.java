package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.graph.VolumeMath;
import com.ikalagaming.graphics.lod.LodStore;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Picking the nearest or farthest sections for the volume atlas's slots. */
class VolumeBricksExtremesTest {

    /** Where the camera is. */
    private static final Vector3d CAMERA = new Vector3d(37, -5, 12);

    /**
     * How far a section's middle is from the camera, squared, as the slots weigh it.
     *
     * @param position The section.
     * @return The squared distance, as a float.
     */
    private static float distance(LodStore.SectionPos position) {
        final double size = VolumeMath.BRICK * VolumeMath.VOXEL_SIZE;
        final double dx = position.x() * size + size / 2 - CAMERA.x;
        final double dy = position.y() * size + size / 2 - CAMERA.y;
        final double dz = position.z() * size + size / 2 - CAMERA.z;
        return (float) (dx * dx + dy * dy + dz * dz);
    }

    /**
     * Random sections, some at the same distance, so ties come up.
     *
     * @return The sections.
     */
    private static List<LodStore.SectionPos> sections() {
        final Random random = new Random(3);
        final List<LodStore.SectionPos> out = new ArrayList<>();
        for (int i = 0; i < 5000; ++i) {
            final int x = random.nextInt(200) - 100;
            final int z = random.nextInt(200) - 100;
            out.add(new LodStore.SectionPos(x, random.nextInt(8) - 4, z));
            if (i % 50 == 0) {
                // Mirrored, the same distance away along x from a camera at a section middle
                out.add(new LodStore.SectionPos(-x, 0, z));
            }
        }
        return out;
    }

    /**
     * The nearest and the farthest picked are the same as sorting them all, in order, for counts
     * under, at and over how many there are.
     */
    @Test
    void testPicksMatchSortingAll() {
        final List<LodStore.SectionPos> all = sections();
        for (boolean farthest : new boolean[] {false, true}) {
            final Comparator<Float> order =
                    farthest ? Comparator.reverseOrder() : Comparator.naturalOrder();
            final List<Float> sorted = new ArrayList<>();
            for (LodStore.SectionPos position : all) {
                sorted.add(distance(position));
            }
            sorted.sort(order);
            for (int most : new int[] {0, 1, 256, all.size(), all.size() + 10}) {
                final List<LodStore.SectionPos> picked =
                        VolumeBricks.extremes(all, CAMERA, most, farthest);
                assertEquals(Math.min(most, all.size()), picked.size());
                for (int i = 0; i < picked.size(); ++i) {
                    assertEquals(
                            sorted.get(i),
                            distance(picked.get(i)),
                            "Farthest " + farthest + ", " + most + " picked, at " + i);
                }
            }
        }
    }

    /** Nothing to pick from gives nothing. */
    @Test
    void testEmpty() {
        assertTrue(VolumeBricks.extremes(List.of(), CAMERA, 256, false).isEmpty());
    }
}
