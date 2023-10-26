/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.render;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.GBuffer;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.graph.RenderBuffers;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Renders things on the screen.
 */
public class Render {
	/**
	 * Rendering configuration.
	 */
	@Getter
	@Setter
	public static class RenderConfig {
		/**
		 * Enable wireframe.
		 */
		private boolean wireframe;

		/**
		 * Post-processing filter that has been selected.
		 */
		private int selectedFilter;

		/**
		 * Whether we are actually drawing the scene.
		 */
		private boolean renderingScene;

		/**
		 * The list of filter names that are available. We use an array to make
		 * ImGui access easier.
		 */
		@Setter(value = AccessLevel.PACKAGE)
		private String[] filterNames;

		/**
		 * Sets the post-processing filter. Must be a valid index in the array
		 * of filters or an exception will be thrown.
		 *
		 * @param newFilter The index of the filter to use.
		 */
		public void setSelectedFilter(int newFilter) {
			if (newFilter < 0 || newFilter > this.filterNames.length) {
				throw new IllegalArgumentException(SafeResourceLoader
					.getStringFormatted("ILLEGAL_FILTER_SELECTION",
						GraphicsPlugin.getResourceBundle(), newFilter + "",
						this.filterNames.length + ""));
			}
			this.selectedFilter = newFilter;
		}
	}

	/**
	 * The size of a draw command.
	 */
	private static final int COMMAND_SIZE = 5 * 4;
	/**
	 * The size of a draw element.
	 */
	private static final int DRAW_ELEMENT_SIZE = 2 * 4;

	/**
	 * The size of a model matrix.
	 */
	private static final int MODEL_MATRIX_SIZE = 4 * 4;
	/**
	 * The binding for the draw elements buffer SSBO.
	 */
	static final int DRAW_ELEMENT_BINDING = 1;

	/**
	 * The binding for the model matrices buffer SSBO.
	 */
	static final int MODEL_MATRICES_BINDING = 2;

	/**
	 * Rendering configurations.
	 */
	public static final RenderConfig configuration = new RenderConfig();

	/**
	 * The animation render handler.
	 */
	private AnimationRender animationRender;

	/**
	 * Geometry buffer.
	 */
	private GBuffer gBuffer;

	/**
	 * The GUI render handler.
	 */
	private GuiRender guiRender;

	/**
	 * The lights render handler.
	 */
	private LightRender lightRender;

	/**
	 * The buffers for indirect drawing of models.
	 */
	private RenderBuffers renderBuffers;

	/**
	 * The scene render handler.
	 */
	private SceneRender sceneRender;

	/**
	 * The shadow render handler.
	 */
	private ShadowRender shadowRender;

	/**
	 * The skybox render handler.
	 */
	private SkyBoxRender skyBoxRender;

	/**
	 * Render a filter on top of the final result.
	 */
	private FilterRender filterRender;

	/**
	 * Whether we have set up the buffers for the scene. If we have, but need to
	 * set data up for the scene again, we will need to clear them out and start
	 * over.
	 */
	private AtomicBoolean buffersPopulated;
	/**
	 * The buffers for the batches.
	 */
	CommandBuffer commandBuffers;

	/**
	 * The Frame Buffer Object for the pre-filter render target.
	 */
	private int screenFBO;

	/**
	 * The depth RBO for the pre-filter render target.
	 */
	private int screenRBODepth;

	/**
	 * The texture ID we render to before applying filters.
	 */
	private int screenTexture;

	/**
	 * Set up a new rendering pipeline.
	 *
	 * @param window The window we are drawing on.
	 */
	public Render(@NonNull Window window) {
		GL.createCapabilities();
		GL11.glEnable(GL13.GL_MULTISAMPLE);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glEnable(GL11.GL_CULL_FACE);

		// Support for transparencies
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		this.sceneRender = new SceneRender();
		this.guiRender = new GuiRender(window);
		this.skyBoxRender = new SkyBoxRender();
		this.shadowRender = new ShadowRender();
		this.lightRender = new LightRender();
		this.animationRender = new AnimationRender();
		this.filterRender = new FilterRender();
		this.gBuffer = new GBuffer(window);
		this.renderBuffers = new RenderBuffers();
		this.buffersPopulated = new AtomicBoolean();
		this.commandBuffers = new CommandBuffer();

		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		this.screenTexture = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.screenTexture);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
			GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
			GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S,
			GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T,
			GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,
			GraphicsManager.getWindow().getWidth(),
			GraphicsManager.getWindow().getHeight(), 0, GL11.GL_RGBA,
			GL11.GL_UNSIGNED_BYTE, 0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

		this.screenRBODepth = GL30.glGenRenderbuffers();
		GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, this.screenRBODepth);
		GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,
			GL14.GL_DEPTH_COMPONENT16, GraphicsManager.getWindow().getWidth(),
			GraphicsManager.getWindow().getHeight());
		GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);

		this.screenFBO = GL30.glGenFramebuffers();
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.screenFBO);
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
			GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, this.screenTexture,
			0);
		GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,
			GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER,
			this.screenRBODepth);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
	}

	/**
	 * Clean up all the rendering resources.
	 */
	public void cleanup() {
		this.sceneRender.cleanup();
		this.guiRender.cleanup();
		this.skyBoxRender.cleanup();
		this.shadowRender.cleanup();
		this.lightRender.cleanup();
		this.animationRender.cleanup();
		this.filterRender.cleanup();
		this.gBuffer.cleanUp();
		this.renderBuffers.cleanup();
		this.commandBuffers.cleanup();
		GL30.glDeleteRenderbuffers(this.screenRBODepth);
		GL30.glDeleteFramebuffers(this.screenFBO);
		GL11.glDeleteTextures(this.screenTexture);
	}

	/**
	 * Reset the blending after we are done with the light rendering.
	 */
	private void lightRenderFinish() {
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
	}

	/**
	 * Set up for rendering lights.
	 *
	 * @param window The window we are rendering in.
	 */
	private void lightRenderStart(@NonNull Window window) {
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.screenFBO);
		GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, this.screenRBODepth);

		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.screenTexture);

		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glViewport(0, 0, window.getWidth(), window.getHeight());

		GL11.glEnable(GL11.GL_BLEND);
		GL14.glBlendEquation(GL14.GL_FUNC_ADD);
		GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
	}

	/**
	 * Set up uniforms for textures and materials.
	 *
	 * @param scene The scene to render.
	 */
	public void recalculateMaterials(@NonNull Scene scene) {
		this.sceneRender.recalculateMaterials(scene);
	}

	/**
	 * Render a scene on the window.
	 *
	 * @param window The window we are drawing in.
	 * @param scene The scene to render.
	 */
	public void render(@NonNull Window window, @NonNull Scene scene) {
		if (configuration.renderingScene) {
			this.updateModelMatrices(scene);

			this.animationRender.render(scene, this.renderBuffers);
			this.shadowRender.render(scene, this.renderBuffers,
				this.commandBuffers);

			if (Render.configuration.wireframe) {
				GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
				GL11.glDisable(GL11.GL_TEXTURE_2D);
			}

			this.sceneRender.render(scene, this.renderBuffers, this.gBuffer,
				this.commandBuffers);

			if (Render.configuration.wireframe) {
				GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
				GL11.glEnable(GL11.GL_TEXTURE_2D);
			}
			this.lightRenderStart(window);
			this.lightRender.render(scene, this.shadowRender, this.gBuffer);
			this.skyBoxRender.render(scene);
			this.lightRenderFinish();

			this.filterRender.render(scene, this.screenTexture);
		}
		this.guiRender.render(scene);
	}

	/**
	 * Update the GUI when we resize the screen.
	 *
	 * @param width The new screen width in pixels.
	 * @param height The new screen height in pixels.
	 */
	public void resize(int width, int height) {
		this.guiRender.resize(width, height);

		// Resize the screen space texture
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.screenTexture);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0,
			GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

		GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, this.screenRBODepth);
		GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,
			GL14.GL_DEPTH_COMPONENT16, width, height);
		GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
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
		int totalEntities = 0;
		int drawElementCount = 0;
		for (Model model : modelList) {
			numMeshes += model.getMeshDrawDataList().size();
			drawElementCount += model.getEntitiesList().size()
				* model.getMeshDrawDataList().size();
			totalEntities += model.getEntitiesList().size();
		}

		Map<String, Integer> entitiesIndexMap = new HashMap<>();

		// currently contains the size of the list of entities
		FloatBuffer modelMatrices =
			MemoryUtil.memAllocFloat(totalEntities * Render.MODEL_MATRIX_SIZE);

		int entityIndex = 0;
		for (Model model : modelList) {
			List<Entity> entities = model.getEntitiesList();
			for (Entity entity : entities) {
				entity.getModelMatrix()
					.get(entityIndex * Render.MODEL_MATRIX_SIZE, modelMatrices);
				entitiesIndexMap.put(entity.getEntityID(), entityIndex);
				entityIndex++;
			}
		}
		int modelMatrixBuffer = GL15.glGenBuffers();
		this.commandBuffers.setAnimatedModelMatricesBuffer(modelMatrixBuffer);
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, modelMatrixBuffer);
		GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, modelMatrices,
			GL15.GL_STATIC_DRAW);
		MemoryUtil.memFree(modelMatrices);

		int firstIndex = 0;
		int baseInstance = 0;
		ByteBuffer commandBuffer =
			MemoryUtil.memAlloc(numMeshes * Render.COMMAND_SIZE);
		ByteBuffer drawElements =
			MemoryUtil.memAlloc(drawElementCount * Render.DRAW_ELEMENT_SIZE);
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

				Entity entity = meshDrawData.animMeshDrawData().entity();
				// model matrix index
				drawElements.putInt(entitiesIndexMap.get(entity.getEntityID()));
				drawElements.putInt(meshDrawData.materialIndex());
			}
		}
		commandBuffer.flip();
		drawElements.flip();

		this.commandBuffers.setAnimatedDrawCount(
			commandBuffer.remaining() / Render.COMMAND_SIZE);

		int animRenderBufferHandle = GL15.glGenBuffers();
		this.commandBuffers.setAnimatedCommandBuffer(animRenderBufferHandle);
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, animRenderBufferHandle);
		GL15.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER, commandBuffer,
			GL15.GL_DYNAMIC_DRAW);
		MemoryUtil.memFree(commandBuffer);

		int drawElementBuffer = GL15.glGenBuffers();
		this.commandBuffers.setAnimatedDrawElementBuffer(drawElementBuffer);
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, drawElementBuffer);
		GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, drawElements,
			GL15.GL_STATIC_DRAW);
		MemoryUtil.memFree(drawElements);
	}

	/**
	 * Set up model data before rendering.
	 *
	 * @param scene The scene to read models from.
	 */
	public void setupData(@NonNull Scene scene) {
		if (this.buffersPopulated.getAndSet(false)) {
			this.renderBuffers.cleanup();
			this.commandBuffers.cleanup();
		}
		this.renderBuffers.loadStaticModels(scene);
		this.renderBuffers.loadAnimatedModels(scene);
		this.sceneRender.recalculateMaterials(scene);
		this.setupAnimCommandBuffer(scene);
		this.setupStaticCommandBuffer(scene);
		this.buffersPopulated.set(true);
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
		int totalEntities = 0;
		int drawElementCount = 0;
		for (Model model : modelList) {
			numMeshes += model.getMeshDrawDataList().size();
			drawElementCount += model.getEntitiesList().size()
				* model.getMeshDrawDataList().size();
			totalEntities += model.getEntitiesList().size();
		}

		Map<String, Integer> entitiesIndexMap = new HashMap<>();

		// currently contains the size of the list of entities
		FloatBuffer modelMatrices =
			MemoryUtil.memAllocFloat(totalEntities * Render.MODEL_MATRIX_SIZE);

		int entityIndex = 0;
		for (Model model : modelList) {
			List<Entity> entities = model.getEntitiesList();
			for (Entity entity : entities) {
				entity.getModelMatrix()
					.get(entityIndex * Render.MODEL_MATRIX_SIZE, modelMatrices);
				entitiesIndexMap.put(entity.getEntityID(), entityIndex);
				entityIndex++;
			}
		}
		int modelMatrixBuffer = GL15.glGenBuffers();
		this.commandBuffers.setStaticModelMatricesBuffer(modelMatrixBuffer);
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, modelMatrixBuffer);
		GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, modelMatrices,
			GL15.GL_STATIC_DRAW);
		MemoryUtil.memFree(modelMatrices);

		int firstIndex = 0;
		int baseInstance = 0;
		ByteBuffer commandBuffer =
			MemoryUtil.memAlloc(numMeshes * Render.COMMAND_SIZE);
		ByteBuffer drawElements =
			MemoryUtil.memAlloc(drawElementCount * Render.DRAW_ELEMENT_SIZE);

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
				baseInstance += numEntities;

				int materialIndex = meshDrawData.materialIndex();
				for (Entity entity : entities) {
					// model matrix index
					drawElements
						.putInt(entitiesIndexMap.get(entity.getEntityID()));
					drawElements.putInt(materialIndex);
				}
			}
		}

		commandBuffer.flip();
		drawElements.flip();

		this.commandBuffers.setStaticDrawCount(
			commandBuffer.remaining() / Render.COMMAND_SIZE);

		int staticRenderBufferHandle = GL15.glGenBuffers();
		this.commandBuffers.setStaticCommandBuffer(staticRenderBufferHandle);
		GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,
			staticRenderBufferHandle);
		GL15.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER, commandBuffer,
			GL15.GL_DYNAMIC_DRAW);
		MemoryUtil.memFree(commandBuffer);

		int drawElementBuffer = GL15.glGenBuffers();
		this.commandBuffers.setStaticDrawElementBuffer(drawElementBuffer);
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, drawElementBuffer);
		GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, drawElements,
			GL15.GL_STATIC_DRAW);
		MemoryUtil.memFree(drawElements);

	}

	/**
	 * Take a list of models and upload the model matrices to the appropriate
	 * buffer.
	 *
	 * @param models The list of models.
	 * @param bufferID The opengl ID of the buffer we want to buffer data to.
	 */
	private void updateModelBuffer(List<Model> models, int bufferID) {
		int totalEntities = 0;
		for (Model model : models) {
			totalEntities += model.getEntitiesList().size();
		}

		// currently contains the size of the list of entities
		FloatBuffer modelMatrices =
			MemoryUtil.memAllocFloat(totalEntities * Render.MODEL_MATRIX_SIZE);

		int entityIndex = 0;
		for (Model model : models) {
			List<Entity> entities = model.getEntitiesList();
			for (Entity entity : entities) {
				entity.getModelMatrix()
					.get(entityIndex * Render.MODEL_MATRIX_SIZE, modelMatrices);
				entityIndex++;
			}
		}
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, bufferID);
		GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, modelMatrices,
			GL15.GL_STATIC_DRAW);
		MemoryUtil.memFree(modelMatrices);
	}

	/**
	 * Update the model matrices buffers for all the scene objects.
	 *
	 * @param scene The scene we are rendering.
	 */
	private void updateModelMatrices(@NonNull Scene scene) {
		List<Model> animatedList = scene.getModelMap().values().stream()
			.filter(Model::isAnimated).toList();
		int animatedBuffer =
			this.commandBuffers.getAnimatedModelMatricesBuffer();
		this.updateModelBuffer(animatedList, animatedBuffer);

		List<Model> staticList = scene.getModelMap().values().stream()
			.filter(m -> !m.isAnimated()).toList();
		int staticBuffer = this.commandBuffers.getStaticModelMatricesBuffer();
		this.updateModelBuffer(staticList, staticBuffer);

	}
}