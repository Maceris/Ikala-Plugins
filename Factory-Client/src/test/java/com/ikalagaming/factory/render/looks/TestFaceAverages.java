package com.ikalagaming.factory.render.looks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.graph.MeshData;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

/** Tests for averaging a texture over a model's faces by direction. */
class TestFaceAverages {

    /**
     * Write a unit square's four corners into vertex data.
     *
     * @param out The vertex data.
     * @param first The first vertex.
     * @param up Whether it faces up, else along +z.
     * @param u0 Its texture's left edge.
     * @param u1 Its texture's right edge.
     */
    private static void square(float[] out, int first, boolean up, float u0, float u1) {
        final float[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int i = 0; i < 4; ++i) {
            final int at = (first + i) * MeshData.VERTEX_SIZE_IN_FLOATS;
            final float a = corners[i][0];
            final float b = corners[i][1];
            if (up) {
                out[at] = a;
                out[at + 1] = 1;
                out[at + 2] = b;
                out[at + 4] = 1;
            } else {
                out[at] = a;
                out[at + 1] = b;
                out[at + 2] = 1;
                out[at + 5] = 1;
            }
            out[at + 12] = a == 0 ? u0 : u1;
            out[at + 13] = b;
        }
    }

    /** A top showing the middle of a strip and a side showing its left take their own colors. */
    @Test
    void testStrip() {
        final float[] vertices = new float[8 * MeshData.VERTEX_SIZE_IN_FLOATS];
        square(vertices, 0, true, 1 / 3f, 2 / 3f);
        square(vertices, 4, false, 0, 1 / 3f);
        final int[] indices = {0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7};
        final MeshData mesh =
                new MeshData(new Vector3f(), new Vector3f(1), 8, vertices, indices, 0, new byte[0]);
        final BufferedImage strip = new BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB);
        strip.setRGB(0, 0, 0xFFFF0000);
        strip.setRGB(1, 0, 0xFF00FF00);
        strip.setRGB(2, 0, 0xFF0000FF);
        final FaceAverages averages = FaceAverages.of(mesh, strip);
        assertTrue(FaceAverages.same(new Vector3f(0, 1, 0), averages.up()), "" + averages.up());
        assertTrue(FaceAverages.same(new Vector3f(1, 0, 0), averages.side()), "" + averages.side());
        assertNull(averages.down());
        assertFalse(FaceAverages.same(averages.up(), averages.side()));
    }

    /** Holes count for nothing, and sRGB texels average in linear light. */
    @Test
    void testHolesAndLinear() {
        final float[] vertices = new float[4 * MeshData.VERTEX_SIZE_IN_FLOATS];
        square(vertices, 0, true, 0, 1);
        final MeshData mesh =
                new MeshData(
                        new Vector3f(),
                        new Vector3f(1),
                        4,
                        vertices,
                        new int[] {0, 1, 2, 0, 2, 3},
                        0,
                        new byte[0]);
        final BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF808080);
        image.setRGB(1, 0, 0x00FFFFFF);
        final Vector3f up = FaceAverages.of(mesh, image).up();
        // sRGB 128 is about 0.216 linear; the see-through white half doesn't count
        assertEquals(0.216f, up.x, 0.002f);
        assertEquals(up.x, up.z, 1e-6f);
    }
}
