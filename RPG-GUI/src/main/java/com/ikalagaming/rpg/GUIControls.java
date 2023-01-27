package com.ikalagaming.rpg;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GuiInstance;
import com.ikalagaming.graphics.MouseInput;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.Texture;
import com.ikalagaming.graphics.render.Render;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.item.ItemPlugin;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.rpg.windows.ImageWindow;
import com.ikalagaming.rpg.windows.ItemCatalogWindow;
import com.ikalagaming.rpg.windows.LuaConsole;
import com.ikalagaming.rpg.windows.PlayerInventory;
import com.ikalagaming.rpg.windows.SceneControls;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import lombok.NonNull;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * A GUI for manipulating lights
 */
public class GUIControls implements GuiInstance {

	private ImBoolean wireframe;

	private ImBoolean showWindowDemo;
	private ImBoolean showWindowInventory;
	private ImBoolean showWindowItemCatalog;
	private ImBoolean showWindowSceneControls;
	private ImBoolean showImageWindow;
	private ImBoolean showLuaConsole;

	private Texture itemTexture;

	private PlayerInventory windowInventory;
	private ItemCatalogWindow windowCatalog;
	/**
	 * The scene controls.
	 */
	SceneControls windowSceneControls;
	private ImageWindow windowImages;
	private LuaConsole windowLuaConsole;

	/**
	 * Set up the light controls.
	 *
	 * @param scene The scene.
	 */
	public GUIControls(@NonNull Scene scene) {
		this.wireframe = new ImBoolean(false);

		this.showWindowInventory = new ImBoolean(false);
		this.showWindowItemCatalog = new ImBoolean(false);
		this.showWindowDemo = new ImBoolean(false);
		this.showWindowSceneControls = new ImBoolean(false);
		this.showImageWindow = new ImBoolean(false);
		this.showLuaConsole = new ImBoolean(false);

		this.windowInventory = new PlayerInventory();
		this.windowInventory.setup(scene);

		this.windowCatalog = new ItemCatalogWindow();
		this.windowCatalog.setup(scene);
		this.windowCatalog.setInventory(this.windowInventory.getInventory());

		this.windowSceneControls = new SceneControls();
		this.windowSceneControls.setup(scene);

		this.windowImages = new ImageWindow();
		this.windowImages.setup(scene);

		this.windowLuaConsole = new LuaConsole();
		this.windowLuaConsole.setup(scene);
	}

	/**
	 * Clean up resources.
	 */
	void cleanup() {
		if (this.itemTexture == null) {
			this.itemTexture.cleanup();
		}
	}

	private void drawDebugMenu() {
		ImGui.setNextWindowPos(10, 10, ImGuiCond.Once);
		ImGui.setNextWindowSize(450, 400, ImGuiCond.Once);
		ImGui.begin("Debug");

		ImGui.text(String.format("FPS: %d", GraphicsManager.getLastFPS()));

		Vector3f position =
			GraphicsManager.getCameraManager().getCamera().getPosition();
		ImGui.text(String.format("Camera position: (%f, %f, %f)", position.x,
			position.y, position.z));
		Vector2f rotation =
			GraphicsManager.getCameraManager().getCamera().getRotation();
		ImGui.text(
			String.format("Camera rotation: (%f, %f)", rotation.x, rotation.y));
		MouseInput input = GraphicsManager.getWindow().getMouseInput();
		ImGui.text(String.format("Mouse position: (%f, %f)",
			input.getCurrentPos().x, input.getCurrentPos().y));
		ImGui.text(String.format("Displace vector: (%f, %f)",
			input.getDisplVec().x, input.getDisplVec().y));

		ImGui.checkbox("Wireframe", this.wireframe);
		ImGui.checkbox("Show Demo", this.showWindowDemo);
		ImGui.checkbox("Show Inventory", this.showWindowInventory);
		ImGui.checkbox("Show Item Catalog", this.showWindowItemCatalog);
		ImGui.checkbox("Show Scene Controls", this.showWindowSceneControls);
		ImGui.checkbox("Show Image Catalog", this.showImageWindow);
		ImGui.checkbox("Show Lua Console", this.showLuaConsole);

		ImGui.end();
	}

	@Override
	public void drawGui() {
		this.oneTimeSetups();

		ImGui.newFrame();

		if (this.showWindowDemo.get()) {
			ImGui.showDemoWindow();
		}

		this.drawDebugMenu();

		if (this.showWindowInventory.get()) {
			this.windowInventory.draw();
		}
		if (this.showWindowItemCatalog.get()) {
			this.windowCatalog.draw();
		}
		if (this.showWindowSceneControls.get()) {
			this.windowSceneControls.draw();
		}
		if (this.showImageWindow.get()) {
			this.windowImages.draw();
		}
		if (this.showLuaConsole.get()) {
			this.windowLuaConsole.draw();
		}

		ImGui.endFrame();
		ImGui.render();
	}

	@Override
	public boolean handleGuiInput(@NonNull Scene scene,
		@NonNull Window window) {
		ImGuiIO imGuiIO = ImGui.getIO();
		MouseInput mouseInput = window.getMouseInput();
		Vector2f mousePos = mouseInput.getCurrentPos();
		imGuiIO.setMousePos(mousePos.x, mousePos.y);
		imGuiIO.setMouseDown(0, mouseInput.isLeftButtonPressed());
		imGuiIO.setMouseDown(1, mouseInput.isRightButtonPressed());
		Render.configuration.setWireframe(this.wireframe.get());

		boolean consumed =
			imGuiIO.getWantCaptureMouse() || imGuiIO.getWantCaptureKeyboard();
		if (consumed) {
			this.windowInventory.handleGuiInput(scene, window);
			this.windowCatalog.handleGuiInput(scene, window);
			this.windowSceneControls.handleGuiInput(scene, window);
			this.windowImages.handleGuiInput(scene, window);
		}
		return consumed;
	}

	/**
	 * Set things up that have to happen on the main thread, but only happen
	 * once, like loading textures.
	 */
	private void oneTimeSetups() {
		if (this.itemTexture == null) {
			this.itemTexture = GraphicsManager.getScene().getTextureCache()
				.createTexture(PluginFolder.getResource(ItemPlugin.PLUGIN_NAME,
					ResourceType.DATA, "item-spritesheet.png")
					.getAbsolutePath());
			GraphicsManager.getRender()
				.recalculateMaterials(GraphicsManager.getScene());
			this.windowInventory.setItemTexture(this.itemTexture);
		}
	}

}
