/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.render;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.graph.CascadeShadow;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.graph.RenderBuffers;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.ShadowBuffer;
import com.ikalagaming.graphics.graph.UniformsMap;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;

import lombok.Getter;
import lombok.NonNull;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles rendering for shadows.
 */
public class ShadowRender {
	/**
	 * The size of render commands.
	 */
	private static final int COMMAND_SIZE = 5 * 4;
	/**
	 * How many animated model draw commands we have set up.
	 */
	private int animDrawCount;
	/**
	 * The animated render buffer ID.
	 */
	private int animRenderBufferHandle;
	/**
	 * The cascade shadow map.
	 *
	 * @return The cascade shadows.
	 */
	@Getter
	private ArrayList<CascadeShadow> cascadeShadows;
	/**
	 * A map from entity ID to its index in the draw elements uniform array.
	 */
	private Map<String, Integer> entitiesIndexMap;
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
	 * How many static model draw commands we have set up.
	 */
	private int staticDrawCount;
	/**
	 * The static render buffer ID.
	 */
	private int staticRenderBufferHandle;
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
		this.entitiesIndexMap = new HashMap<>();
	}

	/**
	 * Clean up buffers and shaders.
	 */
	public void cleanup() {
		this.shaderProgram.cleanup();
		this.shadowBuffer.cleanup();
		GL15.glDeleteBuffers(this.staticRenderBufferHandle);
		GL15.glDeleteBuffers(this.animRenderBufferHandle);
	}

	/**
	 * Create the uniforms for the shadow shader.
	 */
	private void createUniforms() {
		this.uniformsMap = new UniformsMap(this.shaderProgram.getProgramID());
		this.uniformsMap
			.createUniform(ShaderUniforms.Shadow.PROJECTION_VIEW_MATRIX);

		for (int i = 0; i < SceneRender.MAX_DRAW_ELEMENTS; ++i) {
			String name = ShaderUniforms.Shadow.DRAW_ELEMENTS + "[" + i + "].";
			this.uniformsMap.createUniform(
				name + ShaderUniforms.Shadow.DrawElement.MODEL_MATRIX_INDEX);
		}

		for (int i = 0; i < SceneRender.MAX_ENTITIES; ++i) {
			this.uniformsMap.createUniform(
				ShaderUniforms.Shadow.MODEL_MATRICES + "[" + i + "]");
		}
	}

	/**
	 * Render the shadows for the scene.
	 *
	 * @param scene The scene we are rendering.
	 * @param renderBuffers The buffers for indirect drawing of models.
	 */
	public void render(@NonNull Scene scene,
		@NonNull RenderBuffers renderBuffers) {
		CascadeShadow.updateCascadeShadows(this.cascadeShadows, scene);

		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,
			this.shadowBuffer.getDepthMapFBO());
		GL11.glViewport(0, 0, ShadowBuffer.SHADOW_MAP_WIDTH,
			ShadowBuffer.SHADOW_MAP_HEIGHT);

		this.shaderProgram.bind();

		int entityIndex = 0;
		for (Model model : scene.getModelMap().values()) {
			List<Entity> entities = model.getEntitiesList();
			for (Entity entity : entities) {
				this.uniformsMap.setUniform(ShaderUniforms.Shadow.MODEL_MATRICES
					+ "[" + entityIndex + "]", entity.getModelMatrix());
				entityIndex++;
			}
		}

		for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
			GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
				GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D,
				this.shadowBuffer.getDepthMap().getIDs()[i], 0);
			GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
		}

		// Static meshes
		int drawElement = 0;
		List<Model> modelList = scene.getModelMap().values().stream()
			.filter(m -> !m.isAnimated()).toList();
		for (Model model : modelList) {
			List<Entity> entities = model.getEntitiesList();
			// We need uniforms for every entity for all the instances
			for (@SuppressWarnings("unused")
			RenderBuffers.MeshDrawData meshDrawData : model
				.getMeshDrawDataList()) {
				for (Entity entity : entities) {
					String name = ShaderUniforms.Shadow.DRAW_ELEMENTS + "["
						+ drawElement + "].";
					this.uniformsMap.setUniform(name
						+ ShaderUniforms.Shadow.DrawElement.MODEL_MATRIX_INDEX,
						this.entitiesIndexMap.get(entity.getEntityID()));
					drawElement++;
				}
			}
		}
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,
			this.staticRenderBufferHandle);
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
				GL11.GL_UNSIGNED_INT, 0, this.staticDrawCount, 0);
		}

		// Animated meshes
		drawElement = 0;
		modelList = scene.getModelMap().values().stream()
			.filter(Model::isAnimated).toList();
		for (Model model : modelList) {
			for (RenderBuffers.MeshDrawData meshDrawData : model
				.getMeshDrawDataList()) {
				RenderBuffers.AnimMeshDrawData animMeshDrawData =
					meshDrawData.getAnimMeshDrawData();
				Entity entity = animMeshDrawData.getEntity();
				String name = ShaderUniforms.Shadow.DRAW_ELEMENTS + "["
					+ drawElement + "].";
				this.uniformsMap.setUniform(
					name + ShaderUniforms.Shadow.DrawElement.MODEL_MATRIX_INDEX,
					this.entitiesIndexMap.get(entity.getEntityID()));
				drawElement++;
			}
		}
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,
			this.animRenderBufferHandle);
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
				GL11.GL_UNSIGNED_INT, 0, this.animDrawCount, 0);
		}

		GL30.glBindVertexArray(0);
	}

	/**
	 * Set up the command buffer for animated models.
	 *
	 * @param scene The scene to render.
	 */
	private void setupAnimCommandBuffer(@NonNull Scene scene) {
		List<Model> modelList = scene.getModelMap().values().stream()
			.filter(Model::isAnimated).toList();
		int numMeshes = 0;
		for (Model model : modelList) {
			numMeshes += model.getMeshDrawDataList().size();
		}

		int firstIndex = 0;
		int baseInstance = 0;
		ByteBuffer commandBuffer =
			MemoryUtil.memAlloc(numMeshes * ShadowRender.COMMAND_SIZE);
		for (Model model : modelList) {
			for (RenderBuffers.MeshDrawData meshDrawData : model
				.getMeshDrawDataList()) {
				// count
				commandBuffer.putInt(meshDrawData.getVertices());
				// instanceCount
				commandBuffer.putInt(1);
				commandBuffer.putInt(firstIndex);
				// baseVertex
				commandBuffer.putInt(meshDrawData.getOffset());
				commandBuffer.putInt(baseInstance);

				firstIndex += meshDrawData.getVertices();
				baseInstance++;
			}
		}
		commandBuffer.flip();

		this.animDrawCount =
			commandBuffer.remaining() / ShadowRender.COMMAND_SIZE;

		this.animRenderBufferHandle = GL15.glGenBuffers();
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,
			this.animRenderBufferHandle);
		GL15.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER, commandBuffer,
			GL15.GL_DYNAMIC_DRAW);

		MemoryUtil.memFree(commandBuffer);
	}

	/**
	 * Set up data for the scene to prepare for rendering.
	 *
	 * @param scene The scene to render.
	 */
	public void setupData(@NonNull Scene scene) {
		this.setupEntitiesData(scene);
		this.setupStaticCommandBuffer(scene);
		this.setupAnimCommandBuffer(scene);
	}

	/**
	 * Populate the the entities index map from the scene.
	 *
	 * @param scene The scene to render.
	 */
	private void setupEntitiesData(@NonNull Scene scene) {
		this.entitiesIndexMap.clear();
		int entityIndex = 0;
		for (Model model : scene.getModelMap().values()) {
			List<Entity> entities = model.getEntitiesList();
			for (Entity entity : entities) {
				this.entitiesIndexMap.put(entity.getEntityID(), entityIndex);
				entityIndex++;
			}
		}
	}

	/**
	 * Set up the command buffer for static models.
	 *
	 * @param scene The scene to render.
	 */
	private void setupStaticCommandBuffer(@NonNull Scene scene) {
		List<Model> modelList = scene.getModelMap().values().stream()
			.filter(m -> !m.isAnimated()).toList();
		int numMeshes = 0;
		for (Model model : scene.getModelMap().values()) {
			numMeshes += model.getMeshDrawDataList().size();
		}

		int firstIndex = 0;
		int baseInstance = 0;
		ByteBuffer commandBuffer =
			MemoryUtil.memAlloc(numMeshes * ShadowRender.COMMAND_SIZE);
		for (Model model : modelList) {
			List<Entity> entities = model.getEntitiesList();
			int numEntities = entities.size();
			for (RenderBuffers.MeshDrawData meshDrawData : model
				.getMeshDrawDataList()) {
				// count
				commandBuffer.putInt(meshDrawData.getVertices());
				// instanceCount
				commandBuffer.putInt(numEntities);
				commandBuffer.putInt(firstIndex);
				// baseVertex
				commandBuffer.putInt(meshDrawData.getOffset());
				commandBuffer.putInt(baseInstance);

				firstIndex += meshDrawData.getVertices();
				baseInstance += entities.size();
			}
		}
		commandBuffer.flip();

		this.staticDrawCount =
			commandBuffer.remaining() / ShadowRender.COMMAND_SIZE;

		this.staticRenderBufferHandle = GL15.glGenBuffers();
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,
			this.staticRenderBufferHandle);
		GL15.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER, commandBuffer,
			GL15.GL_DYNAMIC_DRAW);

		MemoryUtil.memFree(commandBuffer);
	}

}