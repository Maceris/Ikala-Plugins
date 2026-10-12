package com.ikalagaming.graphics.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Where the occupancy volume's levels sit, and which voxels moving them exposes. */
class OccupancyClipmapTest {

    private static final int SIZE = OccupancyClipmap.SIZE;

    @Test
    void theFirstUpdateExposesEveryLevelWhole() {
        final OccupancyClipmap clipmap = new OccupancyClipmap();
        final List<OccupancyClipmap.Box> boxes = clipmap.update(new Vector3d(10, 40, -7));
        assertEquals(OccupancyClipmap.LEVELS, boxes.size());
        for (OccupancyClipmap.Box box : boxes) {
            assertEquals((long) SIZE * SIZE * SIZE, box.volume());
        }
    }

    @Test
    void theCameraStaysNearTheMiddleOfEveryLevel() {
        final OccupancyClipmap clipmap = new OccupancyClipmap();
        final Vector3d camera = new Vector3d(1234.5, -77.25, -9876.5);
        clipmap.update(camera);
        for (int level = 0; level < OccupancyClipmap.LEVELS; ++level) {
            final int size = OccupancyClipmap.VOXEL_SIZES[level];
            for (int axis = 0; axis < 3; ++axis) {
                final double voxel = camera.get(axis) / size - clipmap.origin(level)[axis];
                assertTrue(voxel >= SIZE / 2 - OccupancyClipmap.SNAP, "Level " + level);
                assertTrue(voxel <= SIZE / 2 + OccupancyClipmap.SNAP, "Level " + level);
            }
        }
    }

    @Test
    void movingExposesExactlyTheNewlyCoveredVoxels() {
        final OccupancyClipmap clipmap = new OccupancyClipmap();
        clipmap.update(new Vector3d(0, 0, 0));
        final long[][] before = new long[OccupancyClipmap.LEVELS][];
        for (int level = 0; level < OccupancyClipmap.LEVELS; ++level) {
            before[level] = clipmap.origin(level).clone();
        }
        final List<OccupancyClipmap.Box> boxes = clipmap.update(new Vector3d(21, -9, 40));
        for (int level = 0; level < OccupancyClipmap.LEVELS; ++level) {
            final long[] after = clipmap.origin(level);
            long expected = (long) SIZE * SIZE * SIZE;
            long overlap = 1;
            for (int axis = 0; axis < 3; ++axis) {
                overlap *= SIZE - Math.abs(after[axis] - before[level][axis]);
            }
            expected -= overlap;
            long exposed = 0;
            final Set<String> seen = new HashSet<>();
            for (OccupancyClipmap.Box box : boxes) {
                if (box.level() != level) {
                    continue;
                }
                exposed += box.volume();
                // Every exposed voxel is new to the level, and none is exposed twice
                for (long x = box.min()[0]; x < box.max()[0]; ++x) {
                    for (long y = box.min()[1]; y < box.max()[1]; ++y) {
                        for (long z = box.min()[2]; z < box.max()[2]; ++z) {
                            assertTrue(seen.add(x + "," + y + "," + z));
                            final boolean wasCovered =
                                    x >= before[level][0]
                                            && x < before[level][0] + SIZE
                                            && y >= before[level][1]
                                            && y < before[level][1] + SIZE
                                            && z >= before[level][2]
                                            && z < before[level][2] + SIZE;
                            assertTrue(!wasCovered, "Level " + level);
                        }
                    }
                }
            }
            assertEquals(expected, exposed, "Level " + level);
        }
    }

    @Test
    void standingStillExposesNothing() {
        final OccupancyClipmap clipmap = new OccupancyClipmap();
        clipmap.update(new Vector3d(5, 5, 5));
        assertTrue(clipmap.update(new Vector3d(5.4, 5.2, 4.9)).isEmpty());
    }

    @Test
    void aSectionMapsToWholeVoxelsInEveryLevel() {
        final OccupancyClipmap clipmap = new OccupancyClipmap();
        clipmap.update(new Vector3d(0, 0, 0));
        final List<OccupancyClipmap.Box> boxes =
                clipmap.boxesFor(new double[] {16, -16, 32}, new double[] {32, 0, 48});
        assertEquals(OccupancyClipmap.LEVELS, boxes.size());
        for (OccupancyClipmap.Box box : boxes) {
            final double[] world = box.world();
            assertEquals(16, world[0]);
            assertEquals(-16, world[1]);
            assertEquals(32, world[2]);
            assertEquals(32, world[3]);
            assertEquals(0, world[4]);
            assertEquals(48, world[5]);
        }
        // Far outside every level, nothing
        assertTrue(
                clipmap.boxesFor(new double[] {5000, 0, 0}, new double[] {5016, 16, 16}).isEmpty());
    }

    @Test
    void aWholeLevelIsEveryVoxelItCovers() {
        final OccupancyClipmap clipmap = new OccupancyClipmap();
        clipmap.update(new Vector3d(300, -20, 75));
        for (int level = 0; level < OccupancyClipmap.LEVELS; ++level) {
            final OccupancyClipmap.Box whole = clipmap.whole(level);
            assertEquals(level, whole.level());
            assertEquals((long) SIZE * SIZE * SIZE, whole.volume());
            for (int axis = 0; axis < 3; ++axis) {
                assertEquals(clipmap.origin(level)[axis], whole.min()[axis]);
            }
        }
    }

    @Test
    void texelsWrapAround() {
        assertEquals(0, OccupancyClipmap.texel(0));
        assertEquals(SIZE - 1, OccupancyClipmap.texel(-1));
        assertEquals(5, OccupancyClipmap.texel(SIZE * 7L + 5));
        assertEquals(SIZE - 3, OccupancyClipmap.texel(-SIZE * 4L - 3));
    }
}
