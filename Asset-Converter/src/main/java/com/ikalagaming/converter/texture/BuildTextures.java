package com.ikalagaming.converter.texture;

import com.ikalagaming.converter.texgraph.Baker;
import com.ikalagaming.converter.texgraph.Evaluator;
import com.ikalagaming.converter.texgraph.TextureGraph;
import com.ikalagaming.converter.texgraph.TextureLibraries;
import com.ikalagaming.converter.texgraph.TextureLibrary;

import lombok.NonNull;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Builds a game's textures from their sources, as {@code bin/buildTextures} runs it: every texture
 * graph under a folder of sources bakes its PNG maps (beside itself, by its maps setting), then
 * every texture's PNG maps there, baked or painted, become one {@code .ika} in the shipped folder
 * with the same layout. Files whose pixels wouldn't change are left alone, so running it again
 * changes only what an edit changed.
 *
 * <pre>
 * buildTextures &lt;sources&gt; &lt;shipped&gt; [--uses &lt;shared parameter file&gt;]
 * </pre>
 *
 * With {@code --uses}, only graphs reading that shared parameter file bake, but every texture is
 * still converted. The baked maps are listed in a {@code .gitignore} beside them, as build output,
 * so painted PNGs in the same folder stay tracked. Exits with 1 if anything failed.
 */
public final class BuildTextures {
    /** The option limiting the bake to graphs reading a shared parameter file. */
    private static final String USES = "--uses";

    /** Not made. */
    private BuildTextures() {}

    /**
     * Build the textures.
     *
     * @param args The folders and options.
     */
    public static void main(String[] args) {
        System.exit(run(List.of(args), System.out, System.err));
    }

    /**
     * Build the textures, reporting to streams.
     *
     * @param args The folders and options.
     * @param out Gets what was done.
     * @param err Gets problems.
     * @return 0 when everything built, 1 otherwise, 2 for bad arguments.
     */
    static int run(@NonNull List<String> args, @NonNull PrintStream out, @NonNull PrintStream err) {
        Path uses = null;
        final List<Path> folders = new ArrayList<>();
        for (int i = 0; i < args.size(); ++i) {
            if (USES.equals(args.get(i)) && i + 1 < args.size()) {
                uses = Path.of(args.get(++i)).toAbsolutePath().normalize();
            } else if (args.get(i).startsWith("-")) {
                return usage(err);
            } else {
                folders.add(Path.of(args.get(i)));
            }
        }
        if (folders.size() != 2 || !Files.isDirectory(folders.get(0))) {
            return usage(err);
        }
        final Path sources = folders.get(0).toAbsolutePath().normalize();
        final Path shipped = folders.get(1).toAbsolutePath().normalize();
        final TextureLibrary library = TextureLibraries.standard();
        final List<Path> bakedMaps = new ArrayList<>();
        int failed = 0;
        int baked = 0;
        int written = 0;
        int unchanged = 0;
        final List<Path> graphs;
        try (Stream<Path> files = Files.walk(sources)) {
            graphs =
                    files.filter(f -> f.toString().endsWith(TextureGraph.SUFFIX)).sorted().toList();
        } catch (IOException e) {
            err.println("Can't list " + sources + ": " + e.getMessage());
            return 1;
        }
        for (Path file : graphs) {
            final TextureGraph graph = new TextureGraph(library);
            try {
                final List<String> problems = graph.read(Files.readString(file));
                if (!problems.isEmpty()) {
                    err.println(file + ": " + String.join("; ", problems));
                    ++failed;
                    continue;
                }
            } catch (IOException | RuntimeException e) {
                err.println(file + ": " + e.getMessage());
                ++failed;
                continue;
            }
            if (uses != null && !reads(graph, file.getParent(), uses)) {
                continue;
            }
            final Baker.Result result =
                    Baker.bake(graph, new Evaluator(library), file.getParent(), null);
            if (!result.ok()) {
                err.println(file + ": " + String.join("; ", result.problems()));
                ++failed;
                continue;
            }
            ++baked;
            written += result.written().size();
            unchanged += result.unchanged().size();
            for (Path map : result.written()) {
                out.println("Baked " + sources.relativize(map));
            }
            bakedMaps.addAll(result.written());
            bakedMaps.addAll(result.unchanged());
        }
        try {
            ignoreBaked(bakedMaps);
        } catch (IOException e) {
            err.println("Can't list the baked maps to ignore: " + e.getMessage());
            ++failed;
        }
        int converted = 0;
        int kept = 0;
        try {
            for (TextureConvert.Converted texture :
                    TextureConvert.convertAll(
                            sources, shipped, com.ikalagaming.converter.AssetConvert.version())) {
                if (texture.written()) {
                    ++converted;
                    out.println("Wrote " + shipped.relativize(texture.target()));
                } else {
                    ++kept;
                }
            }
        } catch (IOException e) {
            err.println(e.getMessage());
            ++failed;
        }
        out.println(
                "Baked "
                        + baked
                        + " graphs ("
                        + written
                        + " maps written, "
                        + unchanged
                        + " unchanged); "
                        + converted
                        + " textures written, "
                        + kept
                        + " unchanged"
                        + (failed == 0 ? "" : "; " + failed + " failed"));
        return failed == 0 ? 0 : 1;
    }

    /** What heads a {@code .gitignore} this keeps, so a hand-written one is never touched. */
    static final String IGNORE_HEADER =
            "# Maps baked from texture graphs by bin/buildTextures, which keeps this list;"
                    + " painted PNGs aren't listed";

    /**
     * List baked maps in a {@code .gitignore} in each folder they were baked to, as build output,
     * so painted PNGs beside them stay tracked. Names already listed stay, as a partial build
     * ({@code --uses}) bakes only some graphs. A {@code .gitignore} this didn't write is left
     * alone.
     *
     * @param maps The maps baked, or left as they were, this run.
     * @throws IOException If a list can't be read or written.
     */
    static void ignoreBaked(@NonNull List<Path> maps) throws IOException {
        final java.util.Map<Path, java.util.SortedSet<String>> byFolder = new java.util.TreeMap<>();
        for (Path map : maps) {
            final Path absolute = map.toAbsolutePath().normalize();
            byFolder.computeIfAbsent(absolute.getParent(), f -> new java.util.TreeSet<>())
                    .add(absolute.getFileName().toString());
        }
        for (java.util.Map.Entry<Path, java.util.SortedSet<String>> folder : byFolder.entrySet()) {
            final Path ignore = folder.getKey().resolve(".gitignore");
            final java.util.SortedSet<String> names = folder.getValue();
            String old = "";
            if (Files.isRegularFile(ignore)) {
                old = Files.readString(ignore).replace("\r\n", "\n");
                if (!old.startsWith(IGNORE_HEADER)) {
                    continue;
                }
                for (String line : old.split("\n")) {
                    if (!line.isBlank() && !line.startsWith("#")) {
                        names.add(line.trim());
                    }
                }
            }
            final String text = IGNORE_HEADER + "\n" + String.join("\n", names) + "\n";
            if (!text.equals(old)) {
                Files.writeString(ignore, text);
            }
        }
    }

    /**
     * Whether a graph reads a shared parameter file.
     *
     * @param graph The graph.
     * @param folder Its file's folder.
     * @param shared The shared file, absolute.
     * @return True if one of its shared files is that file.
     */
    static boolean reads(TextureGraph graph, Path folder, Path shared) {
        for (String name : graph.getShared()) {
            if (folder.resolve(name).toAbsolutePath().normalize().equals(shared)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Print how to use it.
     *
     * @param err Where.
     * @return The exit code for bad arguments.
     */
    private static int usage(PrintStream err) {
        err.println("Usage: buildTextures <sources> <shipped> [--uses <shared parameter file>]");
        err.println(
                "Bakes every *"
                        + TextureGraph.SUFFIX
                        + " under <sources>, then turns every texture's PNG maps there into a"
                        + " .ika under <shipped>.");
        return 2;
    }
}
