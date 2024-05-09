package com.ikalagaming.factory.gui.window;

import static com.ikalagaming.factory.gui.DefaultWindows.MAIN_MENU;
import static com.ikalagaming.factory.gui.DefaultWindows.SINGLE_PLAYER;

import com.ikalagaming.factory.FactoryClientPlugin;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.gui.WindowManager;
import com.ikalagaming.graphics.frontend.gui.component.Button;
import com.ikalagaming.graphics.frontend.gui.component.GuiWindow;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.util.SafeResourceLoader;

import imgui.flag.ImGuiWindowFlags;
import lombok.NonNull;

/** The main menu we start up the game showing. */
public class MainMenu extends GuiWindow {
    private final WindowManager windowManager;
    private final Button singlePlayer;
    private final Button multiPlayer;
    private final Button options;

    public MainMenu(@NonNull WindowManager windowManager) {
        super(
                MAIN_MENU.getName(),
                ImGuiWindowFlags.NoScrollbar
                        | ImGuiWindowFlags.NoScrollWithMouse
                        | ImGuiWindowFlags.NoResize
                        | ImGuiWindowFlags.NoTitleBar
                        | ImGuiWindowFlags.NoDecoration);
        this.windowManager = windowManager;
        setScale(1.0f, 0.98f);
        setDisplacement(0.0f, 0.02f);

        var textSinglePlayer =
                SafeResourceLoader.getString(
                        "MENU_MAIN_MENU_SINGLE_PLAYER", FactoryClientPlugin.getResourceBundle());
        singlePlayer = new Button(textSinglePlayer);
        singlePlayer.setAlignment(Alignment.CENTER);
        singlePlayer.setDisplacement(0.0f, -0.15f);
        singlePlayer.setScale(0.10f, 0.10f);

        var textMultiplayer =
                SafeResourceLoader.getString(
                        "MENU_MAIN_MENU_MULTI_PLAYER", FactoryClientPlugin.getResourceBundle());
        multiPlayer = new Button(textMultiplayer);
        multiPlayer.setAlignment(Alignment.CENTER);
        multiPlayer.setScale(0.10f, 0.10f);

        var textOptions =
                SafeResourceLoader.getString(
                        "MENU_MAIN_MENU_OPTIONS", FactoryClientPlugin.getResourceBundle());
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
            windowManager.hide(MAIN_MENU.getName());
            windowManager.show(SINGLE_PLAYER.getName());
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
