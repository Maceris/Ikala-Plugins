package com.ikalagaming.graphics;

import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.graphics.graph.Camera;
import com.ikalagaming.graphics.graph.DirectionalLight;
import com.ikalagaming.graphics.graph.Mesh;
import com.ikalagaming.graphics.graph.PointLight;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.SpotLight;
import com.ikalagaming.graphics.graph.Transformation;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.util.FileUtils;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
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
		this.shaderProgram.createPointLightListUniform(
			ShaderConstants.Uniform.Fragment.POINT_LIGHTS,
			ShaderConstants.Uniform.Fragment.MAX_POINT_LIGHTS);
		this.shaderProgram.createSpotLightListUniform(
			ShaderConstants.Uniform.Fragment.SPOT_LIGHTS,
			ShaderConstants.Uniform.Fragment.MAX_SPOT_LIGHTS);
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
	 * @param pointLightList A list of point lights to use.
	 * @param spotLightList A list of spot lights to use.
	 * @param directionalLight A directional light to use.
	 */
	public void render(@NonNull Window window, @NonNull Camera camera,
		List<SceneItem> sceneItems, Vector3f ambientLight,
		PointLight[] pointLightList, SpotLight[] spotLightList,
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

		this.renderLights(viewMatrix, ambientLight, pointLightList,
			spotLightList, directionalLight);

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

	/**
	 * Handle the lighting phase of rendering.
	 *
	 * @param viewMatrix The view transform matrix.
	 * @param ambientLight The ambient light color.
	 * @param pointLightList A list of point lights to use.
	 * @param spotLightList A list of spot lights to use.
	 * @param directionalLight A directional light to use.
	 */
	private void renderLights(Matrix4f viewMatrix, Vector3f ambientLight,
		PointLight[] pointLightList, SpotLight[] spotLightList,
		DirectionalLight directionalLight) {
		// Update Light Uniforms
		this.shaderProgram.setUniform(
			ShaderConstants.Uniform.Fragment.AMBIENT_LIGHT, ambientLight);
		this.shaderProgram.setUniform(
			ShaderConstants.Uniform.Fragment.SPECULAR_POWER,
			this.specularPower);

		// Process Point Lights
		int numLights = pointLightList == null ? 0 : pointLightList.length;
		for (int i = 0; i < numLights; ++i) {
			// Can't be null, size = 0 if null
			@SuppressWarnings("null")
			PointLight pointLight = pointLightList[i];
			if (null == pointLight) {
				continue;
			}
			/*
			 * Get a copy of the light object and transform its position to view
			 * coordinates
			 */
			PointLight currentPointLight = new PointLight(pointLight);
			Vector3f lightPosition = currentPointLight.getPosition();
			Vector4f aux = new Vector4f(lightPosition, 1);
			aux.mul(viewMatrix);
			lightPosition.set(aux.x, aux.y, aux.z);
			this.shaderProgram.setUniform(
				ShaderConstants.Uniform.Fragment.POINT_LIGHTS,
				currentPointLight, i);
		}

		// Process Spot Lights
		numLights = spotLightList == null ? 0 : spotLightList.length;
		for (int i = 0; i < numLights; i++) {
			// Can't be null, size = 0 if null
			@SuppressWarnings("null")
			SpotLight spotLight = spotLightList[i];
			/*
			 * Get a copy of the light object and transform its position and
			 * cone direction to view coordinates
			 */
			SpotLight currentSpotLight = new SpotLight(spotLight);
			Vector4f direction =
				new Vector4f(currentSpotLight.getConeDirection(), 0);
			direction.mul(viewMatrix);
			currentSpotLight.setConeDirection(
				new Vector3f(direction.x, direction.y, direction.z));
			Vector3f lightPosition =
				currentSpotLight.getPointLight().getPosition();

			Vector4f aux = new Vector4f(lightPosition, 1);
			aux.mul(viewMatrix);
			lightPosition.set(aux.x, aux.y, aux.z);
			this.shaderProgram.setUniform(
				ShaderConstants.Uniform.Fragment.SPOT_LIGHTS, currentSpotLight,
				i);
		}
		/*
		 * Get a copy of the directional light object and transform its position
		 * to view coordinates
		 */
		DirectionalLight currentDirectionalLight =
			new DirectionalLight(directionalLight);
		Vector4f dir = new Vector4f(currentDirectionalLight.getDirection(), 0);
		dir.mul(viewMatrix);
		currentDirectionalLight.setDirection(new Vector3f(dir.x, dir.y, dir.z));
		this.shaderProgram.setUniform(
			ShaderConstants.Uniform.Fragment.DIRECTIONAL_LIGHT,
			currentDirectionalLight);
	}
}
