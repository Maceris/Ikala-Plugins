package com.ikalagaming.factory.render.looks;

import lombok.NonNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Writes plausible normal maps for block textures, as {@code <name>.normal.png} beside each, from
 * how bumpy the surface likely is: each 16x16 tile's height is its brightness, dark crevices low
 * and bright tops high, unless a hand-painted {@code <name>.height.png} gives it. Tiles wrap within
 * themselves, so a block's faces meet their neighbors' without a seam, and a strip's tiles don't
 * bleed into each other. How deep the bumps go is by texture, from a table: rough stone and bark
 * deep, sand and metal shallow, glass and water flat.
 *
 * <p>Normals point the OpenGL way, green up the texture. Only missing files are written, so
 * touched-up maps are kept, unless {@code --force} is given; better still, touch up the height and
 * write the map again.
 *
 * <p>Run with {@code ./gradlew :Factory-Client:blockNormals --args="[--force] <data folder>"},
 * where the data folder is a content plugin's, such as Lotomation's {@code
 * plugins/Lotomation/data}; relative paths are from Factory-Client's project folder.
 */
public final class BlockNormals {

    /** The ending of a normal map. */
    public static final String NORMAL_SUFFIX = ".normal";

    /** The ending of a hand-painted height map. */
    public static final String HEIGHT_SUFFIX = ".height";

    /** Pixels along each side of a tile. */
    static final int TILE = 16;

    /** How steep the bumps get when a texture isn't in the table. */
    static final float DEFAULT_STRENGTH = 1.5f;

    /**
     * How steep each texture's bumps get: the slope across a tile's full height range, per texel. 0
     * writes no map, for surfaces that are smooth.
     */
    static final Map<String, Float> STRENGTH =
            Map.ofEntries(
                    Map.entry("stone", 2.5f),
                    Map.entry("dirt", 1.6f),
                    Map.entry("grass", 1.2f),
                    Map.entry("sand", 1.2f),
                    Map.entry("concrete", 0.8f),
                    Map.entry("oak_log", 3f),
                    Map.entry("oak_leaves", 1.5f),
                    Map.entry("grass_tuft", 1f),
                    Map.entry("steel", 0.4f),
                    Map.entry("bronze", 0.4f),
                    Map.entry("glass", 0f),
                    Map.entry("water", 0f));

    /** The alpha below which a texel is a hole, which doesn't count toward the height. */
    private static final int HOLE_ALPHA = 128;

    /** The option that writes over existing maps. */
    private static final String FORCE = "--force";

    /** Where the block textures are, under the data folder. */
    private static final String BLOCK_TEXTURES = BlockLooks.TEXTURES_FOLDER + "/block";

    /** Bits to shift for red, green and alpha in a packed color. */
    private static final int RED_SHIFT = 16;

    private static final int GREEN_SHIFT = 8;

    private static final int ALPHA_SHIFT = 24;

    /** The largest channel value. */
    private static final int CHANNEL_MAX = 255;

    /**
     * Write the normal maps.
     *
     * @param args {@code --force} to write over existing maps, then the data folder.
     * @throws IOException If a file can't be read or written.
     */
    public static void main(String[] args) throws IOException {
        final List<String> rest = new ArrayList<>(List.of(args));
        final boolean force = rest.remove(FORCE);
        if (rest.size() != 1) {
            System.err.println("Usage: BlockNormals [--force] <data folder>");
            System.exit(1);
            return;
        }
        final Path folder = Path.of(rest.get(0)).resolve(BLOCK_TEXTURES);
        final List<Path> textures;
        try (Stream<Path> files = Files.list(folder)) {
            textures =
                    files.filter(file -> isColorTexture(file.getFileName().toString()))
                            .sorted()
                            .toList();
        }
        for (Path texture : textures) {
            final String file = texture.getFileName().toString();
            final String name =
                    file.substring(0, file.length() - BlockLooks.TEXTURE_EXTENSION.length());
            final float strength = STRENGTH.getOrDefault(name, DEFAULT_STRENGTH);
            final Path out = folder.resolve(name + NORMAL_SUFFIX + BlockLooks.TEXTURE_EXTENSION);
            if (strength <= 0) {
                System.out.println("Flat, so no map: " + name);
                continue;
            }
            if (Files.exists(out) && !force) {
                System.out.println("Kept " + out);
                continue;
            }
            final BufferedImage color = ImageIO.read(texture.toFile());
            final Path heightFile =
                    folder.resolve(name + HEIGHT_SUFFIX + BlockLooks.TEXTURE_EXTENSION);
            final BufferedImage height =
                    Files.exists(heightFile) ? ImageIO.read(heightFile.toFile()) : null;
            ImageIO.write(normals(color, height, strength), "png", out.toFile());
            System.out.println("Wrote " + out + (height != null ? " from its height map" : ""));
        }
    }

    /**
     * Whether a texture file is a base color, not a map that goes with one, such as {@code
     * stone.normal.png} or {@code stone.height.png}.
     *
     * @param file The file name.
     * @return True for a base color PNG.
     */
    static boolean isColorTexture(@NonNull String file) {
        if (!file.endsWith(BlockLooks.TEXTURE_EXTENSION)) {
            return false;
        }
        // Maps that go with a texture are named <name>.<kind>.png
        final String stem =
                file.substring(0, file.length() - BlockLooks.TEXTURE_EXTENSION.length());
        return stem.indexOf('.') < 0;
    }

    /**
     * A normal map for a texture.
     *
     * @param color The texture, whose brightness is the height unless a height map is given.
     * @param height A hand-painted height map the same size, brighter higher, or null.
     * @param strength How steep the bumps get, see {@link #STRENGTH}.
     * @return The normal map, the same size, tangent-space normals encoded as {@code n / 2 + 0.5}
     *     with green up the texture.
     */
    public static BufferedImage normals(
            @NonNull BufferedImage color, BufferedImage height, float strength) {
        final int width = color.getWidth();
        final int rows = color.getHeight();
        final BufferedImage source = height != null ? height : color;
        // Pixel art comes in 16x16 tiles; anything else is one tile
        final int tileWidth = width % TILE == 0 && rows % TILE == 0 ? TILE : width;
        final int tileHeight = tileWidth == TILE ? TILE : rows;
        final BufferedImage out = new BufferedImage(width, rows, BufferedImage.TYPE_INT_ARGB);
        for (int top = 0; top < rows; top += tileHeight) {
            for (int left = 0; left < width; left += tileWidth) {
                final float[] heights =
                        tileHeights(source, color, left, top, tileWidth, tileHeight);
                writeTile(out, heights, left, top, tileWidth, tileHeight, strength);
            }
        }
        return out;
    }

    /**
     * A tile's heights, from 0 at its lowest to 1 at its highest. Holes take the tile's average, so
     * they don't make cliffs at the edges of leaves.
     *
     * @param source Where the heights come from.
     * @param color The texture, for which texels are holes.
     * @param left The tile's left column.
     * @param top The tile's top row.
     * @param width The tile's width.
     * @param height The tile's height.
     * @return The heights, rows from the top.
     */
    private static float[] tileHeights(
            @NonNull BufferedImage source,
            @NonNull BufferedImage color,
            int left,
            int top,
            int width,
            int height) {
        final float[] heights = new float[width * height];
        float sum = 0;
        int counted = 0;
        float low = Float.MAX_VALUE;
        float high = -Float.MAX_VALUE;
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                final int at = y * width + x;
                if ((color.getRGB(left + x, top + y) >>> ALPHA_SHIFT) < HOLE_ALPHA) {
                    heights[at] = Float.NaN;
                    continue;
                }
                final float value = luminance(source.getRGB(left + x, top + y));
                heights[at] = value;
                sum += value;
                ++counted;
                low = Math.min(low, value);
                high = Math.max(high, value);
            }
        }
        final float mean = counted > 0 ? sum / counted : 0;
        final float range = high - low;
        for (int i = 0; i < heights.length; ++i) {
            final float value = Float.isNaN(heights[i]) ? mean : heights[i];
            heights[i] = range > 1e-6f ? (value - low) / range : 0.5f;
        }
        return heights;
    }

    /**
     * Write a tile's normals: the slope of its heights by a Sobel filter, wrapping within the tile.
     *
     * @param out The normal map.
     * @param heights The tile's heights, 0 to 1.
     * @param left The tile's left column.
     * @param top The tile's top row.
     * @param width The tile's width.
     * @param height The tile's height.
     * @param strength How steep the bumps get.
     */
    private static void writeTile(
            @NonNull BufferedImage out,
            float @NonNull [] heights,
            int left,
            int top,
            int width,
            int height,
            float strength) {
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                // Rising to the right, and rising down the image
                final float dx =
                        (at(heights, width, height, x + 1, y - 1)
                                        + 2 * at(heights, width, height, x + 1, y)
                                        + at(heights, width, height, x + 1, y + 1)
                                        - at(heights, width, height, x - 1, y - 1)
                                        - 2 * at(heights, width, height, x - 1, y)
                                        - at(heights, width, height, x - 1, y + 1))
                                / 8;
                final float dy =
                        (at(heights, width, height, x - 1, y + 1)
                                        + 2 * at(heights, width, height, x, y + 1)
                                        + at(heights, width, height, x + 1, y + 1)
                                        - at(heights, width, height, x - 1, y - 1)
                                        - 2 * at(heights, width, height, x, y - 1)
                                        - at(heights, width, height, x + 1, y - 1))
                                / 8;
                // Green is up the texture, against the image's rows, so rising down tilts it up
                float nx = -dx * strength;
                float ny = dy * strength;
                float nz = 1;
                final float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= length;
                ny /= length;
                nz /= length;
                out.setRGB(left + x, top + y, encode(nx, ny, nz));
            }
        }
    }

    /**
     * A height, wrapping around the tile.
     *
     * @param heights The tile's heights.
     * @param width Its width.
     * @param height Its height.
     * @param x The column, which may be off the tile.
     * @param y The row, which may be off the tile.
     * @return The height.
     */
    private static float at(float @NonNull [] heights, int width, int height, int x, int y) {
        return heights[Math.floorMod(y, height) * width + Math.floorMod(x, width)];
    }

    /**
     * A unit normal as an opaque texel.
     *
     * @param x Along the tangent.
     * @param y Along the bitangent, up the texture.
     * @param z Out of the surface.
     * @return The packed ARGB texel.
     */
    private static int encode(float x, float y, float z) {
        final int red = Math.round((x * 0.5f + 0.5f) * CHANNEL_MAX);
        final int green = Math.round((y * 0.5f + 0.5f) * CHANNEL_MAX);
        final int blue = Math.round((z * 0.5f + 0.5f) * CHANNEL_MAX);
        return CHANNEL_MAX << ALPHA_SHIFT | red << RED_SHIFT | green << GREEN_SHIFT | blue;
    }

    /**
     * A texel's brightness, as its sRGB values weigh to the eye.
     *
     * @param argb The packed texel.
     * @return The brightness, 0 to 1.
     */
    private static float luminance(int argb) {
        final float red = ((argb >> RED_SHIFT) & CHANNEL_MAX) / (float) CHANNEL_MAX;
        final float green = ((argb >> GREEN_SHIFT) & CHANNEL_MAX) / (float) CHANNEL_MAX;
        final float blue = (argb & CHANNEL_MAX) / (float) CHANNEL_MAX;
        return 0.2126f * red + 0.7152f * green + 0.0722f * blue;
    }

    /** Private constructor so this class is not instantiated. */
    private BlockNormals() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
