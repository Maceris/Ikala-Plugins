package com.ikalagaming.graphics;

import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.graphics.graph.Camera;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.Transformation;
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
	 * The name of the model view matrix uniform.
	 */
	private static final String MODEL_VIEW_MATRIX = "modelViewMatrix";

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
			this.shaderProgram.createVertexShader(FileUtils.readAsString(
				PluginFolder.getResource(GraphicsPlugin.PLUGIN_NAME,
					ResourceType.DATA, "vertex.vs")));
			this.shaderProgram.createFragmentShader(FileUtils.readAsString(
				PluginFolder.getResource(GraphicsPlugin.PLUGIN_NAME,
					ResourceType.DATA, "fragment.fs")));
		}
		catch (ShaderException e) {
			Renderer.log.error(SafeResourceLoader.getString(
				"SHADER_ERROR_SETUP", GraphicsPlugin.getResourceBundle()), e);
		}
		this.shaderProgram.link();
		this.shaderProgram.createUniform(Renderer.PROJECTION_MATRIX);
		this.shaderProgram.createUniform(Renderer.MODEL_VIEW_MATRIX);
		this.shaderProgram.createUniform("texture_sampler");
	}

	/**
	 * Render an array of scene items.
	 *
	 * @param window The window to render on.
	 * @param camera The camera.
	 *
	 * @param sceneItems The items to render.
	 */
	public void render(Window window, Camera camera,
		List<SceneItem> sceneItems) {

		this.shaderProgram.bind();

		// Update the projection matrix
		Matrix4f projectionMatrix = this.transformation.getProjectionMatrix(
			Renderer.FOV, window.getWidth(), window.getHeight(),
			Renderer.Z_NEAR, Renderer.Z_FAR);
		this.shaderProgram.setUniform(Renderer.PROJECTION_MATRIX,
			projectionMatrix);

		// Update the view matrix
		Matrix4f viewMatrix = this.transformation.getViewMatrix(camera);
		this.shaderProgram.setUniform("texture_sampler", 0);

		for (SceneItem gameItem : sceneItems) {
			// Set model view matrix for this item
			Matrix4f modelViewMatrix =
				this.transformation.getModelViewMatrix(gameItem, viewMatrix);
			this.shaderProgram.setUniform(Renderer.MODEL_VIEW_MATRIX,
				modelViewMatrix);

			// Render the mesh for this game item
			gameItem.getMesh().render();
		}

		this.shaderProgram.unbind();
	}
}
