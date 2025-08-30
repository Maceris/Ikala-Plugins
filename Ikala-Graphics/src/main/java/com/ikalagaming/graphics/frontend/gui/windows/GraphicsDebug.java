package com.ikalagaming.graphics.frontend.gui.windows;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.RenderConfig;
import com.ikalagaming.graphics.frontend.gui.component.Checkbox;
import com.ikalagaming.graphics.frontend.gui.component.GuiWindow;
import com.ikalagaming.graphics.frontend.gui.component.Slider;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import lombok.NonNull;
import org.joml.Vector3f;

public class GraphicsDebug extends GuiWindow {

    public static final String WINDOW_NAME = "Graphics Debug";

    private final Checkbox fogEnabled;
    private final Checkbox wireframeEnabled;
    private final Slider fogDensity;
    private final Slider directionalLightX;
    private final Slider directionalLightY;
    private final Slider directionalLightZ;
    private final Slider directionalLightIntensity;

    public GraphicsDebug() {
        super(WINDOW_NAME, ImGuiWindowFlags.None);
        setScale(0.34f, 0.45f);
        setDisplacement(0.01f, 0.05f);
        setAlignment(Alignment.NORTH_EAST);

        fogEnabled = new Checkbox("Fog enabled", false);
        wireframeEnabled = new Checkbox("Wireframe enabled", false);
        fogDensity = new Slider("Fog Density", 0, 0, 1);
        directionalLightX = new Slider("Directional Light X", 0, -1, 1);
        directionalLightY = new Slider("Directional Light Y", 0, -1, 1);
        directionalLightZ = new Slider("Directional Light Z", 0, -1, 1);
        directionalLightIntensity = new Slider("Directional Light Intensity", 0, 0, 4f);

        addChild(fogEnabled);
        addChild(fogDensity);
        addChild(wireframeEnabled);
        addChild(directionalLightX);
        addChild(directionalLightY);
        addChild(directionalLightZ);
        addChild(directionalLightIntensity);
    }

    @Override
    public void draw(int width, int height) {
        ImGui.setNextWindowViewport(ImGui.getMainViewport().getID());
        ImGui.setNextWindowPos(
                getActualDisplaceX() * width, getActualDisplaceY() * height, ImGuiCond.Always);
        ImGui.setNextWindowSize(
                getActualWidth() * width, getActualHeight() * height, ImGuiCond.Always);
        ImGui.begin(title, windowOpen, windowFlags);

        if (isVisible()) {
            recalculate();

            Scene scene = GraphicsManager.getScene();

            if (ImGui.collapsingHeader("Stats")) {
                ImGui.text(String.format("FPS: %d", GraphicsManager.getLastFPS()));
                ImGui.text(
                        String.format(
                                "Point lights: %,d",
                                scene.getSceneLights().getPointLights().size()));
                ImGui.text(
                        String.format(
                                "Spot lights: %,d", scene.getSceneLights().getSpotLights().size()));
                ImGui.text(
                        String.format(
                                "Materials loaded - %,d",
                                GraphicsManager.getScene().getMaterialCache().getMaterialCount()));

                long triangles = 0;
                int meshes = 0;
                int entities = 0;
                for (Model model : scene.getModelMap().values()) {
                    meshes += model.getMeshDataList().size();
                    int meshEntities = model.getEntitiesList().size();
                    meshes += model.getMeshDataList().size();
                    entities += meshEntities;
                    int meshTriangles = 0;
                    for (MeshData mesh : model.getMeshDataList()) {
                        meshTriangles += mesh.getIndices().length / 3;
                    }
                    triangles += (long) meshTriangles * meshEntities;
                }

                ImGui.text(String.format("Models loaded: %,d", scene.getModelMap().size()));
                ImGui.text(String.format("Meshes loaded: %,d", meshes));
                ImGui.text(String.format("Entities: %,d", entities));
                ImGui.text(String.format("Triangles: %,d", triangles));
            }

            if (ImGui.collapsingHeader("Render Config Info")) {
                int config = GraphicsManager.getPipelineConfig();
                final String flagString = "%12s - %s";
                ImGui.text(String.format(flagString, "Error", RenderConfig.hasError(config)));
                ImGui.text(
                        String.format(
                                flagString, "Animation", RenderConfig.hasAnimationStage(config)));
                ImGui.text(
                        String.format(flagString, "Shadow", RenderConfig.hasShadowStage(config)));
                ImGui.text(String.format(flagString, "Scene", RenderConfig.hasSceneStage(config)));
                ImGui.text(
                        String.format(flagString, "Skybox", RenderConfig.hasSkyboxStage(config)));
                ImGui.text(
                        String.format(flagString, "Filter", RenderConfig.hasFilterStage(config)));
                ImGui.text(String.format(flagString, "Gui", RenderConfig.hasGuiStage(config)));
                ImGui.text(
                        String.format(
                                flagString,
                                "Transparency",
                                RenderConfig.hasTransparencyPass(config)));
                ImGui.text(
                        String.format(
                                flagString, "Wireframe", RenderConfig.sceneIsWireframe(config)));
            }

            if (ImGui.collapsingHeader("Camera")) {
                var camera = GraphicsManager.getCameraManager().getCamera();
                ImGui.text(
                        String.format(
                                "Camera position: x:%.2f, y:%.2f, z:%.2f",
                                camera.getPosition().x(),
                                camera.getPosition().y(),
                                camera.getPosition().z()));

                ImGui.text(
                        String.format(
                                "Camera rotation: x:%.2f, y:%.2f",
                                camera.getRotation().x(), camera.getRotation().y()));
            }

            if (ImGui.collapsingHeader("Scene Controls")) {
                boolean renderingScene =
                        RenderConfig.hasSceneStage(GraphicsManager.getPipelineConfig());

                if (!renderingScene) {
                    ImGui.beginDisabled();
                    ImGui.text("(Not rendering scene, controls disabled)");
                }
                fogEnabled.draw(width, height);
                fogDensity.draw(width, height);
                wireframeEnabled.draw(width, height);
                directionalLightX.draw(width, height);
                directionalLightY.draw(width, height);
                directionalLightZ.draw(width, height);
                directionalLightIntensity.draw(width, height);
                if (!renderingScene) {
                    ImGui.endDisabled();
                }
            }
        }

        ImGui.end();
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
