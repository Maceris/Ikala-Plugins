package com.ikalagaming.converter.gui.window;

import com.ikalagaming.converter.gui.DefaultWindows;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.RenderConfig;
import com.ikalagaming.graphics.frontend.gui.component.Checkbox;
import com.ikalagaming.graphics.frontend.gui.component.GuiWindow;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;
import com.ikalagaming.graphics.scene.Scene;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import lombok.NonNull;

public class GraphicsDebug extends GuiWindow {

    private final Checkbox fogEnabled;
    private final Checkbox wireframeEnabled;

    public GraphicsDebug() {
        super(DefaultWindows.GRAPHICS_DEBUG.getName(), ImGuiWindowFlags.None);
        setScale(0.24f, 0.25f);
        setDisplacement(0.01f, 0.05f);
        setAlignment(Alignment.NORTH_EAST);

        // (TODO) ches update this when a scene is loaded, preferably fix backend to allow real time
        fogEnabled = new Checkbox("Fog enabled", false);
        wireframeEnabled = new Checkbox("Wireframe enabled", false);

        addChild(fogEnabled);
        addChild(wireframeEnabled);
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
        }

        ImGui.end();
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
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

        return false;
    }
}
