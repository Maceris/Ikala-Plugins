package com.ikalagaming.rpg;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GuiInstance;
import com.ikalagaming.graphics.MouseInput;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.render.Render;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.scene.lights.SceneLights;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import lombok.NonNull;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * A GUI for manipulating lights
 */
public class GUIControls implements GuiInstance {

	private float[] ambientColor;
	private float[] ambientFactor;
	private float[] dirLightColor;
	private float[] dirLightIntensity;
	private float[] dirLightX;
	private float[] dirLightY;
	private float[] dirLightZ;
	private ImBoolean wireframe;

	/**
	 * Set up the light controls.
	 *
	 * @param scene The scene.
	 */
	public GUIControls(@NonNull Scene scene) {
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
		this.wireframe = new ImBoolean(false);
	}

	@Override
	public void drawGui() {
		ImGui.newFrame();
		ImGui.setNextWindowPos(10, 10, ImGuiCond.Once);
		ImGui.setNextWindowSize(450, 400);

		ImGui.begin("Lights controls");
		if (ImGui.collapsingHeader("Ambient Light")) {
			ImGui.sliderFloat("Ambient factor", this.ambientFactor, 0.0f, 1.0f,
				"%.2f");
			ImGui.colorEdit3("Ambient color", this.ambientColor);
		}

		if (ImGui.collapsingHeader("Dir Light")) {
			ImGui.sliderFloat("Dir Light - x", this.dirLightX, -1.0f, 1.0f,
				"%.2f");
			ImGui.sliderFloat("Dir Light - y", this.dirLightY, -1.0f, 1.0f,
				"%.2f");
			ImGui.sliderFloat("Dir Light - z", this.dirLightZ, -1.0f, 1.0f,
				"%.2f");
			ImGui.colorEdit3("Dir Light color", this.dirLightColor);
			ImGui.sliderFloat("Dir Light Intensity", this.dirLightIntensity,
				0.0f, 1.0f, "%.2f");
		}
		ImGui.end();

		ImGui.showDemoWindow();

		ImGui.setNextWindowPos(460, 10, ImGuiCond.Once);
		ImGui.setNextWindowSize(450, 400);
		ImGui.begin("Debug");

		ImGui.text(String.format("FPS: %d", GraphicsManager.getLastFPS()));

		Vector2f rotation =
			GraphicsManager.getCameraManager().getCamera().getRotation();
		ImGui.text(
			String.format("Camera rotation: (%f, %f)", rotation.x, rotation.y));
		Vector2f displ =
			GraphicsManager.getWindow().getMouseInput().getDisplVec();
		ImGui
			.text(String.format("Displace vector: (%f, %f)", displ.x, displ.y));

		ImGui.checkbox("Wireframe", this.wireframe);

		ImGui.end();

		ImGui.endFrame();
		ImGui.render();
	}

	@Override
	public boolean handleGuiInput(@NonNull Scene scene,
		@NonNull Window window) {
		ImGuiIO imGuiIO = ImGui.getIO();
		MouseInput mouseInput = window.getMouseInput();
		Vector2f mousePos = mouseInput.getCurrentPos();
		imGuiIO.setMousePos(mousePos.x, mousePos.y);
		imGuiIO.setMouseDown(0, mouseInput.isLeftButtonPressed());
		imGuiIO.setMouseDown(1, mouseInput.isRightButtonPressed());
		Render.configuration.setWireframe(this.wireframe.get());

		boolean consumed =
			imGuiIO.getWantCaptureMouse() || imGuiIO.getWantCaptureKeyboard();
		if (consumed) {
			SceneLights sceneLights = scene.getSceneLights();
			AmbientLight ambientLight = sceneLights.getAmbientLight();
			ambientLight.setIntensity(this.ambientFactor[0]);
			ambientLight.setColor(this.ambientColor[0], this.ambientColor[1],
				this.ambientColor[2]);

			DirectionalLight dirLight = sceneLights.getDirLight();
			dirLight.setPosition(this.dirLightX[0], this.dirLightY[0],
				this.dirLightZ[0]);
			dirLight.setColor(this.dirLightColor[0], this.dirLightColor[1],
				this.dirLightColor[2]);
			dirLight.setIntensity(this.dirLightIntensity[0]);
		}
		return consumed;
	}

}
