/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Buffers for indirect drawing of models.
 */
public class RenderBuffers {

	/**
	 * Data used for drawing animated meshes.
	 */
	@Getter
	@AllArgsConstructor
	public static class AnimMeshDrawData {
		/**
		 * The entity associated with the model.
		 *
		 * @param The entity associated with the model.
		 * @return The entity associated with the model.
		 */
		private final Entity entity;
		/**
		 * The offset to the binding pose within the data.
		 *
		 * @param The offset to the binding pose within the data.
		 * @return The offset to the binding pose within the data.
		 */
		private final int bindingPoseOffset;
		/**
		 * The offset to the weight within the data.
		 *
		 * @param The offset to the weight within the data.
		 * @return The offset to the weight within the data.
		 */
		private final int weightsOffset;
	}

	/**
	 * Data used for drawing meshes.
	 */
	@Getter
	@AllArgsConstructor
	public static class MeshDrawData {
		/**
		 * The size of the mesh in bytes.
		 *
		 * @param sizeInBytes The size of the mesh in bytes.
		 * @return The size.
		 */
		private final int sizeInBytes;
		/**
		 * The material index that the mesh is associated with.
		 *
		 * @param The material index that the mesh is associated with.
		 * @return The material index.
		 */
		private final int materialIndex;
		/**
		 * The offset in the buffer containing the vertex information and
		 * vertices, measured in vertices.
		 *
		 * @param offset The offset in rows.
		 * @return The offset.
		 */
		private final int offset;
		/**
		 * The number of indices.
		 *
		 * @param vertices The number of indices.
		 * @return The number of indices.
		 */
		private final int vertices;
		/**
		 * Animation mesh draw data, which is null for static meshes.
		 *
		 * @param The animation mesh draw data.
		 * @return The animation mesh draw data.
		 */
		private final AnimMeshDrawData animMeshDrawData;

		/**
		 * Set up mesh draw data for a mesh without animation draw data.
		 *
		 * @param sizeInBytes The size of the mesh in bytes.
		 * @param materialIndex The material index that the mesh is associated
		 *            with.
		 * @param offset The offset in rows, measured in vertices.
		 * @param vertices The number of indices.
		 */
		public MeshDrawData(int sizeInBytes, int materialIndex, int offset,
			int vertices) {
			this(sizeInBytes, materialIndex, offset, vertices, null);
		}
	}

	/**
	 * The Vertex Array Object ID, for animated models.
	 *
	 * @return The VAO ID of the animated model data.
	 */
	@Getter
	private int animVaoID;
	/**
	 * The Vertex Buffer Object ID for binding poses, for animated models.
	 *
	 * @return The binding poses buffer.
	 */
	@Getter
	private int bindingPosesBuffer;
	/**
	 * The Vertex Buffer Object ID for bone indices weights, for animated
	 * models.
	 *
	 * @return The bone indices weights buffer.
	 */
	@Getter
	private int bonesIndicesWeightsBuffer;
	/**
	 * The Vertex Buffer Object ID for bone matrices, for animated models.
	 *
	 * @return The bone matrices buffer.
	 */
	@Getter
	private int bonesMatricesBuffer;
	/**
	 * The Vertex Buffer Object ID for transformed animation vertices after
	 * processing, for animated models.
	 *
	 * @return The processed animation data.
	 */
	@Getter
	private int destAnimationBuffer;
	/**
	 * The Vertex Array Object ID, for static models.
	 *
	 * @return The VAO ID of the static model data.
	 */
	@Getter
	private int staticVaoID;
	/**
	 * The list of Vertex Buffer Objects that this class set up.
	 */
	private List<Integer> vboIDList;

	/**
	 * Set up a new render buffer.
	 */
	public RenderBuffers() {
		this.vboIDList = new ArrayList<>();
	}

	/**
	 * Clean up all the data.
	 */
	public void cleanup() {
		this.vboIDList.stream().forEach(GL15::glDeleteBuffers);
		GL30.glDeleteVertexArrays(this.staticVaoID);
		GL30.glDeleteVertexArrays(this.animVaoID);
	}

	private void defineVertexAttribs() {
		final int stride = 3 * 4 * 4 + 2 * 4;
		int pointer = 0;
		// Positions
		GL20.glEnableVertexAttribArray(0);
		GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, pointer);
		pointer += 3 * 4;
		// Normals
		GL20.glEnableVertexAttribArray(1);
		GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, pointer);
		pointer += 3 * 4;
		// Tangents
		GL20.glEnableVertexAttribArray(2);
		GL20.glVertexAttribPointer(2, 3, GL11.GL_FLOAT, false, stride, pointer);
		pointer += 3 * 4;
		// Bitangents
		GL20.glEnableVertexAttribArray(3);
		GL20.glVertexAttribPointer(3, 3, GL11.GL_FLOAT, false, stride, pointer);
		pointer += 3 * 4;
		// Texture coordinates
		GL20.glEnableVertexAttribArray(4);
		GL20.glVertexAttribPointer(4, 2, GL11.GL_FLOAT, false, stride, pointer);
	}

	/**
	 * Load models with animation information for a scene.
	 *
	 * @param scene The scene to load models for.
	 */
	public void loadAnimatedModels(@NonNull Scene scene) {
		List<Model> modelList = scene.getModelMap().values().stream()
			.filter(Model::isAnimated).toList();
		this.loadBindingPoses(modelList);
		this.loadBonesMatricesBuffer(modelList);
		this.loadBonesIndicesWeights(modelList);

		this.animVaoID = GL30.glGenVertexArrays();
		GL30.glBindVertexArray(this.animVaoID);
		int positionsSize = 0;
		int normalsSize = 0;
		int textureCoordsSize = 0;
		int indicesSize = 0;
		int offset = 0;
		int chunkBindingPoseOffset = 0;
		int bindingPoseOffset = 0;
		int chunkWeightsOffset = 0;
		int weightsOffset = 0;
		for (Model model : modelList) {
			List<Entity> entities = model.getEntitiesList();
			for (Entity entity : entities) {
				List<RenderBuffers.MeshDrawData> meshDrawDataList =
					model.getMeshDrawDataList();
				bindingPoseOffset = chunkBindingPoseOffset;
				weightsOffset = chunkWeightsOffset;
				for (MeshData meshData : model.getMeshDataList()) {
					positionsSize += meshData.getPositions().length;
					normalsSize += meshData.getNormals().length;
					textureCoordsSize +=
						meshData.getTextureCoordinates().length;
					indicesSize += meshData.getIndices().length;

					int meshSizeInBytes = (meshData.getPositions().length
						+ meshData.getNormals().length * 3
						+ meshData.getTextureCoordinates().length) * 4;
					meshDrawDataList.add(new MeshDrawData(meshSizeInBytes,
						meshData.getMaterialIndex(), offset,
						meshData.getIndices().length, new AnimMeshDrawData(
							entity, bindingPoseOffset, weightsOffset)));
					bindingPoseOffset += meshSizeInBytes / 4;
					int groupSize =
						(int) Math.ceil((float) meshSizeInBytes / (14 * 4));
					weightsOffset += groupSize * 2 * 4;
					offset = positionsSize / 3;
				}
			}
			chunkBindingPoseOffset += bindingPoseOffset;
			chunkWeightsOffset += weightsOffset;
		}

		this.destAnimationBuffer = GL15.glGenBuffers();
		this.vboIDList.add(this.destAnimationBuffer);
		FloatBuffer meshesBuffer = MemoryUtil
			.memAllocFloat(positionsSize + normalsSize * 3 + textureCoordsSize);
		for (Model model : modelList) {
			model.getEntitiesList().forEach(e -> {
				for (MeshData meshData : model.getMeshDataList()) {
					this.populateMeshBuffer(meshesBuffer, meshData);
				}
			});
		}
		meshesBuffer.flip();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.destAnimationBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, meshesBuffer,
			GL15.GL_STATIC_DRAW);
		MemoryUtil.memFree(meshesBuffer);

		this.defineVertexAttribs();

		// Index VBO
		int vboId = GL15.glGenBuffers();
		this.vboIDList.add(vboId);
		IntBuffer indicesBuffer = MemoryUtil.memAllocInt(indicesSize);
		for (Model model : modelList) {
			model.getEntitiesList().forEach(e -> {
				for (MeshData meshData : model.getMeshDataList()) {
					indicesBuffer.put(meshData.getIndices());
				}
			});
		}
		indicesBuffer.flip();
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, vboId);
		GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer,
			GL15.GL_STATIC_DRAW);
		MemoryUtil.memFree(indicesBuffer);

		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL30.glBindVertexArray(0);
	}

	/**
	 * Store binding pose information for all of the meshes that are associated
	 * with the animated models.
	 *
	 * @param modelList The modes to load binding pose information for.
	 */
	private void loadBindingPoses(@NonNull List<Model> modelList) {
		int meshSize = 0;
		for (Model model : modelList) {
			for (MeshData meshData : model.getMeshDataList()) {
				meshSize += meshData.getPositions().length
					+ meshData.getNormals().length * 3
					+ meshData.getTextureCoordinates().length
					+ meshData.getIndices().length;
			}
		}

		this.bindingPosesBuffer = GL15.glGenBuffers();
		this.vboIDList.add(this.bindingPosesBuffer);
		FloatBuffer meshesBuffer = MemoryUtil.memAllocFloat(meshSize);
		for (Model model : modelList) {
			for (MeshData meshData : model.getMeshDataList()) {
				this.populateMeshBuffer(meshesBuffer, meshData);
			}
		}
		meshesBuffer.flip();
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,
			this.bindingPosesBuffer);
		GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, meshesBuffer,
			GL15.GL_STATIC_DRAW);
		MemoryUtil.memFree(meshesBuffer);

		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
	}

	/**
	 * Store all of the bone index and weight information for the animated
	 * models.
	 *
	 * @param modelList The list of models to load bone index and weight
	 *            information for.
	 */
	private void loadBonesIndicesWeights(@NonNull List<Model> modelList) {
		int bufferSize = 0;
		for (Model model : modelList) {
			for (MeshData meshData : model.getMeshDataList()) {
				bufferSize += meshData.getBoneIndices().length * 4
					+ meshData.getWeights().length * 4;
			}
		}
		ByteBuffer dataBuffer = MemoryUtil.memAlloc(bufferSize);
		for (Model model : modelList) {
			for (MeshData meshData : model.getMeshDataList()) {
				int[] bonesIndices = meshData.getBoneIndices();
				float[] weights = meshData.getWeights();
				int rows = bonesIndices.length / 4;
				for (int row = 0; row < rows; row++) {
					int startPos = row * 4;
					dataBuffer.putFloat(weights[startPos]);
					dataBuffer.putFloat(weights[startPos + 1]);
					dataBuffer.putFloat(weights[startPos + 2]);
					dataBuffer.putFloat(weights[startPos + 3]);
					dataBuffer.putFloat(bonesIndices[startPos]);
					dataBuffer.putFloat(bonesIndices[startPos + 1]);
					dataBuffer.putFloat(bonesIndices[startPos + 2]);
					dataBuffer.putFloat(bonesIndices[startPos + 3]);
				}
			}
		}
		dataBuffer.flip();

		this.bonesIndicesWeightsBuffer = GL15.glGenBuffers();
		this.vboIDList.add(this.bonesIndicesWeightsBuffer);
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,
			this.bonesIndicesWeightsBuffer);
		GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, dataBuffer,
			GL15.GL_STATIC_DRAW);
		MemoryUtil.memFree(dataBuffer);

		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
	}

	/**
	 * Store the bone matrices for all animations of the animated models.
	 *
	 * @param modelList The list of models to store bone matrices information
	 *            for.
	 */
	private void loadBonesMatricesBuffer(@NonNull List<Model> modelList) {
		int bufferSize = 0;
		for (Model model : modelList) {
			List<Model.Animation> animationsList = model.getAnimationList();
			for (Model.Animation animation : animationsList) {
				List<Model.AnimatedFrame> frameList = animation.getFrames();
				for (Model.AnimatedFrame frame : frameList) {
					Matrix4f[] matrices = frame.getBonesMatrices();
					bufferSize += matrices.length * 64;
				}
			}
		}

		this.bonesMatricesBuffer = GL15.glGenBuffers();
		this.vboIDList.add(this.bonesMatricesBuffer);
		ByteBuffer dataBuffer = MemoryUtil.memAlloc(bufferSize);
		int matrixSize = 4 * 4 * 4;
		for (Model model : modelList) {
			List<Model.Animation> animationsList = model.getAnimationList();
			for (Model.Animation animation : animationsList) {
				List<Model.AnimatedFrame> frameList = animation.getFrames();
				for (Model.AnimatedFrame frame : frameList) {
					frame.setOffset(dataBuffer.position() / matrixSize);
					Matrix4f[] matrices = frame.getBonesMatrices();
					for (Matrix4f matrix : matrices) {
						matrix.get(dataBuffer);
						dataBuffer.position(dataBuffer.position() + matrixSize);
					}
					frame.clearData();
				}
			}
		}
		dataBuffer.flip();

		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,
			this.bonesMatricesBuffer);
		GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, dataBuffer,
			GL15.GL_STATIC_DRAW);
		MemoryUtil.memFree(dataBuffer);
	}

	/**
	 * Load models with no animation information for a scene.
	 *
	 * @param scene The scene to load models for.
	 */
	public void loadStaticModels(@NonNull Scene scene) {
		List<Model> modelList = scene.getModelMap().values().stream()
			.filter(m -> !m.isAnimated()).toList();
		this.staticVaoID = GL30.glGenVertexArrays();
		GL30.glBindVertexArray(this.staticVaoID);
		int positionsSize = 0;
		int normalsSize = 0;
		int textureCoordsSize = 0;
		int indicesSize = 0;
		int offset = 0;
		for (Model model : modelList) {
			List<RenderBuffers.MeshDrawData> meshDrawDataList =
				model.getMeshDrawDataList();
			for (MeshData meshData : model.getMeshDataList()) {
				positionsSize += meshData.getPositions().length;
				normalsSize += meshData.getNormals().length;
				textureCoordsSize += meshData.getTextureCoordinates().length;
				indicesSize += meshData.getIndices().length;

				int meshSizeInBytes = meshData.getPositions().length * 14 * 4;
				meshDrawDataList.add(new MeshDrawData(meshSizeInBytes,
					meshData.getMaterialIndex(), offset,
					meshData.getIndices().length));
				offset = positionsSize / 3;
			}
		}

		int vboID = GL15.glGenBuffers();
		this.vboIDList.add(vboID);
		FloatBuffer meshesBuffer = MemoryUtil
			.memAllocFloat(positionsSize + normalsSize * 3 + textureCoordsSize);
		for (Model model : modelList) {
			for (MeshData meshData : model.getMeshDataList()) {
				this.populateMeshBuffer(meshesBuffer, meshData);
			}
		}
		meshesBuffer.flip();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, meshesBuffer,
			GL15.GL_STATIC_DRAW);
		MemoryUtil.memFree(meshesBuffer);

		this.defineVertexAttribs();

		// Index VBO
		vboID = GL15.glGenBuffers();
		this.vboIDList.add(vboID);
		IntBuffer indicesBuffer = MemoryUtil.memAllocInt(indicesSize);
		for (Model model : modelList) {
			for (MeshData meshData : model.getMeshDataList()) {
				indicesBuffer.put(meshData.getIndices());
			}
		}
		indicesBuffer.flip();
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, vboID);
		GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer,
			GL15.GL_STATIC_DRAW);
		MemoryUtil.memFree(indicesBuffer);

		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL30.glBindVertexArray(0);
	}

	/**
	 * Populate the meshes buffer from mesh data.
	 *
	 * @param meshesBuffer The buffer to populate.
	 * @param meshData The data to fill the buffer with.
	 */
	private void populateMeshBuffer(@NonNull FloatBuffer meshesBuffer,
		@NonNull MeshData meshData) {
		float[] positions = meshData.getPositions();
		float[] normals = meshData.getNormals();
		float[] tangents = meshData.getTangents();
		float[] bitangents = meshData.getBitangents();
		float[] textCoords = meshData.getTextureCoordinates();

		int rows = positions.length / 3;
		for (int row = 0; row < rows; row++) {
			int startPos = row * 3;
			int startTextCoord = row * 2;
			meshesBuffer.put(positions[startPos]);
			meshesBuffer.put(positions[startPos + 1]);
			meshesBuffer.put(positions[startPos + 2]);
			meshesBuffer.put(normals[startPos]);
			meshesBuffer.put(normals[startPos + 1]);
			meshesBuffer.put(normals[startPos + 2]);
			meshesBuffer.put(tangents[startPos]);
			meshesBuffer.put(tangents[startPos + 1]);
			meshesBuffer.put(tangents[startPos + 2]);
			meshesBuffer.put(bitangents[startPos]);
			meshesBuffer.put(bitangents[startPos + 1]);
			meshesBuffer.put(bitangents[startPos + 2]);
			meshesBuffer.put(textCoords[startTextCoord]);
			meshesBuffer.put(textCoords[startTextCoord + 1]);
		}
	}
}