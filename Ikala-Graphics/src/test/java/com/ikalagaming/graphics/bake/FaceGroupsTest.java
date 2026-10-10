package com.ikalagaming.graphics.bake;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Which triangles lie on which face of the box, and how much of each face they cover. */
class FaceGroupsTest {

    private static int countOn(BakeSource source, Face face) {
        int count = 0;
        for (byte triangleFace : source.getTriangleFaces()) {
            if (triangleFace == face.ordinal()) {
                ++count;
            }
        }
        return count;
    }

    /** The rows of a face's grid that are entirely set, as a bit per row. */
    private static int fullRows(long[] mask) {
        int rows = 0;
        for (int row = 0; row < FaceCoverage.GRID_SIZE; ++row) {
            boolean full = true;
            for (int column = 0; column < FaceCoverage.GRID_SIZE; ++column) {
                full &= FaceCoverage.get(mask, FaceCoverage.cell(column, row));
            }
            if (full) {
                rows |= 1 << row;
            }
        }
        return rows;
    }

    @Test
    void aCubeHasTwoTrianglesOnEveryFaceCoveringIt() {
        BakeSource cube = BakeFixtures.cube().source();
        assertEquals(12, cube.getTriangleCount());
        for (Face face : Face.ALL) {
            assertEquals(2, countOn(cube, face), face.name());
            assertArrayEquals(FaceCoverage.full(), cube.getCoverage(face).covers(), face.name());
            assertArrayEquals(FaceCoverage.full(), cube.getCoverage(face).touches(), face.name());
        }
    }

    @Test
    void aSlabCoversItsBottomAndTheLowerHalfOfItsSides() {
        BakeSource slab = BakeFixtures.slab().source();
        assertEquals(2, countOn(slab, Face.NEG_Y));
        assertEquals(0, countOn(slab, Face.POS_Y), "The top is inside the box");
        assertTrue(slab.getCoverage(Face.POS_Y).isEmpty());
        assertArrayEquals(FaceCoverage.full(), slab.getCoverage(Face.NEG_Y).covers());
        // Rows run along v, which is y on the x and z faces: the bottom 8 of 16
        final int lowerHalf = (1 << 8) - 1;
        for (Face side : new Face[] {Face.NEG_X, Face.POS_X, Face.NEG_Z, Face.POS_Z}) {
            assertEquals(2, countOn(slab, side), side.name());
            assertEquals(lowerHalf, fullRows(slab.getCoverage(side).covers()), side.name());
            assertEquals(lowerHalf, fullRows(slab.getCoverage(side).touches()), side.name());
        }
    }

    @Test
    void meshesThatArentBoxShapedHaveNoGroups() {
        BakeSource plant = BakeFixtures.plant().source();
        for (byte face : plant.getTriangleFaces()) {
            assertEquals(Face.NONE, face);
        }
        for (Face face : Face.ALL) {
            assertTrue(plant.getCoverage(face).isEmpty());
        }
    }

    @Test
    void onlyTrianglesOnThePlaneFacingOutCount() {
        float[] min = {0.25f, 0.25f, 0};
        float[] max = {0.75f, 0.75f, 0};
        // A decal set just inside the +z face is kept whatever the neighbors do
        float[] insetMin = {0.25f, 0.25f, 0.99f};
        float[] insetMax = {0.75f, 0.75f, 0.99f};
        BakeSource inset = new BakeFixtures.Mesh().face(insetMin, insetMax, 2, 1).source();
        assertEquals(Face.NONE, inset.getTriangleFaces()[0]);
        // On the plane, within the tolerance, it is on the face
        float[] closeMin = {0.25f, 0.25f, 1 - BakeSource.PLANE_TOLERANCE / 2};
        float[] closeMax = {0.75f, 0.75f, 1 - BakeSource.PLANE_TOLERANCE / 2};
        BakeSource close = new BakeFixtures.Mesh().face(closeMin, closeMax, 2, 1).source();
        assertEquals(Face.POS_Z.ordinal(), close.getTriangleFaces()[0]);
        // On the -z plane but facing into the box
        BakeSource inward = new BakeFixtures.Mesh().face(min, max, 2, 1).source();
        assertEquals(Face.NONE, inward.getTriangleFaces()[0]);
        assertFalse(close.getCoverage(Face.POS_Z).isEmpty());
    }

    @Test
    void theBoxMustBeACube() {
        float[] data = BakeFixtures.cube().vertices;
        int[] indices = BakeFixtures.cube().indices;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BakeSource.derive(
                                data, indices, new Vector3f(0, 0, 0), new Vector3f(1, 2, 1), null));
    }
}
