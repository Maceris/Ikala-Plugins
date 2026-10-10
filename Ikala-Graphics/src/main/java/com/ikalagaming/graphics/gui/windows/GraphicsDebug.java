package com.ikalagaming.graphics.gui.windows;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.component.Checkbox;
import com.ikalagaming.graphics.gui.component.GuiWindow;
import com.ikalagaming.graphics.gui.component.Slider;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Alignment;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.debug.DebugVisualizers;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.vulkan.InstanceRegistry;
import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.NonNull;
import org.joml.Vector3f;

public class GraphicsDebug extends GuiWindow {

    public static final String WINDOW_NAME = "Graphics Debug";

    private final Checkbox fogEnabled;
    private final Checkbox wireframeEnabled;

    private final Checkbox showPointLights;
    private final Checkbox showSpotLights;
    private final Checkbox showDirectionalLight;
    private final Checkbox showEntityBounds;
    private final Checkbox showShadowCascades;
    private final Checkbox showNormals;
    private final Checkbox showTangents;
    private final Slider normalLength;
    private final Checkbox freezeObserver;
    private final Checkbox showUiShowcase;
    private final Slider fogDensity;
    private final Slider directionalLightX;
    private final Slider directionalLightY;
    private final Slider directionalLightZ;
    private final Slider directionalLightIntensity;

    public GraphicsDebug() {
        super(WINDOW_NAME, WindowFlags.NONE);
        setScale(0.34f, 0.45f);
        setDisplacement(0.01f, 0.05f);
        setAlignment(Alignment.NORTH_EAST);

        fogEnabled = new Checkbox("Fog enabled", false);
        wireframeEnabled = new Checkbox("Wireframe enabled", false);
        showPointLights = new Checkbox("Point lights", false);
        showSpotLights = new Checkbox("Spot lights", false);
        showDirectionalLight = new Checkbox("Directional light", false);
        showEntityBounds = new Checkbox("Instance bounds", false);
        showShadowCascades = new Checkbox("Shadow cascades", false);
        showNormals = new Checkbox("Normals", false);
        showTangents = new Checkbox("Tangents", false);
        normalLength = new Slider("Normal length", 0.1f, 0.01f, 1f);
        freezeObserver = new Checkbox("Freeze observer", false);
        showUiShowcase = new Checkbox("UI showcase", false);
        fogDensity = new Slider("Fog Density", 0, 0, 1);
        directionalLightX = new Slider("Directional Light X", 0, -1, 1);
        directionalLightY = new Slider("Directional Light Y", 0, -1, 1);
        directionalLightZ = new Slider("Directional Light Z", 0, -1, 1);
        directionalLightIntensity = new Slider("Directional Light Intensity", 0, 0, 4f);

        addChild(fogEnabled);
        addChild(fogDensity);
        addChild(wireframeEnabled);
        addChild(showPointLights);
        addChild(showSpotLights);
        addChild(showDirectionalLight);
        addChild(showEntityBounds);
        addChild(showShadowCascades);
        addChild(showNormals);
        addChild(showTangents);
        addChild(normalLength);
        addChild(freezeObserver);
        addChild(showUiShowcase);
        addChild(directionalLightX);
        addChild(directionalLightY);
        addChild(directionalLightZ);
        addChild(directionalLightIntensity);
    }

    @Override
    public void draw(int width, int height) {
        IkGui.setNextWindowViewport(IkGui.getMainViewport().id);
        IkGui.setNextWindowPos(
                getActualDisplaceX() * width, getActualDisplaceY() * height, Condition.ONCE);
        IkGui.setNextWindowSize(
                getActualWidth() * width, getActualHeight() * height, Condition.ONCE);
        IkGui.begin(title, windowOpen, windowFlags);

        if (isVisible()) {
            recalculate();

            Scene scene = GraphicsManager.getScene();

            if (IkGui.collapsingHeader("Stats")) {
                IkGui.text(String.format("FPS: %d", GraphicsManager.getLastFPS()));
                IkGui.text(
                        String.format(
                                "Point lights: %,d",
                                scene.getSceneLights().getPointLights().size()));
                IkGui.text(
                        String.format(
                                "Spot lights: %,d", scene.getSceneLights().getSpotLights().size()));
                IkGui.text(
                        String.format(
                                "Materials loaded - %,d",
                                GraphicsManager.getScene().getMaterialCache().getMaterialCount()));

                long triangles = 0;
                int meshes = 0;
                int instances = 0;
                final VulkanInstance renderer = GraphicsManager.getRenderInstance();
                final InstanceRegistry registry =
                        renderer == null || renderer.getState().instances == null
                                ? null
                                : renderer.getState().instances.getRegistry();
                for (Model model : scene.getModelMap().values()) {
                    meshes += model.getMeshDataList().size();
                    int modelInstances = registry == null ? 0 : registry.countOf(model);
                    instances += modelInstances;
                    int meshTriangles = 0;
                    for (MeshData mesh : model.getMeshDataList()) {
                        meshTriangles += mesh.getIndices().length / 3;
                    }
                    triangles += (long) meshTriangles * modelInstances;
                }

                IkGui.text(String.format("Models loaded: %,d", scene.getModelMap().size()));
                IkGui.text(String.format("Meshes loaded: %,d", meshes));
                IkGui.text(String.format("Instances: %,d", instances));
                IkGui.text(String.format("Triangles: %,d", triangles));
            }

            if (IkGui.collapsingHeader("Render Config Info")) {
                int config = GraphicsManager.getPipelineConfig();
                final String flagString = "%12s - %s";
                IkGui.text(String.format(flagString, "Error", RenderConfig.hasError(config)));
                IkGui.text(
                        String.format(
                                flagString, "Animation", RenderConfig.hasAnimationStage(config)));
                IkGui.text(
                        String.format(flagString, "Shadow", RenderConfig.hasShadowStage(config)));
                IkGui.text(String.format(flagString, "Scene", RenderConfig.hasSceneStage(config)));
                IkGui.text(
                        String.format(flagString, "Skybox", RenderConfig.hasSkyboxStage(config)));
                IkGui.text(
                        String.format(flagString, "Filter", RenderConfig.hasFilterStage(config)));
                IkGui.text(String.format(flagString, "Gui", RenderConfig.hasGuiStage(config)));
                IkGui.text(
                        String.format(
                                flagString,
                                "Transparency",
                                RenderConfig.hasTransparencyPass(config)));
                IkGui.text(
                        String.format(
                                flagString, "Wireframe", RenderConfig.sceneIsWireframe(config)));
                IkGui.text(String.format(flagString, "Debug", RenderConfig.hasDebugStage(config)));
            }

            if (IkGui.collapsingHeader("Camera")) {
                var camera = GraphicsManager.getCameraManager().getCamera();
                IkGui.text(
                        String.format(
                                "Camera position: x:%.2f, y:%.2f, z:%.2f",
                                camera.getPosition().x(),
                                camera.getPosition().y(),
                                camera.getPosition().z()));

                IkGui.text(
                        String.format(
                                "Camera rotation: x:%.2f, y:%.2f",
                                camera.getRotation().x(), camera.getRotation().y()));
            }

            if (IkGui.collapsingHeader("Scene Controls")) {
                boolean renderingScene =
                        RenderConfig.hasSceneStage(GraphicsManager.getPipelineConfig());

                if (!renderingScene) {
                    IkGui.beginDisabled();
                    IkGui.text("(Not rendering scene, controls disabled)");
                }
                fogEnabled.draw(width, height);
                fogDensity.draw(width, height);
                wireframeEnabled.draw(width, height);
                directionalLightX.draw(width, height);
                directionalLightY.draw(width, height);
                directionalLightZ.draw(width, height);
                directionalLightIntensity.draw(width, height);
                if (!renderingScene) {
                    IkGui.endDisabled();
                }
            }

            if (IkGui.collapsingHeader("Debug Visualization")) {
                showPointLights.draw(width, height);
                showSpotLights.draw(width, height);
                showDirectionalLight.draw(width, height);
                showEntityBounds.draw(width, height);
                showShadowCascades.draw(width, height);
                showNormals.draw(width, height);
                showTangents.draw(width, height);
                normalLength.draw(width, height);
                freezeObserver.draw(width, height);
                IkGui.textWrapped(
                        "Culling, level of detail and streaming use the observer, so freeze it to"
                                + " inspect them from outside.");
            }

            if (IkGui.collapsingHeader("Retained UI")) {
                showUiShowcase.draw(width, height);
            }
        }

        IkGui.end();
    }

    /**
     * Apply changes to the debug visualization checkboxes. Turning any visualizer on adds the debug
     * stage to the pipeline.
     *
     * @param scene The scene.
     */
    private void handleDebugInput(@NonNull Scene scene) {
        DebugVisualizers visualizers = scene.getDebugVisualizers();
        boolean changed = false;
        if (showPointLights.checkResult()) {
            visualizers.setPointLights(showPointLights.getState());
            changed = true;
        }
        if (showSpotLights.checkResult()) {
            visualizers.setSpotLights(showSpotLights.getState());
            changed = true;
        }
        if (showDirectionalLight.checkResult()) {
            visualizers.setDirectionalLight(showDirectionalLight.getState());
            changed = true;
        }
        if (showEntityBounds.checkResult()) {
            visualizers.setEntityBounds(showEntityBounds.getState());
            changed = true;
        }
        if (showShadowCascades.checkResult()) {
            visualizers.setShadowCascades(showShadowCascades.getState());
            changed = true;
        }
        if (showNormals.checkResult()) {
            visualizers.setNormals(showNormals.getState());
            changed = true;
        }
        if (showTangents.checkResult()) {
            visualizers.setTangents(showTangents.getState());
            changed = true;
        }
        if (normalLength.checkResult()) {
            visualizers.setNormalLength(normalLength.getValue());
        }
        if (freezeObserver.checkResult()) {
            if (freezeObserver.getState()) {
                scene.freezeObserver();
            } else {
                scene.unfreezeObserver();
            }
            visualizers.setObserverFrustum(freezeObserver.getState());
            changed = true;
        }
        if (changed && visualizers.anyEnabled()) {
            final int config = GraphicsManager.getPipelineConfig();
            if (!RenderConfig.hasDebugStage(config)) {
                GraphicsManager.swapPipeline(RenderConfig.builder(config).withDebug().build());
            }
        }
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        super.handleGuiInput(scene, window);

        if (fogEnabled.checkResult()) {
            scene.getFog().setActive(fogEnabled.getState());
            return true;
        }
        if (wireframeEnabled.checkResult()) {
            int oldConfig = GraphicsManager.getPipelineConfig();
            RenderConfig.ConfigBuilder builder = RenderConfig.builder(oldConfig);
            if (wireframeEnabled.getState()) {
                builder.withWireframe();
            } else {
                builder.withoutWireframe();
            }
            GraphicsManager.swapPipeline(builder.build());
        }
        handleDebugInput(scene);
        if (showUiShowcase.checkResult()) {
            UiShowcase.setShown(showUiShowcase.getState());
        }
        DirectionalLight directionalLight = scene.getSceneLights().getDirLight();
        Vector3f directionalLightDir = directionalLight.getDirection();
        if (fogDensity.checkResult()) {
            scene.getFog().setDensity(fogDensity.getValue());
        }
        if (directionalLightX.checkResult()) {
            directionalLightDir.setComponent(0, directionalLightX.getValue());
        }
        if (directionalLightY.checkResult()) {
            directionalLightDir.setComponent(1, directionalLightY.getValue());
        }
        if (directionalLightZ.checkResult()) {
            directionalLightDir.setComponent(2, directionalLightZ.getValue());
        }
        if (directionalLightIntensity.checkResult()) {
            directionalLight.setIntensity(directionalLightIntensity.getValue());
        }

        return false;
    }

    @Override
    public void updateValues(@NonNull Scene scene, @NonNull Window window) {
        super.updateValues(scene, window);

        fogEnabled.setState(scene.getFog().isActive());
        int config = GraphicsManager.getPipelineConfig();
        wireframeEnabled.setState(RenderConfig.sceneIsWireframe(config));

        DirectionalLight directionalLight = scene.getSceneLights().getDirLight();
        Vector3f directionalLightDir = directionalLight.getDirection();
        fogDensity.setValue(scene.getFog().getDensity());
        directionalLightX.setValue(directionalLightDir.x());
        directionalLightY.setValue(directionalLightDir.y());
        directionalLightZ.setValue(directionalLightDir.z());
        directionalLightIntensity.setValue(directionalLight.getIntensity());
    }
}
