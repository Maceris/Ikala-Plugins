package com.ikalagaming.factory.gui.window;

import static com.ikalagaming.factory.gui.DefaultWindows.MAIN_MENU;

import com.ikalagaming.factory.FactoryClientPlugin;
import com.ikalagaming.factory.gui.GuiManager;
import com.ikalagaming.factory.gui.component.Button;
import com.ikalagaming.factory.gui.component.GuiWindow;
import com.ikalagaming.factory.gui.component.util.Alignment;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.util.SafeResourceLoader;

import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import lombok.NonNull;

/** The main menu we start up the game showing. */
public class MainMenu extends GuiWindow {
    private final GuiManager guiManager;
    private final Button singlePlayer;
    private final Button multiPlayer;
    private final Button options;

    public MainMenu(@NonNull GuiManager guiManager) {
        super(
                MAIN_MENU.getName(),
                ImGuiWindowFlags.NoScrollbar
                        | ImGuiWindowFlags.NoScrollWithMouse
                        | ImGuiWindowFlags.NoResize
                        | ImGuiWindowFlags.NoTitleBar
                        | ImGuiWindowFlags.NoDecoration,
                new ImBoolean(true));
        this.guiManager = guiManager;
        setScale(1.0f, 0.98f);
        setDisplacement(0.0f, 0.02f);

        var textSinglePlayer =
                SafeResourceLoader.getString(
                        "MENU_TEXT_SINGLE_PLAYER", FactoryClientPlugin.getResourceBundle());
        singlePlayer = new Button(textSinglePlayer);
        singlePlayer.setAlignment(Alignment.CENTER);
        singlePlayer.setDisplacement(0.0f, -0.15f);
        singlePlayer.setScale(0.10f, 0.10f);

        var textMultiplayer =
                SafeResourceLoader.getString(
                        "MENU_TEXT_MULTI_PLAYER", FactoryClientPlugin.getResourceBundle());
        multiPlayer = new Button(textMultiplayer);
        multiPlayer.setAlignment(Alignment.CENTER);
        multiPlayer.setScale(0.10f, 0.10f);

        var textOptions =
                SafeResourceLoader.getString(
                        "MENU_TEXT_OPTIONS", FactoryClientPlugin.getResourceBundle());
        options = new Button(textOptions);
        options.setAlignment(Alignment.CENTER);
        options.setDisplacement(0.0f, 0.15f);
        options.setScale(0.10f, 0.10f);

        addChild(singlePlayer);
        addChild(multiPlayer);
        addChild(options);
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        if (singlePlayer.checkResult()) {
            guiManager.hide(MAIN_MENU.getName());
            return true;
        }
        if (multiPlayer.checkResult()) {
            // TODO(ches) Joining remote servers FACT-13
        }
        if (options.checkResult()) {
            // TODO(ches) Options menu FACT-14
        }
        return false;
    }
}
