package com.ikalagaming.converter.texgraph;

import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.GraphFile;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A texture graph as its {@code *.texgraph.yml} file holds it: the graph, its tile size and seed,
 * and what it bakes to. The settings come first, then the graph as {@link GraphFile} writes it:
 *
 * <pre>
 * texture graph: 1
 * size: [16, 16]
 * seed: 1
 * data: ../../../plugins/Lotomation/data
 * texture: block/grass
 * model: block/cube_bottom_top
 * shared: ['../shared/wear.texparams.yml']
 * parameters:
 *   tint: {type: color, value: '#6b8f3a', group: Color}
 * nodes:
 *   ...
 * </pre>
 *
 * {@code data} is the content folder baked maps go to, relative to the graph file, unless {@code
 * maps} names another folder of textures for them, like an unshipped source folder; {@code texture}
 * names the maps under its {@code textures} folder, and {@code model} the model under its {@code
 * models} folder whose UVs give the face layout. {@code shared} lists shared parameter files the
 * graph reads, relative to it, and {@code parameters} the values it exposes; see {@link
 * GraphParameter} and {@link SharedParameters}.
 */
public final class TextureGraph {
    /** What a texture graph file's name ends with. */
    public static final String SUFFIX = ".texgraph.yml";

    /** The format's version, written first. */
    public static final int FORMAT = 1;

    /** The key holding the format's version. */
    static final String FORMAT_KEY = "texture graph";

    /** The key holding the graph's parameters. */
    static final String PARAMETERS = "parameters";

    /** A new graph's tile size. */
    public static final int DEFAULT_SIZE = 16;

    /** The largest tile size, to keep CPU evaluation in check. */
    public static final int MAX_SIZE = 4096;

    /**
     * The graph. -- GETTER -- The graph.
     *
     * @return The graph.
     */
    @Getter private final Graph graph;

    /**
     * Pixels across a tile. -- GETTER -- Pixels across. -- SETTER -- Set pixels across.
     *
     * @param width The width.
     * @return The width.
     */
    @Getter @Setter private int width = DEFAULT_SIZE;

    /**
     * Pixels down a tile. -- GETTER -- Pixels down. -- SETTER -- Set pixels down.
     *
     * @param height The height.
     * @return The height.
     */
    @Getter @Setter private int height = DEFAULT_SIZE;

    /**
     * The graph's seed. -- GETTER -- The seed. -- SETTER -- Set the seed.
     *
     * @param seed The seed.
     * @return The seed.
     */
    @Getter @Setter private long seed = 1;

    /**
     * The content folder maps bake to, relative to the graph file, or null. -- GETTER -- The
     * content folder. -- SETTER -- Set the content folder.
     *
     * @param data The folder.
     * @return The folder, or null when not set.
     */
    @Getter @Setter private @Nullable String data;

    /**
     * The folder of textures baked maps go under, relative to the graph file, or null for the
     * content folder's {@code textures}: an unshipped source folder, for maps converted to the
     * shipped format afterwards. -- GETTER -- The maps folder. -- SETTER -- Set the maps folder.
     *
     * @param maps The folder.
     * @return The folder, or null when not set.
     */
    @Getter @Setter private @Nullable String maps;

    /**
     * The maps' name under the content folder's textures, like {@code block/grass}, or null. --
     * GETTER -- The texture name. -- SETTER -- Set the texture name.
     *
     * @param texture The name.
     * @return The name, or null when not set.
     */
    @Getter @Setter private @Nullable String texture;

    /**
     * The model under the content folder's models whose UVs give the layout, or null for one tile
     * on every face. -- GETTER -- The model name. -- SETTER -- Set the model name.
     *
     * @param model The name.
     * @return The name, or null.
     */
    @Getter @Setter private @Nullable String model;

    /** The graph's own parameters, by name, in the order added. */
    private final Map<String, GraphParameter> parameters = new LinkedHashMap<>();

    /** The shared parameter files it reads, relative to the graph file, in order. */
    private final List<String> shared = new ArrayList<>();

    /** Shared parameters' values changed in the editor and not saved to their files yet. */
    private Map<String, GraphParameter> sharedOverrides = Map.of();

    /**
     * Use shared parameters' values the editor changed but hasn't saved, in place of their files'.
     * Not written with the graph.
     *
     * @param overrides The changed parameters, by name.
     */
    public void setSharedOverrides(@NonNull Map<String, GraphParameter> overrides) {
        sharedOverrides = Map.copyOf(overrides);
    }

    /**
     * The graph's own parameters.
     *
     * @return A copy, in the order added.
     */
    public List<GraphParameter> getParameters() {
        return new ArrayList<>(parameters.values());
    }

    /**
     * A parameter of the graph's own.
     *
     * @param name Its name.
     * @return The parameter, or null.
     */
    public @Nullable GraphParameter parameter(@NonNull String name) {
        return parameters.get(name);
    }

    /**
     * Add a parameter, or replace the one with its name.
     *
     * @param parameter The parameter.
     */
    public void putParameter(@NonNull GraphParameter parameter) {
        parameters.put(parameter.name(), parameter);
    }

    /**
     * Remove a parameter.
     *
     * @param name Its name.
     * @return True if there was one.
     */
    public boolean removeParameter(@NonNull String name) {
        return parameters.remove(name) != null;
    }

    /**
     * The shared parameter files the graph reads.
     *
     * @return A copy of their names, relative to the graph file.
     */
    public List<String> getShared() {
        return new ArrayList<>(shared);
    }

    /**
     * Set the shared parameter files the graph reads.
     *
     * @param files Their names, relative to the graph file.
     */
    public void setShared(@NonNull List<String> files) {
        shared.clear();
        shared.addAll(files);
    }

    /**
     * Every parameter the graph can read: its shared files' in order, then its own, which win.
     *
     * @param folder The graph file's folder, which shared files are named from, or null.
     * @param problems Gets each shared file that couldn't be read.
     * @return The parameters, by name.
     */
    public Map<String, GraphParameter> resolveParameters(
            @Nullable Path folder, @NonNull List<String> problems) {
        final Map<String, GraphParameter> all = new LinkedHashMap<>();
        for (String file : shared) {
            if (folder == null) {
                problems.add("Save the graph to read " + file);
                continue;
            }
            try {
                final SharedParameters.Read read = SharedParameters.read(folder.resolve(file));
                all.putAll(read.parameters());
                for (String problem : read.problems()) {
                    problems.add(file + ": " + problem);
                }
            } catch (IOException | IllegalArgumentException e) {
                problems.add("Can't read " + file + ": " + e.getMessage());
            }
        }
        for (Map.Entry<String, GraphParameter> override : sharedOverrides.entrySet()) {
            if (all.containsKey(override.getKey())) {
                all.put(override.getKey(), override.getValue());
            }
        }
        all.putAll(parameters);
        return all;
    }

    /**
     * Make an empty texture graph.
     *
     * @param library The node types it uses.
     */
    public TextureGraph(@NonNull TextureLibrary library) {
        graph = library.newGraph();
    }

    /**
     * The evaluator settings for this graph.
     *
     * @param folder Where relative file names start: the graph file's folder, or null.
     * @return The settings.
     */
    public Evaluator.Settings settings(@Nullable Path folder) {
        return new Evaluator.Settings(
                width, height, seed, folder, resolveParameters(folder, new ArrayList<>()));
    }

    /**
     * Write the file's text.
     *
     * @return The YAML.
     */
    public String write() {
        final StringBuilder out = new StringBuilder();
        out.append(FORMAT_KEY).append(": ").append(FORMAT).append('\n');
        out.append("size: [").append(width).append(", ").append(height).append("]\n");
        out.append("seed: ").append(seed).append('\n');
        if (data != null) {
            out.append("data: ").append(quote(data)).append('\n');
        }
        if (maps != null) {
            out.append("maps: ").append(quote(maps)).append('\n');
        }
        if (texture != null) {
            out.append("texture: ").append(quote(texture)).append('\n');
        }
        if (model != null) {
            out.append("model: ").append(quote(model)).append('\n');
        }
        if (!shared.isEmpty()) {
            out.append("shared: [")
                    .append(String.join(", ", shared.stream().map(TextureGraph::quote).toList()))
                    .append("]\n");
        }
        out.append(GraphParameter.writeAll(PARAMETERS, parameters.values()));
        out.append(GraphFile.write(graph));
        return out.toString();
    }

    /**
     * Read a file's text into this graph, which should be empty.
     *
     * @param text The YAML.
     * @return Problems met, each of which was left out.
     * @throws IllegalArgumentException If the text isn't a texture graph.
     */
    public List<String> read(@NonNull String text) {
        final Object document = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        if (!(document instanceof Map<?, ?> root) || !root.containsKey(FORMAT_KEY)) {
            throw new IllegalArgumentException(
                    "Not a texture graph: it has no '" + FORMAT_KEY + "'");
        }
        final List<String> problems = new ArrayList<>();
        if (root.get(FORMAT_KEY) instanceof Number version && version.intValue() > FORMAT) {
            problems.add(
                    "Written by a newer editor, format " + version + "; some parts may be lost");
        }
        if (root.get("size") instanceof List<?> size
                && size.size() == 2
                && size.get(0) instanceof Number w
                && size.get(1) instanceof Number h) {
            width = Math.clamp(w.intValue(), 1, MAX_SIZE);
            height = Math.clamp(h.intValue(), 1, MAX_SIZE);
        } else if (root.containsKey("size")) {
            problems.add("size is [width, height] in pixels");
        }
        if (root.get("seed") instanceof Number number) {
            seed = number.longValue();
        }
        data = text(root, "data");
        maps = text(root, "maps");
        texture = text(root, "texture");
        model = text(root, "model");
        shared.clear();
        if (root.get("shared") instanceof List<?> files) {
            for (Object file : files) {
                shared.add(String.valueOf(file));
            }
        } else if (root.containsKey("shared")) {
            problems.add("shared is a list of shared parameter files");
        }
        parameters.clear();
        parameters.putAll(GraphParameter.readAll(root.get(PARAMETERS), problems));
        problems.addAll(GraphFile.read(text, graph, (type, from, params) -> {}));
        return problems;
    }

    /**
     * A text setting.
     *
     * @param root The file's map.
     * @param key The setting.
     * @return Its text, or null if absent or blank.
     */
    private static @Nullable String text(Map<?, ?> root, String key) {
        final Object value = root.get(key);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    /**
     * Quote text for YAML.
     *
     * @param text The text.
     * @return It in single quotes.
     */
    private static String quote(String text) {
        return "'" + text.replace("'", "''") + "'";
    }
}
