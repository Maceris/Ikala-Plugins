package com.ikalagaming.graphics.render;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.graph.GBuffer;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.graph.RenderBuffers;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.Texture;
import com.ikalagaming.graphics.graph.TextureCache;
import com.ikalagaming.graphics.graph.UniformsMap;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;

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
import java.util.stream.Collectors;

/**
 * Handles rendering for the scene geometry.
 */
@Slf4j
public class SceneRender {
	/**
	 * The maximum number of draw elements that we can process.
	 */
	public static final int MAX_DRAW_ELEMENTS = 100;
	/**
	 * The maximum number of entities that we can process.
	 */
	public static final int MAX_ENTITIES = 50;
	/**
	 * The size of a draw command.
	 */
	private static final int COMMAND_SIZE = 5 * 4;
	/**
	 * The maximum number of materials we can have.
	 */
	private static final int MAX_MATERIALS = 20;
	/**
	 * The maximum number of textures we can have.
	 */
	private static final int MAX_TEXTURES = 16;
	/**
	 * How many animated model draw commands we have set up.
	 */
	private int animDrawCount;
	/**
	 * The animated render buffer ID.
	 */
	private int animRenderBufferHandle;
	/**
	 * A map from entity ID to its index in the draw elements uniform array.
	 */
	private Map<String, Integer> entitiesIdxMap;
	/**
	 * The shader program for the scene.
	 */
	private ShaderProgram shaderProgram;
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
		this.entitiesIdxMap = new HashMap<>();
	}

	/**
	 * Clean up buffers and shaders.
	 */
	public void cleanup() {
		this.shaderProgram.cleanup();
		GL15.glDeleteBuffers(this.staticRenderBufferHandle);
		GL15.glDeleteBuffers(this.animRenderBufferHandle);
	}

	/**
	 * Set up the uniforms for the shader.
	 */
	private void createUniforms() {
		this.uniformsMap = new UniformsMap(this.shaderProgram.getProgramId());
		this.uniformsMap.createUniform(ShaderUniforms.Scene.PROJECTION_MATRIX);
		this.uniformsMap.createUniform(ShaderUniforms.Scene.VIEW_MATRIX);

		for (int i = 0; i < SceneRender.MAX_TEXTURES; i++) {
			this.uniformsMap.createUniform(
				ShaderUniforms.Scene.TEXTURE_SAMPLER + "[" + i + "]");
		}

		for (int i = 0; i < SceneRender.MAX_MATERIALS; i++) {
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

		for (int i = 0; i < SceneRender.MAX_DRAW_ELEMENTS; i++) {
			String name = ShaderUniforms.Scene.DRAW_ELEMENTS + "[" + i + "].";
			this.uniformsMap.createUniform(
				name + ShaderUniforms.Scene.DrawElement.MODEL_MATRIX_INDEX);
			this.uniformsMap.createUniform(
				name + ShaderUniforms.Scene.DrawElement.MATERIAL_INDEX);
		}

		for (int i = 0; i < SceneRender.MAX_ENTITIES; i++) {
			this.uniformsMap.createUniform(
				ShaderUniforms.Scene.MODEL_MATRICES + "[" + i + "]");
		}
	}

	/**
	 * Render the scene.
	 *
	 * @param scene The scene to render.
	 * @param renderBuffers The buffers for indirect drawing of models.
	 * @param gBuffer The buffer for geometry data.
	 */
	public void render(Scene scene, RenderBuffers renderBuffers,
		GBuffer gBuffer) {
		GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
			gBuffer.getGBufferId());
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glViewport(0, 0, gBuffer.getWidth(), gBuffer.getHeight());
		GL11.glDisable(GL11.GL_BLEND);

		this.shaderProgram.bind();

		this.uniformsMap.setUniform(ShaderUniforms.Scene.PROJECTION_MATRIX,
			scene.getProjection().getProjectionMatrix());
		this.uniformsMap.setUniform(ShaderUniforms.Scene.VIEW_MATRIX,
			scene.getCamera().getViewMatrix());

		TextureCache textureCache = scene.getTextureCache();
		List<Texture> textures =
			textureCache.getAll().stream().collect(Collectors.toList());
		int numTextures = textures.size();
		if (numTextures > SceneRender.MAX_TEXTURES) {
			SceneRender.log.warn(
				"Only " + SceneRender.MAX_TEXTURES + " textures can be used");
		}
		for (int i = 0; i < Math.min(SceneRender.MAX_TEXTURES,
			numTextures); i++) {
			this.uniformsMap.setUniform(
				ShaderUniforms.Scene.TEXTURE_SAMPLER + "[" + i + "]", i);
			Texture texture = textures.get(i);
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
			texture.bind();
		}

		int entityIdx = 0;
		for (Model model : scene.getModelMap().values()) {
			List<Entity> entities = model.getEntitiesList();
			for (Entity entity : entities) {
				this.uniformsMap.setUniform(
					ShaderUniforms.Scene.MODEL_MATRICES + "[" + entityIdx + "]",
					entity.getModelMatrix());
				entityIdx++;
			}
		}

		// Static meshes
		int drawElement = 0;
		List<Model> modelList = scene.getModelMap().values().stream()
			.filter(m -> !m.isAnimated()).collect(Collectors.toList());
		for (Model model : modelList) {
			List<Entity> entities = model.getEntitiesList();
			for (RenderBuffers.MeshDrawData meshDrawData : model
				.getMeshDrawDataList()) {
				for (Entity entity : entities) {
					String name = ShaderUniforms.Scene.DRAW_ELEMENTS + "["
						+ drawElement + "].";
					this.uniformsMap.setUniform(name
						+ ShaderUniforms.Scene.DrawElement.MODEL_MATRIX_INDEX,
						this.entitiesIdxMap.get(entity.getId()));
					this.uniformsMap.setUniform(
						name + ShaderUniforms.Scene.DrawElement.MATERIAL_INDEX,
						meshDrawData.getMaterialIndex());
					drawElement++;
				}
			}
		}
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,
			this.staticRenderBufferHandle);
		GL30.glBindVertexArray(renderBuffers.getStaticVaoId());
		GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES,
			GL11.GL_UNSIGNED_INT, 0, this.staticDrawCount, 0);

		// Animated meshes
		drawElement = 0;
		modelList = scene.getModelMap().values().stream()
			.filter(Model::isAnimated).collect(Collectors.toList());
		for (Model model : modelList) {
			for (RenderBuffers.MeshDrawData meshDrawData : model
				.getMeshDrawDataList()) {
				RenderBuffers.AnimMeshDrawData animMeshDrawData =
					meshDrawData.getAnimMeshDrawData();
				Entity entity = animMeshDrawData.getEntity();
				String name = ShaderUniforms.Scene.DRAW_ELEMENTS + "["
					+ drawElement + "].";
				this.uniformsMap.setUniform(
					name + ShaderUniforms.Scene.DrawElement.MODEL_MATRIX_INDEX,
					this.entitiesIdxMap.get(entity.getId()));
				this.uniformsMap.setUniform(
					name + ShaderUniforms.Scene.DrawElement.MATERIAL_INDEX,
					meshDrawData.getMaterialIndex());
				drawElement++;
			}
		}
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,
			this.animRenderBufferHandle);
		GL30.glBindVertexArray(renderBuffers.getAnimVaoId());
		GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES,
			GL11.GL_UNSIGNED_INT, 0, this.animDrawCount, 0);

		GL30.glBindVertexArray(0);
		GL11.glEnable(GL11.GL_BLEND);
		this.shaderProgram.unbind();
	}

	/**
	 * Set up the command buffer for animated models.
	 *
	 * @param scene The scene to render.
	 */
	private void setupAnimCommandBuffer(Scene scene) {
		List<Model> modelList = scene.getModelMap().values().stream()
			.filter(Model::isAnimated).collect(Collectors.toList());
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
			commandBuffer.remaining() / SceneRender.COMMAND_SIZE;

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
	public void setupData(Scene scene) {
		this.setupEntitiesData(scene);
		this.setupStaticCommandBuffer(scene);
		this.setupAnimCommandBuffer(scene);
		this.setupMaterialsUniform(scene.getTextureCache(),
			scene.getMaterialCache());
	}

	/**
	 * Populate the the entities index map from the scene.
	 *
	 * @param scene The scene to render.
	 */
	private void setupEntitiesData(Scene scene) {
		this.entitiesIdxMap.clear();
		int entityIdx = 0;
		for (Model model : scene.getModelMap().values()) {
			List<Entity> entities = model.getEntitiesList();
			for (Entity entity : entities) {
				this.entitiesIdxMap.put(entity.getId(), entityIdx);
				entityIdx++;
			}
		}
	}

	/**
	 * Set the uniforms from cached textures and materials.
	 *
	 * @param textureCache The texture cache.
	 * @param materialCache The material cache.
	 */
	private void setupMaterialsUniform(TextureCache textureCache,
		MaterialCache materialCache) {
		List<Texture> textures =
			textureCache.getAll().stream().collect(Collectors.toList());
		int numTextures = textures.size();
		if (numTextures > SceneRender.MAX_TEXTURES) {
			SceneRender.log.warn(
				"Only " + SceneRender.MAX_TEXTURES + " textures can be used");
		}
		Map<String, Integer> texturePosMap = new HashMap<>();
		for (int i = 0; i < Math.min(SceneRender.MAX_TEXTURES,
			numTextures); i++) {
			texturePosMap.put(textures.get(i).getTexturePath(), i);
		}

		this.shaderProgram.bind();
		List<Material> materialList = materialCache.getMaterialsList();
		int numMaterials = materialList.size();
		for (int i = 0; i < numMaterials; i++) {
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
			int idx = 0;
			if (normalMapPath != null) {
				idx = texturePosMap.computeIfAbsent(normalMapPath, k -> 0);
			}
			this.uniformsMap.setUniform(
				name + ShaderUniforms.Scene.Material.NORMAL_MAP_INDEX, idx);
			Texture texture =
				textureCache.getTexture(material.getTexturePath());
			idx =
				texturePosMap.computeIfAbsent(texture.getTexturePath(), k -> 0);
			this.uniformsMap.setUniform(
				name + ShaderUniforms.Scene.Material.TEXTURE_INDEX, idx);
		}
		this.shaderProgram.unbind();
	}

	/**
	 * Set up the command buffer for static models.
	 *
	 * @param scene The scene to render.
	 */
	private void setupStaticCommandBuffer(Scene scene) {
		List<Model> modelList = scene.getModelMap().values().stream()
			.filter(m -> !m.isAnimated()).collect(Collectors.toList());
		int numMeshes = 0;
		for (Model model : modelList) {
			numMeshes += model.getMeshDrawDataList().size();
		}

		int firstIndex = 0;
		int baseInstance = 0;
		ByteBuffer commandBuffer =
			MemoryUtil.memAlloc(numMeshes * SceneRender.COMMAND_SIZE);
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
			commandBuffer.remaining() / SceneRender.COMMAND_SIZE;

		this.staticRenderBufferHandle = GL15.glGenBuffers();
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,
			this.staticRenderBufferHandle);
		GL15.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER, commandBuffer,
			GL15.GL_DYNAMIC_DRAW);

		MemoryUtil.memFree(commandBuffer);
	}
}
