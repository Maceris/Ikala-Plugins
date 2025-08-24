package com.ikalagaming.converter;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.exceptions.ModelException;
import com.ikalagaming.graphics.frontend.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Converts raw models to our custom format. */
@Slf4j
public class ModelConverter {

    private static final int DEFAULT_FLAGS =
            /*
             * Generates smooth normals for all vertices in the mesh unless
             * the normals are already there at the time this flag is
             * evaluated.
             */
            Assimp.aiProcess_GenSmoothNormals
                    /*
                     * Reduces number of vertices by reusing identical vertices
                     * between faces
                     */
                    | Assimp.aiProcess_JoinIdenticalVertices
                    /*
                     * Splits all the faces into triangles in case it uses quads
                     * or other geometry
                     */
                    | Assimp.aiProcess_Triangulate
                    /*
                     * Tries to fix normals that point inwards by reversing them
                     */
                    | Assimp.aiProcess_FixInfacingNormals
                    /*
                     * Used for lighting, calculates tangents and bitangents
                     * from normals.
                     */
                    | Assimp.aiProcess_CalcTangentSpace
                    /*
                     * Limits the number of bone weights that affect any
                     * particular vertex. The default bone weight limit is 4.
                     */
                    | Assimp.aiProcess_LimitBoneWeights
                    /*
                     * Generates axis-aligned bounding volume.
                     */
                    | Assimp.aiProcess_GenBoundingBoxes;

    /**
     * Information needed to load a model.
     *
     * @param modelId The ID to supply the model.
     * @param pluginName The plugin that contains the model.
     * @param modelPath The path to the model from the resource directory.
     * @param materialCache The material cache so we can reuse materials.
     * @param animation Whether we want to set up vertices for animation.
     */
    public record ModelLoadRequest(
            @NonNull String modelId,
            @NonNull String pluginName,
            @NonNull String modelPath,
            @NonNull MaterialCache materialCache,
            boolean animation) {}

    /**
     * A bone for animation.
     *
     * @param boneID The ID of the bone.
     * @param boneName The name of the bone.
     * @param offsetMatrix The offset matrix for the bone.
     */
    private record Bone(int boneID, String boneName, Matrix4f offsetMatrix) {}

    /**
     * A mapping of how much a specific bone affects a specific vertex.
     *
     * @param boneID The ID of the bone.
     * @param weight The weight of the bone on the vertex.
     */
    private record VertexWeight(int boneID, float weight) {}

    /**
     * The maximum number of bone weights that can affect a vertex, the default value used by Assimp
     * when limiting bone weights.
     */
    public static final int MAX_WEIGHTS = 4;

    /** The maximum number of bones that are allowed in a model. */
    public static final int MAX_BONES = 256;

    /** Used to generate unique node names for those missing a name. */
    private static final AtomicInteger nodeID = new AtomicInteger();

    /**
     * For a given frame, go through and build transformation matrices from the root node down
     * recursively.
     *
     * @param aiAnimation The animation data.
     * @param boneList The list of bone information.
     * @param frameData The frame data to store results into.
     * @param frame What frame number we are processing.
     * @param node The current node we are processing.
     * @param parentTransformation The transformation matrix of the parent node.
     * @param globalInverseTransform The inverse of the root node transformation matrix.
     */
    private static void buildFrameMatrices(
            @NonNull AIAnimation aiAnimation,
            @NonNull List<ModelConverter.Bone> boneList,
            @NonNull ByteBuffer frameData,
            int frame,
            @NonNull Node node,
            @NonNull Matrix4f parentTransformation,
            @NonNull Matrix4f globalInverseTransform) {

        String nodeName = node.getName();
        Matrix4f nodeTransform = node.getNodeTransformation();

        long animChannel = ModelConverter.findAIAnimChannel(aiAnimation, nodeName);
        if (animChannel != 0) {
            nodeTransform = ModelConverter.buildNodeTransformationMatrix(animChannel, frame);
        }
        Matrix4f nodeGlobalTransform = new Matrix4f(parentTransformation).mul(nodeTransform);

        List<ModelConverter.Bone> affectedBones =
                boneList.stream().filter(b -> b.boneName().equals(nodeName)).toList();

        for (ModelConverter.Bone bone : affectedBones) {
            Matrix4f boneTransform =
                    new Matrix4f(globalInverseTransform)
                            .mul(nodeGlobalTransform)
                            .mul(bone.offsetMatrix());

            boneTransform.get(frameData);
        }

        for (Node childNode : node.getChildren()) {
            ModelConverter.buildFrameMatrices(
                    aiAnimation,
                    boneList,
                    frameData,
                    frame,
                    childNode,
                    nodeGlobalTransform,
                    globalInverseTransform);
        }
    }

    /**
     * Convert a tree of AINodes into a custom node implementation.
     *
     * @param aiNode The assimp node to process.
     * @param parentNode The parent node.
     * @return The converted node.
     */
    private static Node buildNodesTree(@NonNull AINode aiNode, Node parentNode) {
        String nodeName;
        if (aiNode.mName().length() <= 0) {
            nodeName = "node" + nodeID.getAndIncrement();
        } else {
            nodeName = aiNode.mName().dataString();
        }
        Node node =
                new Node(nodeName, parentNode, ModelConverter.toMatrix(aiNode.mTransformation()));

        int numChildren = aiNode.mNumChildren();
        PointerBuffer aiChildren = aiNode.mChildren();
        if (aiChildren != null) {
            for (int i = 0; i < numChildren; ++i) {
                AINode aiChildNode = AINode.create(aiChildren.get(i));
                Node childNode = ModelConverter.buildNodesTree(aiChildNode, node);
                node.addChild(childNode);
            }
        }

        return node;
    }

    /**
     * Calculate the transformation for a node for a given frame.
     *
     * @param channel The animation channel we are calculating a transformation for.
     * @param frame The frame number of the animation we are on.
     * @return The transformation matrix for the animation node.
     */
    private static Matrix4f buildNodeTransformationMatrix(long channel, int frame) {
        AIVectorKey.Buffer scalingKeys;
        AIVectorKey aiVecKey;
        AIVector3D vec;
        Matrix4f nodeTransform;
        int numScalingKeys;

        try (AINodeAnim aiNodeAnim = AINodeAnim.create(channel)) {
            AIVectorKey.Buffer positionKeys = aiNodeAnim.mPositionKeys();
            scalingKeys = aiNodeAnim.mScalingKeys();
            AIQuatKey.Buffer rotationKeys = aiNodeAnim.mRotationKeys();

            // Start with identity then transform, rotate, then scale it
            nodeTransform = new Matrix4f();
            int numPositions = aiNodeAnim.mNumPositionKeys();
            if (positionKeys != null && numPositions > 0) {
                aiVecKey = positionKeys.get(Math.min(numPositions - 1, frame));
                vec = aiVecKey.mValue();
                nodeTransform.translate(vec.x(), vec.y(), vec.z());
            }
            int numRotations = aiNodeAnim.mNumRotationKeys();
            if (rotationKeys != null && numRotations > 0) {
                AIQuatKey quatKey = rotationKeys.get(Math.min(numRotations - 1, frame));
                AIQuaternion aiQuat = quatKey.mValue();
                Quaternionf quat = new Quaternionf(aiQuat.x(), aiQuat.y(), aiQuat.z(), aiQuat.w());
                nodeTransform.rotate(quat);
            }
            numScalingKeys = aiNodeAnim.mNumScalingKeys();
            if (scalingKeys != null && numScalingKeys > 0) {
                aiVecKey = scalingKeys.get(Math.min(numScalingKeys - 1, frame));
                vec = aiVecKey.mValue();
                nodeTransform.scale(vec.x(), vec.y(), vec.z());
            }
        }

        return nodeTransform;
    }

    /**
     * Calculate the maximum number of frames in the animation.
     *
     * @param aiAnimation The animation.
     * @return The maximum number of frames for the animation.
     */
    private static int calcAnimationMaxFrames(@NonNull AIAnimation aiAnimation) {
        int maxFrames = 0;
        int numNodeAnims = aiAnimation.mNumChannels();
        PointerBuffer aiChannels = aiAnimation.mChannels();
        if (aiChannels == null) {
            return 0;
        }
        for (int i = 0; i < numNodeAnims; ++i) {
            int numFrames;
            try (AINodeAnim aiNodeAnim = AINodeAnim.create(aiChannels.get(i))) {
                numFrames =
                        Math.max(
                                Math.max(
                                        aiNodeAnim.mNumPositionKeys(),
                                        aiNodeAnim.mNumScalingKeys()),
                                aiNodeAnim.mNumRotationKeys());
            }
            maxFrames = Math.max(maxFrames, numFrames);
        }

        return maxFrames;
    }

    /**
     * Find the given node within the animation data by name.
     *
     * @param aiAnimation The animation data.
     * @param nodeName The node to look for.
     * @return The pointer that will be used to create an AINodeAnim.
     */
    private static long findAIAnimChannel(
            @NonNull AIAnimation aiAnimation, @NonNull String nodeName) {
        int numAnimNodes = aiAnimation.mNumChannels();
        PointerBuffer aiChannels = aiAnimation.mChannels();
        if (aiChannels == null) {
            return 0;
        }
        for (int i = 0; i < numAnimNodes; ++i) {
            long channel = aiChannels.get(i);
            try (AINodeAnim aiNodeAnim = AINodeAnim.create(channel)) {
                if (nodeName.equals(aiNodeAnim.mNodeName().dataString())) {
                    return channel;
                }
            }
        }
        return 0;
    }

    /**
     * Load a model right now and return it.
     *
     * @param request Data required to load the model.
     * @return The newly loaded model.
     */
    public static Model loadModel(@NonNull ModelConverter.ModelLoadRequest request) {
        File file =
                PluginFolder.getResource(
                        request.pluginName(), PluginFolder.ResourceType.DATA, request.modelPath());
        String fixedPath = file.getAbsolutePath();

        if (!file.exists()) {

            String error =
                    SafeResourceLoader.getStringFormatted(
                            "MODEL_PATH_MISSING",
                            GraphicsPlugin.getResourceBundle(),
                            file.getAbsolutePath());
            log.info(error);
            throw new ModelException(error);
        }
        String modelDir = file.getParent();

        int flags = DEFAULT_FLAGS;

        if (!request.animation) {
            flags |= Assimp.aiProcess_PreTransformVertices;
        }
        AIScene aiScene = Assimp.aiImportFile(fixedPath, flags);
        if (aiScene == null) {
            String error =
                    SafeResourceLoader.getStringFormatted(
                            "MODEL_ERROR_LOADING", GraphicsPlugin.getResourceBundle(), fixedPath);
            log.info(error);
            throw new ModelException(error);
        }

        int numMaterials = aiScene.mNumMaterials();
        PointerBuffer aiMaterials = aiScene.mMaterials();
        List<Integer> materialList = new ArrayList<>();
        for (int i = 0; aiMaterials != null && i < numMaterials; ++i) {
            AIMaterial aiMaterial = AIMaterial.create(aiMaterials.get(i));
            Material material = ModelConverter.processMaterial(aiMaterial, modelDir);
            int index = request.materialCache().addMaterial(material);
            materialList.add(index);
        }

        Model result = new Model(request.modelId());

        int numMeshes = aiScene.mNumMeshes();
        PointerBuffer aiMeshes = aiScene.mMeshes();
        List<ModelConverter.Bone> boneList = new ArrayList<>();
        for (int i = 0; aiMeshes != null && i < numMeshes; ++i) {
            AIMesh aiMesh = AIMesh.create(aiMeshes.get(i));
            MeshData meshData = ModelConverter.processMesh(aiMesh, boneList);
            int materialIdx = aiMesh.mMaterialIndex();
            if (materialIdx >= 0 && materialIdx < materialList.size()) {
                meshData.setMaterialIndex(materialList.get(materialIdx));
            } else {
                meshData.setMaterialIndex(MaterialCache.DEFAULT_MATERIAL_INDEX);
            }

            result.getMeshDataList().add(meshData);
        }

        int numAnimations = aiScene.mNumAnimations();

        AINode sceneRoot = aiScene.mRootNode();
        if (sceneRoot != null) {
            Matrix4f globalInverseTransformation =
                    ModelConverter.toMatrix(sceneRoot.mTransformation()).invert();
            if (numAnimations > 0) {
                nodeID.set(0);
                Node rootNode = ModelConverter.buildNodesTree(sceneRoot, null);

                ModelConverter.processAnimations(
                        aiScene,
                        boneList,
                        rootNode,
                        globalInverseTransformation,
                        result.getAnimationList());
            }
        }

        Assimp.aiReleaseImport(aiScene);

        return result;
    }

    /**
     * Process a scene and pull out a list of animations.
     *
     * @param aiScene The model data.
     * @param boneList The list of bone weight information.
     * @param rootNode The root node of the hierarchy as supplied by assimp.
     * @param globalInverseTransformation The inverse of the root nodes transformation relative to
     *     the parent.
     * @param results The list to store animations in.
     */
    private static void processAnimations(
            @NonNull AIScene aiScene,
            @NonNull List<ModelConverter.Bone> boneList,
            @NonNull Node rootNode,
            @NonNull Matrix4f globalInverseTransformation,
            @NonNull List<Model.Animation> results) {
        final int boneCount = Math.min(ModelConverter.MAX_BONES, boneList.size());

        // Process all animations
        final int numAnimations = aiScene.mNumAnimations();
        PointerBuffer aiAnimations = aiScene.mAnimations();

        if (aiAnimations == null) {
            return;
        }

        int runningTotalOffset = 0;
        for (int i = 0; i < numAnimations; ++i) {
            AIAnimation aiAnimation = AIAnimation.create(aiAnimations.get(i));
            final int maxFrames = ModelConverter.calcAnimationMaxFrames(aiAnimation);

            AIString aiName = aiAnimation.mName();
            String animationName;
            if (aiName.length() <= 0) {
                animationName = "animation" + i;
            } else {
                animationName = aiName.dataString();
            }

            final int FRAME_SIZE = (4 * 4 /* matrix */) * 4 /* bytes per float */;

            ByteBuffer frameData = ByteBuffer.allocate(maxFrames * boneCount * FRAME_SIZE);

            for (int frameNumber = 0; frameNumber < maxFrames; frameNumber++) {
                ModelConverter.buildFrameMatrices(
                        aiAnimation,
                        boneList,
                        frameData,
                        frameNumber,
                        rootNode,
                        rootNode.getNodeTransformation(),
                        globalInverseTransformation);
            }

            Model.Animation animation =
                    new Model.Animation(
                            animationName,
                            aiAnimation.mDuration(),
                            boneCount,
                            maxFrames,
                            frameData.array(),
                            runningTotalOffset);
            runningTotalOffset += frameData.array().length;

            results.add(animation);
        }
    }

    /**
     * Process the mesh and populate the bone list with bone data for use in processing animations,
     * as well as creating and returning a structure for populating a mesh with bone and weight data
     * for the vertices.
     *
     * @param aiMesh The mesh to process.
     * @param boneList The list of bones, which is populated by this method.
     * @return The bone IDs and corresponding weight data affecting each vertex.
     */
    private static byte[] processBones(
            @NonNull AIMesh aiMesh, @NonNull List<ModelConverter.Bone> boneList) {
        final int numBones = aiMesh.mNumBones();
        PointerBuffer aiBones = aiMesh.mBones();
        if (aiBones == null || numBones <= 0) {
            return new byte[0];
        }

        Map<Integer, List<ModelConverter.VertexWeight>> weightSet = new HashMap<>();

        for (int i = 0; i < numBones; ++i) {
            Bone bone;
            int numWeights;
            AIVertexWeight.Buffer aiWeights;
            try (AIBone aiBone = AIBone.create(aiBones.get(i))) {
                int id = boneList.size();
                String boneName;
                if (aiBone.mName().length() <= 0) {
                    boneName = "bone" + id;
                } else {
                    boneName = aiBone.mName().dataString();
                }
                bone = new Bone(id, boneName, ModelConverter.toMatrix(aiBone.mOffsetMatrix()));
                boneList.add(bone);
                numWeights = aiBone.mNumWeights();
                aiWeights = aiBone.mWeights();
            }

            for (int j = 0; j < numWeights; j++) {
                AIVertexWeight aiWeight = aiWeights.get(j);
                ModelConverter.VertexWeight vw =
                        new ModelConverter.VertexWeight(bone.boneID(), aiWeight.mWeight());
                List<ModelConverter.VertexWeight> vertexWeightList =
                        weightSet.computeIfAbsent(
                                aiWeight.mVertexId(), ignored -> new ArrayList<>());
                vertexWeightList.add(vw);
            }
        }

        final int numVertices = aiMesh.mNumVertices();
        ByteBuffer resultData =
                ByteBuffer.allocate(
                        numVertices
                                * MAX_WEIGHTS
                                * 2 /* index + weight */
                                * 4 /* bytes per int/float */);

        for (int i = 0; i < numVertices; ++i) {
            List<ModelConverter.VertexWeight> vertexWeightList =
                    weightSet.computeIfAbsent(i, ignored -> new ArrayList<>());
            final int size = vertexWeightList.size();
            for (int j = 0; j < MAX_WEIGHTS; j++) {
                if (j < size) {
                    ModelConverter.VertexWeight vw = vertexWeightList.get(j);
                    resultData.putInt(vw.boneID());
                    resultData.putFloat(vw.weight());
                } else {
                    resultData.putInt(0);
                    resultData.putFloat(0.0f);
                }
            }
        }

        return resultData.array();
    }

    /**
     * Process the mesh and return the list of indices for the faces.
     *
     * @param aiMesh The mesh to process.
     * @return The indices of all the faces.
     */
    private static int[] processIndices(@NonNull AIMesh aiMesh) {
        List<Integer> indices = new ArrayList<>();
        int numFaces = aiMesh.mNumFaces();
        AIFace.Buffer aiFaces = aiMesh.mFaces();
        for (int i = 0; i < numFaces; ++i) {
            AIFace aiFace = aiFaces.get(i);
            IntBuffer buffer = aiFace.mIndices();
            while (buffer.remaining() > 0) {
                indices.add(buffer.get());
            }
        }
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Process the raw material into something easier for us to use. Any materials color not
     * specified are the {@link Material#DEFAULT_COLOR default}.
     *
     * @param aiMaterial The material to process.
     * @param modelDir The directory for the model, as a path from the resource directory.
     * @return The material that we have processed.
     */
    private static Material processMaterial(
            @NonNull AIMaterial aiMaterial, @NonNull String modelDir) {
        // TODO(ches) load these through converter or loader utils for this plugin
        Material material = new Material();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIString aiTexturePath = AIString.calloc(stack);
            Assimp.aiGetMaterialTexture(
                    aiMaterial,
                    Assimp.aiTextureType_DIFFUSE,
                    0,
                    aiTexturePath,
                    (IntBuffer) null,
                    null,
                    null,
                    null,
                    null,
                    null);
            String texturePath = aiTexturePath.dataString();
            if (!texturePath.isEmpty()) {
                texturePath = modelDir + File.separator + new File(texturePath).getName();
                material.setTexture(
                        GraphicsManager.getRenderInstance().getTextureLoader().load(texturePath));
                material.getBaseColor().set(Material.DEFAULT_COLOR);
            }

            AIString aiNormalMapPath = AIString.calloc(stack);
            Assimp.aiGetMaterialTexture(
                    aiMaterial,
                    Assimp.aiTextureType_NORMALS,
                    0,
                    aiNormalMapPath,
                    (IntBuffer) null,
                    null,
                    null,
                    null,
                    null,
                    null);
            String normalMapPath = aiNormalMapPath.dataString();
            if (!normalMapPath.isEmpty()) {
                normalMapPath = modelDir + File.separator + new File(normalMapPath).getName();
                material.setNormalMap(
                        GraphicsManager.getRenderInstance().getTextureLoader().load(normalMapPath));
            }

            return material;
        }
    }

    /**
     * Process a mesh, populate the bone list for helping with animations later, and returns mesh
     * data containing all the results.
     *
     * @param aiMesh The mesh to process.
     * @param boneList The list of bones information.
     * @return The mesh data populated from the mesh in a format easier to process and use with
     *     OpenGL.
     */
    private static MeshData processMesh(
            @NonNull AIMesh aiMesh, @NonNull List<ModelConverter.Bone> boneList) {
        AIVector3D.Buffer positions = aiMesh.mVertices();
        AIVector3D.Buffer normals = aiMesh.mNormals();
        AIVector3D.Buffer tangents = aiMesh.mTangents();
        AIVector3D.Buffer bitangents = aiMesh.mBitangents();

        final int vertexCount = positions.remaining();

        if (normals == null
                || tangents == null
                || bitangents == null
                || vertexCount != normals.remaining()
                || vertexCount != tangents.remaining()
                || vertexCount != bitangents.remaining()) {
            log.error("Error processing mesh data");
            return new MeshData(
                    new Vector3f(),
                    new Vector3f(),
                    0,
                    new float[0],
                    new int[0],
                    0,
                    new byte[0],
                    new float[0][0]);
        }

        AIAABB aabb = aiMesh.mAABB();
        Vector3f aabbMin = new Vector3f(aabb.mMin().x(), aabb.mMin().y(), aabb.mMin().z());
        Vector3f aabbMax = new Vector3f(aabb.mMax().x(), aabb.mMax().y(), aabb.mMax().z());
        int[] indices = ModelConverter.processIndices(aiMesh);
        final int numBones = aiMesh.mNumBones();
        byte[] boneWeightData = ModelConverter.processBones(aiMesh, boneList);

        final int totalVertexDataSize =
                vertexCount * 4 /* position + normal + tangent + bitangent */ * 3 /* Vec3 */;
        FloatBuffer vertexData = FloatBuffer.allocate(totalVertexDataSize);

        for (int i = 0; i < vertexCount; ++i) {
            AIVector3D position = positions.get();
            AIVector3D normal = normals.get();
            AIVector3D tangent = tangents.get();
            AIVector3D bitangent = bitangents.get();

            vertexData.put(position.x());
            vertexData.put(position.y());
            vertexData.put(position.z());
            vertexData.put(normal.x());
            vertexData.put(normal.y());
            vertexData.put(normal.z());
            vertexData.put(tangent.x());
            vertexData.put(tangent.y());
            vertexData.put(tangent.z());
            vertexData.put(bitangent.x());
            vertexData.put(bitangent.y());
            vertexData.put(bitangent.z());
        }

        final int textureLayers = aiMesh.mNumUVComponents().get();
        float[][] textureData = new float[textureLayers][];

        for (int layerIndex = 0; layerIndex < textureLayers; ++layerIndex) {
            float[] layer = new float[vertexCount * 2];
            textureData[layerIndex] = layer;

            AIVector3D.Buffer texCoords = aiMesh.mTextureCoords(layerIndex);
            if (texCoords == null || texCoords.remaining() != vertexCount) {
                continue;
            }

            int pos = 0;
            while (texCoords.remaining() > 0) {
                AIVector3D textureCoordinate = texCoords.get();

                layer[pos++] = textureCoordinate.x();
                layer[pos++] = 1 - textureCoordinate.y();
            }
        }

        return new MeshData(
                aabbMin,
                aabbMax,
                vertexCount,
                vertexData.array(),
                indices,
                numBones,
                boneWeightData,
                textureData);
    }

    /**
     * Convert a 4x4 row-major Assimp matrix to a 4x4 column-major one for OpenGL.
     *
     * @param aiMatrix4x4 The matrix from Assimp.
     * @return The converted matrix.
     */
    private static Matrix4f toMatrix(@NonNull AIMatrix4x4 aiMatrix4x4) {
        Matrix4f result = new Matrix4f();
        result.m00(aiMatrix4x4.a1());
        result.m10(aiMatrix4x4.a2());
        result.m20(aiMatrix4x4.a3());
        result.m30(aiMatrix4x4.a4());
        result.m01(aiMatrix4x4.b1());
        result.m11(aiMatrix4x4.b2());
        result.m21(aiMatrix4x4.b3());
        result.m31(aiMatrix4x4.b4());
        result.m02(aiMatrix4x4.c1());
        result.m12(aiMatrix4x4.c2());
        result.m22(aiMatrix4x4.c3());
        result.m32(aiMatrix4x4.c4());
        result.m03(aiMatrix4x4.d1());
        result.m13(aiMatrix4x4.d2());
        result.m23(aiMatrix4x4.d3());
        result.m33(aiMatrix4x4.d4());

        return result;
    }

    /** Private constructor to prevent instantiation. */
    private ModelConverter() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
