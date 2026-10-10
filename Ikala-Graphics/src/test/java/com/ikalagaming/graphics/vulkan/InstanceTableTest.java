package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/** The GPU instance table's entry layout and the split positions it stores. */
class InstanceTableTest {

    /**
     * What the instance transform shader computes for one coordinate, in float arithmetic: the
     * difference of the high parts plus the difference of the low parts.
     *
     * @param instance The instance's world coordinate.
     * @param camera The camera's world coordinate.
     * @return The coordinate relative to the camera, as the shader gets it.
     */
    private static float relative(double instance, double camera) {
        float high = InstanceTable.high(instance) - InstanceTable.high(camera);
        float low = InstanceTable.low(instance) - InstanceTable.low(camera);
        return high + low;
    }

    @Test
    void positionsStayExactNearTheCameraFarFromTheOrigin() {
        // A float can only step by 64 blocks out here, so the high parts alone are useless
        final double far = 1e9;
        assertEquals(0.37, relative(far + 0.37, far), 1e-4);
        assertEquals(-12.25, relative(far - 12.25, far), 1e-4);
        assertEquals(0.37, relative(-far + 0.37, -far), 1e-4);
        // A camera between whole floats, as a moving one usually is
        assertEquals(1.5, relative(far + 31.75, far + 30.25), 1e-4);
    }

    @Test
    void farApartPositionsKeepTheirRelativePrecision() {
        // Ten million blocks apart: only float precision of the distance itself is expected
        final double distance = 1e7;
        assertEquals(distance, relative(5e8 + distance, 5e8), distance * 1e-7);
        assertEquals(1234.5, relative(1234.5, 0), 1e-4);
    }

    @Test
    void entriesMatchTheShaderLayout() {
        ByteBuffer out = MemoryUtil.memCalloc(2 * InstanceTable.ENTRY_SIZE);
        try {
            out.position(InstanceTable.ENTRY_SIZE);
            Quaternionf rotation = new Quaternionf(0.1f, 0.2f, 0.3f, 0.9f);
            InstanceTable.writeEntry(out, true, new Vector3d(1e9 + 0.5, -2, 3), rotation, 0.25f);
            assertEquals(InstanceTable.ENTRY_SIZE, out.position(), "The position is unchanged");

            final int base = InstanceTable.ENTRY_SIZE;
            assertEquals(64, InstanceTable.ENTRY_SIZE, "Four vec4s, as std430 lays them out");
            assertEquals(1e9f, out.getFloat(base + InstanceTable.POSITION_HIGH_OFFSET));
            assertEquals(0.25f, out.getFloat(base + InstanceTable.POSITION_HIGH_OFFSET + 12));
            assertEquals(
                    (float) (1e9 + 0.5 - 1e9f),
                    out.getFloat(base + InstanceTable.POSITION_LOW_OFFSET));
            assertEquals(-2f, out.getFloat(base + InstanceTable.POSITION_HIGH_OFFSET + 4));
            assertEquals(0f, out.getFloat(base + InstanceTable.POSITION_LOW_OFFSET + 4));
            assertEquals(0.9f, out.getFloat(base + InstanceTable.ROTATION_OFFSET + 12));
            int flags = out.getInt(base + InstanceTable.INFO_OFFSET);
            assertEquals(InstanceTable.FLAG_ALIVE, flags & InstanceTable.FLAG_ALIVE);
            assertEquals(
                    InstanceTable.VISIBLE_TO_ALL, flags >>> InstanceTable.VISIBILITY_MASK_SHIFT);
            // The first entry was left alone
            assertEquals(0, out.getInt(InstanceTable.INFO_OFFSET));

            InstanceTable.writeEntry(out, false, new Vector3d(), new Quaternionf(), 0);
            assertEquals(0, out.getInt(base + InstanceTable.INFO_OFFSET), "Not alive");
        } finally {
            MemoryUtil.memFree(out);
        }
    }
}
