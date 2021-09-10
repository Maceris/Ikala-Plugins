package com.ikalagaming.graphics;

import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/**
 * Given an array of positions, sets up data to load into graphics card.
 *
 * @author Ches Burks
 *
 */
public class Mesh {
	@Getter
	private final int vaoId;

	private final int vboId;

	@Getter
	private final int vertexCount;

	/**
	 * Create a new mesh given the vertices.
	 *
	 * @param positions The vertices to create a mesh with.
	 */
	public Mesh(float[] positions) {
		FloatBuffer verticesBuffer = null;
		try {
			verticesBuffer = MemoryUtil.memAllocFloat(positions.length);
			this.vertexCount = positions.length / 3;
			verticesBuffer.put(positions).flip();

			this.vaoId = GL30.glGenVertexArrays();
			GL30.glBindVertexArray(this.vaoId);

			this.vboId = GL15.glGenBuffers();
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboId);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

			GL30.glBindVertexArray(0);
		}
		finally {
			if (verticesBuffer != null) {
				MemoryUtil.memFree(verticesBuffer);
			}
		}
	}

	/**
	 * Clean the mesh up.
	 */
	public void cleanUp() {
		GL20.glDisableVertexAttribArray(0);

		// Delete the VBO
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL15.glDeleteBuffers(this.vboId);

		// Delete the VAO
		GL30.glBindVertexArray(0);
		GL30.glDeleteVertexArrays(this.vaoId);
	}
}
