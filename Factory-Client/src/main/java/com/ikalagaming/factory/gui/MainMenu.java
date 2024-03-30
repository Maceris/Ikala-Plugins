package com.ikalagaming.factory.gui;

import static com.ikalagaming.factory.gui.DefaultComponents.DEBUG_TOOLBAR;
import static com.ikalagaming.factory.gui.DefaultComponents.MAIN_MENU;

import com.ikalagaming.factory.FactoryClientPlugin;
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
public class MainMenu extends GuiComponent {
    private final GuiManager guiManager;
    private final String textSinglePlayer;
    private final String textMultiplayer;
    private final String textOptions;
    private final ImVec2 windowSize;
    private final int windowFlags;
    private final ImBoolean windowOpen;

    public MainMenu(@NonNull GuiManager guiManager) {
        this.guiManager = guiManager;
        windowSize = new ImVec2();
        windowOpen = new ImBoolean(true);
        textSinglePlayer =
                SafeResourceLoader.getString(
                        "MENU_TEXT_SINGLE_PLAYER", FactoryClientPlugin.getResourceBundle());
        textMultiplayer =
                SafeResourceLoader.getString(
                        "MENU_TEXT_MULTI_PLAYER", FactoryClientPlugin.getResourceBundle());
        textOptions =
                SafeResourceLoader.getString(
                        "MENU_TEXT_OPTIONS", FactoryClientPlugin.getResourceBundle());
        windowFlags =
                ImGuiWindowFlags.NoScrollbar
                        | ImGuiWindowFlags.NoScrollWithMouse
                        | ImGuiWindowFlags.NoResize
                        | ImGuiWindowFlags.NoTitleBar
                        | ImGuiWindowFlags.NoDecoration;
    }

    @Override
    public void drawGui() {
        int offset = guiManager.isEnabled(DEBUG_TOOLBAR.getName()) ? 20 : 0;
        var window = GraphicsManager.getWindow();
        windowSize.x = window.getWidth();
        windowSize.y = window.getHeight() - offset;
        SizeConstants sizes = guiManager.getSizes();

        ImGui.setNextWindowPos(0, offset, ImGuiCond.Always);
        ImGui.setNextWindowSize(windowSize.x, windowSize.y, ImGuiCond.Always);
        ImGui.begin("Main Menu", windowOpen, windowFlags);

        float centerX = windowSize.x / 2;
        float centerY = windowSize.y / 2;

        ImGui.setCursorPosX(centerX - sizes.buttonWidth() / 2f);
        ImGui.setCursorPosY(centerY - sizes.buttonHeight() - sizes.padding());
        if (ImGui.button(textSinglePlayer, sizes.buttonWidth(), sizes.buttonHeight())) {
            guiManager.disable(MAIN_MENU.getName());
        }
        ImGui.setCursorPosX(centerX - sizes.buttonWidth() / 2f);
        ImGui.setCursorPosY(centerY);
        if (ImGui.button(textMultiplayer, sizes.buttonWidth(), sizes.buttonHeight())) {}
        ImGui.setCursorPosX(centerX - sizes.buttonWidth() / 2f);
        ImGui.setCursorPosY(centerY + sizes.buttonHeight() + sizes.padding());
        if (ImGui.button(textOptions, sizes.buttonWidth(), sizes.buttonHeight())) {}
        ImGui.end();
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        return false;
    }
}
