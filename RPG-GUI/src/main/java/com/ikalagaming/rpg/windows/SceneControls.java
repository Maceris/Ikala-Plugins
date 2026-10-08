package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.scene.Fog;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.scene.lights.SceneLights;

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
    private IkBoolean fogEnabled;
    private float[] fogDensity;

    private float[] dirLightIntensity;

    private float[] dirLightX;
    private float[] dirLightY;
    private float[] dirLightZ;

    @Override
    public void draw() {
        IkGui.setNextWindowPos(200, 200, Condition.ONCE);
        IkGui.setNextWindowSize(450, 400, Condition.ONCE);
        IkGui.begin("Scene Controls");
        if (IkGui.treeNode("Ambient Light")) {
            IkGui.sliderFloat("Ambient factor", ambientFactor, 0.0f, 1.0f, "%.2f");
            IkGui.colorEdit3("Ambient color", ambientColor);
            IkGui.treePop();
        }
        if (IkGui.treeNode("Dir Light")) {
            IkGui.sliderFloat("Dir Light - x", dirLightX, -1.0f, 1.0f, "%.2f");
            IkGui.sliderFloat("Dir Light - y", dirLightY, -1.0f, 1.0f, "%.2f");
            IkGui.sliderFloat("Dir Light - z", dirLightZ, -1.0f, 1.0f, "%.2f");
            IkGui.colorEdit3("Dir Light color", dirLightColor);
            IkGui.sliderFloat("Dir Light Intensity", dirLightIntensity, 0.0f, 1.0f, "%.2f");
            IkGui.treePop();
        }
        if (IkGui.treeNode("Fog")) {
            IkGui.checkbox("Fog Enabled", fogEnabled);
            IkGui.colorEdit3("Fog Color", fogColor);
            IkGui.sliderFloat("Fog Density", fogDensity, 0f, 1.0f, "%.2f");
            IkGui.treePop();
        }

        IkGui.end();
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
        ambientLight.getColor().set(ambientColor[0], ambientColor[1], ambientColor[2]);

        DirectionalLight dirLight = sceneLights.getDirLight();
        dirLight.getDirection().set(dirLightX[0], dirLightY[0], dirLightZ[0]);
        dirLight.getColor().set(dirLightColor[0], dirLightColor[1], dirLightColor[2]);
        dirLight.setIntensity(dirLightIntensity[0]);

        Fog fog = scene.getFog();
        fog.setActive(fogEnabled.get());
        fog.getColor().set(fogColor[0], fogColor[1], fogColor[2]);
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
        fogEnabled = new IkBoolean(fog.isActive());
        fogDensity = new float[] {fog.getDensity()};
    }
}
