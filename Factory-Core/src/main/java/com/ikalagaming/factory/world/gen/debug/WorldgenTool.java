package com.ikalagaming.factory.world.gen.debug;

import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.gen.BiomeSelector;
import com.ikalagaming.factory.world.gen.Stage;
import com.ikalagaming.factory.world.gen.data.Diagnostic;
import com.ikalagaming.factory.world.gen.data.WorldgenCompiler;
import com.ikalagaming.factory.world.gen.data.WorldgenData;
import com.ikalagaming.factory.world.gen.density.Box;
import com.ikalagaming.factory.world.gen.density.DensityNode;
import com.ikalagaming.factory.world.gen.density.Interval;

import lombok.NonNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * The world generation command line tools: {@code check}, {@code render}, {@code hash}, {@code
 * trace} and {@code biome}. Runs from the Gradle {@code worldgen} task, and from the {@code
 * worldgen} console command while the game or editor is running. Both run the same code the game
 * generates with.
 */
public final class WorldgenTool {

    /** What the commands print when used wrongly. */
    private static final String USAGE =
            """
            Usage: worldgen <command> [options]
              check <dataFolder>... [--blocks blocks.csv]
                  Check data for errors. Exits non-zero if there are any.
              render --data <folder> [--data ...] --world <id> --target <target> --out <file.png>
                  [--seed N] [--plane xz|xy|zy] [--origin x,y,z] [--size w,h] [--scale blocks]
                  [--colormap diverging|sequential|categorical] [--channel name] [--range r]
                  [--stage stage] [--blocks blocks.csv]
                  Write an image of a slice. The target is a noise, density or parameter ID,
                  id#path for a node in a file, "biome" for the biome map or "blocks" for blocks.
              hash --data <folder> --world <id> --chunks x,y,z;x,y,z [--seed N] [--stage stage]
                  Print each chunk's content hash.
              trace --data <folder> --world <id> --target <target> --at x,y,z [--seed N]
                  Print every node's value at a point.
              biome --data <folder> --world <id> --at x,y,z [--seed N]
                  Print the parameters at a point and how well each biome fits.
              bounds --data <folder> --world <id> --target <target> --box x0,y0,z0,x1,y1,z1
                  Print every node's range over a box, to see why a region is or isn't skipped.""";

    /** The default seed for tools. */
    private static final long DEFAULT_SEED = 0;

    /** How many numbers an image size has. */
    private static final int IMAGE_AXES = 2;

    /** The default image size, in pixels. */
    private static final int DEFAULT_SIZE = 256;

    /**
     * Run from the command line.
     *
     * @param args The arguments.
     */
    public static void main(String[] args) {
        System.exit(run(Arrays.asList(args), System.out));
    }

    /**
     * Run a command.
     *
     * @param args The command and its options.
     * @param out Where to print.
     * @return The exit code: 0 for success.
     */
    public static int run(@NonNull List<String> args, @NonNull PrintStream out) {
        if (args.isEmpty()) {
            out.println(USAGE);
            return 1;
        }
        final Options options;
        try {
            options = new Options(args.subList(1, args.size()));
        } catch (IllegalArgumentException e) {
            out.println(e.getMessage());
            out.println(USAGE);
            return 1;
        }
        try {
            return switch (args.get(0)) {
                case "check" -> check(options, out);
                case "render" -> render(options, out);
                case "hash" -> hash(options, out);
                case "trace" -> trace(options, out);
                case "biome" -> biome(options, out);
                case "bounds" -> bounds(options, out);
                default -> {
                    out.println(USAGE);
                    yield 1;
                }
            };
        } catch (IllegalArgumentException | IOException e) {
            out.println(e.getMessage());
            return 1;
        }
    }

    /** Parsed command line options: positional arguments and repeatable {@code --name value}s. */
    private static final class Options {
        /** Arguments not attached to an option. */
        private final List<String> positional = new ArrayList<>();

        /** Each option's values. */
        private final Map<String, List<String>> named = new HashMap<>();

        /**
         * Parse options.
         *
         * @param args The arguments after the command.
         */
        Options(@NonNull List<String> args) {
            for (int i = 0; i < args.size(); ++i) {
                final String arg = args.get(i);
                if (arg.startsWith("--")) {
                    if (i + 1 >= args.size()) {
                        throw new IllegalArgumentException("Missing a value for " + arg);
                    }
                    named.computeIfAbsent(arg.substring(2), k -> new ArrayList<>())
                            .add(args.get(++i));
                } else {
                    positional.add(arg);
                }
            }
        }

        /**
         * An option's value.
         *
         * @param name The option.
         * @param fallback The value if it isn't given, or null if it is required.
         * @return The value.
         */
        String get(@NonNull String name, String fallback) {
            final List<String> values = named.get(name);
            if (values == null || values.isEmpty()) {
                if (fallback == null) {
                    throw new IllegalArgumentException("--" + name + " is required");
                }
                return fallback;
            }
            return values.get(values.size() - 1);
        }

        /**
         * Every value of a repeatable option.
         *
         * @param name The option.
         * @return The values.
         */
        List<String> all(@NonNull String name) {
            return named.getOrDefault(name, List.of());
        }

        /**
         * Numbers separated by commas.
         *
         * @param name The option.
         * @param count How many.
         * @param fallback The value if it isn't given.
         * @return The numbers.
         */
        double[] numbers(@NonNull String name, int count, @NonNull String fallback) {
            final String[] parts = get(name, fallback).split(",");
            if (parts.length != count) {
                throw new IllegalArgumentException(
                        "--" + name + " needs " + count + " comma separated numbers");
            }
            double[] result = new double[count];
            for (int i = 0; i < count; ++i) {
                result[i] = Double.parseDouble(parts[i].trim());
            }
            return result;
        }

        /**
         * The data folders.
         *
         * @return The {@code --data} folders, or the positional arguments.
         */
        List<Path> dataFolders() {
            final List<String> folders = all("data").isEmpty() ? positional : all("data");
            if (folders.isEmpty()) {
                throw new IllegalArgumentException("Give at least one data folder");
            }
            return folders.stream().map(Path::of).toList();
        }

        /**
         * The world seed.
         *
         * @return The {@code --seed}, or the default.
         */
        long seed() {
            return Long.parseLong(get("seed", Long.toString(DEFAULT_SEED)));
        }

        /**
         * The known blocks, if a block list is given.
         *
         * @return The block IDs, or null.
         * @throws IOException If the list can't be read.
         */
        Set<String> blocks() throws IOException {
            final String file = get("blocks", "");
            return file.isEmpty() ? null : readBlocks(Path.of(file));
        }

        /**
         * The stage to generate to.
         *
         * @return The {@code --stage}, or every built stage.
         */
        Stage stage() {
            return Stage.valueOf(get("stage", Stage.BLOCK_RULES.name()).toUpperCase(Locale.ROOT));
        }
    }

    /**
     * Read the block IDs from a block list, in the format the server's definition loader reads: CSV
     * with {@code mod_name} and {@code identifier} columns.
     *
     * @param file The CSV file.
     * @return The block IDs.
     * @throws IOException If it can't be read.
     */
    public static Set<String> readBlocks(@NonNull Path file) throws IOException {
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return Set.of();
        }
        final List<String> header = Arrays.asList(lines.get(0).trim().split(","));
        final int mod = header.indexOf("mod_name");
        final int name = header.indexOf("identifier");
        if (mod < 0 || name < 0) {
            throw new IllegalArgumentException(file + " needs mod_name and identifier columns");
        }
        Set<String> ids = new TreeSet<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            final String[] cells = line.split(",", -1);
            ids.add(cells[mod].trim() + ":" + cells[name].trim());
        }
        return ids;
    }

    /**
     * Check data for errors.
     *
     * @param options The options.
     * @param out Where to print.
     * @return 0 if there are no errors.
     * @throws IOException If the block list can't be read.
     */
    private static int check(@NonNull Options options, @NonNull PrintStream out)
            throws IOException {
        WorldgenCompiler compiler =
                new WorldgenCompiler(
                        WorldgenData.load(options.dataFolders()), options.seed(), options.blocks());
        compiler.compileAll();
        for (Diagnostic diagnostic : compiler.getDiagnostics()) {
            out.println(diagnostic);
        }
        final long errors =
                compiler.getDiagnostics().stream()
                        .filter(d -> d.severity() == Diagnostic.Severity.ERROR)
                        .count();
        final long warnings = compiler.getDiagnostics().size() - errors;
        out.println(errors + " errors, " + warnings + " warnings");
        return errors == 0 ? 0 : 1;
    }

    /**
     * Load a world type, printing problems.
     *
     * @param options The options.
     * @param out Where to print.
     * @return The debug context.
     * @throws IOException If the block list can't be read.
     */
    private static WorldgenDebug load(@NonNull Options options, @NonNull PrintStream out)
            throws IOException {
        final WorldgenDebug.LoadResult result =
                WorldgenDebug.load(
                        options.get("world", null),
                        options.dataFolders(),
                        options.seed(),
                        options.blocks());
        result.diagnostics().forEach(out::println);
        return result.debug()
                .orElseThrow(() -> new IllegalArgumentException("The world type has errors"));
    }

    /**
     * Write an image of a slice.
     *
     * @param options The options.
     * @param out Where to print.
     * @return 0 on success.
     * @throws IOException If the image can't be written.
     */
    private static int render(@NonNull Options options, @NonNull PrintStream out)
            throws IOException {
        final WorldgenDebug debug = load(options, out);
        final String target = options.get("target", null);
        final WorldgenDebug.Plane plane =
                WorldgenDebug.Plane.valueOf(options.get("plane", "xz").toUpperCase(Locale.ROOT));
        final double[] origin = options.numbers("origin", DensityNode.AXIS_COUNT, "0,0,0");
        final double[] size =
                options.numbers("size", IMAGE_AXES, DEFAULT_SIZE + "," + DEFAULT_SIZE);
        final int width = (int) size[0];
        final int height = (int) size[1];
        final double scale = Double.parseDouble(options.get("scale", "1"));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Map<String, Integer> legend = new TreeMap<>();
        if ("biome".equals(target) || "blocks".equals(target)) {
            final String[] ids =
                    "biome".equals(target)
                            ? debug.biomeSlice(plane, origin, width, height, scale)
                            : debug.blockSlice(
                                    plane, origin, width, height, scale, options.stage());
            for (int i = 0; i < ids.length; ++i) {
                final int color =
                        WorldgenDebug.airName().equals(ids[i])
                                ? Colormaps.AIR
                                : Colormaps.categorical(ids[i]);
                legend.put(ids[i], color);
                image.setRGB(i % width, i / width, color);
            }
        } else {
            final double[] values =
                    debug.slice(
                            target,
                            options.get("channel", "").isEmpty()
                                    ? null
                                    : options.get("channel", ""),
                            plane,
                            origin,
                            width,
                            height,
                            scale);
            final String colormap = options.get("colormap", "diverging");
            double range = Double.parseDouble(options.get("range", "0"));
            if (range <= 0) {
                for (double v : values) {
                    range = Math.max(range, Math.abs(v));
                }
                range = range == 0 ? 1 : range;
            }
            Interval stats = Interval.of(values[0]);
            for (double v : values) {
                stats = stats.union(Interval.of(v));
            }
            out.println("Values from " + stats.min() + " to " + stats.max());
            for (int row = 0; row < height; ++row) {
                for (int column = 0; column < width; ++column) {
                    final double v = values[row * width + column];
                    int color =
                            switch (colormap) {
                                case "diverging" -> Colormaps.diverging(v / range);
                                case "sequential" ->
                                        Colormaps.sequential(
                                                (v - stats.min())
                                                        / Math.max(
                                                                stats.max() - stats.min(),
                                                                Double.MIN_VALUE));
                                default ->
                                        throw new IllegalArgumentException(
                                                "Unknown colormap " + colormap);
                            };
                    // Where the sign changes to the next pixel, draw the solid boundary
                    if ("diverging".equals(colormap)) {
                        final boolean right =
                                column + 1 < width
                                        && (v > 0) != (values[row * width + column + 1] > 0);
                        final boolean below =
                                row + 1 < height
                                        && (v > 0) != (values[(row + 1) * width + column] > 0);
                        if (right || below) {
                            color = Colormaps.CONTOUR;
                        }
                    }
                    image.setRGB(column, row, color);
                }
            }
        }
        final Path file = Path.of(options.get("out", null));
        ImageIO.write(image, "png", file.toFile());
        legend.forEach((id, color) -> out.printf("%06x %s%n", color, id));
        out.println("Wrote " + file.toAbsolutePath());
        return 0;
    }

    /**
     * Print chunk hashes.
     *
     * @param options The options.
     * @param out Where to print.
     * @return 0 on success.
     * @throws IOException If the block list can't be read.
     */
    private static int hash(@NonNull Options options, @NonNull PrintStream out) throws IOException {
        final WorldgenDebug debug = load(options, out);
        final Stage stage = options.stage();
        for (String spec : options.get("chunks", null).split(";")) {
            final String[] parts = spec.trim().split(",");
            if (parts.length != DensityNode.AXIS_COUNT) {
                throw new IllegalArgumentException("Chunks are x,y,z: " + spec);
            }
            final ChunkPos pos =
                    new ChunkPos(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim()));
            final Chunk chunk = debug.generate(pos, stage);
            out.printf("%d %d %d %016x%n", pos.x(), pos.y(), pos.z(), chunk.contentHash());
        }
        return 0;
    }

    /**
     * Print a trace.
     *
     * @param options The options.
     * @param out Where to print.
     * @return 0 on success.
     * @throws IOException If the block list can't be read.
     */
    private static int trace(@NonNull Options options, @NonNull PrintStream out)
            throws IOException {
        final WorldgenDebug debug = load(options, out);
        final double[] at = options.numbers("at", DensityNode.AXIS_COUNT, "0,0,0");
        printTrace(debug.trace(options.get("target", null), at[0], at[1], at[2]), "", out);
        return 0;
    }

    /**
     * Print a trace tree, indented.
     *
     * @param trace The tree.
     * @param indent The indent for this level.
     * @param out Where to print.
     */
    private static void printTrace(
            @NonNull WorldgenDebug.Trace trace, @NonNull String indent, @NonNull PrintStream out) {
        out.printf(
                "%s%s = %s   [%s, %s]   %s%n",
                indent,
                trace.type(),
                Double.isNaN(trace.value()) ? "(sampled on a lattice)" : trace.value(),
                trace.bounds().min(),
                trace.bounds().max(),
                trace.origin() == null ? "" : trace.origin());
        for (WorldgenDebug.Trace child : trace.children()) {
            printTrace(child, indent + "  ", out);
        }
    }

    /**
     * Print why a point has its biome.
     *
     * @param options The options.
     * @param out Where to print.
     * @return 0 on success.
     * @throws IOException If the block list can't be read.
     */
    private static int biome(@NonNull Options options, @NonNull PrintStream out)
            throws IOException {
        final WorldgenDebug debug = load(options, out);
        final double[] at = options.numbers("at", DensityNode.AXIS_COUNT, "0,0,0");
        final WorldgenDebug.BiomeReport report = debug.biomeAt(at[0], at[1], at[2]);
        report.parameters().forEach((id, value) -> out.printf("%s = %.4f%n", id, value));
        out.println("Chosen: " + report.chosen());
        for (BiomeSelector.Candidate candidate : report.ranked()) {
            out.printf(
                    "  %-40s %s distance %.4f width %.4f weight %s%n",
                    candidate.biome().id(),
                    candidate.contains() ? "fits   " : "outside",
                    candidate.distance(),
                    candidate.width(),
                    candidate.biome().weight());
        }
        return 0;
    }

    /**
     * Print every node's range over a box.
     *
     * @param options The options.
     * @param out Where to print.
     * @return 0 on success.
     * @throws IOException If the block list can't be read.
     */
    private static int bounds(@NonNull Options options, @NonNull PrintStream out)
            throws IOException {
        final WorldgenDebug debug = load(options, out);
        final double[] corners =
                options.numbers("box", 2 * DensityNode.AXIS_COUNT, "0,0,0,16,16,16");
        final Box box =
                new Box(corners[0], corners[1], corners[2], corners[3], corners[4], corners[5]);
        printBounds(debug.bounds(options.get("target", null), box), "", out);
        return 0;
    }

    /**
     * Print a bounds tree, indented.
     *
     * @param bounds The tree.
     * @param indent The indent for this level.
     * @param out Where to print.
     */
    private static void printBounds(
            @NonNull WorldgenDebug.Bounds bounds,
            @NonNull String indent,
            @NonNull PrintStream out) {
        final String verdict =
                bounds.bounds().min() > 0
                        ? "certainly solid"
                        : bounds.bounds().max() <= 0 ? "certainly air" : "mixed";
        out.printf(
                "%s%s [%s, %s] %s   %s%n",
                indent,
                bounds.type(),
                bounds.bounds().min(),
                bounds.bounds().max(),
                verdict,
                bounds.origin() == null ? "" : bounds.origin());
        for (WorldgenDebug.Bounds child : bounds.children()) {
            printBounds(child, indent + "  ", out);
        }
    }

    private WorldgenTool() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
