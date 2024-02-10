/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.render;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.Texture;
import com.ikalagaming.graphics.graph.UniformsMap;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.SkyBox;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles rendering for the skybox.
 */
public class SkyBoxRender {
	/**
	 * The shader program for the skybox.
	 */
	private ShaderProgram shaderProgram;
	/**
	 * The uniform map for the shader program.
	 */
	private UniformsMap uniformsMap;
	/**
	 * The cameras view matrix.
	 */
	private Matrix4f viewMatrix;

	/**
	 * Set up the renderer.
	 */
	public SkyBoxRender() {
		List<ShaderProgram.ShaderModuleData> shaderModuleDataList =
			new ArrayList<>();
		shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(
			"shaders/skybox.vert", GL20.GL_VERTEX_SHADER));
		shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(
			"shaders/skybox.frag", GL20.GL_FRAGMENT_SHADER));
		this.shaderProgram = new ShaderProgram(shaderModuleDataList);
		this.viewMatrix = new Matrix4f();
		this.createUniforms();
	}

	/**
	 * Clean up the shader.
	 */
	public void cleanup() {
		this.shaderProgram.cleanup();
	}

	/**
	 * Set up uniforms for the shader.
	 */
	private void createUniforms() {
		this.uniformsMap = new UniformsMap(this.shaderProgram.getProgramID());
		this.uniformsMap.createUniform(ShaderUniforms.Skybox.PROJECTION_MATRIX);
		this.uniformsMap.createUniform(ShaderUniforms.Skybox.VIEW_MATRIX);
		this.uniformsMap.createUniform(ShaderUniforms.Skybox.MODEL_MATRIX);
		this.uniformsMap.createUniform(ShaderUniforms.Skybox.DIFFUSE);
		this.uniformsMap.createUniform(ShaderUniforms.Skybox.TEXTURE_SAMPLER);
		this.uniformsMap.createUniform(ShaderUniforms.Skybox.HAS_TEXTURE);
	}

	/**
	 * Render the skybox.
	 *
	 * @param scene The scene to render.
	 */
	public void render(@NonNull Scene scene) {
		SkyBox skyBox = scene.getSkyBox();

		if (skyBox == null) {
			return;
		}
		
		this.shaderProgram.bind();

		this.uniformsMap.setUniform(ShaderUniforms.Skybox.PROJECTION_MATRIX,
			scene.getProjection().getProjMatrix());
		this.viewMatrix.set(scene.getCamera().getViewMatrix());
		this.viewMatrix.m30(0);
		this.viewMatrix.m31(0);
		this.viewMatrix.m32(0);
		this.uniformsMap.setUniform(ShaderUniforms.Skybox.VIEW_MATRIX,
			this.viewMatrix);
		this.uniformsMap.setUniform(ShaderUniforms.Skybox.TEXTURE_SAMPLER, 0);

		final Entity skyBoxEntity = skyBox.getSkyBoxEntity();
		Material material =
			scene.getMaterialCache().getMaterial(skyBox.getMaterialIndex());

		this.uniformsMap.setUniform(ShaderUniforms.Skybox.DIFFUSE,
			material.getDiffuseColor());

		Texture texture = material.getTexture();
		boolean hasTexture = false;
		if (texture != null) {
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			texture.bind();
			hasTexture = true;
		}
		
		this.uniformsMap.setUniform(ShaderUniforms.Skybox.HAS_TEXTURE,
			hasTexture ? 1 : 0);

		GL30.glBindVertexArray(skyBox.getVaoID());

		this.uniformsMap.setUniform(ShaderUniforms.Skybox.MODEL_MATRIX,
			skyBoxEntity.getModelMatrix());
		GL11.glDrawElements(GL11.GL_TRIANGLES, skyBox.getVertexCount(),
			GL11.GL_UNSIGNED_INT, 0);

		GL30.glBindVertexArray(0);

		this.shaderProgram.unbind();
	}
}
