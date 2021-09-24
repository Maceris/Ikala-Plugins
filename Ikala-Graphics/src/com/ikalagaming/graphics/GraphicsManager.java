package com.ikalagaming.graphics;

import com.ikalagaming.graphics.events.WindowCreated;
import com.ikalagaming.graphics.exceptions.TextureException;
import com.ikalagaming.graphics.exceptions.WindowCreationException;
import com.ikalagaming.launcher.Launcher;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
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

		// Create the Mesh
		float[] positions = new float[] {
			// V0
			-0.5f, 0.5f, 0.5f,
			// V1
			-0.5f, -0.5f, 0.5f,
			// V2
			0.5f, -0.5f, 0.5f,
			// V3
			0.5f, 0.5f, 0.5f,
			// V4
			-0.5f, 0.5f, -0.5f,
			// V5
			0.5f, 0.5f, -0.5f,
			// V6
			-0.5f, -0.5f, -0.5f,
			// V7
			0.5f, -0.5f, -0.5f,

			// For text coords in top face
			// V8: V4 repeated
			-0.5f, 0.5f, -0.5f,
			// V9: V5 repeated
			0.5f, 0.5f, -0.5f,
			// V10: V0 repeated
			-0.5f, 0.5f, 0.5f,
			// V11: V3 repeated
			0.5f, 0.5f, 0.5f,

			// For text coords in right face
			// V12: V3 repeated
			0.5f, 0.5f, 0.5f,
			// V13: V2 repeated
			0.5f, -0.5f, 0.5f,

			// For text coords in left face
			// V14: V0 repeated
			-0.5f, 0.5f, 0.5f,
			// V15: V1 repeated
			-0.5f, -0.5f, 0.5f,

			// For text coords in bottom face
			// V16: V6 repeated
			-0.5f, -0.5f, -0.5f,
			// V17: V7 repeated
			0.5f, -0.5f, -0.5f,
			// V18: V1 repeated
			-0.5f, -0.5f, 0.5f,
			// V19: V2 repeated
			0.5f, -0.5f, 0.5f,};
		float[] textCoords = new float[] {0.0f, 0.0f, //
			0.0f, 0.5f, //
			0.5f, 0.5f, //
			0.5f, 0.0f, //

			0.0f, 0.0f, //
			0.5f, 0.0f, //
			0.0f, 0.5f, //
			0.5f, 0.5f, //

			// For text coords in top face
			0.0f, 0.5f, //
			0.5f, 0.5f, //
			0.0f, 1.0f, //
			0.5f, 1.0f, //

			// For text coords in right face
			0.0f, 0.0f, //
			0.0f, 0.5f, //

			// For text coords in left face
			0.5f, 0.0f, //
			0.5f, 0.5f, //

			// For text coords in bottom face
			0.5f, 0.0f, //
			1.0f, 0.0f, //
			0.5f, 0.5f, //
			1.0f, 0.5f,//
		};
		int[] indices = new int[] {
			// Front face
			0, 1, 3, 3, 1, 2,
			// Top Face
			8, 10, 11, 9, 8, 11,
			// Right face
			12, 13, 7, 5, 12, 7,
			// Left face
			14, 15, 6, 4, 14, 6,
			// Bottom face
			16, 18, 19, 17, 16, 19,
			// Back face
			4, 6, 7, 5, 4, 7,};
		Texture texture;
		try {
			texture = new Texture("textures/grassblock.png");
		}
		catch (TextureException e) {
			GraphicsManager.log
				.error(SafeResourceLoader.getString("TEXTURE_ERROR_CREATION",
					GraphicsPlugin.getResourceBundle()), e);
			throw new WindowCreationException(e);
		}
		Mesh mesh = new Mesh(positions, textCoords, indices, texture);
		SceneItem item = new SceneItem(mesh);
		item.setPosition(0, 0, -2);

		GraphicsManager.items = new ArrayList<>();
		GraphicsManager.items.add(item);
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

		// clear the framebuffer
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

		GraphicsManager.updateItems();
		GraphicsManager.renderer.render(GraphicsManager.items);

		GraphicsManager.window.update();

		return Launcher.STATUS_OK;

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
