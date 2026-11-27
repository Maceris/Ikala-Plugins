package com.ikalagaming.converter.texgraph;

import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.GraphFile;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
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
 * nodes:
 *   ...
 * </pre>
 *
 * {@code data} is the content folder baked maps go to, relative to the graph file; {@code texture}
 * names the maps under its {@code textures} folder, and {@code model} the model under its {@code
 * models} folder whose UVs give the face layout.
 */
public final class TextureGraph {
    /** What a texture graph file's name ends with. */
    public static final String SUFFIX = ".texgraph.yml";

    /** The format's version, written first. */
    public static final int FORMAT = 1;

    /** The key holding the format's version. */
    static final String FORMAT_KEY = "texture graph";

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
    public Evaluator.Settings settings(@Nullable java.nio.file.Path folder) {
        return new Evaluator.Settings(width, height, seed, folder);
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
        if (texture != null) {
            out.append("texture: ").append(quote(texture)).append('\n');
        }
        if (model != null) {
            out.append("model: ").append(quote(model)).append('\n');
        }
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
        texture = text(root, "texture");
        model = text(root, "model");
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
