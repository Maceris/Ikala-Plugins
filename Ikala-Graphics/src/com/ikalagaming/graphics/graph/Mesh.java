package com.ikalagaming.graphics.graph;

import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Given an array of positions, sets up data to load into graphics card.
 *
 * @author Ches Burks
 *
 */
public class Mesh {

	/**
	 * A list of vertex buffer objects for this mesh.
	 */
	private List<Integer> vboIdList;

	@Getter
	private final int vaoId;

	@Getter
	private final int vertexCount;

	private Texture texture;

	/**
	 * Create a new mesh given the vertices.
	 *
	 * @param positions The vertices to create a mesh with.
	 * @param textCoords The texture coordinates for each position.
	 * @param indices The indexes of positions to draw.
	 * @param texture The texture to use.
	 */
	public Mesh(float[] positions, float[] textCoords, int[] indices,
		Texture texture) {
		FloatBuffer verticesBuffer = null;
		IntBuffer indicesBuffer = null;
		FloatBuffer textCoordsBuffer = null;

		this.vboIdList = new ArrayList<>();
		this.texture = texture;

		try {
			verticesBuffer = MemoryUtil.memAllocFloat(positions.length);
			this.vertexCount = indices.length;
			verticesBuffer.put(positions).flip();

			this.vaoId = GL30.glGenVertexArrays();
			GL30.glBindVertexArray(this.vaoId);

			// Position VBO
			int positionVBO = GL15.glGenBuffers();
			this.vboIdList.add(positionVBO);
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionVBO);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

			// Index VBO
			int indexVboId = GL15.glGenBuffers();
			this.vboIdList.add(indexVboId);
			indicesBuffer = MemoryUtil.memAllocInt(indices.length);
			indicesBuffer.put(indices).flip();
			GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexVboId);
			GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer,
				GL15.GL_STATIC_DRAW);

			// Texture VBO
			int textureVboId = GL15.glGenBuffers();
			this.vboIdList.add(textureVboId);
			textCoordsBuffer = MemoryUtil.memAllocFloat(textCoords.length);
			textCoordsBuffer.put(textCoords).flip();
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, textureVboId);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, textCoordsBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glEnableVertexAttribArray(1);
			GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 0, 0);

			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
			GL30.glBindVertexArray(0);
		}
		finally {
			if (verticesBuffer != null) {
				MemoryUtil.memFree(verticesBuffer);
			}
			if (indicesBuffer != null) {
				MemoryUtil.memFree(indicesBuffer);
			}
			if (textCoordsBuffer != null) {
				MemoryUtil.memFree(textCoordsBuffer);
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
		this.vboIdList.forEach(GL15::glDeleteBuffers);
		this.vboIdList.clear();

		// Delete the VAO
		GL30.glBindVertexArray(0);
		GL30.glDeleteVertexArrays(this.vaoId);
	}

	/**
	 * Render the mesh.
	 */
	public void render() {
		// Activate first texture unit
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		// Bind the texture
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture.getId());

		// Draw the mesh
		GL30.glBindVertexArray(this.getVaoId());
		GL20.glEnableVertexAttribArray(0);
		GL20.glEnableVertexAttribArray(1);

		GL11.glDrawElements(GL11.GL_TRIANGLES, this.getVertexCount(),
			GL11.GL_UNSIGNED_INT, 0);

		// Restore state
		GL20.glDisableVertexAttribArray(0);
		GL20.glDisableVertexAttribArray(1);
		GL30.glBindVertexArray(0);
	}
}
