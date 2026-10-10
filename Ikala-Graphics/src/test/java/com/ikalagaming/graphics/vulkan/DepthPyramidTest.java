package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Random;

/** The depth pyramid the occlusion test reads, as the shader builds it, mirrored in Java. */
class DepthPyramidTest {

    private static float[] randomDepth(Random random, int width, int height) {
        float[] depth = new float[width * height];
        for (int i = 0; i < depth.length; ++i) {
            depth[i] = random.nextFloat();
        }
        return depth;
    }

    @Test
    void levelsHalveDownToOneTexel() {
        // 67 × 45: 33 × 22, 16 × 11, 8 × 5, 4 × 2, 2 × 1, 1 × 1
        assertEquals(6, DepthPyramidMath.levelCount(67, 45));
        int[] widths = new int[6];
        int[] heights = new int[6];
        for (int level = 0; level < 6; ++level) {
            widths[level] = DepthPyramidMath.levelSize(67, level);
            heights[level] = DepthPyramidMath.levelSize(45, level);
        }
        assertArrayEquals(new int[] {33, 16, 8, 4, 2, 1}, widths);
        assertArrayEquals(new int[] {22, 11, 5, 2, 1, 1}, heights);
        assertEquals(1, DepthPyramidMath.levelCount(1, 1), "A single pixel still gets a level");
        assertEquals(1, DepthPyramidMath.levelCount(2, 3));
    }

    @Test
    void theLastTexelOfAnOddRowTakesInTheOneLeftOver() {
        DepthPyramidMath.Pyramid pyramid =
                DepthPyramidMath.build(new float[] {0.1f, 0.2f, 0.9f}, 3, 1);
        assertEquals(1, pyramid.levels().length);
        assertEquals(0.9f, pyramid.levels()[0][0]);

        // 5 wide: texel 0 covers pixels 0 and 1, texel 1 covers 2, 3 and 4
        pyramid = DepthPyramidMath.build(new float[] {0.1f, 0.2f, 0.3f, 0.4f, 0.8f}, 5, 1);
        assertArrayEquals(new float[] {0.2f, 0.8f}, pyramid.levels()[0]);
        assertArrayEquals(new float[] {0.8f}, pyramid.levels()[1]);
    }

    @Test
    void everyTexelIsAtLeastAsFarAsEveryPixelUnderIt() {
        Random random = new Random(4);
        for (int[] size : new int[][] {{1, 1}, {1, 9}, {7, 1}, {37, 23}, {64, 32}, {65, 33}}) {
            final int width = size[0];
            final int height = size[1];
            float[] depth = randomDepth(random, width, height);
            DepthPyramidMath.Pyramid pyramid = DepthPyramidMath.build(depth, width, height);
            for (int level = 0; level < pyramid.levels().length; ++level) {
                final int levelWidth = DepthPyramidMath.levelSize(width, level);
                for (int y = 0; y < height; ++y) {
                    for (int x = 0; x < width; ++x) {
                        final int texelX = DepthPyramidMath.texel(x, width, level);
                        final int texelY = DepthPyramidMath.texel(y, height, level);
                        assertTrue(
                                pyramid.levels()[level][texelY * levelWidth + texelX]
                                        >= depth[y * width + x],
                                "Pixel " + x + ", " + y + " at level " + level);
                    }
                }
            }
        }
    }

    @Test
    void aRectangleNeverLooksNearerThanItIs() {
        Random random = new Random(11);
        final int width = 61;
        final int height = 37;
        float[] depth = randomDepth(random, width, height);
        DepthPyramidMath.Pyramid pyramid = DepthPyramidMath.build(depth, width, height);
        final int levels = pyramid.levels().length;
        for (int i = 0; i < 2000; ++i) {
            int x0 = random.nextInt(width);
            int x1 = x0 + random.nextInt(width - x0);
            int y0 = random.nextInt(height);
            int y1 = y0 + random.nextInt(height - y0);
            float actual = 0;
            for (int y = y0; y <= y1; ++y) {
                for (int x = x0; x <= x1; ++x) {
                    actual = Math.max(actual, depth[y * width + x]);
                }
            }
            assertTrue(DepthPyramidMath.farthest(pyramid, x0, y0, x1, y1) >= actual);

            // The level picked has the rectangle under at most 2 × 2 texels
            int level = DepthPyramidMath.pickLevel(x0, y0, x1, y1, width, height, levels);
            assertTrue(
                    DepthPyramidMath.texel(x1, width, level)
                                    - DepthPyramidMath.texel(x0, width, level)
                            <= 1);
            assertTrue(
                    DepthPyramidMath.texel(y1, height, level)
                                    - DepthPyramidMath.texel(y0, height, level)
                            <= 1);
        }
    }

    @Test
    void smallRectanglesUseFineLevels() {
        // A 2 × 2 pixel square lined up with level 0's texels needs nothing coarser
        assertEquals(0, DepthPyramidMath.pickLevel(4, 4, 5, 5, 64, 64, 6));
        // The whole screen needs the top levels
        assertEquals(4, DepthPyramidMath.pickLevel(0, 0, 63, 63, 64, 64, 6));
    }
}
