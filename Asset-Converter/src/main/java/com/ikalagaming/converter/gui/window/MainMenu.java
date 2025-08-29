package com.ikalagaming.converter.gui.window;

import static com.ikalagaming.converter.gui.DefaultWindows.MAIN_MENU;

import com.ikalagaming.converter.ConverterPlugin;
import com.ikalagaming.converter.ModelConverter;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.Material;
import com.ikalagaming.graphics.frontend.RenderConfig;
import com.ikalagaming.graphics.frontend.gui.WindowManager;
import com.ikalagaming.graphics.frontend.gui.component.Button;
import com.ikalagaming.graphics.frontend.gui.component.GuiWindow;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.scene.lights.PointLight;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.util.SafeResourceLoader;

import imgui.flag.ImGuiWindowFlags;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3f;

import java.util.Objects;

/** The main menu we start up the game showing. */
@Slf4j
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
        Scene scene = GraphicsManager.getScene();

        Model ballModel =
                ModelConverter.loadModel(
                        new ModelConverter.ModelLoadRequest(
                                "shader_ball",
                                ConverterPlugin.PLUGIN_NAME,
                                "models/shader_ball.obj",
                                scene.getMaterialCache(),
                                false));

        Material material = ballModel.getMeshDataList().get(0).getMaterial();

        Objects.requireNonNull(material);
        material.setTexture(null);
        material.setNormalMap(null);
        material.getBaseColor().set(0.75f, 0.0f, 0.0f, 1.0f);

        scene.getMaterialCache().setDirty(true);

        GraphicsManager.getRenderInstance().initializeModel(ballModel);

        {
            scene.addModel(ballModel);
            Entity ball = new Entity("ball", ballModel);
            ball.setScale(0.002f);
            ball.updateModelMatrix();
            scene.addEntity(ball);
        }

        {
            Entity ball2 = new Entity("ball2", ballModel);
            ball2.setScale(0.002f);
            ball2.setPosition(1, 0, 0);
            ball2.updateModelMatrix();

            Material secondMaterial = new Material();
            secondMaterial.getBaseColor().set(0.0f, 0.75f, 0.0f, 1.0f);
            scene.getMaterialCache().addMaterial(secondMaterial);
            for (int i = 0; i < ballModel.getMeshDataList().size(); ++i) {
                ball2.setMaterialOverride(secondMaterial, i);
            }
            scene.addEntity(ball2);
        }

        scene.getSceneLights()
                .setDirLight(
                        new DirectionalLight(
                                new Vector3f(1.0f, 1.0f, 1.0f),
                                new Vector3f(1.0f, 1.0f, 1.0f),
                                1.6e9f));
        scene.getSceneLights()
                .getPointLights()
                .add(
                        new PointLight(
                                new Vector3f(1.0f, 0.0f, 0.0f),
                                new Vector3f(2.0f, 1.1f, 0.0f),
                                1.0f));

        var pipeline =
                RenderConfig.builder().withAnimation().withScene().withSkybox().withGui().build();
        GraphicsManager.swapPipeline(pipeline);
        scene.getCamera().setPosition(1.86f, 2.43f, 2.33f);
        scene.getCamera().setRotation(0.64f, 5.62f);

        var texturePath =
                PluginFolder.getResource(
                                ConverterPlugin.PLUGIN_NAME,
                                PluginFolder.ResourceType.DATA,
                                "textures/skybox.png")
                        .getAbsolutePath();
        scene.setSkyboxTexture(
                GraphicsManager.getRenderInstance().getTextureLoader().load(texturePath));
    }
}
