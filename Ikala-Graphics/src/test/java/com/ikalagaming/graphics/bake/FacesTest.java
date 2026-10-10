package com.ikalagaming.graphics.bake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.graph.Material;

import org.junit.jupiter.api.Test;

/** Which faces neighbors hide, by coverage and material. */
class FacesTest {

    private static final int NONE = CubeRotations.IDENTITY;

    private static boolean hides(BakeSource hidden, Face face, BakeSource neighbor) {
        return Faces.hides(hidden, NONE, face, neighbor, NONE);
    }

    @Test
    void coverageDecides() {
        BakeSource cube = BakeFixtures.cube().source();
        BakeSource slab = BakeFixtures.slab().source();
        assertTrue(hides(cube, Face.POS_X, cube), "Cube against cube");
        assertTrue(hides(slab, Face.POS_X, cube), "A slab's half side against a cube");
        assertFalse(hides(cube, Face.POS_X, slab), "A cube's side is only half covered by a slab");
        assertTrue(hides(slab, Face.POS_X, slab), "Slab sides line up");
        assertTrue(hides(slab, Face.NEG_Y, cube), "A slab standing on a cube");
        assertTrue(hides(cube, Face.POS_Y, slab), "A slab resting on a cube");
        assertFalse(
                hides(slab, Face.POS_Y, cube), "A slab's top isn't on the box, nothing to hide");
        assertFalse(hides(BakeFixtures.plant().source(), Face.POS_X, cube), "Nothing to hide");
    }

    @Test
    void materialDecides() {
        BakeSource stone = BakeFixtures.cube().source();
        BakeSource leaves =
                BakeFixtures.cube().source(BakeFixtures.material(Material.Transparency.CUTOUT));
        Material glassMaterial = BakeFixtures.material(Material.Transparency.TRANSLUCENT);
        BakeSource glass = BakeFixtures.cube().source(glassMaterial);
        BakeSource otherGlass =
                BakeFixtures.cube()
                        .source(BakeFixtures.material(Material.Transparency.TRANSLUCENT));
        BakeSource sameGlass = BakeFixtures.cube().source(glassMaterial);

        assertFalse(hides(stone, Face.POS_X, leaves), "Stone shows through leaves");
        assertTrue(hides(leaves, Face.POS_X, stone), "Leaves against stone are hidden");
        assertFalse(hides(leaves, Face.POS_X, leaves), "Leaves show through leaves");
        assertFalse(hides(stone, Face.POS_X, glass), "Stone shows through glass");
        assertTrue(hides(glass, Face.POS_X, sameGlass), "No face between panes of one glass");
        assertFalse(hides(glass, Face.POS_X, otherGlass), "Two kinds of glass keep their faces");
    }

    @Test
    void rotationsTurnTheFacesCompared() {
        BakeSource slab = BakeFixtures.slab().source();
        BakeSource cube = BakeFixtures.cube().source();
        // Upside down, the slab's full face is on top, away from a cube below it
        final int upsideDown = rotationTurning(Face.NEG_Y, Face.POS_Y);
        assertTrue(Faces.hides(cube, NONE, Face.POS_Y, slab, NONE), "A cube under a floor slab");
        assertFalse(
                Faces.hides(cube, NONE, Face.POS_Y, slab, upsideDown),
                "A cube under a ceiling slab");
        assertTrue(
                Faces.hides(cube, NONE, Face.NEG_Y, slab, upsideDown), "A cube on a ceiling slab");

        BakeSource[] neighbors = new BakeSource[Face.ALL.length];
        int[] rotations = new int[Face.ALL.length];
        neighbors[Face.POS_Y.ordinal()] = slab;
        rotations[Face.POS_Y.ordinal()] = upsideDown;
        neighbors[Face.NEG_Y.ordinal()] = slab;
        rotations[Face.NEG_Y.ordinal()] = upsideDown;
        neighbors[Face.NEG_X.ordinal()] = cube;
        assertEquals(
                Face.NEG_Y.bit() | Face.NEG_X.bit(),
                Faces.hiddenFaces(cube, NONE, neighbors, rotations));
    }

    /**
     * Find a rotation taking one face to another.
     *
     * @param from The face before.
     * @param to The face after.
     * @return The first rotation that does.
     */
    static int rotationTurning(Face from, Face to) {
        for (int rotation = 0; rotation < CubeRotations.COUNT; ++rotation) {
            if (CubeRotations.turn(rotation, from) == to) {
                return rotation;
            }
        }
        throw new AssertionError("No rotation turns " + from + " to " + to);
    }
}
