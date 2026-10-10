package com.ikalagaming.graphics.bake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/** Packing the baked vertex. */
class BakedVertexTest {

    @Test
    void positionsOnTheGridAreExact() {
        assertEquals(16 * BakedVertex.STEPS_PER_UNIT, BakedVertex.position(16));
        assertEquals(-3 * BakedVertex.STEPS_PER_UNIT, BakedVertex.position(-3));
        assertEquals(BakedVertex.STEPS_PER_UNIT / 2, BakedVertex.position(0.5f));
        // Each section's grid lines are whole steps, so two neighbors agree exactly
        assertEquals(0, BakedVertex.position(16) - 16 * BakedVertex.STEPS_PER_UNIT);
        assertThrows(IllegalArgumentException.class, () -> BakedVertex.position(32));
        assertThrows(IllegalArgumentException.class, () -> BakedVertex.position(-33));
    }

    @Test
    void octahedralDirectionsComeBackClose() {
        Random random = new Random(3);
        float[] encoded = new float[2];
        float[] decoded = new float[3];
        for (int i = 0; i < 1000; ++i) {
            float x = random.nextFloat() * 2 - 1;
            float y = random.nextFloat() * 2 - 1;
            float z = random.nextFloat() * 2 - 1;
            float length = (float) Math.sqrt(x * x + y * y + z * z);
            x /= length;
            y /= length;
            z /= length;
            BakedVertex.octahedral(x, y, z, encoded);
            // Through the stored 16-bit values, as the shader sees them
            BakedVertex.fromOctahedral(
                    BakedVertex.unsnorm16(BakedVertex.snorm16(encoded[0])),
                    BakedVertex.unsnorm16(BakedVertex.snorm16(encoded[1])),
                    decoded);
            float dot = x * decoded[0] + y * decoded[1] + z * decoded[2];
            assertTrue(dot > 0.999_99f, "Direction " + x + ", " + y + ", " + z);
        }
        // The axes themselves, including straight down where the fold meets
        for (float[] axis : new float[][] {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {0, -1, 0}}) {
            BakedVertex.octahedral(axis[0], axis[1], axis[2], encoded);
            BakedVertex.fromOctahedral(encoded[0], encoded[1], decoded);
            assertEquals(axis[0], decoded[0], 1e-6);
            assertEquals(axis[1], decoded[1], 1e-6);
            assertEquals(axis[2], decoded[2], 1e-6);
        }
    }

    @Test
    void everyFieldLandsAtItsOffset() {
        ByteBuffer out = ByteBuffer.allocate(2 * BakedVertex.SIZE).order(ByteOrder.LITTLE_ENDIAN);
        BakedVertex.put(
                out,
                BakedVertex.SIZE,
                new float[] {1, 2.5f, -3},
                0,
                (short) 0x1234,
                new float[] {0, 1, 0},
                new float[] {1, 0, 0},
                0,
                true,
                new float[] {0.25f, 3},
                0,
                513);
        final int at = BakedVertex.SIZE;
        assertEquals(BakedVertex.STEPS_PER_UNIT, out.getShort(at));
        assertEquals((short) (2.5f * BakedVertex.STEPS_PER_UNIT), out.getShort(at + 2));
        assertEquals(-3 * BakedVertex.STEPS_PER_UNIT, out.getShort(at + 4));
        assertEquals(0x1234, out.getShort(at + BakedVertex.USER_OFFSET));
        // Up is (0, 1) in octahedral coordinates
        assertEquals(0, out.getShort(at + BakedVertex.NORMAL_OFFSET));
        assertEquals(Short.MAX_VALUE, out.getShort(at + BakedVertex.NORMAL_OFFSET + 2));
        assertEquals(Short.MAX_VALUE, out.getShort(at + BakedVertex.TANGENT_OFFSET));
        assertEquals(0.25f, Float.float16ToFloat(out.getShort(at + BakedVertex.UV_OFFSET)));
        assertEquals(3f, Float.float16ToFloat(out.getShort(at + BakedVertex.UV_OFFSET + 2)));
        assertEquals(513, out.getShort(at + BakedVertex.MATERIAL_OFFSET));
        assertEquals(
                BakedVertex.FLAG_FLIPPED_BITANGENT, out.getShort(at + BakedVertex.FLAGS_OFFSET));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BakedVertex.put(
                                out,
                                0,
                                new float[3],
                                0,
                                (short) 0,
                                new float[] {0, 1, 0},
                                new float[] {1, 0, 0},
                                0,
                                false,
                                new float[2],
                                0,
                                BakedVertex.MAX_MATERIAL + 1));
    }
}
