package com.ikalagaming.converter.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.converter.scene.ImportedScene.Node;
import com.ikalagaming.converter.scene.ImportedScene.TextureSlot;
import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.graph.MeshData;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Tests for reading model files into scenes, and for working out how they are placed. */
class SceneImporterTest {

    /** Where test files are written. */
    @TempDir Path folder;

    /**
     * Write a glTF scene: one triangle mesh with a textured material, drawn by two nodes, one
     * moved, the other moved and stretched along y.
     *
     * @return The file.
     * @throws IOException If it can't be written.
     */
    private Path writeScene() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        float[] positions = {0, 0, 0, 1, 0, 0, 0, 1, 0};
        for (float value : positions) {
            buffer.putFloat(value);
        }
        buffer.putShort((short) 0).putShort((short) 1).putShort((short) 2);
        final String data = Base64.getEncoder().encodeToString(buffer.array());
        Files.createDirectory(folder.resolve("Textures"));
        Files.write(folder.resolve("Textures").resolve("Brick_BaseColor.PNG"), new byte[] {1});
        final String gltf =
                        """
                {
                  "asset": {"version": "2.0"},
                  "scene": 0,
                  "scenes": [{"nodes": [0]}],
                  "extensionsUsed": ["KHR_lights_punctual"],
                  "extensions": {"KHR_lights_punctual": {"lights": [
                    {"name": "Lamp", "type": "point", "color": [1, 0.5, 0.25], "intensity": 3}]}},
                  "nodes": [
                    {"name": "Root", "children": [1, 2, 3]},
                    {"name": "Moved", "mesh": 0, "translation": [1, 0, 0]},
                    {"name": "Stretched", "mesh": 0, "translation": [0, 0, 5],
                     "scale": [1, 2, 1]},
                    {"name": "Lamp", "translation": [2, 3, 4],
                     "extensions": {"KHR_lights_punctual": {"light": 0}}}
                  ],
                  "meshes": [{"name": "Triangle", "primitives": [
                    {"attributes": {"POSITION": 0}, "indices": 1, "material": 0}]}],
                  "materials": [{"name": "Brick", "emissiveFactor": [0.1, 0.2, 0.3],
                    "pbrMetallicRoughness": {
                    "baseColorFactor": [0.5, 0.25, 1, 1], "roughnessFactor": 0.75,
                    "baseColorTexture": {"index": 0}}}],
                  "textures": [{"source": 0}],
                  "images": [{"uri": "C:/somewhere/else/brick_basecolor.png"}],
                  "buffers": [{"byteLength": 44,
                    "uri": "data:application/octet-stream;base64,%s"}],
                  "bufferViews": [
                    {"buffer": 0, "byteOffset": 0, "byteLength": 36},
                    {"buffer": 0, "byteOffset": 36, "byteLength": 6}
                  ],
                  "accessors": [
                    {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3",
                     "min": [0, 0, 0], "max": [1, 1, 0]},
                    {"bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR"}
                  ]
                }
                """
                        .formatted(data);
        Path file = folder.resolve("scene.gltf");
        Files.writeString(file, gltf);
        return file;
    }

    /**
     * Find a node by name.
     *
     * @param node Where to start.
     * @param name The name.
     * @return The node, or null.
     */
    private static Node find(Node node, String name) {
        if (node.name().equals(name)) {
            return node;
        }
        for (Node child : node.children()) {
            Node found = find(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void testReadsTreeMeshesAndMaterials() throws IOException {
        List<String> progress = new ArrayList<>();
        ImportedScene scene = SceneImporter.read(writeScene(), progress::add);
        assertFalse(progress.isEmpty());

        assertEquals(1, scene.meshes().size());
        assertEquals(1, scene.meshes().get(0).triangles());
        assertEquals(1, scene.uniqueTriangles());
        assertEquals(2, scene.drawnTriangles());
        assertEquals(1.0, scene.unitScale(), 1e-9);

        Node moved = find(scene.root(), "Moved");
        Node stretched = find(scene.root(), "Stretched");
        assertNotNull(moved);
        assertNotNull(stretched);
        assertEquals(1, moved.world().m30(), 1e-6);
        assertEquals(2, stretched.world().m11(), 1e-6);

        Vector3d min = new Vector3d();
        Vector3d max = new Vector3d();
        assertTrue(scene.bounds(scene.root(), min, max));
        assertEquals(0, min.x, 1e-6);
        assertEquals(2, max.x, 1e-6);
        assertEquals(2, max.y, 1e-6);
        assertEquals(5, max.z, 1e-6);

        ImportedScene.ImportedMaterial material = scene.materials().get(0);
        assertEquals("Brick", material.name());
        assertEquals(0.25f, material.color().y, 1e-6f);
        assertEquals(0.75f, material.roughness(), 1e-6f);
        assertEquals(0.2f, material.emissive().y, 1e-6f);
        // Found by name, ignoring case, in the Textures folder
        ImportedScene.TextureRef base = material.textures().get(TextureSlot.BASE_COLOR);
        assertNotNull(base);
        assertEquals(folder.resolve("Textures").resolve("Brick_BaseColor.PNG"), base.found());
    }

    @Test
    void testPlacementInstancesAndBakes() throws IOException {
        ImportedScene scene = SceneImporter.read(writeScene(), message -> {});
        ScenePlacement placement = ScenePlacement.prepare(null, scene);
        assertFalse(placement.isNormalGreenDown());
        // The moved node shares the mesh's model; the stretched one is baked into its own
        assertEquals(2, placement.modelCount());
        assertEquals(2, placement.instanceCount());
        assertEquals(1, placement.getBakedNodes());
        assertTrue(placement.isBaked(find(scene.root(), "Stretched")));
        assertFalse(placement.isBaked(find(scene.root(), "Moved")));
    }

    @Test
    void testInstanceTransforms() {
        Node node = find(placeholder(), "n");
        Matrix4d world = new Matrix4d().translation(1, 2, 3).rotateY(0.5).scale(2);
        ScenePlacement.Transform transform = ScenePlacement.instanceTransform(node, world);
        assertNotNull(transform);
        assertEquals(2, transform.scale(), 1e-5);
        assertEquals(3, transform.position().z, 1e-9);
        // Stretched, mirrored and sheared transforms need baking
        assertNull(ScenePlacement.instanceTransform(node, new Matrix4d().scale(1, 2, 1)));
        assertNull(ScenePlacement.instanceTransform(node, new Matrix4d().scale(-1, 1, 1)));
        assertNull(ScenePlacement.instanceTransform(node, new Matrix4d().m10(0.5)));
    }

    @Test
    void testBakingMirroredFlipsTriangles() {
        float[] vertices = new float[3 * MeshData.VERTEX_SIZE_IN_FLOATS];
        int stride = MeshData.VERTEX_SIZE_IN_FLOATS;
        vertices[stride] = 1;
        vertices[2 * stride + 1] = 1;
        for (int v = 0; v < 3; ++v) {
            vertices[v * stride + 5] = 1;
        }
        MeshData mesh =
                new MeshData(
                        new org.joml.Vector3f(),
                        new org.joml.Vector3f(1, 1, 0),
                        3,
                        vertices,
                        new int[] {0, 1, 2},
                        0,
                        new byte[0]);
        Matrix4d mirror = new Matrix4d().translation(10, 0, 0).scale(-2, 1, 1);
        MeshData baked = ScenePlacement.bakeMesh(mesh, mirror, new Vector3d(10, 0, 0));
        assertEquals(-2, baked.getVertexData()[stride], 1e-6f);
        assertEquals(-2, baked.getAabbMin().x, 1e-6f);
        // Winding flipped so the triangle still faces out
        assertEquals(2, baked.getIndices()[1]);
        // The normal stays a unit vector
        assertEquals(1, baked.getVertexData()[5], 1e-6f);
    }

    @Test
    void testColorSpacesAndMissingFile() {
        assertEquals(ColorSpace.SRGB, ScenePlacement.colorSpace(TextureSlot.BASE_COLOR));
        assertEquals(ColorSpace.LINEAR, ScenePlacement.colorSpace(TextureSlot.NORMAL));
        assertEquals(ColorSpace.LINEAR, ScenePlacement.colorSpace(TextureSlot.SPECULAR));
        assertEquals(ColorSpace.SRGB, ScenePlacement.colorSpace(TextureSlot.EMISSIVE));
        assertTrue(ScenePlacement.applied(TextureSlot.SPECULAR));
        assertTrue(ScenePlacement.applied(TextureSlot.EMISSIVE));
        assertFalse(ScenePlacement.applied(TextureSlot.OPACITY));
        // DirectX normal maps for FBX files, OpenGL ones for the rest
        assertTrue(ScenePlacement.defaultGreenDown(Path.of("Bistro.FBX")));
        assertFalse(ScenePlacement.defaultGreenDown(Path.of("scene.gltf")));
        assertThrows(
                IOException.class,
                () -> SceneImporter.read(folder.resolve("missing.fbx"), message -> {}));
    }

    /**
     * A one node tree, for transform checks.
     *
     * @return The root, named n.
     */
    private static Node placeholder() {
        return new Node("n", new Matrix4d(), null, new Matrix4d(), new int[0]);
    }

    @Test
    void testLights() throws IOException {
        ImportedScene scene = SceneImporter.read(writeScene(), message -> {});
        assertEquals(1, scene.lights().size());
        ImportedScene.ImportedLight lamp = scene.lights().get(0);
        assertEquals(ImportedScene.LightType.POINT, lamp.type());
        assertEquals(2, lamp.position().x, 1e-6);
        assertEquals(4, lamp.position().z, 1e-6);
        // The brightest channel is 1, the rest in proportion
        assertEquals(1, lamp.color().x, 1e-5f);
        assertEquals(0.5f, lamp.color().y, 1e-5f);
        assertEquals(0.25f, lamp.color().z, 1e-5f);
        assertTrue(lamp.intensity() > 0);
        assertNull(ScenePlacement.prepare(null, scene).sun());
    }

    @Test
    void testPicking() throws IOException {
        ImportedScene scene = SceneImporter.read(writeScene(), message -> {});
        // The moved triangle spans x 1 to 2, y 0 to 1, at z 0; look at it from in front
        ScenePicker.Hit hit =
                ScenePicker.pick(scene, new Vector3d(1.2, 0.2, 10), new Vector3d(0, 0, -1));
        assertNotNull(hit);
        assertEquals("Moved", hit.node().name());
        assertEquals(10, hit.distance(), 1e-6);
        // The stretched one is at z 5 and twice as tall, so only it is there
        ScenePicker.Hit stretched =
                ScenePicker.pick(scene, new Vector3d(0.2, 1.5, 10), new Vector3d(0, 0, -1));
        assertNotNull(stretched);
        assertEquals("Stretched", stretched.node().name());
        assertEquals(5, stretched.distance(), 1e-6);
        // Nothing to the side
        assertNull(ScenePicker.pick(scene, new Vector3d(5, 5, 10), new Vector3d(0, 0, -1)));
    }

    @Test
    void testPreviewShapes() {
        for (PreviewShapes.Shape shape : PreviewShapes.Shape.values()) {
            ImportedScene scene = PreviewShapes.scene(shape);
            assertEquals(2, scene.meshes().size());
            assertEquals(2, scene.materials().size());
            for (ImportedScene.Mesh mesh : scene.meshes()) {
                assertOutwardAndUnit(mesh.data());
            }
        }
        assertEquals(
                (PreviewShapes.SPHERE_SEGMENTS + 1) * (PreviewShapes.SPHERE_RINGS + 1),
                PreviewShapes.sphere(1).getVertexCount());
        assertEquals(12, PreviewShapes.cube(1).getIndices().length / 3);
    }

    /**
     * Check every triangle winds counter-clockwise seen from where its normals point, and every
     * normal is a unit vector at right angles to its tangent, with the bitangent their cross.
     *
     * @param mesh The mesh.
     */
    private static void assertOutwardAndUnit(MeshData mesh) {
        final int stride = MeshData.VERTEX_SIZE_IN_FLOATS;
        final float[] v = mesh.getVertexData();
        for (int at = 0; at < v.length; at += stride) {
            Vector3f n = new Vector3f(v[at + 3], v[at + 4], v[at + 5]);
            Vector3f t = new Vector3f(v[at + 6], v[at + 7], v[at + 8]);
            Vector3f b = new Vector3f(v[at + 9], v[at + 10], v[at + 11]);
            assertEquals(1, n.length(), 1e-4f);
            assertEquals(0, n.dot(t), 1e-4f);
            assertTrue(n.cross(t, new Vector3f()).distance(b) < 1e-4f);
        }
        final int[] indices = mesh.getIndices();
        for (int i = 0; i < indices.length; i += 3) {
            Vector3f a = position(v, indices[i]);
            Vector3f e1 = position(v, indices[i + 1]).sub(a);
            Vector3f e2 = position(v, indices[i + 2]).sub(a);
            Vector3f face = e1.cross(e2);
            if (face.length() < 1e-9f) {
                // The sphere's poles have triangles with no area
                continue;
            }
            final int at = indices[i] * stride;
            Vector3f normal = new Vector3f(v[at + 3], v[at + 4], v[at + 5]);
            assertTrue(face.dot(normal) > 0, "Triangle " + i / 3 + " faces in");
        }
    }

    /**
     * A vertex's position.
     *
     * @param v The vertex data.
     * @param vertex The vertex.
     * @return Its position.
     */
    private static Vector3f position(float[] v, int vertex) {
        final int at = vertex * MeshData.VERTEX_SIZE_IN_FLOATS;
        return new Vector3f(v[at], v[at + 1], v[at + 2]);
    }
}
