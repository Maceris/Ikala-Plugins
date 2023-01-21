/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.render;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.graph.CascadeShadow;
import com.ikalagaming.graphics.graph.RenderBuffers;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.ShadowBuffer;
import com.ikalagaming.graphics.graph.UniformsMap;
import com.ikalagaming.graphics.scene.Scene;

import lombok.Getter;
import lombok.NonNull;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles rendering for shadows.
 */
public class ShadowRender {
	/**
	 * The cascade shadow map.
	 *
	 * @return The cascade shadows.
	 */
	@Getter
	private ArrayList<CascadeShadow> cascadeShadows;
	/**
	 * The shader program for the shadow rendering.
	 */
	private ShaderProgram shaderProgram;
	/**
	 * The buffer we render shadows to.
	 *
	 * @return The shadow buffer.
	 */
	@Getter
	private ShadowBuffer shadowBuffer;
	/**
	 * The uniform map for the shader program.
	 */
	private UniformsMap uniformsMap;

	/**
	 * Set up a new shadow renderer.
	 */
	public ShadowRender() {
		List<ShaderProgram.ShaderModuleData> shaderModuleDataList =
			new ArrayList<>();
		shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(
			"shaders/shadow.vert", GL20.GL_VERTEX_SHADER));
		this.shaderProgram = new ShaderProgram(shaderModuleDataList);

		this.shadowBuffer = new ShadowBuffer();

		this.cascadeShadows = new ArrayList<>();
		for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
			this.cascadeShadows.add(new CascadeShadow());
		}

		this.createUniforms();
	}

	/**
	 * Clean up buffers and shaders.
	 */
	public void cleanup() {
		this.shaderProgram.cleanup();
		this.shadowBuffer.cleanup();
	}

	/**
	 * Create the uniforms for the shadow shader.
	 */
	private void createUniforms() {
		this.uniformsMap = new UniformsMap(this.shaderProgram.getProgramID());
		this.uniformsMap
			.createUniform(ShaderUniforms.Shadow.PROJECTION_VIEW_MATRIX);
	}

	/**
	 * Render the shadows for the scene.
	 *
	 * @param scene The scene we are rendering.
	 * @param renderBuffers The buffers for indirect drawing of models.
	 * @param commandBuffers The rendering command buffers.
	 */
	public void render(@NonNull Scene scene,
		@NonNull RenderBuffers renderBuffers,
		@NonNull CommandBuffer commandBuffers) {
		CascadeShadow.updateCascadeShadows(this.cascadeShadows, scene);

		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,
			this.shadowBuffer.getDepthMapFBO());
		GL11.glViewport(0, 0, ShadowBuffer.SHADOW_MAP_WIDTH,
			ShadowBuffer.SHADOW_MAP_HEIGHT);
		
		this.shaderProgram.bind();
		
		for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
			GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
				GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D,
				this.shadowBuffer.getDepthMap().getIDs()[i], 0);
			GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
		}
		

		// Static meshes
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,
			Render.DRAW_ELEMENT_BINDING,
			commandBuffers.getStaticDrawElementBuffer());
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,
			Render.MODEL_MATRICES_BINDING,
			commandBuffers.getStaticModelMatricesBuffer());
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,
			commandBuffers.getStaticCommandBuffer());
		GL30.glBindVertexArray(renderBuffers.getStaticVaoID());
		for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
			GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
				GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D,
				this.shadowBuffer.getDepthMap().getIDs()[i], 0);

			CascadeShadow shadowCascade = this.cascadeShadows.get(i);
			this.uniformsMap.setUniform(
				ShaderUniforms.Shadow.PROJECTION_VIEW_MATRIX,
				shadowCascade.getProjViewMatrix());

			GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES,
				GL11.GL_UNSIGNED_INT, 0, commandBuffers.getStaticDrawCount(),
				0);
		}

		// Animated meshes
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,
			Render.DRAW_ELEMENT_BINDING,
			commandBuffers.getAnimatedDrawElementBuffer());
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,
			Render.MODEL_MATRICES_BINDING,
			commandBuffers.getAnimatedModelMatricesBuffer());
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,
			commandBuffers.getAnimatedCommandBuffer());
		GL30.glBindVertexArray(renderBuffers.getAnimVaoID());
		for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
			GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
				GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D,
				this.shadowBuffer.getDepthMap().getIDs()[i], 0);

			CascadeShadow shadowCascade = this.cascadeShadows.get(i);
			this.uniformsMap.setUniform(
				ShaderUniforms.Shadow.PROJECTION_VIEW_MATRIX,
				shadowCascade.getProjViewMatrix());

			GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES,
				GL11.GL_UNSIGNED_INT, 0, commandBuffers.getAnimatedDrawCount(),
				0);
		}

		GL30.glBindVertexArray(0);
		this.shaderProgram.unbind();
	}

}