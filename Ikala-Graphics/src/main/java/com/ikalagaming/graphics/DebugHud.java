package com.ikalagaming.graphics;

import com.ikalagaming.graphics.graph.FontTexture;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.Mesh;
import com.ikalagaming.graphics.scene.SceneItem;

import lombok.Getter;
import org.joml.Vector4f;

import java.awt.Font;

/**
 * An example HUD.
 *
 * @author Ches Burks
 *
 */
public class DebugHud implements Hud {

	private static final Font FONT = new Font("Arial", Font.PLAIN, 10);
	private static final String CHARSET = "ISO-8859-1";

	@Getter
	private final SceneItem[] sceneItems;
	private final TextItem fpsTextItem;
	private final TextItem cameraPosTextItem;
	private final TextItem cameraSpeedTextItem;
	private final SceneItem compassItem;

	/**
	 * Create a new HUD with status text.
	 *
	 * @throws Exception If an exception occurs.
	 */
	public DebugHud() throws Exception {
		FontTexture fontTexture =
			new FontTexture(DebugHud.FONT, DebugHud.CHARSET);
		this.fpsTextItem = new TextItem("FPS: ?", fontTexture);
		this.fpsTextItem.getMesh().getMaterial()
			.setAmbientColor(new Vector4f(1, 1, 1, 1));

		this.cameraPosTextItem = new TextItem("Camera Pos: ?", fontTexture);
		this.cameraPosTextItem.getMesh().getMaterial()
			.setAmbientColor(new Vector4f(1, 1, 1, 1));

		this.cameraSpeedTextItem = new TextItem("Camera Speed: ?", fontTexture);
		this.cameraSpeedTextItem.getMesh().getMaterial()
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
		this.sceneItems = new SceneItem[] {this.fpsTextItem, this.compassItem,
			this.cameraPosTextItem, this.cameraSpeedTextItem};
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
	 * Set the camera position text.
	 *
	 * @param positionText The text to display for the camera position.
	 */
	public void setCameraPosition(String positionText) {
		this.cameraPosTextItem.setText(positionText);
	}

	/**
	 * Sets the camera speed text.
	 *
	 * @param speedText The text to display for the camera speed.
	 */
	public void setCameraSpeed(String speedText) {
		this.cameraSpeedTextItem.setText(speedText);
	}

	/**
	 * Set the status text.
	 *
	 * @param fpsText The new text to display.
	 */
	public void setFPS(String fpsText) {
		this.fpsTextItem.setText(fpsText);
	}

	/**
	 * Update the UI based on window size.
	 *
	 * @param window The window we are drawing on.
	 */
	public void updateSize(Window window) {
		this.fpsTextItem.setPosition(10f, 10f, 0);
		this.cameraPosTextItem.setPosition(10f, 30f, 0);
		this.cameraSpeedTextItem.setPosition(10f, 50f, 0);
		this.compassItem.setPosition(window.getWidth() - 40f, 50f, 0);
	}

}
