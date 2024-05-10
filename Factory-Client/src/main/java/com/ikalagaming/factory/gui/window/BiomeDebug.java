package com.ikalagaming.factory.gui.window;

import static org.lwjgl.opengl.GL11.glIsTexture;

import com.ikalagaming.factory.gui.DefaultWindows;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.TextureHandler;
import com.ikalagaming.graphics.frontend.Format;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.frontend.gui.component.GuiWindow;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.random.RandomGen;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import lombok.NonNull;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class BiomeDebug extends GuiWindow {

    public BiomeDebug() {
        super(DefaultWindows.BIOME_DEBUG.getName(), ImGuiWindowFlags.None);
        setScale(0.70f, 0.90f);
        setDisplacement(0.01f, 0.05f);
        setAlignment(Alignment.NORTH_WEST);
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

    private final AtomicBoolean generateRequested = new AtomicBoolean();
    private Texture temperatureMap;
    private Texture heightMap;
    private Texture erosionMap;
    private Texture vegetationMap;
    private Texture weirdnessMap;

    private Texture biomeMap;

    private TextureHandler textureHandler;

    @Override
    public void draw(final int width, final int height, @NonNull TextureHandler textureHandler) {
        if (this.textureHandler == null) {
            this.textureHandler = textureHandler;
        }
        ImGui.setNextWindowPos(
                getActualDisplaceX() * width, getActualDisplaceY() * height, ImGuiCond.Once);
        ImGui.setNextWindowSize(
                getActualWidth() * width, getActualHeight() * height, ImGuiCond.Once);
        ImGui.begin(title, windowOpen, windowFlags);

        if (ImGui.button("Generate textures")) {
            generateRequested.set(true);
        }

        ImGui.beginGroup();
        ImGui.text("Temperature");
        drawImage(temperatureMap, textureHandler);
        ImGui.text("Erosion");
        drawImage(erosionMap, textureHandler);
        ImGui.endGroup();

        ImGui.sameLine();
        ImGui.beginGroup();
        ImGui.text("Height");
        drawImage(heightMap, textureHandler);
        ImGui.text("Vegetation");
        drawImage(vegetationMap, textureHandler);
        ImGui.endGroup();

        ImGui.sameLine();
        ImGui.beginGroup();
        ImGui.text("Weirdness");
        drawImage(weirdnessMap, textureHandler);
        ImGui.text("Biomes");
        drawImage(biomeMap, textureHandler);
        ImGui.endGroup();

        ImGui.end();
    }

    /**
     * Draw a texture as an image, wherever we currently are in a window.
     *
     * @param image The texture to draw.
     */
    private void drawImage(Texture image, @NonNull TextureHandler textureHandler) {
        if (image != null) {
            if (glIsTexture(image.id())) {
                textureHandler.bind(image);
                ImGui.image(image.id(), image.width(), image.height());
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
    private Texture generateTexture(BufferedImage image, @NonNull TextureHandler textureHandler) {
        int[] rgbValues =
                image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        byte[] rgba = intARGBtoByteRGBA(rgbValues);

        ByteBuffer buffer = ByteBuffer.allocateDirect(rgba.length);
        buffer.put(rgba);
        buffer.rewind();

        return textureHandler.load(buffer, Format.R8G8B8_UINT, image.getWidth(), image.getHeight());
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        if (generateRequested.compareAndExchange(true, false)) {
            long seed = RandomGen.generateSeed();

            if (temperatureMap != null) {
                textureHandler.cleanup(temperatureMap);
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
            temperatureMap = generateTexture(tempImage, textureHandler);

            if (heightMap != null) {
                textureHandler.cleanup(heightMap);
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
            heightMap = generateTexture(heightImage, textureHandler);

            if (erosionMap != null) {
                textureHandler.cleanup(erosionMap);
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
            erosionMap = generateTexture(erosionImage, textureHandler);

            if (vegetationMap != null) {
                textureHandler.cleanup(vegetationMap);
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
            vegetationMap = generateTexture(vegetationImage, textureHandler);

            if (weirdnessMap != null) {
                textureHandler.cleanup(weirdnessMap);
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
            weirdnessMap = generateTexture(weirdnessImage, textureHandler);
        }
        return false;
    }
}
