package com.ikalagaming.factory.render.looks;

import com.ikalagaming.graphics.benchmark.BlockTextures;

import lombok.NonNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

/**
 * Writes the first block textures, drawn in code, as 16x16 PNGs in the asset layout, as a start to
 * repaint by hand. Graphics' test art gives stone, dirt, grass, leaves and water; the rest are
 * drawn here. Only missing files are written: files that already exist are always kept, since they
 * may have been painted over. To start a texture again, delete its file and run this.
 *
 * <p>Run with {@code ./gradlew :Factory-Client:starterTextures --args="<data folder>"}, where the
 * data folder is the one holding {@code assets}, such as Lotomation's {@code
 * plugins/Factory-Client/data}.
 */
public final class StarterTextures {

    /** Pixels along each side. */
    private static final int SIZE = BlockTextures.ART;

    /** Bytes per pixel. */
    private static final int CHANNELS = 4;

    /** The largest channel value. */
    private static final int CHANNEL_MAX = 255;

    /** Where the block textures go, under the data folder. */
    private static final String BLOCK_TEXTURES =
            BlockLooks.ASSETS_FOLDER
                    + "/"
                    + BlockLooks.DEFAULT_NAMESPACE
                    + "/"
                    + BlockLooks.TEXTURES_FOLDER
                    + "/block";

    /** How see-through glass is, as alpha. */
    private static final double GLASS_ALPHA = 0.25;

    /** How many rings an end grain has. */
    private static final double LOG_RINGS = 2.2;

    /** Each texture's art, by file name without the extension. */
    private static final Map<String, Supplier<byte[]>> TEXTURES = textures();

    /**
     * Write the textures.
     *
     * @param args The data folder.
     * @throws IOException If a file can't be written.
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: StarterTextures <data folder>");
            System.exit(1);
            return;
        }
        final Path folder = Path.of(args[0]).resolve(BLOCK_TEXTURES);
        Files.createDirectories(folder);
        for (Map.Entry<String, Supplier<byte[]>> texture : TEXTURES.entrySet()) {
            final Path file = folder.resolve(texture.getKey() + BlockLooks.TEXTURE_EXTENSION);
            if (Files.exists(file)) {
                System.out.println("Kept " + file);
                continue;
            }
            ImageIO.write(image(texture.getValue().get()), "png", file.toFile());
            System.out.println("Wrote " + file);
        }
    }

    /**
     * Every texture's art.
     *
     * @return The art by file name, in a fixed order.
     */
    private static Map<String, Supplier<byte[]>> textures() {
        final Map<String, Supplier<byte[]>> out = new LinkedHashMap<>();
        out.put("stone", () -> BlockTextures.art(BlockTextures.Tile.STONE));
        out.put("dirt", () -> BlockTextures.art(BlockTextures.Tile.DIRT));
        out.put("grass_top", () -> BlockTextures.art(BlockTextures.Tile.GRASS_TOP));
        out.put("grass_side", () -> BlockTextures.art(BlockTextures.Tile.GRASS_SIDE));
        out.put("oak_leaves", () -> BlockTextures.art(BlockTextures.Tile.LEAVES));
        out.put("water", () -> BlockTextures.art(BlockTextures.Tile.WATER));
        out.put("sand", () -> draw(1, StarterTextures::sand));
        out.put("oak_log", () -> draw(2, StarterTextures::bark));
        out.put("oak_log_top", () -> draw(3, StarterTextures::endGrain));
        out.put("concrete", () -> draw(4, StarterTextures::concrete));
        out.put("steel", () -> draw(5, StarterTextures::steel));
        out.put("bronze", () -> draw(6, StarterTextures::bronze));
        out.put("glass", () -> draw(7, StarterTextures::glass));
        out.put("grass_tuft", () -> draw(8, StarterTextures::tuft));
        return out;
    }

    /** Draws one pixel. */
    @FunctionalInterface
    private interface Art {
        /**
         * Draw a pixel.
         *
         * @param x The column.
         * @param y The row, from the top.
         * @param noise The pixel's noise, from 0 to 1.
         * @param out Red, green, blue and alpha, from 0 to 1, written; alpha starts at 1.
         */
        void pixel(int x, int y, double noise, double @NonNull [] out);
    }

    /**
     * Draw a texture.
     *
     * @param salt Which texture, so each has its own noise.
     * @param art The art.
     * @return The pixels, rows from the top, red, green, blue and alpha bytes each.
     */
    private static byte[] draw(int salt, @NonNull Art art) {
        final byte[] out = new byte[SIZE * SIZE * CHANNELS];
        final double[] color = new double[CHANNELS];
        for (int y = 0; y < SIZE; ++y) {
            for (int x = 0; x < SIZE; ++x) {
                color[3] = 1;
                art.pixel(x, y, noise(salt, x, y), color);
                for (int c = 0; c < CHANNELS; ++c) {
                    out[(y * SIZE + x) * CHANNELS + c] =
                            (byte) Math.round(Math.clamp(color[c], 0, 1) * CHANNEL_MAX);
                }
            }
        }
        return out;
    }

    private static void sand(int x, int y, double n, double[] out) {
        final double grain = noise(11, x, y) < 0.1 ? -0.08 : 0;
        set(out, 0.86 + 0.06 * (n - 0.5) + grain, 0.78 + 0.06 * (n - 0.5) + grain, 0.55 + grain);
    }

    private static void bark(int x, int y, double n, double[] out) {
        // Dark furrows running down, wandering a little
        final boolean furrow = (x + (int) (noise(12, 0, y / 4) * 2)) % 4 == 0;
        final double shade = furrow ? 0.65 : 1 + 0.12 * (n - 0.5);
        set(out, 0.4 * shade, 0.3 * shade, 0.18 * shade);
    }

    private static void endGrain(int x, int y, double n, double[] out) {
        final double dx = x - 7.5;
        final double dy = y - 7.5;
        final double r = Math.sqrt(dx * dx + dy * dy);
        if (Math.max(Math.abs(dx), Math.abs(dy)) > 6.5) {
            bark(x, y, n, out);
            return;
        }
        final double ring = Math.sin(r * LOG_RINGS) > 0.6 ? 0.85 : 1;
        final double shade = ring + 0.06 * (n - 0.5);
        set(out, 0.72 * shade, 0.56 * shade, 0.34 * shade);
    }

    private static void concrete(int x, int y, double n, double[] out) {
        final double pore = noise(14, x, y) < 0.05 ? -0.1 : 0;
        final double v = 0.68 + 0.05 * (n - 0.5) + pore;
        set(out, v, v, v * 0.98);
    }

    private static void steel(int x, int y, double n, double[] out) {
        // Brushed: streaks along the rows, and a rivet in each corner
        final double streak = 0.05 * (noise(15, 0, y) - 0.5) + 0.02 * (n - 0.5);
        final boolean rivet = (x == 2 || x == 13) && (y == 2 || y == 13);
        final double v = 0.62 + streak + (rivet ? 0.12 : 0);
        set(out, v, v * 1.01, v * 1.04);
    }

    private static void bronze(int x, int y, double n, double[] out) {
        final boolean edge = x == 0 || y == 0 || x == SIZE - 1 || y == SIZE - 1;
        final double shade = (edge ? 0.8 : 1) + 0.08 * (n - 0.5);
        set(out, 0.72 * shade, 0.5 * shade, 0.24 * shade);
    }

    private static void glass(int x, int y, double n, double[] out) {
        final boolean frame = x == 0 || y == 0 || x == SIZE - 1 || y == SIZE - 1;
        // A glint across one corner
        final boolean glint = x + y == 5 || x + y == 7;
        set(out, 0.75, 0.88, 0.95);
        out[3] = frame ? 0.8 : glint ? 0.5 : GLASS_ALPHA;
    }

    private static void tuft(int x, int y, double n, double[] out) {
        // Blades of differing heights, rooted at the bottom row, with gaps between them
        final int height = (int) (noise(16, x, 0) * 9) + 4;
        final boolean blade = x % 3 != 2 || noise(17, x, 0) < 0.3;
        if (!blade || SIZE - y > height) {
            out[3] = 0;
            return;
        }
        // Darker toward the roots
        final double shade = 0.75 + 0.35 * (SIZE - y) / (double) height + 0.08 * (n - 0.5);
        set(out, 0.27 * shade, 0.52 * shade, 0.17 * shade);
    }

    /**
     * Set a color, keeping alpha.
     *
     * @param out The color, written.
     * @param red The red.
     * @param green The green.
     * @param blue The blue.
     */
    private static void set(double @NonNull [] out, double red, double green, double blue) {
        out[0] = red;
        out[1] = green;
        out[2] = blue;
    }

    /**
     * A hash of a pixel, spread evenly over 0 to 1.
     *
     * @param salt Which value.
     * @param x The column.
     * @param y The row.
     * @return The value.
     */
    private static double noise(int salt, int x, int y) {
        int h = salt * 0x632BE5AB + x * 0x85EBCA6B + y * 0xC2B2AE35;
        // The finalizer of MurmurHash3
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h >>> 8) / (double) (1 << 24);
    }

    /**
     * Pixels as an image.
     *
     * @param pixels The pixels, rows from the top, red, green, blue and alpha bytes each.
     * @return The image.
     */
    static BufferedImage image(byte @NonNull [] pixels) {
        final BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; ++y) {
            for (int x = 0; x < SIZE; ++x) {
                final int at = (y * SIZE + x) * CHANNELS;
                final int argb =
                        (pixels[at + 3] & CHANNEL_MAX) << 24
                                | (pixels[at] & CHANNEL_MAX) << 16
                                | (pixels[at + 1] & CHANNEL_MAX) << 8
                                | pixels[at + 2] & CHANNEL_MAX;
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    /** Private constructor so this class is not instantiated. */
    private StarterTextures() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
