package com.ikalagaming.graphics;

import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.util.FileUtils;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders things on the screen.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class Renderer {

	/**
	 * Field of View in Radians.
	 */
	private static final float FOV = (float) Math.toRadians(60.0f);

	/**
	 * The name of the projection matrix uniform.
	 */
	private static final String PROJECTION_MATRIX = "projectionMatrix";

	/**
	 * The name of the world matrix uniform.
	 */
	private static final String WORLD_MATRIX = "worldMatrix";

	/**
	 * Distance from the camera to the far plane.
	 */
	private static final float Z_FAR = 1000.f;
	/**
	 * Distance from the camera to the near plane.
	 */
	private static final float Z_NEAR = 0.01f;

	private ShaderProgram shaderProgram;

	/**
	 * The transformation to use for the renderer.
	 */
	private Transformation transformation;

	/**
	 * Create a new renderer.
	 */
	public Renderer() {
		this.transformation = new Transformation();
	}

	/**
	 * Recalculate the projection matrix after the size of the window has
	 * changed.
	 *
	 * @return The newly calculated projection matrix.
	 */
	private Matrix4f calculateProjectionMatrix() {
		Window window = GraphicsManager.getWindow();
		return this.transformation.getProjectionMatrix(Renderer.FOV,
			window.getWidth(), window.getHeight(), Renderer.Z_NEAR,
			Renderer.Z_FAR);
	}

	/**
	 * Clean up resources.
	 */
	public void cleanup() {
		if (this.shaderProgram != null) {
			this.shaderProgram.cleanup();
		}
	}

	/**
	 * Initialize the renderer.
	 */
	public void init() {
		// Create a shader program
		this.shaderProgram = new ShaderProgram();
		try {
			this.shaderProgram.createVertexShader(
				FileUtils.readAsString(PluginFolder.getResource(
					GraphicsPlugin.NAME, ResourceType.DATA, "vertex.vs")));
			this.shaderProgram.createFragmentShader(
				FileUtils.readAsString(PluginFolder.getResource(
					GraphicsPlugin.NAME, ResourceType.DATA, "fragment.fs")));
		}
		catch (ShaderException e) {
			Renderer.log.error(SafeResourceLoader.getString(
				"SHADER_ERROR_SETUP", GraphicsPlugin.getResourceBundle()), e);
		}
		this.shaderProgram.link();
		this.shaderProgram.createUniform(Renderer.PROJECTION_MATRIX);
		this.shaderProgram.createUniform(Renderer.WORLD_MATRIX);
		this.shaderProgram.createUniform("texture_sampler");
	}

	/**
	 * Render an array of scene items.
	 *
	 * @param sceneItems The items to render.
	 */
	public void render(List<SceneItem> sceneItems) {
		this.shaderProgram.bind();

		this.shaderProgram.setUniform(Renderer.PROJECTION_MATRIX,
			this.calculateProjectionMatrix());
		for (SceneItem gameItem : sceneItems) {
			// Set world matrix for this item
			Matrix4f worldMatrix =
				this.transformation.getWorldMatrix(gameItem.getPosition(),
					gameItem.getRotation(), gameItem.getScale());
			this.shaderProgram.setUniform(Renderer.WORLD_MATRIX, worldMatrix);
			this.shaderProgram.setUniform("texture_sampler", 0);
			// Render the mesh for this game item
			gameItem.getMesh().render();
		}

		this.shaderProgram.unbind();
	}
}
