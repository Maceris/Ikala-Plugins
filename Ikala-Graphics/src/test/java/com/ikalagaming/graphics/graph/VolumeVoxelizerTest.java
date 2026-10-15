package com.ikalagaming.graphics.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.Face;
import com.ikalagaming.graphics.bake.SectionBaker;
import com.ikalagaming.graphics.benchmark.BlockMeshes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tests for {@link VolumeVoxelizer}. */
class VolumeVoxelizerTest {

    /** One material: a leafy green, partly cut out. */
    private static final VolumeMaterials TABLE =
            new VolumeMaterials(new float[] {0.2f, 0.5f, 0.1f, 0.6f}, 1);

    /** A one meter box, as every test block. */
    private static final BakeSource BOX =
            BakeSource.derive(
                    BlockMeshes.box(new float[] {0, 0, 0}, new float[] {1, 1, 1}, null),
                    BlockMeshes.BOX_MIN,
                    BlockMeshes.BOX_MAX);

    /**
     * Bake blocks at cells, each dropping the faces its neighbors among them hide.
     *
     * @param cells The cells, {x, y, z} each.
     * @return The baked mesh.
     */
    private static SectionBaker.Bucket bake(List<int[]> cells) {
        final Set<Long> filled = new HashSet<>();
        for (int[] cell : cells) {
            filled.add(key(cell[0], cell[1], cell[2]));
        }
        final int count = cells.size();
        final BakeSource[] sources = new BakeSource[count];
        final int[] positions = new int[count];
        final byte[] masks = new byte[count];
        for (int i = 0; i < count; ++i) {
            final int[] cell = cells.get(i);
            sources[i] = BOX;
            positions[i] = SectionBaker.pack(cell[0], cell[1], cell[2]);
            int mask = 0;
            for (Face face : Face.ALL) {
                final int[] next = cell.clone();
                next[face.getAxis()] += face.getSign();
                if (filled.contains(key(next[0], next[1], next[2]))) {
                    mask |= face.bit();
                }
            }
            masks[i] = (byte) mask;
        }
        final SectionBaker.Result result =
                SectionBaker.bake(
                        sources,
                        new int[count],
                        positions,
                        new byte[count],
                        masks,
                        new short[count]);
        return result.get(Material.Transparency.OPAQUE);
    }

    /**
     * A cell's key in a set.
     *
     * @param x Its x.
     * @param y Its y.
     * @param z Its z.
     * @return The key.
     */
    private static long key(int x, int y, int z) {
        return ((long) x << 40) ^ ((long) y << 20) ^ z;
    }

    /**
     * The stored alpha byte of a voxel.
     *
     * @param brick The brick.
     * @param x Its x.
     * @param y Its y.
     * @param z Its z.
     * @return The stored value, 0 to 255.
     */
    private static int stored(VolumeVoxelizer.Brick brick, int x, int y, int z) {
        final int voxel = x + VolumeMath.BRICK * (y + VolumeMath.BRICK * z);
        return Byte.toUnsignedInt(brick.texels()[voxel * 4 + 3]);
    }

    /**
     * A voxel's material, plus 1.
     *
     * @param brick The brick.
     * @param x Its x.
     * @param y Its y.
     * @param z Its z.
     * @return The material plus 1, or 0.
     */
    private static int material(VolumeVoxelizer.Brick brick, int x, int y, int z) {
        return brick.materials()[x + VolumeMath.BRICK * (y + VolumeMath.BRICK * z)];
    }

    /**
     * A voxel's surface bits.
     *
     * @param brick The brick.
     * @param x Its x.
     * @param y Its y.
     * @param z Its z.
     * @return The bits.
     */
    private static int surfaces(VolumeVoxelizer.Brick brick, int x, int y, int z) {
        final int voxel = x + VolumeMath.BRICK * (y + VolumeMath.BRICK * z);
        return brick.surfaces()[voxel / VolumeVoxelizer.SURFACES_PER_INT]
                        >>> (voxel
                                % VolumeVoxelizer.SURFACES_PER_INT
                                * VolumeVoxelizer.SURFACE_BITS)
                & ((1 << VolumeVoxelizer.SURFACE_BITS) - 1);
    }

    /** A lone block is one solid voxel, with its faces' coverage and material, and no more. */
    @Test
    void testLoneBlock() {
        final List<int[]> cells = new ArrayList<>();
        cells.add(new int[] {3, 5, 7});
        final VolumeVoxelizer.Brick brick =
                VolumeVoxelizer.voxelize(
                        List.of(new VolumeVoxelizer.Input(bake(cells), false)), TABLE);
        assertNotNull(brick);
        assertFalse(brick.missingMaterial());
        assertTrue(VolumeMath.isSolid(stored(brick, 3, 5, 7)));
        assertEquals(0.6, VolumeMath.coverage(stored(brick, 3, 5, 7)), 0.01);
        assertEquals(1, material(brick, 3, 5, 7));
        // A face on the block's positive side stays in the block, not the voxel past it
        assertEquals(0, stored(brick, 4, 5, 7));
        assertEquals(0, stored(brick, 3, 6, 7));
        assertEquals(0, stored(brick, 3, 5, 8));
        assertEquals(0, stored(brick, 2, 5, 7));
        // Its surfaces mark its own voxel only
        assertEquals(VolumeVoxelizer.CUTOUT_SURFACE, surfaces(brick, 3, 5, 7));
        assertEquals(0, surfaces(brick, 4, 5, 7));
        assertEquals(0, surfaces(brick, 3, 6, 7));
        assertEquals(0, surfaces(brick, 2, 5, 7));
    }

    /** A block of blocks, which hide their inner faces, is filled solid inside. */
    @Test
    void testCanopyFilled() {
        final List<int[]> cells = new ArrayList<>();
        for (int x = 2; x <= 4; ++x) {
            for (int y = 6; y <= 8; ++y) {
                for (int z = 2; z <= 4; ++z) {
                    cells.add(new int[] {x, y, z});
                }
            }
        }
        final VolumeVoxelizer.Brick brick =
                VolumeVoxelizer.voxelize(
                        List.of(new VolumeVoxelizer.Input(bake(cells), false)), TABLE);
        assertNotNull(brick);
        for (int[] cell : cells) {
            assertTrue(
                    VolumeMath.isSolid(stored(brick, cell[0], cell[1], cell[2])),
                    "cell " + cell[0] + ", " + cell[1] + ", " + cell[2]);
            assertEquals(1, material(brick, cell[0], cell[1], cell[2]));
        }
        // Filled for drawing, but only the surfaces are marked, as the meshes would mark them
        assertEquals(0, surfaces(brick, 3, 7, 3));
        assertEquals(VolumeVoxelizer.CUTOUT_SURFACE, surfaces(brick, 3, 8, 3));
        // Nothing above or below it
        assertEquals(0, stored(brick, 3, 9, 3));
        assertEquals(0, stored(brick, 3, 5, 3));
    }

    /**
     * Translucent blocks, like water, fill nothing: a pool whose floor is hidden by ground doesn't
     * reach down into the ground.
     */
    @Test
    void testWaterNotFilled() {
        final List<int[]> cells = new ArrayList<>();
        for (int x = 2; x <= 4; ++x) {
            for (int z = 2; z <= 4; ++z) {
                cells.add(new int[] {x, 8, z});
            }
        }
        // The floor is hidden, as by ground under it
        final SectionBaker.Bucket pool = bake(cells);
        final VolumeVoxelizer.Brick brick =
                VolumeVoxelizer.voxelize(List.of(new VolumeVoxelizer.Input(pool, true)), TABLE);
        assertNotNull(brick);
        assertFalse(VolumeMath.isSolid(stored(brick, 3, 8, 3)));
        assertTrue(stored(brick, 3, 8, 3) > 0);
        for (int y = 0; y < 8; ++y) {
            assertEquals(0, stored(brick, 3, y, 3), "y " + y);
        }
        assertEquals(0, material(brick, 3, 8, 3));
        assertEquals(VolumeVoxelizer.TRANSLUCENT_SURFACE, surfaces(brick, 3, 8, 3));
        assertEquals(0, surfaces(brick, 3, 7, 3));
    }

    /** Nothing to stop light gives no brick, and a missing material is noted. */
    @Test
    void testEmptyAndMissing() {
        assertNull(VolumeVoxelizer.voxelize(List.of(), TABLE));
        final List<int[]> cells = new ArrayList<>();
        cells.add(new int[] {1, 1, 1});
        final VolumeVoxelizer.Brick brick =
                VolumeVoxelizer.voxelize(
                        List.of(new VolumeVoxelizer.Input(bake(cells), false)),
                        VolumeMaterials.NONE);
        assertNotNull(brick);
        assertTrue(brick.missingMaterial());
        // Where it is still counts, though its color isn't known yet
        assertEquals(VolumeVoxelizer.CUTOUT_SURFACE, surfaces(brick, 1, 1, 1));
    }
}
