package com.ikalagaming.factory.gui.window;

import static com.ikalagaming.factory.gui.DefaultWindows.MAIN_MENU;
import static com.ikalagaming.factory.gui.DefaultWindows.SINGLE_PLAYER;

import com.ikalagaming.factory.FactoryClientPlugin;
import com.ikalagaming.factory.gui.GuiManager;
import com.ikalagaming.factory.gui.component.Button;
import com.ikalagaming.factory.gui.component.GuiWindow;
import com.ikalagaming.factory.gui.component.ScrollBox;
import com.ikalagaming.factory.gui.component.menu.SaveEntry;
import com.ikalagaming.factory.gui.component.util.Alignment;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.util.SafeResourceLoader;

import imgui.flag.ImGuiWindowFlags;
import lombok.NonNull;

/** The screen for selecting a single player game to play. */
public class SinglePlayer extends GuiWindow {
    private final GuiManager guiManager;
    private final Button back;
    private final Button newGame;
    private final Button play;
    private final ScrollBox saves;

    public SinglePlayer(@NonNull GuiManager guiManager) {
        super(
                SINGLE_PLAYER.getName(),
                ImGuiWindowFlags.NoScrollbar
                        | ImGuiWindowFlags.NoScrollWithMouse
                        | ImGuiWindowFlags.NoResize
                        | ImGuiWindowFlags.NoTitleBar
                        | ImGuiWindowFlags.NoDecoration);
        this.guiManager = guiManager;
        setScale(1.0f, 0.98f);
        setDisplacement(0.0f, 0.02f);

        var textSinglePlayer =
                SafeResourceLoader.getString(
                        "MENU_COMMON_BACK", FactoryClientPlugin.getResourceBundle());
        back = new Button(textSinglePlayer);
        back.setAlignment(Alignment.SOUTH_WEST);
        back.setDisplacement(0.05f, 0.01f);
        back.setScale(0.10f, 0.10f);

        var textMultiplayer =
                SafeResourceLoader.getString(
                        "MENU_SINGLE_PLAYER_NEW_GAME", FactoryClientPlugin.getResourceBundle());
        newGame = new Button(textMultiplayer);
        newGame.setAlignment(Alignment.SOUTH);
        newGame.setDisplacement(0.0f, 0.01f);
        newGame.setScale(0.10f, 0.10f);

        var textOptions =
                SafeResourceLoader.getString(
                        "MENU_SINGLE_PLAYER_PLAY", FactoryClientPlugin.getResourceBundle());
        play = new Button(textOptions);
        play.setAlignment(Alignment.SOUTH_EAST);
        play.setDisplacement(0.05f, 0.01f);
        play.setScale(0.10f, 0.10f);

        saves = new ScrollBox("Saves");
        saves.setAlignment(Alignment.NORTH);
        saves.setDisplacement(0.0f, 0.10f);
        saves.setScale(0.80f, 0.70f);

        addChild(back);
        addChild(newGame);
        addChild(play);
        addChild(saves);
        populateSaves();
    }

    private void populateSaves() {
        for (int i = 1; i <= 15; ++i) {
            addSaveEntry("Test Save " + i);
        }
    }

    private void addSaveEntry(final @NonNull String saveName) {
        var entry = new SaveEntry(saveName);
        entry.setAlignment(Alignment.NORTH_WEST);
        entry.setScale(0.99f, 0.10f);
        entry.setDisplacement(0.00f, 0.10f * saves.getChildCount());
        saves.addChild(entry);
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        if (back.checkResult()) {
            guiManager.hide(SINGLE_PLAYER.getName());
            guiManager.show(MAIN_MENU.getName());
            return true;
        }
        if (newGame.checkResult()) {
            // TODO(ches) New Game menu screen FACT-15
        }
        if (play.checkResult()) {
            // TODO(ches) Play game FACT-16
            guiManager.hide(SINGLE_PLAYER.getName());
        }
        return false;
    }
}
