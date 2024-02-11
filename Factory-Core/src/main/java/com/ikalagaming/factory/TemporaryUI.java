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

	private Texture temperature;
	private Texture height;
	private Texture erosion;
	private Texture vegetation;
	private Texture biomes;

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
		ImGui.setNextWindowPos(20, 20, ImGuiCond.Once);
		ImGui.setNextWindowSize(850, 850, ImGuiCond.Once);
		ImGui.begin("World Generation");

		if (ImGui.button("Generate textures")) {
			generateRequested.set(true);
		}

		ImGui.beginGroup();
		ImGui.text("Temperature");
		drawImage(temperature);
		ImGui.text("Erosion");
		drawImage(erosion);
		ImGui.endGroup();

		ImGui.sameLine();
		ImGui.beginGroup();
		ImGui.text("Height");
		drawImage(height);
		ImGui.text("Vegetation");
		drawImage(vegetation);
		ImGui.endGroup();

		ImGui.text("Biomes");
		drawImage(biomes);

		ImGui.end();
	}

	private void drawImage(Texture image) {
		if (image != null) {
			if (GL11.glIsTexture(image.getTextureID())) {
				image.bind();
				ImGui.image(image.getTextureID(), image.getWidth(),
					image.getHeight());
			}
			else {
				ImGui.text("Texture somehow does not exist!");
			}
		}
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
			long seed = gen.generateSeed();

			if (temperature != null) {
				temperature.cleanup();
			}
			int w = 400;
			int h = 400;
			double scale = 0.01;
			int octaves = 8;
			BufferedImage tempImage =
				gen.generateSimplexNoise(RandomGen.SimplexParameters.builder()
					.seed(seed).startX(-w).startY(-h).width(w).height(h)
					.scale(scale).octaves(octaves).build());
			temperature = generateTexture(tempImage);

			if (height != null) {
				height.cleanup();
			}
			BufferedImage heightImage =
				gen.generateSimplexNoise(RandomGen.SimplexParameters.builder()
					.startX(0).startY(-h).seed(seed).width(w).height(h)
					.scale(scale).octaves(octaves).build());
			height = generateTexture(heightImage);

			if (erosion != null) {
				erosion.cleanup();
			}
			BufferedImage erosionImage =
				gen.generateSimplexNoise(RandomGen.SimplexParameters.builder()
					.startX(-w).startY(0).seed(seed).width(w).height(h)
					.scale(scale).octaves(octaves).build());
			erosion = generateTexture(erosionImage);

			if (vegetation != null) {
				vegetation.cleanup();
			}
			BufferedImage vegetationImage =
				gen.generateSimplexNoise(RandomGen.SimplexParameters.builder()
					.startX(0).startY(0).seed(seed).width(w).height(h)
					.scale(scale).octaves(octaves).build());
			vegetation = generateTexture(vegetationImage);

		}

		return imGuiIO.getWantCaptureMouse()
			|| imGuiIO.getWantCaptureKeyboard();
	}

	private Texture generateTexture(BufferedImage image) {
		int[] rgbValues = image.getRGB(0, 0, image.getWidth(),
			image.getHeight(), null, 0, image.getWidth());
		byte[] rgba = intARGBtoByteRGBA(rgbValues);

		ByteBuffer buffer = ByteBuffer.allocateDirect(rgba.length);
		buffer.put(rgba);
		buffer.rewind();

		return new Texture(image.getWidth(), image.getHeight(), buffer);
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
