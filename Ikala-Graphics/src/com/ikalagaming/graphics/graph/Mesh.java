package com.ikalagaming.graphics.graph;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import lombok.Getter;
import org.lwjgl.opengl.GL15;
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

			this.vaoId = glGenVertexArrays();
			glBindVertexArray(this.vaoId);

			// Position VBO
			int positionVBO = glGenBuffers();
			this.vboIdList.add(positionVBO);
			glBindBuffer(GL_ARRAY_BUFFER, positionVBO);
			glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);
			glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
			glBindBuffer(GL_ARRAY_BUFFER, 0);

			// Index VBO
			int indexVboId = glGenBuffers();
			this.vboIdList.add(indexVboId);
			indicesBuffer = MemoryUtil.memAllocInt(indices.length);
			indicesBuffer.put(indices).flip();
			glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexVboId);
			glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer,
				GL_STATIC_DRAW);

			// Texture VBO
			int textureVboId = glGenBuffers();
			this.vboIdList.add(textureVboId);
			textCoordsBuffer = MemoryUtil.memAllocFloat(textCoords.length);
			textCoordsBuffer.put(textCoords).flip();
			glBindBuffer(GL_ARRAY_BUFFER, textureVboId);
			glBufferData(GL_ARRAY_BUFFER, textCoordsBuffer, GL_STATIC_DRAW);
			glEnableVertexAttribArray(1);
			glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);

			glBindBuffer(GL_ARRAY_BUFFER, 0);
			glBindVertexArray(0);
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
		glDisableVertexAttribArray(0);

		// Delete the VBOs
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		this.vboIdList.forEach(GL15::glDeleteBuffers);
		this.vboIdList.clear();

		// Delete the VAO
		glBindVertexArray(0);
		glDeleteVertexArrays(this.vaoId);
	}

	/**
	 * Render the mesh.
	 */
	public void render() {
		// Activate first texture unit
		glActiveTexture(GL_TEXTURE0);
		// Bind the texture
		glBindTexture(GL_TEXTURE_2D, this.texture.getId());

		// Draw the mesh
		glBindVertexArray(this.getVaoId());
		glEnableVertexAttribArray(0);
		glEnableVertexAttribArray(1);

		glDrawElements(GL_TRIANGLES, this.getVertexCount(), GL_UNSIGNED_INT, 0);

		// Restore state
		glDisableVertexAttribArray(0);
		glDisableVertexAttribArray(1);
		glBindVertexArray(0);
	}
}
