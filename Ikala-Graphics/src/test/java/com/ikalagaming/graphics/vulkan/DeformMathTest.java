package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.bake.SurfaceColumns;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The deformation field's packing, refill and footprints, and the surfaces' column lookup. */
class DeformMathTest {

    /**
     * A press packs and unpacks to the tenth of a millimeter, a deeper press packs larger whatever
     * the surface, so atomicMax keeps it, and a rim packs below an untouched-looking press but
     * above zero.
     */
    @Test
    void testPacking() {
        final int press = DeformMath.pack(0.12, 1.25);
        assertEquals(0.12, DeformMath.depth(press), 1e-4);
        assertEquals(DeformMath.heightKey(1.25), press & 0xFFFF);
        assertTrue(Integer.compareUnsigned(DeformMath.pack(0.13, 0), press) > 0);
        final int rim = DeformMath.pack(-0.02, 1.25);
        assertEquals(-0.02, DeformMath.depth(rim), 1e-4);
        assertTrue(Integer.compareUnsigned(rim, 0) > 0);
        assertTrue(Integer.compareUnsigned(rim, press) < 0);
        assertEquals(0, DeformMath.depth(0));
        // Wrapping keeps neighbors apart
        assertEquals(
                DeformMath.index(DeformMath.SIZE + 5, -1),
                DeformMath.index(5, DeformMath.SIZE - 1));
    }

    /** Refill moves a press and a rim toward nothing, then clears the texel. */
    @Test
    void testRefill() {
        final int press = DeformMath.pack(0.01, 0);
        final int halfway = DeformMath.refill(press, 0.01, 0.5);
        assertEquals(0.005, DeformMath.depth(halfway), 1e-4);
        assertEquals(0, DeformMath.refill(press, 0.01, 2));
        final int rim = DeformMath.pack(-0.01, 0);
        assertEquals(-0.005, DeformMath.depth(DeformMath.refill(rim, 0.01, 0.5)), 1e-4);
        assertEquals(press, DeformMath.refill(press, 0, 1));
    }

    /** A footprint is flat in its middle, eases to nothing at its edge, then rises in a rim. */
    @Test
    void testProfile() {
        assertEquals(0.1, DeformMath.profile(0.3, 0.6, 0.1, 0.5, 0.02));
        assertEquals(0.05, DeformMath.profile(0.8, 0.6, 0.1, 0.5, 0.02), 1e-9);
        assertEquals(0, DeformMath.profile(1, 0.6, 0.1, 0.5, 0.02), 1e-9);
        assertEquals(-0.02, DeformMath.profile(1.25, 0.6, 0.1, 0.5, 0.02), 1e-9);
        assertEquals(0, DeformMath.profile(1.6, 0.6, 0.1, 0.5, 0.02));
    }

    /**
     * The registry hands over each column's surfaces highest first, in world meters, with their
     * material's refill rate unless the column has its own, and forgets a removed section's.
     */
    @Test
    void testRegistry() {
        final short[] tops = new short[SurfaceColumns.COLUMNS];
        Arrays.fill(tops, SurfaceColumns.NONE);
        final short[] depths = new short[SurfaceColumns.COLUMNS];
        final int[] materials = new int[SurfaceColumns.COLUMNS];
        final float[] refill = new float[SurfaceColumns.COLUMNS];
        Arrays.fill(refill, Float.NaN);
        tops[SurfaceColumns.index(2, 3)] = 20;
        depths[SurfaceColumns.index(2, 3)] = 4;
        materials[SurfaceColumns.index(2, 3)] = 7;
        tops[SurfaceColumns.index(5, 3)] = 18;
        depths[SurfaceColumns.index(5, 3)] = 2;
        refill[SurfaceColumns.index(5, 3)] = 0.5f;
        final SurfaceRegistry registry = new SurfaceRegistry();
        registry.setRefillRate(7, 0.01f);
        // A section at (16, 32, -16): a roof over the ground in another at y 16
        registry.put(
                1,
                "a",
                new Vector3d(16, 32, -16),
                new SurfaceColumns(16, tops, depths, materials, refill, null));
        registry.put(
                2, "a", new Vector3d(16, 16, -16), new SurfaceColumns(16, tops, depths, materials));
        final List<String> found = new ArrayList<>();
        registry.fill(
                16,
                -16,
                16,
                16,
                (x, z, slot, top, bottom, rate, flags) ->
                        found.add(
                                x + "," + z + "," + slot + "," + top + "," + bottom + "," + rate));
        assertEquals(
                List.of(
                        "18,-13,0,33.25,33.0,0.01",
                        "18,-13,1,17.25,17.0,0.01",
                        "21,-13,0,33.125,33.0,0.5",
                        "21,-13,1,17.125,17.0,0.0"),
                found);
        final long version = registry.version();
        registry.remove(1);
        assertTrue(registry.version() > version);
        found.clear();
        registry.fill(18, -13, 1, 1, (x, z, slot, top, bottom, rate, flags) -> found.add("" + top));
        assertEquals(List.of("17.25"), found);
        registry.removeOwner("a");
        found.clear();
        registry.fill(16, -16, 16, 16, (x, z, slot, top, bottom, rate, flags) -> found.add(""));
        assertTrue(found.isEmpty());
    }
}
