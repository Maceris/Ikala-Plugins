package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.render.Render;
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
 *
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

	private ImBoolean filterEnabled;

	@Override
	public void draw() {
		ImGui.setNextWindowPos(200, 200, ImGuiCond.Once);
		ImGui.setNextWindowSize(450, 400, ImGuiCond.Once);
		ImGui.begin("Scene Controls");
		if (ImGui.treeNode("Ambient Light")) {
			ImGui.sliderFloat("Ambient factor", this.ambientFactor, 0.0f, 1.0f,
				"%.2f");
			ImGui.colorEdit3("Ambient color", this.ambientColor);
			ImGui.treePop();
		}
		if (ImGui.treeNode("Dir Light")) {
			ImGui.sliderFloat("Dir Light - x", this.dirLightX, -1.0f, 1.0f,
				"%.2f");
			ImGui.sliderFloat("Dir Light - y", this.dirLightY, -1.0f, 1.0f,
				"%.2f");
			ImGui.sliderFloat("Dir Light - z", this.dirLightZ, -1.0f, 1.0f,
				"%.2f");
			ImGui.colorEdit3("Dir Light color", this.dirLightColor);
			ImGui.sliderFloat("Dir Light Intensity", this.dirLightIntensity,
				0.0f, 1.0f, "%.2f");
			ImGui.treePop();
		}
		if (ImGui.treeNode("Fog")) {
			ImGui.checkbox("Fog Enabled", this.fogEnabled);
			ImGui.colorEdit3("Fog Color", this.fogColor);
			ImGui.sliderFloat("Fog Density", this.fogDensity, 0f, 1.0f, "%.2f");
			ImGui.treePop();
		}
		ImGui.checkbox("Filter Enabled", this.filterEnabled);
		ImGui.end();
	}

	@Override
	public void handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
		SceneLights sceneLights = scene.getSceneLights();
		AmbientLight ambientLight = sceneLights.getAmbientLight();
		if (this.ambientFactor == null) {
			// we haven't been set up yet
			return;
		}
		ambientLight.setIntensity(this.ambientFactor[0]);
		ambientLight.setColor(this.ambientColor[0], this.ambientColor[1],
			this.ambientColor[2]);

		DirectionalLight dirLight = sceneLights.getDirLight();
		dirLight.setPosition(this.dirLightX[0], this.dirLightY[0],
			this.dirLightZ[0]);
		dirLight.setColor(this.dirLightColor[0], this.dirLightColor[1],
			this.dirLightColor[2]);
		dirLight.setIntensity(this.dirLightIntensity[0]);

		Fog fog = scene.getFog();
		fog.setActive(this.fogEnabled.get());
		fog.setColor(this.fogColor[0], this.fogColor[1], this.fogColor[2]);
		fog.setDensity(this.fogDensity[0]);

		Render.configuration.setFilter(this.filterEnabled.get());
	}

	@Override
	public void setup(@NonNull Scene scene) {
		SceneLights sceneLights = scene.getSceneLights();
		AmbientLight ambientLight = sceneLights.getAmbientLight();
		Vector3f color = ambientLight.getColor();

		this.ambientFactor = new float[] {ambientLight.getIntensity()};
		this.ambientColor = new float[] {color.x, color.y, color.z};

		DirectionalLight dirLight = sceneLights.getDirLight();
		color = dirLight.getColor();
		Vector3f pos = dirLight.getDirection();
		this.dirLightColor = new float[] {color.x, color.y, color.z};
		this.dirLightX = new float[] {pos.x};
		this.dirLightY = new float[] {pos.y};
		this.dirLightZ = new float[] {pos.z};
		this.dirLightIntensity = new float[] {dirLight.getIntensity()};

		Fog fog = scene.getFog();
		this.fogColor =
			new float[] {fog.getColor().x, fog.getColor().y, fog.getColor().z};
		this.fogEnabled = new ImBoolean(fog.isActive());
		this.fogDensity = new float[] {fog.getDensity()};

		this.filterEnabled = new ImBoolean();
	}
}
