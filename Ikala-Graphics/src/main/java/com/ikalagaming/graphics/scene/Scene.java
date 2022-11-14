package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.GuiInstance;
import com.ikalagaming.graphics.exceptions.ModelException;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.graph.TextureCache;
import com.ikalagaming.graphics.scene.lights.SceneLights;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * A scene to be rendered, containing the items and lighting.
 */
@Getter
public class Scene {

	/**
	 * The camera for the scene.
	 * 
	 * @return The scenes camera.
	 */
	private Camera camera;
	/**
	 * The backdrop for the scene.
	 *
	 * @param skyBox The new skybox.
	 * @return The skybox to draw.
	 */
	@Setter
	private SkyBox skyBox;
	/**
	 * The scene lighting information.
	 *
	 * @param sceneLights The lights to render with.
	 * @return The lights used in the scene.
	 */
	@Setter
	private SceneLights sceneLights;
	/**
	 * The material cache to use for this scene.
	 * 
	 * @return The material cache.
	 */
	private MaterialCache materialCache;
	/**
	 * The GUI instance to use for this scene.
	 * 
	 * @param guiInstance The new GUI to use.
	 * @return The current GUI.
	 */
	@Setter
	private GuiInstance guiInstance;
	/**
	 * The texture cache to use for this scene.
	 * 
	 * @return The texture cache.
	 */
	private TextureCache textureCache;
	/**
	 * The projection matrix for the scene.
	 * 
	 * @return The projection matrix information.
	 */
	private Projection projection;
	/**
	 * The fog for the scene.
	 *
	 * @param fog The new fog settings to use.
	 * @return The current fog settings.
	 */
	@Setter
	private Fog fog;
	/**
	 * The list of models, mapped by ID.
	 * 
	 */
	private Map<String, Model> modelMap;

	/**
	 * Set up a new scene.
	 * 
	 * @param width The screen width, in pixels.
	 * @param height The screen height, in pixels.
	 */
	public Scene(int width, int height) {
		modelMap = new HashMap<>();
		projection = new Projection(width, height);
		textureCache = new TextureCache();
		materialCache = new MaterialCache();
		camera = new Camera();
		fog = new Fog();
	}

	/**
	 * Add an entity to the scene.
	 * 
	 * @param entity The entity to add.
	 * @throws ModelException If the entity does not have a valid model ID that
	 *             exists in the model map.
	 * @see #addModel(Model)
	 */
	public void addEntity(Entity entity) {
		String modelId = entity.getModelId();
		Model model = modelMap.get(modelId);
		if (model == null) {
			throw new ModelException("Could not find model [" + modelId + "]");
		}
		model.getEntitiesList().add(entity);
	}

	/**
	 * Add a model to the model map.
	 * 
	 * @param model The model to add.
	 */
	public void addModel(Model model) {
		modelMap.put(model.getId(), model);
	}

	/**
	 * Update the projection matrix for when the screen is resized.
	 * 
	 * @param width The new screen width, in pixels.
	 * @param height The new screen height, in pixels.
	 */
	public void resize(int width, int height) {
		projection.updateProjMatrix(width, height);
	}
}
