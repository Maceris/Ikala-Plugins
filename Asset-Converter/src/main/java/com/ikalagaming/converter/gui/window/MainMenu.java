package com.ikalagaming.converter.gui.window;

import com.ikalagaming.converter.ConverterPlugin;
import com.ikalagaming.converter.ModelConverter;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.scene.lights.PointLight;
import com.ikalagaming.graphics.ui.Align;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Justify;
import com.ikalagaming.graphics.ui.Layer;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * The main menu we start up showing, built as retained UI: a full-screen surface with a centered
 * column of buttons that a mouse, keyboard or gamepad can use.
 */
@Slf4j
public class MainMenu {
    /** The ID of the main menu surface. */
    public static final String SURFACE_ID = "converter/main-menu";

    /** The width of the menu buttons, in UI units. */
    private static final float BUTTON_WIDTH = 240;

    /** The height of the menu buttons, in UI units. */
    private static final float BUTTON_HEIGHT = 40;

    /** The UI the menu is shown through. */
    private final UI ui;

    /** Shows the asset inspector. */
    private final Runnable openInspector;

    /**
     * Create the main menu.
     *
     * @param ui The converter plugin's UI.
     * @param openInspector Shows the asset inspector.
     */
    public MainMenu(@NonNull UI ui, @NonNull Runnable openInspector) {
        this.ui = ui;
        this.openInspector = openInspector;
    }

    /**
     * Build the menu surface, ready to show.
     *
     * @return The surface.
     */
    public Surface build() {
        var textSphereDemo =
                SafeResourceLoader.getString(
                        "MENU_MAIN_SPHERE_DEMO", ConverterPlugin.getResourceBundle());
        var textModelLoader =
                SafeResourceLoader.getString(
                        "MENU_MAIN_MODEL_LOADER", ConverterPlugin.getResourceBundle());
        var textInspector =
                SafeResourceLoader.getString(
                        "MENU_MAIN_ASSET_INSPECTOR", ConverterPlugin.getResourceBundle());

        Column buttons =
                new Column("buttons")
                        .gap(12)
                        .add(
                                new Button("sphere-demo", textSphereDemo)
                                        .width(Sizing.fixed(BUTTON_WIDTH))
                                        .height(Sizing.fixed(BUTTON_HEIGHT))
                                        .autofocus(true)
                                        .onClick(this::startSphereDemo),
                                new Button("model-loader", textModelLoader)
                                        .width(Sizing.fixed(BUTTON_WIDTH))
                                        .height(Sizing.fixed(BUTTON_HEIGHT))
                                        .onClick(
                                                () -> {
                                                    // TODO(ches) model loading UI
                                                }),
                                new Button("asset-inspector", textInspector)
                                        .width(Sizing.fixed(BUTTON_WIDTH))
                                        .height(Sizing.fixed(BUTTON_HEIGHT))
                                        .onClick(this::startInspector));
        Column content =
                new Column("content").justify(Justify.CENTER).align(Align.CENTER).add(buttons);

        return ui.surface(SURFACE_ID)
                .anchors(Anchors.fill())
                .layer(Layer.BACKGROUND)
                .content(content);
    }

    /** Hide the menu and show the asset inspector. */
    private void startInspector() {
        ui.setVisible(SURFACE_ID, false);
        openInspector.run();
    }

    /** Hide the menu and load the sphere demo. */
    private void startSphereDemo() {
        ui.setVisible(SURFACE_ID, false);
        loadSphereDemo();
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

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(1.0f, 1.0f, 1.0f, 1.0f);
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

        scene.getSceneLights()
                .getPointLights()
                .add(
                        new PointLight(
                                new Vector3f(1.0f, 0.1f, 0.1f), new Vector3d(8.5, 2.0, 7.5), 4f));
        scene.getSceneLights()
                .getPointLights()
                .add(
                        new PointLight(
                                new Vector3f(0.1f, 0.1f, 1.0f), new Vector3d(1.7, 2.0, 7.5), 4f));

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
                GraphicsManager.forPlugin(ConverterPlugin.PLUGIN_NAME)
                        .textures()
                        .load(texturePath));
    }
}
