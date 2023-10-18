/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.render;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.graph.GBuffer;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.RenderBuffers;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.Texture;
import com.ikalagaming.graphics.graph.UniformsMap;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles rendering for the scene geometry.
 */
public class SceneRender {
	/**
	 * The maximum number of draw elements that we can process.
	 */
	public static final int MAX_DRAW_ELEMENTS = 300;
	/**
	 * The maximum number of entities that we can process.
	 */
	public static final int MAX_ENTITIES = 100;

	/**
	 * The maximum number of materials we can have.
	 */
	private static final int MAX_MATERIALS = 30;
	/**
	 * The maximum number of textures we can have.
	 */
	private static final int MAX_TEXTURES = 16;

	/**
	 * The shader program for the scene.
	 */
	private ShaderProgram shaderProgram;

	/**
	 * The uniform map for the shader program.
	 */
	private UniformsMap uniformsMap;

	/**
	 * Set up a new scene renderer.
	 */
	public SceneRender() {
		List<ShaderProgram.ShaderModuleData> shaderModuleDataList =
			new ArrayList<>();
		shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(
			"shaders/scene.vert", GL20.GL_VERTEX_SHADER));
		shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(
			"shaders/scene.frag", GL20.GL_FRAGMENT_SHADER));
		this.shaderProgram = new ShaderProgram(shaderModuleDataList);
		this.createUniforms();
	}

	/**
	 * Clean up buffers and shaders.
	 */
	public void cleanup() {
		this.shaderProgram.cleanup();
	}

	/**
	 * Set up the uniforms for the shader.
	 */
	private void createUniforms() {
		this.uniformsMap = new UniformsMap(this.shaderProgram.getProgramID());
		this.uniformsMap.createUniform(ShaderUniforms.Scene.PROJECTION_MATRIX);
		this.uniformsMap.createUniform(ShaderUniforms.Scene.VIEW_MATRIX);

		for (int i = 0; i < SceneRender.MAX_TEXTURES; ++i) {
			this.uniformsMap.createUniform(
				ShaderUniforms.Scene.TEXTURE_SAMPLER + "[" + i + "]");
		}

		for (int i = 0; i < SceneRender.MAX_MATERIALS; ++i) {
			String name = ShaderUniforms.Scene.MATERIALS + "[" + i + "].";
			this.uniformsMap
				.createUniform(name + ShaderUniforms.Scene.Material.DIFFUSE);
			this.uniformsMap
				.createUniform(name + ShaderUniforms.Scene.Material.SPECULAR);
			this.uniformsMap.createUniform(
				name + ShaderUniforms.Scene.Material.REFLECTANCE);
			this.uniformsMap.createUniform(
				name + ShaderUniforms.Scene.Material.NORMAL_MAP_INDEX);
			this.uniformsMap.createUniform(
				name + ShaderUniforms.Scene.Material.TEXTURE_INDEX);
		}

	}

	/**
	 * Set up uniforms for textures and materials.
	 *
	 * @param scene The scene to render.
	 */
	public void recalculateMaterials(@NonNull Scene scene) {
		this.setupMaterialsUniform(scene.getMaterialCache());
	}

	/**
	 * Render the scene.
	 *
	 * @param scene The scene to render.
	 * @param renderBuffers The buffers for indirect drawing of models.
	 * @param gBuffer The buffer for geometry data.
	 * @param commandBuffers The render command buffers.
	 */
	public void render(@NonNull Scene scene,
		@NonNull RenderBuffers renderBuffers, @NonNull GBuffer gBuffer,
		@NonNull CommandBuffer commandBuffers) {

		GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
			gBuffer.getGBufferId());
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glViewport(0, 0, gBuffer.getWidth(), gBuffer.getHeight());
		GL11.glDisable(GL11.GL_BLEND);
		this.shaderProgram.bind();

		this.uniformsMap.setUniform(ShaderUniforms.Scene.PROJECTION_MATRIX,
			scene.getProjection().getProjMatrix());
		this.uniformsMap.setUniform(ShaderUniforms.Scene.VIEW_MATRIX,
			scene.getCamera().getViewMatrix());

		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		Texture.DEFAULT_TEXTURE.bind();

		int nextTexture = 1;
		for (Material material : scene.getMaterialCache().getMaterialsList()) {
			if (material.getNormalMap() != null) {
				GL13.glActiveTexture(GL13.GL_TEXTURE0 + nextTexture);
				material.getNormalMap().bind();
				this.uniformsMap.setUniform(ShaderUniforms.Scene.TEXTURE_SAMPLER
					+ "[" + nextTexture + "]", nextTexture);
				++nextTexture;
			}
			if (material.getTexture() != null) {
				GL13.glActiveTexture(GL13.GL_TEXTURE0 + nextTexture);
				material.getTexture().bind();
				this.uniformsMap.setUniform(ShaderUniforms.Scene.TEXTURE_SAMPLER
					+ "[" + nextTexture + "]", nextTexture);
				++nextTexture;
			}
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
		GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES,
			GL11.GL_UNSIGNED_INT, 0, commandBuffers.getStaticDrawCount(), 0);

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
		GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES,
			GL11.GL_UNSIGNED_INT, 0, commandBuffers.getAnimatedDrawCount(), 0);
		GL30.glBindVertexArray(0);
		GL11.glEnable(GL11.GL_BLEND);
		this.shaderProgram.unbind();
	}

	/**
	 * Set the uniforms from cached textures and materials.
	 *
	 * @param textureCache The texture cache.
	 * @param materialCache The material cache.
	 */
	private void setupMaterialsUniform(@NonNull MaterialCache materialCache) {
		this.shaderProgram.bind();
		List<Material> materialList = materialCache.getMaterialsList();
		int numMaterials = materialList.size();

		int nextTexture = 1;
		for (int i = 0; i < numMaterials; ++i) {
			Material material = materialCache.getMaterial(i);
			String name = ShaderUniforms.Scene.MATERIALS + "[" + i + "].";
			this.uniformsMap.setUniform(
				name + ShaderUniforms.Scene.Material.DIFFUSE,
				material.getDiffuseColor());
			this.uniformsMap.setUniform(
				name + ShaderUniforms.Scene.Material.SPECULAR,
				material.getSpecularColor());
			this.uniformsMap.setUniform(
				name + ShaderUniforms.Scene.Material.REFLECTANCE,
				material.getReflectance());

			// We bind the default texture to 0
			int index = 0;
			if (material.getNormalMap() != null) {
				index = nextTexture;
				++nextTexture;
			}
			this.uniformsMap.setUniform(
				name + ShaderUniforms.Scene.Material.NORMAL_MAP_INDEX, index);
			index = 0;
			if (material.getTexture() != null) {
				index = nextTexture;
				++nextTexture;
			}
			this.uniformsMap.setUniform(
				name + ShaderUniforms.Scene.Material.TEXTURE_INDEX, index);
		}
		this.shaderProgram.unbind();
	}

}