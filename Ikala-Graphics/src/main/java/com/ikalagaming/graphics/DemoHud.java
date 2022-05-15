package com.ikalagaming.graphics;

import com.ikalagaming.graphics.graph.FontTexture;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.Mesh;

import org.joml.Vector4f;

import java.awt.Font;

/**
 * An example HUD.
 *
 * @author Ches Burks
 *
 */
public class DemoHud implements Hud {

	private static final Font FONT = new Font("Arial", Font.PLAIN, 20);
	private static final String CHARSET = "ISO-8859-1";

	private final SceneItem[] gameItems;
	private final TextItem statusTextItem;
	private final SceneItem compassItem;

	/**
	 * Create a new HUD with status text.
	 *
	 * @param statusText The text to display on the screen.
	 * @throws Exception If an exception occurs.
	 */
	public DemoHud(String statusText) throws Exception {
		FontTexture fontTexture =
			new FontTexture(DemoHud.FONT, DemoHud.CHARSET);
		this.statusTextItem = new TextItem(statusText, fontTexture);
		this.statusTextItem.getMesh().getMaterial()
			.setAmbientColor(new Vector4f(1, 1, 1, 1));

		// Create compass
		Mesh mesh = OBJLoader.loadMesh("/models/compass.obj");
		Material material = new Material();
		material.setAmbientColor(new Vector4f(1, 0, 0, 1));
		mesh.setMaterial(material);
		this.compassItem = new SceneItem(mesh);
		this.compassItem.setScale(40.0f);
		// Rotate to transform it to screen coordinates
		this.compassItem.setRotation(0f, 0f, 180f);

		// Create list that holds the items that compose the HUD
		this.gameItems =
			new SceneItem[] {this.statusTextItem, this.compassItem};
	}

	@Override
	public SceneItem[] getSceneItems() {
		return this.gameItems;
	}

	/**
	 * Set the compass rotation angle.
	 *
	 * @param angle The new angle of the compass.
	 */
	public void rotateCompass(float angle) {
		this.compassItem.setRotation(0, 0, 180 + angle);
	}

	/**
	 * Set the status text.
	 *
	 * @param statusText The new text to display.
	 */
	public void setStatusText(String statusText) {
		this.statusTextItem.setText(statusText);
	}

	/**
	 * Update the UI based on window size.
	 *
	 * @param window The window we are drawing on.
	 */
	public void updateSize(Window window) {
		this.statusTextItem.setPosition(10f, window.getHeight() - 50f, 0);
		this.compassItem.setPosition(window.getWidth() - 40f, 50f, 0);
	}

}
