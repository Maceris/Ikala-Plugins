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
import com.ikalagaming.launcher.events.Shutdown;
import com.ikalagaming.rpg.windows.DebugWindow;
import com.ikalagaming.rpg.windows.IkScriptDebugger;
import com.ikalagaming.rpg.windows.ImageWindow;
import com.ikalagaming.rpg.windows.ItemCatalogWindow;
import com.ikalagaming.rpg.windows.LuaConsole;
import com.ikalagaming.rpg.windows.PlayerInventory;
import com.ikalagaming.rpg.windows.SceneControls;

import imgui.ImColor;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import lombok.NonNull;
import org.joml.Vector2f;

/**
 * A GUI for manipulating lights
 */
public class GUIControls implements GuiInstance {

	private ImInt selectedFilter;
	private ImBoolean wireframe;

	private ImBoolean showDebugWindow;
	private ImBoolean showDemo;
	private ImBoolean showInventory;
	private ImBoolean showItemCatalog;
	private ImBoolean showSceneControls;
	private ImBoolean showIkScriptDebugger;
	private ImBoolean showImageWindow;
	private ImBoolean showLuaConsole;

	private Texture itemTexture;

	private boolean dialogueSetUp = false;

	/**
	 * The inventory window.
	 */
	PlayerInventory windowInventory;
	private ItemCatalogWindow windowCatalog;
	/**
	 * The scene controls.
	 */
	SceneControls windowSceneControls;
	private ImageWindow windowImages;
	private IkScriptDebugger ikScriptDebugger;
	private LuaConsole windowLuaConsole;
	private DebugWindow windowDebug;

	/**
	 * Set up the light controls.
	 *
	 * @param scene The scene.
	 */
	public GUIControls(@NonNull Scene scene) {
		this.wireframe = new ImBoolean(false);
		this.selectedFilter =
			new ImInt(Render.configuration.getSelectedFilter());

		this.showDebugWindow = new ImBoolean(true);
		this.showDemo = new ImBoolean(false);
		this.showIkScriptDebugger = new ImBoolean(false);
		this.showImageWindow = new ImBoolean(false);
		this.showInventory = new ImBoolean(false);
		this.showItemCatalog = new ImBoolean(false);
		this.showLuaConsole = new ImBoolean(false);
		this.showSceneControls = new ImBoolean(false);

		this.windowInventory = new PlayerInventory();

		this.windowCatalog = new ItemCatalogWindow();
		this.windowCatalog.setup(scene);
		this.windowCatalog.setInventory(this.windowInventory.getInventory());

		this.windowDebug = new DebugWindow();
		this.windowDebug.setup(scene);

		this.windowSceneControls = new SceneControls();

		this.windowImages = new ImageWindow();
		this.windowImages.setup(scene);

		this.ikScriptDebugger = new IkScriptDebugger();
		this.ikScriptDebugger.setup(scene);

		this.windowLuaConsole = new LuaConsole();
		this.windowLuaConsole.setup(scene);
	}

	/**
	 * Clean up resources.
	 */
	void cleanup() {
		if (this.itemTexture != null) {
			this.itemTexture.cleanup();
		}
	}

	@Override
	public void drawGui() {
		this.oneTimeSetups();

		ImGui.newFrame();

		if (ImGui.beginMainMenuBar()) {
			if (ImGui.beginMenu("Windows")) {
				ImGui.checkbox("Debug", this.showDebugWindow);
				ImGui.checkbox("Demo", this.showDemo);
				ImGui.checkbox("Inventory", this.showInventory);
				ImGui.checkbox("Item Catalog", this.showItemCatalog);
				ImGui.checkbox("Scene Controls", this.showSceneControls);
				ImGui.checkbox("Image Catalog", this.showImageWindow);
				ImGui.checkbox("Ikala Script Debugger",
					this.showIkScriptDebugger);
				ImGui.checkbox("Lua Console", this.showLuaConsole);
				ImGui.checkbox("Dialogue", Dialogue.windowOpen);
				ImGui.endMenu();
			}

			if (ImGui.beginMenu("Render Controls")) {
				ImGui.checkbox("Wireframe", this.wireframe);
				ImGui.listBox("Filter", this.selectedFilter,
					Render.configuration.getFilterNames());
				ImGui.endMenu();
			}
			ImGui.pushStyleColor(ImGuiCol.Text,
				ImColor.floatToColor(1f, 0.1f, 0.1f));
			if (ImGui.menuItem("Quit Game")) {
				new Shutdown().fire();
			}
			ImGui.popStyleColor();

			ImGui.endMainMenuBar();
		}

		this.showWindows();

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
		Render.configuration.setSelectedFilter(this.selectedFilter.get());

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
		if (!this.dialogueSetUp) {
			this.dialogueSetUp = true;
			Dialogue.leftChat("Hi there!");
			Dialogue.rightChat("Hello!");
			Dialogue.centerText("You leave.");
			Dialogue.text(
				"This is a sample window. It can contain a lot of text in it, hypothetically. "
					+ "We probably want some kind of text wrapping, but also "
					+ "I would imagine a scroll bar or something.");
			Dialogue.divider();
			Dialogue.text("We also might have options to select.");
			Dialogue.option("Multiple choice options");
			Dialogue.option("Or maybe just single choice");
			Dialogue.option("Close window??");
			for (int i = 0; i < 20; ++i) {
				Dialogue.leftChat("Stop repeating yourself!");
				Dialogue.rightChat(String.format("I am not x%d!", i));
			}
		}
	}

	/**
	 * Render whichever windows are applicable.
	 */
	private void showWindows() {
		if (this.showDemo.get()) {
			ImGui.showDemoWindow();
		}
		if (this.showDebugWindow.get()) {
			this.windowDebug.draw();
		}

		if (this.showInventory.get()) {
			this.windowInventory.draw();
		}
		if (this.showItemCatalog.get()) {
			this.windowCatalog.draw();
		}
		if (this.showSceneControls.get()) {
			this.windowSceneControls.draw();
		}
		if (this.showIkScriptDebugger.get()) {
			this.ikScriptDebugger.draw();
		}
		if (this.showImageWindow.get()) {
			this.windowImages.draw();
		}
		if (this.showLuaConsole.get()) {
			this.windowLuaConsole.draw();
		}
		if (Dialogue.windowOpen.get()) {
			Dialogue.renderWindow();
		}
	}

}
