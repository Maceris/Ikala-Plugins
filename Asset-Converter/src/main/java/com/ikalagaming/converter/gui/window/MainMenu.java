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
        GraphicsManager.getRenderInstance().initializeModel(ballModel);

        Material material = ballModel.getMeshDataList().get(0).getMaterial();

        Objects.requireNonNull(material);
        material.setTexture(null);
        material.setNormalMap(null);
        material.getBaseColor().set(0.75f, 0.0f, 0.0f, 1.0f);
        scene.getMaterialCache().setDirty(true);
        scene.addModel(ballModel);

        final String ballNameFormatString = "ball_%s_%d";
        float zPos = 0;

        for (int i = 0; i <= 10; ++i) {
            String name = String.format(ballNameFormatString, "anisotropic", i);
            Entity ball = new Entity(name, ballModel);
            ball.setScale(0.003f);
            ball.setPosition(i, 0, zPos);
            ball.updateModelMatrix();

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(0.0f, 0.90f, 0.60f, 1.0f);
            customMaterial.setAnisotropic(0.1f * i);
            customMaterial.setRoughness(0.40f);
            scene.getMaterialCache().addMaterial(customMaterial);

            scene.addEntity(ball);
            ball.setMaterialOverride(customMaterial, 0);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            String name = String.format(ballNameFormatString, "clearcoat", i);
            Entity ball = new Entity(name, ballModel);
            ball.setScale(0.003f);
            ball.setPosition(i, 0, zPos);
            ball.updateModelMatrix();

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(0.35f, 0.75f, 0.95f, 1.0f);
            customMaterial.setClearcoat(0.1f * i);
            scene.getMaterialCache().addMaterial(customMaterial);

            scene.addEntity(ball);
            ball.setMaterialOverride(customMaterial, 0);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            String name = String.format(ballNameFormatString, "clearcoatGloss", i);
            Entity ball = new Entity(name, ballModel);
            ball.setScale(0.003f);
            ball.setPosition(i, 0, zPos);
            ball.updateModelMatrix();

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(0.35f, 0.75f, 0.95f, 1.0f);
            customMaterial.setClearcoatGloss(0.1f * i);
            customMaterial.setRoughness(0.40f);
            scene.getMaterialCache().addMaterial(customMaterial);

            scene.addEntity(ball);
            ball.setMaterialOverride(customMaterial, 0);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            String name = String.format(ballNameFormatString, "metallic", i);
            Entity ball = new Entity(name, ballModel);
            ball.setScale(0.003f);
            ball.setPosition(i, 0, zPos);
            ball.updateModelMatrix();

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(1.0f, 0.95f, 0.f, 1.0f);
            customMaterial.setMetallic(0.1f * i);
            scene.getMaterialCache().addMaterial(customMaterial);

            scene.addEntity(ball);
            ball.setMaterialOverride(customMaterial, 0);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            String name = String.format(ballNameFormatString, "roughness", i);
            Entity ball = new Entity(name, ballModel);
            ball.setScale(0.003f);
            ball.setPosition(i, 0, zPos);
            ball.updateModelMatrix();

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(0.10f, 0.10f, 0.95f, 1.0f);
            customMaterial.setRoughness(0.1f * i);
            scene.getMaterialCache().addMaterial(customMaterial);

            scene.addEntity(ball);
            ball.setMaterialOverride(customMaterial, 0);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            String name = String.format(ballNameFormatString, "sheen", i);
            Entity ball = new Entity(name, ballModel);
            ball.setScale(0.003f);
            ball.setPosition(i, 0, zPos);
            ball.updateModelMatrix();

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(0.75f, 0.65f, 0.50f, 1.0f);
            customMaterial.setSheen(0.1f * i);
            customMaterial.setRoughness(1.0f);
            scene.getMaterialCache().addMaterial(customMaterial);

            scene.addEntity(ball);
            ball.setMaterialOverride(customMaterial, 0);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            String name = String.format(ballNameFormatString, "sheenTint", i);
            Entity ball = new Entity(name, ballModel);
            ball.setScale(0.003f);
            ball.setPosition(i, 0, zPos);
            ball.updateModelMatrix();

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(0.75f, 0.65f, 0.50f, 1.0f);
            customMaterial.setSheenTint(0.1f * i);
            customMaterial.setRoughness(1.0f);
            scene.getMaterialCache().addMaterial(customMaterial);

            scene.addEntity(ball);
            ball.setMaterialOverride(customMaterial, 0);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            String name = String.format(ballNameFormatString, "specular", i);
            Entity ball = new Entity(name, ballModel);
            ball.setScale(0.003f);
            ball.setPosition(i, 0, zPos);
            ball.updateModelMatrix();

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(0.95f, 0.20f, 0.20f, 1.0f);
            customMaterial.setSpecular(0.1f * i);
            customMaterial.setRoughness(0.40f);
            scene.getMaterialCache().addMaterial(customMaterial);

            scene.addEntity(ball);
            ball.setMaterialOverride(customMaterial, 0);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            String name = String.format(ballNameFormatString, "specularTint", i);
            Entity ball = new Entity(name, ballModel);
            ball.setScale(0.003f);
            ball.setPosition(i, 0, zPos);
            ball.updateModelMatrix();

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(0.95f, 0.20f, 0.20f, 1.0f);
            customMaterial.setSpecularTint(0.1f * i);
            customMaterial.setRoughness(0.40f);
            scene.getMaterialCache().addMaterial(customMaterial);

            scene.addEntity(ball);
            ball.setMaterialOverride(customMaterial, 0);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            String name = String.format(ballNameFormatString, "subsurface", i);
            Entity ball = new Entity(name, ballModel);
            ball.setScale(0.003f);
            ball.setPosition(i, 0, zPos);
            ball.updateModelMatrix();

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(1.0f, 1.0f, 1.0f, 1.0f);
            customMaterial.setSubsurface(0.1f * i);
            customMaterial.setRoughness(0.40f);
            scene.getMaterialCache().addMaterial(customMaterial);

            scene.addEntity(ball);
            ball.setMaterialOverride(customMaterial, 0);
        }

        scene.getSceneLights()
                .setDirLight(
                        new DirectionalLight(
                                new Vector3f(1.0f, 1.0f, 1.0f),
                                new Vector3f(0.247f, -0.848f, 0.785f),
                                4f));

        var pipeline =
                RenderConfig.builder().withAnimation().withScene().withSkybox().withGui().build();
        GraphicsManager.swapPipeline(pipeline);
        scene.getCamera().setPosition(5.12f, 6.75f, 10.42f);
        scene.getCamera().setRotation(0.94f, 6.28f);

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
