package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.Mesh;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.graph.TextureCache;

import lombok.Getter;
import lombok.NonNull;

/**
 * A sky box that provides a backdrop for the environment.
 */
@Getter
public class SkyBox {
	/**
	 * The material used to render the skybox.
	 *
	 * @return The material.
	 */
	private Material material;
	/**
	 * The mesh used to render the skybox.
	 *
	 * @return The mesh.
	 */
	private Mesh mesh;
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
	 * Create a new skybox.
	 *
	 * @param skyBoxModelPath The path to the model from the resource directory.
	 * @param textureCache The texture cache to use.
	 * @param materialCache The texture cache to use.
	 */
	public SkyBox(@NonNull String skyBoxModelPath,
		@NonNull TextureCache textureCache,
		@NonNull MaterialCache materialCache) {
		this.skyBoxModel = ModelLoader.loadModel("skybox-model",
			skyBoxModelPath, textureCache, materialCache, false);
		MeshData meshData = this.skyBoxModel.getMeshDataList().get(0);
		this.material = materialCache.getMaterial(meshData.getMaterialIndex());
		this.mesh = new Mesh(meshData);
		this.skyBoxModel.getMeshDataList().clear();
		this.skyBoxEntity =
			new Entity("skyBoxEntity-entity", this.skyBoxModel.getId());
	}

	/**
	 * Clean up the mesh.
	 */
	public void cleanuo() {
		this.mesh.cleanup();
	}
}