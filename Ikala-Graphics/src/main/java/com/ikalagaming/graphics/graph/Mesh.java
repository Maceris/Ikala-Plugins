/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Given an array of positions, sets up data to load into graphics card.
 */
public class Mesh {
	/**
	 * The maximum number of bone weights that can affect a vertex, the default
	 * value used by Assimp when limiting bone weights.
	 */
	public static final int MAX_WEIGHTS = 4;
	/**
	 * The max value (corner) of the axis-aligned bounding box that is generated
	 * by Assimp.
	 *
	 * @return The max value of the AABB.
	 */
	@Getter
	private Vector3f aabbMax;
	/**
	 * The min value (corner) of the axis-aligned bounding box that is generated
	 * by Assimp.
	 *
	 * @return The min value of the AABB.
	 */
	@Getter
	private Vector3f aabbMin;
	/**
	 * The number of vertices in the mesh.
	 *
	 * @return The number of vertices.
	 */
	@Getter
	private int vertexCount;
	/**
	 * The ID of the OpenGL vertex array object.
	 *
	 * @return The VAO ID.
	 */
	@Getter
	private int vaoID;
	/**
	 * A list of vertex buffer objects for this mesh.
	 */
	private List<Integer> vboIDList;

	/**
	 * Create a mesh from raw mesh data.
	 *
	 * @param meshData The mesh data to load in.
	 */
	public Mesh(@NonNull MeshData meshData) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			this.aabbMin = meshData.getAabbMin();
			this.aabbMax = meshData.getAabbMax();
			this.vertexCount = meshData.getIndices().length;
			this.vboIDList = new ArrayList<>();

			this.vaoID = GL30.glGenVertexArrays();
			GL30.glBindVertexArray(this.vaoID);

			// Positions VBO
			int vboID = GL15.glGenBuffers();
			this.vboIDList.add(vboID);
			FloatBuffer positionsBuffer =
				stack.callocFloat(meshData.getPositions().length);
			positionsBuffer.put(0, meshData.getPositions());
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, positionsBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glEnableVertexAttribArray(0);
			GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);

			// Normals VBO
			vboID = GL15.glGenBuffers();
			this.vboIDList.add(vboID);
			FloatBuffer normalsBuffer =
				stack.callocFloat(meshData.getNormals().length);
			normalsBuffer.put(0, meshData.getNormals());
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, normalsBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glEnableVertexAttribArray(1);
			GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 0, 0);

			// Tangents VBO
			vboID = GL15.glGenBuffers();
			this.vboIDList.add(vboID);
			FloatBuffer tangentsBuffer =
				stack.callocFloat(meshData.getTangents().length);
			tangentsBuffer.put(0, meshData.getTangents());
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, tangentsBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glEnableVertexAttribArray(2);
			GL20.glVertexAttribPointer(2, 3, GL11.GL_FLOAT, false, 0, 0);

			// Bitangents VBO
			vboID = GL15.glGenBuffers();
			this.vboIDList.add(vboID);
			FloatBuffer bitangentsBuffer =
				stack.callocFloat(meshData.getBitangents().length);
			bitangentsBuffer.put(0, meshData.getBitangents());
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, bitangentsBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glEnableVertexAttribArray(3);
			GL20.glVertexAttribPointer(3, 3, GL11.GL_FLOAT, false, 0, 0);

			// Texture coordinates VBO
			vboID = GL15.glGenBuffers();
			this.vboIDList.add(vboID);
			FloatBuffer textCoordsBuffer = MemoryUtil
				.memAllocFloat(meshData.getTextureCoordinates().length);
			textCoordsBuffer.put(0, meshData.getTextureCoordinates());
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, textCoordsBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glEnableVertexAttribArray(4);
			GL20.glVertexAttribPointer(4, 2, GL11.GL_FLOAT, false, 0, 0);

			// Bone weights
			vboID = GL15.glGenBuffers();
			this.vboIDList.add(vboID);
			FloatBuffer weightsBuffer =
				MemoryUtil.memAllocFloat(meshData.getWeights().length);
			weightsBuffer.put(meshData.getWeights()).flip();
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, weightsBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glEnableVertexAttribArray(5);
			GL20.glVertexAttribPointer(5, 4, GL11.GL_FLOAT, false, 0, 0);

			// Bone indices
			vboID = GL15.glGenBuffers();
			this.vboIDList.add(vboID);
			IntBuffer boneIndicesBuffer =
				MemoryUtil.memAllocInt(meshData.getBoneIndices().length);
			boneIndicesBuffer.put(meshData.getBoneIndices()).flip();
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, boneIndicesBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glEnableVertexAttribArray(6);
			GL20.glVertexAttribPointer(6, 4, GL11.GL_FLOAT, false, 0, 0);

			// Index VBO
			vboID = GL15.glGenBuffers();
			this.vboIDList.add(vboID);
			IntBuffer indicesBuffer =
				stack.callocInt(meshData.getIndices().length);
			indicesBuffer.put(0, meshData.getIndices());
			GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, vboID);
			GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer,
				GL15.GL_STATIC_DRAW);

			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
			GL30.glBindVertexArray(0);
		}
	}

	/**
	 * Clean the mesh up.
	 */
	public void cleanup() {
		this.vboIDList.stream().forEach(GL15::glDeleteBuffers);
		GL30.glDeleteVertexArrays(this.vaoID);
	}
}