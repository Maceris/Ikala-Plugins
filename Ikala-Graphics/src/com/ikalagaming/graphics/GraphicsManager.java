package com.ikalagaming.graphics;

import com.ikalagaming.graphics.events.WindowCreated;
import com.ikalagaming.graphics.exceptions.MeshException;
import com.ikalagaming.graphics.exceptions.TextureException;
import com.ikalagaming.graphics.exceptions.WindowCreationException;
import com.ikalagaming.graphics.graph.DirectionalLight;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.Mesh;
import com.ikalagaming.graphics.graph.PointLight;
import com.ikalagaming.graphics.graph.SpotLight;
import com.ikalagaming.graphics.graph.Texture;
import com.ikalagaming.launcher.Launcher;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides utilities for handling graphics.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class GraphicsManager {

	private static AtomicBoolean initialized = new AtomicBoolean(false);

	private static List<SceneItem> items;

	private static Renderer renderer;
	private static CameraManager cameraManager;
	private static Timer timer;

	/**
	 * The target frames per second that we want to hit.
	 */
	public static final int TARGET_FPS = 60;

	/**
	 * The (fractional) number of seconds between frame renders at the target
	 * FPS.
	 */
	private static final float FRAME_TIME = 1f / GraphicsManager.TARGET_FPS;

	/**
	 * The time when the next render call should happen to maintain the target
	 * FPS.
	 */
	private static double nextRenderTime;

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

	private static Vector3f ambientLight;
	private static PointLight[] pointLightList;
	private static SpotLight[] spotLightList;
	private static DirectionalLight directionalLight;
	/**
	 * The angle the directional light is at.
	 */
	private static float lightAngle;

	/**
	 * The angle of the spotlight.
	 */
	private static float spotAngle = 0;
	/**
	 * The increment at which the spotlight angle changes.
	 */
	private static float spotIncrement = 1;

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

		GraphicsManager.window = new Window("Ikala Gaming", 640, 480, false);
		GraphicsManager.window.init();

		GraphicsManager.log.debug(SafeResourceLoader.getString("WINDOW_CREATED",
			GraphicsPlugin.getResourceBundle()));
		new WindowCreated(GraphicsManager.window.getWindowHandle()).fire();

		GraphicsManager.renderer = new Renderer();
		GraphicsManager.renderer.init();

		GraphicsManager.log.debug(SafeResourceLoader
			.getString("RENDERER_CREATED", GraphicsPlugin.getResourceBundle()));

		GraphicsManager.cameraManager =
			new CameraManager(GraphicsManager.window);

		Texture texture;
		try {
			texture = new Texture("textures/grassblock.png");
		}
		catch (TextureException e) {
			throw new WindowCreationException(e);
		}
		Mesh mesh;
		try {
			mesh = OBJLoader.loadMesh("models/cube.obj");
		}
		catch (MeshException e) {
			throw new WindowCreationException(e);
		}

		float reflectance = 1f;
		Material material = new Material(texture, reflectance);
		mesh.setMaterial(material);

		SceneItem item = new SceneItem(mesh);
		item.setScale(0.5f);
		item.setPosition(0, 0, -2);

		GraphicsManager.items = new ArrayList<>();
		GraphicsManager.items.add(item);

		GraphicsManager.ambientLight = new Vector3f(0.3f, 0.3f, 0.3f);

		// Point light
		Vector3f lightColour = new Vector3f(1, 1, 1);
		Vector3f lightPosition = new Vector3f(0, 0, 1);
		float lightIntensity = 1.0f;
		PointLight pointLight =
			new PointLight(lightColour, lightPosition, lightIntensity);
		PointLight.Attenuation att =
			new PointLight.Attenuation(0.0f, 0.0f, 1.0f);
		pointLight.setAttenuation(att);
		GraphicsManager.pointLightList = new PointLight[] {pointLight};

		// Directional light
		lightPosition = new Vector3f(-1, 0, 0);
		lightColour = new Vector3f(1, 1, 1);
		GraphicsManager.directionalLight =
			new DirectionalLight(lightColour, lightPosition, lightIntensity);

		// Spot light
		lightPosition = new Vector3f(0, 0.0f, 10f);
		pointLight = new PointLight(new Vector3f(1, 1, 1), lightPosition,
			lightIntensity);
		att = new PointLight.Attenuation(0.0f, 0.0f, 0.02f);
		pointLight.setAttenuation(att);
		Vector3f coneDir = new Vector3f(0, 0, -1);
		float cutoff = (float) Math.cos(Math.toRadians(140));
		SpotLight spotLight = new SpotLight(pointLight, coneDir, cutoff);
		GraphicsManager.spotLightList =
			new SpotLight[] {spotLight, new SpotLight(spotLight)};

		GraphicsManager.timer = new Timer();
		GraphicsManager.timer.init();
		GraphicsManager.nextRenderTime = GraphicsManager.timer.getLastLoopTime()
			+ GraphicsManager.FRAME_TIME;
	}

	/**
	 * Render to the screen.
	 */
	private static void render() {
		// clear the framebuffer
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

		GraphicsManager.updateItems();
		GraphicsManager.updateDirectionalLight();

		GraphicsManager.renderer.render(GraphicsManager.window,
			GraphicsManager.cameraManager.getCamera(), GraphicsManager.items,
			GraphicsManager.ambientLight, GraphicsManager.pointLightList,
			GraphicsManager.spotLightList, GraphicsManager.directionalLight);

		GraphicsManager.window.update();

		// Update the next time we should render a frame
		GraphicsManager.nextRenderTime = GraphicsManager.timer.getLastLoopTime()
			+ GraphicsManager.FRAME_TIME;
	}

	/**
	 * Terminate GLFW and free the error callback. If any windows still remain,
	 * they are destroyed.
	 */
	public static void terminate() {
		GraphicsManager.renderer.cleanup();
		GraphicsManager.items.forEach(SceneItem::cleanup);

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

		// Poll for window events
		GLFW.glfwPollEvents();

		if (GraphicsManager.window.windowShouldClose()) {
			GraphicsManager.window.destroy();
			GraphicsManager.window = null;
			return Launcher.STATUS_REQUEST_REMOVAL;
		}

		final float elapsedTime = GraphicsManager.timer.getElapsedTime();

		GraphicsManager.cameraManager.processInput();

		GraphicsManager.cameraManager.updateCamera(elapsedTime);

		if (GraphicsManager.timer.getTime() >= GraphicsManager.nextRenderTime) {
			GraphicsManager.render();
		}

		return Launcher.STATUS_OK;
	}

	private static void updateDirectionalLight() {
		// Update spot light direction
		GraphicsManager.spotAngle += GraphicsManager.spotIncrement * 0.05f;
		if (GraphicsManager.spotAngle > 2) {
			GraphicsManager.spotIncrement = -1;
		}
		else if (GraphicsManager.spotAngle < -2) {
			GraphicsManager.spotIncrement = 1;
		}
		double spotAngleRad = Math.toRadians(GraphicsManager.spotAngle);
		Vector3f coneDir = GraphicsManager.spotLightList[0].getConeDirection();
		coneDir.y = (float) Math.sin(spotAngleRad);

		// Update directional light direction, intensity and color
		GraphicsManager.lightAngle += 1.1f;
		if (GraphicsManager.lightAngle > 90) {
			GraphicsManager.directionalLight.setIntensity(0);
			if (GraphicsManager.lightAngle >= 360) {
				GraphicsManager.lightAngle = -90;
			}
		}
		else if (GraphicsManager.lightAngle <= -80
			|| GraphicsManager.lightAngle >= 80) {
			float factor =
				1 - (Math.abs(GraphicsManager.lightAngle) - 80) / 10.0f;
			GraphicsManager.directionalLight.setIntensity(factor);
			GraphicsManager.directionalLight.getColor().y =
				Math.max(factor, 0.9f);
			GraphicsManager.directionalLight.getColor().z =
				Math.max(factor, 0.5f);
		}
		else {
			GraphicsManager.directionalLight.setIntensity(1);
			GraphicsManager.directionalLight.getColor().x = 1;
			GraphicsManager.directionalLight.getColor().y = 1;
			GraphicsManager.directionalLight.getColor().z = 1;
		}
		double angRad = Math.toRadians(GraphicsManager.lightAngle);
		GraphicsManager.directionalLight.getDirection().x =
			(float) Math.sin(angRad);
		GraphicsManager.directionalLight.getDirection().y =
			(float) Math.cos(angRad);
	}

	private static void updateItems() {
		for (SceneItem item : GraphicsManager.items) {
			// Update rotation angle
			float rotation = item.getRotation().x + 1.5f;
			if (rotation > 360) {
				rotation = 0;
			}
			item.setRotation(rotation, rotation, rotation);
		}
	}

	/**
	 * Private constructor so this class is not initialized.
	 */
	private GraphicsManager() {}
}
