/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.graph.TextureCache;

import lombok.Getter;
import lombok.NonNull;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * A sky box that provides a backdrop for the environment.
 */
@Getter
public class SkyBox {
	/**
	 * The number of VBO's that we use. These represent: 1) positions 2) texture
	 * coordinates 3) indices
	 */
	private static final int VBO_COUNT = 3;

	/**
	 * The index of the material used to render the skybox.
	 *
	 * @return The material.
	 */
	private int materialIndex;
	/**
	 * The skybox entity.
	 *
	 * @return The entity.
	 */
	private Entity skyBoxEntity;

	/**
	 * The model for the skybox.
	 *
	 * @return The model.
	 */
	private Model skyBoxModel;

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
	private int[] vboIDList;

	/**
	 * The number of vertices in the mesh.
	 *
	 * @return The number of vertices.
	 */
	@Getter
	private int vertexCount;

	/**
	 * Create a new skybox.
	 *
	 * @param skyBoxModelPath The path to the model from the resource directory.
	 * @param textureCache The texture cache to use.
	 * @param materialCache The texture cache to use.
	 */
	public SkyBox(@NonNull String skyBoxModelPath,
		@NonNull TextureCache textureCache,
		@NonNull MaterialCache materialCache) {
		this.skyBoxModel =
			ModelLoader.loadModel(new ModelLoader.ModelLoadRequest(
				"skybox-model", GraphicsPlugin.PLUGIN_NAME, skyBoxModelPath,
				textureCache, materialCache, false));
		MeshData meshData = this.skyBoxModel.getMeshDataList().get(0);
		this.materialIndex = meshData.getMaterialIndex();
		setupBuffers(meshData);
		this.skyBoxModel.getMeshDataList().clear();
		this.skyBoxEntity =
			new Entity("skyBoxEntity-entity", this.skyBoxModel.getId());
	}

	/**
	 * Clean up the mesh.
	 */
	public void cleanuo() {
		GL15.glDeleteBuffers(vboIDList);
		GL30.glDeleteVertexArrays(this.vaoID);
	}

	/**
	 * Load the mesh data into VBOs.
	 * 
	 * @param meshData The data to load.
	 */
	private void setupBuffers(MeshData meshData) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			this.vertexCount = meshData.getIndices().length;
			this.vboIDList = new int[VBO_COUNT];

			this.vaoID = GL30.glGenVertexArrays();
			GL30.glBindVertexArray(this.vaoID);

			// Positions VBO
			GL15.glGenBuffers(vboIDList);

			final int vboPositions = vboIDList[0];
			final int vboTextureCoordinates = vboIDList[1];
			final int vboIndices = vboIDList[2];

			FloatBuffer positionsBuffer =
				stack.callocFloat(meshData.getPositions().length);
			positionsBuffer.put(0, meshData.getPositions());
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboPositions);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, positionsBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glEnableVertexAttribArray(0);
			GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);

			// Texture coordinates VBO
			FloatBuffer textCoordsBuffer =
				stack.callocFloat(meshData.getTextureCoordinates().length);
			textCoordsBuffer.put(0, meshData.getTextureCoordinates());
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboTextureCoordinates);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, textCoordsBuffer,
				GL15.GL_STATIC_DRAW);
			GL20.glEnableVertexAttribArray(1);
			GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 0, 0);

			IntBuffer indicesBuffer =
				stack.callocInt(meshData.getIndices().length);
			indicesBuffer.put(0, meshData.getIndices());
			GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, vboIndices);
			GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer,
				GL15.GL_STATIC_DRAW);

			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
			GL30.glBindVertexArray(0);
		}
	}
}