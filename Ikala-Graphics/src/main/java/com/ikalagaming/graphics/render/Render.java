package com.ikalagaming.graphics.render;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.GBuffer;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.graph.RenderBuffers;
import com.ikalagaming.graphics.scene.Scene;

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
	private SkyboxRender skyBoxRender;

	/**
	 * Set up a new rendering pipeline.
	 *
	 * @param window The window we are drawing on.
	 */
	public Render(Window window) {
		GL.createCapabilities();
		GL11.glEnable(GL13.GL_MULTISAMPLE);
		GL11.glEnable(GL11.GL_DEPTH_TEST);

		// Support for transparencies
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		this.sceneRender = new SceneRender();
		this.guiRender = new GuiRender(window);
		this.skyBoxRender = new SkyboxRender();
		this.shadowRender = new ShadowRender();
		this.lightsRender = new LightsRender();
		this.animationRender = new AnimationRender();
		this.gBuffer = new GBuffer(window);
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
		this.gBuffer.cleanup();
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
	private void lightRenderStart(Window window) {
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
	public void render(Window window, Scene scene) {
		this.animationRender.render(scene, this.renderBuffers);
		this.shadowRender.render(scene, this.renderBuffers);
		this.sceneRender.render(scene, this.renderBuffers, this.gBuffer);
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
	public void setupData(Scene scene) {
		this.renderBuffers.loadStaticModels(scene);
		this.renderBuffers.loadAnimatedModels(scene);
		this.sceneRender.setupData(scene);
		this.shadowRender.setupData(scene);
		List<Model> modelList = new ArrayList<>(scene.getModelMap().values());
		modelList.forEach(m -> m.getMeshDataList().clear());
	}
}
