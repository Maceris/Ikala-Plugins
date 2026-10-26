package com.ikalagaming.graphics.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Downsampling sections' volume bricks into coarse ones, and where each level draws. */
class CoarseBricksTest {

    /** Voxels in a brick. */
    private static final int VOXELS = VolumeMath.BRICK * VolumeMath.BRICK * VolumeMath.BRICK;

    /** A material index plus 1, as solid voxels store it. */
    private static final short LEAVES = 7;

    /** Another. */
    private static final short GLASS = 9;

    /**
     * A voxel's index, x fastest.
     *
     * @param x Its x.
     * @param y Its y.
     * @param z Its z.
     * @return The index.
     */
    private static int index(int x, int y, int z) {
        return x + VolumeMath.BRICK * (y + VolumeMath.BRICK * z);
    }

    /** A brick being filled in. */
    private static final class Builder {
        final byte[] texels = new byte[VOXELS * 4];
        final short[] materials = new short[VOXELS];
        final int[] surfaces = new int[VolumeVoxelizer.SURFACE_INTS];

        /** Make a voxel solid, red, with full coverage, of a material, with cut out surfaces. */
        Builder solid(int x, int y, int z, short material) {
            final int voxel = index(x, y, z);
            texels[voxel * 4] = (byte) 255;
            texels[voxel * 4 + 3] = (byte) VolumeMath.encodeSolid(1);
            materials[voxel] = material;
            surfaces[voxel / VolumeVoxelizer.SURFACES_PER_INT] |=
                    VolumeVoxelizer.CUTOUT_SURFACE
                            << (voxel
                                    % VolumeVoxelizer.SURFACES_PER_INT
                                    * VolumeVoxelizer.SURFACE_BITS);
            return this;
        }

        /** Give a voxel a see-through extinction, blue. */
        Builder seeThrough(int x, int y, int z, double extinction) {
            final int voxel = index(x, y, z);
            texels[voxel * 4 + 2] = (byte) 255;
            texels[voxel * 4 + 3] = (byte) VolumeMath.encode(extinction);
            return this;
        }

        VolumeVoxelizer.Brick build() {
            return new VolumeVoxelizer.Brick(texels, new int[4], materials, surfaces, false);
        }
    }

    /**
     * A coarse voxel's stored alpha.
     *
     * @param brick The brick.
     * @param x Its x.
     * @param y Its y.
     * @param z Its z.
     * @return The stored value, 0 to 255.
     */
    private static int stored(VolumeVoxelizer.Brick brick, int x, int y, int z) {
        return Byte.toUnsignedInt(brick.texels()[index(x, y, z) * 4 + 3]);
    }

    /**
     * A one voxel thick solid floor stays solid at level 1, where it fills half of each coarse
     * voxel, and at level 2, a quarter of each, becomes as much extinction as a ray down through it
     * met before: the light it stops is kept.
     */
    @Test
    void testFloorKeepsTheLightItStops() {
        final Builder floor = new Builder();
        for (int z = 0; z < VolumeMath.BRICK; ++z) {
            for (int x = 0; x < VolumeMath.BRICK; ++x) {
                floor.solid(x, 0, z, LEAVES);
            }
        }
        final VolumeVoxelizer.Brick fine = floor.build();
        final VolumeVoxelizer.Brick[] eight = new VolumeVoxelizer.Brick[8];
        eight[0] = fine;
        final VolumeVoxelizer.Brick one = CoarseBricks.downsample(1, eight);
        assertNotNull(one);
        assertTrue(VolumeMath.isSolid(stored(one, 0, 0, 0)));
        assertEquals(LEAVES, one.materials()[index(3, 0, 3)]);
        assertFalse(VolumeMath.isSolid(stored(one, 0, 1, 0)), "Above the floor stays empty");
        assertEquals(0, stored(one, 0, 1, 0));
        // Only the first child's corner of the coarse brick is filled
        assertEquals(0, stored(one, 8, 0, 0));

        final VolumeVoxelizer.Brick[] sixtyFour = new VolumeVoxelizer.Brick[64];
        sixtyFour[0] = fine;
        final VolumeVoxelizer.Brick two = CoarseBricks.downsample(2, sixtyFour);
        assertNotNull(two);
        final int bottom = stored(two, 0, 0, 0);
        assertFalse(VolumeMath.isSolid(bottom));
        // Down through 4 m at the average, as through the 1 m solid floor
        final double through = VolumeMath.decode(bottom) * CoarseBricks.span(2);
        assertEquals(VolumeMath.MAX_EXTINCTION, through, VolumeMath.MAX_EXTINCTION * 0.05);
        // The cell masks mark it as holding something, not something solid
        assertEquals(0, two.cellMasks()[0] & 1);
        assertEquals(1, two.cellMasks()[2] & 1);
    }

    /**
     * A see-through voxel's extinction is averaged over the coarse voxel, and its color is kept;
     * surfaces are kept wherever any fine voxel had them.
     */
    @Test
    void testSeeThroughAveragesAndSurfacesAreKept() {
        final Builder builder = new Builder();
        builder.seeThrough(0, 0, 0, 8);
        builder.solid(15, 15, 15, GLASS);
        final VolumeVoxelizer.Brick[] eight = new VolumeVoxelizer.Brick[8];
        // In the child at (1, 1, 1): the coarse brick's far corner
        eight[7] = builder.build();
        final VolumeVoxelizer.Brick coarse = CoarseBricks.downsample(1, eight);
        assertNotNull(coarse);
        final int near = stored(coarse, 8, 8, 8);
        assertEquals(8.0 / 8, VolumeMath.decode(near), 0.1);
        assertEquals(255, Byte.toUnsignedInt(coarse.texels()[index(8, 8, 8) * 4 + 2]));
        final int far = index(15, 15, 15);
        assertEquals(
                VolumeVoxelizer.CUTOUT_SURFACE,
                coarse.surfaces()[far / VolumeVoxelizer.SURFACES_PER_INT]
                                >>> (far
                                        % VolumeVoxelizer.SURFACES_PER_INT
                                        * VolumeVoxelizer.SURFACE_BITS)
                        & 3);
        // One solid voxel of eight isn't enough to make the coarse one solid
        assertFalse(VolumeMath.isSolid(stored(coarse, 15, 15, 15)));
    }

    /** A solid coarse voxel takes the most common material of its solid fine voxels. */
    @Test
    void testSolidTakesTheMostCommonMaterial() {
        final Builder builder = new Builder();
        builder.solid(0, 0, 0, GLASS);
        builder.solid(1, 0, 0, LEAVES);
        builder.solid(0, 1, 0, LEAVES);
        builder.solid(1, 1, 0, LEAVES);
        final VolumeVoxelizer.Brick[] eight = new VolumeVoxelizer.Brick[8];
        eight[0] = builder.build();
        final VolumeVoxelizer.Brick coarse = CoarseBricks.downsample(1, eight);
        assertNotNull(coarse);
        assertTrue(VolumeMath.isSolid(stored(coarse, 0, 0, 0)));
        assertEquals(LEAVES, coarse.materials()[0]);
    }

    /** Sections with nothing make no coarse brick. */
    @Test
    void testNothingGivesNoBrick() {
        assertNull(CoarseBricks.downsample(1, new VolumeVoxelizer.Brick[8]));
        final VolumeVoxelizer.Brick[] eight = new VolumeVoxelizer.Brick[8];
        eight[3] = new Builder().build();
        assertNull(CoarseBricks.downsample(1, eight));
    }

    /**
     * Each level draws where its voxels project to the threshold, never inside the minimum, and
     * never before a finer level; the margin keeps a level drawn until a little nearer.
     */
    @Test
    void testDistances() {
        final double[] d = CoarseBricks.distances(1000, 4, 300);
        assertEquals(0, d[0]);
        assertEquals(500, d[1], 1e-9);
        assertEquals(1000, d[2], 1e-9);
        final double[] near = CoarseBricks.distances(1000, 4, 700);
        assertEquals(700, near[1], 1e-9);
        assertEquals(1000, near[2], 1e-9);
        assertTrue(CoarseBricks.reaches(510, 500, false, 8));
        assertFalse(CoarseBricks.reaches(505, 500, false, 8));
        assertTrue(CoarseBricks.reaches(495, 500, true, 8));
        assertFalse(CoarseBricks.reaches(490, 500, true, 8));
    }
}
