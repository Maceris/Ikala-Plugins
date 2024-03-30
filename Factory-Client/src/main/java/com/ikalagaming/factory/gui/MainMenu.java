package com.ikalagaming.factory.gui;

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
        var window = GraphicsManager.getWindow();
        windowSize.x = window.getWidth();
        windowSize.y = window.getHeight();
        int offset = guiManager.isEnabled("Debug Toolbar") ? 20 : 0;
        ImGui.setNextWindowPos(0, offset, ImGuiCond.Always);
        ImGui.setNextWindowSize(windowSize.x, windowSize.y - offset, ImGuiCond.Always);
        ImGui.begin("Main Menu", windowOpen, windowFlags);

        if (ImGui.button(textSinglePlayer)) {}
        if (ImGui.button(textMultiplayer)) {}
        if (ImGui.button(textOptions)) {}
        ImGui.end();
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        return false;
    }
}
