package com.ikalagaming.converter.gui.window;

import com.ikalagaming.converter.gui.DefaultWindows;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.RenderConfig;
import com.ikalagaming.graphics.frontend.gui.component.Checkbox;
import com.ikalagaming.graphics.frontend.gui.component.GuiWindow;
import com.ikalagaming.graphics.frontend.gui.component.Slider;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import lombok.NonNull;
import org.joml.Vector3f;

public class GraphicsDebug extends GuiWindow {

    private final Checkbox fogEnabled;
    private final Checkbox wireframeEnabled;
    private final Slider fogDensity;
    private final Slider directionalLightX;
    private final Slider directionalLightY;
    private final Slider directionalLightZ;
    private final Slider directionalLightIntensity;

    public GraphicsDebug() {
        super(DefaultWindows.GRAPHICS_DEBUG.getName(), ImGuiWindowFlags.None);
        setScale(0.34f, 0.45f);
        setDisplacement(0.01f, 0.05f);
        setAlignment(Alignment.NORTH_EAST);

        fogEnabled = new Checkbox("Fog enabled", false);
        wireframeEnabled = new Checkbox("Wireframe enabled", false);
        fogDensity = new Slider("Fog Density", 0, 0, 10);
        directionalLightX = new Slider("Directional Light X", 0, -1, 1);
        directionalLightY = new Slider("Directional Light Y", 0, -1, 1);
        directionalLightZ = new Slider("Directional Light Z", 0, -1, 1);
        directionalLightIntensity = new Slider("Directional Light Intensity", 0, 0, 4f);

        addChild(fogEnabled);
        addChild(wireframeEnabled);
        addChild(fogDensity);
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
            children.forEach(child -> child.draw(width, height));

            ImGui.text("Render Config:");
            int config = GraphicsManager.getPipelineConfig();
            ImGui.text(String.format("Error Flag - %s", RenderConfig.hasError(config)));
            ImGui.text(
                    String.format("Animation Flag - %s", RenderConfig.hasAnimationStage(config)));
            ImGui.text(String.format("Shadow Flag - %s", RenderConfig.hasShadowStage(config)));
            ImGui.text(String.format("Scene Flag - %s", RenderConfig.hasSceneStage(config)));
            ImGui.text(String.format("Skybox Flag - %s", RenderConfig.hasSkyboxStage(config)));
            ImGui.text(String.format("Filter Flag - %s", RenderConfig.hasFilterStage(config)));
            ImGui.text(String.format("Gui Flag - %s", RenderConfig.hasGuiStage(config)));
            ImGui.text(
                    String.format(
                            "Transparency Flag - %s", RenderConfig.hasTransparencyPass(config)));
            ImGui.text(String.format("Wireframe Flag - %s", RenderConfig.sceneIsWireframe(config)));

            ImGui.text(
                    String.format(
                            "Materials loaded - %s",
                            GraphicsManager.getScene().getMaterialCache().getMaterialCount()));
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
