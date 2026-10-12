package com.ikalagaming.graphics.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Which voxels triangles mark in the occupancy volume. */
class VoxelizeMathTest {

    private static Set<String> marked(Vector3f a, Vector3f b, Vector3f c, Vector3f normal) {
        final Set<String> out = new HashSet<>();
        for (int[] v : VoxelizeMath.voxels(a, b, c, normal)) {
            out.add(v[0] + "," + v[1] + "," + v[2]);
        }
        return out;
    }

    @Test
    void aBlockTopMarksOnlyTheVoxelBelowIt() {
        // The top face of the block at (3, 4, 5), facing up
        final Vector3f up = new Vector3f(0, 1, 0);
        final Vector3f a = new Vector3f(3, 5, 5);
        final Vector3f b = new Vector3f(3, 5, 6);
        final Vector3f c = new Vector3f(4, 5, 6);
        final Vector3f d = new Vector3f(4, 5, 5);
        final Set<String> voxels = marked(a, b, c, up);
        voxels.addAll(marked(a, c, d, up));
        assertEquals(Set.of("3,4,5"), voxels);
        // The same face of the block above, its bottom, marks that block instead
        final Set<String> above = marked(a, b, c, new Vector3f(0, -1, 0));
        above.addAll(marked(a, c, d, new Vector3f(0, -1, 0)));
        assertEquals(Set.of("3,5,5"), above);
    }

    @Test
    void aBlockSideMarksOnlyTheBlock() {
        // The +x face of the block at (0, 0, 0), facing +x
        final Vector3f a = new Vector3f(1, 0, 0);
        final Vector3f b = new Vector3f(1, 1, 0);
        final Vector3f c = new Vector3f(1, 1, 1);
        final Vector3f d = new Vector3f(1, 0, 1);
        final Vector3f east = new Vector3f(1, 0, 0);
        final Set<String> voxels = marked(a, b, c, east);
        voxels.addAll(marked(a, c, d, east));
        assertEquals(Set.of("0,0,0"), voxels);
    }

    @Test
    void aSlopeMarksTheVoxelsItCrosses() {
        // A ramp rising 2 over 4 along x, 1 wide, facing up and toward -x
        final Vector3f a = new Vector3f(0, 0, 0);
        final Vector3f b = new Vector3f(0, 0, 1);
        final Vector3f c = new Vector3f(4, 2, 1);
        final Vector3f d = new Vector3f(4, 2, 0);
        final Vector3f normal = new Vector3f(-2, 4, 0);
        final Set<String> voxels = marked(a, b, c, normal);
        voxels.addAll(marked(a, c, d, normal));
        // Every column along the ramp has a marked voxel, at about the ramp's height
        for (int x = 0; x < 4; ++x) {
            boolean found = false;
            for (int y = 0; y < 2 && !found; ++y) {
                found = voxels.contains(x + "," + y + ",0");
            }
            assertTrue(found, "Column " + x);
        }
        for (String voxel : voxels) {
            final String[] parts = voxel.split(",");
            final int x = Integer.parseInt(parts[0]);
            final int y = Integer.parseInt(parts[1]);
            // The ramp at x is at height x / 2: nothing far above or below it
            assertTrue(y >= Math.floor(x / 2.0) - 1 && y <= Math.ceil((x + 1) / 2.0), voxel);
        }
    }

    @Test
    void aHugeTriangleStopsAtTheCap() {
        final List<int[]> voxels =
                VoxelizeMath.voxels(
                        new Vector3f(0, 0.5f, 0),
                        new Vector3f(0, 0.5f, 100),
                        new Vector3f(100, 0.5f, 0),
                        new Vector3f(0, 1, 0));
        assertTrue(voxels.size() <= VoxelizeMath.MAX_VOXELS);
    }
}
