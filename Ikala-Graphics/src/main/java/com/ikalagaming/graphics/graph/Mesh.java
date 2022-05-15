package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.SceneItem;

import lombok.Getter;
import lombok.Setter;
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
import java.util.function.Consumer;

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
	 * The material for the mesh.
	 *
	 * @param material The new material to use.
	 * @return The current material for the mesh.
	 */
	@Getter
	@Setter
	private Material material;

	/**
	 * Create a new mesh given the vertices.
	 *
	 * @param positions The vertices to create a mesh with.
	 * @param textCoords The texture coordinates for each position.
	 * @param normals The normal array for the faces.
	 * @param indices The indexes of positions to draw.
	 */
	public Mesh(float[] positions, float[] textCoords, float[] normals,
		int[] indices) {
		FloatBuffer verticesBuffer = null;
		FloatBuffer textCoordsBuffer = null;
		FloatBuffer vecNormalsBuffer = null;
		IntBuffer indicesBuffer = null;

		try {
			this.vertexCount = indices.length;
			this.vboIdList = new ArrayList<>();

			this.vaoId = GL30.glGenVertexArrays();
			GL30.glBindVertexArray(this.vaoId);

			// Position VBO
			int vboID = GL15.glGenBuffers();
			this.vboIdList.add(vboID);
			verticesBuffer = MemoryUtil.memAllocFloat(positions.length);
			verticesBuffer.put(positions).flip();
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
			GL20.glEnableVertexAttribArray(0);
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

			// Texture coordinates VBO
			vboID = GL15.glGenBuffers();
			this.vboIdList.add(vboID);
			textCoordsBuffer = MemoryUtil.memAllocFloat(textCoords.length);
			textCoordsBuffer.put(textCoords).flip();
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, textCoordsBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glEnableVertexAttribArray(1);
			GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 0, 0);

			// Vertex normals VBO
			vboID = GL15.glGenBuffers();
			this.vboIdList.add(vboID);
			vecNormalsBuffer = MemoryUtil.memAllocFloat(normals.length);
			vecNormalsBuffer.put(normals).flip();
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vecNormalsBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glEnableVertexAttribArray(2);
			GL20.glVertexAttribPointer(2, 3, GL11.GL_FLOAT, false, 0, 0);

			// Index VBO
			vboID = GL15.glGenBuffers();
			this.vboIdList.add(vboID);
			indicesBuffer = MemoryUtil.memAllocInt(indices.length);
			indicesBuffer.put(indices).flip();
			GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, vboID);
			GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer,
				GL15.GL_STATIC_DRAW);

			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
			GL30.glBindVertexArray(0);

		}
		finally {
			if (verticesBuffer != null) {
				MemoryUtil.memFree(verticesBuffer);
			}
			if (textCoordsBuffer != null) {
				MemoryUtil.memFree(textCoordsBuffer);
			}
			if (vecNormalsBuffer != null) {
				MemoryUtil.memFree(vecNormalsBuffer);
			}
			if (indicesBuffer != null) {
				MemoryUtil.memFree(indicesBuffer);
			}

		}
	}

	/**
	 * Clean the mesh up.
	 */
	public void cleanUp() {
		GL20.glDisableVertexAttribArray(0);

		this.deleteBuffers();

		// Delete the texture
		Texture texture = this.material.getTexture();
		if (texture != null) {
			texture.cleanup();
		}

	}

	/**
	 * Delete the buffers for the mesh.
	 */
	public void deleteBuffers() {
		GL20.glDisableVertexAttribArray(0);

		// Delete the VBOs
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		for (int vboId : this.vboIdList) {
			GL15.glDeleteBuffers(vboId);
		}

		// Delete the VAO
		GL30.glBindVertexArray(0);
		GL30.glDeleteVertexArrays(this.vaoId);
	}

	private void endRender() {
		// Restore state
		GL30.glBindVertexArray(0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
	}

	private void initRender() {
		Texture texture = this.material.getTexture();
		if (texture != null) {
			// Activate first texture unit
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			// Bind the texture
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getId());
		}

		// Draw the mesh
		GL30.glBindVertexArray(this.getVaoId());
	}

	/**
	 * Render the mesh.
	 */
	public void render() {
		this.initRender();
		GL11.glDrawElements(GL11.GL_TRIANGLES, this.getVertexCount(),
			GL11.GL_UNSIGNED_INT, 0);
		this.endRender();
	}

	/**
	 * Render a list of scene items.
	 *
	 * @param sceneItems The items to render.
	 * @param consumer A method to set up data required by the items.
	 */
	public void renderList(List<SceneItem> sceneItems,
		Consumer<SceneItem> consumer) {
		this.initRender();

		for (SceneItem sceneItem : sceneItems) {
			// Set up data required by SceneItem
			consumer.accept(sceneItem);
			// Render this game item
			GL11.glDrawElements(GL11.GL_TRIANGLES, this.getVertexCount(),
				GL11.GL_UNSIGNED_INT, 0);
		}

		this.endRender();
	}
}
