/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.render;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.graph.QuadMesh;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.UniformsMap;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * Handle light rendering.
 */
public class FilterRender {

	/**
	 * The shader program for rendering a sobel edge filter.
	 */
	private final ShaderProgram sobelShader;
	/**
	 * The shader program for rendering the scene unmodified.
	 */
	private final ShaderProgram noFilterShader;

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
	public FilterRender() {
		List<ShaderProgram.ShaderModuleData> shaderModuleDataList =
			new ArrayList<>();
		shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(
			"shaders/filters/sobel.vert", GL20.GL_VERTEX_SHADER));
		shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(
			"shaders/filters/sobel.frag", GL20.GL_FRAGMENT_SHADER));
		this.sobelShader = new ShaderProgram(shaderModuleDataList);

		shaderModuleDataList.clear();
		shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(
			"shaders/filters/nofilter.vert", GL20.GL_VERTEX_SHADER));
		shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(
			"shaders/filters/nofilter.frag", GL20.GL_FRAGMENT_SHADER));
		this.noFilterShader = new ShaderProgram(shaderModuleDataList);
		this.quadMesh = new QuadMesh();
		this.createUniforms();
	}

	/**
	 * Clean up resources.
	 */
	public void cleanup() {
		this.quadMesh.cleanup();
		this.sobelShader.cleanup();
		this.noFilterShader.cleanup();
	}

	/**
	 * Set up the uniforms for the shader program.
	 */
	private void createUniforms() {
		this.uniformsMap = new UniformsMap(this.sobelShader.getProgramID());
		this.uniformsMap.createUniform(ShaderUniforms.Filter.SCREEN_TEXTURE);
	}

	/**
	 * Render a scene.
	 *
	 * @param scene The scene we are rendering.
	 * @param screenTexture The screen texture for post-processing.
	 * @param applyFilter If we actually want to render with a filter.
	 */
	public void render(@NonNull Scene scene, int screenTexture,
		boolean applyFilter) {
		GL11.glDepthMask(false);

		if (applyFilter) {
			this.sobelShader.bind();
		}
		else {
			this.noFilterShader.bind();
		}

		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, screenTexture);

		this.uniformsMap.setUniform(ShaderUniforms.Filter.SCREEN_TEXTURE, 0);

		GL30.glBindVertexArray(this.quadMesh.getVaoID());
		GL11.glDrawElements(GL11.GL_TRIANGLES, this.quadMesh.getVertexCount(),
			GL11.GL_UNSIGNED_INT, 0);

		if (applyFilter) {
			this.sobelShader.unbind();
		}
		else {
			this.noFilterShader.unbind();
		}

		GL11.glDepthMask(true);
	}
}
