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
import com.ikalagaming.graphics.scene.EntityBatch;
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
	 * The uniform map for the shader program.
	 */
	private UniformsMap uniformsMap;

	/**
	 * The buffers for the batches.
	 */
	private Map<EntityBatch, CommandBuffer> commandBuffers;

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
		this.commandBuffers = new HashMap<>();
	}

	/**
	 * Clean up buffers and shaders.
	 */
	public void cleanup() {
		this.shaderProgram.cleanup();
		this.shadowBuffer.cleanup();
		this.clearCommandBuffers();
	}

	/**
	 * Delete all the command buffers and empty the list.
	 */
	private void clearCommandBuffers() {
		this.commandBuffers.values().forEach(CommandBuffer::cleanup);
		this.commandBuffers.clear();
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
	 * Set up before rendering.
	 * 
	 * @param scene The scene we will render.
	 */
	public void startRender(@NonNull Scene scene) {
		CascadeShadow.updateCascadeShadows(this.cascadeShadows, scene);

		for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
			GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
				GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D,
				this.shadowBuffer.getDepthMap().getIDs()[i], 0);
			GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
		}
		this.shaderProgram.bind();
	}

	/**
	 * Clean up after we are done rendering the scene.
	 */
	public void endRender() {
		this.shaderProgram.unbind();
	}

	/**
	 * Render the shadows for the scene.
	 *
	 * @param scene The scene we are rendering.
	 * @param renderBuffers The buffers for indirect drawing of models.
	 * @param batch The subset of entities to draw.
	 */
	public void render(@NonNull Scene scene,
		@NonNull RenderBuffers renderBuffers, @NonNull EntityBatch batch) {

		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,
			this.shadowBuffer.getDepthMapFBO());
		GL11.glViewport(0, 0, ShadowBuffer.SHADOW_MAP_WIDTH,
			ShadowBuffer.SHADOW_MAP_HEIGHT);

		this.entitiesIndexMap.clear();
		CommandBuffer buffers = this.commandBuffers.get(batch);

		int entityIndex = 0;
		for (Model model : batch.getModels()) {
			List<Entity> entities = batch.get(model);
			for (Entity entity : entities) {
				this.uniformsMap.setUniform(ShaderUniforms.Shadow.MODEL_MATRICES
					+ "[" + entityIndex + "]", entity.getModelMatrix());
				this.entitiesIndexMap.put(entity.getEntityID(), entityIndex);
				entityIndex++;
			}
		}

		// Static meshes
		int drawElement = 0;
		List<Model> modelList =
			batch.getModels().stream().filter(m -> !m.isAnimated()).toList();
		for (Model model : modelList) {
			List<Entity> entities = batch.get(model);
			// We need to draw every mesh for each entity
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
			buffers.getStaticCommandBuffer());
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
				GL11.GL_UNSIGNED_INT, 0, buffers.getStaticDrawCount(), 0);
		}

		// Animated meshes
		drawElement = 0;
		modelList =
			batch.getModels().stream().filter(Model::isAnimated).toList();
		for (Model model : modelList) {
			for (RenderBuffers.MeshDrawData meshDrawData : model
				.getMeshDrawDataList()) {
				RenderBuffers.AnimMeshDrawData animMeshDrawData =
					meshDrawData.animMeshDrawData();
				Entity entity = animMeshDrawData.entity();
				String name = ShaderUniforms.Shadow.DRAW_ELEMENTS + "["
					+ drawElement + "].";
				this.uniformsMap.setUniform(
					name + ShaderUniforms.Shadow.DrawElement.MODEL_MATRIX_INDEX,
					this.entitiesIndexMap.get(entity.getEntityID()));
				drawElement++;
			}
		}
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,
			buffers.getAnimatedCommandBuffer());
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
				GL11.GL_UNSIGNED_INT, 0, buffers.getAnimatedDrawCount(), 0);
		}

		GL30.glBindVertexArray(0);
	}

	/**
	 * Set up data for the scene to prepare for rendering.
	 *
	 * @param scene The scene to render.
	 */
	public void setupData(@NonNull Scene scene) {
		this.clearCommandBuffers();
		for (EntityBatch batch : scene.getEntityBatches()) {
			CommandBuffer buffers = new CommandBuffer();
			this.setupAnimCommandBuffer(batch, buffers);
			this.setupStaticCommandBuffer(batch, buffers);
			this.commandBuffers.put(batch, buffers);
		}
	}

	/**
	 * Set up the command buffer for animated models.
	 *
	 * @param batch The batch to render.
	 * @param buffer The command buffer for this batch, which we want to
	 *            populate.
	 */
	private void setupAnimCommandBuffer(@NonNull EntityBatch batch,
		@NonNull CommandBuffer buffer) {
		List<Model> modelList =
			batch.getModels().stream().filter(Model::isAnimated).toList();
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
				commandBuffer.putInt(meshDrawData.vertices());
				// instanceCount
				commandBuffer.putInt(1);
				commandBuffer.putInt(firstIndex);
				// baseVertex
				commandBuffer.putInt(meshDrawData.offset());
				commandBuffer.putInt(baseInstance);

				firstIndex += meshDrawData.vertices();
				baseInstance++;
			}
		}
		commandBuffer.flip();

		buffer.setAnimatedDrawCount(
			commandBuffer.remaining() / ShadowRender.COMMAND_SIZE);

		int animRenderBufferHandle = GL15.glGenBuffers();
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, animRenderBufferHandle);
		GL15.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER, commandBuffer,
			GL15.GL_STREAM_DRAW);

		MemoryUtil.memFree(commandBuffer);

		buffer.setAnimatedCommandBuffer(animRenderBufferHandle);
	}

	/**
	 * Set up the command buffer for static models.
	 *
	 * @param batch The batch to render.
	 * @param buffer The command buffer for this batch, which we want to
	 *            populate.
	 */
	private void setupStaticCommandBuffer(@NonNull EntityBatch batch,
		@NonNull CommandBuffer buffer) {
		List<Model> modelList =
			batch.getModels().stream().filter(m -> !m.isAnimated()).toList();
		int numMeshes = 0;
		for (Model model : modelList) {
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
				commandBuffer.putInt(meshDrawData.vertices());
				// instanceCount
				commandBuffer.putInt(numEntities);
				commandBuffer.putInt(firstIndex);
				// baseVertex
				commandBuffer.putInt(meshDrawData.offset());
				commandBuffer.putInt(baseInstance);

				firstIndex += meshDrawData.vertices();
				baseInstance += entities.size();
			}
		}
		commandBuffer.flip();

		buffer.setStaticDrawCount(
			commandBuffer.remaining() / ShadowRender.COMMAND_SIZE);

		int staticRenderBufferHandle = GL15.glGenBuffers();
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,
			staticRenderBufferHandle);
		GL15.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER, commandBuffer,
			GL15.GL_STREAM_DRAW);

		MemoryUtil.memFree(commandBuffer);

		buffer.setStaticCommandBuffer(staticRenderBufferHandle);
	}

}