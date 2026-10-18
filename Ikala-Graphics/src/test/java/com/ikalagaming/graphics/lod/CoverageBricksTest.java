package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tests for laying coverage volumes out in bricks. */
class CoverageBricksTest {

    /** How wide the test voxels are. */
    private static final double VOXEL = 0.5;

    /** The seed the test voxelizes with. */
    private static final long SEED = 99;

    /** Words of a brick before its voxels. */
    private static final int HEAD = CoverageBricks.HEAD_WORDS;

    /** Bits in a uint of the mask. */
    private static final int MASK_BITS = Integer.SIZE;

    /**
     * Scattered small cards, so the volume's voxels spread thinly over its bricks.
     *
     * @return The corners.
     */
    private static float[] cards() {
        final int cards = 40;
        final float[] out = new float[cards * 9];
        for (int i = 0; i < cards; ++i) {
            final float x = (i * 7 % 13) * 0.9f;
            final float y = (i * 5 % 11) * 0.8f;
            final float z = (i * 3 % 17) * 0.7f;
            final float[] card = {x, y, z, x + 0.3f, y, z, x, y + 0.3f, z + 0.1f};
            System.arraycopy(card, 0, out, i * 9, 9);
        }
        return out;
    }

    /**
     * A voxel as the shaders look it up, see coverage_bricks.glsl's brickVoxel.
     *
     * @param brick The brick's words.
     * @param place The voxel's place in the brick.
     * @return Its four uints, all zero where it holds nothing.
     */
    private static int[] lookUp(ByteBuffer brick, int place) {
        final int bit = place % MASK_BITS;
        final int word = place / MASK_BITS;
        final int quarter = word / 4;
        final int maskAt = (1 + quarter) * CoverageBricks.WORD_BYTES;
        final int bits = brick.getInt(maskAt + (word % 4) * Integer.BYTES);
        if (((bits >>> bit) & 1) == 0) {
            return new int[4];
        }
        int rank = 0;
        if (quarter == 1) {
            rank = brick.getInt(Integer.BYTES) & 0xFFFF;
        } else if (quarter == 2) {
            rank = brick.getInt(Integer.BYTES) >>> Short.SIZE;
        } else if (quarter == 3) {
            rank = brick.getInt(2 * Integer.BYTES);
        }
        for (int i = 0; i < word % 4; ++i) {
            rank += Integer.bitCount(brick.getInt(maskAt + i * Integer.BYTES));
        }
        rank += Integer.bitCount(bits & ((1 << bit) - 1));
        final int[] out = new int[4];
        for (int k = 0; k < 4; ++k) {
            out[k] = brick.getInt((HEAD + rank) * CoverageBricks.WORD_BYTES + k * Integer.BYTES);
        }
        return out;
    }

    /**
     * Bricks keep only the voxels that hold something, and finding a voxel by its place through the
     * mask, as the shaders do, gives its data, or nothing where it holds nothing.
     */
    @Test
    void testSparseBricksFindEveryVoxel() {
        final float[] triangles = cards();
        final CoverageVolume volume =
                CoverageVoxelizer.voxelize(
                        triangles, new int[triangles.length / 9], null, VOXEL, SEED);
        assertTrue(volume.size() > 0);
        final List<CoverageBricks.Brick> bricks = CoverageBricks.of(volume);
        // Each voxel's brick and place
        final Map<Long, Integer> voxelAt = new HashMap<>();
        for (int v = 0; v < volume.size(); ++v) {
            voxelAt.put(volume.keyOf(v), v);
        }
        int found = 0;
        int words = 0;
        for (CoverageBricks.Brick brick : bricks) {
            words += brick.words();
            int held = 0;
            for (int place = 0; place < CoverageBricks.VOXELS; ++place) {
                final int x = brick.x() + place % CoverageBricks.SIDE;
                final int y = brick.y() + place / CoverageBricks.SIDE % CoverageBricks.SIDE;
                final int z = brick.z() + place / (CoverageBricks.SIDE * CoverageBricks.SIDE);
                final Integer v = voxelAt.get(CoverageVolume.key(x, y, z));
                final int[] data = lookUp(brick.data(), place);
                if (v == null) {
                    assertEquals(0, data[0] | data[1] | data[2] | data[3], "Empty " + place);
                    continue;
                }
                ++held;
                ++found;
                // Its density, as a half float, is in the low bits of its first uint
                assertTrue((data[0] & 0xFFFF) != 0, "Voxel " + v + " lost its density");
            }
            assertEquals(HEAD + held, brick.words(), "A brick keeps only what it holds");
        }
        assertEquals(volume.size(), found, "Every voxel is in a brick");
        assertTrue(
                words < bricks.size() * (HEAD + CoverageBricks.VOXELS) / 4,
                words + " words for " + bricks.size() + " bricks");
    }
}
