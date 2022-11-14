package com.ikalagaming.graphics.graph;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

import lombok.Getter;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL15;
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
	 * A list of vertex buffer objects for this mesh.
	 */
	private List<Integer> vboIdList;

	/**
	 * The ID of the OpenGL vertex array object.
	 *
	 * @return The VAO ID.
	 */
	@Getter
	private final int vaoId;

	/**
	 * The number of vertices in the mesh.
	 *
	 * @return The number of vertices.
	 */
	@Getter
	private final int vertexCount;

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
	 * Create a mesh from raw mesh data.
	 * 
	 * @param meshData The mesh data to load in.
	 */
	public Mesh(MeshData meshData) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			this.aabbMin = meshData.getAabbMin();
			this.aabbMax = meshData.getAabbMax();
			vertexCount = meshData.getIndices().length;
			vboIdList = new ArrayList<>();

			vaoId = glGenVertexArrays();
			glBindVertexArray(vaoId);

			// Positions VBO
			int vboId = glGenBuffers();
			vboIdList.add(vboId);
			FloatBuffer positionsBuffer =
				stack.callocFloat(meshData.getPositions().length);
			positionsBuffer.put(meshData.getPositions());
			glBindBuffer(GL_ARRAY_BUFFER, vboId);
			glBufferData(GL_ARRAY_BUFFER, positionsBuffer, GL_STATIC_DRAW);
			glEnableVertexAttribArray(0);
			glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);

			// Normals VBO
			vboId = glGenBuffers();
			vboIdList.add(vboId);
			FloatBuffer normalsBuffer =
				stack.callocFloat(meshData.getNormals().length);
			normalsBuffer.put(meshData.getNormals());
			glBindBuffer(GL_ARRAY_BUFFER, vboId);
			glBufferData(GL_ARRAY_BUFFER, normalsBuffer, GL_STATIC_DRAW);
			glEnableVertexAttribArray(1);
			glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0);

			// Tangents VBO
			vboId = glGenBuffers();
			vboIdList.add(vboId);
			FloatBuffer tangentsBuffer =
				stack.callocFloat(meshData.getTangents().length);
			tangentsBuffer.put(meshData.getTangents());
			glBindBuffer(GL_ARRAY_BUFFER, vboId);
			glBufferData(GL_ARRAY_BUFFER, tangentsBuffer, GL_STATIC_DRAW);
			glEnableVertexAttribArray(2);
			glVertexAttribPointer(2, 3, GL_FLOAT, false, 0, 0);

			// Bitangents VBO
			vboId = glGenBuffers();
			vboIdList.add(vboId);
			FloatBuffer bitangentsBuffer =
				stack.callocFloat(meshData.getBitangents().length);
			bitangentsBuffer.put(meshData.getBitangents());
			glBindBuffer(GL_ARRAY_BUFFER, vboId);
			glBufferData(GL_ARRAY_BUFFER, bitangentsBuffer, GL_STATIC_DRAW);
			glEnableVertexAttribArray(3);
			glVertexAttribPointer(3, 3, GL_FLOAT, false, 0, 0);

			// Texture coordinates VBO
			vboId = glGenBuffers();
			vboIdList.add(vboId);
			FloatBuffer textCoordsBuffer = MemoryUtil
				.memAllocFloat(meshData.getTextureCoordinates().length);
			textCoordsBuffer.put(meshData.getTextureCoordinates());
			glBindBuffer(GL_ARRAY_BUFFER, vboId);
			glBufferData(GL_ARRAY_BUFFER, textCoordsBuffer, GL_STATIC_DRAW);
			glEnableVertexAttribArray(4);
			glVertexAttribPointer(4, 2, GL_FLOAT, false, 0, 0);

			// Bone weights
			vboId = glGenBuffers();
			vboIdList.add(vboId);
			FloatBuffer weightsBuffer =
				MemoryUtil.memAllocFloat(meshData.getWeights().length);
			weightsBuffer.put(meshData.getWeights()).flip();
			glBindBuffer(GL_ARRAY_BUFFER, vboId);
			glBufferData(GL_ARRAY_BUFFER, weightsBuffer, GL_STATIC_DRAW);
			glEnableVertexAttribArray(5);
			glVertexAttribPointer(5, 4, GL_FLOAT, false, 0, 0);

			// Bone indices
			vboId = glGenBuffers();
			vboIdList.add(vboId);
			IntBuffer boneIndicesBuffer =
				MemoryUtil.memAllocInt(meshData.getBoneIndices().length);
			boneIndicesBuffer.put(meshData.getBoneIndices()).flip();
			glBindBuffer(GL_ARRAY_BUFFER, vboId);
			glBufferData(GL_ARRAY_BUFFER, boneIndicesBuffer, GL_STATIC_DRAW);
			glEnableVertexAttribArray(6);
			glVertexAttribPointer(6, 4, GL_FLOAT, false, 0, 0);

			// Index VBO
			vboId = glGenBuffers();
			vboIdList.add(vboId);
			IntBuffer indicesBuffer =
				stack.callocInt(meshData.getIndices().length);
			indicesBuffer.put(meshData.getIndices());
			glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboId);
			glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer,
				GL_STATIC_DRAW);

			glBindBuffer(GL_ARRAY_BUFFER, 0);
			glBindVertexArray(0);
		}
	}

	/**
	 * Clean the mesh up.
	 */
	public void cleanup() {
		vboIdList.stream().forEach(GL15::glDeleteBuffers);
		glDeleteVertexArrays(vaoId);
	}

}
