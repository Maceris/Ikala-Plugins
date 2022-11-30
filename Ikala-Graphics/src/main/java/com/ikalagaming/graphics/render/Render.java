/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.render;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.GeometryBuffer;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.graph.RenderBuffers;
import com.ikalagaming.graphics.scene.Scene;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

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
	}

	/**
	 * Rendering configurations.
	 */
	public static RenderConfig configuration = new RenderConfig();
	/**
	 * The animation render handler.
	 */
	private AnimationRender animationRender;
	/**
	 * Geometry buffer.
	 */
	private GeometryBuffer gBuffer;
	/**
	 * The GUI render handler.
	 */
	private GuiRender guiRender;
	/**
	 * The lights render handler.
	 */
	private LightsRender lightsRender;
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
		this.lightsRender = new LightsRender();
		this.animationRender = new AnimationRender();
		this.gBuffer = new GeometryBuffer(window);
		this.renderBuffers = new RenderBuffers();
	}

	/**
	 * Clean up all the rendering resources.
	 */
	public void cleanup() {
		this.sceneRender.cleanup();
		this.guiRender.cleanup();
		this.skyBoxRender.cleanup();
		this.shadowRender.cleanup();
		this.lightsRender.cleanup();
		this.animationRender.cleanup();
		this.gBuffer.cleanUp();
		this.renderBuffers.cleanup();
	}

	/**
	 * Reset the blending after we are done with the light rendering.
	 */
	private void lightRenderFinish() {
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
	}

	/**
	 * Set up for rendering lights.
	 *
	 * @param window The window we are rendering in.
	 */
	private void lightRenderStart(@NonNull Window window) {
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glViewport(0, 0, window.getWidth(), window.getHeight());

		GL11.glEnable(GL11.GL_BLEND);
		GL14.glBlendEquation(GL14.GL_FUNC_ADD);
		GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);

		GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
			this.gBuffer.getGBufferId());
	}

	/**
	 * Render a scene on the window.
	 *
	 * @param window The window we are drawing in.
	 * @param scene The scene to render.
	 */
	public void render(@NonNull Window window, @NonNull Scene scene) {
		this.animationRender.render(scene, this.renderBuffers);
		this.shadowRender.render(scene, this.renderBuffers);
		if (Render.configuration.isWireframe()) {
			GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
			GL11.glDisable(GL11.GL_TEXTURE_2D);
		}
		this.sceneRender.render(scene, this.renderBuffers, this.gBuffer);
		if (Render.configuration.isWireframe()) {
			GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
		}
		this.lightRenderStart(window);
		this.lightsRender.render(scene, this.shadowRender, this.gBuffer);
		this.skyBoxRender.render(scene);
		this.lightRenderFinish();
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
	}

	/**
	 * Set up model data before rendering.
	 *
	 * @param scene The scene to read models from.
	 */
	public void setupData(@NonNull Scene scene) {
		this.renderBuffers.loadStaticModels(scene);
		this.renderBuffers.loadAnimatedModels(scene);
		this.sceneRender.setupData(scene);
		this.shadowRender.setupData(scene);
		List<Model> modelList = new ArrayList<>(scene.getModelMap().values());
		modelList.forEach(m -> m.getMeshDataList().clear());
	}

	/**
	 * Set up uniforms for textures and materials.
	 * 
	 * @param scene The scene to render.
	 */
	public void recalculateMaterials(@NonNull Scene scene) {
		this.sceneRender.recalculateMaterials(scene);
	}
}