package com.ikalagaming.graphics.graph;

import imgui.ImDrawData;
import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * A class used to model imgui meshes.
 */
@Getter
public class GuiMesh {
	/**
	 * The indices VBO for this mesh.
	 *
	 * @return The indices VBO.
	 */
	private int indicesVBO;
	/**
	 * The vertex array object ID.
	 *
	 * @return The VAO ID.
	 */
	private int vaoID;
	/**
	 * The vertices VBO for this mesh.
	 *
	 * @return The vertices VBO.
	 */
	private int verticesVBO;

	/**
	 * Create a new mesh and set it up for use with imgui.
	 */
	public GuiMesh() {
		this.vaoID = GL30.glGenVertexArrays();
		GL30.glBindVertexArray(this.vaoID);

		// Single VBO
		this.verticesVBO = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.verticesVBO);
		GL20.glEnableVertexAttribArray(0);
		GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false,
			ImDrawData.SIZEOF_IM_DRAW_VERT, 0);
		GL20.glEnableVertexAttribArray(1);
		GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false,
			ImDrawData.SIZEOF_IM_DRAW_VERT, 8);
		GL20.glEnableVertexAttribArray(2);
		GL20.glVertexAttribPointer(2, 4, GL11.GL_UNSIGNED_BYTE, true,
			ImDrawData.SIZEOF_IM_DRAW_VERT, 16);

		this.indicesVBO = GL15.glGenBuffers();

		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL30.glBindVertexArray(0);
	}

	/**
	 * Clean up all the buffers and arrays for this mesh.
	 */
	public void cleanup() {
		GL15.glDeleteBuffers(this.indicesVBO);
		GL15.glDeleteBuffers(this.verticesVBO);
		GL30.glDeleteVertexArrays(this.vaoID);
	}

}