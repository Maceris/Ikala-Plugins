package com.ikalagaming.factory;

import com.ikalagaming.graphics.GuiInstance;
import com.ikalagaming.graphics.MouseInput;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.events.Shutdown;

import imgui.ImColor;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import lombok.NonNull;
import org.joml.Vector2f;

/**
 * A UI that we are using until a proper solution is designed.
 *
 * @author Ches Burks
 *
 */
public class TemporaryUI implements GuiInstance {

	private ImBoolean showDemo;
	private ImBoolean showWorldGen;

	/**
	 * Set up the UI.
	 */
	public TemporaryUI() {
		this.showDemo = new ImBoolean(false);
		this.showWorldGen = new ImBoolean(true);
	}

	@Override
	public void drawGui() {
		ImGui.newFrame();

		if (ImGui.beginMainMenuBar()) {
			if (ImGui.beginMenu("Windows")) {
				ImGui.checkbox("Demo", this.showDemo);
				ImGui.checkbox("World Generation Debugger", this.showWorldGen);
				ImGui.endMenu();
			}
			ImGui.pushStyleColor(ImGuiCol.Text,
				ImColor.floatToColor(1f, 0.1f, 0.1f));
			if (ImGui.menuItem("Quit Game")) {
				new Shutdown().fire();
			}
			ImGui.popStyleColor();

			ImGui.endMainMenuBar();
		}

		this.showWindows();

		ImGui.endFrame();
		ImGui.render();
	}

	/**
	 * Draw the world generation debugging information.
	 */
	private void drawWorldGen() {
		ImGui.setNextWindowPos(410, 10, ImGuiCond.Once);
		ImGui.setNextWindowSize(600, 500, ImGuiCond.Once);
		ImGui.begin("World Generation");

		ImGui.text("Textures TBD");

		ImGui.end();
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

		return imGuiIO.getWantCaptureMouse()
			|| imGuiIO.getWantCaptureKeyboard();
	}

	/**
	 * Render whichever windows are applicable.
	 */
	private void showWindows() {
		if (this.showDemo.get()) {
			ImGui.showDemoWindow();
		}
		if (this.showWorldGen.get()) {
			this.drawWorldGen();
		}

	}

}
