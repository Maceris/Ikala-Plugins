package com.ikalagaming.graphics.graph;

import lombok.Getter;
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
 * Defines a quad that is used to render in the lighting pass.
 */
public class QuadMesh {
	/**
	 * The number of vertices in the mesh.
	 *
	 * @return The vertex count.
	 */
	@Getter
	private int vertexCount;
	/**
	 * The Vertex Array Object for the mesh.
	 *
	 * @return The VAO.
	 */
	@Getter
	private int vaoID;
	/**
	 * The list of VBOs created so we can clean them up.
	 */
	private List<Integer> vboIDList;

	/**
	 * Create a new quad mesh.
	 */
	public QuadMesh() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			this.vboIDList = new ArrayList<>();
			float[] positions = new float[] {-1.0f, 1.0f, 0.0f, 1.0f, 1.0f,
				0.0f, -1.0f, -1.0f, 0.0f, 1.0f, -1.0f, 0.0f,};
			float[] textCoords =
				new float[] {0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f,};
			int[] indices = new int[] {0, 2, 1, 1, 2, 3};
			this.vertexCount = indices.length;

			this.vaoID = GL30.glGenVertexArrays();
			GL30.glBindVertexArray(this.vaoID);

			// Positions VBO
			int vboId = GL15.glGenBuffers();
			this.vboIDList.add(vboId);
			FloatBuffer positionsBuffer = stack.callocFloat(positions.length);
			positionsBuffer.put(0, positions);
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, positionsBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glEnableVertexAttribArray(0);
			GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);

			// Texture coordinates VBO
			vboId = GL15.glGenBuffers();
			this.vboIDList.add(vboId);
			FloatBuffer textCoordsBuffer =
				MemoryUtil.memAllocFloat(textCoords.length);
			textCoordsBuffer.put(0, textCoords);
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, textCoordsBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glEnableVertexAttribArray(1);
			GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 0, 0);

			// Index VBO
			vboId = GL15.glGenBuffers();
			this.vboIDList.add(vboId);
			IntBuffer indicesBuffer = stack.callocInt(indices.length);
			indicesBuffer.put(0, indices);
			GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, vboId);
			GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer,
				GL15.GL_STATIC_DRAW);

			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
			GL30.glBindVertexArray(0);
		}
	}

	/**
	 * Clean up the buffers.
	 */
	public void cleanup() {
		this.vboIDList.stream().forEach(GL15::glDeleteBuffers);
		GL30.glDeleteVertexArrays(this.vaoID);
	}
}