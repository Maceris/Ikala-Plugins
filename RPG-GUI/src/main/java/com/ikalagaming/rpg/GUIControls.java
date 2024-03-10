package com.ikalagaming.rpg;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GuiInstance;
import com.ikalagaming.graphics.MouseInput;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.Texture;
import com.ikalagaming.graphics.render.Render;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.launcher.events.Shutdown;
import com.ikalagaming.rpg.item.ItemPlugin;
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

/** A GUI for manipulating lights */
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

    /** The inventory window. */
    PlayerInventory windowInventory;

    private ItemCatalogWindow windowCatalog;

    /** The scene controls. */
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
        wireframe = new ImBoolean(false);
        selectedFilter = new ImInt(Render.configuration.getSelectedFilter());

        showDebugWindow = new ImBoolean(true);
        showDemo = new ImBoolean(false);
        showIkScriptDebugger = new ImBoolean(false);
        showImageWindow = new ImBoolean(false);
        showInventory = new ImBoolean(false);
        showItemCatalog = new ImBoolean(false);
        showLuaConsole = new ImBoolean(false);
        showSceneControls = new ImBoolean(false);

        windowInventory = new PlayerInventory();

        windowCatalog = new ItemCatalogWindow();
        windowCatalog.setup(scene);
        windowCatalog.setInventory(windowInventory.getInventory());

        windowDebug = new DebugWindow();
        windowDebug.setup(scene);

        windowSceneControls = new SceneControls();

        windowImages = new ImageWindow();
        windowImages.setup(scene);

        ikScriptDebugger = new IkScriptDebugger();
        ikScriptDebugger.setup(scene);

        windowLuaConsole = new LuaConsole();
        windowLuaConsole.setup(scene);
    }

    /** Clean up resources. */
    void cleanup() {
        if (itemTexture != null) {
            itemTexture.cleanup();
        }
    }

    @Override
    public void drawGui() {
        oneTimeSetups();

        ImGui.newFrame();

        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("Windows")) {
                ImGui.checkbox("Debug", showDebugWindow);
                ImGui.checkbox("Demo", showDemo);
                ImGui.checkbox("Inventory", showInventory);
                ImGui.checkbox("Item Catalog", showItemCatalog);
                ImGui.checkbox("Scene Controls", showSceneControls);
                ImGui.checkbox("Image Catalog", showImageWindow);
                ImGui.checkbox("Ikala Script Debugger", showIkScriptDebugger);
                ImGui.checkbox("Lua Console", showLuaConsole);
                ImGui.checkbox("Dialogue", Dialogue.windowOpen);
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Render Controls")) {
                ImGui.checkbox("Wireframe", wireframe);
                ImGui.listBox("Filter", selectedFilter, Render.configuration.getFilterNames());
                ImGui.endMenu();
            }
            ImGui.pushStyleColor(ImGuiCol.Text, ImColor.floatToColor(1f, 0.1f, 0.1f));
            if (ImGui.menuItem("Quit Game")) {
                new Shutdown().fire();
            }
            ImGui.popStyleColor();

            ImGui.endMainMenuBar();
        }

        showWindows();

        ImGui.endFrame();
        ImGui.render();
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        ImGuiIO imGuiIO = ImGui.getIO();
        MouseInput mouseInput = window.getMouseInput();
        Vector2f mousePos = mouseInput.getCurrentPos();
        imGuiIO.setMousePos(mousePos.x, mousePos.y);
        imGuiIO.setMouseDown(0, mouseInput.isLeftButtonPressed());
        imGuiIO.setMouseDown(1, mouseInput.isRightButtonPressed());
        Render.configuration.setWireframe(wireframe.get());
        Render.configuration.setSelectedFilter(selectedFilter.get());

        boolean consumed = imGuiIO.getWantCaptureMouse() || imGuiIO.getWantCaptureKeyboard();
        if (consumed) {
            windowInventory.handleGuiInput(scene, window);
            windowCatalog.handleGuiInput(scene, window);
            windowSceneControls.handleGuiInput(scene, window);
            windowImages.handleGuiInput(scene, window);
        }
        return consumed;
    }

    /**
     * Set things up that have to happen on the main thread, but only happen once, like loading
     * textures.
     */
    private void oneTimeSetups() {
        if (itemTexture == null) {
            itemTexture =
                    new Texture(
                            PluginFolder.getResource(
                                            ItemPlugin.PLUGIN_NAME,
                                            ResourceType.DATA,
                                            "item-spritesheet.png")
                                    .getAbsolutePath());
            GraphicsManager.getRender().recalculateMaterials(GraphicsManager.getScene());
            windowInventory.setItemTexture(itemTexture);
        }
        if (!dialogueSetUp) {
            dialogueSetUp = true;
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

    /** Render whichever windows are applicable. */
    private void showWindows() {
        if (showDemo.get()) {
            ImGui.showDemoWindow();
        }
        if (showDebugWindow.get()) {
            windowDebug.draw();
        }

        if (showInventory.get()) {
            windowInventory.draw();
        }
        if (showItemCatalog.get()) {
            windowCatalog.draw();
        }
        if (showSceneControls.get()) {
            windowSceneControls.draw();
        }
        if (showIkScriptDebugger.get()) {
            ikScriptDebugger.draw();
        }
        if (showImageWindow.get()) {
            windowImages.draw();
        }
        if (showLuaConsole.get()) {
            windowLuaConsole.draw();
        }
        if (Dialogue.windowOpen.get()) {
            Dialogue.renderWindow();
        }
    }
}
