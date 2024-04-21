package com.ikalagaming.factory.gui;

import static org.lwjgl.opengl.GL11.glIsTexture;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.Texture;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.random.RandomGen;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import lombok.NonNull;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class BiomeDebug extends GuiWindow {

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

    private final AtomicBoolean generateRequested = new AtomicBoolean();
    private Texture temperature;
    private Texture height;
    private Texture erosion;
    private Texture vegetation;
    private Texture weirdness;

    private Texture biomes;

    public void draw() {
        ImGui.setNextWindowPos(20, 20, ImGuiCond.Once);
        ImGui.setNextWindowSize(1300, 900, ImGuiCond.Once);
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

        ImGui.sameLine();
        ImGui.beginGroup();
        ImGui.text("Weirdness");
        drawImage(weirdness);
        ImGui.text("Biomes");
        drawImage(biomes);
        ImGui.endGroup();

        ImGui.end();
    }

    /**
     * Draw a texture as an image, wherever we currently are in a window.
     *
     * @param image The texture to draw.
     */
    private void drawImage(Texture image) {
        if (image != null) {
            if (glIsTexture(image.getTextureID())) {
                image.bind();
                ImGui.image(image.getTextureID(), image.getWidth(), image.getHeight());
            } else {
                ImGui.text("Texture somehow does not exist!");
            }
        }
    }

    /**
     * Convert a buffered image to a texture that the graphics library understands.
     *
     * @param image The image to turn into a texture.
     * @return The generated texture.
     */
    private Texture generateTexture(BufferedImage image) {
        int[] rgbValues =
                image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        byte[] rgba = intARGBtoByteRGBA(rgbValues);

        ByteBuffer buffer = ByteBuffer.allocateDirect(rgba.length);
        buffer.put(rgba);
        buffer.rewind();

        return new Texture(image.getWidth(), image.getHeight(), buffer);
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        if (generateRequested.compareAndExchange(true, false)) {
            long seed = RandomGen.generateSeed();

            if (temperature != null) {
                temperature.cleanup();
            }
            int w = 400;
            int h = 400;
            BufferedImage tempImage =
                    RandomGen.generateSimplexNoise(
                            RandomGen.SimplexParameters.builder()
                                    .seed(seed)
                                    .startX(0)
                                    .startY(0)
                                    .width(w)
                                    .height(h)
                                    .scale(0.001)
                                    .octaves(4)
                                    .build());
            temperature = generateTexture(tempImage);

            if (height != null) {
                height.cleanup();
            }
            BufferedImage heightImage =
                    RandomGen.generateSimplexNoise(
                            RandomGen.SimplexParameters.builder()
                                    .startX(0)
                                    .startY(0)
                                    .seed(seed + 1)
                                    .width(w)
                                    .height(h)
                                    .scale(0.002)
                                    .octaves(10)
                                    .build());
            height = generateTexture(heightImage);

            if (erosion != null) {
                erosion.cleanup();
            }
            BufferedImage erosionImage =
                    RandomGen.generateSimplexNoise(
                            RandomGen.SimplexParameters.builder()
                                    .startX(0)
                                    .startY(0)
                                    .seed(seed + 2)
                                    .width(w)
                                    .height(h)
                                    .scale(0.002)
                                    .octaves(6)
                                    .build());
            erosion = generateTexture(erosionImage);

            if (vegetation != null) {
                vegetation.cleanup();
            }
            BufferedImage vegetationImage =
                    RandomGen.generateSimplexNoise(
                            RandomGen.SimplexParameters.builder()
                                    .startX(0)
                                    .startY(0)
                                    .seed(seed + 3)
                                    .width(w)
                                    .height(h)
                                    .scale(0.001)
                                    .octaves(5)
                                    .build());
            vegetation = generateTexture(vegetationImage);

            if (weirdness != null) {
                weirdness.cleanup();
            }
            BufferedImage weirdnessImage =
                    RandomGen.generateSimplexNoise(
                            RandomGen.SimplexParameters.builder()
                                    .startX(0)
                                    .startY(0)
                                    .seed(seed + 4)
                                    .width(w)
                                    .height(h)
                                    .scale(0.002)
                                    .octaves(6)
                                    .build());
            weirdness = generateTexture(weirdnessImage);
        }
        return false;
    }
}
