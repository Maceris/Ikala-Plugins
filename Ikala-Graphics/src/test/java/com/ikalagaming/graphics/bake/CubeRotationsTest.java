package com.ikalagaming.graphics.bake;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ikalagaming.graphics.graph.MeshData;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

/** The 24 cube rotations, and turning coverage masks with them. */
class CubeRotationsTest {

    @Test
    void thereAre24DistinctRotationsStartingWithTheIdentity() {
        Set<String> seen = new HashSet<>();
        for (int rotation = 0; rotation < CubeRotations.COUNT; ++rotation) {
            StringBuilder key = new StringBuilder();
            for (int row = 0; row < 3; ++row) {
                for (int column = 0; column < 3; ++column) {
                    key.append(CubeRotations.element(rotation, row, column)).append(',');
                }
            }
            seen.add(key.toString());
        }
        assertEquals(CubeRotations.COUNT, seen.size());
        for (Face face : Face.ALL) {
            assertEquals(face, CubeRotations.turn(CubeRotations.IDENTITY, face));
        }
    }

    @Test
    void rotationsComposeAndInvert() {
        for (int a = 0; a < CubeRotations.COUNT; ++a) {
            assertEquals(
                    CubeRotations.IDENTITY,
                    CubeRotations.compose(a, CubeRotations.inverse(a)),
                    "Rotation " + a);
            for (int b = 0; b < CubeRotations.COUNT; ++b) {
                final int both = CubeRotations.compose(a, b);
                for (Face face : Face.ALL) {
                    assertEquals(
                            CubeRotations.turn(b, CubeRotations.turn(a, face)),
                            CubeRotations.turn(both, face));
                }
            }
        }
    }

    /**
     * The property the lookup table rests on: turning a mesh and working out its coverage again
     * gives the same masks as turning the original masks.
     */
    @Test
    void turningMasksMatchesTurningTheMesh() {
        final BakeFixtures.Mesh stair = BakeFixtures.stair();
        final BakeSource original = stair.source();
        for (int rotation = 0; rotation < CubeRotations.COUNT; ++rotation) {
            final BakeSource turned = turn(stair, rotation).source();
            for (Face face : Face.ALL) {
                final Face to = CubeRotations.turn(rotation, face);
                final FaceCoverage before = original.getCoverage(face);
                final FaceCoverage after = turned.getCoverage(to);
                final String where = "Rotation " + rotation + ", " + face + " to " + to;
                assertArrayEquals(
                        after.covers(),
                        CubeRotations.turnMask(before.covers(), rotation, face),
                        where);
                assertArrayEquals(
                        after.touches(),
                        CubeRotations.turnMask(before.touches(), rotation, face),
                        where);
            }
        }
    }

    /**
     * Turn a fixture mesh about the unit box's center.
     *
     * @param mesh The mesh.
     * @param rotation The rotation.
     * @return A turned copy.
     */
    private static BakeFixtures.Mesh turn(BakeFixtures.Mesh mesh, int rotation) {
        final int stride = MeshData.VERTEX_SIZE_IN_FLOATS;
        BakeFixtures.Mesh turned = new BakeFixtures.Mesh();
        turned.vertices = mesh.vertices.clone();
        turned.indices = mesh.indices.clone();
        final float[] in = new float[3];
        final float[] out = new float[3];
        for (int at = 0; at < turned.vertices.length; at += stride) {
            // Position about the center, then each direction
            for (int axis = 0; axis < 3; ++axis) {
                in[axis] = mesh.vertices[at + axis] - 0.5f;
            }
            CubeRotations.apply(rotation, in, out);
            for (int axis = 0; axis < 3; ++axis) {
                turned.vertices[at + axis] = out[axis] + 0.5f;
            }
            for (int direction = 3; direction < 12; direction += 3) {
                CubeRotations.apply(
                        rotation, mesh.vertices, at + direction, turned.vertices, at + direction);
            }
        }
        return turned;
    }
}
