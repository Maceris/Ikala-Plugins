package com.ikalagaming.converter.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.converter.scene.ImportedScene.Node;
import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.lod.LodMesh;

import org.joml.Matrix4d;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

/** Tests for gathering a scene's meshes for level of detail. */
class SceneLodTest {

    /**
     * Two cubes, one of them mirrored, from the cube preview's mesh.
     *
     * @return The scene.
     */
    private static ImportedScene twoCubes() {
        final ImportedScene preview = PreviewShapes.scene(PreviewShapes.Shape.CUBE);
        final Matrix4d identity = new Matrix4d();
        final Node root = new Node("Root", identity, null, identity, new int[0]);
        root.addChild(
                new Node(
                        "Cube",
                        new Matrix4d().translation(3, 0, 0),
                        root,
                        root.world(),
                        new int[] {0}));
        root.addChild(
                new Node(
                        "Mirrored",
                        new Matrix4d().translation(-3, 0, 0).scale(-1, 1, 1),
                        root,
                        root.world(),
                        new int[] {0}));
        return new ImportedScene(
                Path.of("cubes"),
                preview.meshes(),
                preview.materials(),
                root,
                List.of(),
                1,
                1,
                List.of(),
                0);
    }

    /**
     * The volume a closed mesh encloses, positive when its triangles face out.
     *
     * @param mesh The mesh.
     * @return The signed volume.
     */
    private static double volume(LodMesh mesh) {
        double total = 0;
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            final double[][] p = new double[3][3];
            for (int corner = 0; corner < 3; ++corner) {
                for (int axis = 0; axis < 3; ++axis) {
                    p[corner][axis] =
                            mesh.getOrigin()[axis] + mesh.position(mesh.corner(t, corner), axis);
                }
            }
            total +=
                    (p[0][0] * (p[1][1] * p[2][2] - p[1][2] * p[2][1])
                                    - p[0][1] * (p[1][0] * p[2][2] - p[1][2] * p[2][0])
                                    + p[0][2] * (p[1][0] * p[2][1] - p[1][1] * p[2][0]))
                            / 6;
        }
        return total;
    }

    @Test
    void testMirroredNodesFaceOut() {
        final ImportedScene scene = twoCubes();
        final Material plain = new Material();
        final SceneLod.Input input =
                SceneLod.gather(scene, scene.root(), (node, mesh) -> plain, material -> 7);
        assertNotNull(input);
        assertEquals(2, input.meshes());
        assertEquals(0, input.skipped());
        final LodMesh mesh = input.mesh();
        assertEquals(24, mesh.getTriangleCount());
        // Two unit cubes; a mirrored one inside out would cancel the other
        assertEquals(2, volume(mesh), 1e-4);
        // Positions are relative to the middle of what went in, which is the world's origin here
        assertEquals(0, mesh.getOrigin()[0], 1e-9);
        assertEquals(7, mesh.material(0));
        // Normals and texture coordinates are kept even without a texture
        assertTrue(mesh.isTextured(0));
    }

    @Test
    void testTexturedKeepsAttributesAndCutoutsStayOut() {
        final ImportedScene scene = twoCubes();
        final Material textured = new Material();
        textured.setTexture(new TextureHandle(1, 0, 1, 1, Format.R8G8B8A8_UNORM));
        final Material cutout = new Material();
        cutout.setTransparency(Material.Transparency.CUTOUT);
        final SceneLod.Input input =
                SceneLod.gather(
                        scene,
                        scene.root(),
                        (node, mesh) -> "Cube".equals(node.name()) ? textured : cutout,
                        material -> 0);
        assertNotNull(input);
        assertEquals(1, input.meshes());
        assertEquals(1, input.skipped());
        assertEquals(12, input.mesh().getTriangleCount());
        for (int t = 0; t < 12; ++t) {
            assertTrue(input.mesh().isTextured(t));
        }
        assertNull(SceneLod.gather(scene, scene.root(), (node, mesh) -> cutout, material -> 0));
    }
}
