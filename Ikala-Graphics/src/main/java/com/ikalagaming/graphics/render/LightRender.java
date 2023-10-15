/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.render;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.graph.CascadeShadow;
import com.ikalagaming.graphics.graph.GBuffer;
import com.ikalagaming.graphics.graph.QuadMesh;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.UniformsMap;
import com.ikalagaming.graphics.scene.Fog;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.scene.lights.PointLight;
import com.ikalagaming.graphics.scene.lights.SceneLights;
import com.ikalagaming.graphics.scene.lights.SpotLight;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * Handle light rendering.
 */
public class LightRender {
	/**
	 * The maximum number of point lights that are allowed.
	 */
	private static final int MAX_POINT_LIGHTS = 50;
	/**
	 * The maximum number of spot lights that are allowed.
	 */
	private static final int MAX_SPOT_LIGHTS = 50;
	/**
	 * The shader program for rendering lights.
	 */
	private final ShaderProgram shaderProgram;
	/**
	 * A mesh for rendering lighting onto.
	 */
	private QuadMesh quadMesh;
	/**
	 * The uniforms for the shader program.
	 */
	private UniformsMap uniformsMap;

	/**
	 * Set up the light renderer.
	 */
	public LightRender() {
		List<ShaderProgram.ShaderModuleData> shaderModuleDataList =
			new ArrayList<>();
		shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(
			"shaders/lights.vert", GL20.GL_VERTEX_SHADER));
		shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(
			"shaders/lights.frag", GL20.GL_FRAGMENT_SHADER));
		this.shaderProgram = new ShaderProgram(shaderModuleDataList);
		this.quadMesh = new QuadMesh();
		this.createUniforms();
	}

	/**
	 * Clean up resources.
	 */
	public void cleanup() {
		this.quadMesh.cleanup();
		this.shaderProgram.cleanup();
	}

	/**
	 * Set up the uniforms for the shader program.
	 */
	private void createUniforms() {
		this.uniformsMap = new UniformsMap(this.shaderProgram.getProgramID());
		this.uniformsMap.createUniform(ShaderUniforms.Light.ALBEDO_SAMPLER);
		this.uniformsMap.createUniform(ShaderUniforms.Light.NORMAL_SAMPLER);
		this.uniformsMap.createUniform(ShaderUniforms.Light.SPECULAR_SAMPLER);
		this.uniformsMap.createUniform(ShaderUniforms.Light.DEPTH_SAMPLER);
		this.uniformsMap
			.createUniform(ShaderUniforms.Light.INVERSE_PROJECTION_MATRIX);
		this.uniformsMap
			.createUniform(ShaderUniforms.Light.INVERSE_VIEW_MATRIX);
		this.uniformsMap.createUniform(ShaderUniforms.Light.AMBIENT_LIGHT + "."
			+ ShaderUniforms.Light.AmbientLight.INTENSITY);
		this.uniformsMap.createUniform(ShaderUniforms.Light.AMBIENT_LIGHT + "."
			+ ShaderUniforms.Light.AmbientLight.COLOR);

		for (int i = 0; i < LightRender.MAX_POINT_LIGHTS; ++i) {
			String name = ShaderUniforms.Light.POINT_LIGHTS + "[" + i + "].";
			this.uniformsMap
				.createUniform(name + ShaderUniforms.Light.PointLight.POSITION);
			this.uniformsMap
				.createUniform(name + ShaderUniforms.Light.PointLight.COLOR);
			this.uniformsMap.createUniform(
				name + ShaderUniforms.Light.PointLight.INTENSITY);
			this.uniformsMap.createUniform(
				name + ShaderUniforms.Light.PointLight.ATTENUATION + "."
					+ ShaderUniforms.Light.Attenuation.CONSTANT);
			this.uniformsMap.createUniform(
				name + ShaderUniforms.Light.PointLight.ATTENUATION + "."
					+ ShaderUniforms.Light.Attenuation.LINEAR);
			this.uniformsMap.createUniform(
				name + ShaderUniforms.Light.PointLight.ATTENUATION + "."
					+ ShaderUniforms.Light.Attenuation.EXPONENT);
		}
		for (int i = 0; i < LightRender.MAX_SPOT_LIGHTS; ++i) {
			String name = ShaderUniforms.Light.SPOT_LIGHTS + "[" + i + "].";
			this.uniformsMap
				.createUniform(name + ShaderUniforms.Light.SpotLight.POINT_LIGHT
					+ "." + ShaderUniforms.Light.PointLight.POSITION);
			this.uniformsMap
				.createUniform(name + ShaderUniforms.Light.SpotLight.POINT_LIGHT
					+ "." + ShaderUniforms.Light.PointLight.COLOR);
			this.uniformsMap
				.createUniform(name + ShaderUniforms.Light.SpotLight.POINT_LIGHT
					+ "." + ShaderUniforms.Light.PointLight.INTENSITY);
			this.uniformsMap
				.createUniform(name + ShaderUniforms.Light.SpotLight.POINT_LIGHT
					+ "." + ShaderUniforms.Light.PointLight.ATTENUATION + "."
					+ ShaderUniforms.Light.Attenuation.CONSTANT);
			this.uniformsMap
				.createUniform(name + ShaderUniforms.Light.SpotLight.POINT_LIGHT
					+ "." + ShaderUniforms.Light.PointLight.ATTENUATION + "."
					+ ShaderUniforms.Light.Attenuation.LINEAR);
			this.uniformsMap
				.createUniform(name + ShaderUniforms.Light.SpotLight.POINT_LIGHT
					+ "." + ShaderUniforms.Light.PointLight.ATTENUATION + "."
					+ ShaderUniforms.Light.Attenuation.EXPONENT);
			this.uniformsMap.createUniform(
				name + ShaderUniforms.Light.SpotLight.CONE_DIRECTION);
			this.uniformsMap
				.createUniform(name + ShaderUniforms.Light.SpotLight.CUTOFF);
		}

		this.uniformsMap.createUniform(ShaderUniforms.Light.DIRECTIONAL_LIGHT
			+ "." + ShaderUniforms.Light.DirectionalLight.COLOR);
		this.uniformsMap.createUniform(ShaderUniforms.Light.DIRECTIONAL_LIGHT
			+ "." + ShaderUniforms.Light.DirectionalLight.DIRECTION);
		this.uniformsMap.createUniform(ShaderUniforms.Light.DIRECTIONAL_LIGHT
			+ "." + ShaderUniforms.Light.DirectionalLight.INTENSITY);

		this.uniformsMap.createUniform(
			ShaderUniforms.Light.FOG + "." + ShaderUniforms.Light.Fog.ENABLED);
		this.uniformsMap.createUniform(
			ShaderUniforms.Light.FOG + "." + ShaderUniforms.Light.Fog.COLOR);
		this.uniformsMap.createUniform(
			ShaderUniforms.Light.FOG + "." + ShaderUniforms.Light.Fog.DENSITY);

		for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
			this.uniformsMap
				.createUniform(ShaderUniforms.Light.SHADOW_MAP_PREFIX + i);
			this.uniformsMap.createUniform(ShaderUniforms.Light.CASCADE_SHADOWS
				+ "[" + i + "]."
				+ ShaderUniforms.Light.CascadeShadow.PROJECTION_VIEW_MATRIX);
			this.uniformsMap
				.createUniform(ShaderUniforms.Light.CASCADE_SHADOWS + "[" + i
					+ "]." + ShaderUniforms.Light.CascadeShadow.SPLIT_DISTANCE);
		}
	}

	/**
	 * Render a scene.
	 *
	 * @param scene The scene we are rendering.
	 * @param shadowRender The shadow renderer.
	 * @param gBuffer The buffer for geometry data.
	 */
	public void render(@NonNull Scene scene, @NonNull ShadowRender shadowRender,
		@NonNull GBuffer gBuffer) {
		this.shaderProgram.bind();

		this.updateLights(scene);

		int nextTexture = 0;
		// Bind the G-Buffer textures
		int[] textureIds = gBuffer.getTextureIDs();
		if (textureIds != null) {
			for (int i = 0; i < textureIds.length; ++i) {
				GL13.glActiveTexture(GL13.GL_TEXTURE0 + nextTexture++);
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureIds[i]);
			}
		}

		this.uniformsMap.setUniform(ShaderUniforms.Light.ALBEDO_SAMPLER, 0);
		this.uniformsMap.setUniform(ShaderUniforms.Light.NORMAL_SAMPLER, 1);
		this.uniformsMap.setUniform(ShaderUniforms.Light.SPECULAR_SAMPLER, 2);
		this.uniformsMap.setUniform(ShaderUniforms.Light.DEPTH_SAMPLER, 3);
		Fog fog = scene.getFog();
		this.uniformsMap.setUniform(
			ShaderUniforms.Light.FOG + "." + ShaderUniforms.Light.Fog.ENABLED,
			fog.isActive() ? 1 : 0);
		this.uniformsMap.setUniform(
			ShaderUniforms.Light.FOG + "." + ShaderUniforms.Light.Fog.COLOR,
			fog.getColor());
		this.uniformsMap.setUniform(
			ShaderUniforms.Light.FOG + "." + ShaderUniforms.Light.Fog.DENSITY,
			fog.getDensity());

		List<CascadeShadow> cascadeShadows = shadowRender.getCascadeShadows();
		for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
			this.uniformsMap.setUniform(
				ShaderUniforms.Light.SHADOW_MAP_PREFIX + i, nextTexture + i);
			CascadeShadow cascadeShadow = cascadeShadows.get(i);
			this.uniformsMap.setUniform(
				ShaderUniforms.Light.CASCADE_SHADOWS + "[" + i + "]."
					+ ShaderUniforms.Light.CascadeShadow.PROJECTION_VIEW_MATRIX,
				cascadeShadow.getProjViewMatrix());
			this.uniformsMap.setUniform(
				ShaderUniforms.Light.CASCADE_SHADOWS + "[" + i + "]."
					+ ShaderUniforms.Light.CascadeShadow.SPLIT_DISTANCE,
				cascadeShadow.getSplitDistance());
		}
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + nextTexture);
		shadowRender.getShadowBuffer()
			.bindTextures(GL13.GL_TEXTURE0 + nextTexture);

		this.uniformsMap.setUniform(
			ShaderUniforms.Light.INVERSE_PROJECTION_MATRIX,
			scene.getProjection().getInvProjMatrix());
		this.uniformsMap.setUniform(ShaderUniforms.Light.INVERSE_VIEW_MATRIX,
			scene.getCamera().getInvViewMatrix());

		GL30.glBindVertexArray(this.quadMesh.getVaoID());
		GL11.glDrawElements(GL11.GL_TRIANGLES, this.quadMesh.getVertexCount(),
			GL11.GL_UNSIGNED_INT, 0);

		this.shaderProgram.unbind();
	}

	/**
	 * Update the uniforms for lights in the scene.
	 *
	 * @param scene The scene we are updating.
	 */
	private void updateLights(@NonNull Scene scene) {
		Matrix4f viewMatrix = scene.getCamera().getViewMatrix();

		SceneLights sceneLights = scene.getSceneLights();
		AmbientLight ambientLight = sceneLights.getAmbientLight();
		this.uniformsMap.setUniform(
			ShaderUniforms.Light.AMBIENT_LIGHT + "."
				+ ShaderUniforms.Light.AmbientLight.INTENSITY,
			ambientLight.getIntensity());
		this.uniformsMap.setUniform(
			ShaderUniforms.Light.AMBIENT_LIGHT + "."
				+ ShaderUniforms.Light.AmbientLight.COLOR,
			ambientLight.getColor());

		DirectionalLight dirLight = sceneLights.getDirLight();
		Vector4f auxDir = new Vector4f(dirLight.getDirection(), 0);
		auxDir.mul(viewMatrix);
		Vector3f dir = new Vector3f(auxDir.x, auxDir.y, auxDir.z);
		this.uniformsMap.setUniform(
			ShaderUniforms.Light.DIRECTIONAL_LIGHT + "."
				+ ShaderUniforms.Light.DirectionalLight.COLOR,
			dirLight.getColor());
		this.uniformsMap.setUniform(ShaderUniforms.Light.DIRECTIONAL_LIGHT + "."
			+ ShaderUniforms.Light.DirectionalLight.DIRECTION, dir);
		this.uniformsMap.setUniform(
			ShaderUniforms.Light.DIRECTIONAL_LIGHT + "."
				+ ShaderUniforms.Light.DirectionalLight.INTENSITY,
			dirLight.getIntensity());

		List<PointLight> pointLights = sceneLights.getPointLights();
		int numPointLights = pointLights.size();
		PointLight pointLight;
		for (int i = 0; i < LightRender.MAX_POINT_LIGHTS; ++i) {
			if (i < numPointLights) {
				pointLight = pointLights.get(i);
			}
			else {
				pointLight = null;
			}
			String name = ShaderUniforms.Light.POINT_LIGHTS + "[" + i + "].";
			this.updatePointLight(pointLight, name, viewMatrix);
		}

		List<SpotLight> spotLights = sceneLights.getSpotLights();
		int numSpotLights = spotLights.size();
		SpotLight spotLight;
		for (int i = 0; i < LightRender.MAX_SPOT_LIGHTS; ++i) {
			if (i < numSpotLights) {
				spotLight = spotLights.get(i);
			}
			else {
				spotLight = null;
			}
			String name = ShaderUniforms.Light.SPOT_LIGHTS + "[" + i + "].";
			this.updateSpotLight(spotLight, name, viewMatrix);
		}
	}

	/**
	 * Update point light uniforms.
	 *
	 * @param pointLight The point light information.
	 * @param prefix The prefix for uniforms.
	 * @param viewMatrix The view matrix.
	 */
	private void updatePointLight(PointLight pointLight, @NonNull String prefix,
		@NonNull Matrix4f viewMatrix) {
		Vector3f lightPosition = new Vector3f();
		Vector3f color = new Vector3f();
		float intensity = 0.0f;
		float constant = 0.0f;
		float linear = 0.0f;
		float exponent = 0.0f;
		if (pointLight != null) {
			Vector4f temp = new Vector4f(pointLight.getPosition(), 1);
			temp.mul(viewMatrix);
			lightPosition.set(temp.x, temp.y, temp.z);
			color.set(pointLight.getColor());
			intensity = pointLight.getIntensity();
			PointLight.Attenuation attenuation = pointLight.getAttenuation();
			constant = attenuation.getConstant();
			linear = attenuation.getLinear();
			exponent = attenuation.getExponent();
		}
		this.uniformsMap.setUniform(
			prefix + ShaderUniforms.Light.PointLight.POSITION, lightPosition);
		this.uniformsMap
			.setUniform(prefix + ShaderUniforms.Light.PointLight.COLOR, color);
		this.uniformsMap.setUniform(
			prefix + ShaderUniforms.Light.PointLight.INTENSITY, intensity);
		this.uniformsMap
			.setUniform(prefix + ShaderUniforms.Light.PointLight.ATTENUATION
				+ "." + ShaderUniforms.Light.Attenuation.CONSTANT, constant);
		this.uniformsMap
			.setUniform(prefix + ShaderUniforms.Light.PointLight.ATTENUATION
				+ "." + ShaderUniforms.Light.Attenuation.LINEAR, linear);
		this.uniformsMap
			.setUniform(prefix + ShaderUniforms.Light.PointLight.ATTENUATION
				+ "." + ShaderUniforms.Light.Attenuation.EXPONENT, exponent);
	}

	/**
	 * Update spot light uniforms for the given spot light.
	 *
	 * @param spotLight The light information.
	 * @param prefix The prefix for uniform values.
	 * @param viewMatrix The view matrix.
	 */
	private void updateSpotLight(SpotLight spotLight, @NonNull String prefix,
		@NonNull Matrix4f viewMatrix) {
		PointLight pointLight = null;
		Vector3f coneDirection = new Vector3f();
		float cutoff = 0.0f;
		if (spotLight != null) {
			coneDirection = spotLight.getConeDirection();
			cutoff = spotLight.getCutOff();
			pointLight = spotLight.getPointLight();
		}

		this.uniformsMap.setUniform(
			prefix + ShaderUniforms.Light.SpotLight.CONE_DIRECTION,
			coneDirection);
		this.uniformsMap
			.setUniform(prefix + ShaderUniforms.Light.SpotLight.CUTOFF, cutoff);
		this.updatePointLight(pointLight,
			prefix + ShaderUniforms.Light.SpotLight.POINT_LIGHT + ".",
			viewMatrix);
	}
}
