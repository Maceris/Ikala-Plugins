package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.MouseInput;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import lombok.NonNull;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Controls for lights and fog.
 *
 * @author Ches Burks
 */
public class DebugWindow implements GUIWindow {

    @Override
    public void draw() {
        ImGui.setNextWindowPos(10, 30, ImGuiCond.Once);
        ImGui.setNextWindowSize(450, 400, ImGuiCond.Once);
        ImGui.begin("Debug");

        ImGui.text(String.format("FPS: %d", GraphicsManager.getLastFPS()));

        Vector3f position = GraphicsManager.getCameraManager().getCamera().getPosition();
        ImGui.text(
                String.format("Camera position: (%f, %f, %f)", position.x, position.y, position.z));
        Vector2f rotation = GraphicsManager.getCameraManager().getCamera().getRotation();
        ImGui.text(String.format("Camera rotation: (%f, %f)", rotation.x, rotation.y));
        MouseInput input = GraphicsManager.getWindow().getMouseInput();
        ImGui.text(
                String.format(
                        "Mouse position: (%f, %f)",
                        input.getCurrentPos().x, input.getCurrentPos().y));
        ImGui.text(
                String.format(
                        "Displace vector: (%f, %f)", input.getDisplVec().x, input.getDisplVec().y));

        ImGui.text(
                String.format(
                        "Current window position: (%f, %f)",
                        ImGui.getWindowPosX(), ImGui.getWindowPosY()));
        ImGui.text(
                String.format(
                        "Current window size: (%f, %f)",
                        ImGui.getWindowSizeX(), ImGui.getWindowSizeY()));

        ImGui.end();
    }

    @Override
    public void handleGuiInput(@NonNull Scene scene, @NonNull Window window) {}

    @Override
    public void setup(@NonNull Scene scene) {}
}
