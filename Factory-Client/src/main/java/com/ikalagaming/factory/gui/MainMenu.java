package com.ikalagaming.factory.gui;

import static com.ikalagaming.factory.gui.DefaultComponents.DEBUG_TOOLBAR;
import static com.ikalagaming.factory.gui.DefaultComponents.MAIN_MENU;

import com.ikalagaming.factory.FactoryClientPlugin;
import com.ikalagaming.factory.gui.component.Button;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.util.SafeResourceLoader;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import lombok.NonNull;

/** The main menu we start up the game showing. */
public class MainMenu extends GuiWindow {
    private final GuiManager guiManager;
    private final Button singlePlayer;
    private final Button multiPlayer;
    private final Button options;
    private final ImVec2 windowSize;
    private final int windowFlags;
    private final ImBoolean windowOpen;

    public MainMenu(@NonNull GuiManager guiManager) {
        this.guiManager = guiManager;
        windowSize = new ImVec2();
        windowOpen = new ImBoolean(true);

        var textSinglePlayer =
                SafeResourceLoader.getString(
                        "MENU_TEXT_SINGLE_PLAYER", FactoryClientPlugin.getResourceBundle());
        singlePlayer = new Button(textSinglePlayer);
        var textMultiplayer =
                SafeResourceLoader.getString(
                        "MENU_TEXT_MULTI_PLAYER", FactoryClientPlugin.getResourceBundle());
        multiPlayer = new Button(textMultiplayer);
        var textOptions =
                SafeResourceLoader.getString(
                        "MENU_TEXT_OPTIONS", FactoryClientPlugin.getResourceBundle());
        options = new Button(textOptions);

        windowFlags =
                ImGuiWindowFlags.NoScrollbar
                        | ImGuiWindowFlags.NoScrollWithMouse
                        | ImGuiWindowFlags.NoResize
                        | ImGuiWindowFlags.NoTitleBar
                        | ImGuiWindowFlags.NoDecoration;
    }

    @Override
    public void drawGui() {
        final int offset = guiManager.isEnabled(DEBUG_TOOLBAR.getName()) ? 20 : 0;
        var window = GraphicsManager.getWindow();
        windowSize.x = window.getWidth();
        windowSize.y = window.getHeight() - offset;
        final SizeConstants sizes = guiManager.getCurrentSizes();

        ImGui.setNextWindowPos(0, offset, ImGuiCond.Always);
        ImGui.setNextWindowSize(windowSize.x, windowSize.y, ImGuiCond.Always);
        ImGui.begin("Main Menu", windowOpen, windowFlags);

        final float centerX = windowSize.x / 2;
        final float centerY = windowSize.y / 2;

        ImGui.setCursorPosX(centerX - sizes.buttonWidth() / 2f);
        ImGui.setCursorPosY(centerY - sizes.buttonHeight() - sizes.padding());
        singlePlayer.draw(sizes);

        ImGui.setCursorPosX(centerX - sizes.buttonWidth() / 2f);
        ImGui.setCursorPosY(centerY);
        multiPlayer.draw(sizes);

        ImGui.setCursorPosX(centerX - sizes.buttonWidth() / 2f);
        ImGui.setCursorPosY(centerY + sizes.buttonHeight() + sizes.padding());
        options.draw(sizes);

        ImGui.end();
    }

    @Override
    public void handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        if (singlePlayer.checkResult()) {
            guiManager.disable(MAIN_MENU.getName());
        }
        if (multiPlayer.checkResult()) {
            // TODO(ches) Joining remote servers FACT-13
        }
        if (options.checkResult()) {
            // TODO(ches) Options menu FACT-14
        }
    }
}
