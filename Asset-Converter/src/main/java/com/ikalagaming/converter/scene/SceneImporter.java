package com.ikalagaming.converter.scene;

import com.ikalagaming.converter.scene.ImportedScene.ImportedMaterial;
import com.ikalagaming.converter.scene.ImportedScene.Mesh;
import com.ikalagaming.converter.scene.ImportedScene.Node;
import com.ikalagaming.converter.scene.ImportedScene.TextureRef;
import com.ikalagaming.converter.scene.ImportedScene.TextureSlot;
import com.ikalagaming.graphics.graph.MeshData;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Reads model files with assimp into an {@link ImportedScene}, keeping the node tree, so a mesh
 * many nodes use is stored once and can be drawn as instances. Works for single meshes and whole
 * scenes alike, from OBJ to FBX, glTF and the rest assimp reads.
 *
 * <p>The file's unit and up axis, where it says them (FBX does), are applied at the root, so the
 * scene comes out in meters with y up. Textures are found by file name next to the model or in a
 * {@code Textures} folder beside it, ignoring case, since files often name paths on the machine
 * they were made on.
 *
 * <p>Safe from any thread; each call reads on its own.
 */
@Slf4j
public final class SceneImporter {
    /** The processing assimp does while reading. */
    static final int FLAGS =
            Assimp.aiProcess_Triangulate
                    | Assimp.aiProcess_GenSmoothNormals
                    | Assimp.aiProcess_CalcTangentSpace
                    | Assimp.aiProcess_JoinIdenticalVertices
                    // Drops points and lines, with the property below, which have nothing to light
                    | Assimp.aiProcess_SortByPType
                    | Assimp.aiProcess_GenBoundingBoxes
                    // Texture coordinates start at the bottom in files, at the top for the GPU
                    | Assimp.aiProcess_FlipUVs;

    /** FBX files say their unit as centimeters per unit. */
    private static final double CENTIMETERS_PER_METER = 100;

    /** Floats per vertex in mesh data. */
    private static final int STRIDE = MeshData.VERTEX_SIZE_IN_FLOATS;

    /** Assimp metadata type for a 32 bit int. */
    private static final int META_INT32 = 1;

    /** Assimp metadata type for a float. */
    private static final int META_FLOAT = 3;

    /** Assimp metadata type for a double. */
    private static final int META_DOUBLE = 4;

    /** The assimp texture types read for each slot, in order of preference. */
    private static final Map<TextureSlot, int[]> TEXTURE_TYPES = new EnumMap<>(TextureSlot.class);

    static {
        TEXTURE_TYPES.put(
                TextureSlot.BASE_COLOR,
                new int[] {Assimp.aiTextureType_BASE_COLOR, Assimp.aiTextureType_DIFFUSE});
        TEXTURE_TYPES.put(
                TextureSlot.NORMAL,
                new int[] {Assimp.aiTextureType_NORMALS, Assimp.aiTextureType_NORMAL_CAMERA});
        TEXTURE_TYPES.put(TextureSlot.SPECULAR, new int[] {Assimp.aiTextureType_SPECULAR});
        TEXTURE_TYPES.put(
                TextureSlot.EMISSIVE,
                new int[] {Assimp.aiTextureType_EMISSIVE, Assimp.aiTextureType_EMISSION_COLOR});
        TEXTURE_TYPES.put(TextureSlot.OPACITY, new int[] {Assimp.aiTextureType_OPACITY});
        TEXTURE_TYPES.put(
                TextureSlot.ROUGHNESS, new int[] {Assimp.aiTextureType_DIFFUSE_ROUGHNESS});
        TEXTURE_TYPES.put(TextureSlot.METALNESS, new int[] {Assimp.aiTextureType_METALNESS});
        TEXTURE_TYPES.put(
                TextureSlot.OCCLUSION,
                new int[] {Assimp.aiTextureType_AMBIENT_OCCLUSION, Assimp.aiTextureType_LIGHTMAP});
    }

    /** Not to be made. */
    private SceneImporter() {}

    /**
     * Read a model file.
     *
     * @param file The file.
     * @param progress Told what is happening, for a status line. Called on the reading thread.
     * @return The scene.
     * @throws IOException If the file can't be read.
     */
    public static ImportedScene read(@NonNull Path file, @NonNull Consumer<String> progress)
            throws IOException {
        final long start = System.nanoTime();
        if (!Files.isRegularFile(file)) {
            throw new IOException("No such file " + file);
        }
        progress.accept("Reading " + file.getFileName() + " with assimp...");
        final AIPropertyStore properties = Assimp.aiCreatePropertyStore();
        final AIScene aiScene;
        try {
            // Pivots become extra nodes otherwise, cluttering the tree for nothing
            Assimp.aiSetImportPropertyInteger(
                    properties, Assimp.AI_CONFIG_IMPORT_FBX_PRESERVE_PIVOTS, 0);
            Assimp.aiSetImportPropertyInteger(
                    properties,
                    Assimp.AI_CONFIG_PP_SBP_REMOVE,
                    Assimp.aiPrimitiveType_POINT | Assimp.aiPrimitiveType_LINE);
            aiScene =
                    Assimp.aiImportFileExWithProperties(
                            file.toAbsolutePath().toString(), FLAGS, null, properties);
        } finally {
            Assimp.aiReleasePropertyStore(properties);
        }
        if (aiScene == null) {
            throw new IOException(
                    "Can't read " + file.getFileName() + ": " + Assimp.aiGetErrorString());
        }
        try {
            return convert(file, aiScene, progress, start);
        } finally {
            Assimp.aiReleaseImport(aiScene);
        }
    }

    /**
     * Turn what assimp read into a scene.
     *
     * @param file The file.
     * @param aiScene What assimp read.
     * @param progress Told what is happening.
     * @param start When reading started, from {@link System#nanoTime()}.
     * @return The scene.
     * @throws IOException If there is no root node.
     */
    private static ImportedScene convert(
            Path file, AIScene aiScene, Consumer<String> progress, long start) throws IOException {
        final List<String> warnings = new ArrayList<>();
        final AIMetaData metadata = aiScene.mMetaData();
        final double unitCentimeters = number(metadata, "UnitScaleFactor", CENTIMETERS_PER_METER);
        final double unitScale = unitCentimeters / CENTIMETERS_PER_METER;
        final int upAxis = (int) number(metadata, "UpAxis", 1);
        final int upSign = (int) number(metadata, "UpAxisSign", 1);
        if (upAxis == 0) {
            warnings.add("The file says x is up, which isn't handled; it is read as y up");
        }

        final TextureFinder finder = new TextureFinder(file.toAbsolutePath().getParent());
        final List<ImportedMaterial> materials = new ArrayList<>();
        final PointerBuffer aiMaterials = aiScene.mMaterials();
        for (int i = 0; aiMaterials != null && i < aiScene.mNumMaterials(); ++i) {
            materials.add(material(AIMaterial.create(aiMaterials.get(i)), i, finder, warnings));
        }
        if (materials.isEmpty()) {
            materials.add(
                    new ImportedMaterial(
                            "default",
                            new Vector4f(1),
                            Float.NaN,
                            Float.NaN,
                            new Vector3f(),
                            Map.of()));
        }

        final List<Mesh> meshes = new ArrayList<>();
        final int meshCount = aiScene.mNumMeshes();
        final PointerBuffer aiMeshes = aiScene.mMeshes();
        for (int i = 0; aiMeshes != null && i < meshCount; ++i) {
            if (i % 256 == 0) {
                progress.accept("Converting meshes, " + i + " of " + meshCount + "...");
            }
            final AIMesh aiMesh = AIMesh.create(aiMeshes.get(i));
            final int material = Math.clamp(aiMesh.mMaterialIndex(), 0, materials.size() - 1);
            meshes.add(new Mesh(name(aiMesh.mName(), "mesh " + i), mesh(aiMesh), material));
        }

        final AINode aiRoot = aiScene.mRootNode();
        if (aiRoot == null) {
            throw new IOException(file.getFileName() + " has no nodes");
        }
        final Matrix4d correction = new Matrix4d().scale(unitScale);
        if (upAxis == 2) {
            // Z up to y up: what was +z goes to +y, and +y to -z
            correction.rotateX(-Math.PI / 2 * upSign);
        }
        final Node root = node(aiRoot, null, correction, meshes, warnings);
        final long millis = (System.nanoTime() - start) / 1_000_000;
        return new ImportedScene(
                file, meshes, materials, root, unitScale, upAxis, warnings, millis);
    }

    /**
     * Read a number from the scene's metadata.
     *
     * @param metadata The metadata, which may be missing.
     * @param key The key.
     * @param fallback What to use if it isn't there or isn't a number.
     * @return The number.
     */
    static double number(@Nullable AIMetaData metadata, @NonNull String key, double fallback) {
        if (metadata == null) {
            return fallback;
        }
        final AIString.Buffer keys = metadata.mKeys();
        final AIMetaDataEntry.Buffer values = metadata.mValues();
        for (int i = 0; i < metadata.mNumProperties(); ++i) {
            if (!key.equals(keys.get(i).dataString())) {
                continue;
            }
            final AIMetaDataEntry entry = values.get(i);
            final ByteBuffer data = entry.mData(Double.BYTES);
            return switch (entry.mType()) {
                case META_INT32 -> data.getInt(0);
                case META_FLOAT -> data.getFloat(0);
                case META_DOUBLE -> data.getDouble(0);
                default -> fallback;
            };
        }
        return fallback;
    }

    /**
     * Read a node and its children.
     *
     * @param aiNode The node.
     * @param parent Its parent, or null for the root.
     * @param parentWorld The parent's world transform, or the root's correction.
     * @param meshes The scene's meshes, to check indices against.
     * @param warnings Where to note problems.
     * @return The node.
     */
    private static Node node(
            AINode aiNode,
            @Nullable Node parent,
            Matrix4dc parentWorld,
            List<Mesh> meshes,
            List<String> warnings) {
        final IntBuffer aiMeshIndices = aiNode.mMeshes();
        final int count = aiMeshIndices == null ? 0 : aiNode.mNumMeshes();
        final int[] meshIndices = new int[count];
        int kept = 0;
        for (int i = 0; i < count; ++i) {
            final int index = aiMeshIndices.get(i);
            if (index >= 0 && index < meshes.size() && meshes.get(index).triangles() > 0) {
                meshIndices[kept++] = index;
            }
        }
        final Node node =
                new Node(
                        name(aiNode.mName(), "node"),
                        matrix(aiNode.mTransformation()),
                        parent,
                        parentWorld,
                        java.util.Arrays.copyOf(meshIndices, kept));
        final PointerBuffer children = aiNode.mChildren();
        for (int i = 0; children != null && i < aiNode.mNumChildren(); ++i) {
            node.addChild(
                    node(AINode.create(children.get(i)), node, node.world(), meshes, warnings));
        }
        return node;
    }

    /**
     * A name from the file, or a fallback for an empty one.
     *
     * @param name The name.
     * @param fallback What to use if it is empty.
     * @return The name.
     */
    private static String name(AIString name, String fallback) {
        final String text = name.dataString();
        return text.isEmpty() ? fallback : text;
    }

    /**
     * Convert an assimp matrix, which is row major.
     *
     * @param m The matrix.
     * @return The same transform as a JOML matrix.
     */
    static Matrix4d matrix(@NonNull AIMatrix4x4 m) {
        return new Matrix4d(
                m.a1(), m.b1(), m.c1(), m.d1(), m.a2(), m.b2(), m.c2(), m.d2(), m.a3(), m.b3(),
                m.c3(), m.d3(), m.a4(), m.b4(), m.c4(), m.d4());
    }

    /**
     * Convert a mesh to mesh data: position, normal, tangent, bitangent and texture coordinates per
     * vertex. A mesh without texture coordinates gets made up tangents.
     *
     * @param aiMesh The mesh, triangulated.
     * @return The mesh data, with no material set.
     */
    private static MeshData mesh(AIMesh aiMesh) {
        final int vertexCount = aiMesh.mNumVertices();
        final AIVector3D.Buffer positions = aiMesh.mVertices();
        final AIVector3D.Buffer normals = aiMesh.mNormals();
        final AIVector3D.Buffer tangents = aiMesh.mTangents();
        final AIVector3D.Buffer bitangents = aiMesh.mBitangents();
        final AIVector3D.Buffer uvs = aiMesh.mTextureCoords(0);
        final float[] vertices = new float[vertexCount * STRIDE];
        final Vector3f normal = new Vector3f();
        final Vector3f tangent = new Vector3f();
        final Vector3f bitangent = new Vector3f();
        for (int v = 0; v < vertexCount; ++v) {
            final int at = v * STRIDE;
            final AIVector3D position = positions.get(v);
            vertices[at] = position.x();
            vertices[at + 1] = position.y();
            vertices[at + 2] = position.z();
            if (normals != null) {
                final AIVector3D n = normals.get(v);
                normal.set(n.x(), n.y(), n.z());
            } else {
                normal.set(0, 1, 0);
            }
            if (tangents != null && bitangents != null) {
                final AIVector3D t = tangents.get(v);
                final AIVector3D b = bitangents.get(v);
                tangent.set(t.x(), t.y(), t.z());
                bitangent.set(b.x(), b.y(), b.z());
            } else {
                // Any direction at right angles will do without texture coordinates
                tangent.set(
                        Math.abs(normal.x) < 0.9f ? 1 : 0, Math.abs(normal.x) < 0.9f ? 0 : 1, 0);
                normal.cross(tangent, bitangent).normalize();
                bitangent.cross(normal, tangent).normalize();
            }
            vertices[at + 3] = normal.x;
            vertices[at + 4] = normal.y;
            vertices[at + 5] = normal.z;
            vertices[at + 6] = tangent.x;
            vertices[at + 7] = tangent.y;
            vertices[at + 8] = tangent.z;
            vertices[at + 9] = bitangent.x;
            vertices[at + 10] = bitangent.y;
            vertices[at + 11] = bitangent.z;
            if (uvs != null) {
                final AIVector3D uv = uvs.get(v);
                vertices[at + 12] = uv.x();
                vertices[at + 13] = uv.y();
            }
        }

        final AIFace.Buffer faces = aiMesh.mFaces();
        final int faceCount = aiMesh.mNumFaces();
        final int[] indices = new int[faceCount * 3];
        int written = 0;
        for (int f = 0; f < faceCount; ++f) {
            final AIFace face = faces.get(f);
            if (face.mNumIndices() != 3) {
                continue;
            }
            final IntBuffer corners = face.mIndices();
            indices[written++] = corners.get(0);
            indices[written++] = corners.get(1);
            indices[written++] = corners.get(2);
        }

        final AIAABB box = aiMesh.mAABB();
        return new MeshData(
                new Vector3f(box.mMin().x(), box.mMin().y(), box.mMin().z()),
                new Vector3f(box.mMax().x(), box.mMax().y(), box.mMax().z()),
                vertexCount,
                vertices,
                written == indices.length ? indices : java.util.Arrays.copyOf(indices, written),
                0,
                new byte[0]);
    }

    /**
     * Read a material's colors, factors and textures.
     *
     * @param aiMaterial The material.
     * @param index Its index, for a name if it has none.
     * @param finder Finds texture files.
     * @param warnings Where to note textures that can't be found.
     * @return The material.
     */
    private static ImportedMaterial material(
            AIMaterial aiMaterial, int index, TextureFinder finder, List<String> warnings) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIString text = AIString.malloc(stack);
            String name = "material " + index;
            if (Assimp.aiGetMaterialString(
                            aiMaterial, Assimp.AI_MATKEY_NAME, Assimp.aiTextureType_NONE, 0, text)
                    == Assimp.aiReturn_SUCCESS) {
                name = name(text, name);
            }

            AIColor4D color = AIColor4D.malloc(stack);
            final Vector4f base = new Vector4f(1, 1, 1, 1);
            if (Assimp.aiGetMaterialColor(
                                    aiMaterial,
                                    Assimp.AI_MATKEY_BASE_COLOR,
                                    Assimp.aiTextureType_NONE,
                                    0,
                                    color)
                            == Assimp.aiReturn_SUCCESS
                    || Assimp.aiGetMaterialColor(
                                    aiMaterial,
                                    Assimp.AI_MATKEY_COLOR_DIFFUSE,
                                    Assimp.aiTextureType_NONE,
                                    0,
                                    color)
                            == Assimp.aiReturn_SUCCESS) {
                base.set(color.r(), color.g(), color.b(), 1);
            }
            final float opacity = factor(aiMaterial, Assimp.AI_MATKEY_OPACITY, stack);
            if (!Float.isNaN(opacity)) {
                base.w = opacity;
            }
            final Vector3f emissive = new Vector3f();
            if (Assimp.aiGetMaterialColor(
                            aiMaterial,
                            Assimp.AI_MATKEY_COLOR_EMISSIVE,
                            Assimp.aiTextureType_NONE,
                            0,
                            color)
                    == Assimp.aiReturn_SUCCESS) {
                emissive.set(color.r(), color.g(), color.b());
            }
            final float roughness = factor(aiMaterial, Assimp.AI_MATKEY_ROUGHNESS_FACTOR, stack);
            final float metallic = factor(aiMaterial, Assimp.AI_MATKEY_METALLIC_FACTOR, stack);

            final Map<TextureSlot, TextureRef> textures = new EnumMap<>(TextureSlot.class);
            for (Map.Entry<TextureSlot, int[]> slot : TEXTURE_TYPES.entrySet()) {
                for (int type : slot.getValue()) {
                    if (Assimp.aiGetMaterialTextureCount(aiMaterial, type) <= 0
                            || Assimp.aiGetMaterialTexture(
                                            aiMaterial,
                                            type,
                                            0,
                                            text,
                                            (IntBuffer) null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null)
                                    != Assimp.aiReturn_SUCCESS) {
                        continue;
                    }
                    final String named = text.dataString();
                    if (named.isEmpty() || named.charAt(0) == '*') {
                        // Embedded textures aren't read yet
                        continue;
                    }
                    final Path found = finder.find(named);
                    if (found == null) {
                        warnings.add("Can't find texture " + named + " for " + name);
                    }
                    textures.put(slot.getKey(), new TextureRef(named, found));
                    break;
                }
            }
            return new ImportedMaterial(name, base, roughness, metallic, emissive, textures);
        }
    }

    /**
     * Read one float property of a material.
     *
     * @param aiMaterial The material.
     * @param key The property key.
     * @param stack The stack to allocate on.
     * @return The value, or NaN if the material doesn't have it.
     */
    private static float factor(AIMaterial aiMaterial, String key, MemoryStack stack) {
        final FloatBuffer value = stack.mallocFloat(1);
        final IntBuffer max = stack.ints(1);
        if (Assimp.aiGetMaterialFloatArray(
                        aiMaterial, key, Assimp.aiTextureType_NONE, 0, value, max)
                != Assimp.aiReturn_SUCCESS) {
            return Float.NaN;
        }
        return value.get(0);
    }

    /**
     * Finds texture files a model names, by file name, next to the model or in a folder called
     * Textures beside it, ignoring case. Files often name paths on the machine they were made on.
     */
    static final class TextureFinder {
        /** The files in the folders searched, by lower case name. */
        private final Map<String, Path> files = new HashMap<>();

        /** The model's folder, for paths relative to it. */
        private final Path folder;

        /**
         * List the folders to search.
         *
         * @param folder The model's folder.
         */
        TextureFinder(@NonNull Path folder) {
            this.folder = folder;
            for (String sub : new String[] {"", "Textures", "textures", "Texture", "Maps"}) {
                final Path dir = sub.isEmpty() ? folder : folder.resolve(sub);
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (Stream<Path> listed = Files.list(dir)) {
                    listed.filter(Files::isRegularFile)
                            .forEach(
                                    path ->
                                            files.putIfAbsent(
                                                    path.getFileName()
                                                            .toString()
                                                            .toLowerCase(Locale.ROOT),
                                                    path));
                } catch (IOException e) {
                    log.debug("Can't list {}", dir, e);
                }
            }
        }

        /**
         * Find a texture.
         *
         * @param named The path the model names.
         * @return The file, or null if it isn't anywhere searched.
         */
        @Nullable Path find(@NonNull String named) {
            final String normalized = named.replace('\\', '/');
            try {
                final Path relative = folder.resolve(normalized);
                if (Files.isRegularFile(relative)) {
                    return relative;
                }
            } catch (RuntimeException e) {
                // Not a valid path here, like a drive letter from another machine; go by name
            }
            final int slash = normalized.lastIndexOf('/');
            final String name = normalized.substring(slash + 1).toLowerCase(Locale.ROOT);
            return files.get(name);
        }
    }
}
