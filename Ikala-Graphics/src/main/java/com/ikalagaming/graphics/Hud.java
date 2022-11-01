package com.ikalagaming.graphics;

import com.ikalagaming.graphics.scene.SceneItem;

/**
 * An interface that defines how to draw a Heads Up Display on the screen.
 *
 * @author Ches Burks
 *
 */
public interface Hud {
	/**
	 * Clean up the HUD items.
	 */
	default void cleanup() {
		SceneItem[] sceneItems = this.getSceneItems();
		for (SceneItem item : sceneItems) {
			item.getMesh().cleanUp();
		}
	}

	/**
	 * The scene items to display as part of the HUD.
	 *
	 * @return The list of scene items to display.
	 */
	SceneItem[] getSceneItems();
}
