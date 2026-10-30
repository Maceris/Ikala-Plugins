package com.ikalagaming.characters.view;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Scale;
import com.ikalagaming.characters.data.Vec3;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.pose.Skeleton;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Tests for moving an accessory's mesh by its placement: along the model's axes, turned and scaled
 * about its socket.
 *
 * @author Ches Burks
 */
class TestAccessoryPlacement {
    /** How close floats must be. */
    private static final float EPSILON = 1e-5f;

    /** A root, and a socket a meter above it turned a quarter turn about Y. */
    private static final Skeleton SKELETON =
            new Skeleton(
                    new Rig(
                            Id.parse("test:rig"),
                            List.of(
                                    new Rig.Bone("root", null, Rig.BoneKind.DEFORM),
                                    new Rig.Bone("socket", "root", Rig.BoneKind.SOCKET))),
                    Map.of(
                            "root",
                            new Skeleton.Rest(new Vector3f(), new Quaternionf()),
                            "socket",
                            new Skeleton.Rest(
                                    new Vector3f(0, 1, 0),
                                    new Quaternionf().rotateY((float) Math.PI / 2))));

    /**
     * One vertex weighted to the socket, its normal along +Y and tangent along +X.
     *
     * @param position Where it is, in model space.
     * @return The mesh.
     */
    private static ImportedCharacter.Part vertexAt(Vector3f position) {
        return vertexAt(position, new Vector3f(0, 1, 0));
    }

    /**
     * A mesh of one vertex with a normal, weighted to bone 1.
     *
     * @param position The vertex's position.
     * @param normal Its normal.
     * @return The mesh.
     */
    private static ImportedCharacter.Part vertexAt(Vector3f position, Vector3f normal) {
        float[] vertices = new float[14];
        vertices[0] = position.x;
        vertices[1] = position.y;
        vertices[2] = position.z;
        vertices[3] = normal.x;
        vertices[4] = normal.y;
        vertices[5] = normal.z;
        vertices[6] = 1;
        vertices[11] = 1;
        return new ImportedCharacter.Part(
                "hat",
                vertices,
                new int[] {1, 0, 0, 0},
                new float[] {1, 0, 0, 0},
                new int[] {0, 0, 0},
                -1,
                new Vector3f(position),
                new Vector3f(position));
    }

    /**
     * Read a mesh's first vertex position.
     *
     * @param part The mesh.
     * @return The position.
     */
    private static Vector3f position(ImportedCharacter.Part part) {
        return new Vector3f(part.vertices()[0], part.vertices()[1], part.vertices()[2]);
    }

    /** The offset is along the model's axes, whichever way the socket bone points. */
    @Test
    void testOffsetAlongModelAxes() {
        ImportedCharacter.Part placed =
                PartAssembler.placed(
                        vertexAt(new Vector3f(0, 1, 0)),
                        SKELETON,
                        new Part.Placement(new Vec3(0.1f, 0, 0), null, 1));
        Vector3f expected = new Vector3f(0.1f, 1, 0);
        Assertions.assertTrue(position(placed).equals(expected, EPSILON), "" + position(placed));
        Assertions.assertTrue(placed.min().equals(expected, EPSILON));
        Assertions.assertTrue(placed.max().equals(expected, EPSILON));
    }

    /** Scale and turns are about the socket, and directions stay unit length. */
    @Test
    void testScaleAndTurnAboutBone() {
        ImportedCharacter.Part grown =
                PartAssembler.placed(
                        vertexAt(new Vector3f(0, 1.5f, 0)),
                        SKELETON,
                        new Part.Placement(null, null, 2));
        Assertions.assertTrue(position(grown).equals(new Vector3f(0, 2, 0), EPSILON));
        Assertions.assertEquals(1, grown.vertices()[4], EPSILON);

        // A quarter turn about z through the socket takes +Y to -X
        ImportedCharacter.Part turned =
                PartAssembler.placed(
                        vertexAt(new Vector3f(0, 1.5f, 0)),
                        SKELETON,
                        new Part.Placement(null, new Vec3(0, 0, 90), 1));
        Assertions.assertTrue(
                position(turned).equals(new Vector3f(-0.5f, 1, 0), EPSILON),
                "" + position(turned));
        Vector3f normal =
                new Vector3f(turned.vertices()[3], turned.vertices()[4], turned.vertices()[5]);
        Assertions.assertTrue(normal.equals(new Vector3f(-1, 0, 0), EPSILON), "" + normal);
    }

    /**
     * A scale that differs by axis stretches along the placement's turned axes, and normals lean
     * the other way, staying square to the stretched surface.
     */
    @Test
    void testScaleByAxis() {
        // Taller only: up from the socket doubles, across stays
        ImportedCharacter.Part taller =
                PartAssembler.placed(
                        vertexAt(new Vector3f(0.5f, 1.5f, 0)),
                        SKELETON,
                        new Part.Placement(null, null, new Scale(1, 2, 1)));
        Assertions.assertTrue(
                position(taller).equals(new Vector3f(0.5f, 2, 0), EPSILON), "" + position(taller));

        // Turned a quarter about z first, its own y now points along -x
        ImportedCharacter.Part turned =
                PartAssembler.placed(
                        vertexAt(new Vector3f(0.5f, 1.5f, 0)),
                        SKELETON,
                        new Part.Placement(null, new Vec3(0, 0, 90), new Scale(1, 2, 1)));
        Assertions.assertTrue(
                position(turned).equals(new Vector3f(-1, 1.5f, 0), EPSILON),
                "" + position(turned));

        // A slanted surface stretched twice as wide leans its normal toward up
        ImportedCharacter.Part wide =
                PartAssembler.placed(
                        vertexAt(new Vector3f(0, 1.5f, 0), new Vector3f(1, 1, 0).normalize()),
                        SKELETON,
                        new Part.Placement(null, null, new Scale(2, 1, 1)));
        Vector3f normal = new Vector3f(wide.vertices()[3], wide.vertices()[4], wide.vertices()[5]);
        Assertions.assertTrue(
                normal.equals(new Vector3f(1, 2, 0).normalize(), EPSILON), "" + normal);
    }

    /** No placement leaves the mesh where it was. */
    @Test
    void testNoPlacement() {
        Assertions.assertTrue(
                Part.Placement.NONE
                        .matrix(new Vector3f(0, 1, 0), new Matrix4f())
                        .equals(new Matrix4f(), EPSILON));
        ImportedCharacter.Part same =
                PartAssembler.placed(
                        vertexAt(new Vector3f(0.2f, 1.3f, 0.1f)), SKELETON, Part.Placement.NONE);
        Assertions.assertTrue(position(same).equals(new Vector3f(0.2f, 1.3f, 0.1f), EPSILON));
    }
}
