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
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Handle light rendering.
 */
@Slf4j
public class LightRender {
	/**
	 * The binding for the point light SSBO.
	 */
	static final int POINT_LIGHT_BINDING = 0;

	/**
	 * The binding for the spot light SSBO.
	 */
	static final int SPOT_LIGHT_BINDING = 1;
	/**
	 * How many lights of each type (spot, point) we currently support.
	 */
	private static final int MAX_LIGHTS_SUPPORTED = 1000;
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
	 * An SSBO for point lights.
	 */
	private int pointLightBuffer;
	/**
	 * An SSBO for spot lights.
	 */
	private int spotLightBuffer;

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
		this.initSSBOs();
	}

	/**
	 * Clean up resources.
	 */
	public void cleanup() {
		this.quadMesh.cleanup();
		this.shaderProgram.cleanup();
		GL15.glDeleteBuffers(this.pointLightBuffer);
		GL15.glDeleteBuffers(this.spotLightBuffer);
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

		this.uniformsMap.createUniform(ShaderUniforms.Light.DIRECTIONAL_LIGHT
			+ "." + ShaderUniforms.Light.DirectionalLight.COLOR);
		this.uniformsMap.createUniform(ShaderUniforms.Light.DIRECTIONAL_LIGHT
			+ "." + ShaderUniforms.Light.DirectionalLight.DIRECTION);
		this.uniformsMap.createUniform(ShaderUniforms.Light.DIRECTIONAL_LIGHT
			+ "." + ShaderUniforms.Light.DirectionalLight.INTENSITY);

		this.uniformsMap.createUniform(ShaderUniforms.Light.POINT_LIGHT_COUNT);
		this.uniformsMap.createUniform(ShaderUniforms.Light.SPOT_LIGHT_COUNT);

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
	 * Initialize the lighting SSBOs and fill them with zeroes.
	 */
	private void initSSBOs() {
		this.pointLightBuffer = GL15.glGenBuffers();
		this.spotLightBuffer = GL15.glGenBuffers();
		/*
		 * Position (vec3 + ignored), color (vec3), intensity (1), Attenuation
		 * (3 + ignored), in that order.
		 */
		final int POINT_LIGHT_SIZE = 4 + 3 + 1 + 4;
		FloatBuffer pointLightFloatBuffer = MemoryUtil
			.memAllocFloat(LightRender.MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE);

		pointLightFloatBuffer
			.put(new float[LightRender.MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE])
			.flip();

		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,
			LightRender.POINT_LIGHT_BINDING, this.pointLightBuffer);
		GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, pointLightFloatBuffer,
			GL15.GL_STATIC_DRAW);

		MemoryUtil.memFree(pointLightFloatBuffer);

		/*
		 * Position (vec3 + ignored), color (vec3), intensity (1), Attenuation
		 * (3 + ignored), cone direction (vec3), cutoff (1) in that order.
		 */
		final int SPOT_LIGHT_SIZE = 4 + 3 + 1 + 4 + 3 + 1;
		FloatBuffer spotLightFloatBuffer = MemoryUtil
			.memAllocFloat(LightRender.MAX_LIGHTS_SUPPORTED * SPOT_LIGHT_SIZE);

		spotLightFloatBuffer
			.put(new float[LightRender.MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE])
			.flip();

		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,
			LightRender.SPOT_LIGHT_BINDING, this.spotLightBuffer);
		GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, spotLightFloatBuffer,
			GL15.GL_STATIC_DRAW);

		MemoryUtil.memFree(spotLightFloatBuffer);
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
	 * Load all the point lights into the SSBO for rendering.
	 *
	 * @param scene The scene to fetch lights from.
	 */
	private void setupPointLightBuffer(@NonNull Scene scene) {
		List<PointLight> pointLights = scene.getSceneLights().getPointLights();
		final Matrix4f viewMatrix = scene.getCamera().getViewMatrix();

		if (pointLights.size() > LightRender.MAX_LIGHTS_SUPPORTED) {
			LightRender.log.error(
				"We only support {} point lights but are trying to render {}.",
				LightRender.MAX_LIGHTS_SUPPORTED, pointLights.size());
		}
		/*
		 * Position (vec3 + ignored), color (vec3), intensity (1), Attenuation
		 * (3 + ignored), in that order.
		 */
		final int STRUCT_SIZE = 4 + 3 + 1 + 4;

		final int lightsToRender =
			Math.min(LightRender.MAX_LIGHTS_SUPPORTED, pointLights.size());

		FloatBuffer lightBuffer =
			MemoryUtil.memAllocFloat(lightsToRender * STRUCT_SIZE);

		Vector4f lightPosition = new Vector4f();
		final float padding = 0.0f;
		for (int i = 0; i < lightsToRender; ++i) {
			PointLight light = pointLights.get(i);
			lightPosition.set(light.getPosition(), 1);
			lightPosition.mul(viewMatrix);
			lightBuffer.put(lightPosition.x);
			lightBuffer.put(lightPosition.y);
			lightBuffer.put(lightPosition.z);
			lightBuffer.put(padding);
			lightBuffer.put(light.getColor().x);
			lightBuffer.put(light.getColor().y);
			lightBuffer.put(light.getColor().z);
			lightBuffer.put(light.getIntensity());
			lightBuffer.put(light.getAttenuation().getConstant());
			lightBuffer.put(light.getAttenuation().getLinear());
			lightBuffer.put(light.getAttenuation().getExponent());
			lightBuffer.put(padding);
		}

		lightBuffer.flip();

		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,
			LightRender.POINT_LIGHT_BINDING, this.pointLightBuffer);
		GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, lightBuffer);

		MemoryUtil.memFree(lightBuffer);

		this.uniformsMap.setUniform(ShaderUniforms.Light.POINT_LIGHT_COUNT,
			lightsToRender);
	}

	/**
	 * Load all the spot lights into the SSBO for rendering.
	 *
	 * @param scene The scene to fetch lights from.
	 */
	private void setupSpotLightBuffer(@NonNull Scene scene) {
		List<SpotLight> spotLights = scene.getSceneLights().getSpotLights();
		final Matrix4f viewMatrix = scene.getCamera().getViewMatrix();

		if (spotLights.size() > LightRender.MAX_LIGHTS_SUPPORTED) {
			LightRender.log.error(
				"We only support {} spot lights but are trying to render {}.",
				LightRender.MAX_LIGHTS_SUPPORTED, spotLights.size());
		}

		/*
		 * Position (vec3 + ignored), color (vec3), intensity (1), Attenuation
		 * (3 + ignored), cone direction (vec3), cutoff (1) in that order.
		 */
		final int STRUCT_SIZE = 4 + 3 + 1 + 4 + 3 + 1;

		final int lightsToRender =
			Math.min(LightRender.MAX_LIGHTS_SUPPORTED, spotLights.size());

		FloatBuffer lightBuffer =
			MemoryUtil.memAllocFloat(lightsToRender * STRUCT_SIZE);

		Vector4f lightPosition = new Vector4f();
		Vector4f lightDirection = new Vector4f();		
		final float padding = 0.0f;
		for (int i = 0; i < lightsToRender; ++i) {
			SpotLight light = spotLights.get(i);
			lightPosition.set(light.getPointLight().getPosition(), 1);
			lightPosition.mul(viewMatrix);
			lightBuffer.put(lightPosition.x);
			lightBuffer.put(lightPosition.y);
			lightBuffer.put(lightPosition.z);
			lightBuffer.put(padding);
			lightBuffer.put(light.getPointLight().getColor().x);
			lightBuffer.put(light.getPointLight().getColor().y);
			lightBuffer.put(light.getPointLight().getColor().z);
			lightBuffer.put(light.getPointLight().getIntensity());
			lightBuffer
				.put(light.getPointLight().getAttenuation().getConstant());
			lightBuffer.put(light.getPointLight().getAttenuation().getLinear());
			lightBuffer
				.put(light.getPointLight().getAttenuation().getExponent());
			lightBuffer.put(padding);
			lightDirection.set(light.getConeDirection(), 1);
			lightDirection.mul(viewMatrix);
			lightBuffer.put(lightDirection.x);
			lightBuffer.put(lightDirection.y);
			lightBuffer.put(lightDirection.z);
			lightBuffer.put(light.getCutOff());
		}
		lightBuffer.flip();

		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,
			LightRender.SPOT_LIGHT_BINDING, this.spotLightBuffer);
		GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, lightBuffer);

		MemoryUtil.memFree(lightBuffer);

		this.uniformsMap.setUniform(ShaderUniforms.Light.SPOT_LIGHT_COUNT,
			lightsToRender);
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

		this.setupPointLightBuffer(scene);
		this.setupSpotLightBuffer(scene);
	}
}
