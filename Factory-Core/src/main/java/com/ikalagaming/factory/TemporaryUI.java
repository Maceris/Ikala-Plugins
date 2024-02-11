package com.ikalagaming.factory;

import com.ikalagaming.graphics.GuiInstance;
import com.ikalagaming.graphics.MouseInput;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.Texture;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.events.Shutdown;
import com.ikalagaming.random.RandomGen;

import imgui.ImColor;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import lombok.NonNull;
import org.joml.Vector2f;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A UI that we are using until a proper solution is designed.
 *
 * @author Ches Burks
 *
 */
public class TemporaryUI implements GuiInstance {

	private ImBoolean showDemo;
	private ImBoolean showWorldGen;
	private AtomicBoolean generateRequested = new AtomicBoolean();

	private Texture currentImage;

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
		ImGui.setNextWindowPos(410, 20, ImGuiCond.Once);
		ImGui.setNextWindowSize(750, 750, ImGuiCond.Once);
		ImGui.begin("World Generation");

		ImGui.text("Textures TBD");

		if (ImGui.button("Generate tiled image")) {
			generateRequested.set(true);
		}

		if (currentImage != null) {
			if (GL11.glIsTexture(currentImage.getTextureID())) {
				currentImage.bind();
				ImGui.image(currentImage.getTextureID(),
					currentImage.getWidth(), currentImage.getHeight());
			}
			else {
				ImGui.text("Texture somehow does not exist!");
			}
		}

		ImGui.end();
	}

	private static byte[] intARGBtoByteRGBA(int[] argb) {
		byte[] rgba = new byte[argb.length * 4];

		for (int i = 0; i < argb.length; i++) {
			rgba[4 * i] = (byte) ((argb[i] >> 16) & 0xff); // R
			rgba[4 * i + 1] = (byte) ((argb[i] >> 8) & 0xff); // G
			rgba[4 * i + 2] = (byte) ((argb[i]) & 0xff); // B
			rgba[4 * i + 3] = (byte) ((argb[i] >> 24) & 0xff); // A
		}

		return rgba;
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

		if (generateRequested.compareAndExchange(true, false)) {
			RandomGen gen = new RandomGen();

			BufferedImage image = gen.generateHeightMap();

			int[] rgbValues = image.getRGB(0, 0, image.getWidth(),
				image.getHeight(), null, 0, image.getWidth());

			byte[] rgba = intARGBtoByteRGBA(rgbValues);

			if (currentImage != null) {
				currentImage.cleanup();
			}

			ByteBuffer buffer = ByteBuffer.allocateDirect(rgba.length);
			buffer.put(rgba);
			buffer.rewind();
			currentImage =
				new Texture(image.getWidth(), image.getHeight(), buffer);
		}

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
