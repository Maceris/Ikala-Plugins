package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for turning fine triangles into coverage volumes. */
class CoverageVoxelizerTest {

    /** How wide the test voxels are. */
    private static final double VOXEL = 0.5;

    /** How wide the test plane is. */
    private static final float PLANE = 4;

    /** The plane's color. */
    private static final float[] LOOK = {0.2f, 0.6f, 0.1f, 0.5f, 0, 0.5f};

    /** The seed the tests voxelize with. */
    private static final long SEED = 1234;

    /**
     * A flat square at a height, as two triangles facing up.
     *
     * @param y Its height.
     * @return The corners.
     */
    private static float[] plane(float y) {
        return new float[] {
            0, y, 0, 0, y, PLANE, PLANE, y, PLANE, 0, y, 0, PLANE, y, PLANE, PLANE, y, 0
        };
    }

    /**
     * Averages giving every material the test look, covering a share.
     *
     * @param coverage The share its texture covers.
     * @return The averages.
     */
    private static MaterialAverages covering(float coverage) {
        return new MaterialAverages() {
            @Override
            public float[] average(int material) {
                return LOOK;
            }

            @Override
            public float coverage(int material) {
                return coverage;
            }
        };
    }

    /**
     * How much light all of a volume's voxels stop, each over its width, averaged over directions.
     *
     * @param volume The volume.
     * @return The sum.
     */
    private static double stopped(CoverageVolume volume) {
        double sum = 0;
        final float[] sggx = new float[CoverageVolume.SGGX_VALUES];
        for (int v = 0; v < volume.size(); ++v) {
            for (int k = 0; k < sggx.length; ++k) {
                sggx[k] = volume.sggx(v, k);
            }
            sum += CoverageVoxelizer.attenuation(sggx, 0, volume.density(v), volume.voxelSize());
        }
        return sum;
    }

    /**
     * A flat surface makes voxels along it that stop light most along its normal, with its color.
     */
    @Test
    void testPlaneStopsLightAlongItsNormal() {
        final CoverageVolume volume =
                CoverageVoxelizer.voxelize(plane(1.25f), new int[2], covering(1), VOXEL, SEED);
        assertTrue(volume.size() > 0);
        final double[] up = {0, 1, 0};
        final double[] across = {1, 0, 0};
        for (int v = 0; v < volume.size(); ++v) {
            assertTrue(volume.density(v) > 0, "Voxel " + v);
            assertTrue(
                    volume.projectedArea(v, up) > 2 * volume.projectedArea(v, across),
                    "Voxel " + v + " faces the wrong way");
            for (int c = 0; c < CoverageVolume.COLOR_VALUES; ++c) {
                assertEquals(LOOK[c], volume.color(v, c), 1e-6);
            }
            // Its own material, unshaded, since nothing shadows a lone plane
            assertEquals(0, volume.material(v));
            assertEquals(1, volume.shade(v), 1e-6);
        }
    }

    /** A material its texture doesn't cover at all makes no voxels. */
    @Test
    void testUncoveredMakesNothing() {
        assertEquals(
                0,
                CoverageVoxelizer.voxelize(plane(1.25f), new int[2], covering(0), VOXEL, SEED)
                        .size());
    }

    /** A material its texture covers half of stops about half the light. */
    @Test
    void testHalfCoveredStopsAboutHalf() {
        final double full =
                stopped(
                        CoverageVoxelizer.voxelize(
                                plane(1.25f), new int[2], covering(1), VOXEL, SEED));
        final double half =
                stopped(
                        CoverageVoxelizer.voxelize(
                                plane(1.25f), new int[2], covering(0.5f), VOXEL, SEED));
        assertTrue(half > 0.35 * full && half < 0.65 * full, half + " of " + full);
    }

    /** How many planes the test stack has. */
    private static final int STACK = 8;

    /** How far apart the stacked planes are, a fraction of a voxel. */
    private static final float STACK_SPACING = 0.05f;

    /**
     * Planes stacked closer than a voxel, as leaves crowd a canopy.
     *
     * @return The corners.
     */
    private static float[] stack() {
        final float[] out = new float[STACK * 18];
        for (int i = 0; i < STACK; ++i) {
            System.arraycopy(plane(1.25f + i * STACK_SPACING), 0, out, i * 18, 18);
        }
        return out;
    }

    /**
     * The voxels' mean color's first value, over the look's.
     *
     * @param volume The volume.
     * @return The share of the look the voxels keep.
     */
    private static double keptColor(CoverageVolume volume) {
        double sum = 0;
        for (int v = 0; v < volume.size(); ++v) {
            sum += volume.color(v, 0);
        }
        return sum / volume.size() / LOOK[0];
    }

    /**
     * Leaves crowded in a voxel shade each other: stacked planes come out darker than their look,
     * and less so where their textures leave holes, while one plane alone keeps its look (see
     * {@link #testPlaneStopsLightAlongItsNormal}).
     */
    @Test
    void testCrowdedLeavesShadeEachOther() {
        final double full =
                keptColor(
                        CoverageVoxelizer.voxelize(
                                stack(), new int[STACK * 2], covering(1), VOXEL, SEED));
        final double holes =
                keptColor(
                        CoverageVoxelizer.voxelize(
                                stack(), new int[STACK * 2], covering(0.3f), VOXEL, SEED));
        assertTrue(full < 0.8, "Crowded leaves keep " + full + " of their color");
        assertTrue(holes > full, "Leaves with holes keep " + holes + ", solid ones " + full);
    }

    /** The same input and seed give the same volume. */
    @Test
    void testSameSeedSameVolume() {
        final CoverageVolume a =
                CoverageVoxelizer.voxelize(plane(1.25f), new int[2], covering(1), VOXEL, SEED);
        final CoverageVolume b =
                CoverageVoxelizer.voxelize(plane(1.25f), new int[2], covering(1), VOXEL, SEED);
        assertEquals(a.size(), b.size());
        for (int v = 0; v < a.size(); ++v) {
            assertEquals(a.keyOf(v), b.keyOf(v));
            assertEquals(a.density(v), b.density(v));
        }
    }
}
