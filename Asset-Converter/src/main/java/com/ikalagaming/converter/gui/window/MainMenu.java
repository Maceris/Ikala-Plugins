package com.ikalagaming.converter.gui.window;

import com.ikalagaming.converter.ConverterPlugin;
import com.ikalagaming.converter.ModelConverter;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.InstanceHandle;
import com.ikalagaming.graphics.Instances;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.scene.lights.PointLight;
import com.ikalagaming.graphics.ui.spec.Observable;
import com.ikalagaming.graphics.ui.spec.SpecBindings;
import com.ikalagaming.graphics.ui.spec.SpecInstance;
import com.ikalagaming.graphics.ui.spec.UiSpec;
import com.ikalagaming.launcher.PluginFolder;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * The main menu we start up showing. Its layout lives in the spec {@code ui/main-menu.yml} in the
 * plugin's data folder, which the editor reloads when it changes; this class supplies what the spec
 * names: the button handlers, the sample count, and the plugin's text.
 */
@Slf4j
public class MainMenu {
    /** The ID of the main menu surface, set in the spec. */
    public static final String SURFACE_ID = "converter/main-menu";

    /** Where the spec is, within the plugin's data folder. */
    public static final String SPEC = "ui/main-menu.yml";

    /** The UI the menu is shown through. */
    private final UI ui;

    /** Shows the asset inspector. */
    private final Runnable openInspector;

    /** How many sample containers have been written, shown under the buttons. */
    private final Observable<Integer> sampleCount;

    /**
     * Create the main menu.
     *
     * @param ui The converter plugin's UI.
     * @param openInspector Shows the asset inspector.
     * @param sampleCount How many sample containers have been written.
     */
    public MainMenu(
            @NonNull UI ui,
            @NonNull Runnable openInspector,
            @NonNull Observable<Integer> sampleCount) {
        this.ui = ui;
        this.openInspector = openInspector;
        this.sampleCount = sampleCount;
    }

    /**
     * Load the menu spec and show it.
     *
     * @return The open menu.
     * @throws com.ikalagaming.graphics.ui.spec.SpecException If the spec is broken.
     */
    public SpecInstance open() {
        UiSpec spec = ui.loadSpec(SPEC);
        return ui.open(
                spec,
                new SpecBindings()
                        .bundle(ConverterPlugin.getResourceBundle())
                        .handler("start-sphere-demo", this::startSphereDemo)
                        .handler(
                                "open-model-loader",
                                () -> {
                                    // TODO(ches) model loading UI
                                })
                        .handler("open-inspector", this::startInspector)
                        .value("samples.count", sampleCount));
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
        GraphicsManager.forPlugin(ConverterPlugin.PLUGIN_NAME).meshes().register(ballModel);

        Material material = ballModel.getMeshDataList().get(0).getMaterial();

        Objects.requireNonNull(material);
        material.setTexture(null);
        material.setNormalMap(null);
        material.getBaseColor().set(0.75f, 0.0f, 0.0f, 1.0f);
        scene.getMaterialCache().setDirty(true);
        scene.addModel(ballModel);

        final Instances instances =
                GraphicsManager.forPlugin(ConverterPlugin.PLUGIN_NAME).instances();
        float zPos = 0;

        for (int i = 0; i <= 10; ++i) {
            InstanceHandle ball =
                    instances.place(ballModel, new Vector3d(i, 0, zPos), new Quaternionf(), 0.003f);

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(0.0f, 0.90f, 0.60f, 1.0f);
            customMaterial.setAnisotropic(0.1f * i);
            customMaterial.setRoughness(0.40f);
            scene.getMaterialCache().addMaterial(customMaterial);

            instances.setMaterial(ball, 0, customMaterial);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            InstanceHandle ball =
                    instances.place(ballModel, new Vector3d(i, 0, zPos), new Quaternionf(), 0.003f);

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(0.35f, 0.75f, 0.95f, 1.0f);
            customMaterial.setClearcoat(0.1f * i);
            scene.getMaterialCache().addMaterial(customMaterial);

            instances.setMaterial(ball, 0, customMaterial);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            InstanceHandle ball =
                    instances.place(ballModel, new Vector3d(i, 0, zPos), new Quaternionf(), 0.003f);

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(0.35f, 0.75f, 0.95f, 1.0f);
            customMaterial.setClearcoatGloss(0.1f * i);
            customMaterial.setRoughness(0.40f);
            scene.getMaterialCache().addMaterial(customMaterial);

            instances.setMaterial(ball, 0, customMaterial);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            InstanceHandle ball =
                    instances.place(ballModel, new Vector3d(i, 0, zPos), new Quaternionf(), 0.003f);

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(1.0f, 0.95f, 0.f, 1.0f);
            customMaterial.setMetallic(0.1f * i);
            scene.getMaterialCache().addMaterial(customMaterial);

            instances.setMaterial(ball, 0, customMaterial);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            InstanceHandle ball =
                    instances.place(ballModel, new Vector3d(i, 0, zPos), new Quaternionf(), 0.003f);

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(0.10f, 0.10f, 0.95f, 1.0f);
            customMaterial.setRoughness(0.1f * i);
            scene.getMaterialCache().addMaterial(customMaterial);

            instances.setMaterial(ball, 0, customMaterial);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            InstanceHandle ball =
                    instances.place(ballModel, new Vector3d(i, 0, zPos), new Quaternionf(), 0.003f);

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(0.75f, 0.65f, 0.50f, 1.0f);
            customMaterial.setSheen(0.1f * i);
            customMaterial.setRoughness(1.0f);
            scene.getMaterialCache().addMaterial(customMaterial);

            instances.setMaterial(ball, 0, customMaterial);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            InstanceHandle ball =
                    instances.place(ballModel, new Vector3d(i, 0, zPos), new Quaternionf(), 0.003f);

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(0.75f, 0.65f, 0.50f, 1.0f);
            customMaterial.setSheenTint(0.1f * i);
            customMaterial.setRoughness(1.0f);
            scene.getMaterialCache().addMaterial(customMaterial);

            instances.setMaterial(ball, 0, customMaterial);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            InstanceHandle ball =
                    instances.place(ballModel, new Vector3d(i, 0, zPos), new Quaternionf(), 0.003f);

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(0.95f, 0.20f, 0.20f, 1.0f);
            customMaterial.setSpecular(0.1f * i);
            customMaterial.setRoughness(0.40f);
            scene.getMaterialCache().addMaterial(customMaterial);

            instances.setMaterial(ball, 0, customMaterial);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            InstanceHandle ball =
                    instances.place(ballModel, new Vector3d(i, 0, zPos), new Quaternionf(), 0.003f);

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(0.95f, 0.20f, 0.20f, 1.0f);
            customMaterial.setSpecularTint(0.1f * i);
            customMaterial.setRoughness(0.40f);
            scene.getMaterialCache().addMaterial(customMaterial);

            instances.setMaterial(ball, 0, customMaterial);
        }
        zPos += 1;

        for (int i = 0; i <= 10; ++i) {
            InstanceHandle ball =
                    instances.place(ballModel, new Vector3d(i, 0, zPos), new Quaternionf(), 0.003f);

            Material customMaterial = new Material();
            customMaterial.getBaseColor().set(1.0f, 1.0f, 1.0f, 1.0f);
            customMaterial.setRoughness(0.40f);
            scene.getMaterialCache().addMaterial(customMaterial);

            instances.setMaterial(ball, 0, customMaterial);
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
