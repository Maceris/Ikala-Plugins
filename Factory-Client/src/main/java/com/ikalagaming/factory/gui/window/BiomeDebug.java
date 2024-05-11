package com.ikalagaming.factory.gui.window;

import com.ikalagaming.factory.gui.DefaultWindows;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.Format;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.frontend.gui.component.Button;
import com.ikalagaming.graphics.frontend.gui.component.GuiWindow;
import com.ikalagaming.graphics.frontend.gui.component.Image;
import com.ikalagaming.graphics.frontend.gui.component.Text;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.random.RandomGen;

import imgui.flag.ImGuiWindowFlags;
import lombok.NonNull;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

public class BiomeDebug extends GuiWindow {

    public BiomeDebug() {
        super(DefaultWindows.BIOME_DEBUG.getName(), ImGuiWindowFlags.None);
        setScale(0.70f, 0.90f);
        setDisplacement(0.01f, 0.05f);
        setAlignment(Alignment.NORTH_WEST);

        temperatureMap = new Image();
        temperatureMap.setScale(0.25f, 0.40f);
        temperatureMap.setDisplacement(0.00f, 0.10f);
        erosionMap = new Image();
        erosionMap.setScale(0.25f, 0.40f);
        erosionMap.setDisplacement(0.00f, 0.60f);
        heightMap = new Image();
        heightMap.setScale(0.25f, 0.40f);
        heightMap.setDisplacement(0.35f, 0.10f);
        vegetationMap = new Image();
        vegetationMap.setScale(0.25f, 0.40f);
        vegetationMap.setDisplacement(0.35f, 0.60f);
        weirdnessMap = new Image();
        weirdnessMap.setScale(0.25f, 0.40f);
        weirdnessMap.setDisplacement(0.65f, 0.10f);
        biomeMap = new Image();
        biomeMap.setScale(0.25f, 0.40f);
        biomeMap.setDisplacement(0.65f, 0.60f);

        var temperature = new Text("Temperature");
        temperature.setScale(0.25f, 0.05f);
        temperature.setDisplacement(0.00f, 0.05f);
        var erosion = new Text("Erosion");
        erosion.setScale(0.25f, 0.05f);
        erosion.setDisplacement(0.00f, 0.55f);
        var height = new Text("Height");
        height.setScale(0.25f, 0.05f);
        height.setDisplacement(0.35f, 0.05f);
        var vegetation = new Text("Vegetation");
        vegetation.setScale(0.30f, 0.05f);
        vegetation.setDisplacement(0.35f, 0.55f);
        var weirdness = new Text("Weirdness");
        weirdness.setScale(0.25f, 0.05f);
        weirdness.setDisplacement(0.65f, 0.05f);
        var biomes = new Text("Biomes");
        biomes.setScale(0.25f, 0.05f);
        biomes.setDisplacement(0.65f, 0.55f);

        addChild(temperature);
        addChild(erosion);
        addChild(height);
        addChild(vegetation);
        addChild(weirdness);
        addChild(biomes);

        addChild(temperatureMap);
        addChild(erosionMap);
        addChild(heightMap);
        addChild(vegetationMap);
        addChild(weirdnessMap);
        addChild(biomeMap);

        generateButton = new Button("Generate textures");
        generateButton.setScale(0.05f, 0.05f);
        generateButton.setDisplacement(0.05f, 0.05f);
        generateButton.setAlignment(Alignment.NORTH_EAST);
        addChild(generateButton);
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

    private final Image temperatureMap;
    private final Image heightMap;
    private final Image erosionMap;
    private final Image vegetationMap;
    private final Image weirdnessMap;

    private final Image biomeMap;

    private final Button generateButton;

    /**
     * Convert a buffered image to a texture that the graphics library understands.
     *
     * @param image The image to turn into a texture.
     * @return The generated texture.
     */
    private Texture generateTexture(@NonNull BufferedImage image) {
        int[] rgbValues =
                image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        byte[] rgba = intARGBtoByteRGBA(rgbValues);

        ByteBuffer buffer = ByteBuffer.allocateDirect(rgba.length);
        buffer.put(rgba);
        buffer.rewind();

        return GraphicsManager.getTextureLoader()
                .load(buffer, Format.R8G8B8A8_UINT, image.getWidth(), image.getHeight());
    }

    @Override
    public boolean handleGuiInput(@NonNull Scene scene, @NonNull Window window) {
        if (generateButton.checkResult()) {
            long seed = RandomGen.generateSeed();

            if (temperatureMap.getTexture() != null) {
                GraphicsManager.getDeletionQueue().add(temperatureMap.getTexture());
                temperatureMap.setTexture(null);
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
            temperatureMap.setTexture(generateTexture(tempImage));

            if (heightMap.getTexture() != null) {
                GraphicsManager.getDeletionQueue().add(heightMap.getTexture());
                heightMap.setTexture(null);
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
            heightMap.setTexture(generateTexture(heightImage));

            if (erosionMap.getTexture() != null) {
                GraphicsManager.getDeletionQueue().add(erosionMap.getTexture());
                erosionMap.setTexture(null);
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
            erosionMap.setTexture(generateTexture(erosionImage));

            if (vegetationMap.getTexture() != null) {
                GraphicsManager.getDeletionQueue().add(vegetationMap.getTexture());
                vegetationMap.setTexture(null);
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
            vegetationMap.setTexture(generateTexture(vegetationImage));

            if (weirdnessMap.getTexture() != null) {
                GraphicsManager.getDeletionQueue().add(weirdnessMap.getTexture());
                weirdnessMap.setTexture(null);
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
            weirdnessMap.setTexture(generateTexture(weirdnessImage));
        }
        return false;
    }
}
