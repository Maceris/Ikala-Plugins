package com.ikalagaming.graphics;

import com.ikalagaming.graphics.events.WindowCreated;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.render.Render;
import com.ikalagaming.graphics.scene.AnimationData;
import com.ikalagaming.graphics.scene.Camera;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Fog;
import com.ikalagaming.graphics.scene.ModelLoader;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.SkyBox;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.scene.lights.PointLight;
import com.ikalagaming.graphics.scene.lights.SceneLights;
import com.ikalagaming.graphics.scene.lights.SpotLight;
import com.ikalagaming.launcher.Launcher;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides utilities for handling graphics.
 */
@Slf4j
public class GraphicsManager {

	/**
	 * Whether we are currently initialized.
	 */
	static AtomicBoolean initialized = new AtomicBoolean(false);

	/**
	 * Whether we want to refresh the scene information.
	 */
	private static AtomicBoolean refreshRequested = new AtomicBoolean(false);

	/**
	 * The scene we are rendering.
	 * 
	 * @return The current scene.
	 */
	@Getter
	private static Scene scene;
	/**
	 * The rendering handler.
	 * 
	 * @return The render stages.
	 */
	@Getter
	private static Render render;
	/**
	 * The camera manager.
	 * 
	 * @return The camera manager.
	 */
	@Getter
	private static CameraManager cameraManager;
	/**
	 * Used to track when we should render another frame.
	 */
	private static Timer renderTimer;
	/**
	 * Used to track when we should make updates.
	 */
	private static Timer updateTimer;
	/**
	 * The target frames per second that we want to hit.
	 */
	public static final int TARGET_FPS = 60;
	/**
	 * The target frames per second that we want to hit.
	 */
	public static final int TARGET_UPS = 60;
	/**
	 * The (fractional) number of seconds between frame renders at the target
	 * FPS.
	 */
	private static final float FRAME_TIME = 1f / GraphicsManager.TARGET_FPS;
	/**
	 * The (fractional) number of seconds between updates at the target UPS.
	 */
	private static final float UPDATE_TIME = 1f / GraphicsManager.TARGET_UPS;
	/**
	 * The time when the next render call should happen to maintain the target
	 * FPS.
	 */
	private static double nextRenderTime;
	/**
	 * The time when the next render call should happen to maintain the target
	 * UPS.
	 */
	private static double nextUpdateTime;
	/**
	 * The current time, stored here to prevent extra doubles.
	 */
	private static double currentTime;
	/**
	 * The last time we rendered a frame, for calculating FPS.
	 */
	private static double lastTime;
	/**
	 * How many frames we rendered since we calculated FPS.
	 */
	private static int framesSinceLastCalculation;
	/**
	 * The last recorded Frames Per Second.
	 * 
	 * @return The last known FPS.
	 */
	@Getter
	private static int lastFPS;

	/**
	 * Whether we should shut down.
	 */
	@Getter(value = AccessLevel.PACKAGE)
	private static AtomicBoolean shutdownFlag = new AtomicBoolean(false);
	/**
	 * Used to track the tick method reference in the framework.
	 *
	 * @param tickStageID The stage ID to use.
	 * @return The tick stage ID.
	 */
	@Getter(value = AccessLevel.PACKAGE)
	@Setter(value = AccessLevel.PACKAGE)
	private static UUID tickStageID;
	/**
	 * The window utility.
	 *
	 * @return The window object.
	 */
	@Getter
	private static Window window;

	private static AnimationData animationData1;
	private static AnimationData animationData2;
	private static Entity cubeEntity1;
	private static Entity cubeEntity2;
	private static float lightAngle;
	private static float rotation;

	/**
	 * Creates a graphics window, fires off a {@link WindowCreated} event. Won't
	 * do anything if a window already exists.
	 *
	 */
	public static void createWindow() {
		if ((null != GraphicsManager.window)
			|| !GraphicsManager.initialized.compareAndSet(false, true)) {
			return;
		}
		GraphicsManager.shutdownFlag.set(false);

		Window.WindowOptions opts = new Window.WindowOptions();
		opts.antiAliasing = true;
		GraphicsManager.window = new Window("Ikala Gaming", opts, () -> {
			GraphicsManager.resize();
			return null;
		});

		GraphicsManager.log.debug(SafeResourceLoader.getString("WINDOW_CREATED",
			GraphicsPlugin.getResourceBundle()));
		new WindowCreated(GraphicsManager.window.getWindowHandle()).fire();

		GraphicsManager.render = new Render(GraphicsManager.window);

		GraphicsManager.log.debug(SafeResourceLoader
			.getString("RENDERER_CREATED", GraphicsPlugin.getResourceBundle()));

		GraphicsManager.scene = new Scene(GraphicsManager.window.getWidth(),
			GraphicsManager.window.getHeight());

		GraphicsManager.cameraManager = new CameraManager(
			GraphicsManager.scene.getCamera(), GraphicsManager.window);

		GraphicsManager.init();

		GraphicsManager.renderTimer = new Timer();
		GraphicsManager.renderTimer.init();
		GraphicsManager.nextRenderTime =
			GraphicsManager.renderTimer.getLastLoopTime()
				+ GraphicsManager.FRAME_TIME;

		GraphicsManager.updateTimer = new Timer();
		GraphicsManager.updateTimer.init();
		GraphicsManager.nextUpdateTime =
			GraphicsManager.renderTimer.getLastLoopTime()
				+ GraphicsManager.UPDATE_TIME;

		GraphicsManager.lastTime = GLFW.glfwGetTime();
	}

	/**
	 * Initialize the application.
	 */
	private static void init() {
		String terrainModelId = "terrain";
		Model terrainModel =
			ModelLoader.loadModel(terrainModelId, "models/terrain/terrain.obj",
				GraphicsManager.scene.getTextureCache(),
				GraphicsManager.scene.getMaterialCache(), false);
		GraphicsManager.scene.addModel(terrainModel);
		Entity terrainEntity = new Entity("terrainEntity", terrainModelId);
		terrainEntity.setScale(100.0f);
		terrainEntity.updateModelMatrix();
		GraphicsManager.scene.addEntity(terrainEntity);

		String bobModelId = "bobModel";
		Model bobModel =
			ModelLoader.loadModel(bobModelId, "models/bob/boblamp.md5mesh",
				GraphicsManager.scene.getTextureCache(),
				GraphicsManager.scene.getMaterialCache(), true);
		GraphicsManager.scene.addModel(bobModel);
		Entity bobEntity = new Entity("bobEntity-1", bobModelId);
		bobEntity.setScale(0.05f);
		bobEntity.updateModelMatrix();
		GraphicsManager.animationData1 =
			new AnimationData(bobModel.getAnimationList().get(0));
		bobEntity.setAnimationData(GraphicsManager.animationData1);
		GraphicsManager.scene.addEntity(bobEntity);

		Entity bobEntity2 = new Entity("bobEntity-2", bobModelId);
		bobEntity2.setPosition(2, 0, 0);
		bobEntity2.setScale(0.025f);
		bobEntity2.updateModelMatrix();
		GraphicsManager.animationData2 =
			new AnimationData(bobModel.getAnimationList().get(0));
		bobEntity2.setAnimationData(GraphicsManager.animationData2);
		GraphicsManager.scene.addEntity(bobEntity2);

		Model cubeModel = ModelLoader.loadModel("cube-model",
			"models/cube/cube.obj", GraphicsManager.scene.getTextureCache(),
			GraphicsManager.scene.getMaterialCache(), false);
		GraphicsManager.scene.addModel(cubeModel);
		GraphicsManager.cubeEntity1 =
			new Entity("cube-entity-1", cubeModel.getId());
		GraphicsManager.cubeEntity1.setPosition(0, 2, -1);
		GraphicsManager.cubeEntity1.updateModelMatrix();
		GraphicsManager.scene.addEntity(GraphicsManager.cubeEntity1);

		GraphicsManager.cubeEntity2 =
			new Entity("cube-entity-2", cubeModel.getId());
		GraphicsManager.cubeEntity2.setPosition(-2, 2, -1);
		GraphicsManager.cubeEntity2.updateModelMatrix();
		GraphicsManager.scene.addEntity(GraphicsManager.cubeEntity2);

		Model floorModel =
			ModelLoader.loadModel("floor_001", "models/dungeon/floor_001.obj",
				GraphicsManager.getScene().getTextureCache(),
				GraphicsManager.getScene().getMaterialCache(), true);
		GraphicsManager.getScene().addModel(floorModel);

		Model wallModel =
			ModelLoader.loadModel("brick_wall", "models/dungeon/brick_wall.obj",
				GraphicsManager.getScene().getTextureCache(),
				GraphicsManager.getScene().getMaterialCache(), true);
		GraphicsManager.getScene().addModel(wallModel);

		Model wallCornerModel = ModelLoader.loadModel("brick_wall_corner",
			"models/dungeon/brick_wall_corner.obj",
			GraphicsManager.getScene().getTextureCache(),
			GraphicsManager.getScene().getMaterialCache(), true);
		GraphicsManager.getScene().addModel(wallCornerModel);

		Model wallAllSidesModel = ModelLoader.loadModel("brick_wall_all_sides",
			"models/dungeon/brick_wall_all_sides.obj",
			GraphicsManager.getScene().getTextureCache(),
			GraphicsManager.getScene().getMaterialCache(), true);
		GraphicsManager.getScene().addModel(wallAllSidesModel);

		Model wallOppositeSidesModel =
			ModelLoader.loadModel("brick_wall_opposite_sides",
				"models/dungeon/brick_wall_opposite_sides.obj",
				GraphicsManager.getScene().getTextureCache(),
				GraphicsManager.getScene().getMaterialCache(), true);
		GraphicsManager.getScene().addModel(wallOppositeSidesModel);

		Model wallThreeSidesModel =
			ModelLoader.loadModel("brick_wall_three_sides",
				"models/dungeon/brick_wall_three_sides.obj",
				GraphicsManager.getScene().getTextureCache(),
				GraphicsManager.getScene().getMaterialCache(), true);
		GraphicsManager.getScene().addModel(wallThreeSidesModel);

		SceneLights sceneLights = new SceneLights();
		AmbientLight ambientLight = sceneLights.getAmbientLight();
		ambientLight.setIntensity(0.5f);
		ambientLight.setColor(0.3f, 0.3f, 0.3f);

		DirectionalLight dirLight = sceneLights.getDirLight();
		dirLight.setPosition(0, 1, 0);
		dirLight.setIntensity(1.0f);

		sceneLights.getPointLights().add(new PointLight(new Vector3f(1, 1, 1),
			new Vector3f(0, 2.5f, -0.4f), 1.0f));

		sceneLights.getSpotLights()
			.add(
				new SpotLight(
					new PointLight(new Vector3f(1, 1, 1),
						new Vector3f(0, 0, -1.6f), 1.0f),
					new Vector3f(0, 0, -1.4f), 1.0f));

		GraphicsManager.scene.setSceneLights(sceneLights);

		SkyBox skyBox = new SkyBox("models/skybox/skybox.obj",
			GraphicsManager.scene.getTextureCache(),
			GraphicsManager.scene.getMaterialCache());
		skyBox.getSkyBoxEntity().setScale(100);
		skyBox.getSkyBoxEntity().updateModelMatrix();
		GraphicsManager.scene.setSkyBox(skyBox);

		GraphicsManager.scene
			.setFog(new Fog(true, new Vector3f(0.5f, 0.5f, 0.5f), 0.02f));

		Camera camera = GraphicsManager.scene.getCamera();
		camera.setPosition(-1.5f, 3.0f, 4.5f);
		camera.addRotation((float) Math.toRadians(15.0f),
			(float) Math.toRadians(390.f));

		GraphicsManager.lightAngle = 45.001f;
		
		GraphicsManager.render.setupData(GraphicsManager.scene);
	}

	/**
	 * Set up the render data after adding/removing renderables.
	 */
	public static void refreshRenderData() {
		refreshRequested.set(true);
	}

	/**
	 * Render to the screen.
	 *
	 * @param elapsedTime The time since the last render.
	 */
	private static void render(final float elapsedTime) {
		GraphicsManager.render.render(GraphicsManager.window,
			GraphicsManager.scene);

		GraphicsManager.window.update();
		++GraphicsManager.framesSinceLastCalculation;

		GraphicsManager.currentTime = GLFW.glfwGetTime();
		if (GraphicsManager.currentTime - GraphicsManager.lastTime >= 1d) {
			GraphicsManager.lastFPS =
				GraphicsManager.framesSinceLastCalculation;
			GraphicsManager.framesSinceLastCalculation = 0;
			GraphicsManager.lastTime = GraphicsManager.currentTime;
		}
	}

	private static void resize() {
		int width = GraphicsManager.window.getWidth();
		int height = GraphicsManager.window.getHeight();
		GraphicsManager.scene.resize(width, height);
		GraphicsManager.render.resize(width, height);
	}

	/**
	 * Terminate GLFW and free the error callback. If any windows still remain,
	 * they are destroyed.
	 */
	public static void terminate() {
		GraphicsManager.render.cleanup();

		if (null != GraphicsManager.window) {
			GraphicsManager.window.destroy();
			GraphicsManager.window = null;
		}

		GLFW.glfwTerminate();
		GLFW.glfwSetErrorCallback(null).free();
		GraphicsManager.initialized.set(false);
	}

	/**
	 * Does a graphical update. Returns the value of if it still exists. That
	 * is, returns false if the window was destroyed and true if it still
	 * exists.
	 *
	 * @return True if the window updated, false if has been destroyed.
	 */
	static int tick() {
		if (null == GraphicsManager.window) {
			return Launcher.STATUS_ERROR;
		}
		if (GraphicsManager.shutdownFlag.get()) {
			GraphicsManager.terminate();
			return Launcher.STATUS_OK;
		}

		if (GraphicsManager.window.windowShouldClose()) {
			GraphicsManager.window.destroy();
			GraphicsManager.window = null;
			return Launcher.STATUS_REQUEST_REMOVAL;
		}

		GraphicsManager.window.pollEvents();

		if (GraphicsManager.updateTimer
			.getTime() >= GraphicsManager.nextUpdateTime) {
			final float elapsedTime =
				GraphicsManager.updateTimer.getElapsedTime();

			GraphicsManager.window.getMouseInput().input();
			GraphicsManager.cameraManager.updateCamera(elapsedTime);

			GuiInstance guiInstance = GraphicsManager.scene.getGuiInstance();
			if (guiInstance != null) {
				guiInstance.handleGuiInput(GraphicsManager.scene,
					GraphicsManager.window);
			}

			GraphicsManager.update((long) elapsedTime);

			if (refreshRequested.compareAndSet(true, false)) {
				GraphicsManager.render.setupData(GraphicsManager.scene);
			}

			// Update the next time we should update models
			GraphicsManager.nextUpdateTime =
				GraphicsManager.updateTimer.getLastLoopTime()
					+ GraphicsManager.UPDATE_TIME;
		}

		if (GraphicsManager.renderTimer
			.getTime() >= GraphicsManager.nextRenderTime) {
			final float elapsedTime =
				GraphicsManager.renderTimer.getElapsedTime();

			GraphicsManager.render(elapsedTime);
			// Update the next time we should render a frame
			GraphicsManager.nextRenderTime =
				GraphicsManager.renderTimer.getLastLoopTime()
					+ GraphicsManager.FRAME_TIME;
		}

		return Launcher.STATUS_OK;
	}

	/**
	 * Set the GUI instance to use.
	 * 
	 * @param gui The user interface.
	 */
	public static void setGUI(GuiInstance gui) {
		scene.setGuiInstance(gui);
	}

	/**
	 * Make game updates.
	 *
	 * @param diffTimeMillis Time since the last update in milliseconds.
	 */
	private static void update(long diffTimeMillis) {
		GraphicsManager.animationData1.nextFrame();
		if (diffTimeMillis % 2 == 0) {
			GraphicsManager.animationData2.nextFrame();
		}

		GraphicsManager.rotation += 1.5;
		if (GraphicsManager.rotation > 360) {
			GraphicsManager.rotation = 0;
		}
		GraphicsManager.cubeEntity1.setRotation(1, 1, 1,
			(float) Math.toRadians(GraphicsManager.rotation));
		GraphicsManager.cubeEntity1.updateModelMatrix();

		GraphicsManager.cubeEntity2.setRotation(1, 1, 1,
			(float) Math.toRadians(360 - GraphicsManager.rotation));
		GraphicsManager.cubeEntity2.updateModelMatrix();

		SceneLights sceneLights = GraphicsManager.scene.getSceneLights();
		DirectionalLight dirLight = sceneLights.getDirLight();
		double angRad = Math.toRadians(GraphicsManager.lightAngle);
		dirLight.getDirection().z = (float) Math.sin(angRad);
		dirLight.getDirection().y = (float) Math.cos(angRad);
	}

	/**
	 * Private constructor so this class is not initialized.
	 */
	private GraphicsManager() {}
}
