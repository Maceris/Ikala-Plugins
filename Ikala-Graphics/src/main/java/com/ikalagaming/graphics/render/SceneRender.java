/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.render;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.graph.GeometryBuffer;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.graph.RenderBuffers;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.Texture;
import com.ikalagaming.graphics.graph.TextureCache;
import com.ikalagaming.graphics.graph.UniformsMap;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.EntityBatch;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
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
 * Handles rendering for the scene geometry.
 */
@Slf4j
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
	 * The size of a draw command.
	 */
	private static final int COMMAND_SIZE = 5 * 4;
	/**
	 * The maximum number of materials we can have.
	 */
	private static final int MAX_MATERIALS = 30;
	/**
	 * The maximum number of textures we can have.
	 */
	private static final int MAX_TEXTURES = 16;

	/**
	 * A map from entity ID to its index in the draw elements uniform array.
	 */
	private Map<String, Integer> entitiesIndexMap;
	/**
	 * The shader program for the scene.
	 */
	private ShaderProgram shaderProgram;

	/**
	 * The uniform map for the shader program.
	 */
	private UniformsMap uniformsMap;
	/**
	 * The buffers for the batches.
	 */
	private Map<EntityBatch, CommandBuffer> commandBuffers;

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
		this.entitiesIndexMap = new HashMap<>();
		this.commandBuffers = new HashMap<>();
	}

	/**
	 * Clean up buffers and shaders.
	 */
	public void cleanup() {
		this.shaderProgram.cleanup();
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

		for (int i = 0; i < SceneRender.MAX_DRAW_ELEMENTS; ++i) {
			String name = ShaderUniforms.Scene.DRAW_ELEMENTS + "[" + i + "].";
			this.uniformsMap.createUniform(
				name + ShaderUniforms.Scene.DrawElement.MODEL_MATRIX_INDEX);
			this.uniformsMap.createUniform(
				name + ShaderUniforms.Scene.DrawElement.MATERIAL_INDEX);
		}

		for (int i = 0; i < SceneRender.MAX_ENTITIES; ++i) {
			this.uniformsMap.createUniform(
				ShaderUniforms.Scene.MODEL_MATRICES + "[" + i + "]");
		}

	}

	/**
	 * Clean up after we are done rendering the scene.
	 */
	public void endRender() {
		this.shaderProgram.unbind();
		GL11.glEnable(GL11.GL_BLEND);
	}

	/**
	 * Set up uniforms for textures and materials.
	 *
	 * @param scene The scene to render.
	 */
	public void recalculateMaterials(@NonNull Scene scene) {
		this.setupMaterialsUniform(scene.getTextureCache(),
			scene.getMaterialCache());
	}

	/**
	 * Render the scene.
	 *
	 * @param scene The scene to render.
	 * @param renderBuffers The buffers for indirect drawing of models.
	 * @param gBuffer The buffer for geometry data.
	 * @param batch The subset of entities to draw.
	 */
	public void render(@NonNull Scene scene,
		@NonNull RenderBuffers renderBuffers, @NonNull GeometryBuffer gBuffer,
		@NonNull EntityBatch batch) {
		this.entitiesIndexMap.clear();
		CommandBuffer buffers = this.commandBuffers.get(batch);

		this.uniformsMap.setUniform(ShaderUniforms.Scene.PROJECTION_MATRIX,
			scene.getProjection().getProjMatrix());
		this.uniformsMap.setUniform(ShaderUniforms.Scene.VIEW_MATRIX,
			scene.getCamera().getViewMatrix());

		TextureCache textureCache = scene.getTextureCache();
		List<Texture> textures = textureCache.getAll().stream().toList();
		int numTextures = textures.size();
		if (numTextures > SceneRender.MAX_TEXTURES) {
			SceneRender.log.warn(
				SafeResourceLoader.getString("TOO_MANY_TEXTURES",
					GraphicsPlugin.getResourceBundle()),
				SceneRender.MAX_TEXTURES);
		}
		for (int i = 0; i < Math.min(SceneRender.MAX_TEXTURES,
			numTextures); ++i) {
			this.uniformsMap.setUniform(
				ShaderUniforms.Scene.TEXTURE_SAMPLER + "[" + i + "]", i);
			Texture texture = textures.get(i);
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
			texture.bind();
		}

		int entityIndex = 0;
		for (Model model : batch.getEntites().keySet()) {
			List<Entity> entities = batch.getEntites().get(model);
			for (Entity entity : entities) {
				this.uniformsMap.setUniform(ShaderUniforms.Scene.MODEL_MATRICES
					+ "[" + entityIndex + "]", entity.getModelMatrix());
				this.entitiesIndexMap.put(entity.getEntityID(), entityIndex);
				entityIndex++;
			}
		}

		// Static meshes
		int drawElement = 0;
		List<Model> modelList = batch.getEntites().keySet().stream()
			.filter(m -> !m.isAnimated()).toList();
		for (Model model : modelList) {
			List<Entity> entities = batch.getEntites().get(model);
			for (RenderBuffers.MeshDrawData meshDrawData : model
				.getMeshDrawDataList()) {
				for (Entity entity : entities) {
					String name = ShaderUniforms.Scene.DRAW_ELEMENTS + "["
						+ drawElement + "].";
					this.uniformsMap.setUniform(name
						+ ShaderUniforms.Scene.DrawElement.MODEL_MATRIX_INDEX,
						this.entitiesIndexMap.get(entity.getEntityID()));
					this.uniformsMap.setUniform(
						name + ShaderUniforms.Scene.DrawElement.MATERIAL_INDEX,
						meshDrawData.materialIndex());
					drawElement++;
				}
			}
		}
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,
			buffers.getStaticCommandBuffer());
		GL30.glBindVertexArray(renderBuffers.getStaticVaoID());
		GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES,
			GL11.GL_UNSIGNED_INT, 0, buffers.getStaticDrawCount(), 0);

		// Animated meshes
		drawElement = 0;
		modelList = batch.getEntites().keySet().stream()
			.filter(Model::isAnimated).toList();
		for (Model model : modelList) {
			for (RenderBuffers.MeshDrawData meshDrawData : model
				.getMeshDrawDataList()) {
				RenderBuffers.AnimMeshDrawData animMeshDrawData =
					meshDrawData.animMeshDrawData();
				Entity entity = animMeshDrawData.entity();
				String name = ShaderUniforms.Scene.DRAW_ELEMENTS + "["
					+ drawElement + "].";
				this.uniformsMap.setUniform(
					name + ShaderUniforms.Scene.DrawElement.MODEL_MATRIX_INDEX,
					this.entitiesIndexMap.get(entity.getEntityID()));
				this.uniformsMap.setUniform(
					name + ShaderUniforms.Scene.DrawElement.MATERIAL_INDEX,
					meshDrawData.materialIndex());
				drawElement++;
			}
		}
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,
			buffers.getAnimatedCommandBuffer());
		GL30.glBindVertexArray(renderBuffers.getAnimVaoID());
		GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES,
			GL11.GL_UNSIGNED_INT, 0, buffers.getAnimatedDrawCount(), 0);

		GL30.glBindVertexArray(0);

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
			MemoryUtil.memAlloc(numMeshes * SceneRender.COMMAND_SIZE);
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
			commandBuffer.remaining() / SceneRender.COMMAND_SIZE);

		int animRenderBufferHandle = GL15.glGenBuffers();
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, animRenderBufferHandle);
		GL15.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER, commandBuffer,
			GL15.GL_DYNAMIC_DRAW);

		MemoryUtil.memFree(commandBuffer);
		buffer.setAnimatedCommandBuffer(animRenderBufferHandle);
	}

	/**
	 * Set up data for the scene to prepare for rendering.
	 *
	 * @param scene The scene to render.
	 */
	public void setupData(@NonNull Scene scene) {
		this.recalculateMaterials(scene);
		this.clearCommandBuffers();
		log.debug("We have " + scene.getEntityBatches().size() + " batches");
		int i = 0;
		for (EntityBatch batch : scene.getEntityBatches()) {
			CommandBuffer buffers = new CommandBuffer();
			this.setupAnimCommandBuffer(batch, buffers);
			this.setupStaticCommandBuffer(batch, buffers);
			this.commandBuffers.put(batch, buffers);
			log.debug(i++ + " has " + batch.getModels().size() + " models:");
			batch.getModels().forEach(model -> {
				log.debug(String.format("%s has %d meshes and %d entitites",
					model.getId(), model.getMeshDrawDataList().size(),
					batch.get(model).size()));
			});
		}
	}

	/**
	 * Set the uniforms from cached textures and materials.
	 *
	 * @param textureCache The texture cache.
	 * @param materialCache The material cache.
	 */
	private void setupMaterialsUniform(@NonNull TextureCache textureCache,
		@NonNull MaterialCache materialCache) {
		List<Texture> textures = textureCache.getAll().stream().toList();
		int numTextures = textures.size();
		if (numTextures > SceneRender.MAX_TEXTURES) {
			SceneRender.log.warn(
				SafeResourceLoader.getString("TOO_MANY_TEXTURES",
					GraphicsPlugin.getResourceBundle()),
				SceneRender.MAX_TEXTURES);
		}
		Map<String, Integer> texturePosMap = new HashMap<>();
		for (int i = 0; i < Math.min(SceneRender.MAX_TEXTURES,
			numTextures); ++i) {
			texturePosMap.put(textures.get(i).getTexturePath(), i);
		}

		this.shaderProgram.bind();
		List<Material> materialList = materialCache.getMaterialsList();
		int numMaterials = materialList.size();
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
			String normalMapPath = material.getNormalMapPath();
			int index = 0;
			if (normalMapPath != null) {
				index = texturePosMap.computeIfAbsent(normalMapPath, k -> 0);
			}
			this.uniformsMap.setUniform(
				name + ShaderUniforms.Scene.Material.NORMAL_MAP_INDEX, index);
			Texture texture =
				textureCache.getTexture(material.getTexturePath());
			index =
				texturePosMap.computeIfAbsent(texture.getTexturePath(), k -> 0);
			this.uniformsMap.setUniform(
				name + ShaderUniforms.Scene.Material.TEXTURE_INDEX, index);
		}
		this.shaderProgram.unbind();
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
			MemoryUtil.memAlloc(numMeshes * SceneRender.COMMAND_SIZE);
		for (Model model : modelList) {
			List<Entity> entities = batch.get(model);
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
			commandBuffer.remaining() / SceneRender.COMMAND_SIZE);

		int staticRenderBufferHandle = GL15.glGenBuffers();
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,
			staticRenderBufferHandle);
		GL15.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER, commandBuffer,
			GL15.GL_DYNAMIC_DRAW);

		MemoryUtil.memFree(commandBuffer);
		buffer.setStaticCommandBuffer(staticRenderBufferHandle);
	}

	/**
	 * Set up before rendering the scene.
	 *
	 * @param scene The scene to render.
	 * @param gBuffer The buffer for geometry data.
	 */
	public void startRender(@NonNull Scene scene,
		@NonNull GeometryBuffer gBuffer) {
		GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
			gBuffer.getGBufferId());
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glViewport(0, 0, gBuffer.getWidth(), gBuffer.getHeight());
		GL11.glDisable(GL11.GL_BLEND);
		this.shaderProgram.bind();
	}
}