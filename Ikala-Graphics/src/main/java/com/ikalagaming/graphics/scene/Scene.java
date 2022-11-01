package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.graph.Mesh;
import com.ikalagaming.graphics.scene.lights.SceneLight;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A scene to be rendered, containing the items and lighting.
 *
 * @author Ches Burks
 *
 */
@Getter
@Setter
public class Scene {
	/**
	 * The models to render in the scene.
	 *
	 * @param sceneItems The items to render.
	 * @return The list of items to render.
	 */
	private SceneItem[] sceneItems;
	/**
	 * The backdrop for the scene.
	 *
	 * @param skyBox The new skybox.
	 * @return The skybox to draw.
	 */
	private SkyBox skyBox;
	/**
	 * The scene lighting information.
	 *
	 * @param sceneLight The lights to render with.
	 * @return The lights used in the scene.
	 */
	private SceneLight sceneLight;

	/**
	 * The fog for the scene.
	 *
	 * @param fog The new fog settings to use.
	 * @return The current fog settings.
	 */
	private Fog fog;

	/**
	 * The scene item meshes.
	 *
	 * @param The new map of meshes.
	 * @return The mapping of meshes to scene items.
	 */
	private Map<Mesh, List<SceneItem>> meshMap;

	/**
	 * Set up a new scene.
	 */
	public Scene() {
		this.meshMap = new HashMap<>();
	}

	/**
	 * Set the scene items and construct a mesh map.
	 *
	 * @param gameItems The list of items to render.
	 */
	public void setSceneItems(SceneItem[] gameItems) {
		if (gameItems == null) {
			return;
		}
		for (int i = 0; i < gameItems.length; i++) {
			SceneItem gameItem = gameItems[i];
			Mesh mesh = gameItem.getMesh();
			List<SceneItem> list = this.meshMap.get(mesh);
			if (list == null) {
				list = new ArrayList<>();
				this.meshMap.put(mesh, list);
			}
			list.add(gameItem);
		}
	}
}
