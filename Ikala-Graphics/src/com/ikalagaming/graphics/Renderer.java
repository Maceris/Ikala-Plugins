package com.ikalagaming.graphics;

import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.graphics.graph.Camera;
import com.ikalagaming.graphics.graph.DirectionalLight;
import com.ikalagaming.graphics.graph.Mesh;
import com.ikalagaming.graphics.graph.PointLight;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.Transformation;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.util.FileUtils;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

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
	 * Distance from the camera to the far plane.
	 */
	private static final float Z_FAR = 1000.f;
	/**
	 * Distance from the camera to the near plane.
	 */
	private static final float Z_NEAR = 0.01f;

	private float specularPower;

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
		this.specularPower = 10f;
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

		this.shaderProgram
			.createUniform(ShaderConstants.Uniform.Vertex.PROJECTION_MATRIX);
		this.shaderProgram
			.createUniform(ShaderConstants.Uniform.Vertex.MODEL_VIEW_MATRIX);
		this.shaderProgram
			.createUniform(ShaderConstants.Uniform.Fragment.TEXTURE_SAMPLER);

		// Create uniform for material
		this.shaderProgram
			.createMaterialUniform(ShaderConstants.Uniform.Fragment.MATERIAL);

		// Create lighting related uniforms
		this.shaderProgram
			.createUniform(ShaderConstants.Uniform.Fragment.SPECULAR_POWER);
		this.shaderProgram
			.createUniform(ShaderConstants.Uniform.Fragment.AMBIENT_LIGHT);
		this.shaderProgram.createPointLightUniform(
			ShaderConstants.Uniform.Fragment.POINT_LIGHT);
		this.shaderProgram.createDirectionalLightUniform(
			ShaderConstants.Uniform.Fragment.DIRECTIONAL_LIGHT);
	}

	/**
	 * Render an array of scene items.
	 *
	 * @param window The window to render on.
	 * @param camera The camera.
	 * @param sceneItems The items to render.
	 * @param ambientLight The ambient light color.
	 * @param pointLight A point light to use.
	 * @param directionalLight A directional light to use.
	 */
	public void render(Window window, Camera camera, List<SceneItem> sceneItems,
		Vector3f ambientLight, PointLight pointLight,
		DirectionalLight directionalLight) {

		this.shaderProgram.bind();

		// Update the projection matrix
		Matrix4f projectionMatrix = this.transformation.getProjectionMatrix(
			Renderer.FOV, window.getWidth(), window.getHeight(),
			Renderer.Z_NEAR, Renderer.Z_FAR);
		this.shaderProgram.setUniform(
			ShaderConstants.Uniform.Vertex.PROJECTION_MATRIX, projectionMatrix);

		// Update the view matrix
		Matrix4f viewMatrix = this.transformation.getViewMatrix(camera);

		// Update Light Uniforms
		this.shaderProgram.setUniform(
			ShaderConstants.Uniform.Fragment.AMBIENT_LIGHT, ambientLight);
		this.shaderProgram.setUniform(
			ShaderConstants.Uniform.Fragment.SPECULAR_POWER,
			this.specularPower);

		/*
		 * Get a copy of the light object and transform its position to view
		 * coordinates
		 */
		PointLight currPointLight = new PointLight(pointLight);
		Vector3f lightPos = currPointLight.getPosition();
		Vector4f aux = new Vector4f(lightPos, 1);
		aux.mul(viewMatrix);
		lightPos.x = aux.x;
		lightPos.y = aux.y;
		lightPos.z = aux.z;
		this.shaderProgram.setUniform(
			ShaderConstants.Uniform.Fragment.POINT_LIGHT, currPointLight);

		/*
		 * Get a copy of the directional light object and transform its position
		 * to view coordinates
		 */
		DirectionalLight currDirLight = new DirectionalLight(directionalLight);
		Vector4f dir = new Vector4f(currDirLight.getDirection(), 0);
		dir.mul(viewMatrix);
		currDirLight.setDirection(new Vector3f(dir.x, dir.y, dir.z));
		this.shaderProgram.setUniform(
			ShaderConstants.Uniform.Fragment.DIRECTIONAL_LIGHT, currDirLight);

		this.shaderProgram
			.setUniform(ShaderConstants.Uniform.Fragment.TEXTURE_SAMPLER, 0);

		for (SceneItem sceneItem : sceneItems) {
			// Set model view matrix for this item
			Matrix4f modelViewMatrix =
				this.transformation.getModelViewMatrix(sceneItem, viewMatrix);
			this.shaderProgram.setUniform(
				ShaderConstants.Uniform.Vertex.MODEL_VIEW_MATRIX,
				modelViewMatrix);

			// Render the mesh for this scene item
			Mesh mesh = sceneItem.getMesh();
			this.shaderProgram.setUniform(
				ShaderConstants.Uniform.Fragment.MATERIAL, mesh.getMaterial());
			mesh.render();
		}

		this.shaderProgram.unbind();
	}
}
