package com.ikalagaming.rpg;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.RenderConfig;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.component.MainToolbar;
import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.IkIO;
import com.ikalagaming.graphics.frontend.gui.data.IkInt;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.util.Color;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.launcher.events.Shutdown;
import com.ikalagaming.rpg.item.ItemPlugin;
import com.ikalagaming.rpg.windows.*;

import lombok.NonNull;

/** A GUI for manipulating lights */
public class GUIControls extends MainToolbar {

    private IkInt selectedFilter;
    private IkBoolean wireframe;

    private IkBoolean showDebugWindow;
    private IkBoolean showDemo;
    private IkBoolean showInventory;
    private IkBoolean showItemCatalog;
    private IkBoolean showSceneControls;
    private IkBoolean showIkScriptDebugger;
    private IkBoolean showImageWindow;
    private IkBoolean showLuaConsole;

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
        wireframe = new IkBoolean(false);
        selectedFilter = new IkInt(0);

        showDebugWindow = new IkBoolean(true);
        showDemo = new IkBoolean(false);
        showIkScriptDebugger = new IkBoolean(false);
        showImageWindow = new IkBoolean(false);
        showInventory = new IkBoolean(false);
        showItemCatalog = new IkBoolean(false);
        showLuaConsole = new IkBoolean(false);
        showSceneControls = new IkBoolean(false);

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
            GraphicsManager.getDeletionQueue().add(itemTexture);
            itemTexture = null;
        }
    }

    /**
     * Draws the menu bar and any open windows. The window manager handles starting and rendering
     * the frame.
     *
     * @param width The width of the window in pixels.
     * @param height The height of the window in pixels.
     */
    @Override
    public void draw(final int width, final int height) {
        oneTimeSetups();

        if (IkGui.beginMainMenuBar()) {
            if (IkGui.beginMenu("Windows")) {
                IkGui.checkbox("Debug", showDebugWindow);
                IkGui.checkbox("Demo", showDemo);
                IkGui.checkbox("Inventory", showInventory);
                IkGui.checkbox("Item Catalog", showItemCatalog);
                IkGui.checkbox("Scene Controls", showSceneControls);
                IkGui.checkbox("Image Catalog", showImageWindow);
                IkGui.checkbox("Ikala Script Debugger", showIkScriptDebugger);
                IkGui.checkbox("Lua Console", showLuaConsole);
                IkGui.checkbox("Dialogue", Dialogue.windowOpen);
                IkGui.endMenu();
            }

            if (IkGui.beginMenu("Render Controls")) {
                IkGui.checkbox("Wireframe", wireframe);
                String[] filters = {"default"};
                IkGui.listBox("Filter", selectedFilter, filters);
                IkGui.endMenu();
            }
            IkGui.pushStyleColor(ColorType.TEXT, Color.rgb(1f, 0.1f, 0.1f));
            if (IkGui.menuItem("Quit Game")) {
                new Shutdown().fire();
            }
            IkGui.popStyleColor();

            IkGui.endMainMenuBar();
        }

        showWindows();
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        final int currentConfig = GraphicsManager.getPipelineConfig();
        final int desiredConfig =
                wireframe.get()
                        ? RenderConfig.builder(currentConfig).withWireframe().build()
                        : RenderConfig.builder(currentConfig).withoutWireframe().build();
        if (desiredConfig != currentConfig) {
            GraphicsManager.swapPipeline(desiredConfig);
        }
        // TODO(ches) apply selectedFilter once there are multiple filters

        final IkIO io = IkGui.getIO();
        if (io.wantCaptureMouse || io.wantCaptureKeyboard) {
            windowInventory.handleGuiInput(scene, window);
            windowCatalog.handleGuiInput(scene, window);
            windowSceneControls.handleGuiInput(scene, window);
            windowImages.handleGuiInput(scene, window);
            return true;
        }
        return false;
    }

    /**
     * Update any internal values as required, which happens at a different frequency from
     * rendering.
     *
     * @param scene The scene we are rendering.
     * @param window The window we are using.
     */
    @Override
    public void updateValues(@NonNull Scene scene, @NonNull Window window) {}

    /**
     * Set things up that have to happen on the main thread, but only happen once, like loading
     * textures.
     */
    private void oneTimeSetups() {
        if (itemTexture == null) {
            itemTexture =
                    GraphicsManager.getRenderInstance()
                            .getTextureLoader()
                            .load(
                                    PluginFolder.getResource(
                                                    ItemPlugin.PLUGIN_NAME,
                                                    ResourceType.DATA,
                                                    "item-spritesheet.png")
                                            .getAbsolutePath());

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
            IkGui.showDemoWindow();
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
