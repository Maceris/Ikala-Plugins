/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.Utils;
import com.ikalagaming.graphics.exceptions.ModelException;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.graph.Texture;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIAABB;
import org.lwjgl.assimp.AIAnimation;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AINodeAnim;
import org.lwjgl.assimp.AIQuatKey;
import org.lwjgl.assimp.AIQuaternion;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.AIVectorKey;
import org.lwjgl.assimp.AIVertexWeight;
import org.lwjgl.assimp.Assimp;
import org.lwjgl.system.MemoryStack;

import java.io.File;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class for loading in models from file.
 */
@Slf4j
public class ModelLoader {
	/**
	 * The maximum number of bone weights that can affect a vertex, the default
	 * value used by Assimp when limiting bone weights.
	 */
	public static final int MAX_WEIGHTS = 4;

	/**
	 * Information needed to load a model.
	 *
	 * @param modelId The ID to supply the model.
	 * @param pluginName The plugin that contains the model.
	 * @param modelPath The path to the model from the resource directory.
	 * @param materialCache The material cache so we can reuse materials.
	 * @param animation Whether we want to set up vertices for animation.
	 * @param flags Post processing flags for
	 *            {@link Assimp#aiImportFile(CharSequence, int) Assimp file
	 *            import}.
	 */
	public static record ModelLoadRequest(@NonNull String modelId,
		@NonNull String pluginName, @NonNull String modelPath,
		@NonNull MaterialCache materialCache, boolean animation, int flags) {

		/**
		 * Load a model using some standard post processing flags, and return
		 * it.
		 *
		 * @param modelId The ID to supply the model.
		 * @param pluginName The plugin that contains the model.
		 * @param modelPath The path to the model from the resource directory.
		 * @param materialCache The material cache so we can reuse materials.
		 * @param animation Whether we want to set up vertices for animation.
		 */
		public ModelLoadRequest(@NonNull String modelId,
			@NonNull String pluginName, @NonNull String modelPath,
			@NonNull MaterialCache materialCache, boolean animation) {
			this(modelId, pluginName, modelPath, materialCache, animation,
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
					| Assimp.aiProcess_GenBoundingBoxes
					/*
					 * Transforms the data so the model is moved to the origin
					 * and coordinates are fixed to match the OpenGL coordinate
					 * system. It interferes with animation so we skip this part
					 * if the model is animated.
					 */
					| (animation ? 0 : Assimp.aiProcess_PreTransformVertices));
		}
	}

	/**
	 * Lists of weights and bones that affect the vertices, ordered by vertex
	 * ID. Each vertex has {@link ModelLoader#MAX_WEIGHTS} entries in each list,
	 * and if there are less than that number of bones affecting the vertex, the
	 * remaining positions are filled with zeroes.
	 *
	 * Each index in the weights list is the weight corresponding to the bone ID
	 * in the bone ID list at the same position.
	 *
	 * @param weights The weight list.
	 * @param boneIDs The bone ID list.
	 */
	public static record AnimMeshData(float[] weights, int[] boneIDs) {}

	/**
	 * A bone for animation.
	 *
	 * @param boneID The ID of the bone.
	 * @param boneName The name of the bone.
	 * @param offsetMatrix The offset matrix for the bone.
	 */
	private static record Bone(int boneID, String boneName,
		Matrix4f offsetMatrix) {}

	/**
	 * A mapping of how much a specific bone affects a specific vertex.
	 *
	 * @param boneID The ID of the bone.
	 * @param vertexID The ID of the vertex.
	 * @param weight The weight of the bone on the vertex.
	 */
	private record VertexWeight(int boneID, int vertexID, float weight) {}

	/**
	 * The list of models we have queued up to load.
	 */
	private static Deque<ModelLoadRequest> loadQueue =
		new ConcurrentLinkedDeque<>();

	/**
	 * The maximum number of bones that are allowed in a model.
	 */
	public static final int MAX_BONES = 150;

	/**
	 * A 4x4 identity matrix.
	 */
	private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();

	/**
	 * Used to generate unique node names for those missing a name.
	 */
	private static AtomicInteger nodeID = new AtomicInteger();

	/**
	 * For a given frame, go through and build transformation matrices from the
	 * root node down recursively.
	 *
	 * @param aiAnimation The animation data.
	 * @param boneList The list of bone information.
	 * @param animatedFrame The current frame data.
	 * @param frame What frame number we are processing.
	 * @param node The current node we are processing.
	 * @param parentTransformation The transformation matrix of the parent node.
	 * @param globalInverseTransform The inverse of the root node transformation
	 *            matrix.
	 */
	private static void buildFrameMatrices(@NonNull AIAnimation aiAnimation,
		@NonNull List<Bone> boneList,
		@NonNull Model.AnimatedFrame animatedFrame, int frame,
		@NonNull Node node, @NonNull Matrix4f parentTransformation,
		@NonNull Matrix4f globalInverseTransform) {

		String nodeName = node.getName();
		AINodeAnim aiNodeAnim =
			ModelLoader.findAIAnimNode(aiAnimation, nodeName);
		Matrix4f nodeTransform = node.getNodeTransformation();
		if (aiNodeAnim != null) {
			nodeTransform =
				ModelLoader.buildNodeTransformationMatrix(aiNodeAnim, frame);
		}
		Matrix4f nodeGlobalTransform =
			new Matrix4f(parentTransformation).mul(nodeTransform);

		List<Bone> affectedBones = boneList.stream()
			.filter(b -> b.boneName().equals(nodeName)).toList();
		for (Bone bone : affectedBones) {
			Matrix4f boneTransform = new Matrix4f(globalInverseTransform)
				.mul(nodeGlobalTransform).mul(bone.offsetMatrix());
			animatedFrame.getBonesMatrices()[bone.boneID()] = boneTransform;
		}

		for (Node childNode : node.getChildren()) {
			ModelLoader.buildFrameMatrices(aiAnimation, boneList, animatedFrame,
				frame, childNode, nodeGlobalTransform, globalInverseTransform);
		}
	}

	/**
	 * Convert a tree of AINodes into a custom node implementation.
	 *
	 * @param aiNode The assimp node to process.
	 * @param parentNode The parent node.
	 * @return The converted node.
	 */
	private static Node buildNodesTree(@NonNull AINode aiNode,
		Node parentNode) {
		String nodeName;
		if (aiNode.mName().length() <= 0) {
			nodeName = "node" + nodeID.getAndIncrement();
		}
		else {
			nodeName = aiNode.mName().dataString();
		}
		Node node = new Node(nodeName, parentNode,
			ModelLoader.toMatrix(aiNode.mTransformation()));

		int numChildren = aiNode.mNumChildren();
		PointerBuffer aiChildren = aiNode.mChildren();
		for (int i = 0; i < numChildren; ++i) {
			AINode aiChildNode = AINode.create(aiChildren.get(i));
			Node childNode = ModelLoader.buildNodesTree(aiChildNode, node);
			node.addChild(childNode);
		}
		return node;
	}

	/**
	 * Calculate the transformation for a node for a given frame.
	 *
	 * @param aiNodeAnim The current node we are calculating the matrix for.
	 * @param frame The frame number of the animation we are on.
	 * @return The transformation matrix for the animation node.
	 */
	private static Matrix4f buildNodeTransformationMatrix(
		@NonNull AINodeAnim aiNodeAnim, int frame) {
		AIVectorKey.Buffer positionKeys = aiNodeAnim.mPositionKeys();
		AIVectorKey.Buffer scalingKeys = aiNodeAnim.mScalingKeys();
		AIQuatKey.Buffer rotationKeys = aiNodeAnim.mRotationKeys();

		AIVectorKey aiVecKey;
		AIVector3D vec;

		// Start with identity then transform, rotate, then scale it
		Matrix4f nodeTransform = new Matrix4f();
		int numPositions = aiNodeAnim.mNumPositionKeys();
		if (numPositions > 0) {
			aiVecKey = positionKeys.get(Math.min(numPositions - 1, frame));
			vec = aiVecKey.mValue();
			nodeTransform.translate(vec.x(), vec.y(), vec.z());
		}
		int numRotations = aiNodeAnim.mNumRotationKeys();
		if (numRotations > 0) {
			AIQuatKey quatKey =
				rotationKeys.get(Math.min(numRotations - 1, frame));
			AIQuaternion aiQuat = quatKey.mValue();
			Quaternionf quat =
				new Quaternionf(aiQuat.x(), aiQuat.y(), aiQuat.z(), aiQuat.w());
			nodeTransform.rotate(quat);
		}
		int numScalingKeys = aiNodeAnim.mNumScalingKeys();
		if (numScalingKeys > 0) {
			aiVecKey = scalingKeys.get(Math.min(numScalingKeys - 1, frame));
			vec = aiVecKey.mValue();
			nodeTransform.scale(vec.x(), vec.y(), vec.z());
		}

		return nodeTransform;
	}

	/**
	 * Calculate the maximum number of frames in the animation.
	 *
	 * @param aiAnimation The animation.
	 * @return The maximum number of frames for the animation.
	 */
	private static int
		calcAnimationMaxFrames(@NonNull AIAnimation aiAnimation) {
		int maxFrames = 0;
		int numNodeAnims = aiAnimation.mNumChannels();
		PointerBuffer aiChannels = aiAnimation.mChannels();
		for (int i = 0; i < numNodeAnims; ++i) {
			AINodeAnim aiNodeAnim = AINodeAnim.create(aiChannels.get(i));
			int numFrames = Math.max(
				Math.max(aiNodeAnim.mNumPositionKeys(),
					aiNodeAnim.mNumScalingKeys()),
				aiNodeAnim.mNumRotationKeys());
			maxFrames = Math.max(maxFrames, numFrames);
		}

		return maxFrames;
	}

	/**
	 * Find the given node within the animation data by name.
	 *
	 * @param aiAnimation The animation data.
	 * @param nodeName The node to look for.
	 * @return The node we found, which might be null if it does not exist.
	 */
	private static AINodeAnim findAIAnimNode(@NonNull AIAnimation aiAnimation,
		@NonNull String nodeName) {
		AINodeAnim result = null;
		int numAnimNodes = aiAnimation.mNumChannels();
		PointerBuffer aiChannels = aiAnimation.mChannels();
		for (int i = 0; i < numAnimNodes; ++i) {
			AINodeAnim aiNodeAnim = AINodeAnim.create(aiChannels.get(i));
			if (nodeName.equals(aiNodeAnim.mNodeName().dataString())) {
				result = aiNodeAnim;
				break;
			}
		}
		return result;
	}

	/**
	 * Check how many models there are left to load.
	 *
	 * @return The size of the model loading queue.
	 */
	public static int getLoadQueueLength() {
		return ModelLoader.loadQueue.size();
	}

	/**
	 * Load a model from the model loading queue. Does nothing if the queue is
	 * empty.
	 */
	public static void loadModel() {
		if (ModelLoader.loadQueue.isEmpty()) {
			return;
		}
		Model loaded = ModelLoader.loadModel(ModelLoader.loadQueue.poll());
		GraphicsManager.getScene().addModel(loaded);
	}

	/**
	 * Load a model right now and return it.
	 *
	 * @param request Data required to load the model.
	 * @return The newly loaded model.
	 */
	public static Model loadModel(ModelLoadRequest request) {
		File file = PluginFolder.getResource(request.pluginName(),
			ResourceType.DATA, request.modelPath());
		String fixedPath = file.getAbsolutePath();

		if (!file.exists()) {
			String error = SafeResourceLoader.getString("MODEL_PATH_MISSING",
				GraphicsPlugin.getResourceBundle());
			ModelLoader.log.info(error, file.getAbsolutePath());
			throw new ModelException(
				error.replaceFirst("\\{\\}", file.getAbsolutePath()));
		}
		String modelDir = file.getParent();

		AIScene aiScene = Assimp.aiImportFile(fixedPath, request.flags());
		if (aiScene == null) {
			String error = SafeResourceLoader.getString("MODEL_ERROR_LOADING",
				GraphicsPlugin.getResourceBundle());
			ModelLoader.log.info(error, fixedPath);
			throw new ModelException(error.replaceFirst("\\{\\}", fixedPath));
		}

		int numMaterials = aiScene.mNumMaterials();
		List<Integer> materialList = new ArrayList<>();
		for (int i = 0; i < numMaterials; ++i) {
			AIMaterial aiMaterial =
				AIMaterial.create(aiScene.mMaterials().get(i));
			Material material =
				ModelLoader.processMaterial(aiMaterial, modelDir);
			int index = request.materialCache().addMaterial(material);
			materialList.add(index);
		}

		int numMeshes = aiScene.mNumMeshes();
		PointerBuffer aiMeshes = aiScene.mMeshes();
		List<MeshData> meshDataList = new ArrayList<>();
		List<Bone> boneList = new ArrayList<>();
		for (int i = 0; i < numMeshes; ++i) {
			AIMesh aiMesh = AIMesh.create(aiMeshes.get(i));
			MeshData meshData = ModelLoader.processMesh(aiMesh, boneList);
			int materialIdx = aiMesh.mMaterialIndex();
			if (materialIdx >= 0 && materialIdx < materialList.size()) {
				meshData.setMaterialIndex(materialList.get(materialIdx));
			}
			else {
				meshData.setMaterialIndex(MaterialCache.DEFAULT_MATERIAL_INDEX);
			}
			meshDataList.add(meshData);
		}

		List<Model.Animation> animations = new ArrayList<>();
		int numAnimations = aiScene.mNumAnimations();
		Matrix4f globalInverseTransformation = ModelLoader
			.toMatrix(aiScene.mRootNode().mTransformation()).invert();
		if (numAnimations > 0) {
			nodeID.set(0);
			Node rootNode =
				ModelLoader.buildNodesTree(aiScene.mRootNode(), null);

			animations = ModelLoader.processAnimations(aiScene, boneList,
				rootNode, globalInverseTransformation);
		}

		Assimp.aiReleaseImport(aiScene);

		return new Model(request.modelId(), meshDataList, animations);
	}

	/**
	 * Put in a request to load a model later, on the main thread.
	 *
	 * @param request The details about what model to load.
	 */
	public static void loadModelLater(ModelLoadRequest request) {
		ModelLoader.loadQueue.add(request);
	}

	/**
	 * Process a scene and pull out a list of animations.
	 *
	 * @param aiScene The model data.
	 * @param boneList The list of bone weight information.
	 * @param rootNode The root node of the hierarchy as supplied by assimp.
	 * @param globalInverseTransformation The inverse of the root nodes
	 *            transformation relative to the parent.
	 * @return The list of animations.
	 */
	private static List<Model.Animation> processAnimations(
		@NonNull AIScene aiScene, @NonNull List<Bone> boneList,
		@NonNull Node rootNode, @NonNull Matrix4f globalInverseTransformation) {
		List<Model.Animation> animations = new ArrayList<>();

		final int boneCount = Math.min(ModelLoader.MAX_BONES, boneList.size());

		// Process all animations
		final int numAnimations = aiScene.mNumAnimations();
		PointerBuffer aiAnimations = aiScene.mAnimations();
		for (int i = 0; i < numAnimations; ++i) {
			AIAnimation aiAnimation = AIAnimation.create(aiAnimations.get(i));
			final int maxFrames =
				ModelLoader.calcAnimationMaxFrames(aiAnimation);

			List<Model.AnimatedFrame> frames = new ArrayList<>();

			AIString aiName = aiAnimation.mName();
			String animationName;
			if (aiName.length() <= 0) {
				animationName = "animation" + i;
			}
			else {
				animationName = aiName.dataString();
			}

			Model.Animation animation = new Model.Animation(animationName,
				aiAnimation.mDuration(), frames);
			animations.add(animation);

			for (int j = 0; j < maxFrames; j++) {
				Matrix4f[] boneMatrices = new Matrix4f[boneCount];
				Arrays.fill(boneMatrices, ModelLoader.IDENTITY_MATRIX);
				Model.AnimatedFrame animatedFrame =
					new Model.AnimatedFrame(boneMatrices);
				ModelLoader.buildFrameMatrices(aiAnimation, boneList,
					animatedFrame, j, rootNode,
					rootNode.getNodeTransformation(),
					globalInverseTransformation);
				frames.add(animatedFrame);
			}
		}
		return animations;
	}

	/**
	 * Process bitangent (points in the direction of the positive Y texture
	 * axis) values for the mesh, and turn it into a list of float values that
	 * we can use for OpenGL.
	 *
	 * @param aiMesh The mesh to process.
	 * @param normals A list of x, y, z values (in that order) of all the
	 *            normals.
	 * @return A list of x, y, z values (in that order) of all the bitangents.
	 */
	private static float[] processBitangents(@NonNull AIMesh aiMesh,
		float[] normals) {

		AIVector3D.Buffer buffer = aiMesh.mBitangents();
		float[] data = new float[buffer.remaining() * 3];
		int pos = 0;
		while (buffer.remaining() > 0) {
			AIVector3D aiBitangent = buffer.get();
			data[pos++] = aiBitangent.x();
			data[pos++] = aiBitangent.y();
			data[pos++] = aiBitangent.z();
		}

		/*
		 * Assimp may not calculate tangents with models that do not have
		 * texture coordinates, so we use empty values in that case.
		 */
		if (data.length == 0) {
			data = new float[normals.length];
		}
		return data;
	}

	/**
	 * Process the mesh and populate the bone list with bone data for use in
	 * processing animations, as well as creating and returning a structure for
	 * populating a mesh with bone and weight data for the vertices.
	 *
	 * @param aiMesh The mesh to process.
	 * @param boneList The list of bones, which is populated by this method.
	 * @return The bone IDs and corresponding weight data affecting each vertex.
	 */
	private static AnimMeshData processBones(@NonNull AIMesh aiMesh,
		@NonNull List<Bone> boneList) {
		List<Integer> boneIds = new ArrayList<>();
		List<Float> weights = new ArrayList<>();

		Map<Integer, List<VertexWeight>> weightSet = new HashMap<>();
		int numBones = aiMesh.mNumBones();
		PointerBuffer aiBones = aiMesh.mBones();
		for (int i = 0; i < numBones; ++i) {
			AIBone aiBone = AIBone.create(aiBones.get(i));
			int id = boneList.size();
			String boneName;
			if (aiBone.mName().length() <= 0) {
				boneName = "bone" + i;
			}
			else {
				boneName = aiBone.mName().dataString();
			}
			Bone bone = new Bone(id, boneName,
				ModelLoader.toMatrix(aiBone.mOffsetMatrix()));
			boneList.add(bone);
			int numWeights = aiBone.mNumWeights();
			AIVertexWeight.Buffer aiWeights = aiBone.mWeights();
			for (int j = 0; j < numWeights; j++) {
				AIVertexWeight aiWeight = aiWeights.get(j);
				VertexWeight vw = new VertexWeight(bone.boneID(),
					aiWeight.mVertexId(), aiWeight.mWeight());
				List<VertexWeight> vertexWeightList = weightSet.computeIfAbsent(
					vw.vertexID(), ignored -> new ArrayList<>());
				vertexWeightList.add(vw);
			}
		}

		int numVertices = aiMesh.mNumVertices();
		for (int i = 0; i < numVertices; ++i) {
			List<VertexWeight> vertexWeightList = weightSet.get(i);
			int size = vertexWeightList == null ? 0 : vertexWeightList.size();
			for (int j = 0; j < MAX_WEIGHTS; j++) {
				if (j < size) {
					/*
					 * Since size is 0 if the weight list is null, this branch
					 * can't execute in that case.
					 */
					@SuppressWarnings("null")
					VertexWeight vw = vertexWeightList.get(j);
					weights.add(vw.weight());
					boneIds.add(vw.boneID());
				}
				else {
					weights.add(0.0f);
					boneIds.add(0);
				}
			}
		}

		return new AnimMeshData(Utils.listFloatToArray(weights),
			boneIds.stream().mapToInt((Integer v) -> v).toArray());
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
	 * Process the raw material into something easier for us to use. Any
	 * materials colors not specified are the {@link Material#DEFAULT_COLOR
	 * default}.
	 *
	 * @param aiMaterial The material to process.
	 * @param modelDir The directory for the model, as a path from the resource
	 *            directory.
	 * @return The material that we have processed.
	 */
	private static Material processMaterial(@NonNull AIMaterial aiMaterial,
		@NonNull String modelDir) {
		Material material = new Material();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			AIColor4D color = AIColor4D.create();

			int result = Assimp.aiGetMaterialColor(aiMaterial,
				Assimp.AI_MATKEY_COLOR_AMBIENT, Assimp.aiTextureType_NONE, 0,
				color);
			if (result == Assimp.aiReturn_SUCCESS) {
				material.setAmbientColor(
					new Vector4f(color.r(), color.g(), color.b(), color.a()));
			}

			result = Assimp.aiGetMaterialColor(aiMaterial,
				Assimp.AI_MATKEY_COLOR_DIFFUSE, Assimp.aiTextureType_NONE, 0,
				color);
			if (result == Assimp.aiReturn_SUCCESS) {
				material.setDiffuseColor(
					new Vector4f(color.r(), color.g(), color.b(), color.a()));
			}

			result = Assimp.aiGetMaterialColor(aiMaterial,
				Assimp.AI_MATKEY_COLOR_SPECULAR, Assimp.aiTextureType_NONE, 0,
				color);
			if (result == Assimp.aiReturn_SUCCESS) {
				material.setSpecularColor(
					new Vector4f(color.r(), color.g(), color.b(), color.a()));
			}

			float reflectance = 0.0f;
			float[] shininessFactor = new float[] {0.0f};
			int[] pMax = new int[] {1};
			result = Assimp.aiGetMaterialFloatArray(aiMaterial,
				Assimp.AI_MATKEY_SHININESS_STRENGTH, Assimp.aiTextureType_NONE,
				0, shininessFactor, pMax);
			if (result != Assimp.aiReturn_SUCCESS) {
				reflectance = shininessFactor[0];
			}
			material.setReflectance(reflectance);

			AIString aiTexturePath = AIString.calloc(stack);
			Assimp.aiGetMaterialTexture(aiMaterial,
				Assimp.aiTextureType_DIFFUSE, 0, aiTexturePath,
				(IntBuffer) null, null, null, null, null, null);
			String texturePath = aiTexturePath.dataString();
			if (texturePath != null && texturePath.length() > 0) {
				texturePath =
					modelDir + File.separator + new File(texturePath).getName();
				material.setTexture(new Texture(texturePath));
				material.setDiffuseColor(Material.DEFAULT_COLOR);
			}

			AIString aiNormalMapPath = AIString.calloc(stack);
			Assimp.aiGetMaterialTexture(aiMaterial,
				Assimp.aiTextureType_NORMALS, 0, aiNormalMapPath,
				(IntBuffer) null, null, null, null, null, null);
			String normalMapPath = aiNormalMapPath.dataString();
			if (normalMapPath != null && normalMapPath.length() > 0) {
				normalMapPath = modelDir + File.separator
					+ new File(normalMapPath).getName();
				material.setNormalMap(new Texture(normalMapPath));
			}

			return material;
		}
	}

	/**
	 * Process a mesh, populate the bone list for helping with animations later,
	 * and returns mesh data containing all the results.
	 *
	 * @param aiMesh The mesh to process.
	 * @param boneList The list of bones information.
	 * @return The mesh data populated from the mesh in a format easier to
	 *         process and use with OpenGL.
	 */
	private static MeshData processMesh(@NonNull AIMesh aiMesh,
		@NonNull List<Bone> boneList) {
		float[] vertices = ModelLoader.processVertices(aiMesh);
		float[] normals = ModelLoader.processNormals(aiMesh);
		float[] tangents = ModelLoader.processTangents(aiMesh, normals);
		float[] bitangents = ModelLoader.processBitangents(aiMesh, normals);
		float[] textCoords = ModelLoader.processTextCoords(aiMesh);
		int[] indices = ModelLoader.processIndices(aiMesh);
		AnimMeshData animMeshData = ModelLoader.processBones(aiMesh, boneList);

		/*
		 * In case texture coordinates weren't processed, we create a new empty
		 * array that is the appropriate size.
		 */
		if (textCoords.length == 0) {
			int numElements = (vertices.length / 3) * 2;
			textCoords = new float[numElements];
		}

		AIAABB aabb = aiMesh.mAABB();
		Vector3f aabbMin =
			new Vector3f(aabb.mMin().x(), aabb.mMin().y(), aabb.mMin().z());
		Vector3f aabbMax =
			new Vector3f(aabb.mMax().x(), aabb.mMax().y(), aabb.mMax().z());

		return new MeshData(vertices, normals, tangents, bitangents, textCoords,
			indices, animMeshData.boneIDs, animMeshData.weights, aabbMin,
			aabbMax);
	}

	/**
	 * Process the normals data from a mesh and turn it into a list of float
	 * values that we can use for OpenGL.
	 *
	 * @param aiMesh The mesh to process.
	 * @return A list of x, y, z values (in that order) of all the normals.
	 */
	private static float[] processNormals(@NonNull AIMesh aiMesh) {
		AIVector3D.Buffer buffer = aiMesh.mNormals();
		float[] data = new float[buffer.remaining() * 3];
		int pos = 0;
		while (buffer.remaining() > 0) {
			AIVector3D normal = buffer.get();
			data[pos++] = normal.x();
			data[pos++] = normal.y();
			data[pos++] = normal.z();
		}
		return data;
	}

	/**
	 * Process tangent (points in the direction of the positive X texture axis)
	 * values for the mesh, and turn it into a list of float values that we can
	 * use for OpenGL.
	 *
	 * @param aiMesh The mesh to process.
	 * @param normals A list of x, y, z values (in that order) of all the
	 *            normals.
	 * @return A list of x, y, z values (in that order) of all the tangents.
	 */
	private static float[] processTangents(@NonNull AIMesh aiMesh,
		@NonNull float[] normals) {

		AIVector3D.Buffer buffer = aiMesh.mTangents();
		float[] data = new float[buffer.remaining() * 3];
		int pos = 0;
		while (buffer.remaining() > 0) {
			AIVector3D aiTangent = buffer.get();
			data[pos++] = aiTangent.x();
			data[pos++] = aiTangent.y();
			data[pos++] = aiTangent.z();
		}

		/*
		 * Assimp may not calculate tangents with models that do not have
		 * texture coordinates, so we use empty values in that case.
		 */
		if (data.length == 0) {
			data = new float[normals.length];
		}
		return data;
	}

	/**
	 * Process the texture coordinates for the first (0 index) texture for a
	 * mesh, converts it to a buffer of x and then 1-y values for each
	 * coordinate.
	 *
	 * @param aiMesh The mesh to process.
	 * @return An array of coordinate values for all texture coordinates, in x,
	 *         y order.
	 */
	private static float[] processTextCoords(@NonNull AIMesh aiMesh) {
		AIVector3D.Buffer buffer = aiMesh.mTextureCoords(0);
		if (buffer == null) {
			return new float[] {};
		}
		float[] data = new float[buffer.remaining() * 2];
		int pos = 0;
		while (buffer.remaining() > 0) {
			AIVector3D textCoord = buffer.get();
			data[pos++] = textCoord.x();
			data[pos++] = 1 - textCoord.y();
		}
		return data;
	}

	/**
	 * Process the vertex data from a mesh and convert it to a float buffer that
	 * we can use for OpenGL.
	 *
	 * @param aiMesh The mesh to process.
	 * @return An array of coordinate values for all vertices, in x, y, z order.
	 */
	private static float[] processVertices(@NonNull AIMesh aiMesh) {
		AIVector3D.Buffer buffer = aiMesh.mVertices();
		float[] data = new float[buffer.remaining() * 3];
		int pos = 0;
		while (buffer.remaining() > 0) {
			AIVector3D textCoord = buffer.get();
			data[pos++] = textCoord.x();
			data[pos++] = textCoord.y();
			data[pos++] = textCoord.z();
		}
		return data;
	}

	/**
	 * Convert a 4x4 row-major Assimp matrix to a 4x4 column-major one for
	 * OpenGL.
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

	/**
	 * Private constructor to prevent instantiation.
	 */
	private ModelLoader() {
		throw new UnsupportedOperationException(
			"This utility class should not be instantiated");
	}
}
