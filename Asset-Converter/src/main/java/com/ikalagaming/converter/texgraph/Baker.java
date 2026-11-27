package com.ikalagaming.converter.texgraph;

import com.ikalagaming.converter.texgraph.OutputNodes.Face;
import com.ikalagaming.graphics.asset.AssetFile;
import com.ikalagaming.graphics.asset.MeshSection;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.ui.graph.model.Graph;

import lombok.NonNull;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferUShort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;

/**
 * Bakes a texture graph to the PNG files the game reads: {@code <name>.png} (base color, sRGB),
 * {@code .height.png} (16-bit), {@code .normal.png}, {@code .orm.png} and {@code .emissive.png}
 * (and {@code .mask.png}), each laid out as the model's UVs expect. A map is only written when the
 * Output has something linked for it, and a file whose pixels wouldn't change is left alone, so
 * bakes don't churn files that are already right.
 */
public final class Baker {
    /** The ending of a height map. */
    public static final String HEIGHT_SUFFIX = ".height";

    /** The ending of a normal map. */
    public static final String NORMAL_SUFFIX = ".normal";

    /** The ending of an occlusion, roughness and metallic map. */
    public static final String ORM_SUFFIX = ".orm";

    /** The ending of an emissive map. */
    public static final String EMISSIVE_SUFFIX = ".emissive";

    /** The ending of a material mask. */
    public static final String MASK_SUFFIX = ".mask";

    /** The image files' extension. */
    public static final String PNG = ".png";

    /** The largest 16-bit value. */
    private static final int MAX_SHORT = 65_535;

    /** Not made. */
    private Baker() {}

    /**
     * What a bake did.
     *
     * @param written Files written.
     * @param unchanged Files whose pixels were already right, left alone.
     * @param problems Why it couldn't bake, empty when it did.
     */
    public record Result(List<Path> written, List<Path> unchanged, List<String> problems) {
        /**
         * Whether the bake went through.
         *
         * @return True if there were no problems.
         */
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    /**
     * Bake a graph.
     *
     * @param texture The graph.
     * @param evaluator Evaluates it, keeping its cache.
     * @param graphFolder The folder the graph's file is in, where its relative names start.
     * @param dataFolder The content folder to bake into, or null for the graph's own setting.
     * @return What happened.
     */
    public static Result bake(
            @NonNull TextureGraph texture,
            @NonNull Evaluator evaluator,
            @NonNull Path graphFolder,
            @Nullable Path dataFolder) {
        final List<Path> written = new ArrayList<>();
        final List<Path> unchanged = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        final Graph graph = texture.getGraph();
        final List<Graph.Node> outputs =
                graph.nodes().stream().filter(n -> OutputNodes.isOutput(n.getType())).toList();
        if (outputs.size() != 1) {
            problems.add(
                    outputs.isEmpty()
                            ? "The graph has no Output node"
                            : "The graph has " + outputs.size() + " Output nodes; bake needs one");
            return new Result(written, unchanged, problems);
        }
        final Path data =
                dataFolder != null
                        ? dataFolder
                        : texture.getData() == null
                                ? null
                                : graphFolder.resolve(texture.getData()).normalize();
        if (data == null || texture.getTexture() == null) {
            problems.add("Set the graph's data folder and texture name to bake it");
            return new Result(written, unchanged, problems);
        }
        final Graph.Node output = outputs.getFirst();
        final List<Face> faces = OutputNodes.faces(output.getType());
        final List<Integer> tiles;
        try {
            tiles = layout(data, texture.getModel()).place(faces);
        } catch (IllegalArgumentException | IOException e) {
            problems.add(e.getMessage());
            return new Result(written, unchanged, problems);
        }
        final Bake bake =
                new Bake(
                        texture,
                        evaluator,
                        graph,
                        texture.settings(graphFolder),
                        output.getId(),
                        faces,
                        tiles,
                        problems);
        final Path base = data.resolve("textures").resolve(texture.getTexture());
        final BufferedImage color = bake.color(OutputNodes.Map.COLOR, true);
        final BufferedImage height = bake.height();
        final BufferedImage normal = bake.normal();
        final BufferedImage orm = bake.orm();
        final BufferedImage emissive = bake.color(OutputNodes.Map.EMISSIVE, false);
        final BufferedImage mask = bake.color(OutputNodes.Map.MASK, true);
        if (!problems.isEmpty()) {
            return new Result(written, unchanged, problems);
        }
        final boolean heightFile =
                graph.param(output.getId(), OutputNodes.HEIGHT_FILE) != null
                        && Boolean.parseBoolean(
                                graph.param(output.getId(), OutputNodes.HEIGHT_FILE));
        try {
            save(color, file(base, ""), written, unchanged);
            save(heightFile ? height : null, file(base, HEIGHT_SUFFIX), written, unchanged);
            save(normal, file(base, NORMAL_SUFFIX), written, unchanged);
            save(orm, file(base, ORM_SUFFIX), written, unchanged);
            save(emissive, file(base, EMISSIVE_SUFFIX), written, unchanged);
            save(mask, file(base, MASK_SUFFIX), written, unchanged);
        } catch (IOException e) {
            problems.add("Couldn't write the maps: " + e.getMessage());
        }
        return new Result(written, unchanged, problems);
    }

    /**
     * The layout of a model under a content folder.
     *
     * @param data The content folder.
     * @param model The model's name under its models folder, or null for one tile.
     * @return The layout.
     * @throws IOException If the model can't be read.
     */
    static FaceLayout layout(Path data, @Nullable String model) throws IOException {
        if (model == null) {
            return FaceLayout.ONE_TILE;
        }
        final Path file = data.resolve("models").resolve(model + ".ika");
        if (!Files.isRegularFile(file)) {
            throw new IOException("There is no model " + model + " at " + file);
        }
        final MeshData mesh =
                AssetFile.open(file)
                        .section(MeshSection.TAG)
                        .map(MeshSection::decode)
                        .orElseThrow(() -> new IOException(model + " has no mesh"));
        return FaceLayout.of(mesh);
    }

    /**
     * A map's file.
     *
     * @param base The texture's path without an extension.
     * @param suffix The map's ending.
     * @return The file.
     */
    private static Path file(Path base, String suffix) {
        return base.resolveSibling(base.getFileName() + suffix + PNG);
    }

    /**
     * Write an image, unless the file already has the same pixels.
     *
     * @param image The image, or null to write nothing.
     * @param file Where.
     * @param written Gets the file if written.
     * @param unchanged Gets the file if left alone.
     * @throws IOException If it can't be written.
     */
    private static void save(
            @Nullable BufferedImage image, Path file, List<Path> written, List<Path> unchanged)
            throws IOException {
        if (image == null) {
            return;
        }
        if (Files.isRegularFile(file) && samePixels(image, ImageIO.read(file.toFile()))) {
            unchanged.add(file);
            return;
        }
        Files.createDirectories(file.getParent());
        if (!ImageIO.write(image, "png", file.toFile())) {
            throw new IOException("No PNG writer for " + file);
        }
        written.add(file);
    }

    /**
     * Whether two images have the same size and pixels, at the depth they store.
     *
     * @param a One image.
     * @param b The other, or null.
     * @return True if the same.
     */
    static boolean samePixels(BufferedImage a, @Nullable BufferedImage b) {
        if (b == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return false;
        }
        if (a.getType() == BufferedImage.TYPE_USHORT_GRAY) {
            final int[] pa =
                    a.getRaster().getPixels(0, 0, a.getWidth(), a.getHeight(), (int[]) null);
            final int[] pb =
                    b.getRaster().getNumBands() == 1
                                    && b.getSampleModel().getSampleSize(0) == Short.SIZE
                            ? b.getRaster()
                                    .getPixels(0, 0, b.getWidth(), b.getHeight(), (int[]) null)
                            : null;
            return Arrays.equals(pa, pb);
        }
        for (int y = 0; y < a.getHeight(); ++y) {
            for (int x = 0; x < a.getWidth(); ++x) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * One bake's evaluation: reads the Output's faces and lays them out.
     *
     * @param texture The graph.
     * @param evaluator The evaluator.
     * @param graph The graph's nodes.
     * @param settings The size, seed and folder.
     * @param output The Output node.
     * @param faces Its faces.
     * @param tiles Each face's tile.
     * @param problems Gets what went wrong.
     */
    private record Bake(
            TextureGraph texture,
            Evaluator evaluator,
            Graph graph,
            Evaluator.Settings settings,
            String output,
            List<Face> faces,
            List<Integer> tiles,
            List<String> problems) {

        /**
         * A face's map, or null if nothing is linked or it failed.
         *
         * @param face The face.
         * @param map The map.
         * @return The plane.
         */
        @Nullable Plane read(Face face, OutputNodes.Map map) {
            final String pin = map.pin(faces.size() == 1 ? null : face);
            if (Evaluator.source(graph, output, pin) == null) {
                return null;
            }
            final Plane plane = evaluator.input(graph, settings, output, pin);
            if (plane == null) {
                problems.add("The Output's " + pin + " has an error upstream");
            }
            return plane;
        }

        /**
         * Whether any face has a map linked.
         *
         * @param map The map.
         * @return True if one does.
         */
        boolean any(OutputNodes.Map map) {
            for (Face face : faces) {
                if (Evaluator.source(graph, output, map.pin(faces.size() == 1 ? null : face))
                        != null) {
                    return true;
                }
            }
            return false;
        }

        /**
         * A new image the size of the whole layout.
         *
         * @param type The image type.
         * @return The image.
         */
        BufferedImage image(int type) {
            return new BufferedImage(settings.width() * faces.size(), settings.height(), type);
        }

        /**
         * A color map, sRGB.
         *
         * @param map Which map.
         * @param alpha Whether to keep alpha; otherwise opaque.
         * @return The image, or null if no face has it.
         */
        @Nullable BufferedImage color(OutputNodes.Map map, boolean alpha) {
            if (!any(map)) {
                return null;
            }
            final BufferedImage out = image(BufferedImage.TYPE_INT_ARGB);
            for (int f = 0; f < faces.size(); ++f) {
                final Plane plane = read(faces.get(f), map);
                final int left = tiles.get(f) * settings.width();
                for (int y = 0; y < settings.height(); ++y) {
                    for (int x = 0; x < settings.width(); ++x) {
                        int argb = 0xFF00_0000;
                        if (plane != null) {
                            final int a =
                                    alpha
                                            ? Math.round(
                                                    Math.clamp(plane.get(x, y, 3), 0, 1)
                                                            * Srgb.MAX_BYTE)
                                            : Srgb.MAX_BYTE;
                            argb =
                                    a << 24
                                            | Srgb.encode(plane.get(x, y, 0)) << 16
                                            | Srgb.encode(plane.get(x, y, 1)) << 8
                                            | Srgb.encode(plane.get(x, y, 2));
                        }
                        out.setRGB(left + x, y, argb);
                    }
                }
            }
            return out;
        }

        /**
         * The height map, 16-bit.
         *
         * @return The image, or null if no face has a height.
         */
        @Nullable BufferedImage height() {
            if (!any(OutputNodes.Map.HEIGHT)) {
                return null;
            }
            final BufferedImage out = image(BufferedImage.TYPE_USHORT_GRAY);
            final short[] values = ((DataBufferUShort) out.getRaster().getDataBuffer()).getData();
            final int stride = out.getWidth();
            for (int f = 0; f < faces.size(); ++f) {
                final Plane plane = read(faces.get(f), OutputNodes.Map.HEIGHT);
                final int left = tiles.get(f) * settings.width();
                for (int y = 0; y < settings.height(); ++y) {
                    for (int x = 0; x < settings.width(); ++x) {
                        final float value =
                                plane == null ? 0 : Math.clamp(plane.get(x, y, 0), 0, 1);
                        values[y * stride + left + x] = (short) Math.round(value * MAX_SHORT);
                    }
                }
            }
            return out;
        }

        /**
         * The normal map, from each face's height, with holes where its color is see-through.
         *
         * @return The image, or null when there is no height, normals are off or flat.
         */
        @Nullable BufferedImage normal() {
            final float strength;
            try {
                strength = Float.parseFloat(graph.param(output, OutputNodes.STRENGTH).trim());
            } catch (NumberFormatException | NullPointerException e) {
                problems.add("The Output's strength isn't a number");
                return null;
            }
            if (!any(OutputNodes.Map.HEIGHT)
                    || strength <= 0
                    || !Boolean.parseBoolean(graph.param(output, OutputNodes.NORMALS))) {
                return null;
            }
            final BufferedImage out = image(BufferedImage.TYPE_INT_ARGB);
            for (int f = 0; f < faces.size(); ++f) {
                final Plane height = read(faces.get(f), OutputNodes.Map.HEIGHT);
                final Plane color = read(faces.get(f), OutputNodes.Map.COLOR);
                final Plane coverage = new Plane(settings.width(), settings.height(), 1);
                for (int y = 0; y < settings.height(); ++y) {
                    for (int x = 0; x < settings.width(); ++x) {
                        coverage.set(x, y, 0, color == null ? 1 : color.get(x, y, 3));
                    }
                }
                final Plane normals =
                        Normals.make(
                                height == null
                                        ? new Plane(settings.width(), settings.height(), 1)
                                        : height,
                                coverage,
                                strength,
                                true);
                final int left = tiles.get(f) * settings.width();
                for (int y = 0; y < settings.height(); ++y) {
                    for (int x = 0; x < settings.width(); ++x) {
                        out.setRGB(
                                left + x,
                                y,
                                0xFF00_0000
                                        | Normals.encode(normals.get(x, y, 0)) << 16
                                        | Normals.encode(normals.get(x, y, 1)) << 8
                                        | Normals.encode(normals.get(x, y, 2)));
                    }
                }
            }
            return out;
        }

        /**
         * The occlusion, roughness and metallic map, linear.
         *
         * @return The image, or null if no face has any of them.
         */
        @Nullable BufferedImage orm() {
            if (!any(OutputNodes.Map.OCCLUSION)
                    && !any(OutputNodes.Map.ROUGHNESS)
                    && !any(OutputNodes.Map.METALLIC)) {
                return null;
            }
            final BufferedImage out = image(BufferedImage.TYPE_INT_ARGB);
            for (int f = 0; f < faces.size(); ++f) {
                final Plane occlusion = read(faces.get(f), OutputNodes.Map.OCCLUSION);
                final Plane roughness = read(faces.get(f), OutputNodes.Map.ROUGHNESS);
                final Plane metallic = read(faces.get(f), OutputNodes.Map.METALLIC);
                final int left = tiles.get(f) * settings.width();
                for (int y = 0; y < settings.height(); ++y) {
                    for (int x = 0; x < settings.width(); ++x) {
                        out.setRGB(
                                left + x,
                                y,
                                0xFF00_0000
                                        | linear(occlusion, x, y, 1) << 16
                                        | linear(roughness, x, y, 1) << 8
                                        | linear(metallic, x, y, 0));
                    }
                }
            }
            return out;
        }

        /**
         * A linear value as a byte.
         *
         * @param plane The plane, or null for the default.
         * @param x The column.
         * @param y The row.
         * @param fallback The value when there is no plane.
         * @return The byte.
         */
        private static int linear(@Nullable Plane plane, int x, int y, float fallback) {
            final float value = plane == null ? fallback : plane.get(x, y, 0);
            return Math.round(Math.clamp(value, 0, 1) * Srgb.MAX_BYTE);
        }
    }
}
