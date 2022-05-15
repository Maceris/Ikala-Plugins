package com.ikalagaming.graphics;

import lombok.Getter;
import lombok.Setter;

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
}
