package com.ikalagaming.characters.gltf;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.pose.Clip;
import com.ikalagaming.characters.pose.Skeleton;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a skinned character from glTF: the first skin's joints become the rig, every mesh skinned
 * to it becomes a part, and every animation of its joints becomes a clip.
 *
 * <ul>
 *   <li>Model space is the file's scene space: Y up, in meters. The root bone's rest is its
 *       transform in the scene, so whatever is above it, like an armature node, is folded in.
 *   <li>Vertices are moved into model space at rest through the skin's inverse bind matrices, so
 *       the file's bind pose needn't match the bones' rest pose.
 *   <li>Bones mustn't be scaled; apply scales before exporting.
 *   <li>Clips keep every rotation track, and translation tracks only for bones that move, like the
 *       root. Scale tracks are ignored.
 *   <li>Materials keep their base color, normal, metal-roughness and emissive textures. Occlusion
 *       is kept only packed into the metal-roughness image's red channel. Normal maps need the
 *       file's tangents; without them, tangents point any way across the normal.
 * </ul>
 *
 * @author Ches Burks
 */
@Slf4j
public final class GltfImporter {
    /** How far a bone's scale may be from 1. */
    private static final float SCALE_TOLERANCE = 1e-3f;

    /** How far a translation key may be from the rest translation and still count as still. */
    private static final float STILL_TOLERANCE = 1e-4f;

    /** The shortest a clip can be, for single-key poses. */
    private static final float MIN_DURATION = 1e-3f;

    /** The namespace of rigs made from imported files. */
    public static final String RIG_NAMESPACE = "imported";

    /** The extension that scales a material's emissive factor past 1. */
    public static final String EMISSIVE_STRENGTH = "KHR_materials_emissive_strength";

    /** The glTF primitive mode for triangle lists. */
    private static final int TRIANGLES = 4;

    /** The file being read. */
    private final GltfFile file;

    /** Each node's parent, or -1. */
    private final int[] parents;

    /** Each node's transform relative to its parent. */
    private final Matrix4f[] locals;

    /** Each node's transform in the scene. */
    private final Matrix4f[] worlds;

    /**
     * Set up to read a file.
     *
     * @param file The file.
     */
    private GltfImporter(GltfFile file) {
        this.file = file;
        final int nodes = file.count("nodes");
        parents = new int[nodes];
        Arrays.fill(parents, -1);
        locals = new Matrix4f[nodes];
        worlds = new Matrix4f[nodes];
        for (int n = 0; n < nodes; ++n) {
            JsonObject node = file.get("nodes", n);
            locals[n] = localMatrix(node);
            for (JsonElement child : GltfFile.array(node, "children")) {
                parents[child.getAsInt()] = n;
            }
        }
        for (int n = 0; n < nodes; ++n) {
            world(n);
        }
    }

    /**
     * Read a character from a file.
     *
     * @param path The {@code .glb} or {@code .gltf} file.
     * @return The character.
     * @throws IOException If the file can't be read.
     * @throws IllegalArgumentException If it holds no usable skinned character.
     */
    public static ImportedCharacter read(@NonNull Path path) throws IOException {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return read(GltfFile.read(path), dot > 0 ? fileName.substring(0, dot) : fileName);
    }

    /**
     * Read a character from an already parsed file.
     *
     * @param file The file.
     * @param name What to call the character.
     * @return The character.
     * @throws IllegalArgumentException If it holds no usable skinned character.
     */
    public static ImportedCharacter read(@NonNull GltfFile file, @NonNull String name) {
        if (file.count("skins") == 0) {
            throw new IllegalArgumentException("The file has no skin to make a skeleton from");
        }
        return new GltfImporter(file).character(name);
    }

    /**
     * Work out a node's scene transform, and its parents' on the way.
     *
     * @param node The node.
     * @return The transform.
     */
    private Matrix4f world(int node) {
        if (worlds[node] == null) {
            worlds[node] =
                    parents[node] < 0
                            ? new Matrix4f(locals[node])
                            : new Matrix4f(world(parents[node])).mul(locals[node]);
        }
        return worlds[node];
    }

    /**
     * A node's transform relative to its parent.
     *
     * @param node The node's JSON.
     * @return The transform.
     */
    private static Matrix4f localMatrix(JsonObject node) {
        if (node.has("matrix")) {
            float[] values = floats(node.getAsJsonArray("matrix"));
            return new Matrix4f().set(values);
        }
        float[] t =
                node.has("translation") ? floats(node.getAsJsonArray("translation")) : new float[3];
        float[] r =
                node.has("rotation")
                        ? floats(node.getAsJsonArray("rotation"))
                        : new float[] {0, 0, 0, 1};
        float[] s =
                node.has("scale") ? floats(node.getAsJsonArray("scale")) : new float[] {1, 1, 1};
        return new Matrix4f()
                .translationRotateScale(t[0], t[1], t[2], r[0], r[1], r[2], r[3], s[0], s[1], s[2]);
    }

    /**
     * Read a JSON array of numbers.
     *
     * @param array The array.
     * @return The numbers.
     */
    private static float[] floats(JsonArray array) {
        float[] values = new float[array.size()];
        for (int i = 0; i < values.length; ++i) {
            values[i] = array.get(i).getAsFloat();
        }
        return values;
    }

    /**
     * Read everything.
     *
     * @param name What to call the character.
     * @return The character.
     */
    private ImportedCharacter character(String name) {
        JsonObject skin = file.get("skins", 0);
        int[] joints = intArray(skin.getAsJsonArray("joints"));
        Map<Integer, Integer> skinIndexOfNode = new HashMap<>();
        for (int i = 0; i < joints.length; ++i) {
            skinIndexOfNode.put(joints[i], i);
        }

        // Bones in parents-first order, from the one root
        List<Integer> roots = new ArrayList<>();
        for (int joint : joints) {
            if (parents[joint] < 0 || !skinIndexOfNode.containsKey(parents[joint])) {
                roots.add(joint);
            }
        }
        if (roots.size() != 1) {
            throw new IllegalArgumentException(
                    "The skeleton needs exactly one root bone, not " + roots.size());
        }
        List<Integer> order = new ArrayList<>();
        Deque<Integer> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            int node = pending.removeFirst();
            order.add(node);
            for (JsonElement child : GltfFile.array(file.get("nodes", node), "children")) {
                if (skinIndexOfNode.containsKey(child.getAsInt())) {
                    pending.addLast(child.getAsInt());
                }
            }
        }
        if (order.size() != joints.length) {
            throw new IllegalArgumentException("Some of the skin's joints aren't under its root");
        }
        Map<Integer, Integer> rigIndexOfNode = new HashMap<>();
        for (int i = 0; i < order.size(); ++i) {
            rigIndexOfNode.put(order.get(i), i);
        }
        int[] skinToRig = new int[joints.length];
        for (int i = 0; i < joints.length; ++i) {
            skinToRig[i] = rigIndexOfNode.get(joints[i]);
        }

        // Rest transforms, and model space rest matrices for baking vertices
        List<Rig.Bone> bones = new ArrayList<>();
        Map<String, Skeleton.Rest> rest = new LinkedHashMap<>();
        Matrix4f[] restModel = new Matrix4f[order.size()];
        for (int i = 0; i < order.size(); ++i) {
            int node = order.get(i);
            String boneName = nodeName(node, "bone");
            String parentName = i == 0 ? null : nodeName(parents[node], "bone");
            Matrix4f transform = i == 0 ? worlds[node] : locals[node];
            Vector3f scale = transform.getScale(new Vector3f());
            if (Math.abs(scale.x - 1) > SCALE_TOLERANCE
                    || Math.abs(scale.y - 1) > SCALE_TOLERANCE
                    || Math.abs(scale.z - 1) > SCALE_TOLERANCE) {
                throw new IllegalArgumentException(
                        "The bone "
                                + boneName
                                + " is scaled by "
                                + scale
                                + "; apply scales before exporting");
            }
            Vector3f translation = transform.getTranslation(new Vector3f());
            Quaternionf rotation = transform.getNormalizedRotation(new Quaternionf());
            rest.put(boneName, new Skeleton.Rest(translation, rotation));
            bones.add(new Rig.Bone(boneName, parentName, Rig.BoneKind.DEFORM));
            Matrix4f local = new Matrix4f().translationRotate(translation, rotation);
            restModel[i] =
                    i == 0
                            ? local
                            : new Matrix4f(restModel[rigIndexOfNode.get(parents[node])]).mul(local);
        }
        Rig rig = new Rig(new Id(RIG_NAMESPACE, "rig/" + sanitize(name)), List.copyOf(bones));

        // Each joint's matrix from the file's bind pose to model space at rest
        Matrix4f[] bindToRest = new Matrix4f[joints.length];
        float[] inverseBinds =
                skin.has("inverseBindMatrices")
                        ? file.floats(skin.get("inverseBindMatrices").getAsInt())
                        : null;
        for (int i = 0; i < joints.length; ++i) {
            Matrix4f inverseBind =
                    inverseBinds == null
                            ? new Matrix4f()
                            : new Matrix4f().set(inverseBinds, i * 16);
            bindToRest[i] = new Matrix4f(restModel[skinToRig[i]]).mul(inverseBind);
        }

        List<ImportedCharacter.Part> parts = new ArrayList<>();
        for (int n = 0; n < file.count("nodes"); ++n) {
            JsonObject node = file.get("nodes", n);
            if (!node.has("mesh") || !node.has("skin") || node.get("skin").getAsInt() != 0) {
                if (node.has("mesh")) {
                    log.warn(
                            "Skipping the mesh on {}, which isn't skinned to the skeleton",
                            nodeName(n, "node"));
                }
                continue;
            }
            JsonObject mesh = file.get("meshes", node.get("mesh").getAsInt());
            JsonArray primitives = mesh.getAsJsonArray("primitives");
            String meshName =
                    mesh.has("name") ? mesh.get("name").getAsString() : nodeName(n, "mesh");
            for (int p = 0; p < primitives.size(); ++p) {
                String partName = primitives.size() == 1 ? meshName : meshName + "." + p;
                parts.add(
                        part(partName, primitives.get(p).getAsJsonObject(), skinToRig, bindToRest));
            }
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("No mesh is skinned to the skeleton");
        }

        List<ImportedCharacter.ClipData> clips = new ArrayList<>();
        for (int a = 0; a < file.count("animations"); ++a) {
            ImportedCharacter.ClipData clip = clip(a, order, rigIndexOfNode, rest);
            if (clip != null) {
                clips.add(clip);
            }
        }
        return new ImportedCharacter(
                name, rig, rest, List.copyOf(parts), List.copyOf(clips), materials(), images());
    }

    /**
     * Read one skinned triangle list as a part, moved into model space at rest.
     *
     * @param name The part's name.
     * @param primitive The primitive's JSON.
     * @param skinToRig Rig bone index by skin joint index.
     * @param bindToRest Each skin joint's matrix from bind pose to model space at rest.
     * @return The part.
     */
    private ImportedCharacter.Part part(
            String name, JsonObject primitive, int[] skinToRig, Matrix4f[] bindToRest) {
        if (primitive.has("mode") && primitive.get("mode").getAsInt() != TRIANGLES) {
            throw new IllegalArgumentException("The mesh " + name + " isn't made of triangles");
        }
        JsonObject attributes = primitive.getAsJsonObject("attributes");
        for (String needed : new String[] {"POSITION", "NORMAL", "JOINTS_0", "WEIGHTS_0"}) {
            if (!attributes.has(needed)) {
                throw new IllegalArgumentException("The mesh " + name + " has no " + needed);
            }
        }
        float[] positions = file.floats(attributes.get("POSITION").getAsInt());
        float[] normals = file.floats(attributes.get("NORMAL").getAsInt());
        float[] tangents =
                attributes.has("TANGENT")
                        ? file.floats(attributes.get("TANGENT").getAsInt())
                        : null;
        float[] uvs =
                attributes.has("TEXCOORD_0")
                        ? file.floats(attributes.get("TEXCOORD_0").getAsInt())
                        : null;
        int[] skinJoints = file.ints(attributes.get("JOINTS_0").getAsInt());
        float[] weights = file.floats(attributes.get("WEIGHTS_0").getAsInt());
        final int count = positions.length / 3;
        if (normals.length != count * 3
                || skinJoints.length != count * 4
                || weights.length != count * 4) {
            throw new IllegalArgumentException("The mesh " + name + " has mismatched attributes");
        }

        float[] vertices = new float[count * 14];
        int[] joints = new int[count * 4];
        Matrix4f blend = new Matrix4f();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        Vector3f tangent = new Vector3f();
        Vector3f bitangent = new Vector3f();
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        for (int v = 0; v < count; ++v) {
            float total = 0;
            for (int b = 0; b < 4; ++b) {
                int skinJoint = skinJoints[v * 4 + b];
                if (skinJoint < 0 || skinJoint >= skinToRig.length) {
                    throw new IllegalArgumentException(
                            "The mesh "
                                    + name
                                    + " names the joint "
                                    + skinJoint
                                    + ", past the skin's");
                }
                joints[v * 4 + b] = skinToRig[skinJoint];
                total += Math.max(0, weights[v * 4 + b]);
            }
            if (total > 0) {
                // The weighted sum of the joints' affine matrices, with the weights scaled to 1
                blend.zero();
                for (int b = 0; b < 4; ++b) {
                    float weight = weights[v * 4 + b];
                    if (weight > 0) {
                        blend.fma4x3(bindToRest[skinJoints[v * 4 + b]], weight / total);
                    }
                }
                blend.m33(1);
            } else {
                blend.set(bindToRest[skinJoints[v * 4]]);
            }
            blend.transformPosition(
                    positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2], position);
            blend.transformDirection(normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2], normal)
                    .normalize();
            float handedness = 1;
            if (tangents != null) {
                blend.transformDirection(
                                tangents[v * 4], tangents[v * 4 + 1], tangents[v * 4 + 2], tangent)
                        .normalize();
                handedness = tangents[v * 4 + 3] < 0 ? -1 : 1;
            } else {
                // Any direction across the normal, since there is no normal map to follow
                Vector3f across =
                        Math.abs(normal.y) < 0.99f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
                normal.cross(across, tangent).normalize();
            }
            normal.cross(tangent, bitangent).mul(handedness);
            int base = v * 14;
            vertices[base] = position.x;
            vertices[base + 1] = position.y;
            vertices[base + 2] = position.z;
            vertices[base + 3] = normal.x;
            vertices[base + 4] = normal.y;
            vertices[base + 5] = normal.z;
            vertices[base + 6] = tangent.x;
            vertices[base + 7] = tangent.y;
            vertices[base + 8] = tangent.z;
            vertices[base + 9] = bitangent.x;
            vertices[base + 10] = bitangent.y;
            vertices[base + 11] = bitangent.z;
            vertices[base + 12] = uvs == null ? 0 : uvs[v * 2];
            vertices[base + 13] = uvs == null ? 0 : uvs[v * 2 + 1];
            min.min(position);
            max.max(position);
        }

        int[] indices;
        if (primitive.has("indices")) {
            indices = file.ints(primitive.get("indices").getAsInt());
        } else {
            indices = new int[count];
            for (int i = 0; i < count; ++i) {
                indices[i] = i;
            }
        }
        int material = primitive.has("material") ? primitive.get("material").getAsInt() : -1;
        return new ImportedCharacter.Part(
                name, vertices, joints, weights, indices, material, min, max);
    }

    /**
     * Read one animation's tracks for the skeleton's bones.
     *
     * @param animation The animation's index.
     * @param order The bones' nodes, in rig order.
     * @param rigIndexOfNode Rig bone index by node.
     * @param rest Each bone's rest transform.
     * @return The clip, or null if it moves none of the skeleton's bones.
     */
    private ImportedCharacter.ClipData clip(
            int animation,
            List<Integer> order,
            Map<Integer, Integer> rigIndexOfNode,
            Map<String, Skeleton.Rest> rest) {
        JsonObject json = file.get("animations", animation);
        String clipName = json.has("name") ? json.get("name").getAsString() : "clip " + animation;
        JsonArray samplers = json.getAsJsonArray("samplers");
        List<Clip.RotationTrack> rotations = new ArrayList<>();
        List<Clip.TranslationTrack> translations = new ArrayList<>();
        float start = Float.POSITIVE_INFINITY;
        float end = Float.NEGATIVE_INFINITY;
        int rootNode = order.get(0);
        // The root's keys are relative to whatever is above it, which its rest folds in
        Matrix4f above = parents[rootNode] < 0 ? new Matrix4f() : worlds[parents[rootNode]];
        Quaternionf aboveRotation = above.getNormalizedRotation(new Quaternionf());

        List<Object[]> raw = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("channels")) {
            JsonObject channel = element.getAsJsonObject();
            JsonObject target = channel.getAsJsonObject("target");
            if (!target.has("node") || !rigIndexOfNode.containsKey(target.get("node").getAsInt())) {
                continue;
            }
            String path = target.get("path").getAsString();
            if (!"rotation".equals(path) && !"translation".equals(path)) {
                continue;
            }
            JsonObject sampler = samplers.get(channel.get("sampler").getAsInt()).getAsJsonObject();
            float[] times = file.floats(sampler.get("input").getAsInt());
            float[] values = file.floats(sampler.get("output").getAsInt());
            int size = "rotation".equals(path) ? 4 : 3;
            if (sampler.has("interpolation")
                    && "CUBICSPLINE".equals(sampler.get("interpolation").getAsString())) {
                // Tangent, value, tangent per key; keep the values
                float[] kept = new float[times.length * size];
                for (int k = 0; k < times.length; ++k) {
                    System.arraycopy(values, (k * 3 + 1) * size, kept, k * size, size);
                }
                values = kept;
            }
            if (times.length == 0 || values.length != times.length * size) {
                continue;
            }
            start = Math.min(start, times[0]);
            end = Math.max(end, times[times.length - 1]);
            raw.add(new Object[] {target.get("node").getAsInt(), path, times, values});
        }
        if (raw.isEmpty()) {
            return null;
        }

        Vector3f scratch = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        for (Object[] track : raw) {
            int node = (Integer) track[0];
            String path = (String) track[1];
            float[] times = ((float[]) track[2]).clone();
            float[] values = ((float[]) track[3]).clone();
            for (int k = 0; k < times.length; ++k) {
                times[k] -= start;
            }
            times = increasing(times);
            String bone = nodeName(node, "bone");
            boolean root = node == rootNode;
            if ("rotation".equals(path)) {
                if (root) {
                    for (int k = 0; k < times.length; ++k) {
                        rotation.set(
                                values[k * 4],
                                values[k * 4 + 1],
                                values[k * 4 + 2],
                                values[k * 4 + 3]);
                        aboveRotation.mul(rotation, rotation).normalize();
                        values[k * 4] = rotation.x;
                        values[k * 4 + 1] = rotation.y;
                        values[k * 4 + 2] = rotation.z;
                        values[k * 4 + 3] = rotation.w;
                    }
                }
                rotations.add(
                        new Clip.RotationTrack(
                                bone, times, Arrays.copyOf(values, times.length * 4)));
            } else {
                if (root) {
                    for (int k = 0; k < times.length; ++k) {
                        above.transformPosition(
                                values[k * 3], values[k * 3 + 1], values[k * 3 + 2], scratch);
                        values[k * 3] = scratch.x;
                        values[k * 3 + 1] = scratch.y;
                        values[k * 3 + 2] = scratch.z;
                    }
                }
                if (moves(values, times.length, rest.get(bone).translation())) {
                    translations.add(
                            new Clip.TranslationTrack(
                                    bone, times, Arrays.copyOf(values, times.length * 3)));
                }
            }
        }
        String lower = clipName.toLowerCase(Locale.ROOT);
        boolean looping = !lower.contains("death") && !lower.contains("die");
        return new ImportedCharacter.ClipData(
                clipName, Math.max(end - start, MIN_DURATION), looping, rotations, translations);
    }

    /**
     * Drop keys whose time isn't after the one before, which some exporters repeat.
     *
     * @param times The times.
     * @return The times, strictly increasing; the values of dropped keys are ignored, since only as
     *     many values as times are kept, which assumes repeats come only at the end.
     */
    private static float[] increasing(float[] times) {
        int count = 1;
        while (count < times.length && times[count] > times[count - 1]) {
            ++count;
        }
        return count == times.length ? times : Arrays.copyOf(times, count);
    }

    /**
     * Whether a translation track ever leaves the rest translation.
     *
     * @param values The keys.
     * @param keys How many keys.
     * @param restTranslation The rest translation.
     * @return True if it moves.
     */
    private static boolean moves(float[] values, int keys, org.joml.Vector3fc restTranslation) {
        for (int k = 0; k < keys; ++k) {
            if (Math.abs(values[k * 3] - restTranslation.x()) > STILL_TOLERANCE
                    || Math.abs(values[k * 3 + 1] - restTranslation.y()) > STILL_TOLERANCE
                    || Math.abs(values[k * 3 + 2] - restTranslation.z()) > STILL_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Read the materials.
     *
     * @return The materials, in file order.
     */
    private List<ImportedCharacter.MaterialData> materials() {
        List<ImportedCharacter.MaterialData> materials = new ArrayList<>();
        for (int m = 0; m < file.count("materials"); ++m) {
            JsonObject material = file.get("materials", m);
            JsonObject pbr =
                    material.has("pbrMetallicRoughness")
                            ? material.getAsJsonObject("pbrMetallicRoughness")
                            : new JsonObject();
            float[] color =
                    pbr.has("baseColorFactor")
                            ? floats(pbr.getAsJsonArray("baseColorFactor"))
                            : new float[] {1, 1, 1, 1};
            int orm = textureImage(pbr, "metallicRoughnessTexture");
            int occlusion = textureImage(material, "occlusionTexture");
            if (occlusion >= 0 && occlusion != orm) {
                // The renderer takes occlusion only packed with roughness and metalness
                log.debug("Material {} has its occlusion in a separate image, left out", m);
            }
            float[] emissive =
                    material.has("emissiveFactor")
                            ? floats(material.getAsJsonArray("emissiveFactor"))
                            : new float[3];
            JsonObject extensions =
                    material.has("extensions")
                            ? material.getAsJsonObject("extensions")
                            : new JsonObject();
            if (extensions.has(EMISSIVE_STRENGTH)) {
                JsonObject strength = extensions.getAsJsonObject(EMISSIVE_STRENGTH);
                float scale =
                        strength.has("emissiveStrength")
                                ? strength.get("emissiveStrength").getAsFloat()
                                : 1;
                for (int c = 0; c < emissive.length; ++c) {
                    emissive[c] *= scale;
                }
            }
            materials.add(
                    new ImportedCharacter.MaterialData(
                            material.has("name")
                                    ? material.get("name").getAsString()
                                    : "material " + m,
                            color,
                            textureImage(pbr, "baseColorTexture"),
                            pbr.has("metallicFactor") ? pbr.get("metallicFactor").getAsFloat() : 1,
                            pbr.has("roughnessFactor")
                                    ? pbr.get("roughnessFactor").getAsFloat()
                                    : 1,
                            -1,
                            textureImage(material, "normalTexture"),
                            orm,
                            orm >= 0 && occlusion == orm,
                            textureImage(material, "emissiveTexture"),
                            emissive));
        }
        return materials;
    }

    /**
     * The image a material's texture reference uses.
     *
     * @param owner The object holding the reference, like the material or its
     *     pbrMetallicRoughness.
     * @param field The reference's field, like "normalTexture".
     * @return The image's index, or -1 if there is no such texture or it has no image.
     */
    private int textureImage(JsonObject owner, String field) {
        if (!owner.has(field)) {
            return -1;
        }
        JsonObject texture =
                file.get("textures", owner.getAsJsonObject(field).get("index").getAsInt());
        return texture.has("source") ? texture.get("source").getAsInt() : -1;
    }

    /**
     * Read the images, embedded or beside the file.
     *
     * @return The images, in file order.
     */
    private List<ImportedCharacter.ImageData> images() {
        List<ImportedCharacter.ImageData> images = new ArrayList<>();
        for (int i = 0; i < file.count("images"); ++i) {
            JsonObject image = file.get("images", i);
            String name = image.has("name") ? image.get("name").getAsString() : "image " + i;
            String mime = image.has("mimeType") ? image.get("mimeType").getAsString() : null;
            byte[] bytes;
            try {
                bytes =
                        image.has("bufferView")
                                ? file.bufferViewBytes(image.get("bufferView").getAsInt())
                                : file.uriBytes(image.get("uri").getAsString());
            } catch (IOException e) {
                log.warn("Couldn't read the image {}: {}", name, e.getMessage());
                bytes = new byte[0];
            }
            images.add(new ImportedCharacter.ImageData(name, mime, bytes));
        }
        return images;
    }

    /**
     * A node's name.
     *
     * @param node The node.
     * @param what What it is, for a made-up name.
     * @return Its name, or one made from its index.
     */
    private String nodeName(int node, String what) {
        JsonObject json = file.get("nodes", node);
        return json.has("name") ? json.get("name").getAsString() : what + "_" + node;
    }

    /**
     * Read a JSON array of integers.
     *
     * @param array The array.
     * @return The integers.
     */
    private static int[] intArray(JsonArray array) {
        int[] values = new int[array.size()];
        for (int i = 0; i < values.length; ++i) {
            values[i] = array.get(i).getAsInt();
        }
        return values;
    }

    /**
     * Turn a name into an ID path segment.
     *
     * @param name The name.
     * @return Lowercase letters, digits, _ and -.
     */
    static String sanitize(String name) {
        String cleaned = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return cleaned.isEmpty() ? "unnamed" : cleaned;
    }
}
