package com.ikalagaming.converter.gui.window;

import static com.ikalagaming.converter.gui.DefaultWindows.MAIN_MENU;

import com.ikalagaming.converter.ConverterPlugin;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.RenderConfig;
import com.ikalagaming.graphics.frontend.gui.WindowManager;
import com.ikalagaming.graphics.frontend.gui.component.Button;
import com.ikalagaming.graphics.frontend.gui.component.GuiWindow;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.ModelLoader;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.PointLight;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.util.SafeResourceLoader;

import imgui.flag.ImGuiWindowFlags;
import lombok.NonNull;
import org.joml.Vector3f;

/** The main menu we start up the game showing. */
public class MainMenu extends GuiWindow {
    private final WindowManager windowManager;
    private final Button sphereDemoButton;
    private final Button modelLoaderButton;

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

        var textSphereDemo =
                SafeResourceLoader.getString(
                        "MENU_MAIN_SPHERE_DEMO", ConverterPlugin.getResourceBundle());
        sphereDemoButton = new Button(textSphereDemo);
        sphereDemoButton.setAlignment(Alignment.CENTER);
        sphereDemoButton.setDisplacement(0.0f, -0.15f);
        sphereDemoButton.setScale(0.10f, 0.10f);

        var textModelLoader =
                SafeResourceLoader.getString(
                        "MENU_MAIN_MODEL_LOADER", ConverterPlugin.getResourceBundle());
        modelLoaderButton = new Button(textModelLoader);
        modelLoaderButton.setAlignment(Alignment.CENTER);
        modelLoaderButton.setScale(0.10f, 0.10f);

        addChild(sphereDemoButton);
        addChild(modelLoaderButton);
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        if (sphereDemoButton.checkResult()) {
            windowManager.hide(MAIN_MENU.getName());
            loadSphereDemo();
            return true;
        }
        if (modelLoaderButton.checkResult()) {
            // TODO(ches) model loading UI
        }
        return false;
    }

    private void loadSphereDemo() {
        Model cubeModel =
                ModelLoader.loadModel(
                        new ModelLoader.ModelLoadRequest(
                                "shader_ball",
                                ConverterPlugin.PLUGIN_NAME,
                                "models/shader_ball.obj",
                                GraphicsManager.getScene().getMaterialCache(),
                                false));
        GraphicsManager.getScene().addModel(cubeModel);
        Entity ball = new Entity("ball", cubeModel.getId());
        GraphicsManager.getScene().addEntity(ball);
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
        var pipeline =
                RenderConfig.builder().withAnimation().withScene().withSkybox().withGui().build();
        GraphicsManager.swapPipeline(pipeline);
        GraphicsManager.getScene().getCamera().setPosition(1.86f, 2.43f, 2.33f);
        GraphicsManager.getScene().getCamera().setRotation(0.64f, 5.62f);

        var texturePath =
                PluginFolder.getResource(
                                ConverterPlugin.PLUGIN_NAME,
                                PluginFolder.ResourceType.DATA,
                                "textures/skybox.png")
                        .getAbsolutePath();
        GraphicsManager.getScene()
                .setSkyboxTexture(
                        GraphicsManager.getRenderInstance().getTextureLoader().load(texturePath));
    }
}
