package com.ikalagaming.factory.world.gen.debug;

import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.factory.world.gen.Stage;
import com.ikalagaming.factory.world.gen.density.DensityNode;
import com.ikalagaming.factory.world.gen.density.Interval;

import lombok.NonNull;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Images of 2D slices through world generation, the one way both the {@code worldgen render} tool
 * and the Worldgen Lab draw them, so they always agree. Sampling can be split into bands of rows,
 * for drawing on several threads.
 */
public final class SliceImages {

    /** The target that shows each pixel's biome. */
    public static final String BIOME_TARGET = "biome";

    /** The target that shows each pixel's block, from generated chunks. */
    public static final String BLOCKS_TARGET = "blocks";

    /** How values become colors. */
    public enum Colormap {
        /** Centered on 0: air blue, solid brown, with a contour where the sign changes. */
        DIVERGING,
        /** From the smallest value to the largest: dark blue through teal to yellow. */
        SEQUENTIAL;

        /**
         * Read a colormap name.
         *
         * @param name The name, like {@code diverging}.
         * @return The colormap.
         * @throws IllegalArgumentException If the name is unknown.
         */
        public static Colormap of(@NonNull String name) {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }
    }

    /**
     * What to draw.
     *
     * @param target A noise, density or parameter ID, {@code id#path}, {@value #BIOME_TARGET} or
     *     {@value #BLOCKS_TARGET}.
     * @param channel The noise channel, or null for the first.
     * @param plane The plane.
     * @param origin The world position of the top-left pixel.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param scale Blocks per pixel.
     * @param stage The last stage generated, for the blocks target.
     */
    public record Request(
            @NonNull String target,
            String channel,
            WorldgenDebug.@NonNull Plane plane,
            double @NonNull [] origin,
            int width,
            int height,
            double scale,
            @NonNull Stage stage) {

        /**
         * Whether the target gives IDs rather than numbers.
         *
         * @return True for biomes and blocks.
         */
        public boolean isCategorical() {
            return BIOME_TARGET.equals(target) || BLOCKS_TARGET.equals(target);
        }
    }

    /**
     * Sampled values, row by row from the top. Numbers for density targets, IDs for categorical
     * ones.
     *
     * @param values The numbers, or null for categorical targets.
     * @param ids The IDs, or null for density targets.
     */
    public record Samples(double[] values, String[] ids) {
        /**
         * Room for a whole request's samples.
         *
         * @param request The request.
         * @return Empty samples of the right kind and size.
         */
        public static Samples allocate(@NonNull Request request) {
            final int count = request.width() * request.height();
            return request.isCategorical()
                    ? new Samples(null, new String[count])
                    : new Samples(new double[count], null);
        }
    }

    /**
     * A drawn image.
     *
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param rgb Packed RGB colors, row by row from the top.
     * @param legend The color of each ID, for categorical targets, sorted.
     * @param range The smallest and largest value, for density targets.
     */
    public record Image(
            int width,
            int height,
            int @NonNull [] rgb,
            @NonNull Map<String, Integer> legend,
            Interval range) {}

    /**
     * Sample some rows of a request.
     *
     * @param debug The world generation to sample.
     * @param request What to draw.
     * @param node The target's function, from {@link WorldgenDebug#resolve}, or null for
     *     categorical targets.
     * @param firstRow The first row to sample.
     * @param rows How many rows.
     * @param chunks Generated chunks shared between bands, for the blocks target.
     * @param into Where the samples go, at their place in the whole image.
     */
    public static void sampleRows(
            @NonNull WorldgenDebug debug,
            @NonNull Request request,
            DensityNode node,
            int firstRow,
            int rows,
            @NonNull Map<ChunkPos, Chunk> chunks,
            @NonNull Samples into) {
        final int width = request.width();
        for (int row = firstRow; row < firstRow + rows; ++row) {
            for (int column = 0; column < width; ++column) {
                final double[] p =
                        WorldgenDebug.pixelPosition(
                                request.plane(), request.origin(), request.scale(), column, row);
                final int index = row * width + column;
                if (BIOME_TARGET.equals(request.target())) {
                    into.ids()[index] = debug.biomeAt(p[0], p[1], p[2]).chosen();
                } else if (BLOCKS_TARGET.equals(request.target())) {
                    into.ids()[index] = blockAt(debug, p, request.stage(), chunks).getName();
                } else {
                    into.values()[index] = node.value(p[0], p[1], p[2], null);
                }
            }
        }
    }

    /**
     * The block at a point, from a generated chunk.
     *
     * @param debug The world generation.
     * @param point The point.
     * @param stage The last stage to generate.
     * @param chunks Chunks already generated, added to.
     * @return The block.
     */
    public static Block blockAt(
            @NonNull WorldgenDebug debug,
            double @NonNull [] point,
            @NonNull Stage stage,
            @NonNull Map<ChunkPos, Chunk> chunks) {
        final long x = (long) StrictMath.floor(point[0]);
        final long y = (long) StrictMath.floor(point[1]);
        final long z = (long) StrictMath.floor(point[2]);
        final ChunkPos pos = ChunkPos.containing(x, y, z);
        final Chunk chunk = chunks.computeIfAbsent(pos, at -> debug.generate(at, stage));
        return chunk.getBlock(
                (int) (x - pos.blockX()), (int) (y - pos.blockY()), (int) (z - pos.blockZ()));
    }

    /**
     * The largest magnitude among values, for a diverging colormap's range.
     *
     * @param values The values.
     * @return The largest magnitude, or 1 if they are all zero.
     */
    public static double autoRange(double @NonNull [] values) {
        double range = 0;
        for (double v : values) {
            range = Math.max(range, Math.abs(v));
        }
        return range == 0 ? 1 : range;
    }

    /**
     * Color sampled values.
     *
     * @param request What was drawn.
     * @param samples The whole image's samples.
     * @param colormap How numbers become colors; ignored for categorical targets.
     * @param range The diverging colormap's range, the magnitude drawn fully saturated.
     * @return The image.
     */
    public static Image colorize(
            @NonNull Request request,
            @NonNull Samples samples,
            @NonNull Colormap colormap,
            double range) {
        final int width = request.width();
        final int height = request.height();
        int[] rgb = new int[width * height];
        Map<String, Integer> legend = new TreeMap<>();
        if (samples.ids() != null) {
            for (int i = 0; i < rgb.length; ++i) {
                final String id = samples.ids()[i];
                rgb[i] = World.AIR_NAME.equals(id) ? Colormaps.AIR : Colormaps.categorical(id);
                legend.put(id, rgb[i]);
            }
            return new Image(width, height, rgb, legend, null);
        }
        final double[] values = samples.values();
        Interval stats = Interval.of(values[0]);
        for (double v : values) {
            stats = stats.union(Interval.of(v));
        }
        final double span = Math.max(stats.max() - stats.min(), Double.MIN_VALUE);
        for (int row = 0; row < height; ++row) {
            for (int column = 0; column < width; ++column) {
                final int index = row * width + column;
                final double v = values[index];
                if (colormap == Colormap.SEQUENTIAL) {
                    rgb[index] = Colormaps.sequential((v - stats.min()) / span);
                    continue;
                }
                // Where the sign changes to the next pixel, draw the solid boundary
                final boolean right = column + 1 < width && (v > 0) != (values[index + 1] > 0);
                final boolean below = row + 1 < height && (v > 0) != (values[index + width] > 0);
                rgb[index] = right || below ? Colormaps.CONTOUR : Colormaps.diverging(v / range);
            }
        }
        return new Image(width, height, rgb, legend, stats);
    }

    /**
     * Draw a whole image on this thread.
     *
     * @param debug The world generation to sample.
     * @param request What to draw.
     * @param colormap How numbers become colors.
     * @param range The diverging colormap's range, or 0 or less to fit the values.
     * @return The image.
     */
    public static Image render(
            @NonNull WorldgenDebug debug,
            @NonNull Request request,
            @NonNull Colormap colormap,
            double range) {
        final DensityNode node =
                request.isCategorical() ? null : debug.resolve(request.target(), request.channel());
        final Samples samples = Samples.allocate(request);
        sampleRows(debug, request, node, 0, request.height(), new ConcurrentHashMap<>(), samples);
        final double fitted =
                range > 0 || samples.values() == null ? range : autoRange(samples.values());
        return colorize(request, samples, colormap, fitted);
    }

    private SliceImages() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
