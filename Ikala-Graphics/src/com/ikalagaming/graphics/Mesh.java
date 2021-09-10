package com.ikalagaming.graphics;

import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

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
	private final int indexVboId;
	private final int colorVboId;

	@Getter
	private final int vertexCount;

	/**
	 * Create a new mesh given the vertices.
	 *
	 * @param positions The vertices to create a mesh with.
	 * @param colors The color of each coordinate.
	 * @param indices The indexes of positions to draw.
	 */
	public Mesh(float[] positions, float[] colors, int[] indices) {
		FloatBuffer verticesBuffer = null;
		IntBuffer indicesBuffer = null;

		try {
			verticesBuffer = MemoryUtil.memAllocFloat(positions.length);
			this.vertexCount = indices.length;
			verticesBuffer.put(positions).flip();

			this.vaoId = GL30.glGenVertexArrays();
			GL30.glBindVertexArray(this.vaoId);

			// Position VBO
			this.vboId = GL15.glGenBuffers();
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboId);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

			// Index VBO
			indexVboId = GL15.glGenBuffers();
			indicesBuffer = MemoryUtil.memAllocInt(indices.length);
			indicesBuffer.put(indices).flip();
			GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexVboId);
			GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer,
				GL15.GL_STATIC_DRAW);
			MemoryUtil.memFree(indicesBuffer);

			// Color VBO
			colorVboId = GL15.glGenBuffers();
			FloatBuffer colorBuffer = MemoryUtil.memAllocFloat(colors.length);
			colorBuffer.put(colors).flip();
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, colorVboId);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, colorBuffer,
				GL15.GL_STATIC_DRAW);
			MemoryUtil.memFree(colorBuffer);
			GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 0, 0);

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

		// Delete the VBOs
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL15.glDeleteBuffers(this.vboId);
		GL15.glDeleteBuffers(this.indexVboId);
		GL15.glDeleteBuffers(this.colorVboId);
		
		// Delete the VAO
		GL30.glBindVertexArray(0);
		GL30.glDeleteVertexArrays(this.vaoId);
	}
}
