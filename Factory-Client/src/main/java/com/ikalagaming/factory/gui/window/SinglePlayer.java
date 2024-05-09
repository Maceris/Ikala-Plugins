package com.ikalagaming.factory.gui.window;

import static com.ikalagaming.factory.gui.DefaultWindows.MAIN_MENU;
import static com.ikalagaming.factory.gui.DefaultWindows.SINGLE_PLAYER;

import com.ikalagaming.factory.FactoryClientPlugin;
import com.ikalagaming.factory.FactoryServerPlugin;
import com.ikalagaming.factory.gui.component.menu.SaveEntry;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.gui.WindowManager;
import com.ikalagaming.graphics.frontend.gui.component.Button;
import com.ikalagaming.graphics.frontend.gui.component.GuiWindow;
import com.ikalagaming.graphics.frontend.gui.component.ScrollBox;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.render.Render;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.ModelLoader;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.SkyBox;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.PointLight;
import com.ikalagaming.util.SafeResourceLoader;

import imgui.flag.ImGuiWindowFlags;
import lombok.NonNull;
import org.joml.Vector3f;

/** The screen for selecting a single player game to play. */
public class SinglePlayer extends GuiWindow {
    private final WindowManager windowManager;
    private final Button back;
    private final Button newGame;
    private final Button play;
    private final ScrollBox saves;

    public SinglePlayer(@NonNull WindowManager windowManager) {
        super(
                SINGLE_PLAYER.getName(),
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
            windowManager.hide(SINGLE_PLAYER.getName());
            windowManager.show(MAIN_MENU.getName());
            return true;
        }
        if (newGame.checkResult()) {
            // TODO(ches) New Game menu screen FACT-15
        }
        if (play.checkResult()) {
            // TODO(ches) Play game FACT-16
            windowManager.hide(SINGLE_PLAYER.getName());
            startGame();
        }
        return false;
    }

    void startGame() {
        // TODO(ches) actually start the server properly, and connect to it
        FactoryServerPlugin.getServer().start();

        Model cubeModel =
                ModelLoader.loadModel(
                        new ModelLoader.ModelLoadRequest(
                                "cube",
                                FactoryClientPlugin.PLUGIN_NAME,
                                "models/cube.obj",
                                GraphicsManager.getScene().getMaterialCache(),
                                false));
        GraphicsManager.getScene().addModel(cubeModel);
        Entity cube = new Entity("TheCube", cubeModel.getId());
        GraphicsManager.getScene().addEntity(cube);
        GraphicsManager.getScene()
                .getSceneLights()
                .setAmbientLight(new AmbientLight(new Vector3f(1.0f, 1.0f, 1.0f), 0.6f));
        GraphicsManager.getScene()
                .getSceneLights()
                .getPointLights()
                .add(
                        new PointLight(
                                new Vector3f(1.0f, 0.0f, 0.0f),
                                new Vector3f(2.0f, 1.1f, 0.0f),
                                1.0f));
        GraphicsManager.refreshRenderData();
        Render.configuration.setRenderingScene(true);
        GraphicsManager.getScene().getCamera().setPosition(1.86f, 2.43f, 2.33f);
        GraphicsManager.getScene().getCamera().setRotation(0.64f, 5.62f);
        GraphicsManager.getScene()
                .setSkyBox(
                        new SkyBox(
                                "models/skybox/skybox.obj",
                                GraphicsManager.getScene().getMaterialCache()));
    }
}
