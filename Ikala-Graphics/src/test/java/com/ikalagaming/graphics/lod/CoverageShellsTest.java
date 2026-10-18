package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/** Tests for coverage volumes' shells. */
class CoverageShellsTest {

    /** How wide the test voxels are. */
    private static final double VOXEL = 0.5;

    /** The seed the tests voxelize with. */
    private static final long SEED = 7;

    /**
     * A closed box's triangles, facing out.
     *
     * @param x Its lowest x.
     * @param y Its lowest y.
     * @param z Its lowest z.
     * @param side Its side.
     * @return The corners.
     */
    private static float[] box(float x, float y, float z, float side) {
        final float[][] c = new float[8][];
        for (int i = 0; i < 8; ++i) {
            c[i] =
                    new float[] {
                        x + (i & 1) * side, y + ((i >> 1) & 1) * side, z + ((i >> 2) & 1) * side
                    };
        }
        final int[] order = {
            0, 2, 3, 0, 3, 1, 4, 5, 7, 4, 7, 6, 0, 1, 5, 0, 5, 4, 2, 6, 7, 2, 7, 3, 0, 4, 6, 0, 6,
            2, 1, 3, 7, 1, 7, 5
        };
        final float[] out = new float[order.length * 3];
        for (int i = 0; i < order.length; ++i) {
            System.arraycopy(c[order[i]], 0, out, i * 3, 3);
        }
        return out;
    }

    /**
     * Two arrays end to end.
     *
     * @param a One.
     * @param b The other.
     * @return Both.
     */
    private static float[] join(float[] a, float[] b) {
        final float[] out = new float[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** A hull is closed, faces out, and holds every point; a cube's has twelve faces. */
    @Test
    void testHullHoldsEveryPoint() {
        final SplittableRandom random = new SplittableRandom(3);
        final long[][] points = new long[400][];
        for (int i = 0; i < points.length; ++i) {
            points[i] =
                    new long[] {
                        random.nextInt(0, 20), random.nextInt(0, 20), random.nextInt(0, 20)
                    };
        }
        final List<int[]> faces = CoverageShells.ConvexHull.of(points, 1);
        assertTrue(faces.size() >= 4);
        final Set<Long> edges = new HashSet<>();
        for (int[] f : faces) {
            for (int e = 0; e < 3; ++e) {
                assertTrue(edges.add((long) f[e] << 32 | f[(e + 1) % 3]), "An edge twice");
            }
            for (int p = 0; p < points.length; ++p) {
                assertTrue(
                        CoverageShells.ConvexHull.orient(points, f[0], f[1], f[2], p) <= 0,
                        "Point " + p + " outside a face");
            }
        }
        for (long edge : edges) {
            final long reverse = (edge & 0xFFFFFFFFL) << 32 | edge >>> 32;
            assertTrue(edges.contains(reverse), "The hull has a hole");
        }
        final long[][] cube = new long[8][];
        for (int i = 0; i < 8; ++i) {
            cube[i] = new long[] {i & 1, (i >> 1) & 1, (i >> 2) & 1};
        }
        final List<int[]> cubeFaces = CoverageShells.ConvexHull.of(cube, 1);
        assertEquals(12, cubeFaces.size());
        assertEquals(1, CoverageShells.ConvexHull.held(cube, cubeFaces), 1e-9);
    }

    /**
     * Groups of voxels apart get shells of their own, whose corners are flagged as shells' and
     * cover less where the volume stops less light.
     */
    @Test
    void testShellsCoverAsMuchAsTheirVolume() {
        final float[] two = join(box(0, 0, 0, 2), box(10, 0, 0, 2));
        final CoverageVolume volume =
                CoverageVoxelizer.voxelize(two, new int[two.length / 9], null, VOXEL, SEED);
        assertEquals(2, CoverageShells.components(volume).size());
        final CoverageShells.Shell shell = CoverageShells.of(volume, false);
        assertTrue(shell.triangles() >= 24, shell.triangles() + " triangles");
        double dense = 0;
        double most = 0;
        for (int at = 0; at < shell.attributes().length; at += Attributes.VALUES) {
            assertTrue(Attributes.isShell(shell.attributes(), at));
            assertTrue(Attributes.isAveraged(shell.attributes(), at));
            dense += coverage(shell.attributes(), at);
            most = Math.max(most, coverage(shell.attributes(), at));
        }
        dense /= shell.attributes().length / Attributes.VALUES;
        // The same boxes, with holes: a third of their surface covered
        final CoverageVolume sparse =
                CoverageVoxelizer.voxelize(
                        two,
                        new int[two.length / 9],
                        new MaterialAverages() {
                            @Override
                            public float[] average(int material) {
                                return null;
                            }

                            @Override
                            public float coverage(int material) {
                                return 1 / 3f;
                            }
                        },
                        VOXEL,
                        SEED);
        final CoverageShells.Shell thin = CoverageShells.of(sparse, false);
        double light = 0;
        for (int at = 0; at < thin.attributes().length; at += Attributes.VALUES) {
            light += coverage(thin.attributes(), at);
        }
        light /= thin.attributes().length / Attributes.VALUES;
        // Closed boxes' shells cover most of what is in front of them; corners out in a hull's
        // margin see only its share of its neighbors' voxels, so cover less
        assertTrue(most > 0.8, "Closed boxes' shells cover at most " + most);
        assertTrue(light < dense, "Boxes with holes cover " + light + " against " + dense);
    }

    /**
     * A model's shell names materials: each triangle the one its corners see, and each corner a
     * shade rather than a color.
     */
    @Test
    void testNamedShellsKeepTheirMaterials() {
        final float[] two = join(box(0, 0, 0, 2), box(10, 0, 0, 2));
        final int[] materials = new int[two.length / 9];
        for (int t = materials.length / 2; t < materials.length; ++t) {
            materials[t] = 7;
        }
        for (int t = 0; t < materials.length / 2; ++t) {
            materials[t] = 3;
        }
        final CoverageVolume volume = CoverageVoxelizer.voxelize(two, materials, null, VOXEL, SEED);
        final CoverageShells.Shell shell = CoverageShells.of(volume, true);
        assertTrue(shell.triangles() >= 24, shell.triangles() + " triangles");
        assertEquals(shell.triangles(), shell.materials().length);
        for (int t = 0; t < shell.triangles(); ++t) {
            final int expected = shell.corners()[t * 9] < 5 ? 3 : 7;
            assertEquals(expected, shell.materials()[t], "Triangle " + t + "'s material");
        }
        for (int at = 0; at < shell.attributes().length; at += Attributes.VALUES) {
            assertTrue(Attributes.isShell(shell.attributes(), at));
            assertTrue(Attributes.isNamedShell(shell.attributes(), at));
            assertTrue(Attributes.isAveraged(shell.attributes(), at));
            final double shade = (shell.attributes()[at + 1] & 0xFF) / 255.0;
            assertTrue(shade > 0, "A corner's shade is " + shade);
        }
        // A world's shell carries colors, its triangles no material
        final CoverageShells.Shell plain = CoverageShells.of(volume, false);
        for (int t = 0; t < plain.triangles(); ++t) {
            assertEquals(0, plain.materials()[t]);
        }
        assertTrue(!Attributes.isNamedShell(plain.attributes(), 0));
    }

    @Test
    void testBricksShareTheirMargins() {
        final int side = CoverageBricks.SIDE;
        final long low = CoverageVolume.key(0, 0, 0);
        final long high = CoverageVolume.key(1, 0, 0);
        final Set<Long> both = Set.of(low, high);
        for (int x = 0; x < 2 * side; ++x) {
            final long voxel = CoverageVolume.key(x, side / 2, side / 2);
            final double sum =
                    CoverageShells.shareOf(voxel, low, both, side)
                            + CoverageShells.shareOf(voxel, high, both, side);
            assertEquals(1, sum, 1e-9, "Shares at x = " + x);
        }
        // Next to the plane between them, each takes some of the other's voxels
        final long acrossPlane = CoverageVolume.key(side, side / 2, side / 2);
        final double taken = CoverageShells.shareOf(acrossPlane, low, both, side);
        assertTrue(taken > 0 && taken < 0.5, "The low brick takes " + taken);
        // Alone, a brick takes all of its voxels, even near its planes
        final long edge = CoverageVolume.key(side - 1, side / 2, side / 2);
        assertEquals(1, CoverageShells.shareOf(edge, low, Set.of(low), side), 1e-9);
    }

    /**
     * A shell corner's coverage, see Attributes.shell.
     *
     * @param attributes The corners.
     * @param at Where the corner starts.
     * @return Its coverage, 0 to 1.
     */
    private static double coverage(int[] attributes, int at) {
        return (attributes[at + 2] >>> 24) / 255.0;
    }
}
