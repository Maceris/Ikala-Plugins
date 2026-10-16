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
                  "nodes": [
                    {"name": "Root", "children": [1, 2]},
                    {"name": "Moved", "mesh": 0, "translation": [1, 0, 0]},
                    {"name": "Stretched", "mesh": 0, "translation": [0, 0, 5],
                     "scale": [1, 2, 1]}
                  ],
                  "meshes": [{"name": "Triangle", "primitives": [
                    {"attributes": {"POSITION": 0}, "indices": 1, "material": 0}]}],
                  "materials": [{"name": "Brick", "pbrMetallicRoughness": {
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
        // Found by name, ignoring case, in the Textures folder
        ImportedScene.TextureRef base = material.textures().get(TextureSlot.BASE_COLOR);
        assertNotNull(base);
        assertEquals(folder.resolve("Textures").resolve("Brick_BaseColor.PNG"), base.found());
    }

    @Test
    void testPlacementInstancesAndBakes() throws IOException {
        ImportedScene scene = SceneImporter.read(writeScene(), message -> {});
        ScenePlacement placement = ScenePlacement.prepare(null, scene);
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
}
