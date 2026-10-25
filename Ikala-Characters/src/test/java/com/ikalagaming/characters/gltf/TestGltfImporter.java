package com.ikalagaming.characters.gltf;

import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.pose.Clip;
import com.ikalagaming.characters.pose.Pose;
import com.ikalagaming.characters.pose.PoseSolver;
import com.ikalagaming.characters.pose.Proportions;
import com.ikalagaming.characters.pose.Skeleton;
import com.ikalagaming.characters.registry.BuiltInContent;
import com.ikalagaming.characters.registry.CharacterRegistry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for reading characters from glTF: a small file built here, and the exported test art in the
 * asset editor's test content when it is there.
 *
 * @author Ches Burks
 */
class TestGltfImporter {
    /** How close floats must be. */
    private static final float EPSILON = 1e-4f;

    /** Where the exported test art is, relative to the plugin's folder. */
    private static final Path TEST_CONTENT =
            Path.of("../../Ikala-Asset-Editor/test_content/characters");

    /** Where the small file is written. */
    @TempDir Path temp;

    /** Builds a binary glTF file. */
    private static final class GlbBuilder {
        /** The JSON. */
        final JsonObject json = new JsonObject();

        /** The binary chunk. */
        final ByteArrayOutputStream binary = new ByteArrayOutputStream();

        GlbBuilder() {
            JsonObject asset = new JsonObject();
            asset.addProperty("version", "2.0");
            json.add("asset", asset);
            for (String name :
                    new String[] {
                        "nodes", "meshes", "skins", "accessors", "bufferViews", "animations"
                    }) {
                json.add(name, new JsonArray());
            }
        }

        /**
         * Add a float accessor.
         *
         * @param type Its type, like "VEC3".
         * @param values The values.
         * @return Its index.
         */
        int floats(String type, float... values) {
            ByteBuffer bytes =
                    ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (float value : values) {
                bytes.putFloat(value);
            }
            return accessor(
                    type, GltfFile.FLOAT, bytes.array(), values.length / GltfFile.components(type));
        }

        /**
         * Add an unsigned short accessor.
         *
         * @param type Its type.
         * @param values The values.
         * @return Its index.
         */
        int shorts(String type, int... values) {
            ByteBuffer bytes =
                    ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (int value : values) {
                bytes.putShort((short) value);
            }
            return accessor(
                    type,
                    GltfFile.UNSIGNED_SHORT,
                    bytes.array(),
                    values.length / GltfFile.components(type));
        }

        private int accessor(String type, int componentType, byte[] bytes, int count) {
            JsonObject view = new JsonObject();
            view.addProperty("buffer", 0);
            view.addProperty("byteOffset", binary.size());
            view.addProperty("byteLength", bytes.length);
            binary.writeBytes(bytes);
            while (binary.size() % 4 != 0) {
                binary.write(0);
            }
            json.getAsJsonArray("bufferViews").add(view);
            JsonObject accessor = new JsonObject();
            accessor.addProperty("bufferView", json.getAsJsonArray("bufferViews").size() - 1);
            accessor.addProperty("componentType", componentType);
            accessor.addProperty("count", count);
            accessor.addProperty("type", type);
            json.getAsJsonArray("accessors").add(accessor);
            return json.getAsJsonArray("accessors").size() - 1;
        }

        /**
         * Write the file.
         *
         * @param path Where.
         * @throws IOException If it can't be written.
         */
        void write(Path path) throws IOException {
            JsonArray buffers = new JsonArray();
            JsonObject buffer = new JsonObject();
            buffer.addProperty("byteLength", binary.size());
            buffers.add(buffer);
            json.add("buffers", buffers);
            byte[] text = json.toString().getBytes(StandardCharsets.UTF_8);
            int textLength = (text.length + 3) & ~3;
            byte[] bin = binary.toByteArray();
            ByteBuffer out =
                    ByteBuffer.allocate(12 + 8 + textLength + 8 + bin.length)
                            .order(ByteOrder.LITTLE_ENDIAN);
            out.putInt(0x46546C67).putInt(2).putInt(out.capacity());
            out.putInt(textLength).putInt(0x4E4F534A).put(text);
            for (int i = text.length; i < textLength; ++i) {
                out.put((byte) ' ');
            }
            out.putInt(bin.length).putInt(0x004E4942).put(bin);
            Files.write(path, out.array());
        }
    }

    /**
     * A node.
     *
     * @param name Its name.
     * @param translation Its translation, or null.
     * @param children Its children's indices.
     * @return The node's JSON.
     */
    private static JsonObject node(String name, float[] translation, int... children) {
        JsonObject node = new JsonObject();
        node.addProperty("name", name);
        if (translation != null) {
            JsonArray t = new JsonArray();
            for (float value : translation) {
                t.add(value);
            }
            node.add("translation", t);
        }
        if (children.length > 0) {
            JsonArray list = new JsonArray();
            for (int child : children) {
                list.add(child);
            }
            node.add("children", list);
        }
        return node;
    }

    /**
     * Write a small character: an armature node 5 m along z holding a root bone and a child bone 1
     * m above it, a triangle weighted to the child, and a clip that moves and turns the root.
     *
     * @param boneScale The child bone's scale.
     * @return The file.
     * @throws IOException If it can't be written.
     */
    private Path smallCharacter(float boneScale) throws IOException {
        GlbBuilder glb = new GlbBuilder();
        JsonArray nodes = glb.json.getAsJsonArray("nodes");
        nodes.add(node("armature", new float[] {0, 0, 5}, 1, 3));
        nodes.add(node("root", null, 2));
        JsonObject child = node("child", new float[] {0, 1, 0});
        if (boneScale != 1) {
            JsonArray scale = new JsonArray();
            scale.add(boneScale);
            scale.add(boneScale);
            scale.add(boneScale);
            child.add("scale", scale);
        }
        nodes.add(child);
        JsonObject meshNode = node("body", null);
        meshNode.addProperty("mesh", 0);
        meshNode.addProperty("skin", 0);
        nodes.add(meshNode);

        // The triangle in bind space, one unit up from the child, which sits at (0, 1, 5)
        int positions = glb.floats("VEC3", 0, 1, 0, 1, 1, 0, 0, 1, 1);
        int normals = glb.floats("VEC3", 0, 1, 0, 0, 1, 0, 0, 1, 0);
        // The first two vertices follow skin joint 0, the child; the last has no weights
        int joints = glb.shorts("VEC4", 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0);
        int weights = glb.floats("VEC4", 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0);
        int indices = glb.shorts("SCALAR", 0, 1, 2);
        JsonObject attributes = new JsonObject();
        attributes.addProperty("POSITION", positions);
        attributes.addProperty("NORMAL", normals);
        attributes.addProperty("JOINTS_0", joints);
        attributes.addProperty("WEIGHTS_0", weights);
        JsonObject primitive = new JsonObject();
        primitive.add("attributes", attributes);
        primitive.addProperty("indices", indices);
        JsonArray primitives = new JsonArray();
        primitives.add(primitive);
        JsonObject mesh = new JsonObject();
        mesh.addProperty("name", "body");
        mesh.add("primitives", primitives);
        glb.json.getAsJsonArray("meshes").add(mesh);

        // Bind pose: the child at (0, 2, 5), a meter higher than its rest, so baking must move
        // the triangle down a meter
        float[] inverseBinds = new float[32];
        // In the skin's joint order: the child, then the root
        new Matrix4f().translation(0, -2, -5).get(inverseBinds, 0);
        new Matrix4f().translation(0, 0, -5).get(inverseBinds, 16);
        int bindMatrices = glb.floats("MAT4", inverseBinds);
        JsonObject skin = new JsonObject();
        JsonArray skinJoints = new JsonArray();
        skinJoints.add(2);
        skinJoints.add(1);
        skin.add("joints", skinJoints);
        skin.addProperty("inverseBindMatrices", bindMatrices);
        glb.json.getAsJsonArray("skins").add(skin);

        Quaternionf turn = new Quaternionf().rotateY((float) Math.PI / 2);
        int times = glb.floats("SCALAR", 1, 2);
        int moves = glb.floats("VEC3", 0, 0, 0, 0, 0, 3);
        int turns = glb.floats("VEC4", 0, 0, 0, 1, turn.x, turn.y, turn.z, turn.w);
        JsonObject animation = new JsonObject();
        animation.addProperty("name", "walk");
        JsonArray samplers = new JsonArray();
        JsonArray channels = new JsonArray();
        int[][] parts = {{moves, 1}, {turns, 1}};
        String[] paths = {"translation", "rotation"};
        for (int i = 0; i < 2; ++i) {
            JsonObject sampler = new JsonObject();
            sampler.addProperty("input", times);
            sampler.addProperty("output", parts[i][0]);
            samplers.add(sampler);
            JsonObject channel = new JsonObject();
            channel.addProperty("sampler", i);
            JsonObject target = new JsonObject();
            target.addProperty("node", parts[i][1]);
            target.addProperty("path", paths[i]);
            channel.add("target", target);
            channels.add(channel);
        }
        animation.add("samplers", samplers);
        animation.add("channels", channels);
        glb.json.getAsJsonArray("animations").add(animation);

        Path path = temp.resolve("small.glb");
        glb.write(path);
        return path;
    }

    /** The skeleton, mesh and clip come out in model space, parents first. */
    @Test
    void testSmallCharacter() throws IOException {
        ImportedCharacter character = GltfImporter.read(smallCharacter(1));
        Assertions.assertEquals("small", character.name());
        // Parents first, though the skin lists the child first
        Assertions.assertEquals(
                List.of("root", "child"),
                character.rig().bones().stream().map(Rig.Bone::name).toList());
        Assertions.assertEquals(new Vector3f(0, 0, 5), character.rest().get("root").translation());

        ImportedCharacter.Part part = character.parts().get(0);
        // Skin joint 0 is the child, bone 1 in the rig; skin joint 1 is the root, bone 0
        Assertions.assertEquals(1, part.joints()[0]);
        Assertions.assertEquals(0, part.joints()[8]);
        // The child's bind was a meter above its rest and 5 m back along z, so its vertex
        // comes down a meter and stays at z 0
        Assertions.assertEquals(0, part.vertices()[1], EPSILON);
        Assertions.assertEquals(0, part.vertices()[2], EPSILON);
        // The third vertex, with no weights, follows its first joint, the root, whose bind and
        // rest agree, so it stays where it was
        Assertions.assertEquals(1, part.vertices()[28 + 1], EPSILON);
        Assertions.assertEquals(1, part.vertices()[28 + 2], EPSILON);

        ImportedCharacter.ClipData clip = character.clip("walk");
        Assertions.assertEquals(1, clip.duration(), EPSILON);
        Assertions.assertTrue(clip.looping());
        // The root's keys are moved by the armature above it
        Clip.TranslationTrack walk = clip.translations().get(0);
        Assertions.assertEquals(0, walk.times()[0], EPSILON);
        Assertions.assertEquals(5, walk.translations()[2], EPSILON);
        Assertions.assertEquals(8, walk.translations()[5], EPSILON);
    }

    /** At rest the palette is identity, so baked vertices draw where they were exported. */
    @Test
    void testRestPaletteMatchesBake() throws IOException {
        ImportedCharacter character = GltfImporter.read(smallCharacter(1));
        Skeleton skeleton = character.skeleton();
        PoseSolver solver = new PoseSolver(skeleton);
        Proportions neutral = Proportions.neutral(skeleton);
        solver.solve(new Pose(skeleton), neutral);
        float[] palette = new float[skeleton.size() * Skeleton.MATRIX_FLOATS];
        solver.writePalette(neutral, palette, 0);
        for (int bone = 0; bone < skeleton.size(); ++bone) {
            Assertions.assertTrue(
                    new Matrix4f().set(palette, bone * 16).equals(new Matrix4f(), EPSILON));
        }
    }

    /** Scaled bones are refused, with the bone named. */
    @Test
    void testScaledBone() throws IOException {
        Path path = smallCharacter(2);
        IllegalArgumentException thrown =
                Assertions.assertThrows(
                        IllegalArgumentException.class, () -> GltfImporter.read(path));
        Assertions.assertTrue(thrown.getMessage().contains("child"), thrown.getMessage());
    }

    /**
     * The mannequin export fits the built-in biped rig and plays its clips.
     *
     * @throws Exception If something can't be read.
     */
    @Test
    void testMannequin() throws Exception {
        Path file = TEST_CONTENT.resolve("mannequin/mannequin.glb");
        Assumptions.assumeTrue(Files.exists(file), "No mannequin export in the test content");
        CharacterRegistry registry = BuiltInContent.loadWith(List.of()).registry();
        Rig biped =
                registry.get(
                        Rig.class, com.ikalagaming.characters.data.Id.parse("ikala:rig/biped"));
        ImportedCharacter character = GltfImporter.read(file).onRig(biped);
        Assertions.assertEquals(15, character.parts().size());
        Assertions.assertNotNull(character.clip("idle"));
        ImportedCharacter.ClipData walk = character.clip("walk");
        Assertions.assertEquals(1, walk.duration(), 0.05);
        Assertions.assertEquals("root", walk.translations().get(0).bone());

        float top = Float.NEGATIVE_INFINITY;
        for (ImportedCharacter.Part part : character.parts()) {
            top = Math.max(top, part.max().y);
        }
        Assertions.assertEquals(1.95, top, 0.03);
        Skeleton skeleton = character.skeleton();
        Pose pose = new Pose(skeleton);
        walk.bind(skeleton).sample(0.25f, pose);
        PoseSolver solver = new PoseSolver(skeleton);
        solver.solve(pose, Proportions.neutral(skeleton));
        // A quarter of the way through the cycle, the root has walked about a quarter of 1.4 m;
        // the mannequin's keys are on whole frames, so not exactly
        Vector3f root = solver.model(0).getTranslation(new Vector3f());
        Assertions.assertEquals(0.35, root.length(), 0.03);
    }

    /**
     * The RPG character exports read with their own skeleton and every clip.
     *
     * @throws Exception If something can't be read.
     */
    @Test
    void testRpgCharacter() throws Exception {
        Path file = TEST_CONTENT.resolve("rpg_human/rpg_male.glb");
        Assumptions.assumeTrue(Files.exists(file), "No RPG export in the test content");
        ImportedCharacter character = GltfImporter.read(file);
        Assertions.assertEquals(43, character.rig().bones().size());
        Assertions.assertEquals("pelvis", character.rig().bones().get(0).name());
        // 32 meshes, four of them with a second material for a skirt, so a second part
        Assertions.assertEquals(36, character.parts().size());
        Assertions.assertEquals(47, character.clips().size());
        Assertions.assertFalse(character.images().isEmpty());
        Skeleton skeleton = character.skeleton();
        for (ImportedCharacter.ClipData clip : character.clips()) {
            clip.bind(skeleton);
        }
    }
}
