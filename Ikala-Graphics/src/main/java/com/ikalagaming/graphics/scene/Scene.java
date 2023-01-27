/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.GuiInstance;
import com.ikalagaming.graphics.exceptions.ModelException;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.graph.TextureCache;
import com.ikalagaming.graphics.scene.lights.SceneLights;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A scene to be rendered, containing the items and lighting.
 */
@Getter
@Slf4j
public class Scene {
	/**
	 * The camera for the scene.
	 *
	 * @return The scenes camera.
	 */
	private Camera camera;
	/**
	 * The fog for the scene.
	 *
	 * @param fog The new fog settings to use.
	 * @return The current fog settings.
	 */
	@Setter
	@NonNull
	private Fog fog;
	/**
	 * The GUI instance to use for this scene.
	 *
	 * @param guiInstance The new GUI to use.
	 * @return The current GUI.
	 */
	@Setter
	private GuiInstance guiInstance;
	/**
	 * The material cache to use for this scene.
	 *
	 * @return The material cache.
	 */
	private MaterialCache materialCache;
	/**
	 * The list of models, mapped by ID.
	 *
	 */
	private Map<String, Model> modelMap;
	/**
	 * The projection matrix for the scene.
	 *
	 * @return The projection matrix information.
	 */
	private Projection projection;
	/**
	 * The scene lighting information.
	 *
	 * @param sceneLights The lights to render with.
	 * @return The lights used in the scene.
	 */
	@Setter
	@NonNull
	private SceneLights sceneLights;
	/**
	 * The backdrop for the scene.
	 *
	 * @param skyBox The new skybox.
	 * @return The skybox to draw.
	 */
	@Setter
	@NonNull
	private SkyBox skyBox;
	/**
	 * The texture cache to use for this scene.
	 *
	 * @return The texture cache.
	 */
	private TextureCache textureCache;

	/**
	 * Set up a new scene.
	 *
	 * @param width The screen width, in pixels.
	 * @param height The screen height, in pixels.
	 */
	public Scene(int width, int height) {
		this.modelMap = new ConcurrentHashMap<>();
		this.projection = new Projection(width, height);
		this.textureCache = new TextureCache();
		this.materialCache = new MaterialCache();
		this.camera = new Camera();
		this.fog = new Fog();
	}

	/**
	 * Add an entity to the scene.
	 *
	 * @param entity The entity to add.
	 * @throws RuntimeException If the entity does not have a valid model ID
	 *             that exists in the model map.
	 * @see #addModel(Model)
	 */
	public void addEntity(@NonNull Entity entity) {
		String modelId = entity.getModelID();
		Model model = this.modelMap.get(modelId);
		if (model == null) {
			String error = SafeResourceLoader.getString("MODEL_MISSING",
				GraphicsPlugin.getResourceBundle());
			Scene.log.info(error, modelId);
			throw new ModelException(
				error.replaceFirst("\\{\\}", "" + modelId));
		}
		model.getEntitiesList().add(entity);
	}

	/**
	 * Add a model to the model map.
	 *
	 * @param model The model to add.
	 */
	public void addModel(@NonNull Model model) {
		this.modelMap.put(model.getId(), model);
	}

	/**
	 * Update the projection matrix for when the screen is resized.
	 *
	 * @param width The new screen width, in pixels.
	 * @param height The new screen height, in pixels.
	 */
	public void resize(int width, int height) {
		this.projection.updateProjMatrix(width, height);
	}
}
