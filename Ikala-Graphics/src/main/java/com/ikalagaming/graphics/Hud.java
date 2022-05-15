package com.ikalagaming.graphics;

/**
 * An interface that defines how to draw a Heads Up Display on the screen.
 * 
 * @author Ches Burks
 *
 */
public interface Hud {
	/**
	 * The scene items to display as part of the HUD.
	 * 
	 * @return The list of scene items to display.
	 */
	SceneItem[] getSceneItems();

	/**
	 * Clean up the HUD items.
	 */
	default void cleanup() {
		SceneItem[] sceneItems = getSceneItems();
		for (SceneItem item : sceneItems) {
			item.getMesh().cleanUp();
		}
	}
}
