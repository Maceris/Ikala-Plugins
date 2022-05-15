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
import java.util.Map;

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

	private ShaderProgram sceneShaderProgram;
	private ShaderProgram hudShaderProgram;
	private ShaderProgram skyBoxShaderProgram;

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
		if (this.sceneShaderProgram != null) {
			this.sceneShaderProgram.cleanup();
		}
	}

	/**
	 * Initialize the renderer.
	 */
	public void init() {
		this.setupSceneShader();
		this.setupHudShader();
		this.setupSkyBoxShader();
	}

	/**
	 * Render an array of scene items.
	 *
	 * @param window The window to render on.
	 * @param camera The camera.
	 * @param scene The scene to render.
	 * @param hud The HUD to render.
	 */
	public void render(@NonNull Window window, @NonNull Camera camera,
		Scene scene, @NonNull Hud hud) {

		this.renderScene(window, camera, scene);
		this.renderSkyBox(window, camera, scene);
		this.renderHud(window, hud);
	}

	private void renderHud(Window window, Hud hud) {
		this.hudShaderProgram.bind();

		Matrix4f ortho = this.transformation.getOrthoProjectionMatrix(0,
			window.getWidth(), window.getHeight(), 0);
		for (SceneItem gameItem : hud.getSceneItems()) {
			Mesh mesh = gameItem.getMesh();
			// Set orthographic and model matrix for this HUD item
			Matrix4f projModelMatrix =
				this.transformation.getOrtoProjModelMatrix(gameItem, ortho);
			this.hudShaderProgram.setUniform(
				ShaderConstants.Uniform.HUD.PROJECTION_MATRIX, projModelMatrix);
			this.hudShaderProgram.setUniform(ShaderConstants.Uniform.HUD.COLOR,
				gameItem.getMesh().getMaterial().getAmbientColor());
			this.hudShaderProgram.setUniform(
				ShaderConstants.Uniform.HUD.HAS_TEXTURE,
				gameItem.getMesh().getMaterial().isTextured() ? 1 : 0);

			// Render the mesh for this HUD item
			mesh.render();
		}

		this.hudShaderProgram.unbind();
	}

	/**
	 * Handle the lighting phase of rendering.
	 *
	 * @param viewMatrix The view transform matrix.
	 * @param sceneLight The light information.
	 */
	private void renderLights(Matrix4f viewMatrix, SceneLight sceneLight) {
		// Update Light Uniforms
		this.sceneShaderProgram.setUniform(
			ShaderConstants.Uniform.Fragment.AMBIENT_LIGHT,
			sceneLight.getAmbientLight());
		this.sceneShaderProgram.setUniform(
			ShaderConstants.Uniform.Fragment.SPECULAR_POWER,
			this.specularPower);

		// Process Point Lights
		int numLights = sceneLight.getPointLightList() == null ? 0
			: sceneLight.getPointLightList().length;
		for (int i = 0; i < numLights; ++i) {
			// Can't be null, size = 0 if null
			PointLight pointLight = sceneLight.getPointLightList()[i];
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
			this.sceneShaderProgram.setUniform(
				ShaderConstants.Uniform.Fragment.POINT_LIGHTS,
				currentPointLight, i);
		}

		// Process Spot Lights
		numLights = sceneLight.getSpotLightList() == null ? 0
			: sceneLight.getSpotLightList().length;
		for (int i = 0; i < numLights; i++) {
			// Can't be null, size = 0 if null
			SpotLight spotLight = sceneLight.getSpotLightList()[i];
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
			this.sceneShaderProgram.setUniform(
				ShaderConstants.Uniform.Fragment.SPOT_LIGHTS, currentSpotLight,
				i);
		}
		/*
		 * Get a copy of the directional light object and transform its position
		 * to view coordinates
		 */
		DirectionalLight currentDirectionalLight =
			new DirectionalLight(sceneLight.getDirectionalLight());
		Vector4f dir = new Vector4f(currentDirectionalLight.getDirection(), 0);
		dir.mul(viewMatrix);
		currentDirectionalLight.setDirection(new Vector3f(dir.x, dir.y, dir.z));
		this.sceneShaderProgram.setUniform(
			ShaderConstants.Uniform.Fragment.DIRECTIONAL_LIGHT,
			currentDirectionalLight);
	}

	/**
	 * Render the scene items.
	 *
	 * @param window The window to render on.
	 * @param camera The camera.
	 * @param scene The scene to render.
	 */
	private void renderScene(Window window, Camera camera, Scene scene) {
		this.sceneShaderProgram.bind();

		// Update the projection matrix
		Matrix4f projectionMatrix = this.transformation.getProjectionMatrix(
			Renderer.FOV, window.getWidth(), window.getHeight(),
			Renderer.Z_NEAR, Renderer.Z_FAR);
		this.sceneShaderProgram.setUniform(
			ShaderConstants.Uniform.Vertex.PROJECTION_MATRIX, projectionMatrix);

		// Update the view matrix
		Matrix4f viewMatrix = this.transformation.getViewMatrix(camera);

		this.renderLights(viewMatrix, scene.getSceneLight());

		this.sceneShaderProgram
			.setUniform(ShaderConstants.Uniform.Fragment.TEXTURE_SAMPLER, 0);

		// Render each mesh with the associated game Items
		Map<Mesh, List<SceneItem>> mapMeshes = scene.getMeshMap();
		for (Mesh mesh : mapMeshes.keySet()) {
			this.sceneShaderProgram.setUniform("material", mesh.getMaterial());
			mesh.renderList(mapMeshes.get(mesh), (SceneItem gameItem) -> {
				Matrix4f modelViewMatrix = this.transformation
					.buildModelViewMatrix(gameItem, viewMatrix);
				this.sceneShaderProgram.setUniform("modelViewMatrix",
					modelViewMatrix);
			});
		}

		this.sceneShaderProgram.unbind();
	}

	/**
	 * Render the skybox as a background.
	 *
	 * @param window The window we are drawing on.
	 * @param camera The camera.
	 * @param scene The scene to render.
	 */
	private void renderSkyBox(Window window, Camera camera, Scene scene) {
		this.skyBoxShaderProgram.bind();

		this.skyBoxShaderProgram
			.setUniform(ShaderConstants.Uniform.Fragment.TEXTURE_SAMPLER, 0);

		// Update projection Matrix
		Matrix4f projectionMatrix = this.transformation.getProjectionMatrix(
			Renderer.FOV, window.getWidth(), window.getHeight(),
			Renderer.Z_NEAR, Renderer.Z_FAR);
		this.skyBoxShaderProgram.setUniform(
			ShaderConstants.Uniform.Vertex.PROJECTION_MATRIX, projectionMatrix);
		SkyBox skyBox = scene.getSkyBox();
		Matrix4f viewMatrix = this.transformation.getViewMatrix(camera);
		viewMatrix.m30(0);
		viewMatrix.m31(0);
		viewMatrix.m32(0);
		Matrix4f modelViewMatrix =
			this.transformation.getModelViewMatrix(skyBox, viewMatrix);
		this.skyBoxShaderProgram.setUniform(
			ShaderConstants.Uniform.Vertex.MODEL_VIEW_MATRIX, modelViewMatrix);
		this.skyBoxShaderProgram.setUniform(
			ShaderConstants.Uniform.Fragment.AMBIENT_LIGHT,
			scene.getSceneLight().getAmbientLight());

		scene.getSkyBox().getMesh().render();

		this.skyBoxShaderProgram.unbind();
	}

	/**
	 * Set up the HUD shader program.
	 */
	private void setupHudShader() {
		this.hudShaderProgram = new ShaderProgram();
		try {
			this.hudShaderProgram.createVertexShader(FileUtils.readAsString(
				PluginFolder.getResource(GraphicsPlugin.PLUGIN_NAME,
					ResourceType.DATA, "shaders/hud_vertex.vs")));
			this.hudShaderProgram.createFragmentShader(FileUtils.readAsString(
				PluginFolder.getResource(GraphicsPlugin.PLUGIN_NAME,
					ResourceType.DATA, "shaders/hud_fragment.fs")));
		}
		catch (ShaderException e) {
			Renderer.log.error(SafeResourceLoader.getString(
				"SHADER_ERROR_SETUP", GraphicsPlugin.getResourceBundle()), e);
		}
		this.hudShaderProgram.link();

		this.hudShaderProgram
			.createUniform(ShaderConstants.Uniform.HUD.PROJECTION_MATRIX);
		this.hudShaderProgram.createUniform(ShaderConstants.Uniform.HUD.COLOR);
		this.hudShaderProgram
			.createUniform(ShaderConstants.Uniform.HUD.HAS_TEXTURE);
	}

	/**
	 * Set up the scene shader program.
	 */
	private void setupSceneShader() {
		this.sceneShaderProgram = new ShaderProgram();
		try {
			this.sceneShaderProgram.createVertexShader(FileUtils.readAsString(
				PluginFolder.getResource(GraphicsPlugin.PLUGIN_NAME,
					ResourceType.DATA, "shaders/vertex.vs")));
			this.sceneShaderProgram.createFragmentShader(FileUtils.readAsString(
				PluginFolder.getResource(GraphicsPlugin.PLUGIN_NAME,
					ResourceType.DATA, "shaders/fragment.fs")));
		}
		catch (ShaderException e) {
			Renderer.log.error(SafeResourceLoader.getString(
				"SHADER_ERROR_SETUP", GraphicsPlugin.getResourceBundle()), e);
		}
		this.sceneShaderProgram.link();

		this.sceneShaderProgram
			.createUniform(ShaderConstants.Uniform.Vertex.PROJECTION_MATRIX);
		this.sceneShaderProgram
			.createUniform(ShaderConstants.Uniform.Vertex.MODEL_VIEW_MATRIX);
		this.sceneShaderProgram
			.createUniform(ShaderConstants.Uniform.Fragment.TEXTURE_SAMPLER);

		// Create uniform for material
		this.sceneShaderProgram
			.createMaterialUniform(ShaderConstants.Uniform.Fragment.MATERIAL);

		// Create lighting related uniforms
		this.sceneShaderProgram
			.createUniform(ShaderConstants.Uniform.Fragment.SPECULAR_POWER);
		this.sceneShaderProgram
			.createUniform(ShaderConstants.Uniform.Fragment.AMBIENT_LIGHT);
		this.sceneShaderProgram.createPointLightListUniform(
			ShaderConstants.Uniform.Fragment.POINT_LIGHTS,
			ShaderConstants.Uniform.Fragment.MAX_POINT_LIGHTS);
		this.sceneShaderProgram.createSpotLightListUniform(
			ShaderConstants.Uniform.Fragment.SPOT_LIGHTS,
			ShaderConstants.Uniform.Fragment.MAX_SPOT_LIGHTS);
		this.sceneShaderProgram.createDirectionalLightUniform(
			ShaderConstants.Uniform.Fragment.DIRECTIONAL_LIGHT);
	}

	/**
	 * Set up the skybox shader program.
	 */
	private void setupSkyBoxShader() {
		this.skyBoxShaderProgram = new ShaderProgram();
		this.skyBoxShaderProgram.createVertexShader(FileUtils
			.readAsString(PluginFolder.getResource(GraphicsPlugin.PLUGIN_NAME,
				ResourceType.DATA, "shaders/skybox_vertex.vs")));
		this.skyBoxShaderProgram.createFragmentShader(FileUtils
			.readAsString(PluginFolder.getResource(GraphicsPlugin.PLUGIN_NAME,
				ResourceType.DATA, "shaders/skybox_fragment.fs")));
		this.skyBoxShaderProgram.link();

		// Create uniforms for projection matrix
		this.skyBoxShaderProgram
			.createUniform(ShaderConstants.Uniform.Vertex.PROJECTION_MATRIX);
		this.skyBoxShaderProgram
			.createUniform(ShaderConstants.Uniform.Vertex.MODEL_VIEW_MATRIX);
		this.skyBoxShaderProgram
			.createUniform(ShaderConstants.Uniform.Fragment.TEXTURE_SAMPLER);
		this.skyBoxShaderProgram
			.createUniform(ShaderConstants.Uniform.Fragment.AMBIENT_LIGHT);
	}
}
