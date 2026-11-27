package com.ikalagaming.converter.texgraph;

import lombok.NonNull;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Bakes texture graphs from the command line, without the editor: one graph, every graph under a
 * folder, or both, as {@code bin/bakeTextures} runs it.
 *
 * <pre>
 * bakeTextures [--data &lt;content folder&gt;] &lt;graph or folder&gt;...
 * </pre>
 *
 * Each graph bakes to the content folder its file names, unless {@code --data} gives another. Maps
 * whose pixels wouldn't change are left alone. Exits with 1 if any graph couldn't bake.
 */
public final class BakeTextures {
    /** The option naming the content folder to bake into. */
    private static final String DATA = "--data";

    /** Not made. */
    private BakeTextures() {}

    /**
     * Bake the graphs given.
     *
     * @param args The options and graphs.
     */
    public static void main(String[] args) {
        System.exit(run(List.of(args), System.out, System.err));
    }

    /**
     * Bake the graphs given, reporting to streams.
     *
     * @param args The options and graphs.
     * @param out Gets what was baked.
     * @param err Gets problems.
     * @return The exit code: 0 when every graph baked, 1 otherwise, 2 for bad arguments.
     */
    static int run(@NonNull List<String> args, @NonNull PrintStream out, @NonNull PrintStream err) {
        Path data = null;
        final List<Path> targets = new ArrayList<>();
        for (int i = 0; i < args.size(); ++i) {
            if (DATA.equals(args.get(i)) && i + 1 < args.size()) {
                data = Path.of(args.get(++i));
            } else if (args.get(i).startsWith("-")) {
                return usage(err);
            } else {
                targets.add(Path.of(args.get(i)));
            }
        }
        if (targets.isEmpty()) {
            return usage(err);
        }
        final List<Path> graphs = new ArrayList<>();
        for (Path target : targets) {
            if (Files.isDirectory(target)) {
                try (Stream<Path> files = Files.walk(target)) {
                    files.filter(f -> f.toString().endsWith(TextureGraph.SUFFIX))
                            .sorted()
                            .forEach(graphs::add);
                } catch (IOException e) {
                    err.println("Can't list " + target + ": " + e.getMessage());
                    return 1;
                }
            } else if (Files.isRegularFile(target)) {
                graphs.add(target);
            } else {
                err.println("There is no graph or folder at " + target);
                return 1;
            }
        }
        int failed = 0;
        int written = 0;
        int unchanged = 0;
        for (Path file : graphs) {
            final Path absolute = file.toAbsolutePath().normalize();
            // A library each, as subgraphs are named by paths relative to each graph
            final TextureGraph graph = new TextureGraph(TextureLibraries.standard());
            final Baker.Result result;
            try {
                final List<String> problems =
                        graph.read(Files.readString(absolute), absolute.getParent());
                if (!problems.isEmpty()) {
                    err.println(file + ": " + String.join("; ", problems));
                    ++failed;
                    continue;
                }
                result =
                        Baker.bake(
                                graph,
                                new Evaluator(graph.getLibrary()),
                                absolute.getParent(),
                                data);
            } catch (IOException | RuntimeException e) {
                err.println(file + ": " + e.getMessage());
                ++failed;
                continue;
            }
            if (!result.ok()) {
                err.println(file + ": " + String.join("; ", result.problems()));
                ++failed;
                continue;
            }
            for (Path map : result.written()) {
                out.println("Wrote " + map);
            }
            written += result.written().size();
            unchanged += result.unchanged().size();
        }
        out.println(
                "Baked "
                        + (graphs.size() - failed)
                        + " of "
                        + graphs.size()
                        + " graphs: "
                        + written
                        + " maps written, "
                        + unchanged
                        + " unchanged");
        return failed == 0 ? 0 : 1;
    }

    /**
     * Print how to use it.
     *
     * @param err Where.
     * @return The exit code for bad arguments.
     */
    private static int usage(PrintStream err) {
        err.println("Usage: bakeTextures [--data <content folder>] <graph or folder>...");
        err.println(
                "Bakes *" + TextureGraph.SUFFIX + " texture graphs to the maps the game reads.");
        return 2;
    }
}
