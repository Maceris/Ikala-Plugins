package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Fog;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.scene.lights.SceneLights;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import lombok.NonNull;
import org.joml.Vector3f;

/**
 * Controls for lights and fog.
 *
 * @author Ches Burks
 */
public class SceneControls implements GUIWindow {
    private float[] ambientColor;
    private float[] ambientFactor;
    private float[] dirLightColor;

    private float[] fogColor;
    private ImBoolean fogEnabled;
    private float[] fogDensity;

    private float[] dirLightIntensity;

    private float[] dirLightX;
    private float[] dirLightY;
    private float[] dirLightZ;

    @Override
    public void draw() {
        ImGui.setNextWindowPos(200, 200, ImGuiCond.Once);
        ImGui.setNextWindowSize(450, 400, ImGuiCond.Once);
        ImGui.begin("Scene Controls");
        if (ImGui.treeNode("Ambient Light")) {
            ImGui.sliderFloat("Ambient factor", ambientFactor, 0.0f, 1.0f, "%.2f");
            ImGui.colorEdit3("Ambient color", ambientColor);
            ImGui.treePop();
        }
        if (ImGui.treeNode("Dir Light")) {
            ImGui.sliderFloat("Dir Light - x", dirLightX, -1.0f, 1.0f, "%.2f");
            ImGui.sliderFloat("Dir Light - y", dirLightY, -1.0f, 1.0f, "%.2f");
            ImGui.sliderFloat("Dir Light - z", dirLightZ, -1.0f, 1.0f, "%.2f");
            ImGui.colorEdit3("Dir Light color", dirLightColor);
            ImGui.sliderFloat("Dir Light Intensity", dirLightIntensity, 0.0f, 1.0f, "%.2f");
            ImGui.treePop();
        }
        if (ImGui.treeNode("Fog")) {
            ImGui.checkbox("Fog Enabled", fogEnabled);
            ImGui.colorEdit3("Fog Color", fogColor);
            ImGui.sliderFloat("Fog Density", fogDensity, 0f, 1.0f, "%.2f");
            ImGui.treePop();
        }

        ImGui.end();
    }

    @Override
    public void handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        SceneLights sceneLights = scene.getSceneLights();
        AmbientLight ambientLight = sceneLights.getAmbientLight();
        if (ambientFactor == null) {
            // we haven't been set up yet
            return;
        }
        ambientLight.setIntensity(ambientFactor[0]);
        ambientLight.setColor(ambientColor[0], ambientColor[1], ambientColor[2]);

        DirectionalLight dirLight = sceneLights.getDirLight();
        dirLight.setDirection(dirLightX[0], dirLightY[0], dirLightZ[0]);
        dirLight.setColor(dirLightColor[0], dirLightColor[1], dirLightColor[2]);
        dirLight.setIntensity(dirLightIntensity[0]);

        Fog fog = scene.getFog();
        fog.setActive(fogEnabled.get());
        fog.setColor(fogColor[0], fogColor[1], fogColor[2]);
        fog.setDensity(fogDensity[0]);
    }

    @Override
    public void setup(@NonNull Scene scene) {
        SceneLights sceneLights = scene.getSceneLights();
        AmbientLight ambientLight = sceneLights.getAmbientLight();
        Vector3f color = ambientLight.getColor();

        ambientFactor = new float[] {ambientLight.getIntensity()};
        ambientColor = new float[] {color.x, color.y, color.z};

        DirectionalLight dirLight = sceneLights.getDirLight();
        color = dirLight.getColor();
        Vector3f pos = dirLight.getDirection();
        dirLightColor = new float[] {color.x, color.y, color.z};
        dirLightX = new float[] {pos.x};
        dirLightY = new float[] {pos.y};
        dirLightZ = new float[] {pos.z};
        dirLightIntensity = new float[] {dirLight.getIntensity()};

        Fog fog = scene.getFog();
        fogColor = new float[] {fog.getColor().x, fog.getColor().y, fog.getColor().z};
        fogEnabled = new ImBoolean(fog.isActive());
        fogDensity = new float[] {fog.getDensity()};
    }
}
