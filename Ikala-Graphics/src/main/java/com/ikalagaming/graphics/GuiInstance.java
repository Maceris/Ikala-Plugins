package com.ikalagaming.graphics;

import com.ikalagaming.graphics.scene.Scene;

/**
 * Defines a Graphical User Interface that we can interact with.
 */
public interface GuiInstance {
	/**
	 * Used to construct the GUI, where we define the window and widgets that
	 * will be used to construct the GUI meshes.
	 */
	void drawGui();

	/**
	 * Process event inputs and returns a boolean to state whether the input has
	 * been processed. For example, if we display an overlapping window we may
	 * not be interested in passing input to the underlying game. You can use
	 * the return value to control that.
	 * 
	 * @param scene The scene we are rendering.
	 * @param window The window we are using.
	 * @return Whether the input has been processed.
	 */
	boolean handleGuiInput(Scene scene, Window window);
}
